package mekanism.api;

import java.util.function.Supplier;

/**
 * Provides an owner-scoped transaction boundary for a machine's containers and local processing state.
 *
 * <p>Transactions are reentrant for the same owner. Callers must not access networks, wait for other machines, or
 * perform cross-machine transfers while a transaction is held. Container listeners run before the transaction is
 * released and must follow the same restriction.</p>
 */
public interface IContainerTransaction {

    /**
     * Runs an action while holding this owner's container transaction.
     */
    void runContainerTransaction(Runnable action);

    /**
     * Calls an action while holding this owner's container transaction.
     */
    <T> T callContainerTransaction(Supplier<T> action);
}
