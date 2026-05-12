package com.taidev.warehouse.service;

import com.taidev.warehouse.common.AppMessages;
import com.taidev.warehouse.domain.product.Product;
import com.taidev.warehouse.domain.reservation.Reservation;
import com.taidev.warehouse.domain.reservation.ReservationItem;
import com.taidev.warehouse.exception.ProductNotFoundException;
import com.taidev.warehouse.repository.ProductRepository;
import com.taidev.warehouse.service.command.ReservationLine;
import com.taidev.warehouse.validation.ReservationCreateValidator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory pattern — encapsulates the (non-trivial) construction of a
 * {@link Reservation} aggregate:
 *
 *   1. Validate the request shape (non-empty items, positive quantities).
 *   2. Resolve {@link Product}s from SKUs in a single batch query.
 *   3. Reject unknown SKUs before any stock is touched.
 *   4. Build the aggregate with its child items wired up correctly.
 *
 * Centralising this here keeps {@link ReservationService} focused on
 * the transactional / locking concerns and makes it trivial to swap in
 * a different construction strategy later (e.g. a "draft" reservation
 * factory for a checkout flow).
 */
@Component
public class ReservationFactory {

    private final ProductRepository productRepository;
    private final ReservationCreateValidator reservationCreateValidator;

    public ReservationFactory(ProductRepository productRepository, ReservationCreateValidator reservationCreateValidator) {
        this.productRepository = productRepository;
        this.reservationCreateValidator = reservationCreateValidator;
    }

    public Reservation create(String orderId, List<ReservationLine> lines) {
        validate(orderId, lines);

        List<String> skus = lines.stream().map(ReservationLine::sku).distinct().toList();
        Map<String, Product> bySku = productRepository.findBySkuIn(skus).stream()
                .collect(Collectors.toMap(Product::getSku, Function.identity()));

        for (String sku : skus) {
            if (!bySku.containsKey(sku)) throw new ProductNotFoundException(sku);
        }

        Reservation reservation = new Reservation(orderId);
        for (ReservationLine line : lines) {
            reservation.addItem(new ReservationItem(reservation, bySku.get(line.sku()), line.quantity()));
        }
        return reservation;
    }

    private void validate(String orderId, List<ReservationLine> lines) {
        reservationCreateValidator.validate(orderId, lines);
    }
}
