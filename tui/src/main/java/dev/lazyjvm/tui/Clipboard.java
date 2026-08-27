package dev.lazyjvm.tui;

import org.jline.terminal.Terminal;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/** Clipboard transport with OSC 52; no shell interpolation or shell process. */
final class Clipboard {
    private Clipboard() {}

    static boolean copy(Terminal terminal, String value) {
        String text = value == null ? "" : value;
        String encoded = Base64.getEncoder().encodeToString((value == null ? "" : value)
                .getBytes(StandardCharsets.UTF_8));
        if (terminal != null) {
            try {
                terminal.writer().print("\033]52;c;" + encoded + "\007");
                terminal.writer().flush();
                return true;
            } catch (RuntimeException ignored) {
                // Fall through to direct platform clipboard tools.
            }
        }
        return copyLocal(text);
    }

    static boolean copyLocal(String value) {
        String[] candidates = {"pbcopy", "wl-copy", "xclip", "xsel"};
        for (String candidate : candidates) {
            try {
                Process process;
                if (candidate.equals("xclip")) process = new ProcessBuilder(candidate, "-selection", "clipboard").start();
                else if (candidate.equals("xsel")) process = new ProcessBuilder(candidate, "--clipboard", "--input").start();
                else process = new ProcessBuilder(candidate).start();
                try (var output = process.getOutputStream()) {
                    output.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
                }
                if (process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0) return true;
                process.destroyForcibly();
            } catch (Exception ignored) {
                // Try next known clipboard tool without invoking a shell.
            }
        }
        return false;
    }

    static String osc52(String value) {
        return "\033]52;c;" + Base64.getEncoder().encodeToString((value == null ? "" : value)
                .getBytes(StandardCharsets.UTF_8)) + "\007";
    }
}
