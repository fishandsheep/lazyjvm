package dev.lazyjvm.domain;

import java.util.List;

public record DiagnosticCommand(String name, String description, CommandImpact impact, List<String> arguments) {
    public DiagnosticCommand {
        description = description == null ? "" : description;
        impact = impact == null ? CommandImpact.MEDIUM : impact;
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }
}
