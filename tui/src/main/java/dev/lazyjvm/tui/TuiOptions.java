package dev.lazyjvm.tui;

import java.nio.file.Path;
import java.time.Duration;

public final class TuiOptions {
    private final Duration refresh;
    private final Duration history;
    private final Path jdkHome;
    private final boolean ascii;
    private final boolean color;
    private final UiLanguage language;

    public TuiOptions(Duration refresh, Duration history, Path jdkHome, boolean ascii, boolean color) {
        this(refresh, history, jdkHome, ascii, color, UiLanguage.ZH_CN);
    }

    public TuiOptions(Duration refresh, Duration history, Path jdkHome, UiLanguage language,
                      boolean ascii, boolean color) {
        this(refresh, history, jdkHome, ascii, color, language);
    }

    public TuiOptions(Duration refresh, Duration history, Path jdkHome, boolean ascii, boolean color,
                      UiLanguage language) {
        this.refresh = refresh == null ? Duration.ofSeconds(1) : refresh;
        this.history = history == null ? Duration.ofMinutes(60) : history;
        this.jdkHome = jdkHome;
        this.ascii = ascii;
        this.color = color;
        this.language = language == null ? UiLanguage.ZH_CN : language;
        if (this.refresh.isNegative() || this.refresh.isZero()) {
            throw new IllegalArgumentException("refresh must be positive");
        }
        if (this.history.compareTo(this.refresh) < 0) {
            throw new IllegalArgumentException("history must be at least one refresh interval");
        }
    }

    public Duration refresh() { return refresh; }
    public Duration history() { return history; }
    public Path jdkHome() { return jdkHome; }
    public boolean ascii() { return ascii; }
    public boolean color() { return color; }
    public UiLanguage language() { return language; }

    public int historyCapacity() {
        long capacity = Math.max(1, history.toMillis() / refresh.toMillis());
        return (int) Math.min(86_400, capacity);
    }
}
