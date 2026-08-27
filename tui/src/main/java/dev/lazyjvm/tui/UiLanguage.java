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
        if (value == null || value.isBlank()) return ZH_CN;
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "zh", "zh-cn", "zh_cn" -> ZH_CN;
            case "en", "en-us", "english" -> EN;
            default -> throw new IllegalArgumentException("language must be zh-CN or en");
        };
    }

    UiLanguage forTerminal(boolean ascii) {
        return ascii ? EN : this;
    }

    boolean isEnglish() {
        return this == EN;
    }
}
