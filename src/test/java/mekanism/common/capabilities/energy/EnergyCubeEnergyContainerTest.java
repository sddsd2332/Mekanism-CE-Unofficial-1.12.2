package mekanism.common.capabilities.energy;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.common.TestBootstrap;
import mekanism.common.tier.EnergyCubeTier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnergyCubeEnergyContainerTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void filledCreativeCubeExposesInfiniteSentinel() {
        double[] stored = {Double.MAX_VALUE};
        EnergyCubeEnergyContainer container = create(EnergyCubeTier.CREATIVE, stored);

        assertEquals(Double.MAX_VALUE, container.getEnergy());
        assertEquals(Double.MAX_VALUE, container.getMaxEnergy());
        assertEquals(250, container.extract(250, Action.EXECUTE,
              AutomationType.EXTERNAL));
        assertEquals(Double.MAX_VALUE, stored[0]);
    }

    @Test
    void emptyCreativeCubeVoidsInputWithoutBecomingFilled() {
        double[] stored = {0};
        EnergyCubeEnergyContainer container = create(EnergyCubeTier.CREATIVE, stored);

        assertEquals(0, container.getEnergy());
        assertEquals(Double.MAX_VALUE, container.getMaxEnergy());
        assertEquals(0, container.insert(250, Action.EXECUTE,
              AutomationType.EXTERNAL));
        assertEquals(0, stored[0]);
    }

    @Test
    void nonCreativeCubeKeepsFiniteContainerValues() {
        double[] stored = {1_000};
        EnergyCubeEnergyContainer container = create(EnergyCubeTier.BASIC, stored);

        assertEquals(1_000, container.getEnergy());
        assertEquals(EnergyCubeTier.BASIC.getMaxEnergy(), container.getMaxEnergy());
    }

    private static EnergyCubeEnergyContainer create(EnergyCubeTier tier, double[] stored) {
        return EnergyCubeEnergyContainer.create(() -> tier, () -> stored[0],
              value -> stored[0] = value, null);
    }
}
