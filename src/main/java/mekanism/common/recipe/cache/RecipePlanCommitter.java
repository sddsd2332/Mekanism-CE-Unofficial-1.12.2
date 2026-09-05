package mekanism.common.recipe.cache;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContainerTransaction;
import mekanism.api.energy.IEnergyContainer;
import mekanism.common.concurrent.TaskExecutor;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.tile.prefab.TileEntityElectricBlock;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;

/** Server-thread application of a worker plan, with one resource simulation and transaction for all lanes. */
public final class RecipePlanCommitter {

    private RecipePlanCommitter() {
    }

    public static boolean commit(IContainerTransaction owner, RecipeRunSnapshot snapshot, RecipeExecutionPlan plan,
          Map<Integer, RecipeLaneCommitTarget> targets, @Nullable IEnergyContainer energy,
          BooleanSupplier stillValid) {
        if (TaskExecutor.isWorkerThread()) throw new IllegalStateException("Recipe commits must run on the server thread");
        if (!stillValid.getAsBoolean() || !plan.isValidFor(snapshot) ||
              !targets.keySet().equals(snapshot.getLanes().keySet()) || !targets.keySet().equals(plan.getLanes().keySet())) return false;
        RecipeExecutionPlan verified = RecipeExecutionPlanner.calculate(snapshot);
        if (!verified.getLanes().equals(plan.getLanes()) ||
              verified.getOperations() != plan.getOperations() ||
              verified.getRandomSeed() != plan.getRandomSeed() ||
              Double.compare(verified.getEnergyAsDouble(), plan.getEnergyAsDouble()) != 0 ||
              !verified.getInputConsumption().equals(plan.getInputConsumption()) ||
              !verified.getPerTickConsumption().equals(plan.getPerTickConsumption()) ||
              !verified.getCompletionConsumption().equals(plan.getCompletionConsumption()) ||
              !verified.getErrors().equals(plan.getErrors()) ||
              !verified.getOutputs().equals(plan.getOutputs())) return false;
        Map<Object, Change> byContainer = new IdentityHashMap<>();
        List<Change> changes = new ArrayList<>();
        for (Map.Entry<Integer, RecipeLaneCommitTarget> entry : targets.entrySet()) {
            RecipeLaneSnapshot lane = snapshot.getLane(entry.getKey());
            RecipeLaneCommitTarget target = entry.getValue();
            if (!register(target.getInputs(), lane.getInputs(), byContainer, changes) ||
                  !register(target.getOutputs(), lane.getOutputContents(), byContainer, changes) ||
                  !target.simulateState(lane, plan.getLane(entry.getKey()))) return false;
        }
        double totalEnergy = 0;
        for (Map.Entry<Integer, RecipeLaneCommitTarget> entry : new TreeMap<>(targets).entrySet()) {
            int index = entry.getKey();
            RecipeLaneCommitTarget target = entry.getValue();
            RecipeLaneSnapshot lane = snapshot.getLane(index);
            RecipeLanePlan lanePlan = plan.getLane(index);
            totalEnergy += lanePlan.getEnergyAsDouble();
            for (Map.Entry<String, Long> cost : lanePlan.getInputConsumption().entrySet()) {
                RecipeLaneCommitTarget.Resource input = target.getInputs().get(cost.getKey());
                if (input == null) return false;
                Change change = byContainer.get(input.identity());
                if (cost.getValue() > change.after.getAmount()) return false;
                change.after = change.after.withAmount(change.after.getAmount() - cost.getValue());
            }
            Map<String, ImmutableResourceSnapshot> current = new LinkedHashMap<>();
            target.getOutputs().forEach((key, value) -> current.put(key, byContainer.get(value.identity()).after));
            Map<String, ImmutableResourceSnapshot> after;
            try {
                after = RecipeExecutionPlanner.outputContentsAfter(lane, current, lanePlan);
            } catch (IllegalArgumentException invalid) {
                return false;
            }
            after.forEach((key, value) -> byContainer.get(target.getOutputs().get(key).identity()).after = value);
        }
        if (Double.compare(totalEnergy, plan.getEnergyAsDouble()) != 0 || totalEnergy > 0 && energy == null) return false;
        List<AtomicPlanCommitter.Operation> operations = new ArrayList<>();
        List<Runnable> notifications = new ArrayList<>();
        for (Change change : changes) {
            operations.add(new AtomicPlanCommitter.Operation(
                  () -> change.resource.simulate(change.before, change.after),
                  () -> {
                      if (!change.before.equals(change.after)) change.resource.write(change.after);
                      return change.resource.read().equals(change.after);
                  }, () -> change.resource.write(change.before)));
            if (!change.before.equals(change.after)) notifications.add(change.resource::notifyChanged);
        }
        if (energy != null) {
            double before = energy.getEnergy();
            double required = totalEnergy;
            if (Double.compare(before, snapshot.getStoredEnergy()) != 0) return false;
            DoubleConsumer writeEnergy;
            Runnable notifyEnergy;
            if (energy instanceof BasicEnergyContainer) {
                writeEnergy = ((BasicEnergyContainer) energy)::setEnergyNoUpdate;
                notifyEnergy = ((BasicEnergyContainer) energy)::onContentsChanged;
            } else if (owner instanceof TileEntityElectricBlock && energy instanceof MachineEnergyContainer &&
                  ((TileEntityElectricBlock) owner).getMainEnergyContainer() == energy) {
                writeEnergy = ((TileEntityElectricBlock) owner).electricityStored::set;
                notifyEnergy = ((MachineEnergyContainer) energy)::onContentsChanged;
            } else return false;
            operations.add(new AtomicPlanCommitter.Operation(
                  () -> Double.compare(energy.extract(required, Action.SIMULATE, AutomationType.INTERNAL), required) == 0,
                  () -> {
                      writeEnergy.accept(before - required);
                      return Double.compare(energy.getEnergy(), before - required) == 0;
                  }, () -> writeEnergy.accept(before)));
            if (required > 0) notifications.add(notifyEnergy);
        }
        if (!AtomicPlanCommitter.commit(owner, stillValid, operations)) return false;
        // Stage every lane's progress and usage before activity, finish or content notifications.
        Map<Integer, Runnable> effects = new LinkedHashMap<>();
        for (Map.Entry<Integer, RecipeLaneCommitTarget> entry : new TreeMap<>(targets).entrySet()) {
            int lane = entry.getKey();
            effects.put(lane, entry.getValue().stageState(snapshot.getLane(lane), plan.getLane(lane)));
        }
        effects.forEach(RecipeRandomContext::runLane);
        notifications.forEach(Runnable::run);
        return true;
    }

    private static boolean register(Map<String, RecipeLaneCommitTarget.Resource> resources,
          Map<String, ImmutableResourceSnapshot> snapshots, Map<Object, Change> byContainer, List<Change> ordered) {
        if (!resources.keySet().equals(snapshots.keySet())) return false;
        for (Map.Entry<String, RecipeLaneCommitTarget.Resource> entry : resources.entrySet()) {
            ImmutableResourceSnapshot before = snapshots.get(entry.getKey());
            RecipeLaneCommitTarget.Resource resource = entry.getValue();
            if (!resource.read().equals(before)) return false;
            Change existing = byContainer.get(resource.identity());
            if (existing == null) {
                Change change = new Change(resource, before);
                byContainer.put(resource.identity(), change);
                ordered.add(change);
            } else if (!existing.before.equals(before)) return false;
        }
        return true;
    }

    private static final class Change {
        private final RecipeLaneCommitTarget.Resource resource;
        private final ImmutableResourceSnapshot before;
        private ImmutableResourceSnapshot after;

        private Change(RecipeLaneCommitTarget.Resource resource, ImmutableResourceSnapshot before) {
            this.resource = resource;
            this.before = before;
            this.after = before;
        }
    }
}
