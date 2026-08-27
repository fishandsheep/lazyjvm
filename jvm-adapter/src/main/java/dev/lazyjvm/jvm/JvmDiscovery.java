package dev.lazyjvm.jvm;

import dev.lazyjvm.domain.JdkIdentity;
import dev.lazyjvm.domain.TargetJvm;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class JvmDiscovery {
    public List<TargetJvm> discover() {
        Map<Long, AttachSupport.Descriptor> descriptors = new HashMap<>();
        final List<AttachSupport.Descriptor> attachDescriptors;
        try {
            attachDescriptors = AttachSupport.list();
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to list local JVMs", failure);
        }
        for (AttachSupport.Descriptor descriptor : attachDescriptors) {
            try {
                descriptors.put(Long.parseLong(descriptor.id()), descriptor);
            } catch (NumberFormatException ignored) {
                // Non-numeric provider identifiers cannot be targeted by the PID-based UI.
            }
        }

        List<TargetJvm> targets = new ArrayList<>();
        for (Map.Entry<Long, AttachSupport.Descriptor> entry : descriptors.entrySet()) {
            long pid = entry.getKey();
            if (pid == currentPid()) continue;
            ProcessDetails details = processDetails(pid);
            String commandLine = details.commandLine;
            String display = entry.getValue().displayName();
            String mainClass = firstToken(display);
            if ("unknown".equals(mainClass)) mainClass = firstToken(commandLine);
            targets.add(new TargetJvm(
                    pid,
                    isBlank(display) ? mainClass : display,
                    mainClass,
                    String.join(" ", details.arguments),
                    details.user,
                    details.startTime,
                    locateJdk(pid, commandLine),
                    isContainerized(pid)));
        }
        targets.sort(Comparator.comparing(TargetJvm::startTime).reversed().thenComparingLong(TargetJvm::pid));
        return Collections.unmodifiableList(targets);
    }

    public Optional<TargetJvm> find(long pid) {
        return discover().stream().filter(target -> target.pid() == pid).findFirst();
    }

    private static String firstToken(String text) {
        if (isBlank(text)) return "unknown";
        int space = text.indexOf(' ');
        return space < 0 ? text : text.substring(0, space);
    }

    private static JdkIdentity locateJdk(long pid, String commandLine) {
        Path executable = Paths.get("/proc", Long.toString(pid), "exe");
        try {
            Path java = Files.readSymbolicLink(executable);
            if (!java.isAbsolute()) java = executable.getParent().resolve(java).normalize();
            Path bin = java.getParent();
            if (bin != null && bin.getParent() != null) {
                return new JdkIdentity(bin.getParent(), "unknown", "unknown", true);
            }
        } catch (Exception ignored) {
            // /proc is Linux-specific. macOS is refined after Attach returns java.home.
        }
        if (!isBlank(commandLine)) {
            String command = commandLine.split("\\s+", 2)[0];
            Path java = Paths.get(command);
            if (java.isAbsolute() && java.getParent() != null && java.getParent().getParent() != null) {
                return new JdkIdentity(java.getParent().getParent(), "unknown", "unknown", false);
            }
        }
        return JdkIdentity.unknown();
    }

    private static boolean isContainerized(long pid) {
        Path cgroup = Paths.get("/proc", Long.toString(pid), "cgroup");
        try {
            String value = new String(Files.readAllBytes(cgroup), StandardCharsets.UTF_8);
            return value.contains("docker") || value.contains("kubepods") || value.contains("containerd");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static long currentPid() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        int separator = runtimeName.indexOf('@');
        String value = separator < 0 ? runtimeName : runtimeName.substring(0, separator);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static ProcessDetails processDetails(long pid) {
        try {
            Class<?> processHandleClass = Class.forName("java.lang.ProcessHandle");
            Optional<?> optionalHandle = (Optional<?>) processHandleClass
                    .getMethod("of", long.class).invoke(null, pid);
            if (!optionalHandle.isPresent()) return ProcessDetails.empty();

            Object info = processHandleClass.getMethod("info").invoke(optionalHandle.get());
            Class<?> infoClass = Class.forName("java.lang.ProcessHandle$Info");
            return new ProcessDetails(
                    optionalString(infoClass.getMethod("commandLine").invoke(info), ""),
                    optionalArguments(infoClass.getMethod("arguments").invoke(info)),
                    optionalString(infoClass.getMethod("user").invoke(info), "unknown"),
                    optionalInstant(infoClass.getMethod("startInstant").invoke(info)));
        } catch (Exception ignored) {
            String commandLine = readCommandLine(pid);
            return new ProcessDetails(commandLine, arguments(commandLine), "unknown", Instant.EPOCH);
        }
    }

    private static String optionalString(Object value, String fallback) {
        if (!(value instanceof Optional)) return fallback;
        Optional<?> optional = (Optional<?>) value;
        return optional.isPresent() ? String.valueOf(optional.get()) : fallback;
    }

    private static String[] optionalArguments(Object value) {
        if (!(value instanceof Optional)) return new String[0];
        Optional<?> optional = (Optional<?>) value;
        if (!optional.isPresent() || !(optional.get() instanceof String[])) return new String[0];
        return ((String[]) optional.get()).clone();
    }

    private static Instant optionalInstant(Object value) {
        if (!(value instanceof Optional)) return Instant.EPOCH;
        Optional<?> optional = (Optional<?>) value;
        return optional.isPresent() && optional.get() instanceof Instant
                ? (Instant) optional.get() : Instant.EPOCH;
    }

    private static String readCommandLine(long pid) {
        Path commandLine = Paths.get("/proc", Long.toString(pid), "cmdline");
        try {
            byte[] bytes = Files.readAllBytes(commandLine);
            String value = new String(bytes, StandardCharsets.UTF_8);
            return value.replace('\0', ' ').trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String[] arguments(String commandLine) {
        if (isBlank(commandLine)) return new String[0];
        String[] values = commandLine.trim().split("\\s+");
        if (values.length <= 1) return new String[0];
        String[] result = new String[values.length - 1];
        System.arraycopy(values, 1, result, 0, result.length);
        return result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class ProcessDetails {
        private final String commandLine;
        private final String[] arguments;
        private final String user;
        private final Instant startTime;

        private ProcessDetails(String commandLine, String[] arguments, String user, Instant startTime) {
            this.commandLine = commandLine == null ? "" : commandLine;
            this.arguments = arguments == null ? new String[0] : arguments;
            this.user = user == null ? "unknown" : user;
            this.startTime = startTime == null ? Instant.EPOCH : startTime;
        }

        private static ProcessDetails empty() {
            return new ProcessDetails("", new String[0], "unknown", Instant.EPOCH);
        }
    }
}
