package dev.lazyjvm.tui;

/** Shared clamping and selection-following rules for every scrollable TUI region. */
final class ScrollModel {
    private ScrollModel() {}

    static int maximum(int total, int visible) {
        return Math.max(0, total - Math.max(1, visible));
    }

    static int clamp(int offset, int total, int visible) {
        return Math.max(0, Math.min(maximum(total, visible), offset));
    }

    static int follow(int selected, int offset, int visible, int total) {
        int safeVisible = Math.max(1, visible);
        int safeTotal = Math.max(0, total);
        int safeSelected = Math.max(0, Math.min(Math.max(0, safeTotal - 1), selected));
        int next = clamp(offset, safeTotal, safeVisible);
        if (safeSelected < next) next = safeSelected;
        if (safeSelected >= next + safeVisible) next = safeSelected - safeVisible + 1;
        return clamp(next, safeTotal, safeVisible);
    }

    static int movePage(int offset, int deltaPages, int total, int visible) {
        if (deltaPages == Integer.MIN_VALUE) return 0;
        if (deltaPages == Integer.MAX_VALUE) return maximum(total, visible);
        return clamp(offset + deltaPages * Math.max(1, visible), total, visible);
    }

    static String hint(int offset, int total, int visible, UiLanguage language) {
        if (total <= 0) return language.isEnglish() ? "empty" : "空";
        int safeOffset = clamp(offset, total, visible);
        int first = Math.min(total, safeOffset + 1);
        int last = Math.min(total, safeOffset + Math.max(1, visible));
        String range = first + "-" + last + "/" + total;
        return language.isEnglish() ? range + " · ↑↓ PgUp/PgDn Home/End" : range + " · ↑↓ PgUp/PgDn Home/End";
    }
}
