package mekanism.qioprocessing.common.terminal;

import mekanism.common.TestBootstrap;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceRule;
import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceRuleMutation;
import mekanism.qioprocessing.common.terminal.QIOMaintenanceRuleService.MutationResult;
import mekanism.qioprocessing.common.terminal.QIOMaintenanceRuleService.MutationStatus;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QIOMaintenanceRuleServiceTest {

    private static PortableResourceDescriptor iron;

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
        iron = PortableResourceDescriptor.item(new ItemStack(Items.IRON_INGOT));
    }

    @Test
    void createUpdateDeleteUseCatalogAndRuleCompareAndSetRevisions() {
        UUID frequencyUUID = UUID.randomUUID();
        UUID editor = UUID.randomUUID();
        QIOProcessingNetworkData network = network(frequencyUUID);
        QIOProcessingTerminalSession session = session(frequencyUUID,
              QIOProcessingTerminalType.MAINTENANCE, 4);

        MutationResult created = QIOMaintenanceRuleService.mutate(session, network, 4,
              0, QIOMaintenanceRuleMutation.create(iron, true, 10, 100, 50, 3, 40),
              editor, 1);
        assertEquals(MutationStatus.APPLIED, created.getStatus());
        assertEquals(1, created.getRulesRevision());
        QIOMaintenanceRule rule = created.getAuthoritativeRule();
        assertNotNull(rule);

        MutationResult staleCatalog = QIOMaintenanceRuleService.mutate(session, network, 4,
              0, QIOMaintenanceRuleMutation.update(rule.getRuleId(),
                    rule.getRuleRevision(), false, 20, 200, 70, 5, 60), editor, 2);
        assertEquals(MutationStatus.REVISION_CONFLICT, staleCatalog.getStatus());
        assertEquals(rule.getRuleId(), staleCatalog.getAuthoritativeRule().getRuleId());

        MutationResult updated = QIOMaintenanceRuleService.mutate(session, network, 4,
              1, QIOMaintenanceRuleMutation.update(rule.getRuleId(),
                    rule.getRuleRevision(), false, 20, 200, 70, 5, 60), editor, 2);
        assertEquals(MutationStatus.APPLIED, updated.getStatus());
        assertEquals(2, updated.getRulesRevision());
        assertEquals(1, updated.getAuthoritativeRule().getRuleRevision());
        assertEquals(200, updated.getAuthoritativeRule().getTargetAmount());

        MutationResult staleRule = QIOMaintenanceRuleService.mutate(session, network, 4,
              2, QIOMaintenanceRuleMutation.delete(rule.getRuleId(), 0), editor, 3);
        assertEquals(MutationStatus.INVALID_TARGET, staleRule.getStatus());
        assertNotNull(staleRule.getAuthoritativeRule());

        MutationResult deleted = QIOMaintenanceRuleService.mutate(session, network, 4,
              2, QIOMaintenanceRuleMutation.delete(rule.getRuleId(), 1), editor, 3);
        assertEquals(MutationStatus.APPLIED, deleted.getStatus());
        assertEquals(3, deleted.getRulesRevision());
        assertNull(deleted.getAuthoritativeRule());
        assertNull(network.getMaintenanceRules().get(rule.getRuleId()));
    }

    @Test
    void createForConfiguredResourceAtomicallyUpdatesExistingRule() {
        UUID frequencyUUID = UUID.randomUUID();
        UUID editor = UUID.randomUUID();
        QIOProcessingNetworkData network = network(frequencyUUID);
        QIOProcessingTerminalSession session = session(frequencyUUID,
              QIOProcessingTerminalType.MAINTENANCE, 4);

        MutationResult first = QIOMaintenanceRuleService.mutate(session, network, 4,
              0, QIOMaintenanceRuleMutation.create(iron, true, 10, 10, 50, 0, 100),
              editor, 1);
        MutationResult second = QIOMaintenanceRuleService.mutate(session, network, 4,
              first.getRulesRevision(), QIOMaintenanceRuleMutation.create(iron, true,
                    200, 200, 64, 0, 100), editor, 2);

        assertEquals(MutationStatus.APPLIED, second.getStatus());
        assertEquals(first.getAuthoritativeRule().getRuleId(),
              second.getAuthoritativeRule().getRuleId());
        assertEquals(1, network.getMaintenanceRules().getRules().size());
        assertEquals(200, second.getAuthoritativeRule().getTargetAmount());
        assertEquals(64, second.getAuthoritativeRule().getMaximumSingleRequest());
    }

    @Test
    void pagingIsRevisionBoundAndOtherTerminalTypesCannotReadRules() {
        UUID frequencyUUID = UUID.randomUUID();
        QIOProcessingNetworkData network = network(frequencyUUID);
        network.createMaintenanceRule(iron, UUID.randomUUID(), true,
              1, 10, 5, 0, 20, 0, 8);
        network.createMaintenanceRule(iron, UUID.randomUUID(), true,
              2, 20, 5, 0, 20, 0, 8);
        QIOProcessingTerminalSession session = session(frequencyUUID,
              QIOProcessingTerminalType.MAINTENANCE, 2);

        QIOPage<QIOMaintenanceRule> first = QIOMaintenanceRuleService.getPage(
              session, network, 2, null, 1);
        assertEquals(1, first.getEntries().size());
        assertEquals(2, first.getTotalSize());
        network.createMaintenanceRule(iron, UUID.randomUUID(), true,
              3, 30, 5, 0, 20, 0, 8);
        assertThrows(IllegalStateException.class, () ->
              QIOMaintenanceRuleService.getPage(session, network, 2,
                    first.getNextCursor(), 1));
        assertThrows(SecurityException.class, () ->
              QIOMaintenanceRuleService.getPage(session(frequencyUUID,
                    QIOProcessingTerminalType.MANAGEMENT, 2), network, 2,
                    null, 1));
    }

    private static QIOProcessingNetworkData network(UUID frequencyUUID) {
        return new QIOProcessingNetworkData(frequencyUUID,
              new QIOFrequencyIdentitySnapshot("maintenance", null,
                    SecurityMode.PUBLIC));
    }

    private static QIOProcessingTerminalSession session(UUID frequencyUUID,
          QIOProcessingTerminalType type, long accessRevision) {
        return new QIOProcessingTerminalSession(UUID.randomUUID(),
              QIOProcessingTerminalSession.TargetKind.BLOCK, type,
              UUID.randomUUID(), 0, frequencyUUID, accessRevision);
    }
}
