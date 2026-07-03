package mekanism.common.base;

import cofh.redstoneflux.api.IEnergyProvider;
import cofh.redstoneflux.api.IEnergyReceiver;
import ic2.api.energy.tile.IEnergySink;
import mekanism.api.IConfigCardAccess.ISpecialConfigData;
import mekanism.api.energy.IStrictEnergyAcceptor;
import mekanism.api.energy.IStrictEnergyOutputter;
import mekanism.api.energy.IStrictEnergyStorage;
import mekanism.common.capabilities.IOffsetCapability;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.integration.computer.IComputerIntegration;
import mekanism.common.security.ISecurityTile;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.common.Optional.Interface;
import net.minecraftforge.fml.common.Optional.InterfaceList;

@InterfaceList({
        @Interface(iface = "cofh.redstoneflux.api.IEnergyProvider", modid = MekanismHooks.REDSTONEFLUX_MOD_ID),
        @Interface(iface = "cofh.redstoneflux.api.IEnergyReceiver", modid = MekanismHooks.REDSTONEFLUX_MOD_ID),
        @Interface(iface = "ic2.api.energy.tile.IEnergySink", modid = MekanismHooks.IC2_MOD_ID),
})
public interface IAdvancedBoundingBlock extends ICapabilityProvider, IBoundingBlock, IInventory, IEnergySink, IStrictEnergyAcceptor, IStrictEnergyOutputter, IStrictEnergyStorage,
        IEnergyReceiver, IEnergyProvider, IComputerIntegration, ISpecialConfigData, ISecurityTile, IOffsetCapability {

    boolean canBoundReceiveEnergy(BlockPos location, EnumFacing side);

    boolean canBoundOutPutEnergy(BlockPos location, EnumFacing side);

    int[] getSlotsForFace(EnumFacing side);

    boolean canInsertItem(int slot, ItemStack stack, EnumFacing side);

    boolean canExtractItem(int slot, ItemStack stack, EnumFacing side);

    default int[] getSlotsForFace(EnumFacing side, Vec3i offset) {
        return getSlotsForFace(side);
    }

    default boolean canInsertItem(int slot, ItemStack stack, EnumFacing side, Vec3i offset) {
        return canInsertItem(slot, stack, side);
    }

    default boolean canExtractItem(int slot, ItemStack stack, EnumFacing side, Vec3i offset) {
        return canExtractItem(slot, stack, side);
    }

    void onPower();

    void onNoPower();
}
