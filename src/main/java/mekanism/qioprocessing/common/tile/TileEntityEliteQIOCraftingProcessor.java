package mekanism.qioprocessing.common.tile;

import mekanism.qioprocessing.api.processor.QIOCraftingProcessorRegistry;
import mekanism.qioprocessing.common.registries.QIOProcessingProcessorHosts;

public class TileEntityEliteQIOCraftingProcessor extends TieredQIOCraftingProcessor {

    public TileEntityEliteQIOCraftingProcessor() {
        super("EliteQIOCraftingProcessor", QIOProcessingProcessorHosts.ELITE_HOST_ID,
              QIOCraftingProcessorRegistry.ELITE_ID);
    }
}
