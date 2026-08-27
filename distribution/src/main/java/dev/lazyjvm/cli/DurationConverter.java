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
        Matcher matcher = SIMPLE.matcher(value.strip().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) throw new CommandLine.TypeConversionException(
                "duration must use ms, s, m, or h, for example 1s or 60m");
        long amount = Long.parseLong(matcher.group(1));
        return switch (matcher.group(2)) {
            case "ms" -> Duration.ofMillis(amount);
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            default -> throw new CommandLine.TypeConversionException("unsupported duration unit");
        };
    }
}
