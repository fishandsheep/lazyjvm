package dev.lazyjvm.jvm;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import dev.lazyjvm.domain.JdkIdentity;
import dev.lazyjvm.domain.TargetJvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class JvmDiscovery {
    public List<TargetJvm> discover() {
        Map<Long, VirtualMachineDescriptor> descriptors = new HashMap<>();
        for (VirtualMachineDescriptor descriptor : VirtualMachine.list()) {
            try {
                descriptors.put(Long.parseLong(descriptor.id()), descriptor);
            } catch (NumberFormatException ignored) {
                // Non-numeric provider identifiers cannot be targeted by the PID-based UI.
            }
        }

        List<TargetJvm> targets = new ArrayList<>();
        for (Map.Entry<Long, VirtualMachineDescriptor> entry : descriptors.entrySet()) {
            long pid = entry.getKey();
            if (pid == ProcessHandle.current().pid()) continue;
            Optional<ProcessHandle> handle = ProcessHandle.of(pid);
            ProcessHandle.Info info = handle.map(ProcessHandle::info).orElse(null);
            String commandLine = info == null ? "" : info.commandLine().orElse("");
            String[] arguments = info == null ? new String[0] : info.arguments().orElse(new String[0]);
            String display = entry.getValue().displayName();
            String mainClass = firstToken(display);
            targets.add(new TargetJvm(
                    pid,
                    display.isBlank() ? mainClass : display,
                    mainClass,
                    String.join(" ", arguments),
                    info == null ? "unknown" : info.user().orElse("unknown"),
                    info == null ? Instant.EPOCH : info.startInstant().orElse(Instant.EPOCH),
                    locateJdk(pid, commandLine),
                    isContainerized(pid)));
        }
        targets.sort(Comparator.comparing(TargetJvm::startTime).reversed().thenComparingLong(TargetJvm::pid));
        return List.copyOf(targets);
    }

    public Optional<TargetJvm> find(long pid) {
        return discover().stream().filter(target -> target.pid() == pid).findFirst();
    }

    private static String firstToken(String text) {
        if (text == null || text.isBlank()) return "unknown";
        int space = text.indexOf(' ');
        return space < 0 ? text : text.substring(0, space);
    }

    private static JdkIdentity locateJdk(long pid, String commandLine) {
        Path executable = Path.of("/proc", Long.toString(pid), "exe");
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
        if (commandLine != null && !commandLine.isBlank()) {
            String command = commandLine.split("\\s+", 2)[0];
            Path java = Path.of(command);
            if (java.isAbsolute() && java.getParent() != null && java.getParent().getParent() != null) {
                return new JdkIdentity(java.getParent().getParent(), "unknown", "unknown", false);
            }
        }
        return JdkIdentity.unknown();
    }

    private static boolean isContainerized(long pid) {
        Path cgroup = Path.of("/proc", Long.toString(pid), "cgroup");
        try {
            String value = Files.readString(cgroup);
            return value.contains("docker") || value.contains("kubepods") || value.contains("containerd");
        } catch (Exception ignored) {
            return false;
        }
    }
}
