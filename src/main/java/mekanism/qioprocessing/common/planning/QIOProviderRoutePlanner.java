package mekanism.qioprocessing.common.planning;

import mekanism.api.processing.MachineRecipeRoute;
import mekanism.api.processing.MachineResourceStack;
import mekanism.api.qio.resource.QIOResourceDescriptor;

import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Pure-data planner for routes observed from real machine providers.
 */
public final class QIOProviderRoutePlanner {
    private QIOProviderRoutePlanner() {
    }

    public enum Failure {
        MISSING_ROUTE,
        CYCLE,
        DEPTH_LIMIT,
        NODE_LIMIT,
        OPERATION_LIMIT,
        CANCELLED,
        OVERFLOW,
        UNRESOLVED_RESOURCE
    }

    public static final class Request {
        private final Map<QIOResourceDescriptor, Long> targets;
        private final Map<QIOResourceDescriptor, Long> inventory;
        private final int maximumDepth;
        private final int maximumNodes;
        private final long maximumOperations;
        private final BooleanSupplier cancellation;

        public Request(Map<QIOResourceDescriptor, Long> targets,
                       Map<QIOResourceDescriptor, Long> inventory, int maximumDepth,
                       int maximumNodes, long maximumOperations, BooleanSupplier cancellation) {
            if (targets == null || targets.isEmpty() || maximumDepth <= 0 || maximumNodes <= 0
                    || maximumOperations <= 0) {
                throw new IllegalArgumentException("Invalid Provider planning budget or targets");
            }
            this.targets = positiveCopy(targets, "target");
            this.inventory = positiveCopy(inventory == null ? Collections.emptyMap() : inventory, "inventory");
            this.maximumDepth = maximumDepth;
            this.maximumNodes = maximumNodes;
            this.maximumOperations = maximumOperations;
            this.cancellation = cancellation == null ? () -> false : cancellation;
        }

        public Request(Map<QIOResourceDescriptor, Long> targets,
                       Map<QIOResourceDescriptor, Long> inventory) {
            this(targets, inventory, 128, 100_000, 100_000, null);
        }

        public Map<QIOResourceDescriptor, Long> targets() {
            return targets;
        }

        public Map<QIOResourceDescriptor, Long> inventory() {
            return inventory;
        }

        public int maximumDepth() {
            return maximumDepth;
        }

        public int maximumNodes() {
            return maximumNodes;
        }

        public long maximumOperations() {
            return maximumOperations;
        }

        public boolean cancelled() {
            return cancellation.getAsBoolean();
        }

        private static Map<QIOResourceDescriptor, Long> positiveCopy(
                Map<QIOResourceDescriptor, Long> values, String name) {
            Map<QIOResourceDescriptor, Long> copy = new LinkedHashMap<>();
            values.forEach((descriptor, amount) -> {
                if (descriptor == null || amount == null || amount <= 0) {
                    throw new IllegalArgumentException("Invalid Provider " + name + " quantity");
                }
                copy.put(descriptor, amount);
            });
            return Collections.unmodifiableMap(copy);
        }
    }

    public static final class Step {
        private final QIOProviderRouteCatalog.RouteSnapshot route;
        private final long operations;
        private final Map<QIOResourceDescriptor, Long> inputs;
        private final Map<QIOResourceDescriptor, Long> outputs;
        private final List<MachineResourceStack> configurationInputs;

        private Step(QIOProviderRouteCatalog.RouteSnapshot route, long operations,
                     Map<QIOResourceDescriptor, Long> inputs,
                     Map<QIOResourceDescriptor, Long> outputs) {
            this.route = Objects.requireNonNull(route, "route");
            if (operations <= 0) throw new IllegalArgumentException("Invalid Provider step operations");
            this.operations = operations;
            this.inputs = immutable(inputs);
            this.outputs = immutable(outputs);
            this.configurationInputs = Collections.unmodifiableList(
                    new ArrayList<>(route.route().configurationInputs()));
        }

        public QIOProviderRouteCatalog.RouteSnapshot route() {
            return route;
        }

        public long operations() {
            return operations;
        }

        public Map<QIOResourceDescriptor, Long> inputs() {
            return inputs;
        }

        public Map<QIOResourceDescriptor, Long> outputs() {
            return outputs;
        }

        public List<MachineResourceStack> configurationInputs() {
            return configurationInputs;
        }

        private static Map<QIOResourceDescriptor, Long> immutable(
                Map<QIOResourceDescriptor, Long> values) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }

    public static final class Result {
        private final QIOProviderRouteCatalog.Snapshot catalog;
        private final Map<QIOResourceDescriptor, Long> targets;
        private final List<Step> steps;
        private final List<Step> attemptedSteps;
        private final Map<QIOResourceDescriptor, Long> remaining;
        private final Map<QIOResourceDescriptor, Long> initialWithdrawals;
        private final Failure failure;
        private final QIOResourceDescriptor failedResource;
        private final int visitedNodes;

        private Result(Request request, QIOProviderRouteCatalog.Snapshot catalog, State state,
                       List<Step> attempted, Failure failure, QIOResourceDescriptor failedResource,
                       int visitedNodes) {
            this.catalog = catalog;
            this.targets = request.targets();
            this.steps = Collections.unmodifiableList(new ArrayList<>(failure == null
                    ? state.steps : Collections.emptyList()));
            this.attemptedSteps = Collections.unmodifiableList(new ArrayList<>(attempted));
            this.remaining = immutable(state.stock);
            this.initialWithdrawals = failure == null ? immutable(state.initialWithdrawals)
                    : Collections.emptyMap();
            this.failure = failure;
            this.failedResource = failedResource;
            this.visitedNodes = visitedNodes;
        }

        public boolean successful() {
            return failure == null;
        }

        public QIOProviderRouteCatalog.Snapshot catalog() {
            return catalog;
        }

        public Map<QIOResourceDescriptor, Long> targets() {
            return targets;
        }

        public List<Step> steps() {
            return steps;
        }

        public List<Step> attemptedSteps() {
            return attemptedSteps;
        }

        public Map<QIOResourceDescriptor, Long> remaining() {
            return remaining;
        }

        public Map<QIOResourceDescriptor, Long> initialWithdrawals() {
            return initialWithdrawals;
        }

        public Failure failure() {
            return failure;
        }

        public QIOResourceDescriptor failedResource() {
            return failedResource;
        }

        public int visitedNodes() {
            return visitedNodes;
        }

        private static Map<QIOResourceDescriptor, Long> immutable(
                Map<QIOResourceDescriptor, Long> values) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }

    private static final class State {
        private Map<QIOResourceDescriptor, Long> stock;
        private Map<QIOResourceDescriptor, Long> initialRemaining;
        private Map<QIOResourceDescriptor, Long> initialWithdrawals = new LinkedHashMap<>();
        private List<Step> steps = new ArrayList<>();
        private long operations;

        private State(Map<QIOResourceDescriptor, Long> stock) {
            this.stock = new LinkedHashMap<>(stock);
            this.initialRemaining = new LinkedHashMap<>(stock);
        }

        private State copy() {
            State copy = new State(stock);
            copy.initialRemaining = new LinkedHashMap<>(initialRemaining);
            copy.initialWithdrawals = new LinkedHashMap<>(initialWithdrawals);
            copy.steps = new ArrayList<>(steps);
            copy.operations = operations;
            return copy;
        }

        private void accept(State other) {
            stock = other.stock;
            initialRemaining = other.initialRemaining;
            initialWithdrawals = other.initialWithdrawals;
            steps = other.steps;
            operations = other.operations;
        }
    }

    private static final class Error {
        private final Failure failure;
        private final QIOResourceDescriptor resource;
        private final List<Step> attempted;

        private Error(Failure failure, QIOResourceDescriptor resource, State state) {
            this.failure = failure;
            this.resource = resource;
            this.attempted = new ArrayList<>(state.steps);
        }
    }

    private static final class Search {
        private final Request request;
        private final QIOProviderRouteCatalog.Snapshot catalog;
        private int nodes;

        private Search(Request request, QIOProviderRouteCatalog.Snapshot catalog) {
            this.request = request;
            this.catalog = catalog;
        }

        private Error resolve(QIOResourceDescriptor resource, long amount, State state,
                              Set<QIOResourceDescriptor> active) {
            if (request.cancelled() || Thread.currentThread().isInterrupted()) {
                return new Error(Failure.CANCELLED, resource, state);
            }
            if (++nodes > request.maximumNodes()) {
                return new Error(Failure.NODE_LIMIT, resource, state);
            }
            long available = state.stock.getOrDefault(resource, 0L);
            long used = Math.min(available, amount);
            set(state.stock, resource, available - used);
            long fromInitial = Math.min(used, state.initialRemaining.getOrDefault(resource, 0L));
            if (fromInitial > 0) {
                set(state.initialRemaining, resource,
                        state.initialRemaining.get(resource) - fromInitial);
                add(state.initialWithdrawals, resource, fromInitial);
            }
            long missing = amount - used;
            if (missing == 0) return null;
            if (!active.add(resource)) return new Error(Failure.CYCLE, resource, state);
            if (active.size() > request.maximumDepth()) {
                active.remove(resource);
                return new Error(Failure.DEPTH_LIMIT, resource, state);
            }
            Error best = null;
            try {
                for (QIOProviderRouteCatalog.RouteSnapshot candidate : routesFor(resource)) {
                    if (request.cancelled() || Thread.currentThread().isInterrupted()) {
                        return new Error(Failure.CANCELLED, resource, state);
                    }
                    MachineRecipeRoute route = candidate.route();
                    long outputPerOperation = amountFor(route.guaranteedOutputs(), resource);
                    if (outputPerOperation <= 0) continue;
                    long selfInput = amountFor(route.inputs(), resource);
                    long netOutput;
                    try {
                        netOutput = Math.subtractExact(outputPerOperation, selfInput);
                    } catch (ArithmeticException overflow) {
                        best = choose(best, new Error(Failure.OVERFLOW, resource, state));
                        continue;
                    }
                    if (selfInput > 0 && netOutput <= 0) {
                        best = choose(best, new Error(Failure.CYCLE, resource, state));
                        continue;
                    }
                    long yield = selfInput > 0 ? netOutput : outputPerOperation;
                    long operations;
                    try {
                        operations = Math.addExact(missing, yield - 1) / yield;
                    } catch (ArithmeticException overflow) {
                        best = choose(best, new Error(Failure.OVERFLOW, resource, state));
                        continue;
                    }
                    if (operations <= 0 || operations > request.maximumOperations()
                            - candidateOperations(state)) {
                        best = choose(best, new Error(Failure.OPERATION_LIMIT, resource, state));
                        continue;
                    }
                    State next = state.copy();
                    if (selfInput > 0) {
                        if (available < selfInput) {
                            best = choose(best, new Error(Failure.CYCLE, resource, next));
                            continue;
                        }
                        set(next.stock, resource, available);
                    }
                    Map<QIOResourceDescriptor, Long> inputs = new LinkedHashMap<>();
                    Map<QIOResourceDescriptor, Long> outputs = new LinkedHashMap<>();
                    Error failure = null;
                    try {
                        for (MachineResourceStack input : route.inputs()) {
                            long count = Math.multiplyExact(operations, input.amount());
                            inputs.merge(input.descriptor(), count, Math::addExact);
                            if (selfInput > 0 && input.descriptor().equals(resource)) continue;
                            failure = resolve(input.descriptor(), count, next, active);
                            if (failure != null) break;
                        }
                        if (failure == null) {
                            for (MachineResourceStack output : route.guaranteedOutputs()) {
                                long count = Math.multiplyExact(operations, output.amount());
                                outputs.merge(output.descriptor(), count, Math::addExact);
                            }
                            if (selfInput > 0) {
                                long finalAmount = Math.addExact(
                                        Math.subtractExact(next.stock.getOrDefault(resource, 0L), amount),
                                        Math.multiplyExact(operations, netOutput));
                                set(next.stock, resource, finalAmount);
                            } else {
                                for (Map.Entry<QIOResourceDescriptor, Long> output : outputs.entrySet()) {
                                    long produced = output.getValue();
                                    long delta = output.getKey().equals(resource)
                                            ? Math.subtractExact(produced, missing) : produced;
                                    add(next.stock, output.getKey(), delta);
                                }
                            }
                            next.operations = Math.addExact(next.operations, operations);
                            next.steps.add(new Step(candidate, operations, inputs, outputs));
                            state.accept(next);
                            return null;
                        }
                    } catch (ArithmeticException overflow) {
                        failure = new Error(Failure.OVERFLOW, resource, next);
                    }
                    if (failure == null) failure = new Error(Failure.MISSING_ROUTE, resource, next);
                    best = choose(best, failure);
                    if (failure.failure == Failure.CANCELLED || failure.failure == Failure.NODE_LIMIT) return failure;
                }
            } finally {
                active.remove(resource);
            }
            return best == null ? new Error(Failure.MISSING_ROUTE, resource, state) : best;
        }

        private List<QIOProviderRouteCatalog.RouteSnapshot> routesFor(QIOResourceDescriptor resource) {
            List<QIOProviderRouteCatalog.RouteSnapshot> routes = new ArrayList<>();
            for (QIOProviderRouteCatalog.RouteSnapshot candidate : catalog.routes()) {
                if (amountFor(candidate.route().guaranteedOutputs(), resource) > 0) routes.add(candidate);
            }
            return routes;
        }

        private static long candidateOperations(State state) {
            return state.operations;
        }

        private static long amountFor(List<MachineResourceStack> stacks, QIOResourceDescriptor resource) {
            long amount = 0;
            for (MachineResourceStack stack : stacks) {
                if (stack.descriptor().equals(resource)) amount = Math.addExact(amount, stack.amount());
            }
            return amount;
        }

        private static Error choose(Error previous, Error next) {
            return previous == null || next.attempted.size() > previous.attempted.size() ? next : previous;
        }
    }

    public static Result plan(Request request, QIOProviderRouteCatalog.Snapshot catalog) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(catalog, "catalog");
        State initial = new State(request.inventory());
        State working = initial.copy();
        Search search = new Search(request, catalog);
        for (Map.Entry<QIOResourceDescriptor, Long> target : request.targets().entrySet()) {
            Error error = search.resolve(target.getKey(), target.getValue(), working, new LinkedHashSet<>());
            if (error != null) {
                List<Step> attempted = error.attempted.size() > working.steps.size()
                        ? error.attempted : working.steps;
                return new Result(request, catalog, initial, attempted, error.failure,
                        error.resource, search.nodes);
            }
        }
        return new Result(request, catalog, working, Collections.emptyList(), null, null, search.nodes);
    }

    private static void set(Map<QIOResourceDescriptor, Long> values,
                            QIOResourceDescriptor resource, long amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative Provider planning amount");
        if (amount == 0) values.remove(resource);
        else values.put(resource, amount);
    }

    private static void add(Map<QIOResourceDescriptor, Long> values,
                            QIOResourceDescriptor resource, long amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative Provider planning delta");
        if (amount == 0) return;
        set(values, resource, Math.addExact(values.getOrDefault(resource, 0L), amount));
    }
}

