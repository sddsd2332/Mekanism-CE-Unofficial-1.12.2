package mekanism.common.tile.machine;

import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.TileNetworkList;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.Upgrade;
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
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.cache.CachedRecipe;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.cache.NucleosynthesizerRecipeCacheLookupMonitor;
import mekanism.common.recipe.cache.RecipeLaneCommitTarget;
import mekanism.common.recipe.cache.RecipeRunSnapshot;
import mekanism.common.recipe.cache.RecipeExecutionPlan;
import mekanism.common.recipe.cache.TwoInputCachedRecipe;
import mekanism.common.recipe.cache.inputs.InputHelper;
import mekanism.common.recipe.cache.outputs.OutputHelper;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.inputs.NucleosynthesizerInput;
import mekanism.common.recipe.machines.NucleosynthesizerRecipe;
import mekanism.common.recipe.outputs.ItemStackOutput;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityUpgradeableMachine;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TileUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TileEntityAntiprotonicNucleosynthesizer extends TileEntityUpgradeableMachine<NucleosynthesizerInput, ItemStackOutput, NucleosynthesizerRecipe> implements ISustainedData, ISpecialSelectionWireframeTile, ITankManager {

    private static final List<RecipeError> TRACKED_ERROR_TYPES = Arrays.asList(
          RecipeError.NOT_ENOUGH_ENERGY,
          RecipeError.NOT_ENOUGH_INPUT,
          RecipeError.NOT_ENOUGH_SECONDARY_INPUT,
          RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );
    private static final String[] methods = new String[]{"getEnergy", "getProgress", "isActive", "facing", "canOperate", "getMaxEnergy", "getEnergyNeeded",
            "getGasStored"};
    public BasicGasTank inputGasTank;
    private GasInventorySlot gasInputSlot;
    private InputInventorySlot inputSlot;
    private EnergyInventorySlot energySlot;
    private OutputInventorySlot outputSlot;
    private double clientEnergyUsed;


    public TileEntityAntiprotonicNucleosynthesizer() {
        super("prc", MachineType.ANTIPROTONIC_NUCLEOSYNTHESIZER, 3, 100, TRACKED_ERROR_TYPES);
        recipeCacheLookupMonitor = new NucleosynthesizerRecipeCacheLookupMonitor(this);
        upgradeComponent.clearSupportedTypes();
        upgradeComponent.setSupported(Upgrade.MUFFLING);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.ENERGY, TransmissionType.GAS);
        initializeInventorySlots();
        configComponent.setupItemIOExtraConfig(inputSlot, outputSlot, gasInputSlot, energySlot);
        configComponent.setConfig(TransmissionType.ITEM, DataType.EXTRA, DataType.INPUT, DataType.INPUT, DataType.ENERGY, DataType.INPUT, DataType.OUTPUT);

        configComponent.setupInputConfig(TransmissionType.GAS, inputGasTank);
        configComponent.setInputConfig(TransmissionType.ENERGY);
        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM);
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        gasInputSlot = builder.addSlot(GasInventorySlot.fillOrConvert(inputGasTank, this::getWorld, listener, 6, 69));
        gasInputSlot.setSlotOverlay(SlotOverlay.MINUS);
        inputSlot = builder.addSlot(InputInventorySlot.at(RecipeHandler::isInNucleosynthesizerRecipe, getRecipeCacheListener(), 26, 40));
        inputSlot.tracksWarnings(slot -> slot.warning(WarningType.NO_MATCHING_RECIPE, getWarningCheck(RecipeError.NOT_ENOUGH_INPUT)));
        outputSlot = builder.addSlot(OutputInventorySlot.at(getRecipeCacheChangeListener(listener), 152, 40));
        outputSlot.tracksWarnings(slot -> slot.warning(WarningType.NO_SPACE_IN_OUTPUT, getWarningCheck(RecipeError.NOT_ENOUGH_OUTPUT_SPACE)));
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 173, 69));
        return builder.build();
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        GasTankHelper builder = createGasTankHelper();
        builder.addTank(getOrCreateInputGasTank());
        return builder.build();
    }

    private BasicGasTank getOrCreateInputGasTank() {
        if (inputGasTank == null) {
            inputGasTank = BasicGasTank.input(10000, this::isValidGas, getRecipeCacheListener());
        }
        return inputGasTank;
    }

    @Override
    public void onAsyncUpdateServer() {
        commitAsyncRecipeTick();
    }

    @Override
    public void prepareAsyncRecipeTick() {
        energySlot.fillContainerOrConvert();
        gasInputSlot.fillTankOrConvert();
    }

    private void finishRecipeTick() {
        if (clientEnergyUsed <= 0 && prevEnergy >= getEnergy()) {
            setActive(false);
        }
        prevEnergy = getEnergy();
    }

    @Override
    protected RecipeLaneCommitTarget createAsyncRecipeCommitTarget(CachedRecipe<NucleosynthesizerRecipe> cache) {
        double rate = getMainEnergyContainer().getEnergyPerTick();
        int passes = rate > 0 ? Math.max(1, (int) Math.sqrt(getEnergy() / rate)) : 1;
        return new RecipeLaneCommitTarget(cache).input("item.0", inputSlot).input("gas.1", inputGasTank)
              .output("item.0", outputSlot).maxProcessingPasses(passes).keepProgressWithoutRecipe(operatingTicks);
    }

    @Override
    public void afterAsyncRecipeCommit(RecipeRunSnapshot snapshot, RecipeExecutionPlan plan) {
        clientEnergyUsed = plan.getEnergyAsDouble();
        operatingTicks = plan.getLane(0).getNewOperatingTicks();
        // Earlier successful passes activate the machine even if the final inactive callback is suppressed.
        if (plan.getOperations() > 0 && prevEnergy < getEnergy()) setActive(true);
        finishRecipeTick();
        recipeCacheLookupMonitor.refreshAfterPlanCommit();
    }

    @Override
    protected void setNoFinish() {
        ticksRequired = BASE_TICKS_REQUIRED;
    }

    @Override
    public void onCachedRecipeChanged(CachedRecipe<NucleosynthesizerRecipe> cachedRecipe, int cacheIndex) {
        super.onCachedRecipeChanged(cachedRecipe, cacheIndex);
        ticksRequired = cachedRecipe == null ? BASE_TICKS_REQUIRED : cachedRecipe.getRecipe().ticks;
    }

    @Nullable
    @Override
    protected GasStack getInputGasForUpgrade() {
        return inputGasTank.getGas();
    }

    @Nonnull
    @Override
    protected ItemStack getExtraSlotForUpgrade() {
        return gasInputSlot.getStack();
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
    public NucleosynthesizerRecipe getRecipe() {
        refreshRecipeLookupCache();
        NucleosynthesizerInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getNucleosynthesizerRecipe(input);
        }
        return cachedRecipe;
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

    public double getEnergyUsed() {
        return clientEnergyUsed;
    }

    public double getProcessRate() {
        double energyPerTick = getEnergyContainer().getEnergyPerTick();
        return energyPerTick <= 0 ? 0 : clientEnergyUsed / energyPerTick;
    }

    public boolean hasWarningNoMatchingSecondaryInput() {
        if (hasWarning(RecipeError.NOT_ENOUGH_SECONDARY_INPUT)) {
            return true;
        }
        if (inputSlot.isEmpty()) {
            return false;
        }
        NucleosynthesizerRecipe recipe = getRecipe();
        if (recipe != null) {
            return inputGasTank.getStored() < recipe.getInput().getGas().amount;
        }
        return hasMatchingSolidInput(inputSlot.getStack());
    }

    public boolean hasWarningNoMatchingItemInput() {
        if (hasWarning(RecipeError.NOT_ENOUGH_INPUT)) {
            return true;
        }
        return !inputSlot.isEmpty() && getRecipe() == null;
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

    @Override
    public NucleosynthesizerInput getInput() {
        return new NucleosynthesizerInput(inputSlot.getStack(), inputGasTank.getGas());
    }

    @Override
    public CachedRecipe<NucleosynthesizerRecipe> createNewCachedRecipe(NucleosynthesizerRecipe recipe, int cacheIndex) {
        return new TwoInputCachedRecipe<>(recipe, this::shouldRecheckAllRecipeErrors,
              InputHelper.getInputHandler(inputSlot, RecipeError.NOT_ENOUGH_INPUT),
              InputHelper.getGasInputHandler(inputGasTank, RecipeError.NOT_ENOUGH_SECONDARY_INPUT),
              OutputHelper.getOutputHandler(outputSlot, RecipeError.NOT_ENOUGH_OUTPUT_SPACE),
              () -> recipe.getInput().getSolid(), () -> recipe.getInput().getGas(),
              (item, gas) -> MachineInput.inputContains(item, recipe.getInput().getSolid())
                    && gas != null && gas.isGasEqual(recipe.getInput().getGas()),
              (item, gas) -> recipe.getOutput().output.copy(), ItemStack::isEmpty, gas -> gas == null || gas.amount <= 0, ItemStack::isEmpty)
              .setCanHolderFunction(() -> MekanismUtils.canFunction(this))
              .setActive(active -> {
                  if (active || prevEnergy >= getEnergy()) {
                      setActive(active);
                  }
              })
              .setEnergyRequirements(() -> MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK + recipe.extraEnergy), getMainEnergyContainer())
              .setRequiredTicks(() -> ticksRequired)
              .setBaselineMaxOperations(() -> getBaselineMaxOperations(MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK + recipe.extraEnergy), true))
              .setOperatingTicksChanged(ticks -> operatingTicks = ticks)
              .setErrorsChanged(this::onRecipeErrorsChanged)
              .setOnFinish(this::onCachedRecipeFinish);
    }

    @Override
    public boolean canOperate(NucleosynthesizerRecipe recipe) {
        return recipe != null && recipe.canOperate(inputSlot, inputGasTank, outputSlot);
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(clientEnergyUsed);
        TileUtils.addTankData(data, inputGasTank);
        return data;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            clientEnergyUsed = dataStream.readDouble();
            TileUtils.readTankData(dataStream, inputGasTank);
        }
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("inputGasTank")) {
            inputGasTank.read(nbtTags.getCompoundTag("inputGasTank"));
        }
        sanitizeAndClampTank();
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
    }


    @Override
    public Map<NucleosynthesizerInput, NucleosynthesizerRecipe> getRecipes() {
        return RecipeHandler.Recipe.ANTIPROTONIC_NUCLEOSYNTHESIZER.get();
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
            case 7 -> new Object[]{inputGasTank.getStored()};
            default -> throw new NoSuchMethodException();
        };
    }

    private boolean isValidGas(Gas gas) {
        return RecipeHandler.Recipe.ANTIPROTONIC_NUCLEOSYNTHESIZER.containsRecipe(gas);
    }

    private boolean hasMatchingSolidInput(ItemStack stack) {
        return getRecipes().keySet().stream().anyMatch(input -> MachineInput.inputContains(stack, input.getSolid()));
    }

    private ItemStack getCurrentOutput() {
        NucleosynthesizerRecipe recipe = getRecipe();
        if (recipe == null || recipe.getOutput().output.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return recipe.getOutput().output;
    }

    @Override
    public Object[] getManagedTanks() {
        return new Object[]{inputGasTank};
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        writeSustainedGasTanks(itemStack);
        ItemDataUtils.setLegacyGas(itemStack, "inputGasTank", inputGasTank.getGas());
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (!readSustainedGasTanks(itemStack)) {
            inputGasTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "inputGasTank"));
        }
        sanitizeAndClampTank();
    }

    private void sanitizeAndClampTank() {
        GasStack stored = inputGasTank.getGas();
        if (stored != null && (stored.amount <= 0 || stored.getGas() == null)) {
            inputGasTank.setEmpty();
        } else if (stored != null) {
            inputGasTank.setStackSize(stored.amount, Action.EXECUTE);
        }
    }
    @Override
    @SideOnly(Side.CLIENT)
    public Class<?> getSelectionWireframeModelClass() {
        return mekanism.client.model.ModelAntiprotonicNucleosynthesizer.class;
    }

    @Override
    public boolean shouldApplyDefaultSelectionWireframeFacingRotation(IBlockState state, IBlockAccess world, BlockPos pos) {
        return true;
    }
}
