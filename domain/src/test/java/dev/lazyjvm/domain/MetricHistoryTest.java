package dev.lazyjvm.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricHistoryTest {
    @Test
    void remainsBoundedAndOrdered() {
        MetricHistory history = new MetricHistory(3);
        for (int i = 0; i < 5; i++) {
            Instant time = Instant.ofEpochSecond(i);
            MetricPoint point = new MetricPoint(time, MetricKey.THREADS_LIVE, i, MetricQuality.EXACT, "test");
            history.add(new MetricSnapshot(time, Map.of(MetricKey.THREADS_LIVE, point), List.of(), List.of(),
                    null, CapabilitySet.of(), Duration.ZERO, List.of()));
        }

        assertEquals(3, history.size());
        assertEquals(List.of(2.0, 3.0, 4.0), history.snapshot().stream()
                .map(sample -> sample.value(MetricKey.THREADS_LIVE)).toList());
    }
}
