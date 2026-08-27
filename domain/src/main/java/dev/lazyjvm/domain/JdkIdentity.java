package dev.lazyjvm.domain;

import java.nio.file.Path;

public record JdkIdentity(Path home, String version, String vendor, boolean matched) {
    public static JdkIdentity unknown() {
        return new JdkIdentity(null, "unknown", "unknown", false);
    }
}
