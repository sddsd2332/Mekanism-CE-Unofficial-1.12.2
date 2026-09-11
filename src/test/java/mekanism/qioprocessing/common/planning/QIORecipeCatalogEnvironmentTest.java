package mekanism.qioprocessing.common.planning;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class QIORecipeCatalogEnvironmentTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void detectsIngredientChangesWithTheSameRecipeShapeAndOutput() {
        ShapedRecipes first = recipe("ingredient_change", Items.STICK,
              new ItemStack(Items.DIAMOND));
        ShapedRecipes second = recipe("ingredient_change", Items.STRING,
              new ItemStack(Items.DIAMOND));

        assertNotEquals(QIORecipeCatalogEnvironment.recipeFingerprint(first),
              QIORecipeCatalogEnvironment.recipeFingerprint(second));
    }

    @Test
    void detectsOutputNbtChanges() {
        ItemStack firstOutput = new ItemStack(Items.DIAMOND);
        NBTTagCompound firstTag = new NBTTagCompound();
        firstTag.setString("variant", "first");
        firstOutput.setTagCompound(firstTag);
        ItemStack secondOutput = new ItemStack(Items.DIAMOND);
        NBTTagCompound secondTag = new NBTTagCompound();
        secondTag.setString("variant", "second");
        secondOutput.setTagCompound(secondTag);

        ShapedRecipes first = recipe("nbt_change", Items.STICK, firstOutput);
        ShapedRecipes second = recipe("nbt_change", Items.STICK, secondOutput);

        assertNotEquals(QIORecipeCatalogEnvironment.recipeFingerprint(first),
              QIORecipeCatalogEnvironment.recipeFingerprint(second));
    }

    @Test
    void detectsOreMemberChangesWithoutChangingTheOreNameOrCount() {
        String first = QIORecipeCatalogEnvironment.oreFingerprint("ingotTest", 17,
              Arrays.asList(new ItemStack(Items.IRON_INGOT),
                    new ItemStack(Items.GOLD_INGOT)));
        String second = QIORecipeCatalogEnvironment.oreFingerprint("ingotTest", 17,
              Arrays.asList(new ItemStack(Items.IRON_INGOT),
                    new ItemStack(Items.DIAMOND)));

        assertNotEquals(first, second);
    }

    private static ShapedRecipes recipe(String path, Item input, ItemStack output) {
        NonNullList<Ingredient> ingredients = NonNullList.withSize(1, Ingredient.EMPTY);
        ingredients.set(0, Ingredient.fromStacks(new ItemStack(input)));
        ShapedRecipes recipe = new ShapedRecipes("", 1, 1, ingredients, output);
        recipe.setRegistryName(new ResourceLocation("qio_test", path));
        return recipe;
    }
}
