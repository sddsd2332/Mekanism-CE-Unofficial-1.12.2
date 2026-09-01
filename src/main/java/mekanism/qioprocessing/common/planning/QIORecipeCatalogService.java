package mekanism.qioprocessing.common.planning;

import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.config.QIOProcessingConfig;
import mekanism.common.config.QIORecipeCatalogScanMode;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfiguration;
import mekanism.qioprocessing.common.util.QIOHashing;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Server-lifecycle cache of frequency-local, manually encoded workbench snapshots. */
/**
 * QIO 处理模块中的 QIORecipeCatalogService 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIORecipeCatalogService {

    public static final QIORecipeCatalogService INSTANCE = new QIORecipeCatalogService();
    private static final double SERVER_TICKS_PER_SECOND = 20D;
    @Nullable
    private QIOWorkbenchRecipeCatalog.Snapshot snapshot;
    @Nullable
    private World world;
    @Nullable
    private QIOWorkbenchRecipeCatalog.RecipeOutputIndex recipeOutputIndex;
    @Nullable
    private QIOWorkbenchRecipeCatalog.RecipeOutputIndex.Capture capture;
    @Nullable
    private ExecutorService catalogWorkers;
    @Nullable
    private CompletableFuture<CacheLoadResult> cacheLoadFuture;
    private List<QIORecipeCatalogPersistence.Candidate> cacheCandidates =
          Collections.emptyList();
    private int cacheCandidateIndex;
    @Nullable
    private QIORecipeCatalogPersistence.Candidate activeCacheCandidate;
    @Nullable
    private QIOWorkbenchRecipeCatalog.RecipeOutputIndex.CacheRestore cacheRestore;
    @Nullable
    private CompletableFuture<CacheSaveResult> cacheSaveFuture;
    @Nullable
    private QIOWorkbenchRecipeCatalog.RecipeOutputIndex deferredCacheSave;
    private boolean deferredCacheSaveForced;
    private long deferredCacheSaveClearEpoch;
    @Nullable
    private QIORecipeCatalogPersistence.SaveToken cacheSaveToken;
    private long captureActiveTicks;
    private int catalogWorkerCount;
    private boolean generationTrusted;
    private QIORecipeCatalogScanMode activeScanMode =
          QIORecipeCatalogScanMode.DISABLED;
    private String environmentSignature = "";
    private long requestedRescanEpoch;
    private long completedRescanEpoch;
    @Nullable
    private List<IRecipe> stableRecipeSnapshot;
    /** Rescan epoch represented by the currently running capture, or zero when idle. */
    private long captureRescanEpoch;
    @Nullable
    private String loadedCacheGenerationId;
    @Nullable
    private String loadedForgeSignature;
    @Nullable
    private UUID loadedDiskGeneration;
    private boolean loadedCacheRequiresRepair;
    private long revision;
    private final QIORecipeTargetedLookupGuard targetedLookupGuard =
          new QIORecipeTargetedLookupGuard();
    private final Map<SnapshotKey, QIOWorkbenchRecipeCatalog.Snapshot> frequencySnapshots =
          new LinkedHashMap<>(64, 0.75F, true) {
              @Override
              protected boolean removeEldestEntry(
                    Map.Entry<SnapshotKey, QIOWorkbenchRecipeCatalog.Snapshot> eldest) {
                  return size() > 64;
              }
          };

    private QIORecipeCatalogService() {
    }

    /**
     * Starts a non-blocking server-lifecycle rebuild. Forge calls are captured in bounded server
     * ticks; only frozen symbol and index data is handed to the configured catalog workers.
     */
    public synchronized void beginRefresh(@Nonnull World world) {
        cancelBuild();
        this.world = Objects.requireNonNull(world, "world");
        snapshot = null;
        // Do not publish an empty provisional index.  Callers use the presence of an index as
        // the initialized signal, and binding that index used to clear every frequency's
        // product-order table while the real capture was still running.
        recipeOutputIndex = null;
        generationTrusted = false;
        loadedCacheGenerationId = null;
        loadedForgeSignature = null;
        loadedDiskGeneration = null;
        loadedCacheRequiresRepair = false;
        requestedRescanEpoch = 0;
        completedRescanEpoch = 0;
        stableRecipeSnapshot = null;
        targetedLookupGuard.clear();
        frequencySnapshots.clear();
        incrementRevision();
        QIOProcessingConfig config = MekanismConfig.local().qioProcessing;
        activeScanMode = config.recipeCatalogScanMode.val();
        if (!activeScanMode.usesPersistentCatalog()) {
            environmentSignature = "";
            recipeOutputIndex = QIOWorkbenchRecipeCatalog.RecipeOutputIndex.empty();
            generationTrusted = true;
            Mekanism.logger.info(
                  "[QIO Recipe Catalog] Scan mode DISABLED: published an empty global directory; direct nine-slot encoding and UUID configuration copies remain available without a catalog scan");
            return;
        }
        List<IRecipe> recipes = getStableRecipeSnapshot();
        environmentSignature = environmentSignature(recipes);
        ensureWorkers();
        cacheSaveToken = new QIORecipeCatalogPersistence.SaveToken();
        File worldDirectory = worldDirectory(world);
        String currentEnvironmentSignature = environmentSignature;
        cacheLoadFuture = worldDirectory == null ? CompletableFuture.completedFuture(
              new CacheLoadResult(Collections.emptyList(),
                    QIORecipeCatalogPersistence.ScanState.empty())) :
              CompletableFuture.supplyAsync(() -> new CacheLoadResult(
                    QIORecipeCatalogPersistence.loadCandidates(worldDirectory,
                          currentEnvironmentSignature),
                    QIORecipeCatalogPersistence.readScanState(worldDirectory)),
                    Objects.requireNonNull(catalogWorkers));
        if (activeScanMode.scansEveryStartup()) {
            startFullCapture(recipes, "FULL mode startup validation");
        } else {
            Mekanism.logger.info(
                  "[QIO Recipe Catalog] {} mode: loading a compatible cached directory before deciding whether a scan is required",
                  activeScanMode);
        }
    }

    public synchronized void refresh(@Nonnull World world) {
        cancelBuild();
        this.world = Objects.requireNonNull(world, "world");
        snapshot = null;
        recipeOutputIndex = null;
        stableRecipeSnapshot = null;
        targetedLookupGuard.clear();
        frequencySnapshots.clear();
        incrementRevision();
        List<IRecipe> recipes = getStableRecipeSnapshot();
        environmentSignature = environmentSignature(recipes);
        long startedNanos = System.nanoTime();
        Mekanism.logger.info(
              "[QIO Recipe Catalog] Synchronous build started: preparing {} registered workbench recipes",
              recipes.size());
        recipeOutputIndex = QIOWorkbenchRecipeCatalog.RecipeOutputIndex.build(world,
              recipes, loggingListener());
        generationTrusted = true;
        String elapsedSeconds = String.format(Locale.ROOT, "%.3f",
              (System.nanoTime() - startedNanos) / 1_000_000_000D);
        Mekanism.logger.info(
              "[QIO Recipe Catalog] Synchronous build completed: published {} workbench recipes in {} seconds",
              recipeOutputIndex.getRecipeCount(), elapsedSeconds);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            synchronized (this) {
                targetedLookupGuard.beginTick();
            }
            return;
        }
        if (event.phase == TickEvent.Phase.END) {
            advanceCapture();
        }
    }

    /** Advances one bounded capture slice; package-visible for lifecycle tests. */
    synchronized boolean advanceCapture() {
        pollCacheLoad();
        pollCacheSave();
        QIOProcessingConfig config = MekanismConfig.local().qioProcessing;
        int maximumCaptures = config.recipeCatalogCapturesPerTick.val();
        long maximumNanos = TimeUnit.MILLISECONDS.toNanos(
              config.recipeCatalogCaptureTimePerTick.val());
        // Cache decoding touches registries and ItemStack capabilities, so it stays on the
        // server thread and owns this tick's bounded capture slice when active.
        if (advanceCacheRestore(maximumCaptures, maximumNanos)) {
            return false;
        }
        if (capture == null) {
            shutdownWorkersWhenIdle();
            return false;
        }
        captureActiveTicks++;
        try {
            if (!capture.process(maximumCaptures, maximumNanos, catalogWorkers,
                  catalogWorkerCount)) {
                return false;
            }
            QIOWorkbenchRecipeCatalog.RecipeOutputIndex next = capture.finish();
            long finishedCaptureEpoch = captureRescanEpoch;
            String previousGeneration = recipeOutputIndex == null ? null :
                  recipeOutputIndex.getGenerationId();
            capture = null;
            captureRescanEpoch = 0;

            // A player (or a recipe loader) may request another scan while this snapshot is
            // being processed. Publishing this stale generation and clearing the marker would
            // lose the newly registered recipe, so queue a fresh stable registry snapshot.
            if (requestedRescanEpoch > finishedCaptureEpoch) {
                List<IRecipe> refreshed = refreshStableRecipeSnapshot();
                environmentSignature = environmentSignature(refreshed);
                targetedLookupGuard.clear();
                startFullCapture(refreshed,
                      "a newer rescan request arrived while the previous build was running");
                Mekanism.logger.info(
                      "[QIO Recipe Catalog] Deferred publication of a stale generation; restarting capture for rescan epoch {}",
                      requestedRescanEpoch);
                return true;
            }

            recipeOutputIndex = next;
            generationTrusted = true;
            frequencySnapshots.clear();
            targetedLookupGuard.clear();
            if (!next.getGenerationId().equals(previousGeneration)) {
                incrementRevision();
            }
            long clearEpochAfterSave = requestedRescanEpoch;
            scheduleCacheSave(next,
                  clearEpochAfterSave > completedRescanEpoch || loadedCacheRequiresRepair,
                  clearEpochAfterSave);
            Mekanism.logger.info(
                  "[QIO Recipe Catalog] Build completed: published {} workbench recipes in {} active server seconds",
                  next.getRecipeCount(), activeCaptureSeconds());
            return true;
        } catch (RuntimeException error) {
            Mekanism.logger.error(
                  "[QIO Recipe Catalog] Build failed after {} active server seconds",
                  activeCaptureSeconds(), error);
            if (capture != null) capture.cancel();
            capture = null;
            captureRescanEpoch = 0;
            if (recipeOutputIndex == null) generationTrusted = false;
            frequencySnapshots.clear();
            shutdownWorkersWhenIdle();
            return false;
        }
    }

    public synchronized boolean isBuilding() {
        return capture != null || cacheLoadFuture != null || cacheRestore != null;
    }

    synchronized void observe(@Nonnull QIOWorkbenchRecipeCatalog.Snapshot next) {
        Objects.requireNonNull(next, "next");
        if (snapshot == null || !snapshot.getStructuralSignature().equals(
              next.getStructuralSignature())) {
            incrementRevision();
        }
        snapshot = next;
        generationTrusted = true;
    }

    @Nonnull
    public synchronized QIOWorkbenchRecipeCatalog.Snapshot getSnapshot() {
        if (snapshot == null) {
            throw new IllegalStateException("QIO workbench recipe catalog is not initialized");
        }
        return snapshot;
    }

    /** Returns the bounded catalog containing only this frequency's encoded patterns. */
    @Nonnull
    public synchronized QIOWorkbenchRecipeCatalog.Snapshot getSnapshot(
          @Nonnull QIOWorkbenchConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        // observe() remains a package-private test hook for deterministic catalog tests.
        if (snapshot != null) {
            return snapshot;
        }
        if (!generationTrusted) {
            throw new IllegalStateException("QIO workbench recipe catalog is still loading");
        }
        if (recipeOutputIndex == null) {
            if (world == null) {
                throw new IllegalStateException("QIO workbench recipe catalog has no server world");
            }
            recipeOutputIndex = activeScanMode == QIORecipeCatalogScanMode.DISABLED ?
                  QIOWorkbenchRecipeCatalog.RecipeOutputIndex.empty() :
                  QIOWorkbenchRecipeCatalog.RecipeOutputIndex.build(world,
                        getStableRecipeSnapshot());
            generationTrusted = true;
        }
        String previousCatalogGeneration = configuration.getCatalogGenerationId();
        configuration.bindCatalogGeneration(recipeOutputIndex.getGenerationId());
        if (!previousCatalogGeneration.equals(recipeOutputIndex.getGenerationId())) {
            frequencySnapshots.keySet().removeIf(existing ->
                  existing.configUUID.equals(configuration.getConfigUUID()));
        }
        // A recipe can keep the same registry ID while its ingredients/signature change (for
        // example through CRT/GRS). Move such entries out of the active planner immediately so
        // they cannot leave this frequency in a permanently invalid state. The immutable copy is
        // retained in recovery for an explicit player decision.
        boolean targetedSnapshot = activeScanMode == QIORecipeCatalogScanMode.DISABLED ||
              activeScanMode.scansAfterCatalogMiss() &&
                    !recipeOutputIndex.arePatternsValid(configuration);
        if (!targetedSnapshot) {
            List<String> invalidPatterns = recipeOutputIndex.invalidPatternIds(configuration);
            if (!invalidPatterns.isEmpty()) {
                int quarantined = configuration.quarantineEncodedPatterns(invalidPatterns);
                if (quarantined > 0) {
                    Mekanism.logger.warn(
                          "[QIO Recipe Catalog] Moved {} changed workbench patterns to recovery for frequency configuration {}",
                          quarantined, configuration.getConfigUUID());
                    frequencySnapshots.keySet().removeIf(existing ->
                          existing.configUUID.equals(configuration.getConfigUUID()));
                }
            }
        }
        SnapshotKey key = new SnapshotKey(configuration.getConfigUUID(),
              configuration.getPatternRevision());
        QIOWorkbenchRecipeCatalog.Snapshot cached = frequencySnapshots.get(key);
        if (cached != null) {
            return cached;
        }
        frequencySnapshots.keySet().removeIf(existing ->
              existing.configUUID.equals(configuration.getConfigUUID()));
        QIOWorkbenchRecipeCatalog.Snapshot next = targetedSnapshot ?
              QIOWorkbenchRecipeCatalog.capturePatterns(world,
                    configuration.getEncodedPatterns(), ignored -> 0) :
              recipeOutputIndex.snapshotFor(configuration);
        frequencySnapshots.put(key, next);
        return next;
    }

    @Nonnull
    public synchronized State getState(@Nonnull QIOWorkbenchConfiguration configuration) {
        return new State(getSnapshot(configuration), getRevision(configuration));
    }

    public synchronized long getRevision() {
        return revision;
    }

    public synchronized long getRevision(@Nonnull QIOWorkbenchConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        long mixed = revision ^ Long.rotateLeft(configuration.getPatternRevision(), 23);
        return mixed & Long.MAX_VALUE;
    }

    /** Restores only recovery patterns which still match the currently published catalog. */
    public synchronized int restoreRecoveryPatterns(@Nonnull QIOWorkbenchConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        QIOWorkbenchRecipeCatalog.RecipeOutputIndex index = recipeOutputIndex;
        if (!generationTrusted) {
            return 0;
        }
        if (index == null) {
            if (world == null) return 0;
            index = QIOWorkbenchRecipeCatalog.RecipeOutputIndex.build(
                  world, getStableRecipeSnapshot());
            recipeOutputIndex = index;
        }
        int restored = configuration.restoreRecoveryPatterns(index::isPatternValid);
        if (restored > 0) {
            frequencySnapshots.keySet().removeIf(existing ->
                  existing.configUUID.equals(configuration.getConfigUUID()));
        }
        return restored;
    }

    public synchronized boolean isInitialized() {
        // A capture/cache restore is an initialized lifecycle as well.  This prevents callers
        // from restarting the build every tick, while isReady() remains the publication gate.
        return snapshot != null || recipeOutputIndex != null || capture != null ||
              cacheLoadFuture != null || cacheRestore != null;
    }

    /** True only after the active generation has been atomically published. */
    public synchronized boolean isReady() {
        return generationTrusted && recipeOutputIndex != null;
    }

    @Nonnull
    public synchronized QIORecipeCatalogScanMode getActiveScanMode() {
        return activeScanMode;
    }

    public synchronized boolean allowsTargetedEncoding() {
        return activeScanMode.allowsTargetedEncoding();
    }

    public synchronized boolean allowsBatchEncoding() {
        return activeScanMode.allowsBatchEncoding();
    }

    public synchronized boolean allowsRecursiveImport() {
        return activeScanMode.allowsRecursiveImport();
    }

    QIORecipeTargetedLookupGuard.Lookup lookupTargetedRecipe(int dimension,
          @Nonnull List<ItemStack> grid) {
        synchronized (this) {
            return targetedLookupGuard.lookup(dimension, grid);
        }
    }

    void rememberTargetedRecipe(int dimension, @Nonnull List<ItemStack> grid,
          @Nullable ResourceLocation recipeId) {
        synchronized (this) {
            targetedLookupGuard.remember(dimension, grid, recipeId);
        }
    }

    void forgetTargetedRecipe(int dimension, @Nonnull List<ItemStack> grid) {
        synchronized (this) {
            targetedLookupGuard.forget(dimension, grid);
        }
    }

    boolean tryAcquireTargetedRecipeSearch() {
        synchronized (this) {
            return targetedLookupGuard.tryAcquireFullSearch();
        }
    }

    /** Marks an on-demand catalog miss durably before starting its background rebuild. */
    public synchronized void encodedPatternsAdded(
          @Nonnull Collection<QIOWorkbenchConfiguration.EncodedPattern> patterns) {
        Objects.requireNonNull(patterns, "patterns");
        if (!activeScanMode.scansAfterCatalogMiss() || patterns.isEmpty()) return;
        QIOWorkbenchRecipeCatalog.RecipeOutputIndex index = recipeOutputIndex;
        QIOWorkbenchConfiguration.EncodedPattern missing = null;
        for (QIOWorkbenchConfiguration.EncodedPattern pattern : patterns) {
            if (pattern != null && (index == null || !index.isPatternValid(pattern))) {
                missing = pattern;
                break;
            }
        }
        if (missing == null) return;
        File directory = world == null ? null : worldDirectory(world);
        // One epoch may cover every miss observed before its capture starts. Once that capture
        // is running, the first later miss must allocate a newer epoch so an unexpected shutdown
        // cannot make the old generation clear work it never captured. Further misses share the
        // already queued follow-up epoch and do not cause an unbounded restart loop.
        boolean followUpAlreadyQueued = hasQueuedFollowUpEpoch(capture != null,
              captureRescanEpoch, requestedRescanEpoch);
        if (!followUpAlreadyQueued) {
            if (directory != null) {
                try {
                    observeScanState(QIORecipeCatalogPersistence.markRescanRequired(directory,
                          missing.getRecipeId().toString()));
                } catch (IOException error) {
                    Mekanism.logger.error(
                          "Unable to persist the QIO recipe catalog rescan marker for {}",
                          missing.getRecipeId(), error);
                    requestRescanLocally();
                }
            } else {
                requestRescanLocally();
            }
        }
        if (capture == null) {
            // CHANGED mode may be reached after a runtime recipe loader has modified Forge's
            // registry. Re-capture the registry now instead of reusing the startup snapshot.
            List<IRecipe> refreshed = refreshStableRecipeSnapshot();
            environmentSignature = environmentSignature(refreshed);
            targetedLookupGuard.clear();
            startFullCapture(refreshed,
                  "an uncached player-encoded recipe");
        }
    }

    /** Returns the parsed immutable directory built for the active server lifecycle. */
    @Nonnull
    synchronized QIOWorkbenchRecipeCatalog.RecipeOutputIndex getRecipeOutputIndex(
          @Nonnull World world) {
        Objects.requireNonNull(world, "world");
        if (this.world == null) {
            this.world = world;
        }
        if (recipeOutputIndex == null) {
            // Direct recipe-selection calls can happen while the lifecycle capture is still in
            // progress.  Give those callers a temporary immutable view without publishing it or
            // touching any frequency configuration.
            if (!generationTrusted) {
                return QIOWorkbenchRecipeCatalog.RecipeOutputIndex.empty();
            }
            recipeOutputIndex = activeScanMode == QIORecipeCatalogScanMode.DISABLED ?
                  QIOWorkbenchRecipeCatalog.RecipeOutputIndex.empty() :
                  QIOWorkbenchRecipeCatalog.RecipeOutputIndex.build(world,
                        getStableRecipeSnapshot());
            generationTrusted = true;
        }
        return recipeOutputIndex;
    }

    @Nonnull
    public synchronized State getState() {
        if (snapshot == null) {
            throw new IllegalStateException("QIO workbench recipe catalog is not initialized");
        }
        return new State(snapshot, revision);
    }

    public synchronized void clear() {
        cancelBuild();
        snapshot = null;
        world = null;
        recipeOutputIndex = null;
        frequencySnapshots.clear();
        revision = 0;
        catalogWorkerCount = 0;
        generationTrusted = false;
        activeScanMode = QIORecipeCatalogScanMode.DISABLED;
        environmentSignature = "";
        requestedRescanEpoch = 0;
        completedRescanEpoch = 0;
        stableRecipeSnapshot = null;
        targetedLookupGuard.clear();
        loadedCacheGenerationId = null;
        loadedForgeSignature = null;
        loadedDiskGeneration = null;
        loadedCacheRequiresRepair = false;
        cacheSaveToken = null;
    }

    private QIOWorkbenchRecipeCatalog.RecipeOutputIndex.BuildListener loggingListener() {
        return new QIOWorkbenchRecipeCatalog.RecipeOutputIndex.BuildListener() {
            @Override
            public void scanComplete(int recipeCount) {
                Mekanism.logger.info(
                      "Global QIO workbench recipe scan found {} recipes; preparing numeric tables",
                      recipeCount);
            }

            @Override
            public void cacheStarted(int recipeCount) {
                Mekanism.logger.info("Caching {} global QIO workbench recipes",
                      recipeCount);
            }
        };
    }

    private void incrementRevision() {
        if (revision == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO recipe catalog revision exhausted");
        }
        revision++;
    }

    private String activeCaptureSeconds() {
        return String.format(Locale.ROOT, "%.3f",
              captureActiveTicks / SERVER_TICKS_PER_SECOND);
    }

    private void cancelBuild() {
        if (cacheSaveToken != null) {
            cacheSaveToken.invalidate();
            cacheSaveToken = null;
        }
        if (cacheLoadFuture != null) {
            cacheLoadFuture.cancel(true);
            cacheLoadFuture = null;
        }
        if (cacheSaveFuture != null) {
            cacheSaveFuture.cancel(true);
            cacheSaveFuture = null;
        }
        deferredCacheSave = null;
        deferredCacheSaveForced = false;
        deferredCacheSaveClearEpoch = 0;
        if (cacheRestore != null) {
            cacheRestore.cancel();
            cacheRestore = null;
        }
        activeCacheCandidate = null;
        cacheCandidates = Collections.emptyList();
        cacheCandidateIndex = 0;
        if (capture != null) {
            capture.cancel();
            capture = null;
        }
        captureRescanEpoch = 0;
        shutdownWorkers();
    }

    private void pollCacheLoad() {
        CompletableFuture<CacheLoadResult> pending = cacheLoadFuture;
        if (pending == null || !pending.isDone()) return;
        cacheLoadFuture = null;
        CacheLoadResult loaded;
        try {
            loaded = pending.join();
        } catch (RuntimeException error) {
            Mekanism.logger.warn("Unable to load the QIO recipe catalog cache", error);
            loaded = new CacheLoadResult(Collections.emptyList(),
                  QIORecipeCatalogPersistence.ScanState.conservative());
        }
        observeScanState(loaded.scanState);
        // A FULL scan may finish before asynchronous cache I/O. Never replace its freshly
        // published result with the older disk generation.
        if (capture == null && generationTrusted && recipeOutputIndex != null) {
            if (isRescanRequired()) {
                scheduleCacheSave(recipeOutputIndex, true, requestedRescanEpoch);
            }
            return;
        }
        List<QIORecipeCatalogPersistence.Candidate> compatible = new ArrayList<>();
        for (QIORecipeCatalogPersistence.Candidate candidate : loaded.candidates) {
            if (environmentSignature.equals(candidate.environmentSignature)) {
                compatible.add(candidate);
            }
        }
        if (!loaded.candidates.isEmpty() && compatible.isEmpty()) {
            Mekanism.logger.info(
                  "[QIO Recipe Catalog] Cached directory belongs to a different Forge environment; scheduling a rebuild");
        }
        cacheCandidates = Collections.unmodifiableList(compatible);
        cacheCandidateIndex = 0;
        beginNextCacheRestore();
    }

    private boolean advanceCacheRestore(int maximumRecords, long maximumNanos) {
        QIOWorkbenchRecipeCatalog.RecipeOutputIndex.CacheRestore active = cacheRestore;
        if (active == null) return false;
        try {
            if (!active.process(maximumRecords, maximumNanos, catalogWorkers,
                  catalogWorkerCount)) {
                return true;
            }
            QIOWorkbenchRecipeCatalog.RecipeOutputIndex restored = active.finish();
            QIORecipeCatalogPersistence.Candidate candidate =
                  Objects.requireNonNull(activeCacheCandidate, "active cache candidate");
            recipeOutputIndex = restored;
            loadedCacheGenerationId = restored.getGenerationId();
            loadedForgeSignature = candidate.cache.forgeData.structuralSignature;
            loadedDiskGeneration = candidate.diskGeneration;
            loadedCacheRequiresRepair = candidate.manifestRepairRequired;
            generationTrusted = true;
            frequencySnapshots.clear();
            targetedLookupGuard.clear();
            incrementRevision();
            cacheRestore = null;
            activeCacheCandidate = null;
            cacheCandidates = Collections.emptyList();
            cacheCandidateIndex = 0;
            if (activeScanMode.scansEveryStartup()) {
                Mekanism.logger.info(
                      "Incrementally loaded {} cached QIO workbench recipes for immediate use; validating Forge data in the background",
                      restored.getRecipeCount());
            } else {
                Mekanism.logger.info(
                      "Incrementally loaded {} cached QIO workbench recipes in {} mode{}",
                      restored.getRecipeCount(), activeScanMode,
                      activeScanMode.scansAfterCatalogMiss() && isRescanRequired() ?
                            "; a persisted rescan is required" :
                            "; full scan skipped");
                if (activeScanMode.scansAfterCatalogMiss() && isRescanRequired()) {
                    startFullCapture(getStableRecipeSnapshot(),
                          "the persisted CHANGED-mode rescan marker");
                } else if (loadedCacheRequiresRepair) {
                    scheduleCacheSave(restored, true, 0);
                }
            }
            return true;
        } catch (RuntimeException error) {
            Mekanism.logger.warn("Ignoring an incompatible QIO recipe catalog generation",
                  error);
            active.cancel();
            cacheRestore = null;
            activeCacheCandidate = null;
            beginNextCacheRestore();
            return true;
        }
    }

    private void beginNextCacheRestore() {
        while (cacheCandidateIndex < cacheCandidates.size()) {
            QIORecipeCatalogPersistence.Candidate candidate =
                  cacheCandidates.get(cacheCandidateIndex++);
            try {
                activeCacheCandidate = candidate;
                cacheRestore = QIOWorkbenchRecipeCatalog.RecipeOutputIndex
                      .beginCacheRestore(candidate.cache);
                return;
            } catch (RuntimeException error) {
                Mekanism.logger.warn("Ignoring an incompatible QIO recipe catalog generation",
                      error);
            }
        }
        activeCacheCandidate = null;
        cacheRestore = null;
        cacheCandidates = Collections.emptyList();
        cacheCandidateIndex = 0;
        if (capture == null && (!generationTrusted || recipeOutputIndex == null)) {
            startFullCapture(getStableRecipeSnapshot(),
                  "no compatible cached directory");
        }
    }

    private void scheduleCacheSave(QIOWorkbenchRecipeCatalog.RecipeOutputIndex next,
          boolean force, long clearRescanEpoch) {
        if (cacheSaveFuture != null) {
            deferredCacheSave = next;
            deferredCacheSaveForced |= force;
            deferredCacheSaveClearEpoch = Math.max(deferredCacheSaveClearEpoch,
                  clearRescanEpoch);
            return;
        }
        ExecutorService workers = catalogWorkers;
        World activeWorld = world;
        if (workers == null || activeWorld == null) return;
        boolean unchanged = next.getGenerationId().equals(loadedCacheGenerationId) &&
              next.getForgeSignature().equals(loadedForgeSignature) &&
              !loadedCacheRequiresRepair;
        if (unchanged && !force) return;
        File worldDirectory = worldDirectory(activeWorld);
        if (worldDirectory == null) return;
        QIORecipeCatalogPersistence.SaveToken token = cacheSaveToken;
        if (token == null) return;
        UUID retainedGeneration = loadedDiskGeneration;
        String savedGenerationId = next.getGenerationId();
        String savedForgeSignature = next.getForgeSignature();
        String savedEnvironmentSignature = environmentSignature;
        cacheSaveFuture = CompletableFuture.supplyAsync(() -> {
            try {
                UUID diskGeneration = QIORecipeCatalogPersistence.save(worldDirectory,
                      next.writeCache(), savedEnvironmentSignature, retainedGeneration, token);
                QIORecipeCatalogPersistence.ScanState scanState =
                      diskGeneration != null && clearRescanEpoch > 0 ?
                            QIORecipeCatalogPersistence.clearRescanRequired(worldDirectory,
                                  clearRescanEpoch) : null;
                return new CacheSaveResult(diskGeneration, savedGenerationId,
                      savedForgeSignature, scanState);
            } catch (IOException error) {
                throw new java.util.concurrent.CompletionException(error);
            }
        }, workers);
    }

    private void pollCacheSave() {
        CompletableFuture<CacheSaveResult> pending = cacheSaveFuture;
        if (pending == null || !pending.isDone()) return;
        cacheSaveFuture = null;
        try {
            CacheSaveResult saved = pending.join();
            if (saved.diskGeneration != null) {
                loadedDiskGeneration = saved.diskGeneration;
                loadedCacheGenerationId = saved.catalogGenerationId;
                loadedForgeSignature = saved.forgeSignature;
                loadedCacheRequiresRepair = false;
                if (saved.scanState != null) observeScanState(saved.scanState);
                Mekanism.logger.info("Persisted the validated QIO recipe catalog generation");
            }
        } catch (RuntimeException error) {
            Mekanism.logger.warn("Unable to persist the QIO recipe catalog generation", error);
        }
        QIOWorkbenchRecipeCatalog.RecipeOutputIndex deferred = deferredCacheSave;
        boolean force = deferredCacheSaveForced;
        long clearEpoch = deferredCacheSaveClearEpoch;
        deferredCacheSave = null;
        deferredCacheSaveForced = false;
        deferredCacheSaveClearEpoch = 0;
        if (deferred != null) {
            scheduleCacheSave(deferred, force, clearEpoch);
        }
    }

    private void startFullCapture(List<? extends IRecipe> recipes, String reason) {
        if (capture != null) return;
        ensureWorkers();
        if (cacheSaveToken == null) {
            cacheSaveToken = new QIORecipeCatalogPersistence.SaveToken();
        }
        captureActiveTicks = 0;
        captureRescanEpoch = requestedRescanEpoch;
        targetedLookupGuard.clear();
        Mekanism.logger.info(
              "[QIO Recipe Catalog] Build started in {} mode ({}): preparing {} registered workbench recipes; players may join while it builds",
              activeScanMode, reason, recipes.size());
        capture = QIOWorkbenchRecipeCatalog.RecipeOutputIndex.beginGlobalCaptureStable(
              Objects.requireNonNull(world, "world"), recipes, loggingListener());
    }

    private void ensureWorkers() {
        if (catalogWorkers != null && !catalogWorkers.isShutdown()) return;
        catalogWorkerCount = MekanismConfig.local().qioProcessing
              .recipeCatalogWorkerThreads.val();
        AtomicInteger workerNumber = new AtomicInteger();
        catalogWorkers = Executors.newFixedThreadPool(catalogWorkerCount, runnable -> {
            Thread thread = new Thread(runnable,
                  "Mekanism-QIO-Recipe-Catalog-" + workerNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    @Nonnull
    synchronized List<IRecipe> getStableRecipeSnapshot() {
        if (stableRecipeSnapshot != null) return stableRecipeSnapshot;
        stableRecipeSnapshot = captureStableRecipeSnapshot();
        return stableRecipeSnapshot;
    }

    /** Re-captures the live Forge registry for a deliberate CHANGED-mode rescan. */
    private synchronized List<IRecipe> refreshStableRecipeSnapshot() {
        stableRecipeSnapshot = captureStableRecipeSnapshot();
        return stableRecipeSnapshot;
    }

    @Nonnull
    private static List<IRecipe> captureStableRecipeSnapshot() {
        List<IRecipe> recipes = new ArrayList<>(ForgeRegistries.RECIPES.getValuesCollection());
        recipes.removeIf(recipe -> recipe == null || recipe.getRegistryName() == null);
        recipes.sort(java.util.Comparator.comparing(recipe ->
              recipe.getRegistryName().toString()));
        return Collections.unmodifiableList(recipes);
    }

    private static String environmentSignature(List<? extends IRecipe> recipes) {
        try {
            return QIORecipeCatalogEnvironment.fingerprint(recipes);
        } catch (RuntimeException | LinkageError error) {
            Mekanism.logger.warn(
                  "Unable to fingerprint the Forge recipe environment; this cache generation will be rebuilt on the next startup",
                  error);
            return QIOHashing.sha256("untrusted-environment|" + UUID.randomUUID());
        }
    }

    private boolean isRescanRequired() {
        return requestedRescanEpoch > completedRescanEpoch;
    }

    /** Package-visible state-machine predicate used by focused rescan lifecycle tests. */
    static boolean hasQueuedFollowUpEpoch(boolean captureActive, long captureEpoch,
          long requestedEpoch) {
        if (captureEpoch < 0 || requestedEpoch < 0) {
            throw new IllegalArgumentException("QIO recipe rescan epochs cannot be negative");
        }
        return captureActive && requestedEpoch > captureEpoch;
    }

    private void observeScanState(QIORecipeCatalogPersistence.ScanState state) {
        requestedRescanEpoch = Math.max(requestedRescanEpoch, state.requestedEpoch);
        completedRescanEpoch = Math.max(completedRescanEpoch, state.completedEpoch);
        completedRescanEpoch = Math.min(completedRescanEpoch, requestedRescanEpoch);
    }

    private void requestRescanLocally() {
        if (requestedRescanEpoch == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO recipe rescan epoch exhausted");
        }
        requestedRescanEpoch++;
    }

    private void shutdownWorkersWhenIdle() {
        if (capture == null && cacheLoadFuture == null && cacheRestore == null &&
            cacheSaveFuture == null && deferredCacheSave == null) {
            shutdownWorkers();
        }
    }

    @Nullable
    private static File worldDirectory(World world) {
        try {
            return world.getSaveHandler().getWorldDirectory();
        } catch (RuntimeException error) {
            Mekanism.logger.debug(
                  "QIO recipe catalog persistence is unavailable for this server world", error);
            return null;
        }
    }

    private void shutdownWorkers() {
        if (catalogWorkers != null) {
            catalogWorkers.shutdownNow();
            catalogWorkers = null;
        }
        catalogWorkerCount = 0;
    }

    private static final class CacheLoadResult {

        private final List<QIORecipeCatalogPersistence.Candidate> candidates;
        private final QIORecipeCatalogPersistence.ScanState scanState;

        private CacheLoadResult(List<QIORecipeCatalogPersistence.Candidate> candidates,
              QIORecipeCatalogPersistence.ScanState scanState) {
            this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
            this.scanState = Objects.requireNonNull(scanState, "scanState");
        }
    }

    private static final class CacheSaveResult {

        @Nullable private final UUID diskGeneration;
        private final String catalogGenerationId;
        private final String forgeSignature;
        @Nullable private final QIORecipeCatalogPersistence.ScanState scanState;

        private CacheSaveResult(@Nullable UUID diskGeneration,
              String catalogGenerationId, String forgeSignature,
              @Nullable QIORecipeCatalogPersistence.ScanState scanState) {
            this.diskGeneration = diskGeneration;
            this.catalogGenerationId = catalogGenerationId;
            this.forgeSignature = forgeSignature;
            this.scanState = scanState;
        }
    }

    /** Atomically pairs a catalog snapshot with the revision that describes it. */
    public static final class State {

        private final QIOWorkbenchRecipeCatalog.Snapshot snapshot;
        private final long revision;

        private State(QIOWorkbenchRecipeCatalog.Snapshot snapshot, long revision) {
            this.snapshot = snapshot;
            this.revision = revision;
        }

        @Nonnull
        public QIOWorkbenchRecipeCatalog.Snapshot getSnapshot() {
            return snapshot;
        }

        public long getRevision() {
            return revision;
        }
    }

    private static final class SnapshotKey {

        private final UUID configUUID;
        private final long patternRevision;

        private SnapshotKey(UUID configUUID, long patternRevision) {
            this.configUUID = Objects.requireNonNull(configUUID, "configUUID");
            this.patternRevision = patternRevision;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof SnapshotKey key)) return false;
            return patternRevision == key.patternRevision &&
                  configUUID.equals(key.configUUID);
        }

        @Override
        public int hashCode() {
            return Objects.hash(configUUID, patternRevision);
        }
    }
}
