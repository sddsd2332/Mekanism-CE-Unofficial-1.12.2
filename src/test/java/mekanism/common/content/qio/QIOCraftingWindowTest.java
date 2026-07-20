package mekanism.common.content.qio;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOCraftingWindowTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void windowPersistsNineInputsWithoutPersistingDerivedOutput() {
        TestHolder firstHolder = new TestHolder();
        QIOCraftingWindow first = firstHolder.windows[0];
        ItemStack stone = new ItemStack(Blocks.STONE, 12);
        first.getCraftingInventory().setInventorySlotContents(4, stone);
        NBTTagCompound tag = new NBTTagCompound();
        first.write(tag);

        TestHolder secondHolder = new TestHolder();
        QIOCraftingWindow second = secondHolder.windows[0];
        second.read(tag);
        assertEquals(12, second.getCraftingInventory().getStackInSlot(4).getCount());
        assertTrue(second.getResultInventory().getStackInSlot(0).isEmpty());
    }

    @Test
    void invalidatingAnUnresolvedWindowClearsItsDerivedOutput() {
        TestHolder holder = new TestHolder();
        QIOCraftingWindow window = holder.windows[0];
        window.getResultInventory().setInventorySlotContents(0, new ItemStack(Blocks.STONE));

        window.invalidateRecipe();

        assertTrue(window.getResultInventory().getStackInSlot(0).isEmpty());
    }

    private static final class TestHolder implements IQIOCraftingWindowHolder {
        private final QIOCraftingWindow[] windows = new QIOCraftingWindow[MAX_CRAFTING_WINDOWS];

        private TestHolder() {
            for (byte i = 0; i < windows.length; i++) {
                windows[i] = new QIOCraftingWindow(this, i);
            }
        }

        @Override
        public World getHolderWorld() {
            return null;
        }

        @Override
        public QIOCraftingWindow[] getCraftingWindows() {
            return windows;
        }

        @Override
        public QIOFrequency getFrequency() {
            return null;
        }

        @Override
        public void onCraftingWindowContentsChanged() {
        }
    }
}
