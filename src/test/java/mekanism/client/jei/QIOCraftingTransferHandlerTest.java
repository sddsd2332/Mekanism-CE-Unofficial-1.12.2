package mekanism.client.jei;

import mekanism.client.jei.QIOCraftingTransferHandler.RecipeInput;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import mekanism.common.inventory.container.SelectedWindowData;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.IStackHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QIOCraftingTransferHandlerTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void preservesHeiCraftingGridCoordinatesForShapedRecipes() {
        Map<Integer, IGuiIngredient<ItemStack>> ingredients = emptyCraftingLayout();
        ingredients.put(1, input(new ItemStack(Blocks.STONE)));
        ingredients.put(2, input(new ItemStack(Blocks.STONE)));
        ingredients.put(4, input(new ItemStack(Blocks.STONE)));
        ingredients.put(5, input(new ItemStack(Blocks.STONE)));

        List<RecipeInput> inputs = QIOCraftingTransferHandler.getRecipeInputs(layout(ingredients));

        assertEquals(4, inputs.size());
        assertEquals(0, inputs.get(0).targetSlot);
        assertEquals(1, inputs.get(1).targetSlot);
        assertEquals(3, inputs.get(2).targetSlot);
        assertEquals(4, inputs.get(3).targetSlot);
    }

    @Test
    void mapsHeiCenteredSingleInputToTheCenterOfTheQioGrid() {
        Map<Integer, IGuiIngredient<ItemStack>> ingredients = emptyCraftingLayout();
        ingredients.put(5, input(new ItemStack(Blocks.STONE)));

        List<RecipeInput> inputs = QIOCraftingTransferHandler.getRecipeInputs(layout(ingredients));

        assertEquals(1, inputs.size());
        assertEquals(4, inputs.get(0).targetSlot);
        assertEquals(5, inputs.get(0).guiSlot);
    }

    @Test
    void rejectsInputSlotsOutsideTheHeiCraftingGrid() {
        Map<Integer, IGuiIngredient<ItemStack>> ingredients = emptyCraftingLayout();
        ingredients.put(10, input(new ItemStack(Blocks.STONE)));

        assertNull(QIOCraftingTransferHandler.getRecipeInputs(layout(ingredients)));
    }

    @Test
    void hidesTransferButtonWithoutAFocusedCraftingWindow() {
        AtomicInteger internalErrors = new AtomicInteger();
        AtomicInteger userErrors = new AtomicInteger();
        IRecipeTransferHandlerHelper helper = (IRecipeTransferHandlerHelper) Proxy.newProxyInstance(
              IRecipeTransferHandlerHelper.class.getClassLoader(), new Class[]{IRecipeTransferHandlerHelper.class},
              (proxy, method, args) -> {
                  if ("createInternalError".equals(method.getName())) {
                      internalErrors.incrementAndGet();
                  } else if (method.getName().startsWith("createUserError")) {
                      userErrors.incrementAndGet();
                  }
                  return null;
              });
        IStackHelper stackHelper = (IStackHelper) Proxy.newProxyInstance(IStackHelper.class.getClassLoader(),
              new Class[]{IStackHelper.class}, (proxy, method, args) -> null);
        QIOCraftingTransferHandler<QIOItemViewerContainer> handler = new QIOCraftingTransferHandler<>(
              QIOItemViewerContainer.class, helper, stackHelper);
        QIOItemViewerContainer container = new QIOItemViewerContainer(null, null);

        handler.transferRecipe(container, null, null, false, false);

        container.setSelectedWindow(new SelectedWindowData(SelectedWindowData.WindowType.QIO_FREQUENCY));
        handler.transferRecipe(container, null, null, false, false);

        assertEquals(2, internalErrors.get());
        assertEquals(0, userErrors.get());
    }

    private static Map<Integer, IGuiIngredient<ItemStack>> emptyCraftingLayout() {
        Map<Integer, IGuiIngredient<ItemStack>> ingredients = new LinkedHashMap<>();
        ingredients.put(0, output());
        for (int slot = 1; slot <= 9; slot++) {
            ingredients.put(slot, input());
        }
        return ingredients;
    }

    private static IGuiIngredient<ItemStack> input(ItemStack... stacks) {
        List<ItemStack> ingredients = new ArrayList<>();
        Collections.addAll(ingredients, stacks);
        return ingredient(true, ingredients);
    }

    private static IGuiIngredient<ItemStack> output() {
        return ingredient(false, Collections.emptyList());
    }

    private static IGuiIngredient<ItemStack> ingredient(boolean input, List<ItemStack> ingredients) {
        return new IGuiIngredient<ItemStack>() {
            @Override
            public ItemStack getDisplayedIngredient() {
                return ingredients.isEmpty() ? null : ingredients.get(0);
            }

            @Override
            public List<ItemStack> getAllIngredients() {
                return ingredients;
            }

            @Override
            public boolean isInput() {
                return input;
            }

            @Override
            public void drawHighlight(Minecraft minecraft, Color color, int xOffset, int yOffset) {
            }
        };
    }

    private static IRecipeLayout layout(Map<Integer, IGuiIngredient<ItemStack>> ingredients) {
        IGuiItemStackGroup group = (IGuiItemStackGroup) Proxy.newProxyInstance(
              IGuiItemStackGroup.class.getClassLoader(), new Class[]{IGuiItemStackGroup.class},
              (proxy, method, args) -> "getGuiIngredients".equals(method.getName()) ? ingredients : null);
        return (IRecipeLayout) Proxy.newProxyInstance(IRecipeLayout.class.getClassLoader(), new Class[]{IRecipeLayout.class},
              (proxy, method, args) -> "getItemStacks".equals(method.getName()) ? group : null);
    }

}
