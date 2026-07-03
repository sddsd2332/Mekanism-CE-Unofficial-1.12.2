package mekanism.client.gui.element.gauge;

import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.util.ResourceLocation;

public enum GaugeOverlay {
    SMALL(16, 28, "small.png"),
    SMALL_MED(16, 46, "small_med.png"),
    STANDARD(16, 58, "standard.png"),
    MEDIUM(32, 58, "medium.png"),
    WIDE(64, 48, "wide.png"),
    SLOT(16,16, "slot.png");

    private final int width;
    private final int height;
    private final ResourceLocation resource;

    GaugeOverlay(int width, int height, String texture) {
        this.width = width;
        this.height = height;
        this.resource = MekanismUtils.getResource(ResourceType.GUI_GAUGE, texture);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public ResourceLocation getResource() {
        return resource;
    }
}
