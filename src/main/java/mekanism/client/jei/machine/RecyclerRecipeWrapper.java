package mekanism.client.jei.machine;

import mekanism.common.recipe.machines.RecyclerRecipe;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.List;

public class RecyclerRecipeWrapper extends Chance2MachineRecipeWrapper<RecyclerRecipe> {

    private final List<ItemStack> inputs;

    public RecyclerRecipeWrapper(RecyclerRecipe recipe, List<ItemStack> inputs) {
        super(recipe);
        this.inputs = inputs;
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        ingredients.setInputLists(VanillaTypes.ITEM, Collections.singletonList(inputs));

        if (recipe.getOutput().hasPrimary()) {
            ingredients.setOutputs(VanillaTypes.ITEM, Collections.singletonList(recipe.getOutput().primaryOutput));
        }
    }

}
