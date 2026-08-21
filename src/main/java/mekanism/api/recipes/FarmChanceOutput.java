package mekanism.api.recipes;

import net.minecraft.item.ItemStack;

/**
 * One independently rolled chance output for an Organic Farm recipe.
 */
public final class FarmChanceOutput {

    private final ItemStack output;
    private final double chance;

    public FarmChanceOutput(ItemStack output, double chance) {
        if (output == null || output.isEmpty()) {
            throw new IllegalArgumentException("Farm chance output cannot be empty");
        }
        if (Double.isNaN(chance) || Double.isInfinite(chance) || chance < 0 || chance > 1) {
            throw new IllegalArgumentException("Farm chance must be between zero and one");
        }
        this.output = output.copy();
        this.chance = chance;
    }

    public ItemStack getOutput() {
        return output.copy();
    }

    public double getChance() {
        return chance;
    }

    public FarmChanceOutput copy() {
        return new FarmChanceOutput(output, chance);
    }
}
