package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.element.tab.GuiSecurityTab;
import mekanism.client.gui.qio.GuiQIOViewerScreen;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.qioprocessing.common.inventory.container.ContainerQIOSmartProcessingTerminal;
import mekanism.qioprocessing.common.tile.TileEntityQIOSmartProcessingTerminal;
import mekanism.client.gui.element.window.GuiWindow;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** QIO viewer screen with three persistent manual crafting windows. */
@SideOnly(Side.CLIENT)
/**
 * QIO 处理模块中的 GuiQIOSmartProcessingTerminal 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class GuiQIOSmartProcessingTerminal extends
      GuiQIOViewerScreen<ContainerQIOSmartProcessingTerminal> {

    private final TileEntityQIOSmartProcessingTerminal tile;
    private final ContainerQIOSmartProcessingTerminal container;
    private GuiQIOSmartProcessingOrderTab orderTab;
    public GuiQIOSmartProcessingTerminal(InventoryPlayer inventory,
          TileEntityQIOSmartProcessingTerminal tile) {
        super(new ContainerQIOSmartProcessingTerminal(inventory, tile));
        this.tile = tile;
        container = (ContainerQIOSmartProcessingTerminal) inventorySlots;
    }

    private GuiQIOSmartProcessingTerminal(ContainerQIOSmartProcessingTerminal container) {
        super(container);
        tile = (TileEntityQIOSmartProcessingTerminal) container.getTerminalTile();
        this.container = container;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        frequencyTab = addButton(new GuiQIOProcessingTerminalFrequencyTab(this,
              container, () -> frequencyTab));
        orderTab = addButton(new GuiQIOSmartProcessingOrderTab(this,
              () -> orderTab, () -> container));
        addButton(new GuiSecurityTab<>(this, tile));
    }

    @Override
    protected String getViewerTitle() {
        return tile.getName();
    }

    @Override
    protected QIOFrequency getQIOFrequency() {
        return container.getTerminalFrequency();
    }

    @Override
    protected GuiQIOViewerScreen<ContainerQIOSmartProcessingTerminal> recreate(
          ContainerQIOSmartProcessingTerminal container) {
        return new GuiQIOSmartProcessingTerminal(container);
    }

    @Override
    protected void adoptTransferredWindow(GuiWindow window) {
        if (window instanceof GuiQIOSmartProcessingOrderWindow && orderTab != null) {
            orderTab.adoptWindows(window);
            orderTab.active = false;
        }
    }
}
