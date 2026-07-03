package mekanism.common.base;

import cofh.redstoneflux.api.IEnergyReceiver;
import ic2.api.energy.EnergyNet;
import ic2.api.energy.tile.IEnergySink;
import ic2.api.energy.tile.IEnergyTile;
import mekanism.api.Coord4D;
import mekanism.api.energy.IStrictEnergyAcceptor;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.integration.fluxnetworks.FluxPlugAcceptor;
import mekanism.common.integration.forgeenergy.ForgeEnergyIntegration;
import mekanism.common.integration.ic2.IC2Integration;
import mekanism.common.integration.redstoneflux.RFIntegration;
import mekanism.common.integration.tesla.TeslaIntegration;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.MekanismUtils;
import net.darkhax.tesla.api.ITeslaConsumer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import sonar.fluxnetworks.common.tileentity.TileFluxPlug;

import java.util.function.Function;

public abstract class EnergyAcceptorWrapper implements IStrictEnergyAcceptor {

    private static final Logger LOGGER = LogManager.getLogger("Mekanism EnergyAcceptorWrapper");
    public Coord4D coord;

    public static EnergyAcceptorWrapper get(TileEntity tileEntity, EnumFacing side) {
        if (tileEntity == null || tileEntity.getWorld() == null) {
            return null;
        }
        EnergyAcceptorWrapper wrapper = getAccepterWrapper(tileEntity, side);

        if (wrapper != null) {
            wrapper.coord = Coord4D.get(tileEntity);
        }

        return wrapper;
    }

    private static @Nullable EnergyAcceptorWrapper getAccepterWrapper(final TileEntity tileEntity, final EnumFacing side) {
        EnergyAcceptorWrapper wrapper = fromCapability(tileEntity, Capabilities.ENERGY_ACCEPTOR_CAPABILITY, side, MekanismAcceptor::new);
        if (wrapper != null) {
            return wrapper;
        }
        if (MekanismUtils.useTesla()) {
            wrapper = fromCapability(tileEntity, Capabilities.TESLA_CONSUMER_CAPABILITY, side, TeslaAcceptor::new);
        }
        if (wrapper != null) {
            return wrapper;
        }
        if (MekanismUtils.useForge()) {
            wrapper = fromCapability(tileEntity, CapabilityEnergy.ENERGY, side, ForgeAcceptor::new);
        }
        if (wrapper != null) {
            return wrapper;
        }
        if (MekanismUtils.useRF() && tileEntity instanceof IEnergyReceiver energyReceiver) {
            wrapper = new RFAcceptor(energyReceiver);
        }
        if (wrapper != null) {
            return wrapper;
        }
        if (MekanismUtils.useIC2()) {
            IEnergyTile tile = EnergyNet.instance.getSubTile(tileEntity.getWorld(), tileEntity.getPos());
            if (tile instanceof IEnergySink sink) {
                wrapper = new IC2Acceptor(sink);
            }
        }
        if (wrapper != null) {
            return wrapper;
        }
        if (MekanismUtils.useFlux() && tileEntity instanceof TileFluxPlug plug) {
            wrapper = new FluxPlugAcceptor(plug, side);
        }
        return wrapper;
    }

    @Nullable
    private static <T> EnergyAcceptorWrapper fromCapability(TileEntity tileEntity, Capability<T> capability, EnumFacing side, Function<T, EnergyAcceptorWrapper> makeAcceptor) {
        T acceptor = CapabilityUtils.getCapability(tileEntity, capability, side);
        if (acceptor != null) {
            return makeAcceptor.apply(acceptor);
        }
        return null;
    }

    public abstract boolean needsEnergy(EnumFacing side);

    public static class MekanismAcceptor extends EnergyAcceptorWrapper {

        private IStrictEnergyAcceptor acceptor;

        public MekanismAcceptor(IStrictEnergyAcceptor mekAcceptor) {
            acceptor = mekAcceptor;
        }

        @Override
        public double acceptEnergy(EnumFacing side, double amount, boolean simulate) {
            return acceptor.acceptEnergy(side, amount, simulate);
        }

        @Override
        public boolean canReceiveEnergy(EnumFacing side) {
            return acceptor.canReceiveEnergy(side);
        }

        @Override
        public boolean needsEnergy(EnumFacing side) {
            return acceptor.acceptEnergy(side, 1, true) > 0;
        }
    }

    public static class RFAcceptor extends EnergyAcceptorWrapper {

        private IEnergyReceiver acceptor;

        public RFAcceptor(IEnergyReceiver rfAcceptor) {
            acceptor = rfAcceptor;
        }

        @Override
        public double acceptEnergy(EnumFacing side, double amount, boolean simulate) {
            return RFIntegration.fromRF(acceptor.receiveEnergy(side, RFIntegration.toRF(amount), simulate));
        }

        @Override
        public boolean canReceiveEnergy(EnumFacing side) {
            return acceptor.canConnectEnergy(side);
        }

        @Override
        public boolean needsEnergy(EnumFacing side) {
            return acceptor.receiveEnergy(side, 1, true) > 0;
        }
    }

    public static class IC2Acceptor extends EnergyAcceptorWrapper {

        private IEnergySink acceptor;

        public IC2Acceptor(IEnergySink ic2Acceptor) {
            acceptor = ic2Acceptor;
        }

        @Override
        public double acceptEnergy(EnumFacing side, double amount, boolean simulate) {
            double sinkVoltage = IC2Integration.getPowerFromTier(acceptor.getSinkTier());
            double toTransfer = Math.min(Math.min(acceptor.getDemandedEnergy(), IC2Integration.toEU(amount)), sinkVoltage);
            if (simulate) {
                //IC2 has no built in way to simulate, so we have to calculate it ourselves
                return IC2Integration.fromEU(toTransfer);
            }
            double rejects = acceptor.injectEnergy(side, toTransfer, sinkVoltage);
            return IC2Integration.fromEU(toTransfer - rejects);
        }

        @Override
        public boolean canReceiveEnergy(EnumFacing side) {
            return acceptor.acceptsEnergyFrom(null, side);
        }

        @Override
        public boolean needsEnergy(EnumFacing side) {
            return acceptor.getDemandedEnergy() > 0;
        }
    }

    public static class TeslaAcceptor extends EnergyAcceptorWrapper {

        private ITeslaConsumer acceptor;

        public TeslaAcceptor(ITeslaConsumer teslaConsumer) {
            acceptor = teslaConsumer;
        }

        @Override
        public double acceptEnergy(EnumFacing side, double amount, boolean simulate) {
            return TeslaIntegration.fromTesla(acceptor.givePower(TeslaIntegration.toTesla(amount), simulate));
        }

        @Override
        public boolean canReceiveEnergy(EnumFacing side) {
            return acceptor.givePower(1, true) > 0;
        }

        @Override
        public boolean needsEnergy(EnumFacing side) {
            return canReceiveEnergy(side);
        }
    }

    public static class ForgeAcceptor extends EnergyAcceptorWrapper {

        private IEnergyStorage acceptor;

        public ForgeAcceptor(IEnergyStorage forgeConsumer) {
            acceptor = forgeConsumer;
        }

        @Override
        public double acceptEnergy(EnumFacing side, double amount, boolean simulate) {
            return ForgeEnergyIntegration.fromForge(acceptor.receiveEnergy(ForgeEnergyIntegration.toForge(amount), simulate));
        }

        @Override
        public boolean canReceiveEnergy(EnumFacing side) {
            return acceptor.canReceive();
        }

        @Override
        public boolean needsEnergy(EnumFacing side) {
            return acceptor.receiveEnergy(1, true) > 0;
        }
    }


}
