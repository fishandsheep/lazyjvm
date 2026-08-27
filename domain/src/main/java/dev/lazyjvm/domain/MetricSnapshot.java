package dev.lazyjvm.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MetricSnapshot {
    private final Instant timestamp;
    private final Map<MetricKey, MetricPoint> metrics;
    private final List<MemoryPoolSnapshot> memoryPools;
    private final List<GcSnapshot> garbageCollectors;
    private final ThreadSnapshot threads;
    private final CapabilitySet capabilities;
    private final Duration collectionLatency;
    private final List<String> warnings;

    public MetricSnapshot(Instant timestamp, Map<MetricKey, MetricPoint> metrics,
                          List<MemoryPoolSnapshot> memoryPools, List<GcSnapshot> garbageCollectors,
                          ThreadSnapshot threads, CapabilitySet capabilities, Duration collectionLatency,
                          List<String> warnings) {
        this.timestamp = timestamp;
        this.metrics = metrics == null ? Collections.<MetricKey, MetricPoint>emptyMap()
                : Collections.unmodifiableMap(new HashMap<MetricKey, MetricPoint>(metrics));
        this.memoryPools = immutableList(memoryPools);
        this.garbageCollectors = immutableList(garbageCollectors);
        this.threads = threads;
        this.capabilities = capabilities == null ? new CapabilitySet(null) : capabilities;
        this.collectionLatency = collectionLatency == null ? Duration.ZERO : collectionLatency;
        this.warnings = immutableList(warnings);
    }

    private static <T> List<T> immutableList(List<T> source) {
        return source == null ? Collections.<T>emptyList()
                : Collections.unmodifiableList(new ArrayList<T>(source));
    }

    public Instant timestamp() { return timestamp; }
    public Map<MetricKey, MetricPoint> metrics() { return metrics; }
    public List<MemoryPoolSnapshot> memoryPools() { return memoryPools; }
    public List<GcSnapshot> garbageCollectors() { return garbageCollectors; }
    public ThreadSnapshot threads() { return threads; }
    public CapabilitySet capabilities() { return capabilities; }
    public Duration collectionLatency() { return collectionLatency; }
    public List<String> warnings() { return warnings; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MetricSnapshot)) return false;
        MetricSnapshot that = (MetricSnapshot) other;
        return Objects.equals(timestamp, that.timestamp) && metrics.equals(that.metrics)
                && memoryPools.equals(that.memoryPools) && garbageCollectors.equals(that.garbageCollectors)
                && Objects.equals(threads, that.threads) && capabilities.equals(that.capabilities)
                && collectionLatency.equals(that.collectionLatency) && warnings.equals(that.warnings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, metrics, memoryPools, garbageCollectors, threads, capabilities,
                collectionLatency, warnings);
    }

    @Override
    public String toString() {
        return "MetricSnapshot[timestamp=" + timestamp + ", metrics=" + metrics + ", memoryPools="
                + memoryPools + ", garbageCollectors=" + garbageCollectors + ", threads=" + threads
                + ", capabilities=" + capabilities + ", collectionLatency=" + collectionLatency
                + ", warnings=" + warnings + "]";
    }

    public double value(MetricKey key) {
        MetricPoint point = metrics.get(key);
        return point == null ? Double.NaN : point.value();
    }
}
