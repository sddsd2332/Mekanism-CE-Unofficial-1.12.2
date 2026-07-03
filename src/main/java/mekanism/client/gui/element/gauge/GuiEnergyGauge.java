package mekanism.client.gui.element.gauge;

import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IStrictEnergyStorage;
import mekanism.api.transmitters.TransmissionType;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

public class GuiEnergyGauge extends GuiGauge<Void> {

    private final IEnergyInfoHandler infoHandler;

    public GuiEnergyGauge(IGuiWrapper gui, IStrictEnergyStorage energyContainer, Type type, int x, int y) {
        this(energyContainer, type.asGaugeType(), gui, x, y);
    }

    public GuiEnergyGauge(IStrictEnergyStorage energyContainer, GaugeType type, IGuiWrapper gui, int x, int y) {
        this(new IEnergyInfoHandler() {
            @Override
            public double getEnergy() {
                return energyContainer.getEnergy();
            }

            @Override
            public double getMaxEnergy() {
                return energyContainer.getMaxEnergy();
            }
        }, type, gui, x, y);
    }

    public GuiEnergyGauge(IEnergyContainer energyContainer, GaugeType type, IGuiWrapper gui, int x, int y) {
        this((IStrictEnergyStorage) energyContainer, type, gui, x, y);
    }

    public GuiEnergyGauge(IEnergyInfoHandler handler, GaugeType type, IGuiWrapper gui, int x, int y) {
        super(type, gui, x, y);
        infoHandler = handler;
    }

    public GuiEnergyGauge(IEnergyInfoHandler handler, GaugeType type, IGuiWrapper gui, int x, int y, int sizeX, int sizeY) {
        super(type, gui, x, y, sizeX, sizeY);
        infoHandler = handler;
    }

    public GuiEnergyGauge withColor(@Nonnull GaugeColor gaugeColor) {
        setGaugeColor(gaugeColor.info);
        return this;
    }

    public GuiEnergyGauge warning(@Nonnull WarningType type, @Nonnull BooleanSupplier warningSupplier) {
        super.warning(type, warningSupplier);
        return this;
    }

    @Override
    public TransmissionType getTransmission() {
        return TransmissionType.ENERGY;
    }

    @Override
    public int getScaledLevel() {
        if (dummy) {
            return height - 2;
        }
        double energy = infoHandler.getEnergy();
        double maxEnergy = infoHandler.getMaxEnergy();
        if (energy <= 0 || maxEnergy <= 0) {
            return 0;
        }
        if (energy == Double.MAX_VALUE) {
            return height - 2;
        }
        double scale = Math.max(Math.min(energy / maxEnergy, 1D), 0D);
        return Math.max(1, (int) (scale * (height - 2)));
    }

    @Nullable
    @Override
    public TextureAtlasSprite getIcon() {
        return MekanismRenderer.energyIcon;
    }

    @Nullable
    @Override
    public ITextComponent getLabel() {
        return null;
    }

    @Override
    public List<String> getTooltipText() {
        return Collections.singletonList(infoHandler.getEnergy() > 0 ? MekanismUtils.getEnergyDisplay(infoHandler.getEnergy(), infoHandler.getMaxEnergy()) : LangUtils.localize("gui.empty"));
    }

    public enum GaugeColor {
        NORMAL(GaugeInfo.STANDARD),
        RED(GaugeInfo.RED),
        BLUE(GaugeInfo.BLUE),
        AQUA(GaugeInfo.AQUA),
        ORANGE(GaugeInfo.ORANGE),
        YELLOW(GaugeInfo.YELLOW);

        private final GaugeInfo info;

        GaugeColor(GaugeInfo info) {
            this.info = info;
        }
    }

    public enum Type {
        MEDIUM(GaugeOverlay.MEDIUM),
        STANDARD(GaugeOverlay.STANDARD),
        SMALL(GaugeOverlay.SMALL),
        SMALL_MED(GaugeOverlay.SMALL_MED),
        WIDE(GaugeOverlay.WIDE);

        private final int width;
        private final int height;
        private final GaugeOverlay overlay;

        Type(GaugeOverlay overlay) {
            this.width = overlay.getWidth() + 2;
            this.height = overlay.getHeight() + 2;
            this.overlay = overlay;
        }

        public GaugeType asGaugeType() {
            return GaugeType.get(GaugeInfo.STANDARD, overlay);
        }
    }

    public interface IEnergyInfoHandler {

        double getEnergy();

        double getMaxEnergy();
    }
}
