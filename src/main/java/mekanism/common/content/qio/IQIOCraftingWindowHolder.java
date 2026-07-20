package mekanism.common.content.qio;

import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * Owns the persistent crafting windows exposed by a QIO viewer.
 *
 * <p>The holder is deliberately separate from the viewer container. A block
 * stores its windows in tile NBT while the portable dashboard stores them in
 * the item stack, but both use exactly the same server-side crafting logic.</p>
 */
public interface IQIOCraftingWindowHolder {

    byte MAX_CRAFTING_WINDOWS = 3;

    @Nullable
    World getHolderWorld();

    QIOCraftingWindow[] getCraftingWindows();

    @Nullable
    QIOFrequency getFrequency();

    @Nullable
    default QIOFrequency getQIOFrequency() {
        return getFrequency();
    }

    void onCraftingWindowContentsChanged();

    @Nullable
    default QIOCraftingWindow getCraftingWindow(byte index) {
        QIOCraftingWindow[] windows = getCraftingWindows();
        return windows != null && index >= 0 && index < windows.length ? windows[index] : null;
    }
}
