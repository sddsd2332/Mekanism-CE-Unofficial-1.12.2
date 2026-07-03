package mekanism.common.item;

import mekanism.api.EnumColor;
import mekanism.api.NBTConstants;
import mekanism.common.Mekanism;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.LangUtils;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class ItemCraftingFormula extends ItemMekanism {

    public static ModelResourceLocation MODEL = new ModelResourceLocation(new ResourceLocation(Mekanism.MODID, "CraftingFormula"), "inventory");
    public static ModelResourceLocation INVALID_MODEL = new ModelResourceLocation(new ResourceLocation(Mekanism.MODID, "CraftingFormulaInvalid"), "inventory");
    public static ModelResourceLocation ENCODED_MODEL = new ModelResourceLocation(new ResourceLocation(Mekanism.MODID, "CraftingFormulaEncoded"), "inventory");

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemstack, World world, List<String> list, ITooltipFlag flag) {
        NonNullList<ItemStack> inv = getInventory(itemstack);
        if (inv != null) {
            addIngredientDetails(inv, list);
        }
    }

    private void addIngredientDetails(NonNullList<ItemStack> inv, List<String> list) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack stack : inv) {
            if (!stack.isEmpty()) {
                boolean found = false;
                for (ItemStack iterStack : stacks) {
                    if (InventoryUtils.canStack(stack, iterStack)) {
                        iterStack.grow(stack.getCount());
                        found = true;
                    }
                }
                if (!found) {
                    stacks.add(stack);
                }
            }
        }
        list.add(EnumColor.GREY + LangUtils.localize("tooltip.ingredients") + ":");
        stacks.forEach(stack ->  list.add(EnumColor.GREY + " - " + stack.getDisplayName() + " (" + stack.getCount() + ")"));
    }

    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, @Nonnull EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (player.isSneaking()) {
            if (!world.isRemote) {
                setInventory(stack, null);
                setInvalid(stack, false);
                ((EntityPlayerMP) player).sendContainerToPlayer(player.openContainer);
            }
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        return new ActionResult<>(EnumActionResult.PASS, stack);
    }

    @Override
    public int getItemStackLimit(ItemStack stack) {
        return hasInventory(stack) ? 1 : 64;
    }

    @Nonnull
    @Override
    public String getItemStackDisplayName(@Nonnull ItemStack stack) {
        if (!hasInventory(stack)) {
            return super.getItemStackDisplayName(stack);
        }
        return super.getItemStackDisplayName(stack) + " " + (isInvalid(stack) ? EnumColor.DARK_RED + "(" + LangUtils.localize("tooltip.invalid")
                : EnumColor.DARK_GREEN + "(" + LangUtils.localize("tooltip.encoded")) + ")";
    }

    public boolean isInvalid(ItemStack stack) {
        return ItemDataUtils.getBoolean(stack, NBTConstants.INVALID);
    }

    public void setInvalid(ItemStack stack, boolean invalid) {
        ItemDataUtils.setBoolean(stack, NBTConstants.INVALID, invalid);
    }

    public boolean hasInventory(ItemStack stack) {
        return ItemDataUtils.hasData(stack, NBTConstants.ITEMS, NBT.TAG_LIST);
    }

    public NonNullList<ItemStack> getInventory(ItemStack stack) {
        if (!hasInventory(stack)) {
            return null;
        }
        NBTTagList tagList = ItemDataUtils.getList(stack, NBTConstants.ITEMS);
        NonNullList<ItemStack> inventory = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int tagCount = 0; tagCount < tagList.tagCount(); tagCount++) {
            NBTTagCompound tagCompound = tagList.getCompoundTagAt(tagCount);
            byte slot = tagCompound.getByte(NBTConstants.SLOT);
            if (slot >= 0 && slot < inventory.size()) {
                inventory.set(slot, new ItemStack(tagCompound));
            }
        }
        return inventory;
    }

    public void setInventory(ItemStack stack, NonNullList<ItemStack> inv) {
        if (inv == null) {
            ItemDataUtils.removeData(stack, NBTConstants.ITEMS);
            return;
        }
        NBTTagList tagList = new NBTTagList();
        for (int slot = 0; slot < inv.size(); slot++) {
            ItemStack slotStack = inv.get(slot);
            if (!slotStack.isEmpty()) {
                NBTTagCompound tagCompound = new NBTTagCompound();
                slotStack.writeToNBT(tagCompound);
                tagCompound.setByte(NBTConstants.SLOT, (byte) slot);
                tagList.appendTag(tagCompound);
            }
        }
        ItemDataUtils.setListOrRemove(stack, NBTConstants.ITEMS, tagList);
    }
}
