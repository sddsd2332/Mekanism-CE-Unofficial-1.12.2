package mekanism.common.capabilities.gas.item;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.common.capabilities.gas.VariableCapacityGasTank;
import mekanism.common.tier.GasTankTier;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.*;

public class RateLimitGasHandler extends ItemStackMekanismGasHandler {

    public static RateLimitGasHandler create(IntSupplier rate, IntSupplier capacity) {
        return create(rate, capacity, ConstantPredicates.alwaysTrueBi(), ConstantPredicates.alwaysTrueBi(), ConstantPredicates.alwaysTrue());
    }

    public static RateLimitGasHandler create(IntSupplier rate, IntSupplier capacity, BiPredicate<GasStack, AutomationType> canExtract,
          BiPredicate<GasStack, AutomationType> canInsert, Predicate<GasStack> isValid) {
        return create(rate, capacity, canExtract, canInsert, isValid, null);
    }

    public static RateLimitGasHandler create(IntSupplier rate, IntSupplier capacity, BiPredicate<GasStack, AutomationType> canExtract,
          BiPredicate<GasStack, AutomationType> canInsert, Predicate<GasStack> isValid, @Nullable String legacyGasKey) {
        Objects.requireNonNull(rate, "Rate supplier cannot be null");
        Objects.requireNonNull(capacity, "Capacity supplier cannot be null");
        Objects.requireNonNull(canExtract, "Extraction validity check cannot be null");
        Objects.requireNonNull(canInsert, "Insertion validity check cannot be null");
        Objects.requireNonNull(isValid, "Gas validity check cannot be null");
        return new RateLimitGasHandler(listener -> new RateLimitGasTank(rate, capacity, canExtract, canInsert, isValid, listener), legacyGasKey);
    }

    public static RateLimitGasHandler create(GasTankTier tier) {
        return create(tier, null);
    }

    public static RateLimitGasHandler create(GasTankTier tier, @Nullable String legacyGasKey) {
        Objects.requireNonNull(tier, "Gas tank tier cannot be null");
        return create(() -> tier, legacyGasKey);
    }

    public static RateLimitGasHandler create(Supplier<GasTankTier> tier, @Nullable String legacyGasKey) {
        Objects.requireNonNull(tier, "Gas tank tier supplier cannot be null");
        return new RateLimitGasHandler(listener -> new GasTankRateLimitGasTank(tier, listener), legacyGasKey);
    }

    private final IExtendedGasTank tank;

    private RateLimitGasHandler(Function<IContentsListener, IExtendedGasTank> tankProvider, @Nullable String legacyGasKey) {
        super(legacyGasKey);
        tank = tankProvider.apply(this);
    }

    @Override
    protected List<IExtendedGasTank> getInitialTanks() {
        return Collections.singletonList(tank);
    }

    public static class RateLimitGasTank extends VariableCapacityGasTank {

        private final IntSupplier rate;

        public RateLimitGasTank(IntSupplier rate, IntSupplier capacity, @Nullable IContentsListener listener) {
            this(rate, capacity, ConstantPredicates.alwaysTrueBi(), ConstantPredicates.alwaysTrueBi(), ConstantPredicates.alwaysTrue(), listener);
        }

        public RateLimitGasTank(IntSupplier rate, IntSupplier capacity, BiPredicate<GasStack, AutomationType> canExtract,
              BiPredicate<GasStack, AutomationType> canInsert, Predicate<GasStack> isValid, @Nullable IContentsListener listener) {
            super(capacity, canExtract, canInsert, isValid, listener, null);
            this.rate = rate;
        }

        @Override
        protected int getInsertRate(@Nullable AutomationType automationType) {
            return automationType == null || automationType == AutomationType.MANUAL ? super.getInsertRate(automationType) : rate.getAsInt();
        }

        @Override
        protected int getExtractRate(@Nullable AutomationType automationType) {
            return automationType == null || automationType == AutomationType.MANUAL ? super.getExtractRate(automationType) : rate.getAsInt();
        }
    }

    private static class GasTankRateLimitGasTank extends RateLimitGasTank {

        private final Supplier<GasTankTier> tier;

        private GasTankRateLimitGasTank(Supplier<GasTankTier> tier, @Nullable IContentsListener listener) {
            super(() -> tier.get().getOutput(), () -> tier.get().getStorage(),
                  (stack, automationType) -> isValidForTier(tier.get(), stack),
                  (stack, automationType) -> isValidForTier(tier.get(), stack),
                  stack -> isValidForTier(tier.get(), stack), listener);
            this.tier = tier;
        }

        @Override
        @Nullable
        public GasStack insert(@Nullable GasStack stack, Action action, AutomationType automationType) {
            return super.insert(stack, action.combine(!isCreative()), automationType);
        }

        @Override
        @Nullable
        public GasStack extract(int amount, Action action, AutomationType automationType) {
            return super.extract(amount, action.combine(!isCreative()), automationType);
        }

        @Override
        public int setStackSize(int amount, Action action) {
            return super.setStackSize(amount, action.combine(!isCreative()));
        }

        private boolean isCreative() {
            return tier.get() == GasTankTier.CREATIVE;
        }

        private static boolean isValidForTier(GasTankTier tier, @Nullable GasStack stack) {
            return stack != null && stack.getGas() != null && (tier == GasTankTier.CREATIVE || !stack.getGas().isRadiation());
        }
    }
}
