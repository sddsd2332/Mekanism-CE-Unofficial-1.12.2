package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.qio.GuiQIOViewerScreen;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.qioprocessing.common.inventory.container.ContainerPortableQIOSmartProcessingTerminal;
import mekanism.client.gui.element.window.GuiWindow;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Portable QIO viewer with three item-owned manual crafting windows. */
@SideOnly(Side.CLIENT)
/**
 * QIO 处理模块中的 GuiPortableQIOSmartProcessingTerminal 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class GuiPortableQIOSmartProcessingTerminal extends
      GuiQIOViewerScreen<ContainerPortableQIOSmartProcessingTerminal> {

    private final ContainerPortableQIOSmartProcessingTerminal container;
    private GuiQIOSmartProcessingOrderTab orderTab;

    public GuiPortableQIOSmartProcessingTerminal(InventoryPlayer inventory,
          EnumHand hand, int itemSlot, ItemStack stack) {
        super(new ContainerPortableQIOSmartProcessingTerminal(inventory, hand,
              itemSlot, stack));
        container = (ContainerPortableQIOSmartProcessingTerminal) inventorySlots;
    }

    private GuiPortableQIOSmartProcessingTerminal(
          ContainerPortableQIOSmartProcessingTerminal container) {
        super(container);
        this.container = container;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        frequencyTab = addButton(new GuiQIOProcessingTerminalFrequencyTab(this,
              container, () -> frequencyTab));
        orderTab = addButton(new GuiQIOSmartProcessingOrderTab(this,
              () -> orderTab, () -> container));
    }

    @Override
    protected String getViewerTitle() {
        ItemStack stack = container.getPortableStack();
        return stack.isEmpty() ? "QIO Smart Processing Terminal" : stack.getDisplayName();
    }

    @Override
    protected QIOFrequency getQIOFrequency() {
        return container.getTerminalFrequency();
    }

    @Override
    protected GuiQIOViewerScreen<ContainerPortableQIOSmartProcessingTerminal> recreate(
          ContainerPortableQIOSmartProcessingTerminal container) {
        return new GuiPortableQIOSmartProcessingTerminal(container);
    }

    @Override
    protected void adoptTransferredWindow(GuiWindow window) {
        if (window instanceof GuiQIOSmartProcessingOrderWindow && orderTab != null) {
            orderTab.adoptWindows(window);
            orderTab.active = false;
        }
    }
}
