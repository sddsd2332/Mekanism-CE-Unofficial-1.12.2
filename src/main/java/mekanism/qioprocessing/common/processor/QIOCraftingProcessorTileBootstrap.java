package mekanism.qioprocessing.common.processor;

import mekanism.qioprocessing.api.processor.QIOCraftingProcessorHostRegistration;
import mekanism.qioprocessing.api.processor.QIOCraftingProcessorHostRegistry;
import mekanism.qioprocessing.api.processor.QIOCraftingProcessorRegistry;
import net.minecraft.util.ResourceLocation;

/**
 * Installs the rewritten Processor host definitions before server-side resolution.
 */
public final class QIOCraftingProcessorTileBootstrap {
    public static final ResourceLocation ORDINARY_HOST = id("qio_crafting_processor");
    public static final ResourceLocation BASIC_HOST = id("basic_qio_crafting_processor");
    public static final ResourceLocation ADVANCED_HOST = id("advanced_qio_crafting_processor");
    public static final ResourceLocation ELITE_HOST = id("elite_qio_crafting_processor");
    public static final ResourceLocation ULTIMATE_HOST = id("ultimate_qio_crafting_processor");
    private static boolean registered;

    private QIOCraftingProcessorTileBootstrap() {
    }

    public static synchronized void register() {
        if (registered) return;
        QIOCraftingProcessorRegistry.bootstrapBuiltins(9);
        QIOCraftingProcessorHostRegistry.register(QIOCraftingProcessorHostRegistration.fixed(
                ORDINARY_HOST, "mekanismqioprocessing", QIOCraftingProcessorTile.Ordinary.class,
                QIOCraftingProcessorRegistry.ORDINARY_ID));
        QIOCraftingProcessorHostRegistry.register(QIOCraftingProcessorHostRegistration.fixed(
                BASIC_HOST, "mekanismqioprocessing", QIOCraftingProcessorTile.Basic.class,
                QIOCraftingProcessorRegistry.BASIC_ID));
        QIOCraftingProcessorHostRegistry.register(QIOCraftingProcessorHostRegistration.fixed(
                ADVANCED_HOST, "mekanismqioprocessing", QIOCraftingProcessorTile.Advanced.class,
                QIOCraftingProcessorRegistry.ADVANCED_ID));
        QIOCraftingProcessorHostRegistry.register(QIOCraftingProcessorHostRegistration.fixed(
                ELITE_HOST, "mekanismqioprocessing", QIOCraftingProcessorTile.Elite.class,
                QIOCraftingProcessorRegistry.ELITE_ID));
        QIOCraftingProcessorHostRegistry.register(QIOCraftingProcessorHostRegistration.fixed(
                ULTIMATE_HOST, "mekanismqioprocessing", QIOCraftingProcessorTile.Ultimate.class,
                QIOCraftingProcessorRegistry.ULTIMATE_ID));
        QIOCraftingProcessorRegistry.freeze();
        QIOCraftingProcessorHostRegistry.freeze();
        registered = true;
    }

    public static synchronized void resetForTests() {
        registered = false;
        QIOCraftingProcessorHostRegistry.resetForTests();
        QIOCraftingProcessorRegistry.resetForTests();
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("mekanismqioprocessing", path);
    }
}
