package mekanism.common.integration.lookingat;

import mekanism.api.gas.GasStack;
import net.minecraftforge.fluids.FluidStack;

public interface LookingAtHelper {

    void addText(String text);

    void addEnergyElement(double energy, double maxEnergy);

    void addFluidElement(FluidStack stored, int capacity);

    void addChemicalElement(GasStack stored, int capacity);

    default void addFluidElements(FluidStack[] stored, int[] capacities, int maxDisplayed) {
        for (int tank = 0; tank < stored.length; tank++) {
            addFluidElement(stored[tank], capacities[tank]);
        }
    }

    default void addChemicalElements(GasStack[] stored, int[] capacities, int maxDisplayed) {
        for (int tank = 0; tank < stored.length; tank++) {
            addChemicalElement(stored[tank], capacities[tank]);
        }
    }
}
