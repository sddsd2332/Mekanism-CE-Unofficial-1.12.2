package mekanism.qioprocessing.common.terminal;

import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchClosureMode;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfigurationMutation;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Session API for server-owned, durable workbench encoding jobs.
 */
public final class QIOWorkbenchClosureService {
    public static final QIOWorkbenchClosureService INSTANCE = new QIOWorkbenchClosureService();

    public enum Status {
        READY, APPLIED, UNCHANGED, REVISION_CONFLICT, CATALOG_CHANGED, INVALID_TARGET, INVALID_PATTERN,
        READ_ONLY, ACCESS_DENIED, EXPIRED, TARGET_CHANGED, BUSY, UNAVAILABLE, PAUSED_RESTART, CANCELLED, FAILED
    }

    private QIOWorkbenchClosureService() {
    }

    public static void preview(QIOWorkbenchConfigurationService.Context context, QIOProcessingTerminalSession session,
                               UUID player, long configurationRevision, long catalogRevision, QIOWorkbenchConfigurationMutation mutation,
                               QIOWorkbenchClosureMode mode, boolean skipCycles, World world, long currentTick, Consumer<PreviewResult> completion) {
        QIOWorkbenchEncodingJobService.INSTANCE.start(context, session, player, configurationRevision,
                catalogRevision, mutation, mode, skipCycles, world, completion);
    }

    public static ConfirmResult confirm(QIOWorkbenchConfigurationService.Context context, QIOProcessingTerminalSession session,
                                        UUID player, UUID nonce, World world, long currentTick) {
        return QIOWorkbenchEncodingJobService.INSTANCE.confirm(context, session, player, nonce, world);
    }

    public static void shutdown() {
        QIOWorkbenchEncodingJobService.INSTANCE.shutdown();
    }

    public static final class PreviewResult {
        private final Status status;
        @Nullable
        private final QIOWorkbenchClosurePreview preview;

        PreviewResult(Status status, @Nullable QIOWorkbenchClosurePreview preview) {
            this.status = status;
            this.preview = preview;
        }

        public Status getStatus() {
            return status;
        }

        @Nullable
        public QIOWorkbenchClosurePreview getPreview() {
            return preview;
        }
    }

    public static final class ConfirmResult {
        private final Status status;

        ConfirmResult(Status status) {
            this.status = status;
        }

        public Status getStatus() {
            return status;
        }
    }
}
