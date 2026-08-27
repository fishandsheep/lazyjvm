package dev.lazyjvm.domain;

import java.time.Duration;
import java.util.List;

public record CommandRequest(long pid, DiagnosticCommand command, List<String> arguments, Duration timeout) {
    public CommandRequest {
        if (pid <= 0) throw new IllegalArgumentException("pid must be positive");
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        timeout = timeout == null ? Duration.ofSeconds(15) : timeout;
    }
}
