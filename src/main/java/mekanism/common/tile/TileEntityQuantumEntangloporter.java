package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.*;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.Upgrade;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.base.ISpecialSelectionWireframeTile;
import mekanism.common.base.ITankManager;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.block.states.BlockStateMachine;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.energy.QuantumEntangloporterEnergyContainerHolder;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.fluid.QuantumEntangloporterFluidTankHolder;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.capabilities.holder.gas.QuantumEntangloporterGasTankHolder;
import mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder;
import mekanism.common.capabilities.holder.heat.QuantumEntangloporterHeatCapacitorHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.QuantumEntangloporterInventorySlotHolder;
import mekanism.common.chunkloading.IChunkLoader;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.entangloporter.InventoryFrequency;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.frequency.IFrequencyHandler;
import mekanism.common.integration.computer.IComputerIntegration;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableDouble;
import mekanism.common.security.ISecurityTile;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.tile.component.*;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.IProxiedSlotInfo.*;
import mekanism.common.tile.component.config.slot.ISlotInfo;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import mekanism.common.tile.prefab.TileEntityElectricBlock;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.HeatUtils;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class TileEntityQuantumEntangloporter extends TileEntityElectricBlock implements ISideConfiguration, ITankManager, IFrequencyHandler,
        IHeatTransfer, IComputerIntegration, ISecurityTile, IChunkLoader, IUpgradeTile, ISpecialSelectionWireframeTile, IConfigCardAccess {

    private static final String[] methods = {"setFrequency", "createFrequency"};
    private static final ISpecialSelectionWireframeTile.SelectionTransform[] SELECTION_ROTATE_SOUTH = {
            ISpecialSelectionWireframeTile.SelectionTransform.rotateY(180.0D, 0.5D, 0.5D, 0.5D)
    };
    private static final ISpecialSelectionWireframeTile.SelectionTransform[] SELECTION_ROTATE_WEST = {
            ISpecialSelectionWireframeTile.SelectionTransform.rotateY(90.0D, 0.5D, 0.5D, 0.5D)
    };
    private static final ISpecialSelectionWireframeTile.SelectionTransform[] SELECTION_ROTATE_EAST = {
            ISpecialSelectionWireframeTile.SelectionTransform.rotateY(270.0D, 0.5D, 0.5D, 0.5D)
    };
    public double lastTransferLoss;
    public double lastEnvironmentLoss;
    public TileComponentEjector ejectorComponent;
    public TileComponentConfig configComponent;
    public TileComponentSecurity securityComponent;
    public TileComponentChunkLoader chunkLoaderComponent;
    public TileComponentUpgrade upgradeComponent;

    public TileEntityQuantumEntangloporter() {
        super("QuantumEntangloporter", 0);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.FLUID, TransmissionType.GAS, TransmissionType.ENERGY, TransmissionType.HEAT);
        setupConfig(TransmissionType.ITEM, InventoryProxy::new, () -> hasFrequency() ? getFreq().getInventorySlots(null) : Collections.emptyList());
        setupConfig(TransmissionType.FLUID, FluidProxy::new, () -> hasFrequency() ? getFreq().getFluidTanks(null) : Collections.emptyList());
        setupConfig(TransmissionType.GAS, GasProxy::new, () -> hasFrequency() ? getFreq().getGasTanks(null) : Collections.emptyList());
        setupConfig(TransmissionType.ENERGY, EnergyProxy::new, () -> hasFrequency() ? getFreq().getEnergyContainers(null) : Collections.emptyList());
        ConfigInfo heatConfig = configComponent.getConfigInfo(TransmissionType.HEAT);
        if (heatConfig != null) {
            Supplier<List<IHeatCapacitor>> heatSupplier = () -> hasFrequency() ? getFreq().getHeatCapacitors(null) : Collections.emptyList();
            heatConfig.addSlotInfo(DataType.INPUT_OUTPUT, new HeatProxy(true, false, heatSupplier));
            heatConfig.fill(DataType.INPUT_OUTPUT);
            heatConfig.setCanEject(false);
        }

        initializeInventorySlots();

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM)
              .setCanEject(type -> hasFrequency() && MekanismUtils.canFunction(this));

        securityComponent = new TileComponentSecurity(this);
        chunkLoaderComponent = new TileComponentChunkLoader(this);

        upgradeComponent = new TileComponentUpgrade(this);
        upgradeComponent.clearSupportedTypes();
        upgradeComponent.setSupported(Upgrade.ANCHOR);
        frequencyComponent.track(FrequencyType.INVENTORY, true, true, true);
    }

    private <T> void setupConfig(TransmissionType type, ProxySlotInfoCreator<T> proxyCreator, Supplier<List<T>> supplier) {
        ConfigInfo config = configComponent.getConfigInfo(type);
        if (config != null) {
            config.addSlotInfo(DataType.INPUT, proxyCreator.create(true, false, supplier));
            config.addSlotInfo(DataType.OUTPUT, proxyCreator.create(false, true, supplier));
            config.addSlotInfo(DataType.INPUT_OUTPUT, proxyCreator.create(true, true, supplier));
            config.fill(DataType.INPUT);
            config.setDataType(DataType.OUTPUT, mekanism.api.RelativeSide.FRONT);
        }
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        InventoryFrequency frequency = getFreq();
        if (frequency != null && frequency.isValid() && !frequency.isRemoved()) {
            frequency.handleEject(world.getTotalWorldTime());
            double[] loss = simulateHeat();
            applyTemperatureChange();
            lastTransferLoss = loss[0];
            lastEnvironmentLoss = loss[1];
        } else {
            lastTransferLoss = 0;
            lastEnvironmentLoss = 0;
        }

    }

    @Override
    public boolean supportsAsync() {
        return false;
    }

    public boolean hasFrequency() {
        InventoryFrequency frequency = getFreq();
        return frequency != null && frequency.isValid() && !frequency.isRemoved();
    }

    public List<IInventorySlot> getFrequencyInventorySlots(@Nullable EnumFacing side) {
        InventoryFrequency frequency = getFreq();
        if (!hasFrequency()) {
            return Collections.emptyList();
        }
        if (side == null) {
            return frequency.getInventorySlots(null);
        }
        ISlotInfo slotInfo = configComponent.getSlotInfo(TransmissionType.ITEM, side, facing);
        return slotInfo instanceof InventorySlotInfo inventorySlotInfo ? inventorySlotInfo.getSlots() : Collections.emptyList();
    }

    public InventoryFrequency getFreq() {
        return getFrequency(FrequencyType.INVENTORY);
    }

    public void setFrequency(FrequencyIdentity identity) {
        UUID owner = securityComponent.getOwnerUUID();
        if (identity != null && owner != null) {
            setFrequency(FrequencyType.INVENTORY, identity, owner);
        }
    }

    public void createFrequency(String name) {
        UUID owner = securityComponent.getOwnerUUID();
        if (name != null && !name.isEmpty() && owner != null) {
            setFrequency(FrequencyType.INVENTORY, new FrequencyIdentity(name, SecurityMode.PUBLIC, owner), owner);
        }
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            return;
        }

        super.handlePacketData(dataStream);

        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            lastTransferLoss = dataStream.readDouble();
            lastEnvironmentLoss = dataStream.readDouble();
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(lastTransferLoss);
        data.add(lastEnvironmentLoss);
        return data;
    }

    @Override
    public boolean sideIsOutput(EnumFacing side) {
        return hasFrequency() && canExtractEnergy(side);
    }

    @Override
    public boolean sideIsConsumer(EnumFacing side) {
        return hasFrequency() && canInsertEnergy(side);
    }

    @Override
    public double getMaxOutput() {
        return !hasFrequency() ? 0 : MekanismConfig.current().general.quantumEntangloporterEnergyTransfer.val();
    }

    @Override
    public double getEnergy() {
        InventoryFrequency frequency = getFreq();
        return frequency == null || !frequency.isValid() || frequency.isRemoved() ? 0 : frequency.storedEnergy.getEnergy();
    }

    @Override
    public void setEnergy(double energy) {
        InventoryFrequency frequency = getFreq();
        if (frequency != null && frequency.isValid() && !frequency.isRemoved()) {
            frequency.storedEnergy.setEnergy(energy);
        }
    }

    @Override
    public double getMaxEnergy() {
        InventoryFrequency frequency = getFreq();
        return frequency == null || !frequency.isValid() || frequency.isRemoved() ? 0 : frequency.storedEnergy.getMaxEnergy();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        return new QuantumEntangloporterInventorySlotHolder(this);
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        return new QuantumEntangloporterFluidTankHolder(this);
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        return new QuantumEntangloporterGasTankHolder(this);
    }

    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        return new QuantumEntangloporterEnergyContainerHolder(this);
    }

    @Override
    protected IHeatCapacitorHolder getInitialHeatCapacitors(IContentsListener listener) {
        return new QuantumEntangloporterHeatCapacitorHolder(this);
    }

    @Override
    public boolean persistInventory() {
        return false;
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
    public boolean hasInventory() {
        return true;
    }

    @Nonnull
    @Override
    public List<IInventorySlot> getInventorySlots(@Nullable EnumFacing side) {
        return getFrequencyInventorySlots(side);
    }

    @Nonnull
    @Override
    protected int[] getInventorySlotIdsForSide(@Nullable EnumFacing side) {
        if (!hasFrequency()) {
            return InventoryUtils.EMPTY;
        }
        List<IInventorySlot> slots = getFrequencyInventorySlots(side);
        InventoryFrequency frequency = getFreq();
        List<IInventorySlot> internalSlots = frequency.getInventorySlots(null);
        int[] slotIds = new int[slots.size()];
        for (int i = 0; i < slots.size(); i++) {
            slotIds[i] = internalSlots.indexOf(slots.get(i));
        }
        return slotIds;
    }

    @Nullable
    @Override
    public IInventorySlot getInventorySlot(int slot) {
        List<IInventorySlot> slots = getFrequencyInventorySlots(null);
        return slot >= 0 && slot < slots.size() ? slots.get(slot) : null;
    }

    @Override
    public double getTemp() {
        InventoryFrequency frequency = getFreq();
        return frequency == null || !frequency.isValid() || frequency.isRemoved() ? 0 : frequency.getTemperature();
    }

    @Override
    public double getInverseConductionCoefficient() {
        return 1;
    }

    @Override
    public double getInsulationCoefficient(EnumFacing side) {
        return 1000;
    }

    @Override
    public void transferHeatTo(double heat) {
        InventoryFrequency frequency = getFreq();
        if (frequency != null && frequency.isValid() && !frequency.isRemoved()) {
            frequency.storedHeat.handleHeat(heat * frequency.storedHeat.getHeatCapacity());
        }
    }

    @Override
    public double[] simulateHeat() {
        return HeatUtils.simulate(this);
    }

    @Override
    public double applyTemperatureChange() {
        InventoryFrequency frequency = getFreq();
        if (frequency != null && frequency.isValid() && !frequency.isRemoved()) {
            frequency.storedHeat.update();
            frequency.temperature = frequency.getTemperature();
        }
        return frequency == null || !frequency.isValid() || frequency.isRemoved() ? 0 : frequency.getTemperature();
    }

    @Override
    public boolean canConnectHeat(EnumFacing side) {
        return hasFrequency() && canInsertHeat(side);
    }

    @Override
    public IHeatTransfer getAdjacent(EnumFacing side) {
        TileEntity adj = Coord4D.get(this).offset(side).getTileEntity(world);
        if (hasFrequency() && canInsertHeat(side)) {
            if (CapabilityUtils.hasCapability(adj, Capabilities.HEAT_TRANSFER_CAPABILITY, side.getOpposite())) {
                return CapabilityUtils.getCapability(adj, Capabilities.HEAT_TRANSFER_CAPABILITY, side.getOpposite());
            }
        }
        return null;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (capability == Capabilities.CONFIG_CARD_CAPABILITY) {
            return true;
        }
        if (capability == Capabilities.HEAT_TRANSFER_CAPABILITY) {
            return hasFrequency() && (side == null || canInsertHeat(side));
        }
        return super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (capability == Capabilities.CONFIG_CARD_CAPABILITY) {
            return Capabilities.CONFIG_CARD_CAPABILITY.cast(this);
        }
        if (capability == Capabilities.HEAT_TRANSFER_CAPABILITY) {
            return hasFrequency() && (side == null || canInsertHeat(side)) ? Capabilities.HEAT_TRANSFER_CAPABILITY.cast(this) : null;
        }
        return super.getCapability(capability, side);
    }

    @Override
    public Object[] getManagedTanks() {
        if (!hasFrequency()) {
            return null;
        }
        InventoryFrequency frequency = getFreq();
        return new Object[]{frequency.storedFluid, frequency.storedGas};
    }

    @Override
    public boolean hasFluidTanks() {
        return super.hasFluidTanks();
    }

    @Nonnull
    @Override
    public List<IExtendedFluidTank> getFluidTanks(@Nullable EnumFacing side) {
        return super.getFluidTanks(side);
    }

    @Override
    public boolean canInsertFluid(@Nullable EnumFacing side) {
        return super.canInsertFluid(side);
    }

    @Override
    public boolean canExtractFluid(@Nullable EnumFacing side) {
        return super.canExtractFluid(side);
    }

    @Override
    public boolean hasGasTanks() {
        return super.hasGasTanks();
    }

    @Nonnull
    @Override
    public List<IExtendedGasTank> getGasTanks(@Nullable EnumFacing side) {
        return super.getGasTanks(side);
    }

    @Override
    public boolean canInsertGas(@Nullable EnumFacing side) {
        return super.canInsertGas(side);
    }

    @Override
    public boolean canExtractGas(@Nullable EnumFacing side) {
        return super.canExtractGas(side);
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
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableDouble.create(() -> lastTransferLoss, value -> lastTransferLoss = value));
        container.track(SyncableDouble.create(() -> lastEnvironmentLoss, value -> lastEnvironmentLoss = value));
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        if (method == 0) {
            if (!(arguments[0] instanceof String)) {
                return new Object[]{"Invalid parameters."};
            }
            String freq = ((String) arguments[0]).trim();
            Frequency frequency = FrequencyType.INVENTORY.getManager(null, SecurityMode.PUBLIC).getFrequency(freq);
            if (frequency == null) {
                return new Object[]{"No public inventory frequency with that name exists."};
            }
            setFrequency(frequency.getIdentity());
            return new Object[]{"Frequency set."};
        }
        if (method == 1) {
            if (!(arguments[0] instanceof String)) {
                return new Object[]{"Invalid parameters."};
            }
            String freq = ((String) arguments[0]).trim();
            if (FrequencyType.INVENTORY.getManager(null, SecurityMode.PUBLIC).getFrequency(freq) != null) {
                return new Object[]{"Public inventory frequency already exists."};
            }
            createFrequency(freq);
            return new Object[]{"Frequency created."};
        }
        throw new NoSuchMethodException();
    }

    @Override
    public TileComponentChunkLoader getChunkLoader() {
        return chunkLoaderComponent;
    }

    @Override
    public Set<ChunkPos> getChunkSet() {
        Set<ChunkPos> ret = new ObjectOpenHashSet<>();
        ret.add(new Chunk3D(Coord4D.get(this)).getPos());
        return ret;
    }

    @Override
    public TileComponentUpgrade getComponent() {
        return this.upgradeComponent;
    }

    @Override
    protected boolean shouldDumpRadiation() {
        //Note: The QE doesn't support radioactive substances but override this method anyway
        return false;
    }

    @Override
    public int getBlockGuiID(Block block, int metadata) {
        return BlockStateMachine.MachineType.get(block, metadata) != null ? BlockStateMachine.MachineType.get(block, metadata).guiId : -1;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Class<?> getSelectionWireframeModelClass() {
        return mekanism.client.model.ModelQuantumEntangloporter.class;
    }

    @Override
    public String[] getSelectionWireframeIgnoredRendererFieldNames() {
        return new String[]{"portRightLarge", "portLeftLarge"};
    }

    @Override
    public boolean shouldApplyDefaultSelectionWireframeFacingRotation(IBlockState state, IBlockAccess world, BlockPos pos) {
        return false;
    }

    @Override
    public ISpecialSelectionWireframeTile.SelectionTransform[] getSelectionWireframeTransforms(IBlockState state, IBlockAccess world, BlockPos pos) {
        EnumFacing currentFacing = facing == null ? EnumFacing.NORTH : facing;
        return switch (currentFacing) {
            case SOUTH -> SELECTION_ROTATE_SOUTH;
            case WEST -> SELECTION_ROTATE_WEST;
            case EAST -> SELECTION_ROTATE_EAST;
            default -> SelectionTransform.EMPTY;
        };
    }
}
