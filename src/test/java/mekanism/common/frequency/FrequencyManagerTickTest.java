package mekanism.common.frequency;

import mekanism.common.TestBootstrap;
import mekanism.common.content.entangloporter.InventoryFrequency;
import mekanism.common.security.ISecurityTile.SecurityMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrequencyManagerTickTest {

    private FrequencyManager<InventoryFrequency> manager;

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @BeforeEach
    void setUp() {
        manager = new FrequencyManager<>(FrequencyType.INVENTORY);
        FrequencyManager.loaded = true;
    }

    @AfterEach
    void tearDown() {
        FrequencyManager.reset();
        FrequencyManager.unregister(manager);
    }

    @Test
    void serverTickProcessesRegisteredFrequencyOnce() {
        AtomicInteger ticks = new AtomicInteger();
        InventoryFrequency frequency = new InventoryFrequency("tick-test", UUID.randomUUID(), SecurityMode.PUBLIC) {
            @Override
            public boolean tick(boolean tickingNormally) {
                ticks.incrementAndGet();
                return false;
            }
        };
        manager.addFrequency(frequency);

        FrequencyManager.tickServer();

        assertEquals(1, ticks.get());
    }
}
