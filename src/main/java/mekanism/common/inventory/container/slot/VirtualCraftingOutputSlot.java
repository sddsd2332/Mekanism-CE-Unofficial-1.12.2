package mekanism.common.inventory.container.slot;

import mekanism.common.content.qio.QIOCraftingWindow;
import mekanism.common.inventory.container.IQIOItemViewerContainer;
import mekanism.common.inventory.container.sync.ISyncableData;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/** Output slot for a QIO crafting window. It is consumed through onCrafted. */
public class VirtualCraftingOutputSlot extends VirtualCraftingSlot implements IHasExtraData {

    private final QIOCraftingWindow craftingWindow;
    /** Client mirror of the server's per-player recipe visibility state. */
    private boolean canCraft;

    public VirtualCraftingOutputSlot(@Nonnull QIOCraftingWindow craftingWindow, @Nonnull IInventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
        this.craftingWindow = craftingWindow;
    }

    @Override
    public boolean isItemValid(@Nonnull ItemStack stack) {
        return false;
    }

    @Nonnull
    @Override
    public ItemStack decrStackSize(int amount) {
        if (!canCraft || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = getStack();
        return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    @Nonnull
    @Override
    public ItemStack getStack() {
        return canCraft ? super.getStack() : ItemStack.EMPTY;
    }

    @Override
    public boolean getHasStack() {
        return canCraft && super.getHasStack();
    }

    @Nonnull
    @Override
    public ItemStack getStackToRender() {
        return canCraft ? super.getStackToRender() : ItemStack.EMPTY;
    }

    @Override
    public boolean canTakeStack(@Nonnull EntityPlayer player) {
        if (player.world.isRemote || !(player instanceof EntityPlayerMP)) {
            return canCraft && super.canTakeStack(player);
        }
        return craftingWindow.canViewRecipe((EntityPlayerMP) player) && super.canTakeStack(player);
    }

    @Nonnull
    @Override
    public ItemStack onTake(@Nonnull EntityPlayer player, @Nonnull ItemStack stack) {
        craftingWindow.onCrafted(player, stack);
        return stack;
    }

    @Nonnull
    public ItemStack performShiftCraft(@Nonnull EntityPlayer player, @Nonnull IQIOItemViewerContainer container) {
        return craftingWindow.performShiftCraft(player, container);
    }

    @Override
    public void addTrackers(EntityPlayer player, Consumer<ISyncableData> tracker) {
        if (player.world.isRemote || !(player instanceof EntityPlayerMP)) {
            tracker.accept(SyncableBoolean.create(() -> canCraft, value -> canCraft = value));
        } else {
            EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
            tracker.accept(SyncableBoolean.create(
                  () -> canCraft = craftingWindow.canViewRecipe(serverPlayer), value -> canCraft = value));
        }
    }
}
