package com.cmc.warehouse.api.advice;

import com.cmc.warehouse.api.dto.ApiErrorData;
import com.cmc.warehouse.api.dto.ApiErrorData.FieldError;
import com.cmc.warehouse.api.dto.ApiResponse;
import com.cmc.warehouse.domain.reservation.state.IllegalReservationTransitionException;
import com.cmc.warehouse.exception.DuplicateActiveReservationException;
import com.cmc.warehouse.exception.InsufficientStockException;
import com.cmc.warehouse.exception.ProductNotFoundException;
import com.cmc.warehouse.exception.ReservationNotFoundException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Single source of truth for HTTP error responses. Both UI and
 * service-to-service callers get the same {@link ApiResponse} envelope with
 * structured {@link ApiErrorData} in {@code data} and the failing URL in {@code url}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static String requestUrl(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        return query == null || query.isBlank() ? uri : uri + "?" + query;
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiResponse<ApiErrorData>> handleStock(InsufficientStockException ex,
                                                                  HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        HttpStatus.CONFLICT.value(),
                        ex.getMessage(),
                        ApiErrorData.of("INSUFFICIENT_STOCK"),
                        requestUrl(request)));
    }

    @ExceptionHandler(IllegalReservationTransitionException.class)
    public ResponseEntity<ApiResponse<ApiErrorData>> handleTransition(IllegalReservationTransitionException ex,
                                                                      HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        HttpStatus.CONFLICT.value(),
                        ex.getMessage(),
                        ApiErrorData.of("ILLEGAL_TRANSITION"),
                        requestUrl(request)));
    }

    @ExceptionHandler(DuplicateActiveReservationException.class)
    public ResponseEntity<ApiResponse<ApiErrorData>> handleDuplicateActiveReservation(
            DuplicateActiveReservationException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        HttpStatus.CONFLICT.value(),
                        ex.getMessage(),
                        ApiErrorData.of("DUPLICATE_ACTIVE_RESERVATION"),
                        requestUrl(request)));
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiResponse<ApiErrorData>> handleProductNotFound(ProductNotFoundException ex,
                                                                           HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                        HttpStatus.NOT_FOUND.value(),
                        ex.getMessage(),
                        ApiErrorData.of("PRODUCT_NOT_FOUND"),
                        requestUrl(request)));
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<ApiResponse<ApiErrorData>> handleReservationNotFound(ReservationNotFoundException ex,
                                                                               HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                        HttpStatus.NOT_FOUND.value(),
                        ex.getMessage(),
                        ApiErrorData.of("RESERVATION_NOT_FOUND"),
                        requestUrl(request)));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<ApiErrorData>> handleOptimisticLock(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        HttpStatus.CONFLICT.value(),
                        "Resource was modified by another transaction. Please retry.",
                        ApiErrorData.of("CONCURRENT_MODIFICATION"),
                        requestUrl(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ApiErrorData>> handleValidation(MethodArgumentNotValidException ex,
                                                                      HttpServletRequest request) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(
                        fe.getField(),
                        fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        HttpStatus.BAD_REQUEST.value(),
                        "Request body failed validation",
                        ApiErrorData.of("VALIDATION_ERROR", fieldErrors),
                        requestUrl(request)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<ApiErrorData>> handleIllegalArgument(IllegalArgumentException ex,
                                                                           HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        HttpStatus.BAD_REQUEST.value(),
                        ex.getMessage(),
                        ApiErrorData.of("BAD_REQUEST"),
                        requestUrl(request)));
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ApiResponse<ApiErrorData>> handleRateLimitExceeded(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        "Too many requests. Please retry later.",
                        ApiErrorData.of("RATE_LIMIT_EXCEEDED"),
                        requestUrl(request)));
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiResponse<ApiErrorData>> handleCircuitOpen(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "Service is temporarily unavailable. Please retry later.",
                        ApiErrorData.of("CIRCUIT_OPEN"),
                        requestUrl(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ApiErrorData>> handleUnexpected(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Unexpected server error",
                        ApiErrorData.of("INTERNAL_ERROR"),
                        requestUrl(request)));
    }
}
