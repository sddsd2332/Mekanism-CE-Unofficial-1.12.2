package mekanism.client.gui.element.gauge;

import mekanism.api.EnumColor;
import mekanism.api.transmitters.TransmissionType;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.GuiUtils.TilingDirection;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiTexturedElement;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.inventory.warning.ISupportsWarning;
import mekanism.common.item.ItemConfigurator;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

public abstract class GuiGauge<T> extends GuiTexturedElement implements ISupportsWarning<GuiGauge<T>> {

    private final GaugeType gaugeType;
    protected boolean dummy;
    protected T dummyType;
    @Nullable
    private BooleanSupplier warningSupplier;
    @Nullable
    private GaugeInfo colorOverride;

    public GuiGauge(GaugeType gaugeType, IGuiWrapper gui, int x, int y) {
        this(gaugeType, gui, x, y, gaugeType.getGaugeOverlay().getWidth() + 2, gaugeType.getGaugeOverlay().getHeight() + 2);
    }

    public GuiGauge(GaugeType gaugeType, IGuiWrapper gui, int x, int y, int sizeX, int sizeY) {
        super(gaugeType.getGaugeOverlay().getResource(), gui, x, y, sizeX, sizeY);
        this.gaugeType = gaugeType;
    }

    @Override
    public GuiGauge<T> warning(@Nonnull mekanism.common.inventory.warning.WarningTracker.WarningType type, @Nonnull BooleanSupplier warningSupplier) {
        this.warningSupplier = ISupportsWarning.compound(this.warningSupplier, gui().trackWarning(type, warningSupplier));
        return this;
    }

    public GuiGauge<T> warning(@Nonnull mekanism.client.gui.warning.WarningTracker.WarningType type, @Nonnull BooleanSupplier warningSupplier) {
        BooleanSupplier trackedWarning = gui().trackWarning(type, warningSupplier);
        this.warningSupplier = ISupportsWarning.compound(this.warningSupplier, trackedWarning);
        return this;
    }

    public abstract int getScaledLevel();

    @Nullable
    public abstract TextureAtlasSprite getIcon();

    @Nullable
    public abstract ITextComponent getLabel();

    public abstract List<String> getTooltipText();

    @Nullable
    public abstract TransmissionType getTransmission();

    protected boolean isVertical() {
        return true;
    }

    protected int getFillDimension() {
        return (isVertical() ? height : width) - 2;
    }

    public GaugeOverlay getGaugeOverlay() {
        return gaugeType.getGaugeOverlay();
    }

    protected GaugeInfo getGaugeColor() {
        return colorOverride == null ? gaugeType.getGaugeInfo() : colorOverride;
    }

    @Nullable
    protected GaugeInfo getColorOverride() {
        return colorOverride;
    }

    protected void setGaugeColor(GaugeInfo colorOverride) {
        this.colorOverride = colorOverride;
    }

    protected void applyRenderColor() {
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        GaugeInfo color = getGaugeColor();
        GuiUtils.renderExtendedTexture(color.getResourceLocation(), color.getSideWidth(), color.getSideHeight(), relativeX, relativeY, width, height);
        MekanismRenderer.resetColor();
        if (!dummy) {
            renderContents();
        }
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTicks) {
    }

    public void renderContents() {
        boolean warning = warningSupplier != null && warningSupplier.getAsBoolean();
        if (warning) {
            minecraft.renderEngine.bindTexture(WARNING_BACKGROUND_TEXTURE);
            GuiUtils.blit(relativeX + 1, relativeY + 1, 0, 0, width - 2, height - 2, 256, 256);
        }
        int scale = getScaledLevel();
        TextureAtlasSprite icon = getIcon();
        if (scale > 0 && icon != null) {
            applyRenderColor();
            minecraft.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            if (isVertical()) {
                drawTiledSprite(relativeX + 1, relativeY + 1, height - 2, width - 2, scale, icon, TilingDirection.UP_RIGHT);
            } else {
                drawTiledSprite(relativeX + 1, relativeY + 1, height - 2, scale, height - 2, icon, TilingDirection.DOWN_RIGHT);
            }
            MekanismRenderer.resetColor();
            if (warning && (scale / (double) getFillDimension()) > 0.98) {
                int halfWidth = (width - 2) / 2;
                minecraft.renderEngine.bindTexture(WARNING_TEXTURE);
                GuiUtils.blit(relativeX + 1 + halfWidth, relativeY + 1, halfWidth, 0, halfWidth, height - 2, 256, 256);
            }
        }
        drawBarOverlay();
    }

    public void drawBarOverlay() {
        GaugeOverlay gaugeOverlay = getGaugeOverlay();
        MekanismRenderer.bindTexture(getResource());
        GuiUtils.blit(relativeX + 1, relativeY + 1, width - 2, height - 2, 0, 0, gaugeOverlay.getWidth(), gaugeOverlay.getHeight(),
              gaugeOverlay.getWidth(), gaugeOverlay.getHeight());
        MekanismRenderer.resetColor();
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        if (dummy) {
            return;
        }
        List<String> tooltip = getTooltipForRender();
        if (!tooltip.isEmpty()) {
            gui().displayTooltips(tooltip, mouseX, mouseY);
        }
    }

    private List<String> getTooltipForRender() {
        ItemStack stack = minecraft.player.inventory.getItemStack();
        EnumColor color = getGaugeColor().getColor();
        if (!stack.isEmpty() && stack.getItem() instanceof ItemConfigurator && color != null) {
            if (gui() instanceof GuiMekanismTile<?, ?> guiTile) {
                TileEntity tile = guiTile.getTileEntity();
                if (tile instanceof ISideConfiguration sideConfig && sideConfig.getConfig() != null && getTransmission() != null) {
                    DataType dataType = null;
                    ConfigInfo config = sideConfig.getConfig().getConfigInfo(getTransmission());
                    if (config != null) {
                        for (DataType type : config.getSupportedDataTypes()) {
                            if (type.getColor() == color) {
                                dataType = type;
                                break;
                            }
                        }
                    }
                    if (dataType == null) {
                        return Collections.singletonList(color.getColoredName());
                    }
                    return Collections.singletonList(color + dataType.localize() + " (" + color.getColoredName() + ")");
                }
            }
            return Collections.emptyList();
        }
        List<String> tooltip = new ArrayList<>(getTooltipText());
        ITextComponent label = getLabel();
        if (label != null) {
            tooltip.add(0, label.getFormattedText());
        }
        return tooltip;
    }

    public void setDummyType(T type) {
        dummyType = type;
    }
}
