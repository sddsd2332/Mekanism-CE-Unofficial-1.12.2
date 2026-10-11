package mekanism.qioprocessing.common.workbench;

import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.Mekanism;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.common.content.qio.QIOStorageManager;
import mekanism.common.util.WorldDirectoryUtils;
import mekanism.qioprocessing.common.machine.QIOMachineProviderBootstrap;
import mekanism.qioprocessing.common.machine.QIOProviderRouteScanner;
import mekanism.qioprocessing.common.network.WorkbenchNetwork;
import mekanism.qioprocessing.common.network.WorkbenchPacket;
import mekanism.qioprocessing.common.pattern.WorkbenchPatternValidator;
import mekanism.qioprocessing.common.pattern.WorkbenchValidationResult;
import mekanism.qioprocessing.common.planning.*;
import mekanism.qioprocessing.common.recipe.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Live server bridge hosted by the legacy module lifecycle.
 */
public final class WorkbenchRuntime {
    private static WorkbenchRuntime current;
    private static final long PATTERN_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final long EXECUTION_RESCAN_DELAY_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final long SLOW_RECIPE_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
    private final MinecraftServer server;
    private final WorkbenchSessions sessions;
    private final WorkbenchBookStore bookStore;
    private final ThreadPoolExecutor worker;
    private final QIOWorkbenchOrderPreparation orderPreparation;
    private final Map<UUID, QIOWorkbenchOrderPreparation.Job> orderJobs = new HashMap<>();
    private final Deque<UUID> executionQueue = new ArrayDeque<>();
    private final Set<UUID> queuedExecutionOrders = new HashSet<>();
    private long nextExecutionRescanNanos;
    private final QIOClaimOrderStore orderStore;
    private final QIOWorkbenchExecutionStore executionStore;
    private final QIOWorkbenchExecutionExecutor executionExecutor;
    private final QIOProviderExecutionStore providerExecutionStore;
    private final QIOProviderExecutionExecutor providerExecutionExecutor;
    private final QIOProviderRouteCatalog providerRouteCatalog;
    private final QIOProviderRouteScanner providerRouteScanner;
    private final QIOClaimOrderRecovery orderRecovery;
    private QIOClaimRecoverySweep recoverySweep;
    private boolean recoveryReported;
    private boolean recoveryStopped;
    private int automaticRecoveryPasses = 1;
    private long nextRecoveryPassNanos;
    private final Map<UUID, Long> lastPatternAttempt = new HashMap<>();
    private final Set<String> quarantinedRecipes = new HashSet<>();

    private WorkbenchRuntime(MinecraftServer server, java.nio.file.Path worldDirectory) {
        this.server = server;
        bookStore = new WorkbenchBookStore(worldDirectory.resolve("mekanism/qio_processing/workbench_v1"));
        orderStore = new QIOClaimOrderStore(worldDirectory.resolve("mekanism/qio_processing/claim_orders_v1"));
        executionStore = new QIOWorkbenchExecutionStore(worldDirectory.resolve("mekanism/qio_processing/claim_orders_v1"));
        executionExecutor = new QIOWorkbenchExecutionExecutor(orderStore, executionStore);
        providerRouteCatalog = new QIOProviderRouteCatalog();
        providerRouteScanner = new QIOProviderRouteScanner(server, providerRouteCatalog);
        providerExecutionStore = new QIOProviderExecutionStore(worldDirectory.resolve("mekanism/qio_processing/claim_orders_v1"));
        providerExecutionExecutor = new QIOProviderExecutionExecutor(orderStore, providerExecutionStore,
                providerRouteCatalog, server);
        orderRecovery = new QIOClaimOrderRecovery(orderStore, new QIOClaimOrderSubmission(orderStore));
        recoverySweep = new QIOClaimRecoverySweep(orderStore, orderRecovery, QIOFrequencyStorageAccess.INSTANCE,
                System::currentTimeMillis, intent -> true);
        worker = new ThreadPoolExecutor(0, 2, 60, TimeUnit.SECONDS, new SynchronousQueue<>(), task -> {
            Thread thread = new Thread(task, "Mekanism-QIO-Recipe-Import");
            thread.setDaemon(true);
            return thread;
        });
        orderPreparation = new QIOWorkbenchOrderPreparation(worker, new QIOClaimOrderSubmission(orderStore));
        sessions = new WorkbenchSessions(new WorkbenchSessions.Persistence() {
            @Override
            public WorkbenchSnapshot load(UUID id) throws IOException {
                return bookStore.load(id);
            }

            @Override
            public void save(WorkbenchSnapshot snapshot) throws IOException {
                bookStore.save(snapshot);
            }
        }, worker);
    }

    public static void start(MinecraftServer server) {
        stop();
        QIOMachineProviderBootstrap.register();
        WorldServer overworld = server == null ? null : server.getWorld(0);
        File worldDirectory = WorldDirectoryUtils.getWorldDirectory(overworld);
        if (worldDirectory == null) {
            Mekanism.logger.warn("QIO workbench runtime is disabled because the overworld has no persistent save directory");
            return;
        }
        current = new WorkbenchRuntime(server, worldDirectory.toPath());
    }

    public static void stop() {
        if (current != null) {
            current.closeRecoverySweep();
            current.sessions.close();
            current.orderJobs.clear();
            current.executionQueue.clear();
            current.queuedExecutionOrders.clear();
            current = null;
        }
    }

    public static UUID startSmartProcessingOrder(MinecraftServer server, EntityPlayerMP player,
                                                 QIOFrequencyReference reference, Map<RecipeItemKey, Long> targets,
                                                 long expectedBookRevision) throws IOException {
        WorkbenchRuntime runtime = currentFor(server);
        if (reference == null || !player.getUniqueID().equals(reference.getBindingPlayerUUID())
                || targets == null || targets.isEmpty()
                || !QIOFrequencyStorageAccess.INSTANCE.canAccess(reference, player.getUniqueID())) {
            throw new IllegalStateException("Smart Processing order authorization denied");
        }
        if (runtime.orderJobs.containsKey(player.getUniqueID())) {
            throw new IllegalStateException("Smart Processing order is already running");
        }
        if (RecipeDirectoryService.INSTANCE.state() != RecipeDirectoryService.State.READY) {
            throw new IllegalStateException("Recipe directory is not ready");
        }
        WorkbenchSnapshot book = runtime.bookStore.load(reference.getFrequencyUUID());
        if (book.revision() != expectedBookRevision) throw new IllegalStateException("Workbench revision changed");
        IQIOStorageView view = QIOFrequencyStorageAccess.INSTANCE.open(reference, player.getUniqueID());
        if (view == null) throw new IllegalStateException("QIO authorization expired");
        try {
            WorkbenchSessions.Access access = new WorkbenchSessions.Access() {
                @Override
                public UUID player() {
                    return player.getUniqueID();
                }

                @Override
                public UUID frequency() {
                    return reference.getFrequencyUUID();
                }

                @Override
                public int dimension() {
                    return player.dimension;
                }

                @Override
                public boolean valid() {
                    return player.world == server.getWorld(player.dimension)
                            && QIOFrequencyStorageAccess.INSTANCE.canAccess(reference, player.getUniqueID());
                }

                @Override
                public QIOFrequencyReference frequencyReference() {
                    return reference;
                }
            };
            QIOWorkbenchOrderPreparation.Job job = runtime.orderPreparation.start(access, view, book,
                    RecipeDirectoryService.INSTANCE.catalog(), targets);
            runtime.orderJobs.put(player.getUniqueID(), job);
            return job.orderId();
        } finally {
            view.close();
        }
    }

    public static QIOWorkbenchOrderPreparation.Prepared pollSmartProcessingOrder(
            MinecraftServer server, EntityPlayerMP player, UUID orderId) throws IOException {
        WorkbenchRuntime runtime = currentFor(server);
        QIOWorkbenchOrderPreparation.Job job = runtime.orderJobs.get(player.getUniqueID());
        if (job == null || !job.orderId().equals(orderId)) {
            return runtime.pollPersistedSmartProcessingOrder(player, orderId);
        }
        QIOFrequencyReference reference = job.frequencyReference();
        IQIOStorageView view = QIOFrequencyStorageAccess.INSTANCE.open(reference, player.getUniqueID());
        if (view == null) throw new IllegalStateException("QIO authorization expired");
        try {
            WorkbenchSnapshot book = runtime.bookStore.load(reference.getFrequencyUUID());
            QIOWorkbenchOrderPreparation.Prepared prepared = runtime.orderPreparation.finish(job,
                    smartAccess(player, reference, server), view, book, RecipeDirectoryService.INSTANCE.catalog());
            if (prepared.status() != QIOWorkbenchOrderPreparation.Status.RUNNING) {
                runtime.orderJobs.remove(player.getUniqueID());
                if (prepared.status() == QIOWorkbenchOrderPreparation.Status.CLAIM_RESERVED
                        && prepared.reservation() != null) runtime.queueExecution(job.orderId());
            }
            return prepared;
        } finally {
            view.close();
        }
    }

    public static String cancelSmartProcessingOrder(MinecraftServer server, EntityPlayerMP player,
                                                    UUID orderId) throws IOException {
        WorkbenchRuntime runtime = currentFor(server);
        QIOWorkbenchOrderPreparation.Job job = runtime.orderJobs.get(player.getUniqueID());
        if (job != null && job.orderId().equals(orderId)) {
            job.cancel();
            runtime.orderJobs.remove(player.getUniqueID());
        }
        if (!runtime.orderStore.exists(orderId)) return job == null ? "NO_ORDER" : "CANCELLED";
        QIOClaimOrderIntent intent = runtime.orderStore.load(orderId);
        if (intent.frequencyReference() == null
                || !player.getUniqueID().equals(intent.frequencyReference().getBindingPlayerUUID())) {
            throw new IllegalStateException("Smart Processing order cancellation denied");
        }
        QIOWorkbenchOrderPreparation.Status persisted = runtime.persistedSmartProcessingStatus(orderId);
        if (persisted == QIOWorkbenchOrderPreparation.Status.COMPLETED
                || persisted == QIOWorkbenchOrderPreparation.Status.FAILED
                || persisted == QIOWorkbenchOrderPreparation.Status.NEEDS_RECONCILIATION) {
            return persisted.name();
        }
        if (runtime.orderStore.cancellationRequested(orderId)) return "CANCELLED";
        runtime.orderStore.requestCancellation(orderId);
        return "CANCELLED";
    }

    private QIOWorkbenchOrderPreparation.Prepared pollPersistedSmartProcessingOrder(
            EntityPlayerMP player, UUID orderId) throws IOException {
        if (!orderStore.exists(orderId)) throw new IllegalStateException("Unknown Smart Processing order");
        QIOClaimOrderIntent intent = orderStore.load(orderId);
        if (intent.frequencyReference() == null
                || !player.getUniqueID().equals(intent.frequencyReference().getBindingPlayerUUID())) {
            throw new IllegalStateException("Smart Processing order access denied");
        }
        if (!QIOFrequencyStorageAccess.INSTANCE.canAccess(intent.frequencyReference(), player.getUniqueID())) {
            throw new IllegalStateException("Smart Processing order authorization expired");
        }
        return QIOWorkbenchOrderPreparation.Prepared.status(persistedSmartProcessingStatus(orderId));
    }

    private QIOWorkbenchOrderPreparation.Status persistedSmartProcessingStatus(UUID orderId) throws IOException {
        if (orderStore.cancellationRequested(orderId)) {
            return QIOWorkbenchOrderPreparation.Status.CANCELLED;
        }
        if (providerExecutionStore.exists(orderId)) {
            switch (providerExecutionStore.load(orderId).status()) {
                case COMPLETED:
                    return QIOWorkbenchOrderPreparation.Status.COMPLETED;
                case FAILED:
                    return QIOWorkbenchOrderPreparation.Status.FAILED;
                case NEEDS_RECONCILIATION:
                    return QIOWorkbenchOrderPreparation.Status.NEEDS_RECONCILIATION;
                default:
                    return QIOWorkbenchOrderPreparation.Status.CLAIM_RESERVED;
            }
        }
        if (executionStore.exists(orderId)) {
            switch (executionStore.load(orderId).status()) {
                case COMPLETED:
                    return QIOWorkbenchOrderPreparation.Status.COMPLETED;
                case FAILED:
                    return QIOWorkbenchOrderPreparation.Status.FAILED;
                case NEEDS_RECONCILIATION:
                    return QIOWorkbenchOrderPreparation.Status.NEEDS_RECONCILIATION;
                default:
                    return QIOWorkbenchOrderPreparation.Status.CLAIM_RESERVED;
            }
        }
        return QIOWorkbenchOrderPreparation.Status.CLAIM_RESERVED;
    }

    private static WorkbenchSessions.Access smartAccess(EntityPlayerMP player,
                                                        QIOFrequencyReference reference, MinecraftServer server) {
        return new WorkbenchSessions.Access() {
            @Override
            public UUID player() {
                return player.getUniqueID();
            }

            @Override
            public UUID frequency() {
                return reference.getFrequencyUUID();
            }

            @Override
            public int dimension() {
                return player.dimension;
            }

            @Override
            public boolean valid() {
                return player.world == server.getWorld(player.dimension)
                        && QIOFrequencyStorageAccess.INSTANCE.canAccess(reference, player.getUniqueID());
            }

            @Override
            public QIOFrequencyReference frequencyReference() {
                return reference;
            }
        };
    }

    /**
     * Returns the live server-thread Provider route catalog for machine discovery integrations.
     */
    public static QIOProviderRouteCatalog.Snapshot providerRouteSnapshot(MinecraftServer server) {
        return currentFor(server).providerRouteCatalog.snapshot();
    }

    public static QIOProviderRouteCatalog.Snapshot providerRouteSnapshot(MinecraftServer server,
                                                                         UUID frequencyUUID) {
        return currentFor(server).providerRouteCatalog.snapshot(frequencyUUID);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (current != null && event.phase == TickEvent.Phase.END) {
            current.sessions.tick();
            if (QIOStorageManager.isLoaded() && RecipeDirectoryService.INSTANCE.state() == RecipeDirectoryService.State.READY) {
                current.providerRouteScanner.step();
            }
            current.executeOneEntry();
            current.recoverOneEntry();
        }
    }

    private void recoverOneEntry() {
        if (recoveryStopped || !QIOStorageManager.isLoaded()) return;
        if (recoverySweep == null) {
            if (nextRecoveryPassNanos == 0 || System.nanoTime() - nextRecoveryPassNanos < 0) return;
            recoverySweep = new QIOClaimRecoverySweep(orderStore, orderRecovery, QIOFrequencyStorageAccess.INSTANCE,
                    System::currentTimeMillis, intent -> true);
            automaticRecoveryPasses++;
            nextRecoveryPassNanos = 0;
        }
        try {
            if (recoverySweep.step()) {
                recoveryReported = true;
                if (recoverySweep.released() + recoverySweep.awaiting() + recoverySweep.unresolved() > 0) {
                    Mekanism.logger.info("[QIO Workbench] Claim recovery pass {}: released={}, awaiting authorization={}, unresolved={}",
                            automaticRecoveryPasses, recoverySweep.released(), recoverySweep.awaiting(), recoverySweep.unresolved());
                }
                int retryable = recoverySweep.retryable();
                int exhausted = recoverySweep.exhausted();
                int operatorRequired = recoverySweep.operatorRequired();
                recoverySweep = null;
                if (retryable > 0 && automaticRecoveryPasses < QIOClaimRecoverySweep.MAX_AUTOMATIC_ATTEMPTS) {
                    nextRecoveryPassNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(QIOClaimRecoverySweep.RETRY_DELAY_MILLIS);
                }
                if (exhausted > 0)
                    Mekanism.logger.warn("[QIO Workbench] {} claim orders exhausted automatic recovery; inspect persisted claim order diagnostics", exhausted);
                if (operatorRequired > 0)
                    Mekanism.logger.warn("[QIO Workbench] {} claim orders have no bound frequency reference; operator review required", operatorRequired);
            }
            if (recoverySweep != null && recoverySweep.lastStatus() != null
                    && recoverySweep.lastStatus() != QIOClaimOrderRecovery.Status.RELEASED) {
                Mekanism.logger.warn("[QIO Workbench] Claim recovery {} for order {}",
                        recoverySweep.lastStatus(), recoverySweep.lastOrderId());
            }
        } catch (IOException | RuntimeException error) {
            Mekanism.logger.error("[QIO Workbench] Claim recovery pass failed; retrying later", error);
            recoveryReported = true;
            closeRecoverySweep();
            if (automaticRecoveryPasses < QIOClaimRecoverySweep.MAX_AUTOMATIC_ATTEMPTS) {
                automaticRecoveryPasses++;
                nextRecoveryPassNanos = System.nanoTime() +
                        TimeUnit.MILLISECONDS.toNanos(QIOClaimRecoverySweep.RETRY_DELAY_MILLIS);
            } else {
                recoveryStopped = true;
            }
        }
    }

    private void executeOneEntry() {
        if (!QIOStorageManager.isLoaded() || RecipeDirectoryService.INSTANCE.state() != RecipeDirectoryService.State.READY)
            return;
        try {
            RecipeCatalog catalog = RecipeDirectoryService.INSTANCE.catalog();
            if (executionQueue.isEmpty()) {
                long now = System.nanoTime();
                if (now < nextExecutionRescanNanos) return;
                for (UUID orderId : orderStore.orderIds()) {
                    if (queuedExecutionOrders.add(orderId)) executionQueue.addLast(orderId);
                }
                nextExecutionRescanNanos = now + EXECUTION_RESCAN_DELAY_NANOS;
            }
            UUID orderId = executionQueue.pollFirst();
            if (orderId == null) return;
            queuedExecutionOrders.remove(orderId);
            if (orderStore.cancellationRequested(orderId)) return;
            QIOClaimOrderIntent intent = orderStore.load(orderId);
            if (intent.providerManifest() != null) {
                if (providerExecutionStore.exists(orderId)) {
                    QIOProviderExecutionState state = providerExecutionStore.load(orderId);
                    if (state.status() == QIOProviderExecutionState.Status.COMPLETED
                            || state.status() == QIOProviderExecutionState.Status.FAILED
                            || state.status() == QIOProviderExecutionState.Status.NEEDS_RECONCILIATION) return;
                }
                QIOProviderExecutionExecutor.Result result = providerExecutionExecutor.executeOne(orderId,
                        QIOFrequencyStorageAccess.INSTANCE);
                if (result.status() == QIOProviderExecutionExecutor.Status.ADVANCED
                        || result.status() == QIOProviderExecutionExecutor.Status.WAITING) {
                    queueExecution(orderId);
                }
                if (result.status() == QIOProviderExecutionExecutor.Status.COMPLETED) {
                    Mekanism.logger.info("[QIO Provider] Execution completed for order {}", orderId);
                } else if (result.status() == QIOProviderExecutionExecutor.Status.NEEDS_RECONCILIATION
                        || result.status() == QIOProviderExecutionExecutor.Status.FAILED) {
                    Mekanism.logger.warn("[QIO Provider] Execution {} for order {}", result.status(), orderId);
                }
                return;
            }
            if (intent.manifest() == null) return;
            if (executionStore.exists(orderId)) {
                QIOWorkbenchExecutionState state = executionStore.load(orderId);
                if (state.status() == QIOWorkbenchExecutionState.Status.COMPLETED
                        || state.status() == QIOWorkbenchExecutionState.Status.FAILED
                        || state.status() == QIOWorkbenchExecutionState.Status.NEEDS_RECONCILIATION) return;
            }
            WorkbenchSnapshot book = bookStore.load(intent.frequencyId());
            int dimension = intent.manifest() == null ? 0 : intent.manifest().dimension();
            QIOWorkbenchExecutionExecutor.Result result = executionExecutor.executeOne(orderId, book, catalog,
                    QIOFrequencyStorageAccess.INSTANCE, server.getWorld(dimension));
            if (result.status() == QIOWorkbenchExecutionExecutor.Status.ADVANCED
                    || result.status() == QIOWorkbenchExecutionExecutor.Status.WAITING) {
                queueExecution(orderId);
            }
            if (result.status() == QIOWorkbenchExecutionExecutor.Status.COMPLETED) {
                Mekanism.logger.info("[QIO Workbench] Execution completed for order {}", orderId);
            } else if (result.status() == QIOWorkbenchExecutionExecutor.Status.NEEDS_RECONCILIATION
                    || result.status() == QIOWorkbenchExecutionExecutor.Status.FAILED) {
                Mekanism.logger.warn("[QIO Workbench] Execution {} for order {}", result.status(), orderId);
            }
        } catch (IOException | RuntimeException error) {
            Mekanism.logger.error("[QIO Workbench] Execution tick stopped; remaining orders will retry or require reconciliation", error);
        }
    }

    private void closeRecoverySweep() {
        if (recoverySweep == null) return;
        try {
            recoverySweep.close();
        } catch (IOException error) {
            Mekanism.logger.error("[QIO Workbench] Failed to close claim recovery scan", error);
        } finally {
            recoverySweep = null;
        }
    }

    public static String claimRecoveryStatus(MinecraftServer server, UUID orderId) throws IOException {
        WorkbenchRuntime runtime = currentFor(server);
        mekanism.qioprocessing.common.planning.QIOClaimOrderIntent intent = runtime.orderStore.load(orderId);
        boolean cancelled = runtime.orderStore.cancellationRequested(orderId);
        boolean hasBoundReference = intent.frequencyReference() != null
                && intent.frequencyReference().getBindingPlayerUUID() != null;
        QIOClaimOrderStore.RecoveryRecord diagnostic = runtime.orderStore.readRecovery(orderId);
        return "order=" + orderId + " frequency=" + intent.frequencyId() + " cancellation=" + cancelled
                + " boundReference=" + hasBoundReference
                + " manifestSteps=" + (intent.manifest() == null ? "legacy-none" : intent.manifest().steps().size())
                + " lastRecovery=" + (diagnostic == null ? "none" : diagnostic.status() + " attempts="
                + diagnostic.attempts() + " at=" + diagnostic.lastAttemptMillis())
                + " (physical Claim state not checked)";
    }

    public static QIOClaimOrderRecovery.Status retryClaimRecovery(MinecraftServer server, UUID orderId) throws IOException {
        WorkbenchRuntime runtime = currentFor(server);
        if (!runtime.recoveryReported || runtime.recoverySweep != null) {
            throw new IllegalStateException("Claim recovery scan is still running");
        }
        if (!QIOStorageManager.isLoaded()) throw new IllegalStateException("QIO storage is not loaded");
        return runtime.orderRecovery.abandonOne(orderId, QIOFrequencyStorageAccess.INSTANCE);
    }

    private static WorkbenchRuntime currentFor(MinecraftServer server) {
        if (current == null || server == null) {
            throw new IllegalStateException("QIO workbench runtime is unavailable");
        }
        if (current.server != server || !server.isCallingFromMinecraftThread()) {
            throw new IllegalStateException("Claim recovery requires the running server thread");
        }
        return current;
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (current != null && !event.player.world.isRemote) {
            UUID playerId = event.player.getUniqueID();
            QIOWorkbenchOrderPreparation.Job job = current.orderJobs.remove(playerId);
            if (job != null) job.cancel();
            current.sessions.end(playerId);
            current.lastPatternAttempt.remove(playerId);
        }
    }

    public static void handle(EntityPlayerMP player, WorkbenchPacket.Request request) {
        if (current == null || current.server != player.getServer()
                || !current.server.isCallingFromMinecraftThread()) return;
        current.dispatch(player, request);
    }

    private void dispatch(EntityPlayerMP player, WorkbenchPacket.Request request) {
        try {
            NBTTagCompound data = request.data();
            String action = text(data, "action");
            if (request.window != player.openContainer.windowId)
                throw new WorkbenchSessions.Rejected(WorkbenchSessions.Failure.ACCESS_DENIED);
            RecipeCatalog catalog = RecipeDirectoryService.INSTANCE.catalog();
            if (RecipeDirectoryService.INSTANCE.state() != RecipeDirectoryService.State.READY) {
                send(player, request, request.token, response("NOT_READY"));
                return;
            }
            UUID playerId = player.getUniqueID();
            if ("OPEN".equals(action)) {
                if (request.token != null) throw new IllegalArgumentException("Open cannot reuse a token");
                WorkbenchSessions.Opened opened = sessions.open(new WorkbenchViewerAccess(player, request.window));
                send(player, request, opened.token, header("OPENED", catalog, opened.snapshot));
                return;
            }
            WorkbenchSnapshot snapshot = sessions.snapshot(playerId, request.token);
            NBTTagCompound reply = header("OK", catalog, snapshot);
            switch (action) {
                case "CLOSE":
                    sessions.end(playerId, request.token);
                    reply.setString("status", "CLOSED");
                    break;
                case "LIST":
                    list(catalog, snapshot, data, reply);
                    break;
                case "ROUTES": {
                    if (data.hasKey("product", 8))
                        page(WorkbenchQuery.routesByFamily(catalog, snapshot, data.getString("product"), offset(data), limit(data)), reply);
                    else {
                        RecipeDeclaration recipe = recipe(catalog, data);
                        page(WorkbenchQuery.routes(catalog, snapshot, recipe.output(), offset(data), limit(data)), reply);
                    }
                    break;
                }
                case "IMPORTED":
                    page(WorkbenchQuery.imported(catalog, snapshot, offset(data), limit(data)), reply);
                    break;
                case "CANDIDATES":
                    candidates(catalog, snapshot, data, reply);
                    break;
                case "PATTERNS":
                    patterns(catalog, snapshot, data, reply);
                    break;
                case "DETAIL":
                    detail(catalog, snapshot, data, reply);
                    break;
                case "CANCEL_PREVIEW":
                    sessions.cancelPreview(playerId, request.token);
                    reply.setString("status", "CANCELLED");
                    break;
                case "PREVIEW_ROUTES": {
                    if (!data.hasUniqueId("preview")) throw new IllegalArgumentException("Missing preview");
                    WorkbenchSessions.Prepared prepared = sessions.poll(playerId, request.token, data.getUniqueId("preview"));
                    if (!prepared.ready || prepared.error != null) {
                        reply.setString("status", prepared.ready ? "FAILED" : "RUNNING");
                        break;
                    }
                    List<RecipeDeclaration> recipes = prepared.preview.discovery().recipes();
                    int from = Math.min(offset(data), recipes.size());
                    int count = limit(data);
                    NBTTagList rows = new NBTTagList();
                    for (int i = from; i < recipes.size() && rows.tagCount() < count; i++) {
                        RecipeDeclaration recipe = recipes.get(i);
                        rows.appendTag(row(recipe, snapshot.routes().get(recipe.id()), null, prepared.preview.generation()));
                    }
                    reply.setTag("rows", rows);
                    reply.setInteger("offset", from);
                    reply.setInteger("total", recipes.size());
                    break;
                }
                case "PREVIEW": {
                    revision(data, snapshot, catalog);
                    RecipeDiscovery.Mode mode = RecipeDiscovery.Mode.valueOf(text(data, "mode"));
                    List<RecipeItemKey> roots = new ArrayList<>();
                    if (bool(data, "all")) {
                        java.util.Set<RecipeItemKey> unique = new java.util.LinkedHashSet<>();
                        for (RecipeDeclaration recipe : catalog.recipes()) unique.add(recipe.output());
                        roots.addAll(unique);
                    } else roots.add(recipe(catalog, data).output());
                    UUID job = sessions.prepare(playerId, request.token, catalog, roots, mode);
                    reply.setUniqueId("preview", job);
                    reply.setString("status", "ACCEPTED");
                    break;
                }
                case "POLL_PREVIEW": {
                    if (!data.hasUniqueId("preview")) throw new IllegalArgumentException("Missing preview");
                    WorkbenchSessions.Prepared prepared = sessions.poll(playerId, request.token, data.getUniqueId("preview"));
                    reply.setString("status", !prepared.ready ? "RUNNING" : prepared.error == null ? "PREPARED" : "FAILED");
                    reply.setUniqueId("preview", prepared.token);
                    if (prepared.preview != null) {
                        reply.setLong("revision", prepared.preview.base().revision());
                        reply.setString("generation", prepared.preview.generation());
                        reply.setInteger("added", prepared.preview.added());
                        reply.setInteger("updated", prepared.preview.updated());
                        reply.setInteger("unchanged", prepared.preview.unchanged());
                        reply.setInteger("incomplete", prepared.preview.discovery().incompleteRecipes().size());
                        reply.setInteger("leaves", prepared.preview.discovery().leaves().size());
                        reply.setInteger("total", prepared.preview.discovery().recipes().size());
                    }
                    break;
                }
                case "SUBMIT_ORDER": {
                    revision(data, snapshot, catalog);
                    if (orderJobs.containsKey(playerId))
                        throw new WorkbenchSessions.Rejected(WorkbenchSessions.Failure.BUSY);
                    WorkbenchSessions.Access access = sessions.access(playerId, request.token);
                    IQIOStorageView view = QIOFrequencyStorageAccess.INSTANCE.open(access.frequencyReference(), playerId);
                    if (view == null) throw new WorkbenchSessions.Rejected(WorkbenchSessions.Failure.ACCESS_DENIED);
                    try {
                        QIOWorkbenchOrderPreparation.Job job = orderPreparation.start(access, view, snapshot, catalog,
                                targets(data));
                        orderJobs.put(playerId, job);
                        reply.setString("status", "ORDER_ACCEPTED");
                        reply.setUniqueId("orderJob", job.orderId());
                    } finally {
                        view.close();
                    }
                    break;
                }
                case "POLL_ORDER": {
                    if (!data.hasUniqueId("orderJob")) throw new IllegalArgumentException("Missing order job");
                    QIOWorkbenchOrderPreparation.Job job = orderJobs.get(playerId);
                    if (job == null || !job.orderId().equals(data.getUniqueId("orderJob"))) {
                        reply.setString("status", "NO_ORDER");
                        break;
                    }
                    WorkbenchSessions.Access access = sessions.access(playerId, request.token);
                    IQIOStorageView view = QIOFrequencyStorageAccess.INSTANCE.open(access.frequencyReference(), playerId);
                    if (view == null) throw new WorkbenchSessions.Rejected(WorkbenchSessions.Failure.ACCESS_DENIED);
                    try {
                        QIOWorkbenchOrderPreparation.Prepared prepared = orderPreparation.finish(job, access, view,
                                snapshot, catalog);
                        reply.setString("status", prepared.status().name());
                        if (prepared.status() == QIOWorkbenchOrderPreparation.Status.CLAIM_RESERVED
                                || prepared.status() == QIOWorkbenchOrderPreparation.Status.CLAIM_REJECTED
                                || prepared.status() == QIOWorkbenchOrderPreparation.Status.NEEDS_RECONCILIATION) {
                            reply.setUniqueId("order", job.orderId());
                        }
                        if (prepared.status() != QIOWorkbenchOrderPreparation.Status.RUNNING)
                            orderJobs.remove(playerId);
                        if (prepared.status() == QIOWorkbenchOrderPreparation.Status.CLAIM_RESERVED) {
                            queueExecution(job.orderId());
                        }
                    } finally {
                        view.close();
                    }
                    break;
                }
                case "CANCEL_ORDER": {
                    QIOWorkbenchOrderPreparation.Job job = orderJobs.get(playerId);
                    if (job != null && data.hasUniqueId("order")
                            && !job.orderId().equals(data.getUniqueId("order"))) {
                        throw new WorkbenchSessions.Rejected(WorkbenchSessions.Failure.ACCESS_DENIED);
                    }
                    UUID orderId = data.hasUniqueId("order") ? data.getUniqueId("order")
                            : job == null ? null : job.orderId();
                    if (job != null) {
                        orderJobs.remove(playerId);
                        job.cancel();
                    }
                    if (orderId != null && orderStore.exists(orderId)) {
                        QIOClaimOrderIntent intent = orderStore.load(orderId);
                        if (intent.frequencyReference() == null
                                || !playerId.equals(intent.frequencyReference().getBindingPlayerUUID())) {
                            throw new WorkbenchSessions.Rejected(WorkbenchSessions.Failure.ACCESS_DENIED);
                        }
                        orderStore.requestCancellation(orderId);
                    }
                    reply.setString("status", "ORDER_CANCELLED");
                    break;
                }
                case "COMMIT": {
                    revision(data, snapshot, catalog);
                    if (!data.hasUniqueId("preview")) throw new IllegalArgumentException("Missing preview");
                    snapshot = sessions.commit(playerId, request.token, data.getUniqueId("preview"), catalog);
                    reply = header("SAVED", catalog, snapshot);
                    break;
                }
                case "ADD_PATTERN": {
                    long revision = revision(data, snapshot, catalog);
                    RecipeDeclaration recipe = recipe(catalog, data);
                    if (!data.hasUniqueId("pattern")) throw new IllegalArgumentException("Missing pattern id");
                    WorkbenchRoute route = snapshot.routes().get(recipe.id());
                    if (route == null)
                        throw new WorkbenchRecipeBook.Rejected(WorkbenchRecipeBook.Rejection.NOT_IMPORTED);
                    if (!route.patterns().containsKey(data.getUniqueId("pattern"))) {
                        if (route.patternCount() >= WorkbenchSnapshot.MAX_PATTERNS_PER_ROUTE)
                            throw new WorkbenchRecipeBook.Rejected(WorkbenchRecipeBook.Rejection.PATTERN_QUOTA_EXCEEDED);
                        int totalPatterns = 0;
                        for (WorkbenchRoute existing : snapshot.routes().values())
                            totalPatterns += existing.patternCount();
                        if (totalPatterns >= WorkbenchSnapshot.MAX_PATTERNS_PER_BOOK)
                            throw new WorkbenchRecipeBook.Rejected(WorkbenchRecipeBook.Rejection.PATTERN_QUOTA_EXCEEDED);
                    }
                    List<ItemStack> inputs = grid(data);
                    if (quarantinedRecipes.contains(recipe.id())) {
                        reply.setString("status", "RECIPE_QUARANTINED");
                        break;
                    }
                    long now = System.nanoTime();
                    Long last = lastPatternAttempt.get(playerId);
                    if (last != null && now - last >= 0 && now - last < PATTERN_INTERVAL_NANOS) {
                        reply.setString("status", "PATTERN_THROTTLED");
                        break;
                    }
                    lastPatternAttempt.put(playerId, now);
                    long started = System.nanoTime();
                    WorkbenchValidationResult result;
                    try {
                        result = WorkbenchPatternValidator.validate(player.world, recipe, catalog.generation(), inputs, data.getUniqueId("pattern"));
                    } finally {
                        if (System.nanoTime() - started > SLOW_RECIPE_NANOS) {
                            quarantinedRecipes.add(recipe.id());
                            Mekanism.logger.warn("[QIO Workbench] Slow recipe pattern validation quarantined until server restart: {}", recipe.id());
                        }
                    }
                    if (quarantinedRecipes.contains(recipe.id())) {
                        reply.setString("status", "RECIPE_QUARANTINED");
                        break;
                    }
                    if (!result.valid()) {
                        reply.setString("status", "PATTERN_INVALID");
                        reply.setString("patternStatus", result.status().name());
                        reply.setString("detail", result.detail());
                    } else {
                        snapshot = sessions.edit(playerId, request.token, book -> book.addPattern(revision, catalog, result.pattern()));
                        reply = header("PATTERN_SAVED", catalog, snapshot);
                        reply.setUniqueId("pattern", result.pattern().id());
                    }
                    break;
                }
                case "REMOVE_PATTERN": {
                    long revision = revision(data, snapshot, catalog);
                    if (!data.hasUniqueId("pattern")) throw new IllegalArgumentException("Missing pattern id");
                    snapshot = sessions.edit(playerId, request.token, book -> book.removePattern(revision, text(data, "recipe"), data.getUniqueId("pattern")));
                    reply = header("PATTERN_REMOVED", catalog, snapshot);
                    break;
                }
                default: {
                    long revision = revision(data, snapshot, catalog);
                    snapshot = sessions.edit(playerId, request.token, book -> edit(book, catalog, data, action, revision));
                    reply = header("SAVED", catalog, snapshot);
                }
            }
            send(player, request, request.token, reply);
        } catch (WorkbenchSessions.Rejected error) {
            send(player, request, request.token, response(error.reason.name()));
        } catch (WorkbenchRecipeBook.Rejected error) {
            send(player, request, request.token, response(error.reason.name()));
        } catch (IOException error) {
            Mekanism.logger.error("[QIO Workbench] Configuration IO failed", error);
            send(player, request, request.token, response("IO_ERROR"));
        } catch (IllegalArgumentException | IndexOutOfBoundsException error) {
            send(player, request, request.token, response("INVALID_REQUEST"));
        } catch (RuntimeException error) {
            Mekanism.logger.error("[QIO Workbench] Request failed", error);
            send(player, request, request.token, response("FAILED"));
        }
    }

    private void queueExecution(UUID orderId) {
        if (queuedExecutionOrders.add(orderId)) executionQueue.addLast(orderId);
    }

    private static void edit(WorkbenchRecipeBook book, RecipeCatalog catalog, NBTTagCompound data, String action, long revision) {
        switch (action) {
            case "ENABLE":
                book.setEnabled(revision, text(data, "recipe"), bool(data, "enabled"));
                break;
            case "PREFER":
                book.setPreferred(revision, catalog, text(data, "recipe"));
                break;
            case "RESET_ALL":
                book.resetAll(revision);
                break;
            case "CLEAR_ALL":
                book.clearAll(revision);
                break;
            case "SYNC_CHOICES":
                book.syncChoices(revision, catalog, text(data, "recipe"), number(data, "slot"));
                break;
            case "RESET_ROUTE":
                book.resetRoute(revision, text(data, "recipe"));
                break;
            case "REMOVE":
                book.remove(revision, Collections.singleton(text(data, "recipe")));
                break;
            case "CHOICE":
            case "MOVE_CHOICE": {
                RecipeDeclaration recipe = recipe(catalog, data);
                int slot = number(data, "slot");
                if (slot < 0 || slot >= recipe.inputs().size()) throw new IllegalArgumentException("Invalid slot");
                WorkbenchRoute route = book.snapshot().routes().get(recipe.id());
                if (route == null) throw new IllegalArgumentException("Route is not imported");
                List<String> choices = new ArrayList<>();
                for (RecipeItemKey item : route.candidates(recipe, slot)) choices.add(item.fingerprint());
                String selected = text(data, "candidate");
                if ("CHOICE".equals(action)) {
                    if (bool(data, "enabled")) {
                        if (!choices.contains(selected)) choices.add(selected);
                    } else if (!choices.remove(selected))
                        throw new IllegalArgumentException("Unknown enabled candidate");
                } else {
                    int index = number(data, "index");
                    if (index < 0 || index >= choices.size() || !choices.remove(selected))
                        throw new IllegalArgumentException("Invalid candidate position");
                    choices.add(index, selected);
                }
                book.setChoices(revision, catalog, recipe.id(), slot, choices);
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown workbench action");
        }
    }

    private static void list(RecipeCatalog catalog, WorkbenchSnapshot book, NBTTagCompound data, NBTTagCompound reply) {
        int offset = offset(data), limit = limit(data), count = 0;
        String search = text(data, "search").toLowerCase(Locale.ROOT);
        NBTTagList rows = new NBTTagList();
        java.util.Map<String, RecipeDeclaration> products = new java.util.TreeMap<>();
        for (RecipeDeclaration recipe : catalog.recipes()) {
            if (!recipe.id().contains(search) && !recipe.output().itemId().contains(search)) continue;
            products.putIfAbsent(recipe.output().familyKey(), recipe);
        }
        for (RecipeDeclaration recipe : products.values()) {
            if (count >= offset && rows.tagCount() < limit) {
                NBTTagCompound product = new NBTTagCompound();
                product.setString("product", recipe.output().familyKey());
                product.setString("item", recipe.output().itemId());
                product.setInteger("meta", recipe.output().metadata());
                product.setTag("stack", recipe.output().displayNBT());
                product.setInteger("routes", catalog.outputsForFamily(recipe.output().familyKey()).size());
                product.setBoolean("imported", catalog.outputsForFamily(recipe.output().familyKey()).stream().anyMatch(route -> book.routes().containsKey(route.id())));
                rows.appendTag(product);
            }
            count++;
        }
        reply.setInteger("total", count);
        reply.setInteger("offset", Math.min(offset, count));
        reply.setTag("rows", rows);
    }

    private static void page(WorkbenchQuery.Page page, NBTTagCompound reply) {
        NBTTagList rows = new NBTTagList();
        for (WorkbenchQuery.Row row : page.rows) {
            NBTTagCompound tag = row(row.declaration, null, row.status.name(), page.generation);
            tag.setString("recipe", row.recipeId);
            tag.setBoolean("enabled", row.enabled);
            tag.setBoolean("imported", row.imported);
            rows.appendTag(tag);
        }
        reply.setInteger("total", page.total);
        reply.setInteger("offset", page.offset);
        reply.setTag("rows", rows);
    }

    private static NBTTagCompound row(RecipeDeclaration recipe, WorkbenchRoute route, String status) {
        return row(recipe, route, status, null);
    }

    private static NBTTagCompound row(RecipeDeclaration recipe, WorkbenchRoute route, String status, String generation) {
        NBTTagCompound tag = new NBTTagCompound();
        if (recipe != null) {
            tag.setString("recipe", recipe.id());
            tag.setString("signature", recipe.signature());
            tag.setString("item", recipe.output().itemId());
            tag.setInteger("meta", recipe.output().metadata());
            tag.setString("output", recipe.output().fingerprint());
            tag.setInteger("count", recipe.outputCount());
            tag.setTag("stack", recipe.output().displayNBT());
            tag.setString("layout", recipe.layout().name());
            tag.setInteger("width", recipe.width());
            tag.setInteger("height", recipe.height());
            tag.setInteger("slots", recipe.inputs().size());
            tag.setBoolean("complete", recipe.hasDeclaredCandidates());
        }
        if (status == null && recipe != null) status = WorkbenchQuery.describe(recipe, route, generation).status.name();
        if (status != null) tag.setString("state", status);
        tag.setBoolean("imported", route != null);
        tag.setBoolean("enabled", route == null || route.enabled());
        tag.setInteger("patterns", route == null ? 0 : route.patterns().size());
        return tag;
    }

    private static void candidates(RecipeCatalog catalog, WorkbenchSnapshot book, NBTTagCompound data, NBTTagCompound reply) {
        RecipeDeclaration recipe = recipe(catalog, data);
        int slot = number(data, "slot");
        if (slot < 0 || slot >= recipe.inputs().size()) throw new IllegalArgumentException("Invalid slot");
        int offset = offset(data), limit = limit(data);
        List<RecipeItemKey> defaults = recipe.inputs().get(slot).candidates();
        java.util.Map<String, Integer> priorities = new java.util.HashMap<>();
        int index = 0;
        for (RecipeItemKey key : book.candidates(recipe, slot)) priorities.put(key.fingerprint(), index++);
        NBTTagList rows = new NBTTagList();
        for (int i = Math.min(offset, defaults.size()); i < defaults.size() && rows.tagCount() < limit; i++) {
            RecipeItemKey key = defaults.get(i);
            NBTTagCompound row = new NBTTagCompound();
            row.setString("candidate", key.fingerprint());
            row.setString("item", key.itemId());
            row.setInteger("meta", key.metadata());
            row.setTag("stack", key.displayNBT());
            row.setInteger("priority", priorities.getOrDefault(key.fingerprint(), -1));
            rows.appendTag(row);
        }
        reply.setInteger("total", defaults.size());
        reply.setInteger("offset", Math.min(offset, defaults.size()));
        reply.setTag("rows", rows);
    }

    private static void detail(RecipeCatalog catalog, WorkbenchSnapshot book, NBTTagCompound data, NBTTagCompound reply) {
        RecipeDeclaration recipe = recipe(catalog, data);
        reply.setString("recipe", recipe.id());
        reply.setTag("output", recipe.output().displayNBT());
        WorkbenchRoute configured = book.routes().get(recipe.id());
        reply.setBoolean("imported", configured != null);
        reply.setBoolean("enabled", configured == null || configured.enabled());
        reply.setInteger("patternCount", configured == null ? 0 : configured.patterns().size());
        reply.setString("layout", recipe.layout().name());
        reply.setInteger("width", recipe.width());
        reply.setInteger("height", recipe.height());
        NBTTagList slots = new NBTTagList();
        for (int index = 0; index < recipe.inputs().size(); index++) {
            NBTTagCompound slot = new NBTTagCompound();
            slot.setInteger("slot", index);
            slot.setBoolean("empty", recipe.inputs().get(index).isEmptySlot());
            List<RecipeItemKey> choices = book.candidates(recipe, index);
            slot.setInteger("enabled", choices.size());
            slot.setInteger("total", recipe.inputs().get(index).candidates().size());
            if (!choices.isEmpty()) slot.setTag("stack", choices.get(0).displayNBT());
            slots.appendTag(slot);
        }
        reply.setTag("slots", slots);
    }

    private static void patterns(RecipeCatalog catalog, WorkbenchSnapshot book, NBTTagCompound data, NBTTagCompound reply) {
        RecipeDeclaration recipe = recipe(catalog, data);
        WorkbenchRoute route = book.routes().get(recipe.id());
        if (route == null) throw new WorkbenchRecipeBook.Rejected(WorkbenchRecipeBook.Rejection.NOT_IMPORTED);
        int offset = offset(data), limit = limit(data), count = route.patternCount();
        NBTTagList rows = new NBTTagList();
        for (mekanism.qioprocessing.common.pattern.VerifiedWorkbenchPattern pattern : route.patternPage(offset, limit)) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setUniqueId("id", pattern.id());
            entry.setBoolean("valid", route.matches(recipe) && pattern.declarationSignature().equals(recipe.signature())
                    && pattern.catalogGeneration().equals(catalog.generation()));
            entry.setTag("stack", pattern.output().displayNBT());
            entry.setInteger("count", pattern.outputCount());
            rows.appendTag(entry);
        }
        reply.setInteger("total", count);
        reply.setInteger("offset", Math.min(offset, count));
        reply.setTag("rows", rows);
    }

    private static long revision(NBTTagCompound data, WorkbenchSnapshot book, RecipeCatalog catalog) {
        if (!data.hasKey("revision", 4) || data.getLong("revision") != book.revision())
            throw new WorkbenchRecipeBook.Rejected(WorkbenchRecipeBook.Rejection.STALE_REVISION);
        if (!catalog.generation().equals(text(data, "generation")))
            throw new WorkbenchRecipeBook.Rejected(WorkbenchRecipeBook.Rejection.STALE_CATALOG);
        return book.revision();
    }

    private static Map<RecipeItemKey, Long> targets(NBTTagCompound data) {
        if (!data.hasKey("targets", 9)) throw new IllegalArgumentException("targets");
        NBTTagList rows = data.getTagList("targets", 10);
        if (rows.tagCount() == 0 || rows.tagCount() > 128) throw new IllegalArgumentException("targets");
        Map<RecipeItemKey, Long> result = new java.util.LinkedHashMap<>();
        for (int index = 0; index < rows.tagCount(); index++) {
            NBTTagCompound row = rows.getCompoundTagAt(index);
            if (!row.hasKey("item", 10) || !row.hasKey("amount", 4)) throw new IllegalArgumentException("target row");
            long amount = row.getLong("amount");
            if (amount <= 0) throw new IllegalArgumentException("target amount");
            RecipeItemKey item = RecipeItemKey.capture(new ItemStack(row.getCompoundTag("item")));
            if (result.putIfAbsent(item, amount) != null) throw new IllegalArgumentException("duplicate target");
        }
        return Collections.unmodifiableMap(result);
    }

    private static RecipeDeclaration recipe(RecipeCatalog catalog, NBTTagCompound data) {
        RecipeDeclaration recipe = catalog.byId(text(data, "recipe"));
        if (recipe == null) throw new WorkbenchRecipeBook.Rejected(WorkbenchRecipeBook.Rejection.UNKNOWN_RECIPE);
        return recipe;
    }

    private static int offset(NBTTagCompound data) {
        int value = number(data, "offset");
        if (value < 0) throw new IllegalArgumentException();
        return value;
    }

    private static int limit(NBTTagCompound data) {
        int value = number(data, "limit");
        if (value < 1 || value > 128) throw new IllegalArgumentException();
        return value;
    }

    private static int number(NBTTagCompound data, String key) {
        if (!data.hasKey(key, 3)) throw new IllegalArgumentException(key);
        return data.getInteger(key);
    }

    private static List<ItemStack> grid(NBTTagCompound data) {
        if (!data.hasKey("grid", 9)) throw new IllegalArgumentException("grid");
        NBTTagList tags = data.getTagList("grid", 10);
        if (tags.tagCount() != 9) throw new IllegalArgumentException("grid");
        List<ItemStack> result = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            NBTTagCompound slot = tags.getCompoundTagAt(i);
            if (!slot.hasKey("stack", 10)) {
                result.add(ItemStack.EMPTY);
                continue;
            }
            NBTTagCompound serialized = slot.getCompoundTag("stack");
            if (!serialized.hasKey("Count", 1) || serialized.getByte("Count") != 1)
                throw new IllegalArgumentException("grid count");
            ItemStack stack = new ItemStack(serialized);
            if (stack.isEmpty() || stack.getCount() != 1) throw new IllegalArgumentException("grid stack");
            result.add(stack);
        }
        return result;
    }

    private static boolean bool(NBTTagCompound data, String key) {
        if (!data.hasKey(key, 1) || data.getByte(key) < 0 || data.getByte(key) > 1)
            throw new IllegalArgumentException(key);
        return data.getBoolean(key);
    }

    private static String text(NBTTagCompound data, String key) {
        if (!data.hasKey(key, 8)) throw new IllegalArgumentException(key);
        String value = data.getString(key);
        if (value.length() > 2048) throw new IllegalArgumentException(key);
        return value;
    }

    private static NBTTagCompound response(String status) {
        NBTTagCompound reply = new NBTTagCompound();
        reply.setString("status", status);
        return reply;
    }

    private static NBTTagCompound header(String status, RecipeCatalog catalog, WorkbenchSnapshot snapshot) {
        NBTTagCompound reply = response(status);
        reply.setUniqueId("book", snapshot.id());
        reply.setLong("revision", snapshot.revision());
        reply.setString("generation", catalog.generation());
        reply.setInteger("imported", snapshot.routes().size());
        return reply;
    }

    private static void send(EntityPlayerMP player, WorkbenchPacket.Request request, UUID token, NBTTagCompound data) {
        WorkbenchNetwork.reply(player, new WorkbenchPacket.Reply(request.window, request.request, token, data));
    }
}
