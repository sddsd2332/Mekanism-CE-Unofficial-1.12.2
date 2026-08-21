package mekanism.qioprocessing.common.content.job;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.content.plan.QIOCraftPlan;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Durable old-plan drain and new-claim intent for one plan revision change. */
/**
 * QIO 处理模块中的 QIOPlanRevisionTransition 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOPlanRevisionTransition {
    public enum ClaimMode { CREATE, REASSIGN, RELEASE }
    private static final int MAX_RESOURCES = 65_536;
    private final QIOCraftPlan pendingPlan;
    private final UUID targetClaimId;
    @Nullable private ClaimMode claimMode;
    @Nullable private UUID requestId;
    @Nullable private UUID sourceClaimId;
    private long expectedContentsRevision = -1;
    private long expectedClaimRevision = -1;
    private Map<PortableResourceDescriptor, Long> targetClaimAmounts = Collections.emptyMap();

    public QIOPlanRevisionTransition(@Nonnull QIOCraftPlan pendingPlan) {
        this(pendingPlan, UUID.randomUUID(), null, null, null, -1, -1,
              Collections.emptyMap());
    }

    private QIOPlanRevisionTransition(QIOCraftPlan pendingPlan, UUID targetClaimId,
          @Nullable ClaimMode claimMode, @Nullable UUID requestId,
          @Nullable UUID sourceClaimId, long expectedContentsRevision,
          long expectedClaimRevision, Map<PortableResourceDescriptor, Long> targetClaimAmounts) {
        this.pendingPlan = Objects.requireNonNull(pendingPlan, "pendingPlan");
        this.targetClaimId = Objects.requireNonNull(targetClaimId, "targetClaimId");
        this.claimMode = claimMode;
        this.requestId = requestId;
        this.sourceClaimId = sourceClaimId;
        this.expectedContentsRevision = expectedContentsRevision;
        this.expectedClaimRevision = expectedClaimRevision;
        this.targetClaimAmounts = QIOProcessingNbt.copyAmounts(targetClaimAmounts, true,
              "replan target claim amounts");
        validateIntent();
    }

    @Nonnull public QIOCraftPlan getPendingPlan() { return pendingPlan; }
    @Nonnull public UUID getTargetClaimId() { return targetClaimId; }
    @Nullable public ClaimMode getClaimMode() { return claimMode; }
    @Nullable public UUID getRequestId() { return requestId; }
    @Nullable public UUID getSourceClaimId() { return sourceClaimId; }
    public long getExpectedContentsRevision() { return expectedContentsRevision; }
    public long getExpectedClaimRevision() { return expectedClaimRevision; }
    @Nonnull public Map<PortableResourceDescriptor, Long> getTargetClaimAmounts() { return targetClaimAmounts; }
    public boolean hasClaimIntent() { return claimMode != null; }

    public void prepareClaim(@Nonnull ClaimMode mode, @Nonnull UUID requestId,
          @Nullable UUID sourceClaimId, long expectedContentsRevision,
          long expectedClaimRevision,
          @Nonnull Map<PortableResourceDescriptor, Long> targetClaimAmounts) {
        if (hasClaimIntent()) throw new IllegalStateException("A replan claim intent is already prepared");
        this.claimMode = Objects.requireNonNull(mode, "mode");
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.sourceClaimId = sourceClaimId;
        this.expectedContentsRevision = QIOProcessingNbt.requireNonNegative(
              expectedContentsRevision, "expectedContentsRevision");
        this.expectedClaimRevision = QIOProcessingNbt.requireNonNegative(
              expectedClaimRevision, "expectedClaimRevision");
        this.targetClaimAmounts = QIOProcessingNbt.copyAmounts(targetClaimAmounts, true,
              "replan target claim amounts");
        validateIntent();
    }

    public void clearClaimIntent() {
        claimMode = null;
        requestId = null;
        sourceClaimId = null;
        expectedContentsRevision = -1;
        expectedClaimRevision = -1;
        targetClaimAmounts = Collections.emptyMap();
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setTag("pendingPlan", pendingPlan.write());
        QIOProcessingNbt.writeUUID(data, "targetClaimId", targetClaimId);
        if (claimMode != null) {
            data.setString("claimMode", claimMode.name());
            QIOProcessingNbt.writeUUID(data, "requestId", requestId);
            if (sourceClaimId != null) QIOProcessingNbt.writeUUID(data, "sourceClaimId", sourceClaimId);
            data.setLong("expectedContentsRevision", expectedContentsRevision);
            data.setLong("expectedClaimRevision", expectedClaimRevision);
            data.setTag("targetClaimAmounts", QIOProcessingNbt.writeAmounts(targetClaimAmounts));
        }
        return data;
    }

    @Nonnull
    public static QIOPlanRevisionTransition read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            ClaimMode mode = data.hasKey("claimMode", NBT.TAG_STRING) ?
                  QIOProcessingNbt.readEnum(data, "claimMode", ClaimMode.class) : null;
            return new QIOPlanRevisionTransition(
                  QIOCraftPlan.read(data.getCompoundTag("pendingPlan")),
                  QIOProcessingNbt.readUUID(data, "targetClaimId"), mode,
                  mode == null ? null : QIOProcessingNbt.readUUID(data, "requestId"),
                  data.hasKey("sourceClaimId", NBT.TAG_STRING) ?
                        QIOProcessingNbt.readUUID(data, "sourceClaimId") : null,
                  mode == null ? -1 : data.getLong("expectedContentsRevision"),
                  mode == null ? -1 : data.getLong("expectedClaimRevision"),
                  mode == null ? Collections.emptyMap() :
                        QIOProcessingNbt.readAmounts(data, "targetClaimAmounts", MAX_RESOURCES));
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO plan revision transition", e);
        }
    }

    private void validateIntent() {
        if (claimMode == null) {
            if (requestId != null || sourceClaimId != null || expectedContentsRevision != -1 ||
                  expectedClaimRevision != -1 || !targetClaimAmounts.isEmpty()) {
                throw new IllegalArgumentException("Unprepared replan transition contains claim intent data");
            }
            return;
        }
        if (requestId == null || expectedContentsRevision < 0 || expectedClaimRevision < 0) {
            throw new IllegalArgumentException("Prepared replan transition has an incomplete claim intent");
        }
        if (claimMode == ClaimMode.REASSIGN && sourceClaimId == null ||
              claimMode != ClaimMode.REASSIGN && sourceClaimId != null) {
            throw new IllegalArgumentException("Replan claim source does not match its mode");
        }
        if (claimMode != ClaimMode.RELEASE && targetClaimAmounts.isEmpty()) {
            throw new IllegalArgumentException("Replan claim target cannot be empty");
        }
    }
}
