package mekanism.common.security;

import io.netty.buffer.ByteBuf;
import mekanism.api.TileNetworkList;
import mekanism.common.HashList;
import mekanism.common.PacketHandler;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.util.MekanismUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants.NBT;

import java.util.UUID;

public class SecurityFrequency extends Frequency {

    public static final String SECURITY = "Security";
    private static final String SECURITY_OVERRIDE_MODE = "securityOverrideMode";

    public boolean override;

    public HashList<String> trusted;

    public SecurityMode securityMode;

    public SecurityFrequency(UUID uuid) {
        this(uuid, SecurityMode.PUBLIC);
    }

    public SecurityFrequency(UUID uuid, SecurityMode securityMode) {
        super(FrequencyType.SECURITY, SECURITY, uuid, SecurityMode.PUBLIC);
        trusted = new HashList<>();
        this.securityMode = securityMode == null ? SecurityMode.PUBLIC : securityMode;
    }

    public SecurityFrequency(NBTTagCompound nbtTags) {
        super(FrequencyType.SECURITY, nbtTags);
    }

    public SecurityFrequency(ByteBuf dataStream) {
        super(FrequencyType.SECURITY, dataStream);
    }

    @Override
    public UUID getKey() {
        return getOwner();
    }

    @Override
    public void setSecurityMode(SecurityMode securityMode) {
        if (this.securityMode != securityMode) {
            this.securityMode = securityMode == null ? SecurityMode.PUBLIC : securityMode;
            dirty = true;
        }
    }

    public SecurityMode getSecurityMode() {
        return securityMode;
    }

    @Override
    public FrequencyIdentity getIdentity() {
        return new FrequencyIdentity(getKey(), SecurityMode.PUBLIC, getOwner());
    }

    @Override
    public int getSyncHash() {
        int code = super.getSyncHash();
        code = 31 * code + (override ? 1 : 0);
        code = 31 * code + securityMode.ordinal();
        code = 31 * code + trusted.hashCode();
        return code;
    }

    public void setOverridden(boolean override) {
        if (this.override != override) {
            this.override = override;
            dirty = true;
        }
    }

    public boolean isOverridden() {
        return override;
    }

    public boolean isTrusted(UUID subject) {
        return subject != null && trusted.contains(MekanismUtils.getLastKnownUsername(subject));
    }

    @Override
    public void write(NBTTagCompound nbtTags) {
        super.write(nbtTags);
        nbtTags.setBoolean("override", override);
        nbtTags.setInteger(SECURITY_OVERRIDE_MODE, securityMode.ordinal());

        if (!trusted.isEmpty()) {
            NBTTagList trustedList = new NBTTagList();
            trusted.forEach(s -> trustedList.appendTag(new NBTTagString(s)));
            nbtTags.setTag("trusted", trustedList);
        }
    }

    @Override
    protected void read(NBTTagCompound nbtTags) {
        super.read(nbtTags);

        trusted = new HashList<>();
        securityMode = SecurityMode.PUBLIC;

        override = nbtTags.getBoolean("override");
        securityMode = MekanismUtils.getByIndex(SecurityMode.values(), nbtTags.getInteger(SECURITY_OVERRIDE_MODE), securityMode);

        if (nbtTags.hasKey("trusted")) {
            NBTTagList trustedList = nbtTags.getTagList("trusted", NBT.TAG_STRING);
            for (int i = 0; i < trustedList.tagCount(); i++) {
                trusted.add(trustedList.getStringTagAt(i));
            }
        }
    }

    @Override
    public void write(TileNetworkList data) {
        super.write(data);

        data.add(override);
        data.add(securityMode.ordinal());

        data.add(trusted.size());
        trusted.forEach(data::add);
    }

    @Override
    protected void read(ByteBuf dataStream) {
        super.read(dataStream);

        trusted = new HashList<>();
        securityMode = SecurityMode.PUBLIC;

        override = dataStream.readBoolean();
        securityMode = MekanismUtils.getByIndex(SecurityMode.values(), dataStream.readInt(), securityMode);

        int size = dataStream.readInt();

        for (int i = 0; i < size; i++) {
            trusted.add(PacketHandler.readString(dataStream));
        }
    }
}
