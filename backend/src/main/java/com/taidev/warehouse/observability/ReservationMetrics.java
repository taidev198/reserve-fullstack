package com.taidev.warehouse.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class ReservationMetrics {

    private final MeterRegistry meterRegistry;

    public ReservationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer.Sample startLatencySample() {
        return Timer.start(meterRegistry);
    }

    public void record(String operation, String result, Timer.Sample sample) {
        Counter.builder("warehouse.reservation.operations")
                .description("Reservation operation outcomes")
                .tag("operation", operation)
                .tag("result", result)
                .register(meterRegistry)
                .increment();

        Timer.builder("warehouse.reservation.operation.duration")
                .description("Reservation operation duration")
                .publishPercentileHistogram()
                .tag("operation", operation)
                .tag("result", result)
                .register(meterRegistry);

        sample.stop(meterRegistry.timer(
                "warehouse.reservation.operation.duration",
                "operation", operation,
                "result", result));
    }

    public void record(String operation, String result, long elapsedNanos) {
        Counter.builder("warehouse.reservation.operations")
                .description("Reservation operation outcomes")
                .tag("operation", operation)
                .tag("result", result)
                .register(meterRegistry)
                .increment();

        Timer.builder("warehouse.reservation.operation.duration")
                .description("Reservation operation duration")
                .publishPercentileHistogram()
                .tag("operation", operation)
                .tag("result", result)
                .register(meterRegistry)
                .record(elapsedNanos, TimeUnit.NANOSECONDS);
    }
}
