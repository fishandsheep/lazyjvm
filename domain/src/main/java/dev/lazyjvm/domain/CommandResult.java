package dev.lazyjvm.domain;

import java.time.Duration;

public record CommandResult(int exitCode, String output, boolean timedOut, boolean truncated, Duration duration) {
    public boolean succeeded() {
        return exitCode == 0 && !timedOut;
    }
}
