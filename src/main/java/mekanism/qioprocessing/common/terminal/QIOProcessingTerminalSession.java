package mekanism.qioprocessing.common.terminal;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** Server-owned capability token for one open block or portable terminal container. */
public final class QIOProcessingTerminalSession {

    public enum TargetKind {
        BLOCK,
        PORTABLE_ITEM
    }

    public enum Validation {
        ACCEPTED,
        CLOSED,
        NONCE_MISMATCH,
        PLAYER_MISMATCH,
        TARGET_KIND_MISMATCH,
        TERMINAL_TYPE_MISMATCH,
        TERMINAL_ID_MISMATCH,
        TARGET_REVISION_MISMATCH,
        FREQUENCY_MISMATCH,
        ACCESS_REVISION_MISMATCH
    }

    private final UUID sessionNonce;
    private final UUID playerUUID;
    private final TargetKind targetKind;
    private final QIOProcessingTerminalType terminalType;
    private final UUID terminalUUID;
    private long targetRevision;
    @Nullable
    private UUID frequencyUUID;
    private long accessRevision;
    private boolean open = true;

    public QIOProcessingTerminalSession(@Nonnull UUID playerUUID,
          @Nonnull TargetKind targetKind, @Nonnull QIOProcessingTerminalType terminalType,
          @Nonnull UUID terminalUUID, long targetRevision, @Nullable UUID frequencyUUID,
          long accessRevision) {
        this(UUID.randomUUID(), playerUUID, targetKind, terminalType, terminalUUID,
              targetRevision, frequencyUUID, accessRevision);
    }

    QIOProcessingTerminalSession(@Nonnull UUID sessionNonce, @Nonnull UUID playerUUID,
          @Nonnull TargetKind targetKind, @Nonnull QIOProcessingTerminalType terminalType,
          @Nonnull UUID terminalUUID, long targetRevision, @Nullable UUID frequencyUUID,
          long accessRevision) {
        this.sessionNonce = Objects.requireNonNull(sessionNonce, "sessionNonce");
        this.playerUUID = Objects.requireNonNull(playerUUID, "playerUUID");
        this.targetKind = Objects.requireNonNull(targetKind, "targetKind");
        this.terminalType = Objects.requireNonNull(terminalType, "terminalType");
        this.terminalUUID = Objects.requireNonNull(terminalUUID, "terminalUUID");
        this.targetRevision = requireRevision(targetRevision, "targetRevision");
        validateFrequencyRevision(frequencyUUID, accessRevision);
        this.frequencyUUID = frequencyUUID;
        this.accessRevision = accessRevision;
    }

    @Nonnull
    public UUID getSessionNonce() {
        return sessionNonce;
    }

    @Nonnull
    public UUID getPlayerUUID() {
        return playerUUID;
    }

    @Nonnull
    public TargetKind getTargetKind() {
        return targetKind;
    }

    @Nonnull
    public QIOProcessingTerminalType getTerminalType() {
        return terminalType;
    }

    @Nonnull
    public UUID getTerminalUUID() {
        return terminalUUID;
    }

    public long getTargetRevision() {
        return targetRevision;
    }

    @Nullable
    public UUID getFrequencyUUID() {
        return frequencyUUID;
    }

    public long getAccessRevision() {
        return accessRevision;
    }

    public boolean isOpen() {
        return open;
    }

    @Nonnull
    public Validation validate(@Nonnull UUID requestNonce, @Nonnull UUID requestPlayerUUID,
          @Nonnull TargetKind requestTargetKind,
          @Nonnull QIOProcessingTerminalType requestTerminalType,
          @Nonnull UUID requestTerminalUUID, long requestTargetRevision,
          @Nullable UUID requestFrequencyUUID, long currentAccessRevision) {
        if (!open) {
            return Validation.CLOSED;
        } else if (!sessionNonce.equals(requestNonce)) {
            return Validation.NONCE_MISMATCH;
        } else if (!playerUUID.equals(requestPlayerUUID)) {
            return Validation.PLAYER_MISMATCH;
        } else if (targetKind != requestTargetKind) {
            return Validation.TARGET_KIND_MISMATCH;
        } else if (terminalType != requestTerminalType) {
            return Validation.TERMINAL_TYPE_MISMATCH;
        } else if (!terminalUUID.equals(requestTerminalUUID)) {
            return Validation.TERMINAL_ID_MISMATCH;
        } else if (targetRevision != requestTargetRevision) {
            return Validation.TARGET_REVISION_MISMATCH;
        } else if (!Objects.equals(frequencyUUID, requestFrequencyUUID)) {
            return Validation.FREQUENCY_MISMATCH;
        } else if (accessRevision != currentAccessRevision) {
            return Validation.ACCESS_REVISION_MISMATCH;
        }
        return Validation.ACCEPTED;
    }

    public void advanceTargetRevision(long expectedRevision, long updatedRevision) {
        if (!open || targetRevision != expectedRevision) {
            throw new IllegalStateException("QIO terminal session target revision changed");
        }
        if (updatedRevision <= targetRevision) {
            throw new IllegalArgumentException("Updated target revision must advance");
        }
        targetRevision = updatedRevision;
    }

    public void rebind(long expectedTargetRevision, long updatedTargetRevision,
          @Nullable UUID updatedFrequencyUUID, long updatedAccessRevision) {
        validateFrequencyRevision(updatedFrequencyUUID, updatedAccessRevision);
        advanceTargetRevision(expectedTargetRevision, updatedTargetRevision);
        frequencyUUID = updatedFrequencyUUID;
        accessRevision = updatedAccessRevision;
    }

    public void close() {
        open = false;
    }

    private static long requireRevision(long revision, String name) {
        if (revision < 0) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
        return revision;
    }

    private static void validateFrequencyRevision(@Nullable UUID frequencyUUID,
          long accessRevision) {
        if (frequencyUUID == null ? accessRevision != -1 : accessRevision < 0) {
            throw new IllegalArgumentException("Frequency and access revision do not match");
        }
    }
}
