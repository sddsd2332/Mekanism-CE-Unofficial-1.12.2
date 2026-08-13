package mekanism.qioprocessing.common.terminal;

import mekanism.api.processing.MachineRecipeRoute;
import mekanism.api.processing.MachinePresentationDescriptor;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.common.TestBootstrap;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.api.machine.QIOAutomationDeviceLocation;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeProfileMutation;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceSnapshot;
import mekanism.qioprocessing.common.planning.QIOProviderCatalog;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeService.Context;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot.Product;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot.Route;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOManagementRecipeServiceTest {

    private static final ResourceLocation PROVIDER =
          new ResourceLocation("test", "remote_passive");
    private static final String SCOPE =
          "test:remote_passive|mekanism:machineblock/enrichment";

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void unloadedPassiveMachineCanBeReadAndMutatedFromPersistedFrequencyData()
          throws Exception {
        UUID owner = UUID.randomUUID();
        UUID frequency = UUID.randomUUID();
        UUID deviceUUID = UUID.randomUUID();
        QIOAutomationDeviceLocation location = new QIOAutomationDeviceLocation(0,
              new BlockPos(12, 64, -8));
        QIOProcessingNetworkData network = network(frequency, owner);
        network.observeAutomationDevice(device(deviceUUID, location,
              QIOAutomationMode.PASSIVE, true), 8);
        network.observeAutomationDeviceRoutes(deviceUUID, QIOAutomationMode.PASSIVE,
              SCOPE, PROVIDER, Collections.singletonList(route()), 16);
        assertTrue(network.markAutomationDeviceOffline(deviceUUID, location, 20));

        QIOProcessingTerminalSession session = session(frequency, owner);
        Context context = QIOManagementRecipeService.open(session, network, 3, owner,
              deviceUUID);
        assertNotNull(context);
        QIOManagementRecipeSnapshot products = QIOManagementRecipeService.products(context,
              0, 16, "", QIOManagementRecipeService.ProductFilter.ALL);
        assertEquals(1, products.getProducts().size());
        Product product = products.getProducts().get(0);
        QIOManagementRecipeSnapshot routes = QIOManagementRecipeService.routes(context,
              product.getProductKey(), 0, 16, "");
        Route route = routes.getRoutes().get(0);
        assertFalse(route.isProfileEnabled());

        assertEquals(QIOManagementRecipeService.MutationStatus.APPLIED,
              QIOManagementRecipeService.mutate(context, products.getProfileRevision(),
                    QIOAutomationRecipeProfileMutation.route(
                          QIOAutomationRecipeProfileMutation.Action.TOGGLE_ROUTE,
                          product.getProductKey(), route.getRouteKey())));
        QIOManagementRecipeSnapshot updated = QIOManagementRecipeService.routes(context,
              product.getProductKey(), 0, 16, "");
        assertTrue(updated.getRoutes().get(0).isProfileEnabled());

        QIOProcessingNetworkData restored = QIOProcessingNetworkData.read(network.write(),
              frequency);
        Context restoredContext = QIOManagementRecipeService.open(session, restored, 3,
              owner, deviceUUID);
        assertNotNull(restoredContext);
        assertEquals(QIOAutomationMode.PASSIVE,
              restored.getProviderCatalog().getDeviceMode(deviceUUID));
        assertTrue(QIOManagementRecipeService.routes(restoredContext,
              product.getProductKey(), 0, 16, "").getRoutes().get(0)
              .isProfileEnabled());
    }

    @Test
    void globalProfileMutationImmediatelyAppliesToAnotherUnloadedMachineOfSameScope() {
        UUID owner = UUID.randomUUID();
        UUID frequency = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        QIOProcessingNetworkData network = network(frequency, owner);
        QIOAutomationDeviceLocation firstLocation = new QIOAutomationDeviceLocation(0,
              new BlockPos(1, 64, 1));
        QIOAutomationDeviceLocation secondLocation = new QIOAutomationDeviceLocation(0,
              new BlockPos(2, 64, 2));
        network.observeAutomationDevice(device(first, firstLocation,
              QIOAutomationMode.SCHEDULED, false), 8);
        network.observeAutomationDevice(device(second, secondLocation,
              QIOAutomationMode.SCHEDULED, false), 8);
        network.observeAutomationDeviceRoutes(first, QIOAutomationMode.SCHEDULED,
              SCOPE, PROVIDER, Collections.singletonList(route()), 16);
        network.observeAutomationDeviceRoutes(second, QIOAutomationMode.SCHEDULED,
              SCOPE, PROVIDER, Collections.singletonList(route()), 16);
        QIOProcessingTerminalSession session = session(frequency, owner);
        Context firstContext = QIOManagementRecipeService.open(session, network, 3, owner,
              first);
        Context secondContext = QIOManagementRecipeService.open(session, network, 3, owner,
              second);
        assertNotNull(firstContext);
        assertNotNull(secondContext);
        QIOManagementRecipeSnapshot firstProducts = QIOManagementRecipeService.products(
              firstContext, 0, 16, "", QIOManagementRecipeService.ProductFilter.ALL);
        Product product = firstProducts.getProducts().get(0);
        Route route = QIOManagementRecipeService.routes(firstContext,
              product.getProductKey(), 0, 16, "").getRoutes().get(0);

        assertEquals(QIOManagementRecipeService.MutationStatus.APPLIED,
              QIOManagementRecipeService.mutate(firstContext,
                    firstProducts.getProfileRevision(),
                    QIOAutomationRecipeProfileMutation.route(
                          QIOAutomationRecipeProfileMutation.Action.TOGGLE_ROUTE,
                          product.getProductKey(), route.getRouteKey())));
        assertFalse(QIOManagementRecipeService.routes(secondContext,
              product.getProductKey(), 0, 16, "").getRoutes().get(0)
              .isProfileEnabled());
    }

    @Test
    void deviceGroupsAndTypePagesUseProfileScopeInsteadOfLoadState() {
        UUID owner = UUID.randomUUID();
        UUID frequency = UUID.randomUUID();
        QIOProcessingNetworkData network = network(frequency, owner);
        QIOAutomationDeviceLocation firstLocation = new QIOAutomationDeviceLocation(0,
              new BlockPos(1, 64, 1));
        QIOAutomationDeviceLocation secondLocation = new QIOAutomationDeviceLocation(0,
              new BlockPos(2, 64, 2));
        QIOAutomationDeviceSnapshot first = device(UUID.randomUUID(), firstLocation,
              QIOAutomationMode.SCHEDULED, true);
        QIOAutomationDeviceSnapshot second = device(UUID.randomUUID(), secondLocation,
              QIOAutomationMode.PASSIVE, false);
        network.observeAutomationDevice(first, 8);
        network.observeAutomationDevice(second, 8);
        QIOProcessingTerminalSession session = session(frequency, owner);

        QIOPage<QIOManagementDeviceGroupSnapshot> groups =
              QIOManagementDeviceGroupService.getPage(session, network, 3, null, 8);
        assertEquals(1, groups.getEntries().size());
        assertEquals(1, groups.getEntries().get(0).getOnlineCount());
        assertEquals(2, groups.getEntries().get(0).getTotalCount());
        QIOPage<QIOAutomationDeviceSnapshot> devices =
              QIOManagementDeviceDirectoryService.getPageForType(session, network, 3,
                    groups.getEntries().get(0).getTypeKey(), null, 8);
        assertEquals(2, devices.getEntries().size());
    }

    @Test
    void presentationTierSplitsDisplayGroupsWithoutSplittingRecipeScope() {
        UUID owner = UUID.randomUUID();
        UUID frequency = UUID.randomUUID();
        QIOProcessingNetworkData network = network(frequency, owner);
        QIOAutomationDeviceLocation firstLocation = new QIOAutomationDeviceLocation(0,
              new BlockPos(1, 64, 1));
        QIOAutomationDeviceLocation secondLocation = new QIOAutomationDeviceLocation(0,
              new BlockPos(2, 64, 2));
        NBTTagCompound basic = new NBTTagCompound();
        basic.setInteger("tier", 0);
        NBTTagCompound ultimate = new NBTTagCompound();
        ultimate.setInteger("tier", 3);
        QIOAutomationDeviceSnapshot first = deviceWithPresentation(UUID.randomUUID(),
              firstLocation, basic);
        QIOAutomationDeviceSnapshot second = deviceWithPresentation(UUID.randomUUID(),
              secondLocation, ultimate);
        network.observeAutomationDevice(first, 8);
        network.observeAutomationDevice(second, 8);

        QIOPage<QIOManagementDeviceGroupSnapshot> groups =
              QIOManagementDeviceGroupService.getPage(session(frequency, owner), network,
                    3, null, 8);

        assertEquals(2, groups.getEntries().size());
        assertEquals(first.getProfileScopeId(), second.getProfileScopeId());
        assertEquals(SCOPE, first.getProfileScopeId());
    }

    @Test
    void managementSessionCannotBeReusedByAnotherPlayer() {
        UUID owner = UUID.randomUUID();
        UUID frequency = UUID.randomUUID();
        UUID deviceUUID = UUID.randomUUID();
        QIOProcessingNetworkData network = network(frequency, owner);
        QIOAutomationDeviceLocation location = new QIOAutomationDeviceLocation(0,
              new BlockPos(3, 64, 3));
        network.observeAutomationDevice(device(deviceUUID, location,
              QIOAutomationMode.SCHEDULED, false), 8);
        network.observeAutomationDeviceRoutes(deviceUUID, QIOAutomationMode.SCHEDULED,
              SCOPE, PROVIDER, Collections.singletonList(route()), 16);

        assertThrows(SecurityException.class, () -> QIOManagementRecipeService.open(
              session(frequency, owner), network, 3, UUID.randomUUID(), deviceUUID));
    }

    @Test
    void tenThousandRetainedOfflineRoutesRemainPageableUnderFiveSeconds() {
        UUID owner = UUID.randomUUID();
        UUID frequency = UUID.randomUUID();
        UUID deviceUUID = UUID.randomUUID();
        QIOAutomationDeviceLocation location = new QIOAutomationDeviceLocation(0,
              new BlockPos(4, 64, 4));
        QIOProcessingNetworkData network = network(frequency, owner);
        network.observeAutomationDevice(device(deviceUUID, location,
              QIOAutomationMode.SCHEDULED, true), 8);
        List<MachineRecipeRoute> routes = new ArrayList<>(10_000);
        for (int index = 0; index < 10_000; index++) {
            routes.add(MachineRecipeRoute.builder("route:item_to_item")
                  .recipeKey("test:stress_" + index)
                  .inputItem("input", new ItemStack(Items.IRON_INGOT))
                  .outputItem("output", new ItemStack(Items.GOLD_INGOT)).build());
        }
        assertTimeout(Duration.ofSeconds(5), () ->
              network.observeAutomationDeviceRoutes(deviceUUID,
                    QIOAutomationMode.SCHEDULED, SCOPE, PROVIDER, routes, 20_000));
        assertTrue(network.markAutomationDeviceOffline(deviceUUID, location, 20));

        assertTimeout(Duration.ofSeconds(5), () -> {
            Context context = QIOManagementRecipeService.open(
                  session(frequency, owner), network, 3, owner, deviceUUID);
            assertNotNull(context);
            QIOManagementRecipeSnapshot products = QIOManagementRecipeService.products(
                  context, 0, 64, "", QIOManagementRecipeService.ProductFilter.ALL);
            assertEquals(1, products.getTotalSize());
            QIOManagementRecipeSnapshot searchedProducts =
                  QIOManagementRecipeService.products(context, 0, 64,
                        "minecraft:gold_ingot",
                        QIOManagementRecipeService.ProductFilter.ALL);
            assertEquals(1, searchedProducts.getTotalSize());
            QIOManagementRecipeSnapshot routePage = QIOManagementRecipeService.routes(
                  context, products.getProducts().get(0).getProductKey(), 0, 64, "");
            assertEquals(10_000, routePage.getTotalSize());
            assertEquals(64, routePage.getRoutes().size());
            QIOManagementRecipeSnapshot searchedRoutes = QIOManagementRecipeService.routes(
                  context, products.getProducts().get(0).getProductKey(), 0, 64,
                  "stress_9999");
            assertEquals(1, searchedRoutes.getTotalSize());
            assertEquals("test:stress_9999",
                  searchedRoutes.getRoutes().get(0).getRecipeKey());
        });
    }

    private static QIOProcessingNetworkData network(UUID frequency, UUID owner) {
        return new QIOProcessingNetworkData(frequency,
              new QIOFrequencyIdentitySnapshot("remote", owner, SecurityMode.PRIVATE));
    }

    private static QIOProcessingTerminalSession session(UUID frequency, UUID owner) {
        return new QIOProcessingTerminalSession(owner,
              QIOProcessingTerminalSession.TargetKind.BLOCK,
              QIOProcessingTerminalType.MANAGEMENT, UUID.randomUUID(), 0, frequency, 3);
    }

    private static QIOAutomationDeviceSnapshot device(UUID uuid,
          QIOAutomationDeviceLocation location, QIOAutomationMode mode, boolean online) {
        return new QIOAutomationDeviceSnapshot(uuid, location,
              QIOAutomationDeviceSnapshot.Kind.AUTOMATION_MACHINE,
              "mekanism:machineblock", 0, PROVIDER.toString(), SCOPE,
              mode.name(), QIOAutomationHost.State.ACTIVE.name(), online, 1, 1, 1,
              1, 0, false, null);
    }

    private static QIOAutomationDeviceSnapshot deviceWithPresentation(UUID uuid,
          QIOAutomationDeviceLocation location, NBTTagCompound tier) {
        return new QIOAutomationDeviceSnapshot(uuid, location,
              QIOAutomationDeviceSnapshot.Kind.AUTOMATION_MACHINE,
              "mekanism:machineblock", 0,
              MachinePresentationDescriptor.of("minecraft:diamond", 0, tier),
              PROVIDER.toString(), SCOPE, QIOAutomationMode.SCHEDULED.name(),
              QIOAutomationHost.State.ACTIVE.name(), true, 1, 1, 1, 1, 0,
              false, null);
    }

    private static MachineRecipeRoute route() {
        return MachineRecipeRoute.builder("route:item_to_item")
              .recipeKey("test:iron_to_gold")
              .inputItem("input", new ItemStack(Items.IRON_INGOT))
              .outputItem("output", new ItemStack(Items.GOLD_INGOT)).build();
    }
}
