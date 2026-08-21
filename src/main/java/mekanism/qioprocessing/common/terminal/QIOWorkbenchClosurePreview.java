package mekanism.qioprocessing.common.terminal;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Bounded client summary for one server-retained recursive recipe import. */
/**
 * QIO 处理模块中的 QIOWorkbenchClosurePreview 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOWorkbenchClosurePreview {

    private static final int SCHEMA_VERSION = 2;
    public static final int MAX_CYCLE_SAMPLES = 16;
    private static final int MAX_CYCLE_PATH_LENGTH = 2_048;

    private final UUID confirmationNonce;
    private final int rootRecipeCount;
    private final int newProductCount;
    private final int newRecipeCount;
    private final int existingRecipeCount;
    private final int leafMaterialCount;
    private final int cycleCount;
    private final int skippedCyclicRecipeCount;
    private final boolean truncated;
    private final List<String> cyclePaths;

    public QIOWorkbenchClosurePreview(@Nonnull UUID confirmationNonce, int rootRecipeCount,
          int newProductCount, int newRecipeCount, int existingRecipeCount,
          int leafMaterialCount, int cycleCount, int skippedCyclicRecipeCount,
          boolean truncated, @Nonnull List<String> cyclePaths) {
        this.confirmationNonce = Objects.requireNonNull(confirmationNonce,
              "confirmationNonce");
        if (rootRecipeCount < 0 || newProductCount < 0 || newRecipeCount < 0 ||
            existingRecipeCount < 0 || leafMaterialCount < 0 || cycleCount < 0 ||
            skippedCyclicRecipeCount < 0) {
            throw new IllegalArgumentException("Negative workbench closure preview counter");
        }
        if (cyclePaths.size() > MAX_CYCLE_SAMPLES) {
            throw new IllegalArgumentException("Too many workbench closure cycle samples");
        }
        List<String> checkedPaths = new ArrayList<>(cyclePaths.size());
        for (String path : cyclePaths) {
            String checked = Objects.requireNonNull(path, "cyclePath");
            if (checked.length() > MAX_CYCLE_PATH_LENGTH) {
                throw new IllegalArgumentException("Workbench closure cycle path is too long");
            }
            checkedPaths.add(checked);
        }
        this.rootRecipeCount = rootRecipeCount;
        this.newProductCount = newProductCount;
        this.newRecipeCount = newRecipeCount;
        this.existingRecipeCount = existingRecipeCount;
        this.leafMaterialCount = leafMaterialCount;
        this.cycleCount = cycleCount;
        this.skippedCyclicRecipeCount = skippedCyclicRecipeCount;
        this.truncated = truncated;
        this.cyclePaths = Collections.unmodifiableList(checkedPaths);
    }

    @Nonnull public UUID getConfirmationNonce() { return confirmationNonce; }
    public int getRootRecipeCount() { return rootRecipeCount; }
    public int getNewProductCount() { return newProductCount; }
    public int getNewRecipeCount() { return newRecipeCount; }
    public int getExistingRecipeCount() { return existingRecipeCount; }
    public int getLeafMaterialCount() { return leafMaterialCount; }
    public int getCycleCount() { return cycleCount; }
    public int getSkippedCyclicRecipeCount() { return skippedCyclicRecipeCount; }
    public boolean isTruncated() { return truncated; }
    @Nonnull public List<String> getCyclePaths() { return cyclePaths; }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("schema", SCHEMA_VERSION);
        QIOProcessingNbt.writeUUID(data, "confirmationNonce", confirmationNonce);
        data.setInteger("rootRecipeCount", rootRecipeCount);
        data.setInteger("newProductCount", newProductCount);
        data.setInteger("newRecipeCount", newRecipeCount);
        data.setInteger("existingRecipeCount", existingRecipeCount);
        data.setInteger("leafMaterialCount", leafMaterialCount);
        data.setInteger("cycleCount", cycleCount);
        data.setInteger("skippedCyclicRecipeCount", skippedCyclicRecipeCount);
        data.setBoolean("truncated", truncated);
        NBTTagList paths = new NBTTagList();
        cyclePaths.forEach(path -> paths.appendTag(new NBTTagString(path)));
        data.setTag("cyclePaths", paths);
        return data;
    }

    @Nonnull
    public static QIOWorkbenchClosurePreview read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            if (data.getInteger("schema") != SCHEMA_VERSION ||
                !data.hasKey("confirmationNonce", NBT.TAG_STRING) ||
                !data.hasKey("rootRecipeCount", NBT.TAG_INT) ||
                !data.hasKey("newProductCount", NBT.TAG_INT) ||
                !data.hasKey("newRecipeCount", NBT.TAG_INT) ||
                !data.hasKey("existingRecipeCount", NBT.TAG_INT) ||
                !data.hasKey("leafMaterialCount", NBT.TAG_INT) ||
                !data.hasKey("cycleCount", NBT.TAG_INT) ||
                !data.hasKey("skippedCyclicRecipeCount", NBT.TAG_INT) ||
                !data.hasKey("truncated", NBT.TAG_BYTE) ||
                !data.hasKey("cyclePaths", NBT.TAG_LIST)) {
                throw new QIOProcessingDataException("Incomplete workbench closure preview");
            }
            NBTTagList storedPaths = data.getTagList("cyclePaths", NBT.TAG_STRING);
            if (storedPaths.tagCount() > MAX_CYCLE_SAMPLES) {
                throw new QIOProcessingDataException("Too many workbench closure cycle samples");
            }
            List<String> paths = new ArrayList<>(storedPaths.tagCount());
            for (int index = 0; index < storedPaths.tagCount(); index++) {
                paths.add(storedPaths.getStringTagAt(index));
            }
            return new QIOWorkbenchClosurePreview(
                  QIOProcessingNbt.readUUID(data, "confirmationNonce"),
                  data.getInteger("rootRecipeCount"), data.getInteger("newProductCount"),
                  data.getInteger("newRecipeCount"), data.getInteger("existingRecipeCount"),
                  data.getInteger("leafMaterialCount"), data.getInteger("cycleCount"),
                  data.getInteger("skippedCyclicRecipeCount"),
                  data.getBoolean("truncated"), paths);
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid workbench closure preview", e);
        }
    }
}
