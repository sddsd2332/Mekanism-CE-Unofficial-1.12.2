package mekanism.qioprocessing.common.tile;

import mekanism.qioprocessing.api.processor.QIOCraftingProcessorRegistry;
import mekanism.qioprocessing.common.registries.QIOProcessingProcessorHosts;
import net.minecraft.util.ResourceLocation;

/** Placeable ordinary one-lane processor and the default addon-reusable implementation. */
/**
 * QIO 处理模块中的 TileEntityQIOCraftingProcessor 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public class TileEntityQIOCraftingProcessor extends QIOCraftingProcessor {

    public TileEntityQIOCraftingProcessor() {
        this("QIOCraftingProcessor", QIOProcessingProcessorHosts.ORDINARY_HOST_ID,
              QIOCraftingProcessorRegistry.ORDINARY_ID);
    }

    protected TileEntityQIOCraftingProcessor(String name, ResourceLocation hostId,
          ResourceLocation definitionId) {
        super(name, hostId, definitionId);
    }
}
