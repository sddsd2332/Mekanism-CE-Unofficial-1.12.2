package mekanism.common.recipe.cache;

import mekanism.api.IAsyncPlanCalculator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.TreeMap;

/**
 * Pure arithmetic used by plan-based machines. It intentionally accepts only a
 * {@link RecipeRunSnapshot} and value maps, so it can safely execute on a worker.
 */
public final class RecipeExecutionPlanner {

    private static final IAsyncPlanCalculator<RecipeRunSnapshot, RecipeExecutionPlan> DETACHED_CALCULATOR =
          RecipeExecutionPlanner::calculate;

    private RecipeExecutionPlanner() {
    }

    public static IAsyncPlanCalculator<RecipeRunSnapshot, RecipeExecutionPlan> detachedCalculator() {
        return DETACHED_CALCULATOR;
    }

    public static RecipeExecutionPlan calculate(RecipeRunSnapshot snapshot) {
        return calculate(snapshot, 1, snapshot.getEnergyPerTick(), snapshot.getRequiredTicks());
    }

    /** Deterministic SplitMix64-style roll; it never advances a shared RNG. */
    public static double randomUnit(long seed, long sequence) {
        long value = seed + 0x9E3779B97F4A7C15L * (sequence + 1);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }

    public static boolean roll(long seed, long sequence, double probability) {
        if (!(probability > 0)) return false;
        if (probability >= 1) return true;
        return randomUnit(seed, sequence) < probability;
    }

    public static RecipeExecutionPlan calculate(RecipeRunSnapshot snapshot, int baselineMaxOperations,
          double energyPerOperation, int requiredTicks, Map<String, Long> inputConsumption,
          Map<String, ImmutableResourceSnapshot> outputs) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Recipe snapshot cannot be null");
        }
        if (snapshot.hasExplicitLanes()) {
            return calculateLanes(snapshot, energyPerOperation);
        }
        RecipeSemanticsSnapshot semantics = snapshot.getRecipeSemantics();
        int maxOperations = Math.max(0, baselineMaxOperations);
        Set<String> errors = new LinkedHashSet<>();
        if (!semantics.isSupported() && !snapshot.getRecipeId().endsWith(":no_recipe")) {
            errors.add("UNSUPPORTED_RECIPE_SNAPSHOT");
            maxOperations = 0;
        }
        double effectiveEnergy = Math.max(Math.max(0, energyPerOperation), semantics.getExtraEnergy());
        if (effectiveEnergy > 0) {
            int energyOperations = snapshot.getStoredEnergy() >= effectiveEnergy ?
                  (int) Math.min(Integer.MAX_VALUE, snapshot.getStoredEnergy() / effectiveEnergy) : 0;
            if (energyOperations == 0 && maxOperations > 0) errors.add("NOT_ENOUGH_ENERGY");
            maxOperations = Math.min(maxOperations, energyOperations);
        }
        Map<String, Long> allInputCosts = new LinkedHashMap<>();
        Map<String, Long> groupedRequirements = new LinkedHashMap<>();
        Map<String, ImmutableResourceSnapshot> requirementTypes = new LinkedHashMap<>();
        for (RecipeResourceFlow flow : semantics.getInputs()) {
            long amount = flow.getResource().getAmount();
            if (amount <= 0) continue;
            allInputCosts.put(flow.getKey(), amount);
            String identity = flow.getResource().identityKey();
            groupedRequirements.merge(identity, amount, RecipeExecutionPlanner::saturatedAdd);
            requirementTypes.putIfAbsent(identity, flow.getResource());
        }
        if (inputConsumption != null) {
            for (Map.Entry<String, Long> entry : inputConsumption.entrySet()) {
                long required = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());
                if (required <= 0) continue;
                ImmutableResourceSnapshot resource = snapshot.getInput(entry.getKey());
                allInputCosts.put(entry.getKey(), required);
                if (resource != null) {
                    String identity = resource.identityKey();
                    groupedRequirements.merge(identity, required, RecipeExecutionPlanner::saturatedAdd);
                    requirementTypes.putIfAbsent(identity, resource);
                } else {
                    errors.add("NOT_ENOUGH_INPUT");
                    maxOperations = 0;
                }
            }
        }
        for (Map.Entry<String, Long> entry : groupedRequirements.entrySet()) {
            long required = entry.getValue();
            long available = available(snapshot, requirementTypes.get(entry.getKey()));
            if (available < required && maxOperations > 0) errors.add("NOT_ENOUGH_INPUT");
            maxOperations = (int) Math.min(maxOperations, required <= 0 ? Integer.MAX_VALUE : available / required);
        }
        for (RecipeResourceFlow flow : semantics.getOutputs()) {
            long amount = flow.getResource().getAmount();
            if (amount <= 0) continue;
            long space = outputSpace(snapshot, flow.getResource());
            if (space < amount && maxOperations > 0) errors.add("NOT_ENOUGH_OUTPUT_SPACE");
            maxOperations = (int) Math.min(maxOperations, space / amount);
        }

        int ticks = semantics.getRequiredTicksOverride() > 0 ? semantics.getRequiredTicksOverride() :
              Math.max(1, requiredTicks);
        int nextProgress = maxOperations > 0 ? snapshot.getOperatingTicks() + 1 : 0;
        boolean complete = maxOperations > 0 && nextProgress >= ticks;
        if (complete) nextProgress = 0;

        Map<String, Long> perTickCosts = new LinkedHashMap<>();
        Map<String, Long> completionCosts = new LinkedHashMap<>();
        for (RecipeResourceFlow flow : semantics.getInputs()) {
            long amount = saturatedMultiply(flow.getResource().getAmount(), maxOperations);
            if (flow.getPhase() == RecipeResourceFlow.Phase.PER_TICK) {
                perTickCosts.put(flow.getKey(), amount);
            } else if (complete) {
                completionCosts.put(flow.getKey(), amount);
            }
        }
        Map<String, ImmutableResourceSnapshot> plannedOutputs = new LinkedHashMap<>();
        if (complete) {
            long sequence = 0;
            Map<String, Long> produced = new LinkedHashMap<>();
            for (int operation = 0; operation < maxOperations; operation++) {
                for (RecipeResourceFlow flow : semantics.getOutputs()) {
                    boolean selected = !flow.isStochastic() ||
                          roll(snapshot.getRandomSeed(), sequence++, flow.getProbability());
                    if (selected) {
                        produced.merge(flow.getKey(), flow.getResource().getAmount(), RecipeExecutionPlanner::saturatedAdd);
                    }
                }
            }
            for (RecipeResourceFlow flow : semantics.getOutputs()) {
                long amount = produced.getOrDefault(flow.getKey(), 0L);
                if (amount > 0) plannedOutputs.put(flow.getKey(), flow.getResource().withAmount(amount));
            }
        }
        if (outputs != null) plannedOutputs.putAll(outputs);
        double energy = maxOperations * effectiveEnergy;
        return RecipeExecutionPlan.builder(snapshot.getRecipeId())
              .recipeSignature(snapshot.getRecipeSignature())
              .globalRecipeGeneration(snapshot.getGlobalRecipeGeneration())
              .categoryRecipeGeneration(snapshot.getCategoryRecipeGeneration())
              .laneIndex(snapshot.getLaneIndex())
              .machineStateVersion(snapshot.getMachineStateVersion())
              .configurationVersion(snapshot.getConfigurationVersion())
              .qioLeaseVersion(snapshot.getQioLeaseVersion())
              .portOwnershipVersion(snapshot.getPortOwnershipVersion())
              .mode(snapshot.getMode())
              .operations(maxOperations)
              .energy(energy)
              .newOperatingTicks(nextProgress)
              .active(maxOperations > 0)
              .errors(errors)
              .inputConsumption(allInputCosts)
              .perTickConsumption(perTickCosts)
              .completionConsumption(completionCosts)
              .outputs(plannedOutputs)
              .randomSeed(snapshot.getRandomSeed())
              .build();
    }

    public static RecipeExecutionPlan calculate(RecipeRunSnapshot snapshot, int baselineMaxOperations,
          double energyPerOperation, int requiredTicks) {
        return calculate(snapshot, baselineMaxOperations, energyPerOperation, requiredTicks,
              Collections.emptyMap(), Collections.emptyMap());
    }

    private static RecipeExecutionPlan calculateLanes(RecipeRunSnapshot snapshot, double defaultEnergy) {
        Map<Integer, RecipeLanePlan> plans = new LinkedHashMap<>();
        Map<String, ImmutableResourceSnapshot> sharedInputs = new LinkedHashMap<>();
        Map<String, ImmutableResourceSnapshot> sharedOutputs = new LinkedHashMap<>();
        Map<String, Long> sharedCapacities = new LinkedHashMap<>();
        Map<String, Long> inputCosts = new LinkedHashMap<>();
        Map<String, Long> tickCosts = new LinkedHashMap<>();
        Map<String, Long> completionCosts = new LinkedHashMap<>();
        Map<String, ImmutableResourceSnapshot> outputs = new LinkedHashMap<>();
        Set<String> errors = new LinkedHashSet<>();
        int operations = 0;
        int progress = 0;
        boolean active = false;
        double remainingEnergy = snapshot.getStoredEnergy();
        double energy = 0;
        // Match the server's process-index order even when a caller supplied an unordered map.
        for (RecipeLaneSnapshot lane : new TreeMap<>(snapshot.getLanes()).values()) {
            for (String key : lane.getSharedInputKeys()) {
                requireSharedValue(sharedInputs, key, lane.getInputs().get(key));
            }
            for (String key : lane.getSharedOutputKeys()) {
                requireSharedValue(sharedOutputs, key, lane.getOutputContents().get(key));
                Long capacity = lane.getOutputCapacities().get(key);
                if (capacity == null || sharedCapacities.containsKey(key) &&
                      !sharedCapacities.get(key).equals(capacity)) {
                    throw new IllegalArgumentException("Inconsistent shared output capacity: " + key);
                }
                sharedCapacities.put(key, capacity);
            }
        }
        for (RecipeLaneSnapshot lane : new TreeMap<>(snapshot.getLanes()).values()) {
            Map<String, ImmutableResourceSnapshot> inputs = new LinkedHashMap<>(lane.getInputs());
            for (String key : lane.getSharedInputKeys()) inputs.put(key, sharedInputs.get(key));
            Map<String, ImmutableResourceSnapshot> contents = new LinkedHashMap<>(lane.getOutputContents());
            for (String key : lane.getSharedOutputKeys()) contents.put(key, sharedOutputs.get(key));
            RecipeLanePlan plan = calculateLane(lane, inputs, contents, remainingEnergy, defaultEnergy,
                  RecipeRandomContext.deriveLaneSeed(snapshot.getRandomSeed(), lane.getLaneIndex()));
            plans.put(lane.getLaneIndex(), plan);
            remainingEnergy = Math.max(0, remainingEnergy - plan.getEnergyAsDouble());
            energy += plan.getEnergyAsDouble();
            operations = (int) Math.min(Integer.MAX_VALUE, (long) operations + plan.getOperations());
            progress = Math.max(progress, plan.getNewOperatingTicks());
            active |= plan.isActive();
            errors.addAll(plan.getErrors());
            aggregateCosts(inputCosts, plan.getInputConsumption(), lane);
            aggregateCosts(tickCosts, plan.getPerTickConsumption(), lane);
            aggregateCosts(completionCosts, plan.getCompletionConsumption(), lane);
            for (Map.Entry<String, Long> cost : plan.getInputConsumption().entrySet()) {
                if (lane.getSharedInputKeys().contains(cost.getKey())) {
                    ImmutableResourceSnapshot stored = sharedInputs.get(cost.getKey());
                    sharedInputs.put(cost.getKey(), stored.withAmount(stored.getAmount() - cost.getValue()));
                }
            }
            for (Map.Entry<String, ImmutableResourceSnapshot> output : plan.getOutputs().entrySet()) {
                outputs.put("lane." + lane.getLaneIndex() + '.' + output.getKey(), output.getValue());
            }
            if (!placeOutputs(lane, contents, plan.getOutputs(), 1)) {
                throw new IllegalStateException("Calculated lane outputs exceed their reserved capacity");
            }
            for (String key : lane.getSharedOutputKeys()) sharedOutputs.put(key, contents.get(key));
        }
        return RecipeExecutionPlan.builder(snapshot.getRecipeId())
              .recipeSignature(snapshot.getRecipeSignature())
              .globalRecipeGeneration(snapshot.getGlobalRecipeGeneration())
              .categoryRecipeGeneration(snapshot.getCategoryRecipeGeneration())
              .laneIndex(snapshot.getLaneIndex())
              .machineStateVersion(snapshot.getMachineStateVersion())
              .configurationVersion(snapshot.getConfigurationVersion())
              .qioLeaseVersion(snapshot.getQioLeaseVersion())
              .portOwnershipVersion(snapshot.getPortOwnershipVersion()).mode(snapshot.getMode())
              .operations(operations).energy(energy).newOperatingTicks(progress).active(active)
              .inputConsumption(inputCosts).perTickConsumption(tickCosts)
              .completionConsumption(completionCosts).outputs(outputs).errors(errors)
              .randomSeed(snapshot.getRandomSeed()).lanes(plans).build();
    }

    private static void requireSharedValue(Map<String, ImmutableResourceSnapshot> values, String key,
          ImmutableResourceSnapshot value) {
        ImmutableResourceSnapshot previous = values.putIfAbsent(key, value);
        if (previous != null && !previous.equals(value)) {
            throw new IllegalArgumentException("Inconsistent shared resource snapshot: " + key);
        }
    }

    private static void aggregateCosts(Map<String, Long> total, Map<String, Long> costs,
          RecipeLaneSnapshot lane) {
        for (Map.Entry<String, Long> cost : costs.entrySet()) {
            String key = lane.getSharedInputKeys().contains(cost.getKey()) ? cost.getKey() :
                  "lane." + lane.getLaneIndex() + '.' + cost.getKey();
            total.merge(key, cost.getValue(), RecipeExecutionPlanner::saturatedAdd);
        }
    }

    private static RecipeLanePlan calculateLane(RecipeLaneSnapshot lane,
          Map<String, ImmutableResourceSnapshot> inputs, Map<String, ImmutableResourceSnapshot> contents,
          double storedEnergy, double defaultEnergy, long seed) {
        if (lane.getMaxProcessingPasses() == 1) {
            return calculateLanePass(lane, inputs, contents, storedEnergy, defaultEnergy, seed, lane.getOperatingTicks(), 0);
        }
        for (RecipeResourceFlow flow : lane.getRecipeSemantics().getInputs()) {
            if (flow.getPhase() == RecipeResourceFlow.Phase.PER_TICK) {
                throw new IllegalArgumentException("Repeated processing requires fixed completion-only inputs");
            }
        }
        Map<String, ImmutableResourceSnapshot> remainingInputs = new LinkedHashMap<>(inputs);
        Map<String, ImmutableResourceSnapshot> outputContents = new LinkedHashMap<>(contents);
        Map<String, Long> inputCosts = new LinkedHashMap<>();
        Map<String, Long> completionCosts = new LinkedHashMap<>();
        Map<String, ImmutableResourceSnapshot> outputs = new LinkedHashMap<>();
        double remainingEnergy = storedEnergy;
        double energy = 0;
        int operations = 0;
        int completedPasses = 0;
        int firstOperations = 0;
        int progress = lane.getOperatingTicks();
        long sequence = 0;
        long drawsPerOperation = lane.getRecipeSemantics().getOutputs().stream().filter(RecipeResourceFlow::isStochastic).count();
        RecipeLanePlan last = null;
        for (int pass = 0; pass < lane.getMaxProcessingPasses(); pass++) {
            last = calculateLanePass(lane, remainingInputs, outputContents, remainingEnergy, defaultEnergy, seed, progress, sequence);
            if (pass == 0) firstOperations = last.getOperations();
            progress = last.getNewOperatingTicks();
            operations = Math.addExact(operations, last.getOperations());
            energy += last.getEnergyAsDouble();
            remainingEnergy = Math.max(0, remainingEnergy - last.getEnergyAsDouble());
            completedPasses += last.getCompletedPasses();
            if (last.getCompletedPasses() > 0) sequence += drawsPerOperation * last.getOperations();
            for (Map.Entry<String, Long> cost : last.getInputConsumption().entrySet()) {
                ImmutableResourceSnapshot stored = remainingInputs.get(cost.getKey());
                remainingInputs.put(cost.getKey(), stored.withAmount(stored.getAmount() - cost.getValue()));
                inputCosts.merge(cost.getKey(), cost.getValue(), RecipeExecutionPlanner::saturatedAdd);
            }
            last.getCompletionConsumption().forEach((key, amount) -> completionCosts.merge(key, amount, RecipeExecutionPlanner::saturatedAdd));
            last.getOutputs().forEach((key, output) -> {
                ImmutableResourceSnapshot previous = outputs.get(key);
                outputs.put(key, output.withAmount(saturatedAdd(previous == null ? 0 : previous.getAmount(), output.getAmount())));
            });
            if (!placeOutputs(lane, outputContents, last.getOutputs(), 1)) {
                throw new IllegalStateException("Repeated processing output allocation failed");
            }
            if (last.getOperations() == 0) break;

            // Nothing changes except progress and energy before the next completion.
            // Batch this interval so very long recipes do not require millions of empty passes.
            int remainingPasses = lane.getMaxProcessingPasses() - pass - 1;
            int untilCompletion = Math.max(0, lane.getRequiredTicks() - progress - 1);
            int energyPasses = last.getEnergyAsDouble() > 0 ?
                  (int) Math.min(Integer.MAX_VALUE, remainingEnergy / last.getEnergyAsDouble()) : Integer.MAX_VALUE;
            int repeated = Math.min(remainingPasses, Math.min(untilCompletion, energyPasses));
            if (last.getCompletedPasses() == 0 && repeated > 0) {
                progress += repeated;
                pass += repeated;
                operations = Math.addExact(operations, Math.multiplyExact(repeated, last.getOperations()));
                double used = repeated * last.getEnergyAsDouble();
                energy += used;
                remainingEnergy = Math.max(0, remainingEnergy - used);
            }
        }
        return new RecipeLanePlan(lane.getLaneIndex(), operations, energy, progress, last.isActive(),
              inputCosts, Collections.emptyMap(), completionCosts, outputs, last.getErrors(), firstOperations, completedPasses);
    }

    private static RecipeLanePlan calculateLanePass(RecipeLaneSnapshot lane,
          Map<String, ImmutableResourceSnapshot> inputs, Map<String, ImmutableResourceSnapshot> contents,
          double storedEnergy, double defaultEnergy, long seed, int operatingTicks, long startSequence) {
        RecipeSemanticsSnapshot semantics = lane.getRecipeSemantics();
        if (!lane.isRecipePresent()) {
            return new RecipeLanePlan(lane.getLaneIndex(), 0,
                  lane.shouldKeepProgressWithoutRecipe() ? operatingTicks : 0, false,
                  Collections.emptyMap(), Collections.singleton("NO_RECIPE"));
        }
        if (lane.isRecipePresent() && (lane.isPausedForErrors() || lane.getBaselineMaxOperations() == 0)) {
            return new RecipeLanePlan(lane.getLaneIndex(), 0, operatingTicks, false,
                  Collections.emptyMap(), lane.isPausedForErrors() ? lane.getErrors() : Collections.emptySet());
        }
        Set<String> errors = new LinkedHashSet<>();
        int operations = lane.isRecipePresent() ? lane.getBaselineMaxOperations() : 0;
        if (!lane.isRecipePresent()) errors.add("NO_RECIPE");
        else if (!semantics.isSupported()) {
            errors.add("UNSUPPORTED_RECIPE_SNAPSHOT");
            operations = 0;
        }
        double perTickEnergy = lane.getEnergyPerTick() >= 0 ? lane.getEnergyPerTick() :
              Math.max(defaultEnergy, semantics.getExtraEnergy());
        int energyOperations = perTickEnergy > 0 ?
              (int) Math.min(Integer.MAX_VALUE, storedEnergy / perTickEnergy) : Integer.MAX_VALUE;
        if (operations > 0 && energyOperations == 0) errors.add("NOT_ENOUGH_ENERGY");

        Map<String, Long> requirements = new LinkedHashMap<>();
        Map<String, Long> perTick = new LinkedHashMap<>();
        Map<String, Long> completion = new LinkedHashMap<>();
        Map<String, String> inputErrors = new LinkedHashMap<>();
        boolean resetProgress = false;
        boolean mismatchedRecipe = false;
        for (RecipeResourceFlow flow : semantics.getInputs()) {
            String key = flow.getKey();
            inputErrors.put(key, inputErrors.isEmpty() ? "NOT_ENOUGH_INPUT" : "NOT_ENOUGH_SECONDARY_INPUT");
            ImmutableResourceSnapshot stored = inputs.get(key);
            boolean typeMismatch = stored == null || stored.isEmpty() || !stored.matchesType(flow.getResource());
            boolean insufficientQuantity = !typeMismatch && !lane.getTemplateInputKeys().contains(key) &&
                  stored.getAmount() < flow.getResource().getAmount();
            if (typeMismatch || insufficientQuantity) {
                operations = 0;
                resetProgress |= flow.getPhase() != RecipeResourceFlow.Phase.PER_TICK;
                mismatchedRecipe |= typeMismatch && flow.getPhase() != RecipeResourceFlow.Phase.PER_TICK;
                if (insufficientQuantity) errors.add(inputErrors.get(key));
                continue;
            }
            if (lane.getTemplateInputKeys().contains(key)) continue;
            long cost = flow.getResource().getAmount();
            boolean constant = flow.getPhase() == RecipeResourceFlow.Phase.PER_TICK;
            if (constant) cost = saturatedMultiply(cost, lane.getPerTickInputMultiplier(key));
            requirements.merge(key, cost, RecipeExecutionPlanner::saturatedAdd);
            (constant ? perTick : completion).merge(key, cost, RecipeExecutionPlanner::saturatedAdd);
        }
        for (Map.Entry<String, Long> requirement : requirements.entrySet()) {
            long cost = requirement.getValue();
            long available = inputs.get(requirement.getKey()).getAmount();
            if (available < cost && completion.containsKey(requirement.getKey())) resetProgress = true;
            if (available < cost) errors.add(inputErrors.get(requirement.getKey()));
            if (cost > 0) operations = (int) Math.min(operations, available / cost);
        }

        Map<String, ImmutableResourceSnapshot> worstCase = new LinkedHashMap<>();
        for (RecipeResourceFlow flow : semantics.getOutputs()) {
            if (flow.getProbability() > 0) worstCase.put(flow.getKey(), flow.getResource());
        }
        int low = 0;
        int high = operations;
        while (low < high) {
            int candidate = low + (int) (((long) high - low + 1) / 2);
            if (placeOutputs(lane, new LinkedHashMap<>(contents), worstCase, candidate)) low = candidate;
            else high = candidate - 1;
        }
        if (operations > 0 && low == 0) errors.add("NOT_ENOUGH_OUTPUT_SPACE");
        operations = low;
        if (operations > energyOperations && energyOperations > 0) {
            errors.add("NOT_ENOUGH_ENERGY_REDUCED_RATE");
        }
        operations = Math.min(operations, energyOperations);
        if (mismatchedRecipe) errors.clear();
        int progress = (lane.isRecipePresent() || lane.shouldKeepProgressWithoutRecipe()) && !resetProgress ? operatingTicks : 0;
        boolean complete = operations > 0 && (long) progress + 1 >= lane.getRequiredTicks();
        if (operations > 0) progress = complete ? 0 : progress + 1;
        Map<String, Long> tickCosts = multiplyCosts(perTick, operations);
        Map<String, Long> completionCosts = multiplyCosts(completion, complete ? operations : 0);
        Map<String, Long> allCosts = new LinkedHashMap<>(tickCosts);
        completionCosts.forEach((key, amount) -> allCosts.merge(key, amount, RecipeExecutionPlanner::saturatedAdd));
        Map<String, ImmutableResourceSnapshot> outputs = new LinkedHashMap<>();
        if (complete) {
            long sequence = startSequence;
            // OutputHelper samples every chance entry for one operation before starting the next.
            for (int operation = 0; operation < operations; operation++) {
                for (RecipeResourceFlow flow : semantics.getOutputs()) {
                    if (!flow.isStochastic() || roll(seed, sequence++, flow.getProbability())) {
                        ImmutableResourceSnapshot previous = outputs.get(flow.getKey());
                        long amount = saturatedAdd(previous == null ? 0 : previous.getAmount(), flow.getResource().getAmount());
                        outputs.put(flow.getKey(), flow.getResource().withAmount(amount));
                    }
                }
            }
        }
        return new RecipeLanePlan(lane.getLaneIndex(), operations, operations * perTickEnergy, progress,
              operations > 0, allCosts, tickCosts, completionCosts, outputs, errors);
    }

    private static Map<String, Long> multiplyCosts(Map<String, Long> costs, int operations) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (operations > 0) costs.forEach((key, amount) -> {
            if (amount > 0) result.put(key, saturatedMultiply(amount, operations));
        });
        return result;
    }

    private static boolean placeOutputs(RecipeLaneSnapshot lane,
          Map<String, ImmutableResourceSnapshot> contents,
          Map<String, ImmutableResourceSnapshot> outputs, int operations) {
        if (lane.hasInterchangeableOutputs() && outputs.size() == 2) {
            Map<String, ImmutableResourceSnapshot> result = new LinkedHashMap<>(contents);
            if (!placeOutputsInOrder(lane, result, outputs, operations)) {
                result = new LinkedHashMap<>(contents);
                java.util.Iterator<Map.Entry<String, ImmutableResourceSnapshot>> iterator = outputs.entrySet().iterator();
                Map.Entry<String, ImmutableResourceSnapshot> first = iterator.next();
                Map.Entry<String, ImmutableResourceSnapshot> second = iterator.next();
                Map<String, ImmutableResourceSnapshot> swapped = new LinkedHashMap<>();
                swapped.put(first.getKey(), second.getValue());
                swapped.put(second.getKey(), first.getValue());
                if (!placeOutputsInOrder(lane, result, swapped, operations)) return false;
            }
            contents.clear();
            contents.putAll(result);
            return true;
        }
        return placeOutputsInOrder(lane, contents, outputs, operations);
    }

    private static boolean placeOutputsInOrder(RecipeLaneSnapshot lane,
          Map<String, ImmutableResourceSnapshot> contents,
          Map<String, ImmutableResourceSnapshot> outputs, int operations) {
        for (Map.Entry<String, ImmutableResourceSnapshot> entry : outputs.entrySet()) {
            ImmutableResourceSnapshot output = entry.getValue();
            long remaining = saturatedMultiply(output.getAmount(), operations);
            if (!lane.hasPooledOutputs()) {
                remaining -= placeOutput(lane, contents, entry.getKey(), output, remaining);
            } else {
                // Match the farm handler: merge into existing stacks before occupying empty slots.
                for (boolean empty : new boolean[]{false, true}) {
                    for (String key : lane.getOutputCapacities().keySet()) {
                        ImmutableResourceSnapshot stored = contents.get(key);
                        if (stored != null && stored.isEmpty() == empty) {
                            remaining -= placeOutput(lane, contents, key, output, remaining);
                        }
                        if (remaining == 0) break;
                    }
                    if (remaining == 0) break;
                }
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    /** Replays the planned placement against values re-read on the server thread. */
    public static Map<String, ImmutableResourceSnapshot> outputContentsAfter(RecipeLaneSnapshot lane,
          Map<String, ImmutableResourceSnapshot> currentContents, RecipeLanePlan plan) {
        Map<String, ImmutableResourceSnapshot> result = new LinkedHashMap<>(currentContents);
        if (!placeOutputs(lane, result, plan.getOutputs(), 1)) {
            throw new IllegalArgumentException("Plan outputs no longer fit the lane");
        }
        return result;
    }

    private static long placeOutput(RecipeLaneSnapshot lane, Map<String, ImmutableResourceSnapshot> contents,
          String key, ImmutableResourceSnapshot output, long amount) {
        ImmutableResourceSnapshot stored = contents.get(key);
        if (amount <= 0 || stored == null || !stored.isEmpty() && !stored.matchesType(output)) return 0;
        long accepted = Math.min(amount, Math.max(0, lane.getOutputCapacity(key, output) - stored.getAmount()));
        if (accepted > 0) contents.put(key, output.withAmount(saturatedAdd(stored.getAmount(), accepted)));
        return accepted;
    }

    private static long available(RecipeRunSnapshot snapshot, ImmutableResourceSnapshot required) {
        if (required == null) return 0;
        long total = 0;
        for (ImmutableResourceSnapshot stored : snapshot.getInputs().values()) {
            if (stored != null && stored.matchesType(required)) {
                total = saturatedAdd(total, stored.getAmount());
            }
        }
        return total;
    }

    private static long outputSpace(RecipeRunSnapshot snapshot, ImmutableResourceSnapshot output) {
        String prefix;
        if (output.getKind() == ImmutableResourceSnapshot.Kind.ITEM) prefix = "item";
        else if (output.getKind() == ImmutableResourceSnapshot.Kind.FLUID) prefix = "fluid";
        else if (output.getKind() == ImmutableResourceSnapshot.Kind.GAS) prefix = "gas";
        else if (output.getKind() == ImmutableResourceSnapshot.Kind.OTHER &&
                 output.identityKey().contains("joules")) prefix = "energy";
        else return Long.MAX_VALUE;
        long total = 0;
        String capacityPrefix = prefix + ".capacity.";
        for (Map.Entry<String, ImmutableResourceSnapshot> entry : snapshot.getOutputs().entrySet()) {
            if (!entry.getKey().startsWith(capacityPrefix)) continue;
            String index = entry.getKey().substring(capacityPrefix.length());
            long capacity = entry.getValue().getAmount();
            ImmutableResourceSnapshot stored = snapshot.getInput(prefix + '.' + index);
            if (stored == null || stored.isEmpty()) {
                total = saturatedAdd(total, capacity);
            } else if (stored.matchesType(output)) {
                total = saturatedAdd(total, Math.max(0, capacity - stored.getAmount()));
            }
        }
        return total;
    }

    private static long saturatedAdd(long first, long second) {
        if (first >= Long.MAX_VALUE - Math.max(0, second)) return Long.MAX_VALUE;
        return first + Math.max(0, second);
    }

    private static long saturatedMultiply(long value, long multiplier) {
        if (value <= 0 || multiplier <= 0) return 0;
        if (value > Long.MAX_VALUE / multiplier) return Long.MAX_VALUE;
        return value * multiplier;
    }
}
