package mekanism.qioprocessing.common.order;

import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.job.QIOCraftingJobSource;
import mekanism.qioprocessing.common.content.plan.QIOCraftPlan;
import mekanism.qioprocessing.common.content.plan.QIOPlanSourceRevisions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOOrderServiceMergeTest {

    private static final PortableResourceDescriptor TARGET = PortableResourceDescriptor.named(
          PortableResourceDescriptor.Kind.ITEM, "test:target", 0, null);
    private static final PortableResourceDescriptor OTHER = PortableResourceDescriptor.named(
          PortableResourceDescriptor.Kind.ITEM, "test:other", 0, null);

    @Test
    void mergeInheritsHighestMatchingActiveManualPriority() {
        UUID requester = UUID.randomUUID();
        QIOProcessingNetworkData network = network();
        network.createJob(QIOCraftingJobSource.MANUAL, requester, 4, 0,
              plan(TARGET), 16);
        network.createJob(QIOCraftingJobSource.MANUAL, requester, 12, 1,
              plan(TARGET), 16);
        network.createJob(QIOCraftingJobSource.MANUAL, UUID.randomUUID(), 50, 2,
              plan(TARGET), 16);
        network.createJob(QIOCraftingJobSource.MAINTENANCE, null, 60, 3,
              plan(TARGET), 16);
        network.createJob(QIOCraftingJobSource.MANUAL, requester, 70, 4,
              plan(OTHER), 16);

        QIOOrderService.MergeResolution merge = QIOOrderService.resolveMerge(network,
              requester, TARGET, -3, true);
        assertTrue(merge.mergeOrder());
        assertEquals(12, merge.priority());

        QIOOrderService.MergeResolution ordinary = QIOOrderService.resolveMerge(network,
              requester, TARGET, -3, false);
        assertFalse(ordinary.mergeOrder());
        assertEquals(-3, ordinary.priority());
    }

    @Test
    void staleMergeRequestFallsBackToRequestedPriority() {
        QIOOrderService.MergeResolution merge = QIOOrderService.resolveMerge(network(),
              UUID.randomUUID(), TARGET, 9, true);
        assertFalse(merge.mergeOrder());
        assertEquals(9, merge.priority());
    }

    private static QIOProcessingNetworkData network() {
        return new QIOProcessingNetworkData(UUID.randomUUID(),
              new QIOFrequencyIdentitySnapshot("merge", null, SecurityMode.PUBLIC));
    }

    private static QIOCraftPlan plan(PortableResourceDescriptor root) {
        return new QIOCraftPlan(UUID.randomUUID(), 1,
              new QIOPlanSourceRevisions(0, 0, 0, 0, 0, 0, 0, 0), root, 1,
              Collections.emptyMap());
    }
}
