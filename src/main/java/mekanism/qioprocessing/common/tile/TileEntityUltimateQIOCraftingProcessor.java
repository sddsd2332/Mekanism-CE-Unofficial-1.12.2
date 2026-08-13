package mekanism.qioprocessing.common.tile;

import mekanism.qioprocessing.api.processor.QIOCraftingProcessorRegistry;
import mekanism.qioprocessing.common.registries.QIOProcessingProcessorHosts;

public class TileEntityUltimateQIOCraftingProcessor extends TieredQIOCraftingProcessor {

    public TileEntityUltimateQIOCraftingProcessor() {
        super("UltimateQIOCraftingProcessor", QIOProcessingProcessorHosts.ULTIMATE_HOST_ID,
              QIOCraftingProcessorRegistry.ULTIMATE_ID);
    }
}
