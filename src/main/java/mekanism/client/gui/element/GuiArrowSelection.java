package mekanism.client.gui.element;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

import java.util.function.Supplier;

public class GuiArrowSelection extends GuiTexturedElement {

    private static final ResourceLocation ARROW = MekanismUtils.getResource(ResourceType.GUI, "arrow_selection.png");

    private final Supplier<ITextComponent> targetText;

    public GuiArrowSelection(IGuiWrapper gui, int x, int y, Supplier<ITextComponent> targetText) {
        super(ARROW, gui, x, y, 33, 19);
        this.targetText = targetText;
    }

    @Override
    public boolean isMouseOver(double xAxis, double yAxis) {
        return this.active && this.visible && xAxis >= getX() + 16 && xAxis < getRight() - 1 && yAxis >= getY() + 1 && yAxis < getBottom() - 1;
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        ITextComponent component = targetText.get();
        if (component != null) {
            drawScrollingString(component, getWidth(), 6, TextAlignment.LEFT, screenTextColor(), 15, 1, false);
        }
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        MekanismRenderer.bindTexture(getResource());
        GuiUtils.blit(relativeX, relativeY, 0, 0, width, height, width, height);
        MekanismRenderer.resetColor();
    }
}
