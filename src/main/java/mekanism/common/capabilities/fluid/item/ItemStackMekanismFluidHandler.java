package mekanism.common.capabilities.fluid.item;

import mekanism.api.NBTConstants;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.fluid.IMekanismFluidHandler;
import mekanism.common.capabilities.ItemCapabilityWrapper.ItemCapability;
import mekanism.common.util.ItemDataUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Helper class for implementing fluid handlers for items.
 */
public abstract class ItemStackMekanismFluidHandler extends ItemCapability implements IMekanismFluidHandler, IFluidHandlerItem {

    @Nullable
    private final String legacyFluidKey;
    protected List<IExtendedFluidTank> tanks;

    protected ItemStackMekanismFluidHandler() {
        this(null);
    }

    protected ItemStackMekanismFluidHandler(@Nullable String legacyFluidKey) {
        this.legacyFluidKey = legacyFluidKey;
    }

    protected abstract List<IExtendedFluidTank> getInitialTanks();

    @Override
    protected void init() {
        super.init();
        this.tanks = getInitialTanks();
    }

    @Override
    protected void load() {
        super.load();
        if (legacyFluidKey != null && getFluidTanks(null).size() == 1) {
            getFluidTanks(null).get(0).setStackUnchecked(ItemDataUtils.getStoredFluid(getStack(), legacyFluidKey));
        } else {
            ItemDataUtils.readContainers(getStack(), NBTConstants.FLUID_TANKS, getFluidTanks(null));
        }
    }

    @Nonnull
    @Override
    public List<IExtendedFluidTank> getFluidTanks(@Nullable EnumFacing side) {
        return tanks;
    }

    @Override
    public void onContentsChanged() {
        List<IExtendedFluidTank> fluidTanks = getFluidTanks(null);
        if (legacyFluidKey != null && fluidTanks.size() == 1) {
            IExtendedFluidTank tank = fluidTanks.get(0);
            ItemDataUtils.setStoredFluid(getStack(), legacyFluidKey, tank.getFluid(), tank.getCapacity());
        } else {
            ItemDataUtils.writeContainers(getStack(), NBTConstants.FLUID_TANKS, fluidTanks);
        }
    }

    @Nonnull
    @Override
    public ItemStack getContainer() {
        return getStack();
    }

    @Override
    public boolean canProcess(Capability<?> capability) {
        return capability == CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY;
    }
}
