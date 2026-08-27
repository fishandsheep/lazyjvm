package dev.lazyjvm.tui;

import java.nio.file.Path;
import java.time.Duration;

public record TuiOptions(Duration refresh, Duration history, Path jdkHome, boolean ascii, boolean color,
                         UiLanguage language) {
    public TuiOptions(Duration refresh, Duration history, Path jdkHome, boolean ascii, boolean color) {
        this(refresh, history, jdkHome, ascii, color, UiLanguage.ZH_CN);
    }

    public TuiOptions(Duration refresh, Duration history, Path jdkHome, UiLanguage language,
                      boolean ascii, boolean color) {
        this(refresh, history, jdkHome, ascii, color, language);
    }

    public TuiOptions {
        refresh = refresh == null ? Duration.ofSeconds(1) : refresh;
        history = history == null ? Duration.ofMinutes(60) : history;
        language = language == null ? UiLanguage.ZH_CN : language;
        if (refresh.isNegative() || refresh.isZero()) throw new IllegalArgumentException("refresh must be positive");
        if (history.compareTo(refresh) < 0) throw new IllegalArgumentException("history must be at least one refresh interval");
    }

    public int historyCapacity() {
        long capacity = Math.max(1, history.toMillis() / refresh.toMillis());
        return (int) Math.min(86_400, capacity);
    }
}
