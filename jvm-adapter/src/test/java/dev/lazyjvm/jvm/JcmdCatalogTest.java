package dev.lazyjvm.jvm;

import dev.lazyjvm.domain.CommandImpact;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JcmdCatalogTest {
    @Test
    void parsesOnlyCommandRowsAndClassifiesImpact() {
        String output = "1234:\n"
                + "The following commands are available:\n"
                + "Compiler.codecache\n"
                + "GC.heap_dump\n"
                + "GC.heap_info\n"
                + "Thread.print\n"
                + "help\n";

        java.util.List<dev.lazyjvm.domain.DiagnosticCommand> commands = new JcmdCatalog().parse(output);
        assertEquals(4, commands.size());
        assertTrue(commands.stream().anyMatch(command -> command.name().equals("GC.heap_dump")
                && command.impact() == CommandImpact.HIGH));
        assertEquals(CommandImpact.HIGH, JcmdCatalog.impact("JFR.start"));
    }

    @Test
    void toleratesLocalizedNoiseUnknownCommandsAndDuplicates() {
        String output = "进程 99：\n"
                + "VM.version\n"
                + "Future.experimental_command\n"
                + "VM.version\n"
                + "命令不可用\n";

        java.util.List<dev.lazyjvm.domain.DiagnosticCommand> commands = new JcmdCatalog().parse(output);
        assertEquals(2, commands.size());
        assertTrue(commands.stream().anyMatch(command -> command.name().equals("Future.experimental_command")
                && command.impact() == CommandImpact.LOW));
    }
}
