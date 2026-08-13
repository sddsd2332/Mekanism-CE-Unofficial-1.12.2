package mekanism.qioprocessing.common.terminal;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;

import javax.annotation.Nonnull;

/** Resource-kind filter used by the smart-processing production catalog. */
public enum QIOSmartProcessingResourceFilter {
    ALL,
    ITEM,
    FLUID,
    GAS;

    public boolean matches(@Nonnull PortableResourceDescriptor resource) {
        return this == ALL || resource.getKind().name().equals(name());
    }

    @Nonnull
    public static QIOSmartProcessingResourceFilter byId(int id) {
        QIOSmartProcessingResourceFilter[] values = values();
        if (id < 0 || id >= values.length) {
            throw new IllegalArgumentException("Unknown smart-processing resource filter " + id);
        }
        return values[id];
    }
}
