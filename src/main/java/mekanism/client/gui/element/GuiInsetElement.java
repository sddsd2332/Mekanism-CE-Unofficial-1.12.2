package mekanism.client.gui.element;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.inventory.warning.ISupportsWarning;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;

public abstract class GuiInsetElement<DATA_SOURCE> extends GuiSideHolder implements ISupportsWarning<GuiInsetElement<DATA_SOURCE>> {

    protected final int border;
    protected final int innerWidth;
    protected final int innerHeight;
    protected final DATA_SOURCE dataSource;
    protected final ResourceLocation overlay;

    @Nullable
    protected BooleanSupplier warningSupplier;

    public GuiInsetElement(ResourceLocation overlay, IGuiWrapper gui, DATA_SOURCE dataSource, int x, int y, int height, int innerSize, boolean left) {
        super(gui, x, y, height, left, false);
        this.overlay = overlay;
        this.dataSource = dataSource;
        this.innerWidth = innerSize;
        this.innerHeight = innerSize;
        this.border = (width - innerWidth) / 2;
        playClickSound = true;
        active = true;
    }

    public GuiInsetElement<DATA_SOURCE> warning(@Nonnull mekanism.client.gui.warning.WarningTracker.WarningType type, @Nonnull BooleanSupplier warningSupplier) {
        this.warningSupplier = ISupportsWarning.compound(this.warningSupplier, gui().trackWarning(type, warningSupplier));
        return this;
    }

    @Override
    public GuiInsetElement<DATA_SOURCE> warning(@Nonnull mekanism.common.inventory.warning.WarningTracker.WarningType type, @Nonnull BooleanSupplier warningSupplier) {
        this.warningSupplier = ISupportsWarning.compound(this.warningSupplier, gui().trackWarning(type, warningSupplier));
        return this;
    }

    @Override
    public boolean isMouseOver(double xAxis, double yAxis) {
        return active && visible && xAxis >= getX() + border && xAxis < getRight() - border && yAxis >= getY() + border && yAxis < getBottom() - border;
    }

    @Override
    protected int getButtonX() {
        return super.getButtonX() + border + (left ? 1 : -1);
    }

    @Override
    protected int getButtonY() {
        return super.getButtonY() + border;
    }

    @Override
    protected int getButtonWidth() {
        return innerWidth;
    }

    @Override
    protected int getButtonHeight() {
        return innerHeight;
    }

    protected ResourceLocation getOverlay() {
        return overlay;
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        if (warningSupplier != null && warningSupplier.getAsBoolean()) {
            drawUncolored();
            MekanismRenderer.bindTexture(WARNING_TEXTURE);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ZERO);
            GuiUtils.blit(relativeX, relativeY, 0, 0, width, height, 256, 256);
            GlStateManager.disableBlend();
            MekanismRenderer.resetColor();
        } else {
            super.drawBackground(mouseX, mouseY, partialTicks);
        }
        if (buttonBackground != ButtonBackground.NONE) {
            drawButton(mouseX, mouseY);
        }
        drawBackgroundOverlay();
    }

    protected void drawBackgroundOverlay() {
        MekanismRenderer.bindTexture(getOverlay());
        GuiUtils.blit(getButtonX(), getButtonY(), 0, 0, innerWidth, innerHeight, innerWidth, innerHeight);
        MekanismRenderer.resetColor();
    }
}
