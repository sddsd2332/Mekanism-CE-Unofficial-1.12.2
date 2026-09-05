package mekanism.api;

/**
 * Detached, worker-safe calculation function. Implementations must be stateless or
 * contain only immutable value configuration; they must not capture a TileEntity,
 * World, capability, handler, container, registry, callback, or mutable collection.
 */
@FunctionalInterface
public interface IAsyncPlanCalculator<SNAPSHOT, PLAN> {

    PLAN calculate(SNAPSHOT snapshot);
}
