package mekanism.qioprocessing.common.item;

import mekanism.common.item.ItemBlockQIOComponent;
import mekanism.qioprocessing.common.tile.QIOProcessingTerminal;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

/** Item form that retains exact terminal identity and frequency-reference NBT. */
public final class ItemBlockQIOProcessingTerminal extends ItemBlockQIOComponent {

    public ItemBlockQIOProcessingTerminal(Block block) {
        super(block);
        setMaxStackSize(1);
    }

    @Override
    public void restorePlacementData(@Nonnull ItemStack stack,
          @Nonnull EntityLivingBase placer, @Nonnull World world,
          @Nonnull BlockPos pos, @Nonnull TileEntity tileEntity) {
        if (!(tileEntity instanceof QIOProcessingTerminal terminal)) {
            super.restorePlacementData(stack, placer, world, pos, tileEntity);
            return;
        }
        java.util.UUID owner = getOwnerUUID(stack);
        if (owner != null) {
            terminal.getSecurity().setOwnerUUID(owner);
        } else if (placer != null) {
            terminal.getSecurity().setOwnerUUID(placer.getUniqueID());
        }
        terminal.getSecurity().setMode(getSecurity(stack));
        if (mekanism.common.util.ItemDataUtils.hasData(stack, "qioSustained",
              net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND)) {
            terminal.readSustainedQIOData(mekanism.common.util.ItemDataUtils.getCompound(
                  stack, "qioSustained"));
        }
        if (!world.isRemote && placer instanceof EntityPlayer player) {
            terminal.restoreExactBinding(player.getUniqueID());
        }
    }
}
