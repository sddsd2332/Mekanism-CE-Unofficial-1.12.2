package mekanism.common.capabilities.resolver;

import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public interface ICapabilityResolver {

    @Nonnull
    List<Capability<?>> getSupportedCapabilities();

    default boolean supports(@Nonnull Capability<?> capability) {
        return getSupportedCapabilities().contains(capability);
    }

    default boolean canResolve(@Nonnull Capability<?> capability, @Nullable EnumFacing side) {
        return supports(capability);
    }

    @Nullable
    <T> T resolve(@Nonnull Capability<T> capability, @Nullable EnumFacing side);

    default void invalidate(@Nonnull Capability<?> capability, @Nullable EnumFacing side) {
    }

    default void invalidateAll() {
    }
}
