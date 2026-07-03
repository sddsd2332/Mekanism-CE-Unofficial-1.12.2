package mekanism.common.frequency;

import mekanism.api.NBTConstants;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.util.ItemDataUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nullable;

public interface IFrequencyItem {

    FrequencyType<?> getFrequencyType();

    @Nullable
    default FrequencyIdentity getFrequency(ItemStack stack) {
        return getFrequencyAware(stack).identity();
    }

    default FrequencyAware<?> getFrequencyAware(ItemStack stack) {
        FrequencyType<?> type = getFrequencyType();
        if (ItemDataUtils.hasData(stack, NBTConstants.COMPONENT_FREQUENCY, NBT.TAG_COMPOUND)) {
            NBTTagCompound frequencyData = ItemDataUtils.getCompound(stack, NBTConstants.COMPONENT_FREQUENCY);
            if (frequencyData.hasKey(type.getName(), NBT.TAG_COMPOUND)) {
                FrequencyIdentity identity = type.getIdentitySerializer().read(frequencyData.getCompoundTag(type.getName()));
                return identity == null ? FrequencyAware.none() : FrequencyAware.identity(identity);
            }
        }
        return FrequencyAware.none();
    }

    default void setFrequency(ItemStack stack, @Nullable FrequencyIdentity frequency) {
        setFrequencyAware(stack, frequency == null ? null : FrequencyAware.identity(frequency));
    }

    default void setFrequencyAware(ItemStack stack, @Nullable FrequencyAware<?> frequencyAware) {
        FrequencyType<?> type = getFrequencyType();
        FrequencyIdentity frequency = frequencyAware == null ? null : frequencyAware.identity();
        if (frequency == null) {
            if (ItemDataUtils.hasData(stack, NBTConstants.COMPONENT_FREQUENCY, NBT.TAG_COMPOUND)) {
                NBTTagCompound frequencyData = ItemDataUtils.getCompound(stack, NBTConstants.COMPONENT_FREQUENCY);
                frequencyData.removeTag(type.getName());
                if (frequencyData.isEmpty()) {
                    ItemDataUtils.removeData(stack, NBTConstants.COMPONENT_FREQUENCY);
                }
            }
            return;
        }
        NBTTagCompound frequencyData = ItemDataUtils.getOrAddCompound(stack, NBTConstants.COMPONENT_FREQUENCY);
        frequencyData.setTag(type.getName(), type.getIdentitySerializer().write(frequency));
    }
}
