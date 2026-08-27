package dev.lazyjvm.jvm;

import dev.lazyjvm.domain.CommandImpact;
import dev.lazyjvm.domain.CommandRequest;
import dev.lazyjvm.domain.CommandResult;
import dev.lazyjvm.domain.DiagnosticCommand;
import dev.lazyjvm.domain.JdkIdentity;
import dev.lazyjvm.domain.TargetJvm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JcmdExecutorTest {
    @TempDir Path temporary;

    @Test
    void passesUntrustedArgumentsWithoutShellInterpretationAndCapsOutput() throws Exception {
        Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
        Path bin = Files.createDirectories(temporary.resolve("bin"));
        Path executable = bin.resolve("jcmd");
        Files.write(executable, "#!/bin/sh\nprintf '%s\\n' \"$@\"\nprintf '%0100d\\n' 0\n"
                .getBytes(StandardCharsets.UTF_8));
        Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"));
        TargetJvm target = new TargetJvm(1234L, "Target", "Target", "", "user", Instant.EPOCH,
                new JdkIdentity(temporary, "21", "test", true), false);
        DiagnosticCommand command = new DiagnosticCommand("VM.version", "version", CommandImpact.LOW,
                Collections.<String>emptyList());
        Path marker = temporary.resolve("injected");
        String hostile = "$(touch " + marker + "); echo injected";

        CommandResult result = new JcmdExecutor(temporary, 64).execute(target,
                new CommandRequest(target.pid(), command, Collections.singletonList(hostile), Duration.ofSeconds(2)));

        assertTrue(result.output().contains("VM.version"));
        assertTrue(result.truncated());
        assertFalse(Files.exists(marker));
    }
}
