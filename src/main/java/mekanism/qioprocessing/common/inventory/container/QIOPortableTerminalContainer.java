package mekanism.qioprocessing.common.inventory.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;

public interface QIOPortableTerminalContainer extends QIOProcessingTerminalSessionContainer {

    @Nonnull
    ItemStack getPortableStack();

    boolean isPortableTargetUsable(@Nonnull EntityPlayer player);

    boolean acceptAuthorizedMutation(long expectedGeneration, long updatedGeneration);
}
