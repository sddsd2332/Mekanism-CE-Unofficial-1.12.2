package mekanism.common.capabilities.fluid.item;

import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.functions.TriPredicate;
import mekanism.common.capabilities.GenericTankSpec;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.function.BiPredicate;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

public class FluidTankSpec extends GenericTankSpec<FluidStack> {

    final IntSupplier rate;
    final IntSupplier capacity;

    public FluidTankSpec(IntSupplier rate, IntSupplier capacity, BiPredicate<FluidStack, AutomationType> canExtract,
          TriPredicate<FluidStack, AutomationType, ItemStack> canInsert, Predicate<FluidStack> isValid, Predicate<ItemStack> supportsStack) {
        super(canExtract, canInsert, isValid, supportsStack);
        this.rate = rate;
        this.capacity = capacity;
    }

    public <TANK extends IExtendedFluidTank> TANK createTank(TankFromSpecCreator<TANK> tankCreator, ItemStack stack) {
        return createTank(tankCreator, stack, null);
    }

    public <TANK extends IExtendedFluidTank> TANK createTank(TankFromSpecCreator<TANK> tankCreator, ItemStack stack, @Nullable IContentsListener listener) {
        return tankCreator.create(rate, capacity, canExtract, (fluid, automationType) -> canInsert.test(fluid, automationType, stack), isValid, listener);
    }

    public static FluidTankSpec create(IntSupplier rate, IntSupplier capacity) {
        return new FluidTankSpec(rate, capacity, ConstantPredicates.alwaysTrueBi(), ConstantPredicates.alwaysTrueTri(), ConstantPredicates.alwaysTrue(),
              ConstantPredicates.alwaysTrue());
    }

    public static FluidTankSpec createFillOnly(IntSupplier rate, IntSupplier capacity, Predicate<FluidStack> isValid) {
        return createFillOnly(rate, capacity, isValid, ConstantPredicates.alwaysTrue());
    }

    public static FluidTankSpec createFillOnly(IntSupplier rate, IntSupplier capacity, Predicate<FluidStack> isValid, Predicate<ItemStack> supportsStack) {
        return new FluidTankSpec(rate, capacity, ConstantPredicates.notExternal(), (fluid, automation, stack) -> supportsStack.test(stack), isValid, supportsStack);
    }

    @FunctionalInterface
    public interface TankFromSpecCreator<TANK extends IExtendedFluidTank> {

        TANK create(IntSupplier rate, IntSupplier capacity, BiPredicate<FluidStack, AutomationType> canExtract,
              BiPredicate<FluidStack, AutomationType> canInsert, Predicate<FluidStack> isValid, @Nullable IContentsListener listener);

        default TANK create(IntSupplier rate, IntSupplier capacity, BiPredicate<FluidStack, AutomationType> canExtract,
              BiPredicate<FluidStack, AutomationType> canInsert, Predicate<FluidStack> isValid) {
            return create(rate, capacity, canExtract, canInsert, isValid, null);
        }
    }
}
