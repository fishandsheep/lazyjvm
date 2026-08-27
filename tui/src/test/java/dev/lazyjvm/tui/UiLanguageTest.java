package dev.lazyjvm.tui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UiLanguageTest {
    @Test
    void parsesDocumentedValues() {
        assertEquals(UiLanguage.ZH_CN, UiLanguage.parse("zh-CN"));
        assertEquals(UiLanguage.EN, UiLanguage.parse("en"));
        assertEquals(UiLanguage.EN, UiLanguage.parse("EN"));
    }

    @Test
    void asciiForcesEnglishForTerminalSafety() {
        assertEquals(UiLanguage.EN, UiLanguage.ZH_CN.forTerminal(true));
        assertEquals(UiLanguage.ZH_CN, UiLanguage.ZH_CN.forTerminal(false));
    }

    @Test
    void rejectsUnknownValues() {
        assertThrows(IllegalArgumentException.class, () -> UiLanguage.parse("fr"));
    }
}
