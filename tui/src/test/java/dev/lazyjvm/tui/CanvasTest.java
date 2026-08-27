package dev.lazyjvm.tui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasTest {
    @Test
    void rendersExactTerminalDimensionsWithoutColor() {
        for (int[] size : new int[][]{{80, 24}, {120, 30}, {200, 50}}) {
            Canvas canvas = new Canvas(size[0], size[1]);
            canvas.box(0, 0, size[0], size[1], "LazyJVM", Style.PANEL, false);
            canvas.text(2, 2, "heap 512 MiB", Style.AMBER);

            String rendered = canvas.render(false).substring("\033[H".length());
            String[] lines = rendered.split("\n", -1);
            assertEquals(size[1], lines.length);
            for (String line : lines) assertEquals(size[0], line.length());
            assertFalse(rendered.contains("\033[38"));
        }
    }

    @Test
    void asciiModeUsesPortableBorders() {
        Canvas canvas = new Canvas(20, 5, true);
        canvas.box(0, 0, 20, 5, "Panel", Style.PANEL, true);
        canvas.text(2, 2, "● live · pause… 1–6", Style.NORMAL);

        String rendered = canvas.render(false);
        assertFalse(rendered.contains("+─"));
        assertTrue(rendered.contains(" Panel "));
        assertTrue(rendered.contains("|"));
        assertTrue(rendered.chars().allMatch(value -> value == 27 || value <= 127));
    }

    @Test
    void unicodeModeUsesRoundedBorders() {
        Canvas canvas = new Canvas(20, 5);
        canvas.box(0, 0, 20, 5, "Panel", Style.PANEL, false);

        String rendered = canvas.render(false);
        assertTrue(rendered.contains("╭─"));
        assertTrue(rendered.contains("╯"));
    }

    @Test
    void cropsLongLabelsWithVisibleEllipsis() {
        assertEquals("abc…", Canvas.crop("abcdef", 4));
        assertEquals("a", Canvas.crop("abcdef", 1));
        assertEquals("", Canvas.crop("abcdef", 0));
    }
}
