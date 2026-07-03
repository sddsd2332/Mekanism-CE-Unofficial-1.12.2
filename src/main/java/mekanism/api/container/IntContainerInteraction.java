package mekanism.api.container;

import mekanism.api.Action;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;

/**
 * Helper to define generalized integer-based container interactions for use in batch container utils.
 */
@FunctionalInterface
public interface IntContainerInteraction<TYPE> {

    /**
     * @param container Container index to interact with.
     * @param amount    Amount being adjusted by the interaction.
     * @param side      The side we are interacting with the handler from (null for internal).
     * @param action    The action to perform, either {@link Action#EXECUTE} or {@link Action#SIMULATE}.
     *
     * @return Result of the interaction, for example the result of an extraction.
     */
    @Nullable
    TYPE interact(int container, int amount, @Nullable EnumFacing side, Action action);
}
