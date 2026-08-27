package dev.lazyjvm.domain;

public record MemoryPoolSnapshot(String name, String type, long used, long committed, long max) {
    public double utilization() {
        long ceiling = max > 0 ? max : committed;
        return ceiling <= 0 ? 0 : Math.min(1.0, (double) used / ceiling);
    }
}
