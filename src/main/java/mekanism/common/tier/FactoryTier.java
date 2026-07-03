package mekanism.common.tier;

import mekanism.common.block.states.BlockStateMachine.MachineType;

import java.util.function.Consumer;

public enum FactoryTier implements ITier {
    BASIC(3),
    ADVANCED(5),
    ELITE(7),
    ULTIMATE(9);

    public final int processes;
    private final BaseTier baseTier;

    FactoryTier(int process) {
        processes = process;
        baseTier = BaseTier.values()[ordinal()];
    }

    @Override
    public BaseTier getBaseTier() {
        return baseTier;
    }

    public static void forEnabled(Consumer<FactoryTier> consumer) {
        if (MachineType.BASIC_FACTORY.isEnabled()) {
            consumer.accept(FactoryTier.BASIC);
        }
        if (MachineType.ADVANCED_FACTORY.isEnabled()) {
            consumer.accept(FactoryTier.ADVANCED);
        }
        if (MachineType.ELITE_FACTORY.isEnabled()) {
            consumer.accept(FactoryTier.ELITE);
        }
        if (MachineType.ULTIMATE_FACTORY.isEnabled()) {
            consumer.accept(FactoryTier.ULTIMATE);
        }
    }
}
