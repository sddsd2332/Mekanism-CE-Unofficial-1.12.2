package mekanism.common.recipe.cache;

import mekanism.api.Action;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.gas.GasStack;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.MekanismBlocks;
import mekanism.common.Upgrade;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.machines.MachineRecipe;
import mekanism.common.tile.factory.TileEntityFactory;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.tile.prefab.TileEntityBasicMachine;
import mekanism.common.tile.prefab.TileEntityElectricBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.world.WorldServer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidStack;
import java.lang.reflect.Method;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.LinkedHashSet;

/** Smoke-only fixtures using registered machines and their real local resource bindings. */
public final class MachineStressFixtures {
    private static final Method PENDING_PLAN = pendingPlanMethod();

    private static Method pendingPlanMethod() {
        try { return TileEntityBasicBlock.class.getMethod("hasPendingAsyncPlan"); }
        catch (NoSuchMethodException baseline) { return null; }
    }

    public static boolean inspectsPendingPlans() { return PENDING_PLAN != null; }

    public static boolean hasPendingPlan(TileEntityBasicBlock tile) {
        check(PENDING_PLAN != null, "Pending-plan inspection is unavailable on the baseline core");
        try { return (Boolean) PENDING_PLAN.invoke(tile); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    public final List<Entry> entries = new ArrayList<>();
    public final Map<String, Integer> types = new LinkedHashMap<>();
    public final Map<String, Integer> upgrades = new LinkedHashMap<>();
    public final Map<String, String> conflictingUpgrades = new LinkedHashMap<>();
    public final Set<String> recipeSignatures = new LinkedHashSet<>();
    private final List<Variant> recipes = new ArrayList<>();
    private final List<Variant> storage = new ArrayList<>();
    private final List<String> supplementalCycleRecipes = new ArrayList<>();
    private final WorldServer world;
    private final String scenario;
    private final int count;
    public final int cycleTarget = Integer.getInteger("mekanism.machine.stress.recipeCycles", 0);
    public final String cyclePattern = System.getProperty("mekanism.machine.stress.recipePattern", "cached");
    private long cycleMeasurementStart;

    public MachineStressFixtures(WorldServer world, String scenario, int count) {
        this.world = world;
        this.scenario = scenario;
        this.count = count;
        check(cycleTarget >= 0 && ("cached".equals(cyclePattern) || "cycle".equals(cyclePattern)), "Invalid recipe cycle mode");
        check(cycleTarget == 0 || "recipe-different".equals(scenario), "Recipe cycle benchmark requires every machine working");
        if (cycleTarget > 0) {
            // User-approved smoke-only additions: the normal BRUSHED table has
            // one recipe. Both patterns use the same augmented table so every
            // existing machine variant can exercise three different recipes.
            addSupplementalBrushed(new ItemStack(Items.CLAY_BALL), new ItemStack(Items.BRICK));
            addSupplementalBrushed(new ItemStack(Items.SLIME_BALL), new ItemStack(Items.STRING));
        }
        // These families share explicit resource bindings. Extra integrations and
        // multiblock controllers need separate fixtures and are not claimed here.
        RecipeType[] modes = {RecipeType.SMELTING, RecipeType.ENRICHING, RecipeType.CRUSHING,
              RecipeType.STAMPING, RecipeType.ROLLING, RecipeType.BRUSHED, RecipeType.TURNING};
        MachineType[] tiers = {MachineType.BASIC_FACTORY, MachineType.ADVANCED_FACTORY,
              MachineType.ELITE_FACTORY, MachineType.ULTIMATE_FACTORY};
        for (RecipeType mode : modes) {
            TileEntityBasicMachine<?, ?, ?> prototype = (TileEntityBasicMachine<?, ?, ?>) mode.getType().tileEntitySupplier.get();
            List<MachineRecipe<?, ?, ?>> definitions = new ArrayList<>(prototype.getRecipes().values());
            definitions.sort(Comparator.comparing(MachineStressBindings::recipeKey));
            check(!definitions.isEmpty(), "No registered recipes for " + mode);
            recipes.add(new Variant(state(mode.getType()), null, definitions, "single:" + mode));
            for (MachineType tier : tiers) recipes.add(new Variant(state(tier), mode, definitions, tier + ":" + mode));
        }
        for (MachineType type : new MachineType[]{MachineType.CHEMICAL_OXIDIZER, MachineType.CHEMICAL_CRYSTALLIZER,
              MachineType.CHEMICAL_DISSOLUTION_CHAMBER, MachineType.CHEMICAL_WASHER}) {
            TileEntityBasicMachine<?, ?, ?> prototype = (TileEntityBasicMachine<?, ?, ?>) type.tileEntitySupplier.get();
            List<MachineRecipe<?, ?, ?>> definitions = new ArrayList<>(prototype.getRecipes().values());
            definitions.sort(Comparator.comparing(MachineStressBindings::recipeKey));
            check(!definitions.isEmpty(), "No registered recipes for " + type);
            recipes.add(new Variant(state(type), null, definitions, "chemical:" + type));
        }
        storage.add(new Variant(state(MachineType.FLUID_TANK), null, null, "storage:fluid_tank"));
        storage.add(new Variant(MekanismBlocks.GasTank.getDefaultState(), null, null, "storage:gas_tank"));
        storage.add(new Variant(MekanismBlocks.EnergyCube.getDefaultState(), null, null, "storage:energy_cube"));
        storage.add(new Variant(MekanismBlocks.BasicBlock.getStateFromMeta(6), null, null, "storage:bin"));
    }

    private static IBlockState state(MachineType type) {
        return type.typeBlock.getBlock().getStateFromMeta(type.meta);
    }

    private void addSupplementalBrushed(ItemStack input, ItemStack output) {
        check(RecipeHandler.getRecipe(new ItemStackInput(input), RecipeHandler.Recipe.BRUSHED) == null,
              "Supplemental BRUSHED input must not replace an existing recipe");
        RecipeHandler.addBrushedRecipe(input, output);
        supplementalCycleRecipes.add(MachineStressBindings.recipeKey(
              RecipeHandler.getRecipe(new ItemStackInput(input), RecipeHandler.Recipe.BRUSHED)));
    }

    public boolean installNextBatch(int batchSize) {
        int limit = Math.min(count, entries.size() + batchSize);
        for (int index = entries.size(); index < limit; index++) {
            boolean working = "recipe-different".equals(scenario) || "mixed".equals(scenario) && (index & 1) == 0;
            int sequence = "mixed".equals(scenario) ? index / 2 : index;
            Variant variant;
            if (working) variant = recipes.get(sequence % recipes.size());
            else {
                int selection = sequence % (recipes.size() + storage.size());
                variant = selection < recipes.size() ? recipes.get(selection) : storage.get(selection - recipes.size());
            }
            // 128 layers in a 32x32 area; all chunks remain in the spawn region.
            BlockPos pos = new BlockPos(index & 31, 64 + (index >> 10), (index >> 5) & 31);
            check(pos.getY() < 256, "Fixture layout exceeds world height");
            world.setBlockState(pos, variant.state, 2);
            TileEntityBasicBlock tile = (TileEntityBasicBlock) world.getTileEntity(pos);
            check(tile != null, "Registered block did not create a machine at " + pos);
            if (variant.mode != null) ((TileEntityFactory) tile).setRecipeType(variant.mode);
            installUpgrades(tile);
            MachineRecipe<?, ?, ?> definition = working ? variant.definitions.get((sequence / recipes.size()) % variant.definitions.size()) : null;
            Entry entry = new Entry(tile, variant, definition);
            entries.add(entry);
            types.merge(variant.name + (working ? ":working" : ":idle"), 1, Integer::sum);
            if (definition != null) recipeSignatures.add(MachineStressBindings.recipeKey(definition));
        }
        return entries.size() == count;
    }

    private void installUpgrades(TileEntityBasicBlock tile) {
        if (!(tile instanceof IUpgradeTile) || !((IUpgradeTile) tile).supportsUpgrades()) return;
        IUpgradeTile upgraded = (IUpgradeTile) tile;
        for (Upgrade upgrade : new ArrayList<>(upgraded.getSupportedUpgradeTypes())) {
            Upgrade conflicting = upgraded.getInstalledUpgrades().keySet().stream()
                  .filter(installed -> installed != upgrade && !upgrade.isCompatibleWith(installed)).findFirst().orElse(null);
            if (conflicting != null) {
                conflictingUpgrades.put(upgrade.getRegistryNameString(), conflicting.getRegistryNameString());
                continue;
            }
            int maximum = upgrade.getMaxInstalled();
            ItemStack stack = upgrade.getStack(maximum);
            int installed = upgraded.getComponent().installUpgrade(stack, Action.EXECUTE);
            check(installed == maximum && upgraded.getInstalledUpgrades(upgrade) == maximum,
                  "Cannot fully install " + upgrade.getRegistryName() + " on " + tile.getClass().getName());
            upgrades.put(upgrade.getRegistryName().toString(), maximum);
        }
    }

    /** Fixture maintenance runs outside the timed tick; machine processing uses normal World ticks. */
    public void prepareTick() {
        // A playerless dedicated world otherwise stops ticking tiles after 300
        // ticks. Keep the normal World tile phase active for the full workload.
        world.resetUpdateEntityTick();
        for (Entry entry : entries) entry.prepare();
    }

    public void beginCycleMeasurement() {
        if (cycleTarget == 0) return;
        cycleMeasurementStart = world.getTotalWorldTime();
        for (Entry entry : entries) entry.cycles.begin(cycleMeasurementStart);
    }

    public boolean cyclesComplete() {
        if (cycleTarget == 0) return false;
        for (Entry entry : entries) if (!entry.cycles.done()) return false;
        return true;
    }

    public Map<String, Object> cycleReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("pattern", cyclePattern);
        report.put("targetOperationsPerLane", cycleTarget);
        report.put("measurementStartTick", cycleMeasurementStart);
        report.put("supplementalSmokeOnlyRecipes", supplementalCycleRecipes);
        report.put("worldTickPolicy", "Playerless-world idle counter reset during fixture maintenance; normal World tile ticks, no direct machine tick calls.");
        report.put("semantics", "64 completed operations per lane. Cached=A repeated; cycle=A,B,C repeated for 64 operations (A22/B21/C21). Each lane starts after its next completed warmup operation; aggregate ticks include that boundary tail. Finished lanes continue normal World ticks with no new input.");
        Map<String, CycleSummary> types = new LinkedHashMap<>();
        for (Entry entry : entries) if (entry.cycles != null) {
            CycleSummary summary = types.computeIfAbsent(entry.variant.name, ignored -> new CycleSummary());
            for (RecipeCycleTracker.CycleLane lane : entry.cycles.lanes) summary.add(lane);
        }
        report.put("types", types);
        return report;
    }

    public void writeCycleEvidence(Path directory) throws IOException {
        if (cycleTarget == 0) return;
        try (BufferedWriter writer = Files.newBufferedWriter(directory.resolve("recipe-cycle-lanes.csv"), StandardCharsets.UTF_8)) {
            writer.write("machine,type,lane,recipeA,recipeB,recipeC,completed,A,B,C,startTick,endTick,cacheBindingReuses,cacheBindingReplacements\n");
            int machine = 0;
            for (Entry entry : entries) {
                if (entry.cycles != null) for (RecipeCycleTracker.CycleLane lane : entry.cycles.lanes) {
                    int[] recipes = entry.cycles.recipeIndexes;
                    writer.write(machine + "," + entry.variant.name + "," + lane.binding.index + "," + recipes[0] + "," + recipes[1] + "," + recipes[2] +
                          "," + lane.completed + "," + lane.byChoice[0] + "," + lane.byChoice[1] + "," + lane.byChoice[2] +
                          "," + lane.startedTick + "," + lane.finishedTick + "," + lane.cacheReuses + "," + lane.cacheReplacements + "\n");
                }
                machine++;
            }
        }
        Map<String, List<String>> catalogue = new LinkedHashMap<>();
        for (Variant variant : recipes) {
            List<String> keys = new ArrayList<>();
            for (MachineRecipe<?, ?, ?> recipe : variant.definitions) keys.add(MachineStressBindings.recipeKey(recipe));
            catalogue.put(variant.name, keys);
        }
        Files.write(directory.resolve("recipe-cycle-catalog.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create()
              .toJson(catalogue).getBytes(StandardCharsets.UTF_8));
    }

    private static final class CycleSummary {
        int lanes;
        int minCompleted = Integer.MAX_VALUE;
        int maxCompleted;
        long operations;
        long cacheBindingReuses;
        long cacheBindingReplacements;
        long minLaneTicks = Long.MAX_VALUE;
        long maxLaneTicks;
        void add(RecipeCycleTracker.CycleLane lane) {
            lanes++; operations += lane.completed;
            minCompleted = Math.min(minCompleted, lane.completed); maxCompleted = Math.max(maxCompleted, lane.completed);
            cacheBindingReuses += lane.cacheReuses; cacheBindingReplacements += lane.cacheReplacements;
            if (lane.done()) {
                long elapsed = lane.finishedTick - lane.startedTick;
                minLaneTicks = Math.min(minLaneTicks, elapsed); maxLaneTicks = Math.max(maxLaneTicks, elapsed);
            }
        }
    }

    public int verifyTick() {
        int working = 0;
        for (Entry entry : entries) {
            check(!entry.tile.isInvalid() && world.getTileEntity(entry.tile.getPos()) == entry.tile,
                  "Machine replaced or invalid at " + entry.tile.getPos());
            check(entry.tile.ticker == entry.tickBefore + 1, "Machine did not tick exactly once: " + entry.variant.name);
            if (inspectsPendingPlans()) check(!hasPendingPlan(entry.tile), "Plan did not finish in the same tick: " + entry.variant.name);
            if (entry.definition != null) {
                check(!entry.wasProcessing || ((TileEntityElectricBlock) entry.tile).getEnergy() < entry.energyBefore,
                      "Working machine did not consume energy: " + entry.variant.name + " at " + entry.tile.getPos());
                for (Object output : entry.bindings.outputs) entry.outputAmount += MachineStressBindings.amount(output);
                if (entry.cycles != null) {
                    try { entry.cycles.observe(world.getTotalWorldTime()); }
                    catch (AssertionError failure) {
                        throw new AssertionError(entry.variant.name + " at " + entry.tile.getPos() + ": " + failure.getMessage(), failure);
                    }
                }
                if (entry.wasProcessing) working++;
            }
        }
        return working;
    }

    public long producedResources() {
        long total = 0;
        for (Entry entry : entries) {
            if (entry.definition != null) check(entry.outputAmount > 0,
                  "Working machine never produced output during this run: " + entry.variant.name + " at " + entry.tile.getPos());
            total += entry.outputAmount;
        }
        return total;
    }

    /** Teardown only, after Spark and all measurements have stopped. */
    public void stopTicking() {
        IdentityHashMap<Object, Boolean> installed = new IdentityHashMap<>();
        for (Entry entry : entries) installed.put(entry.tile, Boolean.TRUE);
        world.tickableTileEntities.removeIf(installed::containsKey);
    }

    public static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public final class Entry {
        public final TileEntityBasicBlock tile;
        private final Variant variant;
        private final MachineRecipe<?, ?, ?> definition;
        private MachineStressBindings bindings;
        private RecipeCycleTracker cycles;
        private boolean wasProcessing;
        private int tickBefore;
        private double energyBefore;
        private long outputAmount;

        private Entry(TileEntityBasicBlock tile, Variant variant, MachineRecipe<?, ?, ?> definition) {
            this.tile = tile;
            this.variant = variant;
            this.definition = definition;
        }

        private void prepare() {
            if (tile instanceof TileEntityElectricBlock) {
                TileEntityElectricBlock electric = (TileEntityElectricBlock) tile;
                electric.setEnergy(electric.getMaxEnergy());
                energyBefore = electric.getEnergy();
            }
            if (definition != null) {
                if (bindings == null) bindings = MachineStressBindings.bind(tile, definition);
                if (cycleTarget > 0) {
                    if (cycles == null) cycles = new RecipeCycleTracker(bindings, variant.definitions, definition, cyclePattern, cycleTarget);
                    wasProcessing = !cycles.done();
                    cycles.prepare();
                    tickBefore = tile.ticker;
                    return;
                }
                wasProcessing = true;
                IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
                for (Object output : bindings.outputs) {
                    if (seen.put(output, Boolean.TRUE) == null) clear(output);
                }
                for (MachineStressBindings.Input input : bindings.inputs) {
                    if (seen.put(input.container, Boolean.TRUE) == null) fill(input.container, input.value);
                }
            }
            tickBefore = tile.ticker;
        }

        static void clear(Object container) {
            if (container instanceof IInventorySlot) ((IInventorySlot) container).setEmpty();
            else if (container instanceof IExtendedGasTank) ((IExtendedGasTank) container).setStack(null);
            else if (container instanceof IExtendedFluidTank) ((IExtendedFluidTank) container).setStack(null);
            else throw new AssertionError("Unsupported fixture output " + container.getClass());
        }

        static void fill(Object container, Object resource) {
            if (container instanceof IInventorySlot) {
                IInventorySlot slot = (IInventorySlot) container;
                ItemStack stack = ((ItemStack) resource).copy();
                if (stack.getMetadata() == 32767) stack.setItemDamage(0);
                stack.setCount(slot.getLimit(stack));
                slot.setStack(stack);
            } else if (container instanceof IExtendedGasTank) {
                IExtendedGasTank tank = (IExtendedGasTank) container;
                tank.setStack(((GasStack) resource).copy().withAmount(tank.getCapacity()));
            } else if (container instanceof IExtendedFluidTank) {
                IExtendedFluidTank tank = (IExtendedFluidTank) container;
                FluidStack stack = ((FluidStack) resource).copy();
                stack.amount = tank.getCapacity();
                tank.setStack(stack);
            } else throw new AssertionError("Unsupported fixture input " + container.getClass());
        }
    }

    private static final class Variant {
        final IBlockState state;
        final RecipeType mode;
        final List<MachineRecipe<?, ?, ?>> definitions;
        final String name;

        Variant(IBlockState state, RecipeType mode, List<MachineRecipe<?, ?, ?>> definitions, String name) {
            this.state = state;
            this.mode = mode;
            this.definitions = definitions;
            this.name = name;
        }
    }
}
