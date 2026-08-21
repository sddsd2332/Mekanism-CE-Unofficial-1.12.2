package mekanism.qioprocessing.common.content.scheduling;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.content.job.QIOCraftingJobSource;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

/**
 * QIO 处理模块中的 ActiveExecutionSlot 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class ActiveExecutionSlot {

    private final UUID slotToken;
    private final UUID ownerJobId;
    private final long acquiredAtSchedulerClock;
    private final QIOCraftingJobSource source;

    /** 创建一个任务执行槽所有权记录。 */
    public ActiveExecutionSlot(@Nonnull UUID slotToken, @Nonnull UUID ownerJobId,
          long acquiredAtSchedulerClock, @Nonnull QIOCraftingJobSource source) {
        this.slotToken = Objects.requireNonNull(slotToken, "slotToken");
        this.ownerJobId = Objects.requireNonNull(ownerJobId, "ownerJobId");
        this.acquiredAtSchedulerClock = QIOProcessingNbt.requireNonNegative(acquiredAtSchedulerClock,
              "acquiredAtSchedulerClock");
        this.source = Objects.requireNonNull(source, "source");
    }

    /** 返回执行槽 token。 */
    @Nonnull
    public UUID getSlotToken() {
        return slotToken;
    }

    /** 返回持有该槽的任务标识。 */
    @Nonnull
    public UUID getOwnerJobId() {
        return ownerJobId;
    }

    /** 返回取得槽时的调度时钟。 */
    public long getAcquiredAtSchedulerClock() {
        return acquiredAtSchedulerClock;
    }

    /** 返回任务来源。 */
    @Nonnull
    public QIOCraftingJobSource getSource() {
        return source;
    }

    /** 将执行槽记录写入 NBT。 */
    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        QIOProcessingNbt.writeUUID(data, "slotToken", slotToken);
        QIOProcessingNbt.writeUUID(data, "ownerJobId", ownerJobId);
        data.setLong("acquiredAtSchedulerClock", acquiredAtSchedulerClock);
        data.setString("source", source.name());
        return data;
    }

    /** 从 NBT 读取执行槽记录。 */
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
