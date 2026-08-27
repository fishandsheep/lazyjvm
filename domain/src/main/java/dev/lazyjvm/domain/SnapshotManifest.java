package dev.lazyjvm.domain;

import java.time.Instant;
import java.util.List;

public record SnapshotManifest(
        String lazyJvmVersion,
        TargetJvm target,
        Instant capturedAt,
        List<String> sources,
        List<String> failures,
        List<String> redactions) {
    public SnapshotManifest {
        sources = sources == null ? List.of() : List.copyOf(sources);
        failures = failures == null ? List.of() : List.copyOf(failures);
        redactions = redactions == null ? List.of() : List.copyOf(redactions);
    }
}
