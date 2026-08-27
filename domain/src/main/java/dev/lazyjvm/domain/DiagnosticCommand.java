package dev.lazyjvm.domain;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

public final class DiagnosticCommand {
    private final String name;
    private final String description;
    private final CommandImpact impact;
    private final List<String> arguments;

    public DiagnosticCommand(String name, String description, CommandImpact impact, List<String> arguments) {
        this.name = name;
        this.description = description == null ? "" : description;
        this.impact = impact == null ? CommandImpact.MEDIUM : impact;
        this.arguments = arguments == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(arguments));
    }

    public String name() { return name; }
    public String description() { return description; }
    public CommandImpact impact() { return impact; }
    public List<String> arguments() { return arguments; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DiagnosticCommand)) return false;
        DiagnosticCommand that = (DiagnosticCommand) other;
        return Objects.equals(name, that.name) && Objects.equals(description, that.description)
                && impact == that.impact && arguments.equals(that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, impact, arguments);
    }

    @Override
    public String toString() {
        return "DiagnosticCommand[name=" + name + ", description=" + description + ", impact=" + impact
                + ", arguments=" + arguments + "]";
    }
}
