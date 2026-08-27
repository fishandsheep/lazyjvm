package dev.lazyjvm.tui;

import java.util.Arrays;

/** Cell based terminal canvas. All coordinates use terminal display cells. */
final class Canvas {
    private final int width;
    private final int height;
    private final String[][] cells;
    private final Style[][] styles;
    private final boolean[][] continuation;
    private final boolean ascii;

    Canvas(int width, int height) {
        this(width, height, false);
    }

    Canvas(int width, int height, boolean ascii) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.ascii = ascii;
        this.cells = new String[this.height][this.width];
        this.styles = new Style[this.height][this.width];
        this.continuation = new boolean[this.height][this.width];
        for (int y = 0; y < this.height; y++) {
            Arrays.fill(cells[y], " ");
            Arrays.fill(styles[y], Style.NORMAL);
        }
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    void fill(int x, int y, int w, int h, char value, Style style) {
        for (int row = Math.max(0, y); row < Math.min(height, y + h); row++) {
            for (int column = Math.max(0, x); column < Math.min(width, x + w); column++) {
                put(column, row, Character.toString(value), 1, style);
            }
        }
    }

    void text(int x, int y, String value, Style style) {
        if (value == null || y < 0 || y >= height) return;
        int column = x;
        for (int offset = 0; offset < value.length() && column < width; ) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            String glyph = ascii ? ascii(codePoint) : new String(Character.toChars(codePoint));
            int glyphWidth = displayWidth(glyph);
            if (glyphWidth == 0) continue;
            if (column + glyphWidth <= 0) {
                column += glyphWidth;
                continue;
            }
            if (column >= 0 && column + glyphWidth <= width) put(column, y, glyph, glyphWidth, style);
            column += glyphWidth;
        }
    }

    void box(int x, int y, int w, int h, String title, Style border, boolean ascii) {
        if (w < 2 || h < 2) return;
        char horizontal = ascii ? '-' : '─';
        char vertical = ascii ? '|' : '│';
        char tl = ascii ? '+' : '╭';
        char tr = ascii ? '+' : '╮';
        char bl = ascii ? '+' : '╰';
        char br = ascii ? '+' : '╯';
        fill(x + 1, y, w - 2, 1, horizontal, border);
        fill(x + 1, y + h - 1, w - 2, 1, horizontal, border);
        fill(x, y + 1, 1, h - 2, vertical, border);
        fill(x + w - 1, y + 1, 1, h - 2, vertical, border);
        text(x, y, Character.toString(tl), border);
        text(x + w - 1, y, Character.toString(tr), border);
        text(x, y + h - 1, Character.toString(bl), border);
        text(x + w - 1, y + h - 1, Character.toString(br), border);
        if (title != null && !title.trim().isEmpty() && w > 6) {
            text(x + 2, y, " " + crop(title, w - 6) + " ", Style.CYAN);
        }
    }

    String render(boolean color) {
        return render(color ? ColorProfile.ANSI256 : ColorProfile.NONE);
    }

    String render(ColorProfile profile) {
        StringBuilder output = new StringBuilder(width * height * 2);
        output.append("\033[H");
        for (int y = 0; y < height; y++) {
            Style active = null;
            for (int x = 0; x < width; x++) {
                Style next = styles[y][x];
                if (next != active) {
                    output.append(next.ansi(profile));
                    active = next;
                }
                if (!continuation[y][x]) output.append(cells[y][x]);
            }
            if (y < height - 1) output.append('\n');
        }
        if (profile != ColorProfile.NONE) output.append(Style.RESET);
        return output.toString();
    }

    static int displayWidth(String value) {
        if (value == null || value.isEmpty()) return 0;
        int width = 0;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            width += codePointWidth(codePoint);
        }
        return width;
    }

    static int codePointWidth(int codePoint) {
        int type = Character.getType(codePoint);
        if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
                || type == Character.FORMAT) return 0;
        if (codePoint == 0) return 0;
        if (codePoint < 32 || (codePoint >= 0x7f && codePoint < 0xa0)) return 1;
        if ((codePoint >= 0x1100 && codePoint <= 0x115f)
                || (codePoint >= 0x2329 && codePoint <= 0x232a)
                || (codePoint >= 0x2e80 && codePoint <= 0xa4cf)
                || (codePoint >= 0xac00 && codePoint <= 0xd7a3)
                || (codePoint >= 0xf900 && codePoint <= 0xfaff)
                || (codePoint >= 0xfe10 && codePoint <= 0xfe19)
                || (codePoint >= 0xfe30 && codePoint <= 0xfe6f)
                || (codePoint >= 0xff00 && codePoint <= 0xff60)
                || (codePoint >= 0xffe0 && codePoint <= 0xffe6)
                || (codePoint >= 0x1f300 && codePoint <= 0x1faff)) return 2;
        return 1;
    }

    static String crop(String value, int displayWidth) {
        if (displayWidth <= 0 || value == null) return "";
        if (Canvas.displayWidth(value) <= displayWidth) return value;
        if (displayWidth == 1) return "a";
        int target = displayWidth - 1;
        StringBuilder result = new StringBuilder();
        int used = 0;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            int codePointWidth = codePointWidth(codePoint);
            if (used + codePointWidth > target) break;
            result.appendCodePoint(codePoint);
            used += codePointWidth;
            offset += Character.charCount(codePoint);
        }
        return result + "…";
    }

    static String prefix(String value, int displayWidth) {
        if (value == null || displayWidth <= 0) return "";
        StringBuilder result = new StringBuilder();
        int used = 0;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            int pointWidth = codePointWidth(codePoint);
            if (pointWidth > 0 && used + pointWidth > displayWidth) break;
            result.appendCodePoint(codePoint);
            used += pointWidth;
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private void put(int x, int y, String glyph, int glyphWidth, Style style) {
        if (x < 0 || y < 0 || x >= width || y >= height || glyphWidth <= 0 || x + glyphWidth > width) return;
        clearCell(x, y);
        if (glyphWidth == 2) clearCell(x + 1, y);
        cells[y][x] = glyph;
        styles[y][x] = style;
        continuation[y][x] = false;
        for (int offset = 1; offset < glyphWidth; offset++) {
            cells[y][x + offset] = "";
            styles[y][x + offset] = style;
            continuation[y][x + offset] = true;
        }
    }

    private void clearCell(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        if (continuation[y][x]) {
            cells[y][x - 1] = " ";
            styles[y][x - 1] = Style.NORMAL;
            continuation[y][x - 1] = false;
        }
        if (x + 1 < width && continuation[y][x + 1]) {
            cells[y][x + 1] = " ";
            styles[y][x + 1] = Style.NORMAL;
            continuation[y][x + 1] = false;
        }
        cells[y][x] = " ";
        styles[y][x] = Style.NORMAL;
        continuation[y][x] = false;
    }

    private static String ascii(int codePoint) {
        if (codePoint <= 127) return Character.toString((char) codePoint);
        switch (codePoint) {
            case '●': return "*";
            case '·': return "|";
            case '–':
            case '—':
            case '−': return "-";
            case '…': return ".";
            case '≥': return ">";
            case '≤': return "<";
            case '×': return "x";
            case '↑': return "^";
            case '↓': return "v";
            case '←': return "<";
            case '→': return ">";
            case '▼':
            case '▾': return "v";
            case '•': return "*";
            case '╭':
            case '╮':
            case '╰':
            case '╯':
            case '┌':
            case '┐':
            case '└':
            case '┘':
            case '┼': return "+";
            case '─': return "-";
            case '│': return "|";
            case '█':
            case '▄': return "#";
            case '░':
            case '┈': return ".";
            default: return "?";
        }
    }
}
