package mekanism.api.annotations;

import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Equivalent to {@link Supplier}, except with nonnull contract.
 *
 * @see Supplier
 */
@FunctionalInterface
public interface NonNullSupplier<T> {
    @NotNull T get();
}
