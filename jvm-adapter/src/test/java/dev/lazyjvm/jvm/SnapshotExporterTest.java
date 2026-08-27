package dev.lazyjvm.jvm;

import dev.lazyjvm.domain.CapabilitySet;
import dev.lazyjvm.domain.CommandResult;
import dev.lazyjvm.domain.GcSnapshot;
import dev.lazyjvm.domain.JdkIdentity;
import dev.lazyjvm.domain.MemoryPoolSnapshot;
import dev.lazyjvm.domain.MetricKey;
import dev.lazyjvm.domain.MetricPoint;
import dev.lazyjvm.domain.MetricQuality;
import dev.lazyjvm.domain.MetricSnapshot;
import dev.lazyjvm.domain.SnapshotManifest;
import dev.lazyjvm.domain.TargetJvm;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
                new JdkIdentity(Paths.get("/jdk"), "21", "Test", true), false);
        MetricPoint point = new MetricPoint(now, MetricKey.HEAP_USED, 1024, MetricQuality.EXACT, "JMX");
        MetricSnapshot sample = new MetricSnapshot(now, Collections.singletonMap(MetricKey.HEAP_USED, point),
                Collections.<MemoryPoolSnapshot>emptyList(), Collections.<GcSnapshot>emptyList(),
                null, CapabilitySet.of(), Duration.ZERO, Collections.<String>emptyList());
        SnapshotManifest manifest = new SnapshotManifest("test", target, now,
                Collections.singletonList("JMX"), Collections.<String>emptyList(), Collections.<String>emptyList());
        Path output = temporary.resolve("report.zip");

        new SnapshotExporter().export(output, manifest, Collections.singletonList(sample), Collections.<String, CommandResult>emptyMap());

        assertTrue(Files.isRegularFile(output));
        try (ZipFile zip = new ZipFile(output.toFile())) {
            assertEquals(Arrays.asList("report.md", "environment.json", "metrics.csv"),
                    zip.stream().map(entry -> entry.getName()).collect(Collectors.toList()));
        }
    }

    @Test
    void rejectsSymbolicLinkOutput() throws Exception {
        Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
        Path destination = temporary.resolve("destination.zip");
        Files.write(destination, "keep".getBytes(StandardCharsets.UTF_8));
        Path link = temporary.resolve("report.zip");
        Files.createSymbolicLink(link, destination.getFileName());
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        TargetJvm target = new TargetJvm(42, "Example", "Example", "", "user", now,
                new JdkIdentity(Paths.get("/jdk"), "21", "Test", true), false);
        SnapshotManifest manifest = new SnapshotManifest("test", target, now,
                Collections.<String>emptyList(), Collections.<String>emptyList(), Collections.<String>emptyList());

        assertThrows(IOException.class,
                () -> new SnapshotExporter().export(link, manifest, Collections.<MetricSnapshot>emptyList(),
                        Collections.<String, CommandResult>emptyMap()));
        assertEquals("keep", new String(Files.readAllBytes(destination), StandardCharsets.UTF_8));
    }
}
