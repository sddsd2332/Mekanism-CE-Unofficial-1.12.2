package mekanism.common.item;

import cofh.api.core.ISecurable;
import cofh.api.item.IUpgradeItem;
import cofh.api.tileentity.IUpgradeable;
import mekanism.common.advancements.MekanismCriteriaTriggers;
import mekanism.common.base.IMetaItem;
import mekanism.common.config.MekanismConfig;
import mekanism.common.tier.BaseTier;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.upgrade.IUpgradeData;
import mekanism.common.util.UpgradeUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

import javax.annotation.Nonnull;
import java.util.Locale;


@Optional.InterfaceList({
        @Optional.Interface(iface = "cofh.api.item.IUpgradeItem", modid = "cofhcore")
})
public class ItemTierInstaller extends ItemMekanism implements IMetaItem, IUpgradeItem {

    public ItemTierInstaller() {
        super();
        setMaxStackSize(MekanismConfig.current().mekce.MAXTierSize.val());
        setHasSubtypes(true);
    }

    @Nonnull
    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        if (world.isRemote) {
            return EnumActionResult.PASS;
        }
        TileEntity tile = world.getTileEntity(pos);
        ItemStack stack = player.getHeldItem(hand);
        BaseTier tier = getTier(stack);
        if (UpgradeUtils.isUpgradeable(tile)) {
            if (tile instanceof TileEntityBasicBlock basicBlock && !basicBlock.playersUsing.isEmpty()) {
                return EnumActionResult.FAIL;
            }
            IUpgradeData upgradeData = UpgradeUtils.getUpgradeData(tile, tier);
            IBlockState upgradeResult = UpgradeUtils.getUpgradeResult(tile, tier);
            boolean upgraded = upgradeData != null && (upgradeResult == null ? UpgradeUtils.parseUpgradeData(tile, upgradeData) :
                  UpgradeUtils.replaceTileForUpgrade(tile, upgradeResult, upgradeData));
            if (upgraded) {
                if (!player.capabilities.isCreativeMode) {
                    stack.shrink(1);
                }
                triggerInstallerUse(player);
                return EnumActionResult.SUCCESS;
            }
            return EnumActionResult.PASS;
        }
        if (Loader.isModLoaded("cofhcore")) {
            return TEUpgradeable(tile, player, stack);
        }
        EnumActionResult result = addOtherMahineUpgrade(tile, stack, tier, player, world, pos, side, hitX, hitY, hitZ, hand);
        if (result == EnumActionResult.SUCCESS) {
            triggerInstallerUse(player);
        }
        return result;
    }

    //用于处理其他的机器的升级 可以mixin这块
    public EnumActionResult addOtherMahineUpgrade(TileEntity tile, ItemStack stack, BaseTier tier, EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        return EnumActionResult.PASS;
    }

    @Optional.Method(modid = "cofhcore")
    public EnumActionResult TEUpgradeable(TileEntity tile, EntityPlayer player, ItemStack stack) {
        if (!MekanismConfig.current().mekce.TEUpgrade.val()) {
            return EnumActionResult.PASS;
        }
        if (tile instanceof ISecurable securable && !securable.canPlayerAccess(player)) {
            return EnumActionResult.PASS;
        }
        if (tile instanceof IUpgradeable upgradeable) {
            if (!upgradeable.canUpgrade(stack)) {
                return EnumActionResult.PASS;
            }
            if (upgradeable.installUpgrade(stack)) {
                if (!player.capabilities.isCreativeMode) {
                    stack.shrink(1);

                }
                triggerInstallerUse(player);
                return EnumActionResult.SUCCESS;
            }
        }
        return EnumActionResult.PASS;
    }

    private void triggerInstallerUse(EntityPlayer player) {
        if (player instanceof EntityPlayerMP playerMP) {
            MekanismCriteriaTriggers.USE_TIER_INSTALLER.trigger(playerMP);
        }
    }


    @Override
    public String getTexture(int meta) {
        return getTier(meta).getSimpleName() + "TierInstaller";
    }

    @Override
    public int getVariants() {
        return BaseTier.values().length;
    }

    @Override
    public void getSubItems(@Nonnull CreativeTabs tabs, @Nonnull NonNullList<ItemStack> itemList) {
        if (isInCreativeTab(tabs)) {
            for (BaseTier tier : BaseTier.values()) {
                itemList.add(new ItemStack(this, 1, tier.ordinal()));
            }
        }
    }

    @Nonnull
    @Override
    public String getItemStackDisplayName(@Nonnull ItemStack stack) {
        return getTier(stack).getColor() + super.getItemStackDisplayName(stack);
    }

    @Nonnull
    @Override
    public String getTranslationKey(ItemStack stack) {
        return "item." + getTier(stack).getSimpleName().toLowerCase(Locale.ROOT) + "TierInstaller";
    }

    @Override
    @Optional.Method(modid = "cofhcore")
    public UpgradeType getUpgradeType(ItemStack stack) {
        BaseTier tier = getTier(stack);
        if (tier == BaseTier.CREATIVE) {
            return UpgradeType.CREATIVE;
        }
        return UpgradeType.INCREMENTAL;
    }

    @Override
    @Optional.Method(modid = "cofhcore")
    public byte getUpgradeLevel(ItemStack stack) {
        BaseTier tier = getTier(stack);
        return switch (tier) {
            case BASIC -> (byte) 1;
            case ADVANCED -> (byte) 2;
            case ELITE -> (byte) 3;
            case ULTIMATE, CREATIVE -> (byte) 4;
        };
    }

    private BaseTier getTier(ItemStack stack) {
        return getTier(stack.getItemDamage());
    }

    private BaseTier getTier(int meta) {
        if (meta >= 0 && meta < BaseTier.values().length) {
            return BaseTier.values()[meta];
        }
        return BaseTier.BASIC;
    }
}
