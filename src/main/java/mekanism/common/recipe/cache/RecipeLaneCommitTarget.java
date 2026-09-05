package mekanism.common.recipe.cache;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.IContentsSnapshot;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.infuse.InfuseType;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.InfuseStorage;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;

/** Live bindings used exclusively during server-thread capture and atomic commit. Never sent to a worker. */
public final class RecipeLaneCommitTarget {

    private final CachedRecipe<?> cache;
    private final Map<String, Resource> inputs = new LinkedHashMap<>();
    private final Map<String, Resource> outputs = new LinkedHashMap<>();
    private boolean pooledOutputs;
    private boolean interchangeableOutputs;
    private int maxProcessingPasses = 1;
    private int noRecipeOperatingTicks;
    private boolean keepProgressWithoutRecipe;
    private final Set<String> templateInputs = new LinkedHashSet<>();

    public RecipeLaneCommitTarget(@Nullable CachedRecipe<?> cache) {
        this.cache = cache;
    }

    public RecipeLaneCommitTarget input(String key, Object container) {
        inputs.put(key, new Resource(container));
        return this;
    }

    public RecipeLaneCommitTarget templateInput(String key, Object container) {
        templateInputs.add(key);
        return input(key, container);
    }

    public RecipeLaneCommitTarget output(String key, Object container) {
        outputs.put(key, new Resource(container));
        return this;
    }

    public RecipeLaneCommitTarget pooledOutputs() {
        pooledOutputs = true;
        return this;
    }

    public RecipeLaneCommitTarget interchangeableOutputs() {
        interchangeableOutputs = true;
        return this;
    }

    /** Repeated processing for fixed recipe parameters without per-tick secondary consumption. */
    public RecipeLaneCommitTarget maxProcessingPasses(int value) {
        if (value < 1) throw new IllegalArgumentException("Processing pass limit must be positive");
        maxProcessingPasses = value;
        return this;
    }

    public RecipeLaneCommitTarget keepProgressWithoutRecipe(int operatingTicks) {
        noRecipeOperatingTicks = Math.max(0, operatingTicks);
        keepProgressWithoutRecipe = true;
        return this;
    }

    Map<String, Resource> getInputs() { return inputs; }
    Map<String, Resource> getOutputs() { return outputs; }

    void prepareForPlan() {
        if (cache != null) cache.preparePlanValues();
    }

    public RecipeLaneSnapshot captureSnapshot(int lane, String mode, boolean active) {
        RecipeSemanticsSnapshot semantics = RecipeSemanticsCompiler.compile(cache == null ? null : cache.getRecipe(), mode);
        RecipeLaneSnapshot.Builder builder = cache == null ? RecipeLaneSnapshot.builder(lane).recipePresent(false)
              .operatingTicks(noRecipeOperatingTicks).keepProgressWithoutRecipe(keepProgressWithoutRecipe) :
              cache.laneSnapshotBuilder(lane);
        builder.active(active).recipeSemantics(semantics).pooledOutputs(pooledOutputs)
              .interchangeableOutputs(interchangeableOutputs).templateInputKeys(templateInputs).maxProcessingPasses(maxProcessingPasses);
        inputs.forEach((key, value) -> builder.input(key, value.read()));
        outputs.forEach((key, value) -> {
            ImmutableResourceSnapshot expected = ImmutableResourceSnapshot.empty();
            for (RecipeResourceFlow flow : semantics.getOutputs()) {
                if (flow.getKey().equals(key)) { expected = flow.getResource(); break; }
            }
            builder.output(key, value.read(), value.capacity(expected));
            if (pooledOutputs || interchangeableOutputs) {
                for (RecipeResourceFlow flow : semantics.getOutputs()) {
                    builder.outputLimit(key, flow.getResource(), value.capacity(flow.getResource()));
                }
            }
        });
        return builder.build();
    }

    boolean simulateState(RecipeLaneSnapshot snapshot, RecipeLanePlan plan) {
        return cache == null ? !snapshot.isRecipePresent() && plan.getOperations() == 0 :
              cache.simulatePlan(snapshot, plan);
    }

    Runnable stageState(RecipeLaneSnapshot snapshot, RecipeLanePlan plan) {
        return cache == null ? () -> { } : cache.stagePlanState(snapshot, plan);
    }

    static final class Resource {
        private final Object container;
        private final InfuseType infusionType;

        private Resource(Object container) {
            this.container = Objects.requireNonNull(container, "Recipe resource container");
            this.infusionType = container instanceof InfuseStorage ? ((InfuseStorage) container).getType() : null;
            if (!(container instanceof IInventorySlot || container instanceof IExtendedGasTank ||
                  container instanceof IExtendedFluidTank || container instanceof InfuseStorage)) {
                throw new IllegalArgumentException("Unsupported recipe commit container: " + container.getClass());
            }
            if (!(container instanceof InfuseStorage || container instanceof IContentsSnapshot)) {
                throw new IllegalArgumentException("Recipe containers must support listener-free transaction restore");
            }
        }

        Object identity() { return container; }

        long capacity(ImmutableResourceSnapshot output) {
            if (container instanceof IInventorySlot) return ((IInventorySlot) container).getLimit(
                  output.getKind() == ImmutableResourceSnapshot.Kind.ITEM ? output.getItemCopy() : net.minecraft.item.ItemStack.EMPTY);
            if (container instanceof IExtendedGasTank) return ((IExtendedGasTank) container).getCapacity();
            if (container instanceof IExtendedFluidTank) return ((IExtendedFluidTank) container).getCapacity();
            return 0;
        }

        ImmutableResourceSnapshot read() {
            if (container instanceof IInventorySlot) return ImmutableResourceSnapshot.of(((IInventorySlot) container).getStack());
            if (container instanceof IExtendedGasTank) return ImmutableResourceSnapshot.of(((IExtendedGasTank) container).getGas());
            if (container instanceof IExtendedFluidTank) return ImmutableResourceSnapshot.of(((IExtendedFluidTank) container).getFluid());
            InfuseStorage storage = (InfuseStorage) container;
            return storage.getType() == null ? ImmutableResourceSnapshot.empty() :
                  ImmutableResourceSnapshot.descriptor("infuse:" + storage.getType().name, storage.getAmount());
        }

        boolean simulate(ImmutableResourceSnapshot before, ImmutableResourceSnapshot after) {
            if (!read().equals(before) || after.getAmount() > Integer.MAX_VALUE) return false;
            long delta = after.getAmount() - before.getAmount();
            if (delta == 0) return before.equals(after);
            if (!before.isEmpty() && !after.isEmpty() && !before.matchesType(after)) return false;
            if (container instanceof InfuseStorage) return delta < 0;
            if (container instanceof IInventorySlot) {
                IInventorySlot slot = (IInventorySlot) container;
                return delta < 0 ? slot.extractItem((int) -delta, Action.SIMULATE, AutomationType.INTERNAL).getCount() == -delta :
                      slot.insertItem(after.withAmount(delta).getItemCopy(), Action.SIMULATE, AutomationType.INTERNAL).isEmpty();
            }
            if (container instanceof IExtendedGasTank) {
                IExtendedGasTank tank = (IExtendedGasTank) container;
                return delta < 0 ? ImmutableResourceSnapshot.of(tank.extract((int) -delta, Action.SIMULATE, AutomationType.INTERNAL)).getAmount() == -delta :
                      ImmutableResourceSnapshot.of(tank.insert(after.withAmount(delta).getGasCopy(), Action.SIMULATE, AutomationType.INTERNAL)).isEmpty();
            }
            IExtendedFluidTank tank = (IExtendedFluidTank) container;
            return delta < 0 ? ImmutableResourceSnapshot.of(tank.extract((int) -delta, Action.SIMULATE, AutomationType.INTERNAL)).getAmount() == -delta :
                  ImmutableResourceSnapshot.of(tank.insert(after.withAmount(delta).getFluidCopy(), Action.SIMULATE, AutomationType.INTERNAL)).isEmpty();
        }

        void write(ImmutableResourceSnapshot value) {
            if (container instanceof InfuseStorage) {
                InfuseStorage storage = (InfuseStorage) container;
                if (value.isEmpty()) storage.setEmpty();
                else storage.setType(infusionType).setAmount((int) value.getAmount());
                return;
            }
            if (container instanceof IInventorySlot) {
                ((IInventorySlot) container).setStackUncheckedNoUpdate(value.isEmpty() ? net.minecraft.item.ItemStack.EMPTY : value.getItemCopy());
            } else if (container instanceof IExtendedGasTank) {
                ((IExtendedGasTank) container).setStackUncheckedNoUpdate(value.getGasCopy());
            } else {
                ((IExtendedFluidTank) container).setStackUncheckedNoUpdate(value.getFluidCopy());
            }
        }

        void notifyChanged() {
            if (container instanceof IContentsListener) ((IContentsListener) container).onContentsChanged();
        }
    }
}
