package mekanism.common.tile.prefab;

import mekanism.api.IContentsListener;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
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
import mekanism.common.recipe.machines.Chance2MachineRecipe;
import mekanism.common.recipe.outputs.ChanceOutput2;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.util.MekanismUtils;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 单输出概率类型机器方块
 *
 * @param <RECIPE> 用于机器的配方
 */

public abstract class TileEntityChanceMachine2<RECIPE extends Chance2MachineRecipe<RECIPE>>
        extends TileEntityUpgradeableMachine<ItemStackInput, ChanceOutput2, RECIPE> {

    private static final List<RecipeError> TRACKED_ERROR_TYPES = Arrays.asList(
          RecipeError.NOT_ENOUGH_ENERGY,
          RecipeError.NOT_ENOUGH_INPUT,
          RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );
    private static final String[] methods = new String[]{"getEnergy", "getProgress", "isActive", "facing", "canOperate", "getMaxEnergy", "getEnergyNeeded"};
    private final java.util.Random recipeRandom = new java.util.Random();
    private boolean recipeRandomSeeded;
    protected InputInventorySlot inputSlot;
    protected EnergyInventorySlot energySlot;
    protected OutputInventorySlot outputSlot;

    public TileEntityChanceMachine2(String soundPath, MachineType type, int ticksRequired) {
        super(soundPath, type, 3, ticksRequired, TRACKED_ERROR_TYPES);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.ENERGY);
        initializeInventorySlots();
        configComponent.setupItemIOConfig(inputSlot, outputSlot, energySlot);
        configComponent.setConfig(TransmissionType.ITEM, DataType.ENERGY, DataType.INPUT, DataType.NONE, DataType.NONE, DataType.NONE, DataType.OUTPUT);
        configComponent.setInputConfig(TransmissionType.ENERGY);

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM);
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        IContentsListener recipeCacheListener = getRecipeCacheListener();
        IContentsListener recipeCacheChangeListener = getRecipeCacheChangeListener(listener);
        inputSlot = builder.addSlot(InputInventorySlot.at(this::isInputItemValid, recipeCacheListener, 56, 17)
              .setAutoPullValidator((stack, side) -> canAutoPullInput(stack)));
        inputSlot.tracksWarnings(slot -> slot.warning(WarningType.NO_MATCHING_RECIPE, getWarningCheck(RecipeError.NOT_ENOUGH_INPUT)));
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 56, 53));
        outputSlot = builder.addSlot(OutputInventorySlot.at(recipeCacheChangeListener, 116, 35));
        outputSlot.tracksWarnings(slot -> slot.warning(WarningType.NO_SPACE_IN_OUTPUT, getWarningCheck(RecipeError.NOT_ENOUGH_OUTPUT_SPACE)));
        return builder.build();
    }

    protected boolean isInputItemValid(@Nonnull ItemStack itemstack) {
        return RecipeHandler.isInRecipe(itemstack, getRecipes()) || MekanismConfig.current().mekce.EnableAddArrItemRecyclerRecipe.val();
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
        return recipe != null && recipe.canOperate(inputSlot, outputSlot);
    }

    private boolean canAutoPullInput(ItemStack stack) {
        RECIPE recipe = RecipeHandler.getChance2Recipe(new ItemStackInput(getSimulatedStackWithInsert(0, stack)), getRecipes());
        return recipe != null && recipe.getOutput().applyOutputs(outputSlot, false);
    }

    /**
     * Returns this machine's probability source. Seeded from the machine position on first use so that a machine
     * replays the same output sequence for the same inputs and no longer shares a process-wide source.
     */
    protected java.util.Random chanceRecipeRandom() {
        if (!recipeRandomSeeded) {
            if (world != null) {
                recipeRandom.setSeed(mekanism.common.recipe.cache.RecipeRandom.deriveSeed(pos.toLong(), 0));
                recipeRandomSeeded = true;
            }
        }
        return recipeRandom;
    }

    @Override
    public CachedRecipe<RECIPE> createNewCachedRecipe(RECIPE recipe, int cacheIndex) {
        return new OneInputCachedRecipe<>(recipe, this::shouldRecheckAllRecipeErrors,
              InputHelper.getInputHandler(inputSlot, RecipeError.NOT_ENOUGH_INPUT),
              OutputHelper.getOutputHandlerChance2(outputSlot, RecipeError.NOT_ENOUGH_OUTPUT_SPACE, chanceRecipeRandom()),
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
            cachedRecipe = RecipeHandler.getChance2Recipe(input, getRecipes());
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

    public boolean hasWarningNoMatchingRecipe() {
        if (hasWarning(RecipeError.NOT_ENOUGH_INPUT)) {
            return true;
        }
        return !inputSlot.isEmpty() && getRecipe() == null;
    }

    public boolean hasWarningNoSpaceInOutput() {
        if (hasWarning(RecipeError.NOT_ENOUGH_OUTPUT_SPACE)) {
            return true;
        }
        RECIPE recipe = getRecipe();
        if (recipe == null || recipe.getOutput().getMaxPrimaryOutput().isEmpty()) {
            return false;
        }
        ItemStack output = recipe.getOutput().getMaxPrimaryOutput();
        ItemStack current = outputSlot.getStack();
        if (!current.isEmpty() && !net.minecraftforge.items.ItemHandlerHelper.canItemStacksStack(current, output)) {
            return false;
        }
        return !outputSlot.insertItem(output.copy(), mekanism.api.Action.SIMULATE, mekanism.api.AutomationType.INTERNAL).isEmpty();
    }

    public boolean hasWarningInputDoesntProduceOutput() {
        if (hasWarning(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT)) {
            return true;
        }
        RECIPE recipe = getRecipe();
        if (recipe == null || recipe.getOutput().getMaxPrimaryOutput().isEmpty()) {
            return false;
        }
        ItemStack current = outputSlot.getStack();
        return !current.isEmpty() && !net.minecraftforge.items.ItemHandlerHelper.canItemStacksStack(current, recipe.getOutput().getMaxPrimaryOutput());
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
