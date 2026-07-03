package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.gas.GasStack;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.Mekanism;
import mekanism.common.base.*;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.capabilities.holder.fluid.FluidTankHelper;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.gas.GasTankHelper;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.integration.computer.IComputerIntegration;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.FluidInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityElectricBlock;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TileUtils;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TileEntityHybridStorage extends TileEntityElectricBlock implements ISideConfiguration, IComputerIntegration,
        ISecurityTile, IConfigCardAccess, ISustainedData, ITankManager, IRedstoneControl, IFluidContainerManager {

    public TileComponentEjector ejectorComponent;
    public TileComponentConfig configComponent;
    public TileComponentSecurity securityComponent;
    private static final int GAS_TANK_CAPACITY = 8_192_000;
    private static final int FLUID_TANK_CAPACITY = 512_000;
    public BasicGasTank gasTank1;
    public BasicGasTank gasTank2;
    public BasicFluidTank fluidTank;
    public RedstoneControl controlType;
    public ContainerEditMode editMode = ContainerEditMode.BOTH;
    private GasInventorySlot gasFillSlot1;
    private GasInventorySlot gasDrainSlot1;
    private GasInventorySlot gasFillSlot2;
    private GasInventorySlot gasDrainSlot2;
    private FluidInventorySlot fluidSlot;
    private OutputInventorySlot fluidOutputSlot;
    private EnergyInventorySlot chargeSlot;
    private EnergyInventorySlot dischargeSlot;
    private final List<IInventorySlot> storageSlots = new ArrayList<>();

    public int delayTicks;
    protected int successCounter = 0;
    protected boolean inventoryChanged = false;


    //TODO：Maybe remove this,Because it's not good to have both input and output in the same place at the same time
    public TileEntityHybridStorage() {
        super(MachineType.HYBRID_STORAGE.getBlockName(), MachineType.HYBRID_STORAGE.getStorage());
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.ENERGY, TransmissionType.FLUID, TransmissionType.GAS);
        initializeInventorySlots();
        configComponent.setupItemStorageConfig(storageSlots);
        configComponent.setConfig(TransmissionType.ITEM, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.OUTPUT);

        configComponent.setIOConfig(TransmissionType.ENERGY);
        configComponent.setupFluidIOConfig(fluidTank, RelativeSide.FRONT);
        configComponent.setConfig(TransmissionType.FLUID, DataType.INPUT, DataType.INPUT, DataType.OUTPUT, DataType.INPUT, DataType.INPUT, DataType.INPUT);
        configComponent.addGasSlotInfo(DataType.INPUT, Arrays.asList(gasTank1, gasTank2));
        configComponent.addGasSlotInfo(DataType.INPUT_1, gasTank1);
        configComponent.addGasSlotInfo(DataType.INPUT_2, gasTank2);
        configComponent.addGasSlotInfo(DataType.OUTPUT, Arrays.asList(gasTank1, gasTank2));
        configComponent.addGasSlotInfo(DataType.INPUT_OUTPUT, Arrays.asList(gasTank1, gasTank2));
        configComponent.addGasSlotInfo(DataType.OUTPUT_1, gasTank1);
        configComponent.addGasSlotInfo(DataType.OUTPUT_2, gasTank2);
        configComponent.setConfig(TransmissionType.GAS, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.INPUT, DataType.OUTPUT);

        controlType = RedstoneControl.DISABLED;
        securityComponent = new TileComponentSecurity(this);
        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM, TransmissionType.GAS, TransmissionType.FLUID, TransmissionType.ENERGY)
              .setCanEject(type -> MekanismUtils.canFunction(this));
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        storageSlots.clear();
        InventorySlotHelper builder = createInventorySlotHelper();
        for (int slot = 0; slot < 120; slot++) {
            int slotX = slot % 15;
            int slotY = slot / 15;
            storageSlots.add(builder.addSlot(BasicInventorySlot.at(listener, 8 + slotX * 18, 26 + slotY * 18)));
        }
        gasFillSlot1 = builder.addSlot(GasInventorySlot.fill(gasTank1, listener, 8, 179));
        gasDrainSlot1 = builder.addSlot(GasInventorySlot.drain(gasTank1, listener, 8, 259));
        gasFillSlot2 = builder.addSlot(GasInventorySlot.fill(gasTank2, listener, 35, 179));
        gasDrainSlot2 = builder.addSlot(GasInventorySlot.drain(gasTank2, listener, 35, 259));
        fluidSlot = builder.addSlot(FluidInventorySlot.input(fluidTank, listener, 233, 179));
        fluidOutputSlot = builder.addSlot(OutputInventorySlot.at(listener, 233, 259));
        chargeSlot = builder.addSlot(EnergyInventorySlot.drain(getMainEnergyContainer(), listener, 260, 179));
        dischargeSlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 260, 259));
        return builder.build();
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        FluidTankHelper builder = createFluidTankHelper();
        builder.addTank(getOrCreateFluidTank(listener));
        return builder.build();
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        GasTankHelper builder = createGasTankHelper();
        builder.addTank(getOrCreateGasTank1(listener));
        builder.addTank(getOrCreateGasTank2(listener));
        return builder.build();
    }

    private BasicGasTank getOrCreateGasTank1(IContentsListener listener) {
        if (gasTank1 == null) {
            gasTank1 = BasicGasTank.create(GAS_TANK_CAPACITY, listener);
        }
        return gasTank1;
    }

    private BasicGasTank getOrCreateGasTank2(IContentsListener listener) {
        if (gasTank2 == null) {
            gasTank2 = BasicGasTank.create(GAS_TANK_CAPACITY, listener);
        }
        return gasTank2;
    }

    private BasicFluidTank getOrCreateFluidTank(IContentsListener listener) {
        if (fluidTank == null) {
            fluidTank = BasicFluidTank.create(FLUID_TANK_CAPACITY, listener);
        }
        return fluidTank;
    }


    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        gasFillSlot1.fillTank();
        gasDrainSlot1.drainTank();
        gasFillSlot2.fillTank();
        gasDrainSlot2.drainTank();
        manageInventory();
        chargeSlot.drainContainer();
        dischargeSlot.fillContainerOrConvert();
        if (fluidTank.getFluid() != null && fluidTank.getFluidAmount() == 0) {
            fluidTank.setEmpty();
        }
    }




    @Override
    public void addTileSyncTask() {
    }

    protected boolean canWork(int minWorkDelay, int maxWorkDelay) {
        if (inventoryChanged) {
            inventoryChanged = false;
            return true;
        }

        if (successCounter <= 0) {
            return ticksExisted % maxWorkDelay == 0;
        }
        int workDelay = Math.max(minWorkDelay, maxWorkDelay - (successCounter * 5));
        return ticksExisted % workDelay == 0;
    }

    protected void incrementSuccessCounter(int maxWorkDelay, int minWorkDelay) {
        int max = (maxWorkDelay - minWorkDelay) / 5;
        if (successCounter < max) {
            successCounter++;
        }
    }

    protected void decrementSuccessCounter() {
        if (successCounter > 0) {
            successCounter--;
        }
    }

    public static ItemStack copyStackWithSize(ItemStack stack, int amount) {
        if (stack.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        ItemStack s = stack.copy();
        s.setCount(amount);
        return s;
    }

    public static boolean matchStacks(@Nonnull ItemStack stack, @Nonnull ItemStack other) {
        if (!ItemStack.areItemsEqual(stack, other)) return false;
        return ItemStack.areItemStackTagsEqual(stack, other);
    }

    private void manageInventory() {
        fluidSlot.handleTank(fluidOutputSlot, editMode);
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
    public boolean sideIsConsumer(EnumFacing side) {
        return configComponent.hasSideForData(TransmissionType.ENERGY, facing, DataType.INPUT, side);
    }

    @Override
    public boolean sideIsOutput(EnumFacing side) {
        return configComponent.hasSideForData(TransmissionType.ENERGY, facing, DataType.OUTPUT, side);
    }

    @Override
    public double getMaxOutput() {
        return MachineType.HYBRID_STORAGE.getStorage();
    }

    @Override
    public double getMaxEnergy() {
        return MachineType.HYBRID_STORAGE.getStorage();
    }

    @Override
    public void setEnergy(double energy) {
        super.setEnergy(energy);
    }

    @Override
    public TileComponentEjector getEjector() {
        return ejectorComponent;
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
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        return configComponent.isCapabilityDisabled(capability, side, facing) || super.isCapabilityDisabled(capability, side);
    }


    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return false;
        }
        return capability == Capabilities.CONFIG_CARD_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return null;
        }
        if (capability == Capabilities.CONFIG_CARD_CAPABILITY) {
            return Capabilities.CONFIG_CARD_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        TileUtils.addTankData(data, fluidTank);
        TileUtils.addTankData(data, gasTank1);
        TileUtils.addTankData(data, gasTank2);
        data.add(controlType.ordinal());
        data.add(editMode.ordinal());
        return data;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            TileUtils.readTankData(dataStream, fluidTank);
            TileUtils.readTankData(dataStream, gasTank1);
            TileUtils.readTankData(dataStream, gasTank2);
            controlType = MekanismUtils.getByIndex(RedstoneControl.values(), dataStream.readInt(), controlType);
            editMode = ContainerEditMode.byIndexStatic(dataStream.readInt());
        }
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (!hasStoredFluidTanks(nbtTags) && nbtTags.hasKey("fluidTank")) {
            fluidTank.readFromNBT(nbtTags.getCompoundTag("fluidTank"));
        }
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("gasTank1")) {
            gasTank1.read(nbtTags.getCompoundTag("gasTank1"));
        }
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("gasTank2")) {
            gasTank2.read(nbtTags.getCompoundTag("gasTank2"));
        }
        sanitizeAndClampTanks();
        controlType = MekanismUtils.getByIndex(RedstoneControl.values(), nbtTags.getInteger("controlType"), controlType);
        editMode = ContainerEditMode.byIndexStatic(nbtTags.getInteger("editMode"));
    }


    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("controlType", controlType.ordinal());
        nbtTags.setInteger("editMode", editMode.ordinal());
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        writeSustainedFluidTanks(itemStack);
        writeSustainedGasTanks(itemStack);
        ItemDataUtils.setLegacyFluid(itemStack, "fluidTank", fluidTank.getFluid());
        ItemDataUtils.setLegacyGas(itemStack, "gasTank1", gasTank1.getGas());
        ItemDataUtils.setLegacyGas(itemStack, "gasTank2", gasTank2.getGas());
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (!readSustainedFluidTanks(itemStack)) {
            fluidTank.setStackUnchecked(ItemDataUtils.getLegacyFluid(itemStack, "fluidTank"));
        }
        if (!readSustainedGasTanks(itemStack)) {
            gasTank1.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "gasTank1"));
            gasTank2.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "gasTank2"));
        }
        sanitizeAndClampTanks();
    }

    private void sanitizeAndClampTanks() {
        sanitizeAndClampTank(fluidTank);
        sanitizeAndClampTank(gasTank1);
        sanitizeAndClampTank(gasTank2);
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
    public Object[] getManagedTanks() {
        return new Object[]{fluidTank, gasTank1, gasTank2};
    }

    @Override
    public RedstoneControl getControlType() {
        return controlType;
    }

    @Override
    public void setControlType(RedstoneControl type) {
        controlType = type;
    }

    @Override
    public boolean canPulse() {
        return false;
    }

    @Override
    public ContainerEditMode getContainerEditMode() {
        return editMode;
    }

    @Override
    public void setContainerEditMode(ContainerEditMode mode) {
        if (editMode != mode) {
            editMode = mode;
            MekanismUtils.saveChunk(this);
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
    }

    @Override
    public int getBlockGuiID(Block block, int metadata) {
        return MachineType.get(block, metadata) != null ? MachineType.get(block, metadata).guiId : -1;
    }

    @Override
    protected boolean shouldDumpRadiation() {
        return true;
    }
}
