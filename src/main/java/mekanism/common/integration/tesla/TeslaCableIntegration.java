package mekanism.common.integration.tesla;

import mekanism.common.integration.MekanismHooks;
import mekanism.common.tile.transmitter.TileEntityUniversalCable;
import net.darkhax.tesla.api.ITeslaConsumer;
import net.darkhax.tesla.api.ITeslaHolder;
import net.darkhax.tesla.api.ITeslaProducer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.Optional.Interface;
import net.minecraftforge.fml.common.Optional.InterfaceList;
import net.minecraftforge.fml.common.Optional.Method;

@InterfaceList({
        @Interface(iface = "net.darkhax.tesla.api.ITeslaConsumer", modid = MekanismHooks.TESLA_MOD_ID),
        @Interface(iface = "net.darkhax.tesla.api.ITeslaProducer", modid = MekanismHooks.TESLA_MOD_ID),
        @Interface(iface = "net.darkhax.tesla.api.ITeslaHolder", modid = MekanismHooks.TESLA_MOD_ID)
})
public class TeslaCableIntegration implements ITeslaConsumer, ITeslaProducer, ITeslaHolder {

    public TileEntityUniversalCable tileEntity;

    public EnumFacing side;

    public TeslaCableIntegration(TileEntityUniversalCable tile, EnumFacing facing) {
        tileEntity = tile;
        side = facing;
    }

    @Override
    @Method(modid = MekanismHooks.TESLA_MOD_ID)
    public long givePower(long power, boolean simulate) {
        return TeslaIntegration.toTesla(tileEntity.acceptEnergy(side, TeslaIntegration.fromTesla(power), simulate));
    }

    @Override
    @Method(modid = MekanismHooks.TESLA_MOD_ID)
    public long takePower(long power, boolean simulate) {
        return TeslaIntegration.toTesla(tileEntity.pullEnergy(side, TeslaIntegration.fromTesla(power), simulate));
    }

    @Override
    @Method(modid = MekanismHooks.TESLA_MOD_ID)
    public long getStoredPower() {
        return TeslaIntegration.toTesla(tileEntity.getEnergy());
    }

    @Override
    @Method(modid = MekanismHooks.TESLA_MOD_ID)
    public long getCapacity() {
        return TeslaIntegration.toTesla(tileEntity.getMaxEnergy());
    }
}
