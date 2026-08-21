package mekanism.qioprocessing.common.registries;

import mekanism.qioprocessing.api.processor.QIOCraftingProcessorHostRegistration;
import mekanism.qioprocessing.api.processor.QIOCraftingProcessorHostRegistry;
import mekanism.qioprocessing.api.processor.QIOCraftingProcessorRegistry;
import mekanism.qioprocessing.common.MekanismQIOProcessing;
import mekanism.qioprocessing.common.tile.TileEntityAdvancedQIOCraftingProcessor;
import mekanism.qioprocessing.common.tile.TileEntityBasicQIOCraftingProcessor;
import mekanism.qioprocessing.common.tile.TileEntityEliteQIOCraftingProcessor;
import mekanism.qioprocessing.common.tile.TileEntityQIOCraftingProcessor;
import mekanism.qioprocessing.common.tile.TileEntityUltimateQIOCraftingProcessor;
import net.minecraft.util.ResourceLocation;

/**
 * QIO 处理模块中的 QIOProcessingProcessorHosts 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOProcessingProcessorHosts {

    public static final ResourceLocation ORDINARY_HOST_ID = id("qio_crafting_processor");
    public static final ResourceLocation BASIC_HOST_ID = id("basic_qio_crafting_processor");
    public static final ResourceLocation ADVANCED_HOST_ID = id("advanced_qio_crafting_processor");
    public static final ResourceLocation ELITE_HOST_ID = id("elite_qio_crafting_processor");
    public static final ResourceLocation ULTIMATE_HOST_ID = id("ultimate_qio_crafting_processor");

    private static boolean registered;

    private QIOProcessingProcessorHosts() {
    }

    public static synchronized void registerBuiltins() {
        if (registered) {
            return;
        }
        register(ORDINARY_HOST_ID, TileEntityQIOCraftingProcessor.class,
              QIOCraftingProcessorRegistry.ORDINARY_ID);
        register(BASIC_HOST_ID, TileEntityBasicQIOCraftingProcessor.class,
              QIOCraftingProcessorRegistry.BASIC_ID);
        register(ADVANCED_HOST_ID, TileEntityAdvancedQIOCraftingProcessor.class,
              QIOCraftingProcessorRegistry.ADVANCED_ID);
        register(ELITE_HOST_ID, TileEntityEliteQIOCraftingProcessor.class,
              QIOCraftingProcessorRegistry.ELITE_ID);
        register(ULTIMATE_HOST_ID, TileEntityUltimateQIOCraftingProcessor.class,
              QIOCraftingProcessorRegistry.ULTIMATE_ID);
        registered = true;
    }

    private static <T extends net.minecraft.tileentity.TileEntity> void register(ResourceLocation hostId,
          Class<T> tileClass, ResourceLocation definitionId) {
        QIOCraftingProcessorHostRegistry.register(QIOCraftingProcessorHostRegistration.fixed(hostId,
              MekanismQIOProcessing.MODID, tileClass, definitionId));
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(MekanismQIOProcessing.MODID, path);
    }
}
