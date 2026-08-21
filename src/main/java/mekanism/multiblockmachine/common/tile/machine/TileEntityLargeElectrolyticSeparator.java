package mekanism.multiblockmachine.common.tile.machine;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.IConfigCardAccess.ISpecialConfigData;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTank;
import mekanism.api.math.MathUtils;
import mekanism.common.Mekanism;
import mekanism.common.Upgrade;
import mekanism.common.Upgrade.IUpgradeInfoHandler;
import mekanism.common.base.*;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.fluid.ProxiedFluidTankHolder;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.capabilities.holder.gas.ProxiedGasTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.FluidInventorySlot;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.cache.CachedRecipe;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.cache.OneInputCachedRecipe;
import mekanism.common.recipe.cache.inputs.InputHelper;
import mekanism.common.recipe.cache.outputs.OutputHelper;
import mekanism.common.recipe.inputs.FluidInput;
import mekanism.common.recipe.machines.SeparatorRecipe;
import mekanism.common.recipe.outputs.ChemicalPairOutput;
import mekanism.common.tile.TileEntityGasTank.GasMode;
import mekanism.common.tile.prefab.TileEntityBasicMachine;
import mekanism.common.util.*;
import mekanism.multiblockmachine.client.render.block.machine.bloom.BloomRenderLargeElectrolyticSeparator;
import mekanism.multiblockmachine.common.MekanismMultiblockMachine;
import mekanism.multiblockmachine.common.MultiblockMachineUpgrades;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.*;

public class TileEntityLargeElectrolyticSeparator extends TileEntityBasicMachine<FluidInput, ChemicalPairOutput, SeparatorRecipe>
        implements ISustainedData, IUpgradeInfoHandler, ITankManager, ISpecialConfigData, IAdvancedBoundingBlock, ISpecialSelectionWireframeTile {

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
    private static final String[] methods = new String[]{"getEnergy", "getOutput", "getMaxEnergy", "getEnergyNeeded", "getWater", "getWaterNeeded", "getHydrogen", "getHydrogenNeeded", "getOxygen", "getOxygenNeeded"};
    private final EjectSpeedController gasSpeedController = new EjectSpeedController();
    private static final int FLUID_TANK_CAPACITY = 81_920_000;
    private static final int GAS_TANK_CAPACITY = 8_192_000;

    public BasicFluidTank fluidTank;

    public BasicGasTank leftTank;

    public BasicGasTank rightTank;

    public GasMode dumpLeft = GasMode.IDLE;
    public GasMode dumpRight = GasMode.IDLE;
    public SeparatorRecipe cachedRecipe;
    public double clientEnergyUsed;
    private int currentRedstoneLevel;
    private int processes = MekanismConfig.current().multiblock.LargeElectrolyticSeparatorProcesses.val();
    public int numPowering;
    public int updateDelay;
    public boolean needsPacket;
    private FluidInventorySlot inputSlot;
    private GasInventorySlot leftSlot;
    private GasInventorySlot rightSlot;
    private EnergyInventorySlot energySlot;
    private final boolean[] trackedErrors = new boolean[TRACKED_ERROR_TYPES.size()];

    public TileEntityLargeElectrolyticSeparator() {
        super("electrolyticseparator", "LargeElectrolyticSeparator", 0, MachineType.ELECTROLYTIC_SEPARATOR.getUsage(), 4, 1);
        initializeInventorySlots();
        upgradeComponent.setSupported(MultiblockMachineUpgrades.THREAD);
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = InventorySlotHelper.readOnly();
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
        getOrCreateFluidTank();
        return ProxiedFluidTankHolder.create(
              this::isFluidInputSide,
              side -> false,
              side -> side == null || isFluidInputSide(side) ? Collections.singletonList(fluidTank) : Collections.emptyList()
        );
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        getOrCreateLeftTank(listener);
        getOrCreateRightTank(listener);
        return ProxiedGasTankHolder.create(
              side -> false,
              this::isGasOutputSide,
              side -> side == null || isGasOutputSide(side) ? Arrays.asList(leftTank, rightTank) : Collections.emptyList()
        );
    }

    private BasicFluidTank getOrCreateFluidTank() {
        if (fluidTank == null) {
            fluidTank = BasicFluidTank.input(FLUID_TANK_CAPACITY, fluid -> RecipeHandler.Recipe.ELECTROLYTIC_SEPARATOR.containsRecipe(fluid.getFluid()), getRecipeCacheListener());
        }
        return fluidTank;
    }

    private BasicGasTank getOrCreateLeftTank(IContentsListener listener) {
        if (leftTank == null) {
            leftTank = BasicGasTank.output(GAS_TANK_CAPACITY, getRecipeCacheChangeListener(listener));
        }
        return leftTank;
    }

    private BasicGasTank getOrCreateRightTank(IContentsListener listener) {
        if (rightTank == null) {
            rightTank = BasicGasTank.output(GAS_TANK_CAPACITY, getRecipeCacheChangeListener(listener));
        }
        return rightTank;
    }

    private boolean isFluidInputSide(@Nullable EnumFacing side) {
        return side == MekanismUtils.getBack(facing) || side == MekanismUtils.getLeft(facing) || side == MekanismUtils.getRight(facing);
    }

    private boolean isGasOutputSide(@Nullable EnumFacing side) {
        return side == facing;
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

    public int getThread() {
        int thread = 1;
        if (upgradeComponent.isUpgradeInstalled(MultiblockMachineUpgrades.THREAD)) {
            thread += upgradeComponent.getUpgrades(MultiblockMachineUpgrades.THREAD);
        }
        return thread;
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
        inputSlot.fillTank();
        leftSlot.drainTank();
        rightSlot.drainTank();
        clientEnergyUsed = processRecipe(getMainEnergyContainer());
        prevEnergy = getEnergy();
        dumpAmount = 8 * Math.min((int) Math.pow(2, upgradeComponent.getUpgrades(Upgrade.SPEED)), MekanismConfig.current().mekce.MAXspeedmachines.val());
        dumpAmount *= processes;
        dumpAmount *= getThread();
        if (needsPacket) {
            Mekanism.packetHandler.sendUpdatePacket(this);
            needsPacket = false;
        }
    }

    @Override
    protected void onUpdateServerPreComponents() {
        super.onUpdateServerPreComponents();
        gasSpeedController.ensureSize(2,
              () -> Arrays.asList(new TankProvider.Gas(leftTank), new TankProvider.Gas(rightTank)));
        handleTank(leftTank, dumpLeft, getLeftTankside(), dumpAmount, 0);
        handleTank(rightTank, dumpRight, getRightTankside(), dumpAmount, 1);
    }

    @Override
    public void addTileSyncTask() {
        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            world.updateComparatorOutputLevel(pos, getBlockType());
            currentRedstoneLevel = newRedstoneLevel;
        }
    }

    private TileEntity getLeftTankside() {
        BlockPos pos = getPos().offset(facing).offset(MekanismUtils.getLeft(facing));
        if (world.getTileEntity(pos) != null) {
            return world.getTileEntity(pos);
        }
        return null;
    }

    private TileEntity getRightTankside() {
        BlockPos pos = getPos().offset(facing).offset(MekanismUtils.getRight(facing));
        if (world.getTileEntity(pos) != null) {
            return world.getTileEntity(pos);
        }
        return null;
    }

    private void handleTank(BasicGasTank tank, GasMode mode, TileEntity tile, int dumpAmount, int tankidx) {
        if (!isContainerExtractionGuarded(tank) && tank.getGas() != null) {
            if (mode != GasMode.DUMPING) {
                ejectGas(Collections.singleton(facing), tank, this.gasSpeedController, tankidx, tile);
            } else {
                tank.extract(dumpAmount, Action.EXECUTE, AutomationType.INTERNAL);
            }
            if (mode == GasMode.DUMPING_EXCESS) {
                int target = getDumpingExcessTarget(tank);
                int stored = tank.getStored();
                if (target < stored) {
                    tank.extract(Math.min(stored - target, dumpAmount), Action.EXECUTE, AutomationType.INTERNAL);
                }
            }
        }
    }


    private int getDumpingExcessTarget(GasTank tank) {
        return MathUtils.clampToInt(tank.getMaxGas() * MekanismConfig.current().general.dumpExcessKeepRatio.val());
    }

    private void ejectGas(Set<EnumFacing> outputSides, BasicGasTank tank, EjectSpeedController speedController, int tankIdx, TileEntity tile) {
        speedController.record(tankIdx);
        if (tank.getGas() == null || tank.getStored() <= 0 || tank.getGas().getGas() == null) {
            return;
        }
        if (!speedController.canEject(tankIdx)) {
            return;
        }
        GasStack toEmit = tank.getGas().copy().withAmount(Math.min(tank.getMaxGas(), tank.getStored()));
        int emitted = GasUtils.emit(toEmit, tile, outputSides);
        speedController.eject(tankIdx, emitted);
        if (emitted <= 0) {
            return;
        }
        tank.extract(emitted, Action.EXECUTE, AutomationType.INTERNAL);
    }

    public int getUpgradedUsage(SeparatorRecipe recipe) {
        int possibleProcess = Math.min((int) Math.pow(2, upgradeComponent.getUpgrades(Upgrade.SPEED)), MekanismConfig.current().mekce.MAXspeedmachines.val());
        possibleProcess *= processes;
        possibleProcess *= getThread();
        return Math.max(possibleProcess, 1);
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

    public FluidInput getInput() {
        return new FluidInput(fluidTank.getFluid());
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

    @Override
    public Map<FluidInput, SeparatorRecipe> getRecipes() {
        return RecipeHandler.Recipe.ELECTROLYTIC_SEPARATOR.get();
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
    public boolean canExtractItem(int slotID, @Nonnull ItemStack itemstack, @Nonnull EnumFacing side) {
        if (slotID == 3) {
            return EnergyInventorySlot.fillExtractCheck(itemstack);
        } else if (slotID == 0) {
            return FluidInventorySlot.fillExtractCheck(fluidTank, itemstack);
        } else if (slotID == 1 || slotID == 2) {
            return !itemstack.isEmpty() && GasInventorySlot.drainExtractCheck(slotID == 1 ? leftTank : rightTank, itemstack);
        }
        return false;
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return InventoryUtils.EMPTY;
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
            dumpLeft = MekanismUtils.getByIndex(GasMode.values(), dataStream.readInt(), GasMode.IDLE);
            dumpRight = MekanismUtils.getByIndex(GasMode.values(), dataStream.readInt(), GasMode.IDLE);
            clientEnergyUsed = dataStream.readDouble();
            numPowering = dataStream.readInt();
            if (updateDelay == 0) {
                updateDelay = MekanismConfig.current().general.UPDATE_DELAY.val();
                MekanismUtils.updateBlock(world, getPos());
            }
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
        data.add(numPowering);
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (!hasStoredFluidTanks(nbtTags) && nbtTags.hasKey("fluidTank")) {
            fluidTank.readFromNBT(nbtTags.getCompoundTag("fluidTank"));
        }
        if (!hasStoredGasTanks(nbtTags)) {
            if (nbtTags.hasKey("leftTank")) {
                leftTank.read(nbtTags.getCompoundTag("leftTank"));
            }
            if (nbtTags.hasKey("rightTank")) {
                rightTank.read(nbtTags.getCompoundTag("rightTank"));
            }
        }
        dumpLeft = MekanismUtils.getByIndex(GasMode.values(), nbtTags.getInteger("dumpLeft"), GasMode.IDLE);
        dumpRight = MekanismUtils.getByIndex(GasMode.values(), nbtTags.getInteger("dumpRight"), GasMode.IDLE);
        sanitizeAndClampTanks();
        numPowering = nbtTags.getInteger("numPowering");
    }


    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("dumpLeft", dumpLeft.ordinal());
        nbtTags.setInteger("dumpRight", dumpRight.ordinal());
        nbtTags.setInteger("numPowering", numPowering);
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        return switch (method) {
            case 0 -> new Object[]{electricityStored};
            case 1 -> new Object[]{dumpAmount};
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
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return false;
        }
        return capability == Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return null;
        } else if (capability == Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY) {
            return Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, side);
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
            energyPerTick = MachineType.ELECTROLYTIC_SEPARATOR.getUsage();
            setEnergy(Math.min(getMaxEnergy(), getEnergy()));
        }
    }

    @Override
    public double getMaxEnergy() {
        return upgradeComponent.isUpgradeInstalled(Upgrade.ENERGY) ? MekanismUtils.getMaxEnergy(this, getTierEnergy()) : getTierEnergy();
    }

    public double getTierEnergy() {
        return MachineType.ELECTROLYTIC_SEPARATOR.getStorage() * processes * getThread();
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(fluidTank.getFluidAmount(), fluidTank.getCapacity());
    }

    public double getScaledLeftTankGasLevel() {
        return Math.max(Math.min((double) leftTank.getStored() / leftTank.getMaxGas(), 1.0D), 0.0D);
    }

    public double getScaledRightTankGasLevel() {
        return Math.max(Math.min((double) rightTank.getStored() / rightTank.getMaxGas(), 1.0D), 0.0D);
    }

    public double getScaledFluidTankLevel() {
        return Math.max(Math.min((double) fluidTank.getFluidAmount() / fluidTank.getCapacity(), 1.0D), 0.0D);
    }
@Override
    public int getBlockGuiID(Block block, int metadata) {
        return 0;
    }

    @Override
    public IGuiProvider guiProvider() {
        return MekanismMultiblockMachine.proxy;
    }

    @Nonnull
    @Override
    public String getName() {
        return LangUtils.localize("tile.LargeElectrolyticSeparator.name");
    }

    @Override
    public NBTTagCompound getConfigurationData(NBTTagCompound nbtTags) {
        nbtTags.setInteger("dumpLeft", dumpLeft.ordinal());
        nbtTags.setInteger("dumpRight", dumpRight.ordinal());
        return nbtTags;
    }

    @Override
    public void setConfigurationData(NBTTagCompound nbtTags) {
        dumpLeft = MekanismUtils.getByIndex(GasMode.values(), nbtTags.getInteger("dumpLeft"), GasMode.IDLE);
        dumpRight = MekanismUtils.getByIndex(GasMode.values(), nbtTags.getInteger("dumpRight"), GasMode.IDLE);
    }

    @Override
    public String getDataType() {
        return getName();
    }

    @Override
    public boolean canBoundReceiveEnergy(BlockPos coord, EnumFacing side) {
        EnumFacing back = MekanismUtils.getBack(facing);
        if (coord.equals(getPos().offset(back))) {
            return side == back;
        }
        return false;
    }

    @Override
    public boolean sideIsConsumer(EnumFacing side) {
        return side == MekanismUtils.getBack(this.facing);
    }

    @Override
    public boolean canBoundOutPutEnergy(BlockPos location, EnumFacing side) {
        return false;
    }

    @Override
    public boolean isPowered() {
        return redstone || numPowering > 0;
    }

    @Override
    public void onPower() {
        numPowering++;
    }

    @Override
    public void onNoPower() {
        numPowering--;
    }

    @Override
    public void collectBoundingBlocks(java.util.function.BiConsumer<BlockPos, Boolean> consumer) {
        for (int y = 0; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || y != 0 || z != 0) {
                        consumer.accept(getPos().add(x, y, z), y == 0);
                    }
                }
            }
        }
    }

    @Override
    public void onPlace() {
        tryPlaceBoundingBlocks(world, Coord4D.get(this));
    }

    @Override
    public void onBreak() {
        removeBoundingBlocks(world, getPos());
    }

    @Override
    public boolean hasOffsetCapability(@NotNull Capability<?> capability, @Nullable EnumFacing side, @NotNull Vec3i offset) {
        if (isOffsetCapabilityDisabled(capability, side, offset)) {
            return false;
        }
        if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            return getGasHandler(side) != null;
        } else if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return getFluidHandler(side) != null;
        } else if (isManagedStrictEnergy(capability)) {
            return getEnergyHandler(capability, side) != null;
        } else if (capability == CapabilityEnergy.ENERGY || isTesla(capability, side)) {
            return true;
        }
        return hasCapability(capability, side);
    }

    @Override
    public @Nullable <T> T getOffsetCapability(@NotNull Capability<T> capability, @Nullable EnumFacing side, @NotNull Vec3i offset) {
        if (isOffsetCapabilityDisabled(capability, side, offset)) {
            return null;
        } else if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            return Capabilities.GAS_HANDLER_CAPABILITY.cast(getGasHandler(side));
        } else if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(getFluidHandler(side));
        } else if (isManagedStrictEnergy(capability)) {
            return getEnergyHandler(capability, side);
        } else if (isTesla(capability, side)) {
            return (T) getTeslaEnergyWrapper(side);
        } else if (capability == CapabilityEnergy.ENERGY) {
            return CapabilityEnergy.ENERGY.cast(getForgeEnergyWrapper(side));
        }
        return getCapability(capability, side);
    }

    @Override
    public boolean isOffsetCapabilityDisabled(@NotNull Capability<?> capability, @Nullable EnumFacing side, @NotNull Vec3i offset) {
        EnumFacing back = facing.getOpposite();
        EnumFacing left = MekanismUtils.getLeft(facing);
        EnumFacing right = MekanismUtils.getRight(facing);
        if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            if (facing == EnumFacing.EAST) {
                if (offset.equals(new Vec3i(1, 0, 1))) {
                    return side != facing;
                }
                if (offset.equals(new Vec3i(1, 0, -1))) {
                    return side != facing;
                }
            } else if (facing == EnumFacing.SOUTH) {
                if (offset.equals(new Vec3i(-1, 0, 1))) {
                    return side != facing;
                }
                if (offset.equals(new Vec3i(1, 0, 1))) {
                    return side != facing;
                }
            } else if (facing == EnumFacing.WEST) {
                if (offset.equals(new Vec3i(-1, 0, 1))) {
                    return side != facing;
                }
                if (offset.equals(new Vec3i(-1, 0, -1))) {
                    return side != facing;
                }
            } else if (facing == EnumFacing.NORTH) {
                if (offset.equals(new Vec3i(-1, 0, -1))) {
                    return side != facing;
                }
                if (offset.equals(new Vec3i(1, 0, -1))) {
                    return side != facing;
                }
            }
            return true;
        }
        if (isManagedStrictEnergy(capability) || capability == CapabilityEnergy.ENERGY || isTesla(capability, side)) {
            if (offset.equals(new Vec3i(back.getXOffset(), 0, back.getZOffset()))) {
                return side != back;
            }
            return true;
        }

        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            if (facing == EnumFacing.EAST) {
                if (offset.equals(new Vec3i(-1, 0, 1))) {
                    return side != left && side != back;
                }
                if (offset.equals(new Vec3i(-1, 0, -1))) {
                    return side != right && side != back;
                }
            } else if (facing == EnumFacing.SOUTH) {
                if (offset.equals(new Vec3i(1, 0, -1))) {
                    return side != right && side != back;
                }
                if (offset.equals(new Vec3i(-1, 0, -1))) {
                    return side != left && side != back;
                }
            } else if (facing == EnumFacing.WEST) {
                if (offset.equals(new Vec3i(1, 0, -1))) {
                    return side != left && side != back;
                }
                if (offset.equals(new Vec3i(1, 0, 1))) {
                    return side != right && side != back;
                }
            } else if (facing == EnumFacing.NORTH) {
                if (offset.equals(new Vec3i(1, 0, 1))) {
                    return side != left && side != back;
                }
                if (offset.equals(new Vec3i(-1, 0, 1))) {
                    return side != right && side != back;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void validate() {
        super.validate();
        if (isRemote()) {
            if (Mekanism.hooks.Bloom && MekanismConfig.current().client.enableBloom.val()) {
                try {
                    new BloomRenderLargeElectrolyticSeparator(this);
                } catch (LinkageError e) {
                    mekanism.common.util.BloomDependencyHelper.disableBloom("BloomRenderLargeElectrolyticSeparator", e);
                }
            }
        }
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            return true;
        } else if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return true;
        } else if (isManagedStrictEnergy(capability) || capability == CapabilityEnergy.ENERGY || isTesla(capability, side)) {
            return true;
        }
        return false;
    }

    private boolean isManagedStrictEnergy(@Nonnull Capability<?> capability) {
        return capability == Capabilities.STRICT_ENERGY_CAPABILITY || isStrictEnergy(capability);
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
        return mekanism.multiblockmachine.client.model.machine.ModelLargeElectrolyticSeparator.class;
    }

    @Override
    protected boolean shouldDumpRadiation() {
        return true;
    }
}
