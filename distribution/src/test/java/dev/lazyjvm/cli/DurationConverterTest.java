package dev.lazyjvm.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationConverterTest {
    @Test
    void parsesDocumentedUnits() {
        DurationConverter converter = new DurationConverter();
        assertEquals(Duration.ofMillis(250), converter.convert("250ms"));
        assertEquals(Duration.ofSeconds(1), converter.convert("1s"));
        assertEquals(Duration.ofMinutes(60), converter.convert("60m"));
    }

    @Test
    void rejectsAmbiguousValues() {
        assertThrows(CommandLine.TypeConversionException.class, () -> new DurationConverter().convert("1"));
    }

    @Test
    void acceptsLanguageOptionAndAlias() {
        CommandLine commandLine = new CommandLine(new LazyJvmCommand());
        assertEquals("en", commandLine.parseArgs("--language", "en").matchedOptionValue("--language", ""));

        CommandLine alias = new CommandLine(new LazyJvmCommand());
        assertEquals("en", alias.parseArgs("--lang", "en").matchedOptionValue("--language", ""));
    }
}
