package dev.lazyjvm.jvm;

import dev.lazyjvm.domain.CommandImpact;
import dev.lazyjvm.domain.DiagnosticCommand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public final class JcmdCatalog {
    private static final Pattern COMMAND_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.]+(?:_old)?");

    public List<DiagnosticCommand> parse(String output) {
        List<DiagnosticCommand> commands = new ArrayList<>();
        for (String line : output.split("\\R")) {
            String candidate = line.strip();
            if (!COMMAND_NAME.matcher(candidate).matches() || candidate.equalsIgnoreCase("help")) continue;
            commands.add(new DiagnosticCommand(candidate, description(candidate), impact(candidate), List.of()));
        }
        return commands.stream().distinct().sorted(Comparator.comparing(DiagnosticCommand::name)).toList();
    }

    public List<DiagnosticCommand> fallback() {
        return List.of("VM.version", "VM.command_line", "VM.flags", "GC.heap_info", "Thread.print",
                        "GC.class_histogram", "JFR.check", "JFR.start", "JFR.dump", "JFR.stop", "GC.run", "GC.heap_dump")
                .stream().map(name -> new DiagnosticCommand(name, description(name), impact(name), List.of())).toList();
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
        return switch (name) {
            case "Thread.print" -> "Print thread stacks and lock information";
            case "GC.heap_info" -> "Show current heap and collector summary";
            case "GC.class_histogram" -> "Count live heap objects by class";
            case "GC.heap_dump" -> "Write a potentially large HPROF heap dump";
            case "GC.run" -> "Request a full garbage collection";
            case "VM.flags" -> "Show active JVM flags";
            case "VM.command_line" -> "Show the target launch command";
            case "VM.version" -> "Show JVM version and build";
            case "JFR.check" -> "List active Flight Recorder recordings";
            case "JFR.start" -> "Start a Flight Recorder recording";
            case "JFR.dump" -> "Write recording data to a JFR file";
            case "JFR.stop" -> "Stop a Flight Recorder recording";
            default -> "Diagnostic command reported by the target JVM";
        };
    }
}
