package mekanism.qioprocessing.common.content.plan;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;

/** Revisions captured by an immutable planning input snapshot. */
/**
 * QIO 处理模块中的 QIOPlanSourceRevisions 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOPlanSourceRevisions {

    private final long contentRevision;
    private final long capacityRevision;
    private final long claimRevision;
    private final long accessRevision;
    private final long recipeCatalogRevision;
    private final long providerCatalogRevision;
    private final long policyRevision;
    private final long taskCommitmentRevision;

    public QIOPlanSourceRevisions(long contentRevision, long capacityRevision, long claimRevision,
          long accessRevision, long recipeCatalogRevision, long providerCatalogRevision,
          long policyRevision, long taskCommitmentRevision) {
        this.contentRevision = QIOProcessingNbt.requireNonNegative(contentRevision, "contentRevision");
        this.capacityRevision = QIOProcessingNbt.requireNonNegative(capacityRevision, "capacityRevision");
        this.claimRevision = QIOProcessingNbt.requireNonNegative(claimRevision, "claimRevision");
        this.accessRevision = QIOProcessingNbt.requireNonNegative(accessRevision, "accessRevision");
        this.recipeCatalogRevision = QIOProcessingNbt.requireNonNegative(recipeCatalogRevision,
              "recipeCatalogRevision");
        this.providerCatalogRevision = QIOProcessingNbt.requireNonNegative(providerCatalogRevision,
              "providerCatalogRevision");
        this.policyRevision = QIOProcessingNbt.requireNonNegative(policyRevision, "policyRevision");
        this.taskCommitmentRevision = QIOProcessingNbt.requireNonNegative(taskCommitmentRevision,
              "taskCommitmentRevision");
    }

    public long getContentRevision() {
        return contentRevision;
    }

    public long getCapacityRevision() {
        return capacityRevision;
    }

    public long getClaimRevision() {
        return claimRevision;
    }

    public long getAccessRevision() {
        return accessRevision;
    }

    public long getRecipeCatalogRevision() {
        return recipeCatalogRevision;
    }

    public long getProviderCatalogRevision() {
        return providerCatalogRevision;
    }

    public long getPolicyRevision() {
        return policyRevision;
    }

    public long getTaskCommitmentRevision() {
        return taskCommitmentRevision;
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setLong("content", contentRevision);
        data.setLong("capacity", capacityRevision);
        data.setLong("claim", claimRevision);
        data.setLong("access", accessRevision);
        data.setLong("recipeCatalog", recipeCatalogRevision);
        data.setLong("providerCatalog", providerCatalogRevision);
        data.setLong("policy", policyRevision);
        data.setLong("taskCommitment", taskCommitmentRevision);
        return data;
    }

    @Nonnull
    public static QIOPlanSourceRevisions read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            if (!data.hasKey("content", NBT.TAG_LONG) ||
                  !data.hasKey("capacity", NBT.TAG_LONG) ||
                  !data.hasKey("claim", NBT.TAG_LONG) ||
                  !data.hasKey("access", NBT.TAG_LONG) ||
                  !data.hasKey("recipeCatalog", NBT.TAG_LONG) ||
                  !data.hasKey("providerCatalog", NBT.TAG_LONG) ||
                  !data.hasKey("policy", NBT.TAG_LONG) ||
                  !data.hasKey("taskCommitment", NBT.TAG_LONG)) {
                throw new QIOProcessingDataException(
                      "QIO plan source revisions are missing current-schema fields");
            }
            return new QIOPlanSourceRevisions(data.getLong("content"), data.getLong("capacity"),
                  data.getLong("claim"), data.getLong("access"), data.getLong("recipeCatalog"),
                  data.getLong("providerCatalog"), data.getLong("policy"),
                  data.getLong("taskCommitment"));
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO plan source revisions", e);
        }
    }
}
