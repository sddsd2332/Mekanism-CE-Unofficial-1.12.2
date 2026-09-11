package mekanism.common.recipe.lookup;

import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.machines.EnrichmentRecipe;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Blocks;
import net.minecraftforge.oredict.OreDictionary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the high-version-style category cache through the real RecipeMap. */
class HighVersionInputRecipeCacheTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void categoryCacheUsesCandidatesAndPreservesWildcardOrder() {
        RecipeHandler.Recipe<ItemStackInput, ?, EnrichmentRecipe> category = RecipeHandler.Recipe.ENRICHMENT_CHAMBER;
        EnrichmentRecipe exact = new EnrichmentRecipe(new ItemStack(Blocks.WOOL, 1, 2), new ItemStack(Items.DIAMOND));
        EnrichmentRecipe wildcard = new EnrichmentRecipe(new ItemStack(Blocks.WOOL, 1, OreDictionary.WILDCARD_VALUE), new ItemStack(Items.GOLD_INGOT));
        try {
            category.put(wildcard);
            category.put(exact);
            assertFalse(category.getInputCache().isInitialized());

            EnrichmentRecipe exactFound = RecipeHandler.getRecipe(new ItemStackInput(new ItemStack(Blocks.WOOL, 1, 2)), category);
            assertSame(Items.DIAMOND, exactFound.getOutput().output.getItem());
            assertNotSame(exact, exactFound);
            assertTrue(category.getInputCache().isInitialized());

            EnrichmentRecipe wildcardFound = RecipeHandler.getRecipe(new ItemStackInput(new ItemStack(Blocks.WOOL, 1, 3)), category);
            assertSame(Items.GOLD_INGOT, wildcardFound.getOutput().output.getItem());
            assertEquals(2, category.getCachedRecipes().size());
        } finally {
            category.remove(exact);
            category.remove(wildcard);
        }
    }

    @Test
    void categoryMutationClearsThePublishedInputCache() {
        RecipeHandler.Recipe<ItemStackInput, ?, EnrichmentRecipe> category = RecipeHandler.Recipe.ENRICHMENT_CHAMBER;
        EnrichmentRecipe recipe = new EnrichmentRecipe(new ItemStack(Blocks.WOOL, 1, 7), new ItemStack(Items.DIAMOND));
        try {
            category.put(recipe);
            assertFalse(category.getInputCache().isInitialized());
            assertTrue(RecipeHandler.getRecipe(new ItemStackInput(new ItemStack(Blocks.WOOL, 1, 7)), category) != null);
            assertTrue(category.getInputCache().isInitialized());
            category.remove(recipe);
            assertFalse(category.getInputCache().isInitialized());
        } finally {
            category.remove(recipe);
        }
    }
}
