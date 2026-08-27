package dev.lazyjvm.tui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class Chart {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withLocale(Locale.ROOT).withZone(ZoneId.systemDefault());

    static final class Series {
        private final String label;
        private final List<Double> values;
        private final Style style;

        Series(String label, List<Double> values, Style style) {
            this.label = label == null ? "" : label;
            this.values = values == null ? Collections.<Double>emptyList()
                    : Collections.unmodifiableList(new ArrayList<Double>(values));
            this.style = style == null ? Style.CYAN : style;
        }

        String label() { return label; }
        List<Double> values() { return values; }
        Style style() { return style; }
    }

    private Chart() {}

    /** Draws multiple series with explicit Y axis and time ticks. Missing values remain gaps. */
    static void timeSeries(Canvas canvas, int x, int y, int width, int height,
                           List<Series> series, List<Instant> timestamps,
                           double min, double max, String unit, boolean ascii) {
        if (width < 12 || height < 5) return;
        int axisX = x + 6;
        int axisY = y + height - 2;
        int plotWidth = Math.max(1, width - 8);
        int plotHeight = Math.max(1, height - 4);
        if (!Double.isFinite(min)) min = 0;
        if (!Double.isFinite(max) || max <= min) max = min + 1;
        canvas.text(x, y, formatAxis(max, unit), Style.MUTED);
        canvas.text(x, axisY - plotHeight / 2, formatAxis((max + min) / 2, unit), Style.MUTED);
        canvas.text(x, axisY, formatAxis(min, unit), Style.MUTED);
        for (int row = 0; row < plotHeight; row++) {
            canvas.text(axisX, y + row, ascii ? "|" : "│", Style.PANEL);
        }
        for (int column = 0; column < plotWidth; column++) {
            canvas.text(axisX + column, axisY, ascii ? "-" : "─", Style.PANEL);
        }
        canvas.text(axisX, axisY, ascii ? "+" : "└", Style.PANEL);
        drawTimeTicks(canvas, axisX, axisY + 1, plotWidth, timestamps, ascii);

        for (Series value : series) {
            List<Double> bucketed = bucket(value.values(), plotWidth);
            int previousX = -1;
            int previousY = -1;
            for (int column = 0; column < bucketed.size(); column++) {
                double current = bucketed.get(column);
                if (!Double.isFinite(current)) {
                    previousX = -1;
                    previousY = -1;
                    continue;
                }
                int pointX = axisX + column;
                int pointY = axisY - 1 - (int) Math.round((current - min) / (max - min) * (plotHeight - 1));
                pointY = Math.max(y, Math.min(axisY - 1, pointY));
                canvas.text(pointX, pointY, ascii ? "*" : "•", value.style());
                if (previousX >= 0) drawSegment(canvas, previousX, previousY, pointX, pointY, value.style(), ascii);
                previousX = pointX;
                previousY = pointY;
            }
        }
    }

    static void multiSeries(Canvas canvas, int x, int y, int width, int height,
                            List<Series> series, List<Instant> timestamps,
                            double min, double max, String unit, boolean ascii) {
        timeSeries(canvas, x, y, width, height, series, timestamps, min, max, unit, ascii);
    }

    private static void drawSegment(Canvas canvas, int x1, int y1, int x2, int y2,
                                    Style style, boolean ascii) {
        int distance = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int step = 1; step < distance; step++) {
            int x = x1 + (x2 - x1) * step / distance;
            int y = y1 + (y2 - y1) * step / distance;
            canvas.text(x, y, ascii ? "." : "·", style);
        }
    }

    private static void drawTimeTicks(Canvas canvas, int x, int y, int width,
                                      List<Instant> timestamps, boolean ascii) {
        if (timestamps == null || timestamps.isEmpty() || width < 8) return;
        Instant first = timestamps.get(0);
        Instant last = timestamps.get(timestamps.size() - 1);
        canvas.text(x, y, TIME.format(first), Style.MUTED);
        String end = TIME.format(last);
        canvas.text(x + Math.max(0, width - Canvas.displayWidth(end)), y, end, Style.MUTED);
        if (width >= 22) {
            Instant middle = timestamps.get(timestamps.size() / 2);
            String label = TIME.format(middle);
            canvas.text(x + Math.max(0, (width - Canvas.displayWidth(label)) / 2), y, label, Style.MUTED);
        }
    }

    private static String formatAxis(double value, String unit) {
        String number;
        if (!Double.isFinite(value)) number = "n/a";
        else if (Math.abs(value) >= 1024 * 1024) number = Format.bytes(value);
        else if (Math.abs(value) >= 1000) number = String.format(Locale.ROOT, "%.0f", value);
        else if (Math.abs(value) >= 10) number = String.format(Locale.ROOT, "%.0f", value);
        else number = String.format(Locale.ROOT, "%.1f", value);
        return Canvas.crop(number + (unit == null || unit.trim().isEmpty() ? "" : " " + unit), 6);
    }

    static List<Double> bucket(List<Double> source, int width) {
        if (source == null || source.isEmpty() || width <= 0) return Collections.emptyList();
        if (source.size() <= width) return Collections.unmodifiableList(new ArrayList<Double>(source));
        List<Double> result = new ArrayList<>(width);
        for (int column = 0; column < width; column++) {
            int from = column * source.size() / width;
            int to = Math.max(from + 1, (column + 1) * source.size() / width);
            double sum = 0;
            int count = 0;
            boolean missing = false;
            for (int index = from; index < Math.min(to, source.size()); index++) {
                double value = source.get(index);
                if (Double.isFinite(value)) {
                    sum += value;
                    count++;
                } else missing = true;
            }
            result.add(missing || count == 0 ? Double.NaN : sum / count);
        }
        return result;
    }
}
