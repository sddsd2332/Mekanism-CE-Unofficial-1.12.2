package mekanism.common.tile.machine;

import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
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
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.cache.CachedRecipe;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.cache.OneInputCachedRecipe;
import mekanism.common.recipe.cache.inputs.InputHelper;
import mekanism.common.recipe.cache.outputs.OutputHelper;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.machines.NutritionalRecipe;
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
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TileEntityNutritionalLiquifier extends TileEntityBasicMachine<ItemStackInput, GasOutput, NutritionalRecipe> implements ISustainedData, ITankManager, ISpecialSelectionWireframeTile {

    private static final List<RecipeError> TRACKED_ERROR_TYPES = Arrays.asList(
          RecipeError.NOT_ENOUGH_ENERGY,
          RecipeError.NOT_ENOUGH_INPUT,
          RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );
    public static final int MAX_GAS = 10000;
    public BasicGasTank gasTank;
    public NutritionalRecipe cachedRecipe;
    public float prevScale;
    public int updateDelay;
    public boolean needsPacket;
    private InputInventorySlot inputSlot;
    private EnergyInventorySlot energySlot;
    private GasInventorySlot gasSlot;

    public TileEntityNutritionalLiquifier() {
        super("oxidizer", MachineType.NUTRITIONAL_LIQUIFIER, 3, 100, TRACKED_ERROR_TYPES);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.GAS, TransmissionType.ENERGY);
        initializeInventorySlots();
        configComponent.setupItemIOConfig(inputSlot, gasSlot, energySlot);
        configComponent.setConfig(TransmissionType.ITEM, DataType.ENERGY, DataType.ENERGY, DataType.INPUT, DataType.ENERGY, DataType.INPUT, DataType.OUTPUT);
        configComponent.setCanEject(TransmissionType.ITEM, false);
        configComponent.setupOutputConfig(TransmissionType.GAS, gasTank, RelativeSide.RIGHT);
        configComponent.setConfig(TransmissionType.GAS, DataType.NONE, DataType.NONE, DataType.NONE, DataType.NONE, DataType.NONE, DataType.OUTPUT);

        configComponent.setInputConfig(TransmissionType.ENERGY);
        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM);
        ejectorComponent.setOutputData(configComponent, TransmissionType.GAS);
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        inputSlot = builder.addSlot(InputInventorySlot.at(stack -> RecipeHandler.getNutritionalRecipe(new ItemStackInput(stack)) != null, getRecipeCacheListener(), 26, 36)
              .setAutoPullValidator((stack, side) -> canAutoPullInput(stack)));
        inputSlot.tracksWarnings(slot -> slot.warning(WarningType.NO_MATCHING_RECIPE, getWarningCheck(RecipeError.NOT_ENOUGH_INPUT)));
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 154, 14));
        gasSlot = builder.addSlot(GasInventorySlot.drain(gasTank, listener, 154, 55));
        gasSlot.setSlotOverlay(SlotOverlay.PLUS);
        return builder.build();
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        GasTankHelper builder = createGasTankHelper();
        builder.addTank(getOrCreateGasTank(listener));
        return builder.build();
    }

    private BasicGasTank getOrCreateGasTank(IContentsListener listener) {
        if (gasTank == null) {
            gasTank = BasicGasTank.output(MAX_GAS, getRecipeCacheChangeListener(listener));
        }
        return gasTank;
    }
@Override
    public void onUpdateClient() {
        super.onUpdateClient();
        if (updateDelay > 0) {
            updateDelay--;
            if (updateDelay == 0) {
                MekanismUtils.updateBlock(world, getPos());
            }
        }
        float targetScale = (float) (gasTank.getGas() != null ? gasTank.getGas().amount : 0) / gasTank.getMaxGas();
        if (Math.abs(prevScale - targetScale) > 0.01) {
            prevScale = (9 * prevScale + targetScale) / 10;
        }
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
        gasSlot.drainTank();
        processRecipe();
        prevEnergy = getEnergy();
        if (needsPacket) {
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
        needsPacket = false;
    }

    @Override
    public void addTileSyncTask() {
    }

    @Override
    public void setFinish() {
        markNoUpdateSync();
    }

    @Override
    public NutritionalRecipe getRecipe() {
        refreshRecipeLookupCache();
        ItemStackInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getNutritionalRecipe(getInput());
        }
        return cachedRecipe;
    }

    @Override
    protected void clearRecipeLookupCache() {
        super.clearRecipeLookupCache();
        cachedRecipe = null;
    }

    @Override
    public NutritionalRecipe getRecipe(int cacheIndex) {
        return getRecipe();
    }

    public MachineEnergyContainer getEnergyContainer() {
        return getMainEnergyContainer();
    }

    public boolean hasWarningNoSpaceInOutput() {
        if (hasWarning(RecipeError.NOT_ENOUGH_OUTPUT_SPACE)) {
            return true;
        }
        GasStack output = getCurrentOutput();
        return output != null && gasTank.canReceiveType(output.getGas()) && gasTank.getNeeded() < output.amount;
    }

    public boolean hasWarningNoMatchingInput() {
        if (hasWarning(RecipeError.NOT_ENOUGH_INPUT)) {
            return true;
        }
        return !inputSlot.isEmpty() && getRecipe() == null;
    }

    public boolean hasWarningInputDoesntProduceOutput() {
        if (hasWarning(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT)) {
            return true;
        }
        GasStack output = getCurrentOutput();
        return output != null && !gasTank.canReceiveType(output.getGas());
    }

    public ItemStackInput getInput() {
        return new ItemStackInput(inputSlot.getStack());
    }

    public boolean canOperate(NutritionalRecipe recipe) {
        return recipe != null && recipe.canOperate(inputSlot, gasTank);
    }

    private boolean canAutoPullInput(ItemStack stack) {
        NutritionalRecipe recipe = RecipeHandler.getNutritionalRecipe(new ItemStackInput(getSimulatedStackWithInsert(0, stack)));
        return recipe != null && recipe.getOutput().applyOutputs(gasTank, false, 1);
    }

    private GasStack getCurrentOutput() {
        NutritionalRecipe recipe = getRecipe();
        if (recipe == null || recipe.getOutput().output == null) {
            return null;
        }
        return recipe.getOutput().output;
    }

    @Override
    public CachedRecipe<NutritionalRecipe> createNewCachedRecipe(NutritionalRecipe recipe, int cacheIndex) {
        return new OneInputCachedRecipe<>(recipe, this::shouldRecheckAllRecipeErrors,
              InputHelper.getInputHandler(inputSlot, RecipeError.NOT_ENOUGH_INPUT),
              OutputHelper.getGasOutputHandler(gasTank, RecipeError.NOT_ENOUGH_OUTPUT_SPACE),
              () -> recipe.getInput().ingredient, input -> mekanism.common.recipe.inputs.MachineInput.inputContains(input, recipe.getInput().ingredient),
              input -> recipe.getOutput().output.copy(), ItemStack::isEmpty, output -> output == null || output.amount <= 0)
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
    public Map<ItemStackInput, NutritionalRecipe> getRecipes() {
        return RecipeHandler.Recipe.NUTRITIONAL_LIQUIFIER.get();
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            TileUtils.readTankData(dataStream, gasTank);
            if (updateDelay == 0) {
                updateDelay = MekanismConfig.current().general.UPDATE_DELAY.val();
                MekanismUtils.updateBlock(world, getPos());
            }
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
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("gasTank")) {
            gasTank.read(nbtTags.getCompoundTag("gasTank"));
        }
        sanitizeAndClampTank();
    }


    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
    }


    @Override
    public void writeSustainedData(ItemStack itemStack) {
        writeSustainedGasTanks(itemStack);
        ItemDataUtils.setLegacyGas(itemStack, "gasTank", gasTank.getGas());
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (!readSustainedGasTanks(itemStack)) {
            gasTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "gasTank"));
        }
        sanitizeAndClampTank();
    }

    private void sanitizeAndClampTank() {
        GasStack stored = gasTank.getGas();
        if (stored != null && (stored.amount <= 0 || stored.getGas() == null)) {
            gasTank.setEmpty();
        } else if (stored != null) {
            gasTank.setStackSize(stored.amount, Action.EXECUTE);
        }
    }

    @Override
    public Object[] getManagedTanks() {
        return new Object[]{gasTank};
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
        return mekanism.client.model.ModelNutritionalLiquifier.class;
    }
}
