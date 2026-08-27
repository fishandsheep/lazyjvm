package dev.lazyjvm.tui;

import java.util.ArrayList;
import java.util.List;

/** Single geometry source for drawing and mouse hit testing. */
final class UiLayout {
    enum HitKind {
        NONE, WORKSPACE, MAIN, COMMAND_LIST, COMMAND_OUTPUT, COMMAND_GROUP, COMMAND_ROW,
        OUTPUT_SELECTOR, OUTPUT_COPY, OUTPUT_ITEM
    }

    record Rect(int x, int y, int width, int height) {
        static Rect none() { return new Rect(-1, -1, 0, 0); }

        boolean contains(int column, int row) {
            return width > 0 && height > 0 && column >= x && column < x + width
                    && row >= y && row < y + height;
        }
    }

    record Hit(HitKind kind, int index) {}

    record CommandHit(int index, String group, boolean groupHeader, Rect row) {
        CommandHit(int index, Rect row) {
            this(index, "", false, row);
        }
    }

    private final Rect workspace;
    private final Rect main;
    private final Rect commandList;
    private Rect commandOutput;
    private Rect outputSelector;
    private Rect outputCopy;
    private Rect outputBody;
    private final List<Rect> workspaceItems = new ArrayList<>();
    private final List<CommandHit> commandRows = new ArrayList<>();
    private final List<Rect> outputItems = new ArrayList<>();
    private final List<Integer> outputItemIndexes = new ArrayList<>();

    private UiLayout(Rect workspace, Rect main, Rect commandList, Rect commandOutput) {
        this.workspace = workspace;
        this.main = main;
        this.commandList = commandList;
        this.commandOutput = commandOutput;
        this.outputSelector = Rect.none();
        this.outputCopy = Rect.none();
        this.outputBody = Rect.none();
    }

    static UiLayout forSize(int width, int height, FocusArea focus, boolean commandsPage) {
        int bodyY = 2;
        int bodyHeight = Math.max(1, height - 3);
        if (width < 80) {
            int popupWidth = Math.min(70, Math.max(30, width - 4));
            Rect workspace = focus == FocusArea.WORKSPACE
                    ? centered(popupWidth, Math.min(Math.max(8, bodyHeight - 2), 10), width, height)
                    : Rect.none();
            Rect main = new Rect(0, bodyY, width, bodyHeight);
            return withCommandAreas(workspace, main, commandsPage, width);
        }
        int left = width >= 100 ? 21 : 17;
        Rect workspace = new Rect(0, bodyY, left, bodyHeight);
        Rect main = new Rect(left, bodyY, Math.max(1, width - left), bodyHeight);
        return withCommandAreas(workspace, main, commandsPage, width);
    }

    private static UiLayout withCommandAreas(Rect workspace, Rect main, boolean commandsPage, int terminalWidth) {
        if (!commandsPage) return new UiLayout(workspace, main, Rect.none(), Rect.none());
        int innerX = main.x() + 1;
        int innerY = main.y() + 1;
        int innerWidth = Math.max(1, main.width() - 2);
        int innerHeight = Math.max(1, main.height() - 2);
        if (terminalWidth >= 100) {
            int listWidth = Math.max(20, Math.min(innerWidth - 12, innerWidth / 3));
            listWidth = Math.min(listWidth, Math.max(1, innerWidth - 2));
            Rect list = new Rect(innerX, innerY, listWidth, innerHeight);
            Rect output = new Rect(innerX + listWidth + 1, innerY,
                    Math.max(1, innerWidth - listWidth - 1), innerHeight);
            return new UiLayout(workspace, main, list, output);
        }
        int outputHeight = Math.max(6, (innerHeight * 3 + 4) / 5);
        outputHeight = Math.min(innerHeight, outputHeight);
        int listHeight = innerHeight - outputHeight - 1;
        if (listHeight < 2) {
            listHeight = Math.min(2, innerHeight);
            outputHeight = Math.max(1, innerHeight - listHeight);
            Rect list = new Rect(innerX, innerY, innerWidth, listHeight);
            Rect output = new Rect(innerX, innerY + listHeight, innerWidth, outputHeight);
            return new UiLayout(workspace, main, list, output);
        }
        Rect list = new Rect(innerX, innerY, innerWidth, listHeight);
        Rect output = new Rect(innerX, innerY + listHeight + 1, innerWidth, outputHeight);
        return new UiLayout(workspace, main, list, output);
    }

    private static Rect centered(int width, int height, int canvasWidth, int canvasHeight) {
        return new Rect(Math.max(0, (canvasWidth - width) / 2),
                Math.max(2, (canvasHeight - height) / 2), width, height);
    }

    Rect workspace() { return workspace; }
    Rect main() { return main; }
    Rect commandList() { return commandList; }
    Rect commandOutput() { return commandOutput; }
    Rect outputSelector() { return outputSelector; }
    Rect outputCopy() { return outputCopy; }
    Rect outputBody() { return outputBody; }
    List<Rect> workspaceItems() { return workspaceItems; }
    List<CommandHit> commandRows() { return commandRows; }
    List<Rect> outputItems() { return outputItems; }
    List<Integer> outputItemIndexes() { return outputItemIndexes; }

    void setOutput(Rect panel, Rect selector, Rect copy, Rect body) {
        commandOutput = panel;
        outputSelector = selector;
        outputCopy = copy;
        outputBody = body;
    }

    Hit hitTest(int x, int y) {
        if (workspace.contains(x, y)) {
            for (int index = 0; index < workspaceItems.size(); index++) {
                if (workspaceItems.get(index).contains(x, y)) return new Hit(HitKind.WORKSPACE, index);
            }
            return new Hit(HitKind.WORKSPACE, -1);
        }
        for (int index = 0; index < outputItems.size(); index++) {
            if (outputItems.get(index).contains(x, y)) {
                return new Hit(HitKind.OUTPUT_ITEM, outputItemIndexes.get(index));
            }
        }
        if (outputCopy.contains(x, y)) return new Hit(HitKind.OUTPUT_COPY, -1);
        if (outputSelector.contains(x, y)) return new Hit(HitKind.OUTPUT_SELECTOR, -1);
        if (outputBody.contains(x, y) || commandOutput.contains(x, y)) return new Hit(HitKind.COMMAND_OUTPUT, -1);
        for (CommandHit hit : commandRows) {
            if (hit.row().contains(x, y)) {
                return new Hit(hit.groupHeader() ? HitKind.COMMAND_GROUP : HitKind.COMMAND_ROW, hit.index());
            }
        }
        if (commandList.contains(x, y)) return new Hit(HitKind.COMMAND_LIST, -1);
        if (main.contains(x, y)) return new Hit(HitKind.MAIN, -1);
        return new Hit(HitKind.NONE, -1);
    }
}
