package mekanism.client.gui.element.bar;

import mekanism.api.energy.IStrictEnergyStorage;
import mekanism.api.math.MathUtils;
import mekanism.api.text.TextComponentGroup;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.common.util.MekanismUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

import static mekanism.client.gui.GuiUtils.blit;

public class GuiVerticalPowerBar extends GuiBar<IBarInfoHandler> {

    private static final ResourceLocation ENERGY_BAR = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_BAR, "vertical_power.png");
    private static final int texWidth = 4;
    private static final int texHeight = 52;

    private final double heightScale;

    public GuiVerticalPowerBar(IGuiWrapper gui, IStrictEnergyStorage container, int x, int y) {
        this(gui, container, x, y, texHeight);
    }

    public GuiVerticalPowerBar(IGuiWrapper gui, IStrictEnergyStorage container, int x, int y, int desiredHeight) {
        this(gui, new IBarInfoHandler() {
            @Override
            public ITextComponent getTooltip() {
                return new TextComponentGroup().translation(MekanismUtils.getEnergyDisplay(container.getEnergy(), container.getMaxEnergy()));
            }

            @Override
            public double getLevel() {
                return MathUtils.divideToLevel(container.getEnergy(), container.getMaxEnergy());
            }
        }, x, y, desiredHeight);
    }

    public GuiVerticalPowerBar(IGuiWrapper gui, IBarInfoHandler handler, int x, int y) {
        this(gui, handler, x, y, texHeight);
    }

    public GuiVerticalPowerBar(IGuiWrapper gui, IBarInfoHandler handler, int x, int y, int desiredHeight) {
        super(ENERGY_BAR, gui, handler, x, y, texWidth, desiredHeight, false);
        heightScale = desiredHeight / (double) texHeight;
    }

    @Override
    protected void renderBarOverlay(int mouseX, int mouseY, float partialTicks, double handlerLevel) {
        int displayInt = (int) (handlerLevel * texHeight);
        if (displayInt > 0) {
            int scaled = calculateScaled(heightScale, displayInt);
            blit(relativeX + 1, relativeY + height - 1 - scaled, texWidth, scaled, 0, 0, texWidth, displayInt, texWidth, texHeight);
        }
    }
}
