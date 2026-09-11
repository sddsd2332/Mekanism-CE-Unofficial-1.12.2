package mekanism.common.tile.component;

import io.netty.buffer.ByteBuf;
import mekanism.api.RelativeSide;
import mekanism.api.TileNetworkList;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.base.IEnergyWrapper;
import mekanism.common.base.ITankManager;
import mekanism.common.base.ITileComponent;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.MekanismContainer.ISpecificContainerTracker;
import mekanism.common.inventory.container.sync.ISyncableData;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.tile.TileEntityEnergyCube;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.*;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.tile.prefab.TileEntityElectricBlock;
import mekanism.common.util.MekanismUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

public class TileComponentConfig implements ITileComponent, ISpecificContainerTracker {

    private static final String SIDE_DATA_STORED = "sideDataStored";
    private static final String CONFIG = "config";
    private static final String EJECTING = "ejecting";

    public final TileEntityContainerBlock tileEntity;
    private final List<TransmissionType> transmissions = new ArrayList<>();
    private final Map<TransmissionType, ConfigInfo> configInfo = new EnumMap<>(TransmissionType.class);
    private final Map<TransmissionType, List<Consumer<EnumFacing>>> configChangeListeners = new EnumMap<>(TransmissionType.class);

    public TileComponentConfig(TileEntityContainerBlock tile, TransmissionType... types) {
        tileEntity = tile;
        for (TransmissionType type : types) {
            addSupported(type);
        }
        tile.components.add(this);
    }

    public List<TransmissionType> getTransmissions() {
        return transmissions;
    }

    public void addConfigChangeListener(TransmissionType transmissionType, Consumer<EnumFacing> listener) {
        configChangeListeners.computeIfAbsent(transmissionType, type -> new ArrayList<>(1)).add(listener);
    }

    public void sideChanged(TransmissionType type, RelativeSide side) {
        EnumFacing direction = side.getDirection(tileEntity.facing);
        sideChangedBasic(type, direction);
        if (tileEntity.getWorld() != null) {
            MekanismUtils.notifyNeighborOfChange(tileEntity.getWorld(), direction, tileEntity.getPos());
        }
    }

    private void sideChangedBasic(TransmissionType type, EnumFacing direction) {
        switch (type) {
            case ENERGY:
                invalidateCapability(Capabilities.STRICT_ENERGY_CAPABILITY, direction);
                invalidateCapability(Capabilities.ENERGY_STORAGE_CAPABILITY, direction);
                invalidateCapability(Capabilities.ENERGY_ACCEPTOR_CAPABILITY, direction);
                invalidateCapability(Capabilities.ENERGY_OUTPUTTER_CAPABILITY, direction);
                invalidateCapability(CapabilityEnergy.ENERGY, direction);
                invalidateCapability(Capabilities.TESLA_CONSUMER_CAPABILITY, direction);
                invalidateCapability(Capabilities.TESLA_PRODUCER_CAPABILITY, direction);
                invalidateCapability(Capabilities.TESLA_HOLDER_CAPABILITY, direction);
                break;
            case FLUID:
                invalidateCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, direction);
                break;
            case GAS:
                invalidateCapability(Capabilities.GAS_HANDLER_CAPABILITY, direction);
                break;
            case ITEM:
                invalidateCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, direction);
                break;
            case HEAT:
                invalidateCapability(Capabilities.HEAT_HANDLER_CAPABILITY, direction);
                invalidateCapability(Capabilities.HEAT_TRANSFER_CAPABILITY, direction);
                break;
            default:
                break;
        }
        MekanismUtils.saveChunk(tileEntity);
        notifyConfigChangeListeners(type, direction);
    }

    private void invalidateCapability(@Nullable Capability<?> capability, EnumFacing direction) {
        if (capability != null) {
            tileEntity.invalidateCapability(capability, direction);
        }
    }

    public void readFrom(TileComponentConfig config) {
        for (TransmissionType type : config.transmissions) {
            ConfigInfo source = config.getConfigInfo(type);
            ConfigInfo target = getConfigInfo(type);
            if (source != null && target != null) {
                target.copyConfigFrom(source);
            }
        }
        notifyConfigChangeListeners();
    }

    public void removeSupported(TransmissionType type) {
        transmissions.remove(type);
        configInfo.remove(type);
        notifyConfigChangeListeners(type, null);
    }

    public void addSupported(TransmissionType type) {
        if (!transmissions.contains(type)) {
            transmissions.add(type);
        }
        configInfo.put(type, new ConfigInfo(() -> tileEntity.facing));
        notifyConfigChangeListeners(type, null);
    }

    public boolean supports(TransmissionType type) {
        return transmissions.contains(type);
    }

    @Nullable
    public ConfigInfo getConfigInfo(TransmissionType type) {
        return configInfo.get(type);
    }

    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side, EnumFacing tileDirection) {
        if (side == null) {
            return false;
        }
        TransmissionType type = null;
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            type = TransmissionType.ITEM;
        } else if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            type = TransmissionType.GAS;
        } else if (capability == Capabilities.HEAT_HANDLER_CAPABILITY || capability == Capabilities.HEAT_TRANSFER_CAPABILITY) {
            type = TransmissionType.HEAT;
        } else if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            type = TransmissionType.FLUID;
        } else if (capability == Capabilities.STRICT_ENERGY_CAPABILITY || capability == Capabilities.ENERGY_STORAGE_CAPABILITY || capability == Capabilities.ENERGY_ACCEPTOR_CAPABILITY ||
              capability == Capabilities.ENERGY_OUTPUTTER_CAPABILITY || capability == CapabilityEnergy.ENERGY ||
              capability == Capabilities.TESLA_HOLDER_CAPABILITY || capability == Capabilities.TESLA_CONSUMER_CAPABILITY ||
              capability == Capabilities.TESLA_PRODUCER_CAPABILITY) {
            type = TransmissionType.ENERGY;
        }
        if (type != null) {
            ConfigInfo info = getConfigInfo(type);
            if (info != null) {
                ISlotInfo slotInfo = info.getSlotInfo(RelativeSide.fromDirections(tileDirection, side));
                return slotInfo == null || !slotInfo.isEnabled();
            }
        }
        return false;
    }

    public void setCanEject(TransmissionType type, boolean eject) {
        ConfigInfo info = getConfigInfo(type);
        if (info != null) {
            info.setCanEject(eject);
        }
    }

    public void removeCanEject(TransmissionType type) {
        setCanEject(type, true);
    }

    public boolean canEject(TransmissionType type) {
        ConfigInfo info = getConfigInfo(type);
        return info != null && info.canEject();
    }

    public boolean isEjecting(TransmissionType type) {
        ConfigInfo info = getConfigInfo(type);
        return info != null && info.isEjecting();
    }

    public void setEjecting(TransmissionType type, boolean eject) {
        ConfigInfo info = getConfigInfo(type);
        if (info != null) {
            info.setEjecting(eject);
            MekanismUtils.saveChunk(tileEntity);
        }
    }

    public void addDisabledSides(RelativeSide... sides) {
        for (ConfigInfo config : configInfo.values()) {
            config.addDisabledSides(sides);
        }
    }

    public void addDisabledSides(TransmissionType type, RelativeSide... sides) {
        ConfigInfo info = getConfigInfo(type);
        if (info != null) {
            info.addDisabledSides(sides);
            notifyConfigChangeListeners(type, null);
        }
    }

    public Set<EnumFacing> getSidesForData(TransmissionType type, EnumFacing facing, DataType dataType) {
        ConfigInfo info = getConfigInfo(type);
        return info == null ? Collections.emptySet() : info.getSidesForData(dataType);
    }

    public boolean hasSideForData(TransmissionType type, EnumFacing facing, DataType dataType, EnumFacing sideToTest) {
        if (sideToTest == null) {
            return false;
        }
        ConfigInfo info = getConfigInfo(type);
        return info != null && info.getDataType(RelativeSide.fromDirections(facing, sideToTest)) == dataType;
    }

    @Nonnull
    public DataType getDataType(TransmissionType type, EnumFacing side, EnumFacing facing) {
        if (side == null) {
            return DataType.EMPTY;
        }
        ConfigInfo info = getConfigInfo(type);
        return info == null ? DataType.EMPTY : info.getDataType(RelativeSide.fromDirections(facing, side));
    }

    @Nonnull
    public DataType getDataType(TransmissionType type, EnumFacing side) {
        if (side == null) {
            return DataType.EMPTY;
        }
        ConfigInfo info = getConfigInfo(type);
        return info == null ? DataType.EMPTY : info.getDataType(RelativeSide.bydex(side.ordinal()));
    }

    @Nullable
    public ISlotInfo getSlotInfo(TransmissionType type, EnumFacing side, EnumFacing facing) {
        ConfigInfo info = getConfigInfo(type);
        if (info == null || side == null) {
            return null;
        }
        return info.getSlotInfo(RelativeSide.fromDirections(facing, side));
    }

    public boolean hasSlot(TransmissionType type, EnumFacing side, EnumFacing facing, int... slots) {
        ISlotInfo slotInfo = getSlotInfo(type, side, facing);
        if (slotInfo == null) {
            return false;
        }
        if (type == TransmissionType.ITEM && slotInfo instanceof InventorySlotInfo inventorySlotInfo) {
            for (int slot : slots) {
                IInventorySlot inventorySlot = tileEntity.getInventorySlot(slot);
                if (inventorySlot != null && inventorySlotInfo.hasSlot(inventorySlot)) {
                    return true;
                }
            }
        } else if ((type == TransmissionType.GAS || type == TransmissionType.FLUID) && tileEntity instanceof ITankManager manager) {
            Object[] tanks = manager.getManagedTanks();
            if (tanks == null) {
                return false;
            }
            for (int slot : slots) {
                if (slot >= 0 && slot < tanks.length) {
                    Object tank = tanks[slot];
                    if (slotInfo instanceof GasSlotInfo gasSlotInfo && tank instanceof IExtendedGasTank gasTank && gasSlotInfo.hasTank(gasTank)) {
                        return true;
                    } else if (slotInfo instanceof FluidSlotInfo fluidSlotInfo && tank instanceof IExtendedFluidTank fluidTank && fluidSlotInfo.hasTank(fluidTank)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void fillConfig(TransmissionType type, DataType dataType) {
        ConfigInfo info = getConfigInfo(type);
        if (info == null) {
            return;
        }
        DataType target = MekanismConfig.current().mekce.EnableTheDefaultConfiguration.val() ? dataType : DataType.NONE;
        info.fill(target);
        notifyConfigChangeListeners(type, null);
    }

    public void setConfig(TransmissionType type, DataType... config) {
        if (config.length != EnumFacing.VALUES.length) {
            throw new IllegalArgumentException("Expected one data type per side");
        }
        ConfigInfo info = getConfigInfo(type);
        if (info == null) {
            return;
        }
        for (int i = 0; i < config.length; i++) {
            DataType dataType = config[i];
            RelativeSide side = RelativeSide.bydex(i);
            if (dataType == DataType.EMPTY) {
                info.addDisabledSides(side);
            } else {
                info.setDataType(allowDefaultConfig(dataType), side);
            }
        }
        notifyConfigChangeListeners(type, null);
    }

    private DataType allowDefaultConfig(DataType dataType) {
        return MekanismConfig.current().mekce.EnableTheDefaultConfiguration.val() || dataType == DataType.EMPTY ? dataType : DataType.NONE;
    }

    public void setIOConfig(TransmissionType type) {
        Object container = getSelfContainer(type);
        if (container == null) {
            throw new IllegalStateException("No default self container for transmission type " + type);
        }
        setupIOConfig(type, container, RelativeSide.RIGHT);
        setConfig(type, DataType.INPUT, DataType.INPUT, DataType.OUTPUT, DataType.INPUT, DataType.INPUT, DataType.INPUT);
    }

    public void setInputConfig(TransmissionType type) {
        Object container = getSelfContainer(type);
        if (container == null) {
            throw new IllegalStateException("No default self container for transmission type " + type);
        }
        setupInputConfig(type, container);
        fillConfig(type, DataType.INPUT);
    }

    public void removeInputConfig(TransmissionType type) {
        removeConfig(type);
        removeCanEject(type);
    }

    public void removeConfig(TransmissionType type) {
        ConfigInfo info = getConfigInfo(type);
        if (info != null) {
            info.fill(DataType.NONE);
            notifyConfigChangeListeners(type, null);
        }
    }

    @Nullable
    private Object getSelfContainer(TransmissionType type) {
        if (type == TransmissionType.ENERGY) {
            if (tileEntity instanceof TileEntityEnergyCube energyCube) {
                return energyCube.getEnergyContainer();
            } else if (tileEntity instanceof TileEntityElectricBlock electricBlock) {
                return electricBlock.getMainEnergyContainer();
            } else if (tileEntity instanceof IEnergyWrapper) {
                return tileEntity;
            }
        } else if (type == TransmissionType.HEAT && tileEntity instanceof IHeatCapacitor) {
            return tileEntity;
        }
        return null;
    }

    private boolean isItemContainerData(TransmissionType type, DataType dataType) {
        return type == TransmissionType.ITEM && (dataType == DataType.ENERGY || dataType == DataType.EXTRA);
    }

    public ConfigInfo setupInputConfig(TransmissionType type, Object container) {
        ConfigInfo config = getConfigInfo(type);
        if (config != null) {
            config.addSlotInfo(DataType.INPUT, createInfo(type, true, false, container));
            config.fill(DataType.INPUT);
            config.setCanEject(false);
            notifyConfigChangeListeners(type, null);
        }
        return config;
    }

    public ConfigInfo setupOutputConfig(TransmissionType type, Object container, RelativeSide... sides) {
        ConfigInfo config = getConfigInfo(type);
        if (config != null) {
            config.addSlotInfo(DataType.OUTPUT, createInfo(type, false, true, container));
            if (sides.length > 0) {
                config.setDataType(DataType.OUTPUT, sides);
            }
            config.setEjecting(true);
            notifyConfigChangeListeners(type, null);
        }
        return config;
    }

    public ConfigInfo setupIOConfig(TransmissionType type, Object inputContainer, Object outputContainer, RelativeSide outputSide) {
        return setupIOConfig(type, inputContainer, outputContainer, outputSide, false, false);
    }

    public ConfigInfo setupIOConfig(TransmissionType type, Object inputContainer, Object outputContainer, RelativeSide outputSide, boolean alwaysAllowInput,
          boolean alwaysAllowOutput) {
        ConfigInfo config = getConfigInfo(type);
        if (config != null) {
            config.addSlotInfo(DataType.INPUT, createInfo(type, true, alwaysAllowOutput, inputContainer));
            config.addSlotInfo(DataType.OUTPUT, createInfo(type, alwaysAllowInput, true, outputContainer));
            config.addSlotInfo(DataType.INPUT_OUTPUT, createInfo(type, true, true, Arrays.asList(inputContainer, outputContainer), Collections.singletonList(outputContainer)));
            config.fill(DataType.INPUT);
            config.setDataType(DataType.OUTPUT, outputSide);
            notifyConfigChangeListeners(type, null);
        }
        return config;
    }

    public ConfigInfo setupIOConfig(TransmissionType type, Object info, RelativeSide outputSide) {
        return setupIOConfig(type, info, outputSide, false);
    }

    public ConfigInfo setupIOConfig(TransmissionType type, Object info, RelativeSide outputSide, boolean alwaysAllow) {
        ConfigInfo config = getConfigInfo(type);
        if (config != null) {
            config.addSlotInfo(DataType.INPUT, createInfo(type, true, alwaysAllow, info));
            config.addSlotInfo(DataType.OUTPUT, createInfo(type, alwaysAllow, true, info));
            config.addSlotInfo(DataType.INPUT_OUTPUT, createInfo(type, true, true, info));
            config.fill(DataType.INPUT);
            config.setDataType(DataType.OUTPUT, outputSide);
            notifyConfigChangeListeners(type, null);
        }
        return config;
    }

    public ConfigInfo setupItemIOConfig(IInventorySlot inputSlot, IInventorySlot outputSlot, IInventorySlot energySlot) {
        return setupItemIOConfig(Collections.singletonList(inputSlot), Collections.singletonList(outputSlot), energySlot, false);
    }

    public ConfigInfo setupItemIOConfig(IInventorySlot inputSlot, IInventorySlot outputSlot) {
        return setupItemIOConfig(Collections.singletonList(inputSlot), Collections.singletonList(outputSlot), false);
    }

    public ConfigInfo setupItemStorageConfig(List<IInventorySlot> storageSlots) {
        ConfigInfo itemConfig = getConfigInfo(TransmissionType.ITEM);
        if (itemConfig != null) {
            addItemSlotInfo(itemConfig, DataType.INPUT, storageSlots);
            addItemSlotInfo(itemConfig, DataType.INPUT_ENHANCED, storageSlots);
            addItemSlotInfo(itemConfig, DataType.OUTPUT, storageSlots);
            addSharedItemSlotInfo(itemConfig, DataType.INPUT_OUTPUT, storageSlots);
            notifyConfigChangeListeners(TransmissionType.ITEM, null);
        }
        return itemConfig;
    }

    public ConfigInfo setupItemOutputConfig(IInventorySlot outputSlot) {
        ConfigInfo itemConfig = getConfigInfo(TransmissionType.ITEM);
        if (itemConfig != null) {
            addItemSlotInfo(itemConfig, DataType.OUTPUT, Collections.singletonList(outputSlot));
            notifyConfigChangeListeners(TransmissionType.ITEM, null);
        }
        return itemConfig;
    }

    public ConfigInfo setupItemOutputEnergyConfig(IInventorySlot outputSlot, IInventorySlot energySlot) {
        ConfigInfo itemConfig = setupItemOutputConfig(outputSlot);
        if (itemConfig != null) {
            addItemSlotInfo(itemConfig, DataType.ENERGY, Collections.singletonList(energySlot));
            notifyConfigChangeListeners(TransmissionType.ITEM, null);
        }
        return itemConfig;
    }

    public ConfigInfo addItemSlotInfo(DataType dataType, IInventorySlot... slots) {
        return addItemSlotInfo(dataType, Arrays.asList(slots), (List<IInventorySlot>) null);
    }

    public ConfigInfo addItemSlotInfo(DataType dataType, List<IInventorySlot> slots) {
        return addItemSlotInfo(dataType, slots, (List<IInventorySlot>) null);
    }

    public ConfigInfo addItemSlotInfo(DataType dataType, List<IInventorySlot> slots, IInventorySlot... outputSlots) {
        return addItemSlotInfo(dataType, slots, Arrays.asList(outputSlots));
    }

    public ConfigInfo addItemSlotInfo(DataType dataType, List<IInventorySlot> slots, @Nullable List<IInventorySlot> outputSlots) {
        ConfigInfo itemConfig = getConfigInfo(TransmissionType.ITEM);
        if (itemConfig != null) {
            itemConfig.addSlotInfo(dataType, createItemSlotInfo(dataType, slots, outputSlots));
            notifyConfigChangeListeners(TransmissionType.ITEM, null);
        }
        return itemConfig;
    }

    public ConfigInfo setupItemIOConfig(List<IInventorySlot> inputSlots, List<IInventorySlot> outputSlots, boolean alwaysAllow) {
        return setupItemIOConfig(inputSlots, outputSlots, null, alwaysAllow);
    }

    public ConfigInfo setupItemIOConfig(List<IInventorySlot> inputSlots, List<IInventorySlot> outputSlots, IInventorySlot energySlot, boolean alwaysAllow) {
        ConfigInfo itemConfig = getConfigInfo(TransmissionType.ITEM);
        if (itemConfig != null) {
            addItemSlotInfo(itemConfig, DataType.INPUT, true, alwaysAllow, inputSlots, inputSlots, alwaysAllow ? inputSlots : Collections.emptyList());
            addItemSlotInfo(itemConfig, DataType.INPUT_ENHANCED, true, alwaysAllow, inputSlots, inputSlots, alwaysAllow ? inputSlots : Collections.emptyList());
            addItemSlotInfo(itemConfig, DataType.OUTPUT, alwaysAllow, true, outputSlots, alwaysAllow ? outputSlots : Collections.emptyList(), outputSlots);
            addCombinedItemSlotInfo(itemConfig, DataType.INPUT_OUTPUT, inputSlots, outputSlots);
            if (energySlot != null) {
                addItemSlotInfo(itemConfig, DataType.ENERGY, Collections.singletonList(energySlot));
            }
            notifyConfigChangeListeners(TransmissionType.ITEM, null);
        }
        return itemConfig;
    }

    public ConfigInfo setupItemDualInputOutputConfig(IInventorySlot leftInputSlot, IInventorySlot rightInputSlot, IInventorySlot outputSlot,
          IInventorySlot energySlot) {
        ConfigInfo itemConfig = getConfigInfo(TransmissionType.ITEM);
        if (itemConfig != null) {
            addItemSlotInfo(itemConfig, DataType.INPUT_1, Collections.singletonList(leftInputSlot));
            addItemSlotInfo(itemConfig, DataType.INPUT_2, Collections.singletonList(rightInputSlot));
            addItemSlotInfo(itemConfig, DataType.OUTPUT, Collections.singletonList(outputSlot));
            addCombinedItemSlotInfo(itemConfig, DataType.INPUT_OUTPUT, Arrays.asList(leftInputSlot, rightInputSlot), Collections.singletonList(outputSlot));
            addItemSlotInfo(itemConfig, DataType.ENERGY, Collections.singletonList(energySlot));
            notifyConfigChangeListeners(TransmissionType.ITEM, null);
        }
        return itemConfig;
    }

    public ConfigInfo setupItemInputDualOutputConfig(IInventorySlot inputSlot, IInventorySlot leftOutputSlot, IInventorySlot rightOutputSlot,
          IInventorySlot energySlot) {
        ConfigInfo itemConfig = getConfigInfo(TransmissionType.ITEM);
        if (itemConfig != null) {
            addItemSlotInfo(itemConfig, DataType.INPUT, Collections.singletonList(inputSlot));
            addItemSlotInfo(itemConfig, DataType.INPUT_ENHANCED, Collections.singletonList(inputSlot));
            addItemSlotInfo(itemConfig, DataType.OUTPUT_1, Collections.singletonList(leftOutputSlot));
            addItemSlotInfo(itemConfig, DataType.OUTPUT_2, Collections.singletonList(rightOutputSlot));
            addCombinedItemSlotInfo(itemConfig, DataType.INPUT_OUTPUT, Collections.singletonList(inputSlot), Arrays.asList(leftOutputSlot, rightOutputSlot));
            addItemSlotInfo(itemConfig, DataType.ENERGY, Collections.singletonList(energySlot));
            notifyConfigChangeListeners(TransmissionType.ITEM, null);
        }
        return itemConfig;
    }

    public ConfigInfo setupItemIOExtraConfig(IInventorySlot inputSlot, IInventorySlot outputSlot, IInventorySlot extraSlot, IInventorySlot energySlot) {
        ConfigInfo itemConfig = getConfigInfo(TransmissionType.ITEM);
        if (itemConfig != null) {
            List<IInventorySlot> inputSlots = Collections.singletonList(inputSlot);
            List<IInventorySlot> outputSlots = Collections.singletonList(outputSlot);
            List<IInventorySlot> extraSlots = Collections.singletonList(extraSlot);
            addItemSlotInfo(itemConfig, DataType.INPUT, inputSlots);
            addItemSlotInfo(itemConfig, DataType.OUTPUT, outputSlots);
            addCombinedItemSlotInfo(itemConfig, DataType.INPUT_OUTPUT, inputSlots, outputSlots);
            addItemSlotInfo(itemConfig, DataType.EXTRA, extraSlots);
            addItemExtraSlotCombinations(itemConfig, inputSlots, outputSlots, extraSlots);
            addItemSlotInfo(itemConfig, DataType.ENERGY, Collections.singletonList(energySlot));
            notifyConfigChangeListeners(TransmissionType.ITEM, null);
        }
        return itemConfig;
    }

    public ConfigInfo setupItemIOExtraConfig(IInventorySlot inputSlot, List<IInventorySlot> outputSlots, IInventorySlot extraSlot, IInventorySlot energySlot) {
        ConfigInfo itemConfig = getConfigInfo(TransmissionType.ITEM);
        if (itemConfig != null) {
            List<IInventorySlot> inputSlots = Collections.singletonList(inputSlot);
            addItemSlotInfo(itemConfig, DataType.INPUT, inputSlots);
            addItemSlotInfo(itemConfig, DataType.OUTPUT, outputSlots);
            addCombinedItemSlotInfo(itemConfig, DataType.INPUT_OUTPUT, inputSlots, outputSlots);
            List<IInventorySlot> extraSlots = Collections.singletonList(extraSlot);
            addItemSlotInfo(itemConfig, DataType.EXTRA, extraSlots);
            addItemExtraSlotCombinations(itemConfig, inputSlots, outputSlots, extraSlots);
            addItemSlotInfo(itemConfig, DataType.ENERGY, Collections.singletonList(energySlot));
            notifyConfigChangeListeners(TransmissionType.ITEM, null);
        }
        return itemConfig;
    }

    public ConfigInfo setupFactoryItemConfig(List<IInventorySlot> inputSlots, List<IInventorySlot> outputSlots, IInventorySlot energySlot, IInventorySlot extraSlot) {
        ConfigInfo itemConfig = setupItemIOConfig(inputSlots, outputSlots, energySlot, false);
        if (itemConfig != null) {
            List<IInventorySlot> extraSlots = Collections.singletonList(extraSlot);
            addItemSlotInfo(itemConfig, DataType.EXTRA, extraSlots);
            addItemExtraSlotCombinations(itemConfig, inputSlots, outputSlots, extraSlots);
            itemConfig.setDataType(DataType.EXTRA, RelativeSide.BOTTOM);
            notifyConfigChangeListeners(TransmissionType.ITEM, null);
        }
        return itemConfig;
    }

    public ConfigInfo addGasSlotInfo(DataType dataType, IExtendedGasTank... tanks) {
        return addGasSlotInfo(dataType, Arrays.asList(tanks), (List<IExtendedGasTank>) null);
    }

    public ConfigInfo addGasSlotInfo(DataType dataType, List<IExtendedGasTank> tanks) {
        return addGasSlotInfo(dataType, tanks, (List<IExtendedGasTank>) null);
    }

    public ConfigInfo addGasSlotInfo(DataType dataType, List<IExtendedGasTank> tanks, IExtendedGasTank... outputTanks) {
        return addGasSlotInfo(dataType, tanks, Arrays.asList(outputTanks));
    }

    public ConfigInfo addGasSlotInfo(DataType dataType, List<IExtendedGasTank> tanks, @Nullable List<IExtendedGasTank> outputTanks) {
        ConfigInfo gasConfig = getConfigInfo(TransmissionType.GAS);
        if (gasConfig != null) {
            gasConfig.addSlotInfo(dataType, createGasSlotInfo(dataType, tanks, outputTanks));
            notifyConfigChangeListeners(TransmissionType.GAS, null);
        }
        return gasConfig;
    }

    public ConfigInfo setupGasIOConfig(IExtendedGasTank inputTank, IExtendedGasTank outputTank) {
        ConfigInfo gasConfig = getConfigInfo(TransmissionType.GAS);
        if (gasConfig != null) {
            addGasSlotInfo(gasConfig, DataType.INPUT, Collections.singletonList(inputTank));
            addGasSlotInfo(gasConfig, DataType.OUTPUT, Collections.singletonList(outputTank));
            addCombinedGasSlotInfo(gasConfig, DataType.INPUT_OUTPUT, Collections.singletonList(inputTank), Collections.singletonList(outputTank));
            notifyConfigChangeListeners(TransmissionType.GAS, null);
        }
        return gasConfig;
    }

    public ConfigInfo setupGasDualInputOutputConfig(IExtendedGasTank leftInputTank, IExtendedGasTank rightInputTank, IExtendedGasTank outputTank) {
        ConfigInfo gasConfig = getConfigInfo(TransmissionType.GAS);
        if (gasConfig != null) {
            addGasSlotInfo(gasConfig, DataType.INPUT_1, Collections.singletonList(leftInputTank));
            addGasSlotInfo(gasConfig, DataType.INPUT_2, Collections.singletonList(rightInputTank));
            addGasSlotInfo(gasConfig, DataType.OUTPUT, Collections.singletonList(outputTank));
            addGasSlotInfo(gasConfig, DataType.INPUT, Arrays.asList(leftInputTank, rightInputTank));
            addCombinedGasSlotInfo(gasConfig, DataType.INPUT_OUTPUT, Arrays.asList(leftInputTank, rightInputTank), Collections.singletonList(outputTank));
            notifyConfigChangeListeners(TransmissionType.GAS, null);
        }
        return gasConfig;
    }

    public ConfigInfo setupGasDualOutputConfig(IExtendedGasTank leftOutputTank, IExtendedGasTank rightOutputTank) {
        ConfigInfo gasConfig = getConfigInfo(TransmissionType.GAS);
        if (gasConfig != null) {
            addGasSlotInfo(gasConfig, DataType.OUTPUT_1, Collections.singletonList(leftOutputTank));
            addGasSlotInfo(gasConfig, DataType.OUTPUT_2, Collections.singletonList(rightOutputTank));
            notifyConfigChangeListeners(TransmissionType.GAS, null);
        }
        return gasConfig;
    }

    public ConfigInfo addFluidSlotInfo(DataType dataType, IExtendedFluidTank... tanks) {
        return addFluidSlotInfo(dataType, Arrays.asList(tanks), (List<IExtendedFluidTank>) null);
    }

    public ConfigInfo addFluidSlotInfo(DataType dataType, List<IExtendedFluidTank> tanks) {
        return addFluidSlotInfo(dataType, tanks, (List<IExtendedFluidTank>) null);
    }

    public ConfigInfo addFluidSlotInfo(DataType dataType, List<IExtendedFluidTank> tanks, IExtendedFluidTank... outputTanks) {
        return addFluidSlotInfo(dataType, tanks, Arrays.asList(outputTanks));
    }

    public ConfigInfo addFluidSlotInfo(DataType dataType, List<IExtendedFluidTank> tanks, @Nullable List<IExtendedFluidTank> outputTanks) {
        ConfigInfo fluidConfig = getConfigInfo(TransmissionType.FLUID);
        if (fluidConfig != null) {
            fluidConfig.addSlotInfo(dataType, createFluidSlotInfo(dataType, tanks, outputTanks));
            notifyConfigChangeListeners(TransmissionType.FLUID, null);
        }
        return fluidConfig;
    }

    public ConfigInfo setupFluidIOConfig(IExtendedFluidTank inputTank, IExtendedFluidTank outputTank) {
        ConfigInfo fluidConfig = getConfigInfo(TransmissionType.FLUID);
        if (fluidConfig != null) {
            addFluidSlotInfo(fluidConfig, DataType.INPUT, Collections.singletonList(inputTank));
            addFluidSlotInfo(fluidConfig, DataType.OUTPUT, Collections.singletonList(outputTank));
            addCombinedFluidSlotInfo(fluidConfig, DataType.INPUT_OUTPUT, Collections.singletonList(inputTank), Collections.singletonList(outputTank));
            notifyConfigChangeListeners(TransmissionType.FLUID, null);
        }
        return fluidConfig;
    }

    public ConfigInfo setupFluidInputConfig(IExtendedFluidTank tank) {
        ConfigInfo fluidConfig = getConfigInfo(TransmissionType.FLUID);
        if (fluidConfig != null) {
            addFluidSlotInfo(fluidConfig, DataType.INPUT, Collections.singletonList(tank));
            fluidConfig.fill(DataType.INPUT);
            fluidConfig.setCanEject(false);
            notifyConfigChangeListeners(TransmissionType.FLUID, null);
        }
        return fluidConfig;
    }

    public ConfigInfo setupFluidIOConfig(IExtendedFluidTank tank, RelativeSide outputSide) {
        return setupFluidIOConfig(tank, outputSide, false);
    }

    public ConfigInfo setupFluidIOConfig(IExtendedFluidTank tank, RelativeSide outputSide, boolean alwaysAllow) {
        ConfigInfo fluidConfig = getConfigInfo(TransmissionType.FLUID);
        if (fluidConfig != null) {
            List<IExtendedFluidTank> tanks = Collections.singletonList(tank);
            addFluidSlotInfo(fluidConfig, DataType.INPUT, true, alwaysAllow, tanks, tanks, alwaysAllow ? tanks : Collections.emptyList());
            addFluidSlotInfo(fluidConfig, DataType.OUTPUT, alwaysAllow, true, tanks, alwaysAllow ? tanks : Collections.emptyList(), tanks);
            addSharedFluidSlotInfo(fluidConfig, DataType.INPUT_OUTPUT, tanks);
            fluidConfig.fill(DataType.INPUT);
            fluidConfig.setDataType(DataType.OUTPUT, outputSide);
            notifyConfigChangeListeners(TransmissionType.FLUID, null);
        }
        return fluidConfig;
    }

    public ConfigInfo setupFluidDualOutputConfig(IExtendedFluidTank leftOutputTank, IExtendedFluidTank rightOutputTank) {
        ConfigInfo fluidConfig = getConfigInfo(TransmissionType.FLUID);
        if (fluidConfig != null) {
            addFluidSlotInfo(fluidConfig, DataType.OUTPUT_1, Collections.singletonList(leftOutputTank));
            addFluidSlotInfo(fluidConfig, DataType.OUTPUT_2, Collections.singletonList(rightOutputTank));
            notifyConfigChangeListeners(TransmissionType.FLUID, null);
        }
        return fluidConfig;
    }

    private void addItemSlotInfo(ConfigInfo itemConfig, DataType dataType, List<IInventorySlot> slots) {
        itemConfig.addSlotInfo(dataType, createItemSlotInfo(dataType, slots));
    }

    private void addItemSlotInfo(ConfigInfo itemConfig, DataType dataType, boolean input, boolean output, List<IInventorySlot> slots,
          List<IInventorySlot> inputSlots, List<IInventorySlot> outputSlots) {
        itemConfig.addSlotInfo(dataType, createItemSlotInfo(input, output, slots, inputSlots, outputSlots));
    }

    private void addCombinedItemSlotInfo(ConfigInfo itemConfig, DataType dataType, List<IInventorySlot> inputSlots, List<IInventorySlot> outputSlots) {
        List<IInventorySlot> slots = new ArrayList<>(inputSlots.size() + outputSlots.size());
        slots.addAll(inputSlots);
        slots.addAll(outputSlots);
        addItemSlotInfo(itemConfig, dataType, true, true, slots, inputSlots, outputSlots);
    }

    private void addItemExtraSlotCombinations(ConfigInfo itemConfig, List<IInventorySlot> inputSlots, List<IInventorySlot> outputSlots, List<IInventorySlot> extraSlots) {
        List<IInventorySlot> inputAndExtraSlots = new ArrayList<>(inputSlots.size() + extraSlots.size());
        inputAndExtraSlots.addAll(inputSlots);
        inputAndExtraSlots.addAll(extraSlots);
        addItemSlotInfo(itemConfig, DataType.INPUT_ENHANCED, true, false, inputAndExtraSlots, inputAndExtraSlots, Collections.emptyList());
        addItemSlotInfo(itemConfig, DataType.INPUT_EXTRA, true, false, inputAndExtraSlots, inputAndExtraSlots, Collections.emptyList());

        List<IInventorySlot> allSlots = new ArrayList<>(inputAndExtraSlots.size() + outputSlots.size());
        allSlots.addAll(inputAndExtraSlots);
        allSlots.addAll(outputSlots);
        addItemSlotInfo(itemConfig, DataType.INPUT_EXTRA_OUTPUT, true, true, allSlots, inputAndExtraSlots, outputSlots);
    }

    private void addSharedItemSlotInfo(ConfigInfo itemConfig, DataType dataType, List<IInventorySlot> slots) {
        addItemSlotInfo(itemConfig, dataType, true, true, slots, slots, slots);
    }

    private void addGasSlotInfo(ConfigInfo gasConfig, DataType dataType, List<IExtendedGasTank> tanks) {
        gasConfig.addSlotInfo(dataType, createGasSlotInfo(dataType, tanks));
    }

    private void addGasSlotInfo(ConfigInfo gasConfig, DataType dataType, boolean input, boolean output, List<IExtendedGasTank> tanks,
          List<IExtendedGasTank> inputTanks, List<IExtendedGasTank> outputTanks) {
        gasConfig.addSlotInfo(dataType, createGasSlotInfo(input, output, tanks, inputTanks, outputTanks));
    }

    private void addCombinedGasSlotInfo(ConfigInfo gasConfig, DataType dataType, List<IExtendedGasTank> inputTanks, List<IExtendedGasTank> outputTanks) {
        List<IExtendedGasTank> tanks = new ArrayList<>(inputTanks.size() + outputTanks.size());
        tanks.addAll(inputTanks);
        tanks.addAll(outputTanks);
        addGasSlotInfo(gasConfig, dataType, true, true, tanks, inputTanks, outputTanks);
    }

    private void addFluidSlotInfo(ConfigInfo fluidConfig, DataType dataType, List<IExtendedFluidTank> tanks) {
        fluidConfig.addSlotInfo(dataType, createFluidSlotInfo(dataType, tanks));
    }

    private void addFluidSlotInfo(ConfigInfo fluidConfig, DataType dataType, boolean input, boolean output, List<IExtendedFluidTank> tanks,
          List<IExtendedFluidTank> inputTanks, List<IExtendedFluidTank> outputTanks) {
        fluidConfig.addSlotInfo(dataType, createFluidSlotInfo(input, output, tanks, inputTanks, outputTanks));
    }

    private void addCombinedFluidSlotInfo(ConfigInfo fluidConfig, DataType dataType, List<IExtendedFluidTank> inputTanks, List<IExtendedFluidTank> outputTanks) {
        List<IExtendedFluidTank> tanks = new ArrayList<>(inputTanks.size() + outputTanks.size());
        tanks.addAll(inputTanks);
        tanks.addAll(outputTanks);
        addFluidSlotInfo(fluidConfig, dataType, true, true, tanks, inputTanks, outputTanks);
    }

    private void addSharedFluidSlotInfo(ConfigInfo fluidConfig, DataType dataType, List<IExtendedFluidTank> tanks) {
        addFluidSlotInfo(fluidConfig, dataType, true, true, tanks, tanks, tanks);
    }

    private InventorySlotInfo createItemSlotInfo(DataType dataType, List<IInventorySlot> slots) {
        return createItemSlotInfo(dataType, slots, null);
    }

    private InventorySlotInfo createItemSlotInfo(DataType dataType, List<IInventorySlot> slots, @Nullable List<IInventorySlot> outputSlots) {
        boolean input = dataType.canInput();
        boolean output = dataType.canOutput() || isItemContainerData(TransmissionType.ITEM, dataType);
        if (outputSlots == null) {
            return new InventorySlotInfo(input, output, slots);
        }
        return createItemSlotInfo(input, output, slots, input ? subtractItemSlots(slots, outputSlots) : Collections.emptyList(), output ? outputSlots : Collections.emptyList());
    }

    private InventorySlotInfo createItemSlotInfo(boolean input, boolean output, List<IInventorySlot> slots, List<IInventorySlot> inputSlots,
          List<IInventorySlot> outputSlots) {
        return new InventorySlotInfo(input, output, slots, inputSlots, outputSlots);
    }

    private GasSlotInfo createGasSlotInfo(DataType dataType, List<IExtendedGasTank> tanks) {
        return createGasSlotInfo(dataType, tanks, null);
    }

    private GasSlotInfo createGasSlotInfo(DataType dataType, List<IExtendedGasTank> tanks, @Nullable List<IExtendedGasTank> outputTanks) {
        boolean input = dataType.canInput();
        boolean output = dataType.canOutput();
        if (outputTanks == null) {
            return new GasSlotInfo(input, output, tanks);
        }
        return createGasSlotInfo(input, output, tanks, input ? subtractGasTanks(tanks, outputTanks) : Collections.emptyList(),
              output ? outputTanks : Collections.emptyList());
    }

    private GasSlotInfo createGasSlotInfo(boolean input, boolean output, List<IExtendedGasTank> tanks, List<IExtendedGasTank> inputTanks,
          List<IExtendedGasTank> outputTanks) {
        return new GasSlotInfo(input, output, tanks, inputTanks, outputTanks);
    }

    private FluidSlotInfo createFluidSlotInfo(DataType dataType, List<IExtendedFluidTank> tanks) {
        return createFluidSlotInfo(dataType, tanks, null);
    }

    private FluidSlotInfo createFluidSlotInfo(DataType dataType, List<IExtendedFluidTank> tanks, @Nullable List<IExtendedFluidTank> outputTanks) {
        boolean input = dataType.canInput();
        boolean output = dataType.canOutput();
        if (outputTanks == null) {
            return new FluidSlotInfo(input, output, tanks);
        }
        return createFluidSlotInfo(input, output, tanks, input ? subtractFluidTanks(tanks, outputTanks) : Collections.emptyList(),
              output ? outputTanks : Collections.emptyList());
    }

    private FluidSlotInfo createFluidSlotInfo(boolean input, boolean output, List<IExtendedFluidTank> tanks, List<IExtendedFluidTank> inputTanks,
          List<IExtendedFluidTank> outputTanks) {
        return new FluidSlotInfo(input, output, tanks, inputTanks, outputTanks);
    }

    private List<IInventorySlot> subtractItemSlots(List<IInventorySlot> slots, List<IInventorySlot> outputSlots) {
        List<IInventorySlot> inputSlots = new ArrayList<>(slots);
        inputSlots.removeAll(outputSlots);
        return inputSlots;
    }

    private List<IExtendedGasTank> subtractGasTanks(List<IExtendedGasTank> tanks, List<IExtendedGasTank> outputTanks) {
        List<IExtendedGasTank> inputTanks = new ArrayList<>(tanks);
        inputTanks.removeAll(outputTanks);
        return inputTanks;
    }

    private List<IExtendedFluidTank> subtractFluidTanks(List<IExtendedFluidTank> tanks, List<IExtendedFluidTank> outputTanks) {
        List<IExtendedFluidTank> inputTanks = new ArrayList<>(tanks);
        inputTanks.removeAll(outputTanks);
        return inputTanks;
    }

    public void incrementOutput(TransmissionType type, EnumFacing direction) {
        ConfigInfo info = getConfigInfo(type);
        if (info == null) {
            return;
        }
        RelativeSide side = RelativeSide.bydex(direction.ordinal());
        DataType previous = info.getDataType(side);
        if (previous != info.incrementDataType(side)) {
            sideChanged(type, side);
        }
    }

    public void decrementOutput(TransmissionType type, EnumFacing direction) {
        ConfigInfo info = getConfigInfo(type);
        if (info == null) {
            return;
        }
        RelativeSide side = RelativeSide.bydex(direction.ordinal());
        DataType previous = info.getDataType(side);
        if (previous != info.decrementDataType(side)) {
            sideChanged(type, side);
        }
    }

    public boolean clearOutput(TransmissionType type, EnumFacing direction) {
        ConfigInfo info = getConfigInfo(type);
        if (info == null) {
            return false;
        }
        RelativeSide side = RelativeSide.bydex(direction.ordinal());
        if (!info.isSideEnabled(side)) {
            return false;
        }
        boolean changed = info.setDataType(DataType.NONE, side);
        if (changed) {
            sideChanged(type, side);
        }
        return true;
    }

    public boolean isSideEnabled(TransmissionType type, EnumFacing direction) {
        ConfigInfo info = getConfigInfo(type);
        return info != null && info.isSideEnabled(RelativeSide.bydex(direction.ordinal()));
    }

    private void notifyConfigChangeListeners() {
        for (TransmissionType type : transmissions) {
            notifyConfigChangeListeners(type, null);
        }
    }

    private void notifyConfigChangeListeners(TransmissionType type, EnumFacing side) {
        for (Consumer<EnumFacing> listener : configChangeListeners.getOrDefault(type, Collections.emptyList())) {
            listener.accept(side);
        }
    }

    @Override
    public void tick() {
    }

    @Override
    public void read(NBTTagCompound nbtTags) {
        if (nbtTags.getBoolean(SIDE_DATA_STORED)) {
            for (TransmissionType type : transmissions) {
                ConfigInfo info = getConfigInfo(type);
                if (info == null) {
                    continue;
                }
                byte[] sideConfig = nbtTags.getByteArray(CONFIG + type.ordinal());
                readConfig(type, sideConfig);
                info.setEjecting(nbtTags.getBoolean(EJECTING + type.ordinal()));
            }
        }
    }

    @Override
    public void read(ByteBuf dataStream) {
        transmissions.clear();

        int amount = dataStream.readInt();
        for (int i = 0; i < amount; i++) {
            TransmissionType type = MekanismUtils.getByIndex(TransmissionType.values(), dataStream.readInt(), TransmissionType.ITEM);
            transmissions.add(type);
            configInfo.computeIfAbsent(type, ignored -> new ConfigInfo(() -> tileEntity.facing));
        }

        for (TransmissionType type : transmissions) {
            byte[] array = new byte[EnumFacing.VALUES.length];
            dataStream.readBytes(array);
            readConfig(type, array);
            ConfigInfo info = getConfigInfo(type);
            if (info != null) {
                info.setEjecting(dataStream.readBoolean());
            } else {
                dataStream.readBoolean();
            }
        }
    }

    private void readConfig(TransmissionType type, byte[] sideConfig) {
        ConfigInfo info = getConfigInfo(type);
        if (info == null) {
            return;
        }
        for (int i = 0; i < sideConfig.length && i < RelativeSide.SIDES.length; i++) {
            RelativeSide side = RelativeSide.bydex(i);
            int index = sideConfig[i];
            if (index == DataType.EMPTY.ordinal()) {
                info.addDisabledSides(side);
            } else {
                info.setDataType(DataType.byIndexStatic(index), side);
            }
        }
        notifyConfigChangeListeners(type, null);
    }

    @Override
    public void write(NBTTagCompound nbtTags) {
        for (TransmissionType type : transmissions) {
            ConfigInfo info = getConfigInfo(type);
            if (info != null) {
                nbtTags.setByteArray(CONFIG + type.ordinal(), asByteArray(info));
                nbtTags.setBoolean(EJECTING + type.ordinal(), info.isEjecting());
            }
        }
        nbtTags.setBoolean(SIDE_DATA_STORED, true);
    }

    @Override
    public void write(TileNetworkList data) {
        data.add(transmissions.size());
        transmissions.forEach(type -> data.add(type.ordinal()));
        for (TransmissionType type : transmissions) {
            ConfigInfo info = getConfigInfo(type);
            data.add(info == null ? new byte[EnumFacing.VALUES.length] : asByteArray(info));
            data.add(info != null && info.isEjecting());
        }
    }

    public byte[] asByteArray(TransmissionType type) {
        ConfigInfo info = getConfigInfo(type);
        return info == null ? new byte[EnumFacing.VALUES.length] : asByteArray(info);
    }

    private byte[] asByteArray(ConfigInfo info) {
        byte[] data = new byte[EnumFacing.VALUES.length];
        for (int i = 0; i < data.length; i++) {
            RelativeSide side = RelativeSide.bydex(i);
            data[i] = (byte) (info.isSideEnabled(side) ? info.getDataType(side).ordinal() : DataType.EMPTY.ordinal());
        }
        return data;
    }

    @Override
    public void invalidate() {
    }

    @Override
    public List<ISyncableData> getSpecificSyncableData() {
        List<ISyncableData> list = new ArrayList<>();
        for (TransmissionType type : getTransmissions()) {
            ConfigInfo info = getConfigInfo(type);
            if (info != null) {
                list.add(SyncableBoolean.create(info::isEjecting, info::setEjecting));
            }
        }
        return list;
    }

    public static BaseSlotInfo createInfo(TransmissionType type, boolean input, boolean output, Object... containers) {
        return createInfo(type, input, output, Arrays.asList(containers));
    }

    @SuppressWarnings("unchecked")
    public static BaseSlotInfo createInfo(TransmissionType type, boolean input, boolean output, List<?> containers, List<?> outputContainers) {
        List<?> inputContainers = input ? subtractObjects(containers, outputContainers) : Collections.emptyList();
        List<?> extractContainers = output ? outputContainers : Collections.emptyList();
        return switch (type) {
            case ITEM -> new InventorySlotInfo(input, output, (List<IInventorySlot>) containers, (List<IInventorySlot>) inputContainers, (List<IInventorySlot>) extractContainers);
            case FLUID -> new FluidSlotInfo(input, output, (List<IExtendedFluidTank>) containers, (List<IExtendedFluidTank>) inputContainers,
                  (List<IExtendedFluidTank>) extractContainers);
            case GAS -> new GasSlotInfo(input, output, (List<IExtendedGasTank>) containers, (List<IExtendedGasTank>) inputContainers,
                  (List<IExtendedGasTank>) extractContainers);
            default -> createInfo(type, input, output, containers);
        };
    }

    private static List<?> subtractObjects(List<?> containers, List<?> outputContainers) {
        List<Object> inputContainers = new ArrayList<>(containers);
        inputContainers.removeAll(outputContainers);
        return inputContainers;
    }

    @SuppressWarnings("unchecked")
    public static BaseSlotInfo createInfo(TransmissionType type, boolean input, boolean output, List<?> containers) {
        return switch (type) {
            case ITEM -> new InventorySlotInfo(input, output, (List<IInventorySlot>) containers);
            case FLUID -> new FluidSlotInfo(input, output, (List<IExtendedFluidTank>) containers);
            case GAS -> new GasSlotInfo(input, output, (List<IExtendedGasTank>) containers);
            case ENERGY -> new EnergySlotInfo(input, output, (List<IEnergyContainer>) containers);
            case HEAT -> new HeatSlotInfo(input, output, (List<IHeatCapacitor>) containers);
            default -> new BaseSlotInfo(input, output);
        };
    }
}
