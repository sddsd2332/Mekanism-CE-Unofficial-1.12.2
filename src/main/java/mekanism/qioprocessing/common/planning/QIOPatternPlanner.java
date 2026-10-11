package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.common.pattern.VerifiedWorkbenchPattern;
import mekanism.qioprocessing.common.pattern.WorkbenchPatternIndex;
import mekanism.qioprocessing.common.recipe.RecipeItemKey;

import java.util.*;

/**
 * Bounded pure-data planning: no World, recipe execution, or QIO mutation.
 */
public final class QIOPatternPlanner {
    private QIOPatternPlanner() {
    }

    public enum Failure {MISSING_PATTERN, CYCLE, DEPTH_LIMIT, NODE_LIMIT, OPERATION_LIMIT, CANCELLED, OVERFLOW}

    public static final class Step {
        private final UUID patternId;
        private final String recipeId;
        private final RecipeItemKey output;
        private final long operations;
        private final Map<RecipeItemKey, Long> inputs, remainders;

        private Step(VerifiedWorkbenchPattern pattern, long operations, Map<RecipeItemKey, Long> inputs,
                     Map<RecipeItemKey, Long> remainders) {
            patternId = pattern.id();
            recipeId = pattern.recipeId();
            output = pattern.output();
            this.operations = operations;
            this.inputs = Collections.unmodifiableMap(inputs);
            this.remainders = Collections.unmodifiableMap(remainders);
        }

        public UUID patternId() {
            return patternId;
        }

        public String recipeId() {
            return recipeId;
        }

        public RecipeItemKey output() {
            return output;
        }

        public long operations() {
            return operations;
        }

        public Map<RecipeItemKey, Long> inputs() {
            return inputs;
        }

        public Map<RecipeItemKey, Long> remainders() {
            return remainders;
        }
    }

    public static final class Result {
        private final String generation;
        private final long revision;
        private final Map<RecipeItemKey, Long> targets;
        private final List<Step> steps, attemptedSteps;
        private final Map<RecipeItemKey, Long> remaining;
        private final Map<RecipeItemKey, Long> initialWithdrawals;
        private final Failure failure;
        private final RecipeItemKey failedItem;
        private final int visitedNodes;

        private Result(QIOPlanningRequest request, WorkbenchPatternIndex index, State state, List<Step> attempted,
                       Failure failure, RecipeItemKey failedItem, int visitedNodes) {
            generation = index.generation();
            revision = index.revision();
            targets = request.targets();
            steps = Collections.unmodifiableList(new ArrayList<>(failure == null ? state.steps : Collections.emptyList()));
            attemptedSteps = Collections.unmodifiableList(new ArrayList<>(attempted));
            remaining = Collections.unmodifiableMap(new LinkedHashMap<>(state.stock));
            initialWithdrawals = failure == null ? Collections.unmodifiableMap(new LinkedHashMap<>(state.initialWithdrawals)) : Collections.emptyMap();
            this.failure = failure;
            this.failedItem = failedItem;
            this.visitedNodes = visitedNodes;
        }

        public boolean successful() {
            return failure == null;
        }

        public String generation() {
            return generation;
        }

        public long revision() {
            return revision;
        }

        public Map<RecipeItemKey, Long> targets() {
            return targets;
        }

        public List<Step> steps() {
            return steps;
        }

        /**
         * Completed branch steps for diagnosis only; never executable on failure.
         */
        public List<Step> attemptedSteps() {
            return attemptedSteps;
        }

        public Map<RecipeItemKey, Long> remaining() {
            return remaining;
        }

        /**
         * Only successful plans expose initial QIO demand; no actual reservation has occurred.
         */
        public Map<RecipeItemKey, Long> initialWithdrawals() {
            return initialWithdrawals;
        }

        public Failure failure() {
            return failure;
        }

        public RecipeItemKey failedItem() {
            return failedItem;
        }

        public int visitedNodes() {
            return visitedNodes;
        }
    }

    private static final class State {
        private Map<RecipeItemKey, Long> stock;
        private Map<RecipeItemKey, Long> initialRemaining;
        private Map<RecipeItemKey, Long> initialWithdrawals = new LinkedHashMap<>();
        private List<Step> steps = new ArrayList<>();
        private long operations;

        private State(Map<RecipeItemKey, Long> stock) {
            this.stock = new LinkedHashMap<>(stock);
            initialRemaining = new LinkedHashMap<>(stock);
        }

        private State copy() {
            State copy = new State(stock);
            copy.initialRemaining = new LinkedHashMap<>(initialRemaining);
            copy.initialWithdrawals = new LinkedHashMap<>(initialWithdrawals);
            copy.steps.addAll(steps);
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
        private final Failure reason;
        private final RecipeItemKey item;
        private final List<Step> attempted;

        private Error(Failure reason, RecipeItemKey item, State state) {
            this.reason = reason;
            this.item = item;
            attempted = new ArrayList<>(state.steps);
        }
    }

    private static final class Search {
        private final QIOPlanningRequest request;
        private final WorkbenchPatternIndex index;
        private int nodes;

        private Search(QIOPlanningRequest request, WorkbenchPatternIndex index) {
            this.request = request;
            this.index = index;
        }

        private Error resolve(RecipeItemKey item, long amount, State state, Set<RecipeItemKey> active) {
            if (request.cancelled() || Thread.currentThread().isInterrupted())
                return new Error(Failure.CANCELLED, item, state);
            if (++nodes > request.maximumNodes()) return new Error(Failure.NODE_LIMIT, item, state);
            long available = state.stock.getOrDefault(item, 0L);
            long used = Math.min(available, amount);
            set(state.stock, item, available - used);
            long fromInitial = Math.min(used, state.initialRemaining.getOrDefault(item, 0L));
            if (fromInitial > 0) {
                set(state.initialRemaining, item, state.initialRemaining.get(item) - fromInitial);
                add(state.initialWithdrawals, item, fromInitial);
            }
            long missing = amount - used;
            if (missing == 0) return null;
            if (active.contains(item)) return new Error(Failure.CYCLE, item, state);
            if (active.size() >= request.maximumDepth()) return new Error(Failure.DEPTH_LIMIT, item, state);
            List<VerifiedWorkbenchPattern> patterns = index.patternsForOutput(item);
            if (patterns.isEmpty()) return new Error(Failure.MISSING_PATTERN, item, state);
            active.add(item);
            Error best = null;
            try {
                for (VerifiedWorkbenchPattern pattern : patterns) {
                    if (request.cancelled() || Thread.currentThread().isInterrupted())
                        return new Error(Failure.CANCELLED, item, state);
                    State candidate = state.copy();
                    int selfInput = pattern.consumedInputs().getOrDefault(item, 0);
                    long netOutput = (long) pattern.outputCount() - selfInput;
                    if (netOutput <= 0 && selfInput > 0) {
                        best = choose(best, new Error(Failure.CYCLE, item, candidate));
                        continue;
                    }
                    long yield = selfInput > 0 ? netOutput : pattern.outputCount();
                    long operations = missing / yield + (missing % yield == 0 ? 0 : 1);
                    if (operations > request.maximumOperations() - candidate.operations) {
                        best = choose(best, new Error(Failure.OPERATION_LIMIT, item, candidate));
                        continue;
                    }
                    if (selfInput > 0) {
                        if (available < selfInput) {
                            best = choose(best, new Error(Failure.CYCLE, item, candidate));
                            continue;
                        }
                        set(candidate.stock, item, available);
                    }
                    candidate.operations += operations;
                    Map<RecipeItemKey, Long> inputs = new LinkedHashMap<>(), remainders = new LinkedHashMap<>();
                    Error failure = null;
                    try {
                        for (Map.Entry<RecipeItemKey, Integer> input : pattern.consumedInputs().entrySet()) {
                            long count = Math.multiplyExact(operations, input.getValue().longValue());
                            inputs.put(input.getKey(), count);
                            if (selfInput > 0 && input.getKey().equals(item)) continue;
                            failure = resolve(input.getKey(), count, candidate, active);
                            if (failure != null) break;
                        }
                        if (failure == null) {
                            if (selfInput > 0 && candidate.stock.getOrDefault(item, 0L) < selfInput) {
                                failure = new Error(Failure.CYCLE, item, candidate);
                            }
                        }
                        if (failure == null) {
                            for (RecipeItemKey remainder : pattern.remainders()) {
                                if (remainder != null) remainders.merge(remainder, operations, Math::addExact);
                            }
                            for (Map.Entry<RecipeItemKey, Long> entry : remainders.entrySet())
                                add(candidate.stock, entry.getKey(), entry.getValue());
                            long produced = Math.multiplyExact(operations, (long) pattern.outputCount());
                            if (selfInput > 0) {
                                long net = Math.multiplyExact(operations, netOutput);
                                long finalCount = Math.addExact(candidate.stock.getOrDefault(item, 0L) - amount, net);
                                add(candidate.stock, item, finalCount - candidate.stock.getOrDefault(item, 0L));
                            } else add(candidate.stock, item, produced - missing);
                            candidate.steps.add(new Step(pattern, operations, inputs, remainders));
                            state.accept(candidate);
                            return null;
                        }
                    } catch (ArithmeticException overflow) {
                        failure = new Error(Failure.OVERFLOW, item, candidate);
                    }
                    best = choose(best, failure);
                    if (failure.reason == Failure.CANCELLED || failure.reason == Failure.NODE_LIMIT) return failure;
                }
            } finally {
                active.remove(item);
            }
            return best;
        }

        private Error choose(Error previous, Error next) {
            return previous == null || next.attempted.size() > previous.attempted.size() ? next : previous;
        }
    }

    public static Result plan(QIOPlanningRequest request, WorkbenchPatternIndex index) {
        if (request == null || index == null) throw new NullPointerException("request/index");
        State initial = new State(request.inventory()), working = initial.copy();
        Search search = new Search(request, index);
        for (Map.Entry<RecipeItemKey, Long> target : request.targets().entrySet()) {
            Error error = search.resolve(target.getKey(), target.getValue(), working, new LinkedHashSet<>());
            if (error != null) {
                List<Step> attempted = error.attempted.size() > working.steps.size() ? error.attempted : working.steps;
                return new Result(request, index, initial, attempted, error.reason, error.item, search.nodes);
            }
        }
        return new Result(request, index, working, Collections.emptyList(), null, null, search.nodes);
    }

    private static void set(Map<RecipeItemKey, Long> stock, RecipeItemKey item, long count) {
        if (count == 0) stock.remove(item);
        else stock.put(item, count);
    }

    private static void add(Map<RecipeItemKey, Long> stock, RecipeItemKey item, long count) {
        set(stock, item, Math.addExact(stock.getOrDefault(item, 0L), count));
    }
}

