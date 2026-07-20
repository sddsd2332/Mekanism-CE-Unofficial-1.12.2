package mekanism.client.gui.element.bar;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.common.util.MekanismUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

/** Digital QIO capacity bar matching the current Mekanism layout. */
public class GuiDigitalBar extends GuiBar<IBarInfoHandler> {

    private static final ResourceLocation DIGITAL_BAR = MekanismUtils.getResource(
          MekanismUtils.ResourceType.GUI_BAR, "dynamic_digital.png");
    private static final int TEXTURE_SIZE = 2;

    public GuiDigitalBar(IGuiWrapper gui, IBarInfoHandler handler, int x, int y, int width) {
        super(DIGITAL_BAR, gui, handler, x, y, width - 2, 6, true);
    }

    @Override
    protected void renderBarOverlay(int mouseX, int mouseY, float partialTicks, double handlerLevel) {
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        minecraft.renderEngine.bindTexture(DIGITAL_BAR);
        GuiUtils.blit(relativeX, relativeY, width, height, 1, 0, 1, 1, TEXTURE_SIZE, TEXTURE_SIZE);
        GuiUtils.blit(relativeX + 1, relativeY + 1, width - 2, height - 2,
              1, 1, 1, 1, TEXTURE_SIZE, TEXTURE_SIZE);
        int displayWidth = calculateScaled(MathHelper.clamp(getHandler().getLevel(), 0, 1), width - 2);
        if (displayWidth > 0) {
            GuiUtils.blit(relativeX + 1, relativeY + 1, displayWidth, height - 2,
                  0, 0, 1, 1, TEXTURE_SIZE, TEXTURE_SIZE);
        }
    }
}
