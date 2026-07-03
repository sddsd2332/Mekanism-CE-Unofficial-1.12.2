package mekanism.common.tile.laser;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.lasers.ILaserReceptor;
import mekanism.common.LaserManager;
import mekanism.common.LaserManager.LaserInfo;
import mekanism.common.Mekanism;
import mekanism.common.base.IComparatorSupport;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.energy.ProxiedEnergyContainerHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class TileEntityLaserTractorBeam extends TileEntityContainerBlock implements ILaserReceptor, ISecurityTile, IComparatorSupport, IEnergyContainer {

    public static final double MAX_ENERGY = 5E9;
    public double collectedEnergy = 0;
    public double lastFired = 0;
    public boolean on = false;
    public Coord4D digging;
    public double diggingProgress;
    public TileComponentSecurity securityComponent = new TileComponentSecurity(this);

    public TileEntityLaserTractorBeam() {
        super("LaserTractorBeam");
        initializeInventorySlots();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        for (int slotY = 0; slotY < 3; slotY++) {
            for (int slotX = 0; slotX < 9; slotX++) {
                OutputInventorySlot inventorySlot = OutputInventorySlot.at(listener, 8 + slotX * 18, 16 + slotY * 18);
                inventorySlot.setSlotType(ContainerSlotType.NORMAL);
                builder.addSlot(inventorySlot);
            }
        }
        return builder.build();
    }

    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        return ProxiedEnergyContainerHolder.create(
              side -> false,
              side -> false,
              side -> side == null ? Collections.singletonList(this) : Collections.emptyList());
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
        if (collectedEnergy > 0) {
            double firing = collectedEnergy;
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
                        List<ItemStack> drops = LaserManager.breakBlock(hitCoord, false, world, pos);
                        if (drops != null) {
                            receiveDrops(drops);
                        }
                        diggingProgress = 0;
                    }
                }
            }
            extract(firing, Action.EXECUTE, AutomationType.INTERNAL);
        } else if (on) {
            on = false;
            diggingProgress = 0;
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
    }

    @Override
    public boolean supportsAsync() {
        return false;
    }

    public double getEnergy() {
        return collectedEnergy;
    }

    public void setEnergy(double energy) {
        collectedEnergy = Math.max(0, Math.min(energy, MAX_ENERGY));
    }

    @Override
    public double getMaxEnergy() {
        return MAX_ENERGY;
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
        return automationType == AutomationType.INTERNAL ? IEnergyContainer.super.extract(amount, action, automationType) : 0;
    }

    @Override
    public boolean canReceiveEnergy(EnumFacing side) {
        return false;
    }

    @Override
    public boolean canOutputEnergy(EnumFacing side) {
        return false;
    }

    public IEnergyContainer getEnergyContainer() {
        return this;
    }

    public void receiveDrops(List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            ItemStack remaining = drop;
            for (IInventorySlot slot : getInventorySlots(null)) {
                remaining = slot.insertItem(remaining, Action.EXECUTE, AutomationType.INTERNAL);
                if (remaining.isEmpty()) {
                    break;
                }
            }
            if (!remaining.isEmpty()) {
                Block.spawnAsEntity(world, pos, remaining);
            }
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(on);
        data.add(collectedEnergy);
        data.add(lastFired);
        return data;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            on = dataStream.readBoolean();
            collectedEnergy = dataStream.readDouble();
            lastFired = dataStream.readDouble();
        }
    }

    @Override
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        return capability == Capabilities.LASER_RECEPTOR_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (capability == Capabilities.LASER_RECEPTOR_CAPABILITY) {
            return Capabilities.LASER_RECEPTOR_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public int getRedstoneLevel() {
        return Container.calcRedstoneFromInventory(this);
    }
}
