package dev.lazyjvm.tui;

import java.time.Duration;
import java.util.Locale;

final class Format {
    private static final String[] BYTE_UNITS = {"B", "KiB", "MiB", "GiB", "TiB"};

    private Format() {}

    static String bytes(double value) {
        if (!Double.isFinite(value) || value < 0) return "n/a";
        int unit = 0;
        while (value >= 1024 && unit < BYTE_UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.ROOT, value >= 100 || unit == 0 ? "%.0f %s" : "%.1f %s", value, BYTE_UNITS[unit]);
    }

    static String percent(double value) {
        return !Double.isFinite(value) || value < 0 ? "n/a" : String.format(Locale.ROOT, "%.1f%%", value);
    }

    static String duration(double millis) {
        if (!Double.isFinite(millis) || millis < 0) return "n/a";
        Duration duration = Duration.ofMillis((long) millis);
        long totalSeconds = duration.getSeconds();
        long days = totalSeconds / 86_400;
        long hours = (totalSeconds % 86_400) / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;
        if (days > 0) return String.format(Locale.ROOT, "%dd %02dh", days, hours);
        if (hours > 0) return String.format(Locale.ROOT, "%dh %02dm", hours, minutes);
        return String.format(Locale.ROOT, "%dm %02ds", minutes, seconds);
    }

    static String number(double value) {
        return !Double.isFinite(value) || value < 0 ? "n/a" : String.format(Locale.ROOT, "%,.0f", value);
    }

    static String bar(double fraction, int width, boolean ascii) {
        fraction = Math.max(0, Math.min(1, fraction));
        int filled = (int) Math.round(width * fraction);
        char on = ascii ? '#' : '█';
        char off = ascii ? '.' : '░';
        return repeat(on, filled) + repeat(off, Math.max(0, width - filled));
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(Math.max(0, count));
        for (int index = 0; index < count; index++) result.append(value);
        return result.toString();
    }
}
