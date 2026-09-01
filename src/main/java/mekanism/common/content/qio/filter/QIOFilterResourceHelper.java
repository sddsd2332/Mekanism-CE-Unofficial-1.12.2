package mekanism.common.content.qio.filter;

import mekanism.api.gas.GasStack;
import mekanism.api.qio.resource.QIOResourceCodecs;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Compatibility projection between legacy QIO filters and codec-defined descriptors. */
public final class QIOFilterResourceHelper {

    private QIOFilterResourceHelper() {
    }

    @Nullable
    public static QIOResourceDescriptor getDescriptor(@Nullable QIOFilter filter) {
        if (filter instanceof QIOResourceFilter) {
            return ((QIOResourceFilter) filter).getDescriptor();
        }
        try {
            if (filter instanceof QIOItemStackFilter) {
                ItemStack stack = ((QIOItemStackFilter) filter).getItemStack();
                return stack.isEmpty() ? null : QIOResourceCodecs.item(stack);
            } else if (filter instanceof QIOFluidFilter) {
                FluidStack stack = ((QIOFluidFilter) filter).getFluid();
                return stack == null || stack.getFluid() == null ? null : QIOResourceCodecs.fluid(stack);
            } else if (filter instanceof QIOGasFilter) {
                GasStack stack = ((QIOGasFilter) filter).getGas();
                return stack == null || stack.getGas() == null ? null : QIOResourceCodecs.gas(stack);
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    /** Creates the compatible exact filter representation for one resolved descriptor. */
    @Nullable
    public static QIOFilter createFilter(@Nonnull QIOResourceDescriptor descriptor) {
        return createFilter(descriptor, false);
    }

    /** Item descriptors retain the legacy fuzzy option; other codecs remain exact. */
    @Nullable
    public static QIOFilter createFilter(@Nonnull QIOResourceDescriptor descriptor,
          boolean fuzzyItem) {
        if (descriptor == null || !descriptor.isResolved()) {
            return null;
        }
        ItemStack item = descriptor.resolve(QIOResourceCodecs.ITEM_STACK);
        if (item != null && !item.isEmpty()) {
            QIOItemStackFilter filter = new QIOItemStackFilter(item);
            filter.setFuzzyMode(fuzzyItem);
            return filter;
        }
        FluidStack fluid = descriptor.resolve(QIOResourceCodecs.FLUID_STACK);
        if (fluid != null && fluid.getFluid() != null) {
            return new QIOFluidFilter(fluid);
        }
        GasStack gas = descriptor.resolve(QIOResourceCodecs.GAS_STACK);
        if (gas != null && gas.getGas() != null) {
            return new QIOGasFilter(gas);
        }
        return new QIOResourceFilter(descriptor);
    }
}
