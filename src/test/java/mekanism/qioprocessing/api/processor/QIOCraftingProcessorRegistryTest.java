package mekanism.qioprocessing.api.processor;

import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOCraftingProcessorRegistryTest {

    @AfterEach
    void cleanup() {
        QIOCraftingProcessorRegistry.resetForTests();
    }

    @Test
    void builtinsUseTheFrozenOneThreeFiveSevenNineLaneContract() {
        QIOCraftingProcessorRegistry.bootstrapBuiltins(Long.MAX_VALUE);
        QIOCraftingProcessorRegistry.freeze();

        assertEquals(5, QIOCraftingProcessorRegistry.getDefinitions().size());
        assertEquals(1, QIOCraftingProcessorRegistry.get(QIOCraftingProcessorRegistry.ORDINARY_ID).getLaneCount());
        assertEquals(3, QIOCraftingProcessorRegistry.get(QIOCraftingProcessorRegistry.BASIC_ID).getLaneCount());
        assertEquals(5, QIOCraftingProcessorRegistry.get(QIOCraftingProcessorRegistry.ADVANCED_ID).getLaneCount());
        assertEquals(7, QIOCraftingProcessorRegistry.get(QIOCraftingProcessorRegistry.ELITE_ID).getLaneCount());
        assertEquals(9, QIOCraftingProcessorRegistry.get(QIOCraftingProcessorRegistry.ULTIMATE_ID).getLaneCount());
        assertEquals(50, QIOCraftingProcessorRegistry.get(
              QIOCraftingProcessorRegistry.ORDINARY_ID).getBaseEnergyUsage());
        assertEquals(900_000, QIOCraftingProcessorRegistry.get(
              QIOCraftingProcessorRegistry.ULTIMATE_ID).getEnergyCapacity());
        assertEquals(8, QIOCraftingProcessorRegistry.get(
              QIOCraftingProcessorRegistry.ULTIMATE_ID).getStackingUpgradeLimit());
        assertTrue(QIOCraftingProcessorRegistry.isFrozen());
    }

    @Test
    void addonDefinitionsRetainLaneCountsAboveIntegerAndAtLongMax() {
        QIOCraftingProcessorRegistry.bootstrapBuiltins(Long.MAX_VALUE);
        long aboveInteger = (long) Integer.MAX_VALUE + 1;
        QIOCraftingProcessorDefinition large = QIOCraftingProcessorRegistry.register(
              new QIOCraftingProcessorDefinition(new ResourceLocation("testaddon", "large"), aboveInteger));
        QIOCraftingProcessorDefinition maximum = QIOCraftingProcessorRegistry.register(
              new QIOCraftingProcessorDefinition(new ResourceLocation("testaddon", "maximum"), Long.MAX_VALUE));

        assertEquals(aboveInteger, large.getLaneCount());
        assertEquals(Long.MAX_VALUE, maximum.getLaneCount());
        assertNotEquals(large.getSignature(), maximum.getSignature());
    }

    @Test
    void configuredLimitDuplicatesAndLateRegistrationsAreRejected() {
        QIOCraftingProcessorRegistry.bootstrapBuiltins(100);
        ResourceLocation id = new ResourceLocation("testaddon", "processor");
        QIOCraftingProcessorRegistry.register(new QIOCraftingProcessorDefinition(id, 100));

        assertThrows(IllegalArgumentException.class, () -> QIOCraftingProcessorRegistry.register(
              new QIOCraftingProcessorDefinition(id, 1)));
        assertThrows(IllegalArgumentException.class, () -> QIOCraftingProcessorRegistry.register(
              new QIOCraftingProcessorDefinition(new ResourceLocation("testaddon", "too_large"), 101)));

        QIOCraftingProcessorRegistry.freeze();
        assertThrows(IllegalStateException.class, () -> QIOCraftingProcessorRegistry.register(
              new QIOCraftingProcessorDefinition(new ResourceLocation("testaddon", "late"), 1)));
    }

    @Test
    void energyAndUpgradeParametersParticipateInDefinitionIdentity() {
        ResourceLocation id = new ResourceLocation("testaddon", "parameterized");
        QIOCraftingProcessorDefinition first = new QIOCraftingProcessorDefinition(id, 4,
              50, 400_000, 8, 8, 8);
        QIOCraftingProcessorDefinition changed = new QIOCraftingProcessorDefinition(id, 4,
              60, 400_000, 8, 8, 8);

        assertNotEquals(first.getSignature(), changed.getSignature());
        assertThrows(IllegalArgumentException.class, () ->
              new QIOCraftingProcessorDefinition(id, 1, 0, 1, 0, 0, 0));
    }
}
