package mekanism.qioprocessing.common.inventory.container;

import mekanism.common.TestBootstrap;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceRule;
import mekanism.qioprocessing.common.terminal.QIOMaintenanceRuleService.MutationStatus;
import mekanism.qioprocessing.common.terminal.QIOPageCursor;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOMaintenanceRuleClientCacheTest {

    private static PortableResourceDescriptor iron;

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
        iron = PortableResourceDescriptor.item(new ItemStack(Items.IRON_INGOT));
    }

    @Test
    void pagesAreContiguousAndMutationInvalidatesTheOldRevision() {
        UUID nonce = UUID.randomUUID();
        QIOMaintenanceRule first = rule();
        QIOMaintenanceRule second = rule();
        QIOMaintenanceRuleClientCache cache = new QIOMaintenanceRuleClientCache();
        QIOPageCursor cursor = new QIOPageCursor(nonce, 2, 1);

        assertTrue(cache.applyPage(nonce, 2, 0, 2,
              Collections.singletonList(first), cursor));
        assertFalse(cache.applyPage(UUID.randomUUID(), 2, 1, 2,
              Collections.singletonList(second), null));
        assertTrue(cache.applyPage(nonce, 2, 1, 2,
              Collections.singletonList(second), null));
        assertEquals(2, cache.getRules().size());

        UUID requestId = UUID.randomUUID();
        assertTrue(cache.applyMutation(nonce, requestId, MutationStatus.APPLIED,
              3, first));
        assertEquals(-1, cache.getSourceRevision());
        assertTrue(cache.getRules().isEmpty());
        assertEquals(requestId, cache.getLastMutationRequestId());
        assertEquals(1, cache.getMutationGeneration());
    }

    @Test
    void delayedOlderFirstPageCannotReplaceNewerRules() {
        UUID nonce = UUID.randomUUID();
        QIOMaintenanceRule newest = rule();
        QIOMaintenanceRuleClientCache cache = new QIOMaintenanceRuleClientCache();

        assertTrue(cache.applyPage(nonce, 9, 0, 1,
              Collections.singletonList(newest), null));
        assertFalse(cache.applyPage(nonce, 8, 0, 0, Collections.emptyList(), null));
        assertEquals(Collections.singletonList(newest), cache.getRules());
        assertEquals(9, cache.getSourceRevision());
    }

    private static QIOMaintenanceRule rule() {
        return new QIOMaintenanceRule(UUID.randomUUID(), iron, UUID.randomUUID(),
              true, 1, 10, 5, 0, 20, 0);
    }
}
