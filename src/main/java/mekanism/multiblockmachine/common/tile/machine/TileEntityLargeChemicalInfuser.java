package mekanism.multiblockmachine.common.tile.machine;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTank;
import mekanism.common.Mekanism;
import mekanism.common.Upgrade;
import mekanism.common.Upgrade.IUpgradeInfoHandler;
import mekanism.common.base.*;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.capabilities.holder.gas.ProxiedGasTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.cache.CachedRecipe;
import mekanism.common.recipe.cache.ChemicalPairCachedRecipe;
import mekanism.common.recipe.cache.inputs.InputHelper;
import mekanism.common.recipe.cache.outputs.OutputHelper;
import mekanism.common.recipe.inputs.ChemicalPairInput;
import mekanism.common.recipe.machines.ChemicalInfuserRecipe;
import mekanism.common.recipe.outputs.GasOutput;
import mekanism.common.tile.prefab.TileEntityBasicMachine;
import mekanism.common.util.*;
import mekanism.multiblockmachine.client.render.block.machine.bloom.BloomRenderLargeChemicalInfuser;
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
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.*;

public class TileEntityLargeChemicalInfuser extends TileEntityBasicMachine<ChemicalPairInput, GasOutput, ChemicalInfuserRecipe> implements ISustainedData, IUpgradeInfoHandler,
        ITankManager, IAdvancedBoundingBlock, ISpecialSelectionWireframeTile {

    public static final int MAX_GAS = 8192000;
    private static final List<CachedRecipe.OperationTracker.RecipeError> TRACKED_ERROR_TYPES = Arrays.asList(
          CachedRecipe.OperationTracker.RecipeError.NOT_ENOUGH_ENERGY,
          CachedRecipe.OperationTracker.RecipeError.NOT_ENOUGH_ENERGY_REDUCED_RATE,
          CachedRecipe.OperationTracker.RecipeError.NOT_ENOUGH_LEFT_INPUT,
          CachedRecipe.OperationTracker.RecipeError.NOT_ENOUGH_RIGHT_INPUT,
          CachedRecipe.OperationTracker.RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
          CachedRecipe.OperationTracker.RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );
    public BasicGasTank leftTank;
    public BasicGasTank rightTank;
    public BasicGasTank centerTank;
    private final EjectSpeedController gasSpeedController = new EjectSpeedController();
    public ChemicalInfuserRecipe cachedRecipe;

    public double clientEnergyUsed;
    private int currentRedstoneLevel;
    public int processes = MekanismConfig.current().multiblock.LargeChemicalInfuserProcesses.val();
    public int numPowering;
    public int updateDelay;
    public boolean needsPacket;
    private GasInventorySlot leftSlot;
    private GasInventorySlot rightSlot;
    private GasInventorySlot centerSlot;
    private EnergyInventorySlot energySlot;

    public TileEntityLargeChemicalInfuser() {
        super("cheminfuser", MachineType.CHEMICAL_INFUSER, 4, 1, TRACKED_ERROR_TYPES);
        fullName = "LargeChemicalInfuser";
        initializeInventorySlots();
        upgradeComponent.setSupported(MultiblockMachineUpgrades.THREAD);
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = InventorySlotHelper.readOnly();
        leftSlot = builder.addSlot(GasInventorySlot.fill(leftTank, listener, 6, 56));
        rightSlot = builder.addSlot(GasInventorySlot.fill(rightTank, listener, 154, 56));
        centerSlot = builder.addSlot(GasInventorySlot.drain(centerTank, listener, 80, 65));
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 154, 14));
        leftSlot.setSlotType(ContainerSlotType.INPUT);
        leftSlot.setSlotOverlay(SlotOverlay.MINUS);
        rightSlot.setSlotType(ContainerSlotType.INPUT);
        rightSlot.setSlotOverlay(SlotOverlay.MINUS);
        centerSlot.setSlotType(ContainerSlotType.OUTPUT);
        centerSlot.setSlotOverlay(SlotOverlay.PLUS);
        return builder.build();
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        getOrCreateLeftTank();
        getOrCreateRightTank();
        getOrCreateCenterTank(listener);
        return ProxiedGasTankHolder.create(
              this::isGasInputSide,
              this::isGasOutputSide,
              side -> canAccessGasTanks(side) ? Arrays.asList(leftTank, rightTank, centerTank) : Collections.emptyList(),
              side -> isGasInputSide(side) ? Arrays.asList(leftTank, rightTank) : Collections.emptyList(),
              side -> isGasOutputSide(side) ? Collections.singletonList(centerTank) : Collections.emptyList()
        );
    }

    private BasicGasTank getOrCreateLeftTank() {
        if (leftTank == null) {
            leftTank = BasicGasTank.input(MAX_GAS, this::isValidLeftGas, getRecipeCacheListener());
        }
        return leftTank;
    }

    private BasicGasTank getOrCreateRightTank() {
        if (rightTank == null) {
            rightTank = BasicGasTank.input(MAX_GAS, this::isValidRightGas, getRecipeCacheListener());
        }
        return rightTank;
    }

    private BasicGasTank getOrCreateCenterTank(IContentsListener listener) {
        if (centerTank == null) {
            centerTank = BasicGasTank.output(MAX_GAS, getRecipeCacheChangeListener(listener));
        }
        return centerTank;
    }

    private boolean canAccessGasTanks(@Nullable EnumFacing side) {
        return side == null || isGasInputSide(side) || isGasOutputSide(side);
    }

    private boolean isGasInputSide(@Nullable EnumFacing side) {
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
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        if (updateDelay > 0) {
            updateDelay--;
            if (updateDelay == 0) {
                needsPacket = true;
            }
        }
        energySlot.fillContainerOrConvert();
        leftSlot.fillTank();
        rightSlot.fillTank();
        centerSlot.drainTank();
        clientEnergyUsed = processRecipe(getMainEnergyContainer());
        prevEnergy = getEnergy();
        if (needsPacket) {
            Mekanism.packetHandler.sendUpdatePacket(this);
            needsPacket = false;
        }

    }

    public int getUpgradedUsage(ChemicalInfuserRecipe recipe) {
        int possibleProcess = Math.min((int) Math.pow(2, upgradeComponent.getUpgrades(Upgrade.SPEED)), MekanismConfig.current().mekce.MAXspeedmachines.val());
        possibleProcess *= processes;
        possibleProcess *= getThread();
        return Math.max(possibleProcess, 1);
    }


    public int getThread() {
        int thread = 1;
        if (upgradeComponent.isUpgradeInstalled(MultiblockMachineUpgrades.THREAD)) {
            thread += upgradeComponent.getUpgrades(MultiblockMachineUpgrades.THREAD);
        }
        return thread;
    }


    @Override
    protected void onUpdateServerPreComponents() {
        super.onUpdateServerPreComponents();
        gasSpeedController.ensureSize(2,
              () -> Arrays.asList(new TankProvider.Gas(centerTank), new TankProvider.Gas(centerTank)));
        handleTank(centerTank, getLeftTankside(), 0);
        handleTank(centerTank, getRightTankside(), 1);
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
        BlockPos left = getPos().offset(facing).offset(MekanismUtils.getLeft(facing));
        if (world.getTileEntity(left) != null) {
            return world.getTileEntity(left);
        }
        return null;
    }

    private TileEntity getRightTankside() {
        BlockPos right = getPos().offset(facing).offset(MekanismUtils.getRight(facing));
        if (world.getTileEntity(right) != null) {
            return world.getTileEntity(right);
        }
        return null;
    }

    private void handleTank(BasicGasTank tank, TileEntity tile, int tankIdx) {
        if (tile != null) {
            ejectGas(EnumSet.of(facing), tank, this.gasSpeedController, tankIdx, tile);
        }
    }

    private void ejectGas(Set<EnumFacing> outputSides, BasicGasTank tank, EjectSpeedController speedController, int tankIdx, TileEntity tile) {
        speedController.record(tankIdx);
        if (isContainerExtractionGuarded(tank) || tank.getGas() == null || tank.getStored() <= 0 || tank.getGas().getGas() == null) {
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

    @Override
    public ChemicalPairInput getInput() {
        return new ChemicalPairInput(leftTank.getGas(), rightTank.getGas());
    }

    @Override
    public ChemicalInfuserRecipe getRecipe() {
        refreshRecipeLookupCache();
        ChemicalPairInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getChemicalInfuserRecipe(getInput());
        }
        return cachedRecipe;
    }

    @Override
    protected void clearRecipeLookupCache() {
        super.clearRecipeLookupCache();
        cachedRecipe = null;
    }

    @Override
    public CachedRecipe<ChemicalInfuserRecipe> createNewCachedRecipe(ChemicalInfuserRecipe recipe, int cacheIndex) {
        return new ChemicalPairCachedRecipe<>(recipe, this::shouldRecheckAllRecipeErrors,
              InputHelper.getGasInputHandler(leftTank, CachedRecipe.OperationTracker.RecipeError.NOT_ENOUGH_LEFT_INPUT),
              InputHelper.getGasInputHandler(rightTank, CachedRecipe.OperationTracker.RecipeError.NOT_ENOUGH_RIGHT_INPUT),
              OutputHelper.getGasOutputHandler(centerTank, CachedRecipe.OperationTracker.RecipeError.NOT_ENOUGH_OUTPUT_SPACE), recipe.getInput(), recipe.getOutput())
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
    public Map<ChemicalPairInput, ChemicalInfuserRecipe> getRecipes() {
        return RecipeHandler.Recipe.CHEMICAL_INFUSER.get();
    }

    private boolean isValidLeftGas(Gas gas) {
        return isValidGas(gas, rightTank);
    }

    private boolean isValidRightGas(Gas gas) {
        return isValidGas(gas, leftTank);
    }

    private boolean isValidGas(Gas gas, GasTank otherTank) {
        if (gas == null) {
            return false;
        }
        Gas otherGas = otherTank == null ? null : otherTank.getGasType();
        for (ChemicalPairInput input : getRecipes().keySet()) {
            if (otherGas == null && (input.leftGas.getGas() == gas || input.rightGas.getGas() == gas)) {
                return true;
            } else if (otherGas != null && ((input.leftGas.getGas() == gas && input.rightGas.getGas() == otherGas) ||
                  (input.rightGas.getGas() == gas && input.leftGas.getGas() == otherGas))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            clientEnergyUsed = dataStream.readDouble();
            TileUtils.readTankData(dataStream, leftTank);
            TileUtils.readTankData(dataStream, rightTank);
            TileUtils.readTankData(dataStream, centerTank);
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
        TileUtils.addTankData(data, leftTank);
        TileUtils.addTankData(data, rightTank);
        TileUtils.addTankData(data, centerTank);
        data.add(numPowering);
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (!hasStoredGasTanks(nbtTags)) {
            if (nbtTags.hasKey("leftTank")) {
                leftTank.read(nbtTags.getCompoundTag("leftTank"));
            }
            if (nbtTags.hasKey("rightTank")) {
                rightTank.read(nbtTags.getCompoundTag("rightTank"));
            }
            if (nbtTags.hasKey("centerTank")) {
                centerTank.read(nbtTags.getCompoundTag("centerTank"));
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

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return InventoryUtils.EMPTY;
    }

    @Override
    public boolean canExtractItem(int slotID, @Nonnull ItemStack itemstack, @Nonnull EnumFacing side) {
        if (slotID == 0 || slotID == 2) {
            return !itemstack.isEmpty() && GasInventorySlot.fillExtractCheck(slotID == 0 ? leftTank : rightTank, itemstack);
        } else if (slotID == 1) {
            return !itemstack.isEmpty() && GasInventorySlot.drainExtractCheck(centerTank, itemstack);
        } else if (slotID == 3) {
            return EnergyInventorySlot.fillExtractCheck(itemstack);
        }
        return false;
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        writeSustainedGasTanks(itemStack);
        ItemDataUtils.setLegacyGas(itemStack, "leftTank", leftTank.getGas());
        ItemDataUtils.setLegacyGas(itemStack, "rightTank", rightTank.getGas());
        ItemDataUtils.setLegacyGas(itemStack, "centerTank", centerTank.getGas());
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (!readSustainedGasTanks(itemStack)) {
            leftTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "leftTank"));
            rightTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "rightTank"));
            centerTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "centerTank"));
        }
        sanitizeAndClampTanks();
    }

    private void sanitizeAndClampTanks() {
        sanitizeAndClampTank(leftTank);
        sanitizeAndClampTank(rightTank);
        sanitizeAndClampTank(centerTank);
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
        return new Object[]{leftTank, rightTank, centerTank};
    }

    @Override
    public double getMaxEnergy() {
        return upgradeComponent.isUpgradeInstalled(Upgrade.ENERGY) ? MekanismUtils.getMaxEnergy(this, getTierEnergy()) : getTierEnergy();
    }

    public double getTierEnergy() {
        return MachineType.CHEMICAL_INFUSER.getStorage() * processes * getThread();
    }

    @Override
    public String[] getMethods() {
        return new String[0];
    }

    @Override
    public Object[] invoke(int method, Object[] args) throws NoSuchMethodException {
        return new Object[0];
    }


    public double getScaledLeftTankGasLevel() {
        return Math.max(Math.min((double) leftTank.getStored() / leftTank.getMaxGas(), 1.0D), 0.0D);
    }

    public double getScaledRightTankGasLevel() {
        return Math.max(Math.min((double) rightTank.getStored() / rightTank.getMaxGas(), 1.0D), 0.0D);
    }

    public double getScaledGasTankLevel() {
        return Math.max(Math.min((double) centerTank.getStored() / centerTank.getMaxGas(), 1.0D), 0.0D);
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(centerTank.getStored(), centerTank.getMaxGas());
    }

    @Override
    public boolean isPowered() {
        return redstone || numPowering > 0;
    }
@Override
    public int getBlockGuiID(Block block, int metadata) {
        return 1;
    }

    @Override
    public IGuiProvider guiProvider() {
        return MekanismMultiblockMachine.proxy;
    }

    @Nonnull
    @Override
    public String getName() {
        return LangUtils.localize("tile.LargeChemicalInfuser.name");
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
                if (offset.equals(new Vec3i(-1, 0, 1))) {
                    return side != left && side != back;
                }
                if (offset.equals(new Vec3i(-1, 0, -1))) {
                    return side != right && side != back;
                }
            } else if (facing == EnumFacing.SOUTH) {
                if (offset.equals(new Vec3i(-1, 0, 1))) {
                    return side != facing;
                }
                if (offset.equals(new Vec3i(1, 0, 1))) {
                    return side != facing;
                }
                if (offset.equals(new Vec3i(1, 0, -1))) {
                    return side != right && side != back;
                }
                if (offset.equals(new Vec3i(-1, 0, -1))) {
                    return side != left && side != back;
                }
            } else if (facing == EnumFacing.WEST) {
                if (offset.equals(new Vec3i(-1, 0, 1))) {
                    return side != facing;
                }
                if (offset.equals(new Vec3i(-1, 0, -1))) {
                    return side != facing;
                }
                if (offset.equals(new Vec3i(1, 0, -1))) {
                    return side != left && side != back;
                }
                if (offset.equals(new Vec3i(1, 0, 1))) {
                    return side != right && side != back;
                }
            } else if (facing == EnumFacing.NORTH) {
                if (offset.equals(new Vec3i(-1, 0, -1)) || offset.equals(new Vec3i(1, 0, -1))) {
                    return side != facing;
                }
                if (offset.equals(new Vec3i(1, 0, 1))) {
                    return side != left && side != back;
                }
                if (offset.equals(new Vec3i(-1, 0, 1))) {
                    return side != right && side != back;
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

        return false;
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
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
                    new BloomRenderLargeChemicalInfuser(this);
                } catch (LinkageError e) {
                    mekanism.common.util.BloomDependencyHelper.disableBloom("BloomRenderLargeChemicalInfuser", e);
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
        return mekanism.multiblockmachine.client.model.machine.ModelLargeChemicalInfuser.class;
    }

    @Override
    protected boolean shouldDumpRadiation() {
        return true;
    }
}
