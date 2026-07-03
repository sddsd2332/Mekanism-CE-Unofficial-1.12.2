package mekanism.client.gui.element.bar;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.math.MathUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.util.LangUtils;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.List;

public class GuiFluidBar extends GuiTankBar<FluidStack, IExtendedFluidTank> {

    public GuiFluidBar(IGuiWrapper gui, IExtendedFluidTank fluidTank, int x, int y, int width, int height, boolean vertical) {
        this(gui, getProvider(fluidTank, java.util.Collections.singletonList(fluidTank)), x, y, width, height, !vertical);
    }

    public GuiFluidBar(IGuiWrapper gui, TankInfoProvider<FluidStack, IExtendedFluidTank> infoProvider, int x, int y, int width, int height, boolean horizontal) {
        super(gui, infoProvider, x, y, width, height, horizontal);
    }

    @Override
    protected boolean isEmpty(@Nullable FluidStack stack) {
        return stack == null || stack.amount <= 0 || stack.getFluid() == null;
    }

    @Override
    protected void applyRenderColor(FluidStack stack) {
        MekanismRenderer.color(stack);
    }

    @Nullable
    @Override
    protected TextureAtlasSprite getIcon(FluidStack stack) {
        return MekanismRenderer.getFluidTexture(stack, MekanismRenderer.FluidType.STILL);
    }

    public static TankInfoProvider<FluidStack, IExtendedFluidTank> getProvider(IExtendedFluidTank fluidTank, List<IExtendedFluidTank> tanks) {
        return new TankInfoProvider<>() {
            @Override
            @Nullable
            public FluidStack getStack() {
                return fluidTank.getFluid();
            }

            @Override
            public IExtendedFluidTank getTank() {
                return fluidTank;
            }

            @Override
            public int getTankIndex() {
                return tanks.indexOf(fluidTank);
            }

            @Nullable
            @Override
            public ITextComponent getTooltip() {
                FluidStack fluid = fluidTank.getFluid();
                if (fluid == null) {
                    return new TextComponentString(LangUtils.localize("gui.empty"));
                }
                String amount = fluidTank.getFluidAmount() == Integer.MAX_VALUE ? LangUtils.localize("gui.infinite") : fluidTank.getFluidAmount() + " mB";
                return new TextComponentString(LangUtils.localizeFluidStack(fluid) + ": " + amount);
            }

            @Override
            public double getLevel() {
                return MathUtils.divideToLevel(fluidTank.getFluidAmount(), fluidTank.getCapacity());
            }
        };
    }
}
