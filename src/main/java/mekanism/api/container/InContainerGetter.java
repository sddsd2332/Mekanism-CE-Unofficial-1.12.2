package mekanism.api.container;

import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;

/**
 * Helper to define a generalized way to get the contents of a container.
 */
@FunctionalInterface
public interface InContainerGetter<STORED> {

    /**
     * @param container Container index of the container to query.
     * @param side      The side we are interacting with the handler from (null for internal).
     *
     * @return The object stored in the given container.
     */
    @Nullable
    STORED getStored(int container, @Nullable EnumFacing side);
}
