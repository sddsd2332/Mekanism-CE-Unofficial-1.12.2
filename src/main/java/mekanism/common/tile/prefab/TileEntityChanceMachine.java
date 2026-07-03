package mekanism.common.tile.prefab;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.cache.CachedRecipe;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.cache.OneInputCachedRecipe;
import mekanism.common.recipe.cache.inputs.InputHelper;
import mekanism.common.recipe.cache.outputs.OutputHelper;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.machines.ChanceMachineRecipe;
import mekanism.common.recipe.outputs.ChanceOutput;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.util.MekanismUtils;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 双输出概率类型机器方块
 *
 * @param <RECIPE> 用于机器的配方
 */

public abstract class TileEntityChanceMachine<RECIPE extends ChanceMachineRecipe<RECIPE>> extends TileEntityUpgradeableMachine<ItemStackInput, ChanceOutput, RECIPE> {

    private static final List<RecipeError> TRACKED_ERROR_TYPES = Arrays.asList(
          RecipeError.NOT_ENOUGH_ENERGY,
          RecipeError.NOT_ENOUGH_INPUT,
          RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );
    private static final String[] methods = new String[]{"getEnergy", "getProgress", "isActive", "facing", "canOperate", "getMaxEnergy", "getEnergyNeeded"};
    private final RecipeError secondaryOutputSpaceError;
    protected InputInventorySlot inputSlot;
    protected EnergyInventorySlot energySlot;
    protected OutputInventorySlot outputSlot;
    protected OutputInventorySlot secondaryOutputSlot;

    public TileEntityChanceMachine(String soundPath, MachineType type, int ticksRequired) {
        this(soundPath, type, ticksRequired, TRACKED_ERROR_TYPES, RecipeError.NOT_ENOUGH_OUTPUT_SPACE);
    }

    protected TileEntityChanceMachine(String soundPath, MachineType type, int ticksRequired, List<RecipeError> trackedErrorTypes,
          RecipeError secondaryOutputSpaceError) {
        super(soundPath, type, 3, ticksRequired, trackedErrorTypes);
        this.secondaryOutputSpaceError = secondaryOutputSpaceError;
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.ENERGY);
        initializeInventorySlots();
        configComponent.setupItemIOConfig(Arrays.asList(inputSlot), Arrays.asList(outputSlot, secondaryOutputSlot), energySlot, false);
        configComponent.setConfig(TransmissionType.ITEM, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.OUTPUT, DataType.INPUT, DataType.ENERGY);
        configComponent.setInputConfig(TransmissionType.ENERGY);

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM);
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        IContentsListener recipeCacheListener = getRecipeCacheListener();
        IContentsListener recipeCacheChangeListener = getRecipeCacheChangeListener(listener);
        inputSlot = builder.addSlot(InputInventorySlot.at(stack -> RecipeHandler.isInRecipe(stack, getRecipes()), recipeCacheListener, 56, 17)
              .setAutoPullValidator((stack, side) -> canAutoPullInput(stack)));
        inputSlot.tracksWarnings(slot -> slot.warning(WarningType.NO_MATCHING_RECIPE, getWarningCheck(RecipeError.NOT_ENOUGH_INPUT)));
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 56, 53));
        outputSlot = builder.addSlot(OutputInventorySlot.at(recipeCacheChangeListener, 116, 35));
        secondaryOutputSlot = builder.addSlot(OutputInventorySlot.at(recipeCacheChangeListener, 132, 35));
        return builder.build();
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

    @Nonnull
    @Override
    protected ItemStack getSecondaryOutputSlotForUpgrade() {
        return secondaryOutputSlot.getStack();
    }

    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        if (energySlot != null) {
            energySlot.fillContainerOrConvert();
        }
        processRecipe();
        prevEnergy = getEnergy();
    }

    @Override
    public void addTileSyncTask() {
    }

    @Override
    public ItemStackInput getInput() {
        return new ItemStackInput(inputSlot.getStack());
    }

    @Override
    public boolean canOperate(RECIPE recipe) {
        return recipe != null && recipe.canOperate(inputSlot, outputSlot, secondaryOutputSlot);
    }

    private boolean canAutoPullInput(ItemStack stack) {
        RECIPE recipe = RecipeHandler.getChanceRecipe(new ItemStackInput(getSimulatedStackWithInsert(0, stack)), getRecipes());
        return recipe != null && recipe.getOutput().applyOutputs(outputSlot, secondaryOutputSlot, false);
    }

    @Override
    public CachedRecipe<RECIPE> createNewCachedRecipe(RECIPE recipe, int cacheIndex) {
        return new OneInputCachedRecipe<>(recipe, this::shouldRecheckAllRecipeErrors,
              InputHelper.getInputHandler(inputSlot, RecipeError.NOT_ENOUGH_INPUT),
              OutputHelper.getOutputHandler(outputSlot, RecipeError.NOT_ENOUGH_OUTPUT_SPACE, secondaryOutputSlot, secondaryOutputSpaceError),
              () -> recipe.getInput().ingredient,
              input -> mekanism.common.recipe.inputs.MachineInput.inputContains(input, recipe.getInput().ingredient),
              input -> recipe.getOutput().copy(), ItemStack::isEmpty, output -> false)
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
    public RECIPE getRecipe() {
        refreshRecipeLookupCache();
        ItemStackInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getChanceRecipe(input, getRecipes());
        }
        return cachedRecipe;
    }

    @Override
    public RECIPE getRecipe(int cacheIndex) {
        return getRecipe();
    }

    public boolean hasWarningNoMatchingRecipe() {
        if (hasWarning(RecipeError.NOT_ENOUGH_INPUT)) {
            return true;
        }
        return !inputSlot.isEmpty() && getRecipe() == null;
    }

    public MachineEnergyContainer getEnergyContainer() {
        return getMainEnergyContainer();
    }

    public boolean hasWarningNoSpaceInOutput() {
        if (hasWarning(RecipeError.NOT_ENOUGH_OUTPUT_SPACE) || hasWarning(secondaryOutputSpaceError)) {
            return true;
        }
        ChanceOutput output = getCurrentOutput();
        return output != null && (hasNoSpace(output.getMainOutput(), outputSlot) || hasNoSpace(output.getMaxSecondaryOutput(), secondaryOutputSlot));
    }

    public boolean hasWarningInputDoesntProduceOutput() {
        if (hasWarning(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT)) {
            return true;
        }
        ChanceOutput output = getCurrentOutput();
        return output != null && (doesntStack(output.getMainOutput(), outputSlot) || doesntStack(output.getMaxSecondaryOutput(), secondaryOutputSlot));
    }

    private ChanceOutput getCurrentOutput() {
        RECIPE recipe = getRecipe();
        return recipe == null ? null : recipe.getOutput();
    }

    private boolean hasNoSpace(ItemStack output, OutputInventorySlot slot) {
        if (output.isEmpty()) {
            return false;
        }
        ItemStack current = slot.getStack();
        if (!current.isEmpty() && !ItemHandlerHelper.canItemStacksStack(current, output)) {
            return false;
        }
        return !slot.insertItem(output.copy(), Action.SIMULATE, AutomationType.INTERNAL).isEmpty();
    }

    private boolean doesntStack(ItemStack output, OutputInventorySlot slot) {
        ItemStack current = slot.getStack();
        return !output.isEmpty() && !current.isEmpty() && !ItemHandlerHelper.canItemStacksStack(current, output);
    }

    @Override
    public Map<ItemStackInput, RECIPE> getRecipes() {
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
            case 1 -> new Object[]{operatingTicks};
            case 2 -> new Object[]{isActive};
            case 3 -> new Object[]{facing};
            case 4 -> new Object[]{canOperate(getRecipe())};
            case 5 -> new Object[]{getMaxEnergy()};
            case 6 -> new Object[]{getMaxEnergy() - getEnergy()};
            default -> throw new NoSuchMethodException();
        };
    }
@Override
    protected boolean shouldDumpRadiation() {
        return false;
    }
}
