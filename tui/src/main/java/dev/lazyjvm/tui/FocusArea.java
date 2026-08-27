package dev.lazyjvm.tui;

/** Focusable regions in monitor mode. */
enum FocusArea {
    WORKSPACE("Workspace", "工作区"),
    MAIN("Main", "主内容"),
    COMMAND_OUTPUT("Command Output", "命令输出");

    private final String english;
    private final String chinese;

    FocusArea(String english, String chinese) {
        this.english = english;
        this.chinese = chinese;
    }

    String label(UiLanguage language) {
        return language.isEnglish() ? english : chinese;
    }

    String label(boolean ascii) {
        return label(ascii ? UiLanguage.EN : UiLanguage.ZH_CN);
    }

    FocusArea next() {
        FocusArea[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    FocusArea previous() {
        FocusArea[] values = values();
        return values[(ordinal() + values.length - 1) % values.length];
    }
}
