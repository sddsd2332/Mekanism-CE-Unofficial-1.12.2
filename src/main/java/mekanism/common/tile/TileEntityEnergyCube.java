package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.IConfigCardAccess;
import mekanism.api.IContentsListener;
import mekanism.api.TileNetworkList;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.Mekanism;
import mekanism.common.base.*;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.energy.EnergyCubeEnergyContainer;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.integration.computer.IComputerIntegration;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.EnergyCubeTier;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityElectricBlock;
import mekanism.common.upgrade.EnergyCubeUpgradeData;
import mekanism.common.upgrade.IUpgradeData;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TileEntityEnergyCube extends TileEntityElectricBlock implements IComputerIntegration, IRedstoneControl, ISideConfiguration, ISecurityTile, IUpgradeableTile,
        IBaseTierProvider,
        IConfigCardAccess, IComparatorSupport, ISpecialSelectionWireframeTile {

    private static final ISpecialSelectionWireframeTile.SelectionTransform[] SELECTION_ROTATE_SOUTH = {
            ISpecialSelectionWireframeTile.SelectionTransform.rotateY(180, 0.5D, 1.5D, 0.5D)
    };
    private static final ISpecialSelectionWireframeTile.SelectionTransform[] SELECTION_ROTATE_WEST = {
            ISpecialSelectionWireframeTile.SelectionTransform.rotateY(90, 0.5D, 1.5D, 0.5D)
    };
    private static final ISpecialSelectionWireframeTile.SelectionTransform[] SELECTION_ROTATE_EAST = {
            ISpecialSelectionWireframeTile.SelectionTransform.rotateY(270, 0.5D, 1.5D, 0.5D)
    };
    private static final ISpecialSelectionWireframeTile.SelectionTransform[] SELECTION_ROTATE_UP = {
            ISpecialSelectionWireframeTile.SelectionTransform.translate(0, 1.0D, 1.0D),
            ISpecialSelectionWireframeTile.SelectionTransform.rotateX(90, 0.5D, 1.5D, 0.5D)
    };
    private static final ISpecialSelectionWireframeTile.SelectionTransform[] SELECTION_ROTATE_DOWN = {
            ISpecialSelectionWireframeTile.SelectionTransform.translate(0, 1.0D, -1.0D),
            ISpecialSelectionWireframeTile.SelectionTransform.rotateX(-90, 0.5D, 1.5D, 0.5D)
    };

    private static final String[] methods = new String[]{"getEnergy", "getOutput", "getMaxEnergy", "getEnergyNeeded"};
    /**
     * This Energy Cube's tier.
     */
    public EnergyCubeTier tier = EnergyCubeTier.BASIC;
    /**
     * The redstone level this Energy Cube is outputting at.
     */
    public int currentRedstoneLevel;
    /**
     * This machine's current RedstoneControl type.
     */
    public RedstoneControl controlType;
    public int prevScale;
    public TileComponentEjector ejectorComponent;
    public TileComponentConfig configComponent;
    public TileComponentSecurity securityComponent;
    private EnergyCubeEnergyContainer energyContainer;
    private EnergyInventorySlot chargeSlot;
    private EnergyInventorySlot dischargeSlot;

    /**
     * A block used to store and transfer electricity.
     */
    public TileEntityEnergyCube() {
        super("EnergyCube", 0);
        configComponent = new TileComponentConfig(this, TransmissionType.ENERGY, TransmissionType.ITEM);
        initializeInventorySlots();
        configComponent.setupItemIOConfig(chargeSlot, dischargeSlot);

        configComponent.setConfig(TransmissionType.ITEM, DataType.NONE, DataType.NONE, DataType.NONE, DataType.NONE, DataType.OUTPUT, DataType.INPUT);
        configComponent.setCanEject(TransmissionType.ITEM, false);
        configComponent.setIOConfig(TransmissionType.ENERGY);
        configComponent.setEjecting(TransmissionType.ENERGY, true);

        controlType = RedstoneControl.DISABLED;

        ejectorComponent = new TileComponentEjector(this, () -> tier.getOutput(), false);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ENERGY)
              .setCanEject(type -> MekanismUtils.canFunction(this));

        securityComponent = new TileComponentSecurity(this);
    }

    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        EnergyContainerHelper builder = createEnergyContainerHelper();
        builder.addContainer(getEnergyContainer(listener));
        return builder.build();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        EnergyCubeEnergyContainer container = getEnergyContainer(listener);
        dischargeSlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(container, this::getWorld, listener, 17, 35));
        dischargeSlot.setSlotOverlay(SlotOverlay.MINUS);
        chargeSlot = builder.addSlot(EnergyInventorySlot.drain(container, listener, 143, 35));
        chargeSlot.setSlotOverlay(SlotOverlay.PLUS);
        return builder.build();
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        chargeSlot.drainContainer();
        dischargeSlot.fillContainerOrConvert();
        int newScale = getScaledEnergyLevel(20);
        if (newScale != prevScale) {
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
        prevScale = newScale;
    }

    @Override
    public boolean supportsAsync() {
        return false;
    }

    @Override
    public boolean canInstallUpgrade(BaseTier upgradeTier) {
        if (upgradeTier.ordinal() != tier.ordinal() + 1) {
            return false;
        }
        return upgradeTier.ordinal() < EnergyCubeTier.values().length;
    }

    @Override
    public BaseTier getBaseTier() {
        return tier.getBaseTier();
    }

    @Nullable
    @Override
    public IUpgradeData getUpgradeData(BaseTier upgradeTier) {
        if (!canInstallUpgrade(upgradeTier)) {
            return null;
        }
        return new EnergyCubeUpgradeData(upgradeTier, facing, clientFacing, ticker, redstone, redstoneLastTick, doAutoSync, getEnergy(),
              currentRedstoneLevel, getControlType(), writeUpgradeComponentData(), chargeSlot.serializeNBT(), dischargeSlot.serializeNBT());
    }

    @Nonnull
    private NBTTagCompound writeUpgradeComponentData() {
        NBTTagCompound componentData = new NBTTagCompound();
        configComponent.write(componentData);
        ejectorComponent.write(componentData);
        securityComponent.write(componentData);
        return componentData;
    }

    @Override
    public boolean parseUpgradeData(IUpgradeData upgradeData) {
        if (upgradeData instanceof EnergyCubeUpgradeData data && data.getUpgradeTier().ordinal() == tier.ordinal() + 1) {
            facing = data.facing;
            clientFacing = data.clientFacing;
            ticker = data.ticker;
            redstone = data.redstone;
            redstoneLastTick = data.redstoneLastTick;
            doAutoSync = data.doAutoSync;
            tier = EnergyCubeTier.values()[data.getUpgradeTier().ordinal()];
            electricityStored.set(Math.max(Math.min(data.energy, getMaxEnergy()), 0));
            currentRedstoneLevel = data.currentRedstoneLevel;
            setControlType(data.controlType);
            configComponent.read(data.componentData);
            ejectorComponent.read(data.componentData);
            securityComponent.read(data.componentData);
            ejectorComponent.setOutputData(configComponent, TransmissionType.ENERGY);
            chargeSlot.deserializeNBT(data.chargeSlot);
            dischargeSlot.deserializeNBT(data.dischargeSlot);
            MekanismUtils.updateBlock(world, getPos());
            Mekanism.packetHandler.sendUpdatePacket(this);
            markNoUpdateSync();
            return true;
        }
        return false;
    }

    @Nonnull
    @Override
    public String getName() {
        return LangUtils.localize("tile.EnergyCube" + tier.getBaseTier().getSimpleName() + ".name");
    }

    @Override
    public double getMaxOutput() {
        return tier.getOutput();
    }

    public EnergyCubeEnergyContainer getEnergyContainer() {
        return getEnergyContainer(this);
    }

    private EnergyCubeEnergyContainer getEnergyContainer(@Nullable IContentsListener listener) {
        if (energyContainer == null) {
            energyContainer = EnergyCubeEnergyContainer.create(() -> tier, this::getEnergy, this::setEnergyUnchecked, listener);
        }
        return energyContainer;
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
    public double getMaxEnergy() {
        return tier.getMaxEnergy();
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        return switch (method) {
            case 0 -> new Object[]{getEnergy()};
            case 1 -> new Object[]{tier.getOutput()};
            case 2 -> new Object[]{getMaxEnergy()};
            case 3 -> new Object[]{(getMaxEnergy() - getEnergy())};
            default -> throw new NoSuchMethodException();
        };
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            EnergyCubeTier prevTier = tier;
            tier = MekanismUtils.getByIndex(EnergyCubeTier.values(), dataStream.readInt(), tier);
            controlType = MekanismUtils.getByIndex(RedstoneControl.values(), dataStream.readInt(), controlType);
            if (prevTier != tier) {
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(tier.ordinal());
        data.add(controlType.ordinal());
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        tier = MekanismUtils.getByIndex(EnergyCubeTier.values(), nbtTags.getInteger("tier"), tier);
        controlType = MekanismUtils.getByIndex(RedstoneControl.values(), nbtTags.getInteger("controlType"), controlType);
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("tier", tier.ordinal());
        nbtTags.setInteger("controlType", controlType.ordinal());
    }

    @Override
    public void setEnergy(double energy) {
        if (tier == EnergyCubeTier.CREATIVE) {
            double max = getMaxEnergy();
            energy = getEnergy() > 0 || energy >= max ? max : 0;
        }
        setEnergyUnchecked(energy);
    }

    private void setEnergyUnchecked(double energy) {
        super.setEnergy(energy);
        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            markNoUpdateSync();
            currentRedstoneLevel = newRedstoneLevel;
        }
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(getEnergy(), getMaxEnergy());
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
    public double acceptEnergy(EnumFacing side, double amount, boolean simulate) {
        return tryCallContainerTransaction(() -> {
            if (side != null && !canInsertExternalEnergy(side)) {
                return 0D;
            }
            Action action = Action.get(!simulate);
            double remainder = getEnergyContainer().insert(amount, side, action, mekanism.api.AutomationType.handler(side));
            trackEnergyInput(amount, action, remainder);
            return amount - remainder;
        }, () -> 0D);
    }

    @Override
    public double pullEnergy(EnumFacing side, double amount, boolean simulate) {
        return tryCallContainerTransaction(() -> {
            if (side != null && !canExtractExternalEnergy(side)) {
                return 0D;
            }
            return getEnergyContainer().extract(amount, side, Action.get(!simulate), mekanism.api.AutomationType.handler(side));
        }, () -> 0D);
    }

    @Override
    public TileComponentEjector getEjector() {
        return ejectorComponent;
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
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        return capability == Capabilities.CONFIG_CARD_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        //Special isCapabilityDisabled override not needed here as it already gets handled in TileEntityElectricBlock
        if (capability == Capabilities.CONFIG_CARD_CAPABILITY) {
            return Capabilities.CONFIG_CARD_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public int getBlockGuiID(Block block, int metadata) {
        return 8;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Class<?> getSelectionWireframeModelClass() {
        return mekanism.client.model.ModelEnergyCube.class;
    }

    @Override
    public String[] getSelectionWireframeSideArrayFieldNames() {
        return new String[]{"connectors", "ports"};
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
            case UP -> SELECTION_ROTATE_UP;
            case DOWN -> SELECTION_ROTATE_DOWN;
            default -> ISpecialSelectionWireframeTile.SelectionTransform.EMPTY;
        };
    }

    @Override
    public boolean shouldRenderSelectionWireframeSide(EnumFacing side, IBlockState state, IBlockAccess world, BlockPos pos) {
        if (configComponent == null) {
            return false;
        }
        return configComponent.getDataType(TransmissionType.ENERGY, side) != DataType.NONE;
    }
}
