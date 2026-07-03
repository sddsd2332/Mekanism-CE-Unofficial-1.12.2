package mekanism.client.gui.element.bar;

import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.math.MathUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.util.LangUtils;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class GuiGasBar extends GuiTankBar<GasStack, IExtendedGasTank> {

    private final Supplier<List<String>> tooltipSupplier;

    public GuiGasBar(IGuiWrapper gui, IExtendedGasTank gasTank, int x, int y, Supplier<List<String>> tooltipSupplier) {
        this(gui, gasTank, x, y, 116, 10, false, tooltipSupplier);
    }

    public GuiGasBar(IGuiWrapper gui, IExtendedGasTank gasTank, int x, int y, int width, int height, boolean vertical, Supplier<List<String>> tooltipSupplier) {
        this(gui, getProvider(gasTank, Collections.singletonList(gasTank)), x, y, width, height, !vertical, tooltipSupplier);
    }

    public GuiGasBar(IGuiWrapper gui, TankInfoProvider<GasStack, IExtendedGasTank> infoProvider, int x, int y, int width, int height, boolean horizontal,
          Supplier<List<String>> tooltipSupplier) {
        super(gui, infoProvider, x, y, width, height, horizontal);
        this.tooltipSupplier = tooltipSupplier;
    }

    @Override
    protected boolean isEmpty(@Nullable GasStack stack) {
        return stack == null || stack.amount <= 0 || stack.getGas() == null;
    }

    @Override
    protected void applyRenderColor(GasStack stack) {
        MekanismRenderer.color(stack);
    }

    @Nullable
    @Override
    protected TextureAtlasSprite getIcon(GasStack stack) {
        return stack.getGas().getSprite();
    }

    @Override
    protected List<String> getTooltip(GasStack stack) {
        List<String> tooltip = new ArrayList<>(tooltipSupplier.get());
        if (!tooltip.isEmpty()) {
            return tooltip;
        }
        return super.getTooltip(stack);
    }

    public static TankInfoProvider<GasStack, IExtendedGasTank> getProvider(IExtendedGasTank gasTank, List<IExtendedGasTank> tanks) {
        return new TankInfoProvider<>() {
            @Override
            @Nullable
            public GasStack getStack() {
                return gasTank.getGas();
            }

            @Override
            public IExtendedGasTank getTank() {
                return gasTank;
            }

            @Override
            public int getTankIndex() {
                return tanks.indexOf(gasTank);
            }

            @Nullable
            @Override
            public ITextComponent getTooltip() {
                GasStack gas = gasTank.getGas();
                if (gas == null) {
                    return new TextComponentString(LangUtils.localize("gui.empty"));
                }
                String amount = gasTank.getStored() == Integer.MAX_VALUE ? LangUtils.localize("gui.infinite") : Integer.toString(gasTank.getStored());
                return new TextComponentString(gas.getGas().getLocalizedName() + ": " + amount);
            }

            @Override
            public double getLevel() {
                return MathUtils.divideToLevel(gasTank.getStored(), gasTank.getMaxGas());
            }
        };
    }
}
