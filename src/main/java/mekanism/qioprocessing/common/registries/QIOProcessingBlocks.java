package mekanism.qioprocessing.common.registries;

import mekanism.qioprocessing.common.MekanismQIOProcessing;
import mekanism.qioprocessing.common.block.BlockQIOCraftingProcessor;
import mekanism.qioprocessing.common.block.BlockQIOProcessingTerminal;
import mekanism.qioprocessing.common.item.ItemBlockQIOCraftingProcessor;
import mekanism.qioprocessing.common.item.ItemBlockQIOProcessingTerminal;
import mekanism.qioprocessing.common.tile.TileEntityAdvancedQIOCraftingProcessor;
import mekanism.qioprocessing.common.tile.TileEntityBasicQIOCraftingProcessor;
import mekanism.qioprocessing.common.tile.TileEntityEliteQIOCraftingProcessor;
import mekanism.qioprocessing.common.tile.TileEntityQIOCraftingProcessor;
import mekanism.qioprocessing.common.tile.TileEntityUltimateQIOCraftingProcessor;
import mekanism.qioprocessing.common.tile.TileEntityQIOManagementTerminal;
import mekanism.qioprocessing.common.tile.TileEntityQIOSmartProcessingTerminal;
import mekanism.qioprocessing.common.tile.TileEntityQIOMaintenanceTerminal;
import mekanism.qioprocessing.common.tile.TileEntityQIOCraftingMonitor;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry.ObjectHolder;
import net.minecraftforge.registries.IForgeRegistry;

@ObjectHolder(MekanismQIOProcessing.MODID)
/**
 * QIO 处理模块中的 QIOProcessingBlocks 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOProcessingBlocks {

    public static final Block QIOCraftingProcessor = new BlockQIOCraftingProcessor(
          TileEntityQIOCraftingProcessor::new);
    public static final Block BasicQIOCraftingProcessor = new BlockQIOCraftingProcessor(
          TileEntityBasicQIOCraftingProcessor::new);
    public static final Block AdvancedQIOCraftingProcessor = new BlockQIOCraftingProcessor(
          TileEntityAdvancedQIOCraftingProcessor::new);
    public static final Block EliteQIOCraftingProcessor = new BlockQIOCraftingProcessor(
          TileEntityEliteQIOCraftingProcessor::new);
    public static final Block UltimateQIOCraftingProcessor = new BlockQIOCraftingProcessor(
          TileEntityUltimateQIOCraftingProcessor::new);
    public static final Block QIOManagementTerminal = new BlockQIOProcessingTerminal(
          TileEntityQIOManagementTerminal::new);
    public static final Block QIOSmartProcessingTerminal = new BlockQIOProcessingTerminal(
          TileEntityQIOSmartProcessingTerminal::new);
    public static final Block QIOMaintenanceTerminal = new BlockQIOProcessingTerminal(
          TileEntityQIOMaintenanceTerminal::new);
    public static final Block QIOCraftingMonitor = new BlockQIOProcessingTerminal(
          TileEntityQIOCraftingMonitor::new);

    private QIOProcessingBlocks() {
    }

    public static void registerBlocks(IForgeRegistry<Block> registry) {
        registry.register(init(QIOCraftingProcessor, "qio_crafting_processor"));
        registry.register(init(BasicQIOCraftingProcessor, "basic_qio_crafting_processor"));
        registry.register(init(AdvancedQIOCraftingProcessor, "advanced_qio_crafting_processor"));
        registry.register(init(EliteQIOCraftingProcessor, "elite_qio_crafting_processor"));
        registry.register(init(UltimateQIOCraftingProcessor, "ultimate_qio_crafting_processor"));
        registry.register(init(QIOManagementTerminal, "qio_management_terminal"));
        registry.register(init(QIOSmartProcessingTerminal, "qio_smart_processing_terminal"));
        registry.register(init(QIOMaintenanceTerminal, "qio_maintenance_terminal"));
        registry.register(init(QIOCraftingMonitor, "qio_crafting_monitor"));
    }

    public static void registerItemBlocks(IForgeRegistry<Item> registry) {
        registerItemBlock(registry, QIOCraftingProcessor, "qio_crafting_processor");
        registerItemBlock(registry, BasicQIOCraftingProcessor, "basic_qio_crafting_processor");
        registerItemBlock(registry, AdvancedQIOCraftingProcessor, "advanced_qio_crafting_processor");
        registerItemBlock(registry, EliteQIOCraftingProcessor, "elite_qio_crafting_processor");
        registerItemBlock(registry, UltimateQIOCraftingProcessor, "ultimate_qio_crafting_processor");
        registerTerminalItemBlock(registry, QIOManagementTerminal, "qio_management_terminal");
        registerTerminalItemBlock(registry, QIOSmartProcessingTerminal, "qio_smart_processing_terminal");
        registerTerminalItemBlock(registry, QIOMaintenanceTerminal, "qio_maintenance_terminal");
        registerTerminalItemBlock(registry, QIOCraftingMonitor, "qio_crafting_monitor");
    }

    private static void registerItemBlock(IForgeRegistry<Item> registry, Block block, String name) {
        registry.register(new ItemBlockQIOCraftingProcessor(block).setTranslationKey(name)
              .setRegistryName(id(name)));
    }

    private static void registerTerminalItemBlock(IForgeRegistry<Item> registry,
          Block block, String name) {
        registry.register(new ItemBlockQIOProcessingTerminal(block)
              .setTranslationKey(name).setRegistryName(id(name)));
    }

    private static Block init(Block block, String name) {
        return block.setTranslationKey(name).setRegistryName(id(name));
    }

    private static ResourceLocation id(String name) {
        return new ResourceLocation(MekanismQIOProcessing.MODID, name);
    }
}
