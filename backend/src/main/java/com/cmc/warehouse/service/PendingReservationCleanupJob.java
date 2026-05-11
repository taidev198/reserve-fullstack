package com.cmc.warehouse.service;

import com.cmc.warehouse.domain.reservation.ReservationStatus;
import com.cmc.warehouse.exception.ReservationNotFoundException;
import com.cmc.warehouse.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Cron job that expires stale PENDING reservations.
 *
 * Expiration path intentionally delegates to ReservationService#cancel so
 * Postgres CAS + Redis release logic remains in one place.
 */
@Component
public class PendingReservationCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(PendingReservationCleanupJob.class);

    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;
    private final int ttlSeconds;
    private final int batchSize;

    public PendingReservationCleanupJob(ReservationRepository reservationRepository,
                                        ReservationService reservationService,
                                        @Value("${app.reservation.pending-cleanup.ttl-seconds:900}") int ttlSeconds,
                                        @Value("${app.reservation.pending-cleanup.batch-size:200}") int batchSize) {
        this.reservationRepository = reservationRepository;
        this.reservationService = reservationService;
        this.ttlSeconds = ttlSeconds;
        this.batchSize = batchSize;
    }

    @Scheduled(
            cron = "${app.reservation.pending-cleanup.cron:0 */1 * * * *}",
            zone = "${app.reservation.pending-cleanup.zone:UTC}"
    )
    public void cleanupExpiredPendingReservations() {
        Instant cutoff = Instant.now().minusSeconds(ttlSeconds);
        List<Long> staleIds = reservationRepository.findIdsByStatusAndCreatedAtBefore(
                ReservationStatus.PENDING,
                cutoff,
                PageRequest.of(0, batchSize)
        );

        if (staleIds.isEmpty()) {
            log.debug("Pending reservation cleanup found no stale rows (ttlSeconds={})", ttlSeconds);
            return;
        }

        int cancelled = 0;
        for (Long id : staleIds) {
            try {
                reservationService.cancel(id);
                cancelled++;
            } catch (ReservationNotFoundException notFound) {
                log.warn("Pending cleanup skipped missing reservation id={}", id);
            } catch (RuntimeException ex) {
                log.error("Pending cleanup failed for reservation id={}: {}", id, ex.toString(), ex);
            }
        }

        log.info("Pending cleanup completed: stale={}, cancelled={}, ttlSeconds={}",
                staleIds.size(), cancelled, ttlSeconds);
    }
}
