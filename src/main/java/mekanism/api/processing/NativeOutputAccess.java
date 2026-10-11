package mekanism.api.processing;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Built-in port native output contract. All reads/extractions must run inside the owner's local transaction.
 * Network calls must never be made from inside that transaction. Custom ports opt out by default.
 */
public final class NativeOutputAccess {
    private final MachinePort port;
    private final List<IInventorySlot> slots;
    private final IExtendedFluidTank fluid;
    private final IExtendedGasTank gas;
    private final AutomationType automation;

    NativeOutputAccess(MachinePort port, List<IInventorySlot> slots, IExtendedFluidTank fluid,
                       IExtendedGasTank gas, AutomationType automation) {
        this.port = port;
        this.slots = slots;
        this.fluid = fluid;
        this.gas = gas;
        this.automation = automation;
    }

    public List<NativeOutputStack> peekAll() {
        List<NativeOutputStack> result = new ArrayList<>();
        if (slots != null) {
            for (IInventorySlot slot : slots) {
                ItemStack stack = slot.getStack();
                if (stack.isEmpty()) continue;
                NativeOutputStack candidate = NativeOutputStack.item(stack);
                int index = 0;
                for (; index < result.size(); index++) {
                    NativeOutputStack existing = result.get(index);
                    if (existing.sameType(candidate) && existing.amount() <= Integer.MAX_VALUE - candidate.amount()) {
                        result.set(index, existing.withAmount(existing.amount() + candidate.amount()));
                        break;
                    }
                }
                if (index == result.size()) result.add(candidate);
            }
        } else if (fluid != null) {
            FluidStack stack = fluid.getFluid();
            if (stack != null && stack.amount > 0) result.add(NativeOutputStack.fluid(stack));
        } else {
            GasStack stack = gas.getGas();
            if (stack != null && stack.amount > 0) result.add(NativeOutputStack.gas(stack));
        }
        return result;
    }

    public boolean hasContents() {
        if (slots != null) {
            for (IInventorySlot slot : slots) if (!slot.isEmpty()) return true;
            return false;
        }
        return fluid != null ? fluid.getFluidAmount() > 0 : gas.getGasAmount() > 0;
    }

    /**
     * One validation and one rollback snapshot. A mismatching/partial extraction restores all local contents.
     */
    public NativeOutputStack extract(NativeOutputStack expected, Predicate<Object> mayExtract) {
        return transfer(expected, mayExtract, true, false);
    }

    NativeOutputStack simulate(NativeOutputStack expected) {
        return transfer(expected, container -> true, false, true);
    }

    /**
     * MachineTransferPlan already owns validation and rollback under the same machine lock.
     */
    NativeOutputStack extractPrepared(NativeOutputStack expected) {
        return transfer(expected, container -> true, false, false);
    }

    private NativeOutputStack transfer(NativeOutputStack expected, Predicate<Object> mayExtract,
                                       boolean ownSnapshot, boolean simulateOnly) {
        if (expected == null || expected.kind() != port.kind() || !port.role().allowsOutput() || port.isConfiguration())
            return null;
        List<IInventorySlot> selected = new ArrayList<>();
        List<Integer> amounts = new ArrayList<>();
        int remaining = expected.amount();
        if (slots != null) {
            for (IInventorySlot slot : slots) {
                if (remaining == 0) break;
                ItemStack current = slot.getStack();
                if (current.isEmpty() || !expected.sameType(NativeOutputStack.item(current))) continue;
                // A guarded member may not be taken even if other members are unguarded.
                if (!mayExtract.test(slot)) return null;
                int request = Math.min(remaining, current.getCount());
                if (ownSnapshot || simulateOnly) {
                    ItemStack simulated = slot.extractItem(request, Action.SIMULATE, automation);
                    if (simulated.isEmpty() || simulated.getCount() != request || !expected.sameType(NativeOutputStack.item(simulated)))
                        return null;
                }
                selected.add(slot);
                amounts.add(request);
                remaining -= request;
            }
            if (remaining != 0) return null;
        } else if (!mayExtract.test(port.container()) ||
                (ownSnapshot || simulateOnly) && !matches(expected, extractTank(expected.amount(), Action.SIMULATE))) {
            return null;
        }
        if (simulateOnly) return expected;
        MachinePort.Snapshot snapshot = ownSnapshot ? port.snapshot() : null;
        try {
            if (slots != null) {
                for (int i = 0; i < selected.size(); i++) {
                    IInventorySlot slot = selected.get(i);
                    if (!mayExtract.test(slot)) {
                        if (snapshot != null) snapshot.restore();
                        return null;
                    }
                    ItemStack actual = slot.extractItem(amounts.get(i), Action.EXECUTE, automation);
                    if (actual.isEmpty() || !matches(expected.withAmount(amounts.get(i)), NativeOutputStack.item(actual))) {
                        if (snapshot != null) snapshot.restore();
                        return null;
                    }
                }
            } else if (!mayExtract.test(port.container()) || !matches(expected, extractTank(expected.amount(), Action.EXECUTE))) {
                if (snapshot != null) snapshot.restore();
                return null;
            }
            return expected;
        } catch (RuntimeException | Error failure) {
            if (snapshot != null) {
                try {
                    snapshot.restore();
                } catch (RuntimeException | Error restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                }
            }
            throw failure;
        }
    }

    private NativeOutputStack extractTank(int amount, Action action) {
        if (fluid != null) {
            FluidStack result = fluid.extract(amount, action, automation);
            return result == null || result.amount <= 0 ? null : NativeOutputStack.fluid(result);
        }
        GasStack result = gas.extract(amount, action, automation);
        return result == null || result.amount <= 0 ? null : NativeOutputStack.gas(result);
    }

    private static boolean matches(NativeOutputStack expected, NativeOutputStack actual) {
        return actual != null && actual.amount() == expected.amount() && expected.sameType(actual);
    }
}
