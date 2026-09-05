package mekanism.common.tile.prefab;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.math.MathUtils;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.Upgrade;
import mekanism.common.base.ISustainedData;
import mekanism.common.base.ITankManager;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.capabilities.holder.gas.GasTankHelper;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import mekanism.common.recipe.GasConversionHandler;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.cache.CachedRecipe;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.cache.IRecipeLookupHandler.ConstantUsageRecipeLookupHandler;
import mekanism.common.recipe.cache.ItemStackConstantGasCachedRecipe;
import mekanism.common.recipe.cache.ItemStackConstantGasCachedRecipe.GasUsageMultiplier;
import mekanism.common.recipe.cache.inputs.InputHelper;
import mekanism.common.recipe.cache.outputs.OutputHelper;
import mekanism.common.recipe.inputs.AdvancedMachineInput;
import mekanism.common.recipe.machines.AdvancedMachineRecipe;
import mekanism.common.recipe.outputs.ItemStackOutput;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.PoissonSampler;
import mekanism.common.util.TileUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 带用气体类型的用电机器
 *
 * @param <RECIPE> 为机器配方类型
 */

public abstract class TileEntityAdvancedElectricMachine<RECIPE extends AdvancedMachineRecipe<RECIPE>> extends
        TileEntityUpgradeableMachine<AdvancedMachineInput, ItemStackOutput, RECIPE> implements ISustainedData, ITankManager, ConstantUsageRecipeLookupHandler {

    private static final List<RecipeError> TRACKED_ERROR_TYPES = Arrays.asList(
          RecipeError.NOT_ENOUGH_ENERGY,
          RecipeError.NOT_ENOUGH_INPUT,
          RecipeError.NOT_ENOUGH_SECONDARY_INPUT,
          RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );
    private static final String[] methods = new String[]{"getEnergy", "getSecondaryStored", "getProgress", "isActive", "facing", "canOperate", "getMaxEnergy",
            "getEnergyNeeded"};
    public static final int BASE_TICKS_REQUIRED = 200;
    public static final int BASE_GAS_PER_TICK = 1;
    public static final int MAX_GAS = 210;
    /**
     * How much secondary energy (fuel) this machine uses per tick, not including upgrades.
     */
    public int BASE_SECONDARY_ENERGY_PER_TICK;
    /**
     * How much secondary energy this machine uses per tick, including upgrades.
     */
    public double secondaryEnergyPerTick;
    public int secondaryEnergyThisTick;
    private double gasPerTickMeanMultiplier = 1;
    private long baseTotalUsage;
    private long usedSoFar;
    private final PoissonSampler gasUsageSampler = new PoissonSampler();
    private final PoissonSampler secondaryEnergySampler = new PoissonSampler();
    public BasicGasTank gasTank;
    public Gas prevGas;
    protected InputInventorySlot inputSlot;
    protected GasInventorySlot gasSlot;
    protected OutputInventorySlot outputSlot;
    protected EnergyInventorySlot energySlot;
    protected final GasUsageMultiplier gasUsageMultiplier;

    /**
     * Advanced Electric Machine -- a machine like this has a total of 4 slots. Input slot (0), fuel slot (1), output slot (2), energy slot (3), and the upgrade slot (4).
     * The machine will not run if it does not have enough electricity, or if it doesn't have enough fuel ticks.
     *
     * @param soundPath        - location of the sound effect
     * @param type             - the type of this machine
     * @param ticksRequired    - how many ticks it takes to smelt an item.
     * @param secondaryPerTick - how much secondary energy (fuel) this machine uses per tick.
     */
    public TileEntityAdvancedElectricMachine(String soundPath, MachineType type, int ticksRequired, int secondaryPerTick) {
        super(soundPath, type, 4, ticksRequired, TRACKED_ERROR_TYPES);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.GAS, TransmissionType.ENERGY);

        initializeInventorySlots();
        configComponent.setupItemIOExtraConfig(inputSlot, outputSlot, gasSlot, energySlot);
        configComponent.setConfig(TransmissionType.ITEM, DataType.EXTRA, DataType.INPUT, DataType.INPUT, DataType.ENERGY, DataType.INPUT, DataType.OUTPUT);

        if (allowExtractingGas()) {
            configComponent.setupIOConfig(TransmissionType.GAS, gasTank, RelativeSide.RIGHT).setCanEject(false);
        } else {
            configComponent.setupInputConfig(TransmissionType.GAS, gasTank);
        }
        configComponent.setInputConfig(TransmissionType.ENERGY);

        BASE_SECONDARY_ENERGY_PER_TICK = secondaryPerTick;
        secondaryEnergyPerTick = secondaryPerTick;
        baseTotalUsage = ticksRequired;
        if (useStatisticalMechanics()) {
            gasUsageMultiplier = (usedSoFar, operatingTicks) -> gasUsageSampler.sample(gasPerTickMeanMultiplier);
        } else {
            gasUsageMultiplier = (usedSoFar, operatingTicks) -> {
                long baseRemaining = baseTotalUsage - usedSoFar;
                int remainingTicks = getTicksRequired() - operatingTicks;
                if (baseRemaining < remainingTicks) {
                    return 0;
                } else if (baseRemaining == remainingTicks) {
                    return 1;
                }
                return Math.max(MathUtils.clampToLong(baseRemaining / (double) remainingTicks), 0);
            };
        }

        if (upgradeableSecondaryEfficiency()) {
            upgradeComponent.setSupported(Upgrade.GAS);
        }
        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM);
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        IContentsListener recipeCacheListener = getRecipeCacheListener();
        IContentsListener recipeCacheChangeListener = getRecipeCacheChangeListener(listener);
        inputSlot = builder.addSlot(InputInventorySlot.at(stack -> getRecipes().keySet().stream().anyMatch(input -> ItemHandlerHelper.canItemStacksStack(input.itemStack, stack)), recipeCacheListener, 64, 17)
              .setAutoPullValidator((stack, side) -> canAutoPullInput(stack)));
        inputSlot.tracksWarnings(slot -> slot.warning(WarningType.NO_MATCHING_RECIPE, getWarningCheck(RecipeError.NOT_ENOUGH_INPUT)));
        gasSlot = builder.addSlot(GasInventorySlot.fillOrConvert(gasTank, this::getWorld, listener, 64, 53));
        outputSlot = builder.addSlot(OutputInventorySlot.at(recipeCacheChangeListener, 116, 35));
        outputSlot.tracksWarnings(slot -> slot.warning(WarningType.NO_SPACE_IN_OUTPUT, getWarningCheck(RecipeError.NOT_ENOUGH_OUTPUT_SPACE)));
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 39, 35));
        return builder.build();
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        GasTankHelper builder = createGasTankHelper();
        builder.addTank(getOrCreateGasTank());
        return builder.build();
    }

    private BasicGasTank getOrCreateGasTank() {
        if (gasTank == null) {
            gasTank = allowExtractingGas() ? BasicGasTank.create(MAX_GAS, this::isValidGas, this::isValidGas, getRecipeCacheListener())
                  : BasicGasTank.input(MAX_GAS, this::isValidGas, getRecipeCacheListener());
        }
        return gasTank;
    }

    @Nullable
    @Override
    protected GasStack getInputGasForUpgrade() {
        return gasTank.getGas();
    }

    @Nonnull
    @Override
    protected ItemStack getInputSlotForUpgrade() {
        return inputSlot.getStack();
    }

    @Nonnull
    @Override
    protected ItemStack getExtraSlotForUpgrade() {
        return gasSlot.getStack();
    }

    @Nonnull
    @Override
    protected ItemStack getOutputSlotForUpgrade() {
        return outputSlot.getStack();
    }

    @Nonnull
    @Override
    protected ItemStack getEnergySlotForUpgrade() {
        return energySlot.getStack();
    }

    /**
     * Gets the amount of ticks the declared itemstack can fuel this machine.
     *
     * @param itemStack - itemstack to check with
     * @return fuel ticks
     */
    @Nullable
    public GasStack getItemGas(ItemStack itemStack) {
        return GasConversionHandler.getItemGas(itemStack, gasTank, this::isValidGas);
    }

    public abstract boolean isValidGas(Gas gas);

    @Override
    public void onAsyncUpdateServer() {
        commitAsyncRecipeTick();
    }

    @Override
    public void prepareAsyncRecipeTick() {
        if (energySlot != null) {
            energySlot.fillContainerOrConvert();
        }
        handleSecondaryFuel();
        secondaryEnergyThisTick = useStatisticalMechanics() ? secondaryEnergySampler.sample(secondaryEnergyPerTick) : ceilSecondaryEnergyPerTick(secondaryEnergyPerTick);
    }

    private void updatePreviousGas() {
        if (!(gasTank.getGasType() == null || gasTank.getStored() == 0)) {
            prevGas = gasTank.getGasType();
        }
    }

    @Override
    protected mekanism.common.recipe.cache.RecipeLaneCommitTarget createAsyncRecipeCommitTarget(CachedRecipe<RECIPE> cache) {
        return new mekanism.common.recipe.cache.RecipeLaneCommitTarget(cache)
              .input("item.0", inputSlot).input("gas.1", gasTank).output("item.0", outputSlot);
    }

    @Override
    public void afterAsyncRecipeCommit(mekanism.common.recipe.cache.RecipeRunSnapshot snapshot,
          mekanism.common.recipe.cache.RecipeExecutionPlan plan) {
        super.afterAsyncRecipeCommit(snapshot, plan);
        updatePreviousGas();
    }

    /**
     * Avoids accidental over-rounding when upgrade scaling lands on an integer but is represented as integer + 1 ULP.
     */
    public static int ceilSecondaryEnergyPerTick(double secondaryEnergyPerTick) {
        return (int) Math.ceil(Math.nextAfter(secondaryEnergyPerTick, Double.NEGATIVE_INFINITY));
    }


    @Override
    public void addTileSyncTask() {
    }

    public void handleSecondaryFuel() {
        gasSlot.fillTankOrConvert();
    }

    public boolean upgradeableSecondaryEfficiency() {
        return false;
    }

    public boolean useStatisticalMechanics() {
        return false;
    }

    public int getRecipeGasUsagePerOperation() {
        if (!useStatisticalMechanics()) {
            return Math.max(1, MathUtils.clampToInt(baseTotalUsage));
        }
        double usage = 3D * Math.max(1, Math.ceil(Math.max(secondaryEnergyPerTick, 0))) * Math.max(1, getTicksRequired());
        return Math.max(1, MathUtils.clampToInt(usage));
    }

    protected boolean allowExtractingGas() {
        return !useStatisticalMechanics();
    }

    @Override
    public AdvancedMachineInput getInput() {
        return new AdvancedMachineInput(inputSlot.getStack(), prevGas);
    }

    @Override
    public RECIPE getRecipe() {
        refreshRecipeLookupCache();
        AdvancedMachineInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getRecipe(input, getRecipes());
        }
        return cachedRecipe;
    }

    @Override
    public RECIPE getRecipe(int cacheIndex) {
        return getRecipe();
    }

    public MachineEnergyContainer getEnergyContainer() {
        return getMainEnergyContainer();
    }

    public boolean hasWarningNoMatchingInput() {
        return !inputSlot.isEmpty() && getRecipe() == null;
    }

    public boolean hasWarningNoMatchingSecondaryInput() {
        return getRecipe() != null && gasTank.getStored() < secondaryEnergyThisTick;
    }

    public boolean hasWarningNoSpaceInOutput() {
        ItemStack output = getCurrentOutput();
        if (output.isEmpty()) {
            return false;
        }
        ItemStack current = outputSlot.getStack();
        if (!current.isEmpty() && !ItemHandlerHelper.canItemStacksStack(current, output)) {
            return false;
        }
        return !outputSlot.insertItem(output.copy(), Action.SIMULATE, AutomationType.INTERNAL).isEmpty();
    }

    public boolean hasWarningInputDoesntProduceOutput() {
        ItemStack output = getCurrentOutput();
        ItemStack current = outputSlot.getStack();
        return !output.isEmpty() && !current.isEmpty() && !ItemHandlerHelper.canItemStacksStack(current, output);
    }

    @Override
    public Map<AdvancedMachineInput, RECIPE> getRecipes() {
        return null;
    }

    @Override
    public boolean canOperate(RECIPE recipe) {
        return recipe != null && recipe.canOperate(inputSlot, outputSlot, gasTank, secondaryEnergyThisTick);
    }

    private boolean canAutoPullInput(ItemStack stack) {
        Gas gasType = gasTank.getGasType() == null ? prevGas : gasTank.getGasType();
        RECIPE recipe = RecipeHandler.getRecipe(new AdvancedMachineInput(getSimulatedStackWithInsert(0, stack), gasType), getRecipes());
        return recipe != null && canOutputToSlot(2, recipe.getOutput().output);
    }

    @Override
    public CachedRecipe<RECIPE> createNewCachedRecipe(RECIPE recipe, int cacheIndex) {
        return new ItemStackConstantGasCachedRecipe<>(recipe, this::shouldRecheckAllRecipeErrors,
              InputHelper.getInputHandler(inputSlot, RecipeError.NOT_ENOUGH_INPUT),
              InputHelper.getConstantGasInputHandler(gasTank, RecipeError.NOT_ENOUGH_SECONDARY_INPUT, false),
              OutputHelper.getOutputHandler(outputSlot, RecipeError.NOT_ENOUGH_OUTPUT_SPACE),
              gasUsageMultiplier, used -> usedSoFar = used)
              .setCanHolderFunction(() -> MekanismUtils.canFunction(this))
              .setActive(active -> {
                  if (active || prevEnergy >= getEnergy()) {
                      setActive(active);
                  }
              })
              .setEnergyRequirements(() -> energyPerTick, getMainEnergyContainer())
              .setRequiredTicks(() -> ticksRequired)
              .setBaselineMaxOperations(() -> getBaselineMaxOperations(energyPerTick, true))
              .setOperatingTicksChanged(ticks -> operatingTicks = ticks)
              .setErrorsChanged(this::onRecipeErrorsChanged)
              .setOnFinish(this::onCachedRecipeFinish);
    }

    private ItemStack getCurrentOutput() {
        RECIPE recipe = getRecipe();
        if (recipe == null || recipe.getOutput().output.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return recipe.getOutput().output;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            TileUtils.readTankData(dataStream, gasTank);
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        TileUtils.addTankData(data, gasTank);
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        usedSoFar = nbtTags.getLong(NBTConstants.USED_SO_FAR);
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("gasTank")) {
            gasTank.read(nbtTags.getCompoundTag("gasTank"));
        }
        gasTank.setMaxGas(MAX_GAS);
        sanitizeAndClampGasTank();
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setLong(NBTConstants.USED_SO_FAR, usedSoFar);
    }

    /**
     * Gets the scaled secondary energy level for the GUI.
     *
     * @param i - multiplier
     * @return scaled secondary energy
     */
    public int getScaledGasLevel(int i) {
        return gasTank.getStored() * i / gasTank.getMaxGas();
    }

    @Override
    public Object[] getManagedTanks() {
        return new Object[]{gasTank};
    }

    @Override
    public void recalculateUpgradables(Upgrade upgrade) {
        super.recalculateUpgradables(upgrade);
        if (!isRecalculatingAllUpgradables() && shouldRecalculateSecondary(upgrade)) {
            recalculateSecondaryUpgrade();
        }
    }

    @Override
    protected void onAllUpgradablesRecalculated(Set<Upgrade> upgrades) {
        super.onAllUpgradablesRecalculated(upgrades);
        if (upgrades.contains(Upgrade.SPEED) || upgradeableSecondaryEfficiency() && upgrades.contains(Upgrade.GAS)) {
            recalculateSecondaryUpgrade();
        }
    }

    private boolean shouldRecalculateSecondary(Upgrade upgrade) {
        return upgrade == Upgrade.SPEED || upgradeableSecondaryEfficiency() && upgrade == Upgrade.GAS;
    }

    private void recalculateSecondaryUpgrade() {
        secondaryEnergyPerTick = MekanismUtils.getSecondaryEnergyPerTickMean(this, BASE_SECONDARY_ENERGY_PER_TICK);
        if (useStatisticalMechanics()) {
            gasPerTickMeanMultiplier = MekanismUtils.getGasPerTickMeanMultiplier(this);
        } else {
            baseTotalUsage = MekanismUtils.getBaseUsage(this, BASE_TICKS_REQUIRED);
        }
    }

    @Override
    public long getSavedUsedSoFar(int cacheIndex) {
        return usedSoFar;
    }

    @Override
    protected long getSavedUsedSoFarForUpgrade() {
        return usedSoFar;
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        return switch (method) {
            case 0 -> new Object[]{getEnergy()};
            case 1 -> new Object[]{gasTank.getStored()};
            case 2 -> new Object[]{operatingTicks};
            case 3 -> new Object[]{isActive};
            case 4 -> new Object[]{facing};
            case 5 -> new Object[]{canOperate(getRecipe())};
            case 6 -> new Object[]{maxEnergy};
            case 7 -> new Object[]{maxEnergy - getEnergy()};
            default -> throw new NoSuchMethodException();
        };
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        writeSustainedGasTanks(itemStack);
        ItemDataUtils.setLegacyGas(itemStack, "gasStored", gasTank.getGas());
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (!readSustainedGasTanks(itemStack)) {
            gasTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "gasStored"));
        }
        gasTank.setMaxGas(MAX_GAS);
        sanitizeAndClampGasTank();
    }

    private void sanitizeAndClampGasTank() {
        GasStack stored = gasTank.getGas();
        if (stored != null && (stored.amount <= 0 || stored.getGas() == null)) {
            gasTank.setEmpty();
        } else if (stored != null) {
            gasTank.setStackSize(stored.amount, Action.EXECUTE);
        }
    }
}
