package dev.lazyjvm.tui;

import java.util.Locale;

/** Languages supported by the terminal interface. */
public enum UiLanguage {
    ZH_CN("zh-CN"),
    EN("en");

    private final String optionValue;

    UiLanguage(String optionValue) {
        this.optionValue = optionValue;
    }

    public String optionValue() {
        return optionValue;
    }

    public static UiLanguage parse(String value) {
        if (value == null || value.trim().isEmpty()) return ZH_CN;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("zh") || normalized.equals("zh-cn") || normalized.equals("zh_cn")) return ZH_CN;
        if (normalized.equals("en") || normalized.equals("en-us") || normalized.equals("english")) return EN;
        throw new IllegalArgumentException("language must be zh-CN or en");
    }

    UiLanguage forTerminal(boolean ascii) {
        return ascii ? EN : this;
    }

    boolean isEnglish() {
        return this == EN;
    }
}
