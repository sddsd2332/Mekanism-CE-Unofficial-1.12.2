package mekanism.common.tile.component.config.slot;

import mekanism.api.inventory.IInventorySlot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InventorySlotInfo extends BaseSlotInfo {

    private final List<IInventorySlot> inventorySlots;
    private final List<IInventorySlot> inputSlots;
    private final List<IInventorySlot> outputSlots;

    public InventorySlotInfo(boolean canInput, boolean canOutput, IInventorySlot... slots) {
        this(canInput, canOutput, Arrays.asList(slots));
    }

    public InventorySlotInfo(boolean canInput, boolean canOutput, List<IInventorySlot> slots) {
        this(canInput, canOutput, slots, canInput ? slots : Collections.emptyList(), canOutput ? slots : Collections.emptyList());
    }

    public InventorySlotInfo(boolean canInput, boolean canOutput, List<IInventorySlot> slots, List<IInventorySlot> inputSlots, List<IInventorySlot> outputSlots) {
        super(canInput, canOutput);
        inventorySlots = Collections.unmodifiableList(slots);
        this.inputSlots = Collections.unmodifiableList(new ArrayList<>(inputSlots));
        this.outputSlots = Collections.unmodifiableList(new ArrayList<>(outputSlots));
    }

    public List<IInventorySlot> getSlots() {
        return inventorySlots;
    }

    public List<IInventorySlot> getInputSlots() {
        return canInput() ? inputSlots : Collections.emptyList();
    }

    public List<IInventorySlot> getOutputSlots() {
        return canOutput() ? outputSlots : Collections.emptyList();
    }

    public boolean canInput(IInventorySlot slot) {
        return getInputSlots().contains(slot);
    }

    public boolean canOutput(IInventorySlot slot) {
        return getOutputSlots().contains(slot);
    }

    @Override
    public boolean isEmpty() {
        for (IInventorySlot slot : getOutputSlots()) {
            if (!slot.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public boolean hasSlot(IInventorySlot slot) {
        return inventorySlots.contains(slot);
    }
}
