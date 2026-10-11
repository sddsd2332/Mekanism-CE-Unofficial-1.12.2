package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;

import java.math.BigInteger;
import java.util.*;

/**
 * Material projection of the displayed partial graph, never an executable plan or reservation.
 */
final class QIOPlanningDiagnostics {
    private QIOPlanningDiagnostics() {
    }

    static Map<PortableResourceDescriptor, Long> requirements(List<QIOPlanStep> steps) {
        Map<Long, QIOPlanStep> nodes = new LinkedHashMap<>();
        Map<Long, Integer> pending = new HashMap<>();
        Map<Long, List<Long>> consumers = new HashMap<>();
        for (QIOPlanStep step : steps) nodes.put(step.getNodeId(), step);
        Deque<QIOPlanStep> ready = new ArrayDeque<>();
        for (QIOPlanStep step : nodes.values()) {
            int count = 0;
            for (long dependency : step.getDependencies())
                if (nodes.containsKey(dependency)) {
                    count++;
                    consumers.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(step.getNodeId());
                }
            pending.put(step.getNodeId(), count);
            if (count == 0) ready.add(step);
        }
        Map<PortableResourceDescriptor, BigInteger> balance = new TreeMap<>(), needed = new TreeMap<>();
        while (!ready.isEmpty()) {
            QIOPlanStep step = ready.removeFirst();
            BigInteger operations = BigInteger.valueOf(step.getOperations());
            step.getExactInputs().forEach((resource, amount) -> {
                BigInteger cost = operations.multiply(BigInteger.valueOf(amount));
                BigInteger present = balance.getOrDefault(resource, BigInteger.ZERO);
                BigInteger used = present.min(cost);
                balance.put(resource, present.subtract(used));
                if (cost.compareTo(used) > 0) needed.merge(resource, cost.subtract(used), BigInteger::add);
            });
            step.getGuaranteedOutputs().forEach((resource, amount) -> balance.merge(resource,
                    operations.multiply(BigInteger.valueOf(amount)), BigInteger::add));
            for (long consumer : consumers.getOrDefault(step.getNodeId(), Collections.emptyList()))
                if (pending.merge(consumer, -1, Integer::sum) == 0) ready.addLast(nodes.get(consumer));
        }
        Map<PortableResourceDescriptor, Long> result = new LinkedHashMap<>();
        // The existing preview protocol is long-valued. Overflow is already a failed result;
        // saturate only its display, never quantities used by the executable planner.
        needed.forEach((resource, amount) -> result.put(resource, amount.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue()));
        return Collections.unmodifiableMap(result);
    }
}
