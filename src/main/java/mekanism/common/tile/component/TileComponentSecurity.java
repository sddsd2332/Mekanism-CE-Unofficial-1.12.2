package mekanism.common.tile.component;

import io.netty.buffer.ByteBuf;
import mekanism.api.TileNetworkList;
import mekanism.common.PacketHandler;
import mekanism.common.base.ITileComponent;
import mekanism.common.config.MekanismConfig;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.security.SecurityFrequency;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.MekanismUtils;
import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

public class TileComponentSecurity implements ITileComponent {

    /**
     * TileEntity implementing this component.
     */
    public TileEntityContainerBlock tileEntity;

    private UUID ownerUUID;
    /**
     * Monotonic identity revision for consumers that cache the owner lookup.
     */
    private long ownerRevision;
    private String clientOwner;

    private SecurityMode securityMode = SecurityMode.PUBLIC;

    public TileComponentSecurity(TileEntityContainerBlock tile) {
        tileEntity = tile;
        tile.addComponent(this);
        tileEntity.getFrequencyComponent().track(FrequencyType.SECURITY, true, false, true);
    }

    public void readFrom(TileComponentSecurity security) {
        ownerUUID = security.ownerUUID;
        ownerRevision++;
        securityMode = security.securityMode;
        tileEntity.getFrequencyComponent().requestSecurityRestore(this);
    }

    public SecurityFrequency getFrequency() {
        return tileEntity.getFrequencyComponent().getFrequency(FrequencyType.SECURITY);
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public long getOwnerRevision() {
        return ownerRevision;
    }

    public void setOwnerUUID(UUID uuid) {
        ownerUUID = uuid;
        ownerRevision++;
        tileEntity.getFrequencyComponent().requestSecurityRestore(this);
        if (ownerUUID == null) {
            tileEntity.getFrequencyComponent().unsetFrequency(FrequencyType.SECURITY);
        } else {
            tileEntity.getFrequencyComponent().setFrequency(FrequencyType.SECURITY, new FrequencyIdentity(ownerUUID, SecurityMode.PUBLIC, ownerUUID), ownerUUID);
        }
    }

    public String getClientOwner() {
        return clientOwner;
    }

    public SecurityMode getMode() {
        if (MekanismConfig.current().general.allowProtection.val()) {
            return securityMode;
        }
        return SecurityMode.PUBLIC;
    }

    public void setMode(SecurityMode mode) {
        securityMode = mode;
    }

    /**
     * Restore legacy owner-only saves from the frequency component's server phase.
     */
    public boolean restoreOwnerFrequency() {
        if (getFrequency() == null && ownerUUID != null) {
            tileEntity.getFrequencyComponent().setFrequency(FrequencyType.SECURITY,
                    new FrequencyIdentity(ownerUUID, SecurityMode.PUBLIC, ownerUUID), ownerUUID);
        }
        return ownerUUID == null || getFrequency() != null;
    }

    @Override
    public void read(NBTTagCompound nbtTags) {
        securityMode = MekanismUtils.getByIndex(SecurityMode.values(), nbtTags.getInteger("securityMode"), securityMode);
        if (nbtTags.hasKey("ownerUUID")) {
            ownerUUID = MekanismUtils.parseUUID(nbtTags.getString("ownerUUID"));
        }
        ownerRevision++;
        tileEntity.getFrequencyComponent().requestSecurityRestore(this);
    }

    @Override
    public void read(ByteBuf dataStream) {
        securityMode = MekanismUtils.getByIndex(SecurityMode.values(), dataStream.readInt(), securityMode);

        if (dataStream.readBoolean()) {
            ownerUUID = MekanismUtils.parseUUID(PacketHandler.readString(dataStream));
            clientOwner = PacketHandler.readString(dataStream);
        } else {
            ownerUUID = null;
            clientOwner = null;
        }
        ownerRevision++;

    }

    @Override
    public void write(NBTTagCompound nbtTags) {
        nbtTags.setInteger("securityMode", securityMode.ordinal());
        if (ownerUUID != null) {
            nbtTags.setString("ownerUUID", ownerUUID.toString());
        }
    }

    @Override
    public void write(TileNetworkList data) {
        data.add(securityMode.ordinal());

        if (ownerUUID != null) {
            data.add(true);
            data.add(ownerUUID.toString());
            data.add(MekanismUtils.getLastKnownUsername(ownerUUID));
        } else {
            data.add(false);
        }

    }

    @Override
    public void invalidate() {
    }
}
