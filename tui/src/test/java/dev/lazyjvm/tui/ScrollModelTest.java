package dev.lazyjvm.tui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScrollModelTest {
    @Test
    void selectionFollowsVisibleWindow() {
        assertEquals(0, ScrollModel.follow(0, 0, 3, 10));
        assertEquals(2, ScrollModel.follow(4, 0, 3, 10));
        assertEquals(6, ScrollModel.follow(8, 2, 3, 10));
    }

    @Test
    void pageAndEndMovementClampToContent() {
        assertEquals(0, ScrollModel.movePage(4, Integer.MIN_VALUE, 10, 3));
        assertEquals(7, ScrollModel.movePage(0, Integer.MAX_VALUE, 10, 3));
        assertEquals(7, ScrollModel.movePage(6, 1, 10, 3));
    }

    @Test
    void rangeHintIdentifiesEmptyAndVisibleRows() {
        assertEquals("空", ScrollModel.hint(0, 0, 3, UiLanguage.ZH_CN));
        assertEquals("3-5/8 · ↑↓ PgUp/PgDn Home/End", ScrollModel.hint(2, 8, 3, UiLanguage.EN));
    }
}
