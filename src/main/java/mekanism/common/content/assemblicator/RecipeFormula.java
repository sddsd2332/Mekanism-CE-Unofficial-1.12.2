package mekanism.common.content.assemblicator;

import mekanism.api.inventory.IInventorySlot;
import mekanism.common.Mekanism;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.RecipeUtils;
import mekanism.common.util.StackUtils;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class RecipeFormula {

    public NonNullList<ItemStack> input = NonNullList.withSize(9, ItemStack.EMPTY);
    public IRecipe recipe;
    private InventoryCrafting dummy = MekanismUtils.getDummyCraftingInv();

    public RecipeFormula(World world, NonNullList<ItemStack> inv) {
        this(world, inv, 0);
    }

    public RecipeFormula(World world, NonNullList<ItemStack> inv, int start) {
        for (int i = 0; i < 9; i++) {
            input.set(i, StackUtils.size(inv.get(start + i), 1));
        }
        resetToRecipe();
        recipe = RecipeUtils.getRecipeFromGrid(dummy, world);
    }

    public RecipeFormula(World world, List<IInventorySlot> craftingGridSlots) {
        for (int i = 0; i < craftingGridSlots.size(); i++) {
            IInventorySlot craftingSlot = craftingGridSlots.get(i);
            if (!craftingSlot.isEmpty()) {
                input.set(i, StackUtils.size(craftingSlot.getStack(), 1));
            }
        }
        resetToRecipe();
        recipe = RecipeUtils.getRecipeFromGrid(dummy, world);
    }

    private void resetToRecipe() {
        for (int i = 0; i < 9; i++) {
            dummy.setInventorySlotContents(i, input.get(i));
        }
    }

    public boolean matches(World world, NonNullList<ItemStack> newInput, int start) {
        for (int i = 0; i < 9; i++) {
            dummy.setInventorySlotContents(i, newInput.get(start + i));
        }
        return recipe.matches(dummy, world);
    }

    public boolean matches(World world, List<IInventorySlot> craftingGridSlots) {
        if (recipe == null) {
            return false;
        }
        for (int i = 0; i < craftingGridSlots.size(); i++) {
            dummy.setInventorySlotContents(i, StackUtils.size(craftingGridSlots.get(i).getStack(), 1));
        }
        return recipe.matches(dummy, world);
    }

    public ItemStack assemble() {
        return recipe == null ? ItemStack.EMPTY : recipe.getCraftingResult(dummy);
    }

    public NonNullList<ItemStack> getRemainingItems() {
        return recipe == null ? NonNullList.create() : recipe.getRemainingItems(dummy);
    }

    public boolean isIngredientInPos(World world, ItemStack stack, int i) {
        resetToRecipe();
        dummy.setInventorySlotContents(i, stack);
        return recipe.matches(dummy, world);
    }

    public boolean isIngredient(World world, ItemStack stack) {
        for (int i = 0; i < 9; i++) {
            dummy.setInventorySlotContents(i, stack);
            if (recipe.matches(dummy, world)) {
                return true;
            }
            dummy.setInventorySlotContents(i, input.get(i));
        }
        return false;
    }

    public List<Integer> getIngredientIndices(World world, ItemStack stack) {
        List<Integer> ret = new ArrayList<>();
        //这是什么崩溃？
        try {
            for (int i = 0; i < 9; i++) {
                dummy.setInventorySlotContents(i, stack);
                if (recipe.matches(dummy, world)) {
                    ret.add(i);
                }
                dummy.setInventorySlotContents(i, input.get(i));
            }
        } catch (Exception e) {
            Mekanism.logger.error(e);
        }
        return ret;
    }

    public boolean isValidFormula(World world) {
        return getRecipe(world) != null;
    }

    public IRecipe getRecipe(World world) {
        return recipe;
    }

    public boolean isFormulaEqual(World world, RecipeFormula formula) {
        return formula.getRecipe(world) == getRecipe(world);
    }
}
