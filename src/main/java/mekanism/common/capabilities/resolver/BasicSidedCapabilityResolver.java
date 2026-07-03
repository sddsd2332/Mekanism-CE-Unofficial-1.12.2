package mekanism.common.capabilities.resolver;

import mekanism.common.capabilities.holder.IHolder;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class BasicSidedCapabilityResolver<HANDLER, BASE_HANDLER> implements ICapabilityResolver {

    private final ProxyCreator<HANDLER, BASE_HANDLER> proxyCreator;
    private final Map<EnumFacing, HANDLER> handlers;
    private final List<Capability<?>> supportedCapabilities;
    private final BASE_HANDLER baseHandler;
    @Nullable
    private HANDLER readOnlyHandler;

    public BasicSidedCapabilityResolver(@Nonnull BASE_HANDLER baseHandler, @Nonnull Capability<HANDLER> supportedCapability,
          @Nonnull BasicProxyCreator<HANDLER, BASE_HANDLER> proxyCreator) {
        this(baseHandler, supportedCapability, proxyCreator, true);
    }

    protected BasicSidedCapabilityResolver(@Nonnull BASE_HANDLER baseHandler, @Nonnull Capability<HANDLER> supportedCapability,
          @Nonnull ProxyCreator<HANDLER, BASE_HANDLER> proxyCreator, boolean canHandle) {
        this.supportedCapabilities = Collections.singletonList(supportedCapability);
        this.baseHandler = baseHandler;
        this.proxyCreator = proxyCreator;
        handlers = canHandle ? new EnumMap<>(EnumFacing.class) : Collections.emptyMap();
    }

    @Nonnull
    public BASE_HANDLER getInternal() {
        return baseHandler;
    }

    @Nonnull
    @Override
    public List<Capability<?>> getSupportedCapabilities() {
        return supportedCapabilities;
    }

    @Nullable
    protected IHolder getHolder() {
        return null;
    }

    @Nullable
    @Override
    public <T> T resolve(@Nonnull Capability<T> capability, @Nullable EnumFacing side) {
        HANDLER handler;
        if (side == null) {
            if (readOnlyHandler == null) {
                readOnlyHandler = proxyCreator.create(baseHandler, null, getHolder());
            }
            handler = readOnlyHandler;
        } else {
            handler = handlers.get(side);
            if (handler == null) {
                handler = proxyCreator.create(baseHandler, side, getHolder());
                handlers.put(side, handler);
            }
        }
        return handler == null ? null : (T) handler;
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

    @FunctionalInterface
    public interface ProxyCreator<HANDLER, BASE_HANDLER> {

        HANDLER create(@Nonnull BASE_HANDLER handler, @Nullable EnumFacing side, @Nullable IHolder holder);
    }

    @FunctionalInterface
    public interface BasicProxyCreator<HANDLER, BASE_HANDLER> extends ProxyCreator<HANDLER, BASE_HANDLER> {

        HANDLER create(@Nonnull BASE_HANDLER handler, @Nullable EnumFacing side);

        @Override
        default HANDLER create(@Nonnull BASE_HANDLER handler, @Nullable EnumFacing side, @Nullable IHolder holder) {
            return create(handler, side);
        }
    }
}
