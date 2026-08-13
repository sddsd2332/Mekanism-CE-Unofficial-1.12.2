package mekanism.qioprocessing.common.machine;

import mekanism.api.processing.MachinePort;
import mekanism.api.processing.MachineRecipeProvider;
import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.api.processing.MachineRecipeRoute;
import mekanism.api.processing.ProviderConformanceDescriptor;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.common.TestBootstrap;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.content.qio.QIODriveStorage;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOResourceTypeRegistry;
import mekanism.common.frequency.FrequencyManager;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.QIOProcessingUpgrades;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIODynamicRouteEndpointTest {

    private static final ResourceLocation PROVIDER_ID =
          new ResourceLocation("test", "dynamic_qio_endpoint");

    private File worldDirectory;

    @BeforeAll
    static void bootstrapMinecraft() throws ReflectiveOperationException {
        TestBootstrap.bootstrapMinecraft();
        QIOAutomationCapabilities.register();
        // Forge injects @CapabilityInject fields during normal mod startup, which the
        // plain unit-test bootstrap does not run.
        QIOAutomationCapabilities.AUTOMATION_HOST = registeredCapability(
              QIOAutomationHost.class);
        assertNotNull(QIOAutomationCapabilities.AUTOMATION_HOST);
    }

    @BeforeEach
    void setup() throws Exception {
        MachineRecipeProviderRegistry.unregister(PROVIDER_ID);
        MachineRecipeProviderRegistry.register(PROVIDER_ID, DynamicTile.class,
              new DynamicProvider());
        FrequencyManager.reset();
        worldDirectory = Files.createTempDirectory("qio-dynamic-endpoint-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
    }

    @AfterEach
    void cleanup() throws Exception {
        MachineRecipeProviderRegistry.unregister(PROVIDER_ID);
        QIOAutomationDeviceRegistry.INSTANCE.shutdown();
        FrequencyManager.reset();
        QIODriveStorage.INSTANCE.reset();
        QIOResourceTypeRegistry.INSTANCE.reset();
        delete(worldDirectory);
    }

    @Test
    void templateEmptyProviderCanBindAndRestoreWithoutDataError() {
        DynamicTile tile = new DynamicTile();
        assertEquals(1, tile.upgrades.setUpgrades(
              QIOProcessingUpgrades.QIO_AUTO_CRAFTING, 1));
        assertEquals(QIOAutomationMode.SCHEDULED, tile.host.getEnabledMode());
        MachineRecipeProviderRegistry.BoundProvider provider =
              MachineRecipeProviderRegistry.find(tile);
        assertNotNull(provider);
        assertTrue(provider.validateQIOEndpointConformance(
              QIOAutomationMode.SCHEDULED).isConformant());
        assertFalse(provider.validateQIOConformance(
              QIOAutomationMode.SCHEDULED).isConformant());
        assertTrue(QIOAutomationBindingService.acceptsEndpoint(provider,
              QIOAutomationMode.SCHEDULED));
        assertFalse(QIOAutomationDeviceRegistry.hasPublishableRoutes(provider,
              QIOAutomationMode.SCHEDULED));

        QIOFrequency frequency = new QIOFrequency("dynamic-template", null,
              SecurityMode.PUBLIC);
        FrequencyManager<QIOFrequency> manager = FrequencyType.QIO.getManager(null,
              SecurityMode.PUBLIC);
        assertNotNull(manager);
        manager.addFrequency(frequency);
        UUID requester = UUID.randomUUID();

        assertTrue(QIOAutomationBindingService.bind(tile, frequency, requester));
        assertEquals(QIOAutomationHost.State.ACTIVE, tile.host.getState());
        assertEquals(frequency.getFrequencyUUID(),
              tile.host.getFrequencyReference().getFrequencyUUID());

        DynamicTile restored = new DynamicTile();
        assertEquals(1, restored.upgrades.setUpgrades(
              QIOProcessingUpgrades.QIO_AUTO_CRAFTING, 1));
        restored.host.deserializeNBT(tile.host.serializeNBT());
        assertEquals(QIOAutomationHost.State.WAITING_ACCESS,
              restored.host.getState());
        assertTrue(QIOAutomationDeviceRegistry.validateEndpointForRegistration(
              restored.host, restored));
        assertEquals(QIOAutomationHost.State.WAITING_ACCESS,
              restored.host.getState());
        assertNull(restored.host.getDataError());
    }

    @Test
    void invalidEndpointStillCannotBindOrSurviveRegistrationValidation() {
        DynamicTile tile = new DynamicTile();
        assertEquals(1, tile.upgrades.setUpgrades(
              QIOProcessingUpgrades.QIO_AUTO_CRAFTING, 1));
        tile.exposeOutput = false;
        MachineRecipeProviderRegistry.BoundProvider provider =
              MachineRecipeProviderRegistry.find(tile);
        assertNotNull(provider);
        assertFalse(QIOAutomationBindingService.acceptsEndpoint(provider,
              QIOAutomationMode.SCHEDULED));

        QIOFrequency frequency = new QIOFrequency("invalid-endpoint", null,
              SecurityMode.PUBLIC);
        FrequencyManager<QIOFrequency> manager = FrequencyType.QIO.getManager(null,
              SecurityMode.PUBLIC);
        assertNotNull(manager);
        manager.addFrequency(frequency);
        assertFalse(QIOAutomationBindingService.bind(tile, frequency,
              UUID.randomUUID()));
        assertEquals(QIOAutomationHost.State.UNBOUND, tile.host.getState());

        assertFalse(QIOAutomationDeviceRegistry.validateEndpointForRegistration(
              tile.host, tile));
        assertEquals(QIOAutomationHost.State.DATA_ERROR, tile.host.getState());
        assertNotNull(tile.host.getDataError());
    }

    @Test
    void unchangedPublishableRoutesRemainInTheRemoteDirectory() {
        assertEquals(QIOAutomationDeviceRegistry.RoutePublicationAction.PUBLISH,
              QIOAutomationDeviceRegistry.routePublicationAction(true, true));
        assertEquals(QIOAutomationDeviceRegistry.RoutePublicationAction.RETAIN,
              QIOAutomationDeviceRegistry.routePublicationAction(true, false));
        assertEquals(QIOAutomationDeviceRegistry.RoutePublicationAction.FORGET,
              QIOAutomationDeviceRegistry.routePublicationAction(false, true));
        assertEquals(QIOAutomationDeviceRegistry.RoutePublicationAction.FORGET,
              QIOAutomationDeviceRegistry.routePublicationAction(false, false));
    }

    private static final class DynamicProvider implements
          MachineRecipeProvider<DynamicTile> {

        @Override
        public List<MachineRecipeRoute> getRecipeRoutes(DynamicTile tile) {
            return tile.templateAvailable ? Collections.singletonList(
                  MachineRecipeRoute.builder("route:item_to_item")
                        .recipeKey("test:dynamic_template")
                        .inputItem("input", new ItemStack(Items.IRON_INGOT))
                        .outputItem("output", new ItemStack(Items.GOLD_INGOT)).build()) :
                  Collections.emptyList();
        }

        @Override
        public List<MachinePort> getPorts(DynamicTile tile) {
            return tile.exposeOutput ? Arrays.asList(
                  MachinePort.item("input", MachinePort.Role.INPUT, tile.input),
                  MachinePort.item("output", MachinePort.Role.OUTPUT, tile.output)) :
                  Collections.singletonList(
                        MachinePort.item("input", MachinePort.Role.INPUT, tile.input));
        }

        @Override
        public ProviderConformanceDescriptor getQIOConformance(DynamicTile tile) {
            return ProviderConformanceDescriptor.builder("test", "dynamic_endpoint")
                  .supports(QIOAutomationMode.SCHEDULED, QIOAutomationMode.PASSIVE)
                  .build();
        }
    }

    private static final class DynamicTile extends TileEntityContainerBlock
          implements IUpgradeTile {

        private final BasicInventorySlot input = BasicInventorySlot.at(null, 0, 0);
        private final BasicInventorySlot output = BasicInventorySlot.at(null, 0, 0);
        private final TileComponentUpgrade upgrades = new TileComponentUpgrade(this);
        private final DefaultQIOAutomationHost host = new DefaultQIOAutomationHost(this);
        private boolean templateAvailable;
        private boolean exposeOutput = true;

        private DynamicTile() {
            super("dynamic_qio_endpoint_test");
            upgrades.setSupported(QIOProcessingUpgrades.QIO_AUTO_CRAFTING,
                  QIOProcessingUpgrades.QIO_AUTO_PROCESSING);
        }

        @Override
        public TileComponentUpgrade getComponent() {
            return upgrades;
        }

        @Override
        public int getBlockGuiID(Block block, int metadata) {
            return -1;
        }

        @Override
        public boolean hasCapability(@Nonnull Capability<?> capability,
              @Nullable EnumFacing side) {
            return capability == QIOAutomationCapabilities.AUTOMATION_HOST ||
                  super.hasCapability(capability, side);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T getCapability(@Nonnull Capability<T> capability,
              @Nullable EnumFacing side) {
            return capability == QIOAutomationCapabilities.AUTOMATION_HOST ?
                  (T) host : super.getCapability(capability, side);
        }
    }

    private static void delete(File file) throws Exception {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) delete(child);
            }
        }
        Files.deleteIfExists(file.toPath());
    }

    @SuppressWarnings("unchecked")
    private static <T> Capability<T> registeredCapability(Class<T> type)
          throws ReflectiveOperationException {
        Field providers = net.minecraftforge.common.capabilities.CapabilityManager.class
              .getDeclaredField("providers");
        providers.setAccessible(true);
        Map<String, Capability<?>> registered = (Map<String, Capability<?>>) providers.get(
              net.minecraftforge.common.capabilities.CapabilityManager.INSTANCE);
        return (Capability<T>) registered.get(type.getName().intern());
    }
}
