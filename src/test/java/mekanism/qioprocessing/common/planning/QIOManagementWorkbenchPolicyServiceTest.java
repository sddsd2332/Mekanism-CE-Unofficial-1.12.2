package mekanism.qioprocessing.common.planning;

import mekanism.common.TestBootstrap;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.policy.QIOPolicyCatalog;
import mekanism.qioprocessing.common.content.policy.QIOPolicyEntrySnapshot;
import mekanism.qioprocessing.common.content.policy.QIOPolicyMutation;
import mekanism.qioprocessing.common.terminal.QIOManagementPolicyService;
import mekanism.qioprocessing.common.terminal.QIOManagementPolicyService.DirectoryPage;
import mekanism.qioprocessing.common.terminal.QIOManagementPolicyService.MutationStatus;
import mekanism.qioprocessing.common.terminal.QIOManagementPolicyService.PageMode;
import mekanism.qioprocessing.common.terminal.QIOManagementPolicyService.WorkbenchFilter;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QIOManagementWorkbenchPolicyServiceTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @AfterEach
    void clearCatalog() {
        QIORecipeCatalogService.INSTANCE.clear();
    }

    @Test
    void workbenchDirectoryUsesLogicalRecipesAndServerSideFilters() {
        ShapedRecipes alpha = recipe("alpha", Ingredient.fromStacks(
              new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT)));
        ShapedRecipes beta = recipe("beta", Ingredient.fromStacks(
              new ItemStack(Items.REDSTONE)));
        QIORecipeCatalogService.INSTANCE.observe(QIOWorkbenchRecipeCatalog.capture(null,
              Arrays.asList(alpha, beta), ignored -> 0, 8, 8, 16));

        UUID frequencyUUID = UUID.randomUUID();
        QIOProcessingNetworkData network = network(frequencyUUID);
        network.setGlobalRoutePolicy(QIOWorkbenchRecipeCatalog.PROVIDER_ID.toString(),
              "test:beta", "test:beta", QIOPolicyCatalog.Toggle.DISABLED, 3L);
        QIOProcessingTerminalSession session = session(frequencyUUID);

        DirectoryPage all = QIOManagementPolicyService.getDirectoryPage(session, network,
              5, null, 8, PageMode.WORKBENCH, WorkbenchFilter.ALL, "", -1);
        assertEquals(2, all.getPage().getTotalSize());
        assertEquals("test:alpha", all.getPage().getEntries().get(0)
              .getRoute().getRecipeKey());

        DirectoryPage disabled = QIOManagementPolicyService.getDirectoryPage(session,
              network, 5, null, 8, PageMode.WORKBENCH,
              WorkbenchFilter.DISABLED, "", -1);
        assertEquals(1, disabled.getPage().getTotalSize());
        assertEquals("test:beta", disabled.getPage().getEntries().get(0)
              .getRoute().getRecipeKey());

        DirectoryPage searched = QIOManagementPolicyService.getDirectoryPage(session,
              network, 5, null, 8, PageMode.WORKBENCH,
              WorkbenchFilter.ALL, "ALPHA", -1);
        assertEquals(1, searched.getPage().getTotalSize());
    }

    @Test
    void staleCatalogCursorAndForgedWorkbenchMutationsAreRejected() {
        ShapedRecipes alpha = recipe("alpha", Ingredient.fromStacks(
              new ItemStack(Items.IRON_INGOT)));
        QIORecipeCatalogService.INSTANCE.observe(QIOWorkbenchRecipeCatalog.capture(null,
              Collections.singletonList(alpha), ignored -> 0, 8, 8, 8));
        UUID frequencyUUID = UUID.randomUUID();
        QIOProcessingNetworkData network = network(frequencyUUID);
        QIOProcessingTerminalSession session = session(frequencyUUID);

        DirectoryPage first = QIOManagementPolicyService.getDirectoryPage(session, network,
              5, null, 1, PageMode.WORKBENCH, WorkbenchFilter.ALL, "", -1);
        assertEquals(MutationStatus.APPLIED, QIOManagementPolicyService.mutate(session,
              network, 5, 0, QIOPolicyMutation.globalRoute(
                    QIOWorkbenchRecipeCatalog.PROVIDER_ID.toString(), "test:alpha",
                    "test:alpha", QIOPolicyCatalog.Toggle.DISABLED, 2L)).getStatus());
        assertEquals(MutationStatus.INVALID_TARGET, QIOManagementPolicyService.mutate(session,
              network, 5, 1, QIOPolicyMutation.globalRoute(
                    QIOWorkbenchRecipeCatalog.PROVIDER_ID.toString(), "test:missing",
                    "test:missing", QIOPolicyCatalog.Toggle.DISABLED, 2L)).getStatus());

        ShapedRecipes beta = recipe("beta", Ingredient.fromStacks(
              new ItemStack(Items.GOLD_INGOT)));
        QIORecipeCatalogService.INSTANCE.observe(QIOWorkbenchRecipeCatalog.capture(null,
              Collections.singletonList(beta), ignored -> 0, 8, 8, 16));
        long oldCatalogRevision = first.getRecipeCatalogRevision();
        assertThrows(IllegalStateException.class, () ->
              QIOManagementPolicyService.getDirectoryPage(session, network, 5,
                    first.getPage().getNextCursor(), 1, PageMode.WORKBENCH,
                    WorkbenchFilter.ALL, "", oldCatalogRevision));
        assertEquals(MutationStatus.APPLIED, QIOManagementPolicyService.mutate(session,
              network, 5, 1, QIOPolicyMutation.globalRoute(
                    QIOWorkbenchRecipeCatalog.PROVIDER_ID.toString(), "test:alpha",
                    "test:alpha", QIOPolicyCatalog.Toggle.INHERIT, null)).getStatus());
    }

    private static ShapedRecipes recipe(String path, Ingredient ingredient) {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(ingredient);
        ShapedRecipes recipe = new ShapedRecipes("test", 1, 1, ingredients,
              new ItemStack(Items.DIAMOND));
        recipe.setRegistryName(new ResourceLocation("test", path));
        return recipe;
    }

    private static QIOProcessingNetworkData network(UUID frequencyUUID) {
        return new QIOProcessingNetworkData(frequencyUUID,
              new QIOFrequencyIdentitySnapshot("workbench", null, SecurityMode.PUBLIC));
    }

    private static QIOProcessingTerminalSession session(UUID frequencyUUID) {
        return new QIOProcessingTerminalSession(UUID.randomUUID(),
              QIOProcessingTerminalSession.TargetKind.BLOCK,
              QIOProcessingTerminalType.MANAGEMENT, UUID.randomUUID(), 0,
              frequencyUUID, 5);
    }
}
