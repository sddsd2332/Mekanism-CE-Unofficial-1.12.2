package mekanism.common.inventory;

import mekanism.api.IContentsListener;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Derived state for queued native-inventory notifications, never for external inventory reads.
 * Writers only set atomic dirty bits; the server thread consumes them after the async work barrier.
 * Slot contents follow IInventorySlot's mutation/notification contract. Explicit markDirty and live
 * comparator reads invalidate the snapshot, so vanilla inventory callers can still edit then mark.
 */
public final class InventoryComparatorTracker implements AutoCloseable {
    private static final ClassValue<Boolean> TRACKABLE = new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            // Do not assume that extension slots preserve BasicInventorySlot's mutation contract.
            if (!type.getName().startsWith("mekanism.common.inventory.slot.") ||
                    type.getClassLoader() != BasicInventorySlot.class.getClassLoader()) return false;
            try {
                return type.getMethod("getStack").getDeclaringClass() == BasicInventorySlot.class &&
                        type.getMethod("onContentsChanged").getDeclaringClass() == BasicInventorySlot.class;
            } catch (ReflectiveOperationException | SecurityException error) {
                return false;
            }
        }
    };
    private static final ClassValue<Boolean> SIMPLE_LIMIT = new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                java.lang.reflect.Method plainLimit;
                try {
                    plainLimit = type.getMethod("getItemStackLimit");
                } catch (NoSuchMethodException obfuscated) {
                    plainLimit = type.getMethod("func_77639_j");
                }
                return type.getMethod("getItemStackLimit", ItemStack.class).getDeclaringClass() == Item.class &&
                        plainLimit.getDeclaringClass() == Item.class;
            } catch (ReflectiveOperationException | SecurityException error) {
                return false;
            }
        }
    };

    private final BasicInventorySlot[] slots;
    private final IContentsListener[] listeners;
    private final AtomicLongArray dirty;
    private final AtomicBoolean invalid = new AtomicBoolean(true);
    private final BitSet dynamicLimits = new BitSet();
    private final Map<Item, Integer> simpleLimits = new IdentityHashMap<>();
    private final float[] contributions;
    private final float[] prefixes;
    private final boolean[] occupied;
    private int occupiedCount;
    private int inventoryLimit;
    private int level;

    private InventoryComparatorTracker(List<IInventorySlot> view) {
        int size = view.size();
        slots = new BasicInventorySlot[size];
        listeners = new IContentsListener[size];
        contributions = new float[size];
        prefixes = new float[size + 1];
        occupied = new boolean[size];
        dirty = new AtomicLongArray((size + 63) >>> 6);
    }

    /**
     * null means the caller must use the original live computation for this holder.
     */
    public static InventoryComparatorTracker create(List<IInventorySlot> view) {
        InventoryComparatorTracker tracker = new InventoryComparatorTracker(view);
        for (int i = 0; i < view.size(); i++) {
            IInventorySlot slot = view.get(i);
            if (!(slot instanceof BasicInventorySlot basic) || !TRACKABLE.get(slot.getClass())) {
                tracker.close();
                return null;
            }
            int index = i;
            IContentsListener listener = () -> tracker.markChanged(index);
            if (!basic.attachComparatorListener(listener)) {
                tracker.close();
                return null;
            }
            tracker.slots[i] = basic;
            tracker.listeners[i] = listener;
        }
        return tracker;
    }

    private void markChanged(int index) {
        int word = index >>> 6;
        long mask = 1L << (index & 63), before;
        do {
            before = dirty.get(word);
            if ((before & mask) != 0) return;
        } while (!dirty.compareAndSet(word, before, before | mask));
    }

    public void invalidate() {
        invalid.set(true);
    }

    public int size() {
        return slots.length;
    }

    public int getLevel(int limit) {
        boolean full = invalid.getAndSet(false) || inventoryLimit != limit;
        inventoryLimit = limit;
        try {
            // Base Item limits can be changed globally without touching an inventory. Check each
            // distinct item once, rather than checking every stack of that item in the factory.
            for (Map.Entry<Item, Integer> entry : simpleLimits.entrySet()) {
                if (entry.getKey().getItemStackLimit() != entry.getValue()) {
                    full = true;
                    break;
                }
            }
            int firstChanged = slots.length;
            if (full) {
                simpleLimits.clear();
                dynamicLimits.clear();
                // Clear before reading: a concurrent notification remains pending for the next pass.
                for (int word = 0; word < dirty.length(); word++) dirty.getAndSet(word, 0);
                for (int i = 0; i < slots.length; i++) refresh(i);
                firstChanged = 0;
            } else {
                for (int word = 0; word < dirty.length(); word++) {
                    long bits = dirty.getAndSet(word, 0);
                    while (bits != 0) {
                        int index = (word << 6) + Long.numberOfTrailingZeros(bits);
                        if (refresh(index)) firstChanged = Math.min(firstChanged, index);
                        bits &= bits - 1;
                    }
                }
                // Stack-sensitive/custom limits may depend on NBT, capabilities or external state.
                // Poll those slots on every notification; do not freeze an arbitrary mod's result.
                for (int i = dynamicLimits.nextSetBit(0); i >= 0; i = dynamicLimits.nextSetBit(i + 1)) {
                    if (refresh(i)) firstChanged = Math.min(firstChanged, i);
                }
            }
            if (firstChanged < slots.length) {
                // Replay the original left-to-right float additions from the first changed term.
                // A running sum += new-old or a tree reduction changes rounding at signal edges.
                for (int i = firstChanged; i < slots.length; i++) {
                    prefixes[i + 1] = occupied[i] ? prefixes[i] + contributions[i] : prefixes[i];
                }
                level = MathHelper.floor(prefixes[slots.length] / (float) slots.length * 14.0F) +
                        (occupiedCount > 0 ? 1 : 0);
            }
            return level;
        } catch (RuntimeException | Error error) {
            invalid.set(true);
            throw error;
        }
    }

    private boolean refresh(int index) {
        ItemStack stack = slots[index].getStack();
        boolean present = !stack.isEmpty();
        float contribution = 0;
        if (present) {
            Item item = stack.getItem();
            boolean simple = stack.getClass() == ItemStack.class && SIMPLE_LIMIT.get(item.getClass());
            dynamicLimits.set(index, !simple);
            if (simple) {
                simpleLimits.put(item, item.getItemStackLimit());
                // Do not retain every item ever put into a long-lived machine.
                if (simpleLimits.size() > slots.length * 2 + 8) invalid.set(true);
            }
            contribution = (float) stack.getCount() / (float) Math.min(inventoryLimit, stack.getMaxStackSize());
        } else {
            dynamicLimits.clear(index);
        }
        boolean changed = present != occupied[index] ||
                Float.floatToIntBits(contribution) != Float.floatToIntBits(contributions[index]);
        if (present != occupied[index]) occupiedCount += present ? 1 : -1;
        occupied[index] = present;
        contributions[index] = contribution;
        return changed;
    }

    @Override
    public void close() {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) slots[i].detachComparatorListener(listeners[i]);
        }
    }
}
