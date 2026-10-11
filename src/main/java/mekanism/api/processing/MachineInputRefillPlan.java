package mekanism.api.processing;

import mekanism.api.IContainerTransaction;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Supplier;

/**
 * A current, input-only refill quote. Existing stock covers part of the target;
 * only missing resources are supplied. Quotes never authorize network extraction
 * without revalidation, and never reserve future machine or network outputs.
 */
public final class MachineInputRefillPlan {
    private final Object owner;
    private final List<MachineResourceStack> missing;
    private final List<MachinePort.Snapshot> baselines;
    private final MachineTransferPlan insertion;
    private boolean consumed;

    private MachineInputRefillPlan(Object owner, List<MachineResourceStack> missing,
                                   List<MachinePort.Snapshot> baselines, MachineTransferPlan insertion) {
        this.owner = owner;
        this.missing = Collections.unmodifiableList(new ArrayList<>(missing));
        this.baselines = baselines;
        this.insertion = insertion;
    }

    @Nullable
    public static MachineInputRefillPlan capture(Object owner, MachineRecipeRoute target,
                                                 Map<String, MachinePort> ports) {
        return locked(owner, () -> captureLocked(owner, target, ports));
    }

    private static MachineInputRefillPlan captureLocked(Object owner, MachineRecipeRoute target,
                                                        Map<String, MachinePort> ports) {
        List<MachineResourceStack> missing = new ArrayList<>();
        List<MachinePort.Snapshot> baselines = new ArrayList<>();
        Map<Object, Boolean> containers = new IdentityHashMap<>();
        MachineTransferPlan insertion = MachineTransferPlan.create(owner);
        for (MachineResourceStack input : target.inputs()) {
            MachinePort port = ports.get(input.portId());
            // Aliased/shared containers need a joint allocation contract; fail closed.
            if (port == null || port.isConfiguration() || !port.role().acceptsInput()
                    || !port.acceptsResource(input) || !claimContainer(containers, port.container()))
                return null;
            baselines.add(port.snapshot());
            long wanted = Math.max(0L, input.amount() - port.getStoredAmount(input));
            if (wanted > 0) {
                MachineResourceStack demand = input.withAmount(wanted);
                missing.add(demand);
                insertion.addInsert(port, demand);
            }
        }
        for (MachineResourceStack configuration : target.configurationInputs()) {
            MachinePort port = ports.get(configuration.portId());
            MachineResourceStack current = port == null ? null : port.peek();
            if (port == null || !port.isConfiguration() || current == null || !current.sameResource(configuration))
                return null;
            baselines.add(port.snapshot());
        }
        if (!missing.isEmpty() && !insertion.canExecute()) return null;
        return new MachineInputRefillPlan(owner, missing, baselines, insertion);
    }

    private static boolean claimContainer(Map<Object, Boolean> containers, Object container) {
        if (container == null || containers.put(container, Boolean.TRUE) != null) return false;
        if (container instanceof List<?> members)
            for (Object member : members)
                if (member == null || containers.put(member, Boolean.TRUE) != null) return false;
        return true;
    }

    /**
     * An input-only search hint. The returned amount still requires capture/execute.
     */
    public static long maximumOperations(MachineRecipeRoute route, Map<String, MachinePort> ports, long maximum) {
        if (maximum <= 0) return 0;
        for (MachineResourceStack input : route.inputs()) {
            MachinePort port = ports.get(input.portId());
            if (port == null || port.isConfiguration() || !port.role().acceptsInput() || !port.acceptsResource(input))
                return 0;
            long stored = port.getStoredAmount(input);
            // If existing stock already covers the target, avoid another insertion query.
            if (stored / input.amount() >= maximum) continue;
            long target = maximum > Long.MAX_VALUE / input.amount() ? Long.MAX_VALUE : maximum * input.amount();
            long room = port.getAvailableCapacity(input, Math.max(0L, target - stored));
            long total = room > Long.MAX_VALUE - stored ? Long.MAX_VALUE : room + stored;
            maximum = Math.min(maximum, total / input.amount());
        }
        return maximum;
    }

    public List<MachineResourceStack> missingInputs() {
        return missing;
    }

    public boolean isEmpty() {
        return missing.isEmpty();
    }

    public boolean canExecute() {
        return locked(owner, () -> !consumed && current() && (isEmpty() || insertion.canExecute()));
    }

    public boolean execute() {
        return locked(owner, () -> {
            if (consumed || !current()) return false;
            consumed = true;
            return isEmpty() || insertion.execute();
        });
    }

    private boolean current() {
        for (MachinePort.Snapshot baseline : baselines)
            if (!baseline.matchesCurrent()) return false;
        return true;
    }

    private static <T> T locked(Object owner, Supplier<T> action) {
        if (owner instanceof IContainerTransaction transaction) return transaction.callContainerTransaction(action);
        synchronized (owner) {
            return action.get();
        }
    }
}
