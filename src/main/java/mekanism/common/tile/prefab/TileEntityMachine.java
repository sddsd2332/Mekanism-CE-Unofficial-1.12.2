package mekanism.common.tile.prefab;

import io.netty.buffer.ByteBuf;
import mekanism.api.TileNetworkList;
import mekanism.common.Upgrade;
import mekanism.common.base.IRedstoneControl;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.util.MekanismUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * 基本机器类型
 */

public abstract class TileEntityMachine extends TileEntityEffectsBlock implements IUpgradeTile, IRedstoneControl, ISecurityTile {

    public double prevEnergy;

    public double BASE_ENERGY_PER_TICK;

    public double energyPerTick;
    public TileComponentUpgrade upgradeComponent;
    public TileComponentSecurity securityComponent = new TileComponentSecurity(this);
    /**
     * This machine's current RedstoneControl type.
     */
    private RedstoneControl controlType = RedstoneControl.DISABLED;

    public TileEntityMachine(String sound, String name, double energyStorge,double energUsage,int upgradeSlot) {
        super(sound, name, energyStorge);
        energyPerTick = BASE_ENERGY_PER_TICK = energUsage;
        upgradeComponent = new TileComponentUpgrade(this);
        upgradeComponent.setSupported(Upgrade.MUFFLING);
    }

    public TileEntityMachine(String sound, MachineType type, int upgradeSlot) {
        this(sound, type.getBlockName(), type.getStorage(),type.getUsage(),upgradeSlot);
    }

    @Override
    protected double getMainEnergyPerTick() {
        return energyPerTick;
    }

    @Override
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return facing != EnumFacing.DOWN && facing != EnumFacing.UP;
    }

    @Override
    public boolean renderUpdate() {
        return true;
    }

    @Override
    public boolean lightUpdate() {
        return true;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            controlType = MekanismUtils.getByIndex(RedstoneControl.values(), dataStream.readInt(), controlType);
            energyPerTick = dataStream.readDouble();
            maxEnergy = dataStream.readDouble();
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(controlType.ordinal());
        data.add(energyPerTick);
        data.add(maxEnergy);
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        controlType = MekanismUtils.getByIndex(RedstoneControl.values(), nbtTags.getInteger("controlType"), controlType);
    }


    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("controlType", controlType.ordinal());
    }

    @Override
    public RedstoneControl getControlType() {
        return controlType;
    }

    @Override
    public void setControlType(@Nonnull RedstoneControl type) {
        controlType = Objects.requireNonNull(type);
        MekanismUtils.saveChunk(this);
    }

    @Override
    public boolean canPulse() {
        return false;
    }

    @Override
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    @Override
    public TileComponentUpgrade getComponent() {
        return upgradeComponent;
    }

    @Override
    public void recalculateUpgradables(Upgrade upgrade) {
        super.recalculateUpgradables(upgrade);
        if (upgrade == Upgrade.ENERGY) {
            maxEnergy = MekanismUtils.getMaxEnergy(this, BASE_MAX_ENERGY);
            energyPerTick = MekanismUtils.getBaseEnergyPerTick(this, BASE_ENERGY_PER_TICK);
            setEnergy(Math.min(getMaxEnergy(), getEnergy()));
        }
    }
}
