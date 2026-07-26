package mekanism.common.tile.prefab;

import io.netty.buffer.ByteBuf;
import mekanism.api.TileNetworkList;
import mekanism.common.Upgrade;
import mekanism.common.base.IComparatorSupport;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.util.MekanismUtils;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.Set;

/**
 * 可以操作的机器类型
 */

public abstract class TileEntityOperationalMachine extends TileEntityMachine implements IComparatorSupport {

    public int operatingTicks;

    public int BASE_TICKS_REQUIRED;

    public int ticksRequired;

    protected TileEntityOperationalMachine(String sound, String name, double energyStorge, double energUsage, int upgradeSlot, int baseTicksRequired) {
        super(sound, name, energyStorge, energUsage, upgradeSlot);
        ticksRequired = BASE_TICKS_REQUIRED = baseTicksRequired;
    }

    protected TileEntityOperationalMachine(String sound, MachineType type, int upgradeSlot, int baseTicksRequired) {
        super(sound, type, upgradeSlot);
        ticksRequired = BASE_TICKS_REQUIRED = baseTicksRequired;
    }

    public double getScaledProgress() {
        return Math.max(Math.min((double) operatingTicks / ticksRequired, 1.0D),0.0D);
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            operatingTicks = dataStream.readInt();
            ticksRequired = dataStream.readInt();
        }
    }


    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(operatingTicks);
        data.add(ticksRequired);
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        operatingTicks = nbtTags.getInteger("operatingTicks");
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("operatingTicks", operatingTicks);
    }

    @Override
    public void recalculateUpgradables(Upgrade upgrade) {
        super.recalculateUpgradables(upgrade);
        if (upgrade == Upgrade.SPEED) {
            ticksRequired = MekanismUtils.getTicks(this, BASE_TICKS_REQUIRED);
            if (!isRecalculatingAllUpgradables()) {
                energyPerTick = MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK);
            }
        } else if (upgrade == Upgrade.ENERGY && !isRecalculatingAllUpgradables()) {
            energyPerTick = MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK); // incorporate speed upgrades
        }
    }

    @Override
    protected void onAllUpgradablesRecalculated(Set<Upgrade> upgrades) {
        super.onAllUpgradablesRecalculated(upgrades);
        if (upgrades.contains(Upgrade.SPEED) || upgrades.contains(Upgrade.ENERGY)) {
            energyPerTick = MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK);
        }
    }

    @Override
    public int getRedstoneLevel() {
        return Container.calcRedstoneFromInventory(this);
    }

    public int getTicksRequired() {
        return ticksRequired;
    }
}
