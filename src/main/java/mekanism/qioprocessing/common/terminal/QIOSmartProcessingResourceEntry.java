package mekanism.qioprocessing.common.terminal;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** Server-authoritative resource row used by the smart-processing terminal. */
/**
 * QIO 处理模块中的 QIOSmartProcessingResourceEntry 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOSmartProcessingResourceEntry {

    private final PortableResourceDescriptor resource;
    private final long stored;
    private final long committed;
    private final long taskReserved;
    private final long inProduction;
    private final boolean schedulable;
    private final boolean passive;
    private final long mergeableInProduction;
    @Nullable
    private final UUID mergeGroupId;

    public QIOSmartProcessingResourceEntry(@Nonnull PortableResourceDescriptor resource,
          long stored, long committed, long taskReserved, long inProduction,
          boolean schedulable, boolean passive) {
        this(resource, stored, committed, taskReserved, inProduction, schedulable, passive,
              0, null);
    }

    public QIOSmartProcessingResourceEntry(@Nonnull PortableResourceDescriptor resource,
          long stored, long committed, long taskReserved, long inProduction,
          boolean schedulable, boolean passive, long mergeableInProduction,
          @Nullable UUID mergeGroupId) {
        this.resource = Objects.requireNonNull(resource, "resource");
        if (stored < 0 || committed < 0 || taskReserved < 0 || inProduction < 0 ||
              mergeableInProduction < 0) {
            throw new IllegalArgumentException("Smart-processing resource amounts cannot be negative");
        }
        if ((mergeableInProduction > 0) != (mergeGroupId != null) ||
              mergeableInProduction > inProduction) {
            throw new IllegalArgumentException("Invalid smart-processing merge projection");
        }
        this.stored = stored;
        this.committed = committed;
        this.taskReserved = taskReserved;
        this.inProduction = inProduction;
        this.schedulable = schedulable;
        this.passive = passive;
        this.mergeableInProduction = mergeableInProduction;
        this.mergeGroupId = mergeGroupId;
    }

    @Nonnull
    public PortableResourceDescriptor getResource() {
        return resource;
    }

    public long getStored() {
        return stored;
    }

    public long getCommitted() {
        return committed;
    }

    public long getAvailable() {
        return Math.max(0, stored - committed);
    }

    public long getTaskReserved() {
        return taskReserved;
    }

    public long getInProduction() {
        return inProduction;
    }

    public boolean isSchedulable() {
        return schedulable;
    }

    public boolean isPassive() {
        return passive;
    }

    public long getMergeableInProduction() {
        return mergeableInProduction;
    }

    public boolean isMergeable() {
        return mergeGroupId != null;
    }

    @Nullable
    public UUID getMergeGroupId() {
        return mergeGroupId;
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setTag("resource", resource.write());
        data.setLong("stored", stored);
        data.setLong("committed", committed);
        data.setLong("taskReserved", taskReserved);
        data.setLong("inProduction", inProduction);
        data.setBoolean("schedulable", schedulable);
        data.setBoolean("passive", passive);
        data.setLong("mergeableInProduction", mergeableInProduction);
        if (mergeGroupId != null) {
            QIOProcessingNbt.writeUUID(data, "mergeGroupId", mergeGroupId);
        }
        return data;
    }

    @Nonnull
    public static QIOSmartProcessingResourceEntry read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            return new QIOSmartProcessingResourceEntry(
                  PortableResourceDescriptor.read(data.getCompoundTag("resource")),
                  data.getLong("stored"), data.getLong("committed"),
                  data.getLong("taskReserved"), data.getLong("inProduction"),
                  data.getBoolean("schedulable"), data.getBoolean("passive"),
                  data.getLong("mergeableInProduction"),
                  QIOProcessingNbt.readOptionalUUID(data, "mergeGroupId"));
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid smart-processing resource entry", e);
        }
    }
}
