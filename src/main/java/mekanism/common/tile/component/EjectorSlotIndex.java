package mekanism.common.tile.component;

import mekanism.api.inventory.IInventorySlot;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable layout index. Validate live layouts on each use; never retain item counts or requests.
 */
final class EjectorSlotIndex {
    private static final ClassValue<Boolean> IDENTITY_EQUALITY = new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("equals", Object.class).getDeclaringClass() == Object.class;
            } catch (ReflectiveOperationException | SecurityException unavailable) {
                return false;
            }
        }
    };
    private final IInventorySlot[] layout;
    private final Map<IInventorySlot, Integer> firstIndices = new IdentityHashMap<>();

    EjectorSlotIndex(List<IInventorySlot> slots) {
        layout = slots.toArray(new IInventorySlot[0]);
        for (int i = 0; i < layout.length; i++) firstIndices.putIfAbsent(layout[i], i);
    }

    boolean matches(List<IInventorySlot> slots) {
        if (slots.size() != layout.length) return false;
        for (int i = 0; i < layout.length; i++) if (slots.get(i) != layout[i]) return false;
        return true;
    }

    int indexOf(List<IInventorySlot> liveSlots, IInventorySlot slot) {
        // Preserve List.indexOf equality for extension slots that define value equality.
        if (slot != null && !IDENTITY_EQUALITY.get(slot.getClass())) return liveSlots.indexOf(slot);
        Integer index = firstIndices.get(slot);
        return index == null ? -1 : index;
    }
}
