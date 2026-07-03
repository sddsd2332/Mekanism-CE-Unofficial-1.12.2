package mekanism.common.capabilities.gas.item;

import mekanism.api.Action;
import mekanism.api.NBTConstants;
import mekanism.api.gas.*;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.ItemCapabilityWrapper.ItemCapability;
import mekanism.common.util.ItemDataUtils;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Helper class for implementing gas handlers for items.
 */
public abstract class ItemStackMekanismGasHandler extends ItemCapability implements IMekanismGasHandler {

    @Nullable
    private final String legacyGasKey;
    protected List<IExtendedGasTank> tanks;

    protected ItemStackMekanismGasHandler() {
        this(null);
    }

    protected ItemStackMekanismGasHandler(@Nullable String legacyGasKey) {
        this.legacyGasKey = legacyGasKey;
    }

    protected abstract List<IExtendedGasTank> getInitialTanks();

    @Override
    protected void init() {
        super.init();
        this.tanks = getInitialTanks();
    }

    @Override
    protected void load() {
        super.load();
        if (legacyGasKey != null && getGasTanks(null).size() == 1) {
            getGasTanks(null).get(0).setStackUnchecked(ItemDataUtils.getStoredGas(getStack(), legacyGasKey));
        } else {
            ItemDataUtils.readContainers(getStack(), NBTConstants.GAS_TANKS, getGasTanks(null));
        }
    }

    @Nonnull
    @Override
    public List<IExtendedGasTank> getGasTanks(@Nullable EnumFacing side) {
        return tanks;
    }

    @Override
    public boolean canInsertGas(@Nullable EnumFacing side) {
        return true;
    }

    @Override
    public boolean canExtractGas(@Nullable EnumFacing side) {
        return true;
    }

    @Override
    public int receiveGas(EnumFacing side, GasStack stack, boolean doTransfer) {
        if (ExtendedGasHandlerUtils.isEmpty(stack)) {
            return 0;
        }
        GasStack remainder = insertGas(stack, side, Action.get(doTransfer));
        return stack.amount - (remainder == null ? 0 : remainder.amount);
    }

    @Override
    @Nullable
    public GasStack drawGas(EnumFacing side, int amount, boolean doTransfer) {
        if (amount <= 0) {
            return null;
        }
        return extractGas(amount, side, Action.get(doTransfer));
    }

    @Override
    public boolean canReceiveGas(EnumFacing side, Gas type) {
        if (type == null || !canInsertGas(side)) {
            return false;
        }
        GasStack remainder = insertGas(new GasStack(type, 1), side, Action.SIMULATE);
        return remainder == null || remainder.amount < 1;
    }

    @Override
    public boolean canDrawGas(EnumFacing side, Gas type) {
        if (!canExtractGas(side)) {
            return false;
        }
        GasStack extracted = type == null ? extractGas(1, side, Action.SIMULATE) : extractGas(new GasStack(type, 1), side, Action.SIMULATE);
        return extracted != null && extracted.amount > 0;
    }

    @Override
    public void onContentsChanged() {
        List<IExtendedGasTank> gasTanks = getGasTanks(null);
        if (legacyGasKey != null && gasTanks.size() == 1) {
            IExtendedGasTank tank = gasTanks.get(0);
            ItemDataUtils.setStoredGas(getStack(), legacyGasKey, tank.getGas(), tank.getCapacity());
        } else {
            ItemDataUtils.writeContainers(getStack(), NBTConstants.GAS_TANKS, gasTanks);
        }
    }

    @Override
    public boolean canProcess(Capability<?> capability) {
        return capability == Capabilities.GAS_HANDLER_CAPABILITY;
    }
}
