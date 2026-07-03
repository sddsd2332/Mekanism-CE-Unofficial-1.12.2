package mekanism.common.tile.machine;

import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.TileNetworkList;
import mekanism.api.infuse.InfuseObject;
import mekanism.api.infuse.InfuseRegistry;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.InfuseStorage;
import mekanism.common.base.ISustainedData;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.cache.CachedRecipe;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.cache.TwoInputCachedRecipe;
import mekanism.common.recipe.cache.inputs.InputHelper;
import mekanism.common.recipe.cache.outputs.OutputHelper;
import mekanism.common.recipe.inputs.InfusionInput;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.machines.MetallurgicInfuserRecipe;
import mekanism.common.recipe.outputs.ItemStackOutput;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.interfaces.IHasDumpButton;
import mekanism.common.tile.prefab.TileEntityUpgradeableMachine;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TileEntityMetallurgicInfuser extends TileEntityUpgradeableMachine<InfusionInput, ItemStackOutput, MetallurgicInfuserRecipe> implements ISustainedData,
      IHasDumpButton {

    private static final List<RecipeError> TRACKED_ERROR_TYPES = Arrays.asList(
          RecipeError.NOT_ENOUGH_ENERGY,
          RecipeError.NOT_ENOUGH_INPUT,
          RecipeError.NOT_ENOUGH_SECONDARY_INPUT,
          RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );
    private static final String[] methods = new String[]{"getEnergy", "getProgress", "facing", "canOperate", "getMaxEnergy", "getEnergyNeeded", "getInfuse",
            "getInfuseNeeded"};
    public static final int SLOT_EXTRA = 0;
    public static final int SLOT_INPUT = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_ENERGY = 3;
    /**
     * The maxiumum amount of infuse this machine can store.
     */
    public int MAX_INFUSE = 1000;
    /**
     * The amount of infuse this machine has stored.
     */
    public InfuseStorage infuseStored = new InfuseStorage();
    private InputInventorySlot extraSlot;
    private InputInventorySlot inputSlot;
    private OutputInventorySlot outputSlot;
    private EnergyInventorySlot energySlot;

    public TileEntityMetallurgicInfuser() {
        super("metalinfuser", MachineType.METALLURGIC_INFUSER, 0, 200, TRACKED_ERROR_TYPES);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.ENERGY);
        initializeInventorySlots();
        configComponent.setupItemIOExtraConfig(inputSlot, outputSlot, extraSlot, energySlot);
        configComponent.setConfig(TransmissionType.ITEM, DataType.EXTRA, DataType.INPUT, DataType.INPUT, DataType.ENERGY, DataType.INPUT, DataType.OUTPUT);

        configComponent.setInputConfig(TransmissionType.ENERGY);
        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM);
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        IContentsListener recipeCacheListener = getRecipeCacheListener();
        IContentsListener recipeCacheChangeListener = getRecipeCacheChangeListener(listener);
        extraSlot = builder.addSlot(InputInventorySlot.at(this::isInfuseInputValid, listener, 17, 35));
        extraSlot.setSlotType(ContainerSlotType.EXTRA);
        inputSlot = builder.addSlot(InputInventorySlot.at(this::isRecipeInputValid, recipeCacheListener, 51, 43)
              .setAutoPullValidator((stack, side) -> canAutoPullInput(stack)));
        inputSlot.tracksWarnings(slot -> slot.warning(WarningType.NO_MATCHING_RECIPE, getWarningCheck(RecipeError.NOT_ENOUGH_INPUT)));
        outputSlot = builder.addSlot(OutputInventorySlot.at(recipeCacheChangeListener, 109, 43));
        outputSlot.tracksWarnings(slot -> slot.warning(WarningType.NO_SPACE_IN_OUTPUT, getWarningCheck(RecipeError.NOT_ENOUGH_OUTPUT_SPACE)));
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 143, 35));
        return builder.build();
    }
    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        energySlot.fillContainerOrConvert();
        ItemStack infuseInput = extraSlot.getStack();
        if (!infuseInput.isEmpty()) {
            InfuseObject pendingInfuseInput = InfuseRegistry.getObject(infuseInput);
            int operations = infuseStored.getSupportedConversionOperations(pendingInfuseInput, MAX_INFUSE, infuseInput.getCount(),
                  MekanismConfig.current().general.bulkSlotItemConversion.val());
            if (operations > 0) {
                infuseStored.increase(pendingInfuseInput, operations);
                MekanismUtils.logMismatchedStackSize(extraSlot.shrinkStack(operations, Action.EXECUTE), operations);
                recipeCacheLookupMonitor.onChange();
            }
        }
        processRecipe();
        prevEnergy = getEnergy();
    }

    @Override
    public void addTileSyncTask() {
    }

    @Nonnull
    @Override
    protected InfuseStorage getInfusionForUpgrade() {
        InfuseStorage copy = new InfuseStorage();
        copy.copyFrom(infuseStored);
        return copy;
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
    protected ItemStack getExtraSlotForUpgrade() {
        return extraSlot.getStack();
    }

    private boolean isInfuseInputValid(ItemStack itemstack) {
        InfuseObject infuseObject = InfuseRegistry.getObject(itemstack);
        return infuseStored.canReceive(infuseObject);
    }

    private boolean isRecipeInputValid(ItemStack itemstack) {
        return hasRecipeForInput(itemstack);
    }

    private boolean hasRecipeForInput(ItemStack itemstack) {
        if (infuseStored.getType() != null) {
            return hasRecipeForInputAndInfuse(itemstack);
        }
        return hasRecipeForItemInput(itemstack);
    }

    private boolean hasRecipeForInputAndInfuse(ItemStack itemstack) {
        return RecipeHandler.getMetallurgicInfuserRecipe(new InfusionInput(infuseStored, itemstack)) != null;
    }

    private boolean hasRecipeForItemInput(ItemStack itemstack) {
        for (InfusionInput input : Recipe.METALLURGIC_INFUSER.get().keySet()) {
            if (ItemHandlerHelper.canItemStacksStack(input.inputStack, itemstack)) {
                return true;
            }
        }
        return false;
    }

    public InfusionInput getInput() {
        return new InfusionInput(infuseStored, inputSlot.getStack());
    }

    public MachineEnergyContainer getEnergyContainer() {
        return getMainEnergyContainer();
    }

    public boolean hasWarningNoMatchingSecondaryInput() {
        if (hasWarning(RecipeError.NOT_ENOUGH_SECONDARY_INPUT)) {
            return true;
        }
        if (getRecipe() != null) {
            return false;
        }
        if (infuseStored.getType() == null || infuseStored.getAmount() <= 0) {
            return !inputSlot.isEmpty();
        }
        MetallurgicInfuserRecipe recipe = RecipeHandler.getMetallurgicInfuserRecipe(getInput());
        return recipe == null || infuseStored.getAmount() < recipe.getInput().infuse.getAmount();
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

    public String getInfuseTooltip() {
        return infuseStored.getType() == null ? LangUtils.localize("gui.empty") : infuseStored.getType().getLocalizedName() + ": " + infuseStored.getAmount();
    }

    @Override
    public CachedRecipe<MetallurgicInfuserRecipe> createNewCachedRecipe(MetallurgicInfuserRecipe recipe, int cacheIndex) {
        return new TwoInputCachedRecipe<>(recipe, this::shouldRecheckAllRecipeErrors,
              InputHelper.getInputHandler(inputSlot, RecipeError.NOT_ENOUGH_INPUT),
              InputHelper.getInfuseInputHandler(infuseStored, RecipeError.NOT_ENOUGH_SECONDARY_INPUT),
              OutputHelper.getOutputHandler(outputSlot, RecipeError.NOT_ENOUGH_OUTPUT_SPACE),
              () -> recipe.getInput().inputStack, () -> recipe.getInput().infuse,
              (item, infuse) -> MachineInput.inputContains(item, recipe.getInput().inputStack)
                    && infuse.getType() == recipe.getInput().infuse.getType(),
              (item, infuse) -> recipe.getOutput().output.copy(), ItemStack::isEmpty,
              infuse -> infuse == null || infuse.getType() == null || infuse.getAmount() <= 0, ItemStack::isEmpty)
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
    public Map<InfusionInput, MetallurgicInfuserRecipe> getRecipes() {
        return Recipe.METALLURGIC_INFUSER.get();
    }

    @Override
    public MetallurgicInfuserRecipe getRecipe() {
        refreshRecipeLookupCache();
        InfusionInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getMetallurgicInfuserRecipe(input);
        }
        return cachedRecipe;
    }

    @Override
    public MetallurgicInfuserRecipe getRecipe(int cacheIndex) {
        return getRecipe();
    }

    @Override
    public boolean canOperate(MetallurgicInfuserRecipe recipe) {
        return recipe != null && recipe.canOperate(inputSlot, outputSlot, infuseStored);
    }

    private boolean canAutoPullInput(ItemStack stack) {
        MetallurgicInfuserRecipe recipe = getRecipeForInsertedInput(stack);
        return recipe != null && canOutputToSlot(SLOT_OUTPUT, recipe.getOutput().output);
    }

    private MetallurgicInfuserRecipe getRecipeForInsertedInput(ItemStack stack) {
        return RecipeHandler.getMetallurgicInfuserRecipe(new InfusionInput(infuseStored, getSimulatedStackWithInsert(SLOT_INPUT, stack)));
    }

    private ItemStack getCurrentOutput() {
        MetallurgicInfuserRecipe recipe = getRecipe();
        if (recipe == null || recipe.getOutput().output.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return recipe.getOutput().output;
    }

    public int getScaledInfuseLevel(int i) {
        return infuseStored.getAmount() * i / MAX_INFUSE;
    }

    @Override
    public void dump() {
        infuseStored.setEmpty();
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        infuseStored.read(nbtTags);
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        ;
        infuseStored.write(nbtTags);
        nbtTags.setBoolean("sideDataStored", true);
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            int amount = dataStream.readInt();
            if (amount == 0) {
                infuseStored.setEmpty();
            } else {
                infuseStored.setAmount(amount);
            }
            return;
        }

        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            infuseStored.readFromPacket(dataStream);
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        infuseStored.addToNetworkList(data);
        return data;
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
            case 2 -> new Object[]{facing};
            case 3 -> new Object[]{canOperate(RecipeHandler.getMetallurgicInfuserRecipe(getInput()))};
            case 4 -> new Object[]{getMaxEnergy()};
            case 5 -> new Object[]{getMaxEnergy() - getEnergy()};
            case 6 -> new Object[]{infuseStored};
            case 7 -> new Object[]{MAX_INFUSE - infuseStored.getAmount()};
            default -> throw new NoSuchMethodException();
        };
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        infuseStored.writeSustainedData(itemStack);
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        infuseStored.readSustainedData(itemStack);
    }

    @Override
    protected boolean shouldDumpRadiation() {
        return false;
    }
}
