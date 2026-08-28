package mekanism.qioprocessing.common.terminal;

import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchClosureMode;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfiguration;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfigurationMutation;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfigurationMutation.Action;
import mekanism.qioprocessing.common.planning.QIOPlanningExecutor;
import mekanism.qioprocessing.common.planning.QIOPlanningService;
import mekanism.qioprocessing.common.planning.QIORecipeCatalogService;
import mekanism.qioprocessing.common.planning.QIOWorkbenchRecipeCatalog;
import mekanism.qioprocessing.common.planning.QIOWorkbenchRecipeCatalog.TargetedRecipeLookupBusyException;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Asynchronous, two-phase coordinator for rooted workbench dependency imports. */
/**
 * QIO 处理模块中的 QIOWorkbenchClosureService 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOWorkbenchClosureService {

    public static final QIOWorkbenchClosureService INSTANCE =
          new QIOWorkbenchClosureService();

    private static final long PREVIEW_LIFETIME_TICKS = 1_200;
    private static final int MAX_PENDING_PREVIEWS = 4_096;
    private static final int MAX_ACTIVE_TRAVERSALS = 16;
    private static final int WORKER_LOOKUP_STEPS = 1_024;
    private static final Map<UUID, PendingClosure> PENDING = new LinkedHashMap<>();
    private static int activeTraversals;

    public enum Status {
        READY,
        APPLIED,
        UNCHANGED,
        REVISION_CONFLICT,
        CATALOG_CHANGED,
        INVALID_TARGET,
        INVALID_PATTERN,
        READ_ONLY,
        ACCESS_DENIED,
        EXPIRED,
        TARGET_CHANGED,
        BUSY,
        UNAVAILABLE
    }

    private QIOWorkbenchClosureService() {
    }

    /**
     * Resolves the selected root on the server thread, then performs every downstream lookup
     * against the immutable global workbench directory on a QIO planning worker.
     */
    public static void preview(@Nonnull QIOWorkbenchConfigurationService.Context context,
          @Nonnull QIOProcessingTerminalSession session, @Nonnull UUID playerUUID,
          long expectedConfigurationRevision, long expectedCatalogRevision,
          @Nonnull QIOWorkbenchConfigurationMutation mutation,
          @Nonnull QIOWorkbenchClosureMode mode, boolean skipCyclicRecipes,
          @Nonnull World world, long currentTick,
          @Nonnull Consumer<PreviewResult> completion) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(playerUUID, "playerUUID");
        Objects.requireNonNull(mutation, "mutation");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(completion, "completion");
        if (!QIORecipeCatalogService.INSTANCE.isReady() ||
            !QIORecipeCatalogService.INSTANCE.allowsRecursiveImport()) {
            completion.accept(new PreviewResult(Status.UNAVAILABLE, null));
            return;
        }
        QIOProcessingNetworkData network = context.getNetwork();
        QIOWorkbenchConfiguration configuration = network.getWorkbenchConfiguration();
        if (!context.isEditable()) {
            completion.accept(new PreviewResult(Status.READ_ONLY, null));
            return;
        }
        if (!session.getPlayerUUID().equals(playerUUID)) {
            completion.accept(new PreviewResult(Status.ACCESS_DENIED, null));
            return;
        }
        if (configuration.getRevision() != expectedConfigurationRevision) {
            completion.accept(new PreviewResult(Status.REVISION_CONFLICT, null));
            return;
        }
        if (context.getCatalogRevision() != expectedCatalogRevision) {
            completion.accept(new PreviewResult(Status.CATALOG_CHANGED, null));
            return;
        }
        if (mode == QIOWorkbenchClosureMode.NONE ||
            mutation.getAction() != Action.ENCODE_PATTERN &&
            mutation.getAction() != Action.ENCODE_TARGETS) {
            completion.accept(new PreviewResult(Status.INVALID_TARGET, null));
            return;
        }

        final List<QIOWorkbenchConfiguration.EncodedPattern> roots;
        final QIOWorkbenchRecipeCatalog.ClosureCapture capture;
        try {
            roots = resolveRoots(world, mutation);
            capture = QIOWorkbenchRecipeCatalog.beginClosureCapture(world, roots,
                  configuration, mode, skipCyclicRecipes);
        } catch (TargetedRecipeLookupBusyException e) {
            completion.accept(new PreviewResult(Status.BUSY, null));
            return;
        } catch (IllegalArgumentException | IllegalStateException e) {
            completion.accept(new PreviewResult(Status.INVALID_PATTERN, null));
            return;
        }

        UUID configUUID = configuration.getConfigUUID();
        UUID frequencyUUID = network.getFrequencyUUID();
        UUID sessionNonce = session.getSessionNonce();
        Action rootAction = mutation.getAction();
        CaptureJob job = new CaptureJob(capture, network, rootAction, sessionNonce,
              playerUUID, frequencyUUID, configUUID, expectedConfigurationRevision,
              expectedCatalogRevision, world, expiresAt(currentTick), completion);
        synchronized (QIOWorkbenchClosureService.class) {
            if (activeTraversals >= MAX_ACTIVE_TRAVERSALS) {
                completion.accept(new PreviewResult(Status.UNAVAILABLE, null));
                return;
            }
            activeTraversals++;
        }
        submitTraversal(job);
    }

    public static synchronized void shutdown() {
        PENDING.clear();
        activeTraversals = 0;
    }

    private static void submitTraversal(CaptureJob job) {
        QIOWorkbenchConfiguration current = job.network.getWorkbenchConfiguration();
        if (!current.getConfigUUID().equals(job.configUUID) ||
            !job.network.getFrequencyUUID().equals(job.frequencyUUID) ||
            current.getRevision() != job.configurationRevision) {
            releaseTraversal();
            job.completion.accept(new PreviewResult(Status.REVISION_CONFLICT, null));
            return;
        }
        if (QIORecipeCatalogService.INSTANCE.getRevision(current) != job.catalogRevision) {
            releaseTraversal();
            job.completion.accept(new PreviewResult(Status.CATALOG_CHANGED, null));
            return;
        }
        QIOPlanningExecutor executor = QIOPlanningService.INSTANCE.getExecutor();
        if (executor == null) {
            releaseTraversal();
            job.completion.accept(new PreviewResult(Status.UNAVAILABLE, null));
            return;
        }
        if (job.capture == null) {
            releaseTraversal();
            job.completion.accept(new PreviewResult(Status.INVALID_PATTERN, null));
            return;
        }
        QIOWorkbenchRecipeCatalog.ClosureCapture capture = job.capture;
        job.capture = null;
        QIOPlanningExecutor.Submission submission = executor.submit(capture,
              (prepared, cancellation) -> {
                  while (!prepared.process(WORKER_LOOKUP_STEPS, Long.MAX_VALUE)) {
                      if (cancellation.isCancelled()) return null;
                  }
                  if (cancellation.isCancelled()) return null;
                  return QIOWorkbenchRecipeCatalog.resolveClosure(prepared.finish(),
                        cancellation::isCancelled);
              }, result -> {
                  releaseTraversal();
                  if (result.getStatus() != QIOPlanningExecutor.ResultStatus.SUCCESS ||
                      result.getValue() == null) {
                      job.completion.accept(new PreviewResult(Status.UNAVAILABLE, null));
                      return;
                  }
                  QIOWorkbenchConfiguration latest =
                        job.network.getWorkbenchConfiguration();
                  if (!latest.getConfigUUID().equals(job.configUUID) ||
                      !job.network.getFrequencyUUID().equals(job.frequencyUUID) ||
                      latest.getRevision() != job.configurationRevision) {
                      job.completion.accept(new PreviewResult(
                            Status.REVISION_CONFLICT, null));
                      return;
                  }
                  if (QIORecipeCatalogService.INSTANCE.getRevision(latest) !=
                      job.catalogRevision) {
                      job.completion.accept(new PreviewResult(Status.CATALOG_CHANGED, null));
                      return;
                  }
                  job.completion.accept(register(result.getValue(), job.rootAction,
                        job.sessionNonce, job.playerUUID, job.frequencyUUID, job.configUUID,
                        job.configurationRevision, job.catalogRevision,
                        job.world.getTotalWorldTime()));
              });
        if (!submission.isAccepted()) {
            releaseTraversal();
            job.completion.accept(new PreviewResult(Status.UNAVAILABLE, null));
        }
    }

    private static synchronized void releaseTraversal() {
        activeTraversals = Math.max(0, activeTraversals - 1);
    }

    @Nonnull
    public static synchronized ConfirmResult confirm(
          @Nonnull QIOWorkbenchConfigurationService.Context context,
          @Nonnull QIOProcessingTerminalSession session, @Nonnull UUID playerUUID,
          @Nonnull UUID confirmationNonce, @Nonnull World world, long currentTick) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(playerUUID, "playerUUID");
        Objects.requireNonNull(confirmationNonce, "confirmationNonce");
        Objects.requireNonNull(world, "world");
        if (!QIORecipeCatalogService.INSTANCE.isReady() ||
            !QIORecipeCatalogService.INSTANCE.allowsRecursiveImport()) {
            return new ConfirmResult(Status.UNAVAILABLE);
        }
        cleanup(currentTick);
        PendingClosure pending = PENDING.remove(confirmationNonce);
        if (pending == null || pending.expiresAt < currentTick) {
            return new ConfirmResult(Status.EXPIRED);
        }
        if (!context.isEditable() || !pending.playerUUID.equals(playerUUID) ||
            !pending.sessionNonce.equals(session.getSessionNonce())) {
            return new ConfirmResult(Status.ACCESS_DENIED);
        }
        QIOProcessingNetworkData network = context.getNetwork();
        QIOWorkbenchConfiguration configuration = network.getWorkbenchConfiguration();
        if (!pending.frequencyUUID.equals(network.getFrequencyUUID()) ||
            !pending.configUUID.equals(configuration.getConfigUUID()) ||
            pending.configurationRevision != configuration.getRevision()) {
            return new ConfirmResult(Status.TARGET_CHANGED);
        }
        if (pending.catalogRevision !=
            QIORecipeCatalogService.INSTANCE.getRevision(configuration)) {
            return new ConfirmResult(Status.CATALOG_CHANGED);
        }
        long before = configuration.getRevision();
        QIOWorkbenchConfiguration.EncodedPattern replacingRoot =
              pending.rootAction == Action.ENCODE_PATTERN ?
                    pending.result.getRoots().get(0) : null;
        final boolean changed;
        try {
            changed = configuration.applyEncodedClosure(replacingRoot,
                  pending.result.getPatterns());
            if (changed && replacingRoot != null) {
                QIOWorkbenchConfigurationService.preferEncodedCandidates(
                      world, configuration, replacingRoot);
            }
            if (changed) {
                QIORecipeCatalogService.INSTANCE.encodedPatternsAdded(
                      pending.result.getPatterns());
            }
        } catch (TargetedRecipeLookupBusyException e) {
            return new ConfirmResult(Status.BUSY);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return new ConfirmResult(Status.INVALID_PATTERN);
        }
        network.markWorkbenchConfigurationChanged(before);
        return new ConfirmResult(changed ? Status.APPLIED : Status.UNCHANGED);
    }

    private static List<QIOWorkbenchConfiguration.EncodedPattern> resolveRoots(World world,
          QIOWorkbenchConfigurationMutation mutation) {
        if (mutation.getAction() == Action.ENCODE_PATTERN) {
            List<QIOWorkbenchConfiguration.EncodedPattern> result = new ArrayList<>(1);
            result.add(QIOWorkbenchRecipeCatalog.resolveEncodedPattern(world,
                  mutation.getGrid()));
            return result;
        }
        List<QIOWorkbenchConfiguration.EncodedPattern> result =
              QIOWorkbenchRecipeCatalog.resolveEncodedTargets(world, mutation.getTargets());
        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                  "No stable workbench recipes match the requested targets");
        }
        return result;
    }

    private static synchronized PreviewResult register(
          QIOWorkbenchRecipeCatalog.ClosureResult result, Action rootAction,
          UUID sessionNonce, UUID playerUUID, UUID frequencyUUID, UUID configUUID,
          long configurationRevision, long catalogRevision, long currentTick) {
        cleanup(currentTick);
        if (PENDING.size() >= MAX_PENDING_PREVIEWS) {
            Iterator<UUID> iterator = PENDING.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        UUID nonce = UUID.randomUUID();
        PendingClosure pending = new PendingClosure(result, rootAction, sessionNonce,
              playerUUID, frequencyUUID, configUUID, configurationRevision,
              catalogRevision, expiresAt(currentTick));
        PENDING.put(nonce, pending);
        QIOWorkbenchClosurePreview preview = new QIOWorkbenchClosurePreview(nonce,
              result.getRoots().size(), result.getNewProductCount(),
              result.getNewRecipeCount(), result.getExistingRecipeCount(),
              result.getLeafMaterialCount(), result.getCycleCount(),
              result.getSkippedCyclicRecipeCount(), result.isTruncated(),
              result.getCyclePaths());
        return new PreviewResult(Status.READY, preview);
    }

    private static void cleanup(long currentTick) {
        PENDING.values().removeIf(pending -> pending.expiresAt < currentTick);
    }

    private static long expiresAt(long currentTick) {
        return currentTick > Long.MAX_VALUE - PREVIEW_LIFETIME_TICKS ? Long.MAX_VALUE :
              currentTick + PREVIEW_LIFETIME_TICKS;
    }

    public static final class PreviewResult {

        private final Status status;
        @Nullable private final QIOWorkbenchClosurePreview preview;

        private PreviewResult(Status status,
              @Nullable QIOWorkbenchClosurePreview preview) {
            this.status = status;
            this.preview = preview;
        }

        @Nonnull public Status getStatus() { return status; }
        @Nullable public QIOWorkbenchClosurePreview getPreview() { return preview; }
    }

    public static final class ConfirmResult {

        private final Status status;

        private ConfirmResult(Status status) {
            this.status = status;
        }

        @Nonnull public Status getStatus() { return status; }
    }

    private static final class PendingClosure {

        private final QIOWorkbenchRecipeCatalog.ClosureResult result;
        private final Action rootAction;
        private final UUID sessionNonce;
        private final UUID playerUUID;
        private final UUID frequencyUUID;
        private final UUID configUUID;
        private final long configurationRevision;
        private final long catalogRevision;
        private final long expiresAt;

        private PendingClosure(QIOWorkbenchRecipeCatalog.ClosureResult result,
              Action rootAction, UUID sessionNonce, UUID playerUUID, UUID frequencyUUID,
              UUID configUUID, long configurationRevision, long catalogRevision,
              long expiresAt) {
            this.result = result;
            this.rootAction = rootAction;
            this.sessionNonce = sessionNonce;
            this.playerUUID = playerUUID;
            this.frequencyUUID = frequencyUUID;
            this.configUUID = configUUID;
            this.configurationRevision = configurationRevision;
            this.catalogRevision = catalogRevision;
            this.expiresAt = expiresAt;
        }
    }

    private static final class CaptureJob {

        @Nullable private QIOWorkbenchRecipeCatalog.ClosureCapture capture;
        private final QIOProcessingNetworkData network;
        private final Action rootAction;
        private final UUID sessionNonce;
        private final UUID playerUUID;
        private final UUID frequencyUUID;
        private final UUID configUUID;
        private final long configurationRevision;
        private final long catalogRevision;
        private final World world;
        private final long expiresAt;
        private final Consumer<PreviewResult> completion;

        private CaptureJob(QIOWorkbenchRecipeCatalog.ClosureCapture capture,
              QIOProcessingNetworkData network, Action rootAction, UUID sessionNonce,
              UUID playerUUID, UUID frequencyUUID, UUID configUUID,
              long configurationRevision, long catalogRevision, World world,
              long expiresAt, Consumer<PreviewResult> completion) {
            this.capture = capture;
            this.network = network;
            this.rootAction = rootAction;
            this.sessionNonce = sessionNonce;
            this.playerUUID = playerUUID;
            this.frequencyUUID = frequencyUUID;
            this.configUUID = configUUID;
            this.configurationRevision = configurationRevision;
            this.catalogRevision = catalogRevision;
            this.world = world;
            this.expiresAt = expiresAt;
            this.completion = completion;
        }
    }
}
