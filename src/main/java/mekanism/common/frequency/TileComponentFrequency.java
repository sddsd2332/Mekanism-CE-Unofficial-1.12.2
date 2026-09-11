package mekanism.common.frequency;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.api.NBTConstants;
import mekanism.api.TileNetworkList;
import mekanism.common.base.ITileComponent;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableFrequency;
import mekanism.common.inventory.container.sync.SyncableFrequencyList;
import mekanism.common.security.ISecurityTile;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.security.SecurityFrequency;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.SecurityUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class TileComponentFrequency implements ITileComponent {

    private static final AtomicInteger OFFSET = new AtomicInteger();

    private final TileEntityContainerBlock tile;
    private final Map<FrequencyType<?>, FrequencyData> nonSecurityFrequencies = new LinkedHashMap<>();
    @Nullable
    private FrequencyData securityFrequency;
    private final Map<SecurityMode, Map<FrequencyType<?>, List<? extends Frequency>>> frequencyCache = new EnumMap<>(SecurityMode.class);
    private final int tickOffset;
    private boolean needsSave;
    private boolean needsNotify;

    public TileComponentFrequency(TileEntityContainerBlock tile) {
        this.tile = tile;
        tickOffset = OFFSET.getAndIncrement() % 5;
        tile.components.add(this);
    }

    public void track(FrequencyType<?> type, boolean needsSync, boolean needsListCache, boolean notifyNeighbors) {
        FrequencyData data = new FrequencyData(needsSync, needsListCache, notifyNeighbors);
        if (type == FrequencyType.SECURITY) {
            securityFrequency = data;
        } else {
            nonSecurityFrequencies.put(type, data);
        }
    }

    public boolean hasCustomFrequencies() {
        return !nonSecurityFrequencies.isEmpty();
    }

    public Set<FrequencyType<?>> getCustomFrequencies() {
        return Collections.unmodifiableSet(nonSecurityFrequencies.keySet());
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <FREQ extends Frequency> FREQ getFrequency(FrequencyType<FREQ> type) {
        FrequencyData data = getFrequencyData(type);
        return data == null ? null : (FREQ) data.frequency;
    }

    @Nullable
    private FrequencyData getFrequencyData(FrequencyType<?> type) {
        return type == FrequencyType.SECURITY ? securityFrequency : nonSecurityFrequencies.get(type);
    }

    @SuppressWarnings("unchecked")
    public <FREQ extends Frequency> List<FREQ> getPublicCache(FrequencyType<FREQ> type) {
        return (List<FREQ>) getCache(SecurityMode.PUBLIC, type);
    }

    @SuppressWarnings("unchecked")
    public <FREQ extends Frequency> List<FREQ> getPrivateCache(FrequencyType<FREQ> type) {
        return (List<FREQ>) getCache(SecurityMode.PRIVATE, type);
    }

    @SuppressWarnings("unchecked")
    public <FREQ extends Frequency> List<FREQ> getTrustedCache(FrequencyType<FREQ> type) {
        return (List<FREQ>) getCache(SecurityMode.TRUSTED, type);
    }

    private <FREQ extends Frequency> List<FREQ> getCache(SecurityMode securityMode, FrequencyType<FREQ> type) {
        Map<FrequencyType<?>, List<? extends Frequency>> cache = frequencyCache.computeIfAbsent(securityMode, mode -> new LinkedHashMap<>());
        return (List<FREQ>) cache.computeIfAbsent(type, ignored -> new ArrayList<>());
    }

    public <FREQ extends Frequency> void setFrequency(FrequencyType<FREQ> type, FrequencyIdentity identity, UUID player) {
        setFrequencyFromData(type, identity, player);
    }

    public <FREQ extends Frequency> void setFrequencyFromData(FrequencyType<FREQ> type, FrequencyIdentity identity, UUID player) {
        FrequencyData data = getFrequencyData(type);
        if (data == null || identity == null || player == null) {
            return;
        }
        setFrequencyFromData(type, identity, player, data);
    }

    private <FREQ extends Frequency> void setFrequencyFromData(FrequencyType<FREQ> type, FrequencyIdentity identity, UUID player, FrequencyData data) {
        FREQ oldFrequency = getFrequency(type);
        FREQ frequency = null;
        FrequencyManager<FREQ> manager;
        if (!player.equals(identity.ownerUUID()) && SecurityUtils.isTrusted(identity.securityMode(), identity.ownerUUID(), player)) {
            manager = type.getManager(identity, identity.ownerUUID());
            frequency = manager == null ? null : manager.getFrequency(identity.key());
            if (frequency == null) {
                identity = new FrequencyIdentity(identity.key(), identity.securityMode(), player);
            }
        }
        if (frequency == null) {
            manager = type.getManager(identity, player);
            if (manager == null) {
                return;
            }
            frequency = manager.getOrCreateFrequency(identity, player);
        } else {
            manager = type.getFrequencyManager(frequency);
            if (manager == null) {
                return;
            }
        }
        if (!frequency.equals(oldFrequency)) {
            deactivate(type, data);
            frequency.update(tile);
            data.frequency = frequency;
            setNeedsNotify(data);
        }
    }

    public <FREQ extends Frequency> void removeFrequency(FrequencyType<FREQ> type, FrequencyIdentity identity, UUID player) {
        removeFrequencyFromData(type, identity, player);
    }

    public <FREQ extends Frequency> void removeFrequencyFromData(FrequencyType<FREQ> type, FrequencyIdentity identity, UUID player) {
        if (identity == null || player == null) {
            return;
        }
        FrequencyManager<FREQ> manager = type.getManager(identity, identity.ownerUUID() == null ? player : identity.ownerUUID());
        if (manager != null && manager.remove(identity.key(), player)) {
            FrequencyData data = getFrequencyData(type);
            if (data != null) {
                setNeedsNotify(data);
            }
        }
    }

    public <FREQ extends Frequency> void unsetFrequency(FrequencyType<FREQ> type) {
        FrequencyData data = getFrequencyData(type);
        unsetFrequency(type, data);
    }

    private <FREQ extends Frequency> void unsetFrequency(FrequencyType<FREQ> type, FrequencyData data) {
        if (data != null && data.frequency != null) {
            deactivate(type, data);
            data.frequency = null;
            setNeedsNotify(data);
        }
    }

    @Override
    public void tick() {
        if (tile.getWorld() == null || tile.getWorld().isRemote) {
            return;
        }
        if (tile.getWorld().getTotalWorldTime() % 5 == tickOffset) {
            if (securityFrequency != null) {
                updateFrequency(FrequencyType.SECURITY, securityFrequency);
            }
            for (Map.Entry<FrequencyType<?>, FrequencyData> entry : nonSecurityFrequencies.entrySet()) {
                updateFrequency((FrequencyType<Frequency>) entry.getKey(), entry.getValue());
            }
        }
        if (needsNotify) {
            MekanismUtils.notifyLoadedNeighborsOfTileChange(tile.getWorld(), Coord4D.get(tile));
            needsNotify = false;
        }
        if (needsSave) {
            tile.markNoUpdateSync();
            needsSave = false;
        }
    }

    private <FREQ extends Frequency> void updateFrequency(FrequencyType<FREQ> type, FrequencyData data) {
        if (data.frequency == null) {
            return;
        }
        FREQ frequency = (FREQ) data.frequency;
        if (frequency.isRemoved()) {
            deactivate(type, data);
            data.frequency = null;
            setNeedsNotify(data);
            return;
        }
        if (type != FrequencyType.SECURITY && frequency.getSecurity() == SecurityMode.TRUSTED && tile instanceof ISecurityTile securityTile) {
            UUID ownerUUID = securityTile.getSecurity().getOwnerUUID();
            if (ownerUUID != null && !frequency.ownerMatches(ownerUUID)) {
                SecurityFrequency security = FrequencyType.SECURITY.getManager(null, SecurityMode.PUBLIC).getFrequency(frequency.getOwner());
                if (security != null && !security.isTrusted(ownerUUID)) {
                    deactivate(type, data);
                    data.frequency = null;
                    setNeedsNotify(data);
                    return;
                }
            }
        }
        if (frequency.isValid()) {
            return;
        }
        FrequencyManager<FREQ> manager = type.getFrequencyManager(frequency);
        if (manager == null) {
            data.frequency = null;
        } else {
            data.frequency = manager.validateAndUpdate(tile, frequency);
        }
        setNeedsNotify(data);
    }

    private <FREQ extends Frequency> void deactivate(FrequencyType<FREQ> type, FrequencyData data) {
        if (data.frequency != null) {
            FrequencyManager<FREQ> manager = type.getFrequencyManager((FREQ) data.frequency);
            if (manager != null) {
                manager.deactivate(data.frequency, tile);
            }
        }
    }

    private void setNeedsNotify(FrequencyData data) {
        if (data.notifyNeighbors) {
            needsNotify = true;
        }
        needsSave = true;
    }

    private <FREQ extends Frequency> Consumer<List<FREQ>> getSetter(SecurityMode securityMode, FrequencyType<FREQ> type) {
        Map<FrequencyType<?>, List<? extends Frequency>> cache = frequencyCache.computeIfAbsent(securityMode, mode -> new LinkedHashMap<>());
        return value -> cache.put(type, value);
    }

    @Override
    public void read(NBTTagCompound nbtTags) {
        if (!nbtTags.hasKey(NBTConstants.COMPONENT_FREQUENCY)) {
            return;
        }
        NBTTagCompound frequencyNBT = nbtTags.getCompoundTag(NBTConstants.COMPONENT_FREQUENCY);
        if (securityFrequency != null) {
            readFrequency(frequencyNBT, FrequencyType.SECURITY, securityFrequency);
        }
        for (Map.Entry<FrequencyType<?>, FrequencyData> entry : nonSecurityFrequencies.entrySet()) {
            readFrequency(frequencyNBT, entry.getKey(), entry.getValue());
        }
    }

    private void readFrequency(NBTTagCompound frequencyNBT, FrequencyType<?> type, FrequencyData data) {
        if (frequencyNBT.hasKey(type.getName())) {
            data.frequency = type.createFromIdentity(frequencyNBT.getCompoundTag(type.getName()));
        }
    }

    @Override
    public void read(ByteBuf dataStream) {
        if (securityFrequency != null) {
            readFrequency(dataStream, securityFrequency);
        }
        for (Map.Entry<FrequencyType<?>, FrequencyData> entry : nonSecurityFrequencies.entrySet()) {
            readFrequency(dataStream, entry.getValue());
        }
    }

    private void readFrequency(ByteBuf dataStream, FrequencyData data) {
        data.frequency = dataStream.readBoolean() ? FrequencyType.read(dataStream) : null;
    }

    @Override
    public void write(NBTTagCompound nbtTags) {
        NBTTagCompound frequencyNBT = new NBTTagCompound();
        if (securityFrequency != null) {
            writeFrequency(frequencyNBT, FrequencyType.SECURITY, securityFrequency);
        }
        for (Map.Entry<FrequencyType<?>, FrequencyData> entry : nonSecurityFrequencies.entrySet()) {
            writeFrequency(frequencyNBT, entry.getKey(), entry.getValue());
        }
        if (!frequencyNBT.isEmpty()) {
            nbtTags.setTag(NBTConstants.COMPONENT_FREQUENCY, frequencyNBT);
        }
    }

    private void writeFrequency(NBTTagCompound frequencyNBT, FrequencyType<?> type, FrequencyData data) {
        Frequency frequency = data.frequency;
        if (frequency != null) {
            frequencyNBT.setTag(type.getName(), type.getIdentitySerializer().write(frequency.getIdentity()));
        }
    }

    public void readConfiguredFrequencies(UUID player, NBTTagCompound data) {
        if (player == null || !hasCustomFrequencies() || !data.hasKey(NBTConstants.COMPONENT_FREQUENCY, NBT.TAG_COMPOUND)) {
            return;
        }
        NBTTagCompound frequencyNBT = data.getCompoundTag(NBTConstants.COMPONENT_FREQUENCY);
        for (Map.Entry<FrequencyType<?>, FrequencyData> entry : nonSecurityFrequencies.entrySet()) {
            readConfiguredFrequency(player, frequencyNBT, entry.getKey(), entry.getValue());
        }
    }

    private <FREQ extends Frequency> void readConfiguredFrequency(UUID player, NBTTagCompound frequencyNBT, FrequencyType<FREQ> type, FrequencyData data) {
        if (frequencyNBT.hasKey(type.getName(), NBT.TAG_COMPOUND)) {
            FrequencyIdentity identity = type.getIdentitySerializer().read(frequencyNBT.getCompoundTag(type.getName()));
            if (identity != null && identity.ownerUUID() != null) {
                if (identity.securityMode() == SecurityMode.PUBLIC || identity.ownerUUID().equals(player)) {
                    setFrequencyFromData(type, identity, identity.ownerUUID(), data);
                }
                return;
            }
        }
        unsetFrequency(type, data);
    }

    public void writeConfiguredFrequencies(NBTTagCompound data) {
        NBTTagCompound frequencyNBT = new NBTTagCompound();
        for (Map.Entry<FrequencyType<?>, FrequencyData> entry : nonSecurityFrequencies.entrySet()) {
            writeFrequency(frequencyNBT, entry.getKey(), entry.getValue());
        }
        if (!frequencyNBT.isEmpty()) {
            data.setTag(NBTConstants.COMPONENT_FREQUENCY, frequencyNBT);
        }
    }

    @Override
    public void write(TileNetworkList data) {
        if (securityFrequency != null) {
            writeFrequency(data, securityFrequency);
        }
        for (Map.Entry<FrequencyType<?>, FrequencyData> entry : nonSecurityFrequencies.entrySet()) {
            writeFrequency(data, entry.getValue());
        }
    }

    private void writeFrequency(TileNetworkList data, FrequencyData frequencyData) {
        Frequency frequency = frequencyData.frequency;
        if (frequency != null) {
            data.add(true);
            frequency.write(data);
        } else {
            data.add(false);
        }
    }

    @Override
    public void invalidate() {
        if (tile.getWorld() == null || tile.getWorld().isRemote) {
            return;
        }
        if (securityFrequency != null) {
            deactivate(FrequencyType.SECURITY, securityFrequency);
        }
        for (Map.Entry<FrequencyType<?>, FrequencyData> entry : nonSecurityFrequencies.entrySet()) {
            deactivate((FrequencyType<Frequency>) entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void trackForMainContainer(MekanismContainer container) {
        if (securityFrequency != null) {
            trackFrequencyForMainContainer(container, FrequencyType.SECURITY, securityFrequency);
        }
        for (Map.Entry<FrequencyType<?>, FrequencyData> entry : nonSecurityFrequencies.entrySet()) {
            trackFrequencyForMainContainer(container, entry.getKey(), entry.getValue());
        }
    }

    private void trackFrequencyForMainContainer(MekanismContainer container, FrequencyType<?> key, FrequencyData data) {
        FrequencyType<Frequency> type = (FrequencyType<Frequency>) key;
        if (data.needsSync) {
            container.track(SyncableFrequency.create(type, () -> data.frequency, frequency -> data.frequency = frequency));
        }
        if (data.needsListCache) {
            Consumer<List<Frequency>> publicSetter = getSetter(SecurityMode.PUBLIC, type);
            Consumer<List<Frequency>> privateSetter = getSetter(SecurityMode.PRIVATE, type);
            Consumer<List<Frequency>> trustedSetter = getSetter(SecurityMode.TRUSTED, type);
            if (container.isRemote()) {
                container.track(SyncableFrequencyList.create(type, () -> getPublicCache(type), publicSetter));
                container.track(SyncableFrequencyList.create(type, () -> getPrivateCache(type), privateSetter));
                container.track(SyncableFrequencyList.create(type, () -> getTrustedCache(type), trustedSetter));
            } else {
                container.track(SyncableFrequencyList.create(type, () -> type.getManager(null, SecurityMode.PUBLIC).getFrequencies(),
                      publicSetter));
                container.track(SyncableFrequencyList.create(type, () -> type.getManager(container.getPlayerUUID(), SecurityMode.PRIVATE).getFrequencies(),
                      privateSetter));
                container.track(SyncableFrequencyList.create(type, () -> type.getManager(container.getPlayerUUID(), SecurityMode.TRUSTED).getFrequencies(),
                      trustedSetter));
            }
        }
    }

    private static class FrequencyData {

        private final boolean needsSync;
        private final boolean needsListCache;
        private final boolean notifyNeighbors;
        @Nullable
        private Frequency frequency;

        private FrequencyData(boolean needsSync, boolean needsListCache, boolean notifyNeighbors) {
            this.needsSync = needsSync;
            this.needsListCache = needsListCache;
            this.notifyNeighbors = notifyNeighbors;
        }
    }
}
