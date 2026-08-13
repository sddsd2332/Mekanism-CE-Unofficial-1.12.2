package mekanism.qioprocessing.common.planning;

import mekanism.common.TestBootstrap;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfiguration;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfigurationMutation;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfigurationMutation.Action;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationService;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationService.Context;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationService.MutationStatus;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot;
import mekanism.qioprocessing.common.planning.QIOWorkbenchRecipeCatalog.RecipeDefinition;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOWorkbenchConfigurationServiceTest {

    private static Item directFluidContainer;

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
        directFluidContainer = new TestFluidContainerItem();
        directFluidContainer.setRegistryName(new ResourceLocation(
              "test", "qio_configuration_direct_fluid_container"));
    }

    @AfterEach
    void clearCatalog() {
        QIORecipeCatalogService.INSTANCE.clear();
    }

    @Test
    void pagesMutationsAndLastCandidateGuardAreServerAuthoritative() {
        ShapedRecipes alpha = recipe("alpha", new ItemStack(Items.DIAMOND),
              Ingredient.fromStacks(new ItemStack(Items.IRON_INGOT),
                    new ItemStack(Items.GOLD_INGOT)));
        ShapedRecipes beta = recipe("beta", new ItemStack(Items.DIAMOND),
              Ingredient.fromStacks(new ItemStack(Items.REDSTONE)));
        ShapedRecipes gamma = recipe("gamma", new ItemStack(Items.EMERALD),
              Ingredient.fromStacks(new ItemStack(Items.COAL)));
        QIORecipeCatalogService.INSTANCE.observe(QIOWorkbenchRecipeCatalog.capture(null,
              Arrays.asList(alpha, beta, gamma), ignored -> 0, 8, 8, 16));

        UUID owner = UUID.randomUUID();
        UUID frequency = UUID.randomUUID();
        QIOProcessingNetworkData network = network(frequency, owner);
        Context context = QIOWorkbenchConfigurationService.open(session(frequency, owner),
              network, 7, owner);

        QIOWorkbenchConfigurationSnapshot products =
              QIOWorkbenchConfigurationService.products(context, 0, 1, "");
        assertEquals(2, products.getTotalSize());
        assertEquals(1, products.getProducts().size());
        assertEquals(1, QIOWorkbenchConfigurationService.products(context, 0, 8,
              "GAMMA").getTotalSize());

        PortableResourceDescriptor diamond = PortableResourceDescriptor.item(
              new ItemStack(Items.DIAMOND));
        String productKey = QIOWorkbenchRecipeCatalog.productKey(diamond);
        QIOWorkbenchConfigurationSnapshot recipes =
              QIOWorkbenchConfigurationService.recipes(context, productKey, 0, 8);
        assertEquals(2, recipes.getRecipes().size());
        QIOWorkbenchConfigurationSnapshot.Recipe alphaSnapshot = recipes.getRecipes().stream()
              .filter(recipe -> recipe.getRecipeId().equals("test:alpha"))
              .findFirst().orElseThrow(AssertionError::new);
        QIOWorkbenchConfigurationSnapshot candidates =
              QIOWorkbenchConfigurationService.candidates(context, productKey,
                    alphaSnapshot.getRecipeId(), alphaSnapshot.getSignature(), 0, 0, 8);
        assertEquals(2, candidates.getCandidates().size());

        long catalogRevision = context.getCatalogRevision();
        assertEquals(MutationStatus.APPLIED, QIOWorkbenchConfigurationService.mutate(
              context, 0, catalogRevision, QIOWorkbenchConfigurationMutation.moveRecipe(
                    productKey, "test:beta", recipes.getRecipes().stream()
                          .filter(recipe -> recipe.getRecipeId().equals("test:beta"))
                          .findFirst().orElseThrow(AssertionError::new).getSignature(), -1)));
        QIOWorkbenchConfigurationSnapshot reordered =
              QIOWorkbenchConfigurationService.recipes(context, productKey, 0, 8);
        assertEquals("test:beta", reordered.getRecipes().get(0).getRecipeId());
        assertEquals(MutationStatus.REVISION_CONFLICT,
              QIOWorkbenchConfigurationService.mutate(context, 0, catalogRevision,
                    QIOWorkbenchConfigurationMutation.resetProduct(productKey)));
        assertEquals(MutationStatus.CATALOG_CHANGED,
              QIOWorkbenchConfigurationService.mutate(context,
                    network.getWorkbenchConfiguration().getRevision(),
                    catalogRevision + 1,
                    QIOWorkbenchConfigurationMutation.resetProduct(productKey)));

        QIOWorkbenchConfigurationSnapshot currentCandidates =
              QIOWorkbenchConfigurationService.candidates(context, productKey,
                    alphaSnapshot.getRecipeId(), alphaSnapshot.getSignature(), 0, 0, 8);
        String first = currentCandidates.getCandidates().get(0).getCandidateId();
        String second = currentCandidates.getCandidates().get(1).getCandidateId();
        long revision = network.getWorkbenchConfiguration().getRevision();
        assertEquals(MutationStatus.APPLIED, QIOWorkbenchConfigurationService.mutate(
              context, revision, catalogRevision,
              QIOWorkbenchConfigurationMutation.candidate(Action.TOGGLE_CANDIDATE,
                    productKey, alphaSnapshot.getRecipeId(), alphaSnapshot.getSignature(),
                    0, first)));
        assertEquals(MutationStatus.LAST_CANDIDATE,
              QIOWorkbenchConfigurationService.mutate(context,
                    network.getWorkbenchConfiguration().getRevision(), catalogRevision,
                    QIOWorkbenchConfigurationMutation.candidate(Action.TOGGLE_CANDIDATE,
                          productKey, alphaSnapshot.getRecipeId(),
                          alphaSnapshot.getSignature(), 0, second)));
    }

    @Test
    void absoluteMoveAndEquivalentSlotSyncAreServerAuthoritative() {
        Ingredient interchangeable = Ingredient.fromStacks(new ItemStack(Items.IRON_INGOT),
              new ItemStack(Items.GOLD_INGOT));
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(interchangeable);
        ingredients.add(interchangeable);
        ShapedRecipes recipe = new ShapedRecipes("test", 2, 1, ingredients,
              new ItemStack(Items.DIAMOND));
        recipe.setRegistryName(new ResourceLocation("test", "sync_candidates"));
        QIORecipeCatalogService.INSTANCE.observe(QIOWorkbenchRecipeCatalog.capture(null,
              Collections.singletonList(recipe), ignored -> 0, 8, 8, 16));

        UUID owner = UUID.randomUUID();
        UUID frequency = UUID.randomUUID();
        QIOProcessingNetworkData network = network(frequency, owner);
        Context context = QIOWorkbenchConfigurationService.open(session(frequency, owner),
              network, 7, owner);
        PortableResourceDescriptor diamond = PortableResourceDescriptor.item(
              new ItemStack(Items.DIAMOND));
        String productKey = QIOWorkbenchRecipeCatalog.productKey(diamond);
        QIOWorkbenchConfigurationSnapshot.Recipe snapshot =
              QIOWorkbenchConfigurationService.recipes(context, productKey, 0, 8)
                    .getRecipes().get(0);
        QIOWorkbenchConfigurationSnapshot candidates =
              QIOWorkbenchConfigurationService.candidates(context, productKey,
                    snapshot.getRecipeId(), snapshot.getSignature(), 0, 0, 8);
        String moved = candidates.getCandidates().get(1).getCandidateId();
        long revision = network.getWorkbenchConfiguration().getRevision();
        assertEquals(MutationStatus.APPLIED, QIOWorkbenchConfigurationService.mutate(
              context, revision, context.getCatalogRevision(),
              QIOWorkbenchConfigurationMutation.moveCandidateToIndex(productKey,
                    snapshot.getRecipeId(), snapshot.getSignature(), 0, moved, 0)));
        assertEquals(MutationStatus.APPLIED, QIOWorkbenchConfigurationService.mutate(
              context, network.getWorkbenchConfiguration().getRevision(),
              context.getCatalogRevision(),
              QIOWorkbenchConfigurationMutation.syncEquivalentCandidates(productKey,
                    snapshot.getRecipeId(), snapshot.getSignature(), 0)));

        QIOWorkbenchConfigurationSnapshot synced =
              QIOWorkbenchConfigurationService.candidates(context, productKey,
                    snapshot.getRecipeId(), snapshot.getSignature(), 1, 0, 8);
        assertEquals(moved, synced.getCandidates().get(0).getCandidateId());
    }

    @Test
    void recipePageMarksThePreferredDirectFluidCandidate() {
        ItemStack filledContainer = new ItemStack(directFluidContainer);
        IFluidHandlerItem handler = mekanism.common.util.FluidContainerUtils
              .getUnstackedFluidHandlerCapability(filledContainer);
        assertNotNull(handler);
        assertEquals(1_000, handler.fill(new FluidStack(FluidRegistry.WATER, 1_000), true));
        filledContainer = handler.getContainer();
        ShapedRecipes recipe = recipe("direct_water_display", new ItemStack(Items.DIAMOND),
              Ingredient.fromStacks(filledContainer));
        QIORecipeCatalogService.INSTANCE.observe(QIOWorkbenchRecipeCatalog.capture(null,
              Collections.singletonList(recipe), ignored -> 0, 8, 8, 16));
        UUID owner = UUID.randomUUID();
        UUID frequency = UUID.randomUUID();
        Context context = QIOWorkbenchConfigurationService.open(session(frequency, owner),
              network(frequency, owner), 7, owner);
        String productKey = QIOWorkbenchRecipeCatalog.productKey(
              PortableResourceDescriptor.item(new ItemStack(Items.DIAMOND)));

        QIOWorkbenchConfigurationSnapshot.Ingredient ingredient =
              QIOWorkbenchConfigurationService.recipes(context, productKey, 0, 8)
                    .getRecipes().get(0).getIngredients().get(0);
        assertTrue(ingredient.isVirtualFluid());
        assertEquals(PortableResourceDescriptor.Kind.FLUID,
              ingredient.getVirtualFluid().getKind());
        assertEquals(1_000, ingredient.getVirtualFluidAmount());
    }

    @Test
    void tenThousandProductSearchAndPageAssemblyStayUnderFiveSeconds() {
        List<IRecipe> recipes = new ArrayList<>(10_000);
        for (int index = 0; index < 10_000; index++) {
            recipes.add(recipe("bulk_" + index,
                  new ItemStack(Items.DIAMOND, 1, index),
                  Ingredient.fromStacks(new ItemStack(Items.IRON_INGOT))));
        }
        QIORecipeCatalogService.INSTANCE.observe(QIOWorkbenchRecipeCatalog.capture(null,
              recipes, ignored -> 0, 8, 8, 8));
        UUID owner = UUID.randomUUID();
        UUID frequency = UUID.randomUUID();
        Context context = QIOWorkbenchConfigurationService.open(session(frequency, owner),
              network(frequency, owner), 7, owner);

        QIOWorkbenchConfigurationSnapshot page = assertTimeout(Duration.ofSeconds(5), () ->
              QIOWorkbenchConfigurationService.products(context, 0, 32,
                    "bulk_9999"));
        assertEquals(1, page.getTotalSize());
        assertEquals(1, page.getProducts().size());
    }

    @Test
    void deleteProductMutationRemovesAllEncodedRoutesForThatOutput() {
        ShapedRecipes alpha = recipe("delete_alpha", new ItemStack(Items.DIAMOND),
              Ingredient.fromStacks(new ItemStack(Items.IRON_INGOT)));
        ShapedRecipes beta = recipe("delete_beta", new ItemStack(Items.DIAMOND),
              Ingredient.fromStacks(new ItemStack(Items.GOLD_INGOT)));
        ShapedRecipes retained = recipe("delete_retained", new ItemStack(Items.EMERALD),
              Ingredient.fromStacks(new ItemStack(Items.REDSTONE)));
        QIOWorkbenchRecipeCatalog.Snapshot snapshot = QIOWorkbenchRecipeCatalog.capture(null,
              Arrays.asList(alpha, beta, retained), ignored -> 0, 8, 8, 16);
        QIORecipeCatalogService.INSTANCE.observe(snapshot);

        UUID owner = UUID.randomUUID();
        UUID frequency = UUID.randomUUID();
        QIOProcessingNetworkData network = network(frequency, owner);
        QIOWorkbenchConfiguration configuration = network.getWorkbenchConfiguration();
        assertEquals(3, configuration.putEncodedPatternsIfAbsent(Arrays.asList(
              encoded(snapshot.getRecipeDefinition(alpha.getRegistryName()),
                    new ItemStack(Items.IRON_INGOT)),
              encoded(snapshot.getRecipeDefinition(beta.getRegistryName()),
                    new ItemStack(Items.GOLD_INGOT)),
              encoded(snapshot.getRecipeDefinition(retained.getRegistryName()),
                    new ItemStack(Items.REDSTONE)))));
        Context context = QIOWorkbenchConfigurationService.open(session(frequency, owner),
              network, 7, owner);
        String diamondKey = QIOWorkbenchRecipeCatalog.productKey(
              PortableResourceDescriptor.item(new ItemStack(Items.DIAMOND)));

        assertEquals(MutationStatus.APPLIED, QIOWorkbenchConfigurationService.mutate(
              context, configuration.getRevision(), context.getCatalogRevision(),
              QIOWorkbenchConfigurationMutation.deleteProduct(diamondKey)));
        assertEquals(1, configuration.getEncodedPatternCount());
        assertEquals(Items.EMERALD, configuration.getEncodedPatterns().get(0).getOutput()
              .resolveItem().getItem());
    }

    private static QIOWorkbenchConfiguration.EncodedPattern encoded(
          RecipeDefinition definition, ItemStack input) {
        List<ItemStack> grid = new ArrayList<>(9);
        grid.add(input);
        while (grid.size() < 9) grid.add(ItemStack.EMPTY);
        return new QIOWorkbenchConfiguration.EncodedPattern(UUID.randomUUID(),
              definition.getRecipeId(), definition.getSignature(), definition.getOutput(),
              definition.getOutputAmount(), grid);
    }

    private static ShapedRecipes recipe(String path, ItemStack output,
          Ingredient ingredient) {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(ingredient);
        ShapedRecipes recipe = new ShapedRecipes("test", 1, 1, ingredients, output);
        recipe.setRegistryName(new ResourceLocation("test", path));
        return recipe;
    }

    private static QIOProcessingNetworkData network(UUID frequency, UUID owner) {
        return new QIOProcessingNetworkData(frequency,
              new QIOFrequencyIdentitySnapshot("workbench", owner,
                    SecurityMode.PRIVATE));
    }

    private static QIOProcessingTerminalSession session(UUID frequency, UUID owner) {
        return new QIOProcessingTerminalSession(owner,
              QIOProcessingTerminalSession.TargetKind.BLOCK,
              QIOProcessingTerminalType.MANAGEMENT, UUID.randomUUID(), 0,
              frequency, 7);
    }

    private static final class TestFluidContainerItem extends Item {

        private TestFluidContainerItem() {
            setMaxStackSize(1);
        }

        @Override
        public ICapabilityProvider initCapabilities(ItemStack stack,
              @Nullable NBTTagCompound nbt) {
            return new FluidHandlerItemStack(stack, 1_000);
        }

        @Override
        public boolean hasContainerItem(ItemStack stack) {
            return true;
        }

        @Override
        public ItemStack getContainerItem(ItemStack stack) {
            return new ItemStack(this);
        }
    }
}
