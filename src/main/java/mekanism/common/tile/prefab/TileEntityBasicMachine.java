package mekanism.common.tile.prefab;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IAsyncMachinePlanner;
import mekanism.api.IAsyncPlanCalculator;
import mekanism.api.IConfigCardAccess;
import mekanism.api.IContentsListener;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.config.MekanismConfig;
import mekanism.common.Upgrade;
import mekanism.common.concurrent.TaskExecutor;
import mekanism.common.integration.computer.IComputerIntegration;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.cache.CachedRecipe;
import mekanism.common.recipe.cache.RecipeExecutionPlan;
import mekanism.common.recipe.cache.RecipeExecutionPlanner;
import mekanism.common.recipe.cache.AsyncMachinePlanSupport;
import mekanism.common.recipe.cache.RecipeRunSnapshot;
import mekanism.common.recipe.cache.RecipeRandomContext;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.cache.IRecipeLookupHandler;
import mekanism.common.recipe.cache.RecipeCacheLookupMonitor;
import mekanism.common.recipe.cache.IAsyncRecipeMachine;
import mekanism.common.recipe.cache.RecipeLaneCommitTarget;
import mekanism.common.recipe.cache.RecipeLanePlan;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.machines.MachineRecipe;
import mekanism.common.recipe.outputs.MachineOutput;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * 基本类型机器方块
 *
 * @param <INPUT>  用于机器的输入
 * @param <OUTPUT> 用于机器的输出
 * @param <RECIPE> 用于机器的配方
 */

public abstract class TileEntityBasicMachine<INPUT extends MachineInput<INPUT>, OUTPUT extends MachineOutput<OUTPUT>, RECIPE extends MachineRecipe<INPUT, OUTPUT, RECIPE>> extends
        TileEntityOperationalMachine implements IComputerIntegration, ISideConfiguration, IConfigCardAccess,
        IRecipeLookupHandler<RECIPE>, IAsyncRecipeMachine {

    public RECIPE cachedRecipe = null;
    protected RecipeCacheLookupMonitor<RECIPE> recipeCacheLookupMonitor = new RecipeCacheLookupMonitor<>(this);
    private IContentsListener recipeCacheChangeListener;
    private final List<RecipeError> trackedRecipeErrorTypes;
    private final boolean[] trackedRecipeErrors;
    private int cachedRecipeVersion = -1;
    private long cachedRecipeGeneration = -1;

    public TileComponentEjector ejectorComponent;
    public TileComponentConfig configComponent;

    /**
     * The foundation of all machines - a simple tile entity with a facing, active state, initialized state, sound effect, and animated texture.
     *
     * @param soundPath         - location of the sound effect
     * @param type              - the type of this machine
     * @param baseTicksRequired - how many ticks it takes to run a cycle
     */
    public TileEntityBasicMachine(String soundPath, MachineType type, int upgradeSlot, int baseTicksRequired) {
        this(soundPath, type, upgradeSlot, baseTicksRequired, Collections.emptyList());
    }

    public TileEntityBasicMachine(String soundPath, MachineType type, int upgradeSlot, int baseTicksRequired, List<RecipeError> trackedErrorTypes) {
        super("machine." + soundPath, type, upgradeSlot, baseTicksRequired);
        this.trackedRecipeErrorTypes = trackedErrorTypes;
        this.trackedRecipeErrors = new boolean[trackedErrorTypes.size()];
    }

    public TileEntityBasicMachine(String soundPath, String name, double energyStorge, double energUsage, int upgradeSlot, int baseTicksRequired) {
        super("machine." + soundPath, name, energyStorge, energUsage, upgradeSlot, baseTicksRequired);
        this.trackedRecipeErrorTypes = Collections.emptyList();
        this.trackedRecipeErrors = new boolean[0];
    }

    @Override
    public boolean sideIsConsumer(EnumFacing side) {
        return configComponent == null ? super.sideIsConsumer(side) :
              configComponent.hasSideForData(TransmissionType.ENERGY, facing, mekanism.common.tile.component.config.DataType.INPUT, side);
    }

    @Override
    public TileComponentConfig getConfig() {
        return configComponent;
    }

    @Override
    public EnumFacing getOrientation() {
        return facing;
    }

    @Override
    public TileComponentEjector getEjector() {
        return ejectorComponent;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return false;
        }
        return capability == Capabilities.CONFIG_CARD_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return null;
        }
        if (capability == Capabilities.CONFIG_CARD_CAPABILITY) {
            return Capabilities.CONFIG_CARD_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        return configComponent != null && configComponent.isCapabilityDisabled(capability, side, facing) || super.isCapabilityDisabled(capability, side);
    }

    protected void setFinish() {

    }

    protected void setNoFinish() {

    }

    protected void onCachedRecipeFinish() {
        setFinish();
        markNoUpdateSync();
    }

    protected int getBaselineMaxOperations(double energyTick, boolean defaultEnergy) {
        if (defaultEnergy && MekanismConfig.current().mekce.EnableUpgradeConfigure.val() && ticksRequired <= 0 && energyTick > 0) {
            int requestedOperations = 1 - ticksRequired;
            int availableOperations = 1 + (int) (getEnergy() / energyTick);
            return Math.max(1, Math.min(requestedOperations, availableOperations));
        }
        return 1;
    }

    protected boolean shouldRecheckAllRecipeErrors() {
        return false;
    }

    @Deprecated
    public boolean canOperate(RECIPE recipe) {
        return false;
    }

    public abstract RECIPE getRecipe();

    public abstract INPUT getInput();

    public abstract Map<INPUT, RECIPE> getRecipes();

    protected void clearRecipeLookupCache() {
        cachedRecipe = null;
    }

    protected void refreshRecipeLookupCache() {
        long recipeGeneration = RecipeHandler.getGlobalRecipeGeneration();
        if (cachedRecipeGeneration != recipeGeneration) {
            clearRecipeLookupCache();
            cachedRecipeGeneration = recipeGeneration;
            cachedRecipeVersion = RecipeHandler.getGlobalRecipeVersion();
        }
    }

    @Override
    public void onRecipeCacheInvalidated(int cacheIndex) {
        clearRecipeLookupCache();
        cachedRecipeGeneration = RecipeHandler.getGlobalRecipeGeneration();
        cachedRecipeVersion = RecipeHandler.getGlobalRecipeVersion();
    }

    @Override
    public void clearRecipeErrors(int cacheIndex) {
        Arrays.fill(trackedRecipeErrors, false);
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        if (trackedRecipeErrors.length > 0) {
            container.trackArray(trackedRecipeErrors);
        }
    }

    protected void onRecipeErrorsChanged(Set<RecipeError> errors) {
        for (int i = 0; i < trackedRecipeErrors.length; i++) {
            trackedRecipeErrors[i] = errors.contains(trackedRecipeErrorTypes.get(i));
        }
    }

    public boolean hasWarning(RecipeError error) {
        int errorIndex = trackedRecipeErrorTypes.indexOf(error);
        return errorIndex != -1 && trackedRecipeErrors[errorIndex];
    }

    public BooleanSupplier getWarningCheck(RecipeError error) {
        int errorIndex = trackedRecipeErrorTypes.indexOf(error);
        return errorIndex == -1 ? () -> false : () -> trackedRecipeErrors[errorIndex];
    }

    protected IContentsListener getRecipeCacheListener() {
        return recipeCacheLookupMonitor;
    }

    protected IContentsListener getRecipeCacheChangeListener(IContentsListener listener) {
        if (recipeCacheChangeListener == null) {
            recipeCacheChangeListener = () -> {
                listener.onContentsChanged();
                recipeCacheLookupMonitor.onChange();
            };
        }
        return recipeCacheChangeListener;
    }

    protected void processRecipe() {
        if (!recipeCacheLookupMonitor.updateAndProcess()) {
            if (prevEnergy >= getEnergy()) {
                setActive(false);
            }
            operatingTicks = 0;
        }
    }

    /**
     * Captures every value visible to the generic recipe pre-planner. Subclasses
     * with specialized semantics may override the planner methods, but must retain
     * the same no-live-reference rule.
     */
    @Nonnull
    @Override
    public RecipeRunSnapshot captureSnapshot() {
        return IAsyncRecipeMachine.super.captureSnapshot();
    }

    @Nonnull
    @Override
    public RecipeExecutionPlan calculatePlan(RecipeRunSnapshot snapshot) {
        return RecipeExecutionPlanner.calculate(snapshot);
    }

    @Override
    public IAsyncPlanCalculator<RecipeRunSnapshot, RecipeExecutionPlan> getAsyncPlanCalculator() {
        return RecipeExecutionPlanner.detachedCalculator();
    }

    /**
     * Recipe machines opt into worker planning only after they expose a complete
     * live commit target. Unmigrated subclasses retain the server-thread path.
     */
    @Override
    @Nullable
    protected mekanism.api.IAsyncMachinePlanner<?, ?> getAsyncMachinePlanner() {
        try {
            return getAsyncRecipeCommitTargets().isEmpty() ? null : this;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public void commitPlan(RecipeRunSnapshot snapshot, RecipeExecutionPlan plan) {
        IAsyncRecipeMachine.super.commitPlan(snapshot, plan);
    }

    @Override
    public Object getAsyncRecipeSnapshotSource() {
        return getRecipe();
    }

    @Override
    public void commitAsyncRecipeTick() {
        IAsyncRecipeMachine.super.commitAsyncRecipeTick();
    }

    @Override
    public Map<Integer, RecipeLaneCommitTarget> getAsyncRecipeCommitTargets() {
        RecipeLaneCommitTarget target = createAsyncRecipeCommitTarget(recipeCacheLookupMonitor.prepareCache());
        return target == null ? Collections.emptyMap() : Collections.singletonMap(0, target);
    }

    protected RecipeLaneCommitTarget createAsyncRecipeCommitTarget(CachedRecipe<RECIPE> cache) {
        return null;
    }

    @Override
    public void afterAsyncRecipeCommit(RecipeRunSnapshot snapshot, RecipeExecutionPlan plan) {
        RecipeLanePlan lane = plan.getLane(0);
        operatingTicks = lane.getNewOperatingTicks();
        if (!snapshot.getLane(0).isRecipePresent() && prevEnergy >= getEnergy()) setActive(false);
        prevEnergy = getEnergy();
        // The legacy processing loop resolves the cache again after a completion.
        // Refresh here so depleted inputs clear the old cache in the same tick.
        recipeCacheLookupMonitor.refreshAfterPlanCommit();
    }

    @Override
    public boolean isPlanStillValid(RecipeRunSnapshot snapshot, RecipeExecutionPlan plan) {
        if (plan == null || !plan.isValidFor(snapshot) ||
            RecipeHandler.getGlobalRecipeGeneration() != snapshot.getGlobalRecipeGeneration()) {
            return false;
        }
        RECIPE currentRecipe = getRecipe();
        return snapshot.getConfigurationVersion() == getProcessingStateVersion() &&
              snapshot.getQioLeaseVersion() == getAsyncLeaseVersion() &&
              snapshot.getPortOwnershipVersion() == getAsyncPortOwnershipVersion() &&
              snapshot.getMode().equals(getAsyncMode()) &&
              AsyncMachinePlanSupport.isStillValid(this, snapshot, plan, currentRecipe,
                    getAsyncRecipeCategoryGeneration());
    }

    /** Hook for machines which can expose their category-specific generation. */
    public long getAsyncRecipeCategoryGeneration() {
        Map<INPUT, RECIPE> recipes = getRecipes();
        for (RecipeHandler.Recipe<?, ?, ?> category : RecipeHandler.Recipe.values()) {
            if (category.get() == recipes) {
                return category.getRecipeGeneration();
            }
        }
        return RecipeHandler.getGlobalRecipeGeneration();
    }

    /** Stable value for recipe modes which alter the input/output direction. */
    public String getAsyncMode() {
        return "";
    }

    protected double processRecipe(IEnergyContainer energyContainer) {
        double energyUsed = recipeCacheLookupMonitor.updateAndProcess(energyContainer);
        if (recipeCacheLookupMonitor.getCachedRecipe(0) == null) {
            if (prevEnergy >= getEnergy()) {
                setActive(false);
            }
            operatingTicks = 0;
        }
        return energyUsed;
    }

    @Override
    public void setEnergy(double energy) {
        double previous = getEnergy();
        super.setEnergy(energy);
        if (Double.compare(previous, getEnergy()) != 0) {
            unpauseRecipeCache();
        }
    }

    @Override
    public void recalculateUpgradables(Upgrade upgrade) {
        super.recalculateUpgradables(upgrade);
        if (!isRecalculatingAllUpgradables()) {
            unpauseRecipeCache();
        }
    }

    @Override
    protected void onAllUpgradablesRecalculated(Set<Upgrade> upgrades) {
        super.onAllUpgradablesRecalculated(upgrades);
        if (!upgrades.isEmpty()) {
            unpauseRecipeCache();
        }
    }

    protected void unpauseRecipeCache() {
        if (recipeCacheLookupMonitor != null && world != null && !world.isRemote) {
            recipeCacheLookupMonitor.unpause();
        }
    }

    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        IContentsListener recipeCacheChangeListener = getRecipeCacheChangeListener(listener);
        getMainEnergyContainer(recipeCacheChangeListener);
        return super.getInitialEnergyContainers(recipeCacheChangeListener);
    }

    protected boolean canOutputToSlot(int slotId, ItemStack output) {
        IInventorySlot outputSlot = getInventorySlot(slotId);
        return outputSlot != null && outputSlot.insertItem(output, Action.SIMULATE, AutomationType.INTERNAL).isEmpty();
    }

    protected ItemStack getSimulatedStackWithInsert(int slotId, ItemStack stack) {
        IInventorySlot slot = getInventorySlot(slotId);
        if (slot == null) {
            return ItemStack.EMPTY;
        }
        ItemStack current = slot.getStack();
        if (current.isEmpty()) {
            return stack.copy();
        }
        ItemStack simulated = current.copy();
        simulated.grow(stack.getCount());
        return simulated;
    }

    @Override
    public int getSavedOperatingTicks(int cacheIndex) {
        return operatingTicks;
    }

    @Override
    public RECIPE getRecipe(int cacheIndex) {
        return getRecipe();
    }

    @Override
    public CachedRecipe<RECIPE> createNewCachedRecipe(RECIPE recipe, int cacheIndex) {
        return null;
    }

    @Override
    public void onCachedRecipeChanged(CachedRecipe<RECIPE> cachedRecipe, int cacheIndex) {
        IRecipeLookupHandler.super.onCachedRecipeChanged(cachedRecipe, cacheIndex);
    }

    @Override
    public int getBlockGuiID(Block block, int metadata) {
        return MachineType.get(block, metadata) != null ? MachineType.get(block, metadata).guiId : -1;
    }
}
