package dev.lazyjvm.domain;

import java.time.Instant;
import java.util.Objects;

public final class TargetJvm {
    private final long pid;
    private final String displayName;
    private final String mainClass;
    private final String arguments;
    private final String user;
    private final Instant startTime;
    private final JdkIdentity jdk;
    private final boolean containerized;

    public TargetJvm(long pid, String displayName, String mainClass, String arguments, String user,
                     Instant startTime, JdkIdentity jdk, boolean containerized) {
        if (pid <= 0) throw new IllegalArgumentException("pid must be positive");
        this.pid = pid;
        this.displayName = displayName == null ? "unknown" : displayName;
        this.mainClass = mainClass == null ? "unknown" : mainClass;
        this.arguments = arguments == null ? "" : arguments;
        this.user = user == null ? "unknown" : user;
        this.startTime = startTime == null ? Instant.EPOCH : startTime;
        this.jdk = jdk == null ? JdkIdentity.unknown() : jdk;
        this.containerized = containerized;
    }

    public long pid() { return pid; }
    public String displayName() { return displayName; }
    public String mainClass() { return mainClass; }
    public String arguments() { return arguments; }
    public String user() { return user; }
    public Instant startTime() { return startTime; }
    public JdkIdentity jdk() { return jdk; }
    public boolean containerized() { return containerized; }

    public TargetJvm withJdk(JdkIdentity identity) {
        return new TargetJvm(pid, displayName, mainClass, arguments, user, startTime, identity, containerized);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TargetJvm)) return false;
        TargetJvm that = (TargetJvm) other;
        return pid == that.pid && containerized == that.containerized
                && displayName.equals(that.displayName) && mainClass.equals(that.mainClass)
                && arguments.equals(that.arguments) && user.equals(that.user)
                && startTime.equals(that.startTime) && jdk.equals(that.jdk);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pid, displayName, mainClass, arguments, user, startTime, jdk, containerized);
    }

    @Override
    public String toString() {
        return "TargetJvm[pid=" + pid + ", displayName=" + displayName + ", mainClass=" + mainClass
                + ", arguments=" + arguments + ", user=" + user + ", startTime=" + startTime
                + ", jdk=" + jdk + ", containerized=" + containerized + "]";
    }
}
