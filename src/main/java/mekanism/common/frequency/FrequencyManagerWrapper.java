package mekanism.common.frequency;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import mekanism.common.Mekanism;
import mekanism.common.security.ISecurityTile.SecurityMode;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class FrequencyManagerWrapper<FREQ extends Frequency> {

    private final Type type;
    private final FrequencyType<FREQ> frequencyType;
    private FrequencyManager<FREQ> publicManager;
    private Map<UUID, FrequencyManager<FREQ>> privateManagers;
    private Map<UUID, FrequencyManager<FREQ>> trustedManagers;

    private FrequencyManagerWrapper(FrequencyType<FREQ> frequencyType, Type type) {
        this.frequencyType = frequencyType;
        this.type = type;
        if (type.supportsPublic()) {
            publicManager = new FrequencyManager<>(frequencyType);
        }
        if (type.supportsPrivate()) {
            privateManagers = new Object2ObjectOpenHashMap<>();
        }
        if (type.supportsTrusted()) {
            trustedManagers = new Object2ObjectOpenHashMap<>();
        }
    }

    public static <FREQ extends Frequency> FrequencyManagerWrapper<FREQ> create(FrequencyType<FREQ> frequencyType, Type type) {
        return new FrequencyManagerWrapper<>(frequencyType, type);
    }

    public FrequencyManager<FREQ> getPublicManager() {
        if (!type.supportsPublic()) {
            Mekanism.logger.error("Attempted to access public frequency manager of type {}.", frequencyType.getName());
            return null;
        }
        return publicManager;
    }

    public FrequencyManager<FREQ> getPrivateManager(UUID ownerUUID) {
        if (!type.supportsPrivate()) {
            Mekanism.logger.error("Attempted to access private frequency manager of type {}.", frequencyType.getName());
            return null;
        }
        return getOwnedManager(privateManagers, ownerUUID, SecurityMode.PRIVATE);
    }

    public FrequencyManager<FREQ> getTrustedManager(UUID ownerUUID) {
        if (!type.supportsTrusted()) {
            Mekanism.logger.error("Attempted to access trusted frequency manager of type {}.", frequencyType.getName());
            return null;
        }
        return getOwnedManager(trustedManagers, ownerUUID, SecurityMode.TRUSTED);
    }

    private FrequencyManager<FREQ> getOwnedManager(Map<UUID, FrequencyManager<FREQ>> managers, UUID ownerUUID, SecurityMode securityMode) {
        if (ownerUUID == null) {
            Mekanism.logger.error("Attempted to access {} frequency manager of type {} with no owner.", securityMode, frequencyType.getName());
            return null;
        }
        FrequencyManager<FREQ> manager = managers.get(ownerUUID);
        if (manager == null) {
            manager = new FrequencyManager<>(frequencyType, ownerUUID, securityMode);
            if (FrequencyManager.loaded) {
                manager.createOrLoad(FrequencyManager.currentWorld);
            }
            managers.put(ownerUUID, manager);
        }
        return manager;
    }

    public Collection<FrequencyManager<FREQ>> getTrustedManagers() {
        return trustedManagers == null ? Collections.emptyList() : trustedManagers.values();
    }

    public void clear() {
        if (privateManagers != null) {
            privateManagers.values().forEach(FrequencyManager::unregister);
            privateManagers.clear();
        }
        if (trustedManagers != null) {
            trustedManagers.values().forEach(FrequencyManager::unregister);
            trustedManagers.clear();
        }
    }

    public enum Type {
        PUBLIC_ONLY,
        PRIVATE_ONLY,
        PUBLIC_PRIVATE_TRUSTED;

        boolean supportsPublic() {
            return this == PUBLIC_ONLY || this == PUBLIC_PRIVATE_TRUSTED;
        }

        boolean supportsPrivate() {
            return this == PRIVATE_ONLY || this == PUBLIC_PRIVATE_TRUSTED;
        }

        boolean supportsTrusted() {
            return this == PUBLIC_PRIVATE_TRUSTED;
        }
    }
}
