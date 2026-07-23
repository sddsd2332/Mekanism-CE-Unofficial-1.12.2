package mekanism.common.tile.factory;

import com.github.bsideup.jabel.Desugar;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import mekanism.api.*;
import mekanism.api.IConfigCardAccess.ISpecialConfigData;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.infuse.InfuseObject;
import mekanism.api.infuse.InfuseRegistry;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.math.MathUtils;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.*;
import mekanism.common.base.*;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.fluid.FluidTankHelper;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.gas.GasTankHelper;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.computer.IComputerIntegration;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.container.sync.SyncableDouble;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.*;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import mekanism.common.lib.inventory.HashedItem;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.cache.*;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.cache.IRecipeLookupHandler.ConstantUsageRecipeLookupHandler;
import mekanism.common.recipe.cache.ItemStackConstantGasCachedRecipe.GasUsageMultiplier;
import mekanism.common.recipe.cache.inputs.IInputHandler;
import mekanism.common.recipe.cache.inputs.InputHelper;
import mekanism.common.recipe.cache.outputs.IOutputHandler;
import mekanism.common.recipe.cache.outputs.OutputHelper;
import mekanism.common.recipe.inputs.*;
import mekanism.common.recipe.machines.*;
import mekanism.common.recipe.outputs.ChanceOutput;
import mekanism.common.recipe.outputs.ChanceOutput2;
import mekanism.common.recipe.outputs.ItemStackOutput;
import mekanism.common.recipe.outputs.PressurizedOutput;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.FactoryTier;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityMachine;
import mekanism.common.upgrade.FactoryUpgradeData;
import mekanism.common.upgrade.IUpgradeData;
import mekanism.common.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLongArray;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

import static mekanism.common.tile.prefab.TileEntityAdvancedElectricMachine.ceilSecondaryEnergyPerTick;


public class TileEntityFactory extends TileEntityMachine implements IComputerIntegration, ISideConfiguration, ISpecialConfigData, IUpgradeableTile,
        ISustainedData, IComparatorSupport, ITankManager, IRecipeLookupHandler<MachineRecipe<?, ?, ?>>, ConstantUsageRecipeLookupHandler,
        mekanism.common.tile.interfaces.IHasDumpButton {
    private static final int LEGACY_SLOT_ENERGY = 0;
    private static final int LEGACY_SLOT_TYPE_INPUT = 1;
    private static final int LEGACY_SLOT_TYPE_OUTPUT = 2;
    private static final int LEGACY_SLOT_EXTRA = 3;
    private static final int LEGACY_FIRST_PROCESS_SLOT = 4;
    private static final int PROCESS_SLOT_STRIDE = 3;
    private static final int RECIPE_CHECK_FREQUENCY = 100;
    private static final int TAG_INT_ARRAY = 11;
    private static final int TAG_LONG_ARRAY = 12;
    private static final int ENERGY_SLOT_X = 7;
    private static final int ENERGY_SLOT_Y = 13;
    private static final int EXTRA_SLOT_X = 7;
    private static final int PROCESS_INPUT_SLOT_Y = 13;
    private static final int PROCESS_OUTPUT_SLOT_Y = 57;
    private static final int PROCESS_SECONDARY_OUTPUT_SLOT_Y = 77;
    private static final String FACTORY_INVENTORY_VERSION_KEY = "factoryInventorySlotsVersion";
    private static final String LEGACY_TYPE_SLOT_DROPS_KEY = "legacyFactoryTypeSlotDrops";
    private static final int FACTORY_INVENTORY_VERSION = 2;
    private static final byte FACTORY_INVENTORY_VERSION_MARKER_SLOT = -1;
    private static final String LEGACY_PROGRESS_KEY_PREFIX = "progress";
    private static final String LEGACY_USED_SO_FAR_KEY_PREFIX = NBTConstants.USED_SO_FAR;
    private static final String LEGACY_FLUID_TANK_KEY = "fluidTank";
    private static final String LEGACY_GAS_TANK_KEY = "gasTank";
    private static final String LEGACY_GAS_OUTPUT_TANK_KEY = "gasOutTank";
    private static final Field NBT_LONG_ARRAY_DATA = findLongArrayDataField();
    private static final String[] methods = new String[]{"getEnergy", "getProgress", "facing", "canOperate", "getMaxEnergy", "getEnergyNeeded"};
    private static final List<RecipeError> FACTORY_ERROR_TYPES = Arrays.asList(
          RecipeError.NOT_ENOUGH_ENERGY,
          RecipeError.NOT_ENOUGH_INPUT,
          RecipeError.NOT_ENOUGH_SECONDARY_INPUT,
          RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );
    private static final Set<RecipeError> FACTORY_GLOBAL_ERROR_TYPES = new java.util.HashSet<>(Arrays.asList(
          RecipeError.NOT_ENOUGH_ENERGY,
          RecipeError.NOT_ENOUGH_SECONDARY_INPUT
    ));
    private static final int BASE_MAX_TANK = 10000;
    /**
     * The amount of infuse this machine has stored.
     */
    private final InfuseStorage infuseStored = new InfuseStorage();
    private final FactoryRecipeCacheLookupMonitor<MachineRecipe<?, ?, ?>>[] recipeCacheLookupMonitors;
    private final BooleanSupplier[] recheckAllRecipeErrors;
    private final ErrorTracker errorTracker;
    private final boolean[] activeStates;
    private final FactoryInvSorter inventorySorter = new FactoryInvSorter(this);
    private final List<ItemStack> legacyTypeSlotDrops = new ArrayList<>();
    private BasicGasTank gasTank;
    private BasicGasTank gasOutTank;
    private BasicFluidTank fluidTank;
    /**
     * This Factory's tier.
     */
    public FactoryTier tier;
    /**
     * An int[] used to track all current operations' progress.
     */
    public final int[] progress;
    private int BASE_MAX_INFUSE = 1000;
    private int maxInfuse;
    /**
     * How many ticks it takes, by default, to run an operation.
     */
    private int BASE_TICKS_REQUIRED;
    /**
     * How many ticks it takes, with upgrades, to run an operation
     */
    private int ticksRequired = 200;
    private boolean sorting;
    private boolean sortingNeeded = true;
    private int observedRecipeVersion = RecipeHandler.getGlobalRecipeVersion();
    private boolean recipeCachesInvalid;
    private boolean upgraded;
    private double lastUsage;
    private TileComponentEjector ejectorComponent;
    private TileComponentConfig configComponent;
    /**
     * This machine's recipe type.
     */

    /**
     * How much secondary energy each operation consumes per tick
     */
    private double secondaryEnergyPerTick = 0;
    private int secondaryEnergyThisTick;
    private double gasPerTickMeanMultiplier = 1;
    private long baseTotalUsage;
    private final long[] usedSoFar;
    private final GasUsageMultiplier gasUsageMultiplier;
    private EnergyInventorySlot energySlot;
    private InputInventorySlot extraSlot;
    private ProcessInfo[] processInfoSlots;
    private List<IInventorySlot> processInputSlots;
    private List<IInventorySlot> processOutputSlots;
    private IInputHandler<ItemStack, ItemStack>[] itemInputHandlers;
    private IInputHandler<ItemStack, ItemStack> extraItemInputHandler;
    private IInputHandler<GasStack, GasStack> gasInputHandler;
    private IInputHandler<GasStack, GasStack> secondaryGasInputHandler;
    private IInputHandler<GasStack, GasStack> constantGasInputHandler;
    private IInputHandler<FluidStack, FluidStack> fluidInputHandler;
    private IInputHandler<InfuseStorage, InfuseStorage> infuseInputHandler;
    private IOutputHandler<ItemStack>[] itemOutputHandlers;
    private IOutputHandler<ChanceOutput>[] chanceOutputHandlers;
    private IOutputHandler<ChanceOutput2>[] chance2OutputHandlers;
    private IOutputHandler<GasStack> gasOutputHandler;
    private IOutputHandler<PressurizedOutput>[] pressurizedOutputHandlers;


    @Nonnull
    private RecipeType recipeType = RecipeType.SMELTING;
    @Nonnull
    private FactoryRecipeHandler recipeHandler = FactoryRecipeHandler.forRecipeType(recipeType);

    protected TileEntityFactory(FactoryTier type, MachineType machine) {
        super("null", machine, 0);
        tier = type;
        int processCount = type.processes;
        progress = new int[processCount];
        isActive = false;
        recipeCacheLookupMonitors = new FactoryRecipeCacheLookupMonitor[processCount];
        recheckAllRecipeErrors = new BooleanSupplier[processCount];
        errorTracker = new ErrorTracker(FACTORY_ERROR_TYPES, FACTORY_GLOBAL_ERROR_TYPES, processCount);
        activeStates = new boolean[processCount];
        usedSoFar = new long[processCount];
        for (int process = 0; process < processCount; process++) {
            int processNumber = process;
            recipeCacheLookupMonitors[processNumber] = new FactoryRecipeCacheLookupMonitor<>(this, processNumber, this::markSortingNeeded);
            int checkOffset = ThreadLocalRandom.current().nextInt(RECIPE_CHECK_FREQUENCY);
            recheckAllRecipeErrors[processNumber] = () -> shouldRecheckAllRecipeErrors(checkOffset);
        }
        maxInfuse = BASE_MAX_INFUSE * processCount;
        BASE_TICKS_REQUIRED = 200;
        baseTotalUsage = BASE_TICKS_REQUIRED;
        gasUsageMultiplier = (usedSoFar, operatingTicks) -> {
            if (usesStatisticalSecondaryFuel()) {
                return StatUtils.inversePoisson(gasPerTickMeanMultiplier);
            }
            long baseRemaining = baseTotalUsage - usedSoFar;
            int remainingTicks = getTicksRequired(null) - operatingTicks;
            if (baseRemaining < remainingTicks) {
                return 0;
            } else if (baseRemaining == remainingTicks) {
                return 1;
            }
            return Math.max(MathUtils.clampToLong(baseRemaining / (double) remainingTicks), 0);
        };
        initializeRecipeTypeState(recipeType);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.ENERGY, TransmissionType.GAS, TransmissionType.FLUID);
        initializeInventorySlots();
        setupFactoryItemConfig();
        applyDefaultItemConfig();

        setupFactoryFluidConfig();
        setupFactoryGasConfig();
        applyDefaultGasConfig();

        configComponent.setInputConfig(TransmissionType.ENERGY);

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM, TransmissionType.GAS);
        setRecipeType(recipeType);
    }

    private void setupFactoryFluidConfig() {
        configComponent.addFluidSlotInfo(DataType.INPUT, fluidTank);
    }

    private void setupFactoryGasConfig() {
        configComponent.setupGasIOConfig(gasTank, gasOutTank);
    }

    private void applyDefaultItemConfig() {
        configComponent.setConfig(TransmissionType.ITEM, DataType.EXTRA, DataType.INPUT, DataType.INPUT, DataType.ENERGY, DataType.INPUT, DataType.OUTPUT);
    }

    private void applyDefaultGasConfig() {
        configComponent.setConfig(TransmissionType.GAS, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.OUTPUT);
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        FluidTankHelper builder = createFluidTankHelper();
        BasicFluidTank inputFluidTank = getOrCreateFluidTank(listener);
        if (hasFluidInput()) {
            builder.addTank(inputFluidTank);
        }
        return builder.build();
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        GasTankHelper builder = createGasTankHelper();
        BasicGasTank inputGasTank = getOrCreateGasTank(listener);
        BasicGasTank outputGasTank = getOrCreateGasOutputTank(listener);
        if (hasGasInput()) {
            builder.addTank(inputGasTank);
        }
        if (hasGasOutput()) {
            builder.addTank(outputGasTank);
        }
        return builder.build();
    }

    private BasicGasTank getOrCreateGasTank(IContentsListener listener) {
        if (gasTank == null) {
            gasTank = BasicGasTank.input(getFactoryTankCapacity(), this::canInsertInputGas, this::isValidInputGas, markAllMonitorsChanged(listener));
        }
        return gasTank;
    }

    private BasicGasTank getOrCreateGasOutputTank(IContentsListener listener) {
        if (gasOutTank == null) {
            gasOutTank = BasicGasTank.output(getFactoryTankCapacity(), markAllMonitorsChanged(listener));
        }
        return gasOutTank;
    }

    private BasicFluidTank getOrCreateFluidTank(IContentsListener listener) {
        if (fluidTank == null) {
            fluidTank = BasicFluidTank.input(getFactoryTankCapacity(), this::canInsertInputFluid, this::isValidInputFluid, markAllMonitorsChanged(listener));
        }
        return fluidTank;
    }

    private int getFactoryTankCapacity() {
        return BASE_MAX_TANK * getProcessCount();
    }

    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        return super.getInitialEnergyContainers(markAllMonitorsUnpaused(listener));
    }

    @Override
    public void setEnergy(double energy) {
        double previous = getEnergy();
        super.setEnergy(energy);
        if (recipeCacheLookupMonitors != null && world != null && !world.isRemote && Double.compare(previous, getEnergy()) != 0) {
            unpauseRecipeCaches();
        }
    }

    @Override
    protected boolean persistFluidTanks() {
        return false;
    }

    @Override
    protected boolean persistGasTanks() {
        return false;
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        int processCount = getProcessCount();
        initializeRecipeHandlers(processCount);
        OutputInventorySlot[] outputSlots = createProcessOutputSlots(listener, processCount, false);
        OutputInventorySlot[] secondaryOutputSlots = createProcessOutputSlots(listener, processCount, true);
        processInfoSlots = new ProcessInfo[processCount];

        InventorySlotHelper builder = createInventorySlotHelper();
        addProcessSlots(builder, listener, outputSlots, secondaryOutputSlots, processCount);
        addSharedInventorySlots(builder, listener);
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private void initializeRecipeHandlers(int processCount) {
        processInputSlots = new ArrayList<>(processCount);
        processOutputSlots = new ArrayList<>(processCount * 2);
        itemInputHandlers = new IInputHandler[processCount];
        itemOutputHandlers = new IOutputHandler[processCount];
        chanceOutputHandlers = new IOutputHandler[processCount];
        chance2OutputHandlers = new IOutputHandler[processCount];
        pressurizedOutputHandlers = new IOutputHandler[processCount];
        gasInputHandler = InputHelper.getGasInputHandler(gasTank, RecipeError.NOT_ENOUGH_INPUT, this::depleteRecipeInput);
        secondaryGasInputHandler = InputHelper.getGasInputHandler(gasTank, RecipeError.NOT_ENOUGH_SECONDARY_INPUT, this::depleteRecipeInput);
        constantGasInputHandler = InputHelper.getConstantGasInputHandler(gasTank, RecipeError.NOT_ENOUGH_SECONDARY_INPUT, false, this::depleteRecipeInput);
        fluidInputHandler = InputHelper.getFluidInputHandler(fluidTank, RecipeError.NOT_ENOUGH_SECONDARY_INPUT, this::depleteRecipeInput);
        infuseInputHandler = InputHelper.getInfuseInputHandler(infuseStored, RecipeError.NOT_ENOUGH_SECONDARY_INPUT, this::depleteRecipeInput);
        gasOutputHandler = OutputHelper.getGasOutputHandler(gasOutTank, RecipeError.NOT_ENOUGH_OUTPUT_SPACE);
    }

    private OutputInventorySlot[] createProcessOutputSlots(IContentsListener listener, int processCount, boolean secondary) {
        OutputInventorySlot[] outputSlots = new OutputInventorySlot[processCount];
        int y = getProcessOutputSlotY(secondary);
        for (int process = 0; process < processCount; process++) {
            outputSlots[process] = OutputInventorySlot.at(markSortingAndRecipeCacheChanged(listener, process), getProcessSlotX(process), y);
        }
        return outputSlots;
    }

    private void addSharedInventorySlots(InventorySlotHelper builder, IContentsListener listener) {
        extraSlot = builder.addSlot(FactoryExtraInventorySlot.create(this, this::isValidExtraSlotItem, this::isValidStoredExtraSlotItem,
              markAllMonitorsChanged(listener), EXTRA_SLOT_X, getExtraSlotY()));
        extraSlot.setSlotType(ContainerSlotType.EXTRA);
        extraSlot.setEnabledSupplier(this::isExtraSlotVisible);
        extraSlot.setPositionSuppliers(() -> EXTRA_SLOT_X, this::getExtraSlotY);
        extraItemInputHandler = InputHelper.getInputHandler(extraSlot, RecipeError.NOT_ENOUGH_SECONDARY_INPUT, this::depleteRecipeInput);
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, ENERGY_SLOT_X, ENERGY_SLOT_Y));
    }

    private void addProcessSlots(InventorySlotHelper builder, IContentsListener listener, OutputInventorySlot[] outputSlots,
          OutputInventorySlot[] secondaryOutputSlots, int processCount) {
        for (int process = 0; process < processCount; process++) {
            int processNumber = process;
            FactoryInputInventorySlot inputSlot = FactoryInputInventorySlot.create(this, processNumber, outputSlots[processNumber],
                  secondaryOutputSlots[processNumber],
                  markRecipeCacheChanged(listener, processNumber), getProcessSlotX(processNumber), PROCESS_INPUT_SLOT_Y);
            inputSlot.setEnabledSupplier(() -> isInputSlotVisible(processNumber));
            inputSlot.setPositionSuppliers(() -> getProcessSlotX(processNumber), () -> PROCESS_INPUT_SLOT_Y);
            inputSlot.tracksWarnings(slot -> slot.warning(WarningType.NO_MATCHING_RECIPE, getWarningCheck(RecipeError.NOT_ENOUGH_INPUT, processNumber)));

            OutputInventorySlot outputSlot = outputSlots[processNumber];
            outputSlot.setEnabledSupplier(() -> isOutputSlotVisible(processNumber, false));
            outputSlot.setPositionSuppliers(() -> getProcessSlotX(processNumber), () -> getProcessOutputSlotY(false));
            outputSlot.tracksWarnings(slot -> slot.warning(WarningType.NO_SPACE_IN_OUTPUT, getWarningCheck(RecipeError.NOT_ENOUGH_OUTPUT_SPACE, processNumber)));

            OutputInventorySlot secondaryOutputSlot = secondaryOutputSlots[processNumber];
            secondaryOutputSlot.setEnabledSupplier(() -> isOutputSlotVisible(processNumber, true));
            secondaryOutputSlot.setPositionSuppliers(() -> getProcessSlotX(processNumber), () -> getProcessOutputSlotY(true));
            secondaryOutputSlot.tracksWarnings(slot -> slot.warning(WarningType.NO_SPACE_IN_OUTPUT, getWarningCheck(RecipeError.NOT_ENOUGH_OUTPUT_SPACE, processNumber)));

            processInfoSlots[processNumber] = new ProcessInfo(processNumber, builder.addSlot(inputSlot), outputSlot, secondaryOutputSlot);
            builder.addSlot(outputSlot);
            builder.addSlot(secondaryOutputSlot);
            processInputSlots.add(inputSlot);
            processOutputSlots.add(outputSlot);
            processOutputSlots.add(secondaryOutputSlot);
            itemInputHandlers[processNumber] = InputHelper.getInputHandler(inputSlot, RecipeError.NOT_ENOUGH_INPUT, this::depleteRecipeInput);
            itemOutputHandlers[processNumber] = OutputHelper.getOutputHandler(outputSlot, RecipeError.NOT_ENOUGH_OUTPUT_SPACE);
            chanceOutputHandlers[processNumber] = OutputHelper.getOutputHandler(outputSlot, secondaryOutputSlot, RecipeError.NOT_ENOUGH_OUTPUT_SPACE);
            chance2OutputHandlers[processNumber] = OutputHelper.getOutputHandlerChance2(outputSlot, RecipeError.NOT_ENOUGH_OUTPUT_SPACE);
            pressurizedOutputHandlers[processNumber] = OutputHelper.getOutputHandler(outputSlot, gasOutTank, RecipeError.NOT_ENOUGH_OUTPUT_SPACE);
        }
    }

    private int getProcessOutputSlotY(boolean secondary) {
        return secondary ? PROCESS_SECONDARY_OUTPUT_SLOT_Y : PROCESS_OUTPUT_SLOT_Y;
    }

    private IContentsListener markRecipeCacheChanged(IContentsListener listener, int process) {
        return () -> {
            listener.onContentsChanged();
            getRecipeCacheLookupMonitor(process).onChange();
        };
    }

    private IContentsListener markSortingAndRecipeCacheChanged(IContentsListener listener, int process) {
        return () -> {
            listener.onContentsChanged();
            markSortingNeeded();
            getRecipeCacheLookupMonitor(process).onChange();
        };
    }

    private IContentsListener markAllMonitorsChanged(IContentsListener listener) {
        return () -> {
            listener.onContentsChanged();
            for (RecipeCacheLookupMonitor<MachineRecipe<?, ?, ?>> monitor : recipeCacheLookupMonitors) {
                monitor.onChange();
            }
        };
    }

    private IContentsListener markAllMonitorsUnpaused(IContentsListener listener) {
        return () -> {
            listener.onContentsChanged();
            unpauseRecipeCaches();
        };
    }

    private void unpauseRecipeCaches() {
        if (recipeCacheLookupMonitors != null) {
            for (RecipeCacheLookupMonitor<MachineRecipe<?, ?, ?>> monitor : recipeCacheLookupMonitors) {
                if (monitor != null) {
                    monitor.unpause();
                }
            }
        }
    }

    private FactoryRecipeCacheLookupMonitor<MachineRecipe<?, ?, ?>> getRecipeCacheLookupMonitor(int process) {
        return recipeCacheLookupMonitors[process];
    }

    private FactoryRecipeCacheLookupMonitor<MachineRecipe<?, ?, ?>> getRecipeCacheLookupMonitor(ProcessInfo processInfo) {
        return getRecipeCacheLookupMonitor(processInfo.process());
    }

    private BooleanSupplier getRecheckAllRecipeErrors(ProcessInfo processInfo) {
        return recheckAllRecipeErrors[processInfo.process()];
    }

    private void setupFactoryItemConfig() {
        configComponent.setupFactoryItemConfig(getProcessInputSlots(), getProcessOutputSlots(), energySlot, extraSlot);
    }

    private List<IInventorySlot> getProcessInputSlots() {
        return processInputSlots;
    }

    private List<IInventorySlot> getProcessOutputSlots() {
        return processOutputSlots;
    }

    private int getExtraSlotY() {
        return getCurrentRecipeHandler().getExtraSlotY();
    }

    public int getExtraSlotGuiY() {
        return getExtraSlotY() - 1;
    }

    private int getProcessSlotBaseX() {
        return tier == FactoryTier.BASIC ? 55 : tier == FactoryTier.ADVANCED ? 35 : tier == FactoryTier.ELITE ? 29 : 27;
    }

    private int getProcessSlotSpacing() {
        return tier == FactoryTier.BASIC ? 38 : tier == FactoryTier.ADVANCED ? 26 : 19;
    }

    public int getProcessSlotX(int processNumber) {
        return getProcessSlotBaseX() + (processNumber * getProcessSlotSpacing());
    }

    public int getProcessGuiSlotX(int processNumber) {
        return getProcessSlotX(processNumber) - 1;
    }

    public int getProcessProgressX(int processNumber) {
        return getProcessSlotX(processNumber) + 4;
    }

    public int getProcessCount() {
        return tier.processes;
    }

    public FactoryTier getTier() {
        return tier;
    }

    public int getFactoryGuiWidthExtra() {
        return tier == FactoryTier.ULTIMATE ? 34 : 0;
    }

    public int getFactoryTankGaugeWidth() {
        return tier == FactoryTier.BASIC ? 94 : tier == FactoryTier.ADVANCED ? 122 : tier == FactoryTier.ELITE ? 132 : 170;
    }

    public int getFactoryGuiHeightExtra() {
        return getCurrentRecipeHandler().getFactoryGuiHeightExtra();
    }

    public boolean hasSecondaryResourceBar() {
        return getCurrentRecipeHandler().hasSecondaryResourceBar();
    }

    public boolean hasSecondaryResourceDump() {
        return getCurrentRecipeHandler().hasSecondaryResourceDump();
    }

    public boolean usesGasSecondaryResourceBar() {
        return getCurrentRecipeHandler().usesGasSecondaryResourceBar();
    }

    public boolean usesPressurizedTankBars() {
        return getCurrentRecipeHandler().usesPressurizedTankBars();
    }

    public boolean hasTallFactoryGui() {
        return getFactoryGuiHeightExtra() == 21;
    }

    public int getPlayerInventoryXOffset() {
        return tier == FactoryTier.ULTIMATE ? 26 : 8;
    }

    public int getPlayerInventoryYOffset() {
        if (usesPressurizedTankBars()) {
            return 103;
        } else if (hasSecondaryResourceBar()) {
            return 95;
        } else if (hasTallFactoryGui()) {
            return 105;
        }
        return 85;
    }

    public int getPlayerInventoryGuiX() {
        return getPlayerInventoryXOffset();
    }

    public int getPlayerInventoryGuiY() {
        return getPlayerInventoryYOffset() - 10;
    }

    public int getSecondaryResourceBarWidth() {
        if (usesPressurizedTankBars()) {
            return getProcessSlotX(getProcessCount() - 1) + 18 - getSecondaryResourceBarX() - 2;
        }
        return getSecondaryResourceDumpButtonX() - 10;
    }

    public int getSecondaryResourceDumpButtonX() {
        return tier == FactoryTier.ULTIMATE ? 182 : 148;
    }

    public int getSecondaryResourceClickMaxX() {
        return tier == FactoryTier.ULTIMATE ? 180 : 146;
    }

    public int getSecondaryResourceClickYOffset() {
        return getCurrentRecipeHandler().getSecondaryResourceClickYOffset();
    }

    public int getSecondaryResourceBarX() {
        return 7;
    }

    public int getSecondaryResourceBarY() {
        return 76;
    }

    public int getSecondaryResourceDumpButtonY() {
        return 76;
    }

    public int getSecondaryResourceClickMinX() {
        return 8;
    }

    public int getSecondaryResourceClickMinY() {
        return 77 + getSecondaryResourceClickYOffset();
    }

    public int getSecondaryResourceClickMaxY() {
        return 82 + getSecondaryResourceClickYOffset();
    }

    public boolean shouldShowInfuseBar() {
        return hasSecondaryResourceBar();
    }

    @Override
    public void dump() {
        getCurrentRecipeHandler().dumpSecondaryResource(this);
    }

    public boolean showsInputGasGauge() {
        return getCurrentRecipeHandler().showsInputGasGauge(this);
    }

    public int getInputGasGaugeX() {
        return getCurrentRecipeHandler().getInputGasGaugeX(this);
    }

    public int getInputGasGaugeY() {
        return getCurrentRecipeHandler().getInputGasGaugeY(this);
    }

    public boolean usesStandardInputGasGauge() {
        return getCurrentRecipeHandler().usesStandardInputGasGauge(this);
    }

    public boolean usesSlotInputGasGauge() {
        return getCurrentRecipeHandler().usesSlotInputGasGauge(this);
    }

    public boolean usesRedInputGasGauge() {
        return getCurrentRecipeHandler().usesRedInputGasGauge();
    }

    public int getOutputGasGaugeX() {
        return getCurrentRecipeHandler().getOutputGasGaugeX(this);
    }

    public int getOutputGasGaugeY() {
        return getCurrentRecipeHandler().getOutputGasGaugeY();
    }

    public boolean showsOutputGasGauge() {
        return getCurrentRecipeHandler().showsOutputGasGauge(this);
    }

    public boolean usesStandardOutputGasGauge() {
        return usesStandardTierGauge();
    }

    public boolean usesSlotOutputGasGauge() {
        return getCurrentRecipeHandler().usesSlotOutputGasGauge(this);
    }

    public boolean usesHorizontalOutputGasGauge() {
        return getCurrentRecipeHandler().usesHorizontalOutputGasGauge(this);
    }

    public int getInputFluidGaugeX() {
        return getCurrentRecipeHandler().getInputFluidGaugeX(this);
    }

    public int getInputFluidGaugeY() {
        return getCurrentRecipeHandler().getInputFluidGaugeY(this);
    }

    public boolean showsInputFluidGauge() {
        return getCurrentRecipeHandler().showsInputFluidGauge(this);
    }

    public boolean usesRedInputFluidGauge() {
        return getCurrentRecipeHandler().usesRedInputFluidGauge();
    }

    public boolean usesSlotInputFluidGauge() {
        return getCurrentRecipeHandler().usesSlotInputFluidGauge(this);
    }

    public boolean usesHorizontalInputFluidGauge() {
        return getCurrentRecipeHandler().usesHorizontalInputFluidGauge(this);
    }

    private boolean usesStandardTierGauge() {
        return tier != FactoryTier.BASIC;
    }

    private boolean isInputSlotVisible(int processNumber) {
        return hasItemInput() && processNumber >= 0;
    }

    private boolean isOutputSlotVisible(int processNumber, boolean secondary) {
        if (secondary) {
            return hasSecondaryItemOutput();
        }
        return hasItemOutput();
    }

    public boolean isExtraSlotVisible() {
        return getCurrentRecipeHandler().isExtraSlotVisible(this);
    }

    private boolean isValidExtraSlotItem(ItemStack stack) {
        if (recipeType == null || stack.isEmpty()) {
            return false;
        }
        return getCurrentRecipeHandler().isValidExtraSlotItem(this, stack);
    }

    private boolean isValidStoredExtraSlotItem(ItemStack stack) {
        return isValidExtraSlotItem(stack) || getCurrentRecipeHandler().isValidExtraSlotContainerItem(this, stack);
    }

    public boolean getExtraSlotLimitMultiplier() {
        return getCurrentRecipeHandler().getExtraSlotLimitMultiplier(this);
    }

    public static ItemStack getRecipeInput(MachineRecipe<?, ?, ?> recipe) {
        if (recipe.recipeInput instanceof ItemStackInput input) {
            return input.ingredient;
        } else if (recipe.recipeInput instanceof AdvancedMachineInput advancedInput) {
            return advancedInput.itemStack;
        } else if (recipe.recipeInput instanceof DoubleMachineInput doubleMachineInput) {
            return doubleMachineInput.itemStack;
        } else if (recipe.recipeInput instanceof InfusionInput infusionInput) {
            return infusionInput.inputStack;
        } else if (recipe.recipeInput instanceof PressurizedInput pressurizedInput) {
            return pressurizedInput.getSolid();
        } else if (recipe.recipeInput instanceof NucleosynthesizerInput input) {
            return input.getSolid();
        } else {
            return ItemStack.EMPTY;
        }
    }

    private static void setSlotStackUnchecked(IInventorySlot slot, @Nonnull ItemStack stack) {
        if (slot instanceof BasicInventorySlot basicSlot) {
            basicSlot.setStackUnchecked(stack);
        } else {
            slot.setStack(stack);
        }
    }

    private void setUpgradeProgress(ProcessInfo processInfo, int ticks) {
        setProgress(processInfo, ticks);
    }

    private void copyInfusionFrom(InfuseStorage source) {
        infuseStored.copyFrom(source);
    }

    private void refreshUpgradeOutputData() {
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM, TransmissionType.GAS);
    }

    public void markUpgraded() {
        upgraded = true;
    }

    public boolean isUpgrade = true;

    @Override
    public boolean canInstallUpgrade(BaseTier upgradeTier) {
        return getNextFactoryUpgradeTier(upgradeTier) != null;
    }

    @Nullable
    private FactoryTier getNextFactoryUpgradeTier(BaseTier upgradeTier) {
        int nextTier = tier.ordinal() + 1;
        if (nextTier >= FactoryTier.values().length || upgradeTier.ordinal() != nextTier) {
            return null;
        }
        return FactoryTier.values()[nextTier];
    }

    @Nullable
    @Override
    public IBlockState getUpgradeResult(BaseTier upgradeTier) {
        FactoryTier targetTier = getNextFactoryUpgradeTier(upgradeTier);
        if (targetTier == null) {
            return null;
        }
        return getFactoryState(targetTier);
    }

    @Nullable
    private IBlockState getFactoryState(FactoryTier factoryTier) {
        return switch (factoryTier) {
            case BASIC -> MekanismBlocks.MachineBlock.getStateFromMeta(5);
            case ADVANCED -> MekanismBlocks.MachineBlock.getStateFromMeta(6);
            case ELITE -> MekanismBlocks.MachineBlock.getStateFromMeta(7);
            case ULTIMATE -> MekanismBlocks.MachineBlock3.getStateFromMeta(7);
        };
    }

    @Override
    public void prepareForUpgrade() {
        isUpgrade = false;
    }

    @Nullable
    @Override
    public IUpgradeData getUpgradeData(BaseTier upgradeTier) {
        if (!canInstallUpgrade(upgradeTier)) {
            return null;
        }
        return new FactoryUpgradeData(upgradeTier, facing, clientFacing, ticker, redstone, redstoneLastTick, doAutoSync, electricityStored.get(), isActive,
              prevEnergy, getControlType(), writeUpgradeComponentData(), recipeType, isSorting(), progress, usedSoFar, infuseStored, energySlot.getStack(),
              extraSlot.getStack(), getUpgradeInputStacks(), getUpgradeOutputStacks(), getUpgradeSecondaryOutputStacks(), gasTank.getGas(), gasOutTank.getGas(),
              fluidTank.getFluid());
    }

    @Nonnull
    private NBTTagCompound writeUpgradeComponentData() {
        NBTTagCompound componentData = new NBTTagCompound();
        upgradeComponent.write(componentData);
        configComponent.write(componentData);
        ejectorComponent.write(componentData);
        securityComponent.write(componentData);
        return componentData;
    }

    private ItemStack[] getUpgradeInputStacks() {
        return getUpgradeProcessStacks(ProcessInfo::inputSlot);
    }

    private ItemStack[] getUpgradeOutputStacks() {
        return getUpgradeProcessStacks(ProcessInfo::outputSlot);
    }

    private ItemStack[] getUpgradeSecondaryOutputStacks() {
        return getUpgradeProcessStacks(ProcessInfo::secondaryOutputSlot);
    }

    private ItemStack[] getUpgradeProcessStacks(Function<ProcessInfo, IInventorySlot> slotGetter) {
        ItemStack[] stacks = new ItemStack[getProcessCount()];
        for (ProcessInfo processInfo : processInfoSlots) {
            IInventorySlot slot = slotGetter.apply(processInfo);
            stacks[processInfo.process()] = slot == null ? ItemStack.EMPTY : slot.getStack();
        }
        return stacks;
    }

    @Override
    public boolean parseUpgradeData(IUpgradeData upgradeData) {
        if (upgradeData instanceof FactoryUpgradeData data && data.getUpgradeTier() == tier.getBaseTier()) {
            applyUpgradeData(data);
            return true;
        }
        return false;
    }

    private void applyUpgradeData(FactoryUpgradeData data) {
        facing = data.facing;
        clientFacing = data.clientFacing;
        ticker = data.ticker;
        redstone = data.redstone;
        redstoneLastTick = data.redstoneLastTick;
        doAutoSync = data.doAutoSync;
        electricityStored.set(data.energy);
        isActive = data.active;
        prevEnergy = data.previousEnergy;
        setRecipeType(data.recipeType);
        setSorting(data.sorting);
        setControlType(data.controlType);
        applyUpgradeComponents(data.componentData);
        copyInfusionFrom(data.infuseStored);
        applyUpgradeProgress(data.progress);
        applyUpgradeUsedSoFar(data.usedSoFar);
        applyUpgradeInventory(data);
        gasTank.setGas(data.inputGas);
        gasOutTank.setGas(data.outputGas);
        fluidTank.setFluid(data.inputFluid);
        sanitizeAndClampTanks();
        upgradeComponent.getSupportedTypes().forEach(this::recalculateUpgradables);
        markUpgraded();
        isUpgrade = true;
        markNoUpdateSync();
        Mekanism.packetHandler.sendUpdatePacket(this);
    }

    private void applyUpgradeComponents(NBTTagCompound componentData) {
        upgradeComponent.read(componentData);
        configComponent.read(componentData);
        ejectorComponent.read(componentData);
        securityComponent.read(componentData);
        refreshUpgradeOutputData();
    }

    private void applyUpgradeProgress(int[] savedProgress) {
        for (ProcessInfo processInfo : processInfoSlots) {
            int process = processInfo.process();
            setUpgradeProgress(processInfo, process < savedProgress.length ? savedProgress[process] : 0);
        }
    }

    private void applyUpgradeUsedSoFar(long[] savedUsedSoFar) {
        for (ProcessInfo processInfo : processInfoSlots) {
            int process = processInfo.process();
            setSavedUsedSoFar(processInfo, process < savedUsedSoFar.length ? savedUsedSoFar[process] : 0);
        }
    }

    private void applyUpgradeInventory(FactoryUpgradeData data) {
        setSlotStackUnchecked(energySlot, data.energySlot);
        setSlotStackUnchecked(extraSlot, data.extraSlot);
        applyUpgradeProcessInventory(data);
    }

    private void applyUpgradeProcessInventory(FactoryUpgradeData data) {
        applyUpgradeProcessStacks(data.inputSlots, ProcessInfo::inputSlot);
        applyUpgradeProcessStacks(data.outputSlots, ProcessInfo::outputSlot);
        applyUpgradeProcessStacks(data.secondaryOutputSlots, ProcessInfo::secondaryOutputSlot);
    }

    private void applyUpgradeProcessStacks(ItemStack[] stacks, Function<ProcessInfo, IInventorySlot> slotGetter) {
        for (ProcessInfo processInfo : processInfoSlots) {
            int process = processInfo.process();
            IInventorySlot slot = slotGetter.apply(processInfo);
            if (slot != null) {
                setSlotStackUnchecked(slot, getUpgradeSlotStack(stacks, process));
            }
        }
    }

    @Nonnull
    private ItemStack getUpgradeSlotStack(ItemStack[] stacks, int process) {
        return process < stacks.length ? stacks[process] : ItemStack.EMPTY;
    }

    @Override
    public void addTileSyncTask() {
    }

    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        if (ticker == 1) {
            Mekanism.EXECUTE_MANAGER.addSyncTask(() -> world.notifyNeighborsOfStateChange(getPos(), getBlockType(), true));
        }
        dropLegacyTypeSlotItems();
        fillEnergySlot();
        handleSecondaryFuel();
        if (shouldSortInventory()) {
            markSortingNotNeeded();
            sortInventory();
        } else if (!sortingNeeded && areRecipeCachesInvalid()) {
            markSortingNeeded();
        }
        double prev = getEnergy();
        updateSecondaryEnergyThisTick();
        processFactoryRecipes();
        markRecipeCachesObserved();
        updateActiveStateAndLastUsage(prev);
        prevEnergy = getEnergy();
    }

    private void fillEnergySlot() {
        energySlot.fillContainerOrConvert();
    }

    private void dropLegacyTypeSlotItems() {
        if (legacyTypeSlotDrops.isEmpty() || world == null || world.isRemote) {
            return;
        }
        for (ItemStack stack : legacyTypeSlotDrops) {
            InventoryUtils.dropStack(stack.copy(), itemStack -> Block.spawnAsEntity(world, pos, itemStack));
        }
        legacyTypeSlotDrops.clear();
    }

    private void updateSecondaryEnergyThisTick() {
        secondaryEnergyThisTick = getCurrentRecipeHandler().getSecondaryEnergyThisTick(this);
    }

    private void processFactoryRecipes() {
        for (ProcessInfo processInfo : processInfoSlots) {
            processFactoryRecipe(processInfo);
        }
    }

    private void processFactoryRecipe(ProcessInfo processInfo) {
        if (!getRecipeCacheLookupMonitor(processInfo).updateAndProcess()) {
            setActiveState(processInfo, false);
            if (!getCurrentRecipeHandler().shouldKeepProgressWithoutRecipe(this, processInfo.inputSlot().getStack())) {
                setProgress(processInfo, 0);
            }
        }
    }

    private boolean isAnyProcessActive() {
        for (boolean active : activeStates) {
            if (active) {
                return true;
            }
        }
        return false;
    }

    private void updateActiveStateAndLastUsage(double previousEnergy) {
        boolean isActive = isAnyProcessActive();
        setActive(isActive);
        setLastUsage(isActive ? previousEnergy - getEnergy() : 0);
    }

    private boolean shouldSortInventory() {
        return sortingNeeded && isSorting() && hasItemInput();
    }

    private boolean areRecipeCachesInvalid() {
        int recipeVersion = RecipeHandler.getGlobalRecipeVersion();
        if (observedRecipeVersion != recipeVersion) {
            recipeCachesInvalid = true;
        }
        return recipeCachesInvalid || CommonWorldTickHandler.flushTagAndRecipeCaches;
    }

    private void markRecipeCachesObserved() {
        observedRecipeVersion = RecipeHandler.getGlobalRecipeVersion();
        recipeCachesInvalid = false;
    }

    private void markSortingNeeded() {
        sortingNeeded = true;
    }

    private void markSortingNotNeeded() {
        sortingNeeded = false;
    }

    private void sortInventory() {
        inventorySorter.sort();
    }

    private int getBaseTicksRequired(@Nullable MachineRecipe<?, ?, ?> recipe) {
        if (usesFixedBaseTicks()) {
            return 1;
        }
        return getCurrentRecipeHandler().getBaseTicksRequired(this, recipe);
    }

    private boolean usesFixedBaseTicks() {
        return getCurrentRecipeHandler().usesFixedBaseTicks();
    }

    public boolean isSorting() {
        return sorting;
    }

    public void toggleSorting() {
        setSorting(!isSorting());
    }

    private void setSorting(boolean sorting) {
        this.sorting = sorting;
    }

    public double getLastUsage() {
        return lastUsage;
    }

    private void setLastUsage(double lastUsage) {
        this.lastUsage = lastUsage;
    }

    public int getTicksRequired() {
        return ticksRequired;
    }

    public int getOperationsPerTick() {
        return getOperationsPerTick(null);
    }

    public int getProgress(int cacheIndex) {
        return progress[cacheIndex];
    }

    private int getProgress(ProcessInfo processInfo) {
        return getProgress(processInfo.process());
    }

    private void setProgress(int cacheIndex, int ticks) {
        progress[cacheIndex] = ticks;
    }

    private void setProgress(ProcessInfo processInfo, int ticks) {
        setProgress(processInfo.process(), ticks);
    }

    private void readProgressFromNBT(NBTTagCompound nbtTags) {
        if (nbtTags.hasKey(NBTConstants.PROGRESS, TAG_INT_ARRAY)) {
            readProgressArrayFromNBT(nbtTags.getIntArray(NBTConstants.PROGRESS));
        } else {
            readLegacyProgressFromNBT(nbtTags);
        }
    }

    private void readProgressArrayFromNBT(int[] savedProgress) {
        if (getProcessCount() != savedProgress.length) {
            Arrays.fill(progress, 0);
        }
        for (ProcessInfo processInfo : processInfoSlots) {
            int process = processInfo.process();
            if (process < savedProgress.length) {
                setProgress(processInfo, savedProgress[process]);
            }
        }
    }

    private void readLegacyProgressFromNBT(NBTTagCompound nbtTags) {
        for (ProcessInfo processInfo : processInfoSlots) {
            setProgress(processInfo, nbtTags.getInteger(getLegacyProgressKey(processInfo)));
        }
    }

    private String getLegacyProgressKey(ProcessInfo processInfo) {
        return LEGACY_PROGRESS_KEY_PREFIX + processInfo.process();
    }

    private void writeProgressToNBT(NBTTagCompound nbtTags) {
        nbtTags.setIntArray(NBTConstants.PROGRESS, Arrays.copyOf(progress, progress.length));
    }

    private void readUsedSoFarFromNBT(NBTTagCompound nbtTags) {
        if (nbtTags.hasKey(NBTConstants.USED_SO_FAR, TAG_LONG_ARRAY)) {
            long[] savedUsed = getLongArray(nbtTags, NBTConstants.USED_SO_FAR);
            if (savedUsed != null) {
                readUsedSoFarArrayFromNBT(savedUsed);
                return;
            }
        }
        readLegacyUsedSoFarFromNBT(nbtTags);
    }

    private void readUsedSoFarArrayFromNBT(long[] savedUsed) {
        if (getProcessCount() != savedUsed.length) {
            Arrays.fill(usedSoFar, 0);
        }
        for (ProcessInfo processInfo : processInfoSlots) {
            int process = processInfo.process();
            if (process < savedUsed.length) {
                setSavedUsedSoFar(processInfo, savedUsed[process]);
            }
        }
    }

    private void readLegacyUsedSoFarFromNBT(NBTTagCompound nbtTags) {
        for (ProcessInfo processInfo : processInfoSlots) {
            setSavedUsedSoFar(processInfo, nbtTags.getLong(getLegacyUsedSoFarKey(processInfo)));
        }
    }

    private String getLegacyUsedSoFarKey(ProcessInfo processInfo) {
        return LEGACY_USED_SO_FAR_KEY_PREFIX + processInfo.process();
    }

    private void writeUsedSoFarToNBT(NBTTagCompound nbtTags) {
        nbtTags.setTag(NBTConstants.USED_SO_FAR, new NBTTagLongArray(Arrays.copyOf(usedSoFar, usedSoFar.length)));
    }

    @Nullable
    private static long[] getLongArray(NBTTagCompound nbtTags, String key) {
        NBTBase tag = nbtTags.getTag(key);
        if (tag instanceof NBTTagLongArray longArray) {
            if (NBT_LONG_ARRAY_DATA == null) {
                Mekanism.logger.error("Unable to read long array NBT key '{}' because the long array data field was not found.", key);
                return null;
            }
            try {
                return (long[]) NBT_LONG_ARRAY_DATA.get(longArray);
            } catch (IllegalAccessException e) {
                Mekanism.logger.error("Unable to read long array NBT key '{}'.", key, e);
            }
        }
        return null;
    }

    @Nullable
    private static Field findLongArrayDataField() {
        for (Field field : NBTTagLongArray.class.getDeclaredFields()) {
            if (field.getType() == long[].class) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    private void setActiveState(boolean state, int cacheIndex) {
        activeStates[cacheIndex] = state;
    }

    private void setActiveState(ProcessInfo processInfo, boolean state) {
        setActiveState(state, processInfo.process());
    }

    private int getTicksRequired(@Nullable MachineRecipe<?, ?, ?> recipe) {
        return MekanismUtils.getTicks(this, getBaseTicksRequired(recipe));
    }

    private double getExtraEnergy(MachineRecipe<?, ?, ?> recipe) {
        return getCurrentRecipeHandler().getExtraEnergy(recipe);
    }

    private double getProcessEnergyPerTick(@Nullable MachineRecipe<?, ?, ?> recipe) {
        if (usesRecipeExtraEnergy()) {
            return MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK + getExtraEnergy(recipe));
        }
        return energyPerTick;
    }

    public boolean showsLongPowerBar() {
        return getCurrentRecipeHandler().showsLongPowerBar();
    }

    private boolean usesRecipeExtraEnergy() {
        return getCurrentRecipeHandler().usesRecipeExtraEnergy();
    }

    private boolean hasSecondaryFuelForRecipeTick() {
        return secondaryEnergyThisTick <= 0 || gasTank.getStored() >= secondaryEnergyThisTick;
    }

    private int getOperationsPerTick(@Nullable MachineRecipe<?, ?, ?> recipe) {
        double energyTick = getProcessEnergyPerTick(recipe);
        int ticksRequired = getTicksRequired(recipe);
        if (canUseConfiguredOperationsPerTick(ticksRequired, energyTick)) {
            return getConfiguredOperationsPerTick(ticksRequired, energyTick);
        }
        return 1;
    }

    private boolean canUseConfiguredOperationsPerTick(int ticksRequired, double energyTick) {
        return MekanismConfig.current().mekce.EnableUpgradeConfigure.val() && ticksRequired <= 0 && energyTick > 0;
    }

    private int getConfiguredOperationsPerTick(int ticksRequired, double energyTick) {
        return Math.max(1, Math.min(getRequestedOperationsPerTick(ticksRequired), getAvailableEnergyOperations(energyTick)));
    }

    private int getRequestedOperationsPerTick(int ticksRequired) {
        return 1 - ticksRequired;
    }

    private int getAvailableEnergyOperations(double energyTick) {
        return 1 + (int) (getEnergy() / energyTick);
    }

    @Nonnull
    public RecipeType getRecipeType() {
        return recipeType;
    }

    private int getRecipeTypeIndex() {
        return recipeType.ordinal();
    }

    public void setRecipeType(@Nonnull RecipeType type) {
        initializeRecipeTypeState(type);
        clearContainerHolderCache();
        clearRecipeCaches();
        if (configComponent != null && ejectorComponent != null) {
            updateTransmissionSupport();
        }
        upgradeComponent.getSupportedTypes().forEach(this::recalculateUpgradables);
        if (hasWorld() && isRemote()) {
            setSoundEvent(type.getSound());
        }
    }

    private void initializeRecipeTypeState(@Nonnull RecipeType type) {
        recipeType = Objects.requireNonNull(type);
        recipeHandler = FactoryRecipeHandler.forRecipeType(recipeType);
        recipeHandler.initializeRecipeTypeState(this);
    }

    private void clearRecipeCaches() {
        for (ProcessInfo processInfo : processInfoSlots) {
            getRecipeCacheLookupMonitor(processInfo).clear();
            clearRecipeErrors(processInfo);
            setActiveState(processInfo, false);
        }
    }

    @Override
    public boolean sideIsConsumer(EnumFacing side) {
        return configComponent.hasSideForData(TransmissionType.ENERGY, facing, DataType.INPUT, side);
    }

    public boolean hasItemInput() {
        return getCurrentRecipeHandler().hasItemInput(this);
    }

    public boolean supportsItem() {
        return getCurrentRecipeHandler().supportsItem(this);
    }

    public boolean hasItemOutput() {
        return getCurrentRecipeHandler().hasItemOutput(this);
    }

    public boolean hasSecondaryItemOutput() {
        return getCurrentRecipeHandler().hasSecondaryItemOutput();
    }


    public boolean hasGasOutput() {
        return getCurrentRecipeHandler().hasGasOutput(this);
    }

    public boolean hasGasInput() {
        return getCurrentRecipeHandler().hasGasInput(this);
    }

    public boolean usesSecondaryGasInput() {
        return getCurrentRecipeHandler().usesSecondaryGasInput();
    }


    public boolean supportsGas() {
        return getCurrentRecipeHandler().supportsGas(this);
    }

    public boolean hasFluidInput() {
        return getCurrentRecipeHandler().hasFluidInput(this);
    }

    public boolean inputProducesOutput(int process, ItemStack fallbackInput, IInventorySlot outputSlot, @Nullable IInventorySlot secondaryOutputSlot,
          boolean updateCache) {
        return isValidProcess(process) && (outputSlot.isEmpty() ||
              getRecipeForInput(getProcessInfo(process), fallbackInput, outputSlot, secondaryOutputSlot, updateCache) != null);
    }

    private boolean inputProducesOutput(ProcessInfo processInfo, ItemStack fallbackInput, boolean updateCache) {
        return processInfo.outputSlot().isEmpty() || getRecipeForInput(processInfo, fallbackInput, updateCache) != null;
    }

    @Nullable
    private MachineRecipe<?, ?, ?> getRecipeForInput(ProcessInfo processInfo, ItemStack fallbackInput, boolean updateCache) {
        return getRecipeForInput(processInfo, fallbackInput, processInfo.outputSlot(), getSecondaryOutputSlotForRecipe(processInfo), updateCache);
    }

    @Nullable
    private CachedRecipe<MachineRecipe<?, ?, ?>> getCachedRecipe(ProcessInfo processInfo) {
        return getRecipeCacheLookupMonitor(processInfo).getCachedRecipe(processInfo.process());
    }

    private boolean isCachedRecipeValid(ProcessInfo processInfo, @Nullable CachedRecipe<MachineRecipe<?, ?, ?>> cachedRecipe, ItemStack inputStack) {
        if (cachedRecipe == null) {
            return false;
        }
        MachineRecipe<?, ?, ?> recipe = cachedRecipe.getRecipe();
        return recipeMatchesCurrentInput(recipe, inputStack, getExtraStack()) &&
              recipeMatchesOutputSlots(recipe, processInfo.outputSlot(), getSecondaryOutputSlotForRecipe(processInfo));
    }

    @Nullable
    private MachineRecipe<?, ?, ?> getRecipeForInput(ProcessInfo processInfo, ItemStack fallbackInput, @Nullable IInventorySlot outputSlot,
          @Nullable IInventorySlot secondaryOutputSlot, boolean updateCache) {
        if (!areRecipeCachesInvalid()) {
            MachineRecipe<?, ?, ?> recipe = getValidCachedRecipeForInput(processInfo, fallbackInput);
            if (recipe != null) {
                return getMatchingCachedRecipe(processInfo, recipe, outputSlot, secondaryOutputSlot, updateCache);
            }
        }
        return updateRecipeCacheIfNeeded(processInfo, findRecipe(processInfo, fallbackInput, outputSlot, secondaryOutputSlot), updateCache);
    }

    @Nullable
    private MachineRecipe<?, ?, ?> getMatchingCachedRecipe(ProcessInfo processInfo, MachineRecipe<?, ?, ?> recipe, @Nullable IInventorySlot outputSlot,
          @Nullable IInventorySlot secondaryOutputSlot, boolean updateCache) {
        if (!recipeMatchesOutputSlots(recipe, outputSlot, secondaryOutputSlot)) {
            return null;
        }
        return updateRecipeCacheIfNeeded(processInfo, recipe, updateCache);
    }

    @Nullable
    private MachineRecipe<?, ?, ?> updateRecipeCacheIfNeeded(ProcessInfo processInfo, @Nullable MachineRecipe<?, ?, ?> recipe, boolean updateCache) {
        if (recipe == null) {
            return null;
        }
        if (updateCache) {
            updateCachedRecipe(processInfo, recipe);
        }
        return recipe;
    }

    @Nullable
    private MachineRecipe<?, ?, ?> getValidCachedRecipeForInput(ProcessInfo processInfo, ItemStack fallbackInput) {
        CachedRecipe<MachineRecipe<?, ?, ?>> cachedRecipe = getCachedRecipe(processInfo);
        MachineRecipe<?, ?, ?> recipe = cachedRecipe == null ? null : cachedRecipe.getRecipe();
        if (recipe != null && recipeMatchesCurrentInput(recipe, fallbackInput, getExtraStack())) {
            return recipe;
        }
        return null;
    }

    @Nullable
    private MachineRecipe<?, ?, ?> findRecipe(ProcessInfo processInfo, ItemStack fallbackInput, @Nullable IInventorySlot outputSlot,
          @Nullable IInventorySlot secondaryOutputSlot) {
        MachineRecipe<?, ?, ?> recipe = findRecipeForInput(fallbackInput, getExtraStack());
        return recipe != null && recipeMatchesOutputSlots(recipe, outputSlot, secondaryOutputSlot) ? recipe : null;
    }

    @Nullable
    private MachineRecipe<?, ?, ?> findRecipeForInput(ItemStack fallbackInput, ItemStack extra) {
        FactoryRecipeHandler handler = getCurrentRecipeHandler();
        return handler == null ? null : handler.findRecipe(this, fallbackInput, extra);
    }

    private void updateCachedRecipe(ProcessInfo processInfo, MachineRecipe<?, ?, ?> recipe) {
        getRecipeCacheLookupMonitor(processInfo).updateCachedRecipe(recipe);
    }

    private boolean recipeMatchesCurrentInput(MachineRecipe<?, ?, ?> recipe, ItemStack fallbackInput, ItemStack extra) {
        FactoryRecipeHandler handler = getCurrentRecipeHandler(recipe);
        return handler != null && handler.recipeMatchesCurrentInput(this, recipe, fallbackInput, extra);
    }

    private boolean recipeMatchesOutputSlots(MachineRecipe<?, ?, ?> recipe, @Nullable IInventorySlot outputSlot, @Nullable IInventorySlot secondaryOutputSlot) {
        FactoryRecipeHandler handler = getCurrentRecipeHandler(recipe);
        return handler != null && handler.recipeMatchesOutputSlots(recipe, outputSlot, secondaryOutputSlot);
    }

    private int getNeededInput(MachineRecipe<?, ?, ?> recipe, ItemStack inputStack) {
        FactoryRecipeHandler handler = getCurrentRecipeHandler(recipe);
        return handler == null ? 1 : handler.getNeededInput(recipe, inputStack);
    }

    private double getSecondaryEnergyPerTick() {
        return getCurrentRecipeHandler().getSecondaryEnergyPerTick(this);
    }

    private boolean isValidInputGas(Gas gas) {
        return getCurrentRecipeHandler().isValidInputGas(this, gas);
    }

    private boolean canInsertInputGas(Gas gas) {
        return getCurrentRecipeHandler().canInsertInputGas(this, gas);
    }

    private boolean isValidInputFluid(FluidStack fluid) {
        return getCurrentRecipeHandler().isValidInputFluid(this, fluid);
    }

    private boolean canInsertInputFluid(FluidStack fluid) {
        return getCurrentRecipeHandler().canInsertInputFluid(this, fluid);
    }

    private void handleSecondaryFuel() {
        handleExtraSlotSecondaryFuel();
    }

    private void handleExtraSlotSecondaryFuel() {
        ItemStack extra = getExtraStack();
        if (!extra.isEmpty()) {
            if (shouldHandleFluidExtraSlot() && handleFluidExtraSlot()) {
                return;
            }
            if (shouldHandleGasExtraSlot() && handleGasExtraSlot()) {
                return;
            } else if (shouldHandleInfusionExtraSlot()) {
                handleInfusionExtraSlot(extra);
            }
        }
    }

    private boolean shouldHandleFluidExtraSlot() {
        return hasFluidInput() && fluidTank.getNeeded() > 0;
    }

    private boolean shouldHandleGasExtraSlot() {
        return hasGasInput() && gasTank.getNeeded() > 0;
    }

    private boolean shouldHandleInfusionExtraSlot() {
        return getCurrentRecipeHandler().handlesInfusionExtraSlot();
    }

    private boolean handleFluidExtraSlot() {
        if (extraSlot.getCount() != 1) {
            return false;
        }
        IFluidHandlerItem fluidHandler = FluidInventorySlot.tryGetFluidHandlerUnstacked(extraSlot.getStack());
        if (fluidHandler == null) {
            return false;
        }
        boolean transferred = false;
        for (int tank = 0, tanks = IFluidHandlerSlot.getTankCount(fluidHandler); tank < tanks && fluidTank.getNeeded() > 0; tank++) {
            FluidStack fluidInItem = IFluidHandlerSlot.getFluidInTank(fluidHandler, tank);
            if (tryTransferFluidFromExtraSlot(fluidHandler, fluidInItem)) {
                transferred = true;
            }
        }
        if (transferred) {
            extraSlot.setStackUnchecked(fluidHandler.getContainer());
        }
        return transferred;
    }

    private boolean tryTransferFluidFromExtraSlot(IFluidHandlerItem fluidHandler, FluidStack fluidInItem) {
        if (IFluidHandlerSlot.isFluidStackEmpty(fluidInItem) || !fluidTank.isFluidValid(fluidInItem)) {
            return false;
        }
        FluidStack simulatedDrain = fluidHandler.drain(fluidInItem.copy(), false);
        if (IFluidHandlerSlot.isFluidStackEmpty(simulatedDrain)) {
            return false;
        }
        FluidStack simulatedRemainder = fluidTank.insert(simulatedDrain, Action.SIMULATE, AutomationType.INTERNAL);
        int accepted = simulatedDrain.amount - IFluidHandlerSlot.getFluidAmount(simulatedRemainder);
        if (accepted <= 0) {
            return false;
        }
        FluidStack actualDrain = fluidHandler.drain(FluidContainerUtils.copyWithAmount(fluidInItem, accepted), true);
        if (IFluidHandlerSlot.isFluidStackEmpty(actualDrain)) {
            return false;
        }
        MekanismUtils.logMismatchedStackSize(IFluidHandlerSlot.getFluidAmount(fluidTank.insert(actualDrain, Action.EXECUTE, AutomationType.INTERNAL)), 0);
        return true;
    }

    private boolean handleGasExtraSlot() {
        return GasInventorySlot.fillTankOrConvert(extraSlot, gasTank, this::getWorld);
    }

    private void handleInfusionExtraSlot(ItemStack extra) {
        InfuseObject pendingInfusionInput = InfuseRegistry.getObject(extra);
        int operations = infuseStored.getSupportedConversionOperations(pendingInfusionInput, maxInfuse, extra.getCount(),
              MekanismConfig.current().general.bulkSlotItemConversion.val());
        if (operations > 0) {
            infuseStored.increase(pendingInfusionInput, operations);
            MekanismUtils.logMismatchedStackSize(extraSlot.shrinkStack(operations, Action.EXECUTE), operations);
        }
    }

    public boolean isItemValidForSlot(@Nonnull ItemStack itemstack) {
        if (!hasItemInput() || itemstack.isEmpty()) {
            return false;
        }
        FactoryRecipeHandler handler = getCurrentRecipeHandler();
        return handler != null && handler.isItemValid(this, itemstack);
    }

    public boolean isValidInputItem(@Nonnull ItemStack itemstack) {
        return hasItemInput() && getCurrentRecipeHandler().hasRecipeForItemInput(this, itemstack);
    }


    public double getScaledProgress(int process) {
        if (getCurrentRecipeHandler().usesActiveStateProgress()) {
            return getActive() ? 1 : 0;
        }
        ProcessInfo processInfo = getProcessInfoOrNull(process);
        if (processInfo == null) {
            return 0;
        }
        MachineRecipe<?, ?, ?> recipe = getRecipeForProgress(processInfo);
        return getScaledProgress(processInfo, recipe);
    }

    @Nullable
    private MachineRecipe<?, ?, ?> getRecipeForProgress(ProcessInfo processInfo) {
        return getRecipeForInput(processInfo, processInfo.inputSlot().getStack(), false);
    }

    private double getScaledProgress(ProcessInfo processInfo, @Nullable MachineRecipe<?, ?, ?> recipe) {
        return Math.max(Math.min((double) getProgress(processInfo) / getTicksRequired(recipe), 1.0D), 0.0F);
    }


    public double getScaledInfuseLevel(int i) {
        return maxInfuse <= 0 ? 0 : (double) infuseStored.getAmount() * i / maxInfuse;
    }

    public InfuseStorage getInfuseStorage() {
        return infuseStored;
    }

    public String getInfuseTooltip() {
        if (infuseStored.getType() == null) {
            return LangUtils.localize("gui.empty");
        }
        String amount = infuseStored.getAmount() == Integer.MAX_VALUE ? LangUtils.localize("gui.infinite") : Integer.toString(infuseStored.getAmount());
        return infuseStored.getType().getLocalizedName() + ": " + amount;
    }

    public List<String> getSecondaryGasTooltip() {
        List<String> tooltip = new ArrayList<>();
        GasStack gas = gasTank.getGas();
        if (gas == null || gas.getGas() == null) {
            tooltip.add(LangUtils.localize("gui.empty"));
            return tooltip;
        }
        String amount = gasTank.getStored() == Integer.MAX_VALUE ? LangUtils.localize("gui.infinite") : Integer.toString(gasTank.getStored());
        tooltip.add(gas.getGas().getLocalizedName() + ": " + amount);
        return tooltip;
    }

    public List<String> getJeiRecipeCategories() {
        return getCurrentRecipeHandler().getJeiRecipeCategories(this);
    }

    public BasicGasTank getInputGasTank() {
        return gasTank;
    }

    public BasicGasTank getOutputGasTank() {
        return gasOutTank;
    }

    public BasicFluidTank getInputFluidTank() {
        return fluidTank;
    }

    private boolean hasLegacyProcessInfuseWarning(ProcessInfo processInfo) {
        return getCurrentRecipeHandler().hasLegacyProcessInfuseWarning(this, processInfo);
    }

    private boolean hasAnyProcess(Predicate<ProcessInfo> check) {
        for (ProcessInfo processInfo : processInfoSlots) {
            if (check.test(processInfo)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTrackedWarning(RecipeError error) {
        return errorTracker.hasError(error);
    }

    private boolean hasTrackedWarning(RecipeError error, ProcessInfo processInfo) {
        return errorTracker.hasError(error, processInfo.process());
    }

    public BooleanSupplier getWarningCheck(RecipeError error, int processIndex) {
        return errorTracker.getWarningCheck(error, processIndex);
    }

    @Override
    public void clearRecipeErrors(int cacheIndex) {
        errorTracker.clearErrors(cacheIndex);
    }

    private void clearRecipeErrors(ProcessInfo processInfo) {
        clearRecipeErrors(processInfo.process());
    }

    private void onRecipeErrorsChanged(ProcessInfo processInfo, Set<RecipeError> errors) {
        errorTracker.onErrorsChanged(errors, processInfo.process());
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        trackProgress(container);
        errorTracker.track(container);
        container.track(SyncableDouble.create(this::getLastUsage, this::setLastUsage));
        container.track(SyncableBoolean.create(this::isSorting, this::setSorting));
        container.track(SyncableInt.create(this::getTicksRequired, value -> ticksRequired = value));
    }

    private void trackProgress(MekanismContainer container) {
        for (ProcessInfo processInfo : processInfoSlots) {
            container.track(SyncableInt.create(progress, processInfo.process()));
        }
    }

    public boolean canOperate(int process) {
        if (!isValidProcess(process)) {
            return false;
        }
        ProcessInfo processInfo = getProcessInfo(process);
        return canOperateRecipe(processInfo, getRecipeForInput(processInfo, processInfo.inputSlot().getStack(), false));
    }

    private boolean canOperateRecipe(ProcessInfo processInfo, MachineRecipe<?, ?, ?> recipe) {
        if (isMissingRequiredItemInput(processInfo)) {
            return false;
        }
        FactoryRecipeHandler handler = getCurrentRecipeHandler(recipe);
        return handler != null && handler.canOperate(this, processInfo, recipe);
    }

    private boolean isMissingRequiredItemInput(ProcessInfo processInfo) {
        return processInfo.inputSlot().isEmpty() && hasItemInput();
    }

    @Nullable
    @Override
    public MachineRecipe<?, ?, ?> getRecipe(int cacheIndex) {
        return getRecipe(getProcessInfo(cacheIndex));
    }

    @Nullable
    private MachineRecipe<?, ?, ?> getRecipe(ProcessInfo processInfo) {
        return findRecipe(processInfo, processInfo.inputSlot().getStack(), processInfo.outputSlot(), getSecondaryOutputSlotForRecipe(processInfo));
    }

    @Override
    public int getSavedOperatingTicks(int cacheIndex) {
        return getProgress(cacheIndex);
    }

    @Override
    public CachedRecipe<MachineRecipe<?, ?, ?>> createNewCachedRecipe(MachineRecipe<?, ?, ?> recipe, int cacheIndex) {
        ProcessInfo processInfo = getProcessInfo(cacheIndex);
        FactoryRecipeHandler handler = getCurrentRecipeHandler(recipe);
        return handler == null ? null : handler.createCachedRecipe(this, processInfo, recipe);
    }

    private FactoryRecipeHandler getCurrentRecipeHandler() {
        return recipeHandler;
    }

    @Nullable
    private FactoryRecipeHandler getCurrentRecipeHandler(@Nullable MachineRecipe<?, ?, ?> recipe) {
        if (recipe == null) {
            return null;
        }
        FactoryRecipeHandler handler = getCurrentRecipeHandler();
        return handler.matchesRecipe(recipe) ? handler : null;
    }

    private IInputHandler<ItemStack, ItemStack> getItemInputHandler(ProcessInfo processInfo) {
        return itemInputHandlers[processInfo.process()];
    }

    private IOutputHandler<ItemStack> getItemOutputHandler(ProcessInfo processInfo) {
        return itemOutputHandlers[processInfo.process()];
    }

    private IOutputHandler<ChanceOutput> getChanceOutputHandler(ProcessInfo processInfo) {
        return chanceOutputHandlers[processInfo.process()];
    }

    private IOutputHandler<ChanceOutput2> getChance2OutputHandler(ProcessInfo processInfo) {
        return chance2OutputHandlers[processInfo.process()];
    }

    private IOutputHandler<PressurizedOutput> getPressurizedOutputHandler(ProcessInfo processInfo) {
        return pressurizedOutputHandlers[processInfo.process()];
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CachedRecipe<MachineRecipe<?, ?, ?>> setupFactoryCachedRecipe(CachedRecipe<? extends MachineRecipe<?, ?, ?>> cachedRecipe, ProcessInfo processInfo) {
        return (CachedRecipe) cachedRecipe
              .setCanHolderFunction(() -> MekanismUtils.canFunction(this) && hasSecondaryFuelForRecipeTick())
              .setErrorsChanged(errors -> onRecipeErrorsChanged(processInfo, errors))
              .setActive(active -> setActiveState(processInfo, active))
              .setEnergyRequirements(() -> getProcessEnergyPerTick(cachedRecipe.getRecipe()), getMainEnergyContainer())
              .setRequiredTicks(() -> getTicksRequired(cachedRecipe.getRecipe()))
              .setBaselineMaxOperations(() -> getOperationsPerTick(cachedRecipe.getRecipe()))
              .setOperatingTicksChanged(ticks -> setProgress(processInfo, ticks))
              .setOnFinish(this::markNoUpdateSync);
    }

    private boolean shouldRecheckAllRecipeErrors(int checkOffset) {
        return !playersUsing.isEmpty() && world != null && world.getTotalWorldTime() % RECIPE_CHECK_FREQUENCY == checkOffset;
    }

    private boolean depleteRecipeInput() {
        return true;
    }

    @Nonnull
    private RecipeType readRecipeTypeFromPacket(ByteBuf dataStream) {
        return MekanismUtils.getByIndex(RecipeType.values(), dataStream.readInt(), recipeType);
    }

    @Nonnull
    private RecipeType readRecipeTypeFromNBT(NBTTagCompound nbtTags) {
        return FactoryRecipeTypeCodec.readOrDefault(nbtTags, recipeType);
    }

    private void writeRecipeTypeToNBT(NBTTagCompound nbtTags) {
        FactoryRecipeTypeCodec.write(nbtTags, recipeType);
    }

    private void addRecipeTypeToNetwork(TileNetworkList data) {
        data.add(getRecipeTypeIndex());
    }

    private void readProgressFromPacket(ByteBuf dataStream) {
        for (ProcessInfo processInfo : processInfoSlots) {
            setProgress(processInfo, dataStream.readInt());
        }
    }

    private void readTanksFromPacket(ByteBuf dataStream) {
        TileUtils.readTankData(dataStream, fluidTank);
        TileUtils.readTankData(dataStream, gasTank);
        TileUtils.readTankData(dataStream, gasOutTank);
    }

    private void addProgressToNetwork(TileNetworkList data) {
        data.add(progress);
    }

    private void addTanksToNetwork(TileNetworkList data) {
        TileUtils.addTankData(data, fluidTank);
        TileUtils.addTankData(data, gasTank);
        TileUtils.addTankData(data, gasOutTank);
    }


    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            handleServerPacket(dataStream);
            return;
        }

        super.handlePacketData(dataStream);

        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            handleClientPacket(dataStream);
        }
    }

    private void handleServerPacket(ByteBuf dataStream) {
        int type = dataStream.readInt();
        if (type == 0) {
            toggleSorting();
        } else if (type == 1) {
            dump();
        }
    }

    private void handleClientPacket(ByteBuf dataStream) {
        RecipeType oldRecipe = recipeType;
        RecipeType packetRecipeType = readRecipeTypeFromPacket(dataStream);
        readFactoryStateFromPacket(dataStream);
        updateRecipeTypeFromPacket(oldRecipe, packetRecipeType);
        readProgressFromPacket(dataStream);
        readTanksFromPacket(dataStream);
        finishUpgradePacketSync();
    }

    private void readFactoryStateFromPacket(ByteBuf dataStream) {
        setSorting(dataStream.readBoolean());
        upgraded = dataStream.readBoolean();
        setLastUsage(dataStream.readDouble());
        infuseStored.readFromPacket(dataStream);
    }

    private void updateRecipeTypeFromPacket(RecipeType oldRecipe, RecipeType packetRecipeType) {
        if (packetRecipeType != oldRecipe) {
            setRecipeType(packetRecipeType);
            if (!upgraded) {
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    private void finishUpgradePacketSync() {
        if (upgraded) {
            markNoUpdateSync();
            MekanismUtils.updateBlock(world, getPos());
            upgraded = false;
        }
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        readRecipeTypeFromNBTIfPresent(nbtTags);
        readFactoryStateFromNBT(nbtTags);
        readStoredTankLists(nbtTags);
        readLegacyTankFallbacks(nbtTags);
        sanitizeAndClampTanks();
    }

    @Override
    protected void readCustomNBTBeforeInventory(NBTTagCompound nbtTags) {
        readPendingLegacyTypeSlotDrops(nbtTags);
        migrateStoredInventory(nbtTags);
    }

    private void readPendingLegacyTypeSlotDrops(NBTTagCompound nbtTags) {
        if (!nbtTags.hasKey(LEGACY_TYPE_SLOT_DROPS_KEY, NBT.TAG_LIST)) {
            return;
        }
        NBTTagList drops = nbtTags.getTagList(LEGACY_TYPE_SLOT_DROPS_KEY, NBT.TAG_COMPOUND);
        for (int tagCount = 0; tagCount < drops.tagCount(); tagCount++) {
            ItemStack stack = new ItemStack(drops.getCompoundTagAt(tagCount));
            if (!stack.isEmpty()) {
                legacyTypeSlotDrops.add(stack);
            }
        }
    }

    private void migrateStoredInventory(NBTTagCompound nbtTags) {
        if (!nbtTags.hasKey(NBTConstants.ITEMS, NBT.TAG_LIST) || nbtTags.getInteger(FACTORY_INVENTORY_VERSION_KEY) >= FACTORY_INVENTORY_VERSION) {
            return;
        }
        NBTTagList storedItems = nbtTags.getTagList(NBTConstants.ITEMS, NBT.TAG_COMPOUND);
        if (isNewFactoryInventoryList(storedItems)) {
            nbtTags.setInteger(FACTORY_INVENTORY_VERSION_KEY, FACTORY_INVENTORY_VERSION);
            return;
        }
        nbtTags.setTag(NBTConstants.ITEMS, migrateInventoryList(storedItems));
        nbtTags.setInteger(FACTORY_INVENTORY_VERSION_KEY, FACTORY_INVENTORY_VERSION);
    }

    private NBTTagList migrateInventoryList(NBTTagList storedItems) {
        List<IInventorySlot> targetSlots = getInventorySlots(null);
        NBTTagList migrated = new NBTTagList();
        for (int tagCount = 0; tagCount < storedItems.tagCount(); tagCount++) {
            NBTTagCompound tagCompound = storedItems.getCompoundTagAt(tagCount);
            byte legacySlot = tagCompound.getByte(NBTConstants.SLOT);
            int newSlot = getNewInventorySlotIndex(legacySlot);
            if (newSlot == FACTORY_INVENTORY_VERSION_MARKER_SLOT) {
                continue;
            }
            NBTTagCompound remapped = tagCompound.copy();
            if (newSlot >= 0 && newSlot < targetSlots.size()) {
                remapped.setByte(NBTConstants.SLOT, (byte) newSlot);
                migrated.appendTag(remapped);
            } else if (legacySlot == LEGACY_SLOT_TYPE_INPUT || legacySlot == LEGACY_SLOT_TYPE_OUTPUT) {
                migrateLegacyTypeSlotItem(remapped);
            }
        }
        appendInventoryVersionMarker(migrated);
        return migrated;
    }

    private int getNewInventorySlotIndex(int legacySlot) {
        if (legacySlot == LEGACY_SLOT_ENERGY) {
            return getEnergySlotIndex();
        } else if (legacySlot == LEGACY_SLOT_EXTRA) {
            return getExtraSlotIndex();
        } else if (legacySlot >= LEGACY_FIRST_PROCESS_SLOT) {
            return getNewProcessSlotIndex(legacySlot);
        }
        return -1;
    }

    private int getNewProcessSlotIndex(int legacySlot) {
        int processCount = getProcessCount();
        int processSlot = legacySlot - LEGACY_FIRST_PROCESS_SLOT;
        if (processSlot < processCount) {
            return processSlot * PROCESS_SLOT_STRIDE;
        }
        processSlot -= processCount;
        if (processSlot < processCount) {
            return processSlot * PROCESS_SLOT_STRIDE + 1;
        }
        processSlot -= processCount;
        if (processSlot < processCount) {
            return processSlot * PROCESS_SLOT_STRIDE + 2;
        }
        return -1;
    }

    private int getEnergySlotIndex() {
        return getProcessCount() * PROCESS_SLOT_STRIDE + 1;
    }

    private int getExtraSlotIndex() {
        return getProcessCount() * PROCESS_SLOT_STRIDE;
    }

    private void migrateLegacyTypeSlotItem(NBTTagCompound tagCompound) {
        ItemStack stack = ItemStack.EMPTY;
        if (tagCompound.hasKey(NBTConstants.ITEM, NBT.TAG_COMPOUND)) {
            stack = new ItemStack(tagCompound.getCompoundTag(NBTConstants.ITEM));
            if (tagCompound.hasKey(NBTConstants.SIZE_OVERRIDE, NBT.TAG_INT)) {
                stack.setCount(tagCompound.getInteger(NBTConstants.SIZE_OVERRIDE));
            }
        }
        if (stack.isEmpty()) {
            return;
        }
        legacyTypeSlotDrops.add(stack.copy());
    }

    private void appendInventoryVersionMarker(NBTTagList items) {
        NBTTagCompound marker = new NBTTagCompound();
        marker.setByte(NBTConstants.SLOT, FACTORY_INVENTORY_VERSION_MARKER_SLOT);
        marker.setInteger(FACTORY_INVENTORY_VERSION_KEY, FACTORY_INVENTORY_VERSION);
        items.appendTag(marker);
    }

    private boolean isNewFactoryInventoryList(NBTTagList storedItems) {
        for (int tagCount = 0; tagCount < storedItems.tagCount(); tagCount++) {
            NBTTagCompound tagCompound = storedItems.getCompoundTagAt(tagCount);
            if (tagCompound.getByte(NBTConstants.SLOT) == FACTORY_INVENTORY_VERSION_MARKER_SLOT &&
                  tagCompound.getInteger(FACTORY_INVENTORY_VERSION_KEY) >= FACTORY_INVENTORY_VERSION) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setInventory(NBTTagList nbtTags, Object... data) {
        if (nbtTags == null || nbtTags.tagCount() == 0 || !hasInventory() || !persistInventory()) {
            return;
        }
        DataHandlerUtils.readContainers(getInventorySlots(null), isNewFactoryInventoryList(nbtTags) ? nbtTags : migrateInventoryList(nbtTags));
    }

    @Override
    public NBTTagList getInventory(Object... data) {
        NBTTagList items = hasInventory() && persistInventory() ? DataHandlerUtils.writeContainers(getInventorySlots(null)) : new NBTTagList();
        appendInventoryVersionMarker(items);
        return items;
    }

    private void readRecipeTypeFromNBTIfPresent(NBTTagCompound nbtTags) {
        if (FactoryRecipeTypeCodec.hasRecipeType(nbtTags)) {
            setRecipeType(readRecipeTypeFromNBT(nbtTags));
        }
    }

    private void readFactoryStateFromNBT(NBTTagCompound nbtTags) {
        setSorting(nbtTags.getBoolean(NBTConstants.SORTING));
        infuseStored.read(nbtTags);
        readProgressFromNBT(nbtTags);
        readUsedSoFarFromNBT(nbtTags);
    }

    private void readStoredTankLists(NBTTagCompound nbtTags) {
        if (hasStoredFluidTanks(nbtTags)) {
            DataHandlerUtils.readContainers(getPersistentFluidTanks(), nbtTags.getTagList(NBTConstants.FLUID_TANKS, NBT.TAG_COMPOUND));
        }
        if (hasStoredGasTanks(nbtTags)) {
            DataHandlerUtils.readContainers(getPersistentGasTanks(), nbtTags.getTagList(NBTConstants.GAS_TANKS, NBT.TAG_COMPOUND));
        }
    }

    private void readLegacyTankFallbacks(NBTTagCompound nbtTags) {
        boolean loadedFluidTanks = hasStoredFluidTanks(nbtTags);
        boolean loadedGasTanks = hasStoredGasTanks(nbtTags);
        if (!loadedFluidTanks) {
            readLegacyFluidTankFallback(nbtTags);
        }
        if (!loadedGasTanks) {
            readLegacyGasTankFallbacks(nbtTags);
        }
    }

    private void readLegacyFluidTankFallback(NBTTagCompound nbtTags) {
        if (nbtTags.hasKey(LEGACY_FLUID_TANK_KEY)) {
            fluidTank.readFromNBT(nbtTags.getCompoundTag(LEGACY_FLUID_TANK_KEY));
        }
    }

    private void readLegacyGasTankFallbacks(NBTTagCompound nbtTags) {
        if (nbtTags.hasKey(LEGACY_GAS_TANK_KEY)) {
            gasTank.read(nbtTags.getCompoundTag(LEGACY_GAS_TANK_KEY));
        }
        if (nbtTags.hasKey(LEGACY_GAS_OUTPUT_TANK_KEY)) {
            gasOutTank.read(nbtTags.getCompoundTag(LEGACY_GAS_OUTPUT_TANK_KEY));
        }
    }

    private void sanitizeAndClampTanks() {
        sanitizeAndClampTank(fluidTank);
        sanitizeAndClampTank(gasTank);
        sanitizeAndClampTank(gasOutTank);
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
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        writeStoredTankLists(nbtTags);
        markStoredInventoryAsCurrent(nbtTags);
        writeFactoryStateToNBT(nbtTags);
    }

    private void writeStoredTankLists(NBTTagCompound nbtTags) {
        nbtTags.setTag(NBTConstants.FLUID_TANKS, DataHandlerUtils.writeContainers(getPersistentFluidTanks()));
        nbtTags.setTag(NBTConstants.GAS_TANKS, DataHandlerUtils.writeContainers(getPersistentGasTanks()));
    }

    private List<BasicFluidTank> getPersistentFluidTanks() {
        return Collections.singletonList(fluidTank);
    }

    private List<BasicGasTank> getPersistentGasTanks() {
        return Arrays.asList(gasTank, gasOutTank);
    }

    private void markStoredInventoryAsCurrent(NBTTagCompound nbtTags) {
        if (nbtTags.hasKey(NBTConstants.ITEMS, NBT.TAG_LIST)) {
            NBTTagList items = nbtTags.getTagList(NBTConstants.ITEMS, NBT.TAG_COMPOUND);
            if (!isNewFactoryInventoryList(items)) {
                appendInventoryVersionMarker(items);
            }
        }
        writePendingLegacyTypeSlotDrops(nbtTags);
    }

    private void writePendingLegacyTypeSlotDrops(NBTTagCompound nbtTags) {
        if (legacyTypeSlotDrops.isEmpty()) {
            nbtTags.removeTag(LEGACY_TYPE_SLOT_DROPS_KEY);
            return;
        }
        NBTTagList drops = new NBTTagList();
        for (ItemStack stack : legacyTypeSlotDrops) {
            if (!stack.isEmpty()) {
                drops.appendTag(stack.writeToNBT(new NBTTagCompound()));
            }
        }
        nbtTags.setTag(LEGACY_TYPE_SLOT_DROPS_KEY, drops);
    }

    private void writeFactoryStateToNBT(NBTTagCompound nbtTags) {
        writeRecipeTypeToNBT(nbtTags);
        nbtTags.setInteger(FACTORY_INVENTORY_VERSION_KEY, FACTORY_INVENTORY_VERSION);
        nbtTags.setBoolean(NBTConstants.SORTING, isSorting());
        infuseStored.write(nbtTags);
        writeProgressToNBT(nbtTags);
        writeUsedSoFarToNBT(nbtTags);
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        addFactoryStateToNetwork(data);
        upgraded = false;
        return data;
    }

    private void addFactoryStateToNetwork(TileNetworkList data) {
        addRecipeTypeToNetwork(data);
        data.add(isSorting());
        data.add(upgraded);
        data.add(getLastUsage());
        infuseStored.addToNetworkList(data);

        addProgressToNetwork(data);
        addTanksToNetwork(data);
    }

    @Nonnull
    public ItemStack getInputStack(int process) {
        ProcessInfo processInfo = getProcessInfoOrNull(process);
        return processInfo == null ? ItemStack.EMPTY : processInfo.inputSlot().getStack();
    }

    @Nonnull
    public ItemStack getOutputStack(int process) {
        ProcessInfo processInfo = getProcessInfoOrNull(process);
        return processInfo == null ? ItemStack.EMPTY : processInfo.outputSlot().getStack();
    }

    @Nonnull
    public ItemStack getSecondaryOutputStack(int process) {
        ProcessInfo processInfo = getProcessInfoOrNull(process);
        if (processInfo == null) {
            return ItemStack.EMPTY;
        }
        IInventorySlot secondaryOutputSlot = processInfo.secondaryOutputSlot();
        return secondaryOutputSlot == null ? ItemStack.EMPTY : secondaryOutputSlot.getStack();
    }

    @Nonnull
    public ItemStack getExtraStack() {
        return extraSlot.getStack();
    }

    private ProcessInfo getProcessInfo(int process) {
        return processInfoSlots[process];
    }

    @Nullable
    private ProcessInfo getProcessInfoOrNull(int process) {
        return isValidProcess(process) ? getProcessInfo(process) : null;
    }

    private boolean isValidProcess(int process) {
        return process >= 0 && process < getProcessCount();
    }

    @Nullable
    private IInventorySlot getSecondaryOutputSlotForRecipe(ProcessInfo processInfo) {
        return hasSecondaryItemOutput() ? processInfo.secondaryOutputSlot() : null;
    }

    @Nonnull
    @Override
    public String getName() {
        String localizationKey = getFactoryLocalizationKey();
        if (LangUtils.canLocalize(localizationKey)) {
            return LangUtils.localize(localizationKey);
        }
        //TODO:Rename this
        return getFallbackFactoryName();
    }

    private String getFactoryLocalizationKey() {
        return "tile." + tier.getBaseTier().getName() + recipeType.getTranslationKey() + "Factory";
    }

    private String getFallbackFactoryName() {
        return tier.getBaseTier().getLocalizedName() + recipeType.getLocalizedName() + super.getName();
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        switch (method) {
            case 0 -> {
                return new Object[]{electricityStored};
            }
            case 1 -> {
                return invokeRecipeProgress(arguments);
            }
            case 2 -> {
                return new Object[]{facing};
            }
            case 3 -> {
                return invokeCanOperate(arguments);
            }
            case 4 -> {
                return new Object[]{getMaxEnergy()};
            }
            case 5 -> {
                return new Object[]{getMaxEnergy() - getEnergy()};
            }
            default -> throw new NoSuchMethodException();
        }
    }

    private Object[] invokeRecipeProgress(Object[] arguments) {
        Object[] error = validateOperationArgument(arguments);
        if (error != null) {
            return error;
        }
        return new Object[]{getRecipeProgress(getOperationArgument(arguments))};
    }

    private Object[] invokeCanOperate(Object[] arguments) {
        Object[] error = validateOperationArgument(arguments);
        if (error != null) {
            return error;
        }
        return new Object[]{canOperate(getOperationArgument(arguments))};
    }

    private Object[] validateOperationArgument(Object[] arguments) {
        if (arguments.length == 0 || arguments[0] == null) {
            return new Object[]{"Please provide a target operation."};
        }
        if (!(arguments[0] instanceof Number)) {
            return new Object[]{"Invalid characters."};
        }
        int process = getOperationArgument(arguments);
        if (!isValidProcess(process)) {
            return new Object[]{"No such operation found."};
        }
        return null;
    }

    private int getOperationArgument(Object[] arguments) {
        return ((Number) arguments[0]).intValue();
    }

    private int getRecipeProgress(int process) {
        return getProgress(process);
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return supportsItem() ? super.getSlotsForFace(side) : InventoryUtils.EMPTY;
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
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return false;
        }
        return isSpecialConfigCapability(capability) || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return null;
        } else if (capability == Capabilities.CONFIG_CARD_CAPABILITY) {
            return Capabilities.CONFIG_CARD_CAPABILITY.cast(this);
        } else if (capability == Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY) {
            return Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        if (configComponent.isCapabilityDisabled(capability, side, facing)) {
            return true;
        } else if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            return isGasCapabilityDisabled();
        } else if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return isFluidCapabilityDisabled();
        }
        return super.isCapabilityDisabled(capability, side);
    }

    private boolean isSpecialConfigCapability(Capability<?> capability) {
        return capability == Capabilities.CONFIG_CARD_CAPABILITY || capability == Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY;
    }

    private boolean isGasCapabilityDisabled() {
        // If side config allows gas, still hide the capability for recipe types that do not use gas.
        return !supportsGas();
    }

    private boolean isFluidCapabilityDisabled() {
        return !hasFluidInput();
    }

    @Override
    public void recalculateUpgradables(Upgrade upgrade) {
        super.recalculateUpgradables(upgrade);
        if (upgrade == Upgrade.ENERGY) {
            recalculateEnergyUpgrade();
        } else if (upgrade == Upgrade.GAS) {
            recalculateGasUpgrade();
        } else if (upgrade == Upgrade.SPEED) {
            recalculateSpeedUpgrade();
        }
        if (world != null && !world.isRemote) {
            unpauseRecipeCaches();
        }
    }

    private void recalculateEnergyUpgrade() {
        energyPerTick = MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK);
    }

    private void recalculateGasUpgrade() {
        secondaryEnergyPerTick = getSecondaryEnergyPerTick();
        updateSecondaryUsageTracking();
    }

    private void recalculateSpeedUpgrade() {
        energyPerTick = MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK);
        ticksRequired = MekanismUtils.getTicks(this, BASE_TICKS_REQUIRED);
        recalculateGasUpgrade();
    }

    private boolean usesStatisticalSecondaryFuel() {
        return getCurrentRecipeHandler().usesStatisticalSecondaryFuel(this);
    }

    private void updateSecondaryUsageTracking() {
        getCurrentRecipeHandler().updateSecondaryUsageTracking(this);
    }

    private void setSavedUsedSoFar(ProcessInfo processInfo, long used) {
        setSavedUsedSoFar(processInfo.process(), used);
    }

    private boolean isProcessInputLockedForSorting(ProcessInfo processInfo) {
        int process = processInfo.process();
        return progress[process] > 0 || usedSoFar[process] > 0;
    }

    public void setSavedUsedSoFar(int cacheIndex, long used) {
        if (isValidProcess(cacheIndex)) {
            usedSoFar[cacheIndex] = Math.max(0, used);
        }
    }

    @Override
    public long getSavedUsedSoFar(int cacheIndex) {
        return isValidProcess(cacheIndex) ? usedSoFar[cacheIndex] : 0;
    }

    @Override
    public Object[] getManagedTanks() {
        return new Object[]{fluidTank, gasTank, gasOutTank};
    }

    @Override
    public NBTTagCompound getConfigurationData(NBTTagCompound nbtTags) {
        writeSortingConfiguration(nbtTags);
        return nbtTags;
    }

    @Override
    public void setConfigurationData(NBTTagCompound nbtTags) {
        readSortingConfiguration(nbtTags);
    }

    private void writeSortingConfiguration(NBTTagCompound nbtTags) {
        nbtTags.setBoolean(NBTConstants.SORTING, isSorting());
    }

    private void readSortingConfiguration(NBTTagCompound nbtTags) {
        setSorting(nbtTags.getBoolean(NBTConstants.SORTING));
    }

    @Override
    public String getDataType() {
        return getFallbackFactoryName();
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        infuseStored.writeSustainedData(itemStack);
        writeSustainedTanks(itemStack);
        writeSustainedLegacyTypeSlotDrops(itemStack);
    }

    private void writeSustainedLegacyTypeSlotDrops(ItemStack itemStack) {
        if (legacyTypeSlotDrops.isEmpty()) {
            ItemDataUtils.removeData(itemStack, LEGACY_TYPE_SLOT_DROPS_KEY);
            return;
        }
        NBTTagList drops = new NBTTagList();
        for (ItemStack stack : legacyTypeSlotDrops) {
            if (!stack.isEmpty()) {
                drops.appendTag(stack.writeToNBT(new NBTTagCompound()));
            }
        }
        ItemDataUtils.setList(itemStack, LEGACY_TYPE_SLOT_DROPS_KEY, drops);
    }

    private void writeSustainedTanks(ItemStack itemStack) {
        ItemDataUtils.writeContainers(itemStack, NBTConstants.GAS_TANKS, getPersistentGasTanks());
        ItemDataUtils.writeContainers(itemStack, NBTConstants.FLUID_TANKS, getPersistentFluidTanks());
        writeLegacySustainedTankFallbacks(itemStack);
    }

    private void writeLegacySustainedTankFallbacks(ItemStack itemStack) {
        ItemDataUtils.setLegacyGas(itemStack, LEGACY_GAS_TANK_KEY, gasTank.getGas());
        ItemDataUtils.setLegacyGas(itemStack, LEGACY_GAS_OUTPUT_TANK_KEY, gasOutTank.getGas());
        ItemDataUtils.setLegacyFluid(itemStack, LEGACY_FLUID_TANK_KEY, fluidTank.getFluid());
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        infuseStored.readSustainedData(itemStack);
        readSustainedTanks(itemStack);
        readSustainedLegacyTypeSlotDrops(itemStack);
    }

    private void readSustainedLegacyTypeSlotDrops(ItemStack itemStack) {
        NBTTagList drops = ItemDataUtils.getList(itemStack, LEGACY_TYPE_SLOT_DROPS_KEY);
        for (int tagCount = 0; tagCount < drops.tagCount(); tagCount++) {
            ItemStack stack = new ItemStack(drops.getCompoundTagAt(tagCount));
            if (!stack.isEmpty()) {
                legacyTypeSlotDrops.add(stack);
            }
        }
    }

    private void readSustainedTanks(ItemStack itemStack) {
        boolean loadedGasTanks = readSustainedGasTanksFromFactoryList(itemStack);
        boolean loadedFluidTanks = readSustainedFluidTanksFromFactoryList(itemStack);
        readLegacySustainedTankFallbacks(itemStack, loadedGasTanks, loadedFluidTanks);
    }

    private boolean readSustainedGasTanksFromFactoryList(ItemStack itemStack) {
        if (ItemDataUtils.hasData(itemStack, NBTConstants.GAS_TANKS, NBT.TAG_LIST)) {
            ItemDataUtils.readContainers(itemStack, NBTConstants.GAS_TANKS, getPersistentGasTanks());
            return true;
        }
        return false;
    }

    private boolean readSustainedFluidTanksFromFactoryList(ItemStack itemStack) {
        if (ItemDataUtils.hasData(itemStack, NBTConstants.FLUID_TANKS, NBT.TAG_LIST)) {
            ItemDataUtils.readContainers(itemStack, NBTConstants.FLUID_TANKS, getPersistentFluidTanks());
            return true;
        }
        return false;
    }

    private void readLegacySustainedTankFallbacks(ItemStack itemStack, boolean loadedGasTanks, boolean loadedFluidTanks) {
        if (!loadedGasTanks) {
            gasTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, LEGACY_GAS_TANK_KEY));
            gasOutTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, LEGACY_GAS_OUTPUT_TANK_KEY));
        }
        if (!loadedFluidTanks) {
            fluidTank.setStackUnchecked(ItemDataUtils.getLegacyFluid(itemStack, LEGACY_FLUID_TANK_KEY));
        }
        sanitizeAndClampTanks();
    }

    @Override
    public int getRedstoneLevel() {
        return Container.calcRedstoneFromInventory(this);
    }


    private void updateTransmissionSupport() {
        updateItemTransmissionSupport();
        updateGasTransmissionSupport();
        updateGasOutputSupport();
        updateFluidTransmissionSupport();
    }

    private void updateItemTransmissionSupport() {
        if (!supportsItem()) {
            if (isTransmissionSupported(TransmissionType.ITEM)) {
                removeItemTransmissionSupport();
            }
        } else if (!isTransmissionSupported(TransmissionType.ITEM)) {
            configComponent.addSupported(TransmissionType.ITEM);
            setupFactoryItemConfig();
            applyDefaultItemConfig();
            ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM, TransmissionType.GAS);
        }
    }

    private void removeItemTransmissionSupport() {
        configComponent.removeSupported(TransmissionType.ITEM);
        configComponent.removeConfig(TransmissionType.ITEM);
        ejectorComponent.removeOutputData(TransmissionType.ITEM);
    }

    private void updateGasTransmissionSupport() {
        if (!supportsGas()) {
            if (isTransmissionSupported(TransmissionType.GAS)) {
                removeGasTransmissionSupport();
            }
        } else if (!isTransmissionSupported(TransmissionType.GAS)) {
            configComponent.addSupported(TransmissionType.GAS);
            setupFactoryGasConfig();
            applyDefaultGasConfig();
            ejectorComponent.setOutputData(configComponent, TransmissionType.GAS);
        }
    }

    private void removeGasTransmissionSupport() {
        configComponent.removeSupported(TransmissionType.GAS);
        ejectorComponent.removeOutputData(TransmissionType.GAS);
    }

    private void updateGasOutputSupport() {
        if (!hasGasOutput()) {
            configComponent.setEjecting(TransmissionType.GAS, false);
            configComponent.setCanEject(TransmissionType.GAS, false);
        } else {
            configComponent.setCanEject(TransmissionType.GAS, true);
        }
    }

    private void updateFluidTransmissionSupport() {
        if (!hasFluidInput()) {
            if (isTransmissionSupported(TransmissionType.FLUID)) {
                removeFluidTransmissionSupport();
            }
        } else if (!isTransmissionSupported(TransmissionType.FLUID)) {
            configComponent.addSupported(TransmissionType.FLUID);
            setupFactoryFluidConfig();
        }
    }

    private void removeFluidTransmissionSupport() {
        configComponent.removeSupported(TransmissionType.FLUID);
        configComponent.removeInputConfig(TransmissionType.FLUID);
    }

    private boolean isTransmissionSupported(TransmissionType type) {
        return configComponent.supports(type);
    }
    private enum FactoryRecipeHandler {
        ADVANCED {
            @Override
            boolean matchesRecipe(MachineRecipe<?, ?, ?> recipe) {
                return recipe instanceof AdvancedMachineRecipe<?>;
            }

            @Override
            MachineRecipe<?, ?, ?> findRecipe(TileEntityFactory factory, ItemStack input, ItemStack extra) {
                return getFactoryRecipe(factory, new AdvancedMachineInput(input, factory.gasTank.getGasType()));
            }

            @Override
            boolean isItemValid(TileEntityFactory factory, ItemStack stack) {
                Gas gasType = factory.gasTank.getGasType();
                return gasType == null ? hasRecipeForItemInput(factory, stack) : getFactoryRecipe(factory, new AdvancedMachineInput(stack, gasType)) != null;
            }

            @Override
            boolean isExtraSlotVisible(TileEntityFactory factory) {
                return true;
            }

            @Override
            boolean hasGasInput() {
                return true;
            }

            @Override
            boolean usesGasSecondaryResourceBar() {
                return true;
            }

            @Override
            boolean isValidExtraSlotItem(TileEntityFactory factory, ItemStack stack) {
                return isValidGasExtraSlotItem(factory, stack);
            }

            @Override
            boolean usesSecondaryGasInput() {
                return true;
            }

            @Override
            boolean supportsGasUpgrade(TileEntityFactory factory) {
                return factory.recipeType == RecipeType.PURIFYING || factory.recipeType == RecipeType.INJECTING;
            }

            @Override
            boolean usesStatisticalSecondaryFuel(TileEntityFactory factory) {
                return supportsGasUpgrade(factory);
            }

            @Override
            double getSecondaryEnergyPerTick(TileEntityFactory factory) {
                return MekanismUtils.getSecondaryEnergyPerTickMean(factory, 1);
            }

            @Override
            int getFactoryGuiHeightExtra() {
                return 11;
            }

            @Override
            void dumpSecondaryResource(TileEntityFactory factory) {
                factory.gasTank.setEmpty();
            }

            @Override
            int getExtraSlotY() {
                return 57;
            }

            @Override
            boolean recipeMatchesCurrentInput(TileEntityFactory factory, MachineRecipe<?, ?, ?> recipe, ItemStack input, ItemStack extra) {
                return super.recipeMatchesCurrentInput(factory, recipe, input, extra) &&
                      recipeMatchesGasInput(factory, (AdvancedMachineInput) recipe.recipeInput);
            }

            @Override
            int getNeededInput(MachineRecipe<?, ?, ?> recipe, ItemStack inputStack) {
                return Math.max(1, ((AdvancedMachineInput) recipe.recipeInput).itemStack.getCount());
            }

            @Override
            boolean canOperate(TileEntityFactory factory, ProcessInfo processInfo, MachineRecipe<?, ?, ?> recipe) {
                return ((AdvancedMachineRecipe<?>) recipe).canOperate(processInfo.inputSlot(), processInfo.outputSlot(), factory.gasTank, factory.secondaryEnergyThisTick);
            }

            @Override
            CachedRecipe<MachineRecipe<?, ?, ?>> createCachedRecipe(TileEntityFactory factory, ProcessInfo processInfo, MachineRecipe<?, ?, ?> recipe) {
                AdvancedMachineRecipe<?> advancedRecipe = (AdvancedMachineRecipe<?>) recipe;
                return factory.setupFactoryCachedRecipe(new ItemStackConstantGasCachedRecipe<>(advancedRecipe, factory.getRecheckAllRecipeErrors(processInfo),
                        factory.getItemInputHandler(processInfo),
                        factory.constantGasInputHandler,
                        factory.getItemOutputHandler(processInfo),
                        factory.gasUsageMultiplier,
                      used -> factory.setSavedUsedSoFar(processInfo, used)), processInfo);
            }
        },
        DOUBLE {
            @Override
            boolean matchesRecipe(MachineRecipe<?, ?, ?> recipe) {
                return recipe instanceof DoubleMachineRecipe<?>;
            }

            @Override
            MachineRecipe<?, ?, ?> findRecipe(TileEntityFactory factory, ItemStack input, ItemStack extra) {
                return getFactoryRecipe(factory, new DoubleMachineInput(input, extra));
            }

            @Override
            boolean isItemValid(TileEntityFactory factory, ItemStack stack) {
                ItemStack extra = factory.getExtraStack();
                return extra.isEmpty() ? hasRecipeForItemInput(factory, stack) : getFactoryRecipe(factory, new DoubleMachineInput(stack, extra)) != null;
            }

            @Override
            boolean isExtraSlotVisible(TileEntityFactory factory) {
                return true;
            }

            @Override
            boolean isValidExtraSlotItem(TileEntityFactory factory, ItemStack stack) {
                return hasRecipeForExtra(factory, stack);
            }

            @Override
            boolean getExtraSlotLimitMultiplier(TileEntityFactory factory) {
                return factory.recipeType == RecipeType.COMBINING || factory.recipeType == RecipeType.AllOY;
            }

            @Override
            boolean recipeMatchesCurrentInput(TileEntityFactory factory, MachineRecipe<?, ?, ?> recipe, ItemStack input, ItemStack extra) {
                return super.recipeMatchesCurrentInput(factory, recipe, input, extra) &&
                      recipeMatchesDoubleExtraInput((DoubleMachineInput) recipe.recipeInput, extra);
            }

            @Override
            int getNeededInput(MachineRecipe<?, ?, ?> recipe, ItemStack inputStack) {
                return Math.max(1, ((DoubleMachineInput) recipe.recipeInput).itemStack.getCount());
            }

            @Override
            boolean canOperate(TileEntityFactory factory, ProcessInfo processInfo, MachineRecipe<?, ?, ?> recipe) {
                return ((DoubleMachineRecipe<?>) recipe).canOperate(processInfo.inputSlot(), factory.extraSlot, processInfo.outputSlot());
            }

            @Override
            CachedRecipe<MachineRecipe<?, ?, ?>> createCachedRecipe(TileEntityFactory factory, ProcessInfo processInfo, MachineRecipe<?, ?, ?> recipe) {
                DoubleMachineRecipe<?> doubleRecipe = (DoubleMachineRecipe<?>) recipe;
                return factory.setupFactoryCachedRecipe(new TwoInputCachedRecipe<>(doubleRecipe, factory.getRecheckAllRecipeErrors(processInfo),
                      factory.getItemInputHandler(processInfo),
                      factory.extraItemInputHandler,
                      factory.getItemOutputHandler(processInfo),
                      () -> doubleRecipe.getInput().itemStack, () -> doubleRecipe.getInput().extraStack,
                      (input, secondary) -> MachineInput.inputContains(input, doubleRecipe.getInput().itemStack)
                            && MachineInput.inputContains(secondary, doubleRecipe.getInput().extraStack),
                      (input, secondary) -> doubleRecipe.getOutput().output.copy(), ItemStack::isEmpty, ItemStack::isEmpty, ItemStack::isEmpty), processInfo);
            }
        },
        CHANCE {
            @Override
            boolean matchesRecipe(MachineRecipe<?, ?, ?> recipe) {
                return recipe instanceof ChanceMachineRecipe<?>;
            }

            @Override
            MachineRecipe<?, ?, ?> findRecipe(TileEntityFactory factory, ItemStack input, ItemStack extra) {
                return getFactoryRecipe(factory, new ItemStackInput(input));
            }

            @Override
            boolean hasSecondaryItemOutput() {
                return true;
            }

            @Override
            boolean recipeMatchesOutputSlots(MachineRecipe<?, ?, ?> recipe, @Nullable IInventorySlot outputSlot, @Nullable IInventorySlot secondaryOutputSlot) {
                return super.recipeMatchesOutputSlots(recipe, outputSlot, secondaryOutputSlot) &&
                      recipeMatchesSecondaryOutputSlot(recipe, secondaryOutputSlot);
            }

            @Override
            int getFactoryGuiHeightExtra() {
                return 21;
            }

            @Override
            boolean canOperate(TileEntityFactory factory, ProcessInfo processInfo, MachineRecipe<?, ?, ?> recipe) {
                return ((ChanceMachineRecipe<?>) recipe).canOperate(processInfo.inputSlot(), processInfo.outputSlot(), processInfo.secondaryOutputSlot());
            }

            @Override
            CachedRecipe<MachineRecipe<?, ?, ?>> createCachedRecipe(TileEntityFactory factory, ProcessInfo processInfo, MachineRecipe<?, ?, ?> recipe) {
                ChanceMachineRecipe<?> chanceRecipe = (ChanceMachineRecipe<?>) recipe;
                return factory.setupFactoryCachedRecipe(new OneInputCachedRecipe<>(chanceRecipe, factory.getRecheckAllRecipeErrors(processInfo),
                      factory.getItemInputHandler(processInfo),
                      factory.getChanceOutputHandler(processInfo),
                      () -> chanceRecipe.getInput().ingredient, input -> MachineInput.inputContains(input, chanceRecipe.getInput().ingredient),
                      input -> chanceRecipe.getOutput().copy(), ItemStack::isEmpty, output -> false), processInfo);
            }
        },
        FARM {
            @Override
            boolean matchesRecipe(MachineRecipe<?, ?, ?> recipe) {
                return recipe instanceof FarmMachineRecipe<?>;
            }

            @Override
            MachineRecipe<?, ?, ?> findRecipe(TileEntityFactory factory, ItemStack input, ItemStack extra) {
                return getFactoryRecipe(factory, new AdvancedMachineInput(input, factory.gasTank.getGasType()));
            }

            @Override
            boolean isItemValid(TileEntityFactory factory, ItemStack stack) {
                Gas gasType = factory.gasTank.getGasType();
                return gasType == null ? hasRecipeForItemInput(factory, stack) : getFactoryRecipe(factory, new AdvancedMachineInput(stack, gasType)) != null;
            }

            @Override
            boolean isExtraSlotVisible(TileEntityFactory factory) {
                return true;
            }

            @Override
            boolean hasGasInput() {
                return true;
            }

            @Override
            boolean isValidExtraSlotItem(TileEntityFactory factory, ItemStack stack) {
                return isValidGasExtraSlotItem(factory, stack);
            }

            @Override
            boolean isFarm() {
                return true;
            }

            @Override
            int getInputGasGaugeY(TileEntityFactory factory) {
                return 34;
            }

            @Override
            int getSecondaryResourceClickYOffset() {
                return 21;
            }

            @Override
            boolean hasSecondaryItemOutput() {
                return true;
            }

            @Override
            boolean recipeMatchesOutputSlots(MachineRecipe<?, ?, ?> recipe, @Nullable IInventorySlot outputSlot, @Nullable IInventorySlot secondaryOutputSlot) {
                return super.recipeMatchesOutputSlots(recipe, outputSlot, secondaryOutputSlot) &&
                      recipeMatchesSecondaryOutputSlot(recipe, secondaryOutputSlot);
            }

            @Override
            int getFactoryGuiHeightExtra() {
                return 21;
            }

            @Override
            int getExtraSlotY() {
                return 68;
            }

            @Override
            boolean usesSecondaryGasInput() {
                return true;
            }

            @Override
            boolean supportsGasUpgrade(TileEntityFactory factory) {
                return true;
            }

            @Override
            boolean usesStatisticalSecondaryFuel(TileEntityFactory factory) {
                return true;
            }

            @Override
            double getSecondaryEnergyPerTick(TileEntityFactory factory) {
                return MekanismUtils.getSecondaryEnergyPerTickMean(factory, 1);
            }

            @Override
            boolean recipeMatchesCurrentInput(TileEntityFactory factory, MachineRecipe<?, ?, ?> recipe, ItemStack input, ItemStack extra) {
                return super.recipeMatchesCurrentInput(factory, recipe, input, extra) &&
                      recipeMatchesGasInput(factory, (AdvancedMachineInput) recipe.recipeInput);
            }

            @Override
            int getNeededInput(MachineRecipe<?, ?, ?> recipe, ItemStack inputStack) {
                return Math.max(1, ((AdvancedMachineInput) recipe.recipeInput).itemStack.getCount());
            }

            @Override
            boolean canOperate(TileEntityFactory factory, ProcessInfo processInfo, MachineRecipe<?, ?, ?> recipe) {
                return ((FarmMachineRecipe<?>) recipe).canOperate(processInfo.inputSlot(), factory.gasTank, factory.secondaryEnergyThisTick,
                      processInfo.outputSlot(), processInfo.secondaryOutputSlot());
            }

            @Override
            CachedRecipe<MachineRecipe<?, ?, ?>> createCachedRecipe(TileEntityFactory factory, ProcessInfo processInfo, MachineRecipe<?, ?, ?> recipe) {
                FarmMachineRecipe<?> farmRecipe = (FarmMachineRecipe<?>) recipe;
                return factory.setupFactoryCachedRecipe(new ItemStackConstantGasCachedRecipe<>(farmRecipe, factory.getRecheckAllRecipeErrors(processInfo),
                        factory.getItemInputHandler(processInfo),
                        factory.constantGasInputHandler,
                        factory.getChanceOutputHandler(processInfo),
                        factory.gasUsageMultiplier,
                      used -> factory.setSavedUsedSoFar(processInfo, used)), processInfo);
            }
        },
        CHANCE2 {
            @Override
            boolean matchesRecipe(MachineRecipe<?, ?, ?> recipe) {
                return recipe instanceof Chance2MachineRecipe<?>;
            }

            @Override
            List<String> getJeiRecipeCategories(TileEntityFactory factory) {
                if (factory.recipeType == RecipeType.RECYCLER && !MekanismConfig.current().mekce.EnableRecyclerRecipeInJei.val()) {
                    return Arrays.asList();
                }
                return super.getJeiRecipeCategories(factory);
            }

            @Override
            MachineRecipe<?, ?, ?> findRecipe(TileEntityFactory factory, ItemStack input, ItemStack extra) {
                return getFactoryRecipe(factory, new ItemStackInput(input));
            }

            @Override
            boolean canOperate(TileEntityFactory factory, ProcessInfo processInfo, MachineRecipe<?, ?, ?> recipe) {
                return ((Chance2MachineRecipe<?>) recipe).canOperate(processInfo.inputSlot(), processInfo.outputSlot());
            }

            @Override
            CachedRecipe<MachineRecipe<?, ?, ?>> createCachedRecipe(TileEntityFactory factory, ProcessInfo processInfo, MachineRecipe<?, ?, ?> recipe) {
                Chance2MachineRecipe<?> chanceRecipe = (Chance2MachineRecipe<?>) recipe;
                return factory.setupFactoryCachedRecipe(new OneInputCachedRecipe<>(chanceRecipe, factory.getRecheckAllRecipeErrors(processInfo),
                      factory.getItemInputHandler(processInfo),
                      factory.getChance2OutputHandler(processInfo),
                      () -> chanceRecipe.getInput().ingredient, input -> MachineInput.inputContains(input, chanceRecipe.getInput().ingredient),
                      input -> chanceRecipe.getOutput().copy(), ItemStack::isEmpty, output -> false), processInfo);
            }
        },
        INFUSING {
            @Override
            boolean matchesRecipe(MachineRecipe<?, ?, ?> recipe) {
                return recipe instanceof MetallurgicInfuserRecipe;
            }

            @Override
            MachineRecipe<?, ?, ?> findRecipe(TileEntityFactory factory, ItemStack input, ItemStack extra) {
                return findInfusingRecipeForInput(factory, input);
            }

            @Override
            boolean isItemValid(TileEntityFactory factory, ItemStack stack) {
                return factory.infuseStored.getType() == null ? hasRecipeForItemInput(factory, stack)
                      : RecipeHandler.getMetallurgicInfuserRecipe(new InfusionInput(factory.infuseStored, stack)) != null;
            }

            @Override
            boolean isExtraSlotVisible(TileEntityFactory factory) {
                return true;
            }

            @Override
            boolean isValidExtraSlotItem(TileEntityFactory factory, ItemStack stack) {
                InfuseObject object = InfuseRegistry.getObject(stack);
                return object != null && (factory.infuseStored == null || factory.infuseStored.canReceive(object));
            }

            @Override
            boolean isInfusing() {
                return true;
            }

            @Override
            boolean hasLegacyInfuseWarning(TileEntityFactory factory) {
                if (factory.hasTrackedWarning(RecipeError.NOT_ENOUGH_SECONDARY_INPUT)) {
                    return true;
                }
                return factory.hasAnyProcess(factory::hasLegacyProcessInfuseWarning);
            }

            @Override
            boolean hasLegacyProcessInfuseWarning(TileEntityFactory factory, ProcessInfo processInfo) {
                return factory.hasTrackedWarning(RecipeError.NOT_ENOUGH_SECONDARY_INPUT, processInfo) ||
                      factory.infuseStored.getAmount() == 0 && !processInfo.inputSlot().isEmpty();
            }

            @Override
            boolean showsInfuseBar() {
                return true;
            }

            @Override
            boolean handlesInfusionExtraSlot() {
                return true;
            }

            @Override
            int getFactoryGuiHeightExtra() {
                return 11;
            }

            @Override
            boolean recipeMatchesCurrentInput(TileEntityFactory factory, MachineRecipe<?, ?, ?> recipe, ItemStack input, ItemStack extra) {
                return super.recipeMatchesCurrentInput(factory, recipe, input, extra) &&
                      recipeMatchesInfusionInput(factory, (InfusionInput) recipe.recipeInput);
            }

            @Override
            int getNeededInput(MachineRecipe<?, ?, ?> recipe, ItemStack inputStack) {
                return Math.max(1, ((InfusionInput) recipe.recipeInput).inputStack.getCount());
            }

            @Override
            boolean canOperate(TileEntityFactory factory, ProcessInfo processInfo, MachineRecipe<?, ?, ?> recipe) {
                return ((MetallurgicInfuserRecipe) recipe).canOperate(processInfo.inputSlot(), processInfo.outputSlot(), factory.infuseStored);
            }

            @Override
            CachedRecipe<MachineRecipe<?, ?, ?>> createCachedRecipe(TileEntityFactory factory, ProcessInfo processInfo, MachineRecipe<?, ?, ?> recipe) {
                MetallurgicInfuserRecipe infuserRecipe = (MetallurgicInfuserRecipe) recipe;
                return factory.setupFactoryCachedRecipe(new TwoInputCachedRecipe<>(infuserRecipe, factory.getRecheckAllRecipeErrors(processInfo),
                      factory.getItemInputHandler(processInfo),
                      factory.infuseInputHandler,
                      factory.getItemOutputHandler(processInfo),
                      () -> infuserRecipe.getInput().inputStack, () -> infuserRecipe.getInput().infuse,
                      (input, infuse) -> MachineInput.inputContains(input, infuserRecipe.getInput().inputStack)
                            && infuse.getType() == infuserRecipe.getInput().infuse.getType(),
                      (input, infuse) -> infuserRecipe.getOutput().output.copy(), ItemStack::isEmpty,
                      infuse -> infuse == null || infuse.getType() == null || infuse.getAmount() <= 0, ItemStack::isEmpty), processInfo);
            }
        },
        PRESSURIZED {
            @Override
            boolean matchesRecipe(MachineRecipe<?, ?, ?> recipe) {
                return recipe instanceof PressurizedRecipe;
            }

            @Override
            MachineRecipe<?, ?, ?> findRecipe(TileEntityFactory factory, ItemStack input, ItemStack extra) {
                return RecipeHandler.getPRCRecipe(new PressurizedInput(input, factory.fluidTank.getFluid(), factory.gasTank.getGas()));
            }

            @Override
            boolean isPressurized() {
                return true;
            }

            @Override
            boolean hasGasInput() {
                return true;
            }

            @Override
            boolean hasGasOutput() {
                return true;
            }

            @Override
            boolean hasFluidInput() {
                return true;
            }

            @Override
            int getInputGasGaugeY(TileEntityFactory factory) {
                return 34;
            }

            @Override
            boolean usesStandardInputGasGauge(TileEntityFactory factory) {
                return false;
            }

            @Override
            boolean usesSlotInputGasGauge(TileEntityFactory factory) {
                return true;
            }

            @Override
            boolean usesPressurizedTankBars() {
                return true;
            }

            @Override
            boolean hasSecondaryResourceBar() {
                return true;
            }

            @Override
            boolean hasSecondaryResourceDump() {
                return false;
            }

            @Override
            boolean usesSlotOutputGasGauge(TileEntityFactory factory) {
                return true;
            }

            @Override
            boolean usesHorizontalOutputGasGauge(TileEntityFactory factory) {
                return false;
            }

            @Override
            boolean showsOutputGasGauge(TileEntityFactory factory) {
                return false;
            }

            @Override
            int getOutputGasGaugeX(TileEntityFactory factory) {
                return 6;
            }

            @Override
            int getOutputGasGaugeY() {
                return 56;
            }

            @Override
            int getInputFluidGaugeX(TileEntityFactory factory) {
                return factory.getProcessGuiSlotX(0);
            }

            @Override
            int getInputFluidGaugeY(TileEntityFactory factory) {
                return 98;
            }

            @Override
            boolean usesRedInputFluidGauge() {
                return true;
            }

            @Override
            boolean showsInputFluidGauge(TileEntityFactory factory) {
                return false;
            }

            @Override
            boolean usesSlotInputFluidGauge(TileEntityFactory factory) {
                return false;
            }

            @Override
            boolean usesHorizontalInputFluidGauge(TileEntityFactory factory) {
                return false;
            }

            @Override
            int getFactoryGuiHeightExtra() {
                return 19;
            }

            @Override
            boolean showsLongPowerBar() {
                return true;
            }

            @Override
            boolean usesRecipeExtraEnergy() {
                return true;
            }

            @Override
            int getBaseTicksRequired(TileEntityFactory factory, @Nullable MachineRecipe<?, ?, ?> recipe) {
                return recipe instanceof PressurizedRecipe pressurized ? pressurized.ticks : super.getBaseTicksRequired(factory, recipe);
            }

            @Override
            double getExtraEnergy(MachineRecipe<?, ?, ?> recipe) {
                return recipe instanceof PressurizedRecipe pressurized ? pressurized.extraEnergy : super.getExtraEnergy(recipe);
            }

            @Override
            boolean isItemValid(TileEntityFactory factory, ItemStack stack) {
                FluidStack fluidStack = factory.fluidTank.getFluid();
                GasStack gasStack = factory.gasTank.getGas();
                if (fluidStack == null && gasStack == null) {
                    return hasRecipeForItemInput(factory, stack);
                } else if (fluidStack == null || gasStack == null) {
                    return hasPartialPressurizedRecipeInput(factory, stack, fluidStack, gasStack);
                }
                return RecipeHandler.getPRCRecipe(new PressurizedInput(stack, fluidStack, gasStack)) != null;
            }

            @Override
            boolean isExtraSlotVisible(TileEntityFactory factory) {
                return true;
            }

            @Override
            boolean isValidExtraSlotItem(TileEntityFactory factory, ItemStack stack) {
                return isValidGasExtraSlotItem(factory, stack) || isValidFluidExtraSlotItem(factory, stack);
            }

            @Override
            boolean isValidInputFluid(TileEntityFactory factory, FluidStack fluid) {
                return factory.hasFluidInput() && fluid != null && hasPressurizedRecipeForFluid(fluid);
            }

            @Override
            boolean canInsertInputGas(TileEntityFactory factory, Gas gas) {
                return isValidInputGas(factory, gas) && canInsertPressurizedInputGas(factory, gas);
            }

            @Override
            boolean canInsertInputFluid(TileEntityFactory factory, FluidStack fluid) {
                return isValidInputFluid(factory, fluid) && canInsertPressurizedInputFluid(factory, fluid);
            }

            @Override
            int getExtraSlotY() {
                return 57;
            }

            @Override
            void dumpSecondaryResource(TileEntityFactory factory) {
                factory.gasTank.setEmpty();
                factory.fluidTank.setEmpty();
            }

            @Override
            boolean recipeMatchesCurrentInput(TileEntityFactory factory, MachineRecipe<?, ?, ?> recipe, ItemStack input, ItemStack extra) {
                return super.recipeMatchesCurrentInput(factory, recipe, input, extra) &&
                      recipeMatchesPressurizedInputs(factory, (PressurizedInput) recipe.recipeInput);
            }

            @Override
            int getNeededInput(MachineRecipe<?, ?, ?> recipe, ItemStack inputStack) {
                return Math.max(1, ((PressurizedInput) recipe.recipeInput).getSolid().getCount());
            }

            @Override
            boolean canOperate(TileEntityFactory factory, ProcessInfo processInfo, MachineRecipe<?, ?, ?> recipe) {
                return ((PressurizedRecipe) recipe).canOperate(processInfo.inputSlot(), factory.fluidTank, factory.gasTank, factory.gasOutTank,
                      processInfo.outputSlot());
            }

            @Override
            CachedRecipe<MachineRecipe<?, ?, ?>> createCachedRecipe(TileEntityFactory factory, ProcessInfo processInfo, MachineRecipe<?, ?, ?> recipe) {
                return factory.setupFactoryCachedRecipe(new PressurizedReactionCachedRecipe((PressurizedRecipe) recipe, factory.getRecheckAllRecipeErrors(processInfo),
                      factory.getItemInputHandler(processInfo),
                      factory.fluidInputHandler,
                      factory.secondaryGasInputHandler,
                      factory.getPressurizedOutputHandler(processInfo)), processInfo);
            }
        },
        NUCLEOSYNTHESIZER {
            @Override
            boolean matchesRecipe(MachineRecipe<?, ?, ?> recipe) {
                return recipe instanceof NucleosynthesizerRecipe;
            }

            @Override
            MachineRecipe<?, ?, ?> findRecipe(TileEntityFactory factory, ItemStack input, ItemStack extra) {
                return RecipeHandler.getNucleosynthesizerRecipe(new NucleosynthesizerInput(input, factory.gasTank.getGas()));
            }

            @Override
            boolean isNucleosynthesizer() {
                return true;
            }

            @Override
            boolean usesSecondaryGasInput() {
                return true;
            }

            @Override
            boolean hasGasInput() {
                return true;
            }

            @Override
            boolean usesSlotInputGasGauge(TileEntityFactory factory) {
                return true;
            }

            @Override
            boolean usesRecipeExtraEnergy() {
                return true;
            }

            @Override
            int getBaseTicksRequired(TileEntityFactory factory, @Nullable MachineRecipe<?, ?, ?> recipe) {
                return recipe instanceof NucleosynthesizerRecipe nucleosynthesizer ? nucleosynthesizer.ticks : super.getBaseTicksRequired(factory, recipe);
            }

            @Override
            double getExtraEnergy(MachineRecipe<?, ?, ?> recipe) {
                return recipe instanceof NucleosynthesizerRecipe nucleosynthesizer ? nucleosynthesizer.extraEnergy : super.getExtraEnergy(recipe);
            }

            @Override
            boolean isItemValid(TileEntityFactory factory, ItemStack stack) {
                GasStack gasStack = factory.gasTank.getGas();
                return gasStack == null ? hasRecipeForItemInput(factory, stack) : RecipeHandler.getNucleosynthesizerRecipe(new NucleosynthesizerInput(stack, gasStack)) != null;
            }

            @Override
            boolean isExtraSlotVisible(TileEntityFactory factory) {
                return true;
            }

            @Override
            boolean isValidExtraSlotItem(TileEntityFactory factory, ItemStack stack) {
                return isValidGasExtraSlotItem(factory, stack);
            }

            @Override
            boolean canInsertInputGas(TileEntityFactory factory, Gas gas) {
                GasStack gasStack = new GasStack(gas, 1);
                return isValidInputGas(factory, gas) && canInsertItemGasInput(factory, stack -> RecipeHandler.getNucleosynthesizerRecipe(new NucleosynthesizerInput(stack, gasStack)) != null);
            }

            @Override
            boolean recipeMatchesCurrentInput(TileEntityFactory factory, MachineRecipe<?, ?, ?> recipe, ItemStack input, ItemStack extra) {
                return super.recipeMatchesCurrentInput(factory, recipe, input, extra) &&
                      recipeMatchesGasInput(factory, (NucleosynthesizerInput) recipe.recipeInput);
            }

            @Override
            int getNeededInput(MachineRecipe<?, ?, ?> recipe, ItemStack inputStack) {
                return Math.max(1, ((NucleosynthesizerInput) recipe.recipeInput).getSolid().getCount());
            }

            @Override
            boolean canOperate(TileEntityFactory factory, ProcessInfo processInfo, MachineRecipe<?, ?, ?> recipe) {
                return ((NucleosynthesizerRecipe) recipe).canOperate(processInfo.inputSlot(), factory.gasTank, processInfo.outputSlot());
            }

            @Override
            CachedRecipe<MachineRecipe<?, ?, ?>> createCachedRecipe(TileEntityFactory factory, ProcessInfo processInfo, MachineRecipe<?, ?, ?> recipe) {
                NucleosynthesizerRecipe nucleosynthesizerRecipe = (NucleosynthesizerRecipe) recipe;
                return factory.setupFactoryCachedRecipe(new TwoInputCachedRecipe<>(nucleosynthesizerRecipe, factory.getRecheckAllRecipeErrors(processInfo),
                      factory.getItemInputHandler(processInfo),
                      factory.secondaryGasInputHandler,
                      factory.getItemOutputHandler(processInfo),
                      () -> nucleosynthesizerRecipe.getInput().getSolid(), () -> nucleosynthesizerRecipe.getInput().getGas(),
                      (input, gas) -> MachineInput.inputContains(input, nucleosynthesizerRecipe.getInput().getSolid())
                            && gas != null && gas.isGasEqual(nucleosynthesizerRecipe.getInput().getGas()),
                      (input, gas) -> nucleosynthesizerRecipe.getOutput().output.copy(), ItemStack::isEmpty,
                      gas -> gas == null || gas.amount <= 0, ItemStack::isEmpty), processInfo);
            }
        },
        BASIC {
            @Override
            boolean matchesRecipe(MachineRecipe<?, ?, ?> recipe) {
                return recipe instanceof BasicMachineRecipe<?>;
            }

            @Override
            List<String> getJeiRecipeCategories(TileEntityFactory factory) {
                if (factory.recipeType == RecipeType.SMELTING) {
                    return Arrays.asList("minecraft.smelting", RecipeHandler.Recipe.ENERGIZED_SMELTER.getJEICategory());
                }
                return super.getJeiRecipeCategories(factory);
            }

            @Override
            MachineRecipe<?, ?, ?> findRecipe(TileEntityFactory factory, ItemStack input, ItemStack extra) {
                return getFactoryRecipe(factory, new ItemStackInput(input));
            }

            @Override
            boolean canOperate(TileEntityFactory factory, ProcessInfo processInfo, MachineRecipe<?, ?, ?> recipe) {
                return ((BasicMachineRecipe<?>) recipe).canOperate(processInfo.inputSlot(), processInfo.outputSlot());
            }

            @Override
            CachedRecipe<MachineRecipe<?, ?, ?>> createCachedRecipe(TileEntityFactory factory, ProcessInfo processInfo, MachineRecipe<?, ?, ?> recipe) {
                BasicMachineRecipe<?> basicRecipe = (BasicMachineRecipe<?>) recipe;
                return factory.setupFactoryCachedRecipe(new OneInputCachedRecipe<>(basicRecipe, factory.getRecheckAllRecipeErrors(processInfo),
                      factory.getItemInputHandler(processInfo),
                      factory.getItemOutputHandler(processInfo),
                      () -> basicRecipe.getInput().ingredient, input -> MachineInput.inputContains(input, basicRecipe.getInput().ingredient),
                      input -> basicRecipe.getOutput().output.copy(), ItemStack::isEmpty, ItemStack::isEmpty), processInfo);
            }
        };

        private static FactoryRecipeHandler forRecipeType(RecipeType type) {
            return switch (type) {
                case COMPRESSING, PURIFYING, INJECTING -> ADVANCED;
                case COMBINING, AllOY -> DOUBLE;
                case SAWING, EXTRACTOR, SEPARATOR -> CHANCE;
                case FARM -> FARM;
                case RECYCLER -> CHANCE2;
                case INFUSING -> INFUSING;
                case PRC -> PRESSURIZED;
                case NUCLEOSYNTHESIZER -> NUCLEOSYNTHESIZER;
                default -> BASIC;
            };
        }

        private static final Map<RecipeType, RecipeHandler.Recipe<?, ?, ?>> RECIPE_REGISTRIES = createRecipeRegistries();
        private static final Map<RecipeType, FactoryInputMatcherCache> INPUT_MATCHERS = createInputMatchers();

        private static Map<RecipeType, RecipeHandler.Recipe<?, ?, ?>> createRecipeRegistries() {
            Map<RecipeType, RecipeHandler.Recipe<?, ?, ?>> registries = new EnumMap<>(RecipeType.class);
            registries.put(RecipeType.SMELTING, RecipeHandler.Recipe.ENERGIZED_SMELTER);
            registries.put(RecipeType.ENRICHING, RecipeHandler.Recipe.ENRICHMENT_CHAMBER);
            registries.put(RecipeType.CRUSHING, RecipeHandler.Recipe.CRUSHER);
            registries.put(RecipeType.COMPRESSING, RecipeHandler.Recipe.OSMIUM_COMPRESSOR);
            registries.put(RecipeType.COMBINING, RecipeHandler.Recipe.COMBINER);
            registries.put(RecipeType.PURIFYING, RecipeHandler.Recipe.PURIFICATION_CHAMBER);
            registries.put(RecipeType.INJECTING, RecipeHandler.Recipe.CHEMICAL_INJECTION_CHAMBER);
            registries.put(RecipeType.INFUSING, RecipeHandler.Recipe.METALLURGIC_INFUSER);
            registries.put(RecipeType.SAWING, RecipeHandler.Recipe.PRECISION_SAWMILL);
            registries.put(RecipeType.STAMPING, RecipeHandler.Recipe.STAMPING);
            registries.put(RecipeType.ROLLING, RecipeHandler.Recipe.ROLLING);
            registries.put(RecipeType.BRUSHED, RecipeHandler.Recipe.BRUSHED);
            registries.put(RecipeType.TURNING, RecipeHandler.Recipe.TURNING);
            registries.put(RecipeType.AllOY, RecipeHandler.Recipe.ALLOY);
            registries.put(RecipeType.EXTRACTOR, RecipeHandler.Recipe.CELL_EXTRACTOR);
            registries.put(RecipeType.SEPARATOR, RecipeHandler.Recipe.CELL_SEPARATOR);
            registries.put(RecipeType.FARM, RecipeHandler.Recipe.ORGANIC_FARM);
            registries.put(RecipeType.RECYCLER, RecipeHandler.Recipe.RECYCLER);
            registries.put(RecipeType.PRC, RecipeHandler.Recipe.PRESSURIZED_REACTION_CHAMBER);
            registries.put(RecipeType.NUCLEOSYNTHESIZER, RecipeHandler.Recipe.ANTIPROTONIC_NUCLEOSYNTHESIZER);
            return registries;
        }

        private static Map<RecipeType, FactoryInputMatcherCache> createInputMatchers() {
            Map<RecipeType, FactoryInputMatcherCache> matchers = new EnumMap<>(RecipeType.class);
            for (Map.Entry<RecipeType, RecipeHandler.Recipe<?, ?, ?>> entry : RECIPE_REGISTRIES.entrySet()) {
                matchers.put(entry.getKey(), new FactoryInputMatcherCache(entry.getValue()));
            }
            return matchers;
        }

        private static RecipeHandler.Recipe<?, ?, ?> getRecipeRegistry(RecipeType type) {
            RecipeHandler.Recipe<?, ?, ?> registry = RECIPE_REGISTRIES.get(type);
            if (registry == null) {
                throw new IllegalStateException("Missing factory recipe registry for " + type);
            }
            return registry;
        }

        private static FactoryInputMatcherCache getInputMatcherCache(RecipeType type) {
            FactoryInputMatcherCache cache = INPUT_MATCHERS.get(type);
            if (cache == null) {
                throw new IllegalStateException("Missing factory input matcher cache for " + type);
            }
            return cache;
        }

        private static class FactoryInputMatcherCache {
            private final RecipeHandler.Recipe<?, ?, ?> recipeRegistry;
            private Map<Item, List<Object>> inputMatchers;
            private Map<Item, List<AdvancedMachineInput>> advancedInputMatchers;
            private Map<Item, List<DoubleMachineInput>> doubleExtraMatchers;
            private int recipeSize = -1;
            private int recipeVersion = -1;

            private FactoryInputMatcherCache(RecipeHandler.Recipe<?, ?, ?> recipeRegistry) {
                this.recipeRegistry = recipeRegistry;
            }

            private boolean hasAdvancedRecipeInput(ItemStack itemStack) {
                if (itemStack.isEmpty()) {
                    return false;
                }
                rebuildIfNeeded();
                List<AdvancedMachineInput> matchers = advancedInputMatchers.get(itemStack.getItem());
                if (matchers == null || matchers.isEmpty()) {
                    return false;
                }
                for (AdvancedMachineInput input : matchers) {
                    if (StackUtils.equalsWildcard(input.itemStack, itemStack)) {
                        return true;
                    }
                }
                return false;
            }

            private boolean hasRecipeForExtra(ItemStack extraStack) {
                if (extraStack.isEmpty()) {
                    return false;
                }
                rebuildIfNeeded();
                List<DoubleMachineInput> matchers = doubleExtraMatchers.get(extraStack.getItem());
                if (matchers == null || matchers.isEmpty()) {
                    return false;
                }
                for (DoubleMachineInput input : matchers) {
                    if (StackUtils.equalsWildcard(input.extraStack, extraStack)) {
                        return true;
                    }
                }
                return false;
            }

            private boolean hasRecipeForInput(ItemStack stack) {
                if (stack.isEmpty()) {
                    return false;
                }
                rebuildIfNeeded();
                List<Object> matchers = inputMatchers.get(stack.getItem());
                if (matchers == null || matchers.isEmpty()) {
                    return false;
                }
                for (Object matcher : matchers) {
                    if (matcher instanceof AdvancedMachineInput input && MachineInput.inputItemMatches(input.itemStack, stack)) {
                        return true;
                    }
                    if (matcher instanceof ItemStackInput input && MachineInput.inputItemMatches(input.ingredient, stack)) {
                        return true;
                    }
                    if (matcher instanceof DoubleMachineInput input && MachineInput.inputItemMatches(input.itemStack, stack)) {
                        return true;
                    }
                    if (matcher instanceof InfusionInput input && MachineInput.inputItemMatches(input.inputStack, stack)) {
                        return true;
                    }
                    if (matcher instanceof NucleosynthesizerInput input && MachineInput.inputItemMatches(input.getSolid(), stack)) {
                        return true;
                    }
                    if (matcher instanceof PressurizedInput input && MachineInput.inputItemMatches(input.getSolid(), stack)) {
                        return true;
                    }
                }
                return false;
            }

            private void rebuildIfNeeded() {
                int currentRecipeSize = recipeRegistry.get().size();
                int currentRecipeVersion = recipeRegistry.getRecipeVersion();
                if (inputMatchers != null && recipeSize == currentRecipeSize && recipeVersion == currentRecipeVersion) {
                    return;
                }
                Map<Item, List<Object>> rebuiltInputMatchers = new HashMap<>();
                Map<Item, List<AdvancedMachineInput>> rebuiltAdvancedInputMatchers = new HashMap<>();
                Map<Item, List<DoubleMachineInput>> rebuiltDoubleExtraMatchers = new HashMap<>();
                for (Object obj : recipeRegistry.get().entrySet()) {
                    Object key = ((Map.Entry<?, ?>) obj).getKey();
                    if (key instanceof AdvancedMachineInput input) {
                        addInputMatcher(rebuiltInputMatchers, input.itemStack, key);
                        addTypedMatcher(rebuiltAdvancedInputMatchers, input.itemStack, input);
                    } else if (key instanceof ItemStackInput input) {
                        addInputMatcher(rebuiltInputMatchers, input.ingredient, key);
                    } else if (key instanceof DoubleMachineInput input) {
                        addInputMatcher(rebuiltInputMatchers, input.itemStack, key);
                        addTypedMatcher(rebuiltDoubleExtraMatchers, input.extraStack, input);
                    } else if (key instanceof InfusionInput input) {
                        addInputMatcher(rebuiltInputMatchers, input.inputStack, key);
                    } else if (key instanceof NucleosynthesizerInput input) {
                        addInputMatcher(rebuiltInputMatchers, input.getSolid(), key);
                    } else if (key instanceof PressurizedInput input) {
                        addInputMatcher(rebuiltInputMatchers, input.getSolid(), key);
                    }
                }
                inputMatchers = rebuiltInputMatchers;
                advancedInputMatchers = rebuiltAdvancedInputMatchers;
                doubleExtraMatchers = rebuiltDoubleExtraMatchers;
                recipeSize = currentRecipeSize;
                recipeVersion = currentRecipeVersion;
            }

            private static void addInputMatcher(Map<Item, List<Object>> matcherMap, ItemStack ingredient, Object matcher) {
                if (!ingredient.isEmpty()) {
                    matcherMap.computeIfAbsent(ingredient.getItem(), item -> new ArrayList<>()).add(matcher);
                }
            }

            private static <T> void addTypedMatcher(Map<Item, List<T>> matcherMap, ItemStack ingredient, T matcher) {
                if (!ingredient.isEmpty()) {
                    matcherMap.computeIfAbsent(ingredient.getItem(), item -> new ArrayList<>()).add(matcher);
                }
            }
        }

        abstract boolean matchesRecipe(MachineRecipe<?, ?, ?> recipe);

        @Nullable
        abstract MachineRecipe<?, ?, ?> findRecipe(TileEntityFactory factory, ItemStack input, ItemStack extra);

        boolean hasItemInput(TileEntityFactory factory) {
            return hasItemInput();
        }

        boolean hasItemInput() {
            return true;
        }

        boolean supportsItem(TileEntityFactory factory) {
            return supportsItem();
        }

        boolean supportsItem() {
            return hasItemInput() || hasItemOutput();
        }

        boolean hasItemOutput(TileEntityFactory factory) {
            return hasItemOutput();
        }

        boolean hasItemOutput() {
            return true;
        }

        boolean hasSecondaryItemOutput() {
            return false;
        }

        boolean hasGasInput(TileEntityFactory factory) {
            return hasGasInput();
        }

        boolean hasGasInput() {
            return false;
        }

        boolean hasGasOutput(TileEntityFactory factory) {
            return hasGasOutput();
        }

        boolean hasGasOutput() {
            return false;
        }

        boolean supportsGas(TileEntityFactory factory) {
            return hasGasInput() || hasGasOutput();
        }

        boolean hasFluidInput(TileEntityFactory factory) {
            return hasFluidInput();
        }

        boolean hasFluidInput() {
            return false;
        }

        boolean usesSecondaryGasInput() {
            return false;
        }

        boolean hasSecondaryResourceBar() {
            return showsInfuseBar() || usesGasSecondaryResourceBar();
        }

        boolean hasSecondaryResourceDump() {
            return hasSecondaryResourceBar();
        }

        boolean usesGasSecondaryResourceBar() {
            return false;
        }

        boolean usesPressurizedTankBars() {
            return false;
        }

        boolean showsInfuseBar() {
            return false;
        }

        boolean handlesInfusionExtraSlot() {
            return false;
        }

        int getFactoryGuiHeightExtra() {
            return 0;
        }

        int getSecondaryResourceClickYOffset() {
            return 0;
        }

        int getInputGasGaugeX(TileEntityFactory factory) {
            return 6;
        }

        int getInputGasGaugeY(TileEntityFactory factory) {
            return 34;
        }

        boolean showsInputGasGauge(TileEntityFactory factory) {
            return hasGasInput(factory) && !usesGasSecondaryResourceBar();
        }

        boolean usesStandardInputGasGauge(TileEntityFactory factory) {
            return false;
        }

        boolean usesSlotInputGasGauge(TileEntityFactory factory) {
            return false;
        }

        boolean usesRedInputGasGauge() {
            return false;
        }

        int getOutputGasGaugeY() {
            return 56;
        }

        boolean showsOutputGasGauge(TileEntityFactory factory) {
            return hasGasOutput(factory);
        }

        int getOutputGasGaugeX(TileEntityFactory factory) {
            return factory.getProcessGuiSlotX(0);
        }

        int getInputFluidGaugeX(TileEntityFactory factory) {
            return 6;
        }

        int getInputFluidGaugeY(TileEntityFactory factory) {
            return 34;
        }

        boolean showsInputFluidGauge(TileEntityFactory factory) {
            return hasFluidInput(factory);
        }

        boolean usesRedInputFluidGauge() {
            return false;
        }

        boolean usesSlotOutputGasGauge(TileEntityFactory factory) {
            return false;
        }

        boolean usesHorizontalOutputGasGauge(TileEntityFactory factory) {
            return false;
        }

        boolean usesSlotInputFluidGauge(TileEntityFactory factory) {
            return false;
        }

        boolean usesHorizontalInputFluidGauge(TileEntityFactory factory) {
            return false;
        }

        void dumpSecondaryResource(TileEntityFactory factory) {
            if (showsInfuseBar()) {
                factory.infuseStored.setEmpty();
            }
        }

        boolean isPressurized() {
            return false;
        }

        boolean isInfusing() {
            return false;
        }

        boolean isNucleosynthesizer() {
            return false;
        }

        boolean isFarm() {
            return false;
        }

        boolean showsLongPowerBar() {
            return hasSecondaryItemOutput();
        }

        boolean usesRecipeExtraEnergy() {
            return false;
        }

        int getBaseTicksRequired(TileEntityFactory factory, @Nullable MachineRecipe<?, ?, ?> recipe) {
            return factory.BASE_TICKS_REQUIRED;
        }

        double getExtraEnergy(MachineRecipe<?, ?, ?> recipe) {
            return 0;
        }

        boolean usesFixedBaseTicks() {
            return false;
        }

        boolean usesActiveStateProgress() {
            return false;
        }

        boolean supportsGasUpgrade(TileEntityFactory factory) {
            return false;
        }

        void initializeRecipeTypeState(TileEntityFactory factory) {
            RecipeType type = factory.recipeType;
            factory.BASE_MAX_ENERGY = factory.maxEnergy = factory.getProcessCount() * Math.max((usesRecipeExtraEnergy() ? 1D : 0.5D) * type.getEnergyStorage(),
                  type.getEnergyUsage());
            factory.BASE_ENERGY_PER_TICK = factory.energyPerTick = type.getEnergyUsage();
            factory.upgradeComponent.setSupported(Upgrade.GAS, supportsGasUpgrade(factory));
            factory.secondaryEnergyPerTick = getSecondaryEnergyPerTick(factory);
            updateSecondaryUsageTracking(factory);
        }

        List<String> getJeiRecipeCategories(TileEntityFactory factory) {
            return Arrays.asList(getRecipeRegistry(factory).getJEICategory());
        }

        boolean hasLegacyInfuseWarning(TileEntityFactory factory) {
            return false;
        }

        boolean hasLegacyProcessInfuseWarning(TileEntityFactory factory, ProcessInfo processInfo) {
            return false;
        }

        boolean usesStatisticalSecondaryFuel(TileEntityFactory factory) {
            return false;
        }

        int getSecondaryEnergyThisTick(TileEntityFactory factory) {
            return usesStatisticalSecondaryFuel(factory) ? StatUtils.inversePoisson(factory.secondaryEnergyPerTick)
                  : ceilSecondaryEnergyPerTick(factory.secondaryEnergyPerTick);
        }

        double getSecondaryEnergyPerTick(TileEntityFactory factory) {
            return 0;
        }

        void updateSecondaryUsageTracking(TileEntityFactory factory) {
            factory.baseTotalUsage = MekanismUtils.getBaseUsage(factory, factory.BASE_TICKS_REQUIRED);
        }

        boolean hasRecipeForItemInput(TileEntityFactory factory, @Nonnull ItemStack stack) {
            return getInputMatcherCache(factory.recipeType).hasRecipeForInput(stack);
        }

        boolean hasSecondaryGasInputRecipe(TileEntityFactory factory, @Nonnull ItemStack stack) {
            return getInputMatcherCache(factory.recipeType).hasAdvancedRecipeInput(stack);
        }

        boolean hasRecipeForExtra(TileEntityFactory factory, @Nonnull ItemStack stack) {
            return getInputMatcherCache(factory.recipeType).hasRecipeForExtra(stack);
        }

        boolean shouldKeepProgressWithoutRecipe(TileEntityFactory factory, @Nonnull ItemStack stack) {
            return usesSecondaryGasInput() && hasSecondaryGasInputRecipe(factory, stack);
        }

        boolean isItemValid(TileEntityFactory factory, ItemStack stack) {
            return hasRecipeForItemInput(factory, stack);
        }

        boolean isExtraSlotVisible(TileEntityFactory factory) {
            return false;
        }

        boolean isValidExtraSlotItem(TileEntityFactory factory, ItemStack stack) {
            return false;
        }

        boolean isValidExtraSlotContainerItem(TileEntityFactory factory, ItemStack stack) {
            if (stack.isEmpty()) {
                return true;
            }
            if (hasGasInput(factory) && GasInventorySlot.isGasContainerItem(stack)) {
                return true;
            }
            return hasFluidInput(factory) && FluidInventorySlot.isFluidContainerItem(stack);
        }

        boolean isValidGasExtraSlotItem(TileEntityFactory factory, ItemStack stack) {
            return GasInventorySlot.fillOrConvertInsertCheck(factory.gasTank, factory::getWorld, stack);
        }

        boolean isValidFluidExtraSlotItem(TileEntityFactory factory, ItemStack stack) {
            return factory.hasFluidInput() && FluidInventorySlot.fillInsertCheck(factory.fluidTank, stack);
        }

        boolean isValidInputGas(TileEntityFactory factory, Gas gas) {
            return factory.supportsGas() && isValidRecipeGas(factory, gas);
        }

        boolean canInsertInputGas(TileEntityFactory factory, Gas gas) {
            return isValidInputGas(factory, gas);
        }

        boolean isValidInputFluid(TileEntityFactory factory, FluidStack fluid) {
            return factory.hasFluidInput() && fluid != null;
        }

        boolean canInsertInputFluid(TileEntityFactory factory, FluidStack fluid) {
            return isValidInputFluid(factory, fluid);
        }

        boolean isValidRecipeGas(TileEntityFactory factory, Gas gas) {
            return getRecipeRegistry(factory).containsRecipe(gas);
        }

        RecipeHandler.Recipe<?, ?, ?> getRecipeRegistry(TileEntityFactory factory) {
            return getRecipeRegistry(factory.recipeType);
        }

        @Nullable
        @SuppressWarnings({"rawtypes", "unchecked"})
        MachineRecipe<?, ?, ?> getFactoryRecipe(TileEntityFactory factory, MachineInput<?> input) {
            return RecipeHandler.getRecipe((MachineInput) input, (RecipeHandler.Recipe) getRecipeRegistry(factory));
        }

        int getExtraSlotY() {
            return 57;
        }

        boolean getExtraSlotLimitMultiplier(TileEntityFactory factory) {
            return false;
        }

        boolean recipeMatchesCurrentInput(TileEntityFactory factory, MachineRecipe<?, ?, ?> recipe, ItemStack input, ItemStack extra) {
            return recipeMatchesPrimaryInput(getRecipeInput(recipe), input);
        }

        boolean recipeMatchesPrimaryInput(ItemStack recipeInput, ItemStack fallbackInput) {
            return !recipeInput.isEmpty() && MachineInput.inputItemMatches(recipeInput, fallbackInput);
        }

        boolean recipeMatchesGasInput(TileEntityFactory factory, AdvancedMachineInput input) {
            return factory.gasTank.getGasType() == null || input.gasType == factory.gasTank.getGasType();
        }

        boolean recipeMatchesGasInput(TileEntityFactory factory, NucleosynthesizerInput input) {
            return factory.gasTank.getGas() == null || factory.gasTank.getGas().isGasEqual(input.getGas());
        }

        boolean recipeMatchesDoubleExtraInput(DoubleMachineInput input, ItemStack extra) {
            return extra.isEmpty() || MachineInput.inputItemMatches(input.extraStack, extra);
        }

        boolean recipeMatchesInfusionInput(TileEntityFactory factory, InfusionInput input) {
            return factory.infuseStored.getAmount() == 0 || factory.infuseStored.getType() == input.infuse.getType();
        }

        @Nullable
        MetallurgicInfuserRecipe findInfusingRecipeForInput(TileEntityFactory factory, ItemStack input) {
            if (factory.infuseStored.getType() != null) {
                return RecipeHandler.getMetallurgicInfuserRecipe(new InfusionInput(factory.infuseStored, input));
            }
            for (Map.Entry<InfusionInput, MetallurgicInfuserRecipe> entry : RecipeHandler.Recipe.METALLURGIC_INFUSER.get().entrySet()) {
                if (ItemHandlerHelper.canItemStacksStack(entry.getKey().inputStack, input)) {
                    return entry.getValue();
                }
            }
            return null;
        }

        boolean recipeMatchesPressurizedInputs(TileEntityFactory factory, PressurizedInput input) {
            return (factory.gasTank.getGas() == null || factory.gasTank.getGas().isGasEqual(input.getGas())) &&
                  (factory.fluidTank.getFluid() == null || factory.fluidTank.getFluid().isFluidEqual(input.getFluid()));
        }

        boolean hasPartialPressurizedRecipeInput(TileEntityFactory factory, ItemStack itemstack, @Nullable FluidStack fluidStack, @Nullable GasStack gasStack) {
            for (PressurizedInput input : RecipeHandler.Recipe.PRESSURIZED_REACTION_CHAMBER.get().keySet()) {
                if (recipeMatchesPrimaryInput(input.getSolid(), itemstack) && recipeMatchesAvailablePressurizedSecondary(input, fluidStack, gasStack)) {
                    return true;
                }
            }
            return false;
        }

        boolean recipeMatchesAvailablePressurizedSecondary(PressurizedInput input, @Nullable FluidStack fluidStack, @Nullable GasStack gasStack) {
            return (fluidStack == null || fluidStack.isFluidEqual(input.getFluid())) && (gasStack == null || gasStack.isGasEqual(input.getGas()));
        }

        boolean hasPressurizedRecipeForFluid(FluidStack fluid) {
            for (PressurizedInput input : RecipeHandler.Recipe.PRESSURIZED_REACTION_CHAMBER.get().keySet()) {
                if (input.containsType(fluid)) {
                    return true;
                }
            }
            return false;
        }

        boolean canInsertPressurizedInputGas(TileEntityFactory factory, Gas gas) {
            GasStack gasStack = new GasStack(gas, 1);
            FluidStack fluidStack = factory.fluidTank.getFluid();
            for (ProcessInfo processInfo : factory.processInfoSlots) {
                if (recipeMatchesPressurizedGasInsertion(factory, processInfo.inputSlot().getStack(), fluidStack, gasStack)) {
                    return true;
                }
            }
            return false;
        }

        boolean canInsertPressurizedInputFluid(TileEntityFactory factory, FluidStack fluid) {
            GasStack gasStack = factory.gasTank.getGas();
            for (ProcessInfo processInfo : factory.processInfoSlots) {
                if (recipeMatchesPressurizedFluidInsertion(factory, processInfo.inputSlot().getStack(), fluid, gasStack)) {
                    return true;
                }
            }
            return false;
        }

        boolean recipeMatchesPressurizedGasInsertion(TileEntityFactory factory, ItemStack itemStack, @Nullable FluidStack fluidStack, GasStack gasStack) {
            for (PressurizedInput input : RecipeHandler.Recipe.PRESSURIZED_REACTION_CHAMBER.get().keySet()) {
                if (input.containsType(gasStack) && recipeMatchesOptionalPressurizedItem(factory, input, itemStack) &&
                      recipeMatchesOptionalPressurizedFluid(input, fluidStack)) {
                    return true;
                }
            }
            return false;
        }

        boolean recipeMatchesPressurizedFluidInsertion(TileEntityFactory factory, ItemStack itemStack, FluidStack fluidStack, @Nullable GasStack gasStack) {
            for (PressurizedInput input : RecipeHandler.Recipe.PRESSURIZED_REACTION_CHAMBER.get().keySet()) {
                if (input.containsType(fluidStack) && recipeMatchesOptionalPressurizedItem(factory, input, itemStack) &&
                      recipeMatchesOptionalPressurizedGas(input, gasStack)) {
                    return true;
                }
            }
            return false;
        }

        boolean recipeMatchesOptionalPressurizedItem(TileEntityFactory factory, PressurizedInput input, ItemStack itemStack) {
            return itemStack.isEmpty() || recipeMatchesPrimaryInput(input.getSolid(), itemStack);
        }

        boolean recipeMatchesOptionalPressurizedFluid(PressurizedInput input, @Nullable FluidStack fluidStack) {
            return fluidStack == null || input.containsType(fluidStack);
        }

        boolean recipeMatchesOptionalPressurizedGas(PressurizedInput input, @Nullable GasStack gasStack) {
            return gasStack == null || input.containsType(gasStack);
        }

        boolean canInsertItemGasInput(TileEntityFactory factory, Predicate<ItemStack> recipeCheck) {
            for (ProcessInfo processInfo : factory.processInfoSlots) {
                ItemStack itemStack = processInfo.inputSlot().getStack();
                if (itemStack.isEmpty() || recipeCheck.test(itemStack)) {
                    return true;
                }
            }
            return false;
        }

        boolean recipeMatchesOutputSlots(MachineRecipe<?, ?, ?> recipe, @Nullable IInventorySlot outputSlot, @Nullable IInventorySlot secondaryOutputSlot) {
            return recipeMatchesPrimaryOutputSlot(recipe, outputSlot);
        }

        boolean recipeMatchesPrimaryOutputSlot(MachineRecipe<?, ?, ?> recipe, @Nullable IInventorySlot outputSlot) {
            return outputSlot == null || outputMatches(getPrimaryRecipeOutput(recipe), outputSlot.getStack());
        }

        boolean recipeMatchesSecondaryOutputSlot(MachineRecipe<?, ?, ?> recipe, @Nullable IInventorySlot secondaryOutputSlot) {
            return secondaryOutputSlot == null || outputMatches(getSecondaryRecipeOutput(recipe), secondaryOutputSlot.getStack());
        }

        ItemStack getPrimaryRecipeOutput(MachineRecipe<?, ?, ?> recipe) {
            if (recipe.recipeOutput instanceof ItemStackOutput stackOutput) {
                return stackOutput.output;
            } else if (recipe.recipeOutput instanceof ChanceOutput stackOutput) {
                return stackOutput.getMainOutput();
            } else if (recipe.recipeOutput instanceof ChanceOutput2 stackOutput) {
                return stackOutput.getMaxPrimaryOutput();
            } else if (recipe.recipeOutput instanceof PressurizedOutput stackOutput) {
                return stackOutput.getItemOutput();
            }
            return ItemStack.EMPTY;
        }

        ItemStack getSecondaryRecipeOutput(MachineRecipe<?, ?, ?> recipe) {
            if (recipe.recipeOutput instanceof ChanceOutput stackOutput) {
                return stackOutput.getMaxSecondaryOutput();
            }
            return ItemStack.EMPTY;
        }

        boolean outputMatches(ItemStack recipeOutput, ItemStack output) {
            return InventoryUtils.areItemsStackable(recipeOutput, output);
        }

        int getNeededInput(MachineRecipe<?, ?, ?> recipe, ItemStack inputStack) {
            return Math.max(1, getRecipeInput(recipe).getCount());
        }


        abstract boolean canOperate(TileEntityFactory factory, ProcessInfo processInfo, MachineRecipe<?, ?, ?> recipe);

        abstract CachedRecipe<MachineRecipe<?, ?, ?>> createCachedRecipe(TileEntityFactory factory, ProcessInfo processInfo, MachineRecipe<?, ?, ?> recipe);
    }

    /**
     * <p>Efficient, intelligent factory sequencing.</p>
     * <p><strong>Non-thread safe。</strong></p>
     * <p>In fact, it still has a lot of room for optimization, limited by the structure of the code, these features are sufficient.</p>
     */
    public static class FactoryInvSorter {
        private final TileEntityFactory factory;
        private final Map<HashedItem, RecipeProcessInfo> processMap = new LinkedHashMap<>();
        private final List<RecipeProcessInfo> processes = new ArrayList<>();
        private final List<ProcessInfo> emptyProcesses = new ArrayList<>();

        public FactoryInvSorter(TileEntityFactory factory) {
            this.factory = factory;
        }

        public void sort() {
            factory.runContainerTransaction(this::sortInTransaction);
        }

        private void sortInTransaction() {
            if (!factory.isSorting()) {
                return;
            }

            reset();

            collectProcesses();
            if (processes.isEmpty()) {
                return;
            }

            if (hasEmptyProcesses()) {
                addEmptySlotsAsTargets();
            }
            if (distributeItems()) {
                factory.markNoUpdateSync();
            }
        }

        private void reset() {
            processes.clear();
            processMap.clear();
            emptyProcesses.clear();
        }

        private boolean hasEmptyProcesses() {
            return !emptyProcesses.isEmpty();
        }

        private void collectProcesses() {
            for (ProcessInfo processInfo : factory.processInfoSlots) {
                if (processInfo == null) {
                    continue;
                }
                if (factory.isProcessInputLockedForSorting(processInfo)) {
                    continue;
                }
                FactoryInputInventorySlot inputSlot = processInfo.inputSlot();
                if (inputSlot.isEmpty()) {
                    emptyProcesses.add(processInfo);
                    continue;
                }
                addInputProcess(processInfo, inputSlot.getStack());
            }
        }

        private void addInputProcess(ProcessInfo processInfo, ItemStack inputStack) {
            RecipeProcessInfo recipeProcessInfo = getOrCreateProcessInfo(HashedItem.raw(inputStack));
            recipeProcessInfo.processes.add(processInfo);
            recipeProcessInfo.totalCount += inputStack.getCount();
            tryInitializeMinPerSlot(processInfo, recipeProcessInfo, inputStack);
        }

        private void tryInitializeMinPerSlot(ProcessInfo processInfo, RecipeProcessInfo recipeProcessInfo, ItemStack inputStack) {
            if (recipeProcessInfo.lazyMinPerSlot != null) {
                return;
            }
            if (factory.areRecipeCachesInvalid()) {
                return;
            }
            CachedRecipe<MachineRecipe<?, ?, ?>> cachedRecipe = factory.getCachedRecipe(processInfo);
            if (factory.isCachedRecipeValid(processInfo, cachedRecipe, inputStack)) {
                recipeProcessInfo.recipe = cachedRecipe.getRecipe();
                ItemStack recipeInput = inputStack.copy();
                recipeProcessInfo.lazyMinPerSlot = () -> factory.getNeededInput(cachedRecipe.getRecipe(), recipeInput);
            }
        }

        private RecipeProcessInfo getOrCreateProcessInfo(HashedItem item) {
            RecipeProcessInfo processInfo = processMap.get(item);
            if (processInfo == null) {
                processInfo = new RecipeProcessInfo(item);
                processMap.put(item, processInfo);
                processes.add(processInfo);
            }
            return processInfo;
        }

        private void addEmptySlotsAsTargets() {
            for (RecipeProcessInfo recipeProcessInfo : processes) {
                int emptyToAdd = getEmptyProcessTargetsToAdd(recipeProcessInfo);
                if (emptyToAdd <= 0) {
                    continue;
                }
                int added = 0;
                List<ProcessInfo> toRemove = new ArrayList<>();
                for (ProcessInfo emptyProcess : emptyProcesses) {
                    if (factory.inputProducesOutput(emptyProcess, recipeProcessInfo.item.getInternalStack(), true)) {
                        recipeProcessInfo.processes.add(emptyProcess);
                        toRemove.add(emptyProcess);
                        added++;
                        if (added >= emptyToAdd) {
                            break;
                        }
                    }
                }
                emptyProcesses.removeAll(toRemove);
                if (!hasEmptyProcesses()) {
                    break;
                }
            }
        }

        private int getEmptyProcessTargetsToAdd(RecipeProcessInfo recipeProcessInfo) {
            int minPerSlot = recipeProcessInfo.getMinPerSlot(factory);
            int maxSlots = recipeProcessInfo.totalCount / minPerSlot;
            if (maxSlots <= 1) {
                return 0;
            }
            int processCount = recipeProcessInfo.processes.size();
            return maxSlots > processCount ? maxSlots - processCount : 0;
        }

        private boolean distributeItems() {
            boolean changed = false;
            for (RecipeProcessInfo recipeProcessInfo : processes) {
                int processCount = recipeProcessInfo.processes.size();
                if (processCount == 1) {
                    continue;
                }
                DistributionState state = getInitialDistributionState(recipeProcessInfo, processCount);
                if (state.numberPerSlot() == state.maxStackSize()) {
                    continue;
                }
                List<DistributionPlan> plan = buildDistributionPlan(recipeProcessInfo, state, processCount);
                if (isDistributionPlanValid(recipeProcessInfo, plan)) {
                    changed |= applyDistributionPlan(plan);
                }
            }
            return changed;
        }

        private boolean isDistributionPlanValid(RecipeProcessInfo recipeProcessInfo, List<DistributionPlan> plan) {
            int totalCount = 0;
            for (DistributionPlan target : plan) {
                int sizeForSlot = target.sizeForSlot();
                if (sizeForSlot < 0) {
                    return false;
                }
                if (sizeForSlot > 0) {
                    FactoryInputInventorySlot inputSlot = target.inputSlot();
                    ItemStack stack = target.item().createStack(sizeForSlot);
                    if (sizeForSlot > inputSlot.getLimit(stack) || !inputSlot.isItemValid(stack)) {
                        return false;
                    }
                }
                totalCount += sizeForSlot;
            }
            return totalCount == recipeProcessInfo.totalCount;
        }

        private List<DistributionPlan> buildDistributionPlan(RecipeProcessInfo recipeProcessInfo, DistributionState state, int processCount) {
            List<DistributionPlan> plan = new ArrayList<>(processCount);
            int remainder = state.remainder();
            for (int i = 0; i < processCount; i++) {
                ProcessInfo processInfo = recipeProcessInfo.processes.get(i);
                DistributionTarget target = getDistributionTarget(state.numberPerSlot(), remainder, state.minPerSlot());
                plan.add(new DistributionPlan(processInfo.inputSlot(), recipeProcessInfo.item, target.sizeForSlot()));
                remainder = target.remainder();
            }
            return plan;
        }

        private boolean applyDistributionPlan(List<DistributionPlan> plan) {
            boolean changed = false;
            for (DistributionPlan target : plan) {
                changed |= applySlotDistribution(target.inputSlot(), target.item(), target.sizeForSlot());
            }
            return changed;
        }

        private boolean applySlotDistribution(FactoryInputInventorySlot inputSlot, HashedItem item, int sizeForSlot) {
            if (inputSlot.isEmpty()) {
                if (sizeForSlot > 0) {
                    inputSlot.setStackUnchecked(item.createStack(sizeForSlot));
                    return true;
                }
            } else if (sizeForSlot == 0) {
                inputSlot.setEmpty();
                return true;
            } else if (ItemHandlerHelper.canItemStacksStack(inputSlot.getStack(), item.getInternalStack()) && inputSlot.getCount() != sizeForSlot) {
                MekanismUtils.logMismatchedStackSize(sizeForSlot, inputSlot.setStackSize(sizeForSlot, Action.EXECUTE));
                return true;
            }
            return false;
        }

        private DistributionState getInitialDistributionState(RecipeProcessInfo recipeProcessInfo, int processCount) {
            HashedItem item = recipeProcessInfo.item;
            int maxStackSize = item.getInternalStack().getMaxStackSize();
            int numberPerSlot = recipeProcessInfo.totalCount / processCount;
            int remainder = recipeProcessInfo.totalCount % processCount;
            int minPerSlot = recipeProcessInfo.getMinPerSlot(factory);
            if (minPerSlot > 1) {
                int perSlotRemainder = numberPerSlot % minPerSlot;
                if (perSlotRemainder > 0) {
                    numberPerSlot -= perSlotRemainder;
                    remainder += perSlotRemainder * processCount;
                }
                if (numberPerSlot + minPerSlot > maxStackSize) {
                    minPerSlot = maxStackSize - numberPerSlot;
                }
            }
            return new DistributionState(maxStackSize, numberPerSlot, remainder, minPerSlot);
        }

        @Desugar
        private record DistributionState(int maxStackSize, int numberPerSlot, int remainder, int minPerSlot) {
        }

        private DistributionTarget getDistributionTarget(int numberPerSlot, int remainder, int minPerSlot) {
            int sizeForSlot = numberPerSlot;
            if (remainder > 0) {
                if (remainder > minPerSlot) {
                    sizeForSlot += minPerSlot;
                    remainder -= minPerSlot;
                } else {
                    sizeForSlot += remainder;
                    remainder = 0;
                }
            }
            return new DistributionTarget(sizeForSlot, remainder);
        }

        @Desugar
        private record DistributionTarget(int sizeForSlot, int remainder) {
        }

        @Desugar
        private record DistributionPlan(FactoryInputInventorySlot inputSlot, HashedItem item, int sizeForSlot) {
        }

        private static class RecipeProcessInfo {
            private final HashedItem item;
            private final List<ProcessInfo> processes = new ArrayList<>();
            @Nullable
            private IntSupplier lazyMinPerSlot;
            @Nullable
            private MachineRecipe<?, ?, ?> recipe;
            private int minPerSlot = 1;
            private int totalCount;

            private RecipeProcessInfo(HashedItem item) {
                this.item = item;
            }

            private int getMinPerSlot(TileEntityFactory factory) {
                if (lazyMinPerSlot != null) {
                    minPerSlot = Math.max(1, lazyMinPerSlot.getAsInt());
                    lazyMinPerSlot = null;
                } else if (recipe == null && !processes.isEmpty()) {
                    ItemStack largerInput = getLargerInputStack();
                    ProcessInfo processInfo = processes.get(0);
                    recipe = factory.getRecipeForInput(processInfo, largerInput, true);
                    if (recipe != null) {
                        minPerSlot = Math.max(1, factory.getNeededInput(recipe, largerInput));
                    }
                }
                return minPerSlot;
            }

            private ItemStack getLargerInputStack() {
                return item.createStack(Math.min(item.getInternalStack().getMaxStackSize(), totalCount));
            }
        }
    }

    @Desugar
    private record ProcessInfo(int process, @Nonnull FactoryInputInventorySlot inputSlot, @Nonnull IInventorySlot outputSlot,
                               @Nullable IInventorySlot secondaryOutputSlot) {
    }

    private static class ErrorTracker {
        private final List<RecipeError> errorTypes;
        private final IntSet globalTypes;
        private final boolean[][] trackedErrors;
        private final int processes;

        private ErrorTracker(List<RecipeError> errorTypes, Set<RecipeError> globalErrorTypes, int processes) {
            this.errorTypes = new ArrayList<>(errorTypes);
            globalTypes = new IntArraySet(globalErrorTypes.size());
            for (int i = 0; i < this.errorTypes.size(); i++) {
                if (globalErrorTypes.contains(this.errorTypes.get(i))) {
                    globalTypes.add(i);
                }
            }
            this.processes = processes;
            trackedErrors = new boolean[processes][this.errorTypes.size()];
        }

        private void track(MekanismContainer container) {
            container.trackArray(trackedErrors);
        }

        private void clearErrors(int processIndex) {
            if (isValidProcessIndex(processIndex)) {
                Arrays.fill(trackedErrors[processIndex], false);
            }
        }

        private void onErrorsChanged(Set<RecipeError> errors, int processIndex) {
            if (isValidProcessIndex(processIndex)) {
                boolean[] processTrackedErrors = trackedErrors[processIndex];
                for (int i = 0; i < processTrackedErrors.length; i++) {
                    processTrackedErrors[i] = errors.contains(errorTypes.get(i));
                }
            }
        }

        private BooleanSupplier getWarningCheck(RecipeError error, int processIndex) {
            int errorIndex = getErrorIndex(error);
            if (errorIndex < 0 || !isValidProcessIndex(processIndex)) {
                return () -> false;
            }
            if (isGlobalErrorIndex(errorIndex)) {
                return () -> {
                    for (boolean[] processTrackedErrors : trackedErrors) {
                        if (processTrackedErrors[errorIndex]) {
                            return true;
                        }
                    }
                    return false;
                };
            }
            return () -> trackedErrors[processIndex][errorIndex];
        }

        private boolean hasError(RecipeError error) {
            int errorIndex = getErrorIndex(error);
            if (errorIndex < 0) {
                return false;
            }
            for (boolean[] processTrackedErrors : trackedErrors) {
                if (processTrackedErrors[errorIndex]) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasError(RecipeError error, int processIndex) {
            int errorIndex = getErrorIndex(error);
            if (errorIndex < 0 || !isValidProcessIndex(processIndex)) {
                return false;
            }
            return isGlobalErrorIndex(errorIndex) ? hasError(error) : trackedErrors[processIndex][errorIndex];
        }

        private int getErrorIndex(RecipeError error) {
            return errorTypes.indexOf(error);
        }

        private boolean isGlobalErrorIndex(int errorIndex) {
            return globalTypes.contains(errorIndex);
        }

        private boolean isValidProcessIndex(int processIndex) {
            return processIndex >= 0 && processIndex < processes;
        }
    }

    @Override
    public int getBlockGuiID(Block block, int metadata) {
        return MachineType.get(block, metadata) != null ? MachineType.get(block, metadata).guiId : -1;
    }

    //TODO:如果是升级，则取消辐射排放
    @Override
    protected boolean shouldDumpRadiation() {
        return isUpgrade;
    }
}
