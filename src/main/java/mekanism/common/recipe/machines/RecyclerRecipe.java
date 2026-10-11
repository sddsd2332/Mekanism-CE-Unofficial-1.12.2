package mekanism.common.recipe.machines;

import mekanism.common.MekanismItems;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.outputs.ChanceOutput2;
import net.minecraft.item.ItemStack;

public class RecyclerRecipe extends Chance2MachineRecipe<RecyclerRecipe> {

    public static final ItemStack DEFAULT_OUTPUT = new ItemStack(MekanismItems.Scrap);
    public static final double DEFAULT_CHANCE = 1F / 6F;
    private final boolean temporary;

    public RecyclerRecipe(ItemStackInput input, ChanceOutput2 output) {
        this(input, output, false);
    }

    private RecyclerRecipe(ItemStackInput input, ChanceOutput2 output, boolean temporary) {
        super(input, output);
        this.temporary = temporary;
    }

    public RecyclerRecipe(ItemStack input, ItemStack primaryOutput, double chance) {
        this(new ItemStackInput(input), new ChanceOutput2(primaryOutput, chance));
    }

    public static RecyclerRecipe temporary(ItemStack input) {
        return new RecyclerRecipe(new ItemStackInput(input),
                new ChanceOutput2(DEFAULT_OUTPUT.copy(), DEFAULT_CHANCE), true);
    }

    public boolean isTemporary() {
        return temporary;
    }


    @Override
    public RecyclerRecipe copy() {
        return new RecyclerRecipe(getInput().copy(), getOutput().copy(), temporary);
    }
}
