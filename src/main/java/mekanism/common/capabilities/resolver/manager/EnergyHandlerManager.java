package mekanism.common.capabilities.resolver.manager;

import mekanism.api.energy.*;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.proxy.ProxyStrictEnergyHandler;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class EnergyHandlerManager implements ICapabilityHandlerManager<IEnergyContainer> {

    @Nullable
    private final IEnergyContainerHolder holder;
    private final ISidedStrictEnergyHandler baseHandler;
    private final boolean canHandle;
    private final Map<EnumFacing, IStrictEnergyHandler> handlers;
    @Nullable
    private IStrictEnergyHandler readOnlyHandler;

    public EnergyHandlerManager(@Nullable IEnergyContainerHolder holder, @Nonnull ISidedStrictEnergyHandler baseHandler) {
        this.holder = holder;
        this.baseHandler = baseHandler;
        canHandle = holder != null;
        handlers = canHandle ? new EnumMap<>(EnumFacing.class) : Collections.emptyMap();
    }

    @Override
    public boolean canHandle() {
        return canHandle;
    }

    @Nonnull
    @Override
    public List<IEnergyContainer> getContainers(@Nullable EnumFacing side) {
        return holder == null ? Collections.emptyList() : holder.getEnergyContainers(side);
    }

    @Nonnull
    @Override
    public List<Capability<?>> getSupportedCapabilities() {
        return Arrays.asList(Capabilities.STRICT_ENERGY_CAPABILITY, Capabilities.ENERGY_STORAGE_CAPABILITY, Capabilities.ENERGY_ACCEPTOR_CAPABILITY,
              Capabilities.ENERGY_OUTPUTTER_CAPABILITY);
    }

    @Override
    public boolean canResolve(@Nonnull Capability<?> capability, @Nullable EnumFacing side) {
        if (!supports(capability) || !canHandle() || getContainers(side).isEmpty()) {
            return false;
        }
        if (capability == Capabilities.STRICT_ENERGY_CAPABILITY) {
            return true;
        }
        if (side == null) {
            return capability == Capabilities.ENERGY_STORAGE_CAPABILITY;
        }
        if (capability == Capabilities.ENERGY_ACCEPTOR_CAPABILITY) {
            return holder.canInsert(side);
        } else if (capability == Capabilities.ENERGY_OUTPUTTER_CAPABILITY) {
            return holder.canExtract(side);
        }
        return true;
    }

    @Nullable
    @Override
    public <T> T resolve(@Nonnull Capability<T> capability, @Nullable EnumFacing side) {
        if (getContainers(side).isEmpty()) {
            return null;
        }
        IStrictEnergyHandler handler = getHandler(side);
        if (capability == Capabilities.STRICT_ENERGY_CAPABILITY ||
              capability == Capabilities.ENERGY_STORAGE_CAPABILITY && handler instanceof IStrictEnergyStorage ||
              capability == Capabilities.ENERGY_ACCEPTOR_CAPABILITY && handler instanceof IStrictEnergyAcceptor ||
              capability == Capabilities.ENERGY_OUTPUTTER_CAPABILITY && handler instanceof IStrictEnergyOutputter) {
            return (T) handler;
        }
        return null;
    }

    @Nonnull
    private IStrictEnergyHandler getHandler(@Nullable EnumFacing side) {
        if (side == null) {
            if (readOnlyHandler == null) {
                readOnlyHandler = new ProxyStrictEnergyHandler(baseHandler, null, holder);
            }
            return readOnlyHandler;
        }
        IStrictEnergyHandler handler = handlers.get(side);
        if (handler == null) {
            handler = new ProxyStrictEnergyHandler(baseHandler, side, holder);
            handlers.put(side, handler);
        }
        return handler;
    }

    @Override
    public void invalidate(@Nonnull Capability<?> capability, @Nullable EnumFacing side) {
        if (side == null) {
            readOnlyHandler = null;
        } else {
            handlers.remove(side);
        }
    }

    @Override
    public void invalidateAll() {
        readOnlyHandler = null;
        handlers.clear();
    }
}
