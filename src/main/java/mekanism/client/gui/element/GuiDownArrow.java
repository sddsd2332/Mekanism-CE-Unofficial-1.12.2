package mekanism.client.gui.element;

import mekanism.client.gui.IGuiWrapper;
import mekanism.common.util.MekanismUtils;
import net.minecraft.util.ResourceLocation;

public class GuiDownArrow extends GuiTextureOnlyElement {

    private static final ResourceLocation ARROW = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "down_arrow.png");

    public GuiDownArrow(IGuiWrapper gui, int x, int y) {
        super(ARROW, gui, x, y, 8, 9);
    }
}
