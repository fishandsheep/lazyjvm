package dev.lazyjvm.jvm;

import dev.lazyjvm.domain.CommandImpact;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JcmdCatalogTest {
    @Test
    void parsesOnlyCommandRowsAndClassifiesImpact() {
        String output = """
                1234:
                The following commands are available:
                Compiler.codecache
                GC.heap_dump
                GC.heap_info
                Thread.print
                help
                """;

        var commands = new JcmdCatalog().parse(output);
        assertEquals(4, commands.size());
        assertTrue(commands.stream().anyMatch(command -> command.name().equals("GC.heap_dump")
                && command.impact() == CommandImpact.HIGH));
        assertEquals(CommandImpact.HIGH, JcmdCatalog.impact("JFR.start"));
    }

    @Test
    void toleratesLocalizedNoiseUnknownCommandsAndDuplicates() {
        String output = """
                进程 99：
                VM.version
                Future.experimental_command
                VM.version
                命令不可用
                """;

        var commands = new JcmdCatalog().parse(output);
        assertEquals(2, commands.size());
        assertTrue(commands.stream().anyMatch(command -> command.name().equals("Future.experimental_command")
                && command.impact() == CommandImpact.LOW));
    }
}
