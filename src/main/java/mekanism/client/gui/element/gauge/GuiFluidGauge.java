package mekanism.client.gui.element.gauge;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.transmitters.TransmissionType;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class GuiFluidGauge extends GuiTankGauge<FluidStack, IExtendedFluidTank> {

    @Nullable
    private ITextComponent label;

    public GuiFluidGauge(IGuiWrapper gui, IExtendedFluidTank fluidTank, int x, int y) {
        this(gui, fluidTank, Type.WIDE, x, y);
    }

    public GuiFluidGauge(IGuiWrapper gui, IExtendedFluidTank fluidTank, Type type, int x, int y) {
        this(gui, fluidTank, type.asGaugeType(), x, y);
    }

    public GuiFluidGauge(IGuiWrapper gui, IExtendedFluidTank fluidTank, GaugeType type, int x, int y) {
        this(() -> fluidTank, () -> Collections.singletonList(fluidTank), type, gui, x, y);
    }

    public GuiFluidGauge(Supplier<IExtendedFluidTank> tankSupplier, Supplier<List<IExtendedFluidTank>> tanksSupplier, GaugeType type, IGuiWrapper gui, int x, int y) {
        this(tankSupplier, tanksSupplier, type, gui, x, y, type.getGaugeOverlay().getWidth() + 2, type.getGaugeOverlay().getHeight() + 2);
    }

    public GuiFluidGauge(Supplier<IExtendedFluidTank> tankSupplier, Supplier<List<IExtendedFluidTank>> tanksSupplier, GaugeType type, IGuiWrapper gui, int x, int y,
          int sizeX, int sizeY) {
        super(type, gui, x, y, sizeX, sizeY, new ITankInfoHandler<IExtendedFluidTank>() {
            @Nullable
            @Override
            public IExtendedFluidTank getTank() {
                return tankSupplier.get();
            }

            @Override
            public int getTankIndex() {
                IExtendedFluidTank tank = getTank();
                return tank == null ? -1 : tanksSupplier.get().indexOf(tank);
            }
        });
    }

    public GuiFluidGauge withColor(@Nonnull GaugeColor gaugeColor) {
        setGaugeColor(gaugeColor.info);
        return this;
    }

    public GuiFluidGauge warning(@Nonnull WarningType type, @Nonnull BooleanSupplier warningSupplier) {
        super.warning(type, warningSupplier);
        return this;
    }

    public GuiFluidGauge setLabel(ITextComponent label) {
        this.label = label;
        return this;
    }

    @Override
    public TransmissionType getTransmission() {
        return TransmissionType.FLUID;
    }

    @Override
    public int getScaledLevel() {
        int fillDimension = getFillDimension();
        if (dummy) {
            return fillDimension;
        }
        IExtendedFluidTank fluidTank = getTank();
        if (fluidTank == null || fluidTank.isEmpty() || fluidTank.getCapacity() == 0) {
            return 0;
        }
        if (fluidTank.getFluidAmount() == Integer.MAX_VALUE) {
            return fillDimension;
        }
        double scale = Math.max(Math.min((double) fluidTank.getFluidAmount() / fluidTank.getCapacity(), 1D), 0D);
        return Math.max(1, MekanismUtils.clampToInt(Math.round(scale * fillDimension)));
    }

    @Nullable
    @Override
    public TextureAtlasSprite getIcon() {
        IExtendedFluidTank fluidTank = getTank();
        if (dummy || fluidTank == null || fluidTank.isEmpty()) {
            return null;
        }
        FluidStack fluid = fluidTank.getFluid();
        return fluid == null ? null : MekanismRenderer.getFluidTexture(fluid, MekanismRenderer.FluidType.STILL);
    }

    @Nullable
    @Override
    public ITextComponent getLabel() {
        return label;
    }

    @Override
    public List<String> getTooltipText() {
        IExtendedFluidTank fluidTank = getTank();
        if (fluidTank == null || fluidTank.isEmpty()) {
            return Collections.singletonList(LangUtils.localize("gui.empty"));
        }
        FluidStack fluid = fluidTank.getFluid();
        if (fluid == null) {
            return Collections.singletonList(LangUtils.localize("gui.empty"));
        }
        int amount = fluidTank.getFluidAmount();
        String amountText = amount == Integer.MAX_VALUE ? LangUtils.localize("gui.infinite") : amount + " mB";
        return Collections.singletonList(LangUtils.localizeFluidStack(fluid) + ": " + amountText);
    }

    @Override
    protected void applyRenderColor() {
        IExtendedFluidTank fluidTank = getTank();
        if (fluidTank != null && !fluidTank.isEmpty()) {
            MekanismRenderer.color(fluidTank.getFluid());
        }
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
        STANDARD(GaugeOverlay.STANDARD),
        SMALL(GaugeOverlay.SMALL),
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
}
