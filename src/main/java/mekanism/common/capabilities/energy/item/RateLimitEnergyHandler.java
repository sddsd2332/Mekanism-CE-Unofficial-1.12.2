package mekanism.common.capabilities.energy.item;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.functions.ConstantPredicates;
import mekanism.common.capabilities.energy.VariableCapacityEnergyContainer;
import mekanism.common.tier.EnergyCubeTier;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class RateLimitEnergyHandler extends ItemStackMekanismEnergyHandler {

    public static RateLimitEnergyHandler create(Supplier<EnergyCubeTier> tier) {
        Objects.requireNonNull(tier, "Energy cube tier supplier cannot be null");
        return new RateLimitEnergyHandler(listener -> new EnergyCubeRateLimitEnergyContainer(tier, listener));
    }

    public static RateLimitEnergyHandler create(DoubleSupplier capacity) {
        return create(() -> capacity.getAsDouble() * 0.005, capacity);
    }

    public static RateLimitEnergyHandler create(DoubleSupplier rate, DoubleSupplier capacity) {
        return create(rate, capacity, ConstantPredicates.alwaysTrue(), ConstantPredicates.alwaysTrue());
    }

    public static RateLimitEnergyHandler create(DoubleSupplier rate, DoubleSupplier capacity, Predicate<AutomationType> canExtract,
          Predicate<AutomationType> canInsert) {
        Objects.requireNonNull(rate, "Rate supplier cannot be null");
        Objects.requireNonNull(capacity, "Capacity supplier cannot be null");
        Objects.requireNonNull(canExtract, "Extraction validity check cannot be null");
        Objects.requireNonNull(canInsert, "Insertion validity check cannot be null");
        return new RateLimitEnergyHandler(listener -> new RateLimitEnergyContainer(rate, capacity, canExtract, canInsert, listener));
    }

    private final IEnergyContainer energyContainer;

    private RateLimitEnergyHandler(Function<IContentsListener, IEnergyContainer> containerProvider) {
        energyContainer = containerProvider.apply(this);
    }

    @Override
    protected List<IEnergyContainer> getInitialContainers() {
        return Collections.singletonList(energyContainer);
    }

    public static class RateLimitEnergyContainer extends VariableCapacityEnergyContainer {

        private final DoubleSupplier rate;

        public RateLimitEnergyContainer(DoubleSupplier rate, DoubleSupplier capacity, Predicate<AutomationType> canExtract,
              Predicate<AutomationType> canInsert, @Nullable IContentsListener listener) {
            super(capacity, canExtract, canInsert, listener);
            this.rate = rate;
        }

        @Override
        protected double getInsertRate(@Nullable AutomationType automationType) {
            return automationType == null || automationType == AutomationType.MANUAL ? super.getInsertRate(automationType) : Math.max(0, rate.getAsDouble());
        }

        @Override
        protected double getExtractRate(@Nullable AutomationType automationType) {
            return automationType == null || automationType == AutomationType.MANUAL ? super.getExtractRate(automationType) : Math.max(0, rate.getAsDouble());
        }
    }

    private static class EnergyCubeRateLimitEnergyContainer extends RateLimitEnergyContainer {

        private final Supplier<EnergyCubeTier> tier;

        private EnergyCubeRateLimitEnergyContainer(Supplier<EnergyCubeTier> tier, @Nullable IContentsListener listener) {
            super(() -> tier.get().getOutput(), () -> tier.get().getMaxEnergy(), ConstantPredicates.alwaysTrue(), ConstantPredicates.alwaysTrue(), listener);
            this.tier = tier;
        }

        private boolean isCreative() {
            return tier.get() == EnergyCubeTier.CREATIVE;
        }

        @Override
        public double getEnergy() {
            double energy = super.getEnergy();
            // Creative cubes are binary sources/sinks. The generic double container applies a
            // heat-safety clamp when reading values, which would otherwise report a full cube as 25% full.
            return isCreative() && energy > 0 ? getMaxEnergy() : energy;
        }

        @Override
        public double insert(double amount, Action action, AutomationType automationType) {
            return super.insert(amount, action.combine(!isCreative()), automationType);
        }

        @Override
        public double extract(double amount, Action action, AutomationType automationType) {
            return super.extract(amount, action.combine(!isCreative()), automationType);
        }
    }
}
