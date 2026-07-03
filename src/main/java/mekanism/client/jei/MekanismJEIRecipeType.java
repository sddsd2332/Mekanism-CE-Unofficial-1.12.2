package mekanism.client.jei;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.machines.AdvancedMachineRecipe;
import mekanism.common.recipe.machines.BasicMachineRecipe;
import mekanism.common.recipe.machines.ChanceMachineRecipe;
import mekanism.common.recipe.machines.ChemicalInfuserRecipe;
import mekanism.common.recipe.machines.CrystallizerRecipe;
import mekanism.common.recipe.machines.DissolutionRecipe;
import mekanism.common.recipe.machines.DoubleMachineRecipe;
import mekanism.common.recipe.ItemStackToEnergyRecipe;
import mekanism.common.recipe.machines.IsotopicRecipe;
import mekanism.common.recipe.machines.OxidationRecipe;
import mekanism.common.recipe.machines.PressurizedRecipe;
import mekanism.common.recipe.machines.SeparatorRecipe;
import mekanism.common.recipe.machines.SolarNeutronRecipe;
import mekanism.common.recipe.machines.WasherRecipe;
import mekanism.common.recipe.machines.NucleosynthesizerRecipe;
import mekanism.common.recipe.machines.NutritionalRecipe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.Collections;
import java.util.List;

/**
 * 1.12 HEI-facing metadata mirror of the high-version Mekanism JEI recipe type.
 *
 * <p>HEI 1.12 still keys categories by String uid, but keeping this type metadata
 * in one place lets category constructors and registration follow the modern
 * Mekanism layout model: recipe id, icon, offset and background dimensions travel together.</p>
 */
public class MekanismJEIRecipeType<RECIPE> implements IRecipeViewerRecipeType<RECIPE> {

    public static final MekanismJEIRecipeType<BasicMachineRecipe<?>> CRUSHING = machine(Recipe.CRUSHER, MachineType.CRUSHER, "tile.MachineBlock.Crusher.name", BasicMachineRecipe.class, -28, -16, 144, 54);
    public static final MekanismJEIRecipeType<BasicMachineRecipe<?>> ENRICHING = machine(Recipe.ENRICHMENT_CHAMBER, MachineType.ENRICHMENT_CHAMBER, "tile.MachineBlock.EnrichmentChamber.name", BasicMachineRecipe.class, -28, -16, 144, 54);
    public static final MekanismJEIRecipeType<BasicMachineRecipe<?>> SMELTING = machine(Recipe.ENERGIZED_SMELTER, MachineType.ENERGIZED_SMELTER, "tile.MachineBlock.EnergizedSmelter.name", BasicMachineRecipe.class, -28, -16, 144, 54);
    public static final MekanismJEIRecipeType<BasicMachineRecipe<?>> STAMPING = machine(Recipe.STAMPING, MachineType.STAMPING, "tile.MachineBlock4.Stamping.name", BasicMachineRecipe.class, -28, -16, 144, 54);
    public static final MekanismJEIRecipeType<BasicMachineRecipe<?>> ROLLING = machine(Recipe.ROLLING, MachineType.ROLLING, "tile.MachineBlock4.Rolling.name", BasicMachineRecipe.class, -28, -16, 144, 54);
    public static final MekanismJEIRecipeType<BasicMachineRecipe<?>> BRUSHED = machine(Recipe.BRUSHED, MachineType.BRUSHED, "tile.MachineBlock4.Brushed.name", BasicMachineRecipe.class, -28, -16, 144, 54);
    public static final MekanismJEIRecipeType<BasicMachineRecipe<?>> TURNING = machine(Recipe.TURNING, MachineType.TURNING, "tile.MachineBlock4.Turning.name", BasicMachineRecipe.class, -28, -16, 144, 54);

    public static final MekanismJEIRecipeType<DoubleMachineRecipe<?>> COMBINING = machine(Recipe.COMBINER, MachineType.COMBINER, "tile.MachineBlock.Combiner.name", DoubleMachineRecipe.class, -28, -16, 144, 54);
    public static final MekanismJEIRecipeType<DoubleMachineRecipe<?>> ALLOYING = machine(Recipe.ALLOY, MachineType.ALLOY, "tile.MachineBlock4.Alloy.name", DoubleMachineRecipe.class, -28, -16, 144, 54);

    public static final MekanismJEIRecipeType<ChemicalInfuserRecipe> CHEMICAL_INFUSING = machine(Recipe.CHEMICAL_INFUSER, MachineType.CHEMICAL_INFUSER, "tile.MachineBlock2.ChemicalInfuser.name", ChemicalInfuserRecipe.class, -4, -4, 169, 79);
    public static final MekanismJEIRecipeType<SeparatorRecipe> SEPARATING = machine(Recipe.ELECTROLYTIC_SEPARATOR, MachineType.ELECTROLYTIC_SEPARATOR, "tile.MachineBlock2.ElectrolyticSeparator.name", SeparatorRecipe.class, -4, -9, 167, 62);
    public static final MekanismJEIRecipeType<WasherRecipe> WASHING = machine(Recipe.CHEMICAL_WASHER, MachineType.CHEMICAL_WASHER, "tile.MachineBlock2.ChemicalWasher.name", WasherRecipe.class, -4, -4, 169, 69);
    public static final MekanismJEIRecipeType<SolarNeutronRecipe> ACTIVATING = machine(Recipe.SOLAR_NEUTRON_ACTIVATOR, MachineType.SOLAR_NEUTRON_ACTIVATOR, "tile.MachineBlock3.SolarNeutronActivator.name", SolarNeutronRecipe.class, -3, -12, 170, 62);
    public static final MekanismJEIRecipeType<IsotopicRecipe> CENTRIFUGING = machine(Recipe.ISOTOPIC_CENTRIFUGE, MachineType.ISOTOPIC_CENTRIFUGE, "tile.MachineBlock3.IsotopicCentrifuge.name", IsotopicRecipe.class, -3, -12, 170, 62);
    public static final MekanismJEIRecipeType<CrystallizerRecipe> CRYSTALLIZING = machine(Recipe.CHEMICAL_CRYSTALLIZER, MachineType.CHEMICAL_CRYSTALLIZER, "tile.MachineBlock2.ChemicalCrystallizer.name", CrystallizerRecipe.class, -5, -3, 147, 79);
    public static final MekanismJEIRecipeType<DissolutionRecipe> DISSOLUTION = machine(Recipe.CHEMICAL_DISSOLUTION_CHAMBER, MachineType.CHEMICAL_DISSOLUTION_CHAMBER, "gui.chemicalDissolutionChamber.short", DissolutionRecipe.class, -4, -4, 169, 78);
    public static final MekanismJEIRecipeType<OxidationRecipe> OXIDIZING = machine(Recipe.CHEMICAL_OXIDIZER, MachineType.CHEMICAL_OXIDIZER, "tile.MachineBlock2.ChemicalOxidizer.name", OxidationRecipe.class, -20, -12, 132, 62);
    public static final MekanismJEIRecipeType<PressurizedRecipe> REACTION = machine(Recipe.PRESSURIZED_REACTION_CHAMBER, MachineType.PRESSURIZED_REACTION_CHAMBER, "tile.MachineBlock2.PressurizedReactionChamber.short.name", PressurizedRecipe.class, -3, -10, 170, 65);

    public static final MekanismJEIRecipeType<AdvancedMachineRecipe<?>> COMPRESSING = machine(Recipe.OSMIUM_COMPRESSOR, MachineType.OSMIUM_COMPRESSOR, "tile.MachineBlock.OsmiumCompressor.name", AdvancedMachineRecipe.class, -28, -16, 144, 54);
    public static final MekanismJEIRecipeType<AdvancedMachineRecipe<?>> PURIFYING = machine(Recipe.PURIFICATION_CHAMBER, MachineType.PURIFICATION_CHAMBER, "tile.MachineBlock.PurificationChamber.name", AdvancedMachineRecipe.class, -28, -16, 144, 54);
    public static final MekanismJEIRecipeType<AdvancedMachineRecipe<?>> INJECTING = machine(Recipe.CHEMICAL_INJECTION_CHAMBER, MachineType.CHEMICAL_INJECTION_CHAMBER, "tile.MachineBlock2.ChemicalInjectionChamber.name", AdvancedMachineRecipe.class, -28, -16, 144, 54);

    public static final MekanismJEIRecipeType<ChanceMachineRecipe<?>> SAWING = machine(Recipe.PRECISION_SAWMILL, MachineType.PRECISION_SAWMILL, "tile.MachineBlock2.PrecisionSawmill.name", ChanceMachineRecipe.class, -28, -16, 144, 54);
    public static final MekanismJEIRecipeType<ChanceMachineRecipe<?>> CELL_EXTRACTING = machine(Recipe.CELL_EXTRACTOR, MachineType.CELL_EXTRACTOR, "tile.MachineBlock4.CellExtractor.name", ChanceMachineRecipe.class, -28, -16, 144, 54);
    public static final MekanismJEIRecipeType<ChanceMachineRecipe<?>> CELL_SEPARATING = machine(Recipe.CELL_SEPARATOR, MachineType.CELL_SEPARATOR, "tile.MachineBlock4.CellSeparator.name", ChanceMachineRecipe.class, -28, -16, 144, 54);

    public static final MekanismJEIRecipeType<NucleosynthesizerRecipe> NUCLEOSYNTHESIZING = machine(Recipe.ANTIPROTONIC_NUCLEOSYNTHESIZER,
          MachineType.ANTIPROTONIC_NUCLEOSYNTHESIZER, "tile.MachineBlock3.antiprotonicnucleosynthesizer.name", NucleosynthesizerRecipe.class, -6, -18, 182, 80);

    public static final MekanismJEIRecipeType<ItemStackToEnergyRecipe> ENERGY_CONVERSION = new MekanismJEIRecipeType<>(Recipe.ENERGY_RECIPE.getJEICategory(),
          "jei.mekanism.energy_conversion", ItemStackToEnergyRecipe.class, new ItemStack(mekanism.common.MekanismBlocks.EnergyCube), -20, -12, 132, 62);
    public static final MekanismJEIRecipeType<NutritionalRecipe> NUTRITIONAL_LIQUIFICATION = machine(Recipe.NUTRITIONAL_LIQUIFIER, MachineType.NUTRITIONAL_LIQUIFIER,
          "tile.MachineBlock3.NutritionalLiquifier.name", NutritionalRecipe.class, -20, -12, 132, 62);

    private final ResourceLocation id;
    private final String translationKey;
    private final Class<? extends RECIPE> recipeClass;
    private final ItemStack iconStack;
    private final int xOffset;
    private final int yOffset;
    private final int width;
    private final int height;

    private MekanismJEIRecipeType(String categoryUid, String translationKey, Class<? extends RECIPE> recipeClass, ItemStack iconStack, int xOffset, int yOffset, int width,
          int height) {
        this.id = RecipeViewerRecipeType.idFromCategoryUid(categoryUid);
        this.translationKey = translationKey;
        this.recipeClass = recipeClass;
        this.iconStack = iconStack;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.width = width;
        this.height = height;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <RECIPE> MekanismJEIRecipeType<RECIPE> machine(Recipe recipe, MachineType machineType, String translationKey, Class recipeClass, int xOffset,
          int yOffset, int width, int height) {
        return new MekanismJEIRecipeType<>(recipe.getJEICategory(), translationKey, recipeClass, machineType.getStack(), xOffset, yOffset, width, height);
    }

    @Override
    public ResourceLocation id() {
        return id;
    }

    public String getCategoryUid() {
        return RecipeViewerRecipeType.categoryUid(id);
    }

    public String translationKey() {
        return translationKey;
    }

    public Class<? extends RECIPE> recipeClass() {
        return recipeClass;
    }

    public ItemStack iconStack() {
        return iconStack;
    }

    @Override
    public List<ItemStack> workstations() {
        return Collections.singletonList(iconStack);
    }

    @Override
    public ITextComponent getTextComponent() {
        return new TextComponentTranslation(translationKey);
    }

    public int xOffset() {
        return xOffset;
    }

    public int yOffset() {
        return yOffset;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}
