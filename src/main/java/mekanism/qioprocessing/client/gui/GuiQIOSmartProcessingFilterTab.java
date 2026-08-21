package mekanism.qioprocessing.client.gui;

import mekanism.client.SpecialColors;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInsetElement;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.util.MekanismUtils;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingResourceFilter;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nonnull;
import java.util.Locale;

/** Fixed-position resource filter tab for the smart-processing order window. */
/**
 * QIO 处理模块中的 GuiQIOSmartProcessingFilterTab 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
final class GuiQIOSmartProcessingFilterTab extends
      GuiInsetElement<QIOSmartProcessingResourceFilter> {

    private final Runnable onSelect;

    GuiQIOSmartProcessingFilterTab(@Nonnull IGuiWrapper gui,
          @Nonnull QIOSmartProcessingResourceFilter filter, int x, int y,
          @Nonnull Runnable onSelect) {
        super(icon(filter), gui, filter, x, y, 26, 18, true);
        this.onSelect = onSelect;
    }

    QIOSmartProcessingResourceFilter getFilter() {
        return dataSource;
    }

    @Override
    protected void colorTab() {
        MekanismRenderer.color(switch (dataSource) {
            case ITEM -> SpecialColors.TAB_ITEM_CONFIG.argb();
            case FLUID -> SpecialColors.TAB_FLUID_CONFIG.argb();
            case GAS -> SpecialColors.TAB_GAS_CONFIG.argb();
            case ALL -> SpecialColors.TAB_CRAFTING_WINDOW.argb();
        });
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        onSelect.run();
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        displayTooltip(new TextComponentTranslation(
              "gui.mekanismqioprocessing.order_filter_" +
                    dataSource.name().toLowerCase(Locale.ROOT)), mouseX, mouseY);
    }

    private static ResourceLocation icon(QIOSmartProcessingResourceFilter filter) {
        return switch (filter) {
            case ITEM -> MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "items.png");
            case FLUID -> MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "fluids.png");
            case GAS -> MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "gases.png");
            case ALL -> MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_BUTTON,
                  "crafting.png");
        };
    }
}
