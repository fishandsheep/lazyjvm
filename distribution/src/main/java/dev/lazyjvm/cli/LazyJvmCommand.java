package dev.lazyjvm.cli;

import dev.lazyjvm.domain.CommandImpact;
import dev.lazyjvm.domain.CommandRequest;
import dev.lazyjvm.domain.CommandResult;
import dev.lazyjvm.domain.DiagnosticCommand;
import dev.lazyjvm.domain.MetricSnapshot;
import dev.lazyjvm.domain.SnapshotManifest;
import dev.lazyjvm.domain.TargetJvm;
import dev.lazyjvm.jvm.JcmdExecutor;
import dev.lazyjvm.jvm.JmxCollector;
import dev.lazyjvm.jvm.JvmDiscovery;
import dev.lazyjvm.jvm.LocalJmxSession;
import dev.lazyjvm.jvm.SnapshotExporter;
import dev.lazyjvm.tui.TuiApplication;
import dev.lazyjvm.tui.UiLanguage;
import dev.lazyjvm.tui.TuiOptions;
import picocli.CommandLine;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "lazyjvm",
        description = "Keyboard-first local JVM diagnostics",
        mixinStandardHelpOptions = true,
        version = "LazyJVM 0.1.0-SNAPSHOT",
        sortOptions = false)
public final class LazyJvmCommand implements Callable<Integer> {
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "PID",
            description = "Attach directly to a local JVM process")
    private Long pid;

    @CommandLine.Option(names = "--refresh", converter = DurationConverter.class,
            description = "Lightweight sample interval (default: ${DEFAULT-VALUE})")
    private Duration refresh = Duration.ofSeconds(1);

    @CommandLine.Option(names = "--history", converter = DurationConverter.class,
            description = "Bounded in-memory history window (default: ${DEFAULT-VALUE})")
    private Duration history = Duration.ofMinutes(60);

    @CommandLine.Option(names = "--jdk-home", paramLabel = "PATH",
            description = "JDK home containing the target-compatible jcmd")
    private Path jdkHome;

    @CommandLine.Option(names = "--ascii", description = "Use ASCII borders and charts")
    private boolean ascii;

    @CommandLine.Option(names = {"--language", "--lang"}, defaultValue = "zh-CN",
            description = "UI language: zh-CN or en (default: ${DEFAULT-VALUE})")
    private String language = "zh-CN";

    @CommandLine.Option(names = "--no-color", description = "Disable terminal colors")
    private boolean noColor;

    @CommandLine.Option(names = "--debug-log", paramLabel = "PATH",
            description = "Write uncaught diagnostics to this file")
    private Path debugLog;

    @CommandLine.Option(names = "--snapshot", paramLabel = "ZIP",
            description = "Collect one non-interactive diagnostic bundle; requires PID")
    private Path snapshot;

    @Override
    public Integer call() throws Exception {
        configureDebugLog();
        if (snapshot != null) {
            if (pid == null) throw new CommandLine.ParameterException(new CommandLine(this), "--snapshot requires PID");
            return snapshot(pid, snapshot);
        }
        UiLanguage uiLanguage;
        try {
            uiLanguage = UiLanguage.parse(language);
        } catch (IllegalArgumentException exception) {
            throw new CommandLine.ParameterException(new CommandLine(this), exception.getMessage(), exception);
        }
        TuiOptions options = new TuiOptions(refresh, history, jdkHome, ascii, !noColor, uiLanguage);
        try (TuiApplication application = new TuiApplication(options, pid)) {
            return application.run();
        }
    }

    private int snapshot(long targetPid, Path output) throws Exception {
        TargetJvm target = new JvmDiscovery().find(targetPid)
                .orElseThrow(() -> new CommandLine.ParameterException(new CommandLine(this),
                        "PID " + targetPid + " is not an attachable local JVM"));
        try (LocalJmxSession session = LocalJmxSession.attach(target);
             JmxCollector collector = new JmxCollector(session)) {
            MetricSnapshot sample = collector.sample();
            JcmdExecutor executor = new JcmdExecutor(jdkHome);
            String jcmdWarning = executor.compatibilityWarning(session.target());
            if (!jcmdWarning.trim().isEmpty()) System.err.println("Warning: " + jcmdWarning);
            Map<String, CommandResult> commands = collectSafeCommands(session.target(), executor);
            SnapshotManifest manifest = new SnapshotManifest("0.1.0-SNAPSHOT", session.target(), Instant.now(),
                    Arrays.asList("Attach API", "JMX MXBeans", "jcmd"), Collections.<String>emptyList(),
                    Collections.singletonList("Environment variables and system properties are not exported by default"));
            Path written = new SnapshotExporter().export(output, manifest, Collections.singletonList(sample), commands);
            System.out.println(written);
            return 0;
        }
    }

    private static Map<String, CommandResult> collectSafeCommands(TargetJvm target, JcmdExecutor executor) {
        if (!executor.available(target)) return Collections.emptyMap();
        Map<String, CommandResult> results = new LinkedHashMap<>();
        for (String name : Arrays.asList("VM.version", "VM.flags", "GC.heap_info", "Thread.print")) {
            try {
                DiagnosticCommand command = new DiagnosticCommand(name, "", CommandImpact.LOW, Collections.<String>emptyList());
                List<String> arguments = name.equals("Thread.print")
                        ? Collections.singletonList("-l") : Collections.<String>emptyList();
                results.put(name, executor.execute(target,
                        new CommandRequest(target.pid(), command, arguments, Duration.ofSeconds(20))));
            } catch (Exception exception) {
                results.put(name, new CommandResult(-1, exception.toString(), false, false, Duration.ZERO));
            }
        }
        return results;
    }

    private void configureDebugLog() throws Exception {
        if (debugLog == null) return;
        Path absolute = debugLog.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IllegalArgumentException("debug log directory does not exist: " + parent);
        }
        if (Files.isSymbolicLink(absolute)) {
            throw new IllegalArgumentException("refusing symbolic-link debug log: " + absolute);
        }
        PrintStream stream = new PrintStream(Files.newOutputStream(absolute), true);
        System.setErr(stream);
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> {
            System.err.println("Uncaught exception in " + thread.getName());
            failure.printStackTrace(System.err);
        });
    }
}
