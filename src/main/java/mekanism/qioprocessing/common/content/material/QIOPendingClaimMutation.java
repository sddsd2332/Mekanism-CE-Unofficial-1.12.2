package mekanism.qioprocessing.common.content.material;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Durable intent written before a QIO claim mutation is submitted. */
public final class QIOPendingClaimMutation {

    public enum Mode {
        CREATE_OR_ADJUST,
        RELEASE
    }

    private static final int MAX_RESOURCE_ENTRIES = 65_536;

    private final UUID requestId;
    private final Mode mode;
    private final long expectedContentsRevision;
    private final long expectedClaimRevision;
    private final Map<PortableResourceDescriptor, Long> amounts;

    public QIOPendingClaimMutation(@Nonnull UUID requestId, @Nonnull Mode mode,
          long expectedContentsRevision, long expectedClaimRevision,
          @Nonnull Map<PortableResourceDescriptor, Long> amounts) {
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.expectedContentsRevision = QIOProcessingNbt.requireNonNegative(expectedContentsRevision,
              "expectedContentsRevision");
        this.expectedClaimRevision = QIOProcessingNbt.requireNonNegative(expectedClaimRevision,
              "expectedClaimRevision");
        this.amounts = QIOProcessingNbt.copyAmounts(amounts, mode == Mode.RELEASE, "claimMutationAmounts");
    }

    @Nonnull
    public UUID getRequestId() {
        return requestId;
    }

    @Nonnull
    public Mode getMode() {
        return mode;
    }

    public long getExpectedContentsRevision() {
        return expectedContentsRevision;
    }

    public long getExpectedClaimRevision() {
        return expectedClaimRevision;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getAmounts() {
        return amounts;
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        QIOProcessingNbt.writeUUID(data, "requestId", requestId);
        data.setString("mode", mode.name());
        data.setLong("expectedContentsRevision", expectedContentsRevision);
        data.setLong("expectedClaimRevision", expectedClaimRevision);
        data.setTag("amounts", QIOProcessingNbt.writeAmounts(amounts));
        return data;
    }

    @Nonnull
    public static QIOPendingClaimMutation read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            return new QIOPendingClaimMutation(QIOProcessingNbt.readUUID(data, "requestId"),
                  QIOProcessingNbt.readEnum(data, "mode", Mode.class),
                  data.getLong("expectedContentsRevision"), data.getLong("expectedClaimRevision"),
                  QIOProcessingNbt.readAmounts(data, "amounts", MAX_RESOURCE_ENTRIES));
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid pending QIO claim mutation", e);
        }
    }
}
