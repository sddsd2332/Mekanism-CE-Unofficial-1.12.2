package mekanism.client.gui.element.button;

import mekanism.client.gui.IGuiWrapper;
import mekanism.common.util.MekanismUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;

public class ToggleButton extends MekanismImageButton {

    private static final ResourceLocation TOGGLE = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_BUTTON, "toggle.png");
    private static final ResourceLocation TOGGLE_FLIPPED = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_BUTTON, "toggle_flipped.png");

    private final ResourceLocation flipped;
    private final BooleanSupplier toggled;
    @Nullable
    private final ITextComponent yesTooltip;
    @Nullable
    private final ITextComponent noTooltip;

    public ToggleButton(IGuiWrapper gui, int x, int y, BooleanSupplier toggled, Runnable onPress) {
        this(gui, x, y, 18, toggled, onPress);
    }

    public ToggleButton(IGuiWrapper gui, int x, int y, int size, BooleanSupplier toggled, Runnable onPress) {
        this(gui, x, y, size, 18, TOGGLE, TOGGLE_FLIPPED, toggled, onPress, null, null);
    }

    public ToggleButton(IGuiWrapper gui, int x, int y, int size, BooleanSupplier toggled, Runnable onPress, @Nullable ITextComponent yesTooltip,
          @Nullable ITextComponent noTooltip) {
        this(gui, x, y, size, 18, TOGGLE, TOGGLE_FLIPPED, toggled, onPress, yesTooltip, noTooltip);
    }

    public ToggleButton(IGuiWrapper gui, int x, int y, BooleanSupplier toggled, Runnable onPress, @Nullable ITextComponent yesTooltip,
          @Nullable ITextComponent noTooltip) {
        this(gui, x, y, 18, 18, 18, 18, TOGGLE, TOGGLE_FLIPPED, toggled, onPress, yesTooltip, noTooltip);
    }

    public ToggleButton(IGuiWrapper gui, int x, int y, int size, int textureSize, ResourceLocation toggle, ResourceLocation flipped, BooleanSupplier toggled,
          Runnable onPress, @Nullable ITextComponent yesTooltip, @Nullable ITextComponent noTooltip) {
        this(gui, x, y, size, size, textureSize, textureSize, toggle, flipped, toggled, onPress, yesTooltip, noTooltip);
    }

    public ToggleButton(IGuiWrapper gui, int x, int y, int width, int height, int textureWidth, int textureHeight, ResourceLocation toggle, ResourceLocation flipped,
          BooleanSupplier toggled, Runnable onPress, @Nullable ITextComponent yesTooltip, @Nullable ITextComponent noTooltip) {
        super(gui, x, y, width, height, textureWidth, textureHeight, toggle, onPress);
        this.flipped = flipped;
        this.toggled = toggled;
        this.yesTooltip = yesTooltip;
        this.noTooltip = noTooltip;
    }

    @Override
    protected ResourceLocation getResource() {
        return toggled.getAsBoolean() ? flipped : super.getResource();
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        ITextComponent tooltip = toggled.getAsBoolean() ? yesTooltip : noTooltip;
        if (tooltip != null) {
            displayTooltip(tooltip, mouseX, mouseY);
        } else {
            super.renderToolTip(mouseX, mouseY);
        }
    }
}
