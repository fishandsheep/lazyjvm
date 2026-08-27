package dev.lazyjvm.tui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiFrameTest {
    @Test
    void compactFrameUsesOnePanelWithoutClipping() {
        String frame = TuiApplication.renderFixture(40, 12, false);
        assertFrame(frame, 40, 12);
        assertTrue(frame.contains("概览"));
        assertTrue(frame.contains("堆"));
        assertFalse(frame.contains("╭─ 工作区"));
        assertFalse(frame.contains("Target detail"));
    }

    @Test
    void standardFrameUsesNavigationAndMainPanel() {
        String frame = TuiApplication.renderFixture(80, 24, false);
        assertFrame(frame, 80, 24);
        assertTrue(frame.contains("工作区"));
        assertTrue(frame.contains("进程 CPU"));
        String top = frame.substring("\033[H".length()).lines().limit(2).reduce("", (a, b) -> a + b);
        assertFalse(top.contains("1 Overview"));
        assertTrue(frame.contains("焦点"));
        assertFalse(frame.contains("Target detail"));
    }

    @Test
    void wideFrameKeepsTargetDetailOutOfMonitor() {
        String frame = TuiApplication.renderFixture(120, 30, false);
        assertFrame(frame, 120, 30);
        assertTrue(frame.contains("工作区"));
        assertFalse(frame.contains("Target detail"));
        assertFalse(frame.contains("目标详情"));
        assertTrue(frame.contains("采样器"));
    }

    @Test
    void languageAndAsciiModesStaySingleLanguage() {
        String chinese = TuiApplication.renderFixture(80, 24, false, UiLanguage.ZH_CN);
        String english = TuiApplication.renderFixture(80, 24, false, UiLanguage.EN);
        String ascii = TuiApplication.renderFixture(80, 24, true, UiLanguage.ZH_CN);

        assertTrue(chinese.contains("概览"));
        assertFalse(chinese.contains("Overview"));
        assertFalse(chinese.contains("Process CPU"));
        assertTrue(english.contains("Overview"));
        assertTrue(english.contains("Process CPU"));
        assertFalse(english.matches("(?s).*[\\u4e00-\\u9fff].*"));
        assertFalse(ascii.matches("(?s).*[\\u4e00-\\u9fff].*"));
        assertTrue(ascii.contains("+--"));
        assertTrue(ascii.chars().allMatch(value -> value == 27 || value <= 127));
        assertFalse(ascii.contains("?"));
    }

    private static void assertFrame(String frame, int width, int height) {
        String[] lines = frame.substring("\033[H".length()).split("\n", -1);
        assertEquals(height, lines.length);
        for (String line : lines) assertEquals(width, Canvas.displayWidth(line));
    }
}
