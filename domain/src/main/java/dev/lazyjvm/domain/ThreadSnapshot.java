package dev.lazyjvm.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class ThreadSnapshot {
    private final int live;
    private final int daemon;
    private final int peak;
    private final Map<Thread.State, Integer> states;
    private final long[] deadlockedThreadIds;

    public ThreadSnapshot(int live, int daemon, int peak, Map<Thread.State, Integer> states,
                          long[] deadlockedThreadIds) {
        this.live = live;
        this.daemon = daemon;
        this.peak = peak;
        this.states = states == null ? Collections.<Thread.State, Integer>emptyMap()
                : Collections.unmodifiableMap(new HashMap<Thread.State, Integer>(states));
        this.deadlockedThreadIds = deadlockedThreadIds == null ? new long[0] : deadlockedThreadIds.clone();
    }

    public int live() { return live; }
    public int daemon() { return daemon; }
    public int peak() { return peak; }
    public Map<Thread.State, Integer> states() { return states; }
    public long[] deadlockedThreadIds() {
        return deadlockedThreadIds.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ThreadSnapshot)) return false;
        ThreadSnapshot that = (ThreadSnapshot) other;
        return live == that.live && daemon == that.daemon && peak == that.peak
                && states.equals(that.states)
                && java.util.Arrays.equals(deadlockedThreadIds, that.deadlockedThreadIds);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(live, daemon, peak, states)
                + java.util.Arrays.hashCode(deadlockedThreadIds);
    }

    @Override
    public String toString() {
        return "ThreadSnapshot[live=" + live + ", daemon=" + daemon + ", peak=" + peak
                + ", states=" + states + ", deadlockedThreadIds="
                + java.util.Arrays.toString(deadlockedThreadIds) + "]";
    }
}
