package mekanism.qioprocessing.common.planning;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nullable;

/** Reads one-item fluid containers without copying or serializing their Forge capabilities. */
final class QIOFluidContainerSnapshot {

    private QIOFluidContainerSnapshot() {
    }

    @Nullable
    static FluidStack capture(ItemStack candidate) {
        if (candidate == null || candidate.isEmpty() || candidate.getCount() != 1) {
            return null;
        }
        try {
            IFluidHandlerItem handler = candidate.getCapability(
                  CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
            if (handler == null) return null;
            IFluidTankProperties[] properties = handler.getTankProperties();
            if (properties == null || properties.length != 1 || properties[0] == null) {
                return null;
            }
            FluidStack stored = properties[0].getContents();
            if (stored == null || stored.getFluid() == null || stored.amount <= 0 ||
                stored.amount > properties[0].getCapacity() ||
                !properties[0].canDrainFluidType(stored)) {
                return null;
            }
            FluidStack simulated = handler.drain(stored.copy(), false);
            return sameFluidAmount(stored, simulated) ? stored.copy() : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean sameFluidAmount(@Nullable FluidStack expected,
          @Nullable FluidStack actual) {
        return expected != null && actual != null && expected.amount == actual.amount &&
              expected.isFluidEqual(actual);
    }
}
