package mekanism.qioprocessing.common.content.maintenance;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.plan.QIOPlanSourceRevisions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static scale guardrails for large maintenance-rule catalogs. */
class QIOMaintenanceRuleCatalogScaleTest {

    @Test
    void targetedWakeDoesNotResetAnInProgressAuditCursor() {
        QIOMaintenanceRuleCatalog catalog = new QIOMaintenanceRuleCatalog();
        UUID creator = UUID.randomUUID();
        PortableResourceDescriptor first = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "stress:first", 0, null);
        PortableResourceDescriptor second = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "stress:second", 0, null);
        catalog.create(first, creator, true, 10, 10, 50, 0, 100, 0, 8);
        catalog.create(second, creator, true, 10, 10, 50, 0, 100, 0, 8);
        catalog.advanceCursor(2, 1, 10, 100);

        assertEquals(1, catalog.getEvaluationCursor());
        assertTrue(catalog.requestTargetedEvaluation(first));
        assertEquals(first, catalog.pollTargetedEvaluation());
        assertTrue(catalog.requestEvaluationPass());
        assertEquals(1, catalog.getEvaluationCursor());
        catalog.advanceCursor(2, 1, 11, 100);
        assertEquals(0, catalog.getNextEvaluationTick());

        long generation = catalog.getEvaluationGeneration();
        assertEquals(generation + 1, catalog.reserveEvaluationGeneration());
    }

    @Test
    void tenThousandRulesAndFourThousandPendingResourcesStayIndexedUnderFiveSeconds() {
        assertTimeout(Duration.ofSeconds(5), () -> {
            UUID frequencyUUID = UUID.randomUUID();
            UUID creator = UUID.randomUUID();
            QIOMaintenanceRuleCatalog catalog = new QIOMaintenanceRuleCatalog();
            List<QIOMaintenanceRule> rules = new ArrayList<>(10_000);
            for (int index = 0; index < 10_000; index++) {
                PortableResourceDescriptor resource = PortableResourceDescriptor.named(
                      PortableResourceDescriptor.Kind.ITEM, "stress:maintenance_" + index,
                      0, null);
                rules.add(catalog.create(resource, creator, true, 10, 100, 50,
                      index, 100, 0, 20_000));
            }

            Map<PortableResourceDescriptor, List<QIOMaintenanceRule>> grouped =
                  catalog.groupedRules();
            assertEquals(10_000, grouped.size());
            assertSame(grouped, catalog.groupedRules());

            QIOPlanSourceRevisions revisions = new QIOPlanSourceRevisions(
                  0, 0, 0, 0, 0, 0, 0, 0);
            for (int index = 0; index < 4_096; index++) {
                QIOMaintenanceRule rule = rules.get(index);
                List<QIOMaintenanceRule> group = Collections.singletonList(rule);
                QIOMaintenanceEvaluation evaluation = QIOMaintenanceEvaluation.create(
                      frequencyUUID, 0, rule.getResource(), group, group,
                      catalog.getRulesRevision(), revisions, 50, rule.getJobPriority(), 1);
                catalog.beginEvaluation(evaluation, group, 1);
                assertSame(evaluation, catalog.getPendingForResource(rule.getResource()));
                catalog.completeEvaluation(evaluation.getEvaluationId(), UUID.randomUUID());
            }
            assertEquals(0, catalog.getPendingEvaluations().size());
            assertEquals(4_096, catalog.getRules().stream()
                  .filter(rule -> rule.getOutstandingJobId() != null).count());
            assertTrue(catalog.clearTerminalOutstanding(Collections.emptyMap()));
            assertFalse(catalog.hasOutstandingRules());

            QIOMaintenanceRuleCatalog restored = QIOMaintenanceRuleCatalog.read(
                  frequencyUUID, catalog.write());
            assertEquals(10_000, restored.groupedRules().size());
            assertNotNull(restored.get(restored.getRules().get(0).getRuleId()));
        });
    }
}
