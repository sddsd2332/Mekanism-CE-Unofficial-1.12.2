package mekanism.client.gui.element.custom;

import mekanism.client.SpecialColors;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiSideHolder;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import mekanism.common.util.MekanismUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;

/** Height and width controls for the resizable QIO viewer. */
public class GuiResizeControls extends GuiSideHolder {

    private static final ResourceLocation PLUS = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_BUTTON, "plus.png");
    private static final ResourceLocation MINUS = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_BUTTON, "minus.png");
    private final MekanismImageButton expand;
    private final MekanismImageButton shrink;
    private int tooltipTicks;

    public <GUI extends IGuiWrapper & ResizeController> GuiResizeControls(GUI gui, int y) {
        super(gui, -26, y, 40, true, false);
        expand = addChild(new MekanismImageButton(gui, relativeX + 4, relativeY + 5, 19, 9, 19, 9, PLUS,
              () -> handleResize(gui, ResizeType.EXPAND_Y), (mekanism.client.gui.element.GuiElement.IHoverable) null));
        shrink = addChild(new MekanismImageButton(gui, relativeX + 4, relativeY + 26, 19, 9, 19, 9, MINUS,
              () -> handleResize(gui, ResizeType.SHRINK_Y), (mekanism.client.gui.element.GuiElement.IHoverable) null));
        active = true;
        updateState();
    }

    private void handleResize(ResizeController gui, ResizeType type) {
        gui.resize(type, GuiScreen.isShiftKeyDown());
        updateState();
    }

    private void updateState() {
        int rows = QIOItemViewerContainer.getConfiguredRows();
        expand.active = rows < ((ResizeController) gui()).getMaxRows();
        shrink.active = rows > QIOItemViewerContainer.SLOTS_Y_MIN;
    }

    @Override
    public void tick() {
        super.tick();
        tooltipTicks = Math.max(0, tooltipTicks - 1);
        updateState();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (button == 0 && !expand.active && mouseX >= expand.getX() && mouseX < expand.getRight() &&
              mouseY >= expand.getY() && mouseY < expand.getBottom()) {
            tooltipTicks = 100;
            return true;
        }
        return handled;
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        if (tooltipTicks > 0 && !expand.active) {
            displayTooltip(mekanism.common.MekanismLang.QIO_COMPENSATE_TOOLTIP.translate(), mouseX, mouseY);
        }
    }

    @Override
    protected void colorTab() {
        MekanismRenderer.color(SpecialColors.TAB_RESIZE_CONTROLS.argb());
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawScaledScrollingString(new TextComponentTranslation("gui.height"), 0, 16, TextAlignment.CENTER,
              titleTextColor(), width, 4, false, 0.6F, getMillis());
    }

    public enum ResizeType {
        EXPAND_X,
        EXPAND_Y,
        SHRINK_X,
        SHRINK_Y
    }

    public interface ResizeController {

        void resize(ResizeType type, boolean adjustMax);

        int getMaxRows();
    }
}
