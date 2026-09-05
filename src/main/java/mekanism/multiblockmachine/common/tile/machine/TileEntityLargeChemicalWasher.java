package mekanism.multiblockmachine.common.tile.machine;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.gas.GasStack;
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
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.FluidInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.cache.CachedRecipe;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.cache.TwoInputCachedRecipe;
import mekanism.common.recipe.cache.inputs.InputHelper;
import mekanism.common.recipe.cache.outputs.OutputHelper;
import mekanism.common.recipe.inputs.GasAndFluidInput;
import mekanism.common.recipe.machines.WasherRecipe;
import mekanism.common.recipe.outputs.GasOutput;
import mekanism.common.tile.prefab.TileEntityBasicMachine;
import mekanism.common.util.*;
import mekanism.multiblockmachine.client.render.block.machine.bloom.BloomRenderLargeChemicalWasher;
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

public class TileEntityLargeChemicalWasher extends TileEntityBasicMachine<GasAndFluidInput, GasOutput, WasherRecipe> implements ISustainedData, IUpgradeInfoHandler, ITankManager, IAdvancedBoundingBlock, ISpecialSelectionWireframeTile {

    private static final int TANK_CAPACITY = 8_192_000;
    private static final List<RecipeError> TRACKED_ERROR_TYPES = Arrays.asList(
          RecipeError.NOT_ENOUGH_ENERGY,
          RecipeError.NOT_ENOUGH_ENERGY_REDUCED_RATE,
          RecipeError.NOT_ENOUGH_INPUT,
          RecipeError.NOT_ENOUGH_SECONDARY_INPUT,
          RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );
    public BasicFluidTank fluidTank;
    public BasicGasTank inputTank;
    public BasicGasTank outputTank;

    public WasherRecipe cachedRecipe;
    public double clientEnergyUsed;
    private int currentRedstoneLevel;
    private final EjectSpeedController gasSpeedController = new EjectSpeedController();
    public int processes = MekanismConfig.current().multiblock.LargeChemicalWasherProcesses.val();
    public int numPowering;
    public int updateDelay;
    public boolean needsPacket;
    private FluidInventorySlot inputSlot;
    private OutputInventorySlot outputSlot;
    private GasInventorySlot outputGasSlot;
    private EnergyInventorySlot energySlot;

    public TileEntityLargeChemicalWasher() {
        super("washer", MachineType.CHEMICAL_WASHER, 4, 1, TRACKED_ERROR_TYPES);
        fullName = "LargeChemicalWasher";
        initializeInventorySlots();
        upgradeComponent.setSupported(MultiblockMachineUpgrades.THREAD);
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = InventorySlotHelper.readOnly();
        inputSlot = builder.addSlot(FluidInventorySlot.fill(fluidTank, listener, 180, 71));
        outputSlot = builder.addSlot(OutputInventorySlot.at(getRecipeCacheChangeListener(listener), 180, 102));
        outputGasSlot = builder.addSlot(GasInventorySlot.drain(outputTank, listener, 152, 56));
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 152, 14));
        outputGasSlot.setSlotOverlay(SlotOverlay.MINUS);
        inputSlot.setSlotType(ContainerSlotType.INPUT);
        return builder.build();
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        getOrCreateInputTank();
        getOrCreateOutputTank(listener);
        return ProxiedGasTankHolder.create(
              this::isGasInputSide,
              this::isGasOutputSide,
              side -> {
                  if (side == null || side == facing) {
                      return Arrays.asList(inputTank, outputTank);
                  } else if (isGasInputSide(side)) {
                      return Collections.singletonList(inputTank);
                  } else if (isGasOutputSide(side)) {
                      return Collections.singletonList(outputTank);
                  }
                  return Collections.emptyList();
              },
              side -> isGasInputSide(side) ? Collections.singletonList(inputTank) : Collections.emptyList(),
              side -> isGasOutputSide(side) ? Collections.singletonList(outputTank) : Collections.emptyList()
        );
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

    private BasicFluidTank getOrCreateFluidTank() {
        if (fluidTank == null) {
            fluidTank = BasicFluidTank.input(TANK_CAPACITY, fluid -> RecipeHandler.Recipe.CHEMICAL_WASHER.containsRecipe(fluid.getFluid()), getRecipeCacheListener());
        }
        return fluidTank;
    }

    private BasicGasTank getOrCreateInputTank() {
        if (inputTank == null) {
            inputTank = BasicGasTank.input(TANK_CAPACITY, gas -> RecipeHandler.Recipe.CHEMICAL_WASHER.containsRecipe(gas), getRecipeCacheListener());
        }
        return inputTank;
    }

    private BasicGasTank getOrCreateOutputTank(IContentsListener listener) {
        if (outputTank == null) {
            outputTank = BasicGasTank.output(TANK_CAPACITY, getRecipeCacheChangeListener(listener));
        }
        return outputTank;
    }

    private boolean isGasInputSide(@Nullable EnumFacing side) {
        return side == facing || side == MekanismUtils.getLeft(facing);
    }

    private boolean isGasOutputSide(@Nullable EnumFacing side) {
        return side == facing || side == MekanismUtils.getRight(facing);
    }

    private boolean isFluidInputSide(@Nullable EnumFacing side) {
        return side == MekanismUtils.getBack(facing) || side == MekanismUtils.getLeft(facing) || side == MekanismUtils.getRight(facing);
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
    protected mekanism.common.recipe.cache.RecipeLaneCommitTarget createAsyncRecipeCommitTarget(CachedRecipe<WasherRecipe> cache) {
        return new mekanism.common.recipe.cache.RecipeLaneCommitTarget(cache)
              .input("gas.0", inputTank).input("fluid.1", fluidTank).output("gas.0", outputTank);
    }

    @Override
    public void afterAsyncRecipeCommit(mekanism.common.recipe.cache.RecipeRunSnapshot snapshot,
          mekanism.common.recipe.cache.RecipeExecutionPlan plan) {
        super.afterAsyncRecipeCommit(snapshot, plan);
        clientEnergyUsed = plan.getEnergyAsDouble();
        finishRecipeTick();
    }

    @Override
    public void onAsyncUpdateServer() {
        // Explicit planner owns capture, calculation, resource mutation and cache state.
        commitAsyncRecipeTick();
    }

    @Override
    public void prepareAsyncRecipeTick() {
        if (updateDelay > 0) {
            updateDelay--;
            if (updateDelay == 0) {
                needsPacket = true;
            }
        }
        energySlot.fillContainerOrConvert();
        manageBuckets();
        outputGasSlot.drainTank();
    }

    private void finishRecipeTick() {
        prevEnergy = getEnergy();
        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            updateComparatorOutputLevelSync();
            currentRedstoneLevel = newRedstoneLevel;
        }
        if (needsPacket) {
            Mekanism.packetHandler.sendUpdatePacket(this);
            needsPacket = false;
        }
    }

    @Override
    protected void onUpdateServerPreComponents() {
        super.onUpdateServerPreComponents();
        gasSpeedController.ensureSize(1,
              () -> Collections.singletonList(new TankProvider.Gas(outputTank)));
        handleTank(outputTank, getRightTankSide(), facing);
        handleTank(outputTank, getRightTankSide(), MekanismUtils.getRight(facing));
    }

    @Override
    public void addTileSyncTask() {
        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            updateComparatorOutputLevelSync();
            currentRedstoneLevel = newRedstoneLevel;
        }
    }

    private TileEntity getRightTankSide() {
        BlockPos pos = getPos().offset(MekanismUtils.getRight(facing)).offset(facing);
        if (world.getTileEntity(pos) != null) {
            return world.getTileEntity(pos);
        }
        return null;
    }


    private void handleTank(BasicGasTank tank, TileEntity tile, EnumFacing side) {
        if (tile != null) {
            ejectGas(Collections.singleton(side), tank, this.gasSpeedController, tile);
        }
    }

    private void ejectGas(Set<EnumFacing> outputSides, BasicGasTank tank, EjectSpeedController speedController, TileEntity tile) {
        speedController.record(0);
        if (isContainerExtractionGuarded(tank) || tank.getGas() == null || tank.getStored() <= 0 || tank.getGas().getGas() == null) {
            return;
        }
        if (!speedController.canEject(0)) {
            return;
        }
        GasStack toEmit = tank.getGas().copy().withAmount(Math.min(tank.getMaxGas(), tank.getStored()));
        int emitted = GasUtils.emit(toEmit, tile, outputSides);
        speedController.eject(0, emitted);
        if (emitted <= 0) {
            return;
        }
        tank.extract(emitted, Action.EXECUTE, AutomationType.INTERNAL);
    }

    @Override
    public WasherRecipe getRecipe() {
        refreshRecipeLookupCache();
        GasAndFluidInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getChemicalWasherRecipe(getInput());
        }
        return cachedRecipe;
    }

    @Override
    protected void clearRecipeLookupCache() {
        super.clearRecipeLookupCache();
        cachedRecipe = null;
    }

    @Override
    public GasAndFluidInput getInput() {
        return new GasAndFluidInput(inputTank.getGas(), fluidTank.getFluid());
    }

    @Override
    public CachedRecipe<WasherRecipe> createNewCachedRecipe(WasherRecipe recipe, int cacheIndex) {
        return new TwoInputCachedRecipe<>(recipe, this::shouldRecheckAllRecipeErrors,
              InputHelper.getGasInputHandler(inputTank, RecipeError.NOT_ENOUGH_INPUT),
              InputHelper.getFluidInputHandler(fluidTank, RecipeError.NOT_ENOUGH_SECONDARY_INPUT),
              OutputHelper.getGasOutputHandler(outputTank, RecipeError.NOT_ENOUGH_OUTPUT_SPACE),
              () -> recipe.getInput().ingredientGas, () -> recipe.getInput().ingredientFluid,
              (gas, fluid) -> gas != null && gas.isGasEqual(recipe.getInput().ingredientGas)
                    && fluid != null && fluid.isFluidEqual(recipe.getInput().ingredientFluid),
              (gas, fluid) -> recipe.getOutput().output.copy(), gas -> gas == null || gas.amount <= 0,
              fluid -> fluid == null || fluid.amount <= 0, output -> output == null || output.amount <= 0)
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
              .setErrorsChanged(this::onRecipeErrorsChanged)
              .setOnFinish(this::onCachedRecipeFinish);
    }

    @Override
    public Map<GasAndFluidInput, WasherRecipe> getRecipes() {
        return RecipeHandler.Recipe.CHEMICAL_WASHER.get();
    }

    private void manageBuckets() {
        inputSlot.fillTank(outputSlot);
    }

    public int getThread() {
        int thread = 1;
        if (upgradeComponent.isUpgradeInstalled(MultiblockMachineUpgrades.THREAD)) {
            thread += upgradeComponent.getUpgrades(MultiblockMachineUpgrades.THREAD);
        }
        return thread;
    }

    public int getUpgradedUsage(WasherRecipe recipe) {
        int possibleProcess = Math.min((int) Math.pow(2, upgradeComponent.getUpgrades(Upgrade.SPEED)), MekanismConfig.current().mekce.MAXspeedmachines.val());
        possibleProcess *= processes;
        possibleProcess *= getThread();
        return Math.max(possibleProcess, 1);
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            clientEnergyUsed = dataStream.readDouble();
            TileUtils.readTankData(dataStream, fluidTank);
            TileUtils.readTankData(dataStream, inputTank);
            TileUtils.readTankData(dataStream, outputTank);
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
        data.add(clientEnergyUsed);
        TileUtils.addTankData(data, fluidTank);
        TileUtils.addTankData(data, inputTank);
        TileUtils.addTankData(data, outputTank);
        data.add(numPowering);
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (!hasStoredFluidTanks(nbtTags) && nbtTags.hasKey("leftTank")) {
            fluidTank.readFromNBT(nbtTags.getCompoundTag("leftTank"));
        }
        if (!hasStoredGasTanks(nbtTags)) {
            if (nbtTags.hasKey("rightTank")) {
                inputTank.read(nbtTags.getCompoundTag("rightTank"));
            }
            if (nbtTags.hasKey("centerTank")) {
                outputTank.read(nbtTags.getCompoundTag("centerTank"));
            }
        }
        sanitizeAndClampTanks();
        numPowering = nbtTags.getInteger("numPowering");
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("numPowering", numPowering);
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return InventoryUtils.EMPTY;
    }

    @Override
    public boolean canExtractItem(int slotID, @Nonnull ItemStack itemstack, @Nonnull EnumFacing side) {
        if (slotID == 1) {
            return !itemstack.isEmpty() && GasInventorySlot.drainExtractCheck(outputTank, itemstack);
        } else if (slotID == 2) {
            return EnergyInventorySlot.fillExtractCheck(itemstack);
        }
        return false;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return false;
        }
        return super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return null;
        }
        return super.getCapability(capability, side);
    }

    @Override
    public double getMaxEnergy() {
        return upgradeComponent.isUpgradeInstalled(Upgrade.ENERGY) ? MekanismUtils.getMaxEnergy(this, getTierEnergy()) : getTierEnergy();
    }

    public double getTierEnergy() {
        return MachineType.CHEMICAL_WASHER.getStorage() * processes * getThread();
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        writeSustainedFluidTanks(itemStack);
        writeSustainedGasTanks(itemStack);
        ItemDataUtils.setLegacyFluid(itemStack, "fluidTank", fluidTank.getFluid());
        ItemDataUtils.setLegacyGas(itemStack, "inputTank", inputTank.getGas());
        ItemDataUtils.setLegacyGas(itemStack, "outputTank", outputTank.getGas());
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (!readSustainedFluidTanks(itemStack)) {
            fluidTank.setStackUnchecked(ItemDataUtils.getLegacyFluid(itemStack, "fluidTank"));
        }
        if (!readSustainedGasTanks(itemStack)) {
            inputTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "inputTank"));
            outputTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "outputTank"));
        }
        sanitizeAndClampTanks();
    }

    private void sanitizeAndClampTanks() {
        sanitizeAndClampTank(fluidTank);
        sanitizeAndClampTank(inputTank);
        sanitizeAndClampTank(outputTank);
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
        return new Object[]{fluidTank, inputTank, outputTank};
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(inputTank.getStored(), inputTank.getMaxGas());
    }

    @Override
    public boolean isPowered() {
        return redstone || numPowering > 0;
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
    public int getBlockGuiID(Block block, int metadata) {
        return 2;
    }

    @Override
    public IGuiProvider guiProvider() {
        return MekanismMultiblockMachine.proxy;
    }

    @Nonnull
    @Override
    public String getName() {
        return LangUtils.localize("tile.LargeChemicalWasher.name");
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
    public boolean canBoundOutPutEnergy(BlockPos location, EnumFacing side) {
        return false;
    }

    @Override
    public boolean sideIsConsumer(EnumFacing side) {
        return side == MekanismUtils.getBack(facing);
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
    public NBTTagCompound getConfigurationData(NBTTagCompound nbtTags) {
        return nbtTags;
    }

    @Override
    public void setConfigurationData(NBTTagCompound nbtTags) {

    }

    @Override
    public String getDataType() {
        return getName();
    }

    @Override
    public void collectBoundingBlocks(java.util.function.BiConsumer<BlockPos, Boolean> consumer) {
        for (int y = 0; y <= 2; y++) {
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

    @Nullable
    @Override
    public <T> T getOffsetCapability(@NotNull Capability<T> capability, @Nullable EnumFacing side, @NotNull Vec3i offset) {
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
                    return side != facing && side != left;
                }
                if (offset.equals(new Vec3i(1, 0, -1))) {
                    return side != facing && side != right;
                }
            } else if (facing == EnumFacing.SOUTH) {
                if (offset.equals(new Vec3i(-1, 0, 1))) {
                    return side != facing && side != left;
                }
                if (offset.equals(new Vec3i(1, 0, 1))) {
                    return side != facing && side != right;
                }
            } else if (facing == EnumFacing.WEST) {
                if (offset.equals(new Vec3i(-1, 0, 1))) {
                    return side != facing && side != right;
                }
                if (offset.equals(new Vec3i(-1, 0, -1))) {
                    return side != facing && side != left;
                }
            } else if (facing == EnumFacing.NORTH) {
                if (offset.equals(new Vec3i(-1, 0, -1))) {
                    return side != facing && side != right;
                }
                if (offset.equals(new Vec3i(1, 0, -1))) {
                    return side != facing && side != left;
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
    public void validate() {
        super.validate();
        if (isRemote()) {
            if (Mekanism.hooks.Bloom && MekanismConfig.current().client.enableBloom.val()) {
                try {
                    new BloomRenderLargeChemicalWasher(this);
                } catch (LinkageError e) {
                    mekanism.common.util.BloomDependencyHelper.disableBloom("BloomRenderLargeChemicalWasher", e);
                }
            }
        }
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
        return mekanism.multiblockmachine.client.model.machine.ModelLargeChemicalWasher.class;
    }

    @Override
    protected boolean shouldDumpRadiation() {
        return true;
    }
}
