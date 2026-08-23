package mekanism.qioprocessing.common.planning;

import mekanism.common.Mekanism;
import mekanism.qioprocessing.common.content.QIOProcessingStorageIO;
import mekanism.qioprocessing.common.util.QIOHashing;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sharded, generation-based persistence for the global QIO workbench catalog. */
final class QIORecipeCatalogPersistence {

    /** Cache format v3: sparse layouts, shared candidates, and bounded candidate identities. */
    static final int SCHEMA_VERSION = 3;
    private static final int MAX_SHARDS = 1_000_000;
    private static final int MAX_RECORDS_PER_SHARD = 4_096;
    private static final long TARGET_ESTIMATED_SHARD_BYTES = 12L * 1024 * 1024;
    private static final int MAX_RECOVERY_ARCHIVES = 8;
    private static final String ROOT_PATH = "mekanism/qio_processing/recipe_catalog";
    private static final String MANIFEST_FILE = "manifest.dat";
    private static final String METADATA_FILE = "metadata.dat";
    private static final Map<String, Object> DIRECTORY_LOCKS = new ConcurrentHashMap<>();

    private QIORecipeCatalogPersistence() {
    }

    @Nonnull
    static List<Candidate> loadCandidates(
          @Nonnull File worldDirectory) {
        try {
            Directories directories = new Directories(worldDirectory);
            synchronized (directoryLock(directories)) {
                List<ManifestSource> manifests = new ArrayList<>(2);
                readManifest(directories.manifest, true, manifests);
                readManifest(QIOProcessingStorageIO.backupFile(directories.manifest), false,
                      manifests);
                Set<UUID> attempted = new LinkedHashSet<>();
                List<Candidate> result = new ArrayList<>(2);
                for (ManifestSource source : manifests) {
                    Manifest manifest = source.manifest;
                    if (manifest.active != null && attempted.add(manifest.active)) {
                        readCandidate(directories, manifest.active,
                              !source.primary, result);
                    }
                    if (manifest.previous != null && attempted.add(manifest.previous)) {
                        readCandidate(directories, manifest.previous, true, result);
                    }
                }
                if (result.isEmpty() && hasCacheState(directories)) {
                    quarantineInvalidCache(directories);
                }
                return Collections.unmodifiableList(result);
            }
        } catch (IOException | RuntimeException error) {
            Mekanism.logger.warn("Unable to read the QIO recipe catalog cache; rebuilding it",
                  error);
            try {
                Directories directories = new Directories(worldDirectory);
                synchronized (directoryLock(directories)) {
                    quarantineInvalidCache(directories);
                }
            } catch (IOException | RuntimeException ignored) {
                Mekanism.logger.debug("Unable to quarantine the invalid QIO recipe catalog cache",
                      ignored);
            }
            return Collections.emptyList();
        }
    }

    private static boolean hasCacheState(Directories directories) {
        return directories.manifest.exists() ||
              QIOProcessingStorageIO.backupFile(directories.manifest).exists() ||
              directories.generations.exists() || directories.staging.exists();
    }

    /** Moves incompatible cache state to a recoverable folder instead of retrying it forever. */
    private static void quarantineInvalidCache(Directories directories) throws IOException {
        if (!hasCacheState(directories)) return;
        File recoveryRoot = child(directories.root, "recovery");
        QIOProcessingStorageIO.ensureDirectory(recoveryRoot);
        String suffix = Long.toString(System.currentTimeMillis());
        File destination = child(recoveryRoot, suffix);
        int attempt = 0;
        while (destination.exists()) {
            destination = child(recoveryRoot, suffix + '-' + (++attempt));
        }
        QIOProcessingStorageIO.ensureDirectory(destination);
        moveIfExists(directories.manifest, child(destination, MANIFEST_FILE));
        moveIfExists(QIOProcessingStorageIO.backupFile(directories.manifest),
              child(destination, MANIFEST_FILE + ".bak"));
        moveIfExists(directories.generations, child(destination, "generations"));
        moveIfExists(directories.staging, child(destination, "staging"));
        Mekanism.logger.warn(
              "Moved incompatible QIO recipe catalog cache to {} for manual recovery; rebuilding a fresh cache",
              destination);
        retainRecoveryArchives(recoveryRoot);
    }

    /** Keeps recovery bounded so repeated bad cache writes cannot grow the world indefinitely. */
    private static void retainRecoveryArchives(@Nonnull File recoveryRoot) {
        File[] archives = recoveryRoot.listFiles(File::isDirectory);
        if (archives == null || archives.length <= MAX_RECOVERY_ARCHIVES) return;
        List<File> ordered = new ArrayList<>(archives.length);
        Collections.addAll(ordered, archives);
        ordered.sort(Comparator.comparingLong(File::lastModified).reversed()
              .thenComparing(File::getName));
        for (int index = MAX_RECOVERY_ARCHIVES; index < ordered.size(); index++) {
            File archive = ordered.get(index);
            try {
                deleteTree(recoveryRoot, archive);
                Mekanism.logger.info("Removed old QIO recipe catalog recovery archive {}", archive);
            } catch (IOException error) {
                Mekanism.logger.warn("Unable to remove old QIO recipe catalog recovery archive {}",
                      archive, error);
            }
        }
    }

    private static void moveIfExists(File source, File target) throws IOException {
        if (!source.exists()) return;
        moveDirectory(source, target);
    }

    static void save(@Nonnull File worldDirectory,
          @Nonnull QIOWorkbenchRecipeCatalog.RecipeOutputIndex.CacheData cache)
          throws IOException {
        Directories directories = new Directories(worldDirectory);
        synchronized (directoryLock(directories)) {
            Manifest current = firstManifest(directories.manifest);
            saveLocked(directories, cache, current == null ? null : current.active,
                  new SaveToken());
        }
    }

    static boolean save(@Nonnull File worldDirectory,
          @Nonnull QIOWorkbenchRecipeCatalog.RecipeOutputIndex.CacheData cache,
          @Nullable UUID retainedGeneration, @Nonnull SaveToken token) throws IOException {
        Directories directories = new Directories(worldDirectory);
        synchronized (directoryLock(directories)) {
            return saveLocked(directories, cache, retainedGeneration, token);
        }
    }

    private static boolean saveLocked(Directories directories,
          QIOWorkbenchRecipeCatalog.RecipeOutputIndex.CacheData cache,
          @Nullable UUID retainedGeneration, SaveToken token) throws IOException {
        if (!token.isValid()) return false;
        QIOProcessingStorageIO.ensureDirectory(directories.generations);
        QIOProcessingStorageIO.ensureDirectory(directories.staging);
        UUID generation = UUID.randomUUID();
        File staging = child(directories.staging, generation.toString());
        File completed = child(directories.generations, generation.toString());
        if (staging.exists() || completed.exists()) {
            throw new IOException("QIO recipe catalog generation already exists: " + generation);
        }
        QIOProcessingStorageIO.ensureDirectory(staging);
        boolean published = false;
        try {
            List<ShardMetadata> shards = new ArrayList<>();
            List<NBTTagCompound> items = new ArrayList<>(
                  cache.forgeData.items.size() + cache.itemTypes.size());
            cache.forgeData.items.forEach(record -> items.add(tagged("registry", record)));
            cache.itemTypes.forEach(record -> items.add(tagged("catalog", record)));
            writeShards(staging, generation, "items", items, shards);
            writeShards(staging, generation, "candidates", cache.candidates, shards);
            writeShards(staging, generation, "variants", cache.forgeData.variants, shards);
            writeShards(staging, generation, "ore_dictionary", cache.forgeData.ores, shards);
            writeShards(staging, generation, "matchers", cache.matchers, shards);
            writeShards(staging, generation, "recipes", cache.recipes, shards);
            writeShards(staging, generation, "output_profiles", cache.outputProfiles, shards);
            writeShards(staging, generation, "candidate_profiles", cache.candidateProfiles, shards);
            writeShards(staging, generation, "reverse_indexes", cache.reverseIndexes, shards);
            NBTTagCompound metadata = new NBTTagCompound();
            metadata.setInteger("schemaVersion", SCHEMA_VERSION);
            metadata.setString("generationUUID", generation.toString());
            metadata.setString("catalogGenerationId", cache.generationId);
            metadata.setString("forgeSignature", cache.forgeData.structuralSignature);
            metadata.setInteger("recipeCount", cache.recipeCount);
            NBTTagList shardList = new NBTTagList();
            shards.forEach(shard -> shardList.appendTag(shard.write()));
            metadata.setTag("shards", shardList);
            metadata.setString("metadataHash", metadataHash(metadata));
            QIOProcessingStorageIO.replaceAtomicWithoutBackup(
                  child(staging, METADATA_FILE), metadata);
            moveDirectory(staging, completed);

            UUID previous = generation.equals(retainedGeneration) ? null : retainedGeneration;
            Manifest next = new Manifest(generation,
                  previous, cache.generationId);
            if (!token.publish(() ->
                  QIOProcessingStorageIO.writeAtomic(directories.manifest, next.write()))) {
                return false;
            }
            published = true;
            retainGenerations(directories.generations, generation, next.previous);
            return true;
        } finally {
            if (!published && staging.isDirectory()) {
                deleteTree(directories.staging, staging);
            }
            if (!published && completed.isDirectory()) {
                deleteTree(directories.generations, completed);
            }
        }
    }

    private static void readCandidate(Directories directories, UUID generation,
          boolean manifestRepairRequired, List<Candidate> output) {
        try {
            output.add(new Candidate(readGeneration(
                  child(directories.generations, generation.toString()), generation),
                  generation, manifestRepairRequired));
        } catch (IOException | RuntimeException error) {
            Mekanism.logger.warn("Ignoring invalid QIO recipe catalog generation {}",
                  generation, error);
        }
    }

    private static QIOWorkbenchRecipeCatalog.RecipeOutputIndex.CacheData readGeneration(
          File directory, UUID expectedGeneration) throws IOException {
        NBTTagCompound metadata = requireFile(child(directory, METADATA_FILE));
        if (metadata.getInteger("schemaVersion") != SCHEMA_VERSION ||
            !expectedGeneration.toString().equals(metadata.getString("generationUUID")) ||
            !metadataHash(metadata).equals(metadata.getString("metadataHash"))) {
            throw new IOException("QIO recipe catalog metadata is invalid");
        }
        NBTTagList storedShards = metadata.getTagList("shards", 10);
        if (storedShards.tagCount() > MAX_SHARDS) {
            throw new IOException("QIO recipe catalog contains too many shards");
        }
        Map<String, List<NBTTagCompound>> categories = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        for (int index = 0; index < storedShards.tagCount(); index++) {
            ShardMetadata shard = ShardMetadata.read(storedShards.getCompoundTagAt(index));
            if (!names.add(shard.fileName)) {
                throw new IOException("Duplicate QIO recipe catalog shard: " + shard.fileName);
            }
            NBTTagCompound data = requireFile(child(directory, shard.fileName));
            validateShard(data, expectedGeneration, shard);
            NBTTagList records = data.getTagList("records", 10);
            List<NBTTagCompound> category = categories.computeIfAbsent(shard.category,
                  ignored -> new ArrayList<>());
            for (int record = 0; record < records.tagCount(); record++) {
                category.add(records.getCompoundTagAt(record).copy());
            }
        }
        List<NBTTagCompound> registryItems = new ArrayList<>();
        List<NBTTagCompound> itemTypes = new ArrayList<>();
        for (NBTTagCompound tagged : category(categories, "items")) {
            String table = tagged.getString("table");
            if ("registry".equals(table)) registryItems.add(tagged.getCompoundTag("record"));
            else if ("catalog".equals(table)) itemTypes.add(tagged.getCompoundTag("record"));
            else throw new IOException("Unknown QIO item cache table: " + table);
        }
        QIOForgeRecipeData.CacheData forgeData = new QIOForgeRecipeData.CacheData(
              registryItems, category(categories, "variants"),
              category(categories, "ore_dictionary"), metadata.getString("forgeSignature"));
        QIOWorkbenchRecipeCatalog.RecipeOutputIndex.CacheData cache =
              new QIOWorkbenchRecipeCatalog.RecipeOutputIndex.CacheData(
              metadata.getString("catalogGenerationId"), metadata.getInteger("recipeCount"),
              forgeData, itemTypes, category(categories, "candidates"),
              category(categories, "matchers"), category(categories, "recipes"),
              category(categories, "output_profiles"),
              category(categories, "candidate_profiles"),
              category(categories, "reverse_indexes"));
        if (!categories.isEmpty()) {
            throw new IOException("QIO recipe catalog contains unknown shard categories");
        }
        return cache;
    }

    private static List<NBTTagCompound> category(
          Map<String, List<NBTTagCompound>> categories, String name) {
        List<NBTTagCompound> values = categories.remove(name);
        return values == null ? Collections.emptyList() : values;
    }

    private static void validateShard(NBTTagCompound data, UUID generation,
          ShardMetadata expected) throws IOException {
        if (data.getInteger("schemaVersion") != SCHEMA_VERSION ||
            !generation.toString().equals(data.getString("generationUUID")) ||
            !expected.category.equals(data.getString("category")) ||
            expected.index != data.getInteger("index")) {
            throw new IOException("QIO recipe catalog shard header is invalid: " +
                  expected.fileName);
        }
        NBTTagList records = data.getTagList("records", 10);
        String hash = payloadHash(expected.category, expected.index, records);
        if (records.tagCount() != expected.recordCount || !hash.equals(expected.hash) ||
            !hash.equals(data.getString("payloadHash"))) {
            throw new IOException("QIO recipe catalog shard checksum is invalid: " +
                  expected.fileName);
        }
    }

    private static void writeShards(File directory, UUID generation, String category,
          List<NBTTagCompound> records, List<ShardMetadata> metadata) throws IOException {
        List<List<NBTTagCompound>> chunks = chunks(records);
        int index = 0;
        for (List<NBTTagCompound> chunk : chunks) {
            index = writeShard(directory, generation, category, chunk, index, metadata);
        }
    }

    private static int writeShard(File directory, UUID generation, String category,
          List<NBTTagCompound> records, int index, List<ShardMetadata> metadata)
          throws IOException {
        if (metadata.size() >= MAX_SHARDS) {
            throw new IOException("QIO recipe catalog contains too many shards");
        }
        NBTTagList values = new NBTTagList();
        records.forEach(record -> values.appendTag(record.copy()));
        String hash = payloadHash(category, index, values);
        String fileName = category + '-' + String.format(java.util.Locale.ROOT,
              "%05d.dat", index);
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("schemaVersion", SCHEMA_VERSION);
        data.setString("generationUUID", generation.toString());
        data.setString("category", category);
        data.setInteger("index", index);
        data.setTag("records", values);
        data.setString("payloadHash", hash);
        File file = child(directory, fileName);
        try {
            QIOProcessingStorageIO.replaceAtomicWithoutBackup(file, data);
        } catch (IOException error) {
            if (!QIOProcessingStorageIO.isFileTooLarge(error) || records.size() <= 1) {
                throw error;
            }
            int middle = records.size() / 2;
            int next = writeShard(directory, generation, category,
                  new ArrayList<>(records.subList(0, middle)), index, metadata);
            return writeShard(directory, generation, category,
                  new ArrayList<>(records.subList(middle, records.size())), next, metadata);
        }
        NBTTagCompound verified = requireFile(file);
        ShardMetadata shard = new ShardMetadata(fileName, category, index,
              records.size(), hash);
        validateShard(verified, generation, shard);
        metadata.add(shard);
        return index + 1;
    }

    private static List<List<NBTTagCompound>> chunks(List<NBTTagCompound> records) {
        if (records.isEmpty()) return Collections.emptyList();
        List<List<NBTTagCompound>> result = new ArrayList<>();
        List<NBTTagCompound> current = new ArrayList<>();
        long estimatedBytes = 0;
        for (NBTTagCompound record : records) {
            long estimate = Math.max(128L, (long) record.toString().length() * 2L);
            if (!current.isEmpty() && (current.size() >= MAX_RECORDS_PER_SHARD ||
                estimatedBytes + estimate > TARGET_ESTIMATED_SHARD_BYTES)) {
                result.add(Collections.unmodifiableList(current));
                current = new ArrayList<>();
                estimatedBytes = 0;
            }
            current.add(record);
            estimatedBytes += estimate;
        }
        if (!current.isEmpty()) result.add(Collections.unmodifiableList(current));
        return result;
    }

    private static NBTTagCompound tagged(String table, NBTTagCompound record) {
        NBTTagCompound tagged = new NBTTagCompound();
        tagged.setString("table", table);
        tagged.setTag("record", record.copy());
        return tagged;
    }

    private static String payloadHash(String category, int index, NBTTagList records) {
        return QIOHashing.sha256(category + '|' + index + '|' + canonical(records));
    }

    private static String metadataHash(NBTTagCompound metadata) {
        NBTTagCompound copy = metadata.copy();
        copy.removeTag("metadataHash");
        return QIOHashing.sha256(canonical(copy));
    }

    private static void readManifest(File file, boolean primary,
          List<ManifestSource> output) {
        try {
            NBTTagCompound data = QIOProcessingStorageIO.read(file);
            if (data != null) output.add(new ManifestSource(Manifest.read(data), primary));
        } catch (IOException | RuntimeException error) {
            Mekanism.logger.warn("Ignoring invalid QIO recipe catalog manifest {}", file,
                  error);
        }
    }

    @Nullable
    private static Manifest firstManifest(File file) {
        List<ManifestSource> manifests = new ArrayList<>(1);
        readManifest(file, true, manifests);
        return manifests.isEmpty() ? null : manifests.get(0).manifest;
    }

    private static NBTTagCompound requireFile(File file) throws IOException {
        NBTTagCompound data = QIOProcessingStorageIO.read(file);
        if (data == null) throw new IOException("Missing QIO recipe catalog file: " + file);
        return data;
    }

    private static String canonical(NBTBase value) {
        StringBuilder result = new StringBuilder();
        appendCanonical(result, value);
        return result.toString();
    }

    private static void appendCanonical(StringBuilder result, NBTBase value) {
        result.append(value.getId()).append(':');
        if (value instanceof NBTTagCompound compound) {
            List<String> keys = new ArrayList<>(compound.getKeySet());
            Collections.sort(keys);
            result.append('{');
            for (String key : keys) {
                result.append(key.length()).append(':').append(key).append('=');
                appendCanonical(result, compound.getTag(key));
                result.append(';');
            }
            result.append('}');
        } else if (value instanceof NBTTagList list) {
            result.append('[');
            for (NBTBase element : list) {
                appendCanonical(result, element);
                result.append(';');
            }
            result.append(']');
        } else {
            result.append(value);
        }
    }

    private static File child(File parent, String name) throws IOException {
        if (name == null || name.isEmpty() || name.contains("/") || name.contains("\\") ||
            name.equals(".") || name.equals("..")) {
            throw new IOException("Invalid QIO recipe catalog path component: " + name);
        }
        File canonicalParent = parent.getCanonicalFile();
        File child = new File(canonicalParent, name).getCanonicalFile();
        Path parentPath = canonicalParent.toPath();
        if (!child.toPath().startsWith(parentPath) || child.equals(canonicalParent)) {
            throw new IOException("QIO recipe catalog path escapes its directory: " + name);
        }
        return child;
    }

    private static void moveDirectory(File source, File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(source.toPath(), target.toPath());
        }
    }

    private static void retainGenerations(File root, UUID active, @Nullable UUID previous)
          throws IOException {
        File[] generations = root.listFiles(File::isDirectory);
        if (generations == null) return;
        for (File generation : generations) {
            UUID id;
            try {
                id = UUID.fromString(generation.getName());
            } catch (RuntimeException ignored) {
                continue;
            }
            if (!id.equals(active) && !id.equals(previous)) {
                deleteTree(root, generation);
            }
        }
    }

    private static Object directoryLock(Directories directories) throws IOException {
        String key = directories.root.getCanonicalPath();
        return DIRECTORY_LOCKS.computeIfAbsent(key, ignored -> new Object());
    }

    private static void deleteTree(File allowedRoot, File target) throws IOException {
        Path root = allowedRoot.getCanonicalFile().toPath();
        Path checked = target.getCanonicalFile().toPath();
        if (checked.equals(root) || !checked.startsWith(root)) {
            throw new IOException("Refusing to remove unsafe QIO recipe catalog path: " + target);
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(checked)) {
            try {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException error) {
                        throw new UncheckedIOException(error);
                    }
                });
            } catch (UncheckedIOException error) {
                throw error.getCause();
            }
        }
    }

    private static final class Directories {

        private final File root;
        private final File generations;
        private final File staging;
        private final File manifest;

        private Directories(File worldDirectory) throws IOException {
            if (worldDirectory == null) throw new IOException("World directory is absent");
            File world = worldDirectory.getCanonicalFile();
            root = new File(world, ROOT_PATH).getCanonicalFile();
            if (!root.toPath().startsWith(world.toPath())) {
                throw new IOException("QIO recipe catalog root escapes the world directory");
            }
            generations = child(root, "generations");
            staging = child(root, "staging");
            manifest = child(root, MANIFEST_FILE);
        }
    }

    static final class Candidate {

        final QIOWorkbenchRecipeCatalog.RecipeOutputIndex.CacheData cache;
        final UUID diskGeneration;
        final boolean manifestRepairRequired;

        private Candidate(QIOWorkbenchRecipeCatalog.RecipeOutputIndex.CacheData cache,
              UUID diskGeneration, boolean manifestRepairRequired) {
            this.cache = cache;
            this.diskGeneration = diskGeneration;
            this.manifestRepairRequired = manifestRepairRequired;
        }
    }

    static final class SaveToken {

        private boolean valid = true;

        synchronized void invalidate() {
            valid = false;
        }

        private synchronized boolean isValid() {
            return valid;
        }

        private synchronized boolean publish(IOAction action) throws IOException {
            if (!valid) return false;
            action.run();
            return true;
        }
    }

    @FunctionalInterface
    private interface IOAction {

        void run() throws IOException;
    }

    private static final class ManifestSource {

        private final Manifest manifest;
        private final boolean primary;

        private ManifestSource(Manifest manifest, boolean primary) {
            this.manifest = manifest;
            this.primary = primary;
        }
    }

    private static final class Manifest {

        private final UUID active;
        @Nullable private final UUID previous;
        private final String catalogGenerationId;

        private Manifest(UUID active, @Nullable UUID previous, String catalogGenerationId) {
            this.active = active;
            this.previous = previous;
            this.catalogGenerationId = catalogGenerationId;
        }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            data.setInteger("schemaVersion", SCHEMA_VERSION);
            data.setString("active", active.toString());
            if (previous != null) data.setString("previous", previous.toString());
            data.setString("catalogGenerationId", catalogGenerationId);
            data.setString("manifestHash", manifestHash(data));
            return data;
        }

        private static Manifest read(NBTTagCompound data) {
            if (data.getInteger("schemaVersion") != SCHEMA_VERSION ||
                !manifestHash(data).equals(data.getString("manifestHash"))) {
                throw new IllegalArgumentException("QIO recipe catalog manifest is invalid");
            }
            UUID active = UUID.fromString(data.getString("active"));
            UUID previous = data.hasKey("previous", 8) ?
                  UUID.fromString(data.getString("previous")) : null;
            String generationId = data.getString("catalogGenerationId");
            if (generationId.length() != 64) {
                throw new IllegalArgumentException("QIO recipe catalog generation ID is invalid");
            }
            return new Manifest(active, previous, generationId);
        }

        private static String manifestHash(NBTTagCompound data) {
            NBTTagCompound copy = data.copy();
            copy.removeTag("manifestHash");
            return QIOHashing.sha256(canonical(copy));
        }
    }

    private static final class ShardMetadata {

        private final String fileName;
        private final String category;
        private final int index;
        private final int recordCount;
        private final String hash;

        private ShardMetadata(String fileName, String category, int index, int recordCount,
              String hash) {
            this.fileName = fileName;
            this.category = category;
            this.index = index;
            this.recordCount = recordCount;
            this.hash = hash;
        }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            data.setString("file", fileName);
            data.setString("category", category);
            data.setInteger("index", index);
            data.setInteger("recordCount", recordCount);
            data.setString("hash", hash);
            return data;
        }

        private static ShardMetadata read(NBTTagCompound data) throws IOException {
            String fileName = data.getString("file");
            String category = data.getString("category");
            int index = data.getInteger("index");
            int count = data.getInteger("recordCount");
            String hash = data.getString("hash");
            if (fileName.isEmpty() || !fileName.endsWith(".dat") ||
                !fileName.startsWith(category + '-') || category.isEmpty() || index < 0 ||
                count < 0 || count > MAX_RECORDS_PER_SHARD || hash.length() != 64) {
                throw new IOException("Invalid QIO recipe catalog shard metadata");
            }
            return new ShardMetadata(fileName, category, index, count, hash);
        }
    }
}
