package mekanism.common.capabilities.resolver.manager;

import mekanism.common.capabilities.holder.IHolder;
import mekanism.common.capabilities.resolver.BasicSidedCapabilityResolver;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

public class CapabilityHandlerManager<HOLDER extends IHolder, CONTAINER, HANDLER, BASE_HANDLER>
      extends BasicSidedCapabilityResolver<HANDLER, BASE_HANDLER> implements ICapabilityHandlerManager<CONTAINER> {

    private final BiFunction<HOLDER, EnumFacing, List<CONTAINER>> containerGetter;
    private final boolean canHandle;
    @Nullable
    protected final HOLDER holder;

    protected CapabilityHandlerManager(@Nullable HOLDER holder, @Nonnull BASE_HANDLER baseHandler, @Nonnull Capability<HANDLER> supportedCapability,
          @Nonnull ProxyCreator<HANDLER, BASE_HANDLER> proxyCreator, @Nonnull BiFunction<HOLDER, EnumFacing, List<CONTAINER>> containerGetter) {
        super(baseHandler, supportedCapability, proxyCreator, holder != null);
        this.holder = holder;
        canHandle = holder != null;
        this.containerGetter = containerGetter;
    }

    @Override
    public boolean canHandle() {
        return canHandle;
    }

    @Nonnull
    @Override
    public List<CONTAINER> getContainers(@Nullable EnumFacing side) {
        return canHandle() ? containerGetter.apply(holder, side) : Collections.emptyList();
    }

    @Override
    public boolean canResolve(@Nonnull Capability<?> capability, @Nullable EnumFacing side) {
        return supports(capability) && canHandle() && !getContainers(side).isEmpty();
    }

    @Nullable
    @Override
    protected IHolder getHolder() {
        return holder;
    }

    /**
     * Assumes capability matching has already been checked by {@link #canResolve(Capability, EnumFacing)}.
     */
    @Nullable
    @Override
    public <T> T resolve(@Nonnull Capability<T> capability, @Nullable EnumFacing side) {
        if (getContainers(side).isEmpty()) {
            return null;
        }
        return super.resolve(capability, side);
    }
}
