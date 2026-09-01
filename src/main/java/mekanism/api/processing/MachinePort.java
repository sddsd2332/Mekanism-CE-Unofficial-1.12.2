package mekanism.api.processing;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsSnapshot;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import mekanism.api.qio.resource.QIOResourceFamilyMatcher;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A stable machine input or output port backed by a Mekanism slot or tank.
 */
public abstract class MachinePort {

    /** Lane marker for a port group shared by every processing lane. */
    public static final long SHARED_LANE = -1;

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

    public enum Purpose {
        PROCESSING,
        CONFIGURATION
    }

    private final MachineResourceKind kind;
    private final QIOResourceFamilyMatcher resourceMatcher;
    private final String portId;
    private final String portGroupId;
    private final long laneId;
    private final Role role;
    private final Purpose purpose;

    private MachinePort(MachineResourceKind kind, String portId, Role role, Purpose purpose,
          String portGroupId, long laneId) {
        this(Objects.requireNonNull(kind, "Port kind cannot be null"), matcherFor(kind), portId,
              role, purpose, portGroupId, laneId);
    }

    /**
     * Constructor for codec-backed integration ports. A matcher is exact: family and codec ids are compared as
     * complete strings, and matching a family never implies conversion between codecs.
     */
    protected MachinePort(@Nonnull QIOResourceFamilyMatcher resourceMatcher, String portId, Role role,
          Purpose purpose, String portGroupId, long laneId) {
        this(inferLegacyKind(resourceMatcher), resourceMatcher, portId, role, purpose, portGroupId, laneId);
    }

    private MachinePort(MachineResourceKind kind, QIOResourceFamilyMatcher resourceMatcher,
          String portId, Role role, Purpose purpose, String portGroupId, long laneId) {
        this.kind = Objects.requireNonNull(kind, "Port kind cannot be null");
        this.resourceMatcher = Objects.requireNonNull(resourceMatcher, "Port resource matcher cannot be null");
        this.portId = Objects.requireNonNull(portId, "Port id cannot be null");
        this.portGroupId = Objects.requireNonNull(portGroupId, "Port group id cannot be null");
        if (laneId < SHARED_LANE) {
            throw new IllegalArgumentException("Lane id must be shared (-1) or non-negative");
        }
        this.laneId = laneId;
        this.role = Objects.requireNonNull(role, "Port role cannot be null");
        this.purpose = Objects.requireNonNull(purpose, "Port purpose cannot be null");
        if (purpose == Purpose.CONFIGURATION && role != Role.BOTH) {
            throw new IllegalArgumentException("Configuration ports must support controlled replacement");
        }
        if (portId.isEmpty() || portGroupId.isEmpty()) {
            throw new IllegalArgumentException("Port and port group ids cannot be empty");
        }
    }

    public MachineResourceKind kind() {
        return kind;
    }

    @Nonnull
    public QIOResourceFamilyMatcher resourceMatcher() {
        return resourceMatcher;
    }

    public final boolean acceptsResource(@Nullable MachineResourceStack stack) {
        return stack != null && acceptsDescriptor(stack.descriptor());
    }

    public final boolean acceptsDescriptor(@Nullable QIOResourceDescriptor descriptor) {
        return descriptor != null && descriptor.isResolved() && resourceMatcher.accepts(descriptor);
    }

    public String portId() {
        return portId;
    }

    public String portGroupId() {
        return portGroupId;
    }

    public long laneId() {
        return laneId;
    }

    public Role role() {
        return role;
    }

    public Purpose purpose() {
        return purpose;
    }

    public boolean isConfiguration() {
        return purpose == Purpose.CONFIGURATION;
    }

    /**
     * Returns the number of physical storage units represented by this port.
     * Single slots and tanks expose one unit; grouped item ports expose their slot count.
     */
    public int storageUnitCount() {
        return 1;
    }

    public final boolean canInsert(@Nullable MachineResourceStack stack) {
        return role.acceptsInput() && acceptsResource(stack) && insert(stack, Action.SIMULATE);
    }

    public final boolean insert(@Nullable MachineResourceStack stack) {
        if (!role.acceptsInput() || !acceptsResource(stack) || !insert(stack, Action.SIMULATE)) {
            return false;
        }
        Snapshot snapshot = snapshot();
        try {
            if (insert(stack, Action.EXECUTE)) {
                return true;
            }
        } catch (RuntimeException | Error failure) {
            restoreAfterFailure(snapshot, failure);
            throw failure;
        }
        snapshot.restore();
        return false;
    }

    public final boolean canExtract(@Nullable MachineResourceStack expected) {
        return role.allowsOutput() && acceptsResource(expected) && extract(expected, Action.SIMULATE) != null;
    }

    /** Exact remaining capacity for this resource, independent of whether the port is input or output. */
    public abstract long getAvailableCapacity(@Nullable MachineResourceStack stack);

    @Nullable
    public final MachineResourceStack extract(@Nullable MachineResourceStack expected) {
        if (!role.allowsOutput() || !acceptsResource(expected) || extract(expected, Action.SIMULATE) == null) {
            return null;
        }
        Snapshot snapshot = snapshot();
        try {
            MachineResourceStack extracted = extract(expected, Action.EXECUTE);
            if (extracted != null) {
                return extracted;
            }
        } catch (RuntimeException | Error failure) {
            restoreAfterFailure(snapshot, failure);
            throw failure;
        }
        snapshot.restore();
        return null;
    }

    private static void restoreAfterFailure(Snapshot snapshot, Throwable failure) {
        try {
            snapshot.restore();
        } catch (RuntimeException | Error restoreFailure) {
            failure.addSuppressed(restoreFailure);
        }
    }

    @Nullable
    public abstract MachineResourceStack peek();

    /**
     * Returns every resource currently held by this port, grouped by resource identity.
     * Single-slot ports expose their normal peek value as a singleton list; grouped item
     * ports override this so mixed contents remain visible to lease baselines.
     */
    @Nonnull
    public List<MachineResourceStack> peekAll() {
        MachineResourceStack current = peek();
        return current == null ? Collections.emptyList() : Collections.singletonList(current);
    }

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

    private static QIOResourceFamilyMatcher matcherFor(MachineResourceKind kind) {
        if (kind.getCodecId() == null) {
            throw new IllegalArgumentException("Custom machine ports require an explicit resource matcher");
        }
        return QIOResourceFamilyMatcher.codec(kind.getCodecId());
    }

    private static MachineResourceKind inferLegacyKind(QIOResourceFamilyMatcher matcher) {
        Objects.requireNonNull(matcher, "Port resource matcher cannot be null");
        if (!matcher.isAny() && matcher.getFamilies().isEmpty() && matcher.getCodecIds().size() == 1) {
            net.minecraft.util.ResourceLocation codecId = matcher.getCodecIds().iterator().next();
            for (MachineResourceKind kind : MachineResourceKind.values()) {
                if (codecId.equals(kind.getCodecId())) {
                    return kind;
                }
            }
        }
        return MachineResourceKind.CUSTOM;
    }

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
        return item(portId, role, slot, automationType, portId, 0);
    }

    @Nullable
    public static MachinePort item(String portId, Role role, @Nullable IInventorySlot slot, String portGroupId, long laneId) {
        return item(portId, role, slot, AutomationType.INTERNAL, portGroupId, laneId);
    }

    @Nullable
    public static MachinePort item(String portId, Role role, @Nullable IInventorySlot slot, AutomationType automationType,
          String portGroupId, long laneId) {
        return slot == null ? null : new ItemPort(portId, role, slot,
              Objects.requireNonNull(automationType, "Automation type cannot be null"), Purpose.PROCESSING,
              portGroupId, laneId);
    }

    @Nullable
    public static MachinePort configurationItem(String portId, @Nullable IInventorySlot slot,
          String portGroupId, long laneId) {
        return slot == null ? null : new ItemPort(portId, Role.BOTH, slot,
              AutomationType.INTERNAL, Purpose.CONFIGURATION, portGroupId, laneId);
    }

    @Nullable
    public static MachinePort itemGroup(String portId, Role role,
          @Nullable List<? extends IInventorySlot> slots, String portGroupId, long laneId) {
        if (slots == null || slots.isEmpty() || slots.stream().anyMatch(Objects::isNull)) {
            return null;
        }
        return new ItemGroupPort(portId, role, slots, Purpose.PROCESSING, portGroupId, laneId);
    }

    @Nullable
    public static MachinePort gas(String portId, Role role, @Nullable IExtendedGasTank tank) {
        return gas(portId, role, tank, portId, 0);
    }

    @Nullable
    public static MachinePort gas(String portId, Role role, @Nullable IExtendedGasTank tank, String portGroupId, long laneId) {
        return tank == null ? null : new GasPort(portId, role, tank, Purpose.PROCESSING,
              portGroupId, laneId);
    }

    @Nullable
    public static MachinePort configurationGas(String portId, @Nullable IExtendedGasTank tank,
          String portGroupId, long laneId) {
        return tank == null ? null : new GasPort(portId, Role.BOTH, tank,
              Purpose.CONFIGURATION, portGroupId, laneId);
    }

    @Nullable
    public static MachinePort fluid(String portId, Role role, @Nullable IExtendedFluidTank tank) {
        return fluid(portId, role, tank, portId, 0);
    }

    @Nullable
    public static MachinePort fluid(String portId, Role role, @Nullable IExtendedFluidTank tank, String portGroupId, long laneId) {
        return tank == null ? null : new FluidPort(portId, role, tank, Purpose.PROCESSING,
              portGroupId, laneId);
    }

    @Nullable
    public static MachinePort configurationFluid(String portId,
          @Nullable IExtendedFluidTank tank, String portGroupId, long laneId) {
        return tank == null ? null : new FluidPort(portId, Role.BOTH, tank,
              Purpose.CONFIGURATION, portGroupId, laneId);
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

        private ItemPort(String portId, Role role, IInventorySlot slot, AutomationType automationType,
              Purpose purpose, String portGroupId, long laneId) {
            super(MachineResourceKind.ITEM, portId, role, purpose, portGroupId, laneId);
            this.slot = slot;
            this.automationType = automationType;
        }

        @Override
        protected boolean insert(@Nullable MachineResourceStack stack, Action action) {
            ItemStack item = stack == null ? ItemStack.EMPTY : stack.itemStack();
            return acceptsResource(stack) && !item.isEmpty() &&
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
        public long getAvailableCapacity(@Nullable MachineResourceStack stack) {
            if (!acceptsResource(stack)) {
                return 0;
            }
            ItemStack expected = stack.itemStack();
            if (expected.isEmpty()) {
                return 0;
            }
            ItemStack current = slot.getStack();
            if (!current.isEmpty() && (!ItemStack.areItemsEqual(current, expected) ||
                  !ItemStack.areItemStackTagsEqual(current, expected))) {
                return 0;
            }
            return Math.max(0, (long) slot.getLimit(expected) -
                  (current.isEmpty() ? 0 : current.getCount()));
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

    private static final class ItemGroupPort extends MachinePort {

        private final List<IInventorySlot> slots;

        private ItemGroupPort(String portId, Role role, List<? extends IInventorySlot> slots,
              Purpose purpose, String portGroupId, long laneId) {
            super(MachineResourceKind.ITEM, portId, role, purpose, portGroupId, laneId);
            this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
        }

        @Override
        protected boolean insert(@Nullable MachineResourceStack stack, Action action) {
            if (!acceptsResource(stack) || stack.amount() > Integer.MAX_VALUE) {
                return false;
            }
            ItemStack expected = stack.itemStack();
            if (expected.isEmpty()) {
                return false;
            }
            List<IInventorySlot> compatible = new ArrayList<>();
            for (IInventorySlot slot : slots) {
                ItemStack current = slot.getStack();
                if (current.isEmpty() || ItemStack.areItemsEqual(current, expected) &&
                      ItemStack.areItemStackTagsEqual(current, expected)) {
                    compatible.add(slot);
                }
            }
            int remaining = (int) stack.amount();
            for (int index = 0; index < compatible.size() && remaining > 0; index++) {
                IInventorySlot slot = compatible.get(index);
                ItemStack current = slot.getStack();
                int capacity = Math.max(0, slot.getLimit(expected) -
                      (current.isEmpty() ? 0 : current.getCount()));
                if (capacity == 0) {
                    continue;
                }
                int slotsLeft = compatible.size() - index;
                int share = (remaining + slotsLeft - 1) / slotsLeft;
                ItemStack offered = expected.copy();
                offered.setCount(Math.min(share, capacity));
                if (offered.isEmpty()) {
                    continue;
                }
                ItemStack remainder = slot.insertItem(offered, action, AutomationType.INTERNAL);
                int inserted = offered.getCount() - (remainder.isEmpty() ? 0 : remainder.getCount());
                remaining -= inserted;
            }
            return remaining == 0;
        }

        @Nullable
        @Override
        protected MachineResourceStack extract(@Nullable MachineResourceStack expected,
              Action action) {
            if (!acceptsResource(expected) ||
                  expected.amount() > Integer.MAX_VALUE) {
                return null;
            }
            ItemStack resource = expected.itemStack();
            if (resource.isEmpty()) {
                return null;
            }
            int remaining = (int) expected.amount();
            int extractedAmount = 0;
            for (IInventorySlot slot : slots) {
                ItemStack stored = slot.getStack();
                if (stored.isEmpty()) {
                    continue;
                }
                if (!ItemStack.areItemsEqual(stored, resource) ||
                      !ItemStack.areItemStackTagsEqual(stored, resource)) {
                    continue;
                }
                int requested = Math.min(remaining, stored.getCount());
                ItemStack extracted = slot.extractItem(requested, action, AutomationType.INTERNAL);
                if (extracted.isEmpty() || extracted.getCount() != requested ||
                      !ItemStack.areItemsEqual(extracted, resource) ||
                      !ItemStack.areItemStackTagsEqual(extracted, resource)) {
                    return null;
                }
                remaining -= requested;
                extractedAmount += requested;
                if (remaining == 0) {
                    break;
                }
            }
            return remaining == 0 ? MachineResourceStack.item(portId(), resource,
                  extractedAmount) : null;
        }

        @Nullable
        @Override
        public MachineResourceStack peek() {
            List<MachineResourceStack> resources = peekAll();
            return resources.isEmpty() ? null : resources.get(0);
        }

        @Nonnull
        @Override
        public List<MachineResourceStack> peekAll() {
            List<MachineResourceStack> resources = new ArrayList<>();
            for (IInventorySlot slot : slots) {
                ItemStack stored = slot.getStack();
                if (stored.isEmpty()) {
                    continue;
                }
                MachineResourceStack existing = null;
                for (MachineResourceStack resource : resources) {
                    if (resource.sameResource(MachineResourceStack.item(portId(), stored))) {
                        existing = resource;
                        break;
                    }
                }
                if (existing == null) {
                    resources.add(MachineResourceStack.item(portId(), stored));
                } else {
                    long amount = existing.amount();
                    if (amount > Long.MAX_VALUE - stored.getCount()) {
                        throw new IllegalStateException("Grouped machine port item amount overflow");
                    }
                    resources.set(resources.indexOf(existing), existing.withAmount(
                          amount + stored.getCount()));
                }
            }
            return Collections.unmodifiableList(resources);
        }

        @Override
        public int storageUnitCount() {
            return slots.size();
        }

        @Override
        public Object container() {
            return slots;
        }

        @Override
        public long getAvailableCapacity(@Nullable MachineResourceStack stack) {
            if (!acceptsResource(stack)) {
                return 0;
            }
            ItemStack expected = stack.itemStack();
            if (expected.isEmpty()) {
                return 0;
            }
            long capacity = 0;
            for (IInventorySlot slot : slots) {
                ItemStack current = slot.getStack();
                if (!current.isEmpty() && (!ItemStack.areItemsEqual(current, expected) ||
                      !ItemStack.areItemStackTagsEqual(current, expected))) {
                    continue;
                }
                int room = Math.max(0, slot.getLimit(expected) -
                      (current.isEmpty() ? 0 : current.getCount()));
                if (room == 0) {
                    continue;
                }
                ItemStack offered = expected.copy();
                offered.setCount(room);
                ItemStack remainder = slot.insertItem(offered, Action.SIMULATE,
                      AutomationType.INTERNAL);
                capacity = Math.addExact(capacity,
                      offered.getCount() - (remainder.isEmpty() ? 0 : remainder.getCount()));
            }
            return capacity;
        }

        @Override
        protected NBTTagCompound serializeContainer() {
            NBTTagCompound data = new NBTTagCompound();
            NBTTagList stored = new NBTTagList();
            for (IInventorySlot slot : slots) {
                stored.appendTag(slot.serializeNBT());
            }
            data.setTag("slots", stored);
            return data;
        }

        @Override
        protected void deserializeContainer(NBTTagCompound nbt) {
            NBTTagList stored = nbt.getTagList("slots", NBT.TAG_COMPOUND);
            if (stored.tagCount() != slots.size()) {
                throw new IllegalArgumentException("Grouped machine port slot count changed");
            }
            for (int index = 0; index < slots.size(); index++) {
                slots.get(index).deserializeNBT(stored.getCompoundTagAt(index));
            }
        }
    }

    private static final class GasPort extends MachinePort {

        private final IExtendedGasTank tank;

        private GasPort(String portId, Role role, IExtendedGasTank tank, Purpose purpose,
              String portGroupId, long laneId) {
            super(MachineResourceKind.GAS, portId, role, purpose, portGroupId, laneId);
            this.tank = tank;
        }

        @Override
        protected boolean insert(@Nullable MachineResourceStack stack, Action action) {
            GasStack gas = stack == null ? null : stack.gasStack();
            if (!acceptsResource(stack) || gas == null) {
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
        public long getAvailableCapacity(@Nullable MachineResourceStack stack) {
            if (!acceptsResource(stack) || stack.gasStack() == null) {
                return 0;
            }
            GasStack current = tank.getGas();
            if (current != null && !current.isGasEqual(stack.gasStack())) {
                return 0;
            }
            return Math.max(0, (long) tank.getCapacity() - tank.getGasAmount());
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

        private FluidPort(String portId, Role role, IExtendedFluidTank tank, Purpose purpose,
              String portGroupId, long laneId) {
            super(MachineResourceKind.FLUID, portId, role, purpose, portGroupId, laneId);
            this.tank = tank;
        }

        @Override
        protected boolean insert(@Nullable MachineResourceStack stack, Action action) {
            FluidStack fluid = stack == null ? null : stack.fluidStack();
            if (!acceptsResource(stack) || fluid == null) {
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
        public long getAvailableCapacity(@Nullable MachineResourceStack stack) {
            if (!acceptsResource(stack) || stack.fluidStack() == null) {
                return 0;
            }
            FluidStack current = tank.getFluid();
            if (current != null && !current.isFluidEqual(stack.fluidStack())) {
                return 0;
            }
            return Math.max(0, (long) tank.getCapacity() - tank.getFluidAmount());
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
        if (!acceptsResource(expected) || expected.amount() <= 0) {
            return false;
        }
        MachineResourceStack stored = peek();
        return stored != null && stored.amount() >= expected.amount() && stored.sameResource(expected);
    }
}
