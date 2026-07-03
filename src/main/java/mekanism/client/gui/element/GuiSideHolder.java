package mekanism.client.gui.element;

import mekanism.client.SpecialColors;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.lib.ColorAtlas.ColorRegistryObject;
import mekanism.common.util.MekanismUtils;
import net.minecraft.util.ResourceLocation;

public class GuiSideHolder extends GuiTexturedElement {

    private static final ResourceLocation HOLDER_LEFT = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "holder_left.png");
    private static final ResourceLocation HOLDER_RIGHT = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "holder_right.png");
    private static final int HOLDER_TEXTURE_WIDTH = 26;
    private static final int HOLDER_TEXTURE_HEIGHT = 9;

    public static GuiSideHolder armorHolder(IGuiWrapper gui) {
        return create(gui, -26, 62, 98, true, true, SpecialColors.TAB_ARMOR_SLOTS);
    }

    public static GuiSideHolder rightArmorHolder(IGuiWrapper gui) {
        return create(gui, gui.getWidth(), 36, 98, false, true, SpecialColors.TAB_ARMOR_SLOTS);
    }

    public static GuiSideHolder create(IGuiWrapper gui, int x, int y, int height, boolean left, boolean slotHolder, ColorRegistryObject tabColor) {
        return new GuiSideHolder(gui, x, y, height, left, slotHolder, tabColor);
    }

    protected final boolean left;
    private final boolean slotHolder;
    private final ColorRegistryObject tabColor;

    public GuiSideHolder(IGuiWrapper gui, int x, int y, int height, boolean left, boolean slotHolder) {
        this(gui, x, y, height, left, slotHolder, SpecialColors.TAB_ARMOR_SLOTS);
    }

    private GuiSideHolder(IGuiWrapper gui, int x, int y, int height, boolean left, boolean slotHolder, ColorRegistryObject tabColor) {
        super(left ? HOLDER_LEFT : HOLDER_RIGHT, gui, x, y, HOLDER_TEXTURE_WIDTH, height);
        this.left = left;
        this.slotHolder = slotHolder;
        this.tabColor = tabColor;
        active = false;
        if (!this.slotHolder) {
            setButtonBackground(ButtonBackground.DEFAULT);
        }
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTicks) {
        if (slotHolder) {
            draw();
        }
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        if (!slotHolder) {
            draw();
        }
    }

    protected void colorTab() {
        MekanismRenderer.color(tabColor.argb());
    }

    protected void drawUncolored() {
        GuiUtils.blitNineSlicedSized(getResource(), relativeX, relativeY, width, height, 4, HOLDER_TEXTURE_WIDTH, HOLDER_TEXTURE_HEIGHT, 0, 0, HOLDER_TEXTURE_WIDTH, HOLDER_TEXTURE_HEIGHT);
    }

    private void draw() {
        colorTab();
        drawUncolored();
        MekanismRenderer.resetColor();
    }
}
