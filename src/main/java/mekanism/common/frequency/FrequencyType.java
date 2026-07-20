package mekanism.common.frequency;

import io.netty.buffer.ByteBuf;
import mekanism.common.Mekanism;
import mekanism.common.content.entangloporter.InventoryFrequency;
import mekanism.common.content.teleporter.TeleporterFrequency;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.security.SecurityFrequency;
import mekanism.common.util.SecurityUtils;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class FrequencyType<FREQ extends Frequency> {

    private static final Map<String, FrequencyType<?>> REGISTRY = new HashMap<>();
    public static final FrequencyType<TeleporterFrequency> TELEPORTER = register(Frequency.TELEPORTER,
          (key, uuid, securityMode) -> new TeleporterFrequency(String.valueOf(key), uuid, securityMode), TeleporterFrequency::new, TeleporterFrequency::new,
          FrequencyManagerWrapper.Type.PUBLIC_PRIVATE_TRUSTED, IdentitySerializer.NAME);
    public static final FrequencyType<InventoryFrequency> INVENTORY = register("Inventory",
          (key, uuid, securityMode) -> new InventoryFrequency(String.valueOf(key), uuid, securityMode), InventoryFrequency::new, InventoryFrequency::new,
          FrequencyManagerWrapper.Type.PUBLIC_PRIVATE_TRUSTED, IdentitySerializer.NAME);
    public static final FrequencyType<QIOFrequency> QIO = register(Frequency.QIO,
          (key, uuid, securityMode) -> new QIOFrequency(String.valueOf(key), uuid, securityMode),
          QIOFrequency::new, QIOFrequency::new,
          FrequencyManagerWrapper.Type.PUBLIC_PRIVATE_TRUSTED, IdentitySerializer.NAME);
    public static final FrequencyType<SecurityFrequency> SECURITY = register(SecurityFrequency.SECURITY,
          (key, uuid, securityMode) -> new SecurityFrequency(uuid, securityMode),
          SecurityFrequency::new, SecurityFrequency::new, FrequencyManagerWrapper.Type.PUBLIC_ONLY, IdentitySerializer.UUID);

    public static void init() {
    }

    private static <FREQ extends Frequency> FrequencyType<FREQ> register(String name, FrequencyConstructor<FREQ> creationFunction,
          NBTConstructor<FREQ> nbtConstructor, BufferConstructor<FREQ> bufferConstructor, FrequencyManagerWrapper.Type managerType,
          IdentitySerializer identitySerializer) {
        FrequencyType<FREQ> type = new FrequencyType<>(name, creationFunction, nbtConstructor, bufferConstructor, managerType, identitySerializer);
        REGISTRY.put(name, type);
        return type;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <FREQ extends Frequency> FrequencyType<FREQ> load(String name) {
        return (FrequencyType<FREQ>) REGISTRY.get(name);
    }

    @Nullable
    public static Frequency read(ByteBuf buffer) {
        buffer.markReaderIndex();
        FrequencyType<?> type = load(mekanism.common.PacketHandler.readString(buffer));
        buffer.resetReaderIndex();
        return type == null ? null : type.create(buffer);
    }

    @Nullable
    public static FrequencyIdentity readIdentity(ByteBuf buffer, FrequencyType<?> type) {
        return type == null ? null : type.getIdentitySerializer().read(buffer);
    }

    public static void writeIdentity(ByteBuf buffer, FrequencyType<?> type, FrequencyIdentity identity) {
        type.getIdentitySerializer().write(buffer, identity);
    }

    public static void clear() {
        for (FrequencyType<?> type : REGISTRY.values()) {
            type.managerWrapper.clear();
        }
    }

    private final String name;
    private final FrequencyConstructor<FREQ> creationFunction;
    private final NBTConstructor<FREQ> nbtConstructor;
    private final BufferConstructor<FREQ> bufferConstructor;
    private final FrequencyManagerWrapper<FREQ> managerWrapper;
    private final IdentitySerializer identitySerializer;

    private FrequencyType(String name, FrequencyConstructor<FREQ> creationFunction, NBTConstructor<FREQ> nbtConstructor,
          BufferConstructor<FREQ> bufferConstructor, FrequencyManagerWrapper.Type managerType, IdentitySerializer identitySerializer) {
        this.name = name;
        this.creationFunction = creationFunction;
        this.nbtConstructor = nbtConstructor;
        this.bufferConstructor = bufferConstructor;
        this.managerWrapper = FrequencyManagerWrapper.create(this, managerType);
        this.identitySerializer = identitySerializer;
    }

    public String getName() {
        return name;
    }

    public FREQ create(Object key, @Nullable UUID ownerUUID, SecurityMode securityMode) {
        return creationFunction.create(key, ownerUUID, securityMode);
    }

    public FREQ create(net.minecraft.nbt.NBTTagCompound tag) {
        return nbtConstructor.create(tag);
    }

    public FREQ create(ByteBuf buffer) {
        return bufferConstructor.create(buffer);
    }

    public FrequencyManagerWrapper<FREQ> getManagerWrapper() {
        return managerWrapper;
    }

    public IdentitySerializer getIdentitySerializer() {
        return identitySerializer;
    }

    @Nullable
    public FREQ createFromIdentity(net.minecraft.nbt.NBTTagCompound tag) {
        FrequencyIdentity identity = identitySerializer.read(tag);
        if (identity == null) {
            return null;
        }
        FREQ frequency = create(identity.key(), identity.ownerUUID(), identity.securityMode());
        frequency.setValid(false);
        return frequency;
    }

    public FrequencyManager<FREQ> getManager(@Nullable UUID owner, SecurityMode securityMode) {
        FrequencyManager<FREQ> manager = switch (securityMode == null ? SecurityMode.PUBLIC : securityMode) {
            case PUBLIC -> getManagerWrapper().getPublicManager();
            case PRIVATE -> getManagerWrapper().getPrivateManager(owner);
            case TRUSTED -> getManagerWrapper().getTrustedManager(owner);
        };
        if (manager != null && FrequencyManager.loaded && FrequencyManager.currentWorld != null) {
            manager.createOrLoad(FrequencyManager.currentWorld);
        }
        return manager;
    }

    @Nullable
    public FrequencyManager<FREQ> getFrequencyManager(@Nullable FREQ freq) {
        if (freq == null) {
            return null;
        }
        if (freq.getType() == SECURITY) {
            return getManagerWrapper().getPublicManager();
        }
        return getManager(freq.getOwner(), freq.getSecurity());
    }

    public FrequencyManager<FREQ> getManager(FrequencyIdentity identity, UUID owner) {
        return getManager(owner, identity == null ? SecurityMode.PUBLIC : identity.securityMode());
    }

    @Nullable
    public FREQ getFrequency(FrequencyIdentity identity, UUID owner) {
        if (identity == null) {
            return null;
        }
        FrequencyManager<FREQ> manager;
        if (!Objects.equals(identity.ownerUUID(), owner) && SecurityUtils.isTrusted(identity.securityMode(), identity.ownerUUID(), owner)) {
            manager = getManager(identity, identity.ownerUUID());
        } else {
            manager = getManager(identity, owner);
        }
        return manager == null ? null : manager.getFrequency(identity.key());
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof FrequencyType<?> other && Objects.equals(name, other.name);
    }

    @FunctionalInterface
    private interface FrequencyConstructor<FREQ extends Frequency> {

        FREQ create(Object key, UUID owner, SecurityMode securityMode);
    }

    @FunctionalInterface
    private interface NBTConstructor<FREQ extends Frequency> {

        FREQ create(net.minecraft.nbt.NBTTagCompound tag);
    }

    @FunctionalInterface
    private interface BufferConstructor<FREQ extends Frequency> {

        FREQ create(ByteBuf buffer);
    }
}
