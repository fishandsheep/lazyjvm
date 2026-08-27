package dev.lazyjvm.domain;

import java.util.Objects;

public final class GcSnapshot {
    private final String name;
    private final long collectionCount;
    private final long collectionTimeMillis;
    private final String pools;

    public GcSnapshot(String name, long collectionCount, long collectionTimeMillis, String pools) {
        this.name = name;
        this.collectionCount = collectionCount;
        this.collectionTimeMillis = collectionTimeMillis;
        this.pools = pools;
    }

    public String name() { return name; }
    public long collectionCount() { return collectionCount; }
    public long collectionTimeMillis() { return collectionTimeMillis; }
    public String pools() { return pools; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GcSnapshot)) return false;
        GcSnapshot that = (GcSnapshot) other;
        return collectionCount == that.collectionCount && collectionTimeMillis == that.collectionTimeMillis
                && Objects.equals(name, that.name) && Objects.equals(pools, that.pools);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, collectionCount, collectionTimeMillis, pools);
    }

    @Override
    public String toString() {
        return "GcSnapshot[name=" + name + ", collectionCount=" + collectionCount
                + ", collectionTimeMillis=" + collectionTimeMillis + ", pools=" + pools + "]";
    }
}
