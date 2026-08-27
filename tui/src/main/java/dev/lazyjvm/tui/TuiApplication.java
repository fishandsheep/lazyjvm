package dev.lazyjvm.tui;

/*
THESIS: Live JVM evidence and its exact diagnostic actions share one keyboard-first operator surface; no command-reference maze.
OWN-WORLD: Graphite field, green focus, blue selection, amber activity, semantic green/yellow/red, rounded terminal rules.
STORY: Select a local JVM, read health at a glance, move into memory, threads, JFR, or commands, then export evidence.
FIRST VIEWPORT: Target identity spans the header; numbered workspaces frame live charts and compact navigation.
FORM: Lazy-series operator console, user-pinned canon; concept seed d15f07bc; signature motion is a live sample sweep across fixed history.
FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, and DESIGN.md
*/

import dev.lazyjvm.domain.Capability;
import dev.lazyjvm.domain.CapabilitySet;
import dev.lazyjvm.domain.CommandImpact;
import dev.lazyjvm.domain.CommandRequest;
import dev.lazyjvm.domain.CommandResult;
import dev.lazyjvm.domain.DiagnosticCommand;
import dev.lazyjvm.domain.GcSnapshot;
import dev.lazyjvm.domain.MemoryPoolSnapshot;
import dev.lazyjvm.domain.MetricHistory;
import dev.lazyjvm.domain.MetricKey;
import dev.lazyjvm.domain.MetricPoint;
import dev.lazyjvm.domain.MetricQuality;
import dev.lazyjvm.domain.MetricSnapshot;
import dev.lazyjvm.domain.SnapshotManifest;
import dev.lazyjvm.domain.TargetJvm;
import dev.lazyjvm.domain.ThreadSnapshot;
import dev.lazyjvm.jvm.JcmdCatalog;
import dev.lazyjvm.jvm.JcmdExecutor;
import dev.lazyjvm.jvm.JmxCollector;
import dev.lazyjvm.jvm.JvmDiscovery;
import dev.lazyjvm.jvm.LocalJmxSession;
import dev.lazyjvm.jvm.SnapshotExporter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class TuiApplication implements AutoCloseable {
    private enum Mode { PICKER, CONNECTING, MONITOR, ERROR }
    private enum Page {
        OVERVIEW("Overview", "概览"), MEMORY("Memory / GC", "内存 / GC"), THREADS("Threads", "线程"),
        JFR("JFR", "JFR"), COMMANDS("Commands", "命令"), REPORTS("Reports", "报告");
        final String label;
        final String chinese;
        Page(String label, String chinese) { this.label = label; this.chinese = chinese; }
        String title(UiLanguage language) { return language.isEnglish() ? label : chinese; }

        String title(boolean ascii) { return title(ascii ? UiLanguage.EN : UiLanguage.ZH_CN); }
    }
    private enum Prompt { NONE, SEARCH, CONFIRM }

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private final TuiOptions options;
    private final JvmDiscovery discovery = new JvmDiscovery();
    private static final AtomicInteger WORKER_ID = new AtomicInteger();
    private final ScheduledExecutorService workers = Executors.newScheduledThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "lazyjvm-worker-" + WORKER_ID.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });
    private final MetricHistory history;
    private final AtomicReference<MetricSnapshot> latest = new AtomicReference<>();
    private final AtomicBoolean sampling = new AtomicBoolean();
    private final Map<String, CommandResult> commandOutputs = new ConcurrentHashMap<>();
    private final List<CommandExecution> commandExecutions = java.util.Collections.synchronizedList(new ArrayList<>());
    private final Set<String> runningCommands = ConcurrentHashMap.newKeySet();
    private final List<String> eventLog = java.util.Collections.synchronizedList(new ArrayList<>());
    private final Long initialPid;

    private Terminal terminal;
    private ColorProfile colorProfile = ColorProfile.NONE;
    private volatile Mode mode = Mode.PICKER;
    private volatile Connection connection;
    private volatile String status = "Discovering local JVMs";
    private volatile String error = "";
    private List<TargetJvm> targets = List.of();
    private int selectedTarget;
    private int selectedJfr;
    private Page page = Page.OVERVIEW;
    private boolean paused;
    private boolean help;
    private boolean contextualHelp;
    private boolean outputDropdown;
    private boolean running = true;
    private Prompt prompt = Prompt.NONE;
    private String promptText = "";
    private String pickerFilter = "";
    private String commandFilter = "";
    private String searchBefore = "";
    private DiagnosticCommand pendingCommand;
    private List<String> pendingArguments = List.of();
    private FocusArea focusArea = FocusArea.MAIN;
    private int selectedExecution = -1;
    private int outputScroll;
    private int selectedCommandRow;
    private int commandScroll;
    private int pickerScroll;
    private int memoryScroll;
    private int threadScroll;
    private int reportScroll;
    private int overviewScroll;
    private boolean reportFollowTail = true;
    private final Set<String> collapsedCommandGroups = new HashSet<>();
    private final DoubleClickTracker commandClick = new DoubleClickTracker();
    private long executionSequence;
    private boolean asciiMode;
    private UiLanguage uiLanguage = UiLanguage.ZH_CN;
    private UiLayout layout = UiLayout.forSize(40, 12, focusArea, false);
    private volatile ScheduledFuture<?> samplingTask;
    private String lastFrame = "";

    public TuiApplication(TuiOptions options, Long initialPid) {
        this.options = options;
        this.initialPid = initialPid;
        this.history = new MetricHistory(options.historyCapacity());
    }

    public int run() throws Exception {
        terminal = TerminalBuilder.builder().system(true).build();
        colorProfile = ColorProfile.detect(terminal, options.color());
        asciiMode = options.ascii() || !StandardCharsets.UTF_8.equals(terminal.encoding());
        uiLanguage = options.language().forTerminal(asciiMode);
        terminal.enterRawMode();
        terminal.handle(Terminal.Signal.INT, signal -> running = false);
        enableMouseTracking();
        terminal.writer().print("\033[?1049h\033[2J\033[H\033[?25l");
        terminal.writer().flush();
        refreshTargets();
        if (initialPid != null) {
            Optional<TargetJvm> target = targets.stream().filter(value -> value.pid() == initialPid).findFirst();
            if (target.isPresent()) connect(target.get());
            else {
                mode = Mode.ERROR;
                error = "PID " + initialPid + " is not an attachable local JVM";
                status = "Target unavailable";
            }
        }

        try {
            while (running) {
                draw();
                InputEvent event = readInputEvent();
                if (event != null) handleInput(event);
            }
            return 0;
        } finally {
            close();
        }
    }

    private void enableMouseTracking() {
        terminal.writer().print("\033[?1000h\033[?1006h");
        terminal.writer().flush();
    }

    private void refreshTargets() {
        try {
            targets = discovery.discover();
            selectedTarget = Math.min(selectedTarget, Math.max(0, visibleTargets().size() - 1));
            pickerScroll = 0;
            status = targets.isEmpty() ? "No attachable JVMs found" : targets.size() + " local JVMs";
        } catch (Exception exception) {
            mode = Mode.ERROR;
            error = concise(exception);
            status = "Discovery failed";
        }
    }

    private void connect(TargetJvm target) {
        mode = Mode.CONNECTING;
        status = "Attaching to PID " + target.pid();
        event("Attach requested for PID " + target.pid());
        CompletableFuture.runAsync(() -> {
            try {
                LocalJmxSession session = LocalJmxSession.attach(target);
                JmxCollector collector = new JmxCollector(session);
                JcmdExecutor jcmd = new JcmdExecutor(options.jdkHome());
                List<DiagnosticCommand> commands = loadCommands(session.target(), jcmd);
                connection = new Connection(session.target(), collector, jcmd, commands);
                mode = Mode.MONITOR;
                String jcmdWarning = jcmd.compatibilityWarning(session.target());
                status = jcmdWarning.isBlank() ? "Connected via local JMX" : "Connected · jcmd version warning";
                event("Connected to " + session.target().displayName());
                if (!jcmdWarning.isBlank()) event(jcmdWarning);
                scheduleSampling();
            } catch (Exception exception) {
                mode = Mode.ERROR;
                error = attachRecovery(exception);
                status = "Attach failed";
                event("Attach failed: " + concise(exception));
            }
        }, workers);
    }

    private List<DiagnosticCommand> loadCommands(TargetJvm target, JcmdExecutor jcmd) {
        JcmdCatalog parser = new JcmdCatalog();
        if (!jcmd.available(target)) return parser.fallback();
        try {
            DiagnosticCommand helpCommand = new DiagnosticCommand("help", "List target diagnostic commands", CommandImpact.LOW, List.of());
            CommandResult result = jcmd.execute(target, new CommandRequest(target.pid(), helpCommand, List.of(), Duration.ofSeconds(8)));
            List<DiagnosticCommand> parsed = parser.parse(result.output());
            return parsed.isEmpty() ? parser.fallback() : parsed;
        } catch (Exception exception) {
            event("jcmd catalog unavailable: " + concise(exception));
            return parser.fallback();
        }
    }

    private void scheduleSampling() {
        ScheduledFuture<?> previous = samplingTask;
        if (previous != null) previous.cancel(false);
        sampleNow();
        samplingTask = workers.scheduleWithFixedDelay(this::sampleNow, options.refresh().toMillis(),
                options.refresh().toMillis(), TimeUnit.MILLISECONDS);
    }

    private void sampleNow() {
        Connection active = connection;
        if (active == null || paused || !sampling.compareAndSet(false, true)) return;
        try {
            MetricSnapshot sample = active.collector().sample();
            latest.set(sample);
            history.add(sample);
            status = "Live · " + sample.collectionLatency().toMillis() + " ms sample";
        } catch (Exception exception) {
            status = "Sample missed · " + concise(exception);
            event(status);
            if (ProcessHandle.of(active.target().pid()).map(ProcessHandle::isAlive).orElse(false) == false) {
                mode = Mode.ERROR;
                error = "Target JVM exited. Press Esc to return to process discovery.";
            }
        } finally {
            sampling.set(false);
        }
    }

    private void handleInput(InputEvent event) throws IOException {
        if (event.kind() == InputEvent.Kind.MOUSE) {
            handleMouse(event.mouse());
            return;
        }
        commandClick.reset();
        int key = event.key();
        if (prompt != Prompt.NONE) {
            handlePromptKey(key);
            return;
        }
        if (key == 3 || key == 'q') {
            running = false;
            return;
        }
        if (key == KEY_F1) {
            help = !help;
            contextualHelp = false;
            return;
        }
        if (key == '?') {
            contextualHelp = !contextualHelp;
            help = false;
            return;
        }
        if (help || contextualHelp) {
            if (key == 27 || key == '\n' || key == '\r') {
                help = false;
                contextualHelp = false;
            }
            return;
        }
        if (key == 9 || key == KEY_SHIFT_TAB) {
            if (mode == Mode.MONITOR) {
                if (page == Page.COMMANDS) {
                    focusArea = key == 9 ? focusArea.next() : focusArea.previous();
                } else {
                    if (focusArea == FocusArea.COMMAND_OUTPUT) focusArea = FocusArea.MAIN;
                    focusArea = focusArea == FocusArea.WORKSPACE
                            ? FocusArea.MAIN : FocusArea.WORKSPACE;
                }
            }
            return;
        }
        if (key == 27) {
            if (outputDropdown) {
                outputDropdown = false;
                return;
            }
            if (mode == Mode.MONITOR || mode == Mode.ERROR) backToPicker();
            return;
        }
        if (key == '/') {
            if (mode == Mode.MONITOR) {
                page = Page.COMMANDS;
                focusArea = FocusArea.MAIN;
            }
            prompt = Prompt.SEARCH;
            promptText = mode == Mode.PICKER ? pickerFilter : commandFilter;
            searchBefore = promptText;
            return;
        }
        if (mode == Mode.PICKER) handlePicker(key, toKey(key));
        else if (mode == Mode.MONITOR) handleMonitor(key, toKey(key));
        else if (mode == Mode.ERROR && key == 'r') refreshTargets();
    }

    private void handleMouse(InputEvent.Mouse mouse) {
        if (mode == Mode.PICKER) {
            if (mouse.wheelUp() || mouse.wheelDown()) {
                List<TargetJvm> visible = visibleTargets();
                int delta = mouse.wheelUp() ? -3 : 3;
                selectedTarget = Math.max(0, Math.min(Math.max(0, visible.size() - 1), selectedTarget + delta));
                pickerScroll = ScrollModel.follow(selectedTarget, pickerScroll, pickerVisibleRows(), visible.size());
            }
            return;
        }
        if (mode != Mode.MONITOR) return;
        if (mouse.wheelUp() || mouse.wheelDown()) {
            commandClick.reset();
            int delta = mouse.wheelUp() ? -3 : 3;
            if (layout.outputBody().contains(mouse.x(), mouse.y()) || layout.commandOutput().contains(mouse.x(), mouse.y())) {
                focusArea = FocusArea.COMMAND_OUTPUT;
                scrollOutput(delta);
            } else if (layout.commandList().contains(mouse.x(), mouse.y())) {
                focusArea = FocusArea.MAIN;
                List<CommandTreeItem> items = commandTree();
                selectedCommandRow = Math.max(0, Math.min(Math.max(0, items.size() - 1), selectedCommandRow + delta));
                commandScroll = ScrollModel.follow(selectedCommandRow, commandScroll, commandVisibleRows(), items.size());
            } else if (layout.main().contains(mouse.x(), mouse.y())) {
                focusArea = FocusArea.MAIN;
                scrollMain(delta);
            } else if (focusArea == FocusArea.COMMAND_OUTPUT) {
                scrollOutput(delta);
            }
            return;
        }
        if (!mouse.primary()) return;
        UiLayout.Hit hit = layout.hitTest(mouse.x(), mouse.y());
        if (hit.kind() != UiLayout.HitKind.COMMAND_ROW) commandClick.reset();
        switch (hit.kind()) {
            case WORKSPACE -> {
                focusArea = FocusArea.WORKSPACE;
                if (hit.index() >= 0 && hit.index() < Page.values().length) page = Page.values()[hit.index()];
            }
            case OUTPUT_SELECTOR -> {
                focusArea = FocusArea.COMMAND_OUTPUT;
                outputDropdown = !outputDropdown;
            }
            case OUTPUT_COPY -> {
                focusArea = FocusArea.COMMAND_OUTPUT;
                copyCurrentOutput();
            }
            case OUTPUT_ITEM -> {
                focusArea = FocusArea.COMMAND_OUTPUT;
                selectedExecution = hit.index();
                outputScroll = 0;
                outputDropdown = false;
            }
            case COMMAND_GROUP -> {
                focusArea = FocusArea.MAIN;
                selectedCommandRow = hit.index();
                toggleSelectedCommandGroup();
            }
            case COMMAND_ROW -> {
                focusArea = FocusArea.MAIN;
                selectedCommandRow = hit.index();
                if (commandClick.register(hit.index(), System.nanoTime())) {
                    CommandTreeItem item = selectedCommandItem();
                    if (item != null && !item.groupHeader()) requestCommand(item.command());
                }
            }
            case COMMAND_OUTPUT -> focusArea = FocusArea.COMMAND_OUTPUT;
            case COMMAND_LIST, MAIN -> focusArea = FocusArea.MAIN;
            case NONE -> { }
        }
    }

    private void handlePicker(int key, Key decoded) {
        List<TargetJvm> visible = visibleTargets();
        if (decoded == Key.UP || key == 'k') selectedTarget = Math.max(0, selectedTarget - 1);
        if (decoded == Key.DOWN || key == 'j') selectedTarget = Math.min(Math.max(0, visible.size() - 1), selectedTarget + 1);
        int rows = Math.max(1, detectedSize(terminal == null ? 12 : terminal.getHeight(), "LINES", 12) - 7);
        if (decoded == Key.PAGE_UP) selectedTarget = Math.max(0, selectedTarget - rows);
        if (decoded == Key.PAGE_DOWN) selectedTarget = Math.min(Math.max(0, visible.size() - 1), selectedTarget + rows);
        if (decoded == Key.HOME) selectedTarget = 0;
        if (decoded == Key.END) selectedTarget = Math.max(0, visible.size() - 1);
        pickerScroll = ScrollModel.follow(selectedTarget, pickerScroll, rows, visible.size());
        if (key == 'r') refreshTargets();
        if ((key == '\n' || key == '\r' || decoded == Key.RIGHT || key == 'l') && !visible.isEmpty()) {
            connect(visible.get(selectedTarget));
        }
    }

    private void handleMonitor(int key, Key decoded) {
        if (key >= '1' && key <= '6') {
            page = Page.values()[key - '1'];
            focusArea = FocusArea.MAIN;
            return;
        }
        if (decoded == Key.LEFT || decoded == Key.RIGHT || key == 'h' || key == 'l') return;
        if (focusArea == FocusArea.WORKSPACE) {
            if (decoded == Key.UP || key == 'k') page = Page.values()[Math.max(0, page.ordinal() - 1)];
            if (decoded == Key.DOWN || key == 'j') page = Page.values()[Math.min(Page.values().length - 1, page.ordinal() + 1)];
            if (key == '\n' || key == '\r') focusArea = FocusArea.MAIN;
            return;
        }
        if (focusArea == FocusArea.COMMAND_OUTPUT) {
            if (decoded == Key.UP || key == 'k') selectExecution(-1);
            if (decoded == Key.DOWN || key == 'j') selectExecution(1);
            if (decoded == Key.PAGE_UP) scrollOutput(-1);
            if (decoded == Key.PAGE_DOWN) scrollOutput(1);
            if (decoded == Key.HOME) scrollOutput(Integer.MIN_VALUE);
            if (decoded == Key.END) scrollOutput(Integer.MAX_VALUE);
            if (key == 'y') copyCurrentOutput();
            return;
        }
        if (decoded == Key.PAGE_UP) {
            scrollMainPage(-1);
            return;
        }
        if (decoded == Key.PAGE_DOWN) {
            scrollMainPage(1);
            return;
        }
        if (decoded == Key.HOME) {
            scrollMainPage(Integer.MIN_VALUE);
            return;
        }
        if (decoded == Key.END) {
            scrollMainPage(Integer.MAX_VALUE);
            return;
        }
        if ((decoded == Key.UP || key == 'k') && page == Page.COMMANDS) {
            moveCommandSelection(-1);
            return;
        }
        if ((decoded == Key.DOWN || key == 'j') && page == Page.COMMANDS) {
            moveCommandSelection(1);
            return;
        }
        if ((decoded == Key.UP || key == 'k') && page == Page.JFR) {
            List<DiagnosticCommand> actions = jfrCommands();
            selectedJfr = Math.max(0, selectedJfr - 1);
            return;
        }
        if ((decoded == Key.DOWN || key == 'j') && page == Page.JFR) {
            List<DiagnosticCommand> actions = jfrCommands();
            selectedJfr = Math.min(Math.max(0, actions.size() - 1), selectedJfr + 1);
            return;
        }
        if (decoded == Key.UP || key == 'k') {
            scrollMain(-1);
            return;
        }
        if (decoded == Key.DOWN || key == 'j') {
            scrollMain(1);
            return;
        }
        if (key == 'p') {
            paused = !paused;
            status = paused ? "Paused · history retained" : "Live sampling resumed";
        }
        if (key == 'r') workers.execute(this::sampleNow);
        if (key == 'e') exportReport();
        if (key == 'c' && page == Page.COMMANDS) {
            commandFilter = "";
            selectedCommandRow = 0;
            commandScroll = 0;
            collapsedCommandGroups.clear();
            status = "Command filter cleared";
        }
        if (page == Page.JFR) {
            List<DiagnosticCommand> actions = jfrCommands();
            if ((key == 'x' || key == '\n' || key == '\r') && !actions.isEmpty()) requestCommand(actions.get(selectedJfr));
        }
        if (page == Page.COMMANDS) {
            CommandTreeItem item = selectedCommandItem();
            if ((key == 'x' || key == '\n' || key == '\r') && item != null) {
                if (item.groupHeader()) toggleSelectedCommandGroup();
                else requestCommand(item.command());
            }
        }
    }

    private void requestCommand(DiagnosticCommand command) {
        Connection active = connection;
        if (active == null) return;
        if (focusArea != FocusArea.MAIN) {
            status = uiLanguage.isEnglish() ? "Focus Main before running commands" : "请先将焦点切到主内容再执行命令";
            return;
        }
        if (runningCommands.contains(command.name())) {
            status = command.name() + " already running";
            return;
        }
        List<String> arguments = defaultArguments(command, active.target());
        if (command.impact() == CommandImpact.HIGH) {
            pendingCommand = command;
            pendingArguments = arguments;
            prompt = Prompt.CONFIRM;
            promptText = "";
            return;
        }
        executeCommand(command, arguments);
    }

    private void executeCommand(DiagnosticCommand command, List<String> arguments) {
        Connection active = connection;
        if (active == null) return;
        if (!runningCommands.add(command.name())) return;
        CommandExecution execution = new CommandExecution(++executionSequence, Instant.now(), Instant.now(),
                command, arguments, null);
        synchronized (commandExecutions) {
            commandExecutions.add(execution);
            while (commandExecutions.size() > 32) {
                commandExecutions.remove(0);
                selectedExecution = Math.max(-1, selectedExecution - 1);
            }
            selectedExecution = commandExecutions.size() - 1;
        }
        outputScroll = 0;
        status = "Running " + command.name();
        event("Command started: " + commandInvocation(active.target(), command, arguments)
                + " [" + UiText.impact(command.impact(), uiLanguage) + "]");
        CompletableFuture.runAsync(() -> {
            try {
                CommandResult result = active.jcmd().execute(active.target(),
                        new CommandRequest(active.target().pid(), command, arguments, timeout(command)));
                commandOutputs.put(command.name(), result);
                finishExecution(execution, result);
                status = command.name() + " · " + UiText.impact(command.impact(), uiLanguage)
                        + (result.timedOut()
                        ? " · " + ui("超时", "TIMEOUT")
                        : result.succeeded()
                        ? " · " + result.duration().toMillis() + " ms"
                        : " · " + ui("退出码 ", "exit ") + result.exitCode());
                event(status);
            } catch (Exception exception) {
                CommandResult failure = new CommandResult(-1, exception.toString(), false, false, Duration.ZERO);
                finishExecution(execution, failure);
                status = command.name() + " · " + UiText.impact(command.impact(), uiLanguage)
                        + " · " + concise(exception);
                event(status);
            } finally {
                runningCommands.remove(command.name());
            }
        }, workers);
    }

    private void finishExecution(CommandExecution started, CommandResult result) {
        synchronized (commandExecutions) {
            for (int index = 0; index < commandExecutions.size(); index++) {
                if (commandExecutions.get(index).id() == started.id()) {
                    commandExecutions.set(index, started.finished(result));
                    selectedExecution = Math.min(selectedExecution, commandExecutions.size() - 1);
                    return;
                }
            }
        }
    }

    private void exportReport() {
        Connection active = connection;
        if (active == null) return;
        status = "Writing diagnostic bundle";
        CompletableFuture.runAsync(() -> {
            try {
                Instant now = Instant.now();
                Path output = Path.of("lazyjvm-report-" + active.target().pid() + "-" + FILE_TIME.format(now) + ".zip");
                SnapshotManifest manifest = new SnapshotManifest("0.1.0-SNAPSHOT", active.target(), now,
                        List.of("Attach API", "JMX MXBeans", "jcmd"), List.of(),
                        List.of("Environment variables and system properties are not exported by default"));
                Path written = new SnapshotExporter().export(output, manifest, history.snapshot(), new LinkedHashMap<>(commandOutputs));
                status = "Report written · " + written;
                event(status);
            } catch (Exception exception) {
                status = "Export failed · " + concise(exception);
                event(status);
            }
        }, workers);
    }

    private void handlePromptKey(int key) {
        if (key == 27) {
            if (prompt == Prompt.SEARCH) {
                if (mode == Mode.PICKER) pickerFilter = searchBefore;
                else commandFilter = searchBefore;
            }
            prompt = Prompt.NONE;
            pendingCommand = null;
            pendingArguments = List.of();
            return;
        }
        if (key == 127 || key == 8) {
            if (!promptText.isEmpty()) promptText = promptText.substring(0, promptText.length() - 1);
            updateLiveFilter();
            return;
        }
        if (key == '\n' || key == '\r') {
            if (prompt == Prompt.SEARCH) {
                if (mode == Mode.PICKER) {
                    pickerFilter = promptText.strip();
                    selectedTarget = 0;
                } else {
                    commandFilter = promptText.strip();
                    selectedCommandRow = 0;
                    commandScroll = 0;
                }
            } else if (prompt == Prompt.CONFIRM && connection != null && pendingCommand != null) {
                String confirmation = connection.target().pid() + " " + pendingCommand.name();
                if (!pendingInvocationFits()) {
                    status = "Confirmation blocked · resize terminal to show full invocation";
                } else if (promptText.equals(confirmation)) {
                    DiagnosticCommand command = pendingCommand;
                    List<String> arguments = pendingArguments;
                    pendingCommand = null;
                    pendingArguments = List.of();
                    executeCommand(command, arguments);
                } else {
                    status = "Confirmation rejected · target and command did not match";
                    pendingCommand = null;
                    pendingArguments = List.of();
                }
            }
            prompt = Prompt.NONE;
            return;
        }
        if (key >= 32 && key < 127) {
            promptText += (char) key;
            updateLiveFilter();
        }
    }

    private void updateLiveFilter() {
        if (prompt != Prompt.SEARCH) return;
        if (mode == Mode.PICKER) {
            pickerFilter = promptText;
            selectedTarget = 0;
        } else {
            commandFilter = promptText;
            selectedCommandRow = 0;
            commandScroll = 0;
        }
    }

    private void draw() {
        int width = detectedSize(terminal.getWidth(), "COLUMNS", 40);
        int height = detectedSize(terminal.getHeight(), "LINES", 12);
        asciiMode = options.ascii() || !StandardCharsets.UTF_8.equals(terminal.encoding());
        uiLanguage = options.language().forTerminal(asciiMode);
        layout = UiLayout.forSize(width, height, focusArea, page == Page.COMMANDS);
        Canvas canvas = new Canvas(width, height, asciiMode);
        drawHeader(canvas);
        if (mode == Mode.PICKER) drawPicker(canvas);
        else if (mode == Mode.CONNECTING) drawConnecting(canvas);
        else if (mode == Mode.MONITOR) drawMonitor(canvas);
        else drawError(canvas);
        drawFooter(canvas);
        if (help) drawHelp(canvas);
        if (prompt != Prompt.NONE) drawPrompt(canvas);
        if (contextualHelp) drawContextualHelp(canvas);
        String frame = canvas.render(colorProfile);
        if (!frame.equals(lastFrame)) {
            terminal.writer().print(frame);
            terminal.writer().flush();
            lastFrame = frame;
        }
    }

    static String renderFixture(int width, int height, boolean ascii) {
        return renderFixture(width, height, ascii, ascii ? UiLanguage.EN : UiLanguage.ZH_CN);
    }

    static String renderFixture(int width, int height, boolean ascii, UiLanguage language) {
        TuiApplication application = new TuiApplication(
                new TuiOptions(Duration.ofSeconds(1), Duration.ofMinutes(60), null, ascii, false, language), null);
        application.asciiMode = ascii;
        application.uiLanguage = language.forTerminal(ascii);
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        Map<MetricKey, MetricPoint> metrics = Map.ofEntries(
                Map.entry(MetricKey.HEAP_USED, new MetricPoint(now, MetricKey.HEAP_USED, 256 * 1024 * 1024, MetricQuality.EXACT, "JMX")),
                Map.entry(MetricKey.HEAP_COMMITTED, new MetricPoint(now, MetricKey.HEAP_COMMITTED, 512 * 1024 * 1024, MetricQuality.EXACT, "JMX")),
                Map.entry(MetricKey.HEAP_MAX, new MetricPoint(now, MetricKey.HEAP_MAX, 1024 * 1024 * 1024, MetricQuality.EXACT, "JMX")),
                Map.entry(MetricKey.PROCESS_CPU, new MetricPoint(now, MetricKey.PROCESS_CPU, 12.5, MetricQuality.EXACT, "JMX")),
                Map.entry(MetricKey.SYSTEM_CPU, new MetricPoint(now, MetricKey.SYSTEM_CPU, 37.0, MetricQuality.EXACT, "JMX")),
                Map.entry(MetricKey.THREADS_LIVE, new MetricPoint(now, MetricKey.THREADS_LIVE, 18, MetricQuality.EXACT, "JMX")),
                Map.entry(MetricKey.CLASSES_LOADED, new MetricPoint(now, MetricKey.CLASSES_LOADED, 2400, MetricQuality.EXACT, "JMX")),
                Map.entry(MetricKey.UPTIME, new MetricPoint(now, MetricKey.UPTIME, 90_000, MetricQuality.EXACT, "JMX")));
        MetricSnapshot sample = new MetricSnapshot(now, metrics,
                List.of(new MemoryPoolSnapshot("G1 Eden Space", "heap", 64, 128, 256)),
                List.of(new GcSnapshot("G1 Young Generation", 12, 48, "G1 Eden Space")),
                new ThreadSnapshot(18, 12, 24, Map.of(Thread.State.RUNNABLE, 8, Thread.State.WAITING, 10), new long[0]),
                CapabilitySet.of(Capability.JMX, Capability.MEMORY_POOLS, Capability.GARBAGE_COLLECTION,
                        Capability.THREADS, Capability.JFR), Duration.ofMillis(8), List.of());
        application.mode = Mode.MONITOR;
        application.focusArea = FocusArea.MAIN;
        application.status = "Live · 8 ms sample";
        application.latest.set(sample);
        application.history.add(sample);
        Canvas canvas = new Canvas(width, height, ascii);
        application.layout = UiLayout.forSize(width, height, application.focusArea, application.page == Page.COMMANDS);
        application.drawHeader(canvas);
        application.drawMonitor(canvas);
        application.drawFooter(canvas);
        return canvas.render(false);
    }

    private void drawHeader(Canvas canvas) {
        canvas.fill(0, 0, canvas.width(), 1, ' ', Style.HEADER);
        canvas.text(1, 0, "LAZYJVM", Style.HEADER);
        String right = mode == Mode.MONITOR
                ? (paused ? ui("已暂停", "PAUSED") : ui("实时", "LIVE"))
                : modeLabel();
        right += "  " + CLOCK.format(Instant.now());
        int rightX = Math.max(9, canvas.width() - Canvas.displayWidth(right) - 1);
        if (canvas.width() >= 60) {
            String target = connection == null ? ui("本地 JVM 发现", "Local JVM discovery") : targetSummary();
            canvas.text(11, 0, Canvas.crop(target, Math.max(0, rightX - 12)), Style.HEADER);
        }
        canvas.text(rightX, 0, Canvas.crop(right, canvas.width() - rightX), Style.HEADER);

        canvas.fill(0, 1, canvas.width(), 1, ' ', Style.PANEL);
        if (mode == Mode.MONITOR) {
            String workspace = connection != null && canvas.width() < 60 ? targetSummary()
                    : ui("工作区", "Workspace") + ": " + page.title(uiLanguage);
            canvas.text(1, 1, Canvas.crop(workspace + "   Tab/Shift+Tab "
                    + ui("切换焦点", "focus") + "   F1 " + ui("帮助", "help"), canvas.width() - 2), Style.MUTED);
        } else {
            canvas.text(1, 1, Canvas.crop(ui("选择可连接 JVM；连接后开始采样。",
                    "Select an attachable JVM. Sampling starts after connection."), canvas.width() - 2), Style.MUTED);
        }
    }

    private void drawPicker(Canvas canvas) {
        int y = 3;
        canvas.text(2, y, ui("本地 JVM", "Local JVMs"), Style.CYAN);
        canvas.text(14, y, pickerFilter.isBlank() ? "" : ui("筛选：", "filter: ") + pickerFilter, Style.AMBER);
        y += 2;
        canvas.text(2, y, ui("  PID       启动时间  用户          主类 / 命令",
                "  PID       START     USER          MAIN CLASS / COMMAND"), Style.MUTED);
        y++;
        List<TargetJvm> visible = visibleTargets();
        if (visible.isEmpty()) {
            canvas.text(4, y + 2, pickerFilter.isBlank() ? ui("没有可连接的 JVM。", "No attachable JVMs found.")
                    : ui("没有匹配筛选条件的 JVM。", "No JVM matches current filter."), Style.YELLOW);
            canvas.text(4, y + 4, ui("按 r 重试发现，按 / 修改筛选。", "Press r to retry discovery or / to change filter."), Style.MUTED);
            return;
        }
        int rows = canvas.height() - y - 3;
        pickerScroll = ScrollModel.follow(selectedTarget, pickerScroll, rows, visible.size());
        int offset = pickerScroll;
        for (int row = 0; row < rows && offset + row < visible.size(); row++) {
            TargetJvm target = visible.get(offset + row);
            boolean selected = offset + row == selectedTarget;
            String line = String.format(Locale.ROOT, "%s %-9d %-9s %-13s %s",
                    selected ? ">" : " ", target.pid(),
                    target.startTime().equals(Instant.EPOCH) ? "?" : CLOCK.format(target.startTime()),
                    Canvas.crop(target.user(), 13), target.displayName());
            canvas.text(2, y + row, Canvas.crop(line, canvas.width() - 4), selected ? Style.SELECTED : Style.NORMAL);
        }
        canvas.text(2, canvas.height() - 2, ScrollModel.hint(pickerScroll, visible.size(), rows, uiLanguage), Style.MUTED);
    }

    private void drawConnecting(Canvas canvas) {
        int width = Math.min(68, canvas.width() - 8);
        int x = (canvas.width() - width) / 2;
        int y = Math.max(4, canvas.height() / 3);
        canvas.box(x, y, width, 7, ui("连接中", "Attaching"), Style.PANEL, asciiMode);
        canvas.text(x + 3, y + 2, Canvas.crop(displayStatus(), width - 6), Style.AMBER);
        canvas.text(x + 3, y + 4, ui("正在协商 Attach、本地 JMX、内存、GC、线程、jcmd 和 JFR。",
                "Negotiating Attach, local JMX, memory, GC, threads, jcmd and JFR."), Style.MUTED);
    }

    private void drawMonitor(Canvas canvas) {
        UiLayout.Rect main = layout.main();
        boolean compact = canvas.width() < 80;
        if (!compact && layout.workspace().width() > 0) {
            drawNavigation(canvas, layout.workspace());
        }
        drawPage(canvas, main.x(), main.y(), main.width(), main.height());
        if (compact && focusArea == FocusArea.WORKSPACE && layout.workspace().width() > 0) {
            drawWorkspacePopup(canvas, layout.workspace());
        }
    }

    private void drawNavigation(Canvas canvas, UiLayout.Rect rect) {
        int x = rect.x(), y = rect.y(), width = rect.width(), height = rect.height();
        Style border = focusArea == FocusArea.WORKSPACE ? Style.GREEN : Style.PANEL;
        canvas.box(x, y, width, height, ui("工作区", "Workspaces"), border, asciiMode);
        layout.workspaceItems().clear();
        for (int index = 0; index < Page.values().length; index++) {
            Page value = Page.values()[index];
            String label = " " + (index + 1) + "  " + value.title(uiLanguage);
            int rowY = y + 2 + index;
            layout.workspaceItems().add(new UiLayout.Rect(x + 1, rowY, Math.max(1, width - 2), 1));
            canvas.text(x + 1, rowY, Canvas.crop(label, width - 2), value == page ? Style.SELECTED : Style.NORMAL);
        }
        MetricSnapshot sample = latest.get();
        if (sample != null && height > 14) {
            canvas.text(x + 2, y + 10, ui("采样器", "Sampler"), Style.MUTED);
            canvas.text(x + 2, y + 12, paused ? ui("|| 已暂停", "|| paused") : ui("● 实时", "● live"), paused ? Style.YELLOW : Style.GREEN);
            canvas.text(x + 2, y + 13, sample.collectionLatency().toMillis() + " ms", Style.NORMAL);
            canvas.text(x + 2, y + 14, history.size() + "/" + history.capacity() + " " + ui("个样本", "samples"), Style.NORMAL);
        }
    }

    private void drawPage(Canvas canvas, int x, int y, int width, int height) {
        Style border = focusArea == FocusArea.MAIN ? Style.GREEN : Style.PANEL;
        canvas.box(x, y, width, height, page.title(uiLanguage), border, asciiMode);
        MetricSnapshot sample = latest.get();
        if (sample == null && page != Page.COMMANDS && page != Page.REPORTS) {
            canvas.text(x + 3, y + 3, ui("等待首个 JMX 样本…", "Waiting for first JMX sample…"), Style.AMBER);
            return;
        }
        switch (page) {
            case OVERVIEW -> drawOverview(canvas, x, y, width, height, sample);
            case MEMORY -> drawMemory(canvas, x, y, width, height, sample);
            case THREADS -> drawThreads(canvas, x, y, width, height, sample);
            case JFR -> drawJfr(canvas, x, y, width, height, sample);
            case COMMANDS -> drawCommands(canvas, x, y, width, height);
            case REPORTS -> drawReports(canvas, x, y, width, height);
        }
    }

    private void drawOverview(Canvas canvas, int x, int y, int width, int height, MetricSnapshot sample) {
        double heap = sample.value(MetricKey.HEAP_USED);
        double heapMax = sample.value(MetricKey.HEAP_MAX);
        double cpu = sample.value(MetricKey.PROCESS_CPU);
        double systemCpu = sample.value(MetricKey.SYSTEM_CPU);
        canvas.text(x + 2, y + 2, Canvas.crop(ui("堆 ", "Heap ") + Format.bytes(heap) + " / " + Format.bytes(heapMax), width / 2 - 2), Style.AMBER);
        canvas.text(x + Math.max(2, width / 2), y + 2,
                Canvas.crop(ui("进程 CPU ", "Process CPU ") + Format.percent(cpu) + " / "
                        + ui("系统 ", "System ") + Format.percent(systemCpu), width / 2 - 2), Style.CYAN);
        if (width < 48 || height < 16) {
            canvas.text(x + 2, y + 5, ui("堆 ", "Heap ") + Format.bar(heapMax > 0 ? heap / heapMax : 0,
                    Math.max(5, width - 18), asciiMode), Style.AMBER);
            canvas.text(x + 2, y + 7, "CPU  " + Format.bar(cpu / 100.0,
                    Math.max(5, width - 18), asciiMode), Style.CYAN);
            canvas.text(x + 2, y + 9, ui("线程 ", "Threads ") + Format.number(sample.value(MetricKey.THREADS_LIVE))
                    + " / " + ui("峰值 ", "peak ") + Format.number(sample.value(MetricKey.THREADS_PEAK)), Style.NORMAL);
            canvas.text(x + 2, y + height - 2, ui("GC 暂停 ", "GC pause ")
                    + Format.number(latestDelta(MetricKey.GC_TIME)) + " " + ui("ms/采样", "ms/sample"), Style.YELLOW);
            return;
        }
        List<Instant> timestamps = history.snapshot().stream().map(MetricSnapshot::timestamp).toList();
        List<Chart.Series> cpuSeries = List.of(
                new Chart.Series(ui("进程", "Process"), series(MetricKey.PROCESS_CPU), Style.CYAN),
                new Chart.Series(ui("系统", "System"), series(MetricKey.SYSTEM_CPU), Style.GREEN));
        List<Chart.Series> heapSeries = List.of(
                new Chart.Series(ui("已用", "Used"), series(MetricKey.HEAP_USED), Style.AMBER),
                new Chart.Series(ui("已提交", "Committed"), series(MetricKey.HEAP_COMMITTED), Style.YELLOW),
                new Chart.Series(ui("上限", "Max"), series(MetricKey.HEAP_MAX), Style.CYAN));
        List<Chart.Series> threadSeries = List.of(
                new Chart.Series(ui("活动", "Live"), series(MetricKey.THREADS_LIVE), Style.CYAN),
                new Chart.Series(ui("峰值", "Peak"), series(MetricKey.THREADS_PEAK), Style.AMBER));
        List<Double> gcPause = deltas(MetricKey.GC_TIME);
        List<Instant> deltaTimes = timestamps.size() <= 1 ? List.of() : timestamps.subList(1, timestamps.size());
        List<Chart.Series> gcSeries = List.of(new Chart.Series(ui("暂停", "Pause"), gcPause, Style.AMBER));
        int cellWidth = Math.max(20, (width - 6) / 2);
        int cellHeight = Math.max(6, (height - 8) / 2);
        boolean grid = width >= 64 && height >= 19;
        if (grid) {
            drawMetricChart(canvas, x + 2, y + 4, cellWidth, cellHeight,
                    ui("进程 CPU / 系统 CPU", "Process CPU / System CPU"), cpuSeries,
                    List.of(MetricKey.PROCESS_CPU, MetricKey.SYSTEM_CPU), timestamps, 0, 100, "%");
            drawMetricChart(canvas, x + 3 + cellWidth, y + 4, cellWidth, cellHeight,
                    ui("已用堆 / 已提交 / 上限", "Heap used / committed / max"), heapSeries,
                    List.of(MetricKey.HEAP_USED, MetricKey.HEAP_COMMITTED, MetricKey.HEAP_MAX), timestamps, 0, finiteOr(heapMax, 1), "bytes");
            drawMetricChart(canvas, x + 2, y + 5 + cellHeight, cellWidth, cellHeight,
                    ui("活动线程 / 峰值", "Live / Peak threads"), threadSeries,
                    List.of(MetricKey.THREADS_LIVE, MetricKey.THREADS_PEAK), timestamps, 0, finiteOr(maxValue(MetricKey.THREADS_PEAK), 1), "threads");
            drawMetricChart(canvas, x + 3 + cellWidth, y + 5 + cellHeight, cellWidth, cellHeight,
                    ui("每次采样 GC 暂停", "GC pause per sample"), gcSeries,
                    List.of(MetricKey.GC_TIME), deltaTimes, 0, finiteOr(maxValue(gcPause), 1), "ms");
        } else {
            int chartHeight = Math.max(5, (height - 8) / 4);
            drawMetricChart(canvas, x + 2, y + 4, width - 4, chartHeight,
                    ui("进程 CPU / 系统 CPU", "Process CPU / System CPU"), cpuSeries,
                    List.of(MetricKey.PROCESS_CPU, MetricKey.SYSTEM_CPU), timestamps, 0, 100, "%");
            drawMetricChart(canvas, x + 2, y + 4 + chartHeight, width - 4, chartHeight,
                    ui("已用堆 / 已提交 / 上限", "Heap used / committed / max"), heapSeries,
                    List.of(MetricKey.HEAP_USED, MetricKey.HEAP_COMMITTED, MetricKey.HEAP_MAX), timestamps, 0, finiteOr(heapMax, 1), "bytes");
            drawMetricChart(canvas, x + 2, y + 4 + chartHeight * 2, width - 4, chartHeight,
                    ui("活动线程 / 峰值", "Live / Peak threads"), threadSeries,
                    List.of(MetricKey.THREADS_LIVE, MetricKey.THREADS_PEAK), timestamps, 0, finiteOr(maxValue(MetricKey.THREADS_PEAK), 1), "threads");
            drawMetricChart(canvas, x + 2, y + 4 + chartHeight * 3, width - 4, chartHeight,
                    ui("每次采样 GC 暂停", "GC pause per sample"), gcSeries,
                    List.of(MetricKey.GC_TIME), deltaTimes, 0, finiteOr(maxValue(gcPause), 1), "ms");
        }
    }

    private void drawMetricChart(Canvas canvas, int x, int y, int width, int height, String title,
                                 List<Chart.Series> series, List<MetricKey> keys, List<Instant> timestamps,
                                 double min, double max, String unit) {
        canvas.text(x, y, Canvas.crop(title + " · " + historyWindow() + " · " + historyQuality(keys) + " · "
                + ui("缺口 ", "gap ") + (asciiMode ? "." : "┈"), width), Style.NORMAL);
        Chart.timeSeries(canvas, x, y + 1, width, Math.max(4, height - 1), series, timestamps,
                min, max, unit, asciiMode);
    }

    private String historyQuality(List<MetricKey> keys) {
        java.util.Set<String> sources = new java.util.TreeSet<>();
        java.util.Set<MetricQuality> qualities = java.util.EnumSet.noneOf(MetricQuality.class);
        for (MetricSnapshot snapshot : history.snapshot()) {
            for (MetricKey key : keys) {
                MetricPoint point = snapshot.metrics().get(key);
                if (point != null) {
                    sources.add(point.source());
                    qualities.add(point.quality());
                }
            }
        }
        String source = sources.isEmpty() ? ui("来源不可用", "source n/a")
                : ui("来源 ", "source ") + String.join(",", sources);
        String quality = qualities.isEmpty() ? ui("质量不可用", "quality n/a")
                : ui("质量 ", "quality ") + qualities.stream().map(this::displayQuality)
                .sorted().reduce((a, b) -> a + "/" + b).orElse(ui("不可用", "n/a"));
        return source + " · " + quality;
    }

    private void drawMemory(Canvas canvas, int x, int y, int width, int height, MetricSnapshot sample) {
        List<MemoryPoolSnapshot> pools = sample.memoryPools();
        if (height < 14) {
            int row = y + 2;
            int visible = Math.max(1, height - 7);
            memoryScroll = ScrollModel.clamp(memoryScroll, pools.size(), visible);
            for (int index = memoryScroll; index < pools.size() && index < memoryScroll + visible; index++) {
                MemoryPoolSnapshot pool = pools.get(index);
                if (row >= y + height - 4) break;
                canvas.text(x + 2, row++, Canvas.crop(pool.name() + "  " + Format.percent(pool.utilization() * 100), width - 4),
                        pool.utilization() >= .9 ? Style.RED : Style.AMBER);
            }
            List<Double> deltas = deltas(MetricKey.GC_TIME);
            double pause = deltas.isEmpty() ? 0 : deltas.get(deltas.size() - 1);
            canvas.text(x + 2, y + height - 3, ui("内存池 " + ScrollModel.hint(memoryScroll, pools.size(), visible, uiLanguage),
                    "Memory pools " + ScrollModel.hint(memoryScroll, pools.size(), visible, uiLanguage)), Style.MUTED);
            canvas.text(x + 2, y + height - 2, ui("最近 GC 暂停 ", "Latest GC pause ")
                    + Format.number(pause) + " ms · JMX", Style.CYAN);
            return;
        }
        int row = y + 2;
        int barWidth = Math.max(8, Math.min(28, width / 3));
        int poolVisible = Math.max(1, Math.min(pools.size(), height - 12));
        memoryScroll = ScrollModel.clamp(memoryScroll, pools.size(), poolVisible);
        for (int index = memoryScroll; index < pools.size() && index < memoryScroll + poolVisible; index++) {
            MemoryPoolSnapshot pool = pools.get(index);
            if (row >= y + height - 11) break;
            canvas.text(x + 2, row, Canvas.crop(pool.name(), Math.max(10, width - barWidth - 22)), Style.NORMAL);
            String bar = Format.bar(pool.utilization(), barWidth, asciiMode);
            Style style = pool.utilization() >= .9 ? Style.RED : pool.utilization() >= .75 ? Style.YELLOW : Style.AMBER;
            canvas.text(x + Math.max(14, width - barWidth - 16), row, bar, style);
            canvas.text(x + width - 14, row, String.format(Locale.ROOT, "%5.1f%%", pool.utilization() * 100), style);
            row++;
        }
        canvas.text(x + 2, row++, ScrollModel.hint(memoryScroll, pools.size(), poolVisible, uiLanguage), Style.MUTED);
        row++;
        canvas.text(x + 2, row++, ui("GC 收集器", "GC collectors"), Style.MUTED);
        for (GcSnapshot collector : sample.garbageCollectors()) {
            if (row >= y + height - 7) break;
            String value = collector.name() + "  " + collector.collectionCount()
                    + ui(" 次收集  ", " runs  ") + collector.collectionTimeMillis() + " ms";
            canvas.text(x + 2, row++, Canvas.crop(value, width - 4), Style.NORMAL);
        }
        List<Double> countDeltas = deltas(MetricKey.GC_COUNT);
        List<Double> timeDeltas = deltas(MetricKey.GC_TIME);
        double latestCount = countDeltas.isEmpty() ? 0 : countDeltas.get(countDeltas.size() - 1);
        double latestTime = timeDeltas.isEmpty() ? 0 : timeDeltas.get(timeDeltas.size() - 1);
        int trendY = y + height - 5;
        canvas.text(x + 2, trendY, ui("最近区间  ", "Latest interval  ") + Format.number(latestCount)
                + ui(" 次收集 · ", " collections · ") + Format.number(latestTime)
                + ui(" ms 暂停", " ms pause"), latestTime > 100 ? Style.YELLOW : Style.CYAN);
        canvas.text(x + 2, trendY + 1, ui("GC 间隔统计 · ms/采样 · ", "GC interval stats · ms/sample · ")
                + historyWindow() + " · JMX", Style.MUTED);
        canvas.text(x + 2, trendY + 2, ui("无坐标图；使用概览查看趋势。", "No area chart; use Overview for the trend."), Style.MUTED);
    }

    private void drawThreads(Canvas canvas, int x, int y, int width, int height, MetricSnapshot sample) {
        ThreadSnapshot threads = sample.threads();
        if (threads == null) {
            canvas.text(x + 2, y + 2, ui("线程数据不可用", "Thread data unavailable"), Style.YELLOW);
            return;
        }
        canvas.text(x + 2, y + 2, ui("活动 ", "Live ") + threads.live() + "   "
                + ui("守护 ", "Daemon ") + threads.daemon() + "   "
                + ui("峰值 ", "Peak ") + threads.peak(), Style.CYAN);
        int row = y + 5;
        int max = threads.states().values().stream().mapToInt(Integer::intValue).max().orElse(1);
        Thread.State[] states = Thread.State.values();
        int visible = Math.max(1, Math.min(states.length, height - 9));
        threadScroll = ScrollModel.clamp(threadScroll, states.length, visible);
        for (int index = threadScroll; index < states.length && index < threadScroll + visible; index++) {
            Thread.State state = states[index];
            int count = threads.states().getOrDefault(state, 0);
            int barWidth = Math.max(5, Math.min(32, width - 28));
            canvas.text(x + 2, row, String.format(Locale.ROOT, "%-14s %6d", state, count), Style.NORMAL);
            canvas.text(x + 24, row, Format.bar((double) count / max, barWidth, asciiMode), state == Thread.State.BLOCKED ? Style.RED : Style.CYAN);
            row++;
            if (row >= y + height - 4) break;
        }
        canvas.text(x + 2, y + height - 4, ui("线程状态 " + ScrollModel.hint(threadScroll, states.length, visible, uiLanguage),
                "Thread states " + ScrollModel.hint(threadScroll, states.length, visible, uiLanguage)), Style.MUTED);
        if (threads.deadlockedThreadIds().length > 0) {
            canvas.text(x + 2, y + height - 2, ui("死锁  ", "DEADLOCK  ") + Arrays.toString(threads.deadlockedThreadIds()), Style.RED);
        } else {
            canvas.text(x + 2, y + height - 2, ui("未检测到 Java 层死锁", "No Java-level deadlock detected"), Style.GREEN);
        }
    }

    private void drawJfr(Canvas canvas, int x, int y, int width, int height, MetricSnapshot sample) {
        boolean available = sample.capabilities().has(Capability.JFR);
        canvas.text(x + 2, y + 2, available ? ui("Flight Recorder 可用", "Flight Recorder available")
                : ui("Flight Recorder 不可用", "Flight Recorder unavailable"), available ? Style.GREEN : Style.YELLOW);
        if (!available) {
            canvas.text(x + 2, y + 4, ui("目标未发布 jdk.management.jfr:type=FlightRecorder。",
                    "Target did not publish jdk.management.jfr:type=FlightRecorder."), Style.NORMAL);
            canvas.text(x + 2, y + 6, ui("此目标无法通过本地 JMX 提供 JFR。",
                    "This target cannot provide JFR through local JMX."), Style.MUTED);
            return;
        }
        if (height < 13) {
            canvas.text(x + 2, y + 4, ui("j/k 选择 · x 执行 · 不自动启动", "j/k select · x run · never automatic"), Style.MUTED);
            List<DiagnosticCommand> compactActions = jfrCommands();
            for (int index = 0; index < compactActions.size() && y + 5 + index < y + height - 1; index++) {
                DiagnosticCommand command = compactActions.get(index);
                String line = (index == selectedJfr ? "> " : "  ") + command.name() + "  "
                        + (runningCommands.contains(command.name()) ? ui("执行中", "RUNNING")
                        : UiText.impact(command.impact(), uiLanguage));
                canvas.text(x + 2, y + 5 + index, Canvas.crop(line, width - 4),
                        index == selectedJfr ? Style.SELECTED : commandStyle(command));
            }
            return;
        }
        canvas.text(x + 2, y + 4, ui("j/k 选择 · x/Enter 执行 · 不自动启动记录",
                "j/k select · x / Enter run · no recording starts automatically"), Style.MUTED);
        List<DiagnosticCommand> actions = jfrCommands();
        int row = y + 6;
        for (int index = 0; index < actions.size() && row < y + height - 5; index++, row++) {
            DiagnosticCommand command = actions.get(index);
            boolean selected = index == selectedJfr;
            String state = runningCommands.contains(command.name()) ? ui("执行中", "RUNNING")
                    : UiText.impact(command.impact(), uiLanguage);
            String line = (selected ? "> " : "  ") + String.format(Locale.ROOT, "%-11s %-10s %s",
                    command.name(), state, UiText.commandDescription(command, uiLanguage));
            canvas.text(x + 2, row, Canvas.crop(line, width - 4), selected ? Style.SELECTED : commandStyle(command));
        }
        if (!actions.isEmpty() && height >= 13) {
            DiagnosticCommand selected = actions.get(Math.min(selectedJfr, actions.size() - 1));
            List<String> arguments = defaultArguments(selected, connection.target());
            canvas.text(x + 2, y + height - 4, ui("精确调用预览", "Exact invocation preview"), Style.MUTED);
            canvas.text(x + 2, y + height - 3,
                    Canvas.crop(commandInvocation(connection.target(), selected, arguments), width - 4), Style.AMBER);
        }
    }

    private void drawCommands(Canvas canvas, int x, int y, int width, int height) {
        List<CommandTreeItem> items = commandTree();
        UiLayout.Rect listRect = layout.commandList();
        UiLayout.Rect outputRect = layout.commandOutput();
        if (listRect.width() <= 0 || outputRect.width() <= 0) {
            return;
        }
        UiLayout.Rect selector = new UiLayout.Rect(outputRect.x() + 2, outputRect.y() + 1,
                Math.max(1, outputRect.width() - 13), 1);
        UiLayout.Rect copy = new UiLayout.Rect(Math.max(outputRect.x() + 2, outputRect.x() + outputRect.width() - 10),
                outputRect.y() + 1, Math.min(8, outputRect.width() - 2), 1);
        UiLayout.Rect body = new UiLayout.Rect(outputRect.x() + 1, outputRect.y() + 3,
                Math.max(1, outputRect.width() - 2), Math.max(1, outputRect.height() - 4));
        layout.setOutput(outputRect, selector, copy, body);
        layout.commandRows().clear();
        layout.outputItems().clear();
        layout.outputItemIndexes().clear();
        Style listBorder = focusArea == FocusArea.MAIN ? Style.GREEN : Style.PANEL;
        Style outputBorder = focusArea == FocusArea.COMMAND_OUTPUT ? Style.GREEN : Style.PANEL;
        canvas.box(listRect.x(), listRect.y(), listRect.width(), listRect.height(), ui("命令树", "Command tree"), listBorder, asciiMode);
        canvas.box(outputRect.x(), outputRect.y(), outputRect.width(), outputRect.height(), ui("命令输出", "Command Output"), outputBorder, asciiMode);
        String filterLabel = commandFilter.isBlank() ? "" : ui(" · 筛选：", " · filter: ") + commandFilter
                + ui(" · c 清除", " · c clears");
        canvas.text(listRect.x() + 2, listRect.y() + 1,
                Canvas.crop(ui("Enter/x：组折叠/执行，双击命令执行", "Enter/x: fold/run, double-click command") + filterLabel,
                        Math.max(1, listRect.width() - 4)), Style.MUTED);
        int visible = listRect.height() < 4 ? 0 : listRect.height() - 3;
        selectedCommandRow = Math.max(0, Math.min(Math.max(0, items.size() - 1), selectedCommandRow));
        commandScroll = ScrollModel.follow(selectedCommandRow, commandScroll, Math.max(1, visible), items.size());
        int row = listRect.y() + 2;
        for (int itemIndex = commandScroll; itemIndex < items.size() && itemIndex < commandScroll + visible; itemIndex++) {
            CommandTreeItem item = items.get(itemIndex);
            boolean selected = itemIndex == selectedCommandRow;
            UiLayout.Rect rowRect = new UiLayout.Rect(listRect.x() + 1, row,
                    Math.max(1, listRect.width() - 2), 1);
            layout.commandRows().add(new UiLayout.CommandHit(itemIndex, item.group(), item.groupHeader(), rowRect));
            if (item.groupHeader()) {
                String marker = collapsedCommandGroups.contains(item.group()) ? "+" : "-";
                String line = (selected ? "> " : "  ") + marker + " " + item.group() + " (" + item.groupSize() + ")";
                canvas.text(rowRect.x() + 1, row, Canvas.crop(line, rowRect.width() - 2),
                        selected ? Style.SELECTED : Style.CYAN);
            } else {
                DiagnosticCommand command = item.command();
                String state = runningCommands.contains(command.name()) ? ui("执行中", "RUN")
                        : UiText.impact(command.impact(), uiLanguage);
                String line = (selected ? "> " : "  ") + String.format(Locale.ROOT, "%-7s %-22s %s",
                        state, command.name(), UiText.commandDescription(command, uiLanguage));
                canvas.text(rowRect.x() + 1, row, Canvas.crop(line, Math.max(1, rowRect.width() - 2)),
                        selected ? Style.SELECTED : commandStyle(command));
            }
            row++;
        }
        if (visible == 0) {
            canvas.text(listRect.x() + 2, listRect.y() + 1,
                    ui("终端高度不足以显示命令行。", "Terminal is too short for command rows."), Style.MUTED);
        } else if (items.isEmpty()) {
            canvas.text(listRect.x() + 2, listRect.y() + 3,
                    ui("没有匹配命令。", "No command matches filter."), Style.YELLOW);
        }
        if (visible > 0) {
            canvas.text(listRect.x() + 2, listRect.y() + listRect.height() - 1,
                    ScrollModel.hint(commandScroll, items.size(), visible, uiLanguage), Style.MUTED);
        }
        drawCommandOutput(canvas, outputRect, selector, copy, body);
        if (outputDropdown) drawOutputDropdown(canvas, outputRect);
    }

    private void drawCommandOutput(Canvas canvas, UiLayout.Rect panel, UiLayout.Rect selector,
                                   UiLayout.Rect copy, UiLayout.Rect body) {
        CommandExecution execution = currentExecution();
        String selectorText = execution == null
                ? ui("暂无执行结果", "No execution result")
                : ui("结果 ", "Result ") + (selectedExecution + 1) + "/" + executionCount() + "  "
                + execution.command().name() + " ▼";
        canvas.text(selector.x(), selector.y(), Canvas.crop(selectorText, selector.width()), Style.CYAN);
        canvas.text(copy.x(), copy.y(), Canvas.crop(ui("[复制]", "[COPY]"), copy.width()), Style.CYAN);
        if (execution == null) {
            canvas.text(body.x() + 1, body.y(), ui("执行命令后，结果会保留在此处。", "Run a command to keep output here."), Style.MUTED);
            return;
        }
        if (execution.running()) {
            canvas.text(body.x() + 1, body.y(), execution.command().name() + " " + ui("执行中", "RUNNING"), Style.AMBER);
            return;
        }
        CommandResult result = execution.result();
        String summary = UiText.impact(execution.command().impact(), uiLanguage)
                + " · " + result.duration().toMillis() + " ms"
                + (result.truncated() ? " · " + ui("已截断", "TRUNCATED") : "")
                + " · " + ui("↑↓ 结果 · PgUp/PgDn 滚动", "↑↓ result · PgUp/PgDn scroll");
        canvas.text(body.x() + 1, body.y(), Canvas.crop(summary, body.width() - 2), commandStyle(execution.command()));
        List<String> lines = OutputNormalizer.lines(result.output(), Math.max(1, body.width() - 2));
        int visible = Math.max(1, body.height() - 2);
        int maximum = Math.max(0, lines.size() - visible);
        outputScroll = Math.max(0, Math.min(outputScroll, maximum));
        for (int index = 0; index < visible && outputScroll + index < lines.size(); index++) {
            canvas.text(body.x() + 1, body.y() + 1 + index, lines.get(outputScroll + index), Style.NORMAL);
        }
        if (maximum > 0) {
            String more = ScrollModel.hint(outputScroll, lines.size(), visible, uiLanguage);
            canvas.text(body.x() + 1, panel.y() + panel.height() - 2, more, Style.MUTED);
        }
    }

    private void drawOutputDropdown(Canvas canvas, UiLayout.Rect panel) {
        List<CommandExecution> executions = executionSnapshot();
        int height = Math.min(Math.max(3, executions.size() + 2), Math.max(3, panel.height() - 1));
        int y = Math.max(panel.y() + 1, panel.y() + panel.height() - height);
        int width = Math.max(1, panel.width() - 2);
        canvas.fill(panel.x() + 1, y, width, height, ' ', Style.DIALOG);
        canvas.box(panel.x() + 1, y, width, height, ui("选择结果", "Select result"), Style.CYAN, asciiMode);
        int visible = Math.max(1, height - 2);
        int start = Math.max(0, Math.min(selectedExecution - visible + 1, executions.size() - visible));
        for (int row = 0; row < visible && start + row < executions.size(); row++) {
            int index = start + row;
            CommandExecution execution = executions.get(index);
            UiLayout.Rect item = new UiLayout.Rect(panel.x() + 2, y + 1 + row, width - 2, 1);
            layout.outputItems().add(item);
            layout.outputItemIndexes().add(index);
            String line = (index == selectedExecution ? "> " : "  ") + (index + 1) + " "
                    + CLOCK.format(execution.startedAt()) + " " + execution.command().name() + " "
                    + (execution.running() ? ui("执行中", "RUNNING")
                    : UiText.impact(execution.command().impact(), uiLanguage));
            canvas.text(item.x(), item.y(), Canvas.crop(line, item.width()), index == selectedExecution ? Style.SELECTED : Style.NORMAL);
        }
    }

    private void drawReports(Canvas canvas, int x, int y, int width, int height) {
        canvas.text(x + 2, y + 2, ui("按 e 在当前目录导出安全诊断 ZIP。",
                "Press e to export a safe diagnostic ZIP in the current directory."), Style.CYAN);
        if (height < 14) {
            canvas.text(x + 2, y + 4, ui("包含：report.md、environment.json、metrics.csv、命令输出",
                    "Includes: report.md, environment.json, metrics.csv, command output"), Style.NORMAL);
            canvas.text(x + 2, y + 6, ui("排除：堆转储、环境变量、完整系统属性",
                    "Excludes: heap dumps, environment variables, full system properties"), Style.MUTED);
            canvas.text(x + 2, y + height - 2, Canvas.crop(displayStatus(), width - 4), Style.AMBER);
            return;
        }
        canvas.text(x + 2, y + 4, ui("包含", "Includes"), Style.MUTED);
        canvas.text(x + 4, y + 5, "report.md  environment.json  metrics.csv  commands/*.txt", Style.NORMAL);
        canvas.text(x + 2, y + 7, ui("默认排除", "Excluded by default"), Style.MUTED);
        canvas.text(x + 4, y + 8, ui("堆转储、环境变量、完整系统属性",
                "heap dumps, environment variables, complete system properties"), Style.NORMAL);
        canvas.text(x + 2, y + 11, ui("最近活动", "Recent activity"), Style.MUTED);
        List<String> events;
        synchronized (eventLog) { events = List.copyOf(eventLog); }
        int visible = Math.max(1, height - 14);
        reportScroll = reportFollowTail ? ScrollModel.maximum(events.size(), visible)
                : ScrollModel.clamp(reportScroll, events.size(), visible);
        int from = reportScroll;
        for (int index = from; index < events.size() && index < from + visible; index++) {
            canvas.text(x + 3, y + 12 + index - from, Canvas.crop(displayEvent(events.get(index)), width - 6), Style.NORMAL);
        }
        canvas.text(x + 2, y + height - 2, ScrollModel.hint(reportScroll, events.size(), visible, uiLanguage), Style.MUTED);
    }

    private void drawWorkspacePopup(Canvas canvas, UiLayout.Rect rect) {
        canvas.fill(rect.x(), rect.y(), rect.width(), rect.height(), ' ', Style.DIALOG);
        canvas.box(rect.x(), rect.y(), rect.width(), rect.height(), ui("工作区", "Workspace"), Style.GREEN, asciiMode);
        canvas.text(rect.x() + 2, rect.y() + 1, ui("j/k 选择，Enter 打开", "j/k select, Enter open"), Style.MUTED);
        layout.workspaceItems().clear();
        for (int index = 0; index < Page.values().length && rect.y() + 3 + index < rect.y() + rect.height() - 1; index++) {
            Page value = Page.values()[index];
            layout.workspaceItems().add(new UiLayout.Rect(rect.x() + 1, rect.y() + 3 + index,
                    Math.max(1, rect.width() - 2), 1));
            canvas.text(rect.x() + 2, rect.y() + 3 + index,
                    (index == page.ordinal() ? "> " : "  ") + value.title(uiLanguage),
                    index == page.ordinal() ? Style.SELECTED : Style.DIALOG);
        }
    }

    private void drawError(Canvas canvas) {
        int width = Math.min(76, canvas.width() - 6);
        int x = (canvas.width() - width) / 2;
        int y = Math.max(3, canvas.height() / 4);
        canvas.box(x, y, width, 10, ui("连接问题", "Connection problem"), Style.RED, asciiMode);
        drawWrapped(canvas, x + 3, y + 2, width - 6, displayError(), Style.NORMAL, 5);
        canvas.text(x + 3, y + 8, ui("Esc：进程列表   r：重试发现   q：退出",
                "Esc: process list   r: retry discovery   q: quit"), Style.CYAN);
    }

    private void drawFooter(Canvas canvas) {
        int y = canvas.height() - 1;
        canvas.fill(0, y, canvas.width(), 1, ' ', Style.FOOTER);
        String keys = mode == Mode.PICKER
                ? ui(" j/k 移动  Enter 连接  / 筛选  r 刷新  F1 帮助  q 退出 ",
                " j/k move  Enter attach  / filter  r refresh  F1 help  q quit ")
                : ui(" Tab 焦点  j/k 移动  Enter/x 执行  双击命令执行  PgUp/PgDn 滚动  y 复制  F1 帮助  q 退出 ",
                " Tab focus  j/k move  Enter/x run  double-click command  PgUp/PgDn scroll  y copy  F1 help  q quit ");
        canvas.text(0, y, Canvas.crop(keys, canvas.width()), Style.FOOTER);
        String focus = ui("焦点 ", "FOCUS ") + focusArea.label(uiLanguage);
        String right = Canvas.crop(focus + " · " + displayStatus(), Math.max(0, canvas.width() / 2));
        canvas.text(Math.max(0, canvas.width() - Canvas.displayWidth(right) - 1), y, right, Style.FOOTER);
    }

    private String displayStatus() {
        if (uiLanguage.isEnglish()) return OutputNormalizer.clean(status);
        if (status.startsWith("Live · ")) return status.replaceFirst("Live · ", "实时 · ").replace(" sample", " 个样本");
        if (status.equals("Discovering local JVMs")) return "正在发现本地 JVM";
        if (status.equals("Live sampling resumed")) return "实时采样已恢复";
        if (status.startsWith("Paused")) return status.replaceFirst("Paused", "已暂停").replace("history retained", "保留历史");
        if (status.startsWith("Running")) return status.replaceFirst("Running", "执行中");
        if (status.startsWith("No attachable JVMs")) return "没有可连接的 JVM";
        if (status.matches("\\d+ local JVMs")) return status.replace(" local JVMs", " 个本地 JVM");
        if (status.equals("Connected via local JMX")) return "已通过本地 JMX 连接";
        if (status.equals("Connected · jcmd version warning")) return "已连接 · jcmd 版本警告";
        if (status.startsWith("Connected")) return status.replaceFirst("Connected", "已连接");
        if (status.startsWith("Returned")) return "已返回进程列表";
        if (status.startsWith("Command filter cleared")) return "命令筛选已清除";
        if (status.startsWith("Copied full output")) return "已复制完整输出";
        if (status.startsWith("No completed output")) return "暂无可复制的完整输出";
        if (status.startsWith("Clipboard unavailable")) return "剪贴板不可用";
        if (status.startsWith("Sample missed")) return status.replaceFirst("Sample missed", "采样失败");
        if (status.startsWith("Discovery failed")) return "发现 JVM 失败";
        if (status.startsWith("Attach failed")) return status.replaceFirst("Attach failed", "连接失败");
        if (status.startsWith("Attaching to PID ")) return status.replaceFirst("Attaching to PID ", "正在连接 PID ");
        if (status.startsWith("Target unavailable")) return "目标不可用";
        if (status.startsWith("Writing diagnostic bundle")) return "正在写入诊断包";
        if (status.startsWith("Report written")) return status.replaceFirst("Report written", "报告已写入");
        if (status.startsWith("Export failed")) return status.replaceFirst("Export failed", "导出失败");
        if (status.startsWith("Confirmation blocked")) return "确认已阻止：请调整终端以显示完整调用";
        if (status.startsWith("Confirmation rejected")) return "确认被拒绝：目标和命令不匹配";
        if (status.contains(" already running")) return status.replace(" already running", " 已在执行");
        if (status.contains(" completed in ")) return status.replace(" completed in ", " 已完成，用时 ");
        if (status.contains(" failed (exit ")) return status.replace(" failed (exit ", " 失败（退出码 ").replace(")", "）");
        if (status.contains(" failed · ")) return status.replace(" failed · ", " 失败：");
        return OutputNormalizer.clean(status);
    }

    private String displayError() {
        if (uiLanguage.isEnglish()) return OutputNormalizer.clean(error);
        String value = OutputNormalizer.clean(error);
        if (value.startsWith("PID ") && value.contains(" is not an attachable local JVM")) {
            return value.replace(" is not an attachable local JVM", " 不是可连接的本地 JVM");
        }
        if (value.startsWith("Target JVM exited.")) return "目标 JVM 已退出。按 Esc 返回进程发现。";
        if (value.contains("Run LazyJVM as the same OS user")) {
            return "连接权限不足。请以目标 JVM 的相同 OS 用户运行 LazyJVM；LazyJVM 不会自动使用 sudo。";
        }
        if (value.contains("target may use -XX:+DisableAttachMechanism")) {
            return "目标可能启用了 -XX:+DisableAttachMechanism，或位于其他 PID namespace。";
        }
        if (value.contains("Confirm the target is a live HotSpot-compatible JVM")) {
            return "请确认目标是当前用户拥有的、仍在运行的 HotSpot 兼容 JVM。";
        }
        return value;
    }

    private void drawHelp(Canvas canvas) {
        int width = Math.min(72, canvas.width() - 6);
        int height = Math.min(20, canvas.height() - 4);
        int x = (canvas.width() - width) / 2;
        int y = (canvas.height() - height) / 2;
        canvas.fill(x, y, width, height, ' ', Style.DIALOG);
        canvas.box(x, y, width, height, ui("帮助", "Help"), Style.CYAN, asciiMode);
        String[] lines = uiLanguage.isEnglish()
                ? new String[]{
                "Tab / Shift+Tab   Focus: Workspace, Main, Command Output",
                "j/k, Up/Down      Move inside focused area",
                "1–6               Quick-open workspace",
                "Enter, x          Open / run selected item",
                "PgUp/PgDn/Home/End Scroll current area; y copies raw output",
                "/                 Live-filter commands; Enter keeps, Esc restores",
                "p pause  r sample  e export  ? contextual help  F1 global help",
                "Mouse             Click selects; double-click runs commands; wheel scrolls",
                "Esc               Close prompt / return to process list   q quit"}
                : new String[]{
                "Tab / Shift+Tab   焦点：工作区、主内容、命令输出",
                "j/k、上下键       在当前区域移动",
                "1–6               快速打开工作区",
                "Enter、x          打开或执行选中项",
                "PgUp/PgDn/Home/End 滚动当前区域；y 复制原始输出",
                "/                 即时筛选命令；Enter 保留，Esc 恢复",
                "p 暂停  r 采样  e 导出  ? 上下文帮助  F1 全局帮助",
                "鼠标              单击选中；双击执行命令；滚轮滚动所在区域",
                "Esc               关闭提示 / 返回进程列表   q 退出"};
        for (int index = 0; index < lines.length && index < height - 4; index++) {
            canvas.text(x + 3, y + 2 + index, Canvas.crop(lines[index], width - 6), Style.DIALOG);
        }
    }

    private void drawPrompt(Canvas canvas) {
        int width = Math.min(78, canvas.width() - 6);
        int contentWidth = Math.max(1, width - 6);
        String invocation = prompt == Prompt.CONFIRM && pendingCommand != null && connection != null
                ? commandInvocation(connection.target(), pendingCommand, pendingArguments) : "";
        List<String> invocationLines = wrap(invocation, contentWidth);
        int desiredHeight = prompt == Prompt.CONFIRM ? 8 + invocationLines.size() : 6;
        int height = Math.min(desiredHeight, canvas.height() - 2);
        int x = (canvas.width() - width) / 2;
        int y = (canvas.height() - height) / 2;
        canvas.fill(x, y, width, height, ' ', Style.DIALOG);
        String title = prompt == Prompt.SEARCH ? ui("筛选", "Filter") : ui("高风险确认", "High-impact confirmation");
        canvas.box(x, y, width, height, title, prompt == Prompt.CONFIRM ? Style.RED : Style.CYAN, asciiMode);
        if (prompt == Prompt.CONFIRM && pendingCommand != null && connection != null) {
            canvas.text(x + 3, y + 2, Canvas.crop(pendingCommand.name() + " · "
                    + UiText.commandDescription(pendingCommand, uiLanguage), width - 6), Style.RED);
            int available = Math.max(0, height - 8);
            if (invocationLines.size() <= available) {
                for (int index = 0; index < invocationLines.size(); index++) {
                    canvas.text(x + 3, y + 3 + index, invocationLines.get(index), Style.AMBER);
                }
            } else {
                canvas.text(x + 3, y + 3, ui("调用内容放不下；请调整终端大小，执行已阻止。",
                        "Invocation does not fit; resize terminal."), Style.YELLOW);
            }
            String required = connection.target().pid() + " " + pendingCommand.name();
            canvas.text(x + 3, y + height - 4, ui("请准确输入：", "Type exactly: ") + required, Style.NORMAL);
            canvas.text(x + 3, y + height - 2, "> " + promptText, Style.CYAN);
        } else {
            canvas.text(x + 3, y + 2, ui("输入即时筛选；Enter 保留；Esc 恢复。",
                    "Type to filter; Enter keeps; Esc restores."), Style.MUTED);
            canvas.text(x + 3, y + 4, "> " + promptText, Style.CYAN);
        }
    }

    private void drawContextualHelp(Canvas canvas) {
        UiText.Help helpText;
        String title;
        if (focusArea == FocusArea.COMMAND_OUTPUT) {
            CommandExecution execution = currentExecution();
            helpText = execution == null ? UiText.page(page.title(uiLanguage), uiLanguage)
                    : UiText.command(execution.command(), uiLanguage);
            title = ui("输出说明", "Output help");
        } else if (page == Page.COMMANDS && selectedCommandItem() != null && !selectedCommandItem().groupHeader()) {
            helpText = UiText.command(selectedCommandItem().command(), uiLanguage);
            title = ui("命令说明", "Command help");
        } else {
            helpText = UiText.page(page.title(uiLanguage), uiLanguage);
            title = ui("页面说明", "Page help");
        }
        int width = Math.min(74, canvas.width() - 6);
        int height = Math.min(13, canvas.height() - 4);
        int x = Math.max(0, (canvas.width() - width) / 2);
        int y = Math.max(2, (canvas.height() - height) / 2);
        canvas.fill(x, y, width, height, ' ', Style.DIALOG);
        canvas.box(x, y, width, height, title, Style.CYAN, asciiMode);
        String[] lines = uiLanguage.isEnglish()
                ? new String[]{helpText.title(), helpText.meaning(), "Value: " + helpText.value(),
                "Source: " + helpText.source(), "Recommendation: " + helpText.recommendation(),
                "Risk: " + helpText.risk(), "Next: " + helpText.nextStep()}
                : new String[]{helpText.title(), helpText.meaning(), "值：" + helpText.value(),
                "来源：" + helpText.source(), "建议：" + helpText.recommendation(),
                "风险：" + helpText.risk(), "下一步：" + helpText.nextStep()};
        for (int index = 0; index < lines.length && index < height - 3; index++) {
            canvas.text(x + 3, y + 2 + index, Canvas.crop(lines[index], width - 6),
                    index == 0 ? Style.CYAN : Style.DIALOG);
        }
    }

    private List<TargetJvm> visibleTargets() {
        if (pickerFilter.isBlank()) return targets;
        String needle = pickerFilter.toLowerCase(Locale.ROOT);
        return targets.stream().filter(target -> (target.displayName() + " " + target.pid() + " " + target.user())
                .toLowerCase(Locale.ROOT).contains(needle)).toList();
    }

    private List<DiagnosticCommand> filteredCommands() {
        Connection active = connection;
        if (active == null) return List.of();
        String needle = commandFilter.toLowerCase(Locale.ROOT);
        return active.commands().stream()
                .filter(command -> !command.name().startsWith("JFR."))
                .filter(command -> commandFilter.isBlank()
                        || (command.name() + " " + command.description()).toLowerCase(Locale.ROOT).contains(needle))
                .sorted(java.util.Comparator.comparing((DiagnosticCommand command) -> commandGroup(command))
                        .thenComparing(commandComparator()))
                .toList();
    }

    private List<CommandGroup> commandGroups() {
        Map<String, List<DiagnosticCommand>> grouped = new TreeMap<>();
        for (DiagnosticCommand command : filteredCommands()) {
            grouped.computeIfAbsent(commandGroup(command), ignored -> new ArrayList<>()).add(command);
        }
        return grouped.entrySet().stream()
                .map(entry -> new CommandGroup(entry.getKey(), entry.getValue().stream().sorted(commandComparator()).toList()))
                .toList();
    }

    private List<CommandTreeItem> commandTree() {
        List<CommandTreeItem> items = new ArrayList<>();
        for (CommandGroup group : commandGroups()) {
            items.add(CommandTreeItem.header(group.name(), group.commands().size()));
            if (!collapsedCommandGroups.contains(group.name())) {
                for (DiagnosticCommand command : group.commands()) {
                    items.add(CommandTreeItem.command(group.name(), command));
                }
            }
        }
        return items;
    }

    private CommandTreeItem selectedCommandItem() {
        List<CommandTreeItem> items = commandTree();
        if (items.isEmpty()) return null;
        selectedCommandRow = Math.max(0, Math.min(items.size() - 1, selectedCommandRow));
        return items.get(selectedCommandRow);
    }

    private int commandVisibleRows() {
        return Math.max(1, layout.commandList().height() - 3);
    }

    private void moveCommandSelection(int delta) {
        List<CommandTreeItem> items = commandTree();
        if (items.isEmpty()) return;
        selectedCommandRow = Math.max(0, Math.min(items.size() - 1, selectedCommandRow + delta));
        commandScroll = ScrollModel.follow(selectedCommandRow, commandScroll, commandVisibleRows(), items.size());
    }

    private void toggleSelectedCommandGroup() {
        CommandTreeItem item = selectedCommandItem();
        if (item == null || !item.groupHeader()) return;
        if (!collapsedCommandGroups.add(item.group())) collapsedCommandGroups.remove(item.group());
        List<CommandTreeItem> items = commandTree();
        selectedCommandRow = Math.min(selectedCommandRow, Math.max(0, items.size() - 1));
        commandScroll = ScrollModel.follow(selectedCommandRow, commandScroll, commandVisibleRows(), items.size());
    }

    private static String commandGroup(DiagnosticCommand command) {
        return commandGroup(command.name());
    }

    private static String commandGroup(String name) {
        int separator = name.indexOf('.');
        return separator > 0 ? name.substring(0, separator) : name;
    }

    private List<DiagnosticCommand> jfrCommands() {
        Connection active = connection;
        if (active == null) return List.of();
        List<String> order = List.of("JFR.check", "JFR.start", "JFR.dump", "JFR.stop");
        return order.stream().map(name -> active.commands().stream()
                        .filter(command -> command.name().equals(name)).findFirst().orElse(null))
                .filter(java.util.Objects::nonNull).toList();
    }

    private static Style commandStyle(DiagnosticCommand command) {
        return commandStyle(command.impact());
    }

    private static Style commandStyle(CommandImpact impact) {
        return impact == CommandImpact.HIGH ? Style.RED
                : impact == CommandImpact.MEDIUM ? Style.YELLOW : Style.GREEN;
    }

    private static java.util.Comparator<DiagnosticCommand> commandComparator() {
        return java.util.Comparator.comparing(DiagnosticCommand::impact).thenComparing(DiagnosticCommand::name);
    }

    private static String commandInvocation(TargetJvm target, DiagnosticCommand command, List<String> arguments) {
        StringBuilder value = new StringBuilder("jcmd ").append(target.pid()).append(' ').append(command.name());
        for (String argument : arguments) value.append(' ').append(displayArgument(argument));
        return value.toString();
    }

    private static String displayArgument(String value) {
        if (value.chars().allMatch(character -> Character.isLetterOrDigit(character)
                || "._-=/<>:".indexOf(character) >= 0)) return value;
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private boolean pendingInvocationFits() {
        if (terminal == null || connection == null || pendingCommand == null) return false;
        int canvasWidth = detectedSize(terminal.getWidth(), "COLUMNS", 40);
        int canvasHeight = detectedSize(terminal.getHeight(), "LINES", 12);
        int dialogWidth = Math.min(78, canvasWidth - 6);
        int lines = wrap(commandInvocation(connection.target(), pendingCommand, pendingArguments),
                Math.max(1, dialogWidth - 6)).size();
        return 8 + lines <= canvasHeight - 2;
    }

    private static List<String> wrap(String value, int width) {
        if (value == null || value.isEmpty()) return List.of();
        List<String> lines = new ArrayList<>();
        String remaining = value;
        while (!remaining.isEmpty()) {
            String part = Canvas.prefix(remaining, width);
            if (part.isEmpty()) break;
            int split = part.length();
            if (split < remaining.length()) {
                int space = part.lastIndexOf(' ');
                if (space > 0) split = space;
            }
            lines.add(remaining.substring(0, split));
            int next = split;
            while (next < remaining.length() && remaining.charAt(next) == ' ') next++;
            remaining = remaining.substring(next);
        }
        return lines;
    }

    private List<Double> series(MetricKey key) {
        return history.snapshot().stream().map(sample -> sample.value(key)).toList();
    }

    private List<Double> seriesFor(List<MetricSnapshot> samples, MetricKey key) {
        return samples.stream().map(sample -> sample.value(key)).toList();
    }

    private List<Double> deltas(MetricKey key) {
        List<Double> source = series(key);
        if (source.size() < 2) return List.of();
        List<Double> result = new ArrayList<>(source.size() - 1);
        for (int index = 1; index < source.size(); index++) {
            double previous = source.get(index - 1);
            double current = source.get(index);
            result.add(Double.isFinite(previous) && Double.isFinite(current) ? Math.max(0, current - previous) : Double.NaN);
        }
        return result;
    }

    private String historyWindow() {
        List<MetricSnapshot> samples = history.snapshot();
        if (samples.size() < 2) return ui("预热中", "warming up");
        Duration visible = Duration.between(samples.get(0).timestamp(), samples.get(samples.size() - 1).timestamp());
        return Format.duration(visible.toMillis());
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) && value > 0 ? value : fallback;
    }

    private double maxValue(MetricKey key) {
        return maxValue(series(key));
    }

    private static double maxValue(List<Double> values) {
        return values.stream().filter(Double::isFinite).mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
    }

    private double latestDelta(MetricKey key) {
        List<Double> values = deltas(key);
        return values.isEmpty() ? Double.NaN : values.get(values.size() - 1);
    }

    private List<CommandExecution> executionSnapshot() {
        synchronized (commandExecutions) {
            return List.copyOf(commandExecutions);
        }
    }

    private int executionCount() {
        synchronized (commandExecutions) {
            return commandExecutions.size();
        }
    }

    private CommandExecution currentExecution() {
        synchronized (commandExecutions) {
            if (commandExecutions.isEmpty()) return null;
            selectedExecution = Math.max(0, Math.min(selectedExecution, commandExecutions.size() - 1));
            return commandExecutions.get(selectedExecution);
        }
    }

    private void selectExecution(int delta) {
        synchronized (commandExecutions) {
            if (commandExecutions.isEmpty()) return;
            selectedExecution = Math.max(0, Math.min(commandExecutions.size() - 1,
                    (selectedExecution < 0 ? commandExecutions.size() - 1 : selectedExecution) + delta));
        }
        outputScroll = 0;
    }

    private void scrollMain(int delta) {
        if (page == Page.COMMANDS) {
            List<CommandTreeItem> items = commandTree();
            commandScroll = ScrollModel.clamp(commandScroll + delta, items.size(), commandVisibleRows());
            selectedCommandRow = ScrollModel.follow(selectedCommandRow, commandScroll, commandVisibleRows(), items.size());
        } else if (page == Page.MEMORY) {
            int total = latest.get() == null ? 0 : latest.get().memoryPools().size();
            memoryScroll = ScrollModel.clamp(memoryScroll + delta, total, memoryVisibleRows());
        } else if (page == Page.THREADS) {
            threadScroll = ScrollModel.clamp(threadScroll + delta, Thread.State.values().length, threadVisibleRows());
        } else if (page == Page.REPORTS) {
            reportFollowTail = false;
            reportScroll = ScrollModel.clamp(reportScroll + delta, eventCount(), reportVisibleRows());
        } else if (page == Page.OVERVIEW) {
            int total = Math.max(0, history.size());
            int visible = Math.max(1, layout.main().height() - 8);
            overviewScroll = ScrollModel.clamp(overviewScroll + delta, total, visible);
        }
    }

    private void scrollMainPage(int pages) {
        if (page == Page.COMMANDS) {
            List<CommandTreeItem> items = commandTree();
            if (pages == Integer.MIN_VALUE) selectedCommandRow = 0;
            else if (pages == Integer.MAX_VALUE) selectedCommandRow = Math.max(0, items.size() - 1);
            else selectedCommandRow = Math.max(0, Math.min(Math.max(0, items.size() - 1),
                    selectedCommandRow + pages * commandVisibleRows()));
            commandScroll = ScrollModel.follow(selectedCommandRow, commandScroll, commandVisibleRows(), items.size());
        } else if (page == Page.MEMORY) {
            int total = latest.get() == null ? 0 : latest.get().memoryPools().size();
            memoryScroll = ScrollModel.movePage(memoryScroll, pages, total, memoryVisibleRows());
        } else if (page == Page.THREADS) {
            threadScroll = ScrollModel.movePage(threadScroll, pages, Thread.State.values().length, threadVisibleRows());
        } else if (page == Page.REPORTS) {
            reportFollowTail = false;
            reportScroll = ScrollModel.movePage(reportScroll, pages, eventCount(), reportVisibleRows());
        } else if (page == Page.OVERVIEW) {
            overviewScroll = ScrollModel.movePage(overviewScroll, pages, history.size(), Math.max(1, layout.main().height() - 8));
        }
    }

    private int memoryVisibleRows() {
        return Math.max(1, layout.main().height() - 12);
    }

    private int pickerVisibleRows() {
        return Math.max(1, detectedSize(terminal == null ? 12 : terminal.getHeight(), "LINES", 12) - 7);
    }

    private int threadVisibleRows() {
        return Math.max(1, Math.min(Thread.State.values().length, layout.main().height() - 9));
    }

    private int reportVisibleRows() {
        return Math.max(1, layout.main().height() - 16);
    }

    private int eventCount() {
        synchronized (eventLog) {
            return eventLog.size();
        }
    }

    private void scrollOutput(int pages) {
        CommandExecution execution = currentExecution();
        if (execution == null || execution.running()) return;
        int width = Math.max(1, layout.outputBody().width() - 2);
        int visible = Math.max(1, layout.outputBody().height() - 2);
        int total = OutputNormalizer.lines(execution.result().output(), width).size();
        outputScroll = ScrollModel.movePage(outputScroll, pages, total, visible);
    }

    private void copyCurrentOutput() {
        CommandExecution execution = currentExecution();
        if (execution == null || execution.running()) {
            status = uiLanguage.isEnglish() ? "No completed output to copy" : "暂无可复制的完整输出";
            return;
        }
        if (Clipboard.copy(terminal, execution.result().output())) {
            status = uiLanguage.isEnglish() ? "Copied full output via OSC 52" : "已通过 OSC 52 复制完整输出";
        } else {
            status = uiLanguage.isEnglish() ? "Clipboard unavailable" : "剪贴板不可用";
        }
    }

    private void backToPicker() {
        closeConnection();
        latest.set(null);
        commandOutputs.clear();
        synchronized (commandExecutions) { commandExecutions.clear(); }
        selectedExecution = -1;
        outputScroll = 0;
        outputDropdown = false;
        reportFollowTail = true;
        paused = false;
        runningCommands.clear();
        commandFilter = "";
        mode = Mode.PICKER;
        status = "Returned to local JVM discovery";
        refreshTargets();
    }

    private void closeConnection() {
        ScheduledFuture<?> task = samplingTask;
        samplingTask = null;
        if (task != null) task.cancel(false);
        Connection active = connection;
        connection = null;
        if (active != null) {
            try {
                active.collector().close();
            } catch (Exception exception) {
                event("Detach warning: " + concise(exception));
            }
        }
    }

    private void event(String message) {
        synchronized (eventLog) {
            eventLog.add(CLOCK.format(Instant.now()) + "  " + message);
            while (eventLog.size() > 200) eventLog.remove(0);
        }
    }

    private InputEvent readInputEvent() throws IOException {
        int first = terminal.reader().read(80L);
        if (first < 0) return null;
        if (first != 27) return InputEvent.key(first);
        int second = terminal.reader().read(4L);
        if (second == 'O') {
            int function = terminal.reader().read(4L);
            return function == 'P' ? InputEvent.key(KEY_F1) : InputEvent.key(27);
        }
        if (second != '[') return InputEvent.key(27);
        int third = terminal.reader().read(4L);
        if (third == '<') return readMouseEvent();
        if (third == 'A') return InputEvent.key(KEY_UP);
        if (third == 'B') return InputEvent.key(KEY_DOWN);
        if (third == 'C') return InputEvent.key(KEY_RIGHT);
        if (third == 'D') return InputEvent.key(KEY_LEFT);
        if (third == 'Z') return InputEvent.key(KEY_SHIFT_TAB);
        if (third == 'H') return InputEvent.key(KEY_HOME);
        if (third == 'F') return InputEvent.key(KEY_END);
        if (third == '1' || third == '2' || third == '3' || third == '4' || third == '5' || third == '6') {
            StringBuilder sequence = new StringBuilder().append((char) third);
            int value;
            while ((value = terminal.reader().read(4L)) >= 0) {
                sequence.append((char) value);
                if (value == '~') break;
                if (sequence.length() > 8) break;
            }
            return switch (sequence.toString()) {
                case "1~" -> InputEvent.key(KEY_HOME);
                case "4~" -> InputEvent.key(KEY_END);
                case "5~" -> InputEvent.key(KEY_PAGE_UP);
                case "6~" -> InputEvent.key(KEY_PAGE_DOWN);
                case "11~" -> InputEvent.key(KEY_F1);
                case "1;2Z" -> InputEvent.key(KEY_SHIFT_TAB);
                default -> InputEvent.key(27);
            };
        }
        return InputEvent.key(27);
    }

    private InputEvent readMouseEvent() throws IOException {
        StringBuilder sequence = new StringBuilder();
        int value;
        char end = 'm';
        while ((value = terminal.reader().read(20L)) >= 0) {
            char character = (char) value;
            if (character == 'M' || character == 'm') {
                end = character;
                break;
            }
            sequence.append(character);
            if (sequence.length() > 40) return InputEvent.key(27);
        }
        String[] fields = sequence.toString().split(";");
        if (fields.length != 3) return InputEvent.key(27);
        try {
            int button = Integer.parseInt(fields[0]);
            int x = Math.max(0, Integer.parseInt(fields[1]) - 1);
            int y = Math.max(0, Integer.parseInt(fields[2]) - 1);
            return InputEvent.mouse(new InputEvent.Mouse(button, x, y, end == 'm',
                    (button & 4) != 0, (button & 8) != 0, (button & 16) != 0));
        } catch (NumberFormatException exception) {
            return InputEvent.key(27);
        }
    }

    private static Key toKey(int value) {
        return switch (value) {
            case KEY_UP -> Key.UP;
            case KEY_DOWN -> Key.DOWN;
            case KEY_LEFT -> Key.LEFT;
            case KEY_RIGHT -> Key.RIGHT;
            case KEY_SHIFT_TAB -> Key.SHIFT_TAB;
            case KEY_PAGE_UP -> Key.PAGE_UP;
            case KEY_PAGE_DOWN -> Key.PAGE_DOWN;
            case KEY_HOME -> Key.HOME;
            case KEY_END -> Key.END;
            case 27 -> Key.ESCAPE;
            default -> Key.OTHER;
        };
    }

    private static List<String> defaultArguments(DiagnosticCommand command, TargetJvm target) {
        String time = FILE_TIME.format(Instant.now());
        return switch (command.name()) {
            case "Thread.print" -> List.of("-l");
            case "GC.heap_dump" -> List.of(Path.of(System.getProperty("java.io.tmpdir"),
                    "lazyjvm-heap-" + target.pid() + "-" + time + ".hprof").toString());
            case "JFR.start" -> List.of("name=lazyjvm", "settings=profile", "duration=60s",
                    "filename=" + Path.of(System.getProperty("java.io.tmpdir"), "lazyjvm-" + target.pid() + "-" + time + ".jfr"));
            case "JFR.dump", "JFR.stop" -> List.of("name=lazyjvm");
            default -> List.of();
        };
    }

    private static Duration timeout(DiagnosticCommand command) {
        return switch (command.impact()) {
            case LOW -> Duration.ofSeconds(15);
            case MEDIUM -> Duration.ofSeconds(45);
            case HIGH -> Duration.ofMinutes(5);
        };
    }

    private static String attachRecovery(Exception exception) {
        String base = concise(exception);
        String lower = base.toLowerCase(Locale.ROOT);
        if (lower.contains("permission") || lower.contains("operation not permitted")) {
            return base + ". Run LazyJVM as the same OS user as the target. LazyJVM will not use sudo automatically.";
        }
        if (lower.contains("attach") || lower.contains("socket")) {
            return base + ". The target may use -XX:+DisableAttachMechanism or live in another PID namespace.";
        }
        return base + ". Confirm the target is a live HotSpot-compatible JVM owned by the current user.";
    }

    private String ui(String chinese, String english) {
        return uiLanguage.isEnglish() ? english : chinese;
    }

    private String modeLabel() {
        return switch (mode) {
            case PICKER -> ui("选择 JVM", "SELECT");
            case CONNECTING -> ui("连接中", "ATTACHING");
            case MONITOR -> ui("实时", "LIVE");
            case ERROR -> ui("错误", "ERROR");
        };
    }

    private String displayQuality(MetricQuality quality) {
        if (uiLanguage.isEnglish()) return quality.name().toLowerCase(Locale.ROOT);
        return switch (quality) {
            case EXACT -> "精确";
            case ESTIMATED -> "估算";
            case UNAVAILABLE -> "不可用";
        };
    }

    private String targetSummary() {
        Connection active = connection;
        if (active == null) return ui("本地 JVM 发现", "Local JVM discovery");
        TargetJvm target = active.target();
        return "PID " + target.pid() + " · " + ui("主类 ", "Main ") + target.mainClass()
                + " · JDK " + target.jdk().version() + " · " + ui("已连接", "Connected");
    }

    private String displayEvent(String value) {
        if (uiLanguage.isEnglish()) return OutputNormalizer.clean(value);
        String event = OutputNormalizer.clean(value);
        if (event.contains("  Command started: ")) return event.replace("  Command started: ", "  命令开始：");
        if (event.contains("  Connected to ")) return event.replace("  Connected to ", "  已连接：");
        if (event.contains("  Attach requested for ")) return event.replace("  Attach requested for ", "  请求连接：");
        if (event.contains("  Attach failed: ")) return event.replace("  Attach failed: ", "  连接失败：");
        if (event.contains("  jcmd catalog unavailable: ")) return event.replace("  jcmd catalog unavailable: ", "  jcmd 命令目录不可用：");
        if (event.contains("  Detach warning: ")) return event.replace("  Detach warning: ", "  断开警告：");
        if (event.contains("  Sample missed")) return event.replace("  Sample missed", "  采样失败");
        if (event.contains("  Report written · ")) return event.replace("  Report written · ", "  报告已写入 · ");
        if (event.contains("  Export failed · ")) return event.replace("  Export failed · ", "  导出失败 · ");
        if (event.contains(" completed in ")) return event.replace(" completed in ", " 已完成，用时 ");
        if (event.contains(" failed (exit ")) return event.replace(" failed (exit ", " 失败（退出码 ").replace(")", "）");
        if (event.contains(" failed · ")) return event.replace(" failed · ", " 失败：");
        return event;
    }

    private static String concise(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        String message = current.getMessage();
        return current.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static int detectedSize(int terminalValue, String environmentName, int minimum) {
        int environmentValue = 0;
        try {
            environmentValue = Integer.parseInt(System.getenv().getOrDefault(environmentName, "0"));
        } catch (NumberFormatException ignored) {
            // Terminal-reported size remains authoritative.
        }
        int detected = terminalValue <= minimum && environmentValue > terminalValue ? environmentValue : terminalValue;
        return Math.max(minimum, detected);
    }

    private static void drawWrapped(Canvas canvas, int x, int y, int width, String text, Style style, int maxLines) {
        List<String> lines = wrap(text == null ? "" : text, width);
        for (int line = 0; line < maxLines && line < lines.size(); line++) {
            canvas.text(x, y + line, lines.get(line).strip(), style);
        }
    }

    @Override
    public void close() {
        running = false;
        closeConnection();
        workers.shutdownNow();
        if (terminal != null) {
            try {
                terminal.writer().print("\033[?1006l\033[?1000l\033[0m\033[?25h\033[?1049l");
                terminal.writer().flush();
                terminal.close();
            } catch (Exception ignored) {
                // Best-effort restoration; shutdown hook in distribution repeats ANSI recovery.
            }
            terminal = null;
        }
    }

    private static final int KEY_UP = -1001;
    private static final int KEY_DOWN = -1002;
    private static final int KEY_LEFT = -1003;
    private static final int KEY_RIGHT = -1004;
    private static final int KEY_SHIFT_TAB = -1005;
    private static final int KEY_PAGE_UP = -1006;
    private static final int KEY_PAGE_DOWN = -1007;
    private static final int KEY_HOME = -1008;
    private static final int KEY_END = -1009;
    private static final int KEY_F1 = -1010;

    private enum Key { UP, DOWN, LEFT, RIGHT, SHIFT_TAB, PAGE_UP, PAGE_DOWN, HOME, END, ESCAPE, OTHER }
    private record CommandGroup(String name, List<DiagnosticCommand> commands) {}
    private record CommandTreeItem(String group, DiagnosticCommand command, boolean groupHeader, int groupSize) {
        static CommandTreeItem header(String group, int size) {
            return new CommandTreeItem(group, null, true, size);
        }

        static CommandTreeItem command(String group, DiagnosticCommand command) {
            return new CommandTreeItem(group, command, false, 0);
        }
    }
    private record Connection(TargetJvm target, JmxCollector collector, JcmdExecutor jcmd,
                              List<DiagnosticCommand> commands) {}
}
