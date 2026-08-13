package mekanism.qioprocessing.common.planning;

import mekanism.api.processing.MachineRecipeRoute;
import mekanism.api.processing.MachinePort;
import mekanism.api.processing.MachineRecipeProvider;
import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.api.processing.ProviderConformanceDescriptor;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.common.TestBootstrap;
import mekanism.common.recipe.processing.MachineRecipeRouteCollectors;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.api.machine.QIOAutomationDeviceLocation;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceCatalog;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceSnapshot;
import mekanism.qioprocessing.common.content.policy.QIOPolicyCatalog;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfileCatalog;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfileLayout;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QIOProviderRouteCatalogTest {

    private static final ResourceLocation PROVIDER = new ResourceLocation("test", "factory");

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void physicalFactoryLanesCollapseToOneLogicalPlanningRoute() {
        MachineRecipeRoute logical = MachineRecipeRoute.builder("route:item_to_item")
              .recipeKey("test:iron_to_gold")
              .inputItem("item_input", new ItemStack(Items.IRON_INGOT))
              .outputItem("item_output", new ItemStack(Items.GOLD_INGOT)).build();
        List<MachineRecipeRoute> lanes = MachineRecipeRouteCollectors.expandLanes(
              Collections.singletonList(logical), 3);
        UUID firstDevice = UUID.randomUUID();
        UUID secondDevice = UUID.randomUUID();
        QIOProviderRouteCatalog.Snapshot snapshot = new QIOProviderRouteCatalog.Builder(
              (provider, route, recipe) -> 7)
              .addDevice(firstDevice, PROVIDER, lanes)
              .addDevice(secondDevice, PROVIDER, lanes)
              .build();

        assertEquals(1, snapshot.getRoutes().size());
        QIOPlanningRoute route = snapshot.getRoutes().get(0);
        assertEquals("test:iron_to_gold", route.getRecipeKey());
        assertEquals(7, route.getRoutePriority());
        assertEquals(2, snapshot.getDeviceIds(route.getStableId()).size());
        assertTrue(snapshot.getDeviceIds(route.getStableId()).contains(firstDevice));
        assertTrue(snapshot.getDeviceIds(route.getStableId()).contains(secondDevice));
        assertTrue(snapshot.getDiagnostics().isEmpty());
    }

    @Test
    void conflictingSemanticsUnderOneStableIdentityAreQuarantined() {
        MachineRecipeRoute first = MachineRecipeRoute.builder("route:item_to_item")
              .recipeKey("test:physical_a").logicalRecipeKey("test:logical")
              .inputItem("input", new ItemStack(Items.IRON_INGOT))
              .outputItem("output", new ItemStack(Items.GOLD_INGOT)).build();
        MachineRecipeRoute second = MachineRecipeRoute.builder("route:item_to_item")
              .recipeKey("test:physical_b").logicalRecipeKey("test:logical")
              .inputItem("input", new ItemStack(Items.IRON_INGOT))
              .outputItem("output", new ItemStack(Items.DIAMOND)).build();

        QIOProviderRouteCatalog.Snapshot snapshot = new QIOProviderRouteCatalog.Builder(
              (provider, route, recipe) -> 0)
              .addDevice(UUID.randomUUID(), PROVIDER, Arrays.asList(first, second))
              .build();

        assertTrue(snapshot.getRoutes().isEmpty());
        assertFalse(snapshot.getDiagnostics().isEmpty());
        assertTrue(snapshot.getDiagnostics().get(0).contains("Conflicting"));
    }

    @Test
    void persistentCatalogSeparatesOfflineAvailabilityFromStructuralRemoval() throws Exception {
        MachineRecipeRoute machineRoute = MachineRecipeRoute.builder("route:item_to_item")
              .recipeKey("test:iron_to_gold")
              .inputItem("input", new ItemStack(Items.IRON_INGOT))
              .outputItem("output", new ItemStack(Items.GOLD_INGOT)).build();
        UUID device = UUID.randomUUID();
        QIOProviderCatalog catalog = new QIOProviderCatalog();
        QIOAutomationDeviceCatalog devices = new QIOAutomationDeviceCatalog();
        devices.observe(scheduledDevice(device), 16);

        QIOProviderRouteCatalog.Snapshot online = new QIOProviderRouteCatalog.Builder(
              (provider, route, recipe) -> 0)
              .addDevice(device, PROVIDER, Collections.singletonList(machineRoute)).build();
        assertTrue(catalog.observe(online, devices, 16));
        assertEquals(1, catalog.getRevision());
        assertEquals(1, catalog.getAvailabilityRevision());
        assertEquals(1, catalog.getRoutes(new QIOPolicyCatalog()).size());

        QIOProviderCatalog restored = QIOProviderCatalog.read(catalog.write());
        long structuralRevision = restored.getRevision();
        assertEquals(1, restored.size());
        assertTrue(restored.getRoutes(new QIOPolicyCatalog()).isEmpty());

        QIOProviderRouteCatalog.Snapshot offline = new QIOProviderRouteCatalog.Builder(
              (provider, route, recipe) -> 0).build();
        assertFalse(restored.observe(offline, devices, 16));
        assertEquals(structuralRevision, restored.getRevision());
        assertEquals(1, restored.size());
        assertTrue(restored.getRoutes(new QIOPolicyCatalog()).isEmpty());

        QIOProviderRouteCatalog.Snapshot removed = new QIOProviderRouteCatalog.Builder(
              (provider, route, recipe) -> 0)
              .addDevice(device, PROVIDER, Collections.emptyList()).build();
        assertTrue(restored.observe(removed, devices, 16));
        assertEquals(structuralRevision + 1, restored.getRevision());
        assertTrue(restored.getRoutes(new QIOPolicyCatalog()).isEmpty());
    }

    @Test
    void providerRouteAdmissionUsesItsOwnCapacityLimit() {
        MachineRecipeRoute first = MachineRecipeRoute.builder("route:first")
              .recipeKey("test:first")
              .inputItem("input", new ItemStack(Items.IRON_INGOT))
              .outputItem("output", new ItemStack(Items.GOLD_INGOT)).build();
        MachineRecipeRoute second = MachineRecipeRoute.builder("route:second")
              .recipeKey("test:second")
              .inputItem("input", new ItemStack(Items.GOLD_INGOT))
              .outputItem("output", new ItemStack(Items.DIAMOND)).build();
        QIOProviderRouteCatalog.Snapshot snapshot = new QIOProviderRouteCatalog.Builder(
              (provider, route, recipe) -> 0).addDevice(UUID.randomUUID(), PROVIDER,
              Arrays.asList(first, second)).build();
        QIOProviderCatalog catalog = new QIOProviderCatalog();
        QIOAutomationDeviceCatalog devices = new QIOAutomationDeviceCatalog();
        UUID device = snapshot.getObservedDeviceIds().iterator().next();
        devices.observe(scheduledDevice(device), 1);

        assertThrows(IllegalStateException.class, () -> catalog.observe(snapshot,
              devices, 1));
        assertEquals(0, catalog.size());
        assertEquals(0, catalog.getRevision());
    }

    @Test
    void fullCatalogAllowsOneForOneRouteReplacement() {
        MachineRecipeRoute first = route("route:first", "test:first", Items.IRON_INGOT,
              Items.GOLD_INGOT);
        MachineRecipeRoute second = route("route:second", "test:second", Items.GOLD_INGOT,
              Items.DIAMOND);
        UUID device = UUID.randomUUID();
        QIOAutomationDeviceCatalog devices = new QIOAutomationDeviceCatalog();
        devices.observe(scheduledDevice(device), 1);
        QIOProviderCatalog catalog = new QIOProviderCatalog();

        assertTrue(catalog.observe(snapshot(device, first), devices, 1));
        assertTrue(catalog.observe(snapshot(device, second), devices, 1));
        assertEquals(1, catalog.size());
        assertEquals("test:second", catalog.getRoutes(new QIOPolicyCatalog()).get(0)
              .getRecipeKey());
    }

    @Test
    void networkRoundTripPreservesRoutesButStartsThemUnavailable() throws Exception {
        UUID frequencyUUID = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(frequencyUUID,
              new QIOFrequencyIdentitySnapshot("test", null, SecurityMode.PUBLIC));
        network.observeAutomationDevice(scheduledDevice(device), 8);
        network.observeProviderCatalog(snapshot(device, route("route:first", "test:first",
              Items.IRON_INGOT, Items.GOLD_INGOT)), 8);

        QIOProcessingNetworkData restored = QIOProcessingNetworkData.read(network.write(),
              frequencyUUID);

        assertEquals(1, restored.getProviderCatalog().size());
        assertTrue(restored.getProviderCatalog().getRoutes(new QIOPolicyCatalog()).isEmpty());
        restored.observeAutomationDevice(scheduledDevice(device), 8);
        restored.observeProviderCatalog(snapshot(device, route("route:first", "test:first",
              Items.IRON_INGOT, Items.GOLD_INGOT)), 8);
        assertEquals(1, restored.getProviderCatalog().getRoutes(
              new QIOPolicyCatalog()).size());
    }

    @Test
    void networkLoadRejectsProviderAssociationWithoutDeviceRecord() {
        UUID frequencyUUID = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(frequencyUUID,
              new QIOFrequencyIdentitySnapshot("test", null, SecurityMode.PUBLIC));
        network.observeAutomationDevice(scheduledDevice(device), 8);
        network.observeProviderCatalog(snapshot(device, route("route:first", "test:first",
              Items.IRON_INGOT, Items.GOLD_INGOT)), 8);
        NBTTagCompound stored = network.write();
        stored.getCompoundTag("automationDevices").setTag("devices", new NBTTagList());

        assertThrows(Exception.class, () -> QIOProcessingNetworkData.read(stored,
              frequencyUUID));
    }

    @Test
    void sharedProviderDevicesResolveProfilesByPersistedMachineScope() throws Exception {
        String firstScope = PROVIDER + "|mekanism:machineblock/basic_factory/smelting";
        String secondScope = PROVIDER + "|mekanism:machineblock/basic_factory/crushing";
        UUID firstDevice = UUID.randomUUID();
        UUID secondDevice = UUID.randomUUID();
        MachineRecipeRoute machineRoute = route("route:shared", "test:shared",
              Items.IRON_INGOT, Items.GOLD_INGOT);
        QIOProviderRouteCatalog.Snapshot online = new QIOProviderRouteCatalog.Builder(
              (provider, routeId, recipe) -> 0)
              .addDevice(firstDevice, PROVIDER, Collections.singletonList(machineRoute))
              .addDevice(secondDevice, PROVIDER, Collections.singletonList(machineRoute))
              .build();
        QIOAutomationDeviceCatalog devices = new QIOAutomationDeviceCatalog();
        devices.observe(scheduledDevice(firstDevice, firstScope), 2);
        devices.observe(scheduledDevice(secondDevice, secondScope), 2);
        QIOProviderCatalog catalog = new QIOProviderCatalog();
        assertTrue(catalog.observe(online, devices, 4));

        QIOAutomationRecipeProfileCatalog profiles =
              new QIOAutomationRecipeProfileCatalog();
        QIOAutomationRecipeProfileLayout layout = new QIOAutomationRecipeProfileLayout(
              Collections.singletonList(machineRoute));
        String routeKey = layout.getRouteKeys().iterator().next();
        assertTrue(profiles.toggleRoute(firstDevice, QIOAutomationMode.SCHEDULED,
              firstScope, layout, routeKey));
        assertEquals(1, catalog.getRoutes(new QIOPolicyCatalog(), profiles).size());

        QIOProviderCatalog restored = QIOProviderCatalog.read(catalog.write());
        restored.validateDevices(devices);
        restored.observe(online, devices, 4);
        assertEquals(1, restored.getRoutes(new QIOPolicyCatalog(), profiles).size());

        assertTrue(profiles.toggleRoute(secondDevice, QIOAutomationMode.SCHEDULED,
              secondScope, layout, routeKey));
        assertTrue(restored.getRoutes(new QIOPolicyCatalog(), profiles).isEmpty());
    }

    @Test
    void planningAdmissionRejectsAnEndpointWithNoCurrentRoutes() {
        ResourceLocation id = new ResourceLocation("test", "empty_planning_endpoint");
        MachineRecipeProviderRegistry.unregister(id);
        try {
            MachineRecipeProviderRegistry.register(id, EmptyRouteTile.class,
                  new EmptyRouteProvider());
            MachineRecipeProviderRegistry.BoundProvider provider =
                  MachineRecipeProviderRegistry.find(new EmptyRouteTile());
            assertTrue(provider.validateQIOEndpointConformance(
                  QIOAutomationMode.SCHEDULED).isConformant());
            assertFalse(QIOProviderRouteCatalog.strictRouteReport(provider,
                  QIOAutomationMode.SCHEDULED).isConformant());
        } finally {
            MachineRecipeProviderRegistry.unregister(id);
        }
    }

    private static QIOProviderRouteCatalog.Snapshot snapshot(UUID device,
          MachineRecipeRoute route) {
        return new QIOProviderRouteCatalog.Builder((provider, routeId, recipe) -> 0)
              .addDevice(device, PROVIDER, Collections.singletonList(route)).build();
    }

    private static MachineRecipeRoute route(String routeId, String recipeKey,
          net.minecraft.item.Item input, net.minecraft.item.Item output) {
        return MachineRecipeRoute.builder(routeId).recipeKey(recipeKey)
              .inputItem("input", new ItemStack(input))
              .outputItem("output", new ItemStack(output)).build();
    }

    private static QIOAutomationDeviceSnapshot scheduledDevice(UUID deviceUUID) {
        return new QIOAutomationDeviceSnapshot(deviceUUID,
              new QIOAutomationDeviceLocation(0, BlockPos.ORIGIN), "minecraft:furnace", 0,
              PROVIDER.toString(), QIOAutomationMode.SCHEDULED, QIOAutomationHost.State.ACTIVE,
              true, 1, 1, 1, 1, 0, null);
    }

    private static QIOAutomationDeviceSnapshot scheduledDevice(UUID deviceUUID,
          String profileScopeId) {
        return new QIOAutomationDeviceSnapshot(deviceUUID,
              new QIOAutomationDeviceLocation(0, BlockPos.ORIGIN), "minecraft:furnace", 0,
              PROVIDER.toString(), profileScopeId, QIOAutomationMode.SCHEDULED,
              QIOAutomationHost.State.ACTIVE, true, 1, 1, 1, 1, 0, null);
    }

    private static final class EmptyRouteTile extends TileEntity {

        private final BasicInventorySlot input = BasicInventorySlot.at(null, 0, 0);
        private final BasicInventorySlot output = BasicInventorySlot.at(null, 0, 0);
    }

    private static final class EmptyRouteProvider implements
          MachineRecipeProvider<EmptyRouteTile> {

        @Override
        public List<MachineRecipeRoute> getRecipeRoutes(EmptyRouteTile tile) {
            return Collections.emptyList();
        }

        @Override
        public List<MachinePort> getPorts(EmptyRouteTile tile) {
            return Arrays.asList(
                  MachinePort.item("input", MachinePort.Role.INPUT, tile.input),
                  MachinePort.item("output", MachinePort.Role.OUTPUT, tile.output));
        }

        @Override
        public ProviderConformanceDescriptor getQIOConformance(EmptyRouteTile tile) {
            return ProviderConformanceDescriptor.builder("test", "empty_planning")
                  .supports(QIOAutomationMode.SCHEDULED).build();
        }
    }
}
