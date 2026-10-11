package mekanism.tools.item;

import mekanism.common.util.LangUtils;
import mekanism.tools.common.IHasRepairType;
import mekanism.tools.common.Materials;
import net.minecraft.block.BlockDispenser;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;
import java.util.List;

public class ItemMekanismShield extends Item implements IHasRepairType {

    protected Materials toolMaterial;

    public ItemMekanismShield(Materials toolMaterial, int maxDamage) {
        super();
        this.toolMaterial = toolMaterial;
        this.maxStackSize = 1;
        this.setMaxDamage(maxDamage);
        addPropertyOverride(new ResourceLocation("blocking"), new IItemPropertyGetter() {
            @SideOnly(Side.CLIENT)
            public float apply(ItemStack stack, @Nullable World worldIn, @Nullable EntityLivingBase entityIn) {

                return entityIn != null && entityIn.isHandActive() && entityIn.getActiveItemStack() == stack ? 1.0F : 0.0F;
            }
        });
        BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.putObject(this, ItemArmor.DISPENSER_BEHAVIOR);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemstack, World world, List<String> list, ITooltipFlag flag) {
        list.add(LangUtils.localize("tooltip.hp") + ": " + (itemstack.getMaxDamage() - itemstack.getItemDamage()));
    }

    @Override
    public boolean isShield(ItemStack stack, @Nullable EntityLivingBase entity) {
        return true;
    }


    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.BLOCK;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand hand) {
        ItemStack itemstack = playerIn.getHeldItem(hand);
        playerIn.setActiveHand(hand);
        return new ActionResult<>(EnumActionResult.SUCCESS, itemstack);
    }

    @Override
    public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
        ItemStack mat = toolMaterial.getMaterial().getRepairItemStack();
        if (!mat.isEmpty() && OreDictionary.itemMatches(mat, repair, false)) {
            return true;
        }
        return super.getIsRepairable(toRepair, repair);
    }

    @Override
    public ItemStack getRepairStack() {
        return toolMaterial.getMaterial().getRepairItemStack();
    }
}
