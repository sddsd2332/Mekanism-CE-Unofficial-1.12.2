package mekanism.common.inventory.container.item;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemStackSlotAccessTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void mainHandAccessStaysBoundToOpeningHotbarSlot() {
        InventoryPlayer inventory = new InventoryPlayer(null);
        inventory.currentItem = 2;
        ItemStack openingStack = new ItemStack(Blocks.STONE);
        inventory.mainInventory.set(2, openingStack);
        inventory.mainInventory.set(5, new ItemStack(Blocks.DIRT));

        ItemStackSlotAccess access = new ItemStackSlotAccess(inventory, EnumHand.MAIN_HAND, 2, openingStack);
        inventory.currentItem = 5;

        assertEquals(2, access.getSlot());
        assertSame(openingStack, access.getStack());
        assertTrue(access.isOriginalStackPresent(false));
    }

    @Test
    void serverRejectsReplacementButClientAllowsSynchronizedCopy() {
        InventoryPlayer inventory = new InventoryPlayer(null);
        ItemStack openingStack = new ItemStack(Blocks.STONE);
        inventory.mainInventory.set(0, openingStack);
        ItemStackSlotAccess access = new ItemStackSlotAccess(inventory, EnumHand.MAIN_HAND, 0, openingStack);

        ItemStack synchronizedCopy = openingStack.copy();
        inventory.mainInventory.set(0, synchronizedCopy);

        assertFalse(access.isOriginalStackPresent(false));
        assertTrue(access.isOriginalStackPresent(true));
        assertSame(synchronizedCopy, access.getStack());

        inventory.mainInventory.set(0, ItemStack.EMPTY);
        assertFalse(access.isOriginalStackPresent(false));
        assertFalse(access.isOriginalStackPresent(true));
    }

    @Test
    void writesStayOnFixedSlotAfterSelectionChanges() {
        InventoryPlayer inventory = new InventoryPlayer(null);
        inventory.currentItem = 1;
        ItemStack openingStack = new ItemStack(Blocks.STONE);
        ItemStack selectedLater = new ItemStack(Blocks.DIRT);
        inventory.mainInventory.set(1, openingStack);
        inventory.mainInventory.set(4, selectedLater);
        ItemStackSlotAccess access = new ItemStackSlotAccess(inventory, EnumHand.MAIN_HAND, 1, openingStack);

        inventory.currentItem = 4;
        ItemStack replacement = new ItemStack(Blocks.COBBLESTONE);
        access.setStack(replacement);

        assertSame(replacement, inventory.mainInventory.get(1));
        assertSame(selectedLater, inventory.mainInventory.get(4));
    }

    @Test
    void offhandUsesVanillaFixedSlot() {
        InventoryPlayer inventory = new InventoryPlayer(null);
        ItemStack openingStack = new ItemStack(Blocks.STONE);
        inventory.offHandInventory.set(0, openingStack);

        ItemStackSlotAccess access = new ItemStackSlotAccess(inventory, EnumHand.OFF_HAND,
              ItemStackSlotAccess.OFFHAND_SLOT, openingStack);

        assertEquals(ItemStackSlotAccess.OFFHAND_SLOT, access.getSlot());
        assertSame(openingStack, access.getStack());
        assertTrue(ItemStackSlotAccess.isValidSlot(EnumHand.OFF_HAND, ItemStackSlotAccess.OFFHAND_SLOT));
        assertFalse(ItemStackSlotAccess.isValidSlot(EnumHand.OFF_HAND, 0));
        assertThrows(IllegalArgumentException.class,
              () -> new ItemStackSlotAccess(inventory, EnumHand.OFF_HAND, 0, openingStack));
    }
}
