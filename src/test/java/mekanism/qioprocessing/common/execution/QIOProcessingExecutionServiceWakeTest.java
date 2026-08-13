package mekanism.qioprocessing.common.execution;

import mekanism.api.processing.MachinePort;
import mekanism.api.processing.MachineRecipeProvider;
import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.api.processing.MachineRecipeRoute;
import mekanism.api.processing.ProviderConformanceDescriptor;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.common.TestBootstrap;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOProcessingExecutionServiceWakeTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @AfterEach
    void cleanup() {
        QIOProcessingExecutionService.INSTANCE.shutdown();
    }

    @Test
    void machineContentsWakeAlsoRequeuesItsFrequency() {
        UUID frequencyUUID = UUID.randomUUID();
        QIOProcessingExecutionService.INSTANCE.wakeDeviceContents(frequencyUUID,
              UUID.randomUUID());

        assertTrue(QIOProcessingExecutionService.INSTANCE
              .hasPendingProviderWake(frequencyUUID));
    }

    @Test
    void passiveExecutionRejectsAnEndpointWithNoCurrentRoutes() {
        ResourceLocation id = new ResourceLocation("test", "empty_execution_endpoint");
        MachineRecipeProviderRegistry.unregister(id);
        try {
            MachineRecipeProviderRegistry.register(id, EmptyRouteTile.class,
                  new EmptyRouteProvider());
            MachineRecipeProviderRegistry.BoundProvider provider =
                  MachineRecipeProviderRegistry.find(new EmptyRouteTile());
            assertTrue(provider.validateQIOEndpointConformance(
                  QIOAutomationMode.PASSIVE).isConformant());
            assertFalse(QIOProcessingExecutionService.acceptsCurrentRoutes(provider,
                  QIOAutomationMode.PASSIVE));
        } finally {
            MachineRecipeProviderRegistry.unregister(id);
        }
    }

    private static final class EmptyRouteTile extends TileEntity {

        private final BasicInventorySlot input = BasicInventorySlot.at(null, 0, 0);
        private final BasicInventorySlot output = BasicInventorySlot.at(null, 0, 0);
    }

    private static final class EmptyRouteProvider implements
          MachineRecipeProvider<EmptyRouteTile> {

        @Override
        public List<MachineRecipeRoute> getRecipeRoutes(EmptyRouteTile tile) {
            return Collections.emptyList();
        }

        @Override
        public List<MachinePort> getPorts(EmptyRouteTile tile) {
            return Arrays.asList(
                  MachinePort.item("input", MachinePort.Role.INPUT, tile.input),
                  MachinePort.item("output", MachinePort.Role.OUTPUT, tile.output));
        }

        @Override
        public ProviderConformanceDescriptor getQIOConformance(EmptyRouteTile tile) {
            return ProviderConformanceDescriptor.builder("test", "empty_execution")
                  .supports(QIOAutomationMode.PASSIVE).build();
        }
    }
}
