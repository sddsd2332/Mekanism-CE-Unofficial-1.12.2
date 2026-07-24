package mekanism.common.inventory.container;

import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.inventory.container.item.FrequencyItemContainer;
import mekanism.common.item.ItemPortableQIODashboard;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

/** Frequency list container for the portable QIO Dashboard. */
public class QIOItemFrequencySelectContainer extends FrequencyItemContainer<QIOFrequency> implements IEmptyContainer {

    public QIOItemFrequencySelectContainer(InventoryPlayer inventory, EnumHand hand, ItemStack stack) {
        super(inventory, hand, stack);
    }

    public QIOItemFrequencySelectContainer(InventoryPlayer inventory, EnumHand hand, int itemSlot, ItemStack stack) {
        super(inventory, hand, itemSlot, stack);
    }

    @Override
    protected FrequencyType<QIOFrequency> getFrequencyType() {
        return FrequencyType.QIO;
    }

    @Override
    protected boolean isValidStack(ItemStack stack) {
        return super.isValidStack(stack) && stack.getItem() instanceof ItemPortableQIODashboard;
    }

    @Override
    protected QIOFrequency getFrequencyFromStack() {
        ItemStack current = getStack();
        if (!(current.getItem() instanceof mekanism.common.frequency.IFrequencyItem)) {
            return null;
        }
        mekanism.common.frequency.Frequency.FrequencyIdentity identity =
              ((mekanism.common.frequency.IFrequencyItem) current.getItem()).getFrequency(current);
        return identity == null ? null : FrequencyType.QIO.getFrequency(identity, getPlayerUUID());
    }
}
