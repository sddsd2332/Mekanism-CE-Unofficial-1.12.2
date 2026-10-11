package mekanism.qioprocessing.common.planning;

import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.config.QIORecipeCatalogScanMode;
import mekanism.qioprocessing.common.content.workbench.QIOVerifiedWorkbenchPattern;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfiguration;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfiguration.EncodedPattern;
import mekanism.qioprocessing.common.util.QIOHashing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Server validation knowledge and per-configuration projections of the process declaration
 * directory. No global recipe execution, item/ore capture, disk catalog or server-world snapshot.
 */
public final class QIORecipeCatalogService {
    public static final QIORecipeCatalogService INSTANCE = new QIORecipeCatalogService();
    private final QIOWeightedCache<String, QIOVerifiedWorkbenchPattern> verified = new QIOWeightedCache<>(
            131_072, 128 * QIOWorkbenchMemory.MIB, (key, value) -> QIOWorkbenchMemory.text(key) + value.getRetainedBytes());
    private final QIOWeightedCache<String, String> failures = new QIOWeightedCache<>(
            131_072, 8 * QIOWorkbenchMemory.MIB, (key, value) -> 128 + QIOWorkbenchMemory.text(key) + QIOWorkbenchMemory.text(value));
    private final Map<String, UUID> pending = new LinkedHashMap<>();
    private final QIOWeightedCache<SnapshotKey, QIOWorkbenchRecipeCatalog.Snapshot> snapshots =
            new QIOWeightedCache<>(64, 128 * QIOWorkbenchMemory.MIB, (key, value) -> 192 + value.getRetainedBytes());
    private long epoch = -1;
    private long serverEpoch;
    private long revision;
    private int defaultDimension;
    private boolean started;
    private boolean knowledgeSaturated;
    @Nullable
    private QIOWorkbenchDeclarations boundDirectory;
    @Nullable
    private QIOWorkbenchDiscovery.Bindings bindings;

    private QIORecipeCatalogService() {
    }

    public synchronized void beginRefresh(World world) {
        QIOWorkbenchDirectoryService.requireServerThread(world);
        defaultDimension = world.provider.getDimension();
        started = true;
        synchronizeEpoch();
    }

    public synchronized void refresh(World world) {
        QIOWorkbenchDirectoryService.requireServerThread(world);
        QIOWorkbenchDirectoryService.INSTANCE.rebuild(world);
        synchronizeEpoch();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) synchronized (this) {
            if (started) synchronizeEpoch();
        }
    }

    private void synchronizeEpoch() {
        long next = QIOWorkbenchDirectoryService.INSTANCE.getEpoch();
        if (epoch == next) return;
        for (UUID request : pending.values()) QIOWorkbenchValidationService.INSTANCE.cancel(request);
        pending.clear();
        verified.clear();
        knowledgeSaturated = false;
        failures.clear();
        snapshots.clear();
        bindings = null;
        boundDirectory = null;
        QIOWorkbenchKnowledge.clear();
        epoch = next;
        revision++;
    }

    public synchronized boolean isInitialized() {
        return started;
    }

    public synchronized boolean isReady() {
        QIOWorkbenchDirectoryService.State state = QIOWorkbenchDirectoryService.INSTANCE.getState();
        return started && (state == QIOWorkbenchDirectoryService.State.READY ||
                state == QIOWorkbenchDirectoryService.State.MANUAL_ONLY);
    }

    public synchronized boolean isBuilding() {
        return started && !isReady();
    }

    public synchronized QIOWorkbenchRecipeCatalog.Snapshot getSnapshot() {
        requireReady();
        return QIOWorkbenchRecipeCatalog.emptyPublished(directory().getGeneration());
    }

    public synchronized QIOWorkbenchRecipeCatalog.Snapshot getSnapshot(QIOWorkbenchConfiguration configuration) {
        return getSnapshot(configuration, defaultDimension);
    }

    public synchronized QIOWorkbenchRecipeCatalog.Snapshot getSnapshot(QIOWorkbenchConfiguration configuration, int dimension) {
        requireReady();
        synchronizeEpoch();
        QIOWorkbenchDeclarations directory = directory();
        if (configuration.getRuntimeBook() != null) {
            SnapshotKey key = new SnapshotKey(configuration.getConfigUUID(), configuration.getPatternRevision(),
                    configuration.getRevision(), dimension, revision, configuration.getCatalogGenerationId(),
                    configuration.getRuntimeBook());
            QIOWorkbenchRecipeCatalog.Snapshot cached = snapshots.get(key);
            if (cached != null) return cached;
            QIOWorkbenchRecipeCatalog.Snapshot result = QIOWorkbenchRecipeCatalog.projectBook(
                    configuration.getRuntimeBook(), configuration.getRuntimeCatalog(), dimension, epoch);
            snapshots.put(key, result);
            return result;
        }
        configuration.bindCatalogGeneration(directory.getGeneration());
        SnapshotKey key = new SnapshotKey(configuration.getConfigUUID(), configuration.getPatternRevision(), configuration.getRevision(), dimension, revision);
        QIOWorkbenchRecipeCatalog.Snapshot cached = snapshots.get(key);
        if (cached != null) return cached;
        List<EncodedPattern> ready = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        boolean settled = true;
        for (EncodedPattern pattern : configuration.getEncodedPatterns()) {
            String identity = key(pattern, dimension);
            QIOVerifiedWorkbenchPattern current = verified.get(identity);
            if (current != null) {
                current = current.withId(pattern.getPatternUUID());
                try {
                    migrateConfiguration(configuration, pattern, current);
                    ready.add(EncodedPattern.fromVerified(current));
                } catch (IllegalArgumentException | IllegalStateException conflict) {
                    // Preference failures belong to this configuration, not to the shared
                    // physical grid. Keep another frequency's identical recipe usable.
                    diagnostics.add(pattern.getRecipeId() + ": PREFERENCE_MIGRATION_CONFLICT: " + conflict.getMessage());
                }
            } else {
                String failure = failures.get(identity);
                if (failure == null && knowledgeSaturated)
                    failure = "WORKBENCH_SERVER_KNOWLEDGE_LIMIT: rebuild directory or restart to release validation knowledge";
                if (failure == null) {
                    settled = false;
                    requestValidation(configuration, pattern, dimension, identity);
                }
                diagnostics.add(pattern.getRecipeId() + ": " + (failure == null ? "AWAITING_SERVER_VALIDATION" : failure));
            }
        }
        QIOWorkbenchRecipeCatalog.Snapshot result = QIOWorkbenchRecipeCatalog.projectVerified(
                QIOWorkbenchRecipeCatalog.emptyPublished(directory.getGeneration()), ready, directory, dimension, epoch);
        result = QIOWorkbenchRecipeCatalog.withDiagnostics(result, diagnostics);
        if (settled)
            snapshots.put(new SnapshotKey(configuration.getConfigUUID(), configuration.getPatternRevision(), configuration.getRevision(), dimension, revision), result);
        return result;
    }

    public synchronized State getState(QIOWorkbenchConfiguration configuration) {
        return getState(configuration, defaultDimension);
    }

    public synchronized State getState(QIOWorkbenchConfiguration configuration, int dimension) {
        QIOWorkbenchRecipeCatalog.Snapshot snapshot = getSnapshot(configuration, dimension);
        return new State(snapshot, getRevision(configuration));
    }

    public synchronized boolean isValidating(QIOWorkbenchConfiguration configuration, int dimension) {
        // Book candidates are validated on demand by the selected-plan continuation.
        if (configuration.getRuntimeBook() != null) return false;
        if (knowledgeSaturated) return false;
        if (snapshots.containsKey(new SnapshotKey(configuration.getConfigUUID(), configuration.getPatternRevision(), configuration.getRevision(), dimension, revision)))
            return false;
        for (EncodedPattern pattern : configuration.getEncodedPatterns()) {
            String identity = key(pattern, dimension);
            if (!verified.containsKey(identity) && !failures.containsKey(identity)) return true;
        }
        return false;
    }

    public synchronized boolean isValidating(QIOWorkbenchConfiguration configuration) {
        return isValidating(configuration, defaultDimension);
    }

    public synchronized State getState() {
        return new State(getSnapshot(), getRevision());
    }

    public synchronized long getRevision() {
        synchronizeEpoch();
        return revision;
    }

    public synchronized long getRevision(QIOWorkbenchConfiguration configuration) {
        long value = getRevision() ^ Long.rotateLeft(configuration.getPatternRevision(), 23);
        if (configuration.getRuntimeBook() != null) {
            value ^= Long.parseUnsignedLong(configuration.getCatalogGenerationId().substring(0, 16), 16);
        }
        return value & Long.MAX_VALUE;
    }

    public synchronized void recordVerified(QIOVerifiedWorkbenchPattern pattern, int dimension) {
        synchronizeEpoch();
        EncodedPattern encoded = EncodedPattern.fromVerified(pattern);
        if (!verified.admit(Collections.singletonMap(key(encoded, dimension), pattern))) {
            saturateKnowledge();
            return;
        }
        failures.remove(key(encoded, dimension));
        snapshots.clear();
    }

    public synchronized void recordVerified(QIOVerifiedWorkbenchPattern pattern) {
        recordVerified(pattern, defaultDimension);
    }

    private void requestValidation(QIOWorkbenchConfiguration configuration, EncodedPattern pattern, int dimension, String identity) {
        if (knowledgeSaturated || pending.containsKey(identity) || pending.size() >= 4096) return;
        UUID owner = UUID.randomUUID();
        pending.put(identity, owner);
        long capturedEpoch = epoch;
        long capturedServer = serverEpoch;
        QIOWorkbenchValidationService.Admission admission = QIOWorkbenchValidationService.INSTANCE.submit(
                QIOWorkbenchValidationService.request(owner, dimension, epoch, pattern), result -> {
                    synchronized (QIORecipeCatalogService.this) {
                        if (capturedServer != serverEpoch || capturedEpoch != QIOWorkbenchDirectoryService.INSTANCE.getEpoch() ||
                                !owner.equals(pending.get(identity))) return;
                        pending.remove(identity);
                        if (!result.isValid()) {
                            if (result.getStatus() == QIOWorkbenchValidationService.Status.WORLD_UNAVAILABLE ||
                                    result.getStatus() == QIOWorkbenchValidationService.Status.STALE_GENERATION) return;
                            failure(identity, pattern, result.getStatus() + ": " + result.getDiagnostic());
                            return;
                        }
                        try {
                            QIOVerifiedWorkbenchPattern migrated = migrate(pattern, result);
                            if (pattern.getVerified() != null && !pattern.getVerified().getOutputs().equals(migrated.getOutputs())) {
                                failure(identity, pattern, "Verified physical outputs changed");
                                return;
                            }
                            Map<String, QIOVerifiedWorkbenchPattern> aliases = new LinkedHashMap<>();
                            aliases.put(identity, migrated);
                            aliases.put(key(EncodedPattern.fromVerified(migrated), dimension), migrated);
                            if (!verified.admit(aliases)) {
                                saturateKnowledge();
                                return;
                            }
                            snapshots.clear();
                            revision++;
                        } catch (RuntimeException error) {
                            failure(identity, pattern, error.getClass().getSimpleName() + ": " + error.getMessage());
                        }
                    }
                });
        if (admission != QIOWorkbenchValidationService.Admission.ACCEPTED) pending.remove(identity);
    }

    private QIOVerifiedWorkbenchPattern migrate(EncodedPattern pattern, QIOWorkbenchValidationService.Result result) {
        QIOWorkbenchDeclarations directory = directory();
        QIOWorkbenchDeclarations.Recipe declaration = directory.get(pattern.getRecipeId());
        if (pattern.getVerified() != null) {
            QIOVerifiedWorkbenchPattern previous = pattern.getVerified();
            for (int slot = 0; slot < 9; slot++) {
                if (!previous.getRemainders().get(slot).getKey().equals(result.getRemainders().get(slot).getKey())) {
                    throw new IllegalArgumentException("Physical remainder changed at slot " + slot);
                }
            }
            QIOVerifiedWorkbenchPattern refreshed = previous.revalidated(directory.getGeneration(), result);
            return declaration == null ? refreshed : refreshed.withDeclaration(declaration.getSignature(), refreshed.getLayout());
        }
        String signature = declaration == null ? QIOHashing.sha256("manual|" + pattern.getRecipeId()) : declaration.getSignature();
        QIOWorkbenchDeclarations.Layout layout = QIOWorkbenchDeclarations.Layout.ORDERED_UNKNOWN;
        // Upgrade to the declared layout only when every physical slot is represented there.
        // Otherwise preserve the old concrete physical layout; never shift a persisted grid.
        if (declaration != null) {
            boolean aligned = true;
            for (int slot = 0; slot < 9; slot++) {
                String stack = result.getInputs().get(slot).getKey();
                boolean matches = false;
                for (QIOWorkbenchDeclarations.Choice choice : declaration.getSlots().get(slot)) {
                    if (!choice.isVirtualFluid() && choice.getStack().getKey().equals(stack)) matches = true;
                }
                if (!matches) aligned = false;
            }
            if (aligned) layout = declaration.getLayout();
        }
        return QIOVerifiedWorkbenchPattern.concrete(pattern.getPatternUUID(), pattern.getRecipeId(), signature,
                directory.getGeneration(), layout, result);
    }

    private void failure(String identity, EncodedPattern pattern, String diagnostic) {
        if (!failures.admit(Collections.singletonMap(identity, diagnostic))) saturateKnowledge();
        snapshots.clear();
        revision++;
        Mekanism.logger.warn("[QIO Workbench] Retained incompatible pattern {}: {}", pattern.getRecipeId(), diagnostic);
    }

    private void saturateKnowledge() {
        knowledgeSaturated = true;
        for (UUID owner : pending.values()) QIOWorkbenchValidationService.INSTANCE.cancel(owner);
        pending.clear();
        snapshots.clear();
        revision++;
        Mekanism.logger.warn("[QIO Workbench] WORKBENCH_SERVER_KNOWLEDGE_LIMIT; retained credentials remain valid, new validation paused until rebuild/restart");
    }

    private void migrateConfiguration(QIOWorkbenchConfiguration configuration, EncodedPattern previous,
                                      QIOVerifiedWorkbenchPattern current) {
        Map<Integer, Map<String, List<String>>> aliases = new LinkedHashMap<>();
        // Old schemas hashed capability-neutral candidate identities. Preserve their order and
        // exclusions when a neutral identity expands to several complete capability states.
        if (previous.getVerified() == null) {
            QIOWorkbenchDeclarations.Recipe recipe = directory().get(previous.getRecipeId());
            List<List<QIOWorkbenchDeclarations.Choice>> aligned = recipe == null ? null : recipe.slotsForGrid(current.getGrid());
            for (int slot = 0; slot < 9; slot++) {
                Map<String, List<String>> mapping = new LinkedHashMap<>();
                if (aligned != null) for (QIOWorkbenchDeclarations.Choice choice : aligned.get(slot)) {
                    if (choice.getStack().isEmpty()) continue;
                    addAlias(mapping, choice.getResource(), choice.getAmount(), choice.isVirtualFluid(), choice.getStack());
                }
                if (!current.getGrid().get(slot).isEmpty()) addAlias(mapping, current.getConsumedResource(slot),
                        current.getConsumedAmount(slot), current.getVirtualSlots()[slot], current.getGrid().get(slot));
                aliases.put(slot, mapping);
            }
        }
        configuration.migrateVerified(previous, current, aliases);
    }

    private static void addAlias(Map<String, List<String>> aliases,
                                 mekanism.qioprocessing.api.resource.PortableResourceDescriptor resource, long amount,
                                 boolean virtual, QIOWorkbenchDeclarations.Stack stack) {
        String oldKey = virtual ? "F|" + resource + '|' + amount + '|' + stack.getResource().withoutCapabilities() :
                "I|" + resource.withoutCapabilities();
        String newKey = virtual ? "F|" + resource + '|' + amount + '|' + stack.getResource() : "I|" + resource;
        List<String> replacements = aliases.computeIfAbsent(QIOHashing.sha256(oldKey), ignored -> new ArrayList<>());
        String candidate = QIOHashing.sha256(newKey);
        if (!replacements.contains(candidate)) replacements.add(candidate);
    }

    private static String key(EncodedPattern pattern, int dimension) {
        StringBuilder identity = new StringBuilder().append(dimension).append('|').append(pattern.getRecipeId())
                .append('|').append(pattern.getOutput()).append('@').append(pattern.getOutputAmount());
        for (QIOWorkbenchDeclarations.Stack stack : pattern.getFrozenGrid())
            identity.append('|').append(stack.getKey());
        if (pattern.getVerified() != null) identity.append('|').append(pattern.getVerified().getContentSignature());
        return QIOHashing.sha256(identity);
    }

    public synchronized QIOWorkbenchRecipeCatalog.Snapshot getEncodedSelectionSnapshot(EncodedPattern pattern, int dimension) {
        if (!isReady() || pattern.getVerified() == null || !verified.containsKey(key(pattern, dimension))) return null;
        return QIOWorkbenchRecipeCatalog.projectVerified(QIOWorkbenchRecipeCatalog.emptyPublished(directory().getGeneration()),
                Collections.singletonList(pattern), directory(), dimension, epoch);
    }

    public synchronized QIOWorkbenchDiscovery.Bindings capturePlannerBindings(QIOWorkbenchDeclarations declarations) {
        if (bindings == null || boundDirectory != declarations) {
            Map<ResourceLocation, String> signatures = new LinkedHashMap<>();
            for (QIOWorkbenchDeclarations.Recipe recipe : declarations.getRecipes())
                signatures.put(recipe.getId(), recipe.getSignature());
            bindings = new QIOWorkbenchDiscovery.Bindings(signatures);
            boundDirectory = declarations;
        }
        return bindings;
    }

    public synchronized int restoreRecoveryPatterns(QIOWorkbenchConfiguration configuration) {
        requireReady();
        return configuration.restoreRecoveryPatterns(pattern -> {
            String identity = key(pattern, defaultDimension);
            if (verified.containsKey(identity)) return true;
            if (!failures.containsKey(identity)) requestValidation(configuration, pattern, defaultDimension, identity);
            return false;
        });
    }

    public synchronized void encodedPatternsAdded(Collection<EncodedPattern> patterns) {
        if (!patterns.isEmpty()) snapshots.clear();
    }

    public synchronized QIORecipeCatalogScanMode getActiveScanMode() {
        return MekanismConfig.local().qioProcessing.recipeCatalogScanMode.val();
    }

    public boolean allowsTargetedEncoding() {
        return true;
    }

    public boolean allowsBatchEncoding() {
        return getActiveScanMode().allowsBatchEncoding();
    }

    public boolean allowsRecursiveImport() {
        return getActiveScanMode().allowsRecursiveImport();
    }

    private void requireReady() {
        if (!isReady()) throw new IllegalStateException("Workbench declaration directory is not ready");
    }

    private QIOWorkbenchDeclarations directory() {
        return QIOWorkbenchDirectoryService.INSTANCE.getDirectory();
    }

    public synchronized void clear() {
        knowledgeSaturated = false;
        serverEpoch++;
        QIOWorkbenchKnowledge.clear();
        for (UUID owner : pending.values()) QIOWorkbenchValidationService.INSTANCE.cancel(owner);
        pending.clear();
        verified.clear();
        failures.clear();
        snapshots.clear();
        bindings = null;
        boundDirectory = null;
        started = false;
        epoch = -1;
        revision++;
    }

    public static final class State {
        private final QIOWorkbenchRecipeCatalog.Snapshot snapshot;
        private final long revision;

        private State(QIOWorkbenchRecipeCatalog.Snapshot snapshot, long revision) {
            this.snapshot = snapshot;
            this.revision = revision;
        }

        public QIOWorkbenchRecipeCatalog.Snapshot getSnapshot() {
            return snapshot;
        }

        public long getRevision() {
            return revision;
        }
    }

    private static final class SnapshotKey {
        final UUID config;
        final long patterns;
        final long configuration;
        final int dimension;
        final long revision;
        final String catalogGeneration;
        final Object book;

        SnapshotKey(UUID config, long patterns, long configuration, int dimension, long revision) {
            this(config, patterns, configuration, dimension, revision, "", null);
        }

        SnapshotKey(UUID config, long patterns, long configuration, int dimension, long revision,
                    String catalogGeneration, Object book) {
            this.config = config;
            this.patterns = patterns;
            this.configuration = configuration;
            this.dimension = dimension;
            this.revision = revision;
            this.catalogGeneration = catalogGeneration;
            this.book = book;
        }

        @Override
        public boolean equals(Object value) {
            if (!(value instanceof SnapshotKey key)) return false;
            return config.equals(key.config) && patterns == key.patterns && configuration == key.configuration &&
                    dimension == key.dimension && revision == key.revision && book == key.book && catalogGeneration.equals(key.catalogGeneration);
        }

        @Override
        public int hashCode() {
            return Objects.hash(config, patterns, configuration, dimension, revision, catalogGeneration, System.identityHashCode(book));
        }
    }
}
