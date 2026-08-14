package mekanism.common.integration.lookingat;

import mekanism.api.energy.IStrictEnergyStorage;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.tile.TileEntityAdvancedBoundingBlock;
import mekanism.common.tile.base.TileEntitySynchronized;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.FluidContainerUtils;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;

public class LookingAtUtils {

    private LookingAtUtils() {
    }


    private static void displayEnergy(LookingAtHelper info, IStrictEnergyStorage energyHandler) {
        info.addEnergyElement(energyHandler.getEnergy(), energyHandler.getMaxEnergy());
    }

    public static void addInfo(LookingAtHelper info, @Nonnull TileEntity tile, boolean displayTanks, boolean displayFluidTanks) {
        IStrictEnergyStorage energyCapability = CapabilityUtils.getCapability(tile, Capabilities.ENERGY_STORAGE_CAPABILITY, null);
        if (energyCapability != null && energyCapability.getMaxEnergy() > 0) {
            displayEnergy(info, energyCapability);
        } else if (tile instanceof TileEntityAdvancedBoundingBlock block && block.getInv() != null && block.getInv().getMaxEnergy() > 0) {
            displayEnergy(info, block.getInv());
        } else if (tile instanceof IStrictEnergyStorage strictEnergyStorage && strictEnergyStorage.getMaxEnergy() > 0) {
            displayEnergy(info, strictEnergyStorage);
        }
        if (displayTanks) {
            if (displayFluidTanks && tile instanceof TileEntitySynchronized) {
                IFluidHandler fluidCapability = CapabilityUtils.getCapability(tile, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
                if (fluidCapability != null) {
                    displayFluid(info, fluidCapability);
                } else if (tile instanceof IFluidHandler handler) {
                    displayFluid(info, handler);
                }
            }
            IGasHandler gasCapability = CapabilityUtils.getCapability(tile, Capabilities.GAS_HANDLER_CAPABILITY, null);
            if (gasCapability != null) {
                displayGas(info, gasCapability);
            } else if (tile instanceof IGasHandler handler) {
                displayGas(info, handler);
            }
        }
    }

    private static void displayFluid(LookingAtHelper info, IFluidHandler fluidHandler) {
        int tanks = FluidContainerUtils.getTankCount(fluidHandler);
        FluidStack[] stored = new FluidStack[tanks];
        int[] capacities = new int[tanks];
        for (int tank = 0; tank < tanks; tank++) {
            stored[tank] = FluidContainerUtils.getFluidInTank(fluidHandler, tank);
            capacities[tank] = FluidContainerUtils.getTankCapacity(fluidHandler, tank);
        }
        info.addFluidElements(stored, capacities, MekanismConfig.current().mekce.LookingAtTankDisplayLimit.val());
    }

    private static void displayGas(LookingAtHelper info, IGasHandler handler) {
        int tanks = GasInventorySlot.getTankCount(handler);
        GasStack[] stored = new GasStack[tanks];
        int[] capacities = new int[tanks];
        for (int tank = 0; tank < tanks; tank++) {
            stored[tank] = GasInventorySlot.getGasInTank(handler, tank);
            capacities[tank] = GasInventorySlot.getTankCapacity(handler, tank);
        }
        info.addChemicalElements(stored, capacities, MekanismConfig.current().mekce.LookingAtTankDisplayLimit.val());
    }


}
