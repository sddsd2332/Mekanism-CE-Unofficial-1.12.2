package mekanism.common.item.interfaces;

import mekanism.api.EnumColor;
import mekanism.api.NBTConstants;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.IColorableFrequency;
import mekanism.common.frequency.IFrequencyItem;
import mekanism.common.util.ItemDataUtils;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nullable;

/** Item-side color cache used by frequency-linked items. */
public interface IColoredItem {

    int DEFAULT_TINT = 0x555555;

    @Nullable
    default EnumColor getColor(ItemStack stack) {
        if (ItemDataUtils.hasData(stack, NBTConstants.COLOR, NBT.TAG_INT)) {
            int index = ItemDataUtils.getInt(stack, NBTConstants.COLOR);
            return index >= 0 && index < EnumColor.values().length ? EnumColor.values()[index] : null;
        }
        return null;
    }

    default void setColor(ItemStack stack, @Nullable EnumColor color) {
        if (color == null) {
            ItemDataUtils.removeData(stack, NBTConstants.COLOR);
        } else {
            ItemDataUtils.setInt(stack, NBTConstants.COLOR, color.ordinal());
        }
    }

    /** Synchronizes the cached item color with the frequency stored on this item. */
    default void syncColorWithFrequency(ItemStack stack) {
        syncColorWithFrequency(stack, null);
    }

    /** Synchronizes using an already resolved frequency when one is available. */
    default void syncColorWithFrequency(ItemStack stack, @Nullable Frequency resolvedFrequency) {
        if (!(this instanceof IFrequencyItem frequencyItem)) {
            return;
        }
        Frequency frequency = resolvedFrequency == null ? frequencyItem.getFrequencyAware(stack).frequency() : resolvedFrequency;
        if (frequency == null) {
            FrequencyIdentity identity = frequencyItem.getFrequency(stack);
            if (identity != null) {
                frequency = frequencyItem.getFrequencyType().getFrequency(identity, identity.ownerUUID());
            }
        }
        EnumColor frequencyColor = frequency instanceof IColorableFrequency ? ((IColorableFrequency) frequency).getColor() : null;
        if (getColor(stack) != frequencyColor) {
            setColor(stack, frequencyColor);
        }
    }
}
