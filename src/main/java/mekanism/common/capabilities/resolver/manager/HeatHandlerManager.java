package mekanism.common.capabilities.resolver.manager;

import mekanism.api.IHeatTransfer;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.heat.IHeatHandler;
import mekanism.api.heat.ISidedHeatHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.heat.ITileHeatHandler;
import mekanism.common.capabilities.heat.LegacyHeatTransferAdapter;
import mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder;
import mekanism.common.capabilities.proxy.ProxyHeatHandler;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HeatHandlerManager implements ICapabilityHandlerManager<IHeatCapacitor> {

    @Nullable
    private final IHeatCapacitorHolder holder;
    private final ISidedHeatHandler heatHandler;

    public HeatHandlerManager(@Nullable IHeatCapacitorHolder holder) {
        this(new HolderSidedHeatHandler(holder), holder);
    }

    public HeatHandlerManager(ISidedHeatHandler heatHandler, @Nullable IHeatCapacitorHolder holder) {
        this.heatHandler = heatHandler;
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
        return Arrays.asList(Capabilities.HEAT_HANDLER_CAPABILITY, Capabilities.HEAT_TRANSFER_CAPABILITY);
    }

    @Override
    public boolean canResolve(@Nonnull Capability<?> capability, @Nullable EnumFacing side) {
        return supports(capability) && canHandle() && !getContainers(side).isEmpty();
    }

    @Nullable
    @Override
    public <T> T resolve(@Nonnull Capability<T> capability, @Nullable EnumFacing side) {
        IHeatHandler resolved = side == null ? heatHandler : new ProxyHeatHandler(heatHandler, side, holder);
        if (capability == Capabilities.HEAT_HANDLER_CAPABILITY) {
            return (T) resolved;
        } else if (capability == Capabilities.HEAT_TRANSFER_CAPABILITY) {
            if (heatHandler instanceof ITileHeatHandler tileHandler) {
                return (T) new LegacyHeatTransferAdapter(resolved, tileHandler, side);
            }
            return (T) new LegacyHeatTransferAdapter(resolved);
        }
        return null;
    }

    private static class HolderSidedHeatHandler implements ISidedHeatHandler {

        @Nullable
        private final IHeatCapacitorHolder holder;

        private HolderSidedHeatHandler(@Nullable IHeatCapacitorHolder holder) {
            this.holder = holder;
        }

        private List<IHeatCapacitor> getCapacitors(@Nullable EnumFacing side) {
            return holder == null ? Collections.emptyList() : holder.getHeatCapacitors(side);
        }

        @Override
        public int getHeatCapacitorCount(@Nullable EnumFacing side) {
            return getCapacitors(side).size();
        }

        @Override
        public double getTemperature(int capacitor, @Nullable EnumFacing side) {
            List<IHeatCapacitor> capacitors = getCapacitors(side);
            return capacitor >= 0 && capacitor < capacitors.size() ? capacitors.get(capacitor).getTemperature() : mekanism.api.heat.HeatAPI.AMBIENT_TEMP;
        }

        @Override
        public double getInverseConduction(int capacitor, @Nullable EnumFacing side) {
            List<IHeatCapacitor> capacitors = getCapacitors(side);
            return capacitor >= 0 && capacitor < capacitors.size() ? capacitors.get(capacitor).getInverseConduction() : mekanism.api.heat.HeatAPI.DEFAULT_INVERSE_CONDUCTION;
        }

        @Override
        public double getHeatCapacity(int capacitor, @Nullable EnumFacing side) {
            List<IHeatCapacitor> capacitors = getCapacitors(side);
            return capacitor >= 0 && capacitor < capacitors.size() ? capacitors.get(capacitor).getHeatCapacity() : mekanism.api.heat.HeatAPI.DEFAULT_HEAT_CAPACITY;
        }

        @Override
        public void handleHeat(int capacitor, double transfer, @Nullable EnumFacing side) {
            List<IHeatCapacitor> capacitors = getCapacitors(side);
            if (capacitor >= 0 && capacitor < capacitors.size()) {
                capacitors.get(capacitor).handleHeat(transfer);
            }
        }

        @Override
        public Object getHeatIdentity(@Nullable EnumFacing side) {
            List<IHeatCapacitor> capacitors = getCapacitors(side);
            return capacitors.size() == 1 ? capacitors.get(0).getHeatIdentity() : this;
        }
    }
}
