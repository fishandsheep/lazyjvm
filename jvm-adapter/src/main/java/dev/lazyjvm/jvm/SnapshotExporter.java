package dev.lazyjvm.jvm;

import dev.lazyjvm.domain.CommandResult;
import dev.lazyjvm.domain.MetricKey;
import dev.lazyjvm.domain.MetricPoint;
import dev.lazyjvm.domain.MetricSnapshot;
import dev.lazyjvm.domain.SnapshotManifest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class SnapshotExporter {
    public Path export(Path output, SnapshotManifest manifest, List<MetricSnapshot> history,
                       Map<String, CommandResult> commandOutputs) throws IOException {
        Path absolute = output.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null || !Files.isDirectory(parent)) throw new IOException("Output directory does not exist: " + parent);
        if (Files.isSymbolicLink(absolute)) throw new IOException("Refusing to overwrite symbolic link: " + absolute);
        Path temporary = Files.createTempFile(parent, ".lazyjvm-report-", ".tmp");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
                entry(zip, "report.md", report(manifest, history));
                entry(zip, "environment.json", environment(manifest));
                entry(zip, "metrics.csv", metrics(history));
                for (Map.Entry<String, CommandResult> command : commandOutputs.entrySet()) {
                    entry(zip, "commands/" + safeName(command.getKey()) + ".txt", command.getValue().output());
                }
            }
            moveAtomically(temporary, absolute);
            return absolute;
        } catch (Exception failure) {
            Files.deleteIfExists(temporary);
            throw failure;
        }
    }

    private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String report(SnapshotManifest manifest, List<MetricSnapshot> history) {
        MetricSnapshot latest = history.isEmpty() ? null : history.get(history.size() - 1);
        StringBuilder text = new StringBuilder("# LazyJVM diagnostic report\n\n");
        text.append("- Target: `").append(manifest.target().displayName()).append("`\n")
                .append("- PID: `").append(manifest.target().pid()).append("`\n")
                .append("- Captured: ").append(manifest.capturedAt()).append("\n")
                .append("- Target Java: ").append(manifest.target().jdk().version()).append("\n")
                .append("- Samples: ").append(history.size()).append("\n\n");
        if (latest != null) {
            text.append("## Latest metrics\n\n");
            latest.metrics().values().stream().sorted((a, b) -> a.key().id().compareTo(b.key().id()))
                    .forEach(point -> text.append("- ").append(point.key().label()).append(": ")
                            .append(point.value()).append(' ').append(point.key().unit())
                            .append(" (`").append(point.source()).append("`, ")
                            .append(point.quality().name().toLowerCase()).append(")\n"));
        }
        if (!manifest.failures().isEmpty()) {
            text.append("\n## Incomplete sources\n\n");
            manifest.failures().forEach(value -> text.append("- ").append(value).append('\n'));
        }
        return text.toString();
    }

    private static String environment(SnapshotManifest manifest) {
        return "{\n" +
                "  \"lazyJvmVersion\": \"" + json(manifest.lazyJvmVersion()) + "\",\n" +
                "  \"capturedAt\": \"" + manifest.capturedAt() + "\",\n" +
                "  \"pid\": " + manifest.target().pid() + ",\n" +
                "  \"displayName\": \"" + json(manifest.target().displayName()) + "\",\n" +
                "  \"javaVersion\": \"" + json(manifest.target().jdk().version()) + "\",\n" +
                "  \"javaVendor\": \"" + json(manifest.target().jdk().vendor()) + "\",\n" +
                "  \"sources\": [" + manifest.sources().stream().map(v -> "\"" + json(v) + "\"").reduce((a, b) -> a + ", " + b).orElse("") + "]\n" +
                "}\n";
    }

    private static String metrics(List<MetricSnapshot> history) {
        StringBuilder csv = new StringBuilder("timestamp,key,label,value,unit,quality,source\n");
        for (MetricSnapshot sample : history) {
            for (MetricPoint point : sample.metrics().values()) {
                csv.append(DateTimeFormatter.ISO_INSTANT.format(point.timestamp())).append(',')
                        .append(csv(point.key().id())).append(',').append(csv(point.key().label())).append(',')
                        .append(point.value()).append(',').append(csv(point.key().unit())).append(',')
                        .append(point.quality()).append(',').append(csv(point.source())).append('\n');
            }
        }
        return csv.toString();
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String safeName(String value) {
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
