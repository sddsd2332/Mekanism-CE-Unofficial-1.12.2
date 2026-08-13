package mekanism.api.qio.external;

import mekanism.common.security.ISecurityTile.SecurityMode;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** Immutable identity used to reopen one exact QIO frequency without a dashboard. */
public final class QIOFrequencyReference {

    private static final String FREQUENCY_UUID = "frequencyUUID";
    private static final String LAST_KNOWN_IDENTITY = "lastKnownIdentity";
    private static final String FREQUENCY_NAME = "name";
    private static final String OWNER_UUID = "ownerUUID";
    private static final String SECURITY_MODE = "securityMode";
    private static final String BINDING_PLAYER_UUID = "bindingPlayerUUID";

    private final UUID frequencyUUID;
    private final String frequencyName;
    @Nullable
    private final UUID ownerUUID;
    private final SecurityMode securityMode;
    @Nullable
    private final UUID bindingPlayerUUID;

    public QIOFrequencyReference(@Nonnull UUID frequencyUUID, @Nonnull String frequencyName,
          @Nullable UUID ownerUUID, @Nonnull SecurityMode securityMode,
          @Nullable UUID bindingPlayerUUID) {
        this.frequencyUUID = Objects.requireNonNull(frequencyUUID, "frequencyUUID");
        this.frequencyName = Objects.requireNonNull(frequencyName, "frequencyName");
        this.ownerUUID = ownerUUID;
        this.securityMode = Objects.requireNonNull(securityMode, "securityMode");
        this.bindingPlayerUUID = bindingPlayerUUID;
    }

    @Nonnull
    public UUID getFrequencyUUID() {
        return frequencyUUID;
    }

    @Nonnull
    public String getFrequencyName() {
        return frequencyName;
    }

    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    @Nonnull
    public SecurityMode getSecurityMode() {
        return securityMode;
    }

    @Nullable
    public UUID getBindingPlayerUUID() {
        return bindingPlayerUUID;
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString(FREQUENCY_UUID, frequencyUUID.toString());
        NBTTagCompound identity = new NBTTagCompound();
        identity.setString(FREQUENCY_NAME, frequencyName);
        if (ownerUUID != null) {
            identity.setString(OWNER_UUID, ownerUUID.toString());
        }
        identity.setString(SECURITY_MODE, securityMode.name());
        data.setTag(LAST_KNOWN_IDENTITY, identity);
        if (bindingPlayerUUID != null) {
            data.setString(BINDING_PLAYER_UUID, bindingPlayerUUID.toString());
        }
        return data;
    }

    @Nonnull
    public static QIOFrequencyReference read(@Nonnull NBTTagCompound data) {
        Objects.requireNonNull(data, "data");
        if (!data.hasKey(FREQUENCY_UUID, NBT.TAG_STRING) ||
              !data.hasKey(LAST_KNOWN_IDENTITY, NBT.TAG_COMPOUND)) {
            throw new IllegalArgumentException("QIO frequency reference is missing its identity");
        }
        NBTTagCompound identity = data.getCompoundTag(LAST_KNOWN_IDENTITY);
        if (!identity.hasKey(FREQUENCY_NAME, NBT.TAG_STRING) ||
              !identity.hasKey(SECURITY_MODE, NBT.TAG_STRING)) {
            throw new IllegalArgumentException("QIO frequency reference has an incomplete identity");
        }
        try {
            return new QIOFrequencyReference(UUID.fromString(data.getString(FREQUENCY_UUID)),
                  identity.getString(FREQUENCY_NAME), readOptionalUUID(identity, OWNER_UUID),
                  SecurityMode.valueOf(identity.getString(SECURITY_MODE)),
                  readOptionalUUID(data, BINDING_PLAYER_UUID));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid QIO frequency reference", e);
        }
    }

    @Nullable
    private static UUID readOptionalUUID(NBTTagCompound data, String key) {
        return data.hasKey(key, NBT.TAG_STRING) ? UUID.fromString(data.getString(key)) : null;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof QIOFrequencyReference other)) {
            return false;
        }
        return frequencyUUID.equals(other.frequencyUUID) && frequencyName.equals(other.frequencyName) &&
              Objects.equals(ownerUUID, other.ownerUUID) && securityMode == other.securityMode &&
              Objects.equals(bindingPlayerUUID, other.bindingPlayerUUID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(frequencyUUID, frequencyName, ownerUUID, securityMode, bindingPlayerUUID);
    }
}
