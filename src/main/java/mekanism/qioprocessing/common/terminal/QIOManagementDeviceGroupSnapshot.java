package mekanism.qioprocessing.common.terminal;

import mekanism.api.processing.MachinePresentationDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceSnapshot;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import java.util.Objects;

/** Stable machine-type summary used by the first management-terminal column. */
/**
 * QIO 处理模块中的 QIOManagementDeviceGroupSnapshot 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOManagementDeviceGroupSnapshot {

    private final String typeKey;
    private final QIOAutomationDeviceSnapshot.Kind kind;
    private final String blockId;
    private final int blockMetadata;
    private final MachinePresentationDescriptor presentation;
    private final String profileScopeId;
    private final int onlineCount;
    private final int totalCount;

    public QIOManagementDeviceGroupSnapshot(@Nonnull String typeKey,
          @Nonnull QIOAutomationDeviceSnapshot.Kind kind, @Nonnull String blockId,
          int blockMetadata, @Nonnull String profileScopeId, int onlineCount,
          int totalCount) {
        this.typeKey = checked(typeKey, "typeKey", false);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.blockId = checked(blockId, "blockId", false);
        if (blockMetadata < 0 || blockMetadata > 15 || totalCount <= 0 ||
            onlineCount < 0 || onlineCount > totalCount) {
            throw new IllegalArgumentException("Invalid QIO management device group");
        }
        this.blockMetadata = blockMetadata;
        this.presentation = MachinePresentationDescriptor.of(blockId, blockMetadata, null);
        this.profileScopeId = checked(profileScopeId, "profileScopeId", true);
        this.onlineCount = onlineCount;
        this.totalCount = totalCount;
    }

    public QIOManagementDeviceGroupSnapshot(@Nonnull String typeKey,
          @Nonnull QIOAutomationDeviceSnapshot.Kind kind, @Nonnull String blockId,
          int blockMetadata, @Nonnull MachinePresentationDescriptor presentation,
          @Nonnull String profileScopeId, int onlineCount, int totalCount) {
        this.typeKey = checked(typeKey, "typeKey", false);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.blockId = checked(blockId, "blockId", false);
        if (blockMetadata < 0 || blockMetadata > 15 || totalCount <= 0 ||
            onlineCount < 0 || onlineCount > totalCount) {
            throw new IllegalArgumentException("Invalid QIO management device group");
        }
        this.blockMetadata = blockMetadata;
        this.presentation = MachinePresentationDescriptor.read(
              Objects.requireNonNull(presentation, "presentation").write());
        this.profileScopeId = checked(profileScopeId, "profileScopeId", true);
        this.onlineCount = onlineCount;
        this.totalCount = totalCount;
    }

    @Nonnull public String getTypeKey() { return typeKey; }
    @Nonnull public QIOAutomationDeviceSnapshot.Kind getKind() { return kind; }
    @Nonnull public String getBlockId() { return blockId; }
    public int getBlockMetadata() { return blockMetadata; }
    @Nonnull public MachinePresentationDescriptor getPresentation() {
        return MachinePresentationDescriptor.read(presentation.write());
    }
    @Nonnull public String getProfileScopeId() { return profileScopeId; }
    public int getOnlineCount() { return onlineCount; }
    public int getTotalCount() { return totalCount; }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("typeKey", typeKey);
        data.setString("kind", kind.name());
        data.setString("blockId", blockId);
        data.setInteger("blockMetadata", blockMetadata);
        data.setTag("presentation", presentation.write());
        data.setString("profileScopeId", profileScopeId);
        data.setInteger("onlineCount", onlineCount);
        data.setInteger("totalCount", totalCount);
        return data;
    }

    @Nonnull
    public static QIOManagementDeviceGroupSnapshot read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            MachinePresentationDescriptor presentation = data.hasKey("presentation",
                  net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND) ?
                  MachinePresentationDescriptor.read(data.getCompoundTag("presentation")) :
                  MachinePresentationDescriptor.of(data.getString("blockId"),
                        data.getInteger("blockMetadata"), null);
            return new QIOManagementDeviceGroupSnapshot(data.getString("typeKey"),
                  QIOAutomationDeviceSnapshot.Kind.valueOf(data.getString("kind")),
                  data.getString("blockId"), data.getInteger("blockMetadata"),
                  presentation, data.getString("profileScopeId"),
                  data.getInteger("onlineCount"),
                  data.getInteger("totalCount"));
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO management device group", e);
        }
    }

    @Nonnull
    public static String typeKey(@Nonnull QIOAutomationDeviceSnapshot device) {
        Objects.requireNonNull(device, "device");
        if (device.getKind() == QIOAutomationDeviceSnapshot.Kind.AUTOMATION_MACHINE) {
            return device.getKind().name() + '|' + device.getProfileScopeId() + '|' +
                  device.getPresentation().presentationKey();
        }
        return device.getKind().name() + '|' +
              device.getPresentation().presentationKey();
    }

    private static String checked(String value, String name, boolean emptyAllowed) {
        String checked = Objects.requireNonNull(value, name).trim();
        if ((!emptyAllowed && checked.isEmpty()) || checked.length() > 2_048) {
            throw new IllegalArgumentException(name + " has an invalid length");
        }
        return checked;
    }
}
