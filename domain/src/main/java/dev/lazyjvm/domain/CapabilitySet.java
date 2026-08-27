package dev.lazyjvm.domain;

import java.util.EnumSet;
import java.util.Set;

public record CapabilitySet(Set<Capability> available) {
    public CapabilitySet {
        available = available == null || available.isEmpty()
                ? Set.of()
                : Set.copyOf(available);
    }

    public boolean has(Capability capability) {
        return available.contains(capability);
    }

    public static CapabilitySet of(Capability... capabilities) {
        if (capabilities.length == 0) return new CapabilitySet(Set.of());
        EnumSet<Capability> values = EnumSet.noneOf(Capability.class);
        values.addAll(Set.of(capabilities));
        return new CapabilitySet(values);
    }
}
