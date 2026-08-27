package dev.lazyjvm.tui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoubleClickTrackerTest {
    @Test
    void recognizesSameRowWithinWindowOnly() {
        DoubleClickTracker tracker = new DoubleClickTracker();

        assertFalse(tracker.register(3, 1_000_000_000L));
        assertTrue(tracker.register(3, 1_400_000_000L));
        assertFalse(tracker.register(3, 1_450_000_001L));
        assertFalse(tracker.register(4, 1_500_000_000L));
    }
}
