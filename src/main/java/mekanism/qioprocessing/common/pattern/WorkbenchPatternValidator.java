package mekanism.qioprocessing.common.pattern;

import mekanism.qioprocessing.common.recipe.RecipeDeclaration;
import mekanism.qioprocessing.common.recipe.RecipeItemKey;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.*;

/**
 * Main-thread-only validation boundary for concrete workbench patterns.
 */
public final class WorkbenchPatternValidator {
    private WorkbenchPatternValidator() {
    }

    public static WorkbenchValidationResult validate(World world, RecipeDeclaration declaration, String catalogGeneration, List<ItemStack> source, UUID id) {
        return validate(null, world, declaration, catalogGeneration, source, id);
    }

    public static WorkbenchValidationResult validate(EntityPlayer player, World world,
                                                     RecipeDeclaration declaration, String catalogGeneration, List<ItemStack> source, UUID id) {
        if (world == null || world.isRemote)
            throw new IllegalStateException("Pattern validation requires the server world");
        if (world.getMinecraftServer() == null || !world.getMinecraftServer().isCallingFromMinecraftThread())
            throw new IllegalStateException("Pattern validation requires the server thread");
        if (source == null || source.size() != 9)
            return WorkbenchValidationResult.invalid(WorkbenchValidationResult.Status.EMPTY_GRID, "Nine slots are required");
        if (!singleItemInputs(source))
            return WorkbenchValidationResult.invalid(WorkbenchValidationResult.Status.INVALID_INPUT, "Each occupied slot requires exactly one item");
        boolean any = false;
        List<ItemStack> grid = new ArrayList<>(9);
        for (ItemStack stack : source) {
            ItemStack copy = stack == null ? ItemStack.EMPTY : stack.copy();
            grid.add(copy);
            any |= !copy.isEmpty();
        }
        if (!any)
            return WorkbenchValidationResult.invalid(WorkbenchValidationResult.Status.EMPTY_GRID, "Grid is empty");
        IRecipe recipe = ForgeRegistries.RECIPES.getValue(new ResourceLocation(declaration.id()));
        if (recipe == null)
            return WorkbenchValidationResult.invalid(WorkbenchValidationResult.Status.UNKNOWN_RECIPE, declaration.id());
        InventoryCrafting inventory = new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) {
                return true;
            }

            @Override
            public void onCraftMatrixChanged(net.minecraft.inventory.IInventory inventory) {
            }
        }, 3, 3);
        for (int i = 0; i < 9; i++) inventory.setInventorySlotContents(i, grid.get(i));
        try {
            ForgeHooks.setCraftingPlayer(player);
            if (!recipe.matches(inventory, world))
                return WorkbenchValidationResult.invalid(WorkbenchValidationResult.Status.NO_MATCH, declaration.id());
            ItemStack output = recipe.getCraftingResult(inventory);
            if (output == null || output.isEmpty())
                return WorkbenchValidationResult.invalid(WorkbenchValidationResult.Status.EMPTY_OUTPUT, declaration.id());
            ItemStack declared = declaration.output().toStack(declaration.outputCount());
            if (!ItemStack.areItemStacksEqual(output, declared) || output.getCount() != declaration.outputCount())
                return WorkbenchValidationResult.invalid(WorkbenchValidationResult.Status.OUTPUT_CHANGED, declaration.id());
            NonNullList<ItemStack> remainder = recipe.getRemainingItems(inventory);
            if (remainder == null || remainder.size() != 9)
                return WorkbenchValidationResult.invalid(WorkbenchValidationResult.Status.INVALID_REMAINDERS, declaration.id());
            List<RecipeItemKey> frozenGrid = new ArrayList<>(9), frozenRemainders = new ArrayList<>(9);
            Map<RecipeItemKey, Integer> consumed = new LinkedHashMap<>();
            for (int i = 0; i < 9; i++) {
                ItemStack input = grid.get(i);
                ItemStack left = remainder.get(i);
                frozenGrid.add(input.isEmpty() ? null : RecipeItemKey.capture(input));
                frozenRemainders.add(left == null || left.isEmpty() ? null : RecipeItemKey.capture(left));
                if (left != null && !left.isEmpty() && left.getCount() != 1)
                    return WorkbenchValidationResult.invalid(WorkbenchValidationResult.Status.INVALID_REMAINDERS, declaration.id());
                if (!input.isEmpty()) consumed.merge(RecipeItemKey.capture(input), 1, Math::addExact);
            }
            VerifiedWorkbenchPattern pattern = new VerifiedWorkbenchPattern(id, declaration.id(), declaration.signature(),
                    catalogGeneration, frozenGrid, RecipeItemKey.capture(output), output.getCount(), frozenRemainders, consumed);
            return WorkbenchValidationResult.valid(pattern);
        } catch (RuntimeException | LinkageError error) {
            return WorkbenchValidationResult.invalid(WorkbenchValidationResult.Status.FAILED, error.getClass().getName() + ": " + error.getMessage());
        } finally {
            ForgeHooks.setCraftingPlayer(null);
        }
    }

    static boolean singleItemInputs(List<ItemStack> source) {
        if (source == null || source.size() != 9) return false;
        for (ItemStack stack : source) if (stack != null && !stack.isEmpty() && stack.getCount() != 1) return false;
        return true;
    }
}
