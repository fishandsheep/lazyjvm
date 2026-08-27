package dev.lazyjvm.tui;

/** Parsed terminal input. Kept separate so keyboard and mouse hit testing share one model. */
final class InputEvent {
    enum Kind { KEY, MOUSE }

    static final class Mouse {
        private final int button;
        private final int x;
        private final int y;
        private final boolean release;
        private final boolean shift;
        private final boolean alt;
        private final boolean control;

        Mouse(int button, int x, int y, boolean release, boolean shift, boolean alt, boolean control) {
            this.button = button;
            this.x = x;
            this.y = y;
            this.release = release;
            this.shift = shift;
            this.alt = alt;
            this.control = control;
        }

        int button() { return button; }
        int x() { return x; }
        int y() { return y; }
        boolean release() { return release; }
        boolean shift() { return shift; }
        boolean alt() { return alt; }
        boolean control() { return control; }
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
