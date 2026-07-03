package mekanism.common.capabilities;

import mekanism.api.*;
import mekanism.api.IConfigCardAccess.ISpecialConfigData;
import mekanism.api.energy.IStrictEnergyAcceptor;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.energy.IStrictEnergyOutputter;
import mekanism.api.energy.IStrictEnergyStorage;
import mekanism.api.gas.IGasHandler;
import mekanism.api.gas.ITubeConnection;
import mekanism.api.lasers.ILaserDissipation;
import mekanism.api.lasers.ILaserReceptor;
import mekanism.api.radiation.capability.IRadiationEntity;
import mekanism.api.radiation.capability.IRadiationShielding;
import mekanism.api.transmitters.IBlockableConnection;
import mekanism.api.transmitters.IGridTransmitter;
import mekanism.common.base.ILogisticalTransporter;
import mekanism.common.base.ITileNetwork;
import mekanism.common.content.gear.mekafishrod.DefaultMekaFishHook;
import mekanism.common.content.gear.mekafishrod.IMekaFishHook;
import mekanism.common.lib.radiation.capability.DefaultRadiationEntity;
import mekanism.common.lib.radiation.capability.DefaultRadiationShielding;
import nc.capability.radiation.resistance.IRadiationResistance;
import net.darkhax.tesla.api.ITeslaConsumer;
import net.darkhax.tesla.api.ITeslaHolder;
import net.darkhax.tesla.api.ITeslaProducer;
import net.minecraft.entity.Entity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;

import java.util.Optional;

/**
 * Created by ben on 30/04/16.
 */
public class Capabilities {

    @CapabilityInject(IStrictEnergyStorage.class)
    public static Capability<IStrictEnergyStorage> ENERGY_STORAGE_CAPABILITY = null;

    @CapabilityInject(IStrictEnergyHandler.class)
    public static Capability<IStrictEnergyHandler> STRICT_ENERGY_CAPABILITY = null;

    @CapabilityInject(IStrictEnergyAcceptor.class)
    public static Capability<IStrictEnergyAcceptor> ENERGY_ACCEPTOR_CAPABILITY = null;

    @CapabilityInject(IStrictEnergyOutputter.class)
    public static Capability<IStrictEnergyOutputter> ENERGY_OUTPUTTER_CAPABILITY = null;

    @CapabilityInject(IConfigurable.class)
    public static Capability<IConfigurable> CONFIGURABLE_CAPABILITY = null;

    @CapabilityInject(IGasHandler.class)
    public static Capability<IGasHandler> GAS_HANDLER_CAPABILITY = null;

    @CapabilityInject(IHeatTransfer.class)
    public static Capability<IHeatTransfer> HEAT_TRANSFER_CAPABILITY = null;

    @CapabilityInject(IBlockableConnection.class)
    public static Capability<IBlockableConnection> BLOCKABLE_CONNECTION_CAPABILITY = null;

    @CapabilityInject(IGridTransmitter.class)
    public static Capability<IGridTransmitter> GRID_TRANSMITTER_CAPABILITY = null;

    @CapabilityInject(IAlloyInteraction.class)
    public static Capability<IAlloyInteraction> ALLOY_INTERACTION_CAPABILITY = null;

    //Kept in for compatibility with any mods that may try to reference it.
    // However, nothing in Mekanism uses this anymore
    @Deprecated
    @CapabilityInject(ITubeConnection.class)
    public static Capability<ITubeConnection> TUBE_CONNECTION_CAPABILITY = null;

    @CapabilityInject(IConfigCardAccess.class)
    public static Capability<IConfigCardAccess> CONFIG_CARD_CAPABILITY = null;

    @CapabilityInject(ISpecialConfigData.class)
    public static Capability<ISpecialConfigData> SPECIAL_CONFIG_DATA_CAPABILITY = null;

    @CapabilityInject(IEvaporationSolar.class)
    public static Capability<IEvaporationSolar> EVAPORATION_SOLAR_CAPABILITY = null;

    @CapabilityInject(ILaserReceptor.class)
    public static Capability<ILaserReceptor> LASER_RECEPTOR_CAPABILITY = null;

    @CapabilityInject(ILaserDissipation.class)
    public static Capability<ILaserDissipation> LASER_DISSIPATION_CAPABILITY = null;

    @CapabilityInject(ITileNetwork.class)
    public static Capability<ITileNetwork> TILE_NETWORK_CAPABILITY = null;

    @CapabilityInject(ILogisticalTransporter.class)
    public static Capability<ILogisticalTransporter> LOGISTICAL_TRANSPORTER_CAPABILITY = null;

    @CapabilityInject(ITeslaConsumer.class)
    public static Capability<ITeslaConsumer> TESLA_CONSUMER_CAPABILITY = null;

    @CapabilityInject(ITeslaProducer.class)
    public static Capability<ITeslaProducer> TESLA_PRODUCER_CAPABILITY = null;

    @CapabilityInject(ITeslaHolder.class)
    public static Capability<ITeslaHolder> TESLA_HOLDER_CAPABILITY = null;


    @CapabilityInject(IRadiationShielding.class)
    public static Capability<IRadiationShielding> RADIATION_SHIELDING_CAPABILITY = null;

    @CapabilityInject(IRadiationEntity.class)
    public static Capability<IRadiationEntity> RADIATION_ENTITY_CAPABILITY = null;

    @CapabilityInject(IRadiationResistance.class)
    public static Capability<IRadiationResistance> NC_CAPABILITY_RADIATION_RESISTANCE = null;

    @CapabilityInject(IMekaFishHook.class)
    public static Capability<IMekaFishHook> MEKA_FISH_HOOK = null;

    public static void registerCapabilities() {
        DefaultStrictEnergyHandler.register();
        DefaultStrictEnergyStorage.register();
        DefaultStrictEnergyAcceptor.register();
        DefaultCableOutputter.register();

        DefaultGridTransmitter.register();
        DefaultLogisticalTransporter.register();
        DefaultBlockableConnection.register();

        DefaultGasHandler.register();
        DefaultTubeConnection.register();

        DefaultConfigurable.register();
        DefaultTileNetwork.register();
        DefaultAlloyInteraction.register();
        DefaultHeatTransfer.register();
        DefaultConfigCardAccess.register();
        DefaultSpecialConfigData.register();
        DefaultEvaporationSolar.register();
        DefaultLaserReceptor.register();
        DefaultLaserDissipation.register();

        DefaultRadiationShielding.register();
        DefaultRadiationEntity.register();
        DefaultMekaFishHook.register();
    }

    public static Optional<IMekaFishHook> getMekaFishCap(Entity entity) {
        if (entity.hasCapability(MEKA_FISH_HOOK, null)) {
            return Optional.ofNullable(entity.getCapability(MEKA_FISH_HOOK, null));
        } else {
            return Optional.empty();
        }
    }
}
