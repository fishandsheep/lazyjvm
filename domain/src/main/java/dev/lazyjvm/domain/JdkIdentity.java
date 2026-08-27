package dev.lazyjvm.domain;

import java.nio.file.Path;
import java.util.Objects;

public final class JdkIdentity {
    private final Path home;
    private final String version;
    private final String vendor;
    private final boolean matched;

    public JdkIdentity(Path home, String version, String vendor, boolean matched) {
        this.home = home;
        this.version = version;
        this.vendor = vendor;
        this.matched = matched;
    }

    public Path home() { return home; }
    public String version() { return version; }
    public String vendor() { return vendor; }
    public boolean matched() { return matched; }

    public static JdkIdentity unknown() {
        return new JdkIdentity(null, "unknown", "unknown", false);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof JdkIdentity)) return false;
        JdkIdentity that = (JdkIdentity) other;
        return matched == that.matched && Objects.equals(home, that.home)
                && Objects.equals(version, that.version) && Objects.equals(vendor, that.vendor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(home, version, vendor, matched);
    }

    @Override
    public String toString() {
        return "JdkIdentity[home=" + home + ", version=" + version + ", vendor=" + vendor
                + ", matched=" + matched + "]";
    }
}
