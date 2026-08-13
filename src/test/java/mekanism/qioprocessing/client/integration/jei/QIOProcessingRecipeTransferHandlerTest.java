package mekanism.qioprocessing.client.integration.jei;

import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.qioprocessing.common.QIOProcessingWindowTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOProcessingRecipeTransferHandlerTest {

    @Test
    void selectedWindowIdentityStrictlySeparatesCraftingOrderAndNoTarget() {
        for (byte index = 0; index < 3; index++) {
            assertEquals(QIOProcessingRecipeTransferHandler.TargetMode.CRAFTING,
                  QIOProcessingRecipeTransferHandler.targetMode(new SelectedWindowData(
                        SelectedWindowData.WindowType.CRAFTING, index)));
        }
        assertEquals(QIOProcessingRecipeTransferHandler.TargetMode.ORDER,
              QIOProcessingRecipeTransferHandler.targetMode(new SelectedWindowData(
                    QIOProcessingWindowTypes.SMART_PROCESSING_ORDER)));
        assertEquals(QIOProcessingRecipeTransferHandler.TargetMode.NONE,
              QIOProcessingRecipeTransferHandler.targetMode(new SelectedWindowData(
                    QIOProcessingWindowTypes.TERMINAL_FREQUENCY)));
        assertEquals(QIOProcessingRecipeTransferHandler.TargetMode.NONE,
              QIOProcessingRecipeTransferHandler.targetMode(null));
    }

    @Test
    void workbenchEncodingTransferOnlyTargetsTheManagementPatternWindow() {
        assertEquals(QIOWorkbenchRecipeTransferHandler.TargetMode.PATTERN,
              QIOWorkbenchRecipeTransferHandler.targetMode(
              new SelectedWindowData(
                    QIOProcessingWindowTypes.MANAGEMENT_WORKBENCH_PATTERN)));
        assertEquals(QIOWorkbenchRecipeTransferHandler.TargetMode.BATCH,
              QIOWorkbenchRecipeTransferHandler.targetMode(new SelectedWindowData(
                    QIOProcessingWindowTypes.MANAGEMENT_WORKBENCH_BATCH)));
        assertEquals(QIOWorkbenchRecipeTransferHandler.TargetMode.PATTERN,
              QIOWorkbenchRecipeTransferHandler.targetMode(new SelectedWindowData(
                    QIOProcessingWindowTypes.MANAGEMENT_WORKBENCH_INVENTORY,
                    QIOProcessingWindowTypes.WORKBENCH_INVENTORY_PATTERN)));
        assertEquals(QIOWorkbenchRecipeTransferHandler.TargetMode.BATCH,
              QIOWorkbenchRecipeTransferHandler.targetMode(new SelectedWindowData(
                    QIOProcessingWindowTypes.MANAGEMENT_WORKBENCH_INVENTORY,
                    QIOProcessingWindowTypes.WORKBENCH_INVENTORY_BATCH)));
        assertFalse(QIOWorkbenchRecipeTransferHandler.isWorkbenchTarget(
              new SelectedWindowData(
                    QIOProcessingWindowTypes.MANAGEMENT_WORKBENCH_CONFIGURATION)));
        assertFalse(QIOWorkbenchRecipeTransferHandler.isWorkbenchTarget(
              new SelectedWindowData(QIOProcessingWindowTypes.SMART_PROCESSING_ORDER)));
        assertFalse(QIOWorkbenchRecipeTransferHandler.isWorkbenchTarget(null));
    }

    @Test
    void workbenchEditorsCanBePinnedButTheirSharedInventoryCannot() {
        assertTrue(QIOProcessingWindowTypes.MANAGEMENT_WORKBENCH_PATTERN.canPin());
        assertTrue(QIOProcessingWindowTypes.MANAGEMENT_WORKBENCH_BATCH.canPin());
        assertFalse(QIOProcessingWindowTypes.MANAGEMENT_WORKBENCH_INVENTORY.canPin());
    }

    @Test
    void craftingProcessorLanesHaveDistinctPinnableWindows() {
        assertTrue(QIOProcessingWindowTypes.CRAFTING_PROCESSOR_RECIPE.canPin());
        SelectedWindowData first = new SelectedWindowData(
              QIOProcessingWindowTypes.CRAFTING_PROCESSOR_RECIPE, (byte) 0);
        SelectedWindowData last = new SelectedWindowData(
              QIOProcessingWindowTypes.CRAFTING_PROCESSOR_RECIPE, (byte) 8);

        assertNotEquals(first, last);
        assertEquals(0, first.extraData);
        assertEquals(8, last.extraData);
    }
}
