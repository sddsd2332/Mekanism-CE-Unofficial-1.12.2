package mekanism.common.tile.machine;

import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.IContentsListener;
import mekanism.api.TileNetworkList;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTank;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.Upgrade;
import mekanism.common.Upgrade.IUpgradeInfoHandler;
import mekanism.common.base.ISustainedData;
import mekanism.common.base.ITankManager;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.capabilities.holder.gas.GasTankHelper;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.cache.CachedRecipe;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.cache.ChemicalPairCachedRecipe;
import mekanism.common.recipe.cache.inputs.InputHelper;
import mekanism.common.recipe.cache.outputs.OutputHelper;
import mekanism.common.recipe.inputs.ChemicalPairInput;
import mekanism.common.recipe.machines.ChemicalInfuserRecipe;
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
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TileEntityChemicalInfuser extends TileEntityBasicMachine<ChemicalPairInput, GasOutput, ChemicalInfuserRecipe> implements ISustainedData, IUpgradeInfoHandler,
        ITankManager {

    public static final int MAX_GAS = 10000;
    private static final List<RecipeError> TRACKED_ERROR_TYPES = Arrays.asList(
            RecipeError.NOT_ENOUGH_ENERGY,
            RecipeError.NOT_ENOUGH_ENERGY_REDUCED_RATE,
            RecipeError.NOT_ENOUGH_LEFT_INPUT,
            RecipeError.NOT_ENOUGH_RIGHT_INPUT,
            RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
            RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );
    public BasicGasTank leftTank;
    public BasicGasTank rightTank;
    public BasicGasTank centerTank;

    public ChemicalInfuserRecipe cachedRecipe;

    public double clientEnergyUsed;
    private GasInventorySlot leftSlot;
    private GasInventorySlot rightSlot;
    private GasInventorySlot centerSlot;
    private EnergyInventorySlot energySlot;

    public TileEntityChemicalInfuser() {
        super("cheminfuser", MachineType.CHEMICAL_INFUSER, 4, 1, TRACKED_ERROR_TYPES);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.GAS, TransmissionType.ENERGY);
        initializeInventorySlots();
        configComponent.setupItemDualInputOutputConfig(leftSlot, rightSlot, centerSlot, energySlot);
        configComponent.setConfig(TransmissionType.ITEM, DataType.NONE, DataType.NONE, DataType.OUTPUT, DataType.ENERGY, DataType.INPUT_1, DataType.INPUT_2);
        configComponent.setCanEject(TransmissionType.ITEM, false);
        configComponent.setupGasDualInputOutputConfig(leftTank, rightTank, centerTank);
        configComponent.setConfig(TransmissionType.GAS, DataType.NONE, DataType.NONE, DataType.OUTPUT, DataType.NONE, DataType.INPUT_1, DataType.INPUT_2);

        configComponent.setInputConfig(TransmissionType.ENERGY);
        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.GAS);
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        leftSlot = builder.addSlot(GasInventorySlot.fill(leftTank, listener, 6, 56));
        rightSlot = builder.addSlot(GasInventorySlot.fill(rightTank, listener, 154, 56));
        centerSlot = builder.addSlot(GasInventorySlot.drain(centerTank, listener, 80, 65));
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 154, 14));
        leftSlot.setSlotType(ContainerSlotType.INPUT);
        leftSlot.setSlotOverlay(SlotOverlay.MINUS);
        rightSlot.setSlotType(ContainerSlotType.INPUT);
        rightSlot.setSlotOverlay(SlotOverlay.MINUS);
        centerSlot.setSlotType(ContainerSlotType.OUTPUT);
        centerSlot.setSlotOverlay(SlotOverlay.PLUS);
        return builder.build();
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        GasTankHelper builder = createGasTankHelper();
        builder.addTank(getOrCreateLeftTank());
        builder.addTank(getOrCreateRightTank());
        builder.addTank(getOrCreateCenterTank(listener));
        return builder.build();
    }

    private BasicGasTank getOrCreateLeftTank() {
        if (leftTank == null) {
            leftTank = BasicGasTank.input(MAX_GAS, this::isValidLeftGas, getRecipeCacheListener());
        }
        return leftTank;
    }

    private BasicGasTank getOrCreateRightTank() {
        if (rightTank == null) {
            rightTank = BasicGasTank.input(MAX_GAS, this::isValidRightGas, getRecipeCacheListener());
        }
        return rightTank;
    }

    private BasicGasTank getOrCreateCenterTank(IContentsListener listener) {
        if (centerTank == null) {
            centerTank = BasicGasTank.output(MAX_GAS, getRecipeCacheChangeListener(listener));
        }
        return centerTank;
    }

    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        energySlot.fillContainerOrConvert();
        leftSlot.fillTank();
        rightSlot.fillTank();
        centerSlot.drainTank();
        clientEnergyUsed = processRecipe(getMainEnergyContainer());
        prevEnergy = getEnergy();
    }


    public int getUpgradedUsage(ChemicalInfuserRecipe recipe) {
        return Math.max(1, Math.min((int) Math.pow(2, getInstalledUpgrades(Upgrade.SPEED)), MekanismConfig.current().mekce.MAXspeedmachines.val()));
    }

    @Override
    public ChemicalPairInput getInput() {
        return new ChemicalPairInput(leftTank.getGas(), rightTank.getGas());
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

    @Override
    public ChemicalInfuserRecipe getRecipe() {
        refreshRecipeLookupCache();
        ChemicalPairInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getChemicalInfuserRecipe(getInput());
        }
        return cachedRecipe;
    }

    @Override
    protected void clearRecipeLookupCache() {
        super.clearRecipeLookupCache();
        cachedRecipe = null;
    }

    @Override
    public ChemicalInfuserRecipe getRecipe(int cacheIndex) {
        return getRecipe();
    }

    public boolean hasWarningNoMatchingLeftInput() {
        if (leftTank.getGas() == null) {
            return rightTank.getGas() != null;
        }
        ChemicalInfuserRecipe recipe = getRecipe();
        if (recipe == null) {
            return rightTank.getGas() != null;
        }
        GasStack required = getRequiredLeftInput(recipe);
        return required == null || leftTank.getStored() < required.amount;
    }

    public boolean hasWarningNoMatchingRightInput() {
        if (rightTank.getGas() == null) {
            return leftTank.getGas() != null;
        }
        ChemicalInfuserRecipe recipe = getRecipe();
        if (recipe == null) {
            return leftTank.getGas() != null;
        }
        GasStack required = getRequiredRightInput(recipe);
        return required == null || rightTank.getStored() < required.amount;
    }

    public boolean hasWarningNoSpaceInOutput() {
        GasStack output = getCurrentOutput();
        return output != null && centerTank.canReceiveType(output.getGas()) && centerTank.getNeeded() < output.amount;
    }

    public boolean hasWarningInputDoesntProduceOutput() {
        GasStack output = getCurrentOutput();
        return output != null && !centerTank.canReceiveType(output.getGas());
    }

    @Override
    public CachedRecipe<ChemicalInfuserRecipe> createNewCachedRecipe(ChemicalInfuserRecipe recipe, int cacheIndex) {
        return new ChemicalPairCachedRecipe<>(recipe, this::shouldRecheckAllRecipeErrors,
                InputHelper.getGasInputHandler(leftTank, CachedRecipe.OperationTracker.RecipeError.NOT_ENOUGH_LEFT_INPUT),
                InputHelper.getGasInputHandler(rightTank, CachedRecipe.OperationTracker.RecipeError.NOT_ENOUGH_RIGHT_INPUT),
                OutputHelper.getGasOutputHandler(centerTank, CachedRecipe.OperationTracker.RecipeError.NOT_ENOUGH_OUTPUT_SPACE), recipe.getInput(), recipe.getOutput())
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
    public Map<ChemicalPairInput, ChemicalInfuserRecipe> getRecipes() {
        return RecipeHandler.Recipe.CHEMICAL_INFUSER.get();
    }

    private boolean isValidLeftGas(Gas gas) {
        return isValidGas(gas, rightTank);
    }

    private boolean isValidRightGas(Gas gas) {
        return isValidGas(gas, leftTank);
    }

    private boolean isValidGas(Gas gas, GasTank otherTank) {
        if (gas == null) {
            return false;
        }
        Gas otherGas = otherTank == null ? null : otherTank.getGasType();
        for (ChemicalPairInput input : getRecipes().keySet()) {
            if (otherGas == null && (input.leftGas.getGas() == gas || input.rightGas.getGas() == gas)) {
                return true;
            } else if (otherGas != null && ((input.leftGas.getGas() == gas && input.rightGas.getGas() == otherGas) ||
                    (input.rightGas.getGas() == gas && input.leftGas.getGas() == otherGas))) {
                return true;
            }
        }
        return false;
    }

    private GasStack getRequiredLeftInput(ChemicalInfuserRecipe recipe) {
        GasStack leftRecipe = recipe.getInput().leftGas;
        GasStack rightRecipe = recipe.getInput().rightGas;
        GasStack leftStored = leftTank.getGas();
        if (leftStored != null) {
            if (leftRecipe != null && leftStored.isGasEqual(leftRecipe)) {
                return leftRecipe;
            } else if (rightRecipe != null && leftStored.isGasEqual(rightRecipe)) {
                return rightRecipe;
            }
        }
        return leftRecipe;
    }

    private GasStack getRequiredRightInput(ChemicalInfuserRecipe recipe) {
        GasStack leftRecipe = recipe.getInput().leftGas;
        GasStack rightRecipe = recipe.getInput().rightGas;
        GasStack rightStored = rightTank.getGas();
        if (rightStored != null) {
            if (rightRecipe != null && rightStored.isGasEqual(rightRecipe)) {
                return rightRecipe;
            } else if (leftRecipe != null && rightStored.isGasEqual(leftRecipe)) {
                return leftRecipe;
            }
        }
        return rightRecipe;
    }

    private GasStack getCurrentOutput() {
        ChemicalInfuserRecipe recipe = getRecipe();
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
            TileUtils.readTankData(dataStream, leftTank);
            TileUtils.readTankData(dataStream, rightTank);
            TileUtils.readTankData(dataStream, centerTank);
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(clientEnergyUsed);
        TileUtils.addTankData(data, leftTank);
        TileUtils.addTankData(data, rightTank);
        TileUtils.addTankData(data, centerTank);
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("leftTank")) {
            leftTank.read(nbtTags.getCompoundTag("leftTank"));
        }
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("rightTank")) {
            rightTank.read(nbtTags.getCompoundTag("rightTank"));
        }
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("centerTank")) {
            centerTank.read(nbtTags.getCompoundTag("centerTank"));
        }
        sanitizeAndClampTanks();
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        writeSustainedGasTanks(itemStack);
        ItemDataUtils.setLegacyGas(itemStack, "leftTank", leftTank.getGas());
        ItemDataUtils.setLegacyGas(itemStack, "rightTank", rightTank.getGas());
        ItemDataUtils.setLegacyGas(itemStack, "centerTank", centerTank.getGas());
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (!readSustainedGasTanks(itemStack)) {
            leftTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "leftTank"));
            rightTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "rightTank"));
            centerTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "centerTank"));
        }
        sanitizeAndClampTanks();
    }

    private void sanitizeAndClampTanks() {
        sanitizeAndClampTank(leftTank);
        sanitizeAndClampTank(rightTank);
        sanitizeAndClampTank(centerTank);
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
        return new Object[]{leftTank, rightTank, centerTank};
    }


    @Override
    public String[] getMethods() {
        return new String[0];
    }

    @Override
    public Object[] invoke(int method, Object[] args) throws NoSuchMethodException {
        return new Object[0];
    }

    @Override
    protected boolean shouldDumpRadiation() {
        return true;
    }
}
