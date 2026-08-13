package mekanism.qioprocessing.common.content.transfer;

import mekanism.api.processing.MachineResourceStack;
import mekanism.common.TestBootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QIOConfigurationExchangeRecordTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void everyDurableExchangePhaseRoundTrips() throws Exception {
        MachineResourceStack target = MachineResourceStack.item("template",
              new ItemStack(Items.DIAMOND), 1);
        MachineResourceStack original = MachineResourceStack.item("template",
              new ItemStack(Items.EMERALD, 17), 17);
        QIOConfigurationExchangeRecord record = new QIOConfigurationExchangeRecord(
              UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
              UUID.randomUUID(), "template", "replicator", 0, target, original,
              UUID.randomUUID());

        assertRoundTrip(record);
        record.markClaimed();
        assertEquals(true, record.hasOutstandingClaim());
        assertRoundTrip(record);
        record.prepareTargetDebit(BigInteger.valueOf(42));
        assertRoundTrip(record);
        record.markTargetDebited();
        assertEquals(false, record.hasOutstandingClaim());
        assertRoundTrip(record);
        record.prepareOldReturn(BigInteger.valueOf(12));
        assertRoundTrip(record);
        record.markOldExtracted();
        assertRoundTrip(record);
        record.markOldQioCredited();
        assertRoundTrip(record);
        record.markNewInstalled();
        assertRoundTrip(record);
        record.commit();
        assertRoundTrip(record);
    }

    @Test
    void emptyAndReusedConfigurationHaveDistinctPersistentPaths() throws Exception {
        MachineResourceStack target = MachineResourceStack.item("template",
              new ItemStack(Items.DIAMOND), 1);
        QIOConfigurationExchangeRecord empty = new QIOConfigurationExchangeRecord(
              UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(),
              "template", "replicator", 0, target, null, UUID.randomUUID());
        assertNull(empty.getOriginal());
        empty.markClaimed();
        empty.prepareTargetDebit(BigInteger.ONE);
        empty.markTargetDebited();
        empty.markNewInstalled();
        empty.commit();
        assertRoundTrip(empty);

        QIOConfigurationExchangeRecord reused = QIOConfigurationExchangeRecord.reused(
              UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(),
              "template", "replicator", 0, target,
              target.withAmount(23));
        assertEquals(QIOConfigurationExchangeRecord.Phase.COMMITTED, reused.getPhase());
        assertEquals(23, reused.getOriginal().amount());
        assertRoundTrip(reused);
    }

    @Test
    void failedClaimAndUnappliedDebitCanRefreshTheirIdempotencyKeys() {
        QIOConfigurationExchangeRecord record = new QIOConfigurationExchangeRecord(
              UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(),
              "template", "replicator", 0,
              MachineResourceStack.item("template", new ItemStack(Items.DIAMOND), 1),
              null, UUID.randomUUID());
        UUID claimRequest = record.getClaimRequestId();
        record.retryClaim(1, 2);
        assertNotEquals(claimRequest, record.getClaimRequestId());
        record.markClaimed();
        record.prepareTargetDebit(BigInteger.TEN);
        UUID debitRequest = record.getConsumeRequestId();
        UUID transfer = record.getTargetDebitTransferId();
        record.retryTargetDebit(BigInteger.valueOf(11), 3, 4);
        assertNotEquals(debitRequest, record.getConsumeRequestId());
        assertNotEquals(transfer, record.getTargetDebitTransferId());
        assertEquals(BigInteger.valueOf(11), record.getTargetQioBaseline());
    }

    @Test
    void contaminatedClaimCanBeReleasedAfterPersistenceRoundTrip() throws Exception {
        QIOConfigurationExchangeRecord record = new QIOConfigurationExchangeRecord(
              UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(),
              "template", "replicator", 0,
              MachineResourceStack.item("template", new ItemStack(Items.DIAMOND), 1),
              null, UUID.randomUUID());
        record.markClaimed();
        UUID releaseRequest = record.getReleaseRequestId();
        record.contaminate("player changed template");

        QIOConfigurationExchangeRecord restored =
              QIOConfigurationExchangeRecord.read(record.write());
        assertEquals(releaseRequest, restored.getReleaseRequestId());
        assertEquals(true, restored.hasOutstandingClaim());
        restored.markClaimReleased();
        assertEquals(false, restored.hasOutstandingClaim());
        assertRoundTrip(restored);
    }

    private static void assertRoundTrip(QIOConfigurationExchangeRecord record) throws Exception {
        QIOConfigurationExchangeRecord restored =
              QIOConfigurationExchangeRecord.read(record.write());
        assertEquals(record.write(), restored.write());
    }
}
