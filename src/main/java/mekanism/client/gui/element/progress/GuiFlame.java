package mekanism.client.gui.element.progress;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.render.MekanismRenderer;

public class GuiFlame extends GuiProgress {

    public GuiFlame(IProgressInfoHandler handler, IGuiWrapper gui, int x, int y) {
        super(handler, ProgressType.FLAME, gui, x, y);
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        MekanismRenderer.bindTexture(getResource());
        GuiUtils.blit(relativeX, relativeY, 0, 0, width, height, type.getTextureWidth(), type.getTextureHeight());
        if (handler.isActive()) {
            int displayInt = (int) (Math.max(0, Math.min(handler.getProgress(), 1)) * height);
            if (displayInt > 0) {
                GuiUtils.blit(relativeX, relativeY + height - displayInt, width, height - displayInt, width, displayInt, type.getTextureWidth(), type.getTextureHeight());
            }
        }
        MekanismRenderer.resetColor();
    }
}
