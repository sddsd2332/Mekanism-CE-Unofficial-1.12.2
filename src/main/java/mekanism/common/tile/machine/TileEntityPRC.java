package mekanism.common.tile.machine;

import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
import mekanism.api.TileNetworkList;
import mekanism.api.gas.GasStack;
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
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.cache.CachedRecipe;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.cache.PressurizedReactionCachedRecipe;
import mekanism.common.recipe.cache.inputs.InputHelper;
import mekanism.common.recipe.cache.outputs.OutputHelper;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.inputs.PressurizedInput;
import mekanism.common.recipe.machines.PressurizedRecipe;
import mekanism.common.recipe.outputs.PressurizedOutput;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityUpgradeableMachine;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TileUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TileEntityPRC extends TileEntityUpgradeableMachine<PressurizedInput, PressurizedOutput, PressurizedRecipe> implements
        ISustainedData, ITankManager {

    public static final RecipeError NOT_ENOUGH_ITEM_INPUT_ERROR = RecipeError.create();
    public static final RecipeError NOT_ENOUGH_FLUID_INPUT_ERROR = RecipeError.create();
    public static final RecipeError NOT_ENOUGH_GAS_INPUT_ERROR = RecipeError.create();
    public static final RecipeError NOT_ENOUGH_SPACE_ITEM_OUTPUT_ERROR = RecipeError.create();
    public static final RecipeError NOT_ENOUGH_SPACE_GAS_OUTPUT_ERROR = RecipeError.create();
    private static final List<RecipeError> TRACKED_ERROR_TYPES = Arrays.asList(
          RecipeError.NOT_ENOUGH_ENERGY,
          NOT_ENOUGH_ITEM_INPUT_ERROR,
          NOT_ENOUGH_FLUID_INPUT_ERROR,
          NOT_ENOUGH_GAS_INPUT_ERROR,
          NOT_ENOUGH_SPACE_ITEM_OUTPUT_ERROR,
          NOT_ENOUGH_SPACE_GAS_OUTPUT_ERROR,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );
    private static final String[] methods = new String[]{"getEnergy", "getProgress", "isActive", "facing", "canOperate", "getMaxEnergy", "getEnergyNeeded",
            "getFluidStored", "getGasStored"};
    private static final int TANK_CAPACITY = 10_000;
    public BasicFluidTank inputFluidTank;
    public BasicGasTank inputGasTank;
    public BasicGasTank outputGasTank;
    private InputInventorySlot inputSlot;
    private EnergyInventorySlot energySlot;
    private OutputInventorySlot outputSlot;
    private final boolean[] trackedErrors = new boolean[TRACKED_ERROR_TYPES.size()];
    private double recipeEnergyRequired;

    public TileEntityPRC() {
        super("prc", MachineType.PRESSURIZED_REACTION_CHAMBER, 3, 100);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.ENERGY, TransmissionType.FLUID, TransmissionType.GAS);
        initializeInventorySlots();
        configComponent.setupItemIOConfig(inputSlot, outputSlot, energySlot);
        configComponent.setConfig(TransmissionType.ITEM, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.OUTPUT, DataType.INPUT, DataType.ENERGY);

        configComponent.setupFluidInputConfig(inputFluidTank);
        configComponent.setupIOConfig(TransmissionType.GAS, inputGasTank, outputGasTank, RelativeSide.RIGHT, false, true);
        configComponent.setConfig(TransmissionType.GAS, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.OUTPUT);

        configComponent.setInputConfig(TransmissionType.ENERGY);
        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM, TransmissionType.GAS)
              .setCanTankEject(tank -> tank != inputGasTank);
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        inputSlot = builder.addSlot(InputInventorySlot.at(RecipeHandler::isInPressurizedRecipe, getRecipeCacheListener(), 54, 40)
              .setAutoPullValidator((stack, side) -> canAutoPullInput(stack)));
        inputSlot.tracksWarnings(slot -> slot.warning(WarningType.NO_MATCHING_RECIPE, getWarningCheck(NOT_ENOUGH_ITEM_INPUT_ERROR)));
        outputSlot = builder.addSlot(OutputInventorySlot.at(getRecipeCacheChangeListener(listener), 116, 40));
        outputSlot.tracksWarnings(slot -> slot.warning(WarningType.NO_SPACE_IN_OUTPUT, getWarningCheck(NOT_ENOUGH_SPACE_ITEM_OUTPUT_ERROR)));
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 141, 22));
        return builder.build();
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        FluidTankHelper builder = createFluidTankHelper();
        builder.addTank(getOrCreateInputFluidTank());
        return builder.build();
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        GasTankHelper builder = createGasTankHelper();
        builder.addTank(getOrCreateInputGasTank());
        builder.addTank(getOrCreateOutputGasTank(listener));
        return builder.build();
    }

    private BasicFluidTank getOrCreateInputFluidTank() {
        if (inputFluidTank == null) {
            inputFluidTank = BasicFluidTank.input(TANK_CAPACITY, this::isValidFluid, getRecipeCacheListener());
        }
        return inputFluidTank;
    }

    private BasicGasTank getOrCreateInputGasTank() {
        if (inputGasTank == null) {
            inputGasTank = BasicGasTank.input(TANK_CAPACITY, gas -> RecipeHandler.Recipe.PRESSURIZED_REACTION_CHAMBER.containsRecipe(gas), getRecipeCacheListener());
        }
        return inputGasTank;
    }

    private BasicGasTank getOrCreateOutputGasTank(IContentsListener listener) {
        if (outputGasTank == null) {
            outputGasTank = BasicGasTank.output(TANK_CAPACITY, getRecipeCacheChangeListener(listener));
        }
        return outputGasTank;
    }
@Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        energySlot.fillContainerOrConvert();
        processRecipe();
        prevEnergy = getEnergy();
    }

    @Override
    public void addTileSyncTask() {
    }

    @Override
    protected void setNoFinish() {
        BASE_TICKS_REQUIRED = 100;
        recipeEnergyRequired = 0;
    }

    @Override
    public void onCachedRecipeChanged(CachedRecipe<PressurizedRecipe> cachedRecipe, int cacheIndex) {
        super.onCachedRecipeChanged(cachedRecipe, cacheIndex);
        PressurizedRecipe recipe = cachedRecipe == null ? null : cachedRecipe.getRecipe();
        int recipeTicks = recipe == null ? 100 : recipe.ticks;
        recipeEnergyRequired = recipe == null ? 0 : recipe.extraEnergy;
        boolean update = BASE_TICKS_REQUIRED != recipeTicks;
        BASE_TICKS_REQUIRED = recipeTicks;
        if (update) {
            recalculateUpgradables(Upgrade.SPEED);
        }
    }

    @Nullable
    @Override
    protected GasStack getInputGasForUpgrade() {
        return inputGasTank.getGas();
    }

    @Nullable
    @Override
    protected GasStack getOutputGasForUpgrade() {
        return outputGasTank.getGas();
    }

    @Nullable
    @Override
    protected FluidStack getInputFluidForUpgrade() {
        return inputFluidTank.getFluid();
    }

    @Nonnull
    @Override
    protected ItemStack getInputSlotForUpgrade() {
        return inputSlot.getStack();
    }

    @Nonnull
    @Override
    protected ItemStack getEnergySlotForUpgrade() {
        return energySlot.getStack();
    }

    @Nonnull
    @Override
    protected ItemStack getOutputSlotForUpgrade() {
        return outputSlot.getStack();
    }

    @Override
    public PressurizedRecipe getRecipe() {
        refreshRecipeLookupCache();
        PressurizedInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getPRCRecipe(input);
        }
        return cachedRecipe;
    }

    @Override
    public PressurizedRecipe getRecipe(int cacheIndex) {
        return getRecipe();
    }

    public MachineEnergyContainer getEnergyContainer() {
        return getMainEnergyContainer();
    }

    public InputInventorySlot getRecipeInputSlot() {
        return inputSlot;
    }

    public OutputInventorySlot getRecipeOutputSlot() {
        return outputSlot;
    }

    @Override
    protected double getMainEnergyPerTick() {
        return getRecipeEnergyPerTick();
    }

    public double getRecipeEnergyRequired() {
        return recipeEnergyRequired;
    }

    public double getRecipeEnergyPerTick() {
        return getRecipeEnergyPerTick(recipeEnergyRequired);
    }

    public double getCurrentEnergyUsage() {
        PressurizedRecipe recipe = getRecipe();
        return recipe == null ? 0 : getRecipeEnergyPerTick(recipe.extraEnergy);
    }

    private double getRecipeEnergyPerTick(double extraEnergy) {
        return MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK + extraEnergy);
    }

    public boolean hasWarningNoMatchingFluidInput() {
        if (hasWarning(NOT_ENOUGH_FLUID_INPUT_ERROR)) {
            return true;
        }
        if (inputSlot.isEmpty()) {
            return false;
        }
        if (inputFluidTank.getFluid() == null) {
            return true;
        }
        PressurizedRecipe recipe = getRecipe();
        if (recipe == null) {
            return hasMatchingSolidInput(inputSlot.getStack()) && hasMatchingGasInput(inputGasTank.getGas());
        }
        return inputFluidTank.getFluidAmount() < recipe.getInput().getFluid().amount;
    }

    public boolean hasWarningNoMatchingGasInput() {
        if (hasWarning(NOT_ENOUGH_GAS_INPUT_ERROR)) {
            return true;
        }
        if (inputSlot.isEmpty()) {
            return false;
        }
        if (inputGasTank.getGas() == null) {
            return true;
        }
        PressurizedRecipe recipe = getRecipe();
        if (recipe == null) {
            return hasMatchingSolidInput(inputSlot.getStack()) && hasMatchingFluidInput(inputFluidTank.getFluid());
        }
        return inputGasTank.getStored() < recipe.getInput().getGas().amount;
    }

    public boolean hasWarningNoMatchingItemInput() {
        return hasWarning(NOT_ENOUGH_ITEM_INPUT_ERROR) || !inputSlot.isEmpty() && getRecipe() == null;
    }

    public boolean hasWarningNoSpaceInItemOutput() {
        if (hasWarning(NOT_ENOUGH_SPACE_ITEM_OUTPUT_ERROR)) {
            return true;
        }
        PressurizedOutput output = getCurrentOutput();
        if (output == null || output.getItemOutput().isEmpty()) {
            return false;
        }
        ItemStack current = outputSlot.getStack();
        if (!current.isEmpty() && !ItemHandlerHelper.canItemStacksStack(current, output.getItemOutput())) {
            return false;
        }
        return !outputSlot.insertItem(output.getItemOutput().copy(), mekanism.api.Action.SIMULATE, mekanism.api.AutomationType.INTERNAL).isEmpty();
    }

    public boolean hasWarningNoSpaceInGasOutput() {
        if (hasWarning(NOT_ENOUGH_SPACE_GAS_OUTPUT_ERROR)) {
            return true;
        }
        PressurizedOutput output = getCurrentOutput();
        GasStack gasOutput = output == null ? null : output.getGasOutput();
        return gasOutput != null && gasOutput.amount > 0 && outputGasTank.canReceiveType(gasOutput.getGas()) && outputGasTank.getNeeded() < gasOutput.amount;
    }

    public boolean hasWarningInputDoesntProduceOutput() {
        if (hasWarning(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT)) {
            return true;
        }
        PressurizedOutput output = getCurrentOutput();
        if (output == null) {
            return false;
        }
        ItemStack itemOutput = output.getItemOutput();
        ItemStack currentItem = outputSlot.getStack();
        if (!itemOutput.isEmpty() && !currentItem.isEmpty() && !ItemHandlerHelper.canItemStacksStack(currentItem, itemOutput)) {
            return true;
        }
        GasStack gasOutput = output.getGasOutput();
        return gasOutput != null && gasOutput.amount > 0 && !outputGasTank.canReceiveType(gasOutput.getGas());
    }

    @Override
    public PressurizedInput getInput() {
        return new PressurizedInput(inputSlot.getStack(), inputFluidTank.getFluid(), inputGasTank.getGas());
    }

    @Override
    public CachedRecipe<PressurizedRecipe> createNewCachedRecipe(PressurizedRecipe recipe, int cacheIndex) {
        return new PressurizedReactionCachedRecipe(recipe, this::shouldRecheckAllRecipeErrors,
              InputHelper.getInputHandler(inputSlot, NOT_ENOUGH_ITEM_INPUT_ERROR),
              InputHelper.getFluidInputHandler(inputFluidTank, NOT_ENOUGH_FLUID_INPUT_ERROR),
              InputHelper.getGasInputHandler(inputGasTank, NOT_ENOUGH_GAS_INPUT_ERROR),
              OutputHelper.getOutputHandler(outputSlot, NOT_ENOUGH_SPACE_ITEM_OUTPUT_ERROR, outputGasTank, NOT_ENOUGH_SPACE_GAS_OUTPUT_ERROR))
              .setCanHolderFunction(() -> MekanismUtils.canFunction(this))
              .setActive(active -> {
                  if (active || prevEnergy >= getEnergy()) {
                      setActive(active);
                  }
              })
              .setEnergyRequirements(this::getRecipeEnergyPerTick, getMainEnergyContainer())
              .setRequiredTicks(() -> ticksRequired)
              .setBaselineMaxOperations(() -> getBaselineMaxOperations(getRecipeEnergyPerTick(), true))
              .setOperatingTicksChanged(ticks -> operatingTicks = ticks)
              .setErrorsChanged(errors -> {
                  for (int i = 0; i < trackedErrors.length; i++) {
                      trackedErrors[i] = errors.contains(TRACKED_ERROR_TYPES.get(i));
                  }
              })
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

    public boolean hasWarning(RecipeError error) {
        int errorIndex = TRACKED_ERROR_TYPES.indexOf(error);
        return errorIndex != -1 && trackedErrors[errorIndex];
    }

    public java.util.function.BooleanSupplier getWarningCheck(RecipeError error) {
        int errorIndex = TRACKED_ERROR_TYPES.indexOf(error);
        return errorIndex == -1 ? () -> false : () -> trackedErrors[errorIndex];
    }

    @Override
    public boolean canOperate(PressurizedRecipe recipe) {
        return recipe != null && recipe.canOperate(inputSlot, inputFluidTank, inputGasTank, outputGasTank, outputSlot);
    }

    private boolean canAutoPullInput(ItemStack stack) {
        PressurizedRecipe recipe = RecipeHandler.getPRCRecipe(new PressurizedInput(getSimulatedStackWithInsert(0, stack), inputFluidTank.getFluid(), inputGasTank.getGas()));
        return recipe != null && recipe.getOutput().applyOutputs(outputSlot, outputGasTank, false);
    }

    private boolean hasMatchingSolidInput(ItemStack stack) {
        return getRecipes().keySet().stream().anyMatch(input -> MachineInput.inputContains(stack, input.getSolid()));
    }

    private boolean hasMatchingFluidInput(FluidStack fluid) {
        return fluid != null && getRecipes().keySet().stream().anyMatch(input -> input.getFluid() != null && input.getFluid().isFluidEqual(fluid));
    }

    private boolean hasMatchingGasInput(GasStack gas) {
        return gas != null && getRecipes().keySet().stream().anyMatch(input -> input.getGas() != null && gas.isGasEqual(input.getGas()));
    }

    private PressurizedOutput getCurrentOutput() {
        PressurizedRecipe recipe = getRecipe();
        return recipe == null ? null : recipe.getOutput();
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(recipeEnergyRequired);
        TileUtils.addTankData(data, inputFluidTank);
        TileUtils.addTankData(data, inputGasTank);
        TileUtils.addTankData(data, outputGasTank);
        return data;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            recipeEnergyRequired = dataStream.readDouble();
            TileUtils.readTankData(dataStream, inputFluidTank);
            TileUtils.readTankData(dataStream, inputGasTank);
            TileUtils.readTankData(dataStream, outputGasTank);
        }
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (!hasStoredFluidTanks(nbtTags) && nbtTags.hasKey("inputFluidTank")) {
            inputFluidTank.readFromNBT(nbtTags.getCompoundTag("inputFluidTank"));
        }
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("inputGasTank")) {
            inputGasTank.read(nbtTags.getCompoundTag("inputGasTank"));
        }
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("outputGasTank")) {
            outputGasTank.read(nbtTags.getCompoundTag("outputGasTank"));
        }
        sanitizeAndClampTanks();
    }


    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);

    }


    @Override
    public Map<PressurizedInput, PressurizedRecipe> getRecipes() {
        return RecipeHandler.Recipe.PRESSURIZED_REACTION_CHAMBER.get();
    }

    private boolean isValidFluid(FluidStack fluid) {
        return fluid != null && getRecipes().keySet().stream().anyMatch(input -> input.getFluid() != null && input.getFluid().isFluidEqual(fluid));
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        return switch (method) {
            case 0 -> new Object[]{getEnergy()};
            case 1 -> new Object[]{operatingTicks};
            case 2 -> new Object[]{isActive};
            case 3 -> new Object[]{facing};
            case 4 -> new Object[]{canOperate(getRecipe())};
            case 5 -> new Object[]{getMaxEnergy()};
            case 6 -> new Object[]{getMaxEnergy() - getEnergy()};
            case 7 -> new Object[]{inputFluidTank.getFluidAmount()};
            case 8 -> new Object[]{inputGasTank.getStored()};
            default -> throw new NoSuchMethodException();
        };
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        writeSustainedFluidTanks(itemStack);
        writeSustainedGasTanks(itemStack);
        ItemDataUtils.setLegacyFluid(itemStack, "inputFluidTank", inputFluidTank.getFluid());
        ItemDataUtils.setLegacyGas(itemStack, "inputGasTank", inputGasTank.getGas());
        ItemDataUtils.setLegacyGas(itemStack, "outputGasTank", outputGasTank.getGas());
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (!readSustainedFluidTanks(itemStack)) {
            inputFluidTank.setStackUnchecked(ItemDataUtils.getLegacyFluid(itemStack, "inputFluidTank"));
        }
        if (!readSustainedGasTanks(itemStack)) {
            inputGasTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "inputGasTank"));
            outputGasTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "outputGasTank"));
        }
        sanitizeAndClampTanks();
    }

    private void sanitizeAndClampTanks() {
        sanitizeAndClampTank(inputFluidTank);
        sanitizeAndClampTank(inputGasTank);
        sanitizeAndClampTank(outputGasTank);
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
    public Object[] getManagedTanks() {
        return new Object[]{inputFluidTank, inputGasTank, outputGasTank};
    }

}
