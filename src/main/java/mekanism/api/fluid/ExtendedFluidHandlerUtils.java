package mekanism.api.fluid;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.container.ContainerInteraction;
import mekanism.api.container.InContainerGetter;
import mekanism.api.container.IntContainerInteraction;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;

public final class ExtendedFluidHandlerUtils {

    private ExtendedFluidHandlerUtils() {
    }

    @Nullable
    public static FluidStack insert(@Nullable FluidStack stack, Action action, IntSupplier tankCount, FluidInTankGetter inTankGetter,
          InsertFluid insertFluid) {
        return insert(stack, null, action, side -> tankCount.getAsInt(), (tank, side) -> inTankGetter.getFluidInTank(tank),
              (tank, toInsert, side, insertAction) -> insertFluid.insert(tank, toInsert, insertAction));
    }

    @Nullable
    public static FluidStack insert(@Nullable FluidStack stack, @Nullable EnumFacing side, Action action, ToIntFunction<EnumFacing> tankCount,
          InContainerGetter<FluidStack> inTankGetter, ContainerInteraction<FluidStack> insertFluid) {
        if (isEmpty(stack)) {
            return null;
        }
        int tanks = tankCount.applyAsInt(side);
        if (tanks == 0) {
            return stack;
        } else if (tanks == 1) {
            return emptyToNull(insertFluid.interact(0, stack, side, action));
        }
        FluidStack toInsert = stack;
        IntList emptyTanks = new IntArrayList();
        for (int tank = 0; tank < tanks; tank++) {
            FluidStack inTank = inTankGetter.getStored(tank, side);
            if (isEmpty(inTank)) {
                emptyTanks.add(tank);
            } else if (inTank.isFluidEqual(stack)) {
                FluidStack remainder = insertFluid.interact(tank, toInsert, side, action);
                if (isEmpty(remainder)) {
                    return null;
                }
                toInsert = remainder;
            }
        }
        for (int tank : emptyTanks) {
            FluidStack remainder = insertFluid.interact(tank, toInsert, side, action);
            if (isEmpty(remainder)) {
                return null;
            }
            toInsert = remainder;
        }
        return toInsert;
    }

    @Nullable
    public static FluidStack insert(@Nullable FluidStack stack, @Nullable EnumFacing side, Function<EnumFacing, List<IExtendedFluidTank>> fluidTankSupplier,
          Action action, AutomationType automationType) {
        if (isEmpty(stack)) {
            return null;
        }
        List<IExtendedFluidTank> fluidTanks = fluidTankSupplier.apply(side);
        return insert(stack, action, automationType, fluidTanks.size(), fluidTanks);
    }

    @Nullable
    public static FluidStack insert(@Nullable FluidStack stack, Action action, AutomationType automationType, int size, List<IExtendedFluidTank> fluidTanks) {
        if (isEmpty(stack)) {
            return null;
        } else if (size == 0) {
            return stack;
        } else if (size == 1) {
            return emptyToNull(fluidTanks.get(0).insert(stack, action, automationType));
        }
        FluidStack toInsert = stack;
        List<IExtendedFluidTank> emptyTanks = new ArrayList<>();
        for (IExtendedFluidTank tank : fluidTanks) {
            if (tank.isEmpty()) {
                emptyTanks.add(tank);
            } else if (tank.isFluidEqual(stack)) {
                FluidStack remainder = tank.insert(toInsert, action, automationType);
                if (isEmpty(remainder)) {
                    return null;
                }
                toInsert = remainder;
            }
        }
        for (IExtendedFluidTank tank : emptyTanks) {
            FluidStack remainder = tank.insert(toInsert, action, automationType);
            if (isEmpty(remainder)) {
                return null;
            }
            toInsert = remainder;
        }
        return toInsert;
    }

    @Nullable
    public static FluidStack extract(int amount, Action action, IntSupplier tankCount, FluidInTankGetter inTankGetter, ExtractFluid extractFluid) {
        return extract(amount, null, action, side -> tankCount.getAsInt(), (tank, side) -> inTankGetter.getFluidInTank(tank),
              (tank, toExtract, side, extractAction) -> extractFluid.extract(tank, toExtract, extractAction));
    }

    @Nullable
    public static FluidStack extract(int amount, @Nullable EnumFacing side, Action action, ToIntFunction<EnumFacing> tankCount,
          InContainerGetter<FluidStack> inTankGetter, IntContainerInteraction<FluidStack> extractFluid) {
        if (amount <= 0) {
            return null;
        }
        int tanks = tankCount.applyAsInt(side);
        if (tanks == 0) {
            return null;
        } else if (tanks == 1) {
            return emptyToNull(extractFluid.interact(0, amount, side, action));
        }
        FluidStack extracted = null;
        int toDrain = amount;
        for (int tank = 0; tank < tanks; tank++) {
            FluidStack inTank = inTankGetter.getStored(tank, side);
            if (isEmpty(extracted) || !isEmpty(inTank) && extracted.isFluidEqual(inTank)) {
                FluidStack drained = extractFluid.interact(tank, toDrain, side, action);
                if (!isEmpty(drained)) {
                    if (isEmpty(extracted)) {
                        extracted = drained;
                    } else {
                        extracted.amount += drained.amount;
                    }
                    toDrain -= drained.amount;
                    if (toDrain == 0) {
                        break;
                    }
                }
            }
        }
        return emptyToNull(extracted);
    }

    @Nullable
    public static FluidStack extract(int amount, @Nullable EnumFacing side, Function<EnumFacing, List<IExtendedFluidTank>> fluidTankSupplier,
          Action action, AutomationType automationType) {
        if (amount <= 0) {
            return null;
        }
        List<IExtendedFluidTank> fluidTanks = fluidTankSupplier.apply(side);
        return extract(amount, action, automationType, fluidTanks.size(), fluidTanks);
    }

    @Nullable
    public static FluidStack extract(int amount, Action action, AutomationType automationType, int size, List<IExtendedFluidTank> fluidTanks) {
        if (amount <= 0 || size == 0) {
            return null;
        } else if (size == 1) {
            return emptyToNull(fluidTanks.get(0).extract(amount, action, automationType));
        }
        FluidStack extracted = null;
        int toDrain = amount;
        for (IExtendedFluidTank fluidTank : fluidTanks) {
            if (isEmpty(extracted) || fluidTank.isFluidEqual(extracted)) {
                FluidStack drained = fluidTank.extract(toDrain, action, automationType);
                if (!isEmpty(drained)) {
                    if (isEmpty(extracted)) {
                        extracted = drained;
                    } else {
                        extracted.amount += drained.amount;
                    }
                    toDrain -= drained.amount;
                    if (toDrain == 0) {
                        break;
                    }
                }
            }
        }
        return emptyToNull(extracted);
    }

    @Nullable
    public static FluidStack extract(@Nullable FluidStack stack, Action action, IntSupplier tankCount, FluidInTankGetter inTankGetter,
          ExtractFluid extractFluid) {
        return extract(stack, null, action, side -> tankCount.getAsInt(), (tank, side) -> inTankGetter.getFluidInTank(tank),
              (tank, toExtract, side, extractAction) -> extractFluid.extract(tank, toExtract, extractAction));
    }

    @Nullable
    public static FluidStack extract(@Nullable FluidStack stack, @Nullable EnumFacing side, Action action, ToIntFunction<EnumFacing> tankCount,
          InContainerGetter<FluidStack> inTankGetter, IntContainerInteraction<FluidStack> extractFluid) {
        if (isEmpty(stack)) {
            return null;
        }
        int tanks = tankCount.applyAsInt(side);
        if (tanks == 0) {
            return null;
        } else if (tanks == 1) {
            FluidStack inTank = inTankGetter.getStored(0, side);
            if (isEmpty(inTank) || !inTank.isFluidEqual(stack)) {
                return null;
            }
            return emptyToNull(extractFluid.interact(0, stack.amount, side, action));
        }
        FluidStack extracted = null;
        int toDrain = stack.amount;
        for (int tank = 0; tank < tanks; tank++) {
            FluidStack inTank = inTankGetter.getStored(tank, side);
            if (!isEmpty(inTank) && stack.isFluidEqual(inTank)) {
                FluidStack drained = extractFluid.interact(tank, toDrain, side, action);
                if (!isEmpty(drained)) {
                    if (isEmpty(extracted)) {
                        extracted = drained;
                    } else {
                        extracted.amount += drained.amount;
                    }
                    toDrain -= drained.amount;
                    if (toDrain == 0) {
                        break;
                    }
                }
            }
        }
        return emptyToNull(extracted);
    }

    @Nullable
    public static FluidStack extract(@Nullable FluidStack stack, @Nullable EnumFacing side, Function<EnumFacing, List<IExtendedFluidTank>> fluidTankSupplier,
          Action action, AutomationType automationType) {
        if (isEmpty(stack)) {
            return null;
        }
        List<IExtendedFluidTank> fluidTanks = fluidTankSupplier.apply(side);
        return extract(stack, action, automationType, fluidTanks.size(), fluidTanks);
    }

    @Nullable
    public static FluidStack extract(@Nullable FluidStack stack, Action action, AutomationType automationType, int size, List<IExtendedFluidTank> fluidTanks) {
        if (isEmpty(stack) || size == 0) {
            return null;
        } else if (size == 1) {
            IExtendedFluidTank tank = fluidTanks.get(0);
            if (tank.isEmpty() || !tank.isFluidEqual(stack)) {
                return null;
            }
            return emptyToNull(tank.extract(stack.amount, action, automationType));
        }
        FluidStack extracted = null;
        int toDrain = stack.amount;
        for (IExtendedFluidTank fluidTank : fluidTanks) {
            if (fluidTank.isFluidEqual(stack)) {
                FluidStack drained = fluidTank.extract(toDrain, action, automationType);
                if (!isEmpty(drained)) {
                    if (isEmpty(extracted)) {
                        extracted = drained;
                    } else {
                        extracted.amount += drained.amount;
                    }
                    toDrain -= drained.amount;
                    if (toDrain == 0) {
                        break;
                    }
                }
            }
        }
        return emptyToNull(extracted);
    }

    public static boolean isEmpty(@Nullable FluidStack stack) {
        return stack == null || stack.amount <= 0;
    }

    @Nullable
    public static FluidStack emptyToNull(@Nullable FluidStack stack) {
        return isEmpty(stack) ? null : stack;
    }

    @FunctionalInterface
    public interface FluidInTankGetter {

        @Nullable
        FluidStack getFluidInTank(int tank);
    }

    @FunctionalInterface
    public interface InsertFluid {

        @Nullable
        FluidStack insert(int tank, FluidStack stack, Action action);
    }

    @FunctionalInterface
    public interface ExtractFluid {

        @Nullable
        FluidStack extract(int tank, int amount, Action action);
    }
}
