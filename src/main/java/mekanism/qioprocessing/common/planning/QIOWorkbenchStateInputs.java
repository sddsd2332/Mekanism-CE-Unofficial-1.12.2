package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.planning.QIOWorkbenchDeclarations.Stack;

import java.util.List;

/**
 * Pure admission hints for returned tools/containers. A hint never proves Ingredient.matches.
 */
final class QIOWorkbenchStateInputs {
    enum Use {CONSUMED, RETURNED_CONTAINER, REUSABLE, DURABILITY, STATEFUL}

    private QIOWorkbenchStateInputs() {
    }

    static boolean sameItem(PortableResourceDescriptor first, PortableResourceDescriptor second) {
        String id = first.getDescriptor().getPersistedItemId();
        return !id.isEmpty() && id.equals(second.getDescriptor().getPersistedItemId());
    }

    static boolean[] slots(List<Stack> grid, List<Stack> remainders, boolean[] virtual) {
        boolean[] result = new boolean[9];
        for (int slot = 0; slot < 9; slot++) {
            Stack input = grid.get(slot), remainder = remainders.get(slot);
            result[slot] = !virtual[slot] && !input.isEmpty() && !remainder.isEmpty() &&
                    sameItem(input.getResource(), remainder.getResource());
        }
        return result;
    }

    static boolean any(boolean[] slots) {
        for (boolean slot : slots) if (slot) return true;
        return false;
    }

    /**
     * A detached classification of a verified transition, not a prediction of future crafts.
     */
    static Use use(Stack input, Stack remainder, boolean virtual) {
        if (virtual || input.isEmpty() || remainder.isEmpty()) return Use.CONSUMED;
        if (input.getResource().equals(remainder.getResource()) && input.getAmount() == remainder.getAmount())
            return Use.REUSABLE;
        if (!sameItem(input.getResource(), remainder.getResource())) return Use.RETURNED_CONTAINER;
        net.minecraft.nbt.NBTTagCompound before = input.getResource().getDescriptor().getPayload();
        net.minecraft.nbt.NBTTagCompound after = remainder.getResource().getDescriptor().getPayload();
        int oldDamage = before.getShort("Damage"), newDamage = after.getShort("Damage");
        before.removeTag("Damage");
        after.removeTag("Damage");
        if (newDamage > oldDamage && before.equals(after) && input.getAmount() == remainder.getAmount())
            return Use.DURABILITY;
        return Use.STATEFUL;
    }

    static boolean changesState(List<Stack> grid, List<Stack> remainders, boolean[] virtual) {
        for (int slot = 0; slot < 9; slot++) {
            Use use = use(grid.get(slot), remainders.get(slot), virtual[slot]);
            if (use == Use.DURABILITY || use == Use.STATEFUL) return true;
        }
        return false;
    }
}
