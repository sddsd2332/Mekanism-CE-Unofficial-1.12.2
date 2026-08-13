package mekanism.qioprocessing.common.planning;

import mekanism.common.Mekanism;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfiguration;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Server-lifecycle cache of frequency-local, manually encoded workbench snapshots. */
public final class QIORecipeCatalogService {

    public static final QIORecipeCatalogService INSTANCE = new QIORecipeCatalogService();

    @Nullable
    private QIOWorkbenchRecipeCatalog.Snapshot snapshot;
    @Nullable
    private World world;
    @Nullable
    private QIOWorkbenchRecipeCatalog.RecipeOutputIndex recipeOutputIndex;
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

    public synchronized void refresh(@Nonnull World world) {
        this.world = Objects.requireNonNull(world, "world");
        snapshot = null;
        recipeOutputIndex = null;
        frequencySnapshots.clear();
        if (revision == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO recipe catalog revision exhausted");
        }
        revision++;
        Mekanism.logger.info("Starting global QIO workbench recipe scan");
        long startedNanos = System.nanoTime();
        recipeOutputIndex = QIOWorkbenchRecipeCatalog.RecipeOutputIndex.build(world,
              ForgeRegistries.RECIPES.getValuesCollection(),
              new QIOWorkbenchRecipeCatalog.RecipeOutputIndex.BuildListener() {
                  @Override
                  public void scanComplete(int recipeCount) {
                      Mekanism.logger.info(
                            "Global QIO workbench recipe scan found {} recipes; preparing to cache them",
                            recipeCount);
                  }

                  @Override
                  public void cacheStarted(int recipeCount) {
                      Mekanism.logger.info("Caching {} global QIO workbench recipes",
                            recipeCount);
                  }
              });
        String elapsedSeconds = String.format(Locale.ROOT, "%.3f",
              (System.nanoTime() - startedNanos) / 1_000_000_000D);
        Mekanism.logger.info("Successfully cached {} global QIO workbench recipes in {} seconds",
              recipeOutputIndex.getRecipeCount(), elapsedSeconds);
    }

    synchronized void observe(@Nonnull QIOWorkbenchRecipeCatalog.Snapshot next) {
        Objects.requireNonNull(next, "next");
        if (snapshot == null || !snapshot.getStructuralSignature().equals(
              next.getStructuralSignature())) {
            if (revision == Long.MAX_VALUE) {
                throw new IllegalStateException("QIO recipe catalog revision exhausted");
            }
            revision++;
        }
        snapshot = next;
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
        if (recipeOutputIndex == null) {
            if (world == null) {
                throw new IllegalStateException("QIO workbench recipe catalog has no server world");
            }
            recipeOutputIndex = QIOWorkbenchRecipeCatalog.RecipeOutputIndex.build(
                  world, ForgeRegistries.RECIPES.getValuesCollection());
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

    public synchronized boolean isInitialized() {
        return snapshot != null || world != null;
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
            recipeOutputIndex = QIOWorkbenchRecipeCatalog.RecipeOutputIndex.build(
                  world, ForgeRegistries.RECIPES.getValuesCollection());
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
        snapshot = null;
        world = null;
        recipeOutputIndex = null;
        frequencySnapshots.clear();
        revision = 0;
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
