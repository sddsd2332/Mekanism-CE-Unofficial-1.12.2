package mekanism.client.gui.element.bar;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.lib.Color.ColorFunction;
import mekanism.common.util.MekanismUtils;
import net.minecraft.util.ResourceLocation;

import static mekanism.client.gui.GuiUtils.blit;

public class GuiDynamicHorizontalRateBar extends GuiBar<IBarInfoHandler> {
    private static final ResourceLocation RATE_BAR = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_BAR, "dynamic_rate.png");
    private static final int texWidth = 3;
    private static final int texHeight = 8;

    private final ColorFunction colorFunction;


    public GuiDynamicHorizontalRateBar(IGuiWrapper gui, IBarInfoHandler handler, int x, int y, int width) {
        this(gui, handler, x, y, width, ColorFunction.HEAT);
    }

    public GuiDynamicHorizontalRateBar(IGuiWrapper gui, IBarInfoHandler handler, int x, int y, int width, ColorFunction colorFunction) {
        super(RATE_BAR, gui, handler, x, y, width, texHeight, true);
        this.colorFunction = colorFunction;
    }

    @Override
    protected void renderBarOverlay(int mouseX, int mouseY, float partialTicks, double handlerLevel) {
        int displayInt = (int) (handlerLevel * (width - 2));
        if (displayInt > 0) {
            for (int i = 0; i < displayInt; i++) {
                float level = i / (float) (width - 2);
                MekanismRenderer.color(colorFunction.getColor(level));
                if (i == 0) {
                    blit(relativeX + 1, relativeY + 1, 0, 0, 1, texHeight, texWidth, texHeight);
                } else if (i == displayInt - 1) {
                    blit(relativeX + 1 + i, relativeY + 1, texWidth - 1, 0, 1, texHeight, texWidth, texHeight);
                } else {
                    blit(relativeX + 1 + i, relativeY + 1, 1, 0, 1, texHeight, texWidth, texHeight);
                }
            }
            MekanismRenderer.resetColor();
        }
    }
}
