package dev.lazyjvm.tui;

import java.util.ArrayList;
import java.util.List;

/** Makes hostile command output safe for the fixed-width TUI while export keeps raw output. */
final class OutputNormalizer {
    private static final String CSI = "\u001B\\[[0-?]*[ -/]*[@-~]";
    private static final String OSC = "\u001B\\][^\u0007]*(?:\u0007|\u001B\\\\)";

    private OutputNormalizer() {}

    static String clean(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String value = raw.replaceAll(OSC, "").replaceAll(CSI, "");
        StringBuilder clean = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\r' || codePoint == '\b' || codePoint == '\f') continue;
            if (codePoint == '\t') {
                clean.append("    ");
                continue;
            }
            if (codePoint == '\n' || (codePoint >= 32 && codePoint != 127
                    && !(codePoint >= 0x80 && codePoint < 0xa0))) clean.appendCodePoint(codePoint);
        }
        return clean.toString();
    }

    static List<String> lines(String raw, int width) {
        int safeWidth = Math.max(1, width);
        List<String> result = new ArrayList<>();
        for (String line : clean(raw).split("\\n", -1)) {
            if (line.isEmpty()) {
                result.add("");
                continue;
            }
            String remaining = line;
            while (Canvas.displayWidth(remaining) > safeWidth) {
                String part = Canvas.prefix(remaining, safeWidth);
                if (part.isEmpty()) {
                    int count = Character.charCount(remaining.codePointAt(0));
                    result.add("?");
                    remaining = remaining.substring(count);
                    continue;
                }
                result.add(part);
                remaining = remaining.substring(part.length());
            }
            result.add(remaining);
        }
        return List.copyOf(result);
    }

    static String normalize(String raw, int width) {
        return String.join("\n", lines(raw, width));
    }

}
