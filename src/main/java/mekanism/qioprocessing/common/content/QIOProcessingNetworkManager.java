package mekanism.qioprocessing.common.content;

import mekanism.common.Mekanism;
import mekanism.qioprocessing.common.content.material.QIOMaterialClaimCoordinator;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** World owner of independently persisted, frequency-level QIO Processing networks. */
public final class QIOProcessingNetworkManager {

    public static final QIOProcessingNetworkManager INSTANCE = new QIOProcessingNetworkManager();
    public static final int WORLD_INDEX_SCHEMA_VERSION = 2;
    private static final int STORAGE_MAINTENANCE_INTERVAL = 100;
    private static final int MAX_WORLD_INDEX_ENTRIES = 1_000_000;

    private final Map<UUID, QIOProcessingNetworkData> networks = new LinkedHashMap<>();
    private final Set<UUID> dirtyNetworks = new LinkedHashSet<>();
    private final Set<UUID> damagedNetworks = new HashSet<>();
    private final Set<UUID> futureNetworks = new HashSet<>();
    private final Set<UUID> reportedIsolatedNetworks = new HashSet<>();
    private File requestedWorldDirectory;
    private File worldDirectory;
    private File rootDirectory;
    private File networkDirectory;
    private File indexFile;
    private boolean loaded;
    private boolean readOnlyFutureIndex;
    private boolean indexDirty;

    private QIOProcessingNetworkManager() {
    }

    public synchronized void createOrLoad(@Nullable World world) {
        if (world != null && !world.isRemote && world.provider.getDimension() == 0) {
            createOrLoad(world.getSaveHandler().getWorldDirectory());
        }
    }

    public synchronized void createOrLoad(@Nullable File worldDirectory) {
        if (worldDirectory == null) {
            return;
        }
        File requested = worldDirectory.getAbsoluteFile();
        if (loaded && requested.equals(requestedWorldDirectory)) {
            return;
        }
        try {
            File canonical = worldDirectory.getCanonicalFile();
            if (loaded && canonical.equals(this.worldDirectory)) {
                requestedWorldDirectory = requested;
                return;
            }
            clearRuntimeState();
            requestedWorldDirectory = requested;
            this.worldDirectory = canonical;
            rootDirectory = new File(canonical, "mekanism/qio_processing");
            networkDirectory = new File(rootDirectory, "networks");
            indexFile = new File(rootDirectory, "index.dat");
            QIOProcessingFileIO.ensureDirectory(networkDirectory);
            if (!loadIndex()) {
                loaded = true;
                return;
            }
            scanIsolatedFiles();
            restoreMissingNetworkBackups();
            loadNetworkFiles();
            reportUnreportedIsolatedNetworks();
            loaded = true;
            indexDirty = true;
        } catch (IOException e) {
            Mekanism.logger.error("Unable to initialize QIO Processing network storage", e);
            clearRuntimeState();
        }
    }

    public synchronized boolean isLoaded() {
        return loaded;
    }

    public synchronized boolean isReadOnlyFutureIndex() {
        return readOnlyFutureIndex;
    }

    @Nullable
    public synchronized QIOProcessingNetworkData get(@Nullable UUID frequencyUUID) {
        return frequencyUUID == null ? null : networks.get(frequencyUUID);
    }

    /** Resolves one unique workbench configuration without exposing frequency file paths. */
    @Nullable
    public synchronized QIOProcessingNetworkData getByWorkbenchConfigUUID(
          @Nullable UUID configUUID) {
        if (configUUID == null) {
            return null;
        }
        QIOProcessingNetworkData match = null;
        for (QIOProcessingNetworkData network : networks.values()) {
            if (!configUUID.equals(network.getWorkbenchConfiguration().getConfigUUID())) {
                continue;
            }
            if (match != null) {
                throw new IllegalStateException(
                      "Duplicate QIO workbench configuration UUID " + configUUID);
            }
            match = network;
        }
        return match;
    }

    @Nonnull
    public synchronized Collection<QIOProcessingNetworkData> getNetworks() {
        return Collections.unmodifiableList(new ArrayList<>(networks.values()));
    }

    @Nonnull
    public synchronized Set<UUID> getDamagedNetworks() {
        return Collections.unmodifiableSet(new HashSet<>(damagedNetworks));
    }

    @Nonnull
    public synchronized Set<UUID> getFutureNetworks() {
        return Collections.unmodifiableSet(new HashSet<>(futureNetworks));
    }

    @Nullable
    public synchronized IsolationStatus getIsolationStatus(@Nullable UUID frequencyUUID) {
        if (frequencyUUID == null) {
            return null;
        }
        if (futureNetworks.contains(frequencyUUID)) {
            return IsolationStatus.FUTURE;
        }
        return damagedNetworks.contains(frequencyUUID) ? IsolationStatus.DAMAGED : null;
    }

    @Nonnull
    public synchronized QIOProcessingNetworkData getOrCreate(@Nonnull UUID frequencyUUID,
          @Nonnull QIOFrequencyIdentitySnapshot identity) {
        requireWritable();
        Objects.requireNonNull(frequencyUUID, "frequencyUUID");
        Objects.requireNonNull(identity, "identity");
        IsolationStatus isolationStatus = getIsolationStatus(frequencyUUID);
        if (isolationStatus == IsolationStatus.DAMAGED) {
            return resetDamagedNetwork(frequencyUUID, identity);
        } else if (isolationStatus != null) {
            throw new IsolatedNetworkException(frequencyUUID, isolationStatus);
        }
        QIOProcessingNetworkData network = networks.get(frequencyUUID);
        if (network == null) {
            network = new QIOProcessingNetworkData(frequencyUUID, identity);
            networks.put(frequencyUUID, network);
            bind(network);
            markDirty(frequencyUUID);
            indexDirty = true;
        } else {
            network.updateFrequencyIdentity(identity);
            if (network.getLifecycle() == QIOProcessingNetworkLifecycle.ORPHANED_FREQUENCY &&
                  network.getFrequencyDeletionBlockers().isEmpty()) {
                // The core frequency removal and this module's tombstone are separate files. If
                // the core file rolled back, the exact same UUID proves this idle network is live.
                network.setLifecycle(QIOProcessingNetworkLifecycle.ACTIVE);
            }
        }
        return network;
    }

    @Nonnull
    private QIOProcessingNetworkData resetDamagedNetwork(UUID frequencyUUID,
          QIOFrequencyIdentitySnapshot identity) {
        File active = QIOProcessingFileIO.dataFile(networkDirectory, frequencyUUID);
        File backup = QIOProcessingFileIO.backupFile(active);
        File activeArchive = requireDamagedArchive(active, frequencyUUID);
        File backupArchive = requireDamagedArchive(backup, frequencyUUID);
        QIOProcessingNetworkData replacement = new QIOProcessingNetworkData(frequencyUUID,
              identity);
        try {
            QIOProcessingFileIO.replaceAtomicWithoutBackup(active, replacement.write());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to rebuild damaged QIO Processing network " +
                  frequencyUUID, e);
        }
        networks.put(frequencyUUID, replacement);
        bind(replacement);
        damagedNetworks.remove(frequencyUUID);
        reportedIsolatedNetworks.remove(frequencyUUID);
        markDirty(frequencyUUID);
        indexDirty = true;
        try {
            writeIndex();
        } catch (IOException e) {
            indexDirty = true;
            Mekanism.logger.warn("Rebuilt damaged QIO Processing network {} but could not " +
                  "immediately refresh the world index; the next maintenance flush will retry",
                  frequencyUUID, e);
        }
        Mekanism.logger.warn("Reset damaged QIO Processing data for frequency {} ('{}') to an " +
                    "empty current-format network after an authorized frequency access. Active " +
                    "archive: {}; backup archive: {}. Saved jobs, buffers, material claims, " +
                    "transfers, passive operations, recipe/workbench/maintenance configuration, " +
                    "provider catalog, and central device records were discarded. Core QIO " +
                    "frequency, inventory, security settings, and machine-local data were not " +
                    "modified; loaded devices may republish their current records.",
              frequencyUUID, identity.getName(), archivePath(activeArchive),
              archivePath(backupArchive));
        return replacement;
    }

    @Nullable
    private static File requireDamagedArchive(File source, UUID frequencyUUID) {
        if (!source.isFile()) {
            return null;
        }
        File archive = QIOProcessingFileIO.quarantine(source, ".damaged");
        if (archive == null) {
            throw new IllegalStateException("Unable to archive damaged QIO Processing data for " +
                  frequencyUUID + ": " + source);
        }
        return archive;
    }

    public synchronized void frequencyDeleted(@Nonnull UUID frequencyUUID) {
        if (!loaded || readOnlyFutureIndex) {
            return;
        }
        QIOProcessingNetworkData network = networks.get(Objects.requireNonNull(frequencyUUID,
              "frequencyUUID"));
        if (network == null) {
            return;
        }
        network.setLifecycle(QIOProcessingNetworkLifecycle.ORPHANED_FREQUENCY);
        try {
            flushNetwork(frequencyUUID);
        } catch (IOException | RuntimeException e) {
            Mekanism.logger.error("Unable to persist deleted QIO frequency tombstone {}",
                  frequencyUUID, e);
        }
    }

    @Nonnull
    public QIOMaterialClaimCoordinator.PersistenceBarrier persistenceBarrier() {
        return network -> checkpointNetwork(network.getFrequencyUUID());
    }

    /**
     * Persists the current generation without rotating the backup file.
     *
     * <p>Execution phase transitions use this path because they already retain the network as
     * dirty and the regular maintenance flush will create the verified backup generation. Keeping
     * backup rotation out of every transfer phase avoids copying and re-reading the same (often
     * large) network file several times in one server tick.</p>
     */
    public synchronized void checkpointNetwork(@Nonnull UUID frequencyUUID) throws IOException {
        requireWritable();
        QIOProcessingNetworkData network = networks.get(Objects.requireNonNull(frequencyUUID,
              "frequencyUUID"));
        if (network == null) {
            throw new IllegalArgumentException("Unknown QIO Processing network " + frequencyUUID);
        }
        QIOProcessingFileIO.replaceAtomicWithoutBackup(
              QIOProcessingFileIO.dataFile(networkDirectory, frequencyUUID), network.write());
        // A checkpoint is a durable current generation, not a backup checkpoint. Retain the
        // dirty marker so the next maintenance flush still rotates a recoverable backup.
        dirtyNetworks.add(frequencyUUID);
    }

    public synchronized void flushNetwork(@Nonnull UUID frequencyUUID) throws IOException {
        requireWritable();
        QIOProcessingNetworkData network = networks.get(Objects.requireNonNull(frequencyUUID,
              "frequencyUUID"));
        if (network == null) {
            throw new IllegalArgumentException("Unknown QIO Processing network " + frequencyUUID);
        }
        QIOProcessingFileIO.writeAtomic(QIOProcessingFileIO.dataFile(networkDirectory, frequencyUUID),
              network.write());
        dirtyNetworks.remove(frequencyUUID);
        if (indexDirty) {
            writeIndex();
        }
    }

    public void flush() {
        Map<UUID, QIOProcessingNetworkData> pending;
        synchronized (this) {
            if (!loaded || readOnlyFutureIndex) {
                return;
            }
            pending = new LinkedHashMap<>();
            for (UUID uuid : dirtyNetworks) {
                QIOProcessingNetworkData network = networks.get(uuid);
                if (network != null && !damagedNetworks.contains(uuid) && !futureNetworks.contains(uuid)) {
                    pending.put(uuid, network);
                }
            }
        }
        Set<UUID> saved = new HashSet<>();
        for (Map.Entry<UUID, QIOProcessingNetworkData> entry : pending.entrySet()) {
            try {
                QIOProcessingFileIO.writeAtomic(QIOProcessingFileIO.dataFile(networkDirectory,
                      entry.getKey()), entry.getValue().write());
                saved.add(entry.getKey());
            } catch (IOException | RuntimeException e) {
                Mekanism.logger.error("Unable to save QIO Processing network {}", entry.getKey(), e);
            }
        }
        synchronized (this) {
            dirtyNetworks.removeAll(saved);
            if (indexDirty && dirtyNetworks.isEmpty()) {
                try {
                    writeIndex();
                } catch (IOException e) {
                    Mekanism.logger.error("Unable to save QIO Processing world index", e);
                }
            }
        }
    }

    public void tick(@Nullable World world) {
        if (world != null && !world.isRemote && world.provider.getDimension() == 0) {
            if (world.getTotalWorldTime() % STORAGE_MAINTENANCE_INTERVAL == 0) {
                if (!isLoaded()) {
                    createOrLoad(world);
                }
                flush();
            }
        }
    }

    public void shutdown() {
        flush();
        synchronized (this) {
            clearRuntimeState();
        }
    }

    public synchronized void resetForTests() {
        clearRuntimeState();
    }

    private boolean loadIndex() throws IOException {
        if (!indexFile.isFile()) {
            restoreCurrentIndexBackup();
            return !readOnlyFutureIndex;
        }
        NBTTagCompound data;
        try {
            data = QIOProcessingFileIO.read(indexFile);
        } catch (Exception e) {
            Mekanism.logger.error("QIO Processing world index is damaged; rebuilding its directory view", e);
            QIOProcessingFileIO.quarantine(indexFile, ".damaged");
            restoreCurrentIndexBackup();
            return !readOnlyFutureIndex;
        }
        if (data == null) {
            return true;
        }
        int schema = data.getInteger("worldIndexSchemaVersion");
        if (schema > WORLD_INDEX_SCHEMA_VERSION) {
            readOnlyFutureIndex = true;
            Mekanism.logger.error("QIO Processing world index schema {} is newer than supported {}; " +
                  "network storage will remain read-only", schema, WORLD_INDEX_SCHEMA_VERSION);
            return false;
        }
        if (schema != WORLD_INDEX_SCHEMA_VERSION) {
            Mekanism.logger.error("Unsupported QIO Processing world index schema {}; rebuilding index", schema);
            QIOProcessingFileIO.quarantine(indexFile, ".damaged");
            restoreCurrentIndexBackup();
        } else if (!validCurrentIndex(data)) {
            Mekanism.logger.error("QIO Processing world index is missing or contains invalid " +
                  "current-schema state; rebuilding index");
            QIOProcessingFileIO.quarantine(indexFile, ".damaged");
            restoreCurrentIndexBackup();
        }
        return !readOnlyFutureIndex;
    }

    private boolean restoreCurrentIndexBackup() {
        File backup = QIOProcessingFileIO.backupFile(indexFile);
        if (!backup.isFile()) {
            return false;
        }
        try {
            NBTTagCompound data = QIOProcessingFileIO.read(backup);
            if (data == null) {
                return false;
            }
            int schema = data.getInteger("worldIndexSchemaVersion");
            if (schema > WORLD_INDEX_SCHEMA_VERSION) {
                readOnlyFutureIndex = true;
                Mekanism.logger.error("QIO Processing world-index backup schema {} is newer " +
                      "than supported {}; network storage will remain read-only", schema,
                      WORLD_INDEX_SCHEMA_VERSION);
                return false;
            }
            if (schema != WORLD_INDEX_SCHEMA_VERSION || !validCurrentIndex(data)) return false;
            QIOProcessingFileIO.writeAtomic(indexFile, data);
            Mekanism.logger.warn("Restored QIO Processing world index from current-schema backup");
            return true;
        } catch (IOException | RuntimeException e) {
            Mekanism.logger.error("Unable to restore QIO Processing world index backup", e);
            return false;
        }
    }

    private static boolean validCurrentIndex(NBTTagCompound data) {
        if (!data.hasKey("networks", NBT.TAG_LIST)) return false;
        NBTTagList entries = data.getTagList("networks", NBT.TAG_COMPOUND);
        if (entries.tagCount() > MAX_WORLD_INDEX_ENTRIES) return false;
        Set<UUID> seen = new HashSet<>();
        for (int index = 0; index < entries.tagCount(); index++) {
            NBTTagCompound entry = entries.getCompoundTagAt(index);
            if (!entry.hasKey("frequencyUUID", NBT.TAG_STRING) ||
                !entry.hasKey("status", NBT.TAG_STRING)) return false;
            try {
                UUID uuid = UUID.fromString(entry.getString("frequencyUUID"));
                String status = entry.getString("status");
                if (!uuid.toString().equals(entry.getString("frequencyUUID")) ||
                    !("ACTIVE".equals(status) || "DAMAGED".equals(status) ||
                      "FUTURE".equals(status)) || !seen.add(uuid)) return false;
            } catch (RuntimeException e) {
                return false;
            }
        }
        return true;
    }

    private void scanIsolatedFiles() {
        File[] files = networkDirectory.listFiles(File::isFile);
        if (files == null) {
            return;
        }
        for (File file : files) {
            UUID uuid = QIOProcessingFileIO.parseIsolatedDataFileUUID(file);
            if (uuid != null) {
                if (file.getName().contains(".dat.future")) {
                    futureNetworks.add(uuid);
                    damagedNetworks.remove(uuid);
                } else if (file.getName().contains(".dat.damaged") &&
                      !futureNetworks.contains(uuid)) {
                    if (QIOProcessingFileIO.dataFile(networkDirectory, uuid).isFile()) {
                        damagedNetworks.remove(uuid);
                    } else {
                        damagedNetworks.add(uuid);
                    }
                }
            }
        }
    }

    private void restoreMissingNetworkBackups() {
        for (File backup : QIOProcessingFileIO.listBackupFiles(networkDirectory)) {
            UUID uuid = QIOProcessingFileIO.parseBackupFileUUID(backup);
            if (uuid == null || futureNetworks.contains(uuid) ||
                QIOProcessingFileIO.dataFile(networkDirectory, uuid).isFile() ||
                networks.containsKey(uuid)) {
                continue;
            }
            QIOProcessingNetworkData restored = restoreNetworkBackup(uuid, true);
            if (restored != null) {
                registerLoadedNetwork(uuid, restored);
            }
        }
    }

    private void loadNetworkFiles() {
        for (File file : QIOProcessingFileIO.listDataFiles(networkDirectory)) {
            UUID uuid = QIOProcessingFileIO.parseDataFileUUID(file);
            if (uuid == null) {
                Mekanism.logger.warn("Ignoring QIO Processing file with an invalid UUID name: {}",
                      file.getName());
                continue;
            }
            if (damagedNetworks.contains(uuid) || futureNetworks.contains(uuid)) {
                reportIsolation(uuid, getIsolationStatus(uuid), null);
                continue;
            }
            if (networks.containsKey(uuid)) {
                continue;
            }
            try {
                NBTTagCompound data = QIOProcessingFileIO.read(file);
                if (data == null) {
                    throw new IOException("File is empty");
                }
                int schema = readNetworkSchema(data);
                if (schema > QIOProcessingNetworkData.SCHEMA_VERSION) {
                    QIOProcessingFileIO.quarantine(file, ".future");
                    markIsolated(uuid, IsolationStatus.FUTURE, null);
                    continue;
                }
                QIOProcessingNetworkData network;
                if (isLegacyNetworkSchema(schema)) {
                    network = resetLegacyNetwork(uuid, file, data, schema);
                } else {
                    network = QIOProcessingNetworkData.read(data, uuid);
                }
                registerLoadedNetwork(uuid, network);
            } catch (Exception e) {
                QIOProcessingFileIO.quarantine(file, ".damaged");
                QIOProcessingNetworkData restored = restoreNetworkBackup(uuid, false);
                if (restored != null) {
                    registerLoadedNetwork(uuid, restored);
                } else if (!futureNetworks.contains(uuid)) {
                    markIsolated(uuid, IsolationStatus.DAMAGED, e);
                }
            }
        }
    }

    @Nullable
    private QIOProcessingNetworkData restoreNetworkBackup(UUID uuid,
          boolean resetLegacyData) {
        File active = QIOProcessingFileIO.dataFile(networkDirectory, uuid);
        File backup = QIOProcessingFileIO.backupFile(active);
        if (!backup.isFile()) {
            return null;
        }
        try {
            NBTTagCompound data = QIOProcessingFileIO.read(backup);
            if (data == null) {
                throw new IOException("Backup is empty");
            }
            int schema = readNetworkSchema(data);
            if (schema > QIOProcessingNetworkData.SCHEMA_VERSION) {
                markIsolated(uuid, IsolationStatus.FUTURE, null);
                return null;
            }
            if (isLegacyNetworkSchema(schema)) {
                return resetLegacyData ? resetLegacyNetwork(uuid, backup, data, schema) : null;
            }
            if (schema != QIOProcessingNetworkData.SCHEMA_VERSION) {
                throw new QIOProcessingDataException(
                      "Unsupported QIO Processing backup schema " + schema);
            }
            QIOProcessingNetworkData restored = QIOProcessingNetworkData.read(data, uuid);
            QIOProcessingFileIO.replaceAtomicWithoutBackup(active, data);
            Mekanism.logger.warn("Restored QIO Processing network {} from current-schema backup",
                  uuid);
            return restored;
        } catch (IOException | QIOProcessingDataException | RuntimeException e) {
            if (resetLegacyData) {
                markIsolated(uuid, IsolationStatus.DAMAGED, e);
            }
            return null;
        }
    }

    @Nonnull
    private QIOProcessingNetworkData resetLegacyNetwork(UUID uuid, File source,
          NBTTagCompound data, int schema) throws IOException, QIOProcessingDataException {
        QIOFrequencyIdentitySnapshot identity = readLegacyIdentity(data, uuid);
        File active = QIOProcessingFileIO.dataFile(networkDirectory, uuid);
        File backup = QIOProcessingFileIO.backupFile(active);
        File activeArchive = null;
        File backupArchive = null;
        if (source.equals(active)) {
            activeArchive = requireLegacyArchive(active);
        } else if (source.equals(backup)) {
            backupArchive = requireLegacyArchive(backup);
        } else {
            throw new IOException("Unexpected QIO Processing legacy source " + source);
        }
        if (backup.isFile() && backupArchive == null) {
            backupArchive = requireLegacyArchive(backup);
        }

        QIOProcessingNetworkData replacement = new QIOProcessingNetworkData(uuid, identity);
        NBTTagCompound replacementData = replacement.write();
        QIOProcessingFileIO.replaceAtomicWithoutBackup(active, replacementData);
        if (backup.isFile()) {
            try {
                QIOProcessingFileIO.replaceAtomicWithoutBackup(backup, replacementData);
            } catch (IOException e) {
                dirtyNetworks.add(uuid);
                Mekanism.logger.warn("Rebuilt QIO Processing network {} but could not refresh " +
                      "its current-schema backup; the next dirty flush will retry", uuid, e);
            }
        }
        Mekanism.logger.warn("Reset obsolete QIO Processing data for frequency {} ('{}') from " +
                    "development schema {} to {}. Active archive: {}; backup archive: {}. " +
                    "Saved jobs, buffers, material claims, transfers, passive operations, " +
                    "recipe/workbench/maintenance configuration, provider catalog, and central " +
                    "device records were discarded. The core QIO frequency, inventory, security " +
                    "settings, and machine bindings were not modified; loaded devices may " +
                    "republish their current records.",
              uuid, identity.getName(), schema, QIOProcessingNetworkData.SCHEMA_VERSION,
              archivePath(activeArchive), archivePath(backupArchive));
        return replacement;
    }

    @Nonnull
    private static QIOFrequencyIdentitySnapshot readLegacyIdentity(NBTTagCompound data,
          UUID expectedUUID) throws QIOProcessingDataException {
        if (!data.hasKey("frequencyUUID", NBT.TAG_STRING) ||
            !data.hasKey("lastKnownFrequencyIdentity", NBT.TAG_COMPOUND)) {
            throw new QIOProcessingDataException(
                  "Legacy QIO Processing data is missing its frequency identity");
        }
        UUID storedUUID = QIOProcessingNbt.readUUID(data, "frequencyUUID");
        if (!expectedUUID.equals(storedUUID)) {
            throw new QIOProcessingDataException(
                  "Legacy QIO Processing file name and frequency UUID disagree");
        }
        NBTTagCompound identity = data.getCompoundTag("lastKnownFrequencyIdentity");
        if (!identity.hasKey("name", NBT.TAG_STRING) ||
            !identity.hasKey("securityMode", NBT.TAG_STRING) ||
            identity.hasKey("ownerUUID") && !identity.hasKey("ownerUUID", NBT.TAG_STRING)) {
            throw new QIOProcessingDataException(
                  "Legacy QIO Processing frequency identity has invalid field types");
        }
        return QIOFrequencyIdentitySnapshot.read(identity);
    }

    private static int readNetworkSchema(NBTTagCompound data)
          throws QIOProcessingDataException {
        if (!data.hasKey("networkDataSchemaVersion", NBT.TAG_INT)) {
            throw new QIOProcessingDataException(
                  "QIO Processing network schema is missing or has the wrong type");
        }
        return data.getInteger("networkDataSchemaVersion");
    }

    private static boolean isLegacyNetworkSchema(int schema) {
        return schema > 0 && schema < QIOProcessingNetworkData.SCHEMA_VERSION;
    }

    @Nonnull
    private static File requireLegacyArchive(File source) throws IOException {
        File archive = QIOProcessingFileIO.archiveCopy(source, ".legacy");
        if (archive == null) {
            throw new IOException("Unable to archive obsolete QIO Processing data " + source);
        }
        return archive;
    }

    private static String archivePath(@Nullable File archive) {
        return archive == null ? "none" : archive.getAbsolutePath();
    }

    private void registerLoadedNetwork(UUID uuid, QIOProcessingNetworkData network) {
        networks.put(uuid, network);
        bind(network);
        damagedNetworks.remove(uuid);
        futureNetworks.remove(uuid);
        reportedIsolatedNetworks.remove(uuid);
        if (network.wasRepairedOnLoad()) {
            dirtyNetworks.add(uuid);
        }
    }

    private void markIsolated(UUID uuid, IsolationStatus status,
          @Nullable Throwable cause) {
        if (status == IsolationStatus.FUTURE) {
            futureNetworks.add(uuid);
            damagedNetworks.remove(uuid);
        } else if (!futureNetworks.contains(uuid)) {
            damagedNetworks.add(uuid);
        }
        reportIsolation(uuid, getIsolationStatus(uuid), cause);
    }

    private void reportUnreportedIsolatedNetworks() {
        Set<UUID> isolated = new HashSet<>(damagedNetworks);
        isolated.addAll(futureNetworks);
        isolated.stream().map(UUID::toString).sorted().map(UUID::fromString).forEach(uuid ->
              reportIsolation(uuid, getIsolationStatus(uuid), null));
    }

    private void reportIsolation(UUID uuid, @Nullable IsolationStatus status,
          @Nullable Throwable cause) {
        if (status == null || !reportedIsolatedNetworks.add(uuid)) {
            return;
        }
        String message;
        if (status == IsolationStatus.FUTURE) {
            message = "QIO Processing network {} contains newer-schema module data and remains " +
                  "isolated so it cannot be overwritten or downgraded. Saved central device " +
                  "records are unavailable. Core QIO frequency, inventory, security data, and " +
                  "machine-local data were not modified.";
        } else {
            message = "QIO Processing network {} contains damaged current-format module data and " +
                  "remains isolated. It was not automatically erased because it may contain " +
                  "jobs, buffers, material claims, or in-flight transfers. Saved central device " +
                  "records are unavailable. Core QIO frequency, inventory, security data, and " +
                  "machine-local data were not modified.";
        }
        if (cause == null) {
            Mekanism.logger.error(message, uuid);
        } else {
            Mekanism.logger.error(message, uuid, cause);
        }
    }

    private void bind(QIOProcessingNetworkData network) {
        UUID uuid = network.getFrequencyUUID();
        network.bindDirtyListener(() -> markDirty(uuid));
    }

    private synchronized void markDirty(UUID frequencyUUID) {
        if (loaded && !readOnlyFutureIndex && networks.containsKey(frequencyUUID)) {
            dirtyNetworks.add(frequencyUUID);
            indexDirty = true;
        }
    }

    private void writeIndex() throws IOException {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("worldIndexSchemaVersion", WORLD_INDEX_SCHEMA_VERSION);
        NBTTagList entries = new NBTTagList();
        Set<UUID> all = new HashSet<>(networks.keySet());
        all.addAll(damagedNetworks);
        all.addAll(futureNetworks);
        all.stream().map(UUID::toString).sorted().forEach(raw -> {
            UUID uuid = UUID.fromString(raw);
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("frequencyUUID", raw);
            entry.setString("status", futureNetworks.contains(uuid) ? "FUTURE" :
                  damagedNetworks.contains(uuid) ? "DAMAGED" : "ACTIVE");
            entries.appendTag(entry);
        });
        data.setTag("networks", entries);
        QIOProcessingFileIO.writeAtomic(indexFile, data);
        indexDirty = false;
    }

    private void requireWritable() {
        if (!loaded) {
            throw new IllegalStateException("QIO Processing network storage is not loaded");
        }
        if (readOnlyFutureIndex) {
            throw new IllegalStateException("QIO Processing network storage has a newer world index");
        }
    }

    private void clearRuntimeState() {
        for (QIOProcessingNetworkData network : networks.values()) {
            network.bindDirtyListener(null);
        }
        networks.clear();
        dirtyNetworks.clear();
        damagedNetworks.clear();
        futureNetworks.clear();
        reportedIsolatedNetworks.clear();
        requestedWorldDirectory = null;
        worldDirectory = null;
        rootDirectory = null;
        networkDirectory = null;
        indexFile = null;
        loaded = false;
        readOnlyFutureIndex = false;
        indexDirty = false;
    }

    public enum IsolationStatus {
        DAMAGED,
        FUTURE
    }

    public static final class IsolatedNetworkException extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        private final UUID frequencyUUID;
        private final IsolationStatus status;

        private IsolatedNetworkException(UUID frequencyUUID, IsolationStatus status) {
            super("QIO Processing network " + frequencyUUID + " is isolated as " + status +
                  " and cannot be overwritten");
            this.frequencyUUID = frequencyUUID;
            this.status = status;
        }

        @Nonnull
        public UUID getFrequencyUUID() {
            return frequencyUUID;
        }

        @Nonnull
        public IsolationStatus getStatus() {
            return status;
        }
    }
}
