package mekanism.qioprocessing.common.terminal;

import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkLifecycle;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfiguration;
import mekanism.qioprocessing.common.planning.QIORecipeCatalogService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Two-phase, server-side workbench configuration copy coordinator. */
/**
 * QIO 处理模块中的 QIOWorkbenchCopyService 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOWorkbenchCopyService {

    private static final long PREVIEW_LIFETIME_TICKS = 1_200;
    private static final int MAX_PENDING_PREVIEWS = 4_096;
    private static final Map<UUID, PendingCopy> PENDING = new LinkedHashMap<>();

    public enum Status {
        READY,
        APPLIED,
        UNCHANGED,
        ALREADY_IMPORTED,
        NOT_FOUND,
        ACCESS_DENIED,
        INVALID_TARGET,
        EXPIRED,
        SOURCE_CHANGED,
        TARGET_CHANGED,
        UNAVAILABLE
    }

    private QIOWorkbenchCopyService() {
    }

    @Nonnull
    /** 预览工作台配置复制并生成短期 token。 */
    public static synchronized PreviewResult preview(
          @Nonnull QIOWorkbenchConfigurationService.Context context,
          @Nonnull QIOProcessingTerminalSession session, @Nonnull UUID playerUUID,
          @Nonnull UUID sourceConfigUUID, long currentTick) {
        Objects.requireNonNull(context, "context");
        if (!QIORecipeCatalogService.INSTANCE.isReady()) {
            return new PreviewResult(Status.UNAVAILABLE, null);
        }
        return preview(context.getNetwork(), context.isEditable(), session, playerUUID,
              sourceConfigUUID, currentTick,
              QIOProcessingNetworkManager.INSTANCE::getByWorkbenchConfigUUID,
              QIOWorkbenchCopyService::canRead);
    }

    static synchronized PreviewResult preview(@Nonnull QIOProcessingNetworkData target,
          boolean editable, @Nonnull QIOProcessingTerminalSession session,
          @Nonnull UUID playerUUID, @Nonnull UUID sourceConfigUUID, long currentTick,
          @Nonnull NetworkResolver resolver, @Nonnull AccessChecker accessChecker) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(playerUUID, "playerUUID");
        Objects.requireNonNull(sourceConfigUUID, "sourceConfigUUID");
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(accessChecker, "accessChecker");
        cleanup(currentTick);
        if (!editable || !session.getPlayerUUID().equals(playerUUID)) {
            return new PreviewResult(Status.ACCESS_DENIED, null);
        }
        QIOWorkbenchConfiguration targetConfiguration = target.getWorkbenchConfiguration();
        if (sourceConfigUUID.equals(targetConfiguration.getConfigUUID())) {
            return new PreviewResult(Status.INVALID_TARGET, null);
        }
        QIOProcessingNetworkData source;
        try {
            source = resolver.resolve(sourceConfigUUID);
        } catch (IllegalStateException e) {
            return new PreviewResult(Status.INVALID_TARGET, null);
        }
        if (source == null || source.getLifecycle() != QIOProcessingNetworkLifecycle.ACTIVE) {
            return new PreviewResult(Status.NOT_FOUND, null);
        }
        if (!accessChecker.canRead(source, playerUUID)) {
            return new PreviewResult(Status.ACCESS_DENIED, null);
        }
        QIOWorkbenchConfiguration sourceConfiguration = source.getWorkbenchConfiguration();
        String digest = sourceConfiguration.contentDigest();
        if (targetConfiguration.importedFrom(sourceConfiguration.getConfigUUID(),
              sourceConfiguration.getRevision(), digest)) {
            return new PreviewResult(Status.ALREADY_IMPORTED, null);
        }
        UUID confirmationNonce = UUID.randomUUID();
        PendingCopy pending = new PendingCopy(confirmationNonce, session.getSessionNonce(),
              playerUUID, target.getFrequencyUUID(), targetConfiguration.getConfigUUID(),
              targetConfiguration.getRevision(), sourceConfiguration.getConfigUUID(),
              sourceConfiguration.getRevision(), digest, expiresAt(currentTick));
        if (PENDING.size() >= MAX_PENDING_PREVIEWS) {
            Iterator<UUID> iterator = PENDING.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        PENDING.put(confirmationNonce, pending);
        QIOWorkbenchCopyPreview preview = new QIOWorkbenchCopyPreview(confirmationNonce,
              sourceConfiguration.getConfigUUID(), sourceConfiguration.getOriginUUID(),
              targetConfiguration.getConfigUUID(), sourceConfiguration.getRevision(),
              targetConfiguration.getRevision(), digest,
              source.getLastKnownFrequencyIdentity().getName(),
              sourceConfiguration.getProductOverrideCount(),
              sourceConfiguration.getEncodedPatternCount(),
              sourceConfiguration.getIngredientOverrideCount());
        return new PreviewResult(Status.READY, preview);
    }

    @Nonnull
    /** 确认带 token 的工作台配置复制。 */
    public static synchronized ConfirmResult confirm(
          @Nonnull QIOWorkbenchConfigurationService.Context context,
          @Nonnull QIOProcessingTerminalSession session, @Nonnull UUID playerUUID,
          @Nonnull UUID confirmationNonce, long currentTick) {
        Objects.requireNonNull(context, "context");
        if (!QIORecipeCatalogService.INSTANCE.isReady()) {
            return new ConfirmResult(Status.UNAVAILABLE,
                  context.getNetwork().getWorkbenchConfiguration().getRevision());
        }
        return confirm(context.getNetwork(), context.isEditable(), session, playerUUID,
              confirmationNonce, currentTick,
              QIOProcessingNetworkManager.INSTANCE::getByWorkbenchConfigUUID,
              QIOWorkbenchCopyService::canRead);
    }

    static synchronized ConfirmResult confirm(@Nonnull QIOProcessingNetworkData target,
          boolean editable, @Nonnull QIOProcessingTerminalSession session,
          @Nonnull UUID playerUUID, @Nonnull UUID confirmationNonce, long currentTick,
          @Nonnull NetworkResolver resolver, @Nonnull AccessChecker accessChecker) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(playerUUID, "playerUUID");
        Objects.requireNonNull(confirmationNonce, "confirmationNonce");
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(accessChecker, "accessChecker");
        cleanup(currentTick);
        PendingCopy pending = PENDING.remove(confirmationNonce);
        if (pending == null || pending.expiresAt < currentTick) {
            return result(Status.EXPIRED, target);
        }
        if (!editable || !pending.playerUUID.equals(playerUUID) ||
            !pending.sessionNonce.equals(session.getSessionNonce())) {
            return result(Status.ACCESS_DENIED, target);
        }
        QIOWorkbenchConfiguration targetConfiguration = target.getWorkbenchConfiguration();
        if (!pending.targetFrequencyUUID.equals(target.getFrequencyUUID()) ||
            !pending.targetConfigUUID.equals(targetConfiguration.getConfigUUID()) ||
            pending.targetRevision != targetConfiguration.getRevision()) {
            return result(Status.TARGET_CHANGED, target);
        }
        QIOProcessingNetworkData source;
        try {
            source = resolver.resolve(pending.sourceConfigUUID);
        } catch (IllegalStateException e) {
            return result(Status.INVALID_TARGET, target);
        }
        if (source == null || source.getLifecycle() != QIOProcessingNetworkLifecycle.ACTIVE) {
            return result(Status.NOT_FOUND, target);
        }
        if (!accessChecker.canRead(source, playerUUID)) {
            return result(Status.ACCESS_DENIED, target);
        }
        QIOWorkbenchConfiguration sourceConfiguration = source.getWorkbenchConfiguration();
        if (pending.sourceRevision != sourceConfiguration.getRevision() ||
            !pending.sourceDigest.equals(sourceConfiguration.contentDigest())) {
            return result(Status.SOURCE_CHANGED, target);
        }
        long before = targetConfiguration.getRevision();
        boolean changed = targetConfiguration.replaceFrom(sourceConfiguration);
        target.markWorkbenchConfigurationChanged(before);
        return new ConfirmResult(changed ? Status.APPLIED : Status.UNCHANGED,
              targetConfiguration.getRevision());
    }

    private static ConfirmResult result(Status status, QIOProcessingNetworkData target) {
        return new ConfirmResult(status,
              target.getWorkbenchConfiguration().getRevision());
    }

    private static boolean canRead(QIOProcessingNetworkData source, UUID playerUUID) {
        QIOFrequencyIdentitySnapshot identity = source.getLastKnownFrequencyIdentity();
        QIOFrequencyReference reference = new QIOFrequencyReference(source.getFrequencyUUID(),
              identity.getName(), identity.getOwnerUUID(), identity.getSecurityMode(),
              playerUUID);
        return QIOFrequencyStorageAccess.INSTANCE.canAccess(reference, playerUUID);
    }

    private static void cleanup(long currentTick) {
        PENDING.values().removeIf(pending -> pending.expiresAt < currentTick);
    }

    private static long expiresAt(long currentTick) {
        return currentTick > Long.MAX_VALUE - PREVIEW_LIFETIME_TICKS ? Long.MAX_VALUE :
              currentTick + PREVIEW_LIFETIME_TICKS;
    }

    @FunctionalInterface
    interface NetworkResolver {

        @Nullable
        QIOProcessingNetworkData resolve(UUID configUUID);
    }

    @FunctionalInterface
    interface AccessChecker {

        boolean canRead(QIOProcessingNetworkData network, UUID playerUUID);
    }

    public static final class PreviewResult {

        private final Status status;
        @Nullable private final QIOWorkbenchCopyPreview preview;

        private PreviewResult(Status status, @Nullable QIOWorkbenchCopyPreview preview) {
            this.status = status;
            this.preview = preview;
        }

        @Nonnull public Status getStatus() { return status; }
        @Nullable public QIOWorkbenchCopyPreview getPreview() { return preview; }
    }

    public static final class ConfirmResult {

        private final Status status;
        private final long targetRevision;

        private ConfirmResult(Status status, long targetRevision) {
            this.status = status;
            this.targetRevision = targetRevision;
        }

        @Nonnull public Status getStatus() { return status; }
        public long getTargetRevision() { return targetRevision; }
    }

    private static final class PendingCopy {

        private final UUID confirmationNonce;
        private final UUID sessionNonce;
        private final UUID playerUUID;
        private final UUID targetFrequencyUUID;
        private final UUID targetConfigUUID;
        private final long targetRevision;
        private final UUID sourceConfigUUID;
        private final long sourceRevision;
        private final String sourceDigest;
        private final long expiresAt;

        private PendingCopy(UUID confirmationNonce, UUID sessionNonce, UUID playerUUID,
              UUID targetFrequencyUUID, UUID targetConfigUUID, long targetRevision,
              UUID sourceConfigUUID, long sourceRevision, String sourceDigest,
              long expiresAt) {
            this.confirmationNonce = confirmationNonce;
            this.sessionNonce = sessionNonce;
            this.playerUUID = playerUUID;
            this.targetFrequencyUUID = targetFrequencyUUID;
            this.targetConfigUUID = targetConfigUUID;
            this.targetRevision = targetRevision;
            this.sourceConfigUUID = sourceConfigUUID;
            this.sourceRevision = sourceRevision;
            this.sourceDigest = sourceDigest;
            this.expiresAt = expiresAt;
        }
    }
}
