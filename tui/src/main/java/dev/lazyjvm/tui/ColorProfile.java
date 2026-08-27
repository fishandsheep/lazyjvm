package dev.lazyjvm.tui;

import org.jline.terminal.Terminal;

import java.util.Locale;

enum ColorProfile {
    NONE,
    ANSI16,
    ANSI256,
    TRUECOLOR;

    static ColorProfile detect(Terminal terminal, boolean enabled) {
        if (!enabled) return NONE;
        String colorTerm = System.getenv().getOrDefault("COLORTERM", "").toLowerCase(Locale.ROOT);
        String term = System.getenv().getOrDefault("TERM", terminal.getType() == null ? "" : terminal.getType())
                .toLowerCase(Locale.ROOT);
        if (colorTerm.contains("truecolor") || colorTerm.contains("24bit")) return TRUECOLOR;
        if (term.contains("256color")) return ANSI256;
        if (term.equals("dumb") || term.trim().isEmpty()) return NONE;
        return ANSI16;
    }
}
