package mekanism.common.capabilities.holder;

import mekanism.api.RelativeSide;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Supplier;

public class BasicHolder<TYPE> implements IHolder {

    private final Map<RelativeSide, List<TYPE>> directionalSlots = new EnumMap<>(RelativeSide.class);
    private final List<TYPE> inventorySlots = new ArrayList<>();
    protected final Supplier<EnumFacing> facingSupplier;

    protected BasicHolder(Supplier<EnumFacing> facingSupplier) {
        this.facingSupplier = facingSupplier;
    }

    protected void addSlotInternal(@Nonnull TYPE slot, RelativeSide... sides) {
        inventorySlots.add(slot);
        for (RelativeSide side : sides) {
            directionalSlots.computeIfAbsent(side, ignored -> new ArrayList<>()).add(slot);
        }
    }

    @Nonnull
    public List<TYPE> getSlots(@Nullable EnumFacing side) {
        if (side == null || directionalSlots.isEmpty()) {
            return inventorySlots;
        }
        List<TYPE> slots = directionalSlots.get(RelativeSide.fromDirections(facingSupplier.get(), side));
        return slots == null ? Collections.emptyList() : slots;
    }
}
