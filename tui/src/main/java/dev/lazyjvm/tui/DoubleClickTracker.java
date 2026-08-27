package dev.lazyjvm.tui;

/** Detects two clicks on the same row within a terminal-friendly interval. */
final class DoubleClickTracker {
    private static final long WINDOW_NANOS = 450_000_000L;

    private int lastIndex = -1;
    private long lastClickNanos = Long.MIN_VALUE;

    boolean register(int index, long nowNanos) {
        boolean doubleClick = index == lastIndex
                && nowNanos >= lastClickNanos
                && nowNanos - lastClickNanos <= WINDOW_NANOS;
        if (doubleClick) reset();
        else {
            lastIndex = index;
            lastClickNanos = nowNanos;
        }
        return doubleClick;
    }

    void reset() {
        lastIndex = -1;
        lastClickNanos = Long.MIN_VALUE;
    }
}
