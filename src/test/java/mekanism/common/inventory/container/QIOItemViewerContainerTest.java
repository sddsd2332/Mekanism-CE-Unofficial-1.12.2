package mekanism.common.inventory.container;

import mekanism.common.content.qio.IQIOCraftingWindowHolder;
import mekanism.common.content.qio.QIOCraftingWindow;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.inventory.container.slot.VirtualCraftingOutputSlot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOItemViewerContainerTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void successfulShiftCraftReturnsEmptyAndForcesAuthoritativeSync() {
        TestContainer container = new TestContainer();
        QIOCraftingWindow window = new QIOCraftingWindow(new TestHolder(), (byte) 0);
        container.addOutput(new SuccessfulOutputSlot(window));

        ItemStack result = container.quickMoveStack(null, 0);

        assertTrue(result.isEmpty());
        assertTrue(container.synced);
    }

    @Test
    void clientCraftingSelectionUsesTheLocallyFocusedWindow() {
        TestContainer container = new TestContainer();
        container.setSelectedWindow(new SelectedWindowData(SelectedWindowData.WindowType.CRAFTING, (byte) 2));

        assertEquals(2, container.getSelectedCraftingGrid());
        assertEquals(-1, container.getSelectedCraftingGrid(UUID.randomUUID()));
    }

    private static final class TestContainer extends QIOItemViewerContainer {

        private boolean synced;

        private TestContainer() {
            super(null, null);
        }

        private void addOutput(VirtualCraftingOutputSlot output) {
            addSlot(output);
        }

        @Override
        protected void syncServerOnlyChanges(@Nonnull EntityPlayer player, boolean syncViewer) {
            synced = true;
        }
    }

    private static final class SuccessfulOutputSlot extends VirtualCraftingOutputSlot {

        private SuccessfulOutputSlot(QIOCraftingWindow window) {
            super(window, window.getResultInventory(), 0, 0, 0);
        }

        @Override
        public boolean getHasStack() {
            return true;
        }

        @Nonnull
        @Override
        public ItemStack performShiftCraft(@Nonnull EntityPlayer player, @Nonnull IQIOItemViewerContainer container) {
            return new ItemStack(Blocks.STONE);
        }
    }

    private static final class TestHolder implements IQIOCraftingWindowHolder {

        @Override
        public World getHolderWorld() {
            return null;
        }

        @Override
        public QIOCraftingWindow[] getCraftingWindows() {
            return new QIOCraftingWindow[0];
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
