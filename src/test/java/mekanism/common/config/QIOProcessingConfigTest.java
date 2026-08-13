package mekanism.common.config;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.init.Bootstrap;
import net.minecraftforge.common.config.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOProcessingConfigTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void defaultsUseTheMekanismOptionContract() {
        QIOProcessingConfig config = new QIOProcessingConfig();

        assertEquals(5, config.planningTimePerTick.val());
        assertEquals(32, config.planningResultsPerTick.val());
        assertEquals(1_200, config.previewTimeoutTicks.val());
        assertEquals(8, config.previewsPerPlayer.val());
        assertEquals(1_024, config.nonTerminalJobsPerFrequency.val());
        assertEquals(128, config.executionActionsPerTick.val());
        assertEquals(8, config.slotGrantsPerTick.val());
        assertEquals(false, config.limitWorkbenchThreadsPerJob.val());
        assertEquals(8, config.maxWorkbenchThreadsPerJob.val());
        assertEquals(false, config.limitMachineThreadsPerJob.val());
        assertEquals(8, config.maxMachineThreadsPerJob.val());
        assertEquals(64, config.executionSlotsPerFrequency.val());
        assertEquals(32, config.claimRefreshesPerTick.val());
        assertEquals(Long.MAX_VALUE, config.processorLaneLimit.val());
        assertEquals(1_024, config.automaticOutputTransferLimit.val());
        assertEquals(100, config.maintenanceEvaluationInterval.val());
        assertEquals(16, config.maintenanceGroupsPerTick.val());
        assertEquals(4_096, config.maintenanceRulesPerFrequency.val());
        assertEquals(1_000_000, config.maintenanceBatchCap.val());
        assertEquals(65_536, config.deviceRecordsPerFrequency.val());
        assertEquals(65_536, config.providerRoutesPerFrequency.val());
        assertEquals(128, config.terminalPageSize.val());
    }

    @Test
    void existingQioConfigCategoriesLoadAndNormalizeThroughBaseConfig() {
        Configuration forgeConfig = new Configuration();
        forgeConfig.get("planning", "planningTimePerTick", 5).set(0);
        forgeConfig.get("planning", "planningResultsPerTick", 32).set(77);
        forgeConfig.get("scheduling", "limitWorkbenchThreadsPerJob", false).set(false);
        forgeConfig.get("scheduling", "maxWorkbenchThreadsPerJob", 8).set(0);
        forgeConfig.get("scheduling", "limitMachineThreadsPerJob", false).set(false);
        forgeConfig.get("scheduling", "maxMachineThreadsPerJob", 8).set(0);
        forgeConfig.get("scheduling", "executionSlotsPerFrequency", 64).set(2);
        forgeConfig.get("claims", "claimRefreshesPerTick", 32).set(0);
        forgeConfig.get("processors", "processorLaneLimit", Long.toString(Long.MAX_VALUE))
              .set("8");
        forgeConfig.get("automation", "automaticOutputTransferLimit", "1024")
              .set("0");
        forgeConfig.get("maintenance", "evaluationInterval", 100).set(0);
        forgeConfig.get("maintenance", "groupsPerTick", 16).set(0);
        forgeConfig.get("maintenance", "rulesPerFrequency", 4_096).set(0);
        forgeConfig.get("maintenance", "batchCap", "1000000").set("0");
        forgeConfig.get("devices", "recordsPerFrequency", 65_536).set(0);
        forgeConfig.get("providers", "routesPerFrequency", 65_536).set(0);
        forgeConfig.get("terminals", "maximumPageSize", 128).set(1);

        QIOProcessingConfig config = new QIOProcessingConfig();
        config.load(forgeConfig);

        assertEquals(1, config.planningTimePerTick.val());
        assertEquals(77, config.planningResultsPerTick.val());
        assertEquals(false, config.limitWorkbenchThreadsPerJob.val());
        assertEquals(1, config.maxWorkbenchThreadsPerJob.val());
        assertEquals(false, config.limitMachineThreadsPerJob.val());
        assertEquals(1, config.maxMachineThreadsPerJob.val());
        assertEquals(QIOProcessingConfig.MIN_EXECUTION_SLOTS,
              config.executionSlotsPerFrequency.val());
        assertEquals(1, config.claimRefreshesPerTick.val());
        assertEquals(9, config.processorLaneLimit.val());
        assertEquals(1, config.automaticOutputTransferLimit.val());
        assertEquals(20, config.maintenanceEvaluationInterval.val());
        assertEquals(1, config.maintenanceGroupsPerTick.val());
        assertEquals(1, config.maintenanceRulesPerFrequency.val());
        assertEquals(1, config.maintenanceBatchCap.val());
        assertEquals(1, config.deviceRecordsPerFrequency.val());
        assertEquals(1, config.providerRoutesPerFrequency.val());
        assertEquals(QIOProcessingConfig.MIN_TERMINAL_PAGE_SIZE,
              config.terminalPageSize.val());
        assertEquals("9", forgeConfig.getCategory("processors")
              .get("processorLaneLimit").getString());
        assertTrue(forgeConfig.hasCategory("planning"));
        assertTrue(forgeConfig.hasCategory("scheduling"));
        assertTrue(forgeConfig.hasCategory("claims"));
        assertTrue(forgeConfig.hasCategory("processors"));
        assertTrue(forgeConfig.hasCategory("automation"));
        assertTrue(forgeConfig.hasCategory("maintenance"));
        assertTrue(forgeConfig.hasCategory("devices"));
        assertTrue(forgeConfig.hasCategory("providers"));
        assertTrue(forgeConfig.hasCategory("terminals"));
    }

    @Test
    void moduleValuesUseMekanismConfigNetworkSerialization() throws Exception {
        assertEquals(QIOProcessingConfig.class,
              MekanismConfig.class.getField("qioProcessing").getType());
        QIOProcessingConfig config = new QIOProcessingConfig();
        assertNotNull(config);
        config.planningTimePerTick.set(7);
        config.planningResultsPerTick.set(45);
        config.previewTimeoutTicks.set(600);
        config.previewsPerPlayer.set(5);
        config.nonTerminalJobsPerFrequency.set(2_048);
        config.executionActionsPerTick.set(90);
        config.slotGrantsPerTick.set(6);
        config.limitWorkbenchThreadsPerJob.set(false);
        config.maxWorkbenchThreadsPerJob.set(4);
        config.limitMachineThreadsPerJob.set(false);
        config.maxMachineThreadsPerJob.set(12);
        config.executionSlotsPerFrequency.set(128);
        config.claimRefreshesPerTick.set(17);
        config.processorLaneLimit.set((long) Integer.MAX_VALUE + 50);
        config.automaticOutputTransferLimit.set(8_192);
        config.maintenanceEvaluationInterval.set(200);
        config.maintenanceGroupsPerTick.set(24);
        config.maintenanceRulesPerFrequency.set(8_192);
        config.maintenanceBatchCap.set(2_000_000);
        config.deviceRecordsPerFrequency.set(12_345);
        config.providerRoutesPerFrequency.set(23_456);
        config.terminalPageSize.set(256);
        ByteBuf buffer = Unpooled.buffer();
        config.write(buffer);

        QIOProcessingConfig restored = new QIOProcessingConfig();
        restored.read(buffer);

        assertEquals(7, restored.planningTimePerTick.val());
        assertEquals(45, restored.planningResultsPerTick.val());
        assertEquals(600, restored.previewTimeoutTicks.val());
        assertEquals(5, restored.previewsPerPlayer.val());
        assertEquals(2_048, restored.nonTerminalJobsPerFrequency.val());
        assertEquals(90, restored.executionActionsPerTick.val());
        assertEquals(6, restored.slotGrantsPerTick.val());
        assertEquals(false, restored.limitWorkbenchThreadsPerJob.val());
        assertEquals(4, restored.maxWorkbenchThreadsPerJob.val());
        assertEquals(false, restored.limitMachineThreadsPerJob.val());
        assertEquals(12, restored.maxMachineThreadsPerJob.val());
        assertEquals(128, restored.executionSlotsPerFrequency.val());
        assertEquals(17, restored.claimRefreshesPerTick.val());
        assertEquals((long) Integer.MAX_VALUE + 50, restored.processorLaneLimit.val());
        assertEquals(8_192, restored.automaticOutputTransferLimit.val());
        assertEquals(200, restored.maintenanceEvaluationInterval.val());
        assertEquals(24, restored.maintenanceGroupsPerTick.val());
        assertEquals(8_192, restored.maintenanceRulesPerFrequency.val());
        assertEquals(2_000_000, restored.maintenanceBatchCap.val());
        assertEquals(12_345, restored.deviceRecordsPerFrequency.val());
        assertEquals(23_456, restored.providerRoutesPerFrequency.val());
        assertEquals(256, restored.terminalPageSize.val());
    }

    @Test
    void networkValuesAreDefensivelyClamped() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(0);
        buffer.writeInt(-1);
        buffer.writeInt(0);
        buffer.writeInt(0);
        buffer.writeInt(0);
        buffer.writeInt(0);
        buffer.writeInt(0);
        buffer.writeBoolean(false);
        buffer.writeInt(0);
        buffer.writeBoolean(false);
        buffer.writeInt(0);
        buffer.writeInt(2);
        buffer.writeInt(0);
        buffer.writeLong(8);
        buffer.writeLong(0);
        buffer.writeInt(0);
        buffer.writeInt(0);
        buffer.writeInt(0);
        buffer.writeLong(0);
        buffer.writeInt(0);
        buffer.writeInt(0);
        buffer.writeInt(Integer.MAX_VALUE);
        QIOProcessingConfig restored = new QIOProcessingConfig();

        restored.read(buffer);

        assertEquals(1, restored.planningTimePerTick.val());
        assertEquals(1, restored.planningResultsPerTick.val());
        assertEquals(20, restored.previewTimeoutTicks.val());
        assertEquals(1, restored.previewsPerPlayer.val());
        assertEquals(8, restored.nonTerminalJobsPerFrequency.val());
        assertEquals(1, restored.executionActionsPerTick.val());
        assertEquals(1, restored.slotGrantsPerTick.val());
        assertEquals(false, restored.limitWorkbenchThreadsPerJob.val());
        assertEquals(1, restored.maxWorkbenchThreadsPerJob.val());
        assertEquals(false, restored.limitMachineThreadsPerJob.val());
        assertEquals(1, restored.maxMachineThreadsPerJob.val());
        assertEquals(QIOProcessingConfig.MIN_EXECUTION_SLOTS,
              restored.executionSlotsPerFrequency.val());
        assertEquals(1, restored.claimRefreshesPerTick.val());
        assertEquals(9, restored.processorLaneLimit.val());
        assertEquals(1, restored.automaticOutputTransferLimit.val());
        assertEquals(20, restored.maintenanceEvaluationInterval.val());
        assertEquals(1, restored.maintenanceGroupsPerTick.val());
        assertEquals(1, restored.maintenanceRulesPerFrequency.val());
        assertEquals(1, restored.maintenanceBatchCap.val());
        assertEquals(1, restored.deviceRecordsPerFrequency.val());
        assertEquals(1, restored.providerRoutesPerFrequency.val());
        assertEquals(QIOProcessingConfig.MAX_TERMINAL_PAGE_SIZE,
              restored.terminalPageSize.val());
    }
}
