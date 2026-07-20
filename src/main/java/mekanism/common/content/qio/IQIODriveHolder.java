package mekanism.common.content.qio;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Stable world identity used by the world-level QIO drive mount lock.
 */
public interface IQIODriveHolder extends IQIOFrequencyHolder {

    int getQIODimension();

    BlockPos getQIOPosition();

    default List<ItemStack> getQIODriveStacks() {
        return Collections.emptyList();
    }

    /**
     * Writes a drive stack back to the holder's authoritative inventory.
     *
     * <p>Some inventory implementations expose a defensive copy from
     * {@code getQIODriveStacks()}.  QIO assigns the drive UUID and updates its
     * display metadata while mounting and transacting, so a holder must offer
     * a write path as well as the scan path.  Implementations that return live
     * stack references may keep the default no-op.</p>
     */
    default void updateQIODriveStack(int slot, ItemStack stack) {
    }

    /** Called after a physical drive slot changes so its frequency can remount. */
    default void onQIODriveSlotChanged(int slot) {
        QIOFrequency frequency = getQIOFrequency();
        if (frequency != null) {
            frequency.requestRefresh();
        }
    }

    default void setQIODriveSlotState(int slot, QIODriveSlotState state) {
    }

    @Override
    @Nullable
    default QIOFrequency getQIOFrequency() {
        return null;
    }
}
