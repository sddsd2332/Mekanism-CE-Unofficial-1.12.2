package mekanism.client.jei;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.api.gear.ModuleData;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.client.jei.machine.FarmMachineRecipeCategory;
import mekanism.client.jei.machine.RecyclerRecipeCategory;
import mekanism.client.recipe_viewer.jei.machine.ChemicalChemicalToChemicalRecipeCategory;
import mekanism.client.recipe_viewer.jei.machine.ChemicalCrystallizerRecipeCategory;
import mekanism.client.recipe_viewer.jei.machine.ChemicalDissolutionRecipeCategory;
import mekanism.client.recipe_viewer.jei.machine.ChemicalToChemicalRecipeCategory;
import mekanism.client.recipe_viewer.jei.machine.CombinerRecipeCategory;
import mekanism.client.recipe_viewer.jei.machine.ElectrolysisRecipeCategory;
import mekanism.client.recipe_viewer.jei.machine.FluidChemicalToChemicalRecipeCategory;
import mekanism.client.recipe_viewer.jei.machine.FluidToFluidRecipeCategory;
import mekanism.client.recipe_viewer.jei.machine.ItemStackChemicalToItemStackRecipeCategory;
import mekanism.client.recipe_viewer.jei.machine.ItemStackToChemicalRecipeCategory;
import mekanism.client.recipe_viewer.jei.machine.ItemStackToEnergyRecipeCategory;
import mekanism.client.recipe_viewer.jei.machine.ItemStackToFluidOptionalItemRecipeCategory;
import mekanism.client.recipe_viewer.jei.machine.ItemStackToItemStackRecipeCategory;
import mekanism.client.recipe_viewer.jei.machine.MetallurgicInfuserRecipeCategory;
import mekanism.client.recipe_viewer.jei.machine.NucleosynthesizingRecipeCategory;
import mekanism.client.recipe_viewer.jei.machine.PressurizedReactionRecipeCategory;
import mekanism.client.recipe_viewer.jei.machine.RotaryCondensentratorRecipeCategory;
import mekanism.client.recipe_viewer.jei.machine.SPSRecipeCategory;
import mekanism.client.recipe_viewer.jei.machine.SawmillRecipeCategory;
import mekanism.client.jei.machine.other.AmbientGasCategory;
import mekanism.common.Mekanism;
import mekanism.common.MekanismBlocks;
import mekanism.common.MekanismItems;
import mekanism.common.base.IFactory;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.base.ITierItem;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.ModuleHelper;
import mekanism.common.inventory.container.robit.ContainerRobitInventory;
import mekanism.common.inventory.container.ContainerQIODashboard;
import mekanism.common.inventory.container.PortableQIODashboardContainer;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.util.LangUtils;
import mekanism.common.util.StorageUtils;
import mezz.jei.api.*;
import mezz.jei.api.ISubtypeRegistry.ISubtypeInterpreter;
import mezz.jei.api.ingredients.IIngredientBlacklist;
import mezz.jei.api.ingredients.IModIngredientRegistration;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.api.recipe.IRecipeCategoryRegistration;
import mezz.jei.api.recipe.IVanillaRecipeFactory;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistryModifiable;

import java.util.*;
import java.lang.reflect.Method;
import java.util.stream.Collectors;

@JEIPlugin
public class MekanismJEI implements IModPlugin {

    public static final IIngredientType<GasStack> TYPE_GAS = () -> GasStack.class;
    public static final IIngredientType<GasStack> TYPE_CHEMICAL = TYPE_GAS;
    public static final mekanism.client.recipe_viewer.jei.ChemicalStackHelper CHEMICAL_STACK_HELPER = new mekanism.client.recipe_viewer.jei.ChemicalStackHelper();
    public static final GasStackHelper GAS_STACK_HELPER = CHEMICAL_STACK_HELPER;
    public static IJeiRuntime jeiRuntime;
    private static Method recipeGuiRefreshMethod;
    private static boolean recipeGuiRefreshUnavailable;
    private static final String NC_MOD_ID = "nuclearcraft";
    private static final String NC_SHIELDING_RECIPE_CLASS = "nc.recipe.vanilla.recipe.ShapelessArmorRadShieldingRecipe";
    private static final String NC_RAD_SHIELDING_ITEM = "rad_shielding";
    private static final List<IRecipe> NC_SHIELDING_RECIPES_FOR_JEI = new ArrayList<>();

    public static String genericRecipeType(IRecipeViewerRecipeType<?> recipeType) {
        return mekanism.client.recipe_viewer.jei.MekanismJEI.genericRecipeType(recipeType);
    }

    public static <TYPE> String recipeType(IRecipeViewerRecipeType<TYPE> recipeType) {
        return mekanism.client.recipe_viewer.jei.MekanismJEI.recipeType(recipeType);
    }

    public static <TYPE> String holderRecipeType(IRecipeViewerRecipeType<TYPE> recipeType) {
        return mekanism.client.recipe_viewer.jei.MekanismJEI.holderRecipeType(recipeType);
    }

    public static String[] recipeType(IRecipeViewerRecipeType<?>... recipeTypes) {
        return mekanism.client.recipe_viewer.jei.MekanismJEI.recipeType(recipeTypes);
    }

    public static final ISubtypeInterpreter NBT_INTERPRETER = itemStack -> {
        String ret = Integer.toString(itemStack.getMetadata());

        if (itemStack.getItem() instanceof ITierItem) {
            ret += ":" + ((ITierItem) itemStack.getItem()).getBaseTier(itemStack).getSimpleName();
        }

        if (itemStack.getItem() instanceof IFactory) {
            RecipeType recipeType = ((IFactory) itemStack.getItem()).getRecipeTypeOrNull(itemStack);
            if (recipeType != null) {
                ret += ":" + recipeType.getName();
            }
        }

        if (itemStack.getItem() == Item.getItemFromBlock(MekanismBlocks.GasTank)) {
            GasStack gasStack = GasInventorySlot.getContainedGas(itemStack);
            if (gasStack != null) {
                ret += ":" + gasStack.getGas().getName();
            }
        }

        if (itemStack.getItem() == Item.getItemFromBlock(MekanismBlocks.EnergyCube)) {
            ret += ":" + (StorageUtils.getStoredEnergy(itemStack) > 0 ? "filled" : "empty");
        }

        return ret.toLowerCase(Locale.ROOT);
    };

    @Override
    public void registerItemSubtypes(ISubtypeRegistry registry) {
        registry.registerSubtypeInterpreter(Item.getItemFromBlock(MekanismBlocks.EnergyCube), NBT_INTERPRETER);
        registry.registerSubtypeInterpreter(Item.getItemFromBlock(MekanismBlocks.MachineBlock), NBT_INTERPRETER);
        registry.registerSubtypeInterpreter(Item.getItemFromBlock(MekanismBlocks.MachineBlock2), NBT_INTERPRETER);
        registry.registerSubtypeInterpreter(Item.getItemFromBlock(MekanismBlocks.MachineBlock3), NBT_INTERPRETER);
        registry.registerSubtypeInterpreter(Item.getItemFromBlock(MekanismBlocks.BasicBlock), NBT_INTERPRETER);
        registry.registerSubtypeInterpreter(Item.getItemFromBlock(MekanismBlocks.BasicBlock2), NBT_INTERPRETER);
        registry.registerSubtypeInterpreter(Item.getItemFromBlock(MekanismBlocks.GasTank), NBT_INTERPRETER);
        registry.registerSubtypeInterpreter(Item.getItemFromBlock(MekanismBlocks.CardboardBox), NBT_INTERPRETER);
        registry.registerSubtypeInterpreter(Item.getItemFromBlock(MekanismBlocks.Transmitter), NBT_INTERPRETER);

        registry.registerSubtypeInterpreter(Item.getItemFromBlock(MekanismBlocks.BasicBlock3), NBT_INTERPRETER);
        registry.registerSubtypeInterpreter(Item.getItemFromBlock(MekanismBlocks.MachineBlock4), NBT_INTERPRETER);
    }

    @Override
    public void registerIngredients(IModIngredientRegistration registry) {
        List<GasStack> list = GasRegistry.getRegisteredGasses().stream().filter(Gas::isVisible).map(g -> new GasStack(g, Fluid.BUCKET_VOLUME)).collect(Collectors.toList());
        registry.register(MekanismJEI.TYPE_CHEMICAL, list, CHEMICAL_STACK_HELPER, new mekanism.client.recipe_viewer.jei.ChemicalStackRenderer());
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registry) {
        IGuiHelper guiHelper = registry.getJeiHelpers().getGuiHelper();

        addRecipeCategory(registry, MachineType.CHEMICAL_CRYSTALLIZER, new ChemicalCrystallizerRecipeCategory(guiHelper, RecipeViewerRecipeType.CRYSTALLIZING));
        addRecipeCategory(registry, MachineType.CHEMICAL_DISSOLUTION_CHAMBER, new ChemicalDissolutionRecipeCategory(guiHelper, RecipeViewerRecipeType.DISSOLUTION));
        addRecipeCategory(registry, MachineType.CHEMICAL_INFUSER, new ChemicalChemicalToChemicalRecipeCategory(guiHelper, RecipeViewerRecipeType.CHEMICAL_INFUSING));
        addRecipeCategory(registry, MachineType.CHEMICAL_OXIDIZER, new ItemStackToChemicalRecipeCategory(guiHelper, RecipeViewerRecipeType.OXIDIZING, false));
        addRecipeCategory(registry, MachineType.CHEMICAL_WASHER, new FluidChemicalToChemicalRecipeCategory(guiHelper, RecipeViewerRecipeType.WASHING));
        addRecipeCategory(registry, MachineType.ELECTROLYTIC_SEPARATOR, new ElectrolysisRecipeCategory(guiHelper, RecipeViewerRecipeType.SEPARATING));
        addRecipeCategory(registry, MachineType.METALLURGIC_INFUSER, new MetallurgicInfuserRecipeCategory(guiHelper, RecipeViewerRecipeType.METALLURGIC_INFUSING));
        addRecipeCategory(registry, MachineType.PRESSURIZED_REACTION_CHAMBER, new PressurizedReactionRecipeCategory(guiHelper, RecipeViewerRecipeType.REACTION));

        addRecipeCategory(registry, MachineType.ROTARY_CONDENSENTRATOR, new RotaryCondensentratorRecipeCategory(guiHelper, true));
        addRecipeCategory(registry, MachineType.ROTARY_CONDENSENTRATOR, new RotaryCondensentratorRecipeCategory(guiHelper, false));

        addRecipeCategory(registry, MachineType.SOLAR_NEUTRON_ACTIVATOR, new ChemicalToChemicalRecipeCategory(guiHelper, RecipeViewerRecipeType.ACTIVATING));

        addRecipeCategory(registry, MachineType.COMBINER, new CombinerRecipeCategory(guiHelper, RecipeViewerRecipeType.COMBINING));

        addRecipeCategory(registry, MachineType.PURIFICATION_CHAMBER, new ItemStackChemicalToItemStackRecipeCategory(guiHelper, RecipeViewerRecipeType.PURIFYING));
        addRecipeCategory(registry, MachineType.OSMIUM_COMPRESSOR, new ItemStackChemicalToItemStackRecipeCategory(guiHelper, RecipeViewerRecipeType.COMPRESSING));
        addRecipeCategory(registry, MachineType.CHEMICAL_INJECTION_CHAMBER, new ItemStackChemicalToItemStackRecipeCategory(guiHelper, RecipeViewerRecipeType.INJECTING));

        addRecipeCategory(registry, MachineType.PRECISION_SAWMILL, new SawmillRecipeCategory(guiHelper, RecipeViewerRecipeType.SAWING));

        addRecipeCategory(registry, MachineType.ENRICHMENT_CHAMBER, new ItemStackToItemStackRecipeCategory(guiHelper, RecipeViewerRecipeType.ENRICHING));
        addRecipeCategory(registry, MachineType.CRUSHER, new ItemStackToItemStackRecipeCategory(guiHelper, RecipeViewerRecipeType.CRUSHING));
        addRecipeCategory(registry, MachineType.ENERGIZED_SMELTER, new ItemStackToItemStackRecipeCategory(guiHelper, RecipeViewerRecipeType.SMELTING));

        //There is no config option to disable the thermal evaporation plant
        registry.addRecipeCategories(new FluidToFluidRecipeCategory(guiHelper, RecipeViewerRecipeType.EVAPORATING));

        /**
         * ADD START
         */
        addRecipeCategory(registry, MachineType.ISOTOPIC_CENTRIFUGE, new ChemicalToChemicalRecipeCategory(guiHelper, RecipeViewerRecipeType.CENTRIFUGING));
        addRecipeCategory(registry, MachineType.NUTRITIONAL_LIQUIFIER, new ItemStackToFluidOptionalItemRecipeCategory(guiHelper, RecipeViewerRecipeType.NUTRITIONAL_LIQUIFICATION, false));
        addRecipeCategory(registry, MachineType.ORGANIC_FARM, new FarmMachineRecipeCategory(guiHelper, Recipe.ORGANIC_FARM.getJEICategory(), "tile.MachineBlock3.OrganicFarm.name", ProgressType.BAR));
        addRecipeCategory(registry, MachineType.ANTIPROTONIC_NUCLEOSYNTHESIZER, new NucleosynthesizingRecipeCategory(guiHelper, RecipeViewerRecipeType.NUCLEOSYNTHESIZING));
        addRecipeCategory(registry, MachineType.STAMPING, new ItemStackToItemStackRecipeCategory(guiHelper, RecipeViewerRecipeType.STAMPING));
        addRecipeCategory(registry, MachineType.ROLLING, new ItemStackToItemStackRecipeCategory(guiHelper, RecipeViewerRecipeType.ROLLING));
        addRecipeCategory(registry, MachineType.BRUSHED, new ItemStackToItemStackRecipeCategory(guiHelper, RecipeViewerRecipeType.BRUSHED));
        addRecipeCategory(registry, MachineType.TURNING, new ItemStackToItemStackRecipeCategory(guiHelper, RecipeViewerRecipeType.TURNING));
        addRecipeCategory(registry, MachineType.ALLOY, new CombinerRecipeCategory(guiHelper, RecipeViewerRecipeType.ALLOYING));
        addRecipeCategory(registry, MachineType.CELL_EXTRACTOR, new SawmillRecipeCategory(guiHelper, RecipeViewerRecipeType.CELL_EXTRACTING));
        addRecipeCategory(registry, MachineType.CELL_SEPARATOR, new SawmillRecipeCategory(guiHelper, RecipeViewerRecipeType.CELL_SEPARATING));
        if (MekanismConfig.current().mekce.EnableRecyclerRecipeInJei.val()) {
            addRecipeCategory(registry, MachineType.RECYCLER, new RecyclerRecipeCategory(guiHelper));
        }
        addRecipeCategory(registry, MachineType.AMBIENT_ACCUMULATOR, new AmbientGasCategory(guiHelper));
        addRecipeCategory(registry, MachineType.SPS, new SPSRecipeCategory(guiHelper, RecipeViewerRecipeType.SPS));
        registry.addRecipeCategories(new ItemStackToEnergyRecipeCategory<>(guiHelper, RecipeViewerRecipeType.ENERGY_CONVERSION));
        /**
         * ADD END
         */


    }

    private void addRecipeCategory(IRecipeCategoryRegistration registry, MachineType type, BaseRecipeCategory category) {
        if (type.isEnabled()) {
            registry.addRecipeCategories(category);
        }
    }

    @Override
    public void register(IModRegistry registry) {
        registry.addAdvancedGuiHandlers(new mekanism.client.recipe_viewer.jei.JeiGuiElementHandler());
        registry.addGhostIngredientHandler(GuiMekanism.class, new mekanism.client.recipe_viewer.jei.JeiGhostIngredientHandler<>());

        //Blacklist
        IIngredientBlacklist ingredientBlacklist = registry.getJeiHelpers().getIngredientBlacklist();
        ingredientBlacklist.addIngredientToBlacklist(new ItemStack(MekanismItems.ItemProxy));
        ingredientBlacklist.addIngredientToBlacklist(new ItemStack(MekanismBlocks.BoundingBlock));

        if (Mekanism.hooks.NuclearCraft) {
            cacheAndRemoveNuclearCraftShieldingRecipes();
        }

        //Register the recipes and their catalysts if enabled
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerEnrichmentChamber(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerCrusher(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerCombiner(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerPurification(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerCompressor(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerInjection(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerSawmill(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerMetallurgicInfuser(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerCrystallizer(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerDissolution(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerChemicalInfuser(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerOxidizer(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerWasher(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerNeutronActivator(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerSeparator(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerEvaporationPlant(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerReactionChamber(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerCondensentrator(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerSmelter(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerFormulaicAssemblicator(registry);
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(ContainerRobitInventory.class, VanillaRecipeCategoryUid.CRAFTING, 1, 9, 10, 36);
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(new QIOCraftingTransferHandler<>(ContainerQIODashboard.class,
              registry.getJeiHelpers().recipeTransferHandlerHelper(), registry.getJeiHelpers().getStackHelper()), VanillaRecipeCategoryUid.CRAFTING);
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(new QIOCraftingTransferHandler<>(PortableQIODashboardContainer.class,
              registry.getJeiHelpers().recipeTransferHandlerHelper(), registry.getJeiHelpers().getStackHelper()), VanillaRecipeCategoryUid.CRAFTING);

        /**
         *  ADD START
         */
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerIsotopicCentrifuge(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerNutritional(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerFarm(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerAntiprotonicNucleosynthesizer(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerStamping(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerRolling(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerBrushed(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerTurning(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerAlloy(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerCellExtractor(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerCellSeparator(registry);
        if (MekanismConfig.current().mekce.EnableRecyclerRecipeInJei.val()) {
            mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerRecycler(registry);
        }
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerAmbientAccumulator(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerSPS(registry);
        mekanism.client.recipe_viewer.jei.RecipeRegistryHelper.registerItemStackToEnergyRecipe(registry);

        if (Mekanism.hooks.MekanismMixinHelp) {
            IVanillaRecipeFactory factory = registry.getJeiHelpers().getVanillaRecipeFactory();
            Map<ItemStack, List<ItemStack>> items = Maps.newHashMap();
            ItemStack hdpe = new ItemStack(MekanismItems.HDPE_SHEET);
            items.put(hdpe, Lists.newArrayList(new ItemStack(MekanismItems.HDPE_REINFORCED_ELYTRA)));
            items.forEach((repairMaterial, value) -> {
                value.forEach(ingredient -> {
                    ItemStack damaged1 = ingredient.copy();
                    damaged1.setItemDamage(damaged1.getMaxDamage());
                    ItemStack damaged2 = ingredient.copy();
                    damaged2.setItemDamage(damaged2.getMaxDamage() * 3 / 4);
                    ItemStack damaged3 = ingredient.copy();
                    damaged3.setItemDamage(247);
                    registry.addRecipes(ImmutableList.of(factory.createAnvilRecipe(damaged1, Collections.singletonList(repairMaterial), Collections.singletonList(damaged2))), VanillaRecipeCategoryUid.ANVIL);
                    registry.addRecipes(ImmutableList.of(factory.createAnvilRecipe(damaged2, Collections.singletonList(damaged2), Collections.singletonList(damaged3))), VanillaRecipeCategoryUid.ANVIL);
                });
            });
        }
        /**
         * ADD END
         */

        registry.addIngredientInfo(
                ModuleHelper.INSTANCE.getAll().stream()
                        .filter(Objects::nonNull)
                        .map(ModuleData::getStack)
                        .filter(stack -> stack != null && !stack.isEmpty() && stack.getItem().getRegistryName() != null && !stack.getItem().equals(MekanismItems.ModuleBase))
                        .collect(Collectors.toList())
                , VanillaTypes.ITEM, LangUtils.localize("mekanism.module.info"));
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        MekanismJEI.jeiRuntime = jeiRuntime;
        QIORecipeViewerGuiHandler.register();
        recipeGuiRefreshMethod = null;
        recipeGuiRefreshUnavailable = false;
        if (!Mekanism.hooks.NuclearCraft || jeiRuntime == null || NC_SHIELDING_RECIPES_FOR_JEI.isEmpty()) {
            return;
        }
        IRecipeRegistry recipeRegistry = jeiRuntime.getRecipeRegistry();
        int removed = 0;
        for (IRecipe recipe : NC_SHIELDING_RECIPES_FOR_JEI) {
            recipeRegistry.removeRecipe(recipe);
            removed++;
        }
        if (removed > 0) {
            Mekanism.logger.info("Removed {} NC shielding recipes for Mek armor from JEI runtime.", removed);
        }
        NC_SHIELDING_RECIPES_FOR_JEI.clear();
    }

    /** Rebuilds transfer buttons after the non-slot QIO inventory snapshot changes. */
    public static void refreshRecipeTransferButtons() {
        IJeiRuntime runtime = jeiRuntime;
        if (runtime == null || recipeGuiRefreshUnavailable) {
            return;
        }
        IRecipesGui recipesGui = runtime.getRecipesGui();
        if (recipesGui == null || Minecraft.getMinecraft().currentScreen != recipesGui) {
            return;
        }
        try {
            if (recipeGuiRefreshMethod == null) {
                // JEI/HEI 4.x exposes this on the runtime GUI implementation,
                // but not through IRecipesGui's public API.
                recipeGuiRefreshMethod = recipesGui.getClass().getMethod("onStateChange");
            }
            recipeGuiRefreshMethod.invoke(recipesGui);
        } catch (ReflectiveOperationException | SecurityException ex) {
            recipeGuiRefreshUnavailable = true;
            Mekanism.logger.warn("Unable to refresh QIO recipe transfer availability for {}",
                  recipesGui.getClass().getName(), ex);
        }
    }

    private static void cacheAndRemoveNuclearCraftShieldingRecipes() {
        NC_SHIELDING_RECIPES_FOR_JEI.clear();
        NC_SHIELDING_RECIPES_FOR_JEI.addAll(findNuclearCraftShieldingRecipes());
        int removed = removeNuclearCraftShieldingRecipesFromForgeRegistry(NC_SHIELDING_RECIPES_FOR_JEI);
        if (removed > 0) {
            Mekanism.logger.info("Removed {} NC shielding recipes for Mek armor during JEI registration.", removed);
        }
    }

    private static List<IRecipe> findNuclearCraftShieldingRecipes() {
        List<IRecipe> matching = new ArrayList<>();
        for (IRecipe recipe : ForgeRegistries.RECIPES.getValuesCollection()) {
            if (isNuclearCraftShieldingRecipeForBlockedMekArmor(recipe)) {
                matching.add(recipe);
            }
        }
        return matching;
    }

    private static int removeNuclearCraftShieldingRecipesFromForgeRegistry(List<IRecipe> recipes) {
        if (!(ForgeRegistries.RECIPES instanceof IForgeRegistryModifiable)) {
            return 0;
        }
        @SuppressWarnings("unchecked")
        IForgeRegistryModifiable<IRecipe> recipeRegistry = (IForgeRegistryModifiable<IRecipe>) ForgeRegistries.RECIPES;
        int removed = 0;
        Set<ResourceLocation> removedNames = new HashSet<>();
        for (IRecipe recipe : recipes) {
            ResourceLocation recipeName = recipe.getRegistryName();
            if (recipeName != null && removedNames.add(recipeName)) {
                recipeRegistry.remove(recipeName);
                removed++;
            }
        }
        return removed;
    }

    private static boolean isNuclearCraftShieldingRecipeForBlockedMekArmor(IRecipe recipe) {
        if (recipe == null) {
            return false;
        }
        ItemStack output = recipe.getRecipeOutput();
        if (output.isEmpty() || !isMekArmorBlockedForNCShielding(output.getItem())) {
            return false;
        }
        if (NC_SHIELDING_RECIPE_CLASS.equals(recipe.getClass().getName())) {
            return true;
        }
        ResourceLocation recipeName = recipe.getRegistryName();
        if (recipeName == null || !NC_MOD_ID.equals(recipeName.getNamespace())) {
            return false;
        }
        return containsNuclearCraftRadShieldingIngredient(recipe) || recipe.getClass().getName().contains("RadShielding");
    }

    private static boolean containsNuclearCraftRadShieldingIngredient(IRecipe recipe) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient == null || ingredient == Ingredient.EMPTY) {
                continue;
            }
            for (ItemStack stack : ingredient.getMatchingStacks()) {
                if (stack.isEmpty()) {
                    continue;
                }
                ResourceLocation itemName = stack.getItem().getRegistryName();
                if (itemName != null && NC_MOD_ID.equals(itemName.getNamespace()) && NC_RAD_SHIELDING_ITEM.equals(itemName.getPath())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isMekArmorBlockedForNCShielding(Item item) {
        return item == MekanismItems.HAZMAT_MASK || item == MekanismItems.HAZMAT_GOWN || item == MekanismItems.HAZMAT_PANTS || item == MekanismItems.HAZMAT_BOOTS ||
                item == MekanismItems.MEKASUIT_HELMET || item == MekanismItems.MEKASUIT_BODYARMOR || item == MekanismItems.MEKASUIT_PANTS || item == MekanismItems.MEKASUIT_BOOTS;
    }
}
