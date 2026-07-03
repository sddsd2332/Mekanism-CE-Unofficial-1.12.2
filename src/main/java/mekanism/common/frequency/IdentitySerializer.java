package mekanism.common.frequency;

import io.netty.buffer.ByteBuf;
import mekanism.api.NBTConstants;
import mekanism.common.PacketHandler;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.util.MekanismUtils;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.util.UUID;

public interface IdentitySerializer {

    IdentitySerializer NAME = new IdentitySerializer() {
        @Nullable
        @Override
        public FrequencyIdentity read(NBTTagCompound data) {
            String name = data.getString(NBTConstants.NAME);
            if (name.isEmpty()) {
                return null;
            }
            UUID ownerUUID = data.hasKey(NBTConstants.OWNER_UUID) ? MekanismUtils.parseUUID(data.getString(NBTConstants.OWNER_UUID)) : null;
            return new FrequencyIdentity(name, SecurityMode.byIndexStatic(data.getInteger(NBTConstants.SECURITY_MODE)), ownerUUID);
        }

        @Override
        public NBTTagCompound write(FrequencyIdentity identity) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString(NBTConstants.NAME, identity.key() == null ? "" : identity.key().toString());
            tag.setInteger(NBTConstants.SECURITY_MODE, identity.securityMode().ordinal());
            if (identity.ownerUUID() != null) {
                tag.setString(NBTConstants.OWNER_UUID, identity.ownerUUID().toString());
            }
            return tag;
        }

        @Nullable
        @Override
        public FrequencyIdentity read(ByteBuf buffer) {
            String key = PacketHandler.readString(buffer);
            if (key.isEmpty()) {
                return null;
            }
            SecurityMode securityMode = SecurityMode.byIndexStatic(buffer.readInt());
            UUID ownerUUID = buffer.readBoolean() ? MekanismUtils.parseUUID(PacketHandler.readString(buffer)) : null;
            return new FrequencyIdentity(key, securityMode, ownerUUID);
        }

        @Override
        public void write(ByteBuf buffer, FrequencyIdentity identity) {
            PacketHandler.writeString(buffer, identity.key() == null ? "" : identity.key().toString());
            buffer.writeInt(identity.securityMode().ordinal());
            buffer.writeBoolean(identity.ownerUUID() != null);
            if (identity.ownerUUID() != null) {
                PacketHandler.writeString(buffer, identity.ownerUUID().toString());
            }
        }
    };

    IdentitySerializer UUID = new IdentitySerializer() {
        @Nullable
        @Override
        public FrequencyIdentity read(NBTTagCompound data) {
            if (!data.hasKey(NBTConstants.OWNER_UUID)) {
                return null;
            }
            UUID ownerUUID = MekanismUtils.parseUUID(data.getString(NBTConstants.OWNER_UUID));
            return ownerUUID == null ? null : new FrequencyIdentity(ownerUUID, SecurityMode.byIndexStatic(data.getInteger(NBTConstants.SECURITY_MODE)), ownerUUID);
        }

        @Override
        public NBTTagCompound write(FrequencyIdentity identity) {
            NBTTagCompound tag = new NBTTagCompound();
            if (identity.key() instanceof UUID uuid) {
                tag.setString(NBTConstants.OWNER_UUID, uuid.toString());
            } else if (identity.ownerUUID() != null) {
                tag.setString(NBTConstants.OWNER_UUID, identity.ownerUUID().toString());
            }
            tag.setInteger(NBTConstants.SECURITY_MODE, identity.securityMode().ordinal());
            return tag;
        }

        @Nullable
        @Override
        public FrequencyIdentity read(ByteBuf buffer) {
            UUID ownerUUID = MekanismUtils.parseUUID(PacketHandler.readString(buffer));
            SecurityMode securityMode = SecurityMode.byIndexStatic(buffer.readInt());
            return ownerUUID == null ? null : new FrequencyIdentity(ownerUUID, securityMode, ownerUUID);
        }

        @Override
        public void write(ByteBuf buffer, FrequencyIdentity identity) {
            Object key = identity.key();
            UUID ownerUUID = key instanceof UUID uuid ? uuid : identity.ownerUUID();
            PacketHandler.writeString(buffer, ownerUUID == null ? "" : ownerUUID.toString());
            buffer.writeInt(identity.securityMode().ordinal());
        }
    };

    @Nullable
    FrequencyIdentity read(NBTTagCompound data);

    NBTTagCompound write(FrequencyIdentity identity);

    @Nullable
    FrequencyIdentity read(ByteBuf buffer);

    void write(ByteBuf buffer, FrequencyIdentity identity);
}
