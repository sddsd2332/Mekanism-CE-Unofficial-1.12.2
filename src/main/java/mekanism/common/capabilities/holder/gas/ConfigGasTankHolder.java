package mekanism.common.capabilities.holder.gas;

import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.capabilities.holder.ConfigHolder;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.config.slot.GasSlotInfo;
import mekanism.common.tile.component.config.slot.ISlotInfo;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class ConfigGasTankHolder extends ConfigHolder<IExtendedGasTank> implements IGasTankHolder {

    public ConfigGasTankHolder(ISideConfiguration sideConfiguration) {
        super(sideConfiguration);
    }

    public ConfigGasTankHolder(Supplier<EnumFacing> facingSupplier, Supplier<TileComponentConfig> configSupplier) {
        super(facingSupplier, configSupplier);
    }

    void addTank(@Nonnull IExtendedGasTank tank) {
        slots.add(tank);
    }

    @Override
    protected TransmissionType getTransmissionType() {
        return TransmissionType.GAS;
    }

    @Nonnull
    @Override
    public List<IExtendedGasTank> getTanks(@Nullable EnumFacing direction) {
        return getSlots(direction, slotInfo -> slotInfo instanceof GasSlotInfo ? ((GasSlotInfo) slotInfo).getTanks() : Collections.emptyList());
    }

    @Override
    public boolean canInsert(@Nullable EnumFacing direction, @Nonnull IExtendedGasTank tank) {
        return canInteract(direction, tank, true);
    }

    @Override
    public boolean canExtract(@Nullable EnumFacing direction, @Nonnull IExtendedGasTank tank) {
        return canInteract(direction, tank, false);
    }

    @Nonnull
    @Override
    public List<IExtendedGasTank> getTanksForInsert(@Nullable EnumFacing direction) {
        return getSlots(direction, slotInfo -> slotInfo instanceof GasSlotInfo ? ((GasSlotInfo) slotInfo).getInputTanks() : Collections.emptyList());
    }

    @Nonnull
    @Override
    public List<IExtendedGasTank> getTanksForExtract(@Nullable EnumFacing direction) {
        return getSlots(direction, slotInfo -> slotInfo instanceof GasSlotInfo ? ((GasSlotInfo) slotInfo).getOutputTanks() : Collections.emptyList());
    }

    private boolean canInteract(@Nullable EnumFacing direction, @Nonnull IExtendedGasTank tank, boolean insert) {
        if (direction == null) {
            return slots.contains(tank);
        }
        ISlotInfo slotInfo = getSlotInfo(direction);
        if (isNoConfig(slotInfo)) {
            return slots.contains(tank);
        }
        if (!(slotInfo instanceof GasSlotInfo gasSlotInfo)) {
            return false;
        }
        return insert ? gasSlotInfo.canInput(tank) : gasSlotInfo.canOutput(tank);
    }
}
