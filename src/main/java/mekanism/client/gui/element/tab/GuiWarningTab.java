package mekanism.client.gui.element.tab;

import mekanism.api.EnumColor;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiTexturedElement;
import mekanism.client.gui.warning.IWarningTracker;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.MekanismLang;
import mekanism.common.util.MekanismUtils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class GuiWarningTab extends GuiTexturedElement {

    private static final ResourceLocation WARNING_LEFT = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_TAB, "warning_info_left.png");
    private static final ResourceLocation WARNING_RIGHT = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_TAB, "warning_info_right.png");

    private final IWarningTracker warningTracker;

    public GuiWarningTab(IGuiWrapper gui, IWarningTracker warningTracker, int y) {
        this(gui, warningTracker, y, true);
    }

    public GuiWarningTab(IGuiWrapper gui, IWarningTracker warningTracker, boolean left) {
        this(gui, warningTracker, 109, left);
    }

    public GuiWarningTab(IGuiWrapper gui, IWarningTracker warningTracker, int y, boolean left) {
        super(left ? WARNING_LEFT : WARNING_RIGHT, gui, left ? -26 : gui.getWidth(), y, 26, 26);
        this.warningTracker = warningTracker;
        updateVisibility();
    }

    @Override
    public void tick() {
        super.tick();
        updateVisibility();
    }

    private void updateVisibility() {
        visible = warningTracker.hasWarning();
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        MekanismRenderer.bindTexture(getResource());
        GlStateManager.enableBlend();
        GuiUtils.blit(relativeX, relativeY, 0, 0, width, height, width, height);
        GlStateManager.disableBlend();
        MekanismRenderer.resetColor();
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        List<String> info = new ArrayList<>();
        info.add(MekanismLang.ISSUES.translateColored(EnumColor.YELLOW).getFormattedText());
        warningTracker.getWarnings().forEach(component -> info.add(component.getFormattedText()));
        displayTooltips(info, mouseX, mouseY);
    }
}
