package dev.lazyjvm.tui;

import dev.lazyjvm.domain.CommandResult;
import dev.lazyjvm.domain.DiagnosticCommand;

import java.time.Instant;
import java.util.List;

/** One invocation, including repeated invocations of same command. */
record CommandExecution(long id, Instant startedAt, Instant finishedAt, DiagnosticCommand command,
                        List<String> arguments, CommandResult result) {
    CommandExecution {
        startedAt = startedAt == null ? Instant.now() : startedAt;
        finishedAt = finishedAt == null ? startedAt : finishedAt;
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    boolean running() { return result == null; }

    CommandExecution finished(CommandResult value) {
        return new CommandExecution(id, startedAt, Instant.now(), command, arguments, value);
    }
}
