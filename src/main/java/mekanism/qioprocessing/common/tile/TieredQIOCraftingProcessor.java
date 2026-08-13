package mekanism.qioprocessing.common.tile;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;

/** Shared inheritance point for the four built-in tiered variants. */
public abstract class TieredQIOCraftingProcessor extends QIOCraftingProcessor {

    protected TieredQIOCraftingProcessor(@Nonnull String name, @Nonnull ResourceLocation hostId,
          @Nonnull ResourceLocation definitionId) {
        super(name, hostId, definitionId);
    }
}
