package mekanism.common.inventory;

import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class InventoryPersonalChest extends ItemStackMekanismInventory implements ISlotBackedInventory {

    private static final int SLOT_COUNT = 54;
    public EnumHand currentHand;

    public InventoryPersonalChest(ItemStack stack, EnumHand hand) {
        super(stack);
        currentHand = hand;
    }

    @Override
    protected List<IInventorySlot> getInitialInventory() {
        List<IInventorySlot> inventorySlots = new ArrayList<>(SLOT_COUNT);
        for (int slotY = 0; slotY < 6; slotY++) {
            for (int slotX = 0; slotX < 9; slotX++) {
                inventorySlots.add(BasicInventorySlot.at(this, 8 + slotX * 18, 18 + slotY * 18));
            }
        }
        return inventorySlots;
    }

    public ItemStack getStack() {
        return stack;
    }

    @Nonnull
    @Override
    public String getName() {
        return "PersonalChest";
    }
}
