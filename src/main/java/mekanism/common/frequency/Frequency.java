package mekanism.common.frequency;

import io.netty.buffer.ByteBuf;
import mekanism.api.NBTConstants;
import mekanism.api.TileNetworkList;
import mekanism.common.PacketHandler;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.util.MekanismUtils;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

public abstract class Frequency {

    public static final String TELEPORTER = "Teleporter";

    protected boolean dirty;
    private boolean removed;
    private FrequencyType<?> frequencyType;
    public String name;
    public UUID ownerUUID;
    public String clientOwner;
    public boolean valid = true;
    private SecurityMode securityMode = SecurityMode.PUBLIC;

    protected Frequency(FrequencyType<?> frequencyType, String name, @Nullable UUID ownerUUID, SecurityMode securityMode) {
        this(frequencyType, name, ownerUUID, MekanismUtils.getLastKnownUsername(ownerUUID), securityMode);
    }

    protected Frequency(FrequencyType<?> frequencyType, String name, @Nullable UUID ownerUUID, String ownerName, SecurityMode securityMode) {
        this.frequencyType = frequencyType;
        this.name = name;
        this.ownerUUID = ownerUUID;
        this.clientOwner = ownerName;
        this.securityMode = securityMode == null ? SecurityMode.PUBLIC : securityMode;
    }

    protected Frequency(FrequencyType<?> frequencyType, NBTTagCompound nbtTags) {
        this.frequencyType = frequencyType;
        read(nbtTags);
    }

    protected Frequency(FrequencyType<?> frequencyType, ByteBuf dataStream) {
        this.frequencyType = frequencyType;
        read(dataStream);
    }

    /**
     * @return {@code true} if persistent data was changed and the frequency needs to be saved.
     */
    public boolean tick(boolean tickingNormally) {
        return dirty;
    }

    /**
     * Kept as a 1.12 bridge for existing world tick callers.
     */
    public boolean tick() {
        return tick(true);
    }

    public void onRemove() {
        removed = true;
    }

    public boolean isRemoved() {
        return removed;
    }

    /**
     * @return {@code true} if persistent data was changed by deactivating the tile and the frequency needs to be saved.
     */
    public boolean onDeactivate(Object coord) {
        return false;
    }

    /**
     * @return {@code true} if persistent data was changed by updating the tile and the frequency needs to be saved.
     */
    public boolean update(Object coord) {
        return false;
    }

    public FrequencyType<?> getType() {
        return frequencyType;
    }

    public Object getKey() {
        return name;
    }

    public SecurityMode getSecurity() {
        return securityMode;
    }

    public void setSecurityMode(SecurityMode securityMode) {
        SecurityMode mode = securityMode == null ? SecurityMode.PUBLIC : securityMode;
        if (this.securityMode != mode) {
            this.securityMode = mode;
            dirty = true;
        }
    }

    public Frequency setSecurity(SecurityMode securityMode) {
        setSecurityMode(securityMode);
        return this;
    }

    public boolean isPublic() {
        return getSecurity() == SecurityMode.PUBLIC;
    }

    public boolean isPrivate() {
        return getSecurity() == SecurityMode.PRIVATE;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getName() {
        return name;
    }

    @Nullable
    public UUID getOwner() {
        return ownerUUID;
    }

    public boolean ownerMatches(UUID toCheck) {
        return Objects.equals(ownerUUID, toCheck);
    }

    public String getOwnerName() {
        return clientOwner == null ? "" : clientOwner;
    }

    public int getSyncHash() {
        return hashCode();
    }

    public void write(NBTTagCompound nbtTags) {
        nbtTags.setString(NBTConstants.TYPE, frequencyType.getName());
        nbtTags.setString(NBTConstants.NAME, name);
        if (ownerUUID != null) {
            nbtTags.setString(NBTConstants.OWNER_UUID, ownerUUID.toString());
        }
        nbtTags.setInteger(NBTConstants.SECURITY_MODE, getSecurity().ordinal());
    }

    protected void read(NBTTagCompound nbtTags) {
        FrequencyType<?> serializedType = FrequencyType.load(nbtTags.getString(NBTConstants.TYPE));
        if (serializedType != null) {
            frequencyType = serializedType;
        } else if (frequencyType == null) {
            frequencyType = FrequencyType.TELEPORTER;
        }
        name = nbtTags.getString(NBTConstants.NAME);
        ownerUUID = nbtTags.hasKey(NBTConstants.OWNER_UUID) ? MekanismUtils.parseUUID(nbtTags.getString(NBTConstants.OWNER_UUID)) : null;
        clientOwner = MekanismUtils.getLastKnownUsername(ownerUUID);
        securityMode = SecurityMode.byIndexStatic(nbtTags.getInteger(NBTConstants.SECURITY_MODE));
    }

    public void write(TileNetworkList data) {
        data.add(frequencyType.getName());
        data.add(name);
        data.add(ownerUUID != null);
        if (ownerUUID != null) {
            data.add(ownerUUID.toString());
        }
        data.add(getOwnerName());
        data.add(getSecurity().ordinal());
    }

    public void write(ByteBuf buffer) {
        TileNetworkList data = new TileNetworkList();
        write(data);
        PacketHandler.encode(data.toArray(), buffer);
    }

    protected void read(ByteBuf dataStream) {
        FrequencyType<?> serializedType = FrequencyType.load(PacketHandler.readString(dataStream));
        frequencyType = serializedType == null ? FrequencyType.TELEPORTER : serializedType;
        name = PacketHandler.readString(dataStream);
        ownerUUID = dataStream.readBoolean() ? MekanismUtils.parseUUID(PacketHandler.readString(dataStream)) : null;
        clientOwner = PacketHandler.readString(dataStream);
        securityMode = SecurityMode.byIndexStatic(dataStream.readInt());
    }

    @Override
    public int hashCode() {
        int code = 1;
        code = 31 * code + Objects.hashCode(frequencyType);
        code = 31 * code + Objects.hashCode(name);
        code = 31 * code + Objects.hashCode(ownerUUID);
        if (frequencyType != FrequencyType.SECURITY) {
            code = 31 * code + getSecurity().ordinal();
        }
        return code;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Frequency frequency) {
            return Objects.equals(frequencyType, frequency.frequencyType)
                  && (frequencyType == FrequencyType.SECURITY || getSecurity() == frequency.getSecurity())
                  && Objects.equals(name, frequency.name)
                  && Objects.equals(ownerUUID, frequency.ownerUUID);
        }
        return false;
    }

    public FrequencyIdentity getIdentity() {
        return new FrequencyIdentity(getKey(), getSecurity(), ownerUUID);
    }

    public static class FrequencyIdentity {

        public final Object key;
        public final SecurityMode securityMode;
        @Nullable
        public final UUID ownerUUID;

        public FrequencyIdentity(Object key, SecurityMode securityMode, @Nullable UUID ownerUUID) {
            this.key = key;
            this.securityMode = securityMode == null ? SecurityMode.PUBLIC : securityMode;
            this.ownerUUID = ownerUUID;
        }

        public Object key() {
            return key;
        }

        public SecurityMode securityMode() {
            return securityMode;
        }

        @Nullable
        public UUID ownerUUID() {
            return ownerUUID;
        }

        @Nullable
        public static FrequencyIdentity load(NBTTagCompound data) {
            return IdentitySerializer.NAME.read(data);
        }

        public NBTTagCompound serialize() {
            return IdentitySerializer.NAME.write(this);
        }

        public void write(ByteBuf buffer) {
            IdentitySerializer.NAME.write(buffer, this);
        }

        @Nullable
        public static FrequencyIdentity read(ByteBuf buffer) {
            return IdentitySerializer.NAME.read(buffer);
        }

        public boolean isPublic() {
            return securityMode == SecurityMode.PUBLIC;
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, securityMode, ownerUUID);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof FrequencyIdentity other) {
                return Objects.equals(key, other.key)
                      && securityMode == other.securityMode
                      && Objects.equals(ownerUUID, other.ownerUUID);
            }
            return false;
        }
    }
}
