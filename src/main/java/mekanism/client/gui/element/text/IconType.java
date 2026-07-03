package mekanism.client.gui.element.text;

import mekanism.common.util.MekanismUtils;
import net.minecraft.util.ResourceLocation;

public enum IconType {
    DIGITAL(MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "digital_text_input.png"), 4, 7);

    private final ResourceLocation icon;
    private final int xSize, ySize;

    IconType(ResourceLocation icon, int xSize, int ySize) {
        this.icon = icon;
        this.xSize = xSize;
        this.ySize = ySize;
    }

    public ResourceLocation getIcon() {
        return icon;
    }

    public int getWidth() {
        return xSize;
    }

    public int getHeight() {
        return ySize;
    }

    public int getOffsetX() {
        return xSize + 4;
    }
}