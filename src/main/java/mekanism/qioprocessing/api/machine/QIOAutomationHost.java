package mekanism.qioprocessing.api.machine;

import mekanism.api.processing.QIOAutomationMode;
import mekanism.api.qio.external.QIOFrequencyReference;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative, persistent QIO automation state attached to an admitted machine tile. */
public interface QIOAutomationHost extends INBTSerializable<NBTTagCompound> {

    enum State {
        UNBOUND,
        ACTIVE,
        DRAINING_CHANGE,
        WAITING_ACCESS,
        IDENTITY_CONFLICT,
        DATA_ERROR;

        public boolean acceptsNewOperations() {
            return this == ACTIVE;
        }
    }

    @Nonnull
    UUID getPersistentDeviceUUID();

    @Nullable
    QIOFrequencyReference getFrequencyReference();

    @Nullable
    QIOAutomationMode getEnabledMode();

    @Nonnull
    State getState();

    long getConfigurationRevision();

    default boolean isManagementPaused() {
        return false;
    }

    default boolean setManagementPaused(boolean paused) {
        return false;
    }

    /** Applies an already authorized mode selection made by the machine upgrade inventory. */
    boolean selectMode(@Nonnull QIOAutomationMode mode);

    /** Clears the selected mode after the last QIO automation upgrade is removed. */
    boolean clearMode();

    /**
     * Clears the selected mode, optionally removing the public frequency binding before
     * already-owned operations have finished draining.
     */
    default boolean clearMode(boolean detachFrequencyImmediately) {
        return clearMode();
    }

    @Nonnull
    Map<UUID, MachineOperationLease> getLeases();

    @Nonnull
    Map<UUID, MachineOperationToken> getOperationTokens();

    @Nonnull
    Map<Long, MachineActivitySnapshot> getActivitySnapshots();

    @Nonnull
    Map<UUID, QIOOutputBufferEntry> getOutputBufferEntries();

    @Nullable
    MachineOperationLease tryAcquireLease(@Nonnull UUID leaseId, @Nonnull UUID ownerOperationId,
          @Nonnull MachineOperationLease.Mode mode, long laneId, long createdAt,
          @Nonnull Collection<MachinePortBaseline> baselines);

    boolean attachOperationToken(@Nonnull MachineOperationToken token);

    boolean transitionOperation(@Nonnull UUID operationId, @Nonnull MachineOperationToken.State tokenState,
          @Nonnull MachineOperationLease.State leaseState);

    boolean recordTransferReceipt(@Nonnull UUID operationId, @Nonnull UUID transferId);

    /** True only after the endpoint snapshot containing this receipt reached persistent storage. */
    default boolean isTransferReceiptPersisted(@Nonnull UUID operationId,
          @Nonnull UUID transferId) {
        return false;
    }

    /** Called only after the endpoint snapshot containing the receipt reached storage. */
    default void confirmTransferReceiptPersisted(@Nonnull UUID operationId,
          @Nonnull UUID transferId) {
    }

    /** Called only after a completed/released operation snapshot reached storage. */
    default void confirmCompletedOperationPersisted(@Nonnull UUID operationId) {
    }

    boolean contaminateLease(@Nonnull UUID leaseId, @Nonnull String reason);

    /**
     * Relinquishes a scheduled job operation without claiming an output or refunding inputs
     * that may already have entered the physical machine.
     */
    boolean abandonJobOperation(@Nonnull UUID operationId);

    /** Releases an ACQUIRED lease when no operation token was attached. */
    boolean releaseUnattachedLease(@Nonnull UUID leaseId);

    /** Removes a terminal token and its released lease after every transfer is settled. */
    boolean forgetSettledOperation(@Nonnull UUID operationId);

    void updateActivitySnapshot(@Nonnull MachineActivitySnapshot snapshot);
}
