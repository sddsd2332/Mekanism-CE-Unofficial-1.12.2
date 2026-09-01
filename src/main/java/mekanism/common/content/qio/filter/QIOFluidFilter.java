package mekanism.common.content.qio.filter;

import mekanism.common.content.qio.QIOResourceEntry;
import mekanism.common.content.qio.QIOResourceKind;
import mekanism.api.qio.resource.QIOResourceCodecs;
import mekanism.api.qio.resource.QIOResourceFamilyMatcher;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

public class QIOFluidFilter extends QIOFilter {

    public static final String TYPE = "fluid";
    private FluidStack fluid;

    public QIOFluidFilter() {
    }

    public QIOFluidFilter(FluidStack fluid) {
        this.fluid = fluid == null ? null : new FluidStack(fluid, 1);
    }

    public FluidStack getFluid() {
        return fluid == null ? null : fluid.copy();
    }

    public FluidStack getFluidStack() {
        return getFluid();
    }

    public void setFluid(FluidStack fluid) {
        this.fluid = fluid == null ? null : new FluidStack(fluid, 1);
    }

    @Override public QIOResourceFamilyMatcher getMatcher() {
        return QIOResourceFamilyMatcher.family(QIOResourceCodecs.FLUID_FAMILY);
    }

    @Override
    public boolean matches(QIOResourceEntry entry) {
        FluidStack candidate = entry.getFluid();
        return fluid != null && candidate != null && fluid.isFluidEqual(candidate);
    }

    @Override
    public boolean matches(FluidStack stack) {
        return fluid != null && stack != null && fluid.isFluidEqual(stack);
    }

    @Override public String getType() { return TYPE; }

    @Override public boolean hasFilter() { return fluid != null && fluid.getFluid() != null; }

    @Override
    public void writePayload(NBTTagCompound data) {
        if (fluid != null) {
            fluid.writeToNBT(data);
        }
    }

    @Override
    protected void readPayload(NBTTagCompound data) {
        fluid = FluidStack.loadFluidStackFromNBT(data);
    }
}
