package dev.lazyjvm.tui;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartTest {
    @Test
    void bucketsLongHistoryToVisibleWidth() {
        assertEquals(Arrays.asList(1.5, 3.5), Chart.bucket(Arrays.asList(1.0, 2.0, 3.0, 4.0), 2));
    }

    @Test
    void preservesMissingSamplesAsVisibleGaps() {
        List<Double> values = Chart.bucket(Arrays.asList(1.0, Double.NaN, 3.0, 4.0), 2);
        assertTrue(Double.isNaN(values.get(0)));
        assertEquals(3.5, values.get(1));
    }

    @Test
    void timeSeriesIncludesAxesAndTimeLabelsWithoutFillingGaps() {
        Canvas canvas = new Canvas(48, 10, true);
        Chart.timeSeries(canvas, 0, 0, 48, 10,
                Arrays.asList(new Chart.Series("cpu", Arrays.asList(10.0, Double.NaN, 30.0), Style.CYAN)),
                Arrays.asList(Instant.ofEpochSecond(0), Instant.ofEpochSecond(1), Instant.ofEpochSecond(2)),
                0, 100, "%", true);

        String frame = canvas.render(false);
        assertTrue(frame.matches("(?s).*\\d{2}:\\d{2}:\\d{2}.*"));
        assertTrue(frame.contains("100 %") || frame.contains("100"));
        assertTrue(frame.contains("|"));
    }
}
