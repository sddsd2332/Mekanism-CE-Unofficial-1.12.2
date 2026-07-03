package mekanism.client.gui.element.gauge;

import mekanism.api.EnumColor;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.transmitters.TransmissionType;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.recipe.GasStackFuelToEnergyRecipe;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class GuiGasGauge extends GuiTankGauge<GasStack, IExtendedGasTank> {

    @Nullable
    private ITextComponent label;

    public GuiGasGauge(IGuiWrapper gui, IExtendedGasTank gasTank, int x, int y) {
        this(gui, gasTank, Type.STANDARD, x, y);
    }

    public GuiGasGauge(IGuiWrapper gui, IExtendedGasTank gasTank, Type type, int x, int y) {
        this(gui, () -> gasTank, type, x, y);
    }

    public GuiGasGauge(IGuiWrapper gui, Supplier<IExtendedGasTank> gasTankSupplier, Type type, int x, int y) {
        this(gasTankSupplier, () -> Collections.singletonList(gasTankSupplier.get()), type.asGaugeType(), gui, x, y);
    }

    public GuiGasGauge(Supplier<IExtendedGasTank> tankSupplier, Supplier<List<IExtendedGasTank>> tanksSupplier, GaugeType type, IGuiWrapper gui, int x, int y) {
        this(tankSupplier, tanksSupplier, type, gui, x, y, type.getGaugeOverlay().getWidth() + 2, type.getGaugeOverlay().getHeight() + 2);
    }

    public GuiGasGauge(Supplier<IExtendedGasTank> tankSupplier, Supplier<List<IExtendedGasTank>> tanksSupplier, GaugeType type, IGuiWrapper gui, int x, int y,
          int sizeX, int sizeY) {
        super(type, gui, x, y, sizeX, sizeY, new ITankInfoHandler<IExtendedGasTank>() {
            @Nullable
            @Override
            public IExtendedGasTank getTank() {
                return tankSupplier.get();
            }

            @Override
            public int getTankIndex() {
                IExtendedGasTank tank = getTank();
                return tank == null ? -1 : tanksSupplier.get().indexOf(tank);
            }
        });
    }

    public GuiGasGauge withColor(@Nonnull GaugeColor gaugeColor) {
        setGaugeColor(gaugeColor.info);
        return this;
    }

    public GuiGasGauge warning(@Nonnull WarningType type, @Nonnull BooleanSupplier warningSupplier) {
        super.warning(type, warningSupplier);
        return this;
    }

    public GuiGasGauge setLabel(ITextComponent label) {
        this.label = label;
        return this;
    }

    @Override
    public TransmissionType getTransmission() {
        return TransmissionType.GAS;
    }

    @Override
    public int getScaledLevel() {
        int fillDimension = getFillDimension();
        if (dummy) {
            return fillDimension;
        }
        IExtendedGasTank gasTank = getTank();
        GasStack gas = gasTank == null ? null : gasTank.getGas();
        if (gas == null || gasTank.getMaxGas() == 0) {
            return 0;
        }
        if (gasTank.getStored() == Integer.MAX_VALUE) {
            return fillDimension;
        }
        double scale = Math.max(Math.min((double) gasTank.getStored() / gasTank.getMaxGas(), 1D), 0D);
        return Math.max(1, MekanismUtils.clampToInt(Math.round(scale * fillDimension)));
    }

    @Nullable
    @Override
    public TextureAtlasSprite getIcon() {
        IExtendedGasTank gasTank = getTank();
        if (dummy || gasTank == null || gasTank.isEmpty()) {
            return null;
        }
        GasStack gas = gasTank.getGas();
        return gas == null || gas.getGas() == null ? null : gas.getGas().getSprite();
    }

    @Nullable
    @Override
    public ITextComponent getLabel() {
        return label;
    }

    @Override
    public List<String> getTooltipText() {
        List<String> tooltip = new ArrayList<>();
        IExtendedGasTank gasTank = getTank();
        GasStack stack = gasTank == null ? null : gasTank.getGas();
        if (stack == null || stack.getGas() == null) {
            tooltip.add(LangUtils.localize("gui.empty"));
            return tooltip;
        }
        String amountText = gasTank.getStored() == Integer.MAX_VALUE ? LangUtils.localize("gui.infinite") : Integer.toString(gasTank.getStored());
        tooltip.add(stack.getGas().getLocalizedName() + ": " + amountText);
        if (stack.getGas().isRadiation()) {
            tooltip.add(EnumColor.GREY + LangUtils.localize("chemical.mekanism.attribute.radiation") + EnumColor.INDIGO +
                  UnitDisplayUtils.getDisplayShort(stack.getGas().getRadioactivity(), UnitDisplayUtils.RadiationUnit.SVH, 2));
        }
        if (RecipeHandler.Recipe.GAS_FUEL_TO_ENERGY_RECIPE.containsRecipe(stack.getGas())) {
            GasStackFuelToEnergyRecipe recipe = RecipeHandler.getGasStackFuelToEnergyRecipe(stack);
            if (recipe != null) {
                tooltip.add(LangUtils.localize("chemical.mekanism.attribute.fuel.burn_ticks") + EnumColor.INDIGO + recipe.getInput().ingredient.amount +
                      TextFormatting.RESET + " t");
                tooltip.add(LangUtils.localize("chemical.mekanism.attribute.fuel.energy_density") + EnumColor.INDIGO +
                      MekanismUtils.getEnergyDisplay(recipe.getOutput().energyOutput * recipe.getInput().ingredient.amount));
            }
        }
        return tooltip;
    }

    @Override
    protected void applyRenderColor() {
        IExtendedGasTank gasTank = getTank();
        if (gasTank != null && !gasTank.isEmpty()) {
            MekanismRenderer.color(gasTank.getGas());
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
}
