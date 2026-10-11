package mekanism.common.item;

import mekanism.api.EnumColor;
import mekanism.api.mixninapi.ElytraMixinHelp;
import mekanism.common.Mekanism;
import mekanism.common.MekanismItems;
import mekanism.common.util.LangUtils;
import net.minecraft.block.BlockDispenser;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.IItemPropertyGetter;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class ItemHDPEElytra extends ItemMekanism implements ElytraMixinHelp {

    public ItemHDPEElytra() {
        super();
        setMaxStackSize(1);
        setMaxDamage(648);
        setRarity(EnumRarity.RARE);
        this.addPropertyOverride(new ResourceLocation("broken"), new IItemPropertyGetter() {
            @SideOnly(Side.CLIENT)
            public float apply(ItemStack stack, @Nullable World worldIn, @Nullable EntityLivingBase entityIn) {
                return isUsable(stack) ? 0.0F : 1.0F;
            }
        });
        BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.putObject(this, ItemArmor.DISPENSER_BEHAVIOR);
    }

    public static boolean isUsable(ItemStack stack) {
        return stack.getItemDamage() < stack.getMaxDamage() - 1;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack itemstack, World world, @Nonnull List<String> list, @Nonnull ITooltipFlag flag) {
        if (!Mekanism.hooks.MekanismMixinHelp) {
            list.add(EnumColor.DARK_RED + LangUtils.localize("need.installation.mod") + ".");
        }
    }

    @Nullable
    @Override
    public EntityEquipmentSlot getEquipmentSlot(ItemStack stack) {
        return EntityEquipmentSlot.CHEST;
    }

    @Override
    public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
        return repair.getItem() == MekanismItems.HDPE_SHEET;
    }

    @Override
    public boolean canElytraFly(ItemStack stack, EntityLivingBase entity) {
        return isUsable(stack);
    }

    @Override
    public boolean elytraFlightTick(ItemStack stack, EntityLivingBase entity, int flightTicks) {
        if (!entity.world.isRemote && (flightTicks + 1) % 20 == 0) {
            stack.damageItem(1, entity);
        }
        return true;
    }

}
