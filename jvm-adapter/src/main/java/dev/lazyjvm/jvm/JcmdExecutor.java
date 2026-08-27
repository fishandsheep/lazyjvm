package dev.lazyjvm.jvm;

import dev.lazyjvm.domain.CommandRequest;
import dev.lazyjvm.domain.CommandResult;
import dev.lazyjvm.domain.JdkIdentity;
import dev.lazyjvm.domain.TargetJvm;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class JcmdExecutor {
    public static final int DEFAULT_OUTPUT_LIMIT = 4 * 1024 * 1024;

    private final Path overrideJdkHome;
    private final int outputLimit;

    public JcmdExecutor(Path overrideJdkHome) {
        this(overrideJdkHome, DEFAULT_OUTPUT_LIMIT);
    }

    public JcmdExecutor(Path overrideJdkHome, int outputLimit) {
        if (outputLimit <= 0) throw new IllegalArgumentException("outputLimit must be positive");
        this.overrideJdkHome = overrideJdkHome;
        this.outputLimit = outputLimit;
    }

    public boolean available(TargetJvm target) {
        try {
            return Files.isExecutable(resolveJcmd(target));
        } catch (Exception ignored) {
            return false;
        }
    }

    public String compatibilityWarning(TargetJvm target) {
        try {
            Path selected = resolveJcmd(target).toAbsolutePath().normalize();
            if (overrideJdkHome != null) return "";
            JdkIdentity identity = target.jdk();
            if (identity != null && identity.home() != null) {
                Path targetHome = identity.home().toAbsolutePath().normalize();
                if (selected.startsWith(targetHome)) return "";
            }
            String targetVersion = identity == null ? "unknown" : identity.version();
            return "jcmd fallback uses LazyJVM Java " + Runtime.version().feature()
                    + " for target Java " + targetVersion + "; pass --jdk-home for a target-matched tool";
        } catch (Exception exception) {
            return "jcmd unavailable: " + exception.getMessage();
        }
    }

    public CommandResult execute(TargetJvm target, CommandRequest request) throws Exception {
        if (request.pid() != target.pid()) throw new IllegalArgumentException("request PID does not match target");
        List<String> argv = new ArrayList<>();
        argv.add(resolveJcmd(target).toString());
        argv.add(Long.toString(target.pid()));
        argv.add(request.command().name());
        argv.addAll(request.arguments());

        long started = System.nanoTime();
        ProcessBuilder builder = new ProcessBuilder(argv).redirectErrorStream(true);
        builder.environment().put("LC_ALL", "C");
        Process process = builder.start();
        FutureTask<ReadResult> reader = new FutureTask<>(() -> readLimited(process.getInputStream(), outputLimit));
        Thread outputReader = new Thread(reader, "lazyjvm-jcmd-output");
        outputReader.setDaemon(true);
        outputReader.start();

        boolean finished = process.waitFor(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroy();
            if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly();
        }
        ReadResult read = reader.get(2, TimeUnit.SECONDS);
        int exit = finished ? process.exitValue() : -1;
        return new CommandResult(exit, read.output(), !finished, read.truncated(),
                Duration.ofNanos(System.nanoTime() - started));
    }

    public Path resolveJcmd(TargetJvm target) {
        List<Path> homes = new ArrayList<>();
        if (overrideJdkHome != null) homes.add(overrideJdkHome);
        JdkIdentity identity = target.jdk();
        if (identity != null && identity.home() != null) {
            homes.add(identity.home());
            Path parent = identity.home().getParent();
            if (parent != null) homes.add(parent);
        }
        homes.add(Path.of(System.getProperty("java.home")));
        for (Path home : homes) {
            Path candidate = home.resolve("bin").resolve(isWindows() ? "jcmd.exe" : "jcmd");
            if (Files.isExecutable(candidate)) return candidate;
        }
        throw new IllegalStateException("No executable jcmd found; pass --jdk-home for the target JDK");
    }

    private static ReadResult readLimited(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 64 * 1024));
        byte[] buffer = new byte[8192];
        int read;
        boolean truncated = false;
        while ((read = input.read(buffer)) >= 0) {
            int writable = Math.min(read, Math.max(0, limit - output.size()));
            if (writable > 0) output.write(buffer, 0, writable);
            if (writable < read) truncated = true;
        }
        String text = output.toString(StandardCharsets.UTF_8);
        if (truncated) text += "\n[LazyJVM truncated output at " + limit + " bytes]\n";
        return new ReadResult(text, truncated);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private record ReadResult(String output, boolean truncated) {}
}
