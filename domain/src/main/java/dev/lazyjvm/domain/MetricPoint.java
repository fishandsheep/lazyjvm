package dev.lazyjvm.domain;

import java.time.Instant;
import java.util.Objects;

public final class MetricPoint {
    private final Instant timestamp;
    private final MetricKey key;
    private final double value;
    private final MetricQuality quality;
    private final String source;

    public MetricPoint(Instant timestamp, MetricKey key, double value, MetricQuality quality, String source) {
        this.timestamp = Objects.requireNonNull(timestamp);
        this.key = Objects.requireNonNull(key);
        this.value = value;
        this.quality = Objects.requireNonNull(quality);
        this.source = source == null ? "unknown" : source;
    }

    public Instant timestamp() { return timestamp; }
    public MetricKey key() { return key; }
    public double value() { return value; }
    public MetricQuality quality() { return quality; }
    public String source() { return source; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MetricPoint)) return false;
        MetricPoint that = (MetricPoint) other;
        return Double.compare(value, that.value) == 0 && timestamp.equals(that.timestamp)
                && key.equals(that.key) && quality == that.quality && source.equals(that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, key, value, quality, source);
    }

    @Override
    public String toString() {
        return "MetricPoint[timestamp=" + timestamp + ", key=" + key + ", value=" + value
                + ", quality=" + quality + ", source=" + source + "]";
    }
}
