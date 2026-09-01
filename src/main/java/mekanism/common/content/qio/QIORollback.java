package mekanism.common.content.qio;

import mekanism.api.Action;
import mekanism.api.gas.GasStack;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import mekanism.common.Mekanism;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.UUID;

/** Shared accounting for compensating QIO resource transfers. */
public final class QIORollback {

    private QIORollback() {
    }

    public static long restore(@Nullable QIOFrequency frequency, @Nullable UUID resource, long amount, String operation) {
        long restored = frequency == null || resource == null || amount <= 0 ? 0
              : frequency.massInsert(resource, amount, Action.EXECUTE);
        report(operation, amount, restored);
        return restored;
    }

    public static long restore(@Nullable QIOFrequency frequency, @Nullable ItemStack stack, long amount, String operation) {
        long restored = frequency == null || stack == null || stack.isEmpty() || amount <= 0 ? 0
              : frequency.massInsert(stack, amount, Action.EXECUTE);
        report(operation, amount, restored);
        return restored;
    }

    public static long restore(@Nullable QIOFrequency frequency, @Nullable FluidStack stack, long amount, String operation) {
        long restored = frequency == null || stack == null || stack.getFluid() == null || amount <= 0 ? 0
              : frequency.massInsert(stack, amount, Action.EXECUTE);
        report(operation, amount, restored);
        return restored;
    }

    public static long restore(@Nullable QIOFrequency frequency, @Nullable GasStack stack, long amount, String operation) {
        long restored = frequency == null || stack == null || stack.getGas() == null || amount <= 0 ? 0
              : frequency.massInsert(stack, amount, Action.EXECUTE);
        report(operation, amount, restored);
        return restored;
    }

    public static long restore(@Nullable QIOFrequency frequency, @Nullable QIOResourceDescriptor descriptor,
          long amount, String operation) {
        long restored = frequency == null || descriptor == null || amount <= 0 ? 0 :
              frequency.massInsert(descriptor, amount, Action.EXECUTE);
        report(operation, amount, restored);
        return restored;
    }

    private static void report(String operation, long expected, long restored) {
        if (expected > 0 && restored != expected) {
            Mekanism.logger.error("Unable to fully roll back QIO {}: expected {}, restored {}, unresolved {}",
                  operation, expected, restored, Math.max(0, expected - restored));
        }
    }
}
