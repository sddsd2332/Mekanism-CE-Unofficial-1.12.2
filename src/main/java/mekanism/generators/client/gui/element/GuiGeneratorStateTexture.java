package mekanism.generators.client.gui.element;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.render.MekanismRenderer;
import net.minecraft.util.ResourceLocation;

import java.util.function.BooleanSupplier;

public class GuiGeneratorStateTexture extends GuiElement {

    private static final ResourceLocation STATE_HOLDER = new ResourceLocation("mekanismgenerators", "gui/state_holder.png");

    private final BooleanSupplier onSupplier;
    private final Icon onIcon;
    private final Icon offIcon;

    public GuiGeneratorStateTexture(IGuiWrapper gui, int x, int y, BooleanSupplier onSupplier, Icon onIcon, Icon offIcon) {
        super(gui, x, y, 16, 16);
        this.onSupplier = onSupplier;
        this.onIcon = onIcon;
        this.offIcon = offIcon;
        active = false;
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTicks) {
        MekanismRenderer.bindTexture(STATE_HOLDER);
        GuiUtils.blit(relativeX, relativeY, 0, 0, 16, 16, 16, 16);
        Icon icon = onSupplier.getAsBoolean() ? onIcon : offIcon;
        MekanismRenderer.bindTexture(icon.resource);
        GuiUtils.blit(relativeX + 2, relativeY + 2, 0, 0, 12, 12, 12, 12);
        MekanismRenderer.resetColor();
    }

    public enum Icon {
        WIND_OFF("wind_off.png"),
        WIND_ON("wind_on.png"),
        NO_SUN("no_sun.png"),
        SEES_SUN("sees_sun.png");

        private final ResourceLocation resource;

        Icon(String texture) {
            this.resource = new ResourceLocation("mekanismgenerators", "gui/" + texture);
        }
    }
}
