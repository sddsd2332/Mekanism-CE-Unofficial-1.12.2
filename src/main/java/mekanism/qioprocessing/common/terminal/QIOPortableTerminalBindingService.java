package mekanism.qioprocessing.common.terminal;

import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.common.util.SecurityUtils;
import mekanism.qioprocessing.common.inventory.container.QIOPortableTerminalContainer;
import mekanism.qioprocessing.common.item.ItemPortableQIOProcessingTerminal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.Objects;

/** Server-authoritative exact frequency mutation for an already validated portable session. */
/**
 * QIO 处理模块中的 QIOPortableTerminalBindingService 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOPortableTerminalBindingService {

    private QIOPortableTerminalBindingService() {
    }

    public static boolean bind(@Nonnull QIOPortableTerminalContainer container,
          @Nonnull QIOFrequency frequency, @Nonnull EntityPlayer player) {
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(player, "player");
        ItemStack stack = container.getPortableStack();
        if (!container.isPortableTargetUsable(player) || stack.isEmpty() ||
            !(stack.getItem() instanceof ItemPortableQIOProcessingTerminal item) ||
            !SecurityUtils.canAccess(player, stack) ||
            !SecurityUtils.canAccess(frequency.getSecurity(), player.getUniqueID(),
                  frequency.getOwner())) {
            return false;
        }
        QIOFrequencyReference reference = QIOFrequencyStorageAccess.INSTANCE.createReference(
              frequency, player.getUniqueID());
        if (!QIOFrequencyStorageAccess.INSTANCE.canAccess(reference,
              player.getUniqueID())) {
            return false;
        }
        boolean changed = item.applyAuthorizedFrequency(stack, frequency,
              player.getUniqueID());
        if (changed) {
            player.inventory.markDirty();
        }
        return changed;
    }

    public static boolean unbind(@Nonnull QIOPortableTerminalContainer container,
          @Nonnull EntityPlayer player) {
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(player, "player");
        ItemStack stack = container.getPortableStack();
        if (!container.isPortableTargetUsable(player) || stack.isEmpty() ||
            !(stack.getItem() instanceof ItemPortableQIOProcessingTerminal item) ||
            !SecurityUtils.canAccess(player, stack)) {
            return false;
        }
        boolean changed = item.applyAuthorizedFrequency(stack, null,
              player.getUniqueID());
        if (changed) {
            player.inventory.markDirty();
        }
        return changed;
    }
}
