package dev.lazyjvm.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MetricSnapshot(
        Instant timestamp,
        Map<MetricKey, MetricPoint> metrics,
        List<MemoryPoolSnapshot> memoryPools,
        List<GcSnapshot> garbageCollectors,
        ThreadSnapshot threads,
        CapabilitySet capabilities,
        Duration collectionLatency,
        List<String> warnings) {
    public MetricSnapshot {
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        memoryPools = memoryPools == null ? List.of() : List.copyOf(memoryPools);
        garbageCollectors = garbageCollectors == null ? List.of() : List.copyOf(garbageCollectors);
        capabilities = capabilities == null ? new CapabilitySet(null) : capabilities;
        collectionLatency = collectionLatency == null ? Duration.ZERO : collectionLatency;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public double value(MetricKey key) {
        MetricPoint point = metrics.get(key);
        return point == null ? Double.NaN : point.value();
    }
}
