package mekanism.common.capabilities.holder.slot;

import mekanism.api.inventory.IInventorySlot;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.capabilities.holder.ConfigHolder;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.config.slot.ISlotInfo;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class ConfigInventorySlotHolder extends ConfigHolder<IInventorySlot> implements IInventorySlotHolder {

    ConfigInventorySlotHolder(ISideConfiguration sideConfiguration) {
        super(sideConfiguration);
    }

    ConfigInventorySlotHolder(Supplier<EnumFacing> facingSupplier, Supplier<TileComponentConfig> configSupplier) {
        super(facingSupplier, configSupplier);
    }

    void addSlot(@Nonnull IInventorySlot slot) {
        slots.add(slot);
    }

    @Override
    protected TransmissionType getTransmissionType() {
        return TransmissionType.ITEM;
    }

    @Nonnull
    @Override
    public List<IInventorySlot> getInventorySlots(@Nullable EnumFacing direction) {
        return getSlots(direction, slotInfo -> slotInfo instanceof InventorySlotInfo ? ((InventorySlotInfo) slotInfo).getSlots() : Collections.emptyList());
    }

    @Override
    public boolean canInsert(@Nullable EnumFacing direction, @Nonnull IInventorySlot slot) {
        return canInteract(direction, slot, true);
    }

    @Override
    public boolean canExtract(@Nullable EnumFacing direction, @Nonnull IInventorySlot slot) {
        return canInteract(direction, slot, false);
    }

    private boolean canInteract(@Nullable EnumFacing direction, @Nonnull IInventorySlot slot, boolean insert) {
        if (direction == null) {
            return slots.contains(slot);
        }
        ISlotInfo slotInfo = getSlotInfo(direction);
        if (isNoConfig(slotInfo)) {
            return slots.contains(slot);
        }
        if (!(slotInfo instanceof InventorySlotInfo inventorySlotInfo)) {
            return false;
        }
        return insert ? inventorySlotInfo.canInput(slot) : inventorySlotInfo.canOutput(slot);
    }
}
