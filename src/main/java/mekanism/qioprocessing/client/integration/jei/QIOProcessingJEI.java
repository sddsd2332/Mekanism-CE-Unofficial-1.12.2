package mekanism.qioprocessing.client.integration.jei;

import mekanism.client.jei.MekanismJEI;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.qioprocessing.client.integration.QIORecipeViewerBridge;
import mekanism.qioprocessing.common.content.policy.QIOPolicyEntrySnapshot;
import mekanism.qioprocessing.common.inventory.container.ContainerPortableQIOSmartProcessingTerminal;
import mekanism.qioprocessing.common.inventory.container.ContainerQIOSmartProcessingTerminal;
import mekanism.qioprocessing.common.inventory.container.ContainerPortableQIOProcessingTerminal;
import mekanism.qioprocessing.common.inventory.container.ContainerQIOProcessingTerminal;
import mekanism.qioprocessing.common.registries.QIOProcessingBlocks;
import mekanism.qioprocessing.common.registries.QIOProcessingItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JEIPlugin
public final class QIOProcessingJEI implements IModPlugin {

    @Override
    public void register(IModRegistry registry) {
        QIOWorkbenchJEIInputHandler.register();
        IRecipeTransferHandlerHelper helper =
              registry.getJeiHelpers().recipeTransferHandlerHelper();
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(
              new QIOProcessingRecipeTransferHandler<>(
                    ContainerQIOSmartProcessingTerminal.class, helper,
                    registry.getJeiHelpers().getStackHelper()),
              VanillaRecipeCategoryUid.CRAFTING);
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(
              new QIOWorkbenchRecipeTransferHandler<>(ContainerQIOProcessingTerminal.class,
                    helper), VanillaRecipeCategoryUid.CRAFTING);
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(
              new QIOWorkbenchRecipeTransferHandler<>(
                    ContainerPortableQIOProcessingTerminal.class, helper),
              VanillaRecipeCategoryUid.CRAFTING);
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(
              new QIOProcessingRecipeTransferHandler<>(
                    ContainerPortableQIOSmartProcessingTerminal.class, helper,
                    registry.getJeiHelpers().getStackHelper()),
              VanillaRecipeCategoryUid.CRAFTING);

        registry.addRecipeCatalyst(new ItemStack(QIOProcessingBlocks.QIOCraftingProcessor),
              VanillaRecipeCategoryUid.CRAFTING);
        registry.addRecipeCatalyst(new ItemStack(QIOProcessingBlocks.BasicQIOCraftingProcessor),
              VanillaRecipeCategoryUid.CRAFTING);
        registry.addRecipeCatalyst(new ItemStack(QIOProcessingBlocks.AdvancedQIOCraftingProcessor),
              VanillaRecipeCategoryUid.CRAFTING);
        registry.addRecipeCatalyst(new ItemStack(QIOProcessingBlocks.EliteQIOCraftingProcessor),
              VanillaRecipeCategoryUid.CRAFTING);
        registry.addRecipeCatalyst(new ItemStack(QIOProcessingBlocks.UltimateQIOCraftingProcessor),
              VanillaRecipeCategoryUid.CRAFTING);
        registry.addRecipeCatalyst(new ItemStack(QIOProcessingBlocks.QIOSmartProcessingTerminal),
              VanillaRecipeCategoryUid.CRAFTING);
        registry.addRecipeCatalyst(new ItemStack(
              QIOProcessingItems.PortableQIOSmartProcessingTerminal),
              VanillaRecipeCategoryUid.CRAFTING);
        registry.addRecipeCatalyst(new ItemStack(QIOProcessingBlocks.QIOManagementTerminal),
              VanillaRecipeCategoryUid.CRAFTING);
        registry.addRecipeCatalyst(new ItemStack(
              QIOProcessingItems.PortableQIOManagementTerminal),
              VanillaRecipeCategoryUid.CRAFTING);
        QIORecipeViewerBridge.register(new QIORecipeViewerBridge.Viewer() {
            @Override
            public boolean canOpen(QIOPolicyEntrySnapshot policy) {
                return recipeOutput(policy) != null || !categories(policy).isEmpty();
            }

            @Override
            public boolean open(QIOPolicyEntrySnapshot policy) {
                if (MekanismJEI.jeiRuntime == null) return false;
                ItemStack output = recipeOutput(policy);
                if (output != null) {
                    MekanismJEI.jeiRuntime.getRecipesGui().show(new IFocus<ItemStack>() {
                        @Override public ItemStack getValue() { return output; }
                        @Override public Mode getMode() { return Mode.OUTPUT; }
                    });
                    return true;
                }
                List<String> categories = categories(policy);
                if (categories.isEmpty()) return false;
                MekanismJEI.jeiRuntime.getRecipesGui().showCategories(categories);
                return true;
            }
        });
    }

    private static ItemStack recipeOutput(QIOPolicyEntrySnapshot policy) {
        if (policy == null || policy.getRoute() == null ||
            !"mekanismqioprocessing:workbench".equals(
                  policy.getRoute().getProviderId())) return null;
        try {
            IRecipe recipe = ForgeRegistries.RECIPES.getValue(new ResourceLocation(
                  policy.getRoute().getRecipeKey()));
            if (recipe == null || recipe.getRecipeOutput().isEmpty()) return null;
            return recipe.getRecipeOutput().copy();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<String> categories(QIOPolicyEntrySnapshot policy) {
        if (policy == null || policy.getRoute() == null) return Collections.emptyList();
        String provider = policy.getRoute().getProviderId();
        int separator = provider.indexOf(':');
        String path = separator < 0 ? provider : provider.substring(separator + 1);
        List<String> result = new ArrayList<>();
        switch (path) {
            case "metallurgic_infuser" -> add(result, Recipe.METALLURGIC_INFUSER);
            case "chemical_dissolution_chamber" -> add(result, Recipe.CHEMICAL_DISSOLUTION_CHAMBER);
            case "chemical_oxidizer" -> add(result, Recipe.CHEMICAL_OXIDIZER);
            case "nutritional_liquifier" -> add(result, Recipe.NUTRITIONAL_LIQUIFIER);
            case "chemical_crystallizer" -> add(result, Recipe.CHEMICAL_CRYSTALLIZER);
            case "chemical_infuser" -> add(result, Recipe.CHEMICAL_INFUSER);
            case "chemical_washer" -> add(result, Recipe.CHEMICAL_WASHER);
            case "electrolytic_separator" -> add(result, Recipe.ELECTROLYTIC_SEPARATOR);
            case "isotopic_centrifuge" -> add(result, Recipe.ISOTOPIC_CENTRIFUGE);
            case "pressurized_reaction_chamber" -> add(result, Recipe.PRESSURIZED_REACTION_CHAMBER);
            case "antiprotonic_nucleosynthesizer" -> add(result, Recipe.ANTIPROTONIC_NUCLEOSYNTHESIZER);
            case "rotary_condensentrator" -> add(result, Recipe.ROTARY_CONDENSENTRATOR);
            case "solar_neutron_activator" -> add(result, Recipe.SOLAR_NEUTRON_ACTIVATOR);
            case "chance_machine" -> {
                add(result, Recipe.PRECISION_SAWMILL); add(result, Recipe.RECYCLER);
                add(result, Recipe.CELL_EXTRACTOR); add(result, Recipe.CELL_SEPARATOR);
            }
            case "double_item_machine" -> {
                add(result, Recipe.COMBINER); add(result, Recipe.ALLOY);
            }
            case "advanced_gas_machine" -> {
                add(result, Recipe.PURIFICATION_CHAMBER);
                add(result, Recipe.CHEMICAL_INJECTION_CHAMBER);
                add(result, Recipe.OSMIUM_COMPRESSOR);
            }
            case "electric_machine", "factory" -> {
                add(result, Recipe.ENERGIZED_SMELTER); add(result, Recipe.ENRICHMENT_CHAMBER);
                add(result, Recipe.CRUSHER); add(result, Recipe.STAMPING);
                add(result, Recipe.ROLLING); add(result, Recipe.BRUSHED);
                add(result, Recipe.TURNING);
            }
            default -> {
            }
        }
        return result;
    }

    private static void add(List<String> categories, Recipe recipe) {
        String category = recipe.getJEICategory();
        if (!categories.contains(category)) categories.add(category);
    }
}
