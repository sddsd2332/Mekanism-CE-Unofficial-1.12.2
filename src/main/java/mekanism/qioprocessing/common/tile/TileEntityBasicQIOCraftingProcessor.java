package mekanism.qioprocessing.common.tile;

import mekanism.qioprocessing.api.processor.QIOCraftingProcessorRegistry;
import mekanism.qioprocessing.common.registries.QIOProcessingProcessorHosts;

public class TileEntityBasicQIOCraftingProcessor extends TieredQIOCraftingProcessor {

    public TileEntityBasicQIOCraftingProcessor() {
        super("BasicQIOCraftingProcessor", QIOProcessingProcessorHosts.BASIC_HOST_ID,
              QIOCraftingProcessorRegistry.BASIC_ID);
    }
}
