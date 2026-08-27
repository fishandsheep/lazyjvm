package dev.lazyjvm.tui;

import dev.lazyjvm.domain.CommandResult;
import dev.lazyjvm.domain.DiagnosticCommand;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** One invocation, including repeated invocations of same command. */
final class CommandExecution {
    private final long id;
    private final Instant startedAt;
    private final Instant finishedAt;
    private final DiagnosticCommand command;
    private final List<String> arguments;
    private final CommandResult result;

    CommandExecution(long id, Instant startedAt, Instant finishedAt, DiagnosticCommand command,
                     List<String> arguments, CommandResult result) {
        this.id = id;
        this.startedAt = startedAt == null ? Instant.now() : startedAt;
        this.finishedAt = finishedAt == null ? this.startedAt : finishedAt;
        this.command = command;
        this.arguments = arguments == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(arguments));
        this.result = result;
    }

    long id() { return id; }
    Instant startedAt() { return startedAt; }
    Instant finishedAt() { return finishedAt; }
    DiagnosticCommand command() { return command; }
    List<String> arguments() { return arguments; }
    CommandResult result() { return result; }

    boolean running() { return result == null; }

    CommandExecution finished(CommandResult value) {
        return new CommandExecution(id, startedAt, Instant.now(), command, arguments, value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CommandExecution)) return false;
        CommandExecution that = (CommandExecution) other;
        return id == that.id && Objects.equals(startedAt, that.startedAt)
                && Objects.equals(finishedAt, that.finishedAt) && Objects.equals(command, that.command)
                && arguments.equals(that.arguments) && Objects.equals(result, that.result);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, startedAt, finishedAt, command, arguments, result);
    }

    @Override
    public String toString() {
        return "CommandExecution[id=" + id + ", startedAt=" + startedAt + ", finishedAt=" + finishedAt
                + ", command=" + command + ", arguments=" + arguments + ", result=" + result + "]";
    }
}
