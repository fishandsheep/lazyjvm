package dev.lazyjvm.tui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputNormalizerTest {
    @Test
    void removesTerminalControlsAndExpandsTabs() {
        String value = "\033[31mVM.log\033[0m\tline\r\n\033]0;title\007next";

        assertEquals("VM.log    line\nnext", OutputNormalizer.clean(value));
    }

    @Test
    void wrapsByTerminalDisplayWidth() {
        assertEquals(List.of("中 文", "abc"), OutputNormalizer.lines("中 文abc", 5));
        assertFalse(OutputNormalizer.normalize("long line", 4).contains("\033"));
        assertTrue(Canvas.displayWidth("中文") == 4);
    }
}
