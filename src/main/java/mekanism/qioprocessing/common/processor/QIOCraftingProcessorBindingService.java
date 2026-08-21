package mekanism.qioprocessing.common.processor;

import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyManager;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.util.SecurityUtils;
import mekanism.qioprocessing.common.tile.QIOCraftingProcessor;
import net.minecraft.entity.player.EntityPlayer;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

/** Server-authoritative exact-frequency binding for workbench processors. */
/**
 * QIO 处理模块中的 QIOCraftingProcessorBindingService 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOCraftingProcessorBindingService {

    private QIOCraftingProcessorBindingService() {
    }

    public static boolean bind(@Nonnull QIOCraftingProcessor processor,
          @Nonnull FrequencyIdentity identity, @Nonnull UUID expectedFrequencyUUID,
          @Nonnull EntityPlayer player) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(expectedFrequencyUUID, "expectedFrequencyUUID");
        FrequencyManager<QIOFrequency> manager = FrequencyType.QIO.getManager(
              identity.ownerUUID(), identity.securityMode());
        QIOFrequency frequency = manager == null ? null : manager.getFrequency(identity.key());
        return frequency != null && expectedFrequencyUUID.equals(
              frequency.getFrequencyUUID()) && bind(processor, frequency, player);
    }

    public static boolean bind(@Nonnull QIOCraftingProcessor processor,
          @Nonnull QIOFrequency frequency, @Nonnull EntityPlayer player) {
        Objects.requireNonNull(processor, "processor");
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(player, "player");
        if (player.world == null || player.world.isRemote || processor.getWorld() == null ||
            processor.getWorld().isRemote || processor.getWorld() != player.world ||
            !processor.isUsableByPlayer(player) ||
            !SecurityUtils.canAccess(player, processor) ||
            !SecurityUtils.canAccess(frequency.getSecurity(), player.getUniqueID(),
                  frequency.getOwner())) {
            return false;
        }
        QIOFrequencyReference reference = QIOFrequencyStorageAccess.INSTANCE.createReference(
              frequency, player.getUniqueID());
        return QIOFrequencyStorageAccess.INSTANCE.canAccess(reference,
              player.getUniqueID()) && processor.applyAuthorizedBinding(frequency,
              player.getUniqueID());
    }

    public static boolean unbind(@Nonnull QIOCraftingProcessor processor,
          @Nonnull EntityPlayer player) {
        Objects.requireNonNull(processor, "processor");
        Objects.requireNonNull(player, "player");
        return player.world != null && !player.world.isRemote &&
              processor.getWorld() == player.world && processor.isUsableByPlayer(player) &&
              SecurityUtils.canAccess(player, processor) &&
              processor.applyAuthorizedBinding(null, player.getUniqueID());
    }
}
