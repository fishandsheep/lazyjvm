package dev.lazyjvm.domain;

import java.util.Objects;

public final class MetricKey {
    private final String id;
    private final String label;
    private final String unit;

    public MetricKey(String id, String label, String unit) {
        this.id = Objects.requireNonNull(id);
        this.label = Objects.requireNonNull(label);
        this.unit = unit == null ? "" : unit;
    }

    public String id() { return id; }
    public String label() { return label; }
    public String unit() { return unit; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MetricKey)) return false;
        MetricKey that = (MetricKey) other;
        return id.equals(that.id) && label.equals(that.label) && unit.equals(that.unit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, label, unit);
    }

    @Override
    public String toString() {
        return "MetricKey[id=" + id + ", label=" + label + ", unit=" + unit + "]";
    }

    public static final MetricKey PROCESS_CPU = new MetricKey("process.cpu", "Process CPU", "%");
    public static final MetricKey SYSTEM_CPU = new MetricKey("system.cpu", "System CPU", "%");
    public static final MetricKey HEAP_USED = new MetricKey("heap.used", "Heap used", "bytes");
    public static final MetricKey HEAP_COMMITTED = new MetricKey("heap.committed", "Heap committed", "bytes");
    public static final MetricKey HEAP_MAX = new MetricKey("heap.max", "Heap max", "bytes");
    public static final MetricKey NON_HEAP_USED = new MetricKey("nonheap.used", "Non-heap used", "bytes");
    public static final MetricKey THREADS_LIVE = new MetricKey("threads.live", "Live threads", "threads");
    public static final MetricKey THREADS_PEAK = new MetricKey("threads.peak", "Peak threads", "threads");
    public static final MetricKey CLASSES_LOADED = new MetricKey("classes.loaded", "Loaded classes", "classes");
    public static final MetricKey GC_COUNT = new MetricKey("gc.count", "GC count", "collections");
    public static final MetricKey GC_TIME = new MetricKey("gc.time", "GC time", "ms");
    public static final MetricKey UPTIME = new MetricKey("runtime.uptime", "Uptime", "ms");
}
