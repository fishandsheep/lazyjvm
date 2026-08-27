package dev.lazyjvm.domain;

import java.util.Map;

public record ThreadSnapshot(
        int live,
        int daemon,
        int peak,
        Map<Thread.State, Integer> states,
        long[] deadlockedThreadIds) {
    public ThreadSnapshot {
        states = states == null ? Map.of() : Map.copyOf(states);
        deadlockedThreadIds = deadlockedThreadIds == null ? new long[0] : deadlockedThreadIds.clone();
    }

    @Override
    public long[] deadlockedThreadIds() {
        return deadlockedThreadIds.clone();
    }
}
