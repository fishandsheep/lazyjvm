package dev.lazyjvm.domain;

import java.time.Instant;
import java.util.Objects;

public record TargetJvm(
        long pid,
        String displayName,
        String mainClass,
        String arguments,
        String user,
        Instant startTime,
        JdkIdentity jdk,
        boolean containerized) {

    public TargetJvm {
        if (pid <= 0) throw new IllegalArgumentException("pid must be positive");
        displayName = Objects.requireNonNullElse(displayName, "unknown");
        mainClass = Objects.requireNonNullElse(mainClass, "unknown");
        arguments = Objects.requireNonNullElse(arguments, "");
        user = Objects.requireNonNullElse(user, "unknown");
        startTime = Objects.requireNonNullElse(startTime, Instant.EPOCH);
        jdk = Objects.requireNonNullElseGet(jdk, JdkIdentity::unknown);
    }

    public TargetJvm withJdk(JdkIdentity identity) {
        return new TargetJvm(pid, displayName, mainClass, arguments, user, startTime, identity, containerized);
    }
}
