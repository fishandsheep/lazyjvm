package dev.lazyjvm.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class SnapshotManifest {
    private final String lazyJvmVersion;
    private final TargetJvm target;
    private final Instant capturedAt;
    private final List<String> sources;
    private final List<String> failures;
    private final List<String> redactions;

    public SnapshotManifest(String lazyJvmVersion, TargetJvm target, Instant capturedAt,
                            List<String> sources, List<String> failures, List<String> redactions) {
        this.lazyJvmVersion = lazyJvmVersion;
        this.target = target;
        this.capturedAt = capturedAt;
        this.sources = immutableList(sources);
        this.failures = immutableList(failures);
        this.redactions = immutableList(redactions);
    }

    private static <T> List<T> immutableList(List<T> source) {
        return source == null ? Collections.<T>emptyList()
                : Collections.unmodifiableList(new ArrayList<T>(source));
    }

    public String lazyJvmVersion() { return lazyJvmVersion; }
    public TargetJvm target() { return target; }
    public Instant capturedAt() { return capturedAt; }
    public List<String> sources() { return sources; }
    public List<String> failures() { return failures; }
    public List<String> redactions() { return redactions; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SnapshotManifest)) return false;
        SnapshotManifest that = (SnapshotManifest) other;
        return Objects.equals(lazyJvmVersion, that.lazyJvmVersion) && Objects.equals(target, that.target)
                && Objects.equals(capturedAt, that.capturedAt) && sources.equals(that.sources)
                && failures.equals(that.failures) && redactions.equals(that.redactions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lazyJvmVersion, target, capturedAt, sources, failures, redactions);
    }

    @Override
    public String toString() {
        return "SnapshotManifest[lazyJvmVersion=" + lazyJvmVersion + ", target=" + target
                + ", capturedAt=" + capturedAt + ", sources=" + sources + ", failures=" + failures
                + ", redactions=" + redactions + "]";
    }
}
