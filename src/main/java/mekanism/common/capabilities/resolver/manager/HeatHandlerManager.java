package mekanism.common.capabilities.resolver.manager;

import mekanism.api.IHeatTransfer;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class HeatHandlerManager implements ICapabilityHandlerManager<IHeatCapacitor> {

    @Nullable
    private final IHeatCapacitorHolder holder;

    public HeatHandlerManager(@Nullable IHeatCapacitorHolder holder) {
        this.holder = holder;
    }

    @Override
    public boolean canHandle() {
        return holder != null;
    }

    @Nonnull
    @Override
    public List<IHeatCapacitor> getContainers(@Nullable EnumFacing side) {
        return holder == null ? Collections.emptyList() : holder.getHeatCapacitors(side);
    }

    @Nonnull
    @Override
    public List<Capability<?>> getSupportedCapabilities() {
        return Collections.singletonList(Capabilities.HEAT_TRANSFER_CAPABILITY);
    }

    @Override
    public boolean canResolve(@Nonnull Capability<?> capability, @Nullable EnumFacing side) {
        return supports(capability) && canHandle() && getHeatTransfer(side) != null;
    }

    @Nullable
    @Override
    public <T> T resolve(@Nonnull Capability<T> capability, @Nullable EnumFacing side) {
        IHeatTransfer heatTransfer = getHeatTransfer(side);
        return heatTransfer == null ? null : (T) heatTransfer;
    }

    @Nullable
    private IHeatTransfer getHeatTransfer(@Nullable EnumFacing side) {
        for (IHeatCapacitor capacitor : getContainers(side)) {
            if (capacitor instanceof IHeatTransfer) {
                return (IHeatTransfer) capacitor;
            }
        }
        return null;
    }
}
