package com.taidev.warehouse.api.controller;

import com.taidev.warehouse.api.dto.PageResponse;
import com.taidev.warehouse.api.dto.ReservationRequests.CreateReservationRequest;
import com.taidev.warehouse.api.dto.ReservationResponses.ReservationView;
import com.taidev.warehouse.api.mapper.ReservationMapper;
import com.taidev.warehouse.service.ReservationService;
import com.taidev.warehouse.service.command.ReservationLine;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/reservations")
@RateLimiter(name = "reservationApi")
public class ReservationController {

    private static final Logger log = LoggerFactory.getLogger(ReservationController.class);

    private final ReservationService reservationService;
    private final ReservationMapper reservationMapper;

    public ReservationController(ReservationService reservationService, ReservationMapper reservationMapper) {
        this.reservationService = reservationService;
        this.reservationMapper = reservationMapper;
    }

    @PostMapping
    public ResponseEntity<ReservationView> create(@Valid @RequestBody CreateReservationRequest request) {
        log.info("API create reservation request received: orderId={}, itemCount={}",
                request.orderId(), request.items().size());
        List<ReservationLine> lines = request.items().stream()
                .map(i -> new ReservationLine(i.sku(), i.quantity()))
                .toList();
        var outcome = reservationService.reserveWithOutcome(request.orderId(), lines);
        var reservation = outcome.reservation();
        log.info("API create reservation completed: orderId={}, reservationId={}, status={}, replayed={}",
                request.orderId(), reservation.getId(), reservation.getStatus(), outcome.idempotentReplay());
        ResponseEntity.BodyBuilder response = outcome.idempotentReplay()
                ? ResponseEntity.ok()
                : ResponseEntity.created(URI.create("/reservations/" + reservation.getId()));
        return response
                .header("X-Reservation-Idempotent-Replay", String.valueOf(outcome.idempotentReplay()))
                .body(reservationMapper.toView(reservation));
    }

    @PostMapping("/{id}/confirm")
    @ResponseStatus(HttpStatus.OK)
    public ReservationView confirm(@PathVariable Long id) {
        log.info("API confirm reservation request received: reservationId={}", id);
        var reservation = reservationService.confirm(id);
        log.info("API confirm reservation completed: reservationId={}, status={}",
                reservation.getId(), reservation.getStatus());
        return reservationMapper.toView(reservation);
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.OK)
    public ReservationView cancel(@PathVariable Long id) {
        log.info("API cancel reservation request received: reservationId={}", id);
        var reservation = reservationService.cancel(id);
        log.info("API cancel reservation completed: reservationId={}, status={}",
                reservation.getId(), reservation.getStatus());
        return reservationMapper.toView(reservation);
    }

    @GetMapping
    public PageResponse<ReservationView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        log.info("API list reservations request received");
        var result = reservationService.listPage(PageRequest.of(page, size));
        log.info("API list reservations completed: page={}, size={}, count={}",
                result.getNumber(), result.getSize(), result.getNumberOfElements());
        return PageResponse.from(result, reservationMapper::toView);
    }

    @GetMapping("/{id}")
    public ReservationView get(@PathVariable Long id) {
        log.info("API get reservation request received: reservationId={}", id);
        var reservation = reservationService.get(id);
        log.info("API get reservation completed: reservationId={}, status={}",
                reservation.getId(), reservation.getStatus());
        return reservationMapper.toView(reservation);
    }
}
