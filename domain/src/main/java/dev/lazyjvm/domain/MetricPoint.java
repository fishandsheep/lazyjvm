package dev.lazyjvm.domain;

import java.time.Instant;
import java.util.Objects;

public record MetricPoint(
        Instant timestamp,
        MetricKey key,
        double value,
        MetricQuality quality,
        String source) {
    public MetricPoint {
        timestamp = Objects.requireNonNull(timestamp);
        key = Objects.requireNonNull(key);
        quality = Objects.requireNonNull(quality);
        source = Objects.requireNonNullElse(source, "unknown");
    }
}
