package mekanism.common.content.qio;

import mekanism.api.Action;
import mekanism.api.qio.external.QIOClaimBacking;
import mekanism.api.qio.external.QIOClaimRequest;
import mekanism.api.qio.external.QIOClaimResult;
import mekanism.api.qio.external.QIOStorageChangeBatch;
import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.api.qio.external.QIOTransferResult;
import mekanism.common.lib.inventory.HashedItem;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.tier.QIODriveTier;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOResourceClaimTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    private File worldDirectory;
    private TestHolder holder;
    private QIOFrequency frequency;
    private ItemStack storedItem;
    private UUID resourceId;

    @BeforeEach
    void setup() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-resource-claim-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        holder = new TestHolder(new ItemStack(new TestDriveItem()));
        frequency = new QIOFrequency("claims", null, SecurityMode.PUBLIC);
        frequency.addHolder(holder);
        frequency.refresh();
        storedItem = new ItemStack(Blocks.STONE);
        assertEquals(20, frequency.massInsert(storedItem, 20, Action.EXECUTE));
        resourceId = QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(HashedItem.create(storedItem));
        assertNotNull(resourceId);
    }

    @AfterEach
    void cleanup() throws Exception {
        QIODriveStorage.INSTANCE.reset();
        QIOResourceTypeRegistry.INSTANCE.reset();
        delete(worldDirectory);
    }

    @Test
    void logicalClaimLimitsEveryOrdinaryExtractionPath() {
        UUID claimId = UUID.randomUUID();
        QIOClaimResult result = createClaim(claimId, 12, frequency.getContentsRevision(), frequency.getClaimRevision());

        assertEquals(QIOClaimResult.Status.APPLIED, result.getStatus());
        assertEquals(20, frequency.getStored(resourceId));
        assertEquals(12, frequency.getCommitted(resourceId));
        assertEquals(8, frequency.getAvailable(resourceId));
        assertEquals(8, frequency.massExtract(storedItem, 20, Action.SIMULATE));
        assertEquals(8, frequency.massExtract(storedItem, 20, Action.EXECUTE));
        assertEquals(12, frequency.getStored(resourceId));
        assertEquals(0, frequency.getAvailable(resourceId));

        QIOStorageEntry entry = frequency.getExternalStorageEntry(resourceId);
        assertNotNull(entry);
        assertEquals(12, entry.getStoredAmountClamped());
        assertEquals(12, entry.getCommittedAmountClamped());
        assertEquals(0, entry.getAvailableAmountClamped());
        assertEquals(frequency.getClaimRevision(), frequency.getExternalStorageSnapshot().getClaimRevision());
    }

    @Test
    void concurrentClaimsOnlyReceiveUncommittedStock() {
        QIOClaimResult first = createClaim(UUID.randomUUID(), 15,
              frequency.getContentsRevision(), frequency.getClaimRevision());
        QIOClaimResult second = createClaim(UUID.randomUUID(), 10,
              frequency.getContentsRevision(), frequency.getClaimRevision());

        assertEquals(QIOClaimResult.Status.APPLIED, first.getStatus());
        assertEquals(QIOClaimResult.Status.PARTIAL, second.getStatus());
        assertEquals(5, second.getCommittedAmounts().get(resourceId));
        assertEquals(5, second.getUnavailableAmounts().get(resourceId));
        assertEquals(20, frequency.getCommitted(resourceId));
        assertEquals(0, frequency.getAvailable(resourceId));
    }

    @Test
    void releaseRestoresAvailabilityWithoutChangingPhysicalContents() {
        UUID claimId = UUID.randomUUID();
        createClaim(claimId, 12, frequency.getContentsRevision(), frequency.getClaimRevision());
        long contentsRevision = frequency.getContentsRevision();

        QIOClaimResult release = frequency.submitClaimRequest(QIOClaimRequest.release(UUID.randomUUID(),
              claimId, "mekanismqioprocessing", "job/plan-1", frequency.getClaimRevision(),
              Collections.emptyMap()));

        assertEquals(QIOClaimResult.Status.APPLIED, release.getStatus());
        assertNull(frequency.getResourceClaim(claimId));
        assertEquals(contentsRevision, frequency.getContentsRevision());
        assertEquals(0, frequency.getCommitted(resourceId));
        assertEquals(20, frequency.getAvailable(resourceId));
    }

    @Test
    void consumeIsBatchAtomicAndIdempotent() {
        ItemStack secondItem = new ItemStack(Blocks.DIRT);
        assertEquals(10, frequency.massInsert(secondItem, 10, Action.EXECUTE));
        UUID secondResource = QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(HashedItem.create(secondItem));
        UUID claimId = UUID.randomUUID();
        Map<UUID, Long> amounts = new LinkedHashMap<>();
        amounts.put(resourceId, 8L);
        amounts.put(secondResource, 4L);
        QIOClaimResult claim = frequency.submitClaimRequest(QIOClaimRequest.createOrAdjust(UUID.randomUUID(),
              claimId, "mekanismqioprocessing", "job/plan-1", 0, 1,
              frequency.getContentsRevision(), frequency.getClaimRevision(), amounts));
        assertEquals(QIOClaimResult.Status.APPLIED, claim.getStatus());
        long oldContentsRevision = frequency.getContentsRevision();
        long oldClaimRevision = frequency.getClaimRevision();
        UUID requestId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        Map<UUID, BigInteger> baselines = new LinkedHashMap<>();
        baselines.put(resourceId, BigInteger.valueOf(20));
        baselines.put(secondResource, BigInteger.valueOf(10));
        QIOClaimRequest consume = QIOClaimRequest.consumeReconciled(requestId, transferId, claimId,
              "mekanismqioprocessing", "job/plan-1", oldContentsRevision, oldClaimRevision,
              amounts, baselines);

        QIOClaimResult first = frequency.submitClaimRequest(consume);
        QIOClaimResult replay = frequency.submitClaimRequest(consume);

        assertEquals(QIOClaimResult.Status.APPLIED, first.getStatus());
        assertEquals(first.getConsumedAmounts(), replay.getConsumedAmounts());
        assertEquals(oldContentsRevision + 1, frequency.getContentsRevision());
        assertEquals(oldClaimRevision + 1, frequency.getClaimRevision());
        assertEquals(12, frequency.getStored(resourceId));
        assertEquals(6, frequency.getStored(secondResource));
        assertEquals(0, frequency.getCommitted(resourceId));
        assertEquals(0, frequency.getCommitted(secondResource));
        assertNull(frequency.getResourceClaim(claimId));

        QIOClaimResult conflict = frequency.submitClaimRequest(QIOClaimRequest.release(requestId,
              claimId, "mekanismqioprocessing", "job/plan-1", QIOClaimRequest.ANY_REVISION,
              Collections.emptyMap()));
        assertEquals(QIOClaimResult.Status.REQUEST_ID_CONFLICT, conflict.getStatus());
    }

    @Test
    void failedConsumeReceiptCanResumeWithoutDoubleExtraction() {
        QIOClaimLedger ledger = new QIOClaimLedger();
        FailingConsumeStorage storage = new FailingConsumeStorage(resourceId, 20);
        UUID claimId = UUID.randomUUID();
        Map<UUID, Long> amounts = Collections.singletonMap(resourceId, 8L);
        QIOClaimResult claim = ledger.submit(QIOClaimRequest.createOrAdjust(
              UUID.randomUUID(), claimId, "mekanismqioprocessing", "job/configuration",
              0, 1, 0, ledger.getRevision(), amounts), 0, storage).getResult();
        assertTrue(claim.isSuccess());

        QIOClaimRequest consume = QIOClaimRequest.consumeReconciled(UUID.randomUUID(),
              UUID.randomUUID(), claimId, "mekanismqioprocessing", "job/configuration",
              0, ledger.getRevision(), amounts,
              Collections.singletonMap(resourceId, BigInteger.valueOf(20)));
        QIOClaimResult failed = ledger.submit(consume, 0, storage).getResult();

        assertEquals(QIOClaimResult.Status.FAILED, failed.getStatus());
        assertEquals(12, storage.amount);
        assertEquals(1, storage.extractions);
        assertNotNull(ledger.getClaim(claimId));

        QIOClaimResult recovered = ledger.submit(consume, 1, storage).getResult();
        QIOClaimResult replay = ledger.submit(consume, 1, storage).getResult();

        assertEquals(QIOClaimResult.Status.APPLIED, recovered.getStatus());
        assertEquals(amounts, recovered.getConsumedAmounts());
        assertEquals(recovered.getConsumedAmounts(), replay.getConsumedAmounts());
        assertEquals(12, storage.amount);
        assertEquals(1, storage.extractions);
        assertNull(ledger.getClaim(claimId));
    }

    @Test
    void claimsAndReceiptsSurviveFrequencyNbtReload() {
        UUID claimId = UUID.randomUUID();
        UUID createRequestId = UUID.randomUUID();
        QIOClaimRequest create = QIOClaimRequest.createOrAdjust(createRequestId, claimId,
              "mekanismqioprocessing", "job/plan-1", 2, 9, frequency.getContentsRevision(),
              frequency.getClaimRevision(), Collections.singletonMap(resourceId, 10L));
        QIOClaimResult original = frequency.submitClaimRequest(create);
        NBTTagCompound data = new NBTTagCompound();
        frequency.write(data);
        frequency.removeHolder(holder);

        QIOFrequency reloaded = new QIOFrequency(data);
        reloaded.addHolder(holder);
        reloaded.refresh();
        QIOClaimResult replay = reloaded.submitClaimRequest(create);

        assertEquals(original.getStatus(), replay.getStatus());
        assertEquals(original.getClaimRevision(), replay.getClaimRevision());
        assertNotNull(reloaded.getResourceClaim(claimId));
        assertEquals(10, reloaded.getCommitted(resourceId));
        assertEquals(10, reloaded.getAvailable(resourceId));
    }

    @Test
    void externalInsertTransferIsIdempotentAcrossFrequencyReload() {
        ItemStack dirt = new ItemStack(Blocks.DIRT);
        UUID transferId = UUID.randomUUID();

        QIOTransferResult applied = frequency.insertExternalTransfer(transferId, dirt, 7,
              BigInteger.ZERO);
        QIOTransferResult replay = frequency.insertExternalTransfer(transferId, dirt, 7,
              BigInteger.ZERO);
        QIOTransferResult conflict = frequency.insertExternalTransfer(transferId, storedItem, 7,
              BigInteger.valueOf(20));

        assertEquals(QIOTransferResult.Status.APPLIED, applied.getStatus());
        assertEquals(QIOTransferResult.Status.REPLAYED, replay.getStatus());
        assertEquals(7, replay.getTransferredAmount());
        assertEquals(QIOTransferResult.Status.TRANSFER_ID_CONFLICT, conflict.getStatus());
        assertEquals(7, frequency.getStored(dirt));
        assertEquals(20, frequency.getStored(storedItem));

        NBTTagCompound data = new NBTTagCompound();
        frequency.write(data);
        frequency.removeHolder(holder);
        QIOFrequency reloaded = new QIOFrequency(data);
        reloaded.addHolder(holder);
        reloaded.refresh();

        QIOTransferResult reloadedReplay = reloaded.insertExternalTransfer(transferId, dirt, 7,
              BigInteger.ZERO);
        assertEquals(QIOTransferResult.Status.REPLAYED, reloadedReplay.getStatus());
        assertEquals(7, reloaded.getStored(dirt));
    }

    @Test
    void reconciledConsumeRepairsFrequencyRollbackWithoutDoubleExtraction() {
        UUID claimId = UUID.randomUUID();
        Map<UUID, Long> amounts = Collections.singletonMap(resourceId, 8L);
        QIOClaimResult claim = createClaim(claimId, 8, frequency.getContentsRevision(),
              frequency.getClaimRevision());
        assertTrue(claim.isSuccess());
        NBTTagCompound frequencyBeforeConsume = new NBTTagCompound();
        frequency.write(frequencyBeforeConsume);
        QIOClaimRequest consume = QIOClaimRequest.consumeReconciled(UUID.randomUUID(),
              UUID.randomUUID(), claimId, "mekanismqioprocessing", "job/plan-1",
              frequency.getContentsRevision(), frequency.getClaimRevision(), amounts,
              Collections.singletonMap(resourceId, BigInteger.valueOf(20)));

        assertTrue(frequency.submitClaimRequest(consume).isSuccess());
        assertEquals(12, frequency.getStored(resourceId));

        frequency.removeHolder(holder);
        QIOFrequency rolledBack = new QIOFrequency(frequencyBeforeConsume);
        rolledBack.addHolder(holder);
        rolledBack.refresh();
        QIOClaimResult recovered = rolledBack.submitClaimRequest(consume);

        assertTrue(recovered.isSuccess());
        assertEquals(amounts, recovered.getConsumedAmounts());
        assertEquals(12, rolledBack.getStored(resourceId));
        assertNull(rolledBack.getResourceClaim(claimId));
        frequency = rolledBack;
    }

    @Test
    void reconciledConsumeRepairsDriveRollbackBeforeReplayingReceipt() throws Exception {
        assertTrue(QIOStorageManager.flush());
        byte[] driveBeforeConsume = Files.readAllBytes(driveFile());
        UUID claimId = UUID.randomUUID();
        Map<UUID, Long> amounts = Collections.singletonMap(resourceId, 8L);
        assertTrue(createClaim(claimId, 8, frequency.getContentsRevision(),
              frequency.getClaimRevision()).isSuccess());
        QIOClaimRequest consume = QIOClaimRequest.consumeReconciled(UUID.randomUUID(),
              UUID.randomUUID(), claimId, "mekanismqioprocessing", "job/plan-1",
              frequency.getContentsRevision(), frequency.getClaimRevision(), amounts,
              Collections.singletonMap(resourceId, BigInteger.valueOf(20)));
        assertTrue(frequency.submitClaimRequest(consume).isSuccess());
        NBTTagCompound frequencyAfterConsume = new NBTTagCompound();
        frequency.write(frequencyAfterConsume);

        frequency.removeHolder(holder);
        Files.write(driveFile(), driveBeforeConsume);
        QIODriveStorage.INSTANCE.reset();
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        QIOFrequency restored = new QIOFrequency(frequencyAfterConsume);
        restored.addHolder(holder);
        restored.refresh();
        assertEquals(20, restored.getStored(resourceId));

        QIOClaimResult replay = restored.submitClaimRequest(consume);

        assertTrue(replay.isSuccess());
        assertEquals(12, restored.getStored(resourceId));
        frequency = restored;
    }

    @Test
    void reconciledInsertRecoversAReceiptAfterFrequencyRollback() {
        ItemStack dirt = new ItemStack(Blocks.DIRT);
        UUID transferId = UUID.randomUUID();
        NBTTagCompound frequencyBeforeInsert = new NBTTagCompound();
        frequency.write(frequencyBeforeInsert);

        QIOTransferResult applied = frequency.insertExternalTransfer(transferId, dirt, 7,
              BigInteger.ZERO);
        assertEquals(QIOTransferResult.Status.APPLIED, applied.getStatus());
        frequency.removeHolder(holder);
        QIOFrequency rolledBack = new QIOFrequency(frequencyBeforeInsert);
        rolledBack.addHolder(holder);

        QIOTransferResult recovered = rolledBack.insertExternalTransfer(transferId, dirt, 7,
              BigInteger.ZERO);

        assertEquals(QIOTransferResult.Status.REPLAYED, recovered.getStatus());
        assertEquals(7, recovered.getTransferredAmount());
        assertEquals(7, rolledBack.getStored(dirt));
        frequency = rolledBack;
    }

    @Test
    void reconciledInsertRepairsDriveRollbackBeforeReplayingReceipt() throws Exception {
        assertTrue(QIOStorageManager.flush());
        byte[] driveBeforeInsert = Files.readAllBytes(driveFile());
        ItemStack dirt = new ItemStack(Blocks.DIRT);
        UUID transferId = UUID.randomUUID();
        assertTrue(frequency.insertExternalTransfer(transferId, dirt, 7,
              BigInteger.ZERO).isSuccess());
        NBTTagCompound frequencyAfterInsert = new NBTTagCompound();
        frequency.write(frequencyAfterInsert);

        frequency.removeHolder(holder);
        Files.write(driveFile(), driveBeforeInsert);
        QIODriveStorage.INSTANCE.reset();
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        QIOFrequency restored = new QIOFrequency(frequencyAfterInsert);
        restored.addHolder(holder);
        restored.refresh();
        assertEquals(0, restored.getStored(dirt));

        QIOTransferResult replay = restored.insertExternalTransfer(transferId, dirt, 7,
              BigInteger.ZERO);

        assertEquals(QIOTransferResult.Status.REPLAYED, replay.getStatus());
        assertEquals(7, restored.getStored(dirt));
        frequency = restored;
    }

    @Test
    void reconciledInsertRejectsAnUnexplainedPhysicalAmount() {
        UUID transferId = UUID.randomUUID();

        QIOTransferResult result = frequency.insertExternalTransfer(transferId, storedItem, 3,
              BigInteger.valueOf(10));

        assertEquals(QIOTransferResult.Status.RECONCILIATION_CONFLICT, result.getStatus());
        assertEquals(20, frequency.getStored(storedItem));
    }

    @Test
    void reconciledConsumeRejectsAnAmbiguousPartialPhysicalDelta() {
        UUID claimId = UUID.randomUUID();
        Map<UUID, Long> amounts = Collections.singletonMap(resourceId, 8L);
        assertTrue(createClaim(claimId, 8, frequency.getContentsRevision(),
              frequency.getClaimRevision()).isSuccess());
        assertEquals(4, frequency.massExtract(storedItem, 4, Action.EXECUTE));
        QIOClaimRequest consume = QIOClaimRequest.consumeReconciled(UUID.randomUUID(),
              UUID.randomUUID(), claimId, "mekanismqioprocessing", "job/plan-1",
              QIOClaimRequest.ANY_REVISION, QIOClaimRequest.ANY_REVISION, amounts,
              Collections.singletonMap(resourceId, BigInteger.valueOf(20)));

        QIOClaimResult result = frequency.submitClaimRequest(consume);

        assertEquals(QIOClaimResult.Status.RECONCILIATION_CONFLICT, result.getStatus());
        assertEquals(16, frequency.getStored(resourceId));
        assertNotNull(frequency.getResourceClaim(claimId));
    }

    @Test
    void reconciledConsumeRejectsAnAmountOutsideItsPhysicalInterval() {
        UUID claimId = UUID.randomUUID();
        Map<UUID, Long> amounts = Collections.singletonMap(resourceId, 8L);
        assertTrue(createClaim(claimId, 8, frequency.getContentsRevision(),
              frequency.getClaimRevision()).isSuccess());
        QIOClaimRequest consume = QIOClaimRequest.consumeReconciled(UUID.randomUUID(),
              UUID.randomUUID(), claimId, "mekanismqioprocessing", "job/plan-1",
              QIOClaimRequest.ANY_REVISION, QIOClaimRequest.ANY_REVISION, amounts,
              Collections.singletonMap(resourceId, BigInteger.valueOf(30)));

        QIOClaimResult result = frequency.submitClaimRequest(consume);

        assertEquals(QIOClaimResult.Status.RECONCILIATION_CONFLICT, result.getStatus());
        assertEquals(20, frequency.getStored(resourceId));
        assertNotNull(frequency.getResourceClaim(claimId));
    }

    @Test
    void claimChangesInvalidateExternalAvailabilityCaches() {
        frequency.tick(true);
        List<QIOStorageChangeBatch> batches = new ArrayList<>();
        assertTrue(frequency.addExternalStorageListener(batches::add));
        long oldContentsRevision = frequency.getContentsRevision();
        long oldClaimRevision = frequency.getClaimRevision();

        createClaim(UUID.randomUUID(), 6, oldContentsRevision, oldClaimRevision);
        assertTrue(batches.isEmpty());
        frequency.tick(true);

        assertEquals(1, batches.size());
        QIOStorageChangeBatch batch = batches.get(0);
        assertEquals(oldContentsRevision, batch.getNewContentsRevision());
        assertEquals(oldClaimRevision, batch.getOldClaimRevision());
        assertEquals(oldClaimRevision + 1, batch.getNewClaimRevision());
        assertTrue(batch.isClaimChanged());
        assertTrue(batch.isFullRescanRequired());
        assertFalse(batch.isInvalidated());
    }

    @Test
    void physicalBackingUsesStableClaimPriorityWithoutRewritingLogicalClaims() {
        UUID highClaimId = UUID.randomUUID();
        UUID lowClaimId = UUID.randomUUID();
        QIOClaimResult high = frequency.submitClaimRequest(QIOClaimRequest.createOrAdjust(
              UUID.randomUUID(), highClaimId, "high", "job/high", 10, 2,
              frequency.getContentsRevision(), frequency.getClaimRevision(),
              Collections.singletonMap(resourceId, 12L)));
        QIOClaimResult low = frequency.submitClaimRequest(QIOClaimRequest.createOrAdjust(
              UUID.randomUUID(), lowClaimId, "low", "job/low", 0, 1,
              frequency.getContentsRevision(), frequency.getClaimRevision(),
              Collections.singletonMap(resourceId, 8L)));
        assertTrue(high.isSuccess());
        assertTrue(low.isSuccess());
        long logicalClaimRevision = frequency.getClaimRevision();

        UUID driveId = ((IQIODriveItem) holder.drive.getItem()).getDriveId(holder.drive);
        QIODriveRecord drive = QIODriveStorage.INSTANCE.get(driveId);
        assertNotNull(drive);
        assertEquals(5, drive.extract(resourceId, 5, Action.EXECUTE));
        frequency.requestRefresh();

        QIOClaimBacking highBacking = frequency.getResourceClaimBacking(highClaimId);
        QIOClaimBacking lowBacking = frequency.getResourceClaimBacking(lowClaimId);
        assertNotNull(highBacking);
        assertNotNull(lowBacking);
        assertTrue(highBacking.isFullyBacked());
        assertEquals(12, highBacking.getBackedAmounts().get(resourceId));
        assertEquals(3, lowBacking.getBackedAmounts().get(resourceId));
        assertEquals(5, lowBacking.getUnsupportedAmounts().get(resourceId));
        assertEquals(logicalClaimRevision, frequency.getClaimRevision());
        assertEquals(20, frequency.getCommitted(resourceId));
        assertEquals(0, frequency.getAvailable(resourceId));

        assertEquals(5, drive.insert(resourceId, 5, Action.EXECUTE));
        frequency.requestRefresh();
        QIOClaimBacking restored = frequency.getResourceClaimBacking(lowClaimId);
        assertNotNull(restored);
        assertTrue(restored.isFullyBacked());
        assertEquals(8, restored.getBackedAmounts().get(resourceId));
    }

    private QIOClaimResult createClaim(UUID claimId, long amount, long contentsRevision, long claimRevision) {
        return frequency.submitClaimRequest(QIOClaimRequest.createOrAdjust(UUID.randomUUID(), claimId,
              "mekanismqioprocessing", "job/plan-1", 0, 1, contentsRevision, claimRevision,
              Collections.singletonMap(resourceId, amount)));
    }

    private java.nio.file.Path driveFile() {
        UUID driveId = ((IQIODriveItem) holder.drive.getItem()).getDriveId(holder.drive);
        assertNotNull(driveId);
        return new File(worldDirectory, "mekanism/qio/drives/" + driveId + ".dat").toPath();
    }

    private static void delete(File file) throws Exception {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    delete(child);
                }
            }
        }
        Files.deleteIfExists(file.toPath());
    }

    private static final class TestDriveItem extends Item implements IQIODriveItem {

        private TestDriveItem() {
            setMaxStackSize(1);
        }

        @Override
        public QIODriveTier getDriveTier() {
            return QIODriveTier.BASE;
        }
    }

    private static final class FailingConsumeStorage implements QIOClaimLedger.Storage {

        private final UUID resourceId;
        private long amount;
        private int extractions;
        private boolean failNextPersistence = true;

        private FailingConsumeStorage(UUID resourceId, long amount) {
            this.resourceId = resourceId;
            this.amount = amount;
        }

        @Override
        public boolean isClaimable(UUID resource) {
            return resourceId.equals(resource);
        }

        @Override
        public QIOAmount getStored(UUID resource) {
            return resourceId.equals(resource) ? QIOAmount.of(amount) : QIOAmount.ZERO;
        }

        @Override
        public boolean extractClaimed(Map<UUID, Long> resources) {
            long requested = resources.getOrDefault(resourceId, 0L);
            if (requested < 0 || requested > amount || resources.size() != 1) {
                return false;
            }
            amount -= requested;
            extractions++;
            return true;
        }

        @Override
        public boolean persistPhysical() {
            if (failNextPersistence) {
                failNextPersistence = false;
                return false;
            }
            return true;
        }
    }

    private static final class TestHolder implements IQIODriveHolder {

        private ItemStack drive;

        private TestHolder(ItemStack drive) {
            this.drive = drive;
        }

        @Override
        public int getQIODimension() {
            return 0;
        }

        @Override
        public BlockPos getQIOPosition() {
            return BlockPos.ORIGIN;
        }

        @Override
        public List<ItemStack> getQIODriveStacks() {
            return Collections.singletonList(drive);
        }

        @Override
        public void updateQIODriveStack(int slot, ItemStack stack) {
            drive = stack.copy();
        }
    }
}
