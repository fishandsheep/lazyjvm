package dev.lazyjvm.tui;

enum Style {
    NORMAL("\033[37;40m", "\033[38;5;252m\033[48;5;235m", "\033[38;2;220;220;220m\033[48;2;24;24;24m"),
    MUTED("\033[37;40m", "\033[38;5;244m\033[48;5;235m", "\033[38;2;150;150;150m\033[48;2;24;24;24m"),
    CYAN("\033[96;40m", "\033[38;5;81m\033[48;5;235m", "\033[38;2;80;190;220m\033[48;2;24;24;24m"),
    SELECTED("\033[30;104;1m", "\033[38;5;255m\033[48;5;25m\033[1m", "\033[38;2;245;245;245m\033[48;2;35;85;150m\033[1m"),
    CYAN_REVERSE("\033[30;104;1m", "\033[38;5;255m\033[48;5;25m\033[1m", "\033[38;2;245;245;245m\033[48;2;35;85;150m\033[1m"),
    AMBER("\033[93;40m", "\033[38;5;215m\033[48;5;235m", "\033[38;2;235;170;75m\033[48;2;24;24;24m"),
    GREEN("\033[92;40m", "\033[38;5;114m\033[48;5;235m", "\033[38;2;100;205;120m\033[48;2;24;24;24m"),
    YELLOW("\033[93;40m", "\033[38;5;221m\033[48;5;235m", "\033[38;2;240;205;80m\033[48;2;24;24;24m"),
    RED("\033[91;40m", "\033[38;5;203m\033[48;5;235m", "\033[38;2;240;95;95m\033[48;2;24;24;24m"),
    HEADER("\033[37;40;1m", "\033[38;5;255m\033[48;5;234m\033[1m", "\033[38;2;235;235;235m\033[48;2;30;30;30m\033[1m"),
    FOOTER("\033[37;100m", "\033[38;5;252m\033[48;5;237m", "\033[38;2;208;208;208m\033[48;2;48;48;48m"),
    PANEL("\033[37;40m", "\033[38;5;240m\033[48;5;235m", "\033[38;2;105;105;105m\033[48;2;24;24;24m"),
    DIALOG("\033[97;100m", "\033[38;5;255m\033[48;5;237m", "\033[38;2;238;238;238m\033[48;2;48;48;48m"),
    DIALOG_TITLE("\033[30;103;1m", "\033[38;5;234m\033[48;5;215m\033[1m", "\033[38;2;28;28;28m\033[48;2;235;170;75m\033[1m");

    static final String RESET = "\033[0m";
    private final String ansi16;
    private final String ansi256;
    private final String trueColor;

    Style(String ansi16, String ansi256, String trueColor) {
        this.ansi16 = ansi16;
        this.ansi256 = ansi256;
        this.trueColor = trueColor;
    }

    String ansi(boolean color) {
        return ansi(color ? ColorProfile.ANSI256 : ColorProfile.NONE);
    }

    String ansi(ColorProfile profile) {
        switch (profile) {
            case NONE: return "";
            case ANSI16: return ansi16;
            case ANSI256: return ansi256;
            case TRUECOLOR: return trueColor;
            default: throw new IllegalArgumentException("Unknown color profile: " + profile);
        }
    }
}
