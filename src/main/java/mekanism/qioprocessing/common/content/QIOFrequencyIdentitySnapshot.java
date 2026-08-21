package mekanism.qioprocessing.common.content;

import mekanism.common.security.ISecurityTile.SecurityMode;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** Last known display/security identity; frequency UUID remains the authoritative key. */
/**
 * QIO 处理模块中的 QIOFrequencyIdentitySnapshot 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOFrequencyIdentitySnapshot {

    private static final int MAX_NAME_LENGTH = 256;

    private final String name;
    @Nullable
    private final UUID ownerUUID;
    private final SecurityMode securityMode;

    public QIOFrequencyIdentitySnapshot(@Nonnull String name, @Nullable UUID ownerUUID,
          @Nonnull SecurityMode securityMode) {
        Objects.requireNonNull(name, "name");
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("QIO frequency name exceeds " + MAX_NAME_LENGTH + " characters");
        }
        this.name = trimmed;
        this.ownerUUID = ownerUUID;
        this.securityMode = Objects.requireNonNull(securityMode, "securityMode");
    }

    @Nonnull
    public String getName() {
        return name;
    }

    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    @Nonnull
    public SecurityMode getSecurityMode() {
        return securityMode;
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("name", name);
        if (ownerUUID != null) {
            QIOProcessingNbt.writeUUID(data, "ownerUUID", ownerUUID);
        }
        data.setString("securityMode", securityMode.name());
        return data;
    }

    @Nonnull
    public static QIOFrequencyIdentitySnapshot read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            return new QIOFrequencyIdentitySnapshot(data.getString("name"),
                  data.hasKey("ownerUUID", NBT.TAG_STRING) ?
                        QIOProcessingNbt.readUUID(data, "ownerUUID") : null,
                  QIOProcessingNbt.readEnum(data, "securityMode", SecurityMode.class));
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid last known QIO frequency identity", e);
        }
    }
}
