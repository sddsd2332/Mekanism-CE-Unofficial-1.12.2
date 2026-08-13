package mekanism.qioprocessing.common.content.profile;

import mekanism.api.processing.MachineRecipeRoute;
import mekanism.api.processing.MachineResourceStack;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.common.TestBootstrap;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfile.RouteFilterMode;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOAutomationRecipeProfileCatalogTest {

    private static final String PROVIDER = "test:profile_provider";

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void scheduledAndPassiveDefaultsMatchAeSemantics() {
        QIOAutomationRecipeProfileCatalog catalog = new QIOAutomationRecipeProfileCatalog();
        UUID device = UUID.randomUUID();
        QIOAutomationRecipeProfileLayout layout = layout();
        String route = layout.getDefaultRouteOrder(layout.getDefaultProductOrder().get(0)).get(0);

        assertEquals(RouteFilterMode.BLACKLIST, catalog.getSelection(device,
              QIOAutomationMode.SCHEDULED, PROVIDER).getFilterMode());
        assertTrue(catalog.getActiveProfile(device, QIOAutomationMode.SCHEDULED,
              PROVIDER).isRouteEnabled(route));
        assertEquals(RouteFilterMode.WHITELIST, catalog.getSelection(device,
              QIOAutomationMode.PASSIVE, PROVIDER).getFilterMode());
        assertFalse(catalog.getActiveProfile(device, QIOAutomationMode.PASSIVE,
              PROVIDER).isRouteEnabled(route));
        assertFalse(catalog.toggleRouteFilterMode(device, QIOAutomationMode.PASSIVE,
              PROVIDER));
    }

    @Test
    void globalSlotsWrapAndRemainIndependent() {
        QIOAutomationRecipeProfileCatalog catalog = new QIOAutomationRecipeProfileCatalog();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        QIOAutomationRecipeProfileLayout layout = layout();
        String route = layout.getDefaultRouteOrder(layout.getDefaultProductOrder().get(0)).get(0);

        assertTrue(catalog.toggleRoute(first, QIOAutomationMode.SCHEDULED, PROVIDER,
              layout, route));
        assertFalse(catalog.getActiveProfile(second, QIOAutomationMode.SCHEDULED,
              PROVIDER).isRouteEnabled(route));
        assertTrue(catalog.cycleGlobalSlot(first, QIOAutomationMode.SCHEDULED,
              PROVIDER, 1));
        assertEquals(2, catalog.getSelection(first, QIOAutomationMode.SCHEDULED,
              PROVIDER).getGlobalSlot());
        assertTrue(catalog.getActiveProfile(first, QIOAutomationMode.SCHEDULED,
              PROVIDER).isRouteEnabled(route));
        for (int index = 0; index < 9; index++) {
            assertTrue(catalog.cycleGlobalSlot(first, QIOAutomationMode.SCHEDULED,
                  PROVIDER, 1));
        }
        assertEquals(1, catalog.getSelection(first, QIOAutomationMode.SCHEDULED,
              PROVIDER).getGlobalSlot());
        assertFalse(catalog.getActiveProfile(first, QIOAutomationMode.SCHEDULED,
              PROVIDER).isRouteEnabled(route));
    }

    @Test
    void individualProfileCopiesCurrentGlobalOnce() {
        QIOAutomationRecipeProfileCatalog catalog = new QIOAutomationRecipeProfileCatalog();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        QIOAutomationRecipeProfileLayout layout = layout();
        String route = layout.getDefaultRouteOrder(layout.getDefaultProductOrder().get(0)).get(0);

        catalog.toggleRoute(first, QIOAutomationMode.SCHEDULED, PROVIDER, layout, route);
        assertTrue(catalog.toggleProfileMode(first, QIOAutomationMode.SCHEDULED, PROVIDER));
        assertTrue(catalog.getSelection(first, QIOAutomationMode.SCHEDULED,
              PROVIDER).isIndividual());
        assertFalse(catalog.getActiveProfile(first, QIOAutomationMode.SCHEDULED,
              PROVIDER).isRouteEnabled(route));

        catalog.toggleRoute(first, QIOAutomationMode.SCHEDULED, PROVIDER, layout, route);
        assertTrue(catalog.getActiveProfile(first, QIOAutomationMode.SCHEDULED,
              PROVIDER).isRouteEnabled(route));
        assertFalse(catalog.getActiveProfile(second, QIOAutomationMode.SCHEDULED,
              PROVIDER).isRouteEnabled(route));

        catalog.toggleProfileMode(first, QIOAutomationMode.SCHEDULED, PROVIDER);
        catalog.toggleProfileMode(first, QIOAutomationMode.SCHEDULED, PROVIDER);
        assertTrue(catalog.getActiveProfile(first, QIOAutomationMode.SCHEDULED,
              PROVIDER).isRouteEnabled(route));
    }

    @Test
    void blacklistAndWhitelistProfilesAreSeparate() {
        QIOAutomationRecipeProfileCatalog catalog = new QIOAutomationRecipeProfileCatalog();
        UUID device = UUID.randomUUID();
        QIOAutomationRecipeProfileLayout layout = layout();
        String route = layout.getDefaultRouteOrder(layout.getDefaultProductOrder().get(0)).get(0);

        catalog.toggleRoute(device, QIOAutomationMode.SCHEDULED, PROVIDER, layout, route);
        assertFalse(catalog.getActiveProfile(device, QIOAutomationMode.SCHEDULED,
              PROVIDER).isRouteEnabled(route));
        assertTrue(catalog.toggleRouteFilterMode(device, QIOAutomationMode.SCHEDULED,
              PROVIDER));
        assertEquals(RouteFilterMode.WHITELIST, catalog.getSelection(device,
              QIOAutomationMode.SCHEDULED, PROVIDER).getFilterMode());
        assertFalse(catalog.getActiveProfile(device, QIOAutomationMode.SCHEDULED,
              PROVIDER).isRouteEnabled(route));
        catalog.toggleRoute(device, QIOAutomationMode.SCHEDULED, PROVIDER, layout, route);
        assertTrue(catalog.getActiveProfile(device, QIOAutomationMode.SCHEDULED,
              PROVIDER).isRouteEnabled(route));
        catalog.toggleRouteFilterMode(device, QIOAutomationMode.SCHEDULED, PROVIDER);
        assertFalse(catalog.getActiveProfile(device, QIOAutomationMode.SCHEDULED,
              PROVIDER).isRouteEnabled(route));
    }

    @Test
    void orderingAmountsResetAndRoundTripRemainStable() throws Exception {
        QIOAutomationRecipeProfileCatalog catalog = new QIOAutomationRecipeProfileCatalog();
        UUID device = UUID.randomUUID();
        QIOAutomationRecipeProfileLayout layout = layout();
        String firstProduct = layout.getDefaultProductOrder().stream()
              .filter(product -> layout.getDefaultRouteOrder(product).size() > 1)
              .findFirst().orElseThrow(AssertionError::new);
        List<String> firstRoutes = layout.getDefaultRouteOrder(firstProduct);
        String firstRoute = firstRoutes.get(0);

        assertTrue(catalog.moveProduct(device, QIOAutomationMode.SCHEDULED, PROVIDER,
              layout, firstProduct, -1, false, false));
        assertTrue(catalog.moveRoute(device, QIOAutomationMode.SCHEDULED, PROVIDER,
              layout, firstProduct, firstRoute, 0, true, false));
        assertTrue(catalog.setCraftAmount(device, QIOAutomationMode.SCHEDULED,
              PROVIDER, layout, Long.MAX_VALUE));
        assertTrue(catalog.setRouteCraftAmount(device, QIOAutomationMode.SCHEDULED,
              PROVIDER, layout, firstRoute, 2));

        QIOAutomationRecipeProfileCatalog restored =
              QIOAutomationRecipeProfileCatalog.read(catalog.write());
        QIOAutomationRecipeProfile restoredProfile = restored.getActiveProfile(device,
              QIOAutomationMode.SCHEDULED, PROVIDER);
        assertEquals(firstProduct, restoredProfile.getCurrentProductOrder(
              layout.getDefaultProductOrder()).get(0));
        assertEquals(firstRoutes.get(firstRoutes.size() - 1), restoredProfile
              .getCurrentRouteOrder(firstProduct, firstRoutes).get(0));
        assertEquals(layout.getMaximumCraftAmount(), restoredProfile.getCraftAmount());
        assertEquals(2, restoredProfile.getEffectiveCraftAmount(firstRoute,
              layout.getRoute(firstRoute).getMaximumCraftAmount()));

        assertTrue(restored.resetProduct(device, QIOAutomationMode.SCHEDULED, PROVIDER,
              layout, firstProduct));
        assertTrue(restored.resetAll(device, QIOAutomationMode.SCHEDULED, PROVIDER));
        assertTrue(restored.getActiveProfile(device, QIOAutomationMode.SCHEDULED,
              PROVIDER).isEmpty());
    }

    @Test
    void profileCatalogPersistsAsRequiredNetworkSchemaState() throws Exception {
        UUID frequency = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        QIOAutomationRecipeProfileLayout layout = layout();
        String route = layout.getDefaultRouteOrder(layout.getDefaultProductOrder().get(0)).get(0);
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(frequency,
              new QIOFrequencyIdentitySnapshot("profiles", null,
                    mekanism.common.security.ISecurityTile.SecurityMode.PUBLIC));
        long before = network.getAutomationRecipeProfiles().getRevision();
        assertTrue(network.getAutomationRecipeProfiles().toggleRoute(device,
              QIOAutomationMode.SCHEDULED, PROVIDER, layout, route));
        assertTrue(network.markAutomationRecipeProfilesChanged(before));

        QIOProcessingNetworkData restored = QIOProcessingNetworkData.read(network.write(),
              frequency);
        assertFalse(restored.getAutomationRecipeProfiles().getActiveProfile(device,
              QIOAutomationMode.SCHEDULED, PROVIDER).isRouteEnabled(route));

        NBTTagCompound missing = network.write();
        missing.removeTag("automationRecipeProfiles");
        assertThrows(QIOProcessingDataException.class, () ->
              QIOProcessingNetworkData.read(missing, frequency));
    }

    @Test
    void configurationCardTransferRoundTripsGlobalSelectionAndProfile() throws Exception {
        QIOAutomationRecipeProfileLayout layout = layout();
        String route = layout.getDefaultRouteOrder(
              layout.getDefaultProductOrder().get(0)).get(0);
        UUID sourceDevice = UUID.randomUUID();
        QIOAutomationRecipeProfileCatalog source =
              new QIOAutomationRecipeProfileCatalog();
        source.cycleGlobalSlot(sourceDevice, QIOAutomationMode.SCHEDULED, PROVIDER, 1);
        source.toggleRouteFilterMode(sourceDevice, QIOAutomationMode.SCHEDULED, PROVIDER);
        source.toggleRoute(sourceDevice, QIOAutomationMode.SCHEDULED, PROVIDER, layout, route);
        source.setCraftAmount(sourceDevice, QIOAutomationMode.SCHEDULED, PROVIDER, layout, 2);

        QIOAutomationRecipeProfileTransfer transfer =
              QIOAutomationRecipeProfileTransfer.read(
                    QIOAutomationRecipeProfileTransfer.capture(source, sourceDevice,
                          QIOAutomationMode.SCHEDULED, PROVIDER).write());
        UUID targetDevice = UUID.randomUUID();
        QIOAutomationRecipeProfileCatalog target =
              new QIOAutomationRecipeProfileCatalog();
        assertFalse(transfer.applyTo(target, targetDevice, QIOAutomationMode.SCHEDULED,
              PROVIDER + "|other_machine", layout));
        assertTrue(transfer.applyTo(target, targetDevice, QIOAutomationMode.SCHEDULED,
              PROVIDER, layout));

        QIOAutomationRecipeProfileCatalog.Selection selection = target.getSelection(
              targetDevice, QIOAutomationMode.SCHEDULED, PROVIDER);
        assertFalse(selection.isIndividual());
        assertEquals(2, selection.getGlobalSlot());
        assertEquals(RouteFilterMode.WHITELIST, selection.getFilterMode());
        assertTrue(target.getActiveProfile(targetDevice, QIOAutomationMode.SCHEDULED,
              PROVIDER).isRouteEnabled(route));
        assertEquals(2, target.getActiveProfile(targetDevice,
              QIOAutomationMode.SCHEDULED, PROVIDER).getCraftAmount());
    }

    @Test
    void sharedProviderMachineScopesKeepGlobalProfilesFullyIsolated() throws Exception {
        String firstScope = PROVIDER + "|mekanism:machineblock/basic_factory/smelting";
        String secondScope = PROVIDER + "|mekanism:machineblock/basic_factory/crushing";
        UUID firstDevice = UUID.randomUUID();
        UUID secondDevice = UUID.randomUUID();
        QIOAutomationRecipeProfileLayout layout = layout();
        List<String> products = layout.getDefaultProductOrder();
        String firstRoute = layout.getDefaultRouteOrder(products.get(0)).get(0);
        String secondRoute = layout.getDefaultRouteOrder(products.get(1)).get(0);
        QIOAutomationRecipeProfileCatalog catalog =
              new QIOAutomationRecipeProfileCatalog();

        assertTrue(catalog.toggleRoute(firstDevice, QIOAutomationMode.SCHEDULED,
              firstScope, layout, firstRoute));
        assertTrue(catalog.moveProduct(firstDevice, QIOAutomationMode.SCHEDULED,
              firstScope, layout, products.get(0), 1, false, false));
        assertTrue(catalog.setRouteCraftAmount(firstDevice, QIOAutomationMode.SCHEDULED,
              firstScope, layout, firstRoute, 2));
        assertTrue(catalog.toggleRoute(secondDevice, QIOAutomationMode.SCHEDULED,
              secondScope, layout, secondRoute));
        assertTrue(catalog.setCraftAmount(secondDevice, QIOAutomationMode.SCHEDULED,
              secondScope, layout, 3));

        QIOAutomationRecipeProfile first = catalog.getActiveProfile(firstDevice,
              QIOAutomationMode.SCHEDULED, firstScope);
        QIOAutomationRecipeProfile second = catalog.getActiveProfile(secondDevice,
              QIOAutomationMode.SCHEDULED, secondScope);
        assertFalse(first.isRouteEnabled(firstRoute));
        assertTrue(second.isRouteEnabled(firstRoute));
        assertFalse(second.isRouteEnabled(secondRoute));
        assertEquals(products.get(1), layout.ordered(first, false).get(0).getProductKey());
        assertEquals(products.get(0), layout.ordered(second, false).get(0).getProductKey());

        QIOAutomationRecipeProfileLayout reduced = new QIOAutomationRecipeProfileLayout(
              Collections.singletonList(layout.getRoute(secondRoute).getMachineRoute()));
        assertTrue(catalog.pruneActive(firstDevice, QIOAutomationMode.SCHEDULED,
              firstScope, reduced));
        assertFalse(catalog.getActiveProfile(secondDevice, QIOAutomationMode.SCHEDULED,
              secondScope).isRouteEnabled(secondRoute));
        assertEquals(3, catalog.getActiveProfile(secondDevice,
              QIOAutomationMode.SCHEDULED, secondScope).getCraftAmount());

        QIOAutomationRecipeProfileCatalog restored =
              QIOAutomationRecipeProfileCatalog.read(catalog.write());
        assertTrue(restored.getActiveProfile(firstDevice, QIOAutomationMode.SCHEDULED,
              firstScope).isRouteEnabled(firstRoute));
        assertFalse(restored.getActiveProfile(secondDevice, QIOAutomationMode.SCHEDULED,
              secondScope).isRouteEnabled(secondRoute));
        assertEquals(3, restored.getActiveProfile(secondDevice,
              QIOAutomationMode.SCHEDULED, secondScope).getCraftAmount());
    }

    @Test
    void individualCardTransferPreservesTargetsRememberedGlobalSlot() throws Exception {
        QIOAutomationRecipeProfileLayout layout = layout();
        String route = layout.getDefaultRouteOrder(
              layout.getDefaultProductOrder().get(0)).get(0);
        UUID sourceDevice = UUID.randomUUID();
        QIOAutomationRecipeProfileCatalog source =
              new QIOAutomationRecipeProfileCatalog();
        source.toggleProfileMode(sourceDevice, QIOAutomationMode.PASSIVE, PROVIDER);
        source.toggleRoute(sourceDevice, QIOAutomationMode.PASSIVE, PROVIDER, layout, route);
        QIOAutomationRecipeProfileTransfer transfer =
              QIOAutomationRecipeProfileTransfer.capture(source, sourceDevice,
                    QIOAutomationMode.PASSIVE, PROVIDER);

        UUID targetDevice = UUID.randomUUID();
        QIOAutomationRecipeProfileCatalog target =
              new QIOAutomationRecipeProfileCatalog();
        for (int index = 0; index < 6; index++) {
            target.cycleGlobalSlot(targetDevice, QIOAutomationMode.PASSIVE, PROVIDER, 1);
        }
        assertTrue(transfer.applyTo(target, targetDevice, QIOAutomationMode.PASSIVE,
              PROVIDER, layout));
        QIOAutomationRecipeProfileCatalog.Selection selection = target.getSelection(
              targetDevice, QIOAutomationMode.PASSIVE, PROVIDER);
        assertTrue(selection.isIndividual());
        assertEquals(7, selection.getGlobalSlot());
        assertTrue(target.getActiveProfile(targetDevice, QIOAutomationMode.PASSIVE,
              PROVIDER).isRouteEnabled(route));

        NBTTagCompound damaged = transfer.write();
        damaged.setInteger("schema", 3);
        assertThrows(QIOProcessingDataException.class,
              () -> QIOAutomationRecipeProfileTransfer.read(damaged));
    }

    private static QIOAutomationRecipeProfileLayout layout() {
        MachineRecipeRoute ironA = MachineRecipeRoute.builder("iron_a")
              .recipeKey("test:iron_a").logicalRecipeKey("test:iron_a")
              .input(MachineResourceStack.item("input", new ItemStack(Items.COAL, 2)))
              .output(MachineResourceStack.item("output", new ItemStack(Items.IRON_INGOT)))
              .build();
        MachineRecipeRoute ironB = MachineRecipeRoute.builder("iron_b")
              .recipeKey("test:iron_b").logicalRecipeKey("test:iron_b")
              .input(MachineResourceStack.item("input", new ItemStack(Items.CLAY_BALL)))
              .output(MachineResourceStack.item("output", new ItemStack(Items.IRON_INGOT)))
              .build();
        MachineRecipeRoute gold = MachineRecipeRoute.builder("gold")
              .recipeKey("test:gold").logicalRecipeKey("test:gold")
              .input(MachineResourceStack.item("input", new ItemStack(Items.COAL)))
              .output(MachineResourceStack.item("output", new ItemStack(Items.GOLD_INGOT)))
              .build();
        return new QIOAutomationRecipeProfileLayout(Arrays.asList(ironA, ironB, gold));
    }
}
