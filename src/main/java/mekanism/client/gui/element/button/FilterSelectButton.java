package mekanism.client.gui.element.button;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

public class FilterSelectButton extends MekanismButton {

    private static final ResourceLocation ARROWS = MekanismUtils.getResource(ResourceType.GUI_BUTTON, "filter_arrows.png");
    private static final int TEXTURE_WIDTH = 22;
    private static final int TEXTURE_HEIGHT = 14;

    private final boolean down;
    private final IClickable onPress;

    public FilterSelectButton(IGuiWrapper gui, int x, int y, boolean down, IClickable onPress) {
        super(gui, x, y, 11, 7, new mekanism.api.text.TextComponentGroup(), null, null);
        this.down = down;
        this.onPress = onPress;
        this.playClickSound = true;
        setButtonBackground(ButtonBackground.NONE);
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        if (resetColorBeforeRender()) {
            MekanismRenderer.resetColor();
        }
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
              GlStateManager.DestFactor.ZERO);
        GlStateManager.enableDepth();
        minecraft.renderEngine.bindTexture(ARROWS);
        GuiUtils.blit(getButtonX(), getButtonY(), isMouseOverCheckWindows(mouseX, mouseY) ? width : 0, down ? 7 : 0, width, height, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public boolean isMouseOver(double xAxis, double yAxis) {
        if (super.isMouseOver(xAxis, yAxis)) {
            double xShifted = xAxis - getX();
            double yShifted = yAxis - getY();
            if (down) {
                if (yShifted < 2) {
                    return true;
                } else if (yShifted < 3) {
                    return xShifted >= 1 && xShifted < 10;
                } else if (yShifted < 4) {
                    return xShifted >= 2 && xShifted < 9;
                } else if (yShifted < 5) {
                    return xShifted >= 3 && xShifted < 8;
                } else if (yShifted < 6) {
                    return xShifted >= 4 && xShifted < 7;
                }
                return xShifted >= 5 && xShifted < 6;
            }
            if (yShifted < 1) {
                return xShifted >= 5 && xShifted < 6;
            } else if (yShifted < 2) {
                return xShifted >= 4 && xShifted < 7;
            } else if (yShifted < 3) {
                return xShifted >= 3 && xShifted < 8;
            } else if (yShifted < 4) {
                return xShifted >= 2 && xShifted < 9;
            } else if (yShifted < 5) {
                return xShifted >= 1 && xShifted < 10;
            }
            return true;
        }
        return false;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        onPress.onClick(this, mouseX, mouseY);
    }
}
