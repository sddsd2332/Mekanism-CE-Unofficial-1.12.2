package mekanism.common.item;

import mekanism.api.IAlloyInteraction;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.tier.AlloyTier;
import mekanism.common.util.CapabilityUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

public class ItemAlloy extends ItemMekanism {


    private final AlloyTier tier;

    public ItemAlloy(AlloyTier tier) {
        this.tier = tier;
    }

    @Nonnull
    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(pos);
        ItemStack stack = player.getHeldItem(hand);
        if (MekanismConfig.current().general.allowTransmitterAlloyUpgrade.val() && CapabilityUtils.hasCapability(tile, Capabilities.ALLOY_INTERACTION_CAPABILITY, side)) {
            if (!world.isRemote) {
                IAlloyInteraction interaction = CapabilityUtils.getCapability(tile, Capabilities.ALLOY_INTERACTION_CAPABILITY, side);
                interaction.onAlloyInteraction(player, stack, tier);
            }
            return EnumActionResult.SUCCESS;
        }
        return EnumActionResult.PASS;
    }


    public AlloyTier getTier() {
        return tier;
    }
}
