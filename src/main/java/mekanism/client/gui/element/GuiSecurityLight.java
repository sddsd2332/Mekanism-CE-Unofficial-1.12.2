package mekanism.client.gui.element;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.util.MekanismUtils;
import net.minecraft.util.ResourceLocation;

import java.util.function.IntSupplier;

public class GuiSecurityLight extends GuiTexturedElement {

    private static final ResourceLocation LIGHTS = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "security_lights.png");

    private final IntSupplier lightSupplier;

    public GuiSecurityLight(IGuiWrapper gui, int x, int y, IntSupplier lightSupplier) {
        super(LIGHTS, gui, x, y, 8, 8);
        this.lightSupplier = lightSupplier;
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        GuiUtils.renderBackgroundTexture(GuiInnerScreen.SCREEN, GuiInnerScreen.SCREEN_SIZE, GuiInnerScreen.SCREEN_SIZE, relativeX, relativeY, width, height, 256, 256);
        MekanismRenderer.bindTexture(getResource());
        GuiUtils.blit(relativeX + 1, relativeY + 1, 6 * lightSupplier.getAsInt(), 0, width - 2, height - 2, 18, 6);
        MekanismRenderer.resetColor();
    }
}
