package mekanism.qioprocessing.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.TileNetworkList;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.tile.qio.TileEntityQIOComponent;
import mekanism.qioprocessing.common.terminal.QIOProcessingFrequencyAccess;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalDeviceRegistry;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** Shared exact-frequency state for the four QIO Processing terminal blocks. */
public abstract class QIOProcessingTerminal extends TileEntityQIOComponent {

    private static final int DATA_SCHEMA = 1;
    private static final int MAX_DIAGNOSTIC_LENGTH = 512;

    private final QIOProcessingTerminalType terminalType;
    private UUID persistentTerminalUUID = UUID.randomUUID();
    private long configurationRevision;
    @Nullable
    private QIOFrequencyReference frequencyReference;
    @Nullable
    private String dataError;
    private boolean identityConflict;

    protected QIOProcessingTerminal(@Nonnull String name,
          @Nonnull QIOProcessingTerminalType terminalType) {
        super(name);
        this.terminalType = Objects.requireNonNull(terminalType, "terminalType");
    }

    @Nonnull
    public final QIOProcessingTerminalType getTerminalType() {
        return terminalType;
    }

    @Nonnull
    public final UUID getPersistentTerminalUUID() {
        return persistentTerminalUUID;
    }

    public final long getConfigurationRevision() {
        return configurationRevision;
    }

    @Nullable
    public final QIOFrequencyReference getFrequencyReference() {
        return frequencyReference;
    }

    @Nullable
    public final String getDataError() {
        return dataError;
    }

    public final boolean hasDataError() {
        return dataError != null;
    }

    public final boolean hasIdentityConflict() {
        return identityConflict;
    }

    public final void setIdentityConflict(boolean identityConflict) {
        this.identityConflict = identityConflict;
    }

    @Override
    @Nullable
    public final QIOFrequency getQIOFrequency() {
        return hasDataError() || identityConflict ? null : QIOProcessingFrequencyAccess.resolve(
              frequencyReference);
    }

    /** Generic name-only frequency packets are not authoritative for processing terminals. */
    @Override
    public final void setFrequency(FrequencyType<?> type, FrequencyIdentity data,
          UUID player) {
    }

    /** Generic removal packets are likewise rejected for the exact QIO binding. */
    @Override
    public final void removeFrequency(FrequencyType<?> type, FrequencyIdentity data,
          UUID player) {
    }

    public final boolean applyAuthorizedBinding(@Nullable QIOFrequency frequency,
          @Nonnull UUID bindingPlayerUUID) {
        Objects.requireNonNull(bindingPlayerUUID, "bindingPlayerUUID");
        if (hasDataError() || identityConflict) {
            return false;
        }
        QIOFrequencyReference updatedReference = frequency == null ? null :
              QIOFrequencyStorageAccess.INSTANCE.createReference(frequency,
                    bindingPlayerUUID);
        if (Objects.equals(frequencyReference, updatedReference)) {
            return false;
        }
        if (frequency == null) {
            getFrequencyComponent().unsetFrequency(FrequencyType.QIO);
        } else {
            getFrequencyComponent().setFrequencyFromData(FrequencyType.QIO,
                  frequency.getIdentity(), bindingPlayerUUID);
        }
        frequencyReference = updatedReference;
        markConfigurationChanged();
        return true;
    }

    /** Restores the exact sustained reference without resolving or creating a name-only frequency. */
    public final boolean restoreExactBinding(@Nonnull UUID bindingPlayerUUID) {
        Objects.requireNonNull(bindingPlayerUUID, "bindingPlayerUUID");
        if (frequencyReference == null || hasDataError() || identityConflict) {
            return false;
        }
        QIOFrequency frequency = QIOProcessingFrequencyAccess.resolveAccessible(
              frequencyReference, bindingPlayerUUID);
        if (frequency == null) {
            getFrequencyComponent().unsetFrequency(FrequencyType.QIO);
            return false;
        }
        getFrequencyComponent().setFrequencyFromData(FrequencyType.QIO,
              frequency.getIdentity(), bindingPlayerUUID);
        return true;
    }

    protected final void markConfigurationChanged() {
        incrementConfigurationRevision();
        if (world != null && !world.isRemote) {
            QIOProcessingTerminalDeviceRegistry.INSTANCE.refresh(this);
            // The exact frequency reference is part of the bounded tile update payload. A
            // container property update alone cannot refresh the frequency selector's tile view.
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (world != null && !world.isRemote) {
            QIOProcessingTerminalDeviceRegistry.INSTANCE.register(this);
        }
    }

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        if ((world.getTotalWorldTime() + getPos().toLong()) % 16 == 0) {
            QIOProcessingTerminalDeviceRegistry.INSTANCE.refresh(this);
        }
    }

    @Override
    public void onChunkUnload() {
        if (world != null && !world.isRemote) {
            QIOProcessingTerminalDeviceRegistry.INSTANCE.unregister(this);
        }
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (world != null && !world.isRemote) {
            QIOProcessingTerminalDeviceRegistry.INSTANCE.unregisterRemoved(this);
        }
    }

    @Override
    public void writeCustomNBT(NBTTagCompound data) {
        super.writeCustomNBT(data);
        writeTerminalData(data);
    }

    @Override
    public void readCustomNBT(NBTTagCompound data) {
        super.readCustomNBT(data);
        readTerminalData(data);
    }

    @Override
    public void writeSustainedQIOData(NBTTagCompound data) {
        super.writeSustainedQIOData(data);
        writeTerminalData(data);
    }

    @Override
    public void readSustainedQIOData(NBTTagCompound data) {
        super.readSustainedQIOData(data);
        readTerminalData(data);
    }

    @Override
    protected void writeUpdateNBT(NBTTagCompound data) {
        writeQIOVisualUpdateNBT(data);
        writeClientSyncData(data);
    }

    @Override
    protected void readUpdateNBT(NBTTagCompound data) {
        readQIOVisualUpdateNBT(data);
        readClientSyncData(data);
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (world != null && world.isRemote) {
            readClientSyncData(PacketHandler.readNBT(dataStream));
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        NBTTagCompound terminalData = new NBTTagCompound();
        writeClientSyncData(terminalData);
        data.add(terminalData);
        return data;
    }

    private void writeClientSyncData(NBTTagCompound data) {
        data.setLong("qioProcessingTerminalConfigRevision", configurationRevision);
        writeFrequencyReference(data);
    }

    private void readClientSyncData(NBTTagCompound data) {
        configurationRevision = Math.max(0,
              data.getLong("qioProcessingTerminalConfigRevision"));
        try {
            frequencyReference = data.hasKey("qioProcessingFrequencyReference",
                  NBT.TAG_COMPOUND) ? QIOFrequencyReference.read(data.getCompoundTag(
                  "qioProcessingFrequencyReference")) : null;
        } catch (RuntimeException ignored) {
            frequencyReference = null;
        }
    }

    private void writeFrequencyReference(NBTTagCompound data) {
        if (frequencyReference == null) {
            data.removeTag("qioProcessingFrequencyReference");
        } else {
            data.setTag("qioProcessingFrequencyReference", frequencyReference.write());
        }
    }

    private void writeTerminalData(NBTTagCompound data) {
        data.setInteger("qioProcessingTerminalSchema", DATA_SCHEMA);
        data.setString("qioProcessingTerminalUUID", persistentTerminalUUID.toString());
        data.setLong("qioProcessingTerminalConfigRevision", configurationRevision);
        if (frequencyReference == null) {
            data.removeTag("qioProcessingFrequencyReference");
        } else {
            data.setTag("qioProcessingFrequencyReference", frequencyReference.write());
        }
        if (dataError == null) {
            data.removeTag("qioProcessingTerminalDataError");
        } else {
            data.setString("qioProcessingTerminalDataError", dataError);
        }
    }

    private void readTerminalData(NBTTagCompound data) {
        if (!data.hasKey("qioProcessingTerminalSchema")) {
            return;
        }
        try {
            if (data.getInteger("qioProcessingTerminalSchema") != DATA_SCHEMA) {
                throw new IllegalArgumentException("Unsupported terminal schema " +
                      data.getInteger("qioProcessingTerminalSchema"));
            }
            String storedUUID = data.getString("qioProcessingTerminalUUID");
            UUID parsedUUID = UUID.fromString(storedUUID);
            if (!parsedUUID.toString().equals(storedUUID)) {
                throw new IllegalArgumentException("Non-canonical terminal UUID");
            }
            long parsedRevision = data.getLong("qioProcessingTerminalConfigRevision");
            if (parsedRevision < 0) {
                throw new IllegalArgumentException("Negative terminal configuration revision");
            }
            QIOFrequencyReference parsedReference =
                  data.hasKey("qioProcessingFrequencyReference", NBT.TAG_COMPOUND) ?
                        QIOFrequencyReference.read(data.getCompoundTag(
                              "qioProcessingFrequencyReference")) : null;
            persistentTerminalUUID = parsedUUID;
            configurationRevision = parsedRevision;
            frequencyReference = parsedReference;
            dataError = data.hasKey("qioProcessingTerminalDataError", NBT.TAG_STRING) ?
                  boundedDiagnostic(data.getString("qioProcessingTerminalDataError")) : null;
        } catch (RuntimeException e) {
            frequencyReference = null;
            dataError = boundedDiagnostic(e.getMessage() == null ?
                  e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private void incrementConfigurationRevision() {
        if (configurationRevision == Long.MAX_VALUE) {
            dataError = "QIO processing terminal configuration revision exhausted";
        } else {
            configurationRevision++;
        }
        markNoUpdateSync();
    }

    private static String boundedDiagnostic(String diagnostic) {
        String checked = diagnostic == null || diagnostic.trim().isEmpty() ?
              "unknown QIO processing terminal data error" : diagnostic.trim();
        return checked.substring(0, Math.min(MAX_DIAGNOSTIC_LENGTH,
              checked.length()));
    }
}
