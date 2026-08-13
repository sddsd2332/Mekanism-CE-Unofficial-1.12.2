package mekanism.qioprocessing.common.terminal;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

public final class QIODeviceCommandResult {

    public enum Status {
        ACCEPTED,
        UNCHANGED,
        REVISION_CONFLICT,
        OFFLINE,
        IDENTITY_MISMATCH,
        INVALID_STATE,
        NOT_FOUND
    }

    private final UUID requestId;
    private final UUID deviceUUID;
    private final Status status;
    private final long directoryRevision;
    private final long configurationRevision;
    private final boolean paused;

    public QIODeviceCommandResult(@Nonnull UUID requestId, @Nonnull UUID deviceUUID,
          @Nonnull Status status, long directoryRevision, long configurationRevision,
          boolean paused) {
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.deviceUUID = Objects.requireNonNull(deviceUUID, "deviceUUID");
        this.status = Objects.requireNonNull(status, "status");
        this.directoryRevision = QIOProcessingNbt.requireNonNegative(directoryRevision,
              "directoryRevision");
        this.configurationRevision = QIOProcessingNbt.requireNonNegative(
              configurationRevision, "configurationRevision");
        this.paused = paused;
    }

    @Nonnull public UUID getRequestId() { return requestId; }
    @Nonnull public UUID getDeviceUUID() { return deviceUUID; }
    @Nonnull public Status getStatus() { return status; }
    public long getDirectoryRevision() { return directoryRevision; }
    public long getConfigurationRevision() { return configurationRevision; }
    public boolean isPaused() { return paused; }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        QIOProcessingNbt.writeUUID(data, "requestId", requestId);
        QIOProcessingNbt.writeUUID(data, "deviceUUID", deviceUUID);
        data.setString("status", status.name());
        data.setLong("directoryRevision", directoryRevision);
        data.setLong("configurationRevision", configurationRevision);
        data.setBoolean("paused", paused);
        return data;
    }

    @Nonnull
    public static QIODeviceCommandResult read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            return new QIODeviceCommandResult(QIOProcessingNbt.readUUID(data, "requestId"),
                  QIOProcessingNbt.readUUID(data, "deviceUUID"),
                  QIOProcessingNbt.readEnum(data, "status", Status.class),
                  data.getLong("directoryRevision"), data.getLong("configurationRevision"),
                  data.getBoolean("paused"));
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO device command response", e);
        }
    }
}
