package mekanism.qioprocessing.common.content.maintenance;

import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.api.qio.external.QIOStorageSnapshot;
import mekanism.common.TestBootstrap;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.job.QIOCraftingJob;
import mekanism.qioprocessing.common.content.job.QIOCraftingJobSource;
import mekanism.qioprocessing.common.content.job.QIOCraftingJobState;
import mekanism.qioprocessing.common.content.plan.QIOCraftPlan;
import mekanism.qioprocessing.common.content.plan.QIOPlanSourceRevisions;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class QIOMaintenanceEvaluatorTest {

    private static PortableResourceDescriptor iron;

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
        iron = PortableResourceDescriptor.item(new ItemStack(Items.IRON_INGOT));
    }

    @Test
    void onlyTriggeredRulesContributeTargetPriorityAndBatchCap() {
        QIOProcessingNetworkData network = network();
        QIOMaintenanceRule low = rule(network, 100, 1_000, 900, 3, 0);
        QIOMaintenanceRule high = rule(network, 500, 800, 200, 10, 0);
        QIOMaintenanceRule dormant = rule(network, 10, 2_000, 1, 99, 0);

        QIOMaintenanceEvaluator.Decision atFourHundred = QIOMaintenanceEvaluator.evaluate(
              network, storage(network, 400), iron, Arrays.asList(low, high, dormant), 1_000,
              100);

        assertEquals(QIOMaintenanceEvaluator.Status.REQUEST, atFourHundred.getStatus());
        assertEquals(Collections.singletonList(high.getRuleId()),
              atFourHundred.getTriggeredRules().stream().map(QIOMaintenanceRule::getRuleId)
                    .collect(Collectors.toList()));
        assertEquals(200, atFourHundred.getRequestAmount());
        assertEquals(10, atFourHundred.getJobPriority());

        QIOMaintenanceEvaluator.Decision atFifty = QIOMaintenanceEvaluator.evaluate(network,
              storage(network, 50), iron, Arrays.asList(low, high, dormant), 1_000, 100);

        assertEquals(QIOMaintenanceEvaluator.Status.REQUEST, atFifty.getStatus());
        List<UUID> expectedTriggered = Arrays.asList(low.getRuleId(), high.getRuleId());
        expectedTriggered.sort(UUID::compareTo);
        assertEquals(expectedTriggered, atFifty.getTriggeredRules().stream()
              .map(QIOMaintenanceRule::getRuleId).sorted().collect(Collectors.toList()));
        assertEquals(200, atFifty.getRequestAmount());
        assertEquals(10, atFifty.getJobPriority());
    }

    @Test
    void guaranteedRootOutputPreventsDuplicateMaintenanceOrders() {
        QIOProcessingNetworkData network = network();
        QIOMaintenanceRule rule = rule(network, 100, 1_000, 1_000, 0, 0);
        network.createJob(QIOCraftingJobSource.MANUAL, UUID.randomUUID(), 0, 1,
              plan(iron, 500), 100);

        QIOMaintenanceEvaluator.Decision decision = QIOMaintenanceEvaluator.evaluate(network,
              storage(network, 50), iron, Collections.singletonList(rule), 1_000, 2);

        assertEquals(QIOMaintenanceEvaluator.Status.NOT_TRIGGERED, decision.getStatus());
        assertEquals(BigInteger.valueOf(50), decision.getEffectiveStock().getQioSpendable());
        assertEquals(BigInteger.valueOf(500),
              decision.getEffectiveStock().getCommittedGuaranteedInbound());
        assertEquals(BigInteger.valueOf(550), decision.getEffectiveStock().getTotal());
    }

    @Test
    void oneActiveMaintenanceOrderOwnsTheResourceGroup() {
        QIOProcessingNetworkData network = network();
        QIOMaintenanceRule rule = rule(network, 500, 1_000, 1_000, 0, 0);
        QIOCraftingJob active = network.createJob(QIOCraftingJobSource.MAINTENANCE, null,
              0, 1, plan(iron, 300), 100);

        QIOMaintenanceEvaluator.Decision decision = QIOMaintenanceEvaluator.evaluate(network,
              storage(network, 0), iron, Collections.singletonList(rule), 1_000, 2);

        assertEquals(QIOMaintenanceEvaluator.Status.ACTIVE_ORDER, decision.getStatus());
        assertEquals(active.getJobId(), decision.getActiveJobId());
    }

    @Test
    void projectedHotPathStillSeesActiveMaintenanceOrder() {
        QIOProcessingNetworkData network = network();
        QIOMaintenanceRule rule = rule(network, 500, 500, 64, 0, 0);
        QIOCraftingJob active = network.createJob(QIOCraftingJobSource.MAINTENANCE, null,
              0, 1, plan(iron, 64), 100);
        Map<PortableResourceDescriptor, BigInteger> spendable = new LinkedHashMap<>();
        spendable.put(iron, BigInteger.ZERO);
        QIOMaintenanceEvaluator.EvaluationContext context =
              QIOMaintenanceEvaluator.EvaluationContext.createProjected(network, spendable);

        QIOMaintenanceEvaluator.Decision decision = QIOMaintenanceEvaluator.evaluate(network,
              context, iron, Collections.singletonList(rule), 1_000, 2);

        assertEquals(QIOMaintenanceEvaluator.Status.ACTIVE_ORDER, decision.getStatus());
        assertEquals(active.getJobId(), decision.getActiveJobId());
        assertEquals(BigInteger.valueOf(64),
              decision.getEffectiveStock().getCommittedGuaranteedInbound());
    }

    @Test
    void failedRuleHonorsItsRetryTick() {
        QIOProcessingNetworkData network = network();
        QIOMaintenanceRule rule = rule(network, 500, 1_000, 1_000, 0, 40);
        network.getMaintenanceRules().recordGroupEvaluation(Collections.singletonList(rule),
              UUID.randomUUID(),
              QIOMaintenanceRule.EvaluationStatus.NO_ROUTE, 100, "no route");

        QIOMaintenanceEvaluator.Decision decision = QIOMaintenanceEvaluator.evaluate(network,
              storage(network, 0), iron, Collections.singletonList(rule), 1_000, 120);

        assertEquals(QIOMaintenanceEvaluator.Status.RETRY_WAIT, decision.getStatus());
        assertEquals(140, decision.getRetryAtTick());
    }

    @Test
    void targetOnlyRuleRequestsConfiguredBatchAsSoonAsStockDropsBelowTarget() {
        QIOProcessingNetworkData network = network();
        QIOMaintenanceRule rule = rule(network, 100, 100, 50, 0, 0);

        QIOMaintenanceEvaluator.Decision below = QIOMaintenanceEvaluator.evaluate(network,
              storage(network, 99), iron, Collections.singletonList(rule), 1_000, 1);
        QIOMaintenanceEvaluator.Decision atTarget = QIOMaintenanceEvaluator.evaluate(network,
              storage(network, 100), iron, Collections.singletonList(rule), 1_000, 1);

        assertEquals(QIOMaintenanceEvaluator.Status.REQUEST, below.getStatus());
        assertEquals(50, below.getRequestAmount());
        assertEquals(QIOMaintenanceEvaluator.Status.NOT_TRIGGERED, atTarget.getStatus());
    }

    @Test
    void pendingEvaluationAndStableJobBindingSurvivePersistence() throws Exception {
        QIOProcessingNetworkData network = network();
        QIOMaintenanceRule rule = rule(network, 500, 1_000, 1_000, 7, 0);
        QIOMaintenanceEvaluation evaluation = evaluation(network,
              Collections.singletonList(rule), 500, 7, 10);
        QIOMaintenanceEvaluation replay = evaluation(network,
              Collections.singletonList(rule), 500, 7, 10);
        assertEquals(evaluation.getEvaluationId(), replay.getEvaluationId());
        assertEquals(evaluation.getJobId(), replay.getJobId());

        network.getMaintenanceRules().beginEvaluation(evaluation,
              Collections.singletonList(rule), 10);
        QIOProcessingNetworkData restored = QIOProcessingNetworkData.read(network.write(),
              network.getFrequencyUUID());
        QIOMaintenanceEvaluation loaded = restored.getMaintenanceRules().getPending(
              evaluation.getEvaluationId());

        assertNotNull(loaded);
        assertEquals(evaluation.getJobId(), loaded.getJobId());
        assertEquals(QIOMaintenanceRule.EvaluationStatus.PLANNING,
              restored.getMaintenanceRules().get(rule.getRuleId()).getEvaluationStatus());

        QIOCraftingJob job = restored.createJob(loaded.getJobId(),
              QIOCraftingJobSource.MAINTENANCE, null, loaded.getJobPriority(), 11,
              plan(iron, loaded.getRequestAmount()), 100);
        restored.getMaintenanceRules().completeEvaluation(loaded.getEvaluationId(), job.getJobId());
        QIOProcessingNetworkData rebound = QIOProcessingNetworkData.read(restored.write(),
              restored.getFrequencyUUID());

        assertNull(rebound.getMaintenanceRules().getPending(loaded.getEvaluationId()));
        assertEquals(job.getJobId(), rebound.getMaintenanceRules().get(rule.getRuleId())
              .getOutstandingJobId());
        assertEquals(loaded.getEvaluationId(),
              rebound.getMaintenanceRules().getLastCompletedEvaluationId());
    }

    @Test
    void duplicatePendingClaimForOneResourceIsRejectedAndTerminalJobClearsBinding() {
        QIOProcessingNetworkData network = network();
        QIOMaintenanceRule rule = rule(network, 500, 1_000, 1_000, 0, 0);
        QIOMaintenanceEvaluation first = evaluation(network, Collections.singletonList(rule),
              500, 0, 10);
        network.getMaintenanceRules().beginEvaluation(first, Collections.singletonList(rule), 10);
        QIOMaintenanceEvaluation second = QIOMaintenanceEvaluation.create(
              network.getFrequencyUUID(), 1, iron, Collections.singletonList(rule),
              Collections.singletonList(rule),
              network.getMaintenanceRules().getRulesRevision(), revisions(), 500, 0, 11);

        assertThrows(IllegalStateException.class, () ->
              network.getMaintenanceRules().beginEvaluation(second,
                    Collections.singletonList(rule), 11));

        QIOCraftingJob job = network.createJob(first.getJobId(),
              QIOCraftingJobSource.MAINTENANCE, null, 0, 12, plan(iron, 500), 100);
        network.getMaintenanceRules().completeEvaluation(first.getEvaluationId(), job.getJobId());
        assertEquals(job.getJobId(), rule.getOutstandingJobId());
        assertNotNull(network.acquireExecutionSlot(job.getJobId(), 8));
        network.releaseExecutionSlot(job.getJobId(), QIOCraftingJobState.COMPLETED);

        assertNull(rule.getOutstandingJobId());
        assertEquals(QIOMaintenanceRule.EvaluationStatus.NEVER, rule.getEvaluationStatus());
        assertEquals(0, network.getMaintenanceRules().getNextEvaluationTick());
    }

    @Test
    void maintenanceRuntimeDoesNotInvalidateManualPlanningCommitments() throws Exception {
        QIOProcessingNetworkData network = network();
        QIOMaintenanceRule rule = rule(network, 500, 1_000, 1_000, 0, 0);
        long generalRevision = network.getNetworkRevision();

        network.getMaintenanceRules().recordGroupEvaluation(Collections.singletonList(rule),
              UUID.randomUUID(), QIOMaintenanceRule.EvaluationStatus.NOT_TRIGGERED, 1, null);
        network.markMaintenanceRuntimeChanged();

        assertEquals(0, network.getTaskCommitmentRevision());
        assertEquals(generalRevision + 1, network.getNetworkRevision());
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL,
              UUID.randomUUID(), 0, 2, plan(iron, 1), 100);
        assertEquals(1, network.getTaskCommitmentRevision());
        network.requestJobCancellation(job.getJobId());
        assertEquals(2, network.getTaskCommitmentRevision());

        QIOProcessingNetworkData restored = QIOProcessingNetworkData.read(network.write(),
              network.getFrequencyUUID());
        assertEquals(2, restored.getTaskCommitmentRevision());
    }

    @Test
    void editedRuleInvalidatesPendingEvaluationWithoutRetryBackoff() {
        QIOProcessingNetworkData network = network();
        QIOMaintenanceRule rule = rule(network, 500, 1_000, 1_000, 0, 0);
        QIOMaintenanceEvaluation evaluation = evaluation(network,
              Collections.singletonList(rule), 500, 0, 10);
        network.getMaintenanceRules().beginEvaluation(evaluation,
              Collections.singletonList(rule), 10);
        network.updateMaintenanceRule(rule.getRuleId(), rule.getRuleRevision(),
              UUID.randomUUID(), true, 400, 900, 500, 2, 40, 11);

        network.getMaintenanceRules().invalidateEvaluation(evaluation.getEvaluationId());

        assertNull(network.getMaintenanceRules().getPending(evaluation.getEvaluationId()));
        assertEquals(QIOMaintenanceRule.EvaluationStatus.NEVER, rule.getEvaluationStatus());
        assertEquals(0, rule.getNextRetryTick());
        assertEquals(0, network.getMaintenanceRules().getNextEvaluationTick());
    }

    @Test
    void sharedContextEvaluatesTenThousandEntrySnapshotUnderFiveSeconds() {
        QIOProcessingNetworkData network = network();
        QIOMaintenanceRule rule = rule(network, 20_000, 25_000, 1_000, 0, 0);
        List<QIOStorageEntry> entries = new ArrayList<>(10_000);
        for (int index = 0; index < 10_000; index++) {
            entries.add(QIOStorageEntry.item(UUID.randomUUID(), BigInteger.ONE,
                  new ItemStack(Items.IRON_INGOT)));
        }
        QIOStorageSnapshot storage = new QIOStorageSnapshot(network.getFrequencyUUID(),
              "maintenance-scale", 1, 1, 1, 1, entries,
              BigInteger.valueOf(1_000_000), BigInteger.valueOf(100_000), 0, 0);
        QIOMaintenanceEvaluator.EvaluationContext context = assertTimeout(
              Duration.ofSeconds(5), () ->
                    QIOMaintenanceEvaluator.EvaluationContext.create(network, storage));
        QIOMaintenanceEvaluator.Decision decision = assertTimeout(Duration.ofSeconds(5), () -> {
            QIOMaintenanceEvaluator.Decision current = null;
            for (int index = 0; index < 10_000; index++) {
                current = QIOMaintenanceEvaluator.evaluate(network, context, iron,
                      Collections.singletonList(rule), 1_000, 1);
            }
            return current;
        });
        assertNotNull(decision);
        assertEquals(QIOMaintenanceEvaluator.Status.REQUEST, decision.getStatus());
        assertEquals(1_000, decision.getRequestAmount());
        assertEquals(BigInteger.valueOf(10_000),
              decision.getEffectiveStock().getQioSpendable());
    }

    private static QIOProcessingNetworkData network() {
        UUID frequency = UUID.randomUUID();
        return new QIOProcessingNetworkData(frequency,
              new QIOFrequencyIdentitySnapshot("maintenance", null, SecurityMode.PUBLIC));
    }

    private static QIOMaintenanceRule rule(QIOProcessingNetworkData network, long trigger,
          long target, long cap, long priority, int retry) {
        return network.createMaintenanceRule(iron, UUID.randomUUID(), true, trigger, target,
              cap, priority, retry == 0 ? 20 : retry, 0, 100);
    }

    private static QIOMaintenanceEvaluation evaluation(QIOProcessingNetworkData network,
          List<QIOMaintenanceRule> group, long amount, long priority, long tick) {
        return QIOMaintenanceEvaluation.create(network.getFrequencyUUID(), 0, iron, group,
              group, network.getMaintenanceRules().getRulesRevision(), revisions(), amount,
              priority, tick);
    }

    private static QIOStorageSnapshot storage(QIOProcessingNetworkData network, long amount) {
        List<QIOStorageEntry> entries = amount == 0 ? Collections.emptyList() :
              Collections.singletonList(
              QIOStorageEntry.item(UUID.randomUUID(), BigInteger.valueOf(amount),
                    new ItemStack(Items.IRON_INGOT)));
        return new QIOStorageSnapshot(network.getFrequencyUUID(), "maintenance", 0, 0, 0,
              0, entries, BigInteger.valueOf(1_000_000), BigInteger.valueOf(1_000), 0, 0);
    }

    private static QIOCraftPlan plan(PortableResourceDescriptor root, long amount) {
        return new QIOCraftPlan(UUID.randomUUID(), 1, revisions(), root, amount,
              Collections.emptyMap());
    }

    private static QIOPlanSourceRevisions revisions() {
        return new QIOPlanSourceRevisions(0, 0, 0, 0, 0, 0, 0, 0);
    }
}
