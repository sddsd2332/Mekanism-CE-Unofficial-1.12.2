package mekanism.common.tile.laser;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.lasers.ILaserReceptor;
import mekanism.common.LaserManager;
import mekanism.common.LaserManager.LaserInfo;
import mekanism.common.Mekanism;
import mekanism.common.base.IRedstoneControl;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.energy.ProxiedEnergyContainerHolder;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.computer.IComputerIntegration;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;

public class TileEntityLaserAmplifier extends TileEntityContainerBlock implements ILaserReceptor, IRedstoneControl, IEnergyContainer,
        IComputerIntegration, ISecurityTile {

    public static final double MAX_ENERGY = 5E9;
    private static final String[] methods = new String[]{"getEnergy", "getMaxEnergy"};
    public double collectedEnergy = 0;
    public double lastFired = 0;
    public double minThreshold = 0;
    public double maxThreshold = 5E9;
    public int ticks = 0;
    public int time = 0;
    public RedstoneControl controlType = RedstoneControl.DISABLED;
    public boolean on = false;
    public Coord4D digging;
    public double diggingProgress;
    public boolean emittingRedstone;
    public int currentRedstoneLevel;
    public RedstoneOutput outputMode = RedstoneOutput.OFF;
    public TileComponentSecurity securityComponent = new TileComponentSecurity(this);

    public TileEntityLaserAmplifier() {
        super("LaserAmplifier");
        initializeInventorySlots();
    }

    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        return ProxiedEnergyContainerHolder.create(
              side -> false,
              side -> side != null && canOutputEnergy(side),
              side -> side == null || canOutputEnergy(side) ? Collections.singletonList(this) : Collections.emptyList());
    }

    @Override
    public void receiveLaserEnergy(double energy, EnumFacing side) {
        insert(energy, side, Action.EXECUTE, AutomationType.INTERNAL);
    }

    @Override
    public boolean canLasersDig() {
        return false;
    }

    @Override
    public void onUpdateClient() {
        super.onUpdateClient();
        if (on) {
            RayTraceResult mop = LaserManager.fireLaserClient(this, facing, lastFired, world);
            Coord4D hitCoord = mop == null ? null : new Coord4D(mop, world);
            if (hitCoord == null || !hitCoord.equals(digging)) {
                digging = hitCoord;
                diggingProgress = 0;
            }

            if (hitCoord != null) {
                IBlockState blockHit = hitCoord.getBlockState(world);
                TileEntity tileHit = hitCoord.getTileEntity(world);
                float hardness = blockHit.getBlockHardness(world, hitCoord.getPos());

                if (!(hardness < 0 || (LaserManager.isReceptor(tileHit, mop.sideHit) && !LaserManager.getReceptor(tileHit, mop.sideHit).canLasersDig()))) {
                    diggingProgress += lastFired;
                    if (diggingProgress < hardness * MekanismConfig.current().general.laserEnergyNeededPerHardness.val()) {
                        Mekanism.proxy.addHitEffects(hitCoord, mop);
                    }
                }
            }

        }
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        boolean prevRedstone = emittingRedstone;
        emittingRedstone = false;
        if (ticks < time) {
            ticks++;
        } else {
            ticks = 0;
        }
        if (toFire() > 0) {
            double firing = toFire();
            if (!on || firing != lastFired) {
                on = true;
                lastFired = firing;
                Mekanism.packetHandler.sendUpdatePacket(this);
            }
            LaserInfo info = LaserManager.fireLaser(this, facing, firing, world);
            Coord4D hitCoord = info.movingPos == null ? null : new Coord4D(info.movingPos, world);

            if (hitCoord == null || !hitCoord.equals(digging)) {
                digging = hitCoord;
                diggingProgress = 0;
            }

            if (hitCoord != null) {
                IBlockState blockHit = hitCoord.getBlockState(world);
                TileEntity tileHit = hitCoord.getTileEntity(world);
                float hardness = blockHit.getBlockHardness(world, hitCoord.getPos());
                if (!(hardness < 0 || (LaserManager.isReceptor(tileHit, info.movingPos.sideHit) && !LaserManager.getReceptor(tileHit, info.movingPos.sideHit).canLasersDig()))) {
                    diggingProgress += firing;
                    if (diggingProgress >= hardness * MekanismConfig.current().general.laserEnergyNeededPerHardness.val()) {
                        LaserManager.breakBlock(hitCoord, true, world, pos);
                        diggingProgress = 0;
                    }
                }
            }
            emittingRedstone = info.foundEntity;
            extract(firing, Action.EXECUTE, AutomationType.INTERNAL);
        } else if (on) {
            on = false;
            diggingProgress = 0;
            Mekanism.packetHandler.sendUpdatePacket(this);
        }

        if (outputMode != RedstoneOutput.ENTITY_DETECTION) {
            emittingRedstone = false;
        }
        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            markNoUpdateSync();
            currentRedstoneLevel = newRedstoneLevel;
        }
        if (emittingRedstone != prevRedstone) {
            world.notifyNeighborsOfStateChange(getPos(), getBlockType(), true);
        }
    }

    @Override
    public boolean supportsAsync() {
        return false;
    }

    @Override
    public double pullEnergy(EnumFacing side, double amount, boolean simulate) {
        double toGive = extract(amount, Action.SIMULATE, AutomationType.EXTERNAL);
        if (toGive < 0.0001) {
            return 0;
        }
        return extract(toGive, Action.get(!simulate), AutomationType.EXTERNAL);
    }

    @Override
    public double insert(double amount, @Nullable EnumFacing side, Action action, AutomationType automationType) {
        if (automationType != AutomationType.INTERNAL) {
            return amount;
        }
        return IEnergyContainer.super.insert(amount, side, action, automationType);
    }

    @Override
    public double extract(double amount, Action action, AutomationType automationType) {
        if (automationType != AutomationType.INTERNAL && automationType != AutomationType.EXTERNAL) {
            return 0;
        }
        return IEnergyContainer.super.extract(amount, action, automationType);
    }

    @Override
    public boolean canReceiveEnergy(EnumFacing side) {
        return false;
    }

    @Override
    public double getEnergy() {
        return collectedEnergy;
    }

    @Override
    public void setEnergy(double energy) {
        collectedEnergy = Math.max(0, Math.min(energy, MAX_ENERGY));
    }

    public boolean shouldFire() {
        return collectedEnergy >= minThreshold && ticks >= time && MekanismUtils.canFunction(this);
    }

    public double toFire() {
        return shouldFire() ? Math.min(collectedEnergy, maxThreshold) : 0;
    }

    public IEnergyContainer getEnergyContainer() {
        return this;
    }

    public int getDelay() {
        return time;
    }

    public double getMinThreshold() {
        return minThreshold;
    }

    public double getMaxThreshold() {
        return maxThreshold;
    }

    private void setMinThreshold(double threshold) {
        minThreshold = Math.min(MAX_ENERGY, Math.max(0, threshold));
        if (minThreshold > maxThreshold) {
            maxThreshold = minThreshold;
        }
    }

    private void setMaxThreshold(double threshold) {
        maxThreshold = Math.min(MAX_ENERGY, Math.max(0, threshold));
        if (maxThreshold < minThreshold) {
            minThreshold = maxThreshold;
        }
    }

    public RedstoneOutput getOutputMode() {
        return outputMode;
    }

    public int getRedstoneLevel() {
        if (outputMode != RedstoneOutput.ENERGY_CONTENTS) {
            return 0;
        }
        return MekanismUtils.redstoneLevelFromContents(getEnergy(), getMaxEnergy());
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(on);
        data.add(minThreshold);
        data.add(maxThreshold);
        data.add(time);
        data.add(collectedEnergy);
        data.add(lastFired);
        data.add(controlType.ordinal());
        data.add(emittingRedstone);
        data.add(outputMode.ordinal());
        return data;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            switch (dataStream.readInt()) {
                case 0 -> setMinThreshold(MekanismUtils.convertToJoules(dataStream.readDouble()));
                case 1 -> setMaxThreshold(MekanismUtils.convertToJoules(dataStream.readDouble()));
                case 2 -> time = Math.max(0, dataStream.readInt());
                case 3 ->
                        outputMode = RedstoneOutput.values()[outputMode.ordinal() == RedstoneOutput.values().length - 1 ? 0 : outputMode.ordinal() + 1];
                case 4 ->
                        outputMode = RedstoneOutput.values()[outputMode.ordinal() == 0 ? RedstoneOutput.values().length - 1 : outputMode.ordinal() - 1];
            }
            return;
        }

        super.handlePacketData(dataStream);

        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            on = dataStream.readBoolean();
            minThreshold = dataStream.readDouble();
            maxThreshold = dataStream.readDouble();
            time = dataStream.readInt();
            collectedEnergy = dataStream.readDouble();
            lastFired = dataStream.readDouble();
            controlType = MekanismUtils.getByIndex(RedstoneControl.values(), dataStream.readInt(), controlType);
            emittingRedstone = dataStream.readBoolean();
            outputMode = MekanismUtils.getByIndex(RedstoneOutput.values(), dataStream.readInt(), outputMode);
        }
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        on = nbtTags.getBoolean("on");
        setMinThreshold(nbtTags.getDouble("minThreshold"));
        setMaxThreshold(nbtTags.getDouble("maxThreshold"));
        time = Math.max(0, nbtTags.getInteger("time"));
        collectedEnergy = nbtTags.getDouble("collectedEnergy");
        lastFired = nbtTags.getDouble("lastFired");
        controlType = MekanismUtils.getByIndex(RedstoneControl.values(), nbtTags.getInteger("controlType"), controlType);
        outputMode = MekanismUtils.getByIndex(RedstoneOutput.values(), nbtTags.getInteger("outputMode"), outputMode);
    }


    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setBoolean("on", on);
        nbtTags.setDouble("minThreshold", minThreshold);
        nbtTags.setDouble("maxThreshold", maxThreshold);
        nbtTags.setInteger("time", time);
        nbtTags.setDouble("collectedEnergy", collectedEnergy);
        nbtTags.setDouble("lastFired", lastFired);
        nbtTags.setInteger("controlType", controlType.ordinal());
        nbtTags.setInteger("outputMode", outputMode.ordinal());
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
        return true;
    }

    @Override
    public boolean canOutputEnergy(EnumFacing side) {
        return true;
    }

    @Override
    public double getMaxEnergy() {
        return MAX_ENERGY;
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        return switch (method) {
            case 0 -> new Object[]{getEnergy()};
            case 1 -> new Object[]{getMaxEnergy()};
            default -> throw new NoSuchMethodException();
        };
    }

    @Override
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing facing) {
        return capability == Capabilities.LASER_RECEPTOR_CAPABILITY || super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing facing) {
        if (capability == Capabilities.LASER_RECEPTOR_CAPABILITY) {
            return Capabilities.LASER_RECEPTOR_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, facing);
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return InventoryUtils.EMPTY;
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return true;
        }
        return super.isCapabilityDisabled(capability, side);
    }

    public enum RedstoneOutput {
        OFF("off"),
        ENTITY_DETECTION("entityDetection"),
        ENERGY_CONTENTS("energyContents");

        private String unlocalizedName;

        RedstoneOutput(String name) {
            unlocalizedName = name;
        }

        public String getName() {
            return LangUtils.localize("gui." + unlocalizedName);
        }
    }
}
