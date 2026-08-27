package dev.lazyjvm.cli;

import picocli.CommandLine;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DurationConverter implements CommandLine.ITypeConverter<Duration> {
    private static final Pattern SIMPLE = Pattern.compile("([1-9][0-9]*)(ms|s|m|h)", Pattern.CASE_INSENSITIVE);

    @Override
    public Duration convert(String value) {
        Matcher matcher = SIMPLE.matcher(value.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) throw new CommandLine.TypeConversionException(
                "duration must use ms, s, m, or h, for example 1s or 60m");
        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);
        if ("ms".equals(unit)) return Duration.ofMillis(amount);
        if ("s".equals(unit)) return Duration.ofSeconds(amount);
        if ("m".equals(unit)) return Duration.ofMinutes(amount);
        if ("h".equals(unit)) return Duration.ofHours(amount);
        throw new CommandLine.TypeConversionException("unsupported duration unit");
    }
}
