package mekanism.qioprocessing.client.integration;

import mekanism.qioprocessing.common.content.policy.QIOPolicyEntrySnapshot;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * Optional client bridge kept free of JEI classes for installations without a recipe viewer.
 */

/**
 * QIO 处理模块中的 QIORecipeViewerBridge 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIORecipeViewerBridge {

    public interface Viewer {
        boolean canOpen(@Nonnull QIOPolicyEntrySnapshot policy);

        boolean open(@Nonnull QIOPolicyEntrySnapshot policy);

        default boolean supportsBookmarkImport() {
            return false;
        }

        /** Adds bookmark item targets to the editor only; returns -1 if unavailable. */
        default int importBookmarks(Predicate<ItemStack> addTarget, int remaining) {
            return -1;
        }
    }

    @Nullable
    private static Viewer viewer;

    private QIORecipeViewerBridge() {
    }

    public static synchronized void register(@Nonnull Viewer viewer) {
        QIORecipeViewerBridge.viewer = viewer;
    }

    public static synchronized boolean canOpen(@Nullable QIOPolicyEntrySnapshot policy) {
        return policy != null && viewer != null && viewer.canOpen(policy);
    }

    public static synchronized boolean open(@Nullable QIOPolicyEntrySnapshot policy) {
        return policy != null && viewer != null && viewer.open(policy);
    }

    public static synchronized boolean supportsBookmarkImport() {
        return viewer != null && viewer.supportsBookmarkImport();
    }

    public static synchronized int importBookmarks(Predicate<ItemStack> addTarget, int remaining) {
        if (remaining <= 0) return 0;
        return viewer == null ? -1 : viewer.importBookmarks(addTarget, remaining);
    }
}
