package dev.lazyjvm.jvm;

import dev.lazyjvm.domain.CommandImpact;
import dev.lazyjvm.domain.DiagnosticCommand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

public final class JcmdCatalog {
    private static final Pattern COMMAND_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.]+(?:_old)?");

    public List<DiagnosticCommand> parse(String output) {
        List<DiagnosticCommand> commands = new ArrayList<>();
        for (String line : output.split("\\R")) {
            String candidate = line.trim();
            if (!COMMAND_NAME.matcher(candidate).matches() || candidate.equalsIgnoreCase("help")) continue;
            commands.add(new DiagnosticCommand(candidate, description(candidate), impact(candidate), java.util.Collections.<String>emptyList()));
        }
        return commands.stream().distinct().sorted(Comparator.comparing(DiagnosticCommand::name))
                .collect(Collectors.toList());
    }

    public List<DiagnosticCommand> fallback() {
        return Arrays.asList("VM.version", "VM.command_line", "VM.flags", "GC.heap_info", "Thread.print",
                        "GC.class_histogram", "JFR.check", "JFR.start", "JFR.dump", "JFR.stop", "GC.run", "GC.heap_dump")
                .stream().map(name -> new DiagnosticCommand(name, description(name), impact(name),
                        java.util.Collections.<String>emptyList())).collect(Collectors.toList());
    }

    public static CommandImpact impact(String name) {
        if (name.equals("GC.run") || name.equals("GC.heap_dump") || name.startsWith("VM.set_flag")
                || name.equals("JVMTI.agent_load") || name.startsWith("JFR.start")) return CommandImpact.HIGH;
        if (name.contains("class_histogram") || name.startsWith("Compiler.")) {
            return CommandImpact.MEDIUM;
        }
        return CommandImpact.LOW;
    }

    private static String description(String name) {
        if (name.equals("Thread.print")) return "Print thread stacks and lock information";
        if (name.equals("GC.heap_info")) return "Show current heap and collector summary";
        if (name.equals("GC.class_histogram")) return "Count live heap objects by class";
        if (name.equals("GC.heap_dump")) return "Write a potentially large HPROF heap dump";
        if (name.equals("GC.run")) return "Request a full garbage collection";
        if (name.equals("VM.flags")) return "Show active JVM flags";
        if (name.equals("VM.command_line")) return "Show the target launch command";
        if (name.equals("VM.version")) return "Show JVM version and build";
        if (name.equals("JFR.check")) return "List active Flight Recorder recordings";
        if (name.equals("JFR.start")) return "Start a Flight Recorder recording";
        if (name.equals("JFR.dump")) return "Write recording data to a JFR file";
        if (name.equals("JFR.stop")) return "Stop a Flight Recorder recording";
        return "Diagnostic command reported by the target JVM";
    }
}
