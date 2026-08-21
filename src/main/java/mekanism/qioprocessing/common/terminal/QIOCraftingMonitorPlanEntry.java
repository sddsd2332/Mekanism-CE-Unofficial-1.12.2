package mekanism.qioprocessing.common.terminal;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.content.plan.QIOCyclePlanNode;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/** One bounded row from an immutable craft plan's flat step/cycle table. */
/**
 * QIO 处理模块中的 QIOCraftingMonitorPlanEntry 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOCraftingMonitorPlanEntry {

    public enum Kind {
        STEP,
        CYCLE
    }

    private final Kind kind;
    @Nullable
    private final QIOPlanStep step;
    @Nullable
    private final QIOCyclePlanNode cycle;

    private QIOCraftingMonitorPlanEntry(Kind kind, @Nullable QIOPlanStep step,
          @Nullable QIOCyclePlanNode cycle) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.step = step;
        this.cycle = cycle;
        if ((kind == Kind.STEP) != (step != null) || (kind == Kind.CYCLE) != (cycle != null)) {
            throw new IllegalArgumentException("QIO monitor plan entry has an invalid shape");
        }
    }

    @Nonnull
    public static QIOCraftingMonitorPlanEntry step(@Nonnull QIOPlanStep step) {
        return new QIOCraftingMonitorPlanEntry(Kind.STEP,
              Objects.requireNonNull(step, "step"), null);
    }

    @Nonnull
    public static QIOCraftingMonitorPlanEntry cycle(@Nonnull QIOCyclePlanNode cycle) {
        return new QIOCraftingMonitorPlanEntry(Kind.CYCLE, null,
              Objects.requireNonNull(cycle, "cycle"));
    }

    @Nonnull
    public Kind getKind() {
        return kind;
    }

    public long getNodeId() {
        return kind == Kind.STEP ? step.getNodeId() : cycle.getNodeId();
    }

    @Nullable
    public QIOPlanStep getStep() {
        return step;
    }

    @Nullable
    public QIOCyclePlanNode getCycle() {
        return cycle;
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("kind", kind.name());
        data.setTag(kind == Kind.STEP ? "step" : "cycle",
              kind == Kind.STEP ? step.write() : cycle.write());
        return data;
    }

    @Nonnull
    public static QIOCraftingMonitorPlanEntry read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            Kind kind = QIOProcessingNbt.readEnum(data, "kind", Kind.class);
            if (kind == Kind.STEP && data.hasKey("step", NBT.TAG_COMPOUND)) {
                return step(QIOPlanStep.read(data.getCompoundTag("step")));
            }
            if (kind == Kind.CYCLE && data.hasKey("cycle", NBT.TAG_COMPOUND)) {
                return cycle(QIOCyclePlanNode.read(data.getCompoundTag("cycle")));
            }
            throw new QIOProcessingDataException("QIO monitor plan entry is missing its payload");
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO monitor plan entry", e);
        }
    }
}
