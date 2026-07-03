package mekanism.common.tile;

import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.SecurityUtils;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nonnull;
import java.util.function.BiPredicate;

public class TileEntityPersonalChest extends TileEntityContainerBlock implements ISecurityTile {

    public float lidAngle;

    public float prevLidAngle;

    public TileComponentSecurity securityComponent;

    public TileEntityPersonalChest() {
        super("PersonalChest");
        initializeInventorySlots();
        securityComponent = new TileComponentSecurity(this);
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        BiPredicate<ItemStack, AutomationType> canInteract = (stack, automationType) ->
              automationType == AutomationType.MANUAL || SecurityUtils.getSecurity(this, Side.SERVER) == SecurityMode.PUBLIC;
        for (int slotY = 0; slotY < 6; slotY++) {
            for (int slotX = 0; slotX < 9; slotX++) {
                builder.addSlot(BasicInventorySlot.at(canInteract, canInteract, listener, 8 + slotX * 18, 18 + slotY * 18));
            }
        }
        return builder.build();
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        prevLidAngle = lidAngle;
        float increment = 0.1F;
        if ((!playersUsing.isEmpty()) && (lidAngle == 0.0F)) {
            world.playSound(null, getPos().getX() + 0.5F, getPos().getY() + 0.5D, getPos().getZ() + 0.5F, SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.5F, (world.rand.nextFloat() * 0.1F) + 0.9F);
        }

        if ((playersUsing.isEmpty() && lidAngle > 0.0F) || (!playersUsing.isEmpty() && lidAngle < 1.0F)) {
            float angle = lidAngle;
            if (!playersUsing.isEmpty()) {
                lidAngle += increment;
            } else {
                lidAngle -= increment;
            }
            if (lidAngle > 1.0F) {
                lidAngle = 1.0F;
            }
            float split = 0.5F;
            if (lidAngle < split && angle >= split) {
                world.playSound(null, getPos().getX() + 0.5D, getPos().getY() + 0.5D, getPos().getZ() + 0.5D, SoundEvents.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 0.5F, (world.rand.nextFloat() * 0.1F) + 0.9F);
            }
            if (lidAngle < 0.0F) {
                lidAngle = 0.0F;
            }
        }
    }

    @Override
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return facing != EnumFacing.DOWN && facing != EnumFacing.UP;
    }

    @Override
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }
}
