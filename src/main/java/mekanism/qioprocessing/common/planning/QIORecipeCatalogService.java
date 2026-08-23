package mekanism.qioprocessing.common.planning;

import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.config.QIOProcessingConfig;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfiguration;
import net.minecraft.item.crafting.IRecipe;
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
    private CompletableFuture<List<QIORecipeCatalogPersistence.Candidate>> cacheLoadFuture;
    private List<QIORecipeCatalogPersistence.Candidate> cacheCandidates =
          Collections.emptyList();
    private int cacheCandidateIndex;
    @Nullable
    private QIORecipeCatalogPersistence.Candidate activeCacheCandidate;
    @Nullable
    private QIOWorkbenchRecipeCatalog.RecipeOutputIndex.CacheRestore cacheRestore;
    @Nullable
    private CompletableFuture<Boolean> cacheSaveFuture;
    @Nullable
    private QIORecipeCatalogPersistence.SaveToken cacheSaveToken;
    private long captureActiveTicks;
    private int catalogWorkerCount;
    private boolean generationTrusted;
    @Nullable
    private String loadedCacheGenerationId;
    @Nullable
    private String loadedForgeSignature;
    @Nullable
    private UUID loadedDiskGeneration;
    private boolean loadedCacheRequiresRepair;
    private long revision;
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
        frequencySnapshots.clear();
        incrementRevision();
        QIOProcessingConfig config = MekanismConfig.local().qioProcessing;
        catalogWorkerCount = config.recipeCatalogWorkerThreads.val();
        AtomicInteger workerNumber = new AtomicInteger();
        catalogWorkers = Executors.newFixedThreadPool(catalogWorkerCount, runnable -> {
            Thread thread = new Thread(runnable,
                  "Mekanism-QIO-Recipe-Catalog-" + workerNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        cacheSaveToken = new QIORecipeCatalogPersistence.SaveToken();
        File worldDirectory = worldDirectory(world);
        cacheLoadFuture = worldDirectory == null ? CompletableFuture.completedFuture(
              Collections.emptyList()) : CompletableFuture.supplyAsync(() ->
                    QIORecipeCatalogPersistence.loadCandidates(worldDirectory), catalogWorkers);
        // Forge registry collections are live and may be mutated by late recipe integrations.
        // Capture a stable server-thread view before handing it to the incremental scanner.
        Collection<IRecipe> recipes = Collections.unmodifiableList(new java.util.ArrayList<>(
              ForgeRegistries.RECIPES.getValuesCollection()));
        captureActiveTicks = 0;
        Mekanism.logger.info(
              "[QIO Recipe Catalog] Build started: preparing {} registered workbench recipes; players may join while it builds",
              recipes.size());
        capture = QIOWorkbenchRecipeCatalog.RecipeOutputIndex.beginGlobalCapture(world,
              recipes, loggingListener());
    }

    public synchronized void refresh(@Nonnull World world) {
        cancelBuild();
        this.world = Objects.requireNonNull(world, "world");
        snapshot = null;
        recipeOutputIndex = null;
        frequencySnapshots.clear();
        incrementRevision();
        Collection<IRecipe> recipes = Collections.unmodifiableList(new java.util.ArrayList<>(
              ForgeRegistries.RECIPES.getValuesCollection()));
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
        if (event.phase != TickEvent.Phase.END) return;
        advanceCapture();
    }

    /** Advances one bounded capture slice; package-visible for lifecycle tests. */
    synchronized boolean advanceCapture() {
        pollCacheLoad();
        pollCacheSave();
        if (capture == null) {
            shutdownWorkersWhenIdle();
            return false;
        }
        captureActiveTicks++;
        try {
            QIOProcessingConfig config = MekanismConfig.local().qioProcessing;
            int maximumCaptures = config.recipeCatalogCapturesPerTick.val();
            long maximumNanos = TimeUnit.MILLISECONDS.toNanos(
                  config.recipeCatalogCaptureTimePerTick.val());
            // Cache decoding touches registries and ItemStack capabilities, so it stays on the
            // server thread and owns this tick's bounded capture slice when active.
            if (advanceCacheRestore(maximumCaptures, maximumNanos)) {
                return false;
            }
            if (!capture.process(maximumCaptures, maximumNanos, catalogWorkers,
                  catalogWorkerCount)) {
                return false;
            }
            QIOWorkbenchRecipeCatalog.RecipeOutputIndex next = capture.finish();
            String previousGeneration = recipeOutputIndex == null ? null :
                  recipeOutputIndex.getGenerationId();
            recipeOutputIndex = next;
            capture = null;
            generationTrusted = true;
            frequencySnapshots.clear();
            if (!next.getGenerationId().equals(previousGeneration)) {
                incrementRevision();
            }
            scheduleCacheSave(next);
            Mekanism.logger.info(
                  "[QIO Recipe Catalog] Build completed: published {} workbench recipes in {} active server seconds",
                  next.getRecipeCount(), activeCaptureSeconds());
            return true;
        } catch (RuntimeException error) {
            Mekanism.logger.error(
                  "[QIO Recipe Catalog] Build failed after {} active server seconds",
                  activeCaptureSeconds(), error);
            cancelBuild();
            recipeOutputIndex = null;
            generationTrusted = false;
            frequencySnapshots.clear();
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
        if (capture != null || !generationTrusted) {
            throw new IllegalStateException("QIO workbench recipe catalog is still loading");
        }
        if (recipeOutputIndex == null) {
            if (world == null) {
                throw new IllegalStateException("QIO workbench recipe catalog has no server world");
            }
            recipeOutputIndex = QIOWorkbenchRecipeCatalog.RecipeOutputIndex.build(
                  world, ForgeRegistries.RECIPES.getValuesCollection());
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
        SnapshotKey key = new SnapshotKey(configuration.getConfigUUID(),
              configuration.getPatternRevision());
        QIOWorkbenchRecipeCatalog.Snapshot cached = frequencySnapshots.get(key);
        if (cached != null) {
            return cached;
        }
        frequencySnapshots.keySet().removeIf(existing ->
              existing.configUUID.equals(configuration.getConfigUUID()));
        QIOWorkbenchRecipeCatalog.Snapshot next = recipeOutputIndex.snapshotFor(configuration);
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
        if (capture != null || !generationTrusted) {
            return 0;
        }
        if (index == null) {
            if (world == null) return 0;
            index = QIOWorkbenchRecipeCatalog.RecipeOutputIndex.build(
                  world, ForgeRegistries.RECIPES.getValuesCollection());
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
        return generationTrusted && capture == null && recipeOutputIndex != null;
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
            if (capture != null || !generationTrusted) {
                return QIOWorkbenchRecipeCatalog.RecipeOutputIndex.build(
                      world, new java.util.ArrayList<>(ForgeRegistries.RECIPES.getValuesCollection()));
            }
            recipeOutputIndex = QIOWorkbenchRecipeCatalog.RecipeOutputIndex.build(
                  world, new java.util.ArrayList<>(ForgeRegistries.RECIPES.getValuesCollection()));
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
        shutdownWorkers();
    }

    private void pollCacheLoad() {
        CompletableFuture<List<QIORecipeCatalogPersistence.Candidate>> pending =
              cacheLoadFuture;
        if (pending == null || !pending.isDone()) return;
        cacheLoadFuture = null;
        if (capture == null) return;
        List<QIORecipeCatalogPersistence.Candidate> candidates;
        try {
            candidates = pending.join();
        } catch (RuntimeException error) {
            Mekanism.logger.warn("Unable to load the QIO recipe catalog cache", error);
            candidates = Collections.emptyList();
        }
        cacheCandidates = candidates;
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
            frequencySnapshots.clear();
            incrementRevision();
            cacheRestore = null;
            activeCacheCandidate = null;
            cacheCandidates = Collections.emptyList();
            cacheCandidateIndex = 0;
            Mekanism.logger.info(
                  "Incrementally loaded {} cached QIO workbench recipes; validating Forge data in the background",
                  restored.getRecipeCount());
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
        while (capture != null && cacheCandidateIndex < cacheCandidates.size()) {
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
    }

    private void scheduleCacheSave(QIOWorkbenchRecipeCatalog.RecipeOutputIndex next) {
        ExecutorService workers = catalogWorkers;
        World activeWorld = world;
        if (workers == null || activeWorld == null) return;
        boolean unchanged = next.getGenerationId().equals(loadedCacheGenerationId) &&
              next.getForgeSignature().equals(loadedForgeSignature) &&
              !loadedCacheRequiresRepair;
        if (unchanged) return;
        File worldDirectory = worldDirectory(activeWorld);
        if (worldDirectory == null) return;
        QIORecipeCatalogPersistence.SaveToken token = cacheSaveToken;
        if (token == null) return;
        UUID retainedGeneration = loadedDiskGeneration;
        cacheSaveFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return QIORecipeCatalogPersistence.save(worldDirectory, next.writeCache(),
                      retainedGeneration, token);
            } catch (IOException error) {
                throw new java.util.concurrent.CompletionException(error);
            }
        }, workers);
    }

    private void pollCacheSave() {
        CompletableFuture<Boolean> pending = cacheSaveFuture;
        if (pending == null || !pending.isDone()) return;
        cacheSaveFuture = null;
        try {
            if (pending.join()) {
                Mekanism.logger.info("Persisted the validated QIO recipe catalog generation");
            }
        } catch (RuntimeException error) {
            Mekanism.logger.warn("Unable to persist the QIO recipe catalog generation", error);
        }
    }

    private void shutdownWorkersWhenIdle() {
        if (capture == null && cacheLoadFuture == null && cacheRestore == null &&
            cacheSaveFuture == null) {
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
