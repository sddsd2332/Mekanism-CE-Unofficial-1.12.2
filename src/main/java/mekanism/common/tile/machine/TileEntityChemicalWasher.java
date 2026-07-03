package mekanism.common.tile.machine;

import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
import mekanism.api.TileNetworkList;
import mekanism.api.gas.GasStack;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.Upgrade;
import mekanism.common.Upgrade.IUpgradeInfoHandler;
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
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.FluidInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.cache.CachedRecipe;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.cache.TwoInputCachedRecipe;
import mekanism.common.recipe.cache.inputs.InputHelper;
import mekanism.common.recipe.cache.outputs.OutputHelper;
import mekanism.common.recipe.inputs.GasAndFluidInput;
import mekanism.common.recipe.machines.WasherRecipe;
import mekanism.common.recipe.outputs.GasOutput;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityBasicMachine;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TileUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TileEntityChemicalWasher extends TileEntityBasicMachine<GasAndFluidInput, GasOutput, WasherRecipe> implements ISustainedData, IUpgradeInfoHandler, ITankManager {

    public static final int MAX_GAS = 10000;
    public static final int MAX_FLUID = 10000;
    private static final List<RecipeError> TRACKED_ERROR_TYPES = Arrays.asList(
          RecipeError.NOT_ENOUGH_ENERGY,
          RecipeError.NOT_ENOUGH_ENERGY_REDUCED_RATE,
          RecipeError.NOT_ENOUGH_INPUT,
          RecipeError.NOT_ENOUGH_SECONDARY_INPUT,
          RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );
    public static int WATER_USAGE = 5;
    public BasicFluidTank fluidTank;
    public BasicGasTank inputTank;
    public BasicGasTank outputTank;

    public WasherRecipe cachedRecipe;
    public double clientEnergyUsed;
    private int currentRedstoneLevel;
    private FluidInventorySlot inputSlot;
    private OutputInventorySlot outputSlot;
    private GasInventorySlot gasSlot;
    private EnergyInventorySlot energySlot;

    public TileEntityChemicalWasher() {
        super("washer", MachineType.CHEMICAL_WASHER, 4, 1, TRACKED_ERROR_TYPES);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.ENERGY, TransmissionType.FLUID, TransmissionType.GAS);
        initializeInventorySlots();
        configComponent.setupItemIOConfig(Collections.singletonList(inputSlot), Arrays.asList(gasSlot, outputSlot), energySlot, true);
        configComponent.setConfig(TransmissionType.ITEM, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.ENERGY, DataType.INPUT, DataType.OUTPUT);
        configComponent.setCanEject(TransmissionType.ITEM, false);

        configComponent.setupFluidInputConfig(fluidTank);
        configComponent.setupIOConfig(TransmissionType.GAS, inputTank, outputTank, RelativeSide.RIGHT);
        configComponent.setConfig(TransmissionType.GAS, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.OUTPUT);

        configComponent.setInputConfig(TransmissionType.ENERGY);
        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM, TransmissionType.GAS)
              .setCanTankEject(tank -> tank != inputTank);
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        inputSlot = builder.addSlot(FluidInventorySlot.fill(fluidTank, listener, 180, 71));
        inputSlot.setSlotType(ContainerSlotType.INPUT);
        inputSlot.setSlotOverlay(SlotOverlay.INPUT);
        outputSlot = builder.addSlot(OutputInventorySlot.at(getRecipeCacheChangeListener(listener), 180, 102));
        outputSlot.setSlotOverlay(SlotOverlay.OUTPUT);
        gasSlot = builder.addSlot(GasInventorySlot.drain(outputTank, listener, 152, 56));
        gasSlot.setSlotOverlay(SlotOverlay.MINUS);
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 152, 14));
        return builder.build();
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        FluidTankHelper builder = createFluidTankHelper();
        builder.addTank(getOrCreateFluidTank());
        return builder.build();
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        GasTankHelper builder = createGasTankHelper();
        builder.addTank(getOrCreateInputTank());
        builder.addTank(getOrCreateOutputTank(listener));
        return builder.build();
    }

    private BasicFluidTank getOrCreateFluidTank() {
        if (fluidTank == null) {
            fluidTank = BasicFluidTank.input(MAX_FLUID, fluid -> Recipe.CHEMICAL_WASHER.containsRecipe(fluid.getFluid()), getRecipeCacheListener());
        }
        return fluidTank;
    }

    private BasicGasTank getOrCreateInputTank() {
        if (inputTank == null) {
            inputTank = BasicGasTank.input(MAX_GAS, gas -> Recipe.CHEMICAL_WASHER.containsRecipe(gas), getRecipeCacheListener());
        }
        return inputTank;
    }

    private BasicGasTank getOrCreateOutputTank(IContentsListener listener) {
        if (outputTank == null) {
            outputTank = BasicGasTank.output(MAX_GAS, getRecipeCacheChangeListener(listener));
        }
        return outputTank;
    }

    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        energySlot.fillContainerOrConvert();
        manageBuckets();
        gasSlot.drainTank();
        clientEnergyUsed = processRecipe(getMainEnergyContainer());
        prevEnergy = getEnergy();
        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            updateComparatorOutputLevelSync();
            currentRedstoneLevel = newRedstoneLevel;
        }
    }

    @Override
    public WasherRecipe getRecipe() {
        refreshRecipeLookupCache();
        GasAndFluidInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getChemicalWasherRecipe(getInput());
        }
        return cachedRecipe;
    }

    @Override
    protected void clearRecipeLookupCache() {
        super.clearRecipeLookupCache();
        cachedRecipe = null;
    }

    @Override
    public WasherRecipe getRecipe(int cacheIndex) {
        return getRecipe();
    }

    @Override
    public GasAndFluidInput getInput() {
        return new GasAndFluidInput(inputTank.getGas(), fluidTank.getFluid());
    }

    public MachineEnergyContainer getEnergyContainer() {
        return getMainEnergyContainer();
    }

    public boolean usedEnergy() {
        return clientEnergyUsed > 0;
    }

    public double getEnergyUsed() {
        return clientEnergyUsed;
    }

    public boolean hasWarningNoMatchingFluidInput() {
        if (fluidTank.getFluid() == null) {
            return inputTank.getGas() != null;
        }
        WasherRecipe recipe = getRecipe();
        if (recipe == null) {
            return inputTank.getGas() != null;
        }
        return fluidTank.getFluidAmount() < recipe.getInput().ingredientFluid.amount;
    }

    public boolean hasWarningNoMatchingGasInput() {
        if (inputTank.getGas() == null) {
            return fluidTank.getFluid() != null;
        }
        WasherRecipe recipe = getRecipe();
        if (recipe == null) {
            return fluidTank.getFluid() != null;
        }
        return inputTank.getStored() < recipe.getInput().ingredientGas.amount;
    }

    public boolean hasWarningNoSpaceInOutput() {
        GasStack output = getCurrentOutput();
        return output != null && outputTank.canReceiveType(output.getGas()) && outputTank.getNeeded() < output.amount;
    }

    public boolean hasWarningInputDoesntProduceOutput() {
        GasStack output = getCurrentOutput();
        return output != null && !outputTank.canReceiveType(output.getGas());
    }

    @Override
    public CachedRecipe<WasherRecipe> createNewCachedRecipe(WasherRecipe recipe, int cacheIndex) {
        return new TwoInputCachedRecipe<>(recipe, this::shouldRecheckAllRecipeErrors,
              InputHelper.getGasInputHandler(inputTank, RecipeError.NOT_ENOUGH_INPUT),
              InputHelper.getFluidInputHandler(fluidTank, RecipeError.NOT_ENOUGH_SECONDARY_INPUT),
              OutputHelper.getGasOutputHandler(outputTank, RecipeError.NOT_ENOUGH_OUTPUT_SPACE),
              () -> recipe.getInput().ingredientGas, () -> recipe.getInput().ingredientFluid,
              (gas, fluid) -> gas != null && gas.isGasEqual(recipe.getInput().ingredientGas)
                    && fluid != null && fluid.isFluidEqual(recipe.getInput().ingredientFluid),
              (gas, fluid) -> recipe.getOutput().output.copy(), gas -> gas == null || gas.amount <= 0,
              fluid -> fluid == null || fluid.amount <= 0, output -> output == null || output.amount <= 0)
              .setCanHolderFunction(() -> MekanismUtils.canFunction(this))
              .setActive(active -> {
                  if (active || prevEnergy >= getEnergy()) {
                      setActive(active);
                  }
              })
              .setEnergyRequirements(() -> energyPerTick, getMainEnergyContainer())
              .setRequiredTicks(() -> ticksRequired)
              .setBaselineMaxOperations(() -> getUpgradedUsage(recipe))
              .setOperatingTicksChanged(ticks -> operatingTicks = ticks)
              .setErrorsChanged(this::onRecipeErrorsChanged)
              .setOnFinish(this::onCachedRecipeFinish);
    }

    @Override
    public Map<GasAndFluidInput, WasherRecipe> getRecipes() {
        return Recipe.CHEMICAL_WASHER.get();
    }

    private void manageBuckets() {
        if (fluidTank.getFluidAmount() != fluidTank.getCapacity()) {
            inputSlot.fillTank(outputSlot);
        }
    }


    public int getUpgradedUsage(WasherRecipe recipe) {
        int possibleProcess = Math.min((int) Math.pow(2, upgradeComponent.getUpgrades(Upgrade.SPEED)), MekanismConfig.current().mekce.MAXspeedmachines.val());
        possibleProcess = Math.min(Math.min(inputTank.getStored(), outputTank.getNeeded()), possibleProcess);
        possibleProcess = Math.min((int) (getEnergy() / energyPerTick), possibleProcess);
        possibleProcess = Math.max(possibleProcess, 1);
        return Math.min(fluidTank.getFluidAmount() / recipe.recipeInput.ingredientFluid.amount, possibleProcess);
    }

    private GasStack getCurrentOutput() {
        WasherRecipe recipe = getRecipe();
        if (recipe == null || recipe.getOutput().output == null) {
            return null;
        }
        return recipe.getOutput().output;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            clientEnergyUsed = dataStream.readDouble();
            TileUtils.readTankData(dataStream, fluidTank);
            TileUtils.readTankData(dataStream, inputTank);
            TileUtils.readTankData(dataStream, outputTank);
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(clientEnergyUsed);
        TileUtils.addTankData(data, fluidTank);
        TileUtils.addTankData(data, inputTank);
        TileUtils.addTankData(data, outputTank);
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (!hasStoredFluidTanks(nbtTags) && nbtTags.hasKey("leftTank")) {
            fluidTank.readFromNBT(nbtTags.getCompoundTag("leftTank"));
        }
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("rightTank")) {
            inputTank.read(nbtTags.getCompoundTag("rightTank"));
        }
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("centerTank")) {
            outputTank.read(nbtTags.getCompoundTag("centerTank"));
        }
        sanitizeAndClampTanks();
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        writeSustainedFluidTanks(itemStack);
        writeSustainedGasTanks(itemStack);
        ItemDataUtils.setLegacyFluid(itemStack, "fluidTank", fluidTank.getFluid());
        ItemDataUtils.setLegacyGas(itemStack, "inputTank", inputTank.getGas());
        ItemDataUtils.setLegacyGas(itemStack, "outputTank", outputTank.getGas());
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (!readSustainedFluidTanks(itemStack)) {
            fluidTank.setStackUnchecked(ItemDataUtils.getLegacyFluid(itemStack, "fluidTank"));
        }
        if (!readSustainedGasTanks(itemStack)) {
            inputTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "inputTank"));
            outputTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "outputTank"));
        }
        sanitizeAndClampTanks();
    }

    private void sanitizeAndClampTanks() {
        sanitizeAndClampTank(fluidTank);
        sanitizeAndClampTank(inputTank);
        sanitizeAndClampTank(outputTank);
    }

    private void sanitizeAndClampTank(BasicFluidTank tank) {
        FluidStack stored = tank.getFluid();
        if (stored != null && (stored.amount <= 0 || stored.getFluid() == null)) {
            tank.setEmpty();
        } else if (stored != null) {
            tank.setStackSize(stored.amount, Action.EXECUTE);
        }
    }

    private void sanitizeAndClampTank(BasicGasTank tank) {
        GasStack stored = tank.getGas();
        if (stored != null && (stored.amount <= 0 || stored.getGas() == null)) {
            tank.setEmpty();
        } else if (stored != null) {
            tank.setStackSize(stored.amount, Action.EXECUTE);
        }
    }

    @Override
    public List<String> getInfo(Upgrade upgrade) {
        return upgrade == Upgrade.SPEED ? upgrade.getExpScaledInfo(this) : upgrade.getMultScaledInfo(this);
    }

    @Override
    public Object[] getManagedTanks() {
        return new Object[]{fluidTank, inputTank, outputTank};
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(inputTank.getStored(), inputTank.getMaxGas());
    }

    @Override
    public String[] getMethods() {
        return new String[0];
    }

    @Override
    public Object[] invoke(int method, Object[] args) throws NoSuchMethodException {
        return new Object[0];
    }
}
