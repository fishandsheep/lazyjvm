package dev.lazyjvm.domain;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CommandRequest {
    private final long pid;
    private final DiagnosticCommand command;
    private final List<String> arguments;
    private final Duration timeout;

    public CommandRequest(long pid, DiagnosticCommand command, List<String> arguments, Duration timeout) {
        if (pid <= 0) throw new IllegalArgumentException("pid must be positive");
        this.pid = pid;
        this.command = command;
        this.arguments = arguments == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(arguments));
        this.timeout = timeout == null ? Duration.ofSeconds(15) : timeout;
    }

    public long pid() { return pid; }
    public DiagnosticCommand command() { return command; }
    public List<String> arguments() { return arguments; }
    public Duration timeout() { return timeout; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CommandRequest)) return false;
        CommandRequest that = (CommandRequest) other;
        return pid == that.pid && Objects.equals(command, that.command)
                && arguments.equals(that.arguments) && timeout.equals(that.timeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pid, command, arguments, timeout);
    }

    @Override
    public String toString() {
        return "CommandRequest[pid=" + pid + ", command=" + command + ", arguments=" + arguments
                + ", timeout=" + timeout + "]";
    }
}
