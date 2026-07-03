package mekanism.common.content.entangloporter;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.Coord4D;
import mekanism.api.DataHandlerUtils;
import mekanism.api.NBTConstants;
import mekanism.api.RelativeSide;
import mekanism.api.TileNetworkList;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasHandler;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.inventory.IMekanismInventory;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.base.EnergyAcceptorWrapper;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.network.distribution.EnergyAcceptorTarget;
import mekanism.common.content.network.distribution.FluidHandlerTarget;
import mekanism.common.content.network.distribution.GasHandlerTarget;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.inventory.slot.EntangloporterInventorySlot;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.tile.TileEntityQuantumEntangloporter;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.util.CableUtils;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.EmitUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.PipeUtils;
import mekanism.common.util.TileUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class InventoryFrequency extends Frequency implements IMekanismInventory {

    public static final String ENTANGLOPORTER = "Entangloporter";

    public BasicEnergyContainer storedEnergy;
    public BasicFluidTank storedFluid;
    public BasicGasTank storedGas;
    public IInventorySlot storedItem;
    private List<IInventorySlot> inventorySlots;
    private List<IExtendedFluidTank> fluidTanks;
    private List<IExtendedGasTank> gasTanks;
    private List<IEnergyContainer> energyContainers;
    public BasicHeatCapacitor storedHeat;
    private List<IHeatCapacitor> heatCapacitors;
    private final Map<Coord4D, TileEntityQuantumEntangloporter> activeQEs = new Object2ObjectOpenHashMap<>();
    private long lastEject = -1;
    public double temperature;

    public InventoryFrequency(String n, UUID uuid) {
        this(n, uuid, SecurityMode.PUBLIC);
    }

    public InventoryFrequency(String n, UUID uuid, SecurityMode securityMode) {
        super(FrequencyType.INVENTORY, n, uuid, securityMode);
        initContainers();
    }

    public InventoryFrequency(NBTTagCompound nbtTags) {
        super(FrequencyType.INVENTORY, nbtTags);
    }

    public InventoryFrequency(ByteBuf dataStream) {
        super(FrequencyType.INVENTORY, dataStream);
    }

    private void initContainers() {
        storedEnergy = BasicEnergyContainer.create(MekanismConfig.current().general.quantumEntangloporterEnergyTransfer.val(), this);
        energyContainers = Collections.singletonList(storedEnergy);
        storedHeat = BasicHeatCapacitor.create(HeatAPI.DEFAULT_HEAT_CAPACITY, HeatAPI.DEFAULT_INVERSE_CONDUCTION, 1_000, () -> HeatAPI.AMBIENT_TEMP, this);
        heatCapacitors = Collections.singletonList(storedHeat);
        initTanks();
        storedItem = EntangloporterInventorySlot.create(this);
        inventorySlots = Collections.singletonList(storedItem);
        temperature = getTemperature();
    }

    private void initTanks() {
        storedFluid = BasicFluidTank.create(MekanismConfig.current().general.quantumEntangloporterFluidBuffer.val(), this);
        storedGas = BasicGasTank.create(MekanismConfig.current().general.quantumEntangloporterGasBuffer.val(), this);
        fluidTanks = Collections.singletonList(storedFluid);
        gasTanks = Collections.singletonList(storedGas);
    }

    @Override
    public List<IInventorySlot> getInventorySlots(@Nullable EnumFacing side) {
        if (inventorySlots == null) {
            initContainers();
        }
        return inventorySlots;
    }

    public List<IExtendedFluidTank> getFluidTanks(@Nullable EnumFacing side) {
        if (fluidTanks == null) {
            initContainers();
        }
        return fluidTanks;
    }

    public List<IExtendedGasTank> getGasTanks(@Nullable EnumFacing side) {
        if (gasTanks == null) {
            initContainers();
        }
        return gasTanks;
    }

    public List<IEnergyContainer> getEnergyContainers(@Nullable EnumFacing side) {
        if (energyContainers == null) {
            initContainers();
        }
        return energyContainers;
    }

    public List<IHeatCapacitor> getHeatCapacitors(@Nullable EnumFacing side) {
        if (heatCapacitors == null) {
            initContainers();
        }
        return heatCapacitors;
    }

    @Override
    public void onContentsChanged() {
        dirty = true;
    }

    @Override
    public boolean update(Object source) {
        boolean changedData = super.update(source);
        if (source instanceof TileEntityQuantumEntangloporter entangloporter) {
            activeQEs.put(Coord4D.get(entangloporter), entangloporter);
        } else if (source instanceof TileEntity tile) {
            activeQEs.remove(Coord4D.get(tile));
        } else if (source instanceof Coord4D coord) {
            activeQEs.remove(coord);
        }
        return changedData;
    }

    @Override
    public boolean onDeactivate(Object source) {
        boolean changedData = super.onDeactivate(source);
        if (source instanceof TileEntity tile) {
            activeQEs.remove(Coord4D.get(tile));
        } else if (source instanceof Coord4D coord) {
            activeQEs.remove(coord);
        }
        return changedData;
    }

    public void handleEject(long gameTime) {
        if (isValid() && !activeQEs.isEmpty() && lastEject != gameTime) {
            lastEject = gameTime;
            Map<TransmissionType, Consumer<TileEntityQuantumEntangloporter>> typesToEject = new EnumMap<>(TransmissionType.class);
            List<Runnable> transferHandlers = new ArrayList<>(3);
            int expected = 6 * activeQEs.size();
            addEnergyTransferHandler(typesToEject, transferHandlers, expected);
            addFluidTransferHandler(typesToEject, transferHandlers, expected);
            addGasTransferHandler(typesToEject, transferHandlers, expected);
            if (!typesToEject.isEmpty()) {
                for (Map.Entry<Coord4D, TileEntityQuantumEntangloporter> activeEntry : new ArrayList<>(activeQEs.entrySet())) {
                    TileEntityQuantumEntangloporter qe = activeEntry.getValue();
                    if (qe.isInvalid() || qe.getWorld() == null) {
                        activeQEs.remove(activeEntry.getKey());
                        continue;
                    }
                    if (!MekanismUtils.canFunction(qe)) {
                        continue;
                    }
                    for (Map.Entry<TransmissionType, Consumer<TileEntityQuantumEntangloporter>> entry : typesToEject.entrySet()) {
                        TransmissionType transmissionType = entry.getKey();
                        ConfigInfo config = qe.getConfig().getConfigInfo(transmissionType);
                        if (qe.getEjector().isEjecting(config, transmissionType)) {
                            entry.getValue().accept(qe);
                        }
                    }
                }
                for (Runnable transferHandler : transferHandlers) {
                    transferHandler.run();
                }
            }
        }
    }

    private void addEnergyTransferHandler(Map<TransmissionType, Consumer<TileEntityQuantumEntangloporter>> typesToEject, List<Runnable> transferHandlers,
          int expected) {
        double toSend = storedEnergy.extract(storedEnergy.getMaxEnergy(), Action.SIMULATE, AutomationType.INTERNAL);
        if (toSend > 0) {
            SendingEnergyAcceptorTarget target = new SendingEnergyAcceptorTarget(expected, storedEnergy, toSend);
            typesToEject.put(TransmissionType.ENERGY, target);
            transferHandlers.add(target);
        }
    }

    private void addFluidTransferHandler(Map<TransmissionType, Consumer<TileEntityQuantumEntangloporter>> typesToEject, List<Runnable> transferHandlers,
          int expected) {
        FluidStack fluidToSend = storedFluid.extract(storedFluid.getCapacity(), Action.SIMULATE, AutomationType.INTERNAL);
        if (fluidToSend != null && fluidToSend.amount > 0) {
            SendingFluidHandlerTarget target = new SendingFluidHandlerTarget(fluidToSend, expected, storedFluid);
            typesToEject.put(TransmissionType.FLUID, target);
            transferHandlers.add(target);
        }
    }

    private void addGasTransferHandler(Map<TransmissionType, Consumer<TileEntityQuantumEntangloporter>> typesToEject, List<Runnable> transferHandlers,
          int expected) {
        GasStack gasToSend = storedGas.extract(storedGas.getCapacity(), Action.SIMULATE, AutomationType.INTERNAL);
        if (gasToSend != null && gasToSend.amount > 0) {
            SendingGasHandlerTarget target = new SendingGasHandlerTarget(gasToSend, expected, storedGas);
            typesToEject.put(TransmissionType.GAS, target);
            transferHandlers.add(target);
        }
    }

    private static void collectOutputSides(TileEntityQuantumEntangloporter qe, TransmissionType transmissionType, Consumer<EnumFacing> sideConsumer) {
        ConfigInfo config = qe.getConfig().getConfigInfo(transmissionType);
        if (config == null) {
            return;
        }
        for (Map.Entry<RelativeSide, DataType> sideEntry : config.getSideConfig()) {
            if (sideEntry.getValue().canOutput()) {
                sideConsumer.accept(sideEntry.getKey().getDirection(qe.getOrientation()));
            }
        }
    }

    private static class SendingEnergyAcceptorTarget extends EnergyAcceptorTarget implements Runnable, Consumer<TileEntityQuantumEntangloporter> {

        private final IEnergyContainer storedEnergy;
        private final double toSend;

        private SendingEnergyAcceptorTarget(int expectedSize, IEnergyContainer storedEnergy, double toSend) {
            super(expectedSize);
            this.storedEnergy = storedEnergy;
            this.toSend = toSend;
        }

        @Override
        public void run() {
            if (getHandlerCount() > 0) {
                storedEnergy.extract(EmitUtils.sendToAcceptors(this, toSend), Action.EXECUTE, AutomationType.INTERNAL);
            }
        }

        @Override
        public void accept(TileEntityQuantumEntangloporter qe) {
            collectOutputSides(qe, TransmissionType.ENERGY, side -> {
                TileEntity tile = Coord4D.get(qe).offset(side).getTileEntity(qe.getWorld());
                if (tile != null && (CableUtils.isValidAcceptorOnSide(qe, tile, side) || CableUtils.isCable(tile))) {
                    EnumFacing opposite = side.getOpposite();
                    EnergyAcceptorWrapper acceptor = EnergyAcceptorWrapper.get(tile, opposite);
                    if (acceptor != null && acceptor.canReceiveEnergy(opposite) && acceptor.needsEnergy(opposite)) {
                        addHandler(opposite, acceptor);
                    }
                }
            });
        }
    }

    private static class SendingFluidHandlerTarget extends FluidHandlerTarget implements Runnable, Consumer<TileEntityQuantumEntangloporter> {

        private final FluidStack toSend;
        private final IExtendedFluidTank storedFluid;

        private SendingFluidHandlerTarget(FluidStack toSend, int expectedSize, IExtendedFluidTank storedFluid) {
            super(toSend, expectedSize);
            this.toSend = toSend;
            this.storedFluid = storedFluid;
        }

        @Override
        public void run() {
            if (getHandlerCount() > 0) {
                storedFluid.extract(EmitUtils.sendToAcceptors(this, toSend.amount, toSend), Action.EXECUTE, AutomationType.INTERNAL);
            }
        }

        @Override
        public void accept(TileEntityQuantumEntangloporter qe) {
            collectOutputSides(qe, TransmissionType.FLUID, side -> {
                TileEntity tile = Coord4D.get(qe).offset(side).getTileEntity(qe.getWorld());
                IFluidHandler handler = CapabilityUtils.getCapability(tile, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side.getOpposite());
                if (handler != null && PipeUtils.canFill(handler, toSend)) {
                    addHandler(handler);
                }
            });
        }
    }

    private static class SendingGasHandlerTarget extends GasHandlerTarget implements Runnable, Consumer<TileEntityQuantumEntangloporter> {

        private final GasStack toSend;
        private final IExtendedGasTank storedGas;

        private SendingGasHandlerTarget(GasStack toSend, int expectedSize, IExtendedGasTank storedGas) {
            super(toSend, expectedSize);
            this.toSend = toSend;
            this.storedGas = storedGas;
        }

        @Override
        public void run() {
            if (getHandlerCount() > 0) {
                storedGas.extract(EmitUtils.sendToAcceptors(this, toSend.amount, toSend), Action.EXECUTE, AutomationType.INTERNAL);
            }
        }

        @Override
        public void accept(TileEntityQuantumEntangloporter qe) {
            collectOutputSides(qe, TransmissionType.GAS, side -> {
                EnumFacing opposite = side.getOpposite();
                TileEntity tile = Coord4D.get(qe).offset(side).getTileEntity(qe.getWorld());
                IGasHandler handler = CapabilityUtils.getCapability(tile, Capabilities.GAS_HANDLER_CAPABILITY, opposite);
                if (handler != null && GasInventorySlot.canReceiveGas(handler, opposite, toSend)) {
                    addHandler(opposite, handler);
                }
            });
        }
    }

    @Override
    public void write(NBTTagCompound nbtTags) {
        super.write(nbtTags);
        nbtTags.setTag(NBTConstants.ENERGY_STORED, storedEnergy.serializeNBT());
        nbtTags.setDouble("storedEnergy", storedEnergy.getEnergy());
        if (storedFluid.getFluid() != null) {
            nbtTags.setTag("storedFluid", storedFluid.writeToNBT(new NBTTagCompound()));
        }
        if (storedGas.getGas() != null) {
            nbtTags.setTag("storedGas", storedGas.write(new NBTTagCompound()));
        }
        nbtTags.setTag("storedItem", storedItem.serializeNBT());
        nbtTags.setTag(NBTConstants.HEAT_STORED, storedHeat.serializeNBT());
        nbtTags.setDouble("temperature", getTemperature());
    }

    @Override
    protected void read(NBTTagCompound nbtTags) {
        super.read(nbtTags);
        initContainers();
        if (nbtTags.hasKey(NBTConstants.ENERGY_STORED, NBT.TAG_COMPOUND)) {
            storedEnergy.deserializeNBT(nbtTags.getCompoundTag(NBTConstants.ENERGY_STORED));
        } else {
            storedEnergy.setEnergy(nbtTags.getDouble("storedEnergy"));
        }

        if (nbtTags.hasKey("storedFluid")) {
            storedFluid.readFromNBT(nbtTags.getCompoundTag("storedFluid"));
        }
        if (nbtTags.hasKey("storedGas")) {
            storedGas.read(nbtTags.getCompoundTag("storedGas"));
        }

        if (nbtTags.hasKey("storedItem")) {
            storedItem.deserializeNBT(nbtTags.getCompoundTag("storedItem"));
        } else {
            DataHandlerUtils.readContainers(inventorySlots, nbtTags.getTagList(NBTConstants.ITEMS, NBT.TAG_COMPOUND));
        }
        if (nbtTags.hasKey(NBTConstants.HEAT_STORED, NBT.TAG_COMPOUND)) {
            storedHeat.deserializeNBT(nbtTags.getCompoundTag(NBTConstants.HEAT_STORED));
        } else {
            storedHeat.setHeat((nbtTags.getDouble("temperature") + HeatAPI.AMBIENT_TEMP) * storedHeat.getHeatCapacity());
        }
        temperature = getTemperature();
    }

    @Override
    public void write(TileNetworkList data) {
        super.write(data);
        data.add(storedEnergy.getEnergy());
        TileUtils.addTankData(data, storedFluid);
        TileUtils.addTankData(data, storedGas);
        data.add(getTemperature());
    }

    @Override
    protected void read(ByteBuf dataStream) {
        super.read(dataStream);
        initContainers();
        storedEnergy.setEnergy(dataStream.readDouble());
        TileUtils.readTankData(dataStream, storedFluid);
        TileUtils.readTankData(dataStream, storedGas);
        storedHeat.setHeat((dataStream.readDouble() + HeatAPI.AMBIENT_TEMP) * storedHeat.getHeatCapacity());
        temperature = getTemperature();
    }

    public double getTemperature() {
        if (storedHeat == null) {
            return temperature;
        }
        return storedHeat.getTemperature() - HeatAPI.AMBIENT_TEMP;
    }
}
