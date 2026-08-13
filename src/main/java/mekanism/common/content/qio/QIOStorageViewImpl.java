package mekanism.common.content.qio;

import mekanism.api.Action;
import mekanism.api.gas.GasStack;
import mekanism.api.qio.external.IQIOStorageListener;
import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOClaimRequest;
import mekanism.api.qio.external.QIOClaimResult;
import mekanism.api.qio.external.QIOClaimBacking;
import mekanism.api.qio.external.QIOResourceClaim;
import mekanism.api.qio.external.QIOStorageChangeBatch;
import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.api.qio.external.QIOStorageSnapshot;
import mekanism.api.qio.external.QIOTransferResult;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Shared lifecycle and listener implementation for dashboard and frequency-reference views. */
final class QIOStorageViewImpl implements IQIOStorageView {

    private final QIOFrequency frequency;
    private final BooleanSupplier ownerValidity;
    @Nullable
    private final Consumer<QIOStorageViewImpl> trackingStarted;
    @Nullable
    private final Consumer<QIOStorageViewImpl> trackingStopped;
    private final Set<IQIOStorageListener> listeners =
          Collections.newSetFromMap(new IdentityHashMap<>());
    private final IQIOStorageListener frequencyListener = this::onFrequencyChanged;
    private boolean listening;
    private boolean closed;

    QIOStorageViewImpl(@Nonnull QIOFrequency frequency, @Nonnull BooleanSupplier ownerValidity,
          @Nullable Consumer<QIOStorageViewImpl> trackingStarted,
          @Nullable Consumer<QIOStorageViewImpl> trackingStopped) {
        this.frequency = frequency;
        this.ownerValidity = ownerValidity;
        this.trackingStarted = trackingStarted;
        this.trackingStopped = trackingStopped;
    }

    @Nonnull
    @Override
    public UUID getFrequencyUUID() {
        return frequency.getFrequencyUUID();
    }

    @Nonnull
    @Override
    public String getFrequencyName() {
        return frequency.getName();
    }

    @Override
    public long getContentsRevision() {
        return frequency.getContentsRevision();
    }

    @Override
    public long getCapacityRevision() {
        return frequency.getCapacityRevision();
    }

    @Override
    public long getClaimRevision() {
        return frequency.getClaimRevision();
    }

    @Override
    public long getAccessRevision() {
        return frequency.getAccessRevision();
    }

    @Nonnull
    @Override
    public QIOStorageSnapshot getSnapshot() {
        requireValid();
        return frequency.getExternalStorageSnapshot();
    }

    @Nullable
    @Override
    public QIOStorageEntry getResource(UUID resourceUUID) {
        return isCurrentlyValid() ? frequency.getExternalStorageEntry(resourceUUID) : null;
    }

    @Nullable
    @Override
    public QIOResourceClaim getClaim(UUID claimId) {
        return isCurrentlyValid() ? frequency.getResourceClaim(claimId) : null;
    }

    @Nullable
    @Override
    public QIOClaimBacking getClaimBacking(UUID claimId) {
        return isCurrentlyValid() ? frequency.getResourceClaimBacking(claimId) : null;
    }

    @Nonnull
    @Override
    public QIOClaimResult submitClaim(QIOClaimRequest request) {
        requireValid();
        return frequency.submitClaimRequest(request);
    }

    @Override
    public long insert(ItemStack stack, long amount, Action action) {
        return isCurrentlyValid() ? frequency.massInsert(
              stack == null ? ItemStack.EMPTY : stack.copy(), amount, action) : 0;
    }

    @Override
    public long insert(FluidStack stack, long amount, Action action) {
        return isCurrentlyValid() && stack != null ?
              frequency.massInsert(stack.copy(), amount, action) : 0;
    }

    @Override
    public long insert(GasStack stack, long amount, Action action) {
        return isCurrentlyValid() && stack != null ?
              frequency.massInsert(stack.copy(), amount, action) : 0;
    }

    @Nonnull
    @Override
    public QIOTransferResult insertIdempotent(@Nonnull UUID transferId, ItemStack stack,
          long amount, @Nonnull BigInteger expectedStoredAmount) {
        return isCurrentlyValid() ? frequency.insertExternalTransfer(transferId, stack, amount,
              expectedStoredAmount) : new QIOTransferResult(transferId,
              QIOTransferResult.Status.FAILED, Math.max(1, amount), 0);
    }

    @Nonnull
    @Override
    public QIOTransferResult insertIdempotent(@Nonnull UUID transferId, FluidStack stack,
          long amount, @Nonnull BigInteger expectedStoredAmount) {
        return isCurrentlyValid() ? frequency.insertExternalTransfer(transferId, stack, amount,
              expectedStoredAmount) : new QIOTransferResult(transferId,
              QIOTransferResult.Status.FAILED, Math.max(1, amount), 0);
    }

    @Nonnull
    @Override
    public QIOTransferResult insertIdempotent(@Nonnull UUID transferId, GasStack stack,
          long amount, @Nonnull BigInteger expectedStoredAmount) {
        return isCurrentlyValid() ? frequency.insertExternalTransfer(transferId, stack, amount,
              expectedStoredAmount) : new QIOTransferResult(transferId,
              QIOTransferResult.Status.FAILED, Math.max(1, amount), 0);
    }

    @Override
    public long extract(ItemStack stack, long amount, Action action) {
        return isCurrentlyValid() ? frequency.massExtract(
              stack == null ? ItemStack.EMPTY : stack.copy(), amount, action) : 0;
    }

    @Override
    public long extract(FluidStack stack, long amount, Action action) {
        return isCurrentlyValid() && stack != null ?
              frequency.massExtract(stack.copy(), amount, action) : 0;
    }

    @Override
    public long extract(GasStack stack, long amount, Action action) {
        return isCurrentlyValid() && stack != null ?
              frequency.massExtract(stack.copy(), amount, action) : 0;
    }

    @Override
    public boolean addListener(IQIOStorageListener listener) {
        if (listener == null || closed || !isCurrentlyValid() || !listeners.add(listener)) {
            return false;
        }
        if (!listening) {
            listening = frequency.addExternalStorageListener(frequencyListener);
            if (!listening) {
                listeners.remove(listener);
                return false;
            }
            if (trackingStarted != null) {
                trackingStarted.accept(this);
            }
        }
        return true;
    }

    @Override
    public boolean removeListener(IQIOStorageListener listener) {
        if (listener == null || !listeners.remove(listener)) {
            return false;
        }
        stopListeningIfUnused();
        return true;
    }

    @Override
    public boolean isValid() {
        return isCurrentlyValid();
    }

    @Override
    public void close() {
        invalidate(false);
    }

    boolean isCurrentlyValid() {
        return !closed && frequency.isValid() && !frequency.isRemoved() &&
              QIOStorageManager.isLoaded() && ownerValidity.getAsBoolean();
    }

    void invalidate(boolean notify) {
        if (closed) {
            return;
        }
        closed = true;
        if (listening) {
            frequency.removeExternalStorageListener(frequencyListener);
            listening = false;
            if (trackingStopped != null) {
                trackingStopped.accept(this);
            }
        }
        if (notify && !listeners.isEmpty()) {
            QIOStorageChangeBatch invalidation = QIOStorageChangeBatch.invalidated(
                  frequency.getContentsRevision(), frequency.getCapacityRevision(),
                  frequency.getClaimRevision(), frequency.getAccessRevision());
            for (IQIOStorageListener listener : new ArrayList<>(listeners)) {
                try {
                    listener.onQIOStorageChanged(invalidation);
                } catch (RuntimeException e) {
                    QIOLog.LOGGER.error("A QIO storage view listener failed during invalidation", e);
                }
            }
        }
        listeners.clear();
    }

    private void requireValid() {
        if (!isCurrentlyValid()) {
            throw new IllegalStateException("QIO storage view is no longer valid");
        }
    }

    private void onFrequencyChanged(QIOStorageChangeBatch changes) {
        if (changes.isInvalidated() || !isCurrentlyValid()) {
            invalidate(true);
            return;
        }
        for (IQIOStorageListener listener : new ArrayList<>(listeners)) {
            if (!listeners.contains(listener)) {
                continue;
            }
            try {
                listener.onQIOStorageChanged(changes);
            } catch (RuntimeException e) {
                listeners.remove(listener);
                QIOLog.LOGGER.error("Removing a failing listener from a QIO storage view", e);
            }
        }
        stopListeningIfUnused();
    }

    private void stopListeningIfUnused() {
        if (listeners.isEmpty() && listening) {
            frequency.removeExternalStorageListener(frequencyListener);
            listening = false;
            if (trackingStopped != null) {
                trackingStopped.accept(this);
            }
        }
    }
}
