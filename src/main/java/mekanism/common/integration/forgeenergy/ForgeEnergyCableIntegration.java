package mekanism.common.integration.forgeenergy;

import mekanism.common.tile.transmitter.TileEntityUniversalCable;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.energy.IEnergyStorage;

public class ForgeEnergyCableIntegration implements IEnergyStorage {

    public TileEntityUniversalCable tileEntity;

    public EnumFacing side;

    public ForgeEnergyCableIntegration(TileEntityUniversalCable tile, EnumFacing facing) {
        tileEntity = tile;
        side = facing;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        return ForgeEnergyIntegration.toForge(tileEntity.acceptEnergy(side, ForgeEnergyIntegration.fromForge(maxReceive), simulate));
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return ForgeEnergyIntegration.toForge(tileEntity.pullEnergy(side, ForgeEnergyIntegration.fromForge(maxExtract), simulate));
    }

    @Override
    public int getEnergyStored() {
        return ForgeEnergyIntegration.toForge(tileEntity.getEnergy());
    }

    @Override
    public int getMaxEnergyStored() {
        return ForgeEnergyIntegration.toForge(tileEntity.getMaxEnergy());
    }

    @Override
    public boolean canExtract() {
        return tileEntity.canOutputEnergy(side);
    }

    @Override
    public boolean canReceive() {
        return tileEntity.canReceiveEnergy(side);
    }
}
