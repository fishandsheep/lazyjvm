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
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        if (days > 0) return "%dd %02dh".formatted(days, hours);
        if (hours > 0) return "%dh %02dm".formatted(hours, minutes);
        return "%dm %02ds".formatted(minutes, seconds);
    }

    static String number(double value) {
        return !Double.isFinite(value) || value < 0 ? "n/a" : String.format(Locale.ROOT, "%,.0f", value);
    }

    static String bar(double fraction, int width, boolean ascii) {
        fraction = Math.max(0, Math.min(1, fraction));
        int filled = (int) Math.round(width * fraction);
        char on = ascii ? '#' : '█';
        char off = ascii ? '.' : '░';
        return Character.toString(on).repeat(filled) + Character.toString(off).repeat(Math.max(0, width - filled));
    }
}
