package dev.lazyjvm.domain;

import java.time.Duration;
import java.util.Objects;

public final class CommandResult {
    private final int exitCode;
    private final String output;
    private final boolean timedOut;
    private final boolean truncated;
    private final Duration duration;

    public CommandResult(int exitCode, String output, boolean timedOut, boolean truncated, Duration duration) {
        this.exitCode = exitCode;
        this.output = output;
        this.timedOut = timedOut;
        this.truncated = truncated;
        this.duration = duration;
    }

    public int exitCode() { return exitCode; }
    public String output() { return output; }
    public boolean timedOut() { return timedOut; }
    public boolean truncated() { return truncated; }
    public Duration duration() { return duration; }

    public boolean succeeded() {
        return exitCode == 0 && !timedOut;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CommandResult)) return false;
        CommandResult that = (CommandResult) other;
        return exitCode == that.exitCode && timedOut == that.timedOut && truncated == that.truncated
                && Objects.equals(output, that.output) && Objects.equals(duration, that.duration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exitCode, output, timedOut, truncated, duration);
    }

    @Override
    public String toString() {
        return "CommandResult[exitCode=" + exitCode + ", output=" + output + ", timedOut=" + timedOut
                + ", truncated=" + truncated + ", duration=" + duration + "]";
    }
}
