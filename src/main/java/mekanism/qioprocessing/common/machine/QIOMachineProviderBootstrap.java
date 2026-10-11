package mekanism.qioprocessing.common.machine;

import mekanism.api.processing.MachineRecipeProviderRegistry;

/**
 * Installs built-in machine route providers exactly once per JVM.
 */
public final class QIOMachineProviderBootstrap {
    private static boolean registered;

    private QIOMachineProviderBootstrap() {
    }

    public static synchronized void register() {
        if (registered) return;
        MachineRecipeProviderRegistry.register(QIOElectricMachineProvider.ID,
                mekanism.common.tile.prefab.TileEntityElectricMachine.class,
                new QIOElectricMachineProvider());
        MachineRecipeProviderRegistry.register(QIODoubleElectricMachineProvider.ID,
                mekanism.common.tile.prefab.TileEntityDoubleElectricMachine.class,
                new QIODoubleElectricMachineProvider());
        MachineRecipeProviderRegistry.register(QIOChemicalInfuserProvider.ID,
                mekanism.common.tile.machine.TileEntityChemicalInfuser.class,
                new QIOChemicalInfuserProvider());
        MachineRecipeProviderRegistry.register(QIOElectrolyticSeparatorProvider.ID,
                mekanism.common.tile.machine.TileEntityElectrolyticSeparator.class,
                new QIOElectrolyticSeparatorProvider());
        registered = true;
    }
}

