package mekanism.common;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.functions.ConstantPredicates;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import mekanism.common.capabilities.energy.item.RateLimitEnergyHandler.RateLimitEnergyContainer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FreeRunnerEnergyMigrationTest {

    @Test
    void manualConsumptionDoesNotExposeExternalDischarge() {
        RateLimitEnergyContainer container = container(100);

        assertEquals(0, container.extract(50, Action.EXECUTE, AutomationType.EXTERNAL));
        assertEquals(50, container.extract(50, Action.EXECUTE, AutomationType.MANUAL));
        assertEquals(50, container.getEnergy());
    }

    @Test
    void fullEnergyAbsorbsAllFallDamage() {
        RateLimitEnergyContainer container = container(1_000);

        assertEquals(1, CommonPlayerTickHandler.calculateFallDamageAbsorption(container, 10, 1, 50), 0.000_001F);
        assertEquals(500, container.getEnergy());
    }

    @Test
    void partialEnergyAbsorbsOnlyThePaidFraction() {
        RateLimitEnergyContainer container = container(250);

        assertEquals(0.5F, CommonPlayerTickHandler.calculateFallDamageAbsorption(container, 10, 1, 50), 0.000_001F);
        assertEquals(0, container.getEnergy());
    }

    @Test
    void emptyContainerDoesNotAbsorbDamage() {
        RateLimitEnergyContainer container = container(0);

        assertEquals(0, CommonPlayerTickHandler.calculateFallDamageAbsorption(container, 10, 1, 50), 0.000_001F);
    }

    @Test
    void zeroCostAbsorbsDamageWithoutEnergy() {
        RateLimitEnergyContainer container = container(0);

        assertEquals(1, CommonPlayerTickHandler.calculateFallDamageAbsorption(container, 10, 1, 0), 0.000_001F);
    }

    private static RateLimitEnergyContainer container(double stored) {
        RateLimitEnergyContainer container = new RateLimitEnergyContainer(() -> 100, () -> 1_000,
              BasicEnergyContainer.manualOnly, ConstantPredicates.alwaysTrue(), null);
        container.setEnergy(stored);
        return container;
    }
}
