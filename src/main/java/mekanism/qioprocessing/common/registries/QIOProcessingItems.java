package mekanism.qioprocessing.common.registries;

import mekanism.common.item.ItemUpgrade;
import mekanism.qioprocessing.common.MekanismQIOProcessing;
import mekanism.qioprocessing.common.QIOProcessingUpgrades;
import mekanism.qioprocessing.common.item.ItemQIOAutomationUpgrade;
import mekanism.qioprocessing.common.item.ItemPortableQIOProcessingTerminal;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;

import static mekanism.qioprocessing.common.MekanismQIOProcessing.TAB;

public final class QIOProcessingItems {


    public static final Item QIOStackingUpgrade = new ItemUpgrade(
            QIOProcessingUpgrades.QIO_STACKING).setCreativeTab(TAB);
    public static final Item QIOAutoCraftingUpgrade = new ItemQIOAutomationUpgrade(QIOProcessingUpgrades.QIO_AUTO_CRAFTING,
            mekanism.api.processing.QIOAutomationMode.SCHEDULED).setCreativeTab(TAB);
    public static final Item QIOAutoProcessingUpgrade = new ItemQIOAutomationUpgrade(QIOProcessingUpgrades.QIO_AUTO_PROCESSING,
            mekanism.api.processing.QIOAutomationMode.PASSIVE).setCreativeTab(TAB);
    public static final Item QIOAutoOutputUpgrade = new ItemQIOAutomationUpgrade(QIOProcessingUpgrades.QIO_AUTO_OUTPUT,
            mekanism.api.processing.QIOAutomationMode.OUTPUT_ONLY).setCreativeTab(TAB);
    public static final Item PortableQIOManagementTerminal = new ItemPortableQIOProcessingTerminal(
            QIOProcessingTerminalType.MANAGEMENT).setCreativeTab(TAB);
    public static final Item PortableQIOSmartProcessingTerminal = new ItemPortableQIOProcessingTerminal(
            QIOProcessingTerminalType.SMART_PROCESSING).setCreativeTab(TAB);
    public static final Item PortableQIOMaintenanceTerminal = new ItemPortableQIOProcessingTerminal(
            QIOProcessingTerminalType.MAINTENANCE).setCreativeTab(TAB);
    public static final Item PortableQIOCraftingMonitor = new ItemPortableQIOProcessingTerminal(
            QIOProcessingTerminalType.CRAFTING_MONITOR).setCreativeTab(TAB);

    private QIOProcessingItems() {
    }

    public static void registerItems(IForgeRegistry<Item> registry) {
        registry.register(init(QIOStackingUpgrade, "qio_stacking_upgrade"));
        registry.register(init(QIOAutoCraftingUpgrade, "qio_auto_crafting_upgrade"));
        registry.register(init(QIOAutoProcessingUpgrade, "qio_auto_processing_upgrade"));
        registry.register(init(QIOAutoOutputUpgrade, "qio_auto_output_upgrade"));
        registry.register(init(PortableQIOManagementTerminal,
                "portable_qio_management_terminal"));
        registry.register(init(PortableQIOSmartProcessingTerminal,
                "portable_qio_smart_processing_terminal"));
        registry.register(init(PortableQIOMaintenanceTerminal,
                "portable_qio_maintenance_terminal"));
        registry.register(init(PortableQIOCraftingMonitor,
                "portable_qio_crafting_monitor"));
    }

    private static Item init(Item item, String name) {
        return item.setTranslationKey(name).setRegistryName(new ResourceLocation(MekanismQIOProcessing.MODID, name));
    }
}
