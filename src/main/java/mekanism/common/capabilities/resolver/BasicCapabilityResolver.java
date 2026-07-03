package mekanism.common.capabilities.resolver;

import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class BasicCapabilityResolver implements ICapabilityResolver {

    public static <T> BasicCapabilityResolver create(@Nonnull Capability<T> supportedCapability, @Nonnull Supplier<T> supplier) {
        return new BasicCapabilityResolver(supplier, supportedCapability);
    }

    public static <T> BasicCapabilityResolver constant(@Nonnull Capability<T> supportedCapability, @Nonnull T value) {
        return create(supportedCapability, () -> value);
    }

    private final List<Capability<?>> supportedCapabilities;
    private final Supplier<?> supplier;

    @SafeVarargs
    protected <T> BasicCapabilityResolver(@Nonnull Supplier<T> supplier, @Nonnull Capability<? super T>... supportedCapabilities) {
        this.supplier = Objects.requireNonNull(supplier, "Capability supplier cannot be null.");
        this.supportedCapabilities = Collections.unmodifiableList(Arrays.asList(supportedCapabilities));
    }

    @Nonnull
    @Override
    public List<Capability<?>> getSupportedCapabilities() {
        return supportedCapabilities;
    }

    @Nullable
    @Override
    public <T> T resolve(@Nonnull Capability<T> capability, @Nullable EnumFacing side) {
        Object resolved = supplier.get();
        return resolved == null ? null : (T) resolved;
    }
}
