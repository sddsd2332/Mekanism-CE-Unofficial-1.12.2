package mekanism.client.gui.element.button;

import mekanism.api.text.TextComponentGroup;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.render.MekanismRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.function.Supplier;

import static mekanism.client.gui.GuiUtils.blit;

public class MekanismImageButton extends MekanismButton {

    private final ResourceLocation resourceLocation;
    private final int textureWidth;
    private final int textureHeight;
    @Nullable
    private Supplier<ITextComponent> tooltipSupplier;

    public MekanismImageButton(IGuiWrapper gui, int x, int y, int size, ResourceLocation resource, Runnable onPress) {
        this(gui, x, y, size, size, resource, onPress);
    }

    public MekanismImageButton(IGuiWrapper gui, int x, int y, int size, ResourceLocation resource, Runnable onPress, IHoverable onHover) {
        this(gui, x, y, size, size, resource, onPress, onHover);
    }

    public MekanismImageButton(IGuiWrapper gui, int x, int y, int size, int textureSize, ResourceLocation resource, Runnable onPress) {
        this(gui, x, y, size, textureSize, resource, onPress, null);
    }

    public MekanismImageButton(IGuiWrapper gui, int x, int y, int size, int textureSize, ResourceLocation resource, Runnable onPress, IHoverable onHover) {
        this(gui, x, y, size, size, textureSize, textureSize, resource, onPress, onHover);
    }

    public MekanismImageButton(IGuiWrapper gui, int x, int y, int width, int height, int textureWidth, int textureHeight, ResourceLocation resource, Runnable onPress) {
        this(gui, x, y, width, height, textureWidth, textureHeight, resource, onPress, (IHoverable) null);
    }

    public MekanismImageButton(IGuiWrapper gui, int x, int y, int width, int height, int textureWidth, int textureHeight, ResourceLocation resource, Runnable onPress, IHoverable onHover) {
        super(gui, x, y, width, height, new TextComponentGroup(), onPress, onHover);
        this.resourceLocation = resource;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    public MekanismImageButton(IGuiWrapper gui, int x, int y, int width, int height, int textureWidth, int textureHeight, ResourceLocation resource,
          Runnable onLeftClick, Runnable onRightClick) {
        this(gui, x, y, width, height, textureWidth, textureHeight, resource, onLeftClick, onRightClick, null);
    }

    public MekanismImageButton(IGuiWrapper gui, int x, int y, int width, int height, int textureWidth, int textureHeight, ResourceLocation resource,
          Runnable onLeftClick, Runnable onRightClick, IHoverable onHover) {
        super(gui, x, y, width, height, new TextComponentGroup(), onLeftClick, onRightClick, onHover);
        this.resourceLocation = resource;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        MekanismRenderer.bindTexture(getResource());
        blit(getButtonX(), getButtonY(), getButtonWidth(), getButtonHeight(), 0, 0, textureWidth, textureHeight, textureWidth, textureHeight);
    }

    public MekanismImageButton setTooltip(ITextComponent tooltip) {
        return setTooltip(() -> tooltip);
    }

    public MekanismImageButton setTooltip(String tooltip) {
        return setTooltip(new TextComponentString(tooltip));
    }

    public MekanismImageButton setTooltip(Supplier<ITextComponent> tooltipSupplier) {
        this.tooltipSupplier = tooltipSupplier;
        return this;
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        if (tooltipSupplier != null) {
            displayTooltip(tooltipSupplier.get(), mouseX, mouseY);
        }
    }

    protected ResourceLocation getResource() {
        return resourceLocation;
    }
}
