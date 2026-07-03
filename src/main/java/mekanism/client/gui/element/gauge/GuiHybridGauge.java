package mekanism.client.gui.element.gauge;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.transmitters.TransmissionType;
import mekanism.client.gui.IGuiWrapper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

public class GuiHybridGauge extends GuiGauge<Void> {

    private final Supplier<IExtendedGasTank> gasTankSupplier;

    private final GuiGasGauge gasGauge;
    private final GuiFluidGauge fluidGauge;

    @Nullable
    private ITextComponent label;

    public GuiHybridGauge(Supplier<IExtendedGasTank> gasTankSupplier, Supplier<List<IExtendedGasTank>> gasTanksSupplier,
          Supplier<IExtendedFluidTank> fluidTankSupplier, Supplier<List<IExtendedFluidTank>> fluidTanksSupplier, GaugeType type,
          IGuiWrapper gui, int x, int y) {
        this(gasTankSupplier, gasTanksSupplier, fluidTankSupplier, fluidTanksSupplier, type, gui, x, y,
              type.getGaugeOverlay().getWidth() + 2, type.getGaugeOverlay().getHeight() + 2);
    }

    public GuiHybridGauge(Supplier<IExtendedGasTank> gasTankSupplier, Supplier<List<IExtendedGasTank>> gasTanksSupplier,
          Supplier<IExtendedFluidTank> fluidTankSupplier, Supplier<List<IExtendedFluidTank>> fluidTanksSupplier, GaugeType type,
          IGuiWrapper gui, int x, int y, int width, int height) {
        super(type, gui, x, y, width, height);
        this.gasTankSupplier = gasTankSupplier;
        gasGauge = addPositionOnlyChild(new GuiGasGauge(gasTankSupplier, gasTanksSupplier, type, gui, x, y, width, height));
        fluidGauge = addPositionOnlyChild(new GuiFluidGauge(fluidTankSupplier, fluidTanksSupplier, type, gui, x, y, width, height));
    }

    public GuiHybridGauge setLabel(@Nullable ITextComponent label) {
        this.label = label;
        return this;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Match the modern hybrid gauge: let both backing gauges try to consume the click.
        return gasGauge.mouseClicked(mouseX, mouseY, button) | fluidGauge.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void applyRenderColor() {
        gasGauge.applyRenderColor();
        fluidGauge.applyRenderColor();
    }

    @Override
    public int getScaledLevel() {
        return Math.max(gasGauge.getScaledLevel(), fluidGauge.getScaledLevel());
    }

    @Nullable
    @Override
    public TextureAtlasSprite getIcon() {
        return isGasTankEmpty() ? fluidGauge.getIcon() : gasGauge.getIcon();
    }

    @Nullable
    @Override
    public ITextComponent getLabel() {
        return label;
    }

    @Override
    public List<String> getTooltipText() {
        return isGasTankEmpty() ? fluidGauge.getTooltipText() : gasGauge.getTooltipText();
    }

    @Nullable
    @Override
    public TransmissionType getTransmission() {
        IExtendedGasTank gasTank = gasTankSupplier.get();
        return gasTank == null || !gasTank.isEmpty() ? TransmissionType.GAS : TransmissionType.FLUID;
    }

    private boolean isGasTankEmpty() {
        IExtendedGasTank gasTank = gasTankSupplier.get();
        return gasTank == null || gasTank.isEmpty();
    }
}
