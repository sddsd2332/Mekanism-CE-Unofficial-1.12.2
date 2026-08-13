package mekanism.qioprocessing.common.content.scheduling;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.content.job.QIOCraftingJobSource;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

public final class ActiveExecutionSlot {

    private final UUID slotToken;
    private final UUID ownerJobId;
    private final long acquiredAtSchedulerClock;
    private final QIOCraftingJobSource source;

    public ActiveExecutionSlot(@Nonnull UUID slotToken, @Nonnull UUID ownerJobId,
          long acquiredAtSchedulerClock, @Nonnull QIOCraftingJobSource source) {
        this.slotToken = Objects.requireNonNull(slotToken, "slotToken");
        this.ownerJobId = Objects.requireNonNull(ownerJobId, "ownerJobId");
        this.acquiredAtSchedulerClock = QIOProcessingNbt.requireNonNegative(acquiredAtSchedulerClock,
              "acquiredAtSchedulerClock");
        this.source = Objects.requireNonNull(source, "source");
    }

    @Nonnull
    public UUID getSlotToken() {
        return slotToken;
    }

    @Nonnull
    public UUID getOwnerJobId() {
        return ownerJobId;
    }

    public long getAcquiredAtSchedulerClock() {
        return acquiredAtSchedulerClock;
    }

    @Nonnull
    public QIOCraftingJobSource getSource() {
        return source;
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        QIOProcessingNbt.writeUUID(data, "slotToken", slotToken);
        QIOProcessingNbt.writeUUID(data, "ownerJobId", ownerJobId);
        data.setLong("acquiredAtSchedulerClock", acquiredAtSchedulerClock);
        data.setString("source", source.name());
        return data;
    }

    @Nonnull
    public static ActiveExecutionSlot read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            return new ActiveExecutionSlot(QIOProcessingNbt.readUUID(data, "slotToken"),
                  QIOProcessingNbt.readUUID(data, "ownerJobId"),
                  data.getLong("acquiredAtSchedulerClock"),
                  QIOProcessingNbt.readEnum(data, "source", QIOCraftingJobSource.class));
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO execution slot", e);
        }
    }
}
