package mekanism.qioprocessing.common.terminal;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

/** Server-issued, revision-bound preview for one destructive workbench import. */
/**
 * QIO 处理模块中的 QIOWorkbenchCopyPreview 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOWorkbenchCopyPreview {

    private static final int SCHEMA_VERSION = 2;

    private final UUID confirmationNonce;
    private final UUID sourceConfigUUID;
    private final UUID sourceOriginUUID;
    private final UUID targetConfigUUID;
    private final long sourceRevision;
    private final long targetRevision;
    private final String sourceDigest;
    private final String sourceFrequencyName;
    private final int productOverrideCount;
    private final int encodedPatternCount;
    private final int ingredientOverrideCount;

    public QIOWorkbenchCopyPreview(UUID confirmationNonce, UUID sourceConfigUUID,
          UUID sourceOriginUUID, UUID targetConfigUUID, long sourceRevision,
          long targetRevision, String sourceDigest, String sourceFrequencyName,
          int productOverrideCount, int encodedPatternCount,
          int ingredientOverrideCount) {
        this.confirmationNonce = Objects.requireNonNull(confirmationNonce,
              "confirmationNonce");
        this.sourceConfigUUID = Objects.requireNonNull(sourceConfigUUID,
              "sourceConfigUUID");
        this.sourceOriginUUID = Objects.requireNonNull(sourceOriginUUID,
              "sourceOriginUUID");
        this.targetConfigUUID = Objects.requireNonNull(targetConfigUUID,
              "targetConfigUUID");
        if (sourceRevision < 0 || targetRevision < 0 || productOverrideCount < 0 ||
            encodedPatternCount < 0 || ingredientOverrideCount < 0) {
            throw new IllegalArgumentException("Invalid workbench copy preview counters");
        }
        this.sourceRevision = sourceRevision;
        this.targetRevision = targetRevision;
        this.sourceDigest = checkedDigest(sourceDigest);
        String checkedName = Objects.requireNonNull(sourceFrequencyName,
              "sourceFrequencyName").trim();
        if (checkedName.length() > 256) {
            throw new IllegalArgumentException("Workbench copy source name is too long");
        }
        this.sourceFrequencyName = checkedName;
        this.productOverrideCount = productOverrideCount;
        this.encodedPatternCount = encodedPatternCount;
        this.ingredientOverrideCount = ingredientOverrideCount;
    }

    @Nonnull public UUID getConfirmationNonce() { return confirmationNonce; }
    @Nonnull public UUID getSourceConfigUUID() { return sourceConfigUUID; }
    @Nonnull public UUID getSourceOriginUUID() { return sourceOriginUUID; }
    @Nonnull public UUID getTargetConfigUUID() { return targetConfigUUID; }
    public long getSourceRevision() { return sourceRevision; }
    public long getTargetRevision() { return targetRevision; }
    @Nonnull public String getSourceDigest() { return sourceDigest; }
    @Nonnull public String getSourceFrequencyName() { return sourceFrequencyName; }
    public int getProductOverrideCount() { return productOverrideCount; }
    public int getEncodedPatternCount() { return encodedPatternCount; }
    public int getIngredientOverrideCount() { return ingredientOverrideCount; }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("schema", SCHEMA_VERSION);
        QIOProcessingNbt.writeUUID(data, "confirmationNonce", confirmationNonce);
        QIOProcessingNbt.writeUUID(data, "sourceConfigUUID", sourceConfigUUID);
        QIOProcessingNbt.writeUUID(data, "sourceOriginUUID", sourceOriginUUID);
        QIOProcessingNbt.writeUUID(data, "targetConfigUUID", targetConfigUUID);
        data.setLong("sourceRevision", sourceRevision);
        data.setLong("targetRevision", targetRevision);
        data.setString("sourceDigest", sourceDigest);
        data.setString("sourceFrequencyName", sourceFrequencyName);
        data.setInteger("productOverrideCount", productOverrideCount);
        data.setInteger("encodedPatternCount", encodedPatternCount);
        data.setInteger("ingredientOverrideCount", ingredientOverrideCount);
        return data;
    }

    @Nonnull
    public static QIOWorkbenchCopyPreview read(NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            if (data.getInteger("schema") != SCHEMA_VERSION ||
                !data.hasKey("confirmationNonce", NBT.TAG_STRING) ||
                !data.hasKey("sourceConfigUUID", NBT.TAG_STRING) ||
                !data.hasKey("sourceOriginUUID", NBT.TAG_STRING) ||
                !data.hasKey("targetConfigUUID", NBT.TAG_STRING) ||
                !data.hasKey("sourceRevision", NBT.TAG_LONG) ||
                !data.hasKey("targetRevision", NBT.TAG_LONG) ||
                !data.hasKey("sourceDigest", NBT.TAG_STRING) ||
                !data.hasKey("sourceFrequencyName", NBT.TAG_STRING) ||
                !data.hasKey("productOverrideCount", NBT.TAG_INT) ||
                !data.hasKey("encodedPatternCount", NBT.TAG_INT) ||
                !data.hasKey("ingredientOverrideCount", NBT.TAG_INT)) {
                throw new QIOProcessingDataException("Incomplete workbench copy preview");
            }
            return new QIOWorkbenchCopyPreview(
                  QIOProcessingNbt.readUUID(data, "confirmationNonce"),
                  QIOProcessingNbt.readUUID(data, "sourceConfigUUID"),
                  QIOProcessingNbt.readUUID(data, "sourceOriginUUID"),
                  QIOProcessingNbt.readUUID(data, "targetConfigUUID"),
                  data.getLong("sourceRevision"), data.getLong("targetRevision"),
                  data.getString("sourceDigest"), data.getString("sourceFrequencyName"),
                  data.getInteger("productOverrideCount"),
                  data.getInteger("encodedPatternCount"),
                  data.getInteger("ingredientOverrideCount"));
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid workbench copy preview", e);
        }
    }

    private static String checkedDigest(String digest) {
        String checked = Objects.requireNonNull(digest, "sourceDigest").trim()
              .toLowerCase(java.util.Locale.ROOT);
        if (checked.length() != 64 ||
            !checked.chars().allMatch(character -> character >= '0' && character <= '9' ||
                  character >= 'a' && character <= 'f')) {
            throw new IllegalArgumentException("Invalid workbench source digest");
        }
        return checked;
    }
}
