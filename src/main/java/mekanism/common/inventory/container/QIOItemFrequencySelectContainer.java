package mekanism.common.inventory.container;

import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.inventory.container.item.FrequencyItemContainer;
import mekanism.common.item.ItemPortableQIODashboard;
import mekanism.common.util.SecurityUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

/** Frequency list container for the portable QIO Dashboard. */
public class QIOItemFrequencySelectContainer extends FrequencyItemContainer<QIOFrequency> implements IEmptyContainer {

    public QIOItemFrequencySelectContainer(InventoryPlayer inventory, EnumHand hand, ItemStack stack) {
        super(inventory, hand, stack);
    }

    @Override
    protected FrequencyType<QIOFrequency> getFrequencyType() {
        return FrequencyType.QIO;
    }

    public ItemStack getStack() {
        return getCurrentStack();
    }

    private ItemStack getCurrentStack() {
        if (inv != null && inv.player != null) {
            return inv.player.getHeldItem(hand);
        }
        return stack;
    }

    @Override
    protected QIOFrequency getFrequencyFromStack() {
        ItemStack current = getCurrentStack();
        if (!(current.getItem() instanceof mekanism.common.frequency.IFrequencyItem)) {
            return null;
        }
        mekanism.common.frequency.Frequency.FrequencyIdentity identity =
              ((mekanism.common.frequency.IFrequencyItem) current.getItem()).getFrequency(current);
        return identity == null ? null : FrequencyType.QIO.getFrequency(identity, getPlayerUUID());
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        // Do not require the client hand slot to be synchronized before the
        // frequency screen has rendered its first frame.  Server-side packet
        // validation still checks the actual held dashboard and security.
        if (player.world.isRemote) {
            return true;
        }
        ItemStack held = player.getHeldItem(hand);
        return !held.isEmpty() && held.getItem() instanceof ItemPortableQIODashboard &&
              (player.world.isRemote || SecurityUtils.canAccess(player, held)) && super.canInteractWith(player);
    }
}
