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
            IFluidHandler fluidHandler = getFluidHandler(tile, displayFluidTanks);
            IGasHandler gasHandler = getGasHandler(tile);
            int fluidTanks = fluidHandler == null ? 0 : FluidContainerUtils.getTankCount(fluidHandler);
            int gasTanks = gasHandler == null ? 0 : GasInventorySlot.getTankCount(gasHandler);
            int[] displayLimits = getTankDisplayLimits(fluidTanks, gasTanks);
            if (fluidTanks > 0) {
                displayFluid(info, fluidHandler, fluidTanks, displayLimits[0]);
            }
            if (gasTanks > 0) {
                displayGas(info, gasHandler, gasTanks, displayLimits[1]);
            }
        }
    }

    private static IFluidHandler getFluidHandler(TileEntity tile, boolean displayFluidTanks) {
        if (!displayFluidTanks || !(tile instanceof TileEntitySynchronized)) {
            return null;
        }
        IFluidHandler fluidCapability = CapabilityUtils.getCapability(tile, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
        if (fluidCapability != null) {
            return fluidCapability;
        }
        return tile instanceof IFluidHandler handler ? handler : null;
    }

    private static IGasHandler getGasHandler(TileEntity tile) {
        IGasHandler gasCapability = CapabilityUtils.getCapability(tile, Capabilities.GAS_HANDLER_CAPABILITY, null);
        if (gasCapability != null) {
            return gasCapability;
        }
        return tile instanceof IGasHandler handler ? handler : null;
    }

    private static int[] getTankDisplayLimits(int fluidTanks, int gasTanks) {
        int totalLimit = Math.max(2, MekanismConfig.current().mekce.LookingAtTankDisplayLimit.val());
        if (fluidTanks == 0) {
            return new int[]{0, Math.min(gasTanks, totalLimit)};
        } else if (gasTanks == 0) {
            return new int[]{Math.min(fluidTanks, totalLimit), 0};
        }
        int fluidLimit = Math.min(fluidTanks, totalLimit / 2);
        int gasLimit = Math.min(gasTanks, totalLimit - fluidLimit);
        int remaining = totalLimit - fluidLimit - gasLimit;
        fluidLimit += Math.min(fluidTanks - fluidLimit, remaining);
        return new int[]{fluidLimit, gasLimit};
    }

    private static void displayFluid(LookingAtHelper info, IFluidHandler fluidHandler, int tanks, int maxDisplayed) {
        FluidStack[] stored = new FluidStack[tanks];
        int[] capacities = new int[tanks];
        for (int tank = 0; tank < tanks; tank++) {
            stored[tank] = FluidContainerUtils.getFluidInTank(fluidHandler, tank);
            capacities[tank] = FluidContainerUtils.getTankCapacity(fluidHandler, tank);
        }
        info.addFluidElements(stored, capacities, maxDisplayed);
    }

    private static void displayGas(LookingAtHelper info, IGasHandler handler, int tanks, int maxDisplayed) {
        GasStack[] stored = new GasStack[tanks];
        int[] capacities = new int[tanks];
        for (int tank = 0; tank < tanks; tank++) {
            stored[tank] = GasInventorySlot.getGasInTank(handler, tank);
            capacities[tank] = GasInventorySlot.getTankCapacity(handler, tank);
        }
        info.addChemicalElements(stored, capacities, maxDisplayed);
    }


}
