package mekanism.client.gui.element;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.util.ResourceLocation;

import java.util.function.BooleanSupplier;

public class GuiBigLight extends GuiTexturedElement {

    private static final ResourceLocation LIGHTS = MekanismUtils.getResource(ResourceType.GUI, "big_lights.png");

    private final BooleanSupplier lightSupplier;

    public GuiBigLight(IGuiWrapper gui, int x, int y, BooleanSupplier lightSupplier) {
        super(LIGHTS, gui, x, y, 14, 14);
        this.lightSupplier = lightSupplier;
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        GuiUtils.renderBackgroundTexture(GuiInnerScreen.SCREEN, GuiInnerScreen.SCREEN_SIZE, GuiInnerScreen.SCREEN_SIZE, relativeX, relativeY, width, height,
              GuiInnerScreen.SCREEN_SIZE, GuiInnerScreen.SCREEN_SIZE);
        MekanismRenderer.bindTexture(getResource());
        GuiUtils.blit(relativeX + 1, relativeY + 1, lightSupplier.getAsBoolean() ? 0 : 12, 0, width - 2, height - 2, 24, 12);
    }
}
