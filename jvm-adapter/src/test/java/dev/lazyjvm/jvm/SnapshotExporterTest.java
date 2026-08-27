package dev.lazyjvm.jvm;

import dev.lazyjvm.domain.CapabilitySet;
import dev.lazyjvm.domain.JdkIdentity;
import dev.lazyjvm.domain.MetricKey;
import dev.lazyjvm.domain.MetricPoint;
import dev.lazyjvm.domain.MetricQuality;
import dev.lazyjvm.domain.MetricSnapshot;
import dev.lazyjvm.domain.SnapshotManifest;
import dev.lazyjvm.domain.TargetJvm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotExporterTest {
    @TempDir Path temporary;

    @Test
    void writesDocumentedSafeEntries() throws Exception {
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        TargetJvm target = new TargetJvm(42, "Example", "Example", "", "user", now,
                new JdkIdentity(Path.of("/jdk"), "21", "Test", true), false);
        MetricPoint point = new MetricPoint(now, MetricKey.HEAP_USED, 1024, MetricQuality.EXACT, "JMX");
        MetricSnapshot sample = new MetricSnapshot(now, Map.of(MetricKey.HEAP_USED, point),
                List.of(), List.of(), null, CapabilitySet.of(), Duration.ZERO, List.of());
        SnapshotManifest manifest = new SnapshotManifest("test", target, now, List.of("JMX"), List.of(), List.of());
        Path output = temporary.resolve("report.zip");

        new SnapshotExporter().export(output, manifest, List.of(sample), Map.of());

        assertTrue(Files.isRegularFile(output));
        try (ZipFile zip = new ZipFile(output.toFile())) {
            assertEquals(List.of("report.md", "environment.json", "metrics.csv"),
                    zip.stream().map(entry -> entry.getName()).toList());
        }
    }

    @Test
    void rejectsSymbolicLinkOutput() throws Exception {
        Path destination = temporary.resolve("destination.zip");
        Files.writeString(destination, "keep");
        Path link = temporary.resolve("report.zip");
        Files.createSymbolicLink(link, destination.getFileName());
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        TargetJvm target = new TargetJvm(42, "Example", "Example", "", "user", now,
                new JdkIdentity(Path.of("/jdk"), "21", "Test", true), false);
        SnapshotManifest manifest = new SnapshotManifest("test", target, now, List.of(), List.of(), List.of());

        assertThrows(IOException.class,
                () -> new SnapshotExporter().export(link, manifest, List.of(), Map.of()));
        assertEquals("keep", Files.readString(destination));
    }
}
