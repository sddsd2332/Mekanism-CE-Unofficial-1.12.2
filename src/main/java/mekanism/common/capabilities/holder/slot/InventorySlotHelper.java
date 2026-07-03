package mekanism.common.capabilities.holder.slot;

import mekanism.api.RelativeSide;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.tile.component.TileComponentConfig;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class InventorySlotHelper {

    private final IInventorySlotHolder slotHolder;
    private boolean built;

    private InventorySlotHelper(IInventorySlotHolder slotHolder) {
        this.slotHolder = slotHolder;
    }

    public static InventorySlotHelper readOnly() {
        return new InventorySlotHelper(new ReadOnlyInventorySlotHolder());
    }

    public static InventorySlotHelper forSide(Supplier<EnumFacing> facingSupplier) {
        return forSide(facingSupplier, null, null);
    }

    public static InventorySlotHelper forSide(Supplier<EnumFacing> facingSupplier, @Nullable Predicate<RelativeSide> insertPredicate,
          @Nullable Predicate<RelativeSide> extractPredicate) {
        return new InventorySlotHelper(new InventorySlotHolder(facingSupplier, insertPredicate, extractPredicate));
    }

    public static InventorySlotHelper forSideWithConfig(Supplier<EnumFacing> facingSupplier, Supplier<TileComponentConfig> configSupplier) {
        return new InventorySlotHelper(new ConfigInventorySlotHolder(facingSupplier, configSupplier));
    }

    public static InventorySlotHelper forSideWithConfig(ISideConfiguration sideConfiguration) {
        return new InventorySlotHelper(new ConfigInventorySlotHolder(sideConfiguration));
    }

    public <SLOT extends IInventorySlot> SLOT addSlot(@Nonnull SLOT slot) {
        if (built) {
            throw new IllegalStateException("Builder has already built.");
        }
        if (slotHolder instanceof InventorySlotHolder) {
            ((InventorySlotHolder) slotHolder).addSlot(slot);
        } else if (slotHolder instanceof ReadOnlyInventorySlotHolder) {
            ((ReadOnlyInventorySlotHolder) slotHolder).addSlot(slot);
        } else if (slotHolder instanceof ConfigInventorySlotHolder) {
            ((ConfigInventorySlotHolder) slotHolder).addSlot(slot);
        } else {
            throw new IllegalArgumentException("Holder does not know how to add slots");
        }
        return slot;
    }

    public <SLOT extends IInventorySlot> SLOT addSlot(@Nonnull SLOT slot, RelativeSide... sides) {
        if (built) {
            throw new IllegalStateException("Builder has already built.");
        }
        if (slotHolder instanceof InventorySlotHolder) {
            ((InventorySlotHolder) slotHolder).addSlot(slot, sides);
        } else {
            throw new IllegalArgumentException("Holder does not know how to add slots on specific sides");
        }
        return slot;
    }

    public IInventorySlotHolder build() {
        built = true;
        return slotHolder;
    }
}
