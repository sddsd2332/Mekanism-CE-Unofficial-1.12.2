package mekanism.common.tile.machine;

import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.TileNetworkList;
import mekanism.api.gas.GasStack;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.Mekanism;
import mekanism.common.base.ISpecialSelectionWireframeTile;
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
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.cache.CachedRecipe;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.cache.OneInputCachedRecipe;
import mekanism.common.recipe.cache.inputs.InputHelper;
import mekanism.common.recipe.cache.outputs.OutputHelper;
import mekanism.common.recipe.inputs.GasInput;
import mekanism.common.recipe.machines.CrystallizerRecipe;
import mekanism.common.recipe.outputs.ItemStackOutput;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityBasicMachine;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TileUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

public class TileEntityChemicalCrystallizer extends TileEntityBasicMachine<GasInput, ItemStackOutput, CrystallizerRecipe> implements ISustainedData, ITankManager, ISpecialSelectionWireframeTile {

    private static final List<RecipeError> TRACKED_ERROR_TYPES = Arrays.asList(
          RecipeError.NOT_ENOUGH_ENERGY,
          RecipeError.NOT_ENOUGH_INPUT,
          RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );
    public static final int MAX_GAS = 10000;
    public BasicGasTank inputTank;
    public CrystallizerRecipe cachedRecipe;
    public float prevScale;
    public int updateDelay;
    public boolean needsPacket;
    private GasInventorySlot inputSlot;
    private OutputInventorySlot outputSlot;
    private EnergyInventorySlot energySlot;
    private final boolean[] trackedErrors = new boolean[TRACKED_ERROR_TYPES.size()];

    public TileEntityChemicalCrystallizer() {
        super("crystallizer", MachineType.CHEMICAL_CRYSTALLIZER, 3, 200);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.ENERGY, TransmissionType.GAS);
        initializeInventorySlots();
        configComponent.setupItemIOConfig(inputSlot, outputSlot, energySlot);
        configComponent.setConfig(TransmissionType.ITEM, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.ENERGY, DataType.INPUT, DataType.OUTPUT);

        configComponent.setupInputConfig(TransmissionType.GAS, inputTank);

        configComponent.setInputConfig(TransmissionType.ENERGY);
        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM);
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        inputSlot = builder.addSlot(GasInventorySlot.fill(inputTank, listener, 8, 65));
        outputSlot = builder.addSlot(OutputInventorySlot.at(getRecipeCacheChangeListener(listener), 129, 57));
        outputSlot.tracksWarnings(slot -> slot.warning(WarningType.NO_SPACE_IN_OUTPUT, getWarningCheck(RecipeError.NOT_ENOUGH_OUTPUT_SPACE)));
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 152, 5));
        inputSlot.setSlotOverlay(SlotOverlay.PLUS);
        return builder.build();
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        GasTankHelper builder = createGasTankHelper();
        builder.addTank(getOrCreateInputTank());
        return builder.build();
    }

    private BasicGasTank getOrCreateInputTank() {
        if (inputTank == null) {
            inputTank = BasicGasTank.input(MAX_GAS, gas -> Recipe.CHEMICAL_CRYSTALLIZER.containsRecipe(gas), getRecipeCacheListener());
        }
        return inputTank;
    }

    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        tickUpdateDelay();
        energySlot.fillContainerOrConvert();
        inputSlot.fillTank();
        processRecipe();
        prevEnergy = getEnergy();
        sendPendingUpdate();
    }

    private void tickUpdateDelay() {
        if (updateDelay > 0) {
            updateDelay--;
            if (updateDelay == 0) {
                needsPacket = true;
            }
        }
    }

    private void sendPendingUpdate() {
        if (needsPacket) {
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
        needsPacket = false;
    }

    @Override
    protected boolean supportsAsyncIdleSkipping() {
        return getClass() == TileEntityChemicalCrystallizer.class;
    }

    @Override
    protected boolean isAsyncUpdateIdle() {
        return inputTank.isEmpty() && inputSlot.isEmpty() && energySlot.isEmpty() && isEmptyRecipeStateSettled();
    }

    @Override
    protected void onAsyncUpdateSkipped() {
        tickUpdateDelay();
        setActive(false);
        sendPendingUpdate();
    }

    @Override
    public void onUpdateClient() {
        if (updateDelay > 0) {
            updateDelay--;
            if (updateDelay == 0) {
                MekanismUtils.updateBlock(world, getPos());
            }
        }
        float targetScale = (float) (inputTank.getGas() != null ? inputTank.getGas().amount : 0) / inputTank.getMaxGas();
        if (Math.abs(prevScale - targetScale) > 0.01) {
            prevScale = (9 * prevScale + targetScale) / 10;
        }
    }

    public GasInput getInput() {
        return new GasInput(inputTank.getGas());
    }

    public CrystallizerRecipe getRecipe() {
        refreshRecipeLookupCache();
        GasInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getChemicalCrystallizerRecipe(getInput());
        }
        return cachedRecipe;
    }

    @Override
    protected void clearRecipeLookupCache() {
        super.clearRecipeLookupCache();
        cachedRecipe = null;
    }

    @Override
    public CrystallizerRecipe getRecipe(int cacheIndex) {
        return getRecipe();
    }

    public MachineEnergyContainer getEnergyContainer() {
        return getMainEnergyContainer();
    }

    public OutputInventorySlot getRecipeOutputSlot() {
        return outputSlot;
    }

    public int getEnergySlotX() {
        return energySlot.getGuiX();
    }

    public boolean hasWarningNoMatchingRecipe() {
        return hasWarning(RecipeError.NOT_ENOUGH_INPUT) || inputTank.getGas() != null && getRecipe() == null;
    }

    public boolean hasWarningNoSpaceInOutput() {
        if (hasWarning(RecipeError.NOT_ENOUGH_OUTPUT_SPACE)) {
            return true;
        }
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
        if (hasWarning(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT)) {
            return true;
        }
        ItemStack output = getCurrentOutput();
        ItemStack current = outputSlot.getStack();
        return !output.isEmpty() && !current.isEmpty() && !ItemHandlerHelper.canItemStacksStack(current, output);
    }

    public boolean canOperate(CrystallizerRecipe recipe) {
        return recipe != null && recipe.canOperate(inputTank, outputSlot);
    }

    @Override
    public CachedRecipe<CrystallizerRecipe> createNewCachedRecipe(CrystallizerRecipe recipe, int cacheIndex) {
        return new OneInputCachedRecipe<>(recipe, this::shouldRecheckAllRecipeErrors,
              InputHelper.getGasInputHandler(inputTank, RecipeError.NOT_ENOUGH_INPUT),
              OutputHelper.getOutputHandler(outputSlot, RecipeError.NOT_ENOUGH_OUTPUT_SPACE),
              () -> recipe.getInput().ingredient, input -> input != null && input.isGasEqual(recipe.getInput().ingredient),
              input -> recipe.getOutput().output.copy(), input -> input == null || input.amount <= 0, ItemStack::isEmpty)
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

    @Override
    public void clearRecipeErrors(int cacheIndex) {
        Arrays.fill(trackedErrors, false);
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.trackArray(trackedErrors);
    }

    protected void onRecipeErrorsChanged(Set<RecipeError> errors) {
        for (int i = 0; i < trackedErrors.length; i++) {
            trackedErrors[i] = errors.contains(TRACKED_ERROR_TYPES.get(i));
        }
    }

    public boolean hasWarning(RecipeError error) {
        int errorIndex = TRACKED_ERROR_TYPES.indexOf(error);
        return errorIndex != -1 && trackedErrors[errorIndex];
    }

    public BooleanSupplier getWarningCheck(RecipeError error) {
        int errorIndex = TRACKED_ERROR_TYPES.indexOf(error);
        return errorIndex == -1 ? () -> false : () -> trackedErrors[errorIndex];
    }

    private ItemStack getCurrentOutput() {
        CrystallizerRecipe recipe = getRecipe();
        if (recipe == null || recipe.getOutput().output.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return recipe.getOutput().output;
    }

    @Override
    public Map<GasInput, CrystallizerRecipe> getRecipes() {
        return Recipe.CHEMICAL_CRYSTALLIZER.get();
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            TileUtils.readTankData(dataStream, inputTank);
            if (updateDelay == 0) {
                updateDelay = MekanismConfig.current().general.UPDATE_DELAY.val();
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        TileUtils.addTankData(data, inputTank);
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("rightTank")) {
            inputTank.read(nbtTags.getCompoundTag("rightTank"));
        }
        sanitizeAndClampTank();
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setBoolean("sideDataStored", true);
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
    public void writeSustainedData(ItemStack itemStack) {
        writeSustainedGasTanks(itemStack);
        ItemDataUtils.setLegacyGas(itemStack, "inputTank", inputTank.getGas());
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (!readSustainedGasTanks(itemStack)) {
            inputTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "inputTank"));
        }
        sanitizeAndClampTank();
    }

    private void sanitizeAndClampTank() {
        GasStack stored = inputTank.getGas();
        if (stored != null && (stored.amount <= 0 || stored.getGas() == null)) {
            inputTank.setEmpty();
        } else if (stored != null) {
            inputTank.setStackSize(stored.amount, Action.EXECUTE);
        }
    }


    @Override
    public Object[] getManagedTanks() {
        return new Object[]{inputTank};
    }

    public int getScaledFuelLevel(int i) {
        return inputTank.getStored() * i / inputTank.getMaxGas();
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
    @SideOnly(Side.CLIENT)
    public Class<?> getSelectionWireframeModelClass() {
        return mekanism.client.model.ModelChemicalCrystallizer.class;
    }
}
