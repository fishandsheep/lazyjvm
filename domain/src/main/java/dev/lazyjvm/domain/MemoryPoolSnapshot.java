package dev.lazyjvm.domain;

import java.util.Objects;

public final class MemoryPoolSnapshot {
    private final String name;
    private final String type;
    private final long used;
    private final long committed;
    private final long max;

    public MemoryPoolSnapshot(String name, String type, long used, long committed, long max) {
        this.name = name;
        this.type = type;
        this.used = used;
        this.committed = committed;
        this.max = max;
    }

    public String name() { return name; }
    public String type() { return type; }
    public long used() { return used; }
    public long committed() { return committed; }
    public long max() { return max; }

    public double utilization() {
        long ceiling = max > 0 ? max : committed;
        return ceiling <= 0 ? 0 : Math.min(1.0, (double) used / ceiling);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MemoryPoolSnapshot)) return false;
        MemoryPoolSnapshot that = (MemoryPoolSnapshot) other;
        return used == that.used && committed == that.committed && max == that.max
                && Objects.equals(name, that.name) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, used, committed, max);
    }

    @Override
    public String toString() {
        return "MemoryPoolSnapshot[name=" + name + ", type=" + type + ", used=" + used
                + ", committed=" + committed + ", max=" + max + "]";
    }
}
