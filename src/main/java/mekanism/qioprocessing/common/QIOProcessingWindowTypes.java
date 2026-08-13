package mekanism.qioprocessing.common;

import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

public final class QIOProcessingWindowTypes {

    public static final byte WORKBENCH_INVENTORY_PATTERN = 0;
    public static final byte WORKBENCH_INVENTORY_BATCH = 1;
    public static final byte MANAGEMENT_RECIPE_SCHEDULED = 0;
    public static final byte MANAGEMENT_RECIPE_PASSIVE = 1;

    public static final WindowType AUTOMATION_FREQUENCY = WindowType.register(
          new ResourceLocation(MekanismQIOProcessing.MODID, "automation_frequency"),
          "qio_automation_frequency", true);
    public static final WindowType AUTOMATION_CRAFTING_CONFIG = WindowType.register(
          new ResourceLocation(MekanismQIOProcessing.MODID, "automation_crafting_config"),
          "qio_automation_crafting_config", true);
    public static final WindowType AUTOMATION_PROCESSING_CONFIG = WindowType.register(
          new ResourceLocation(MekanismQIOProcessing.MODID, "automation_processing_config"),
          "qio_automation_processing_config", true);
    public static final WindowType TERMINAL_FREQUENCY = WindowType.register(
          new ResourceLocation(MekanismQIOProcessing.MODID, "terminal_frequency"),
          "qio_terminal_frequency", true);
    public static final WindowType CRAFTING_PROCESSOR_FREQUENCY = WindowType.register(
          new ResourceLocation(MekanismQIOProcessing.MODID, "crafting_processor_frequency"),
          "qio_crafting_processor_frequency", true);
    public static final WindowType CRAFTING_PROCESSOR_RECIPE = WindowType.register(
          new ResourceLocation(MekanismQIOProcessing.MODID, "crafting_processor_recipe"),
          "qio_crafting_processor_recipe", true, (byte) 9);
    public static final WindowType SMART_PROCESSING_ORDER = WindowType.register(
          new ResourceLocation(MekanismQIOProcessing.MODID, "smart_processing_order"),
          "qio_smart_processing_order", true);
    public static final WindowType SMART_PROCESSING_ANALYSIS = WindowType.register(
          new ResourceLocation(MekanismQIOProcessing.MODID, "smart_processing_analysis"),
          "qio_smart_processing_analysis", false);
    public static final WindowType CRAFTING_MONITOR_PLAN = WindowType.register(
          new ResourceLocation(MekanismQIOProcessing.MODID, "crafting_monitor_plan"),
          "qio_crafting_monitor_plan", true);
    public static final WindowType MANAGEMENT_RECIPE_CONFIG = WindowType.register(
          new ResourceLocation(MekanismQIOProcessing.MODID,
                "management_recipe_config"),
          "qio_management_recipe_config", true, (byte) 2);
    public static final WindowType MANAGEMENT_WORKBENCH_CONFIGURATION = WindowType.register(
          new ResourceLocation(MekanismQIOProcessing.MODID,
                "management_workbench_configuration"),
          "qio_management_workbench_configuration", true);
    public static final WindowType MANAGEMENT_WORKBENCH_PATTERN = WindowType.register(
          new ResourceLocation(MekanismQIOProcessing.MODID,
                "management_workbench_pattern"),
          "qio_management_workbench_pattern", true);
    public static final WindowType MANAGEMENT_WORKBENCH_BATCH = WindowType.register(
          new ResourceLocation(MekanismQIOProcessing.MODID,
                "management_workbench_batch"),
          "qio_management_workbench_batch", true);
    public static final WindowType MANAGEMENT_WORKBENCH_CANDIDATES = WindowType.register(
          new ResourceLocation(MekanismQIOProcessing.MODID,
                "management_workbench_candidates"),
          "qio_management_workbench_candidates", true);
    public static final WindowType MANAGEMENT_WORKBENCH_INVENTORY = WindowType.register(
          new ResourceLocation(MekanismQIOProcessing.MODID,
                "management_workbench_inventory"),
          "qio_management_workbench_inventory", false, (byte) 2);

    private QIOProcessingWindowTypes() {
    }

    public static void bootstrap() {
    }

    public static boolean isWorkbenchPatternWindow(@Nullable SelectedWindowData windowData) {
        return windowData != null && (windowData.type == MANAGEMENT_WORKBENCH_PATTERN ||
              (windowData.type == MANAGEMENT_WORKBENCH_INVENTORY &&
                    windowData.extraData == WORKBENCH_INVENTORY_PATTERN));
    }

    public static boolean isWorkbenchBatchWindow(@Nullable SelectedWindowData windowData) {
        return windowData != null && (windowData.type == MANAGEMENT_WORKBENCH_BATCH ||
              (windowData.type == MANAGEMENT_WORKBENCH_INVENTORY &&
                    windowData.extraData == WORKBENCH_INVENTORY_BATCH));
    }

    public static boolean isWorkbenchEditorWindow(@Nullable SelectedWindowData windowData) {
        return isWorkbenchPatternWindow(windowData) || isWorkbenchBatchWindow(windowData);
    }
}
