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
import mekanism.common.recipe.machines.BasicMachineRecipe;
import mekanism.common.recipe.outputs.ItemStackOutput;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.machine.TileEntityBrushed;
import mekanism.common.tile.machine.TileEntityCrusher;
import mekanism.common.tile.machine.TileEntityEnergizedSmelter;
import mekanism.common.tile.machine.TileEntityEnrichmentChamber;
import mekanism.common.tile.machine.TileEntityRolling;
import mekanism.common.tile.machine.TileEntityStamping;
import mekanism.common.tile.machine.TileEntityTurning;
import mekanism.common.util.MekanismUtils;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


/**
 * 使用电力的机器类型
 */

public abstract class TileEntityElectricMachine<RECIPE extends BasicMachineRecipe<RECIPE>> extends TileEntityUpgradeableMachine<ItemStackInput, ItemStackOutput, RECIPE> {

    private static final List<RecipeError> TRACKED_ERROR_TYPES = Arrays.asList(
          RecipeError.NOT_ENOUGH_ENERGY,
          RecipeError.NOT_ENOUGH_INPUT,
          RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );
    private static final String[] methods = new String[]{"getEnergy", "getProgress", "isActive", "facing", "canOperate", "getMaxEnergy", "getEnergyNeeded"};
    protected InputInventorySlot inputSlot;
    protected EnergyInventorySlot energySlot;
    protected OutputInventorySlot outputSlot;

    /**
     * A simple electrical machine. This has 3 slots - the input slot (0), the energy slot (1), output slot (2), and the upgrade slot (3). It will not run if it does not
     * have enough energy.
     *
     * @param soundPath     - location of the sound effect
     * @param type          - type of this machine
     * @param ticksRequired - ticks required to operate -- or smelt an item.
     */
    public TileEntityElectricMachine(String soundPath, MachineType type, int ticksRequired) {
        super(soundPath, type, 3, ticksRequired, TRACKED_ERROR_TYPES);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.ENERGY);
        initializeInventorySlots();
        configComponent.setupItemIOConfig(inputSlot, outputSlot, energySlot);
        configComponent.setConfig(TransmissionType.ITEM, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.ENERGY, DataType.INPUT, DataType.OUTPUT);
        configComponent.setInputConfig(TransmissionType.ENERGY);

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM);
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        IContentsListener recipeCacheListener = getRecipeCacheListener();
        IContentsListener recipeCacheChangeListener = getRecipeCacheChangeListener(listener);
        inputSlot = builder.addSlot(InputInventorySlot.at(stack -> RecipeHandler.isInRecipe(stack, getRecipes()), recipeCacheListener, 64, 17)
              .setAutoPullValidator((stack, side) -> canAutoPullInput(stack)));
        inputSlot.tracksWarnings(slot -> slot.warning(WarningType.NO_MATCHING_RECIPE, getWarningCheck(RecipeError.NOT_ENOUGH_INPUT)));
        outputSlot = builder.addSlot(OutputInventorySlot.at(recipeCacheChangeListener, 116, 35));
        outputSlot.tracksWarnings(slot -> slot.warning(WarningType.NO_SPACE_IN_OUTPUT, getWarningCheck(RecipeError.NOT_ENOUGH_OUTPUT_SPACE)));
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 64, 53));
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
    protected boolean supportsAsyncIdleSkipping() {
        Class<?> type = getClass();
        return type == TileEntityEnergizedSmelter.class || type == TileEntityEnrichmentChamber.class ||
              type == TileEntityCrusher.class || type == TileEntityStamping.class ||
              type == TileEntityRolling.class || type == TileEntityBrushed.class || type == TileEntityTurning.class;
    }

    @Override
    protected boolean isAsyncUpdateIdle() {
        return inputSlot.isEmpty() && energySlot.isEmpty() && isEmptyRecipeStateSettled();
    }

    @Override
    public void addTileSyncTask() {
    }

    @Override
    public ItemStackInput getInput() {
        return new ItemStackInput(inputSlot.getStack());
    }

    @Override
    public RECIPE getRecipe() {
        refreshRecipeLookupCache();
        ItemStackInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getRecipe(input, getRecipes());
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
        return !inputSlot.isEmpty() && getRecipe() == null;
    }

    public boolean hasWarningNoSpaceInOutput() {
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
        ItemStack output = getCurrentOutput();
        ItemStack current = outputSlot.getStack();
        return !output.isEmpty() && !current.isEmpty() && !ItemHandlerHelper.canItemStacksStack(current, output);
    }

    @Override
    public Map<ItemStackInput, RECIPE> getRecipes() {
        return null;
    }

    @Override
    public boolean canOperate(RECIPE recipe) {
        return recipe != null && recipe.canOperate(inputSlot, outputSlot);
    }

    private ItemStack getCurrentOutput() {
        RECIPE recipe = getRecipe();
        if (recipe == null || recipe.getOutput().output.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return recipe.getOutput().output;
    }

    private boolean canAutoPullInput(ItemStack stack) {
        RECIPE recipe = RecipeHandler.getRecipe(new ItemStackInput(getSimulatedStackWithInsert(0, stack)), getRecipes());
        return recipe != null && canOutputToSlot(2, recipe.getOutput().output);
    }

    @Override
    public CachedRecipe<RECIPE> createNewCachedRecipe(RECIPE recipe, int cacheIndex) {
        return new OneInputCachedRecipe<>(recipe, this::shouldRecheckAllRecipeErrors,
              InputHelper.getInputHandler(inputSlot, RecipeError.NOT_ENOUGH_INPUT),
              OutputHelper.getOutputHandler(outputSlot, RecipeError.NOT_ENOUGH_OUTPUT_SPACE),
              () -> recipe.getInput().ingredient,
              input -> mekanism.common.recipe.inputs.MachineInput.inputContains(input, recipe.getInput().ingredient),
              input -> recipe.getOutput().output.copy(), ItemStack::isEmpty, ItemStack::isEmpty)
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
