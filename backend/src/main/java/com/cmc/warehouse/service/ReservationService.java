package com.cmc.warehouse.service;

import com.cmc.warehouse.common.AppMessages;
import com.cmc.warehouse.common.AppNumbers;
import com.cmc.warehouse.domain.reservation.Reservation;
import com.cmc.warehouse.domain.reservation.ReservationItem;
import com.cmc.warehouse.domain.reservation.ReservationStatus;
import com.cmc.warehouse.exception.DuplicateActiveReservationException;
import com.cmc.warehouse.exception.InsufficientStockException;
import com.cmc.warehouse.exception.ReservationNotFoundException;
import com.cmc.warehouse.observability.ReservationMetrics;
import com.cmc.warehouse.repository.InventoryRepository;
import com.cmc.warehouse.repository.ReservationRepository;
import com.cmc.warehouse.service.command.ReservationLine;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Coordinates the full reservation lifecycle.
 *
 * Every mutation visibly composes three concurrency primitives:
 *
 *   1. REDIS — per-SKU counters live in Redis keys
 *      ({@code stock:{sku}} and {@code reserved:{sku}}) so the hot
 *      path doesn't fight for Postgres row locks.
 *
 *   2. LUA  — the check-and-write step runs as a single atomic Lua
 *      script. Redis executes scripts single-threaded, so between
 *      the {@code (stock - reserved) >= qty} check and the
 *      {@code INCRBY reserved} write, no other client can interleave
 *      a read or a write. This is the no-oversell guarantee.
 *
 *   3. CAS  — Postgres is updated with a conditional SQL UPDATE
 *      ({@code UPDATE inventory … WHERE (total - reserved) >= :qty})
 *      whose WHERE clause IS the compare-and-swap. Postgres takes a
 *      row lock for the statement, so the check and the write happen
 *      atomically against the same row snapshot. Zero rows updated
 *      → conflict, we compensate Redis and throw.
 *
 * Other design patterns in play here:
 *   - {@link ReservationFactory} (Factory)        — aggregate construction.
 *   - {@link com.cmc.warehouse.domain.reservation.state.ReservationState}
 *     (State) — enforces PENDING → CONFIRMED / CANCELLED transitions.
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    public static final String STOCK_PREFIX    = "stock:";
    public static final String RESERVED_PREFIX = "reserved:";

    private final ReservationFactory reservationFactory;
    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final StringRedisTemplate redis;
    private final RedisScript<Long> reserveScript;
    private final RedisScript<Long> releaseScript;
    private final RedisScript<Long> consumeScript;
    private final ReservationMetrics reservationMetrics;

    public ReservationService(ReservationFactory reservationFactory,
                              ReservationRepository reservationRepository,
                              InventoryRepository inventoryRepository,
                              StringRedisTemplate redis,
                              RedisScript<Long> reserveScript,
                              RedisScript<Long> releaseScript,
                              RedisScript<Long> consumeScript,
                              ReservationMetrics reservationMetrics) {
        this.reservationFactory   = reservationFactory;
        this.reservationRepository = reservationRepository;
        this.inventoryRepository  = inventoryRepository;
        this.redis                = redis;
        this.reserveScript        = reserveScript;
        this.releaseScript        = releaseScript;
        this.consumeScript        = consumeScript;
        this.reservationMetrics   = reservationMetrics;
    }

    public record ReserveOutcome(Reservation reservation, boolean idempotentReplay) {}

    /**
     * POST /reservations → here.
     *
     * Order of operations matters:
     *   (a) Build & persist the PENDING reservation row.
     *   (b) REDIS + LUA reserve — fast atomic oversell check.
     *   (c) SQL CAS reserve     — durable Postgres mirror.
     *
     * If (c) fails we compensate (b) by running the release Lua
     * script, so Redis and Postgres can't drift on the happy path.
     * If anything throws, the surrounding {@code @Transactional}
     * rolls back (a) and any partial SQL CAS updates in (c).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "reservationOps")
    public Reservation reserve(String orderId, List<ReservationLine> lines) {
        return reserveWithOutcome(orderId, lines).reservation();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "reservationOps")
    public ReserveOutcome reserveWithOutcome(String orderId, List<ReservationLine> lines) {
        Timer.Sample sample = reservationMetrics.startLatencySample();
        log.info("Reservation reserve started: orderId={}, lineCount={}", orderId, lines.size());
        try {
            var existingPending = reservationRepository
                    .findFirstByOrderIdAndStatusOrderByCreatedAtDesc(orderId, ReservationStatus.PENDING);
            if (existingPending.isPresent()) {
                ReserveOutcome outcome = handleExistingPending(orderId, lines, existingPending.get());
                reservationMetrics.record(
                        "reserve",
                        outcome.idempotentReplay() ? "idempotent_replay" : "success",
                        sample);
                return outcome;
            }

            Reservation reservation = reservationFactory.create(orderId, lines);
            Reservation saved;
            try {
                saved = reservationRepository.save(reservation);
            } catch (DataIntegrityViolationException raceEx) {
                // Handles concurrent duplicate create attempts guarded by the DB partial unique index.
                var winner = reservationRepository
                        .findFirstByOrderIdAndStatusOrderByCreatedAtDesc(orderId, ReservationStatus.PENDING)
                        .orElseThrow(() -> raceEx);
                ReserveOutcome outcome = handleExistingPending(orderId, lines, winner);
                reservationMetrics.record(
                        "reserve",
                        outcome.idempotentReplay() ? "idempotent_replay" : "success",
                        sample);
                return outcome;
            }
            List<ReservationItem> items = saved.getItems();

            // ─── (b) REDIS + LUA — atomic hot-path reserve ────────────────
            List<String> keys = redisKeysFor(items);
            Object[] args = quantitiesArgvFor(items);
            log.debug("Reserve {} → calling reserve.lua on Redis with keys={}, args={}",
                    orderId, keys, args);
            Long luaResult = redis.execute(reserveScript, keys, args);
            if (luaResult == null || luaResult == AppNumbers.NEGATIVE_ONE) {
                log.warn("Reservation reserve rejected by Redis script: orderId={}, status={}", orderId, luaResult);
                // Lua returns a single status, not the offending SKU — surface
                // the first line so the caller has something actionable.
                ReservationItem first = items.get(0);
                throw new InsufficientStockException(
                        first.getProduct().getSku(), first.getQuantity(), availableQuantity(first));
            }

            // ─── (c) SQL CAS — durable Postgres mirror ────────────────────
            // Each UPDATE's WHERE clause is the compare-and-swap. If anyone
            // else moved the row first and there's no longer enough stock,
            // the row simply isn't matched and the UPDATE affects 0 rows.
            try {
                for (ReservationItem item : items) {
                    int updated = inventoryRepository.reserveCas(
                            item.getProduct().getId(), item.getQuantity());
                    log.debug("Reserve {} → SQL CAS UPDATE for SKU {} returned {} row(s)",
                            orderId, item.getProduct().getSku(), updated);
                    if (updated == AppNumbers.ZERO) {
                        throw new InsufficientStockException(
                                item.getProduct().getSku(), item.getQuantity(), availableQuantity(item));
                    }
                }
            } catch (RuntimeException e) {
                compensateRedis(releaseScript, "reserve compensation", keys, args);
                log.warn("Reservation reserve failed and compensated: orderId={}, error={}", orderId, e.toString());
                throw e;
            }

            log.info("Reservation reserve completed: orderId={}, reservationId={}, status={}",
                    orderId, saved.getId(), saved.getStatus());
            reservationMetrics.record("reserve", "success", sample);
            return new ReserveOutcome(saved, false);
        } catch (Exception e) {
            reservationMetrics.record("reserve", classifyFailureResult(e), sample);
            throw e;
        }
    }

    /**
     * POST /reservations/{id}/confirm → here.
     *
     * Confirm permanently removes the reserved units from inventory:
     *   - State pattern enforces the PENDING → CONFIRMED transition.
     *   - SQL CAS decrements BOTH total_quantity and reserved_quantity
     *     in Postgres atomically.
     *   - The consume Lua script mirrors the same change in Redis.
     */
    @Transactional
    @CircuitBreaker(name = "reservationOps")
    public Reservation confirm(Long reservationId) {
        Timer.Sample sample = reservationMetrics.startLatencySample();
        log.info("Reservation confirm started: reservationId={}", reservationId);
        try {
            Reservation reservation = loadOrThrow(reservationId);
            reservation.confirm();
            applyDurableChange(reservation, /*forConfirm=*/true);
            log.info("Reservation confirm completed: reservationId={}, status={}",
                    reservation.getId(), reservation.getStatus());
            reservationMetrics.record("confirm", "success", sample);
            return reservation;
        } catch (Exception e) {
            reservationMetrics.record("confirm", classifyFailureResult(e), sample);
            throw e;
        }
    }

    /**
     * POST /reservations/{id}/cancel → here.
     *
     * Cancel returns the held units to the pool:
     *   - State pattern enforces the PENDING → CANCELLED transition.
     *   - SQL CAS decrements reserved_quantity in Postgres atomically.
     *   - The release Lua script mirrors the same change in Redis.
     */
    @Transactional
    @CircuitBreaker(name = "reservationOps")
    public Reservation cancel(Long reservationId) {
        Timer.Sample sample = reservationMetrics.startLatencySample();
        log.info("Reservation cancel started: reservationId={}", reservationId);
        try {
            Reservation reservation = loadOrThrow(reservationId);
            reservation.cancel();
            applyDurableChange(reservation, /*forConfirm=*/false);
            log.info("Reservation cancel completed: reservationId={}, status={}",
                    reservation.getId(), reservation.getStatus());
            reservationMetrics.record("cancel", "success", sample);
            return reservation;
        } catch (Exception e) {
            reservationMetrics.record("cancel", classifyFailureResult(e), sample);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<Reservation> listAll() {
        return reservationRepository.findAllWithItems();
    }

    @Transactional(readOnly = true)
    public Page<Reservation> listPage(Pageable pageable) {
        return reservationRepository.findPageWithItems(pageable);
    }

    @Transactional(readOnly = true)
    public Reservation get(Long id) {
        return loadOrThrow(id);
    }

    // ─── internals ────────────────────────────────────────────────────

    /**
     * Shared post-state-change effect for confirm / cancel. Runs
     * SQL CAS first (so a failure rolls back inside the
     * {@code @Transactional}), then mirrors the result in Redis via
     * the corresponding Lua script (consume on confirm, release on
     * cancel). All-or-nothing in Redis because the Lua scripts
     * validate every SKU before writing any.
     */
    private void applyDurableChange(Reservation reservation, boolean forConfirm) {
        log.info("Reservation {} durable change started: reservationId={}, itemCount={}",
                forConfirm ? "confirm" : "cancel", reservation.getId(), reservation.getItems().size());
        List<ReservationItem> items = reservation.getItems();
        for (ReservationItem item : items) {
            int updated = forConfirm
                    ? inventoryRepository.consumeCas(item.getProduct().getId(), item.getQuantity())
                    : inventoryRepository.releaseCas(item.getProduct().getId(), item.getQuantity());
            log.debug("{} {} → SQL CAS UPDATE for SKU {} returned {} row(s)",
                    forConfirm ? "Confirm" : "Cancel",
                    reservation.getId(), item.getProduct().getSku(), updated);
            if (updated == 0) {
                throw new IllegalStateException(
                        "SQL CAS failed for SKU " + item.getProduct().getSku()
                                + " during " + (forConfirm ? "confirm" : "cancel")
                                + " — bookkeeping drift");
            }
        }

        List<String> keys = redisKeysFor(items);
        Object[] args = quantitiesArgvFor(items);
        RedisScript<Long> script = forConfirm ? consumeScript : releaseScript;
        Long luaResult = redis.execute(script, keys, args);
        log.debug("{} {} → {}.lua on Redis returned {}",
                forConfirm ? "Confirm" : "Cancel",
                reservation.getId(),
                forConfirm ? "consume" : "release",
                luaResult);
        if (luaResult == null || luaResult != AppNumbers.ONE) {
            // Redis and Postgres have drifted — the script refused to
            // apply the mutation. Surface loudly so a reconciler / on-call
            // can investigate; the @Transactional rolls back the SQL CAS.
            throw new IllegalStateException(
                    "Redis " + (forConfirm ? "consume" : "release")
                            + " refused (status=" + luaResult
                            + ") — Redis/Postgres drift on reservation " + reservation.getId());
        }
        log.info("Reservation {} durable change completed: reservationId={}",
                forConfirm ? "confirm" : "cancel", reservation.getId());
    }

    /**
     * Try to undo a Redis-side mutation when a later step in the
     * compound operation failed. Best-effort — if the compensation
     * itself fails we log loudly; the dual-write window between
     * Redis and Postgres is documented in the README.
     */
    private void compensateRedis(RedisScript<Long> script, String reason,
                                 List<String> keys, Object[] args) {
        try {
            Long result = redis.execute(script, keys, args);
            log.warn("Redis compensation ({}) ran, status={}", reason, result);
        } catch (Exception compensationEx) {
            log.error("Redis compensation ({}) failed — drift possible: {}",
                    reason, compensationEx.toString(), compensationEx);
        }
    }

    /** Pack items into the (stock:{sku}, reserved:{sku}) layout the Lua scripts expect. */
    private static List<String> redisKeysFor(List<ReservationItem> items) {
        List<String> keys = new ArrayList<>(items.size() * 2);
        for (ReservationItem item : items) {
            keys.add(STOCK_PREFIX + item.getProduct().getSku());
            keys.add(RESERVED_PREFIX + item.getProduct().getSku());
        }
        return keys;
    }

    private static Object[] quantitiesArgvFor(List<ReservationItem> items) {
        Object[] args = new Object[items.size()];
        for (int i = 0; i < items.size(); i++) {
            args[i] = String.valueOf(items.get(i).getQuantity());
        }
        return args;
    }

    /**
     * Read best-effort "available" quantity for error reporting.
     *
     * Redis is preferred because it is the hot path and reflects the latest
     * reserved counter. If Redis keys are missing/unparsable, fall back to the
     * durable Postgres row.
     */
    private int availableQuantity(ReservationItem item) {
        String sku = item.getProduct().getSku();
        String stockRaw = redis.opsForValue().get(STOCK_PREFIX + sku);
        String reservedRaw = redis.opsForValue().get(RESERVED_PREFIX + sku);
        if (stockRaw != null && reservedRaw != null) {
            try {
                int stock = Integer.parseInt(stockRaw);
                int reserved = Integer.parseInt(reservedRaw);
                return Math.max(stock - reserved, AppNumbers.ZERO);
            } catch (NumberFormatException ex) {
                log.warn("Cannot parse Redis stock counters for SKU {}: stock='{}', reserved='{}'",
                        sku, stockRaw, reservedRaw);
            }
        }

        return inventoryRepository.findByProductId(item.getProduct().getId())
                .map(inv -> Math.max(inv.getTotalQuantity() - inv.getReservedQuantity(), AppNumbers.ZERO))
                .orElse(AppNumbers.ZERO);
    }

    private ReserveOutcome handleExistingPending(String orderId, List<ReservationLine> lines, Reservation existing) {
        if (sameItemSet(lines, existing.getItems())) {
            log.info("Reservation reserve idempotent replay: orderId={}, reservationId={}", orderId, existing.getId());
            return new ReserveOutcome(existing, true);
        }
        throw new DuplicateActiveReservationException(
                orderId, AppMessages.DUPLICATE_ACTIVE_RESERVATION_REASON_TEMPLATE.formatted(existing.getId()));
    }

    private static boolean sameItemSet(List<ReservationLine> requested, List<ReservationItem> existing) {
        return aggregateRequested(requested).equals(aggregateExisting(existing));
    }

    private static Map<String, Integer> aggregateRequested(List<ReservationLine> lines) {
        Map<String, Integer> totals = new HashMap<>();
        for (ReservationLine line : lines) {
            totals.merge(line.sku(), line.quantity(), Integer::sum);
        }
        return totals;
    }

    private static Map<String, Integer> aggregateExisting(List<ReservationItem> items) {
        Map<String, Integer> totals = new HashMap<>();
        for (ReservationItem item : items) {
            totals.merge(item.getProduct().getSku(), item.getQuantity(), Integer::sum);
        }
        return totals;
    }

    private Reservation loadOrThrow(Long id) {
        return reservationRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));
    }

    private static String classifyFailureResult(Exception e) {
        if (e instanceof InsufficientStockException) {
            return "insufficient_stock";
        }
        if (e instanceof ReservationNotFoundException) {
            return "not_found";
        }
        if (e instanceof DuplicateActiveReservationException) {
            return "duplicate_active";
        }
        if (e instanceof IllegalStateException) {
            return "state_conflict";
        }
        return "error";
    }
}
