package dev.lazyjvm.tui;

/** Parsed terminal input. Kept separate so keyboard and mouse hit testing share one model. */
final class InputEvent {
    enum Kind { KEY, MOUSE }

    record Mouse(int button, int x, int y, boolean release, boolean shift, boolean alt, boolean control) {
        boolean wheelUp() { return (button & 64) != 0 && (button & 1) == 0; }
        boolean wheelDown() { return (button & 64) != 0 && (button & 1) == 1; }
        boolean primary() { return (button & 3) == 0 && !release; }
    }

    private final Kind kind;
    private final int key;
    private final Mouse mouse;

    private InputEvent(Kind kind, int key, Mouse mouse) {
        this.kind = kind;
        this.key = key;
        this.mouse = mouse;
    }

    static InputEvent key(int key) {
        return new InputEvent(Kind.KEY, key, null);
    }

    static InputEvent mouse(Mouse mouse) {
        return new InputEvent(Kind.MOUSE, -1, mouse);
    }

    Kind kind() { return kind; }
    int key() { return key; }
    Mouse mouse() { return mouse; }
}
