package mekanism.client.gui.element.button;

import mekanism.client.gui.IGuiWrapper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

import java.util.function.BooleanSupplier;

public class TooltipToggleButton extends MekanismImageButton {

    private final BooleanSupplier toggled;
    private final ITextComponent yesTooltip;
    private final ITextComponent noTooltip;

    public TooltipToggleButton(IGuiWrapper gui, int x, int y, int size, ResourceLocation resource, BooleanSupplier toggled, Runnable onPress,
          ITextComponent yesTooltip, ITextComponent noTooltip) {
        this(gui, x, y, size, size, resource, toggled, onPress, yesTooltip, noTooltip);
    }

    public TooltipToggleButton(IGuiWrapper gui, int x, int y, int size, int textureSize, ResourceLocation resource, BooleanSupplier toggled, Runnable onPress,
          ITextComponent yesTooltip, ITextComponent noTooltip) {
        this(gui, x, y, size, size, textureSize, textureSize, resource, toggled, onPress, yesTooltip, noTooltip);
    }

    public TooltipToggleButton(IGuiWrapper gui, int x, int y, int width, int height, int textureWidth, int textureHeight, ResourceLocation resource,
          BooleanSupplier toggled, Runnable onPress, ITextComponent yesTooltip, ITextComponent noTooltip) {
        super(gui, x, y, width, height, textureWidth, textureHeight, resource, onPress);
        this.toggled = toggled;
        this.yesTooltip = yesTooltip;
        this.noTooltip = noTooltip;
    }

    public TooltipToggleButton(IGuiWrapper gui, int x, int y, int size, ResourceLocation resource, BooleanSupplier toggled, Runnable onLeftClick,
          Runnable onRightClick, ITextComponent yesTooltip, ITextComponent noTooltip) {
        this(gui, x, y, size, size, resource, toggled, onLeftClick, onRightClick, yesTooltip, noTooltip);
    }

    public TooltipToggleButton(IGuiWrapper gui, int x, int y, int size, int textureSize, ResourceLocation resource, BooleanSupplier toggled, Runnable onLeftClick,
          Runnable onRightClick, ITextComponent yesTooltip, ITextComponent noTooltip) {
        this(gui, x, y, size, size, textureSize, textureSize, resource, toggled, onLeftClick, onRightClick, yesTooltip, noTooltip);
    }

    public TooltipToggleButton(IGuiWrapper gui, int x, int y, int width, int height, int textureWidth, int textureHeight, ResourceLocation resource,
          BooleanSupplier toggled, Runnable onLeftClick, Runnable onRightClick, ITextComponent yesTooltip, ITextComponent noTooltip) {
        super(gui, x, y, width, height, textureWidth, textureHeight, resource, onLeftClick, onRightClick, null);
        this.toggled = toggled;
        this.yesTooltip = yesTooltip;
        this.noTooltip = noTooltip;
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        displayTooltip(toggled.getAsBoolean() ? yesTooltip : noTooltip, mouseX, mouseY);
    }
}
