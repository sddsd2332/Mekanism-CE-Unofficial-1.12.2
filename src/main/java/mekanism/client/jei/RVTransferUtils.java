package mekanism.client.jei;

import mekanism.common.inventory.container.ContainerFormulaicAssemblicator;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.slot.FormulaicCraftingSlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import net.minecraft.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

public final class RVTransferUtils {

    private RVTransferUtils() {
    }

    public static List<Slot> getFormulaicInputSlots(ContainerFormulaicAssemblicator container) {
        List<Slot> slots = new ArrayList<>();
        slots.addAll(container.getMainInventorySlots());
        slots.addAll(container.getHotBarSlots());
        for (InventoryContainerSlot slot : container.getInventoryContainerSlots()) {
            if (slot.getInventorySlot() instanceof InputInventorySlot) {
                slots.add(slot);
            }
        }
        return slots;
    }

    public static List<Slot> getFormulaicCraftingSlots(ContainerFormulaicAssemblicator container) {
        List<Slot> slots = new ArrayList<>(9);
        for (InventoryContainerSlot slot : container.getInventoryContainerSlots()) {
            if (slot.getInventorySlot() instanceof FormulaicCraftingSlot) {
                slots.add(slot);
            }
        }
        return slots;
    }
}
