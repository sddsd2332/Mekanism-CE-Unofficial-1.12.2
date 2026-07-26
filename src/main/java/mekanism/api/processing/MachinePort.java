package mekanism.api.processing;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsSnapshot;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * A stable machine input or output port backed by a Mekanism slot or tank.
 */
public abstract class MachinePort {

    public enum Role {
        INPUT,
        OUTPUT,
        BOTH;

        public boolean acceptsInput() {
            return this != OUTPUT;
        }

        public boolean allowsOutput() {
            return this != INPUT;
        }
    }

    private final MachineResourceKind kind;
    private final String portId;
    private final Role role;

    private MachinePort(MachineResourceKind kind, String portId, Role role) {
        this.kind = Objects.requireNonNull(kind, "Port kind cannot be null");
        this.portId = Objects.requireNonNull(portId, "Port id cannot be null");
        this.role = Objects.requireNonNull(role, "Port role cannot be null");
        if (portId.isEmpty()) {
            throw new IllegalArgumentException("Port id cannot be empty");
        }
    }

    public MachineResourceKind kind() {
        return kind;
    }

    public String portId() {
        return portId;
    }

    public Role role() {
        return role;
    }

    public final boolean canInsert(@Nullable MachineResourceStack stack) {
        return role.acceptsInput() && insert(stack, Action.SIMULATE);
    }

    public final boolean insert(@Nullable MachineResourceStack stack) {
        if (!role.acceptsInput() || !insert(stack, Action.SIMULATE)) {
            return false;
        }
        Snapshot snapshot = snapshot();
        if (insert(stack, Action.EXECUTE)) {
            return true;
        }
        snapshot.restore();
        return false;
    }

    public final boolean canExtract(@Nullable MachineResourceStack expected) {
        return role.allowsOutput() && extract(expected, Action.SIMULATE) != null;
    }

    @Nullable
    public final MachineResourceStack extract(@Nullable MachineResourceStack expected) {
        if (!role.allowsOutput() || extract(expected, Action.SIMULATE) == null) {
            return null;
        }
        Snapshot snapshot = snapshot();
        MachineResourceStack extracted = extract(expected, Action.EXECUTE);
        if (extracted != null) {
            return extracted;
        }
        snapshot.restore();
        return null;
    }

    @Nullable
    public abstract MachineResourceStack peek();

    protected abstract boolean insert(@Nullable MachineResourceStack stack, Action action);

    @Nullable
    protected abstract MachineResourceStack extract(@Nullable MachineResourceStack expected, Action action);

    public abstract Object container();

    public Snapshot snapshot() {
        Object container = container();
        IContentsSnapshot contentsSnapshot = container instanceof IContentsSnapshot snapshot ? snapshot : null;
        return new Snapshot(this, contentsSnapshot, contentsSnapshot == null ? serializeContainer() : contentsSnapshot.createContentsSnapshot());
    }

    protected abstract NBTTagCompound serializeContainer();

    protected abstract void deserializeContainer(NBTTagCompound nbt);

    @Nullable
    public static MachinePort item(String portId, Role role, @Nullable IInventorySlot slot) {
        return item(portId, role, slot, AutomationType.INTERNAL);
    }

    /**
     * Creates an item port using the supplied machine automation context.
     *
     * <p>Recipe-owned ports normally use {@link AutomationType#INTERNAL}. Output-only machines may use
     * {@link AutomationType#EXTERNAL} when their slots distinguish extractable products from retained configuration
     * items.</p>
     */
    @Nullable
    public static MachinePort item(String portId, Role role, @Nullable IInventorySlot slot, AutomationType automationType) {
        return slot == null ? null : new ItemPort(portId, role, slot,
              Objects.requireNonNull(automationType, "Automation type cannot be null"));
    }

    @Nullable
    public static MachinePort gas(String portId, Role role, @Nullable IExtendedGasTank tank) {
        return tank == null ? null : new GasPort(portId, role, tank);
    }

    @Nullable
    public static MachinePort fluid(String portId, Role role, @Nullable IExtendedFluidTank tank) {
        return tank == null ? null : new FluidPort(portId, role, tank);
    }

    public static final class Snapshot {

        private final MachinePort port;
        @Nullable
        private final IContentsSnapshot contentsSnapshot;
        private final NBTTagCompound nbt;

        private Snapshot(MachinePort port, @Nullable IContentsSnapshot contentsSnapshot, NBTTagCompound nbt) {
            this.port = port;
            this.contentsSnapshot = contentsSnapshot;
            this.nbt = nbt.copy();
        }

        public void restore() {
            if (contentsSnapshot == null) {
                port.deserializeContainer(nbt.copy());
            } else {
                contentsSnapshot.restoreContentsSnapshot(nbt.copy());
            }
        }
    }

    private static final class ItemPort extends MachinePort {

        private final IInventorySlot slot;
        private final AutomationType automationType;

        private ItemPort(String portId, Role role, IInventorySlot slot, AutomationType automationType) {
            super(MachineResourceKind.ITEM, portId, role);
            this.slot = slot;
            this.automationType = automationType;
        }

        @Override
        protected boolean insert(@Nullable MachineResourceStack stack, Action action) {
            ItemStack item = stack == null ? ItemStack.EMPTY : stack.itemStack();
            return stack != null && stack.kind() == kind() && !item.isEmpty() &&
                  slot.insertItem(item, action, automationType).isEmpty();
        }

        @Nullable
        @Override
        protected MachineResourceStack extract(@Nullable MachineResourceStack expected, Action action) {
            if (!isExpected(expected) || expected.amount() > Integer.MAX_VALUE) {
                return null;
            }
            ItemStack extracted = slot.extractItem((int) expected.amount(), action, automationType);
            if (extracted.isEmpty() || extracted.getCount() != expected.amount()) {
                return null;
            }
            MachineResourceStack result = MachineResourceStack.item(portId(), extracted);
            return result.sameResource(expected) ? result : null;
        }

        @Nullable
        @Override
        public MachineResourceStack peek() {
            return slot.isEmpty() ? null : MachineResourceStack.item(portId(), slot.getStack());
        }

        @Override
        public Object container() {
            return slot;
        }

        @Override
        protected NBTTagCompound serializeContainer() {
            return slot.serializeNBT();
        }

        @Override
        protected void deserializeContainer(NBTTagCompound nbt) {
            slot.deserializeNBT(nbt);
        }
    }

    private static final class GasPort extends MachinePort {

        private final IExtendedGasTank tank;

        private GasPort(String portId, Role role, IExtendedGasTank tank) {
            super(MachineResourceKind.GAS, portId, role);
            this.tank = tank;
        }

        @Override
        protected boolean insert(@Nullable MachineResourceStack stack, Action action) {
            GasStack gas = stack == null ? null : stack.gasStack();
            if (stack == null || stack.kind() != kind() || gas == null) {
                return false;
            }
            GasStack remainder = tank.insert(gas, action, AutomationType.INTERNAL);
            return remainder == null || remainder.amount <= 0;
        }

        @Nullable
        @Override
        protected MachineResourceStack extract(@Nullable MachineResourceStack expected, Action action) {
            if (!isExpected(expected) || expected.amount() > Integer.MAX_VALUE) {
                return null;
            }
            GasStack extracted = tank.extract((int) expected.amount(), action, AutomationType.INTERNAL);
            if (extracted == null || extracted.amount != expected.amount()) {
                return null;
            }
            MachineResourceStack result = MachineResourceStack.gas(portId(), extracted);
            return result.sameResource(expected) ? result : null;
        }

        @Nullable
        @Override
        public MachineResourceStack peek() {
            GasStack stack = tank.getStack();
            return stack == null || stack.amount <= 0 ? null : MachineResourceStack.gas(portId(), stack);
        }

        @Override
        public Object container() {
            return tank;
        }

        @Override
        protected NBTTagCompound serializeContainer() {
            return tank.serializeNBT();
        }

        @Override
        protected void deserializeContainer(NBTTagCompound nbt) {
            tank.deserializeNBT(nbt);
        }
    }

    private static final class FluidPort extends MachinePort {

        private final IExtendedFluidTank tank;

        private FluidPort(String portId, Role role, IExtendedFluidTank tank) {
            super(MachineResourceKind.FLUID, portId, role);
            this.tank = tank;
        }

        @Override
        protected boolean insert(@Nullable MachineResourceStack stack, Action action) {
            FluidStack fluid = stack == null ? null : stack.fluidStack();
            if (stack == null || stack.kind() != kind() || fluid == null) {
                return false;
            }
            FluidStack remainder = tank.insert(fluid, action, AutomationType.INTERNAL);
            return remainder == null || remainder.amount <= 0;
        }

        @Nullable
        @Override
        protected MachineResourceStack extract(@Nullable MachineResourceStack expected, Action action) {
            if (!isExpected(expected) || expected.amount() > Integer.MAX_VALUE) {
                return null;
            }
            FluidStack extracted = tank.extract((int) expected.amount(), action, AutomationType.INTERNAL);
            if (extracted == null || extracted.amount != expected.amount()) {
                return null;
            }
            MachineResourceStack result = MachineResourceStack.fluid(portId(), extracted);
            return result.sameResource(expected) ? result : null;
        }

        @Nullable
        @Override
        public MachineResourceStack peek() {
            FluidStack stack = tank.getFluid();
            return stack == null || stack.amount <= 0 ? null : MachineResourceStack.fluid(portId(), stack);
        }

        @Override
        public Object container() {
            return tank;
        }

        @Override
        protected NBTTagCompound serializeContainer() {
            return tank.serializeNBT();
        }

        @Override
        protected void deserializeContainer(NBTTagCompound nbt) {
            tank.deserializeNBT(nbt);
        }
    }

    protected final boolean isExpected(@Nullable MachineResourceStack expected) {
        if (expected == null || expected.kind() != kind() || expected.amount() <= 0) {
            return false;
        }
        MachineResourceStack stored = peek();
        return stored != null && stored.amount() >= expected.amount() && stored.sameResource(expected);
    }
}
