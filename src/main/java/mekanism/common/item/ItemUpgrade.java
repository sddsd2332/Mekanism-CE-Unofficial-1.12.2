package mekanism.common.item;

import mekanism.api.EnumColor;
import mekanism.api.Action;
import mekanism.common.Upgrade;
import mekanism.common.base.IUpgradeItem;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.tile.TileEntityBoundingBlock;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;

public class ItemUpgrade extends ItemMekanism implements IUpgradeItem {

    private final Upgrade upgrade;

    public ItemUpgrade(Upgrade type) {
        super();
        upgrade = Objects.requireNonNull(type, "Upgrade type cannot be null");
        setMaxStackSize(type.getMaxItemStackSize());
        setRarity(EnumRarity.UNCOMMON);
    }

    @Override
    public int getItemStackLimit(ItemStack stack) {
        return upgrade.getMaxItemStackSize();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemstack, World world, List<String> list, ITooltipFlag flag) {
        if (!Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            list.add(LangUtils.localize("tooltip.hold") + " " + EnumColor.AQUA + "shift" + EnumColor.GREY + " " + LangUtils.localize("tooltip.forDetails"));
        } else {
            list.addAll(MekanismUtils.splitTooltip(getUpgradeType(itemstack).getDescription(), itemstack));
        }
    }

    @Override
    public Upgrade getUpgradeType(ItemStack stack) {
        return upgrade;
    }

    @Nonnull
    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        if (player.isSneaking()) {
            TileEntity tile = world.getTileEntity(pos);
            ItemStack stack = player.getHeldItem(hand);
            Upgrade type = getUpgradeType(stack);
            //看看目标是不是虚拟方块，
            if (tile instanceof TileEntityBoundingBlock block) {
                //如果是虚拟方块,且主方块不是空的
                if (block.getMainTile() != null) {
                    //设置tile到为虚拟方块的主方块
                    tile = block.getMainTile();
                }
            }

            if (tile instanceof IUpgradeTile upgradeTile && upgradeTile.supportsUpgrades()) {
                if (upgradeTile.canInstallUpgrade(type)) {
                    TileComponentUpgrade component = upgradeTile.getComponent();
                    if (!world.isRemote) {
                        component.installUpgrade(stack, Action.EXECUTE);
                    }
                    return EnumActionResult.SUCCESS;
                }
            }
        }
        return EnumActionResult.PASS;
    }
}
