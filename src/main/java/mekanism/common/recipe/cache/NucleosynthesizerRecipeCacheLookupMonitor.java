package mekanism.common.recipe.cache;

import mekanism.api.energy.IEnergyContainer;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.recipe.machines.NucleosynthesizerRecipe;

public class NucleosynthesizerRecipeCacheLookupMonitor extends RecipeCacheLookupMonitor<NucleosynthesizerRecipe> {

    public NucleosynthesizerRecipeCacheLookupMonitor(IRecipeLookupHandler<NucleosynthesizerRecipe> handler) {
        super(handler);
    }

    public double updateAndProcess(IEnergyContainer energyContainer) {
        if (!(energyContainer instanceof MachineEnergyContainer machineEnergyContainer)) {
            return 0;
        }
        double prev = energyContainer.getEnergy();
        if (updateAndProcess()) {
            double energyPerTick = machineEnergyContainer.getEnergyPerTick();
            if (energyPerTick > 0) {
                int toProcess = (int) Math.sqrt(prev / energyPerTick);
                for (int i = 0; i < toProcess - 1; i++) {
                    cachedRecipe.process();
                }
            }
            return Math.max(0, prev - energyContainer.getEnergy());
        }
        return 0;
    }
}
