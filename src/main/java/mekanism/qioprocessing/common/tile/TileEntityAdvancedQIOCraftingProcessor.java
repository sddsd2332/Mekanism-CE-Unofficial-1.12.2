package mekanism.qioprocessing.common.tile;

import mekanism.qioprocessing.api.processor.QIOCraftingProcessorRegistry;
import mekanism.qioprocessing.common.registries.QIOProcessingProcessorHosts;

public class TileEntityAdvancedQIOCraftingProcessor extends TieredQIOCraftingProcessor {

    public TileEntityAdvancedQIOCraftingProcessor() {
        super("AdvancedQIOCraftingProcessor", QIOProcessingProcessorHosts.ADVANCED_HOST_ID,
              QIOCraftingProcessorRegistry.ADVANCED_ID);
    }
}
