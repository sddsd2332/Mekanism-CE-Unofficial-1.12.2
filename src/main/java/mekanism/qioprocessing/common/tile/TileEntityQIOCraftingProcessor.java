package mekanism.qioprocessing.common.tile;

import mekanism.qioprocessing.api.processor.QIOCraftingProcessorRegistry;
import mekanism.qioprocessing.common.registries.QIOProcessingProcessorHosts;
import net.minecraft.util.ResourceLocation;

/** Placeable ordinary one-lane processor and the default addon-reusable implementation. */
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
