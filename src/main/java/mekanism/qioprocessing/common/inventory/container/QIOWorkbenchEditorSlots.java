package mekanism.qioprocessing.common.inventory.container;

import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.inventory.container.slot.HotBarSlot;
import mekanism.common.inventory.container.slot.MainInventorySlot;
import mekanism.common.inventory.container.slot.VirtualHotBarSlot;
import mekanism.common.inventory.container.slot.VirtualMainInventorySlot;
import mekanism.qioprocessing.common.QIOProcessingWindowTypes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Conditional real player slots shared by the workbench recipe editors. */
/**
 * QIO 处理模块中的 QIOWorkbenchEditorSlots 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOWorkbenchEditorSlots {

    public static final int INVENTORY_X = 227;
    public static final int INVENTORY_Y = 157;

    private QIOWorkbenchEditorSlots() {
    }

    public static boolean isEditorWindow(@Nullable SelectedWindowData windowData) {
        return QIOProcessingWindowTypes.isWorkbenchEditorWindow(windowData);
    }

    public static MainInventorySlot main(MekanismContainer container, IInventory inventory,
          int index, int x, int y) {
        return new EditorMainInventorySlot(container, inventory, index, x, y);
    }

    public static HotBarSlot hotbar(MekanismContainer container, IInventory inventory,
          int index, int x, int y, boolean locked) {
        return new EditorHotBarSlot(container, inventory, index, x, y, locked);
    }

    @Nullable
    private static SelectedWindowData selected(MekanismContainer container,
          @Nonnull EntityPlayer player) {
        return container.isRemote() ? container.getSelectedWindow() :
              container.getSelectedWindow(player.getUniqueID());
    }

    private static final class EditorMainInventorySlot extends VirtualMainInventorySlot {

        private final MekanismContainer container;

        private EditorMainInventorySlot(MekanismContainer container, IInventory inventory,
              int index, int x, int y) {
            super(inventory, index, x, y);
            this.container = container;
        }

        @Override
        public boolean exists(@Nullable SelectedWindowData windowData) {
            return isEditorWindow(windowData);
        }

        @Override
        public boolean canTakeStack(@Nonnull EntityPlayer player) {
            return exists(selected(container, player)) && super.canTakeStack(player);
        }

        @Override
        public boolean isEnabled() {
            return container.isRemote() && exists(container.getSelectedWindow());
        }
    }

    private static final class EditorHotBarSlot extends VirtualHotBarSlot {

        private final MekanismContainer container;
        private final boolean locked;

        private EditorHotBarSlot(MekanismContainer container, IInventory inventory,
              int index, int x, int y, boolean locked) {
            super(inventory, index, x, y);
            this.container = container;
            this.locked = locked;
        }

        @Override
        public boolean exists(@Nullable SelectedWindowData windowData) {
            return isEditorWindow(windowData);
        }

        @Override
        public boolean canTakeStack(@Nonnull EntityPlayer player) {
            return !locked && exists(selected(container, player)) &&
                  super.canTakeStack(player);
        }

        @Override
        public boolean isEnabled() {
            return container.isRemote() && exists(container.getSelectedWindow());
        }
    }
}
