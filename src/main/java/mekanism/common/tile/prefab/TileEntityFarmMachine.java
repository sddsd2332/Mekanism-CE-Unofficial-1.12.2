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
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.capabilities.holder.fluid.FluidTankHelper;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.gas.GasTankHelper;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.capabilities.merged.MergedTank;
import mekanism.common.capabilities.merged.MergedTank.CurrentType;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.HybridInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.recipe.GasConversionHandler;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.cache.CachedRecipe;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.cache.IRecipeLookupHandler.ConstantUsageRecipeLookupHandler;
import mekanism.common.recipe.cache.ItemStackConstantFarmCachedRecipe;
import mekanism.common.recipe.cache.ItemStackConstantGasCachedRecipe.GasUsageMultiplier;
import mekanism.common.recipe.cache.inputs.InputHelper;
import mekanism.common.recipe.cache.outputs.OutputHelper;
import mekanism.common.recipe.inputs.FarmInput;
import mekanism.common.recipe.machines.FarmMachineRecipe;
import mekanism.common.recipe.outputs.FarmOutput;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.PoissonSampler;
import mekanism.common.util.TileUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 农场方块
 * 气体输入，物品输入
 * 物品输出 概率物品输出
 */

public abstract class TileEntityFarmMachine<RECIPE extends FarmMachineRecipe<RECIPE>> extends TileEntityUpgradeableMachine<FarmInput, FarmOutput, RECIPE> implements ISustainedData, ITankManager, ConstantUsageRecipeLookupHandler {

    public static final int BASE_TICKS_REQUIRED = 200;
    public static final int BASE_GAS_PER_TICK = 1;
    public static final int OUTPUT_SLOT_COUNT = 64;
    public static final int SECONDARY_TANK_CAPACITY = 1_000;
    private static final String[] methods = new String[]{"getEnergy", "getSecondaryStored", "getProgress", "isActive", "facing", "canOperate", "getMaxEnergy", "getEnergyNeeded"};
    public static int MAX_GAS = SECONDARY_TANK_CAPACITY;

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
    public BasicFluidTank fluidTank;
    public MergedTank mergedTank;
    public Gas prevGas;
    public Fluid prevFluid;
    protected InputInventorySlot inputSlot;
    protected HybridInventorySlot mergedTankSlot;
    protected EnergyInventorySlot energySlot;
    protected OutputInventorySlot outputSlot;
    protected OutputInventorySlot secondaryOutputSlot;
    protected final List<OutputInventorySlot> outputSlots = new ArrayList<>(OUTPUT_SLOT_COUNT);
    protected final GasUsageMultiplier gasUsageMultiplier;


    public TileEntityFarmMachine(String soundPath, MachineType type, int ticksRequired, int secondaryPerTick) {
        super(soundPath, type, 5, ticksRequired);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.FLUID, TransmissionType.GAS, TransmissionType.ENERGY);

        initializeInventorySlots();
        configComponent.setupItemIOExtraConfig(inputSlot, new ArrayList<>(outputSlots), mergedTankSlot, energySlot);
        configComponent.setConfig(TransmissionType.ITEM, DataType.ENERGY, DataType.EMPTY, DataType.NONE, DataType.EXTRA, DataType.INPUT, DataType.OUTPUT);

        configComponent.setupInputConfig(TransmissionType.FLUID, mergedTank.getFluidTank());
        configComponent.setupInputConfig(TransmissionType.GAS, mergedTank.getGasTank());
        configComponent.setInputConfig(TransmissionType.ENERGY);
        // The farm occupies the block above its controller with a bounding block.
        // Keep that physical top face out of every side-configuration channel.
        configComponent.addDisabledSides(RelativeSide.TOP);

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
        inputSlot = builder.addSlot(InputInventorySlot.at(stack -> getRecipes().keySet().stream().anyMatch(input -> ItemHandlerHelper.canItemStacksStack(input.itemStack, stack)), recipeCacheListener, 10, 79)
              .setAutoPullValidator((stack, side) -> canAutoPullInput(stack)));
        mergedTankSlot = builder.addSlot(HybridInventorySlot.inputOrDrainOrConvert(getOrCreateMergedTank(), this::getWorld, listener, 10, 121));
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 10, 37));
        outputSlots.clear();
        for (int slot = 0; slot < OUTPUT_SLOT_COUNT; slot++) {
            int x = 70 + slot % 8 * 18;
            int y = 16 + slot / 8 * 18;
            outputSlots.add(builder.addSlot(OutputInventorySlot.at(recipeCacheChangeListener, x, y)));
        }
        outputSlot = outputSlots.get(0);
        secondaryOutputSlot = outputSlots.get(1);
        return builder.build();
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        FluidTankHelper builder = createFluidTankHelper();
        builder.addTank(getOrCreateMergedTank().getFluidTank());
        return builder.build();
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        GasTankHelper builder = createGasTankHelper();
        builder.addTank(getOrCreateMergedTank().getGasTank());
        return builder.build();
    }

    private BasicGasTank getOrCreateGasTank() {
        if (gasTank == null) {
            gasTank = BasicGasTank.input(SECONDARY_TANK_CAPACITY, this::isValidGas, getRecipeCacheListener());
        }
        return gasTank;
    }

    private BasicFluidTank getOrCreateFluidTank() {
        if (fluidTank == null) {
            fluidTank = BasicFluidTank.input(SECONDARY_TANK_CAPACITY,
                  stack -> stack != null && stack.getFluid() != null && isValidFluid(stack.getFluid()), getRecipeCacheListener());
        }
        return fluidTank;
    }

    private MergedTank getOrCreateMergedTank() {
        if (mergedTank == null) {
            mergedTank = MergedTank.create(getOrCreateFluidTank(), getOrCreateGasTank());
        }
        return mergedTank;
    }

    @Nullable
    @Override
    protected GasStack getInputGasForUpgrade() {
        return mergedTank.getGasTank().getGas();
    }

    @Nullable
    @Override
    protected FluidStack getInputFluidForUpgrade() {
        FluidStack fluid = mergedTank.getFluidTank().getFluid();
        return fluid == null ? null : fluid.copy();
    }

    @Nonnull
    @Override
    protected ItemStack getInputSlotForUpgrade() {
        return inputSlot.getStack();
    }

    @Nonnull
    @Override
    protected ItemStack getExtraSlotForUpgrade() {
        return mergedTankSlot.getStack();
    }

    @Nonnull
    @Override
    protected ItemStack getOutputSlotForUpgrade() {
        return outputSlot.getStack();
    }

    @Nonnull
    @Override
    protected ItemStack getSecondaryOutputSlotForUpgrade() {
        return secondaryOutputSlot.getStack();
    }

    @Nonnull
    @Override
    protected ItemStack getEnergySlotForUpgrade() {
        return energySlot.getStack();
    }

    @Nullable
    public GasStack getItemGas(ItemStack itemStack) {
        return GasConversionHandler.getItemGas(itemStack, mergedTank.getGasTank(), this::isValidGas);
    }

    public abstract boolean isValidGas(Gas gas);

    public abstract boolean isValidFluid(Fluid fluid);

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
        secondaryEnergyThisTick = useStatisticalMechanics() ? secondaryEnergySampler.sample(secondaryEnergyPerTick) : (int) Math.ceil(secondaryEnergyPerTick);
    }

    private void updatePreviousMedium() {
        if (mergedTank.getCurrentType().isGas()) {
            prevGas = mergedTank.getGasTank().getGas().getGas();
            prevFluid = null;
        } else if (mergedTank.getCurrentType() == CurrentType.FLUID) {
            prevFluid = mergedTank.getFluidTank().getFluid().getFluid();
            prevGas = null;
        }
    }

    @Override
    protected mekanism.common.recipe.cache.RecipeLaneCommitTarget createAsyncRecipeCommitTarget(CachedRecipe<RECIPE> cache) {
        mekanism.common.recipe.cache.RecipeLaneCommitTarget target = new mekanism.common.recipe.cache.RecipeLaneCommitTarget(cache)
              .input("item.0", inputSlot).pooledOutputs();
        if (cache != null) {
            if (cache.getRecipe().getInput().isGasInput()) target.input("gas.1", mergedTank.getGasTank());
            else target.input("fluid.1", mergedTank.getFluidTank());
        }
        for (int index = 0; index < outputSlots.size(); index++) target.output("item." + index, outputSlots.get(index));
        return target;
    }

    @Override
    public void afterAsyncRecipeCommit(mekanism.common.recipe.cache.RecipeRunSnapshot snapshot,
          mekanism.common.recipe.cache.RecipeExecutionPlan plan) {
        super.afterAsyncRecipeCommit(snapshot, plan);
        updatePreviousMedium();
    }

    @Override
    public void addTileSyncTask() {
    }

    public void handleSecondaryFuel() {
        CurrentType currentType = mergedTank.getCurrentType();
        if (currentType == CurrentType.EMPTY || currentType == CurrentType.FLUID) {
            if (mergedTankSlot.fillTank()) {
                return;
            }
        }
        if (currentType == CurrentType.EMPTY || currentType.isGas()) {
            GasInventorySlot.fillTankOrConvert(mergedTankSlot, mergedTank.getGasTank(), this::getWorld);
        }
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

    @Override
    public FarmInput getInput() {
        return createFarmInput(inputSlot.getStack());
    }

    private FarmInput createFarmInput(ItemStack itemStack) {
        GasStack gas = mergedTank.getGasTank().getGas();
        if (gas != null && gas.amount > 0) {
            return new FarmInput(itemStack, gas);
        }
        FluidStack fluid = mergedTank.getFluidTank().getFluid();
        if (fluid != null && fluid.amount > 0) {
            return new FarmInput(itemStack, fluid);
        }
        if (prevGas != null) {
            return new FarmInput(itemStack, prevGas);
        }
        return new FarmInput(itemStack, prevFluid);
    }

    @Override
    public RECIPE getRecipe() {
        refreshRecipeLookupCache();
        FarmInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getFarmRecipe(input, getRecipes());
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

    public boolean hasWarningNoMatchingSecondaryInput() {
        RECIPE recipe = getRecipe();
        if (recipe == null) {
            return false;
        }
        int required = Math.max(0, secondaryEnergyThisTick);
        if (recipe.getInput().isGasInput()) {
            GasStack stored = mergedTank.getGasTank().getGas();
            return stored == null || !stored.isGasEqual(recipe.getInput().gasInput) || stored.amount < required;
        }
        FluidStack stored = mergedTank.getFluidTank().getFluid();
        return stored == null || !stored.isFluidEqual(recipe.getInput().fluidInput) || stored.amount < required;
    }

    public boolean hasWarningNoSpaceInOutput() {
        FarmOutput output = getCurrentOutput();
        return output != null && !OutputHelper.canFitFarmOutput(outputSlots, output);
    }

    public boolean hasWarningInputDoesntProduceOutput() {
        return false;
    }

    private FarmOutput getCurrentOutput() {
        RECIPE recipe = getRecipe();
        return recipe == null ? null : recipe.getOutput();
    }

    @Override
    public boolean canOperate(RECIPE recipe) {
        return recipe != null && recipe.getInput().useItem(inputSlot, false) && hasSecondaryInput(recipe.getInput(), secondaryEnergyThisTick) &&
              OutputHelper.canFitFarmOutput(outputSlots, recipe.getOutput());
    }

    private boolean hasSecondaryInput(FarmInput input, int usage) {
        int scale = Math.max(usage, 0);
        if (input.isGasInput()) {
            GasStack stored = mergedTank.getGasTank().getGas();
            return stored != null && stored.isGasEqual(input.gasInput) && stored.amount >= input.gasInput.amount * scale;
        }
        FluidStack stored = mergedTank.getFluidTank().getFluid();
        return stored != null && stored.isFluidEqual(input.fluidInput) && stored.amount >= input.fluidInput.amount * scale;
    }

    private boolean canAutoPullInput(ItemStack stack) {
        RECIPE recipe = RecipeHandler.getFarmRecipe(createFarmInput(getSimulatedStackWithInsert(0, stack)), getRecipes());
        return recipe != null && OutputHelper.canFitFarmOutput(outputSlots, recipe.getOutput());
    }

    @Override
    public CachedRecipe<RECIPE> createNewCachedRecipe(RECIPE recipe, int cacheIndex) {
        return new ItemStackConstantFarmCachedRecipe<>(recipe, this::shouldRecheckAllRecipeErrors,
              InputHelper.getInputHandler(inputSlot, RecipeError.NOT_ENOUGH_INPUT),
              InputHelper.getConstantGasInputHandler(mergedTank.getGasTank(), RecipeError.NOT_ENOUGH_SECONDARY_INPUT, false),
              InputHelper.getConstantFluidInputHandler(mergedTank.getFluidTank(), RecipeError.NOT_ENOUGH_SECONDARY_INPUT, false),
              OutputHelper.getFarmOutputHandler(outputSlots, RecipeError.NOT_ENOUGH_OUTPUT_SPACE),
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
              .setOnFinish(this::onCachedRecipeFinish);
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            TileUtils.readTankData(dataStream, fluidTank);
            TileUtils.readTankData(dataStream, gasTank);
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        TileUtils.addTankData(data, fluidTank);
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
        sanitizeAndClampTanks();
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
        return getSecondaryStored() * i / SECONDARY_TANK_CAPACITY;
    }

    public int getSecondaryStored() {
        return mergedTank.getCurrentType() == CurrentType.FLUID ? mergedTank.getFluidTank().getFluidAmount() : mergedTank.getGasTank().getGasAmount();
    }

    public List<OutputInventorySlot> getOutputSlots() {
        return Collections.unmodifiableList(outputSlots);
    }

    @Override
    public Object[] getManagedTanks() {
        return new Object[]{mergedTank.getFluidTank(), mergedTank.getGasTank()};
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
    public Map<FarmInput, RECIPE> getRecipes() {
        return null;
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        return switch (method) {
            case 0 -> new Object[]{getEnergy()};
            case 1 -> new Object[]{getSecondaryStored()};
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
        writeSustainedFluidTanks(itemStack);
        writeSustainedGasTanks(itemStack);
        ItemDataUtils.setLegacyGas(itemStack, "gasStored", gasTank.getGas());
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        readSustainedFluidTanks(itemStack);
        if (!readSustainedGasTanks(itemStack)) {
            gasTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "gasStored"));
        }
        gasTank.setMaxGas(MAX_GAS);
        sanitizeAndClampTanks();
    }

    private void sanitizeAndClampTanks() {
        FluidStack fluid = fluidTank.getFluid();
        if (fluid != null && (fluid.amount <= 0 || fluid.getFluid() == null)) {
            fluidTank.setEmpty();
        } else if (fluid != null) {
            fluidTank.setStackSize(fluid.amount, Action.EXECUTE);
        }
        GasStack stored = gasTank.getGas();
        if (stored != null && (stored.amount <= 0 || stored.getGas() == null)) {
            gasTank.setEmpty();
        } else if (stored != null) {
            gasTank.setStackSize(stored.amount, Action.EXECUTE);
        }
        if (!fluidTank.isEmpty() && !gasTank.isEmpty()) {
            fluidTank.setEmpty();
        }
    }
}
