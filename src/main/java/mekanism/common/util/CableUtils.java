package mekanism.common.util;

import cofh.redstoneflux.api.IEnergyConnection;
import cofh.redstoneflux.api.IEnergyProvider;
import cofh.redstoneflux.api.IEnergyReceiver;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.Coord4D;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IStrictEnergyAcceptor;
import mekanism.api.energy.IStrictEnergyOutputter;
import mekanism.api.transmitters.IGridTransmitter;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.base.EnergyAcceptorWrapper;
import mekanism.common.base.IEnergyWrapper;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.content.network.distribution.EnergyAcceptorTarget;
import mekanism.common.integration.ic2.IC2Integration;
import mekanism.common.tile.multiblock.TileEntityInductionPort;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.Collection;
import java.util.List;

public final class CableUtils {

    private static final double MAX_DOUBLE_SAFE_TRANSFER = 1L << 40;

    public static boolean isCable(TileEntity tileEntity) {
        IGridTransmitter gridTransmitter = CapabilityUtils.getCapability(tileEntity, Capabilities.GRID_TRANSMITTER_CAPABILITY, null);
        if (gridTransmitter != null) {
            return TransmissionType.checkTransmissionType(gridTransmitter, TransmissionType.ENERGY);
        }
        return false;
    }

    /**
     * Gets the adjacent connections to a TileEntity, from a subset of its sides.
     *
     * @param cableEntity - TileEntity that's trying to connect
     * @param side        - side to check
     * @return boolean whether the acceptor is valid
     */
    public static boolean isValidAcceptorOnSide(TileEntity cableEntity, TileEntity tile, EnumFacing side) {
        if (tile == null || isCable(tile)) {
            return false;
        }
        return isAcceptor(cableEntity, tile, side) || isOutputter(cableEntity, tile, side) ||
                (MekanismUtils.useRF() && tile instanceof IEnergyConnection connection && connection.canConnectEnergy(side.getOpposite())) ||
                (MekanismUtils.useForge() && CapabilityUtils.hasCapability(tile, CapabilityEnergy.ENERGY, side.getOpposite()));
    }

    /**
     * Gets all the connected cables around a specific tile entity.
     *
     * @param tileEntity - center tile entity
     * @return TileEntity[] of connected cables
     */
    public static TileEntity[] getConnectedOutputters(TileEntity tileEntity) {
        return getConnectedOutputters(tileEntity, tileEntity.getPos(), tileEntity.getWorld());
    }

    public static TileEntity[] getConnectedOutputters(BlockPos pos, World world) {
        return getConnectedOutputters(MekanismUtils.getTileEntity(world, pos), pos, world);
    }

    public static TileEntity[] getConnectedOutputters(TileEntity source, BlockPos pos, World world) {
        TileEntity[] connected = new TileEntity[6];
        for (EnumFacing orientation : EnumFacing.VALUES) {
            final TileEntity outputter = MekanismUtils.getTileEntity(world, pos.offset(orientation));
            if (isOutputter(source, outputter, orientation)) {
                connected[orientation.ordinal()] = outputter;
            }
        }
        return connected;
    }

    public static TileEntity[] getConnectedTileEntities(TileEntity source, BlockPos pos, World world) {
        TileEntity[] connected = new TileEntity[6];
        for (EnumFacing orientation : EnumFacing.VALUES) {
            final TileEntity te = MekanismUtils.getTileEntity(world, pos.offset(orientation));
            connected[orientation.ordinal()] = te;
        }
        return connected;
    }

    public static TileEntity[] getConnectedTileEntities(List<EnumFacing> sides, BlockPos pos, World world) {
        TileEntity[] connected = new TileEntity[6];
        for (EnumFacing orientation : sides) {
            final TileEntity te = MekanismUtils.getTileEntity(world, pos.offset(orientation));
            connected[orientation.ordinal()] = te;
        }
        return connected;
    }

    public static boolean isOutputter(TileEntity source, TileEntity tileEntity, EnumFacing side) {
        if (tileEntity == null) {
            return false;
        }

        EnumFacing opposite = side.getOpposite();

        IStrictEnergyOutputter outputter = CapabilityUtils.getCapability(tileEntity, Capabilities.ENERGY_OUTPUTTER_CAPABILITY, opposite);
        if (outputter != null && outputter.canOutputEnergy(opposite)) {
            return true;
        }
        if (MekanismUtils.useTesla() && CapabilityUtils.hasCapability(tileEntity, Capabilities.TESLA_PRODUCER_CAPABILITY, opposite)) {
            return true;
        }
        IEnergyStorage forgeStorage;
        if (MekanismUtils.useForge() && (forgeStorage = CapabilityUtils.getCapability(tileEntity, CapabilityEnergy.ENERGY, opposite)) != null) {
            return forgeStorage.canExtract();
        }
        if (MekanismUtils.useRF() && tileEntity instanceof IEnergyProvider provider&& provider.canConnectEnergy(opposite)) {
            return true;
        }
        return MekanismUtils.useIC2() && IC2Integration.isOutputter(tileEntity, side);

    }

    public static boolean isAcceptor(TileEntity source, TileEntity tileEntity, EnumFacing side) {
        if (CapabilityUtils.hasCapability(tileEntity, Capabilities.GRID_TRANSMITTER_CAPABILITY, side.getOpposite())) {
            return false;
        }
        IStrictEnergyAcceptor strictEnergyAcceptor;
        if ((strictEnergyAcceptor = CapabilityUtils.getCapability(tileEntity, Capabilities.ENERGY_ACCEPTOR_CAPABILITY, side.getOpposite())) != null) {
            return strictEnergyAcceptor.canReceiveEnergy(side.getOpposite());
        }
        if (MekanismUtils.useTesla() && CapabilityUtils.hasCapability(tileEntity, Capabilities.TESLA_CONSUMER_CAPABILITY, side.getOpposite())) {
            return true;
        }
        IEnergyStorage energyStorage;
        if (MekanismUtils.useForge() && (energyStorage = CapabilityUtils.getCapability(tileEntity, CapabilityEnergy.ENERGY, side.getOpposite())) != null) {
            return energyStorage.canReceive();
        }
        if (MekanismUtils.useRF() && tileEntity instanceof IEnergyReceiver receiver) {
            return receiver.canConnectEnergy(side.getOpposite());
        }
        return MekanismUtils.useIC2() && IC2Integration.isAcceptor(tileEntity, side);
    }

    public static void emit(IEnergyWrapper emitter) {
        emit(emitter, 1);
    }

    public static void emit(Collection<EnumFacing> sides, IEnergyWrapper emitter) {
        TileEntity tileEntity = (TileEntity) emitter;
        if (tileEntity.getWorld().isRemote || !MekanismUtils.canFunction(tileEntity)) {
            return;
        }

        double energyToSend = Math.min(emitter.getEnergy(), emitter.getMaxOutput());
        if (!(energyToSend > 0)) {
            return;
        }

        Coord4D coord = Coord4D.get(tileEntity);
        EnergyAcceptorTarget target = new EnergyAcceptorTarget();
        for (EnumFacing side : sides) {
            TileEntity tile = coord.offset(side).getTileEntity(tileEntity.getWorld());
            if (tile != null && (isValidAcceptorOnSide(tileEntity, tile, side) || isCable(tile))) {
                EnumFacing opposite = side.getOpposite();
                EnergyAcceptorWrapper acceptor = EnergyAcceptorWrapper.get(tile, opposite);
                if (acceptor != null && acceptor.canReceiveEnergy(opposite) && acceptor.needsEnergy(opposite)) {
                    target.addHandler(opposite, acceptor);
                }
            }
        }

        if (target.getHandlerCount() > 0) {
            double sent = EmitUtils.sendToAcceptors(target, energyToSend);
            if (emitter instanceof TileEntityInductionPort port) {
                port.removeEnergy(sent, false);
            } else {
                emitter.setEnergy(emitter.getEnergy() - sent);
            }
        }
    }

    public static void emit(Collection<EnumFacing> sides, IEnergyContainer container, TileEntity tileEntity) {
        emit(sides, container, tileEntity, container.getMaxEnergy());
    }

    public static void emit(Collection<EnumFacing> sides, IEnergyContainer container, TileEntity tileEntity, double maxOutput) {
        if (tileEntity.getWorld().isRemote || !MekanismUtils.canFunction(tileEntity) || container.isEmpty()) {
            return;
        }

        double energyToSend = container.extract(clampEnergyTransfer(maxOutput), null, Action.SIMULATE, AutomationType.INTERNAL);
        if (!(energyToSend > 0)) {
            return;
        }

        Coord4D coord = Coord4D.get(tileEntity);
        EnergyAcceptorTarget target = new EnergyAcceptorTarget();
        for (EnumFacing side : sides) {
            TileEntity tile = coord.offset(side).getTileEntity(tileEntity.getWorld());
            if (tile != null && (isValidAcceptorOnSide(tileEntity, tile, side) || isCable(tile))) {
                EnumFacing opposite = side.getOpposite();
                EnergyAcceptorWrapper acceptor = EnergyAcceptorWrapper.get(tile, opposite);
                if (acceptor != null && acceptor.canReceiveEnergy(opposite) && acceptor.needsEnergy(opposite)) {
                    target.addHandler(opposite, acceptor);
                }
            }
        }

        if (target.getHandlerCount() > 0) {
            double sent = EmitUtils.sendToAcceptors(target, energyToSend);
            container.extract(sent, null, Action.EXECUTE, AutomationType.INTERNAL);
        }
    }

    private static double clampEnergyTransfer(double amount) {
        if (!(amount > 0)) {
            return 0;
        }
        return Math.min(amount, MAX_DOUBLE_SAFE_TRANSFER);
    }

    public static void emit(IEnergyWrapper emitter, int i) {
        TileEntity tileEntity = (TileEntity) emitter;
        if (tileEntity.getWorld().isRemote || !MekanismUtils.canFunction(tileEntity)) {
            return;
        }

        double energyToSend = Math.min(emitter.getEnergy(), emitter.getMaxOutput());
        if (!(energyToSend > 0)) {
            return;
        }

        Coord4D coord = Coord4D.get(tileEntity);
        //Fake that we have one target given we know that no sides will overlap
        // This allows us to have slightly better performance
        EnergyAcceptorTarget target = new EnergyAcceptorTarget();
        for (EnumFacing side : EnumFacing.VALUES) {
            if (!emitter.sideIsOutput(side)) {
                continue;
            }
            TileEntity tile = coord.offset(side, i).getTileEntity(tileEntity.getWorld());
            //If it can accept energy or it is a cable
            if (tile != null && (isValidAcceptorOnSide(tileEntity, tile, side) || isCable(tile))) {
                //Get the opposite side as the current side is relative to us
                EnumFacing opposite = side.getOpposite();
                EnergyAcceptorWrapper acceptor = EnergyAcceptorWrapper.get(tile, opposite);
                if (acceptor != null && acceptor.canReceiveEnergy(opposite) && acceptor.needsEnergy(opposite)) {
                    target.addHandler(opposite, acceptor);
                }
            }
        }

        if (target.getHandlerCount() > 0) {
            double sent = EmitUtils.sendToAcceptors(target, energyToSend);
            if (emitter instanceof TileEntityInductionPort port) {
                //Streamline sideless removal method for induction port.
                port.removeEnergy(sent, false);
            } else {
                emitter.setEnergy(emitter.getEnergy() - sent);
            }
        }
    }
}
