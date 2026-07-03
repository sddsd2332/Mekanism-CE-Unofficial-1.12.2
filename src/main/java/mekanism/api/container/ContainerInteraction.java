package mekanism.api.container;

import mekanism.api.Action;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;

/**
 * Helper to define generalized container interactions for use in batch container utils.
 */
@FunctionalInterface
public interface ContainerInteraction<TYPE> {

    /**
     * @param container Container index to interact with.
     * @param stack     Object being interacted with. This must not be modified by the handler.
     * @param side      The side we are interacting with the handler from (null for internal).
     * @param action    The action to perform, either {@link Action#EXECUTE} or {@link Action#SIMULATE}.
     *
     * @return Result of the interaction, for example the result of an insert or extraction.
     */
    @Nullable
    TYPE interact(int container, @Nullable TYPE stack, @Nullable EnumFacing side, Action action);
}
