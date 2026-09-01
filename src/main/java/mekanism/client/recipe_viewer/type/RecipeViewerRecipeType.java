package mekanism.client.recipe_viewer.type;

import mekanism.client.jei.MekanismJEIRecipeType;
import mekanism.common.recipe.RecipeHandler.Recipe;
import net.minecraft.util.ResourceLocation;

public final class RecipeViewerRecipeType {

    private RecipeViewerRecipeType() {
    }

    public static final IRecipeViewerRecipeType<?> VANILLA_CRAFTING = simple(new ResourceLocation("minecraft", "crafting"));
    public static final IRecipeViewerRecipeType<?> VANILLA_SMELTING = simple(new ResourceLocation("minecraft", "smelting"));

    public static final IRecipeViewerRecipeType<?> CRUSHING = MekanismJEIRecipeType.CRUSHING;
    public static final IRecipeViewerRecipeType<?> ENRICHING = MekanismJEIRecipeType.ENRICHING;
    public static final IRecipeViewerRecipeType<?> SMELTING = MekanismJEIRecipeType.SMELTING;
    public static final IRecipeViewerRecipeType<?> STAMPING = MekanismJEIRecipeType.STAMPING;
    public static final IRecipeViewerRecipeType<?> ROLLING = MekanismJEIRecipeType.ROLLING;
    public static final IRecipeViewerRecipeType<?> BRUSHED = MekanismJEIRecipeType.BRUSHED;
    public static final IRecipeViewerRecipeType<?> TURNING = MekanismJEIRecipeType.TURNING;
    public static final IRecipeViewerRecipeType<?> COMBINING = MekanismJEIRecipeType.COMBINING;
    public static final IRecipeViewerRecipeType<?> ALLOYING = MekanismJEIRecipeType.ALLOYING;
    public static final IRecipeViewerRecipeType<?> CHEMICAL_INFUSING = MekanismJEIRecipeType.CHEMICAL_INFUSING;
    public static final IRecipeViewerRecipeType<?> SEPARATING = MekanismJEIRecipeType.SEPARATING;
    public static final IRecipeViewerRecipeType<?> WASHING = MekanismJEIRecipeType.WASHING;
    public static final IRecipeViewerRecipeType<?> ACTIVATING = MekanismJEIRecipeType.ACTIVATING;
    public static final IRecipeViewerRecipeType<?> CENTRIFUGING = MekanismJEIRecipeType.CENTRIFUGING;
    public static final IRecipeViewerRecipeType<?> CRYSTALLIZING = MekanismJEIRecipeType.CRYSTALLIZING;
    public static final IRecipeViewerRecipeType<?> DISSOLUTION = MekanismJEIRecipeType.DISSOLUTION;
    public static final IRecipeViewerRecipeType<?> OXIDIZING = MekanismJEIRecipeType.OXIDIZING;
    public static final IRecipeViewerRecipeType<?> REACTION = MekanismJEIRecipeType.REACTION;
    public static final IRecipeViewerRecipeType<?> COMPRESSING = MekanismJEIRecipeType.COMPRESSING;
    public static final IRecipeViewerRecipeType<?> PURIFYING = MekanismJEIRecipeType.PURIFYING;
    public static final IRecipeViewerRecipeType<?> INJECTING = MekanismJEIRecipeType.INJECTING;
    public static final IRecipeViewerRecipeType<?> SAWING = MekanismJEIRecipeType.SAWING;
    public static final IRecipeViewerRecipeType<?> CELL_EXTRACTING = MekanismJEIRecipeType.CELL_EXTRACTING;
    public static final IRecipeViewerRecipeType<?> CELL_SEPARATING = MekanismJEIRecipeType.CELL_SEPARATING;
    public static final IRecipeViewerRecipeType<?> NUCLEOSYNTHESIZING = MekanismJEIRecipeType.NUCLEOSYNTHESIZING;
    public static final IRecipeViewerRecipeType<?> ENERGY_CONVERSION = MekanismJEIRecipeType.ENERGY_CONVERSION;
    public static final IRecipeViewerRecipeType<?> NUTRITIONAL_LIQUIFICATION = MekanismJEIRecipeType.NUTRITIONAL_LIQUIFICATION;

    public static final IRecipeViewerRecipeType<?> METALLURGIC_INFUSING = simple(Recipe.METALLURGIC_INFUSER.getJEICategory());
    public static final IRecipeViewerRecipeType<?> EVAPORATING = simple(Recipe.THERMAL_EVAPORATION_PLANT.getJEICategory());
    public static final IRecipeViewerRecipeType<?> CONDENSENTRATING = simple("mekanism.rotary_condensentrator_condensentrating");
    public static final IRecipeViewerRecipeType<?> DECONDENSENTRATING = simple("mekanism.rotary_condensentrator_decondensentrating");
    public static final IRecipeViewerRecipeType<?> ORGANIC_FARM = simple(Recipe.ORGANIC_FARM.getJEICategory());
    public static final IRecipeViewerRecipeType<?> RECYCLER = simple(Recipe.RECYCLER.getJEICategory());
    public static final IRecipeViewerRecipeType<?> AMBIENT_ACCUMULATOR = simple(Recipe.AMBIENT_ACCUMULATOR.getJEICategory());
    public static final IRecipeViewerRecipeType<?> SPS = simple("mekanism.sps");
    public static final IRecipeViewerRecipeType<?> FISSION_REACTOR = simple("mekanismgenerators.fission_reactor");
    public static final IRecipeViewerRecipeType<?> FUSION_COOLING = simple(Recipe.FUSION_COOLING.getJEICategory());
    public static final IRecipeViewerRecipeType<?> GAS_FUEL_TO_ENERGY = simple(Recipe.GAS_FUEL_TO_ENERGY_RECIPE.getJEICategory());

    public static IRecipeViewerRecipeType<?> simple(String categoryUid) {
        return new SimpleRecipeViewerRecipeType(idFromCategoryUid(categoryUid));
    }

    public static IRecipeViewerRecipeType<?> simple(ResourceLocation id) {
        return new SimpleRecipeViewerRecipeType(id);
    }

    public static ResourceLocation idFromCategoryUid(String categoryUid) {
        int index = categoryUid.indexOf('.');
        if (index == -1) {
            return new ResourceLocation(categoryUid);
        }
        return new ResourceLocation(categoryUid.substring(0, index), categoryUid.substring(index + 1));
    }

    public static String categoryUid(IRecipeViewerRecipeType<?> recipeType) {
        return recipeType == null ? null : categoryUid(recipeType.id());
    }

    public static String categoryUid(ResourceLocation id) {
        return id == null ? null : id.getNamespace() + "." + id.getPath();
    }

    private static class SimpleRecipeViewerRecipeType implements IRecipeViewerRecipeType<Object> {

        private final ResourceLocation id;

        private SimpleRecipeViewerRecipeType(ResourceLocation id) {
            this.id = id;
        }

        @Override
        public ResourceLocation id() {
            return id;
        }

        @Override
        public Class<?> recipeClass() {
            return Object.class;
        }

        @Override
        public int xOffset() {
            return 0;
        }

        @Override
        public int yOffset() {
            return 0;
        }

        @Override
        public int width() {
            return 0;
        }

        @Override
        public int height() {
            return 0;
        }
    }
}
