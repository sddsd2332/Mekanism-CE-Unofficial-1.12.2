package mekanism.common.tile.machine;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.gas.GasStack;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.Mekanism;
import mekanism.common.Upgrade;
import mekanism.common.Upgrade.IUpgradeInfoHandler;
import mekanism.common.base.IBoundingBlock;
import mekanism.common.base.ISpecialSelectionWireframeTile;
import mekanism.common.base.ISustainedData;
import mekanism.common.base.ITankManager;
import mekanism.common.block.states.BlockStateMachine;
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
import mekanism.common.recipe.cache.OneInputCachedRecipe;
import mekanism.common.recipe.cache.inputs.InputHelper;
import mekanism.common.recipe.cache.outputs.OutputHelper;
import mekanism.common.recipe.inputs.GasInput;
import mekanism.common.recipe.machines.IsotopicRecipe;
import mekanism.common.recipe.outputs.GasOutput;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityBasicMachine;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TileUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TileEntityIsotopicCentrifuge extends TileEntityBasicMachine<GasInput, GasOutput, IsotopicRecipe> implements ISustainedData, IBoundingBlock, IUpgradeInfoHandler, ITankManager, ISpecialSelectionWireframeTile {
    private static final ISpecialSelectionWireframeTile.SelectionTransform[] SELECTION_ROTATE_SOUTH = {
            ISpecialSelectionWireframeTile.SelectionTransform.rotateY(180, 0.5D, 0.5D, 0.5D)
    };
    private static final ISpecialSelectionWireframeTile.SelectionTransform[] SELECTION_ROTATE_WEST = {
            ISpecialSelectionWireframeTile.SelectionTransform.rotateY(90, 0.5D, 0.5D, 0.5D)
    };
    private static final ISpecialSelectionWireframeTile.SelectionTransform[] SELECTION_ROTATE_EAST = {
            ISpecialSelectionWireframeTile.SelectionTransform.rotateY(270, 0.5D, 0.5D, 0.5D)
    };

    public static final int MAX_GAS = 10000;
    private static final List<RecipeError> TRACKED_ERROR_TYPES = Arrays.asList(
          RecipeError.NOT_ENOUGH_ENERGY,
          RecipeError.NOT_ENOUGH_ENERGY_REDUCED_RATE,
          RecipeError.NOT_ENOUGH_INPUT,
          RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );
    public BasicGasTank inputTank;
    public BasicGasTank outputTank;
    public IsotopicRecipe cachedRecipe;
    public double clientEnergyUsed;
    private int currentRedstoneLevel;
    public float prevScale;
    public int updateDelay;
    public boolean needsPacket;
    private GasInventorySlot inputSlot;
    private GasInventorySlot outputSlot;
    private EnergyInventorySlot energySlot;

    public TileEntityIsotopicCentrifuge() {
        super("washer", BlockStateMachine.MachineType.ISOTOPIC_CENTRIFUGE, 3, 1, TRACKED_ERROR_TYPES);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.ENERGY, TransmissionType.GAS);
        initializeInventorySlots();
        configComponent.setupItemIOConfig(inputSlot, outputSlot, energySlot);
        configComponent.setConfig(TransmissionType.ITEM, DataType.INPUT, DataType.EMPTY, DataType.OUTPUT, DataType.ENERGY, DataType.INPUT, DataType.INPUT);
        configComponent.setCanEject(TransmissionType.ITEM, false);
        configComponent.setupIOConfig(TransmissionType.GAS, inputTank, outputTank, RelativeSide.FRONT, false, true);
        configComponent.setConfig(TransmissionType.GAS, DataType.INPUT, DataType.EMPTY, DataType.OUTPUT, DataType.INPUT, DataType.INPUT, DataType.INPUT);
        configComponent.setupInputConfig(TransmissionType.ENERGY, this);
        configComponent.setConfig(TransmissionType.ENERGY, DataType.INPUT, DataType.EMPTY, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.INPUT);
        configComponent.setCanEject(TransmissionType.ENERGY, false);

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM, TransmissionType.GAS)
              .setCanTankEject(tank -> tank != inputTank);
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        inputSlot = builder.addSlot(GasInventorySlot.fill(inputTank, listener, 5, 56));
        outputSlot = builder.addSlot(GasInventorySlot.drain(outputTank, listener, 155, 56));
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 155, 14));
        inputSlot.setSlotType(ContainerSlotType.INPUT);
        inputSlot.setSlotOverlay(SlotOverlay.MINUS);
        outputSlot.setSlotType(ContainerSlotType.OUTPUT);
        outputSlot.setSlotOverlay(SlotOverlay.PLUS);
        return builder.build();
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        GasTankHelper builder = createGasTankHelper();
        builder.addTank(getOrCreateInputTank());
        builder.addTank(getOrCreateOutputTank(listener));
        return builder.build();
    }

    private BasicGasTank getOrCreateInputTank() {
        if (inputTank == null) {
            inputTank = BasicGasTank.input(MAX_GAS, gas -> RecipeHandler.Recipe.ISOTOPIC_CENTRIFUGE.containsRecipe(gas), getRecipeCacheListener());
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
        if (updateDelay > 0) {
            updateDelay--;
            if (updateDelay == 0) {
                needsPacket = true;
            }
        }
        energySlot.fillContainerOrConvert();
        inputSlot.fillTank();
        outputSlot.drainTank();
        clientEnergyUsed = processRecipe(getMainEnergyContainer());
        prevEnergy = getEnergy();
        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            updateComparatorOutputLevelSync();
            currentRedstoneLevel = newRedstoneLevel;
        }
        if (needsPacket) {
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
        needsPacket = false;
    }

    @Override
    public void onUpdateClient() {
        if (updateDelay > 0) {
            updateDelay--;
            if (updateDelay == 0) {
                MekanismUtils.updateBlock(world, getPos());
            }
        }
        float targetScale = (float) (outputTank.getGas() != null ? outputTank.getGas().amount : 0) / outputTank.getMaxGas();
        if (Math.abs(prevScale - targetScale) > 0.01) {
            prevScale = (9 * prevScale + targetScale) / 10;
        }
    }


    @Override
    public IsotopicRecipe getRecipe() {
        refreshRecipeLookupCache();
        GasInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getIsotopicRecipe(getInput());
        }
        return cachedRecipe;
    }

    @Override
    protected void clearRecipeLookupCache() {
        super.clearRecipeLookupCache();
        cachedRecipe = null;
    }

    @Override
    public IsotopicRecipe getRecipe(int cacheIndex) {
        return getRecipe();
    }

    @Override
    public GasInput getInput() {
        return new GasInput(inputTank.getGas());
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

    public boolean hasWarningNoMatchingRecipe() {
        return inputTank.getGas() != null && getRecipe() == null;
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
    public CachedRecipe<IsotopicRecipe> createNewCachedRecipe(IsotopicRecipe recipe, int cacheIndex) {
        return new OneInputCachedRecipe<>(recipe, this::shouldRecheckAllRecipeErrors,
              InputHelper.getGasInputHandler(inputTank, RecipeError.NOT_ENOUGH_INPUT),
              OutputHelper.getGasOutputHandler(outputTank, RecipeError.NOT_ENOUGH_OUTPUT_SPACE),
              () -> recipe.getInput().ingredient, input -> input != null && input.isGasEqual(recipe.getInput().ingredient),
              input -> recipe.getOutput().output.copy(), input -> input == null || input.amount <= 0, output -> output == null || output.amount <= 0)
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
    public Map<GasInput, IsotopicRecipe> getRecipes() {
        return RecipeHandler.Recipe.ISOTOPIC_CENTRIFUGE.get();
    }

    public int getUpgradedUsage(IsotopicRecipe recipe) {
        int possibleProcess = Math.min((int) Math.pow(2, upgradeComponent.getUpgrades(Upgrade.SPEED)), MekanismConfig.current().mekce.MAXspeedmachines.val());
        possibleProcess = Math.min(Math.min(inputTank.getStored(), outputTank.getNeeded()), possibleProcess);
        possibleProcess = Math.min((int) (getEnergy() / energyPerTick), possibleProcess);
        possibleProcess = Math.max(possibleProcess,1);
        return Math.min(inputTank.getStored() / recipe.recipeInput.ingredient.amount, possibleProcess);
    }

    private GasStack getCurrentOutput() {
        IsotopicRecipe recipe = getRecipe();
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
            TileUtils.readTankData(dataStream, inputTank);
            TileUtils.readTankData(dataStream, outputTank);
            if (updateDelay == 0) {
                updateDelay = MekanismConfig.current().general.UPDATE_DELAY.val();
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(clientEnergyUsed);
        TileUtils.addTankData(data, inputTank);
        TileUtils.addTankData(data, outputTank);
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("inputTank")) {
            inputTank.read(nbtTags.getCompoundTag("inputTank"));
        }
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("outputTank")) {
            outputTank.read(nbtTags.getCompoundTag("outputTank"));
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
        ItemDataUtils.setLegacyGas(itemStack, "inputTank", inputTank.getGas());
        ItemDataUtils.setLegacyGas(itemStack, "outputTank", outputTank.getGas());
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (!readSustainedGasTanks(itemStack)) {
            inputTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "inputTank"));
            outputTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "outputTank"));
        }
        sanitizeAndClampTanks();
    }

    private void sanitizeAndClampTanks() {
        sanitizeAndClampTank(inputTank);
        sanitizeAndClampTank(outputTank);
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
        return new Object[]{inputTank, outputTank};
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(inputTank.getStored(), inputTank.getMaxGas());
    }

    @Override
    public void collectBoundingBlocks(java.util.function.BiConsumer<BlockPos, Boolean> consumer) {
        consumer.accept(getPos().up(), false);
    }

    @Override
    public void onPlace() {
        tryPlaceBoundingBlocks(world, Coord4D.get(this));
    }

    @Override
    public void onBreak() {
        removeBoundingBlocks(world, getPos());
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
    public void setActive(boolean active) {
        super.setActive(active);
        if (updateDelay == 0) {
            Mekanism.packetHandler.sendUpdatePacket(this);
            updateDelay = 10;
        }
    }
@Override
    protected boolean shouldDumpRadiation() {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Class<?> getSelectionWireframeModelClass() {
        return mekanism.client.model.ModelIsotopicCentrifuge.class;
    }

    @Override
    public boolean shouldApplyDefaultSelectionWireframeFacingRotation(IBlockState state, IBlockAccess world, BlockPos pos) {
        return false;
    }

    @Override
    public ISpecialSelectionWireframeTile.SelectionTransform[] getSelectionWireframeTransforms(IBlockState state, IBlockAccess world, BlockPos pos) {
        EnumFacing currentFacing = facing == null ? EnumFacing.NORTH : facing;
        return switch (currentFacing) {
            case SOUTH -> SELECTION_ROTATE_SOUTH;
            case WEST -> SELECTION_ROTATE_WEST;
            case EAST -> SELECTION_ROTATE_EAST;
            default -> SelectionTransform.EMPTY;
        };
    }
}
