package mekanism.client.gui.element.tab;

import mekanism.client.SpecialColors;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInsetElement;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import mekanism.common.util.MekanismUtils;
import net.minecraft.util.ResourceLocation;

/** Controls whether inventory shift-click prioritizes QIO or an open crafting window. */
public class GuiTargetDirectionTab extends GuiInsetElement<QIOItemViewerContainer> {

    private static final ResourceLocation INTO_FREQUENCY = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_BUTTON, "crafting_out.png");
    private static final ResourceLocation INTO_WINDOW = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_BUTTON, "crafting_in.png");

    public GuiTargetDirectionTab(IGuiWrapper gui, QIOItemViewerContainer container, int y) {
        super(INTO_FREQUENCY, gui, container, -26, y, 26, 18, true);
    }

    @Override
    protected void colorTab() {
        MekanismRenderer.color(SpecialColors.TAB_TARGET_DIRECTION.argb());
    }

    @Override
    protected ResourceLocation getOverlay() {
        return dataSource.shiftClickIntoFrequency() ? INTO_FREQUENCY : INTO_WINDOW;
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        dataSource.toggleTargetDirection();
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        displayTooltip((dataSource.shiftClickIntoFrequency() ? MekanismLang.QIO_TRANSFER_TO_FREQUENCY :
              MekanismLang.QIO_TRANSFER_TO_WINDOW).translate(), mouseX, mouseY);
    }
}
