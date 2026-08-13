package mekanism.common.content.qio;

import mekanism.api.qio.external.QIOTransferResult;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;

/** Frequency-owned persistent receipts for cross-storage insertions. Called under the QIO frequency lock. */
final class QIOTransferLedger {

    private static final String RECEIPTS = "qioTransferReceipts";
    private static final int MAX_RECEIPTS = 65_536;
    private final Map<UUID, Receipt> receipts = new LinkedHashMap<>();

    @Nonnull
    QIOTransferResult submitReconciled(@Nonnull UUID transferId, @Nonnull String digest,
          long requestedAmount, @Nonnull QIOAmount expectedStored,
          @Nonnull Supplier<QIOAmount> currentStored, @Nonnull LongUnaryOperator insertion,
          @Nonnull BooleanSupplier persistence) {
        Objects.requireNonNull(transferId, "Transfer id cannot be null");
        Objects.requireNonNull(digest, "Transfer digest cannot be null");
        Objects.requireNonNull(expectedStored, "Expected stored amount cannot be null");
        Objects.requireNonNull(currentStored, "Current stored amount cannot be null");
        Objects.requireNonNull(insertion, "Insertion cannot be null");
        Objects.requireNonNull(persistence, "Persistence barrier cannot be null");
        if (requestedAmount <= 0 || digest.isEmpty()) {
            return new QIOTransferResult(transferId, QIOTransferResult.Status.INVALID_REQUEST,
                  Math.max(1, requestedAmount), 0);
        }
        Receipt existing = receipts.get(transferId);
        if (existing != null && !existing.digest.equals(digest)) {
            return new QIOTransferResult(transferId,
                  QIOTransferResult.Status.TRANSFER_ID_CONFLICT, requestedAmount, 0);
        }
        QIOAmount current = Objects.requireNonNull(currentStored.get(),
              "Current stored amount cannot be null");
        long transferred = existing == null ? requestedAmount : existing.transferredAmount;
        QIOAmount target = expectedStored.add(transferred);
        boolean atBaseline = current.equals(expectedStored);
        boolean atTarget = current.equals(target);
        if (!atBaseline && !atTarget) {
            return new QIOTransferResult(transferId,
                  QIOTransferResult.Status.RECONCILIATION_CONFLICT,
                  existing == null ? requestedAmount : existing.requestedAmount, 0);
        }

        long alreadyInserted = atTarget ? transferred : 0;
        if (existing == null) {
            ensureCapacity();
        }
        if (existing == null && atTarget) {
            if (!persistence.getAsBoolean()) {
                return new QIOTransferResult(transferId, QIOTransferResult.Status.FAILED,
                      requestedAmount, 0);
            }
            receipts.put(transferId, new Receipt(digest, requestedAmount, requestedAmount));
            return new QIOTransferResult(transferId, QIOTransferResult.Status.REPLAYED,
                  requestedAmount, requestedAmount);
        }

        long missing = transferred - alreadyInserted;
        if (missing > 0) {
            long inserted = insertion.applyAsLong(missing);
            if (inserted < 0 || inserted > missing) {
                throw new IllegalStateException("QIO reconciled insertion returned an invalid amount");
            }
            if (inserted == 0) {
                return new QIOTransferResult(transferId, QIOTransferResult.Status.NO_CAPACITY,
                      existing == null ? requestedAmount : existing.requestedAmount, 0);
            }
            if (!persistence.getAsBoolean()) {
                return new QIOTransferResult(transferId, QIOTransferResult.Status.FAILED,
                      existing == null ? requestedAmount : existing.requestedAmount, 0);
            }
            if (inserted != missing) {
                return new QIOTransferResult(transferId, QIOTransferResult.Status.FAILED,
                      existing == null ? requestedAmount : existing.requestedAmount, 0);
            }
        } else if (!persistence.getAsBoolean()) {
            return new QIOTransferResult(transferId, QIOTransferResult.Status.FAILED,
                  existing == null ? requestedAmount : existing.requestedAmount, 0);
        }

        if (existing != null) {
            return new QIOTransferResult(transferId, QIOTransferResult.Status.REPLAYED,
                  existing.requestedAmount, existing.transferredAmount);
        }
        receipts.put(transferId, new Receipt(digest, requestedAmount, requestedAmount));
        return new QIOTransferResult(transferId, QIOTransferResult.Status.APPLIED,
              requestedAmount, requestedAmount);
    }

    void write(NBTTagCompound data) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, Receipt> entry : receipts.entrySet()) {
            NBTTagCompound receipt = new NBTTagCompound();
            receipt.setString("transferId", entry.getKey().toString());
            receipt.setString("digest", entry.getValue().digest);
            receipt.setLong("requestedAmount", entry.getValue().requestedAmount);
            receipt.setLong("transferredAmount", entry.getValue().transferredAmount);
            receipt.setBoolean("reconcilable", true);
            list.appendTag(receipt);
        }
        data.setTag(RECEIPTS, list);
    }

    void read(NBTTagCompound data) {
        receipts.clear();
        NBTTagList list = data.getTagList(RECEIPTS, NBT.TAG_COMPOUND);
        int count = Math.min(list.tagCount(), MAX_RECEIPTS);
        for (int index = 0; index < count; index++) {
            NBTTagCompound receipt = list.getCompoundTagAt(index);
            try {
                UUID transferId = UUID.fromString(receipt.getString("transferId"));
                String digest = receipt.getString("digest");
                long requested = receipt.getLong("requestedAmount");
                long transferred = receipt.getLong("transferredAmount");
                if (!receipt.hasKey("reconcilable", NBT.TAG_BYTE) ||
                      !receipt.getBoolean("reconcilable") || digest.isEmpty() || requested <= 0 ||
                      transferred <= 0 || transferred > requested) {
                    throw new IllegalArgumentException("Invalid QIO transfer receipt");
                }
                receipts.putIfAbsent(transferId, new Receipt(digest, requested, transferred));
            } catch (RuntimeException e) {
                QIOLog.LOGGER.error("Unable to restore a QIO transfer receipt", e);
            }
        }
        if (list.tagCount() > MAX_RECEIPTS) {
            QIOLog.LOGGER.error("QIO transfer receipt count {} exceeds the supported limit {}; excess receipts were isolated",
                  list.tagCount(), MAX_RECEIPTS);
        }
    }

    private void ensureCapacity() {
        if (receipts.size() >= MAX_RECEIPTS) {
            java.util.Iterator<UUID> iterator = receipts.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }

    private static final class Receipt {

        private final String digest;
        private final long requestedAmount;
        private final long transferredAmount;
        private Receipt(String digest, long requestedAmount, long transferredAmount) {
            this.digest = digest;
            this.requestedAmount = requestedAmount;
            this.transferredAmount = transferredAmount;
        }
    }
}
