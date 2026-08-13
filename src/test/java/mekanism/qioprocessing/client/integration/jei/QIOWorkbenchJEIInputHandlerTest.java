package mekanism.qioprocessing.client.integration.jei;

import mekanism.common.TestBootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOWorkbenchJEIInputHandlerTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void shiftLeftAndRightPressesAreBothBatchImportGestures() {
        assertTrue(QIOWorkbenchJEIInputHandler.isImportGesture(0, true, true));
        assertTrue(QIOWorkbenchJEIInputHandler.isImportGesture(1, true, true));
        assertFalse(QIOWorkbenchJEIInputHandler.isImportGesture(2, true, true));
        assertFalse(QIOWorkbenchJEIInputHandler.isImportGesture(0, false, true));
        assertFalse(QIOWorkbenchJEIInputHandler.isImportGesture(0, true, false));
    }

    @Test
    void importedOverlayTargetIsAnIndependentSingleItem() {
        ItemStack source = new ItemStack(Items.REDSTONE, 32);

        ItemStack imported = QIOWorkbenchJEIInputHandler.copyTarget(source);

        assertEquals(1, imported.getCount());
        assertEquals(32, source.getCount());
        assertNotSame(source, imported);
    }
}
