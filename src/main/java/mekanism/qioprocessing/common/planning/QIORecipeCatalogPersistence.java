package mekanism.qioprocessing.common.planning;

import mekanism.common.Mekanism;
import mekanism.qioprocessing.common.content.QIOProcessingStorageIO;
import mekanism.qioprocessing.common.util.QIOHashing;
import mekanism.qioprocessing.common.util.QIONbtUtils;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLongArray;
import net.minecraft.nbt.NBTTagString;

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

    /** Cache format v5: v4 data plus frozen fluid-container states for Forge variants. */
    static final int SCHEMA_VERSION = 5;
    private static final int SCAN_STATE_VERSION = 2;
    // Metadata is one unsharded file. This still permits hundreds of millions of normal-sized
    // records while keeping its shard table bounded well below the generic NBT list limit.
    private static final int MAX_SHARDS = 65_536;
    private static final int MAX_RECORDS_PER_SHARD = 4_096;
    private static final int MAX_ITEM_RECORDS = 5_000_000;
    private static final int MAX_VARIANT_RECORDS = 4_000_000;
    private static final int MAX_ORE_RECORDS = 1_000_000;
    private static final int MAX_INDEX_RECORDS = 4_000_000;
    // StellarCore's default large-NBT warning starts at 16 MiB and logs a stack for every
    // subsequent tracker update. Keep verified cache shards comfortably below that boundary.
    private static final long MAX_TRACKED_SHARD_BYTES = 8L * 1024 * 1024;
    private static final int MAX_RECOVERY_ARCHIVES = 8;
    private static final String ROOT_PATH = "mekanism/qio_processing/recipe_catalog";
    private static final String MANIFEST_FILE = "manifest.dat";
    private static final String METADATA_FILE = "metadata.dat";
    private static final String SCAN_STATE_FILE = "scan-state.dat";
    private static final Map<String, Object> DIRECTORY_LOCKS = new ConcurrentHashMap<>();

    private QIORecipeCatalogPersistence() {
    }

    @Nonnull
    static List<Candidate> loadCandidates(
          @Nonnull File worldDirectory) {
        return loadCandidates(worldDirectory, null);
    }

    @Nonnull
    static List<Candidate> loadCandidates(@Nonnull File worldDirectory,
          @Nullable String expectedEnvironmentSignature) {
        try {
            Directories directories = new Directories(worldDirectory);
            synchronized (directoryLock(directories)) {
                List<ManifestSource> manifests = new ArrayList<>(2);
                readManifest(directories.manifest, true, manifests);
                readManifest(QIOProcessingStorageIO.backupFile(directories.manifest), false,
                      manifests);
                Set<UUID> attempted = new LinkedHashSet<>();
                List<Candidate> result = new ArrayList<>(2);
                boolean[] environmentMismatch = new boolean[1];
                for (ManifestSource source : manifests) {
                    Manifest manifest = source.manifest;
                    if (manifest.active != null && attempted.add(manifest.active)) {
                        readCandidate(directories, manifest.active,
                              !source.primary, expectedEnvironmentSignature,
                              environmentMismatch, result);
                    }
                    if (manifest.previous != null && attempted.add(manifest.previous)) {
                        readCandidate(directories, manifest.previous, true,
                              expectedEnvironmentSignature, environmentMismatch, result);
                    }
                }
                if (result.isEmpty() && !environmentMismatch[0] &&
                    hasCacheState(directories)) {
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

    @Nullable
    static UUID save(@Nonnull File worldDirectory,
          @Nonnull QIOWorkbenchRecipeCatalog.RecipeOutputIndex.CacheData cache,
          @Nonnull String environmentSignature, @Nullable UUID retainedGeneration,
          @Nonnull SaveToken token) throws IOException {
        Directories directories = new Directories(worldDirectory);
        synchronized (directoryLock(directories)) {
            return saveLocked(directories, cache, environmentSignature, retainedGeneration,
                  token);
        }
    }

    @Nullable
    private static UUID saveLocked(Directories directories,
          QIOWorkbenchRecipeCatalog.RecipeOutputIndex.CacheData cache,
          String environmentSignature, @Nullable UUID retainedGeneration,
          SaveToken token) throws IOException {
        if (!isHash(environmentSignature) || !token.isValid()) return null;
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
            token.requireValid();
            List<ShardMetadata> shards = new ArrayList<>();
            List<NBTTagCompound> items = new ArrayList<>(
                  cache.forgeData.items.size() + cache.itemTypes.size());
            for (NBTTagCompound record : cache.forgeData.items) {
                token.requireValid();
                items.add(tagged("registry", record));
            }
            for (NBTTagCompound record : cache.itemTypes) {
                token.requireValid();
                items.add(tagged("catalog", record));
            }
            writeShards(staging, generation, "items", items, shards, token);
            writeShards(staging, generation, "candidates", cache.candidates, shards, token);
            writeShards(staging, generation, "variants", cache.forgeData.variants, shards,
                  token);
            writeShards(staging, generation, "ore_dictionary", cache.forgeData.ores, shards,
                  token);
            writeShards(staging, generation, "matchers", cache.matchers, shards, token);
            writeShards(staging, generation, "recipes", cache.recipes, shards, token);
            writeShards(staging, generation, "output_profiles", cache.outputProfiles, shards,
                  token);
            writeShards(staging, generation, "candidate_profiles", cache.candidateProfiles,
                  shards, token);
            writeShards(staging, generation, "reverse_indexes", cache.reverseIndexes, shards,
                  token);
            token.requireValid();
            NBTTagCompound metadata = new NBTTagCompound();
            metadata.setInteger("schemaVersion", SCHEMA_VERSION);
            metadata.setString("generationUUID", generation.toString());
            metadata.setString("catalogGenerationId", cache.generationId);
            metadata.setString("forgeSignature", cache.forgeData.structuralSignature);
            metadata.setString("environmentSignature", environmentSignature);
            metadata.setInteger("recipeCount", cache.recipeCount);
            NBTTagList shardList = new NBTTagList();
            for (ShardMetadata shard : shards) {
                token.requireValid();
                shardList.appendTag(shard.write());
            }
            metadata.setTag("shards", shardList);
            metadata.setString("metadataHash", metadataHash(metadata));
            token.requireValid();
            QIOProcessingStorageIO.replaceAtomicWithoutBackup(
                  child(staging, METADATA_FILE), metadata);
            token.requireValid();
            moveDirectory(staging, completed);

            UUID previous = generation.equals(retainedGeneration) ? null : retainedGeneration;
            Manifest next = new Manifest(generation,
                  previous, cache.generationId);
            if (!token.publish(() ->
                  QIOProcessingStorageIO.writeAtomic(directories.manifest, next.write()))) {
                return null;
            }
            published = true;
            retainGenerations(directories.generations, generation, next.previous);
            return generation;
        } catch (SaveCancelledException ignored) {
            return null;
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
          boolean manifestRepairRequired, @Nullable String expectedEnvironmentSignature,
          boolean[] environmentMismatch, List<Candidate> output) {
        try {
            LoadedGeneration loaded = readGeneration(
                  child(directories.generations, generation.toString()), generation,
                  expectedEnvironmentSignature);
            output.add(new Candidate(loaded.cache, loaded.environmentSignature, generation,
                  manifestRepairRequired));
        } catch (EnvironmentMismatchException mismatch) {
            environmentMismatch[0] = true;
            Mekanism.logger.info(
                  "Skipping QIO recipe catalog generation {} from a different Forge environment",
                  generation);
        } catch (IOException | RuntimeException error) {
            Mekanism.logger.warn("Ignoring invalid QIO recipe catalog generation {}",
                  generation, error);
        }
    }

    private static LoadedGeneration readGeneration(
          File directory, UUID expectedGeneration,
          @Nullable String expectedEnvironmentSignature) throws IOException {
        NBTTagCompound metadata = requireFile(child(directory, METADATA_FILE));
        if (metadata.getInteger("schemaVersion") != SCHEMA_VERSION ||
            !expectedGeneration.toString().equals(metadata.getString("generationUUID"))) {
            throw new IOException("QIO recipe catalog metadata is invalid");
        }
        NBTTagList storedShards = metadata.getTagList("shards", 10);
        if (storedShards.tagCount() > MAX_SHARDS) {
            throw new IOException("QIO recipe catalog contains too many shards");
        }
        if (!metadataHash(metadata).equals(metadata.getString("metadataHash"))) {
            throw new IOException("QIO recipe catalog metadata is invalid");
        }
        String environmentSignature = metadata.getString("environmentSignature");
        if (!isHash(environmentSignature)) {
            throw new IOException("QIO recipe catalog environment signature is invalid");
        }
        if (expectedEnvironmentSignature != null &&
            !expectedEnvironmentSignature.equals(environmentSignature)) {
            throw new EnvironmentMismatchException();
        }
        Map<String, List<NBTTagCompound>> categories = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        for (int index = 0; index < storedShards.tagCount(); index++) {
            ShardMetadata shard = ShardMetadata.read(storedShards.getCompoundTagAt(index));
            int categoryLimit = categoryRecordLimit(shard.category);
            if (!names.add(shard.fileName)) {
                throw new IOException("Duplicate QIO recipe catalog shard: " + shard.fileName);
            }
            NBTTagCompound data = requireFile(child(directory, shard.fileName));
            validateShard(data, expectedGeneration, shard);
            NBTTagList records = data.getTagList("records", 10);
            List<NBTTagCompound> category = categories.computeIfAbsent(shard.category,
                  ignored -> new ArrayList<>());
            if ((long) category.size() + records.tagCount() > categoryLimit) {
                throw new IOException("QIO recipe catalog category contains too many records: " +
                      shard.category);
            }
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
        return new LoadedGeneration(cache, environmentSignature);
    }

    /** Package-visible for focused validation tests; unknown categories are never accepted. */
    static int categoryRecordLimit(String category) throws IOException {
        switch (category) {
            case "items":
                return MAX_ITEM_RECORDS;
            case "variants":
                return MAX_VARIANT_RECORDS;
            case "ore_dictionary":
                return MAX_ORE_RECORDS;
            case "candidates":
            case "matchers":
            case "recipes":
            case "output_profiles":
            case "candidate_profiles":
            case "reverse_indexes":
                return MAX_INDEX_RECORDS;
            default:
                throw new IOException("Unknown QIO recipe catalog shard category: " + category);
        }
    }

    @Nonnull
    static ScanState readScanState(@Nonnull File worldDirectory) {
        try {
            Directories directories = new Directories(worldDirectory);
            synchronized (directoryLock(directories)) {
                NBTTagCompound data = QIOProcessingStorageIO.read(directories.scanState);
                return data == null ? ScanState.empty() : ScanState.read(data);
            }
        } catch (IOException | RuntimeException error) {
            Mekanism.logger.warn(
                  "Unable to read the QIO recipe catalog scan marker; rebuilding conservatively",
                  error);
            return ScanState.conservative();
        }
    }

    static boolean isRescanRequired(@Nonnull File worldDirectory) {
        return readScanState(worldDirectory).isRequired();
    }

    @Nonnull
    static ScanState markRescanRequired(@Nonnull File worldDirectory,
          @Nonnull String recipeId)
          throws IOException {
        Directories directories = new Directories(worldDirectory);
        synchronized (directoryLock(directories)) {
            ScanState current = readScanStateForWrite(directories);
            if (current.requestedEpoch == Long.MAX_VALUE) {
                throw new IOException("QIO recipe rescan epoch exhausted");
            }
            ScanState next = new ScanState(current.requestedEpoch + 1,
                  current.completedEpoch, limitedRecipeId(recipeId));
            writeScanState(directories, next);
            return next;
        }
    }

    /** Clears only requests covered by the catalog generation which was just published. */
    @Nonnull
    static ScanState clearRescanRequired(@Nonnull File worldDirectory, long coveredEpoch)
          throws IOException {
        if (coveredEpoch <= 0) {
            throw new IllegalArgumentException("Covered QIO recipe rescan epoch must be positive");
        }
        Directories directories = new Directories(worldDirectory);
        synchronized (directoryLock(directories)) {
            ScanState current = readScanStateForWrite(directories);
            long requested = Math.max(current.requestedEpoch, coveredEpoch);
            long completed = Math.max(current.completedEpoch,
                  Math.min(coveredEpoch, requested));
            ScanState next = new ScanState(requested, completed,
                  requested > completed ? current.recipeId : "");
            writeScanState(directories, next);
            return next;
        }
    }

    @Nonnull
    private static ScanState readScanStateForWrite(Directories directories) throws IOException {
        try {
            NBTTagCompound data = QIOProcessingStorageIO.read(directories.scanState);
            return data == null ? ScanState.empty() : ScanState.read(data);
        } catch (IOException | RuntimeException damaged) {
            Mekanism.logger.warn(
                  "Isolating invalid QIO recipe catalog scan marker {}; rebuilding conservatively",
                  directories.scanState, damaged);
            if (QIOProcessingStorageIO.quarantine(directories.scanState,
                  ".damaged") == null && directories.scanState.exists()) {
                throw new IOException("Unable to isolate the damaged QIO recipe scan marker",
                      damaged);
            }
            return ScanState.conservative();
        }
    }

    private static void writeScanState(Directories directories, ScanState state)
          throws IOException {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("scanStateVersion", SCAN_STATE_VERSION);
        data.setLong("requestedEpoch", state.requestedEpoch);
        data.setLong("completedEpoch", state.completedEpoch);
        data.setBoolean("rescanRequired", state.isRequired());
        if (state.isRequired() && !state.recipeId.isEmpty()) {
            data.setString("recipeId", state.recipeId);
        }
        data.setString("stateHash", stateHash(data));
        QIOProcessingStorageIO.writeAtomic(directories.scanState, data);
    }

    private static String limitedRecipeId(String recipeId) {
        String value = recipeId == null ? "" : recipeId;
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    private static String stateHash(NBTTagCompound state) {
        NBTTagCompound copy = state.copy();
        copy.removeTag("stateHash");
        return QIOHashing.sha256(canonical(copy));
    }

    private static boolean isHash(String value) {
        if (value == null || value.length() != 64) return false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!((character >= '0' && character <= '9') ||
                  (character >= 'a' && character <= 'f'))) return false;
        }
        return true;
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
          List<NBTTagCompound> records, List<ShardMetadata> metadata, SaveToken token)
          throws IOException {
        token.requireValid();
        List<List<NBTTagCompound>> chunks = chunks(records, token);
        int index = 0;
        for (List<NBTTagCompound> chunk : chunks) {
            token.requireValid();
            index = writeShard(directory, generation, category, chunk, index, metadata, token);
        }
    }

    private static int writeShard(File directory, UUID generation, String category,
          List<NBTTagCompound> records, int index, List<ShardMetadata> metadata,
          SaveToken token)
          throws IOException {
        token.requireValid();
        if (metadata.size() >= MAX_SHARDS) {
            throw new IOException("QIO recipe catalog contains too many shards");
        }
        NBTTagList values = new NBTTagList();
        for (NBTTagCompound record : records) {
            token.requireValid();
            values.appendTag(record.copy());
        }
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
        if (estimatedNbtReadBytes(data) > MAX_TRACKED_SHARD_BYTES) {
            return splitShard(directory, generation, category, records, index, metadata,
                  "tracked NBT size", token);
        }
        token.requireValid();
        File file = child(directory, fileName);
        try {
            QIOProcessingStorageIO.replaceAtomicWithoutBackup(file, data);
        } catch (IOException error) {
            if (!QIOProcessingStorageIO.isFileTooLarge(error) || records.size() <= 1) {
                throw error;
            }
            return splitShard(directory, generation, category, records, index, metadata,
                  "compressed or expanded file size", token);
        }
        token.requireValid();
        NBTTagCompound verified = requireFile(file);
        token.requireValid();
        ShardMetadata shard = new ShardMetadata(fileName, category, index,
              records.size(), hash);
        validateShard(verified, generation, shard);
        metadata.add(shard);
        return index + 1;
    }

    private static int splitShard(File directory, UUID generation, String category,
          List<NBTTagCompound> records, int index, List<ShardMetadata> metadata,
          String limit, SaveToken token) throws IOException {
        token.requireValid();
        if (records.size() <= 1) {
            throw new IOException("One QIO recipe catalog " + category +
                  " record exceeds the " + limit + " limit");
        }
        int middle = records.size() / 2;
        int next = writeShard(directory, generation, category,
              new ArrayList<>(records.subList(0, middle)), index, metadata, token);
        return writeShard(directory, generation, category,
              new ArrayList<>(records.subList(middle, records.size())), next, metadata, token);
    }

    private static List<List<NBTTagCompound>> chunks(List<NBTTagCompound> records,
          SaveToken token) throws IOException {
        if (records.isEmpty()) return Collections.emptyList();
        List<List<NBTTagCompound>> result = new ArrayList<>();
        List<NBTTagCompound> current = new ArrayList<>();
        long estimatedBytes = 0;
        for (NBTTagCompound record : records) {
            token.requireValid();
            long estimate = Math.max(128L, estimatedNbtReadBytes(record));
            if (!current.isEmpty() && (current.size() >= MAX_RECORDS_PER_SHARD ||
                saturatedAdd(estimatedBytes, estimate) > MAX_TRACKED_SHARD_BYTES)) {
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

    /** Mirrors Forge 1.12's NBTSizeTracker allocation accounting for one unnamed root tag. */
    static long estimatedNbtReadBytes(NBTBase value) {
        return saturatedAdd(7, estimatedPayloadBytes(value));
    }

    private static long estimatedPayloadBytes(NBTBase value) {
        if (value == null) return 0;
        switch (value.getId()) {
            case 0:
                return 8;
            case 1:
                return 9;
            case 2:
                return 10;
            case 3:
            case 5:
                return 12;
            case 4:
            case 6:
                return 16;
            case 7:
                return saturatedAdd(24,
                      ((NBTTagByteArray) value).getByteArray().length);
            case 8:
                return saturatedAdd(38,
                      modifiedUtfLength(((NBTTagString) value).getString()));
            case 9: {
                NBTTagList list = (NBTTagList) value;
                long bytes = saturatedAdd(37, saturatedMultiply(4, list.tagCount()));
                for (NBTBase child : list) {
                    bytes = saturatedAdd(bytes, estimatedPayloadBytes(child));
                }
                return bytes;
            }
            case 10: {
                NBTTagCompound compound = (NBTTagCompound) value;
                long bytes = 49;
                for (String key : compound.getKeySet()) {
                    bytes = saturatedAdd(bytes, saturatedAdd(33,
                          saturatedMultiply(2, key.length())));
                    bytes = saturatedAdd(bytes,
                          estimatedPayloadBytes(compound.getTag(key)));
                }
                return bytes;
            }
            case 11:
                return saturatedAdd(24, saturatedMultiply(4,
                      ((NBTTagIntArray) value).getIntArray().length));
            case 12:
                return saturatedAdd(24, saturatedMultiply(8,
                      QIONbtUtils.longArrayLength((NBTTagLongArray) value)));
            default:
                return Long.MAX_VALUE;
        }
    }

    private static long modifiedUtfLength(String value) {
        long bytes = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            bytes = saturatedAdd(bytes, character == 0 ? 2 :
                  character <= 0x7F ? 1 : character <= 0x7FF ? 2 : 3);
        }
        return bytes;
    }

    private static long saturatedMultiply(long left, long right) {
        if (left <= 0 || right <= 0) return 0;
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static long saturatedAdd(long left, long right) {
        if (left < 0 || right < 0 || left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
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

    static final class ScanState {

        final long requestedEpoch;
        final long completedEpoch;
        final String recipeId;

        private ScanState(long requestedEpoch, long completedEpoch, String recipeId) {
            if (requestedEpoch < 0 || completedEpoch < 0 ||
                completedEpoch > requestedEpoch) {
                throw new IllegalArgumentException("Invalid QIO recipe rescan epochs");
            }
            this.requestedEpoch = requestedEpoch;
            this.completedEpoch = completedEpoch;
            this.recipeId = limitedRecipeId(recipeId);
        }

        boolean isRequired() {
            return requestedEpoch > completedEpoch;
        }

        @Nonnull
        static ScanState empty() {
            return new ScanState(0, 0, "");
        }

        @Nonnull
        static ScanState conservative() {
            return new ScanState(1, 0, "damaged-marker");
        }

        @Nonnull
        private static ScanState read(NBTTagCompound data) {
            if (data.getInteger("scanStateVersion") != SCAN_STATE_VERSION ||
                !data.hasKey("requestedEpoch", 4) ||
                !data.hasKey("completedEpoch", 4) ||
                !data.hasKey("rescanRequired", 1) ||
                !stateHash(data).equals(data.getString("stateHash"))) {
                throw new IllegalArgumentException(
                      "QIO recipe catalog scan marker is invalid");
            }
            long requested = data.getLong("requestedEpoch");
            long completed = data.getLong("completedEpoch");
            String recipeId = data.hasKey("recipeId", 8) ?
                  data.getString("recipeId") : "";
            ScanState state = new ScanState(requested, completed, recipeId);
            if (data.getBoolean("rescanRequired") != state.isRequired() ||
                recipeId.length() > 512) {
                throw new IllegalArgumentException(
                      "QIO recipe catalog scan marker state is inconsistent");
            }
            return state;
        }
    }

    private static final class Directories {

        private final File root;
        private final File generations;
        private final File staging;
        private final File manifest;
        private final File scanState;

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
            scanState = child(root, SCAN_STATE_FILE);
        }
    }

    private static final class LoadedGeneration {

        private final QIOWorkbenchRecipeCatalog.RecipeOutputIndex.CacheData cache;
        private final String environmentSignature;

        private LoadedGeneration(
              QIOWorkbenchRecipeCatalog.RecipeOutputIndex.CacheData cache,
              String environmentSignature) {
            this.cache = cache;
            this.environmentSignature = environmentSignature;
        }
    }

    private static final class EnvironmentMismatchException extends IOException {
    }

    static final class Candidate {

        final QIOWorkbenchRecipeCatalog.RecipeOutputIndex.CacheData cache;
        final String environmentSignature;
        final UUID diskGeneration;
        final boolean manifestRepairRequired;

        private Candidate(QIOWorkbenchRecipeCatalog.RecipeOutputIndex.CacheData cache,
              String environmentSignature, UUID diskGeneration,
              boolean manifestRepairRequired) {
            this.cache = cache;
            this.environmentSignature = environmentSignature;
            this.diskGeneration = diskGeneration;
            this.manifestRepairRequired = manifestRepairRequired;
        }
    }

    static final class SaveToken {

        private volatile boolean valid = true;

        synchronized void invalidate() {
            valid = false;
        }

        private boolean isValid() {
            return valid;
        }

        private void requireValid() throws SaveCancelledException {
            if (!valid) throw new SaveCancelledException();
        }

        private synchronized boolean publish(IOAction action) throws IOException {
            if (!valid) return false;
            action.run();
            return true;
        }
    }

    private static final class SaveCancelledException extends IOException {

        private SaveCancelledException() {
            super("QIO recipe catalog save was cancelled");
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
