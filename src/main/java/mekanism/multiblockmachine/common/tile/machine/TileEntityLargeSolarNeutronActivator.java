package mekanism.multiblockmachine.common.tile.machine;

import ic2.api.energy.tile.IEnergyEmitter;
import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.gas.GasStack;
import mekanism.common.Mekanism;
import mekanism.common.Upgrade;
import mekanism.common.base.*;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.capabilities.holder.gas.ProxiedGasTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.integration.computer.IComputerIntegration;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.cache.CachedRecipe;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.cache.IRecipeLookupHandler;
import mekanism.common.recipe.cache.OneInputCachedRecipe;
import mekanism.common.recipe.cache.RecipeCacheLookupMonitor;
import mekanism.common.recipe.cache.inputs.InputHelper;
import mekanism.common.recipe.cache.outputs.OutputHelper;
import mekanism.common.recipe.inputs.GasInput;
import mekanism.common.recipe.machines.SolarNeutronRecipe;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.*;
import mekanism.multiblockmachine.client.render.block.machine.bloom.BloomRenderLargeSolarNeutronActivator;
import mekanism.multiblockmachine.common.MekanismMultiblockMachine;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.*;

public class TileEntityLargeSolarNeutronActivator extends TileEntityContainerBlock implements IUpgradeTile, IRedstoneControl, ISecurityTile, IComputerIntegration, IConfigCardAccess, IAdvancedBoundingBlock, ISustainedData, ITankManager, Upgrade.IUpgradeInfoHandler, IComparatorSupport, IActiveState, ISpecialSelectionWireframeTile,
        IRecipeLookupHandler<SolarNeutronRecipe> {

    public static final int MAX_GAS = 8192000;
    private static final List<RecipeError> TRACKED_ERROR_TYPES = Arrays.asList(
          RecipeError.NOT_ENOUGH_INPUT,
          RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );
    private final RecipeCacheLookupMonitor<SolarNeutronRecipe> recipeCacheLookupMonitor = new RecipeCacheLookupMonitor<>(this);
    public BasicGasTank inputTank;
    public BasicGasTank outputTank;
    private SolarNeutronRecipe cachedRecipe;
    private int cachedRecipeVersion = -1;
    private int currentRedstoneLevel;
    private boolean isActive;
    private long lastActive = -1;
    private boolean needsRainCheck;

    public int operatingTicks;

    public int BASE_TICKS_REQUIRED;

    public int ticksRequired;
    private final int RECENT_THRESHOLD = 100;
    public TileComponentUpgrade upgradeComponent;
    public TileComponentSecurity securityComponent = new TileComponentSecurity(this);
    private RedstoneControl controlType = RedstoneControl.DISABLED;
    public int processes = MekanismConfig.current().multiblock.LargeSolarNeutronProcesses.val();
    public int numPowering;
    private final EjectSpeedController gasSpeedController = new EjectSpeedController();
    private boolean seesSunThisTick;
    private GasInventorySlot inputSlot;
    private GasInventorySlot outputSlot;
    private final boolean[] trackedErrors = new boolean[TRACKED_ERROR_TYPES.size()];

    public TileEntityLargeSolarNeutronActivator() {
        this(1);
    }

    public TileEntityLargeSolarNeutronActivator(int baseTicksRequired) {
        super("LargeSolarNeutronActivator");
        ticksRequired = BASE_TICKS_REQUIRED = baseTicksRequired;
        upgradeComponent = new TileComponentUpgrade(this);
        upgradeComponent.setSupported(Upgrade.ENERGY, false);
        upgradeComponent.setSupported(Upgrade.THREAD);
        initializeInventorySlots();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = InventorySlotHelper.readOnly();
        inputSlot = builder.addSlot(GasInventorySlot.fill(inputTank, listener, 5, 56));
        outputSlot = builder.addSlot(GasInventorySlot.drain(outputTank, listener, 155, 56));
        inputSlot.setSlotType(ContainerSlotType.INPUT);
        inputSlot.setSlotOverlay(SlotOverlay.MINUS);
        outputSlot.setSlotType(ContainerSlotType.OUTPUT);
        outputSlot.setSlotOverlay(SlotOverlay.PLUS);
        return builder.build();
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        getOrCreateInputTank();
        getOrCreateOutputTank();
        return ProxiedGasTankHolder.create(
              this::isGasInputSide,
              this::isGasOutputSide,
              side -> {
                  if (side == null) {
                      return Arrays.asList(inputTank, outputTank);
                  } else if (isGasInputSide(side)) {
                      return Collections.singletonList(inputTank);
                  } else if (isGasOutputSide(side)) {
                      return Collections.singletonList(outputTank);
                  }
                  return Collections.emptyList();
              }
        );
    }

    private BasicGasTank getOrCreateInputTank() {
        if (inputTank == null) {
            inputTank = BasicGasTank.input(MAX_GAS, gas -> RecipeHandler.Recipe.SOLAR_NEUTRON_ACTIVATOR.containsRecipe(gas), recipeCacheLookupMonitor);
        }
        return inputTank;
    }

    private BasicGasTank getOrCreateOutputTank() {
        if (outputTank == null) {
            outputTank = BasicGasTank.output(MAX_GAS, this::onRecipeOutputContentsChanged);
        }
        return outputTank;
    }

    private boolean isGasInputSide(@Nullable EnumFacing side) {
        return side == MekanismUtils.getBack(facing);
    }

    private boolean isGasOutputSide(@Nullable EnumFacing side) {
        return side == MekanismUtils.getLeft(facing) || side == MekanismUtils.getRight(facing);
    }

    private void onRecipeOutputContentsChanged() {
        onContentsChanged();
        recipeCacheLookupMonitor.onChange();
    }

    @Override
    public void validate() {
        super.validate();
        // Cache the flag to know if rain matters where this block is placed
        needsRainCheck = world.provider.getBiomeForCoords(getPos()).canRain();
        if (isRemote()) {
            if (Mekanism.hooks.Bloom && MekanismConfig.current().client.enableBloom.val()) {
                try {
                    new BloomRenderLargeSolarNeutronActivator(this);
                } catch (LinkageError e) {
                    mekanism.common.util.BloomDependencyHelper.disableBloom("BloomRenderLargeSolarNeutronActivator", e);
                }
            }
        }
    }

    @Override
    public void onUpdateClient() {
        super.onUpdateClient();
        if (!isActive && lastActive > 0) {
            long updateDiff = world.getTotalWorldTime() - lastActive;
            if (updateDiff > RECENT_THRESHOLD) {
                MekanismUtils.updateBlock(world, getPos());
                lastActive = -1;
            }
        }
    }

    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        Mekanism.EXECUTE_MANAGER.addSyncTask(this::addTileSyncTask);
        inputSlot.fillTank();
        outputSlot.drainTank();

        // TODO: Ideally the neutron activator should use the sky brightness to determine throughput; but
        // changing this would dramatically affect a lot of setups with Fusion reactors which can take
        // a long time to relight. I don't want to be chased by a mob right now, so just doing basic
        // rain checks.
        boolean seesSun = world.isDaytime() && world.canSeeSky(getPos().up(2)) && !world.provider.isNether();
        if (needsRainCheck) {
            seesSun &= !(world.isRaining() || world.isThundering());
        }

        seesSunThisTick = seesSun;
        if (!recipeCacheLookupMonitor.updateAndProcess()) {
            setActive(false);
        }

        // Every 20 ticks (once a second), send update to client. Note that this is a 50% reduction in network
        // traffic from previous implementation that send the update every 10 ticks.
        if (world.getTotalWorldTime() % 20 == 0) {
            Mekanism.packetHandler.sendUpdatePacket(this);
        }

        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            updateComparatorOutputLevelSync();
            currentRedstoneLevel = newRedstoneLevel;
        }
    }

    public int getUpgradedUsage(SolarNeutronRecipe recipe) {
        int possibleProcess = Math.min((int) Math.pow(2, upgradeComponent.getUpgrades(Upgrade.SPEED)), MekanismConfig.current().mekce.MAXspeedmachines.val());
        possibleProcess *= processes;
        possibleProcess *= getThread();
        possibleProcess = Math.min(Math.min(inputTank.getStored(), outputTank.getNeeded()), possibleProcess);
        return Math.min(inputTank.getStored() / recipe.recipeInput.ingredient.amount, possibleProcess);
    }

    public int getThread() {
        int thread = 1;
        if (upgradeComponent.isUpgradeInstalled(Upgrade.THREAD)) {
            thread += upgradeComponent.getUpgrades(Upgrade.THREAD);
        }
        return thread;
    }


    public void addTileSyncTask() {
        this.gasSpeedController.ensureSize(2, () -> Arrays.asList(new TankProvider.Gas(outputTank), new TankProvider.Gas(outputTank)));
        handleTank(outputTank, getLeftTankside(), MekanismUtils.getLeft(facing), 0);
        handleTank(outputTank, getRightTankside(), MekanismUtils.getRight(facing), 1);
        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            world.updateComparatorOutputLevel(pos, getBlockType());
            currentRedstoneLevel = newRedstoneLevel;
        }
    }

    private TileEntity getLeftTankside() {
        BlockPos left = getPos().offset(MekanismUtils.getBack(facing)).offset(MekanismUtils.getLeft(facing));
        if (world.getTileEntity(left) != null) {
            return world.getTileEntity(left);
        }
        return null;
    }

    private TileEntity getRightTankside() {
        BlockPos right = getPos().offset(MekanismUtils.getBack(facing)).offset(MekanismUtils.getRight(facing));
        if (world.getTileEntity(right) != null) {
            return world.getTileEntity(right);
        }
        return null;
    }


    private void handleTank(BasicGasTank tank, TileEntity tile, EnumFacing side, int tankIdx) {
        if (tile != null) {
            ejectGas(Collections.singleton(side), tank, this.gasSpeedController, tankIdx, tile);
        }
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

    public SolarNeutronRecipe getRecipe() {
        refreshRecipeLookupCache();
        GasInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getSolarNeutronRecipe(getInput());
        }
        return cachedRecipe;
    }

    private void refreshRecipeLookupCache() {
        int recipeVersion = RecipeHandler.getGlobalRecipeVersion();
        if (cachedRecipeVersion != recipeVersion) {
            cachedRecipe = null;
            cachedRecipeVersion = recipeVersion;
        }
    }

    @Override
    public void onRecipeCacheInvalidated(int cacheIndex) {
        cachedRecipe = null;
        cachedRecipeVersion = RecipeHandler.getGlobalRecipeVersion();
    }

    public GasInput getInput() {
        return new GasInput(inputTank.getGas());
    }

    public boolean hasWarningNoMatchingRecipe() {
        return hasWarning(RecipeError.NOT_ENOUGH_INPUT) || inputTank.getGas() != null && getRecipe() == null;
    }

    public boolean hasWarningNoSpaceInOutput() {
        if (hasWarning(RecipeError.NOT_ENOUGH_OUTPUT_SPACE)) {
            return true;
        }
        GasStack output = getCurrentOutput();
        return output != null && outputTank.canReceiveType(output.getGas()) && outputTank.getNeeded() < output.amount;
    }

    public boolean hasWarningInputDoesntProduceOutput() {
        if (hasWarning(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT)) {
            return true;
        }
        GasStack output = getCurrentOutput();
        return output != null && !outputTank.canReceiveType(output.getGas());
    }

    private GasStack getCurrentOutput() {
        SolarNeutronRecipe recipe = getRecipe();
        if (recipe == null || recipe.getOutput().output == null) {
            return null;
        }
        return recipe.getOutput().output;
    }

    @Override
    public SolarNeutronRecipe getRecipe(int cacheIndex) {
        return getRecipe();
    }

    @Override
    public CachedRecipe<SolarNeutronRecipe> createNewCachedRecipe(SolarNeutronRecipe recipe, int cacheIndex) {
        return new OneInputCachedRecipe<>(recipe, () -> false,
              InputHelper.getGasInputHandler(inputTank, RecipeError.NOT_ENOUGH_INPUT),
              OutputHelper.getGasOutputHandler(outputTank, RecipeError.NOT_ENOUGH_OUTPUT_SPACE),
              () -> recipe.getInput().ingredient,
              input -> input != null && input.isGasEqual(recipe.getInput().ingredient),
              input -> recipe.getOutput().output.copy(),
              input -> input == null || input.amount <= 0,
              output -> output == null || output.amount <= 0)
              .setCanHolderFunction(() -> seesSunThisTick && MekanismUtils.canFunction(this))
              .setActive(this::setActive)
              .setRequiredTicks(() -> 1)
              .setBaselineMaxOperations(() -> getUpgradedUsage(recipe))
              .setErrorsChanged(errors -> {
                  for (int i = 0; i < trackedErrors.length; i++) {
                      trackedErrors[i] = errors.contains(TRACKED_ERROR_TYPES.get(i));
                  }
              });
    }

    @Override
    public void clearRecipeErrors(int cacheIndex) {
        Arrays.fill(trackedErrors, false);
    }

    public boolean hasWarning(RecipeError error) {
        int errorIndex = TRACKED_ERROR_TYPES.indexOf(error);
        return errorIndex != -1 && trackedErrors[errorIndex];
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.trackArray(trackedErrors);
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            boolean newActive = dataStream.readBoolean();
            boolean stateChange = newActive != isActive;
            isActive = newActive;
            if (stateChange && !isActive) {
                // Switched off; note the time
                lastActive = world.getTotalWorldTime();
            } else if (stateChange && isActive) {
                // Switching on; if lastActive is not currently set, trigger a lighting update
                // and make sure lastActive is clear
                if (lastActive == -1) {
                    MekanismUtils.updateBlock(world, getPos());
                }
                lastActive = -1;
            }
            controlType = MekanismUtils.getByIndex(RedstoneControl.values(), dataStream.readInt(), RedstoneControl.DISABLED);
            operatingTicks = dataStream.readInt();
            ticksRequired = dataStream.readInt();
            TileUtils.readTankData(dataStream, inputTank);
            TileUtils.readTankData(dataStream, outputTank);
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(isActive);
        data.add(controlType.ordinal());
        data.add(operatingTicks);
        data.add(ticksRequired);
        TileUtils.addTankData(data, inputTank);
        TileUtils.addTankData(data, outputTank);
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        isActive = nbtTags.getBoolean("isActive");
        controlType = MekanismUtils.getByIndex(RedstoneControl.values(), nbtTags.getInteger("controlType"), RedstoneControl.DISABLED);
        operatingTicks = nbtTags.getInteger("operatingTicks");
        if (!hasStoredGasTanks(nbtTags)) {
            if (nbtTags.hasKey("inputTank")) {
                inputTank.read(nbtTags.getCompoundTag("inputTank"));
            }
            if (nbtTags.hasKey("outputTank")) {
                outputTank.read(nbtTags.getCompoundTag("outputTank"));
            }
        }
        sanitizeAndClampTanks();
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setBoolean("isActive", isActive);
        nbtTags.setInteger("controlType", controlType.ordinal());
        nbtTags.setInteger("operatingTicks", operatingTicks);
    }

    @Override
    public void onPlace() {
        for (int y = 0; y <= 2; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    BlockPos pos1 = getPos().add(x, y, z);
                    if (y == 0) {
                        MekanismUtils.makeAdvancedBoundingBlock(world, pos1, Coord4D.get(this));
                    } else {
                        MekanismUtils.makeBoundingBlock(world, pos1, Coord4D.get(this));
                    }
                    world.notifyNeighborsOfStateChange(pos1, getBlockType(), true);
                }
            }
        }
    }

    @Override
    public void onBreak() {
        for (int y = 0; y <= 2; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    world.setBlockToAir(getPos().add(x, y, z));
                }
            }
        }
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
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        return capability == Capabilities.GAS_HANDLER_CAPABILITY;
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        writeSustainedGasTanks(itemStack);
        ItemDataUtils.setLegacyGas(itemStack, "inputTank", inputTank.getGas());
        ItemDataUtils.setLegacyGas(itemStack, "outputTank", outputTank.getGas());
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (!readSustainedGasTanks(itemStack)) {
            inputTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "inputTank"));
            outputTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "outputTank"));
        }
        sanitizeAndClampTanks();
    }

    private void sanitizeAndClampTanks() {
        sanitizeAndClampTank(inputTank);
        sanitizeAndClampTank(outputTank);
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
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return facing != EnumFacing.DOWN && facing != EnumFacing.UP;
    }

    @Override
    public boolean renderUpdate() {
        return false;
    }

    @Override
    public boolean lightUpdate() {
        return false;
    }

    @Override
    public Object[] getManagedTanks() {
        return new Object[]{inputTank, outputTank};
    }


    @Override
    public List<String> getInfo(Upgrade upgrade) {
        return upgrade == Upgrade.SPEED ? upgrade.getExpScaledInfo(this) : upgrade.getMultScaledInfo(this);
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return InventoryUtils.EMPTY;
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(inputTank.getStored(), inputTank.getMaxGas());
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
    public RedstoneControl getControlType() {
        return controlType;
    }

    @Override
    public void setControlType(RedstoneControl type) {
        controlType = Objects.requireNonNull(type);
        MekanismUtils.saveChunk(this);
    }

    @Override
    public boolean canPulse() {
        return false;
    }

    @Override
    public TileComponentUpgrade getComponent() {
        return upgradeComponent;
    }

    @Override
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    @Override
    public boolean getActive() {
        return isActive;
    }

    @Override
    public void setActive(boolean active) {
        boolean stateChange = isActive != active;
        if (stateChange) {
            isActive = active;
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
    }

    @Override
    public boolean wasActiveRecently() {
        // If the machine is currently active or it flipped off within our threshold,
        // we'll consider it recently active.
        return isActive || (lastActive > 0 && (world.getTotalWorldTime() - lastActive) < RECENT_THRESHOLD);
    }

    @Override
    public int getBlockGuiID(Block block, int metadata) {
        return 5;
    }

    @Override
    public IGuiProvider guiProvider() {
        return MekanismMultiblockMachine.proxy;
    }

    @Override
    public boolean canBoundReceiveEnergy(BlockPos location, EnumFacing side) {
        return false;
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
    @Optional.Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int extractEnergy(EnumFacing enumFacing, int i, boolean b) {
        return 0;
    }

    @Override
    @Optional.Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int receiveEnergy(EnumFacing enumFacing, int i, boolean b) {
        return 0;
    }

    @Override
    @Optional.Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int getEnergyStored(EnumFacing enumFacing) {
        return 0;
    }

    @Override
    @Optional.Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int getMaxEnergyStored(EnumFacing enumFacing) {
        return 0;
    }

    @Override
    @Optional.Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public boolean canConnectEnergy(EnumFacing enumFacing) {
        return false;
    }

    @Override
    @Optional.Method(modid = MekanismHooks.IC2_MOD_ID)
    public double getDemandedEnergy() {
        return 0;
    }

    @Override
    @Optional.Method(modid = MekanismHooks.IC2_MOD_ID)
    public int getSinkTier() {
        return 0;
    }

    @Override
    @Optional.Method(modid = MekanismHooks.IC2_MOD_ID)
    public double injectEnergy(EnumFacing enumFacing, double v, double v1) {
        return 0;
    }

    @Optional.Method(modid = MekanismHooks.IC2_MOD_ID)
    public boolean acceptsEnergyFrom(IEnergyEmitter iEnergyEmitter, EnumFacing enumFacing) {
        return false;
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
    public double acceptEnergy(EnumFacing side, double amount, boolean simulate) {
        return 0;
    }

    @Override
    public boolean canReceiveEnergy(EnumFacing side) {
        return false;
    }

    @Override
    public double pullEnergy(EnumFacing side, double amount, boolean simulate) {
        return 0;
    }

    @Override
    public boolean canOutputEnergy(EnumFacing side) {
        return false;
    }

    @Override
    public double getEnergy() {
        return 0;
    }

    @Override
    public void setEnergy(double energy) {

    }

    @Override
    public double getMaxEnergy() {
        return 0;
    }

    @Override
    public boolean hasOffsetCapability(@NotNull Capability<?> capability, @Nullable EnumFacing side, @NotNull Vec3i offset) {
        if (isOffsetCapabilityDisabled(capability, side, offset)) {
            return false;
        }
        if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            return getGasHandler(side) != null;
        }
        return hasCapability(capability, side);
    }

    @Override
    public @Nullable <T> T getOffsetCapability(@NotNull Capability<T> capability, @Nullable EnumFacing side, @NotNull Vec3i offset) {
        if (isOffsetCapabilityDisabled(capability, side, offset)) {
            return null;
        } else if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            return Capabilities.GAS_HANDLER_CAPABILITY.cast(getGasHandler(side));
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

    @Nonnull
    @Override
    public String getName() {
        return LangUtils.localize("tile.LargeSolarNeutronActivator.name");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Class<?> getSelectionWireframeModelClass() {
        return mekanism.multiblockmachine.client.model.machine.ModelLargeSolarNeutronActivator.class;
    }

    @Override
    protected boolean shouldDumpRadiation() {
        return true;
    }
}
