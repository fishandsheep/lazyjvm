package dev.lazyjvm.tui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiLayoutTest {
    @Test
    void keepsRailsAndMainGeometryAtKnownWidths() {
        UiLayout standard = UiLayout.forSize(80, 24, FocusArea.MAIN, false);
        UiLayout wide = UiLayout.forSize(120, 30, FocusArea.MAIN, false);

        assertEquals(17, standard.workspace().width());
        assertEquals(63, standard.main().width());
        assertEquals(21, wide.workspace().width());
        assertEquals(0, wide.commandList().width());
    }

    @Test
    void commandsUseHorizontalSplitOnWideTerminals() {
        UiLayout layout = UiLayout.forSize(120, 30, FocusArea.MAIN, true);

        assertTrue(layout.commandList().width() > 0);
        assertTrue(layout.commandOutput().width() > 0);
        assertTrue(layout.commandOutput().x() > layout.commandList().x());
    }

    @Test
    void commandsUseTallOutputOnNarrowTerminals() {
        UiLayout layout = UiLayout.forSize(80, 24, FocusArea.MAIN, true);

        assertTrue(layout.commandList().height() > 0);
        assertTrue(layout.commandOutput().height() >= layout.main().height() / 2);
        assertTrue(layout.commandOutput().y() > layout.commandList().y());
    }

    @Test
    void focusCyclesForwardAndBackward() {
        assertEquals(FocusArea.COMMAND_OUTPUT, FocusArea.MAIN.next());
        assertEquals(FocusArea.MAIN, FocusArea.COMMAND_OUTPUT.previous());
    }

    @Test
    void compactWorkspacePopupWinsOverUnderlyingCommandPanel() {
        UiLayout layout = UiLayout.forSize(40, 12, FocusArea.WORKSPACE, true);
        layout.workspaceItems().add(new UiLayout.Rect(layout.workspace().x() + 1,
                layout.workspace().y() + 2, 10, 1));

        assertEquals(UiLayout.HitKind.WORKSPACE,
                layout.hitTest(layout.workspace().x() + 2, layout.workspace().y() + 2).kind());
    }

    @Test
    void commandRowUsesDoubleClickInsteadOfAnExecuteHitArea() {
        UiLayout layout = UiLayout.forSize(120, 30, FocusArea.MAIN, true);
        UiLayout.Rect row = new UiLayout.Rect(layout.commandList().x() + 1,
                layout.commandList().y() + 2, layout.commandList().width() - 2, 1);
        layout.commandRows().add(new UiLayout.CommandHit(0, "VM", false, row));

        assertEquals(UiLayout.HitKind.COMMAND_ROW,
                layout.hitTest(row.x() + row.width() - 1, row.y()).kind());
    }
}
