package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.tab.window.GuiWindowCreatorTab;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import mekanism.common.util.MekanismUtils;
import mekanism.qioprocessing.common.inventory.container.QIOSmartProcessingPageContainer;
import mekanism.qioprocessing.common.QIOProcessingWindowTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/** Side tab opening the server-authoritative smart-processing order window. */
/**
 * QIO 处理模块中的 GuiQIOSmartProcessingOrderTab 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class GuiQIOSmartProcessingOrderTab extends GuiWindowCreatorTab<Void, GuiQIOSmartProcessingOrderTab> {
    static final WindowType WINDOW_TYPE = QIOProcessingWindowTypes.SMART_PROCESSING_ORDER;
    private static final ResourceLocation ICON = MekanismUtils.getResource(
          MekanismUtils.ResourceType.GUI_BUTTON, "crafting.png");
    private final Supplier<QIOSmartProcessingPageContainer> containerSupplier;

    public GuiQIOSmartProcessingOrderTab(@Nonnull IGuiWrapper gui,
          @Nonnull Supplier<GuiQIOSmartProcessingOrderTab> self,
          @Nonnull Supplier<QIOSmartProcessingPageContainer> containerSupplier) {
        super(ICON, gui, null, 60, false, self);
        this.containerSupplier = containerSupplier;
    }

    @Override
    protected GuiWindow createWindow(SelectedWindowData windowData) {
        return new GuiQIOSmartProcessingOrderWindow(gui(),
              (gui().getWidth() - 220) / 2, 15, containerSupplier.get(), windowData);
    }

    @Override
    protected SelectedWindowData getNextWindowData() {
        return new SelectedWindowData(WINDOW_TYPE);
    }

    @Override
    protected List<SelectedWindowData> getValidWindows() {
        return Collections.singletonList(getNextWindowData());
    }

    @Override
    protected ITextComponent getTooltipText() {
        return new TextComponentTranslation("gui.mekanismqioprocessing.order_tab");
    }
}
