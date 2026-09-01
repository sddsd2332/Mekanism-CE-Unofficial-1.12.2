package mekanism.api.processing;

import mekanism.api.IContainerTransaction;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Atomic local transfer plan spanning multiple machine slots and tanks.
 */
public final class MachineTransferPlan {

    private final List<Entry> entries = new ArrayList<>();
    private final List<MachineResourceStack> extracted = new ArrayList<>();
    @Nullable
    private final IContainerTransaction transactionOwner;
    private final Object fallbackLock;

    private MachineTransferPlan(@Nullable IContainerTransaction transactionOwner, Object fallbackLock) {
        this.transactionOwner = transactionOwner;
        this.fallbackLock = fallbackLock;
    }

    public static MachineTransferPlan create() {
        return new MachineTransferPlan(null, new Object());
    }

    public static MachineTransferPlan create(@Nullable Object owner) {
        return new MachineTransferPlan(owner instanceof IContainerTransaction transaction ? transaction : null,
              owner == null ? new Object() : owner);
    }

    public MachineTransferPlan addInsert(@Nullable MachinePort port, @Nullable MachineResourceStack stack) {
        entries.add(new Entry(Operation.INSERT, port, stack));
        return this;
    }

    public MachineTransferPlan addExtract(@Nullable MachinePort port, @Nullable MachineResourceStack stack) {
        entries.add(new Entry(Operation.EXTRACT, port, stack));
        return this;
    }

    public boolean canExecute() {
        return inTransaction(this::canExecuteLocked);
    }

    public boolean execute() {
        return inTransaction(this::executeLocked);
    }

    public List<MachineResourceStack> getExtracted() {
        return Collections.unmodifiableList(new ArrayList<>(extracted));
    }

    private boolean canExecuteLocked() {
        if (entries.isEmpty() || hasDuplicateContainers()) {
            return false;
        }
        for (Entry entry : entries) {
            if (entry.port == null || entry.stack == null || !entry.port.acceptsResource(entry.stack)) {
                return false;
            }
            if (entry.operation == Operation.INSERT ? !entry.port.canInsert(entry.stack) : !entry.port.canExtract(entry.stack)) {
                return false;
            }
        }
        return true;
    }

    private boolean executeLocked() {
        extracted.clear();
        if (!canExecuteLocked()) {
            return false;
        }
        List<MachinePort.Snapshot> snapshots = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            snapshots.add(entry.port.snapshot());
        }
        try {
            for (Entry entry : entries) {
                boolean success;
                if (entry.operation == Operation.INSERT) {
                    success = entry.port.insert(entry.stack);
                } else {
                    MachineResourceStack value = entry.port.extract(entry.stack);
                    success = value != null;
                    if (success) {
                        extracted.add(value);
                    }
                }
                if (!success) {
                    restore(snapshots);
                    extracted.clear();
                    return false;
                }
            }
        } catch (RuntimeException | Error failure) {
            restoreAfterFailure(snapshots, failure);
            extracted.clear();
            throw failure;
        }
        return true;
    }

    private boolean hasDuplicateContainers() {
        Map<Object, Boolean> containers = new IdentityHashMap<>();
        for (Entry entry : entries) {
            if (entry.port != null && containers.put(entry.port.container(), Boolean.TRUE) != null) {
                return true;
            }
        }
        return false;
    }

    private static void restore(List<MachinePort.Snapshot> snapshots) {
        for (int i = snapshots.size() - 1; i >= 0; i--) {
            snapshots.get(i).restore();
        }
    }

    private static void restoreAfterFailure(List<MachinePort.Snapshot> snapshots,
          Throwable failure) {
        for (int i = snapshots.size() - 1; i >= 0; i--) {
            try {
                snapshots.get(i).restore();
            } catch (RuntimeException | Error restoreFailure) {
                failure.addSuppressed(restoreFailure);
            }
        }
    }

    private boolean inTransaction(Supplier<Boolean> action) {
        if (transactionOwner != null) {
            return transactionOwner.callContainerTransaction(action);
        }
        synchronized (fallbackLock) {
            return action.get();
        }
    }

    private enum Operation {
        INSERT,
        EXTRACT
    }

    private static final class Entry {

        private final Operation operation;
        @Nullable
        private final MachinePort port;
        @Nullable
        private final MachineResourceStack stack;

        private Entry(Operation operation, @Nullable MachinePort port, @Nullable MachineResourceStack stack) {
            this.operation = operation;
            this.port = port;
            this.stack = stack;
        }
    }
}
