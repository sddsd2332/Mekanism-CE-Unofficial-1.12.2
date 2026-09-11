package mekanism.common.recipe;

import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.machines.EnrichmentRecipe;
import mekanism.common.recipe.outputs.ItemStackOutput;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeGenerationTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void reloadBoundaryAlwaysAdvancesAndNotifies() {
        long before = RecipeHandler.getGlobalRecipeGeneration();
        AtomicLong observed = new AtomicLong(-1);
        Consumer<RecipeGeneration> listener = generation -> observed.set(generation.getGlobalGeneration());
        RecipeHandler.addRecipeGenerationListener(listener);
        try {
            RecipeHandler.markRecipeReloadComplete();
        } finally {
            RecipeHandler.removeRecipeGenerationListener(listener);
        }
        assertEquals(before + 1, RecipeHandler.getGlobalRecipeGeneration());
        assertEquals(before + 1, observed.get());
    }

    @Test
    void categoryAndGlobalGenerationAdvanceTogether() {
        RecipeHandler.Recipe<ItemStackInput, ItemStackOutput, EnrichmentRecipe> category =
              RecipeHandler.Recipe.ENRICHMENT_CHAMBER;
        EnrichmentRecipe recipe = new EnrichmentRecipe(new ItemStack(Items.CLAY_BALL, 13),
              new ItemStack(Items.DIAMOND));
        long globalBefore = RecipeHandler.getGlobalRecipeGeneration();
        long categoryBefore = category.getRecipeGeneration();
        category.put(recipe);
        try {
            assertTrue(RecipeHandler.getGlobalRecipeGeneration() > globalBefore);
            assertEquals(categoryBefore + 1, category.getRecipeGeneration());
            RecipeGeneration captured = category.getGeneration();
            assertEquals(RecipeHandler.getGlobalRecipeGeneration(), captured.getGlobalGeneration());
            assertEquals(category.getRecipeGeneration(), captured.getCategoryGeneration());
        } finally {
            category.remove(recipe);
        }
    }

    @Test
    void compiledDefinitionDoesNotChangeWhenLiveRecipeMutates() {
        EnrichmentRecipe recipe = new EnrichmentRecipe(new ItemStack(Items.IRON_INGOT, 2),
              new ItemStack(Items.DIAMOND, 1));
        RecipeGeneration generation = new RecipeGeneration(10, 3);
        RecipeDefinitionSnapshot compiled = RecipeSnapshotCompiler.compile("enrich", generation, recipe);
        String capturedSignature = compiled.getSemanticSignature();

        recipe.recipeInput.ingredient.setCount(30);
        recipe.recipeOutput.output.setCount(20);

        assertEquals(capturedSignature, compiled.getSemanticSignature());
        assertNotEquals(capturedSignature, RecipeSnapshotCompiler.semanticSignature(recipe));
    }
}
