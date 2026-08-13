package mekanism.common.frequency;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.NBTConstants;
import mekanism.api.qio.external.QIOFrequencyDeleteCheck;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.Mekanism;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOFrequencyLifecycleRegistry;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.security.SecurityFrequency;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FrequencyManager<FREQ extends Frequency> {

    public static final int MAX_FREQ_LENGTH = 16;
    public static final List<Character> SPECIAL_CHARS = Arrays.asList('-', ' ', '|', '\'', '\"', '_', '+', ':', '(', ')', '?', '!', '/', '@', '$', '`', '~', ',', '.', '#');

    public static boolean loaded;
    @Nullable
    static World currentWorld;

    private static final Set<FrequencyManager<?>> managers = new ObjectOpenHashSet<>();

    static void unregister(FrequencyManager<?> manager) {
        managers.remove(manager);
    }

    private final Map<Object, FREQ> frequencies = new LinkedHashMap<>();
    @Nullable
    private FrequencyDataHandler dataHandler;
    private UUID ownerUUID;
    private SecurityMode securityMode = SecurityMode.PUBLIC;
    private final FrequencyType<FREQ> frequencyType;

    public FrequencyManager(FrequencyType<FREQ> frequencyType) {
        this.frequencyType = frequencyType;
        managers.add(this);
    }

    public FrequencyManager(FrequencyType<FREQ> frequencyType, UUID ownerUUID, SecurityMode securityMode) {
        this(frequencyType);
        this.ownerUUID = ownerUUID;
        this.securityMode = securityMode == null ? SecurityMode.PUBLIC : securityMode;
    }

    public static void load(World world) {
        if (!loaded) {
            loaded = true;
            currentWorld = world;
            FrequencyType.init();
            for (FrequencyManager<?> manager : new ArrayList<>(managers)) {
                manager.createOrLoad(world);
            }
        }
    }

    public static void tick(World world) {
        if (world == null || world.isRemote || world.provider.getDimension() != 0) {
            return;
        }
        if (!loaded) {
            load(world);
        }
        currentWorld = world;
        tickServer();
    }

    /** Ticks global frequency state once after all server worlds have ticked. */
    public static void tickServer() {
        if (!loaded) {
            return;
        }
        for (FrequencyManager<?> manager : new ArrayList<>(managers)) {
            manager.tickSelf(currentWorld);
        }
    }

    public static void reset() {
        for (FrequencyManager<?> manager : new ArrayList<>(managers)) {
            manager.frequencies.clear();
            manager.dataHandler = null;
        }
        FrequencyType.clear();
        currentWorld = null;
        loaded = false;
    }

    public boolean remove(Object key, UUID ownerUUID) {
        FREQ freq = getFrequency(key);
        if (freq != null && freq.ownerMatches(ownerUUID)) {
            QIOFrequencyReference reference = null;
            if (freq instanceof QIOFrequency qioFrequency) {
                reference = QIOFrequencyStorageAccess.INSTANCE.createReference(qioFrequency,
                      ownerUUID);
                QIOFrequencyDeleteCheck check = QIOFrequencyLifecycleRegistry.beforeDelete(
                      reference, ownerUUID);
                if (!check.isAllowed()) {
                    Mekanism.logger.warn("Refusing to delete QIO frequency {} ({}) because: {}",
                          qioFrequency.getName(), qioFrequency.getFrequencyUUID(),
                          String.join(", ", check.getBlockers()));
                    return false;
                }
            }
            freq.onRemove();
            frequencies.remove(key);
            markDirty();
            if (reference != null) {
                QIOFrequencyLifecycleRegistry.afterDelete(reference, ownerUUID);
            }
            return true;
        }
        return false;
    }

    public void deactivate(Object source) {
        deactivate(null, source);
    }

    public void deactivate(@Nullable Frequency freq, Object source) {
        if (freq != null) {
            if (freq.onDeactivate(source)) {
                markDirty();
            }
            return;
        }
        for (FREQ iterFreq : frequencies.values()) {
            if (iterFreq.onDeactivate(source)) {
                markDirty();
            }
        }
    }

    public FREQ update(Object source, FREQ freq) {
        FREQ storedFreq = frequencies.get(freq.getKey());
        if (storedFreq != null) {
            if (storedFreq.update(source)) {
                markDirty();
            }
            return storedFreq;
        }
        deactivate(source);
        return null;
    }

    public FREQ validateAndUpdate(Object source, FREQ freq) {
        FREQ storedFreq = frequencies.get(freq.getKey());
        if (storedFreq == null) {
            freq.setValid(true);
            frequencies.put(freq.getKey(), freq);
            storedFreq = freq;
            markDirty();
        }
        if (storedFreq.update(source)) {
            markDirty();
        }
        return storedFreq;
    }

    public void createOrLoad(World world) {
        if (world == null || dataHandler != null) {
            return;
        }
        String name = getName();
        dataHandler = (FrequencyDataHandler) world.getPerWorldStorage().getOrLoadData(FrequencyDataHandler.class, name);
        if (dataHandler == null) {
            dataHandler = new FrequencyDataHandler(name);
            dataHandler.setManager(this);
            world.getPerWorldStorage().setData(name, dataHandler);
        } else {
            dataHandler.setManager(this);
            dataHandler.syncManager();
        }
    }

    public Collection<FREQ> getFrequencies() {
        if (securityMode == SecurityMode.TRUSTED && ownerUUID != null) {
            List<FREQ> trustedFrequencies = new ArrayList<>(frequencies.values());
            FrequencyManager<SecurityFrequency> securityManager = FrequencyType.SECURITY.getManager(null, SecurityMode.PUBLIC);
            for (FrequencyManager<FREQ> trustedManager : frequencyType.getManagerWrapper().getTrustedManagers()) {
                if (!ownerUUID.equals(trustedManager.ownerUUID)) {
                    SecurityFrequency frequency = securityManager == null ? null : securityManager.getFrequency(trustedManager.ownerUUID);
                    if (frequency != null && frequency.isTrusted(ownerUUID)) {
                        trustedFrequencies.addAll(trustedManager.frequencies.values());
                    }
                }
            }
            return trustedFrequencies;
        }
        return frequencies.values();
    }

    @Nullable
    public FREQ getFrequency(Object key) {
        return frequencies.get(key);
    }

    public FREQ getOrCreateFrequency(FrequencyIdentity identity, @Nullable UUID ownerUUID) {
        FREQ freq = frequencies.get(identity.key());
        if (freq == null) {
            freq = frequencyType.create(identity.key(), ownerUUID, identity.securityMode());
            frequencies.put(identity.key(), freq);
            markDirty();
        }
        return freq;
    }

    public void addFrequency(FREQ freq) {
        frequencies.put(freq.getKey(), freq);
        markDirty();
    }

    protected void markDirty() {
        if (dataHandler != null) {
            dataHandler.markDirty();
        }
    }

    public FrequencyType<FREQ> getType() {
        return frequencyType;
    }

    private void tickSelf(World world) {
        boolean dirty = false;
        Iterator<FREQ> iter = frequencies.values().iterator();
        while (iter.hasNext()) {
            FREQ frequency = iter.next();
            if (frequency.isRemoved()) {
                iter.remove();
                dirty = true;
                continue;
            }
            dirty |= frequency.tick(true);
        }
        if (dirty) {
            markDirty();
        }
    }

    public String getName() {
        String owner = ownerUUID == null ? "" : ownerUUID + "_";
        if (securityMode != SecurityMode.PUBLIC) {
            return owner + frequencyType.getName() + securityMode.name() + "FrequencyHandler";
        }
        return owner + frequencyType.getName() + "FrequencyHandler";
    }

    public static class FrequencyDataHandler extends WorldSavedData {

        public FrequencyManager<?> manager;
        public Set<Frequency> loadedFrequencies;
        public UUID loadedOwner;

        public FrequencyDataHandler(String tagName) {
            super(tagName);
        }

        public void setManager(FrequencyManager<?> manager) {
            this.manager = manager;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public void syncManager() {
            if (loadedFrequencies != null) {
                for (Frequency frequency : loadedFrequencies) {
                    ((FrequencyManager) manager).frequencies.put(frequency.getKey(), frequency);
                }
                manager.ownerUUID = loadedOwner;
            }
        }

        @Override
        public void readFromNBT(@Nonnull NBTTagCompound nbtTags) {
            if (nbtTags.hasKey(NBTConstants.OWNER_UUID)) {
                loadedOwner = UUID.fromString(nbtTags.getString(NBTConstants.OWNER_UUID));
            }
            NBTTagList list = nbtTags.getTagList(NBTConstants.FREQUENCY_LIST, NBT.TAG_COMPOUND);
            loadedFrequencies = new ObjectOpenHashSet<>();
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound compound = list.getCompoundTagAt(i);
                FrequencyType<?> type = FrequencyType.load(compound.getString(NBTConstants.TYPE));
                if (type == null && manager != null) {
                    type = manager.frequencyType;
                }
                if (type != null) {
                    loadedFrequencies.add(type.create(compound));
                }
            }
        }

        @Nonnull
        @Override
        public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound nbtTags) {
            if (manager.ownerUUID != null) {
                nbtTags.setString(NBTConstants.OWNER_UUID, manager.ownerUUID.toString());
            }
            NBTTagList list = new NBTTagList();
            for (Frequency freq : manager.getFrequencies()) {
                NBTTagCompound compound = new NBTTagCompound();
                freq.write(compound);
                list.appendTag(compound);
            }
            nbtTags.setTag(NBTConstants.FREQUENCY_LIST, list);
            return nbtTags;
        }
    }
}
