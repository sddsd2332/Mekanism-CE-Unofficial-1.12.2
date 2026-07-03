package mekanism.generators.client.gui.element;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiTexturedElement;
import mekanism.client.render.MekanismRenderer;
import mekanism.generators.common.MekanismGenerators;
import net.minecraft.util.ResourceLocation;

import java.util.function.BooleanSupplier;

public class GuiStateTexture extends GuiTexturedElement {

    private static final ResourceLocation STATE_HOLDER = new ResourceLocation(MekanismGenerators.MODID, "gui/state_holder.png");

    private final BooleanSupplier onSupplier;
    private final ResourceLocation onTexture;
    private final ResourceLocation offTexture;

    public GuiStateTexture(IGuiWrapper gui, int x, int y, BooleanSupplier onSupplier, ResourceLocation onTexture, ResourceLocation offTexture) {
        super(STATE_HOLDER, gui, x, y, 16, 16);
        this.onSupplier = onSupplier;
        this.onTexture = onTexture;
        this.offTexture = offTexture;
        active = false;
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        MekanismRenderer.bindTexture(getResource());
        GuiUtils.blit(relativeX, relativeY, 0, 0, width, height, width, height);
        ResourceLocation resource = onSupplier.getAsBoolean() ? onTexture : offTexture;
        MekanismRenderer.bindTexture(resource);
        GuiUtils.blit(relativeX + 2, relativeY + 2, 0, 0, width - 4, height - 4, width - 4, height - 4);
        MekanismRenderer.resetColor();
    }
}
