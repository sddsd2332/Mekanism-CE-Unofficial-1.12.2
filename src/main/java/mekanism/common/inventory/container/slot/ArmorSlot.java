package mekanism.common.inventory.container.slot;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

public class ArmorSlot extends InsertableSlot {

    private final EntityEquipmentSlot slotType;

    public ArmorSlot(InventoryPlayer inventory, int index, int x, int y, EntityEquipmentSlot slotType) {
        super(inventory, index, x, y);
        this.slotType = slotType;
    }

    public ArmorSlot(InventoryPlayer inventory, EntityEquipmentSlot armorType, int x, int y) {
        this(inventory, 35 + armorType.getSlotIndex(), x, y, armorType);
    }

    @Override
    public int getSlotStackLimit() {
        return 1;
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return stack.getItem().isValidArmor(stack, slotType, ((InventoryPlayer) inventory).player);
    }

    @Override
    public boolean canTakeStack(@Nonnull EntityPlayer player) {
        ItemStack stack = getStack();
        return (stack.isEmpty() || player.isCreative() || !EnchantmentHelper.hasBindingCurse(stack)) && super.canTakeStack(player);
    }

    @SideOnly(Side.CLIENT)
    public String getSlotTexture() {
        return ItemArmor.EMPTY_SLOT_NAMES[slotType.getIndex()];
    }
}
