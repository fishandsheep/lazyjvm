package dev.lazyjvm.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.Objects;

public final class CapabilitySet {
    private final Set<Capability> available;

    public CapabilitySet(Set<Capability> available) {
        this.available = available == null || available.isEmpty()
                ? Collections.<Capability>emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(available));
    }

    public Set<Capability> available() {
        return available;
    }

    public boolean has(Capability capability) {
        return available.contains(capability);
    }

    public static CapabilitySet of(Capability... capabilities) {
        if (capabilities == null || capabilities.length == 0) return new CapabilitySet(null);
        EnumSet<Capability> values = EnumSet.noneOf(Capability.class);
        Collections.addAll(values, capabilities);
        return new CapabilitySet(values);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CapabilitySet)) return false;
        CapabilitySet that = (CapabilitySet) other;
        return available.equals(that.available);
    }

    @Override
    public int hashCode() {
        return Objects.hash(available);
    }

    @Override
    public String toString() {
        return "CapabilitySet[available=" + available + "]";
    }
}
