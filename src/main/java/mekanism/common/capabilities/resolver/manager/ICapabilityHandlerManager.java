package mekanism.common.capabilities.resolver.manager;

import mekanism.common.capabilities.resolver.ICapabilityResolver;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public interface ICapabilityHandlerManager<CONTAINER> extends ICapabilityResolver {

    boolean canHandle();

    @Nonnull
    List<CONTAINER> getContainers(@Nullable EnumFacing side);
}
