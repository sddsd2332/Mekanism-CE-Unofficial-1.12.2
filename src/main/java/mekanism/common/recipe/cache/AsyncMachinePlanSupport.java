package mekanism.common.recipe.cache;

import mekanism.api.energy.IEnergyContainer;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.base.IActiveState;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.RecipeSnapshotCompiler;
import mekanism.common.recipe.machines.MachineRecipe;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.tile.prefab.TileEntityElectricBlock;
import mekanism.common.tile.prefab.TileEntityOperationalMachine;
import mekanism.common.tile.prefab.TileEntityMachine;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Set;
import java.util.LinkedHashSet;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;

/** Shared main-thread capture and pure calculation helpers for specialized machines. */
public final class AsyncMachinePlanSupport {

    private static final Map<TileEntityBasicBlock, CompiledSource> COMPILED_SOURCES = new WeakHashMap<>();

    private AsyncMachinePlanSupport() {
    }

    public static Set<String> captureErrors(@Nullable CachedRecipe<?> recipe) {
        if (recipe == null) return Collections.emptySet();
        Set<String> errors = new LinkedHashSet<>();
        for (RecipeError error : recipe.getErrors()) {
            if (error == RecipeError.NOT_ENOUGH_INPUT) errors.add("NOT_ENOUGH_INPUT");
            else if (error == RecipeError.NOT_ENOUGH_SECONDARY_INPUT) errors.add("NOT_ENOUGH_SECONDARY_INPUT");
            else if (error == RecipeError.NOT_ENOUGH_LEFT_INPUT) errors.add("NOT_ENOUGH_LEFT_INPUT");
            else if (error == RecipeError.NOT_ENOUGH_RIGHT_INPUT) errors.add("NOT_ENOUGH_RIGHT_INPUT");
            else if (error == RecipeError.NOT_ENOUGH_ENERGY) errors.add("NOT_ENOUGH_ENERGY");
            else if (error == RecipeError.NOT_ENOUGH_ENERGY_REDUCED_RATE) errors.add("NOT_ENOUGH_ENERGY_REDUCED_RATE");
            else if (error == RecipeError.NOT_ENOUGH_OUTPUT_SPACE) errors.add("NOT_ENOUGH_OUTPUT_SPACE");
            else if (error == RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT) errors.add("INPUT_DOESNT_PRODUCE_OUTPUT");
            else errors.add("CUSTOM_RECIPE_ERROR");
        }
        return errors;
    }

    public static Set<RecipeError> resolveErrors(Set<String> names) {
        Set<RecipeError> errors = new LinkedHashSet<>();
        for (String name : names) {
            switch (name) {
                case "NOT_ENOUGH_ENERGY": errors.add(RecipeError.NOT_ENOUGH_ENERGY); break;
                case "NOT_ENOUGH_ENERGY_REDUCED_RATE": errors.add(RecipeError.NOT_ENOUGH_ENERGY_REDUCED_RATE); break;
                case "NOT_ENOUGH_SECONDARY_INPUT": errors.add(RecipeError.NOT_ENOUGH_SECONDARY_INPUT); break;
                case "NOT_ENOUGH_LEFT_INPUT": errors.add(RecipeError.NOT_ENOUGH_LEFT_INPUT); break;
                case "NOT_ENOUGH_RIGHT_INPUT": errors.add(RecipeError.NOT_ENOUGH_RIGHT_INPUT); break;
                case "NOT_ENOUGH_OUTPUT_SPACE": errors.add(RecipeError.NOT_ENOUGH_OUTPUT_SPACE); break;
                case "INPUT_DOESNT_PRODUCE_OUTPUT": errors.add(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT); break;
                default: errors.add(RecipeError.NOT_ENOUGH_INPUT); break;
            }
        }
        return errors;
    }

    public static RecipeRunSnapshot capture(TileEntityBasicBlock tile, @Nullable Object recipeSource,
          long categoryGeneration) {
        return capture(tile, recipeSource, categoryGeneration, tile.getProcessingStateVersion(), 0, 0, "");
    }

    public static RecipeRunSnapshot capture(TileEntityBasicBlock tile, @Nullable Object recipeSource,
          long categoryGeneration, long configurationVersion, long qioLeaseVersion,
          long portOwnershipVersion, String mode) {
        return capture(tile, recipeSource, categoryGeneration, configurationVersion, qioLeaseVersion,
              portOwnershipVersion, mode, Collections.emptyMap());
    }

    public static RecipeRunSnapshot capture(TileEntityBasicBlock tile, @Nullable Object recipeSource,
          long categoryGeneration, long configurationVersion, long qioLeaseVersion,
          long portOwnershipVersion, String mode, Map<Integer, RecipeLaneSnapshot> laneSnapshots) {
        String recipeId = recipeId(tile, recipeSource);
        long generation = RecipeHandler.getGlobalRecipeGeneration();
        CompiledSource compiled = compileSource(tile, recipeSource, generation, categoryGeneration, mode);
        String signature = compiled.signature;
        RecipeSemanticsSnapshot semantics = compiled.semantics;
        Map<String, ImmutableResourceSnapshot> inputs = new LinkedHashMap<>();
        Map<String, ImmutableResourceSnapshot> capacities = new LinkedHashMap<>();
        double storedEnergy = 0;
        double energyPerTick = 0;
        int operatingTicks = 0;
        int requiredTicks = 1;

        if (tile instanceof TileEntityContainerBlock) {
            TileEntityContainerBlock containerTile = (TileEntityContainerBlock) tile;
            List<IInventorySlot> slots = containerTile.getInventorySlots(null);
            for (int index = 0; index < slots.size(); index++) {
                IInventorySlot slot = slots.get(index);
                ItemStack stack = slot.getStack();
                inputs.put("item." + index, ImmutableResourceSnapshot.of(stack));
                capacities.put("item.capacity." + index,
                      ImmutableResourceSnapshot.descriptor("items", Math.max(0,
                            slot.getLimit(stack.isEmpty() ? ItemStack.EMPTY : stack))));
            }
            List<IExtendedFluidTank> fluidTanks = containerTile.getFluidTanks(null);
            for (int index = 0; index < fluidTanks.size(); index++) {
                IExtendedFluidTank tank = fluidTanks.get(index);
                FluidStack stack = tank.getFluid();
                inputs.put("fluid." + index, ImmutableResourceSnapshot.of(stack));
                capacities.put("fluid.capacity." + index,
                      ImmutableResourceSnapshot.descriptor("millibuckets", Math.max(0, tank.getCapacity())));
            }
            List<IExtendedGasTank> gasTanks = containerTile.getGasTanks(null);
            for (int index = 0; index < gasTanks.size(); index++) {
                IExtendedGasTank tank = gasTanks.get(index);
                GasStack stack = tank.getGas();
                inputs.put("gas." + index, ImmutableResourceSnapshot.of(stack));
                capacities.put("gas.capacity." + index,
                      ImmutableResourceSnapshot.descriptor("gas", Math.max(0, tank.getCapacity())));
            }
            List<IEnergyContainer> energyContainers = containerTile.getEnergyContainers(null);
            for (int index = 0; index < energyContainers.size(); index++) {
                IEnergyContainer energy = energyContainers.get(index);
                inputs.put("energy." + index,
                      ImmutableResourceSnapshot.descriptor("joules", nonNegativeLong(energy.getEnergy())));
                capacities.put("energy.capacity." + index,
                      ImmutableResourceSnapshot.descriptor("joules", nonNegativeLong(energy.getMaxEnergy())));
            }
        }
        if (tile instanceof TileEntityElectricBlock) {
            TileEntityElectricBlock electric = (TileEntityElectricBlock) tile;
            storedEnergy = electric.getEnergy();
            energyPerTick = electric instanceof TileEntityMachine ?
                  ((TileEntityMachine) electric).energyPerTick : 0;
        }
        if (tile instanceof TileEntityOperationalMachine) {
            TileEntityOperationalMachine operational = (TileEntityOperationalMachine) tile;
            operatingTicks = Math.max(0, operational.operatingTicks);
            requiredTicks = Math.max(1, operational.ticksRequired);
        }
        Map<String, Integer> upgrades = new LinkedHashMap<>();
        if (tile instanceof IUpgradeTile) {
            ((IUpgradeTile) tile).getInstalledUpgrades().forEach((upgrade, amount) ->
                  upgrades.put(upgrade.getRegistryNameString(), amount));
        }
        long stateVersion = tile.getProcessingStateVersion();
        long worldTime = tile.getWorld() == null ? 0 : tile.getWorld().getTotalWorldTime();
        int dimension = tile.getWorld() == null ? 0 : tile.getWorld().provider.getDimension();
        Map<Integer, RecipeLaneSnapshot> lanes = enrichLanes(laneSnapshots, compiled, operatingTicks,
              requiredTicks, tile instanceof IActiveState && ((IActiveState) tile).getActive(),
              inputs, capacities);
        return RecipeRunSnapshot.builder(recipeId)
              .recipeSignature(signature)
              .globalRecipeGeneration(generation)
              .categoryRecipeGeneration(Math.max(0, categoryGeneration))
              .machineStateVersion(stateVersion)
              .configurationVersion(Math.max(0, configurationVersion))
              .qioLeaseVersion(Math.max(0, qioLeaseVersion))
              .portOwnershipVersion(Math.max(0, portOwnershipVersion))
              .mode(mode)
              .randomSeed(seed(tile, worldTime, stateVersion))
              .operatingTicks(operatingTicks)
              .requiredTicks(requiredTicks)
              .energy(storedEnergy, energyPerTick)
              .redstonePowered(tile.redstone)
              .active(tile instanceof IActiveState && ((IActiveState) tile).getActive())
              .dimension(dimension)
              .worldTime(worldTime)
              .inputs(inputs)
              .outputs(capacities)
              .upgrades(upgrades)
              .recipeSemantics(semantics)
              .lanes(lanes)
              .build();
    }

    private static Map<Integer, RecipeLaneSnapshot> enrichLanes(
          Map<Integer, RecipeLaneSnapshot> supplied, CompiledSource compiled,
          int operatingTicks, int requiredTicks, boolean active,
          Map<String, ImmutableResourceSnapshot> inputs,
          Map<String, ImmutableResourceSnapshot> capacities) {
        Map<Integer, RecipeLaneSnapshot> base = new LinkedHashMap<>();
        if (supplied != null) base.putAll(supplied);
        if (base.isEmpty() && compiled.laneSemantics.size() > 1) {
            for (int lane = 0; lane < compiled.laneSemantics.size(); lane++) {
                base.put(lane, RecipeLaneSnapshot.builder(lane)
                      .operatingTicks(operatingTicks)
                      .requiredTicks(requiredTicks)
                      .active(active)
                      .inputs(inputs)
                      .build());
            }
        }
        if (base.isEmpty()) return Collections.emptyMap();
        Map<Integer, RecipeLaneSnapshot> enriched = new LinkedHashMap<>();
        for (Map.Entry<Integer, RecipeLaneSnapshot> entry : base.entrySet()) {
            int lane = entry.getKey();
            RecipeSemanticsSnapshot laneSemantics = lane < compiled.laneSemantics.size() ?
                  compiled.laneSemantics.get(lane) : RecipeSemanticsSnapshot.empty();
            boolean recipePresent = lane < compiled.laneRecipePresent.size() &&
                  compiled.laneRecipePresent.get(lane);
            enriched.put(lane, entry.getValue().withRecipeSemantics(recipePresent, laneSemantics));
        }
        return enriched;
    }

    public static RecipeExecutionPlan calculate(RecipeRunSnapshot snapshot, int baselineOperations) {
        return RecipeExecutionPlanner.calculate(snapshot,
              snapshot.getRecipeId().endsWith(":no_recipe") ? 0 : Math.max(0, baselineOperations),
              snapshot.getEnergyPerTick(), snapshot.getRequiredTicks(), Collections.emptyMap(),
              Collections.emptyMap());
    }

    public static boolean isStillValid(TileEntityBasicBlock tile, RecipeRunSnapshot snapshot,
          RecipeExecutionPlan plan, @Nullable Object currentRecipeSource, long categoryGeneration) {
        return plan != null && plan.isValidFor(snapshot) &&
              tile.getProcessingStateVersion() == snapshot.getMachineStateVersion() &&
              RecipeHandler.getGlobalRecipeGeneration() == snapshot.getGlobalRecipeGeneration() &&
              categoryGeneration == snapshot.getCategoryRecipeGeneration() &&
              snapshot.getConfigurationVersion() == tile.getProcessingStateVersion() &&
              recipeId(tile, currentRecipeSource).equals(snapshot.getRecipeId()) &&
              recipeSignature(currentRecipeSource).equals(snapshot.getRecipeSignature());
    }

    public static String recipeId(TileEntityBasicBlock tile, @Nullable Object recipeSource) {
        return tile.getClass().getName() + ':' + (recipeSource == null ? "no_recipe" : "recipe");
    }

    public static String recipeSignature(@Nullable Object recipeSource) {
        if (recipeSource == null) {
            return "missing";
        }
        return RecipeSnapshotCompiler.semanticSignature(copyRecipeSource(recipeSource));
    }

    public static synchronized void invalidateCompiledSource(TileEntityBasicBlock tile) {
        if (tile != null) COMPILED_SOURCES.remove(tile);
    }

    private static synchronized CompiledSource compileSource(TileEntityBasicBlock tile,
          @Nullable Object source, long globalGeneration, long categoryGeneration, String mode) {
        CompiledSource existing = COMPILED_SOURCES.get(tile);
        String checkedMode = mode == null ? "" : mode;
        List<Object> identities = sourceIdentities(source);
        if (existing != null && existing.globalGeneration == globalGeneration &&
            existing.categoryGeneration == categoryGeneration && existing.mode.equals(checkedMode) &&
            sameIdentities(existing.identities, identities)) {
            return existing;
        }
        CompiledSource compiled = new CompiledSource(globalGeneration, categoryGeneration, checkedMode,
              identities, recipeSignature(source), RecipeSemanticsCompiler.compile(source, checkedMode),
              compileLaneSemantics(identities, checkedMode));
        COMPILED_SOURCES.put(tile, compiled);
        return compiled;
    }

    private static List<Object> sourceIdentities(@Nullable Object source) {
        if (source == null) return Collections.emptyList();
        List<Object> identities = new ArrayList<>();
        if (source instanceof Iterable<?>) {
            for (Object value : (Iterable<?>) source) identities.add(value);
        } else if (source instanceof Object[]) {
            Collections.addAll(identities, (Object[]) source);
        } else {
            identities.add(source);
        }
        return identities;
    }

    private static boolean sameIdentities(List<Object> first, List<Object> second) {
        if (first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++) {
            if (first.get(index) != second.get(index)) return false;
        }
        return true;
    }

    private static List<RecipeSemanticsSnapshot> compileLaneSemantics(List<Object> values, String mode) {
        if (values.isEmpty()) return Collections.emptyList();
        List<RecipeSemanticsSnapshot> semantics = new ArrayList<>(values.size());
        for (Object value : values) semantics.add(RecipeSemanticsCompiler.compile(value, mode));
        return Collections.unmodifiableList(semantics);
    }

    private static Object copyRecipeSource(Object source) {
        if (source instanceof MachineRecipe<?, ?, ?>) {
            return ((MachineRecipe<?, ?, ?>) source).copy();
        }
        if (source instanceof Iterable<?>) {
            List<Object> copy = new ArrayList<>();
            for (Object value : (Iterable<?>) source) {
                copy.add(value instanceof MachineRecipe<?, ?, ?> ?
                      ((MachineRecipe<?, ?, ?>) value).copy() : value);
            }
            return copy;
        }
        if (source instanceof Object[]) {
            List<Object> copy = new ArrayList<>();
            for (Object value : (Object[]) source) {
                copy.add(value instanceof MachineRecipe<?, ?, ?> ?
                      ((MachineRecipe<?, ?, ?>) value).copy() : value);
            }
            return copy;
        }
        return source;
    }

    private static long seed(TileEntityBasicBlock tile, long worldTime, long stateVersion) {
        long value = worldTime * 0x9E3779B97F4A7C15L ^ stateVersion ^ tile.getPos().toLong();
        value ^= (long) tile.getClass().getName().hashCode() << 32;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static long nonNegativeLong(double value) {
        if (!Double.isFinite(value) || value <= 0) return 0;
        return value >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) value;
    }

    private static final class CompiledSource {
        private final long globalGeneration;
        private final long categoryGeneration;
        private final String mode;
        private final List<Object> identities;
        private final String signature;
        private final RecipeSemanticsSnapshot semantics;
        private final List<RecipeSemanticsSnapshot> laneSemantics;
        private final List<Boolean> laneRecipePresent;

        private CompiledSource(long globalGeneration, long categoryGeneration, String mode,
              List<Object> identities, String signature, RecipeSemanticsSnapshot semantics,
              List<RecipeSemanticsSnapshot> laneSemantics) {
            this.globalGeneration = globalGeneration;
            this.categoryGeneration = categoryGeneration;
            this.mode = mode;
            this.identities = new ArrayList<>(identities);
            this.signature = signature;
            this.semantics = semantics;
            this.laneSemantics = laneSemantics;
            List<Boolean> present = new ArrayList<>(identities.size());
            for (Object identity : identities) present.add(identity != null);
            this.laneRecipePresent = Collections.unmodifiableList(present);
        }
    }
}
