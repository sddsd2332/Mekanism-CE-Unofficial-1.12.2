package mekanism.common.capabilities.fluid.item;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.fluid.VariableCapacityFluidTank;
import mekanism.common.tier.FluidTankTier;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.*;

public class RateLimitFluidHandler extends ItemStackMekanismFluidHandler {

    public static RateLimitFluidHandler create(IntSupplier rate, IntSupplier capacity) {
        return create(rate, capacity, BasicFluidTank.alwaysTrueBi, BasicFluidTank.alwaysTrueBi, BasicFluidTank.alwaysTrue);
    }

    public static RateLimitFluidHandler create(IntSupplier rate, IntSupplier capacity, BiPredicate<FluidStack, AutomationType> canExtract,
          BiPredicate<FluidStack, AutomationType> canInsert, Predicate<FluidStack> isValid) {
        return create(rate, capacity, canExtract, canInsert, isValid, null);
    }

    public static RateLimitFluidHandler create(IntSupplier rate, IntSupplier capacity, BiPredicate<FluidStack, AutomationType> canExtract,
          BiPredicate<FluidStack, AutomationType> canInsert, Predicate<FluidStack> isValid, @Nullable String legacyFluidKey) {
        Objects.requireNonNull(rate, "Rate supplier cannot be null");
        Objects.requireNonNull(capacity, "Capacity supplier cannot be null");
        Objects.requireNonNull(canExtract, "Extraction validity check cannot be null");
        Objects.requireNonNull(canInsert, "Insertion validity check cannot be null");
        Objects.requireNonNull(isValid, "Fluid validity check cannot be null");
        return new RateLimitFluidHandler(listener -> new RateLimitFluidTank(rate, capacity, canExtract, canInsert, isValid, listener), legacyFluidKey);
    }

    public static RateLimitFluidHandler create(FluidTankTier tier) {
        return create(tier, null);
    }

    public static RateLimitFluidHandler create(FluidTankTier tier, @Nullable String legacyFluidKey) {
        Objects.requireNonNull(tier, "Fluid tank tier cannot be null");
        return create(() -> tier, legacyFluidKey);
    }

    public static RateLimitFluidHandler create(Supplier<FluidTankTier> tier, @Nullable String legacyFluidKey) {
        Objects.requireNonNull(tier, "Fluid tank tier supplier cannot be null");
        return new RateLimitFluidHandler(listener -> new FluidTankRateLimitFluidTank(tier, listener), legacyFluidKey);
    }

    private final IExtendedFluidTank tank;

    private RateLimitFluidHandler(Function<IContentsListener, IExtendedFluidTank> tankProvider, @Nullable String legacyFluidKey) {
        super(legacyFluidKey);
        tank = tankProvider.apply(this);
    }

    @Override
    protected List<IExtendedFluidTank> getInitialTanks() {
        return Collections.singletonList(tank);
    }

    public static class RateLimitFluidTank extends VariableCapacityFluidTank {

        private final IntSupplier rate;

        public RateLimitFluidTank(IntSupplier rate, IntSupplier capacity, @Nullable IContentsListener listener) {
            this(rate, capacity, alwaysTrueBi, alwaysTrueBi, alwaysTrue, listener);
        }

        public RateLimitFluidTank(IntSupplier rate, IntSupplier capacity, BiPredicate<FluidStack, AutomationType> canExtract,
              BiPredicate<FluidStack, AutomationType> canInsert, Predicate<FluidStack> isValid, @Nullable IContentsListener listener) {
            super(capacity, canExtract, canInsert, isValid, listener);
            this.rate = rate;
        }

        @Override
        protected int getRate(@Nullable AutomationType automationType) {
            return automationType == null || automationType == AutomationType.MANUAL ? super.getRate(automationType) : rate.getAsInt();
        }
    }

    private static class FluidTankRateLimitFluidTank extends RateLimitFluidTank {

        private final Supplier<FluidTankTier> tier;

        private FluidTankRateLimitFluidTank(Supplier<FluidTankTier> tier, @Nullable IContentsListener listener) {
            super(() -> tier.get().getOutput(), () -> tier.get().getStorage(), BasicFluidTank.alwaysTrueBi, BasicFluidTank.alwaysTrueBi,
                  BasicFluidTank.alwaysTrue, listener);
            this.tier = tier;
        }

        @Override
        @Nullable
        public FluidStack insert(@Nullable FluidStack stack, Action action, AutomationType automationType) {
            return super.insert(stack, action.combine(!isCreative()), automationType);
        }

        @Override
        @Nullable
        public FluidStack extract(int amount, Action action, AutomationType automationType) {
            return super.extract(amount, action.combine(!isCreative()), automationType);
        }

        @Override
        public int setStackSize(int amount, Action action) {
            return super.setStackSize(amount, action.combine(!isCreative()));
        }

        private boolean isCreative() {
            return tier.get() == FluidTankTier.CREATIVE;
        }
    }
}
