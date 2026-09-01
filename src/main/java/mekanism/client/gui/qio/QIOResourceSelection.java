package mekanism.client.gui.qio;

import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasHandler;
import mekanism.api.qio.client.QIOResourceSelectionAdapterRegistry;
import mekanism.api.qio.resource.QIOResourceCodecs;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import mekanism.common.content.qio.QIONetworkResourceLimits;
import mekanism.common.util.FluidContainerUtils;
import mekanism.common.util.GasUtils;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Deterministic client-side selection rules shared by QIO resource target slots. */
public final class QIOResourceSelection {

    private QIOResourceSelection() {
    }

    /** A normal ingredient selection never silently unwraps an item container. */
    @Nonnull
    public static Resolution fromIngredient(@Nullable Object ingredient) {
        if (ingredient instanceof ItemStack) {
            ItemStack stack = (ItemStack) ingredient;
            return stack.isEmpty() ? Resolution.none() : Resolution.of(QIOResourceCodecs.item(stack));
        } else if (ingredient instanceof FluidStack) {
            FluidStack stack = (FluidStack) ingredient;
            return stack.amount <= 0 || stack.getFluid() == null ? Resolution.none() :
                  Resolution.of(QIOResourceCodecs.fluid(stack));
        } else if (ingredient instanceof GasStack) {
            GasStack stack = (GasStack) ingredient;
            return stack.amount <= 0 || stack.getGas() == null ? Resolution.none() :
                  Resolution.of(QIOResourceCodecs.gas(stack));
        } else if (ingredient != null) {
            return resolve(QIOResourceSelectionAdapterRegistry.INSTANCE
                  .getIngredientCandidates(ingredient));
        }
        return Resolution.none();
    }

    /** Left click selects the item state; right click selects one unambiguous contained resource. */
    @Nonnull
    public static Resolution fromCarried(@Nullable ItemStack carried, int button) {
        if (carried == null || carried.isEmpty()) {
            return Resolution.none();
        }
        return button == 0 ? Resolution.of(QIOResourceCodecs.item(carried)) :
              button == 1 ? fromContainer(carried) : Resolution.none();
    }

    @Nonnull
    public static Resolution fromContainer(@Nonnull ItemStack container) {
        if (container.isEmpty()) {
            return Resolution.none();
        }
        Set<QIOResourceDescriptor> candidates = new LinkedHashSet<>();
        IFluidHandlerItem fluidHandler = FluidContainerUtils.getUnstackedFluidHandlerCapability(container);
        if (fluidHandler != null) {
            int tankCount = FluidContainerUtils.getTankCount(fluidHandler);
            for (int tank = 0; tank < tankCount; tank++) {
                FluidStack fluid = FluidContainerUtils.getFluidInTank(fluidHandler, tank);
                if (fluid != null && fluid.amount > 0 && fluid.getFluid() != null) {
                    addSafe(candidates, QIOResourceCodecs.fluid(fluid));
                }
            }
        }
        IGasHandler gasHandler = GasUtils.getUnstackedGasHandlerCapability(container);
        if (gasHandler != null) {
            int tankCount = GasUtils.getTankCount(gasHandler);
            for (int tank = 0; tank < tankCount; tank++) {
                GasStack gas = GasUtils.getGasInTank(gasHandler, tank);
                if (gas != null && gas.amount > 0 && gas.getGas() != null) {
                    addSafe(candidates, QIOResourceCodecs.gas(gas));
                }
            }
        }
        for (QIOResourceDescriptor descriptor : QIOResourceSelectionAdapterRegistry.INSTANCE
              .getContainedResourceCandidates(container)) {
            addSafe(candidates, descriptor);
        }
        return resolve(candidates);
    }

    @Nonnull
    private static Resolution resolve(Collection<QIOResourceDescriptor> supplied) {
        Set<QIOResourceDescriptor> candidates = new LinkedHashSet<>();
        if (supplied != null) {
            for (QIOResourceDescriptor descriptor : supplied) {
                addSafe(candidates, descriptor);
            }
        }
        if (candidates.isEmpty()) {
            return Resolution.none();
        }
        return candidates.size() == 1 ? Resolution.of(candidates.iterator().next()) :
              Resolution.ambiguous();
    }

    private static void addSafe(Set<QIOResourceDescriptor> candidates,
          @Nullable QIOResourceDescriptor descriptor) {
        if (descriptor != null && descriptor.isResolved() &&
              QIONetworkResourceLimits.isSafeDescriptorPayload(descriptor.getPayload())) {
            candidates.add(descriptor);
        }
    }

    public static final class Resolution {

        @Nullable
        private final QIOResourceDescriptor descriptor;
        private final boolean ambiguous;

        private Resolution(@Nullable QIOResourceDescriptor descriptor, boolean ambiguous) {
            this.descriptor = descriptor;
            this.ambiguous = ambiguous;
        }

        @Nonnull
        private static Resolution none() {
            return new Resolution(null, false);
        }

        @Nonnull
        private static Resolution ambiguous() {
            return new Resolution(null, true);
        }

        @Nonnull
        private static Resolution of(@Nonnull QIOResourceDescriptor descriptor) {
            return descriptor.isResolved() &&
                  QIONetworkResourceLimits.isSafeDescriptorPayload(descriptor.getPayload()) ?
                  new Resolution(descriptor, false) : none();
        }

        @Nullable
        public QIOResourceDescriptor getDescriptor() {
            return descriptor;
        }

        public boolean isUnique() {
            return descriptor != null;
        }

        public boolean isAmbiguous() {
            return ambiguous;
        }
    }
}
