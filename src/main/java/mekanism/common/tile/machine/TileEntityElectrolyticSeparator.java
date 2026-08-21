package mekanism.common.tile.machine;

import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.TileNetworkList;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTank;
import mekanism.api.math.MathUtils;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.Upgrade;
import mekanism.common.Upgrade.IUpgradeInfoHandler;
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
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.FluidInventorySlot;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.cache.CachedRecipe;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.cache.OneInputCachedRecipe;
import mekanism.common.recipe.cache.inputs.InputHelper;
import mekanism.common.recipe.cache.outputs.OutputHelper;
import mekanism.common.recipe.inputs.FluidInput;
import mekanism.common.recipe.machines.SeparatorRecipe;
import mekanism.common.recipe.outputs.ChemicalPairOutput;
import mekanism.common.tier.GasTankTier;
import mekanism.common.tile.TileEntityGasTank.GasMode;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TileEntityElectrolyticSeparator extends TileEntityBasicMachine<FluidInput, ChemicalPairOutput, SeparatorRecipe>
        implements ISustainedData,
        IUpgradeInfoHandler, ITankManager {

    public static final RecipeError NOT_ENOUGH_SPACE_LEFT_OUTPUT_ERROR = RecipeError.create();
    public static final RecipeError NOT_ENOUGH_SPACE_RIGHT_OUTPUT_ERROR = RecipeError.create();
    private static final List<RecipeError> TRACKED_ERROR_TYPES = Arrays.asList(
          RecipeError.NOT_ENOUGH_ENERGY,
          RecipeError.NOT_ENOUGH_ENERGY_REDUCED_RATE,
          RecipeError.NOT_ENOUGH_INPUT,
          NOT_ENOUGH_SPACE_LEFT_OUTPUT_ERROR,
          NOT_ENOUGH_SPACE_RIGHT_OUTPUT_ERROR,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );
    private static final String[] methods = new String[]{"getEnergy", "getOutput", "getMaxEnergy", "getEnergyNeeded", "getWater", "getWaterNeeded", "getHydrogen",
            "getHydrogenNeeded", "getOxygen", "getOxygenNeeded"};
    /**
     * This separator's water slot.
     */
    public BasicFluidTank fluidTank;
    /**
     * The maximum amount of gas this block can store.
     */
    public int MAX_GAS = 2400;
    /**
     * The amount of oxygen this block is storing.
     */
    public BasicGasTank leftTank;
    /**
     * The amount of hydrogen this block is storing.
     */
    public BasicGasTank rightTank;
    /**
     * How fast this block can output gas.
     */
    //public int output = 512;
    /**
     * The type of gas this block is outputting.
     */
    public GasMode dumpLeft = GasMode.IDLE;
    /**
     * Type type of gas this block is dumping.
     */
    public GasMode dumpRight = GasMode.IDLE;
    public SeparatorRecipe cachedRecipe;
    public double clientEnergyUsed;
    private int currentRedstoneLevel;
    private FluidInventorySlot inputSlot;
    private GasInventorySlot leftSlot;
    private GasInventorySlot rightSlot;
    private EnergyInventorySlot energySlot;
    private final boolean[] trackedErrors = new boolean[TRACKED_ERROR_TYPES.size()];

    public TileEntityElectrolyticSeparator() {
        super("electrolyticseparator", MachineType.ELECTROLYTIC_SEPARATOR, 4, 1);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.ENERGY, TransmissionType.GAS, TransmissionType.FLUID);
        initializeInventorySlots();
        configComponent.setupItemInputDualOutputConfig(inputSlot, leftSlot, rightSlot, energySlot);
        configComponent.setConfig(TransmissionType.ITEM, DataType.NONE, DataType.NONE, DataType.INPUT, DataType.ENERGY, DataType.OUTPUT_1, DataType.OUTPUT_2);
        configComponent.setCanEject(TransmissionType.ITEM, false);

        configComponent.addFluidSlotInfo(DataType.INPUT, fluidTank);
        configComponent.setupGasDualOutputConfig(leftTank, rightTank);
        configComponent.setConfig(TransmissionType.GAS, DataType.NONE, DataType.NONE, DataType.NONE, DataType.NONE, DataType.OUTPUT_1, DataType.OUTPUT_2);

        configComponent.setInputConfig(TransmissionType.ENERGY);
        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM, TransmissionType.GAS)
              .setCanTankEject(tank -> {
                  if (tank == leftTank) {
                      return dumpLeft != GasMode.DUMPING;
                  } else if (tank == rightTank) {
                      return dumpRight != GasMode.DUMPING;
                  }
                  return true;
              });
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        inputSlot = builder.addSlot(FluidInventorySlot.fill(fluidTank, listener, 26, 35));
        leftSlot = builder.addSlot(GasInventorySlot.drain(leftTank, listener, 59, 52));
        rightSlot = builder.addSlot(GasInventorySlot.drain(rightTank, listener, 101, 52));
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 143, 35));
        inputSlot.setSlotType(ContainerSlotType.INPUT);
        leftSlot.setSlotType(ContainerSlotType.OUTPUT);
        rightSlot.setSlotType(ContainerSlotType.OUTPUT);
        return builder.build();
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        FluidTankHelper builder = createFluidTankHelper();
        builder.addTank(getOrCreateFluidTank());
        return builder.build();
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        GasTankHelper builder = createGasTankHelper();
        builder.addTank(getOrCreateLeftTank(listener));
        builder.addTank(getOrCreateRightTank(listener));
        return builder.build();
    }

    private BasicFluidTank getOrCreateFluidTank() {
        if (fluidTank == null) {
            fluidTank = BasicFluidTank.input(24_000, fluid -> Recipe.ELECTROLYTIC_SEPARATOR.containsRecipe(fluid.getFluid()), getRecipeCacheListener());
        }
        return fluidTank;
    }

    private BasicGasTank getOrCreateLeftTank(IContentsListener listener) {
        if (leftTank == null) {
            leftTank = BasicGasTank.output(MAX_GAS, getRecipeCacheChangeListener(listener));
        }
        return leftTank;
    }

    private BasicGasTank getOrCreateRightTank(IContentsListener listener) {
        if (rightTank == null) {
            rightTank = BasicGasTank.output(MAX_GAS, getRecipeCacheChangeListener(listener));
        }
        return rightTank;
    }

    @Override
    public void onCachedRecipeChanged(CachedRecipe<SeparatorRecipe> cachedRecipe, int cacheIndex) {
        super.onCachedRecipeChanged(cachedRecipe, cacheIndex);
        double energyUsage = cachedRecipe == null ? MachineType.ELECTROLYTIC_SEPARATOR.getUsage() : cachedRecipe.getRecipe().energyUsage;
        boolean update = BASE_ENERGY_PER_TICK != energyUsage;
        BASE_ENERGY_PER_TICK = energyUsage;
        if (update) {
            recalculateUpgradables(Upgrade.ENERGY);
        }
    }

    public int dumpAmount;

    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        energySlot.fillContainerOrConvert();
        inputSlot.fillTank();
        leftSlot.drainTank();
        rightSlot.drainTank();

        clientEnergyUsed = processRecipe(getMainEnergyContainer());
        prevEnergy = getEnergy();
        dumpAmount = 8 * Math.min((int) Math.pow(2, upgradeComponent.getUpgrades(Upgrade.SPEED)), MekanismConfig.current().mekce.MAXspeedmachines.val());
    }

    @Override
    protected void onUpdateServerPreComponents() {
        super.onUpdateServerPreComponents();
        handleTank(leftTank, dumpLeft, dumpAmount);
        handleTank(rightTank, dumpRight, dumpAmount);
    }

    @Override
    public void addTileSyncTask() {
        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            updateComparatorOutputLevelSync();
            currentRedstoneLevel = newRedstoneLevel;
        }

    }

    private void handleTank(BasicGasTank tank, GasMode mode, int dumpAmount) {
        if (!isContainerExtractionGuarded(tank) && tank.getGas() != null) {
            if (mode == GasMode.DUMPING) {
                tank.extract(dumpAmount, Action.EXECUTE, AutomationType.INTERNAL);
            }
            if (mode == GasMode.DUMPING_EXCESS) {
                int target = getDumpingExcessTarget(tank);
                int stored = tank.getStored();
                if (target < stored) {
                    tank.extract(Math.min(stored - target, GasTankTier.BASIC.getBaseOutput()), Action.EXECUTE, AutomationType.INTERNAL);
                }
            }
        }
    }

    private int getDumpingExcessTarget(GasTank tank) {
        return MathUtils.clampToInt(tank.getMaxGas() * MekanismConfig.current().general.dumpExcessKeepRatio.val());
    }

    public int getUpgradedUsage(SeparatorRecipe recipe) {
        return Math.max(1, Math.min((int) Math.pow(2, upgradeComponent.getUpgrades(Upgrade.SPEED)), MekanismConfig.current().mekce.MAXspeedmachines.val()));
    }

    public SeparatorRecipe getRecipe() {
        refreshRecipeLookupCache();
        FluidInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getElectrolyticSeparatorRecipe(getInput());
        }
        return cachedRecipe;
    }

    @Override
    protected void clearRecipeLookupCache() {
        super.clearRecipeLookupCache();
        cachedRecipe = null;
    }

    @Override
    public SeparatorRecipe getRecipe(int cacheIndex) {
        return getRecipe();
    }

    public FluidInput getInput() {
        return new FluidInput(fluidTank.getFluid());
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

    public boolean hasWarningNoMatchingInput() {
        if (hasWarning(RecipeError.NOT_ENOUGH_INPUT)) {
            return true;
        }
        if (fluidTank.getFluid() == null) {
            return !inputSlot.isEmpty();
        }
        SeparatorRecipe recipe = getRecipe();
        if (recipe == null) {
            return true;
        }
        return fluidTank.getFluidAmount() < recipe.getInput().ingredient.amount;
    }

    public boolean hasWarningNoSpaceLeftOutput() {
        if (hasWarning(NOT_ENOUGH_SPACE_LEFT_OUTPUT_ERROR)) {
            return true;
        }
        ChemicalPairOutput output = getConfiguredOutput();
        return output != null && leftTank.canReceiveType(output.leftGas.getGas()) && leftTank.getNeeded() < output.leftGas.amount;
    }

    public boolean hasWarningNoSpaceRightOutput() {
        if (hasWarning(NOT_ENOUGH_SPACE_RIGHT_OUTPUT_ERROR)) {
            return true;
        }
        ChemicalPairOutput output = getConfiguredOutput();
        return output != null && rightTank.canReceiveType(output.rightGas.getGas()) && rightTank.getNeeded() < output.rightGas.amount;
    }

    public boolean hasWarningInputDoesntProduceOutput() {
        if (hasWarning(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT)) {
            return true;
        }
        return getCurrentOutput() != null && getConfiguredOutput() == null;
    }

    @Override
    public CachedRecipe<SeparatorRecipe> createNewCachedRecipe(SeparatorRecipe recipe, int cacheIndex) {
        return new OneInputCachedRecipe<>(recipe, this::shouldRecheckAllRecipeErrors,
              InputHelper.getFluidInputHandler(fluidTank, RecipeError.NOT_ENOUGH_INPUT),
              OutputHelper.getChemicalPairOutputHandler(leftTank, NOT_ENOUGH_SPACE_LEFT_OUTPUT_ERROR, rightTank, NOT_ENOUGH_SPACE_RIGHT_OUTPUT_ERROR),
              () -> recipe.getInput().ingredient,
              input -> input != null && input.isFluidEqual(recipe.getInput().ingredient),
              input -> recipe.getOutput().copy(), input -> input == null || input.amount <= 0, output -> output == null || !output.isValid())
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
    public Map<FluidInput, SeparatorRecipe> getRecipes() {
        return Recipe.ELECTROLYTIC_SEPARATOR.get();
    }

    private ChemicalPairOutput getCurrentOutput() {
        SeparatorRecipe recipe = getRecipe();
        return recipe == null ? null : recipe.getOutput();
    }

    private ChemicalPairOutput getConfiguredOutput() {
        ChemicalPairOutput output = getCurrentOutput();
        if (output == null || !output.isValid()) {
            return null;
        }
        if (leftTank.canReceiveType(output.leftGas.getGas()) && rightTank.canReceiveType(output.rightGas.getGas())) {
            return output;
        } else if (leftTank.canReceiveType(output.rightGas.getGas()) && rightTank.canReceiveType(output.leftGas.getGas())) {
            return output.swap();
        }
        return null;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            byte type = dataStream.readByte();
            if (type == 0) {
                dumpLeft = GasMode.values()[dumpLeft.ordinal() == GasMode.values().length - 1 ? 0 : dumpLeft.ordinal() + 1];
            } else if (type == 1) {
                dumpRight = GasMode.values()[dumpRight.ordinal() == GasMode.values().length - 1 ? 0 : dumpRight.ordinal() + 1];
            }
            return;
        }

        super.handlePacketData(dataStream);

        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            TileUtils.readTankData(dataStream, fluidTank);
            TileUtils.readTankData(dataStream, leftTank);
            TileUtils.readTankData(dataStream, rightTank);
            dumpLeft = MekanismUtils.getByIndex(GasMode.values(), dataStream.readInt(), dumpLeft);
            dumpRight = MekanismUtils.getByIndex(GasMode.values(), dataStream.readInt(), dumpRight);
            clientEnergyUsed = dataStream.readDouble();
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        TileUtils.addTankData(data, fluidTank);
        TileUtils.addTankData(data, leftTank);
        TileUtils.addTankData(data, rightTank);
        data.add(dumpLeft.ordinal());
        data.add(dumpRight.ordinal());
        data.add(clientEnergyUsed);
        return data;
    }

    public BasicGasTank getTank(EnumFacing side) {
        if (configComponent.hasSlot(TransmissionType.GAS, side, facing, 1)) {
            return leftTank;
        } else if (configComponent.hasSlot(TransmissionType.GAS, side, facing, 2)) {
            return rightTank;
        }
        return null;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (!hasStoredFluidTanks(nbtTags) && nbtTags.hasKey("fluidTank")) {
            fluidTank.readFromNBT(nbtTags.getCompoundTag("fluidTank"));
        }
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("leftTank")) {
            leftTank.read(nbtTags.getCompoundTag("leftTank"));
        }
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("rightTank")) {
            rightTank.read(nbtTags.getCompoundTag("rightTank"));
        }
        dumpLeft = MekanismUtils.getByIndex(GasMode.values(), nbtTags.getInteger("dumpLeft"), dumpLeft);
        dumpRight = MekanismUtils.getByIndex(GasMode.values(), nbtTags.getInteger("dumpRight"), dumpRight);
        sanitizeAndClampTanks();
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("dumpLeft", dumpLeft.ordinal());
        nbtTags.setInteger("dumpRight", dumpRight.ordinal());

    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        return switch (method) {
            case 0 -> new Object[]{electricityStored};
            case 2 -> new Object[]{BASE_MAX_ENERGY};
            case 3 -> new Object[]{BASE_MAX_ENERGY - electricityStored.get()};
            case 4 -> new Object[]{fluidTank.getFluid() != null ? fluidTank.getFluid().amount : 0};
            case 5 ->
                    new Object[]{fluidTank.getFluid() != null ? (fluidTank.getCapacity() - fluidTank.getFluid().amount) : 0};
            case 6 -> new Object[]{leftTank.getStored()};
            case 7 -> new Object[]{leftTank.getNeeded()};
            case 8 -> new Object[]{rightTank.getStored()};
            case 9 -> new Object[]{rightTank.getNeeded()};
            default -> throw new NoSuchMethodException();
        };
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        writeSustainedFluidTanks(itemStack);
        writeSustainedGasTanks(itemStack);
        ItemDataUtils.setLegacyFluid(itemStack, "fluidTank", fluidTank.getFluid());
        ItemDataUtils.setLegacyGas(itemStack, "leftTank", leftTank.getGas());
        ItemDataUtils.setLegacyGas(itemStack, "rightTank", rightTank.getGas());
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (!readSustainedFluidTanks(itemStack)) {
            fluidTank.setStackUnchecked(ItemDataUtils.getLegacyFluid(itemStack, "fluidTank"));
        }
        if (!readSustainedGasTanks(itemStack)) {
            leftTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "leftTank"));
            rightTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "rightTank"));
        }
        sanitizeAndClampTanks();
    }

    private void sanitizeAndClampTanks() {
        sanitizeAndClampTank(fluidTank);
        sanitizeAndClampTank(leftTank);
        sanitizeAndClampTank(rightTank);
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
    public List<String> getInfo(Upgrade upgrade) {
        return upgrade == Upgrade.SPEED ? upgrade.getExpScaledInfo(this) : upgrade.getMultScaledInfo(this);
    }

    @Override
    public Object[] getManagedTanks() {
        return new Object[]{fluidTank, leftTank, rightTank};
    }

    @Override
    public void recalculateUpgradables(Upgrade upgrade) {
        super.recalculateUpgradables(upgrade);
        if (upgrade == Upgrade.ENERGY) {
            maxEnergy = MekanismUtils.getMaxEnergy(this, BASE_MAX_ENERGY);
            energyPerTick = MachineType.ELECTROLYTIC_SEPARATOR.getUsage();
            setEnergy(Math.min(getMaxEnergy(), getEnergy()));
        }
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(fluidTank.getFluidAmount(), fluidTank.getCapacity());
    }
@Override
    protected boolean shouldDumpRadiation() {
        return true;
    }
}
