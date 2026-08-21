package mekanism.common.tile.component;

import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.EnumColor;
import mekanism.api.TileNetworkList;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.base.ITileComponent;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.MekanismContainer.ISpecificContainerTracker;
import mekanism.common.inventory.container.sync.ISyncableData;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.IRecipeInputInventorySlot;
import mekanism.common.lib.inventory.HandlerTransitRequest;
import mekanism.common.lib.inventory.TransitRequest;
import mekanism.common.lib.inventory.TransitRequest.TransitResponse;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.*;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;

import java.util.*;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;

public class TileComponentEjector implements ITileComponent, ISpecificContainerTracker {


    private TileEntityContainerBlock tileEntity;

    private boolean strictInput;
    private EnumColor outputColor;
    private EnumColor[] inputColors = new EnumColor[]{null, null, null, null, null, null};
    private int tickDelay = 0;
    private Map<TransmissionType, TileComponentConfig> configSources = new EnumMap<>(TransmissionType.class);
    private Map<TransmissionType, ConfigInfo> configInfo = new EnumMap<>(TransmissionType.class);
    private Predicate<TransmissionType> canEject;
    private Predicate<IExtendedGasTank> canTankEject;
    private DoubleSupplier energyEjectRate;

    private final EjectSpeedController fluid = new EjectSpeedController();
    private final EjectSpeedController gas = new EjectSpeedController();

    public TileComponentEjector(TileEntityContainerBlock tile) {
        this(tile, null);
    }

    public TileComponentEjector(TileEntityContainerBlock tile, DoubleSupplier energyEjectRate, boolean energyMarker) {
        this(tile, energyEjectRate);
    }

    private TileComponentEjector(TileEntityContainerBlock tile, DoubleSupplier energyEjectRate) {
        tileEntity = tile;
        this.energyEjectRate = energyEjectRate;
        tile.components.add(this);
    }

    public TileComponentEjector setOutputData(TileComponentConfig config, TransmissionType... types) {
        for (TransmissionType type : types) {
            ConfigInfo info = config.getConfigInfo(type);
            if (info != null) {
                configInfo.put(type, info);
                configSources.put(type, config);
            }
        }
        return this;
    }

    public TileComponentEjector setCanEject(Predicate<TransmissionType> canEject) {
        this.canEject = canEject;
        return this;
    }

    public TileComponentEjector setCanTankEject(Predicate<IExtendedGasTank> canTankEject) {
        this.canTankEject = canTankEject;
        return this;
    }

    public TileComponentEjector removeOutputData(TransmissionType type) {
        configInfo.remove(type);
        configSources.remove(type);
        return this;
    }

    public void readFrom(TileComponentEjector ejector) {
        strictInput = ejector.strictInput;
        outputColor = ejector.outputColor;
        inputColors = ejector.inputColors;
        tickDelay = ejector.tickDelay;
        configSources = ejector.configSources;
        configInfo = ejector.configInfo;
        canEject = ejector.canEject;
        canTankEject = ejector.canTankEject;
        energyEjectRate = ejector.energyEjectRate;
    }

    public boolean isEjecting(ConfigInfo info, TransmissionType type) {
        return info != null && info.isEjecting() && (canEject == null || canEject.test(type));
    }

    @Override
    public void tick() {
        if (tileEntity.getWorld().isRemote) {
            return;
        }

        if (tickDelay == 0 || MekanismConfig.current().mekce.ItemsEjectWithoutDelay.val()) {
            inputItemsByConfig();
            outputItemsByConfig();
            if (!MekanismConfig.current().mekce.ItemsEjectWithoutDelay.val()) {
                tickDelay = MekanismConfig.current().mekce.ItemEjectionDelay.val();
            }
        } else {
            tickDelay--;
        }

        for (TransmissionType type : new ArrayList<>(configInfo.keySet())) {
            if (type != TransmissionType.ITEM && type != TransmissionType.HEAT) {
                ejectByConfig(type);
            }
        }
    }

    /**
     * Eject gas.
     */
    private void ejectGas(Set<EnumFacing> outputSides, IExtendedGasTank tank, EjectSpeedController speedController, int tankIdx) {
        speedController.record(tankIdx);

        if (tileEntity.isContainerExtractionGuarded(tank)) {
            return;
        }

        if (tank.getGas() == null || tank.getGasAmount() <= 0 || tank.getGas().getGas() == null) {
            return;
        }

        if (!speedController.canEject(tankIdx)) {
            return;
        }

        int emitAmount = Math.min(tank.getCapacity(), tank.getGasAmount());
        GasStack simulated = tank.extract(emitAmount, Action.SIMULATE, AutomationType.INTERNAL);
        int emitted = simulated == null ? 0 : GasUtils.emit(simulated, tileEntity, outputSides);
        speedController.eject(tankIdx, emitted);
        if (emitted <= 0) {
            return;
        }

        tank.extract(emitted, Action.EXECUTE, AutomationType.INTERNAL);
    }

    /**
     * Eject fluid.
     */
    private void ejectFluid(Set<EnumFacing> outputSides, IExtendedFluidTank tank, EjectSpeedController speedController, int tankIdx) {
        speedController.record(tankIdx);

        if (tileEntity.isContainerExtractionGuarded(tank)) {
            return;
        }

        if (tank.getFluid() == null || tank.getFluidAmount() <= 0) {
            return;
        }

        if (!speedController.canEject(tankIdx)) {
            return;
        }

        int emitAmount = Math.min(tank.getCapacity(), tank.getFluidAmount());
        FluidStack simulated = tank.extract(emitAmount, Action.SIMULATE, AutomationType.INTERNAL);
        int emitted = simulated == null ? 0 : FluidUtils.emit(outputSides, simulated, tileEntity);
        speedController.eject(tankIdx, emitted);
        if (emitted <= 0) {
            return;
        }

        tank.extract(emitted, Action.EXECUTE, AutomationType.INTERNAL);
    }

    private boolean outputItemsByConfig() {
        ConfigInfo info = getConfigInfo(TransmissionType.ITEM);
        if (info == null || !canEject(TransmissionType.ITEM)) {
            return false;
        }
        Map<List<IInventorySlot>, Set<EnumFacing>> outputData = null;
        for (DataType dataType : info.getSupportedDataTypes()) {
            if (!dataType.canOutput()) {
                continue;
            }
            ISlotInfo slotInfo = info.getSlotInfo(dataType);
            if (slotInfo != null && slotInfo.isEmpty()) {
                continue;
            }
            if (!(slotInfo instanceof InventorySlotInfo inventorySlotInfo)) {
                continue;
            }
            Set<EnumFacing> outputs = info.getSidesForData(dataType);
            if (outputs.isEmpty()) {
                continue;
            }
            List<IInventorySlot> outputSlots = inventorySlotInfo.getOutputSlots();
            if (outputSlots.isEmpty()) {
                continue;
            }
            if (outputData == null) {
                outputData = new HashMap<>();
            }
            outputData.computeIfAbsent(new ArrayList<>(outputSlots), ignored -> EnumSet.noneOf(EnumFacing.class)).addAll(outputs);
        }
        if (outputData == null || outputData.isEmpty()) {
            return false;
        }
        for (Map.Entry<List<IInventorySlot>, Set<EnumFacing>> entry : outputData.entrySet()) {
            outputItems(entry.getKey(), entry.getValue());
        }
        return true;
    }

    private void outputItems(List<IInventorySlot> outputSlots, Set<EnumFacing> outputs) {
        TileEntityContainerBlock self = tileEntity;
        for (EnumFacing side : outputs) {
            IItemHandler handler = InventoryUtils.getItemHandler(self, side);
            if (handler == null) {
                continue;
            }
            List<IInventorySlot> sideSlots = self.getInventorySlots(side);
            TileEntity tile = MekanismUtils.getTileEntity(self.getWorld(), self.getPos().offset(side));
            if (tile == null) {
                continue;
            }
            TransitRequest ejectMap = getEjectItemMap(handler, sideSlots, outputSlots);
            while (!ejectMap.isEmpty()) {
                TransitResponse response = ejectMap.eject(self, InventoryUtils.getItemHandler(tile, side.getOpposite()), 0, ignored -> outputColor);
                if (response.isEmpty()) {
                    break;
                }
                response.useAll();
            }
        }
    }

    private boolean ejectByConfig(TransmissionType type) {
        ConfigInfo info = getConfigInfo(type);
        if (info == null || !canEject(type)) {
            return false;
        }
        Map<Object, Set<EnumFacing>> outputData = null;
        for (DataType dataType : info.getSupportedDataTypes()) {
            if (!dataType.canOutput()) {
                continue;
            }
            ISlotInfo slotInfo = info.getSlotInfo(dataType);
            if (slotInfo != null && slotInfo.isEmpty()) {
                continue;
            }
            Set<EnumFacing> outputSides = info.getSidesForData(dataType);
            if (outputSides.isEmpty()) {
                continue;
            }
            if (type == TransmissionType.GAS && slotInfo instanceof GasSlotInfo gasSlotInfo) {
                for (IExtendedGasTank tank : gasSlotInfo.getOutputTanks()) {
                    if (tank.getGas() != null && tank.getGasAmount() > 0 && (canTankEject == null || canTankEject.test(tank))) {
                        if (outputData == null) {
                            outputData = new HashMap<>();
                        }
                        outputData.computeIfAbsent(tank, ignored -> EnumSet.noneOf(EnumFacing.class)).addAll(outputSides);
                    }
                }
            } else if (type == TransmissionType.FLUID && slotInfo instanceof FluidSlotInfo fluidSlotInfo) {
                for (IExtendedFluidTank tank : fluidSlotInfo.getOutputTanks()) {
                    if (tank.getFluid() != null && tank.getFluidAmount() > 0) {
                        if (outputData == null) {
                            outputData = new HashMap<>();
                        }
                        outputData.computeIfAbsent(tank, ignored -> EnumSet.noneOf(EnumFacing.class)).addAll(outputSides);
                    }
                }
            } else if (type == TransmissionType.ENERGY && slotInfo instanceof EnergySlotInfo energySlotInfo) {
                for (IEnergyContainer container : energySlotInfo.getContainers()) {
                    if (container.getEnergy() > 0) {
                        if (outputData == null) {
                            outputData = new HashMap<>();
                        }
                        outputData.computeIfAbsent(container, ignored -> EnumSet.noneOf(EnumFacing.class)).addAll(outputSides);
                    }
                }
            }
        }
        if (outputData == null || outputData.isEmpty()) {
            return true;
        }
        List<Object> tanks = new ArrayList<>(outputData.keySet());
        if (type == TransmissionType.GAS) {
            gas.ensureSize(tanks.size(), () -> {
                List<TankProvider> providers = new ArrayList<>(tanks.size());
                for (Object tank : tanks) {
                    providers.add(new TankProvider.Gas((IExtendedGasTank) tank));
                }
                return providers;
            });
            for (int tankIdx = 0; tankIdx < tanks.size(); tankIdx++) {
                ejectGas(outputData.get(tanks.get(tankIdx)), (IExtendedGasTank) tanks.get(tankIdx), gas, tankIdx);
            }
        } else if (type == TransmissionType.FLUID) {
            fluid.ensureSize(tanks.size(), () -> {
                List<TankProvider> providers = new ArrayList<>(tanks.size());
                for (Object tank : tanks) {
                    providers.add(new TankProvider.Fluid((IExtendedFluidTank) tank));
                }
                return providers;
            });
            for (int tankIdx = 0; tankIdx < tanks.size(); tankIdx++) {
                ejectFluid(outputData.get(tanks.get(tankIdx)), (IExtendedFluidTank) tanks.get(tankIdx), fluid, tankIdx);
            }
        } else if (type == TransmissionType.ENERGY) {
            for (Object container : tanks) {
                IEnergyContainer energyContainer = (IEnergyContainer) container;
                CableUtils.emit(outputData.get(container), energyContainer, tileEntity,
                      energyEjectRate == null ? energyContainer.getMaxEnergy() : energyEjectRate.getAsDouble());
            }
        }
        return true;
    }

    private void inputItemsByConfig() {
        ConfigInfo info = getConfigInfo(TransmissionType.ITEM);
        if (info == null) {
            return;
        }
        Set<List<IInventorySlot>> handledInputSlots = new HashSet<>();
        for (DataType dataType : info.getSupportedDataTypes()) {
            if (!dataType.canAutoPull()) {
                continue;
            }
            ISlotInfo slotInfo = info.getSlotInfo(dataType);
            if (!(slotInfo instanceof InventorySlotInfo inventorySlotInfo) || !slotInfo.canInput()) {
                continue;
            }
            List<IInventorySlot> inputSlots = inventorySlotInfo.getInputSlots();
            if (inputSlots.isEmpty()) {
                continue;
            }
            List<IInventorySlot> inputSlotGroup = new ArrayList<>(inputSlots);
            if (!handledInputSlots.add(inputSlotGroup)) {
                continue;
            }
            Set<EnumFacing> inputSides = info.getSidesForData(dataType);
            if (inputSides.isEmpty()) {
                continue;
            }
            for (EnumFacing side : inputSides) {
                TileEntity tile = MekanismUtils.getTileEntity(tileEntity.getWorld(), tileEntity.getPos().offset(side));
                if (tile == null || !InventoryUtils.isItemHandler(tile, side.getOpposite())) {
                    continue;
                }
                IItemHandler handler = InventoryUtils.getItemHandler(tile, side.getOpposite());
                if (handler != null && inputFromExternal(handler, inputSlotGroup, side)) {
                    tileEntity.markNoUpdateSync();
                    break;
                }
            }
        }
    }

    private boolean inputFromExternal(IItemHandler external, List<IInventorySlot> internalSlots, EnumFacing side) {
        for (int externalSlot = external.getSlots() - 1; externalSlot >= 0; externalSlot--) {
            ItemStack externalStack = external.getStackInSlot(externalSlot);
            if (externalStack.isEmpty()) {
                continue;
            }
            if (inputFromExternalSlot(external, externalSlot, externalStack, internalSlots, side, true) ||
                  inputFromExternalSlot(external, externalSlot, externalStack, internalSlots, side, false)) {
                return true;
            }
        }
        return false;
    }

    private boolean inputFromExternalSlot(IItemHandler external, int externalSlot, ItemStack externalStack, List<IInventorySlot> internalSlots, EnumFacing side,
          boolean ignoreEmpty) {
        int maxCanExtract = Math.min(externalStack.getCount(), externalStack.getMaxStackSize());
        for (IInventorySlot internalSlot : internalSlots) {
            if (ignoreEmpty == internalSlot.isEmpty()) {
                continue;
            }
            ItemStack simulatedRemainder = internalSlot.insertItem(externalStack, Action.SIMULATE, AutomationType.EXTERNAL);
            int toMove = externalStack.getCount() - simulatedRemainder.getCount();
            if (toMove <= 0) {
                continue;
            }
            ItemStack extracted = external.extractItem(externalSlot, Math.min(maxCanExtract, toMove), true);
            if (extracted.isEmpty()) {
                continue;
            }
            if (internalSlot instanceof IRecipeInputInventorySlot recipeInputSlot && !recipeInputSlot.canAutoPull(extracted, side)) {
                continue;
            }
            ItemStack remainder = internalSlot.insertItem(extracted, Action.EXECUTE, AutomationType.EXTERNAL);
            int moved = extracted.getCount() - remainder.getCount();
            if (moved > 0) {
                external.extractItem(externalSlot, moved, false);
                return true;
            }
        }
        return false;
    }

    private TransitRequest getEjectItemMap(IItemHandler handler, List<IInventorySlot> sideSlots, List<IInventorySlot> outputSlots) {
        HandlerTransitRequest request = new HandlerTransitRequest(handler);
        List<IInventorySlot> shuffled = new ArrayList<>(outputSlots);
        Collections.shuffle(shuffled);
        for (IInventorySlot slot : shuffled) {
            int slotIndex = sideSlots.indexOf(slot);
            if (slotIndex != -1) {
                addToEjectItemMap(request, slot, slotIndex);
            }
        }
        return request;
    }

    private void addToEjectItemMap(HandlerTransitRequest request, IInventorySlot slot, int index) {
        if (tileEntity.isContainerExtractionGuarded(slot)) {
            return;
        }
        ItemStack stack = slot.getStack();
        if (!stack.isEmpty() && !slot.extractItem(1, mekanism.api.Action.SIMULATE, mekanism.api.AutomationType.EXTERNAL).isEmpty()) {
            request.addItem(stack, index);
        }
    }

    private ConfigInfo getConfigInfo(TransmissionType type) {
        TileComponentConfig config = configSources.get(type);
        return config == null ? configInfo.get(type) : config.getConfigInfo(type);
    }

    private boolean canEject(TransmissionType type) {
        return getEjecting(type) && (canEject == null || canEject.test(type));
    }

    public boolean hasStrictInput() {
        return strictInput;
    }

    public void setStrictInput(boolean strict) {
        strictInput = strict;
        MekanismUtils.saveChunk(tileEntity);
    }

    public EnumColor getOutputColor() {
        return outputColor;
    }

    public void setOutputColor(EnumColor color) {
        outputColor = color;
        MekanismUtils.saveChunk(tileEntity);
    }

    public void setInputColor(EnumFacing side, EnumColor color) {
        inputColors[side.ordinal()] = color;
        MekanismUtils.saveChunk(tileEntity);
    }

    public EnumColor getInputColor(EnumFacing side) {
        return inputColors[side.ordinal()];
    }

    @Override
    public void read(NBTTagCompound nbtTags) {
        strictInput = nbtTags.getBoolean("strictInput");
        if (nbtTags.hasKey("ejectColor")) {
            outputColor = readColor(nbtTags.getInteger("ejectColor"));
        }
        for (int i = 0; i < 6; i++) {
            if (nbtTags.hasKey("inputColors" + i)) {
                inputColors[i] = readColor(nbtTags.getInteger("inputColors" + i));
            }
        }
    }

    @Override
    public void read(ByteBuf dataStream) {
        strictInput = dataStream.readBoolean();
        outputColor = readColor(dataStream.readInt());
        for (int i = 0; i < 6; i++) {
            inputColors[i] = readColor(dataStream.readInt());
        }
    }

    @Override
    public void write(NBTTagCompound nbtTags) {
        nbtTags.setBoolean("strictInput", strictInput);
        if (outputColor != null) {
            nbtTags.setInteger("ejectColor", getColorIndex(outputColor));
        }
        for (int i = 0; i < 6; i++) {
            nbtTags.setInteger("inputColors" + i, getColorIndex(inputColors[i]));
        }
    }

    @Override
    public void write(TileNetworkList data) {
        data.add(strictInput);
        data.add(getColorIndex(outputColor));
        for (int i = 0; i < 6; i++) {
            data.add(getColorIndex(inputColors[i]));
        }
    }

    private EnumColor readColor(int inputColor) {
        if (inputColor == -1) {
            return null;
        }
        return MekanismUtils.getByIndex(TransporterUtils.colors, inputColor, null);
    }

    private int getColorIndex(EnumColor color) {
        if (color == null) {
            return -1;
        }
        return TransporterUtils.colors.indexOf(color);
    }

    @Override
    public void invalidate() {
    }

    @Override
    public List<ISyncableData> getSpecificSyncableData() {
        List<ISyncableData> list = new ArrayList<>();
        list.add(SyncableBoolean.create(this::hasStrictInput, value -> strictInput = value));
        list.add(SyncableInt.create(() -> getColorIndex(outputColor), index -> outputColor = readColor(index)));
        for (int i = 0; i < inputColors.length; i++) {
            int idx = i;
            list.add(SyncableInt.create(() -> getColorIndex(inputColors[idx]), index -> inputColors[idx] = readColor(index)));
        }
        return list;
    }

    private boolean getEjecting(TransmissionType type) {
        if (tileEntity instanceof ISideConfiguration configuration) {
            return configuration.getConfig() != null && configuration.getConfig().isEjecting(type);
        } else {
            return false;
        }
    }

}
