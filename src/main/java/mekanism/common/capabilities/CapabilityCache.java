package mekanism.common.capabilities;

import mekanism.common.capabilities.resolver.ICapabilityResolver;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CapabilityCache {

    private final List<ICapabilityResolver> resolvers = new ArrayList<>();

    public void setCapabilityResolvers(@Nonnull Collection<? extends ICapabilityResolver> capabilityResolvers) {
        resolvers.clear();
        resolvers.addAll(capabilityResolvers);
    }

    public void addCapabilityResolver(@Nonnull ICapabilityResolver resolver) {
        resolvers.add(resolver);
    }

    @Nonnull
    public List<ICapabilityResolver> getResolvers() {
        return Collections.unmodifiableList(resolvers);
    }

    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing side) {
        for (ICapabilityResolver resolver : resolvers) {
            if (resolver.canResolve(capability, side)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing side) {
        for (ICapabilityResolver resolver : resolvers) {
            if (resolver.canResolve(capability, side)) {
                T resolved = resolver.resolve(capability, side);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        return null;
    }

    public void invalidate(@Nullable Capability<?> capability, @Nullable EnumFacing side) {
        if (capability == null) {
            invalidateAll();
        } else {
            for (ICapabilityResolver resolver : resolvers) {
                if (resolver.supports(capability)) {
                    resolver.invalidate(capability, side);
                }
            }
        }
    }

    public void invalidateAll() {
        for (ICapabilityResolver resolver : resolvers) {
            resolver.invalidateAll();
        }
    }
}
