package mekanism.qioprocessing.common.machine;

import mekanism.api.processing.MachinePort;
import mekanism.api.processing.MachineRecipeProvider;
import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.api.processing.MachineRecipeRoute;
import mekanism.api.processing.ProviderConformanceDescriptor;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.TestBootstrap;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeConfigType;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeProfileMutation;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfileLayout;
import net.minecraft.init.Items;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOAutomationRecipeConfigServiceTest {

    private static final ResourceLocation PROVIDER_ID =
          new ResourceLocation("test", "qio_recipe_config_cache");

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @AfterEach
    void cleanup() {
        MachineRecipeProviderRegistry.unregister(PROVIDER_ID);
        QIOAutomationRecipeConfigService.clearRouteDirectoryCache();
    }

    @Test
    void routeDirectoryCacheTracksProviderRevision() throws Exception {
        MutableProvider provider = registerProvider();
        MachineRecipeProviderRegistry.BoundProvider bound =
              MachineRecipeProviderRegistry.find(provider.tile);
        UUID deviceUUID = UUID.randomUUID();

        Object first = routeDirectory(bound, deviceUUID);
        Object cached = routeDirectory(bound, deviceUUID);
        assertSame(first, cached);
        assertTrue(contains(cached, "route", "test:first"));

        provider.revision++;
        provider.recipeKey = "test:second";
        Object refreshed = routeDirectory(bound, deviceUUID);
        assertNotSame(first, refreshed);
        assertFalse(contains(refreshed, "route", "test:first"));
        assertTrue(contains(refreshed, "route", "test:second"));
    }

    @Test
    void transientProviderFailureIsNotCachedAsUnavailable() throws Exception {
        MutableProvider provider = registerProvider();
        MachineRecipeProviderRegistry.BoundProvider bound =
              MachineRecipeProviderRegistry.find(provider.tile);
        UUID deviceUUID = UUID.randomUUID();

        provider.failRoutes = true;
        Object unavailable = routeDirectory(bound, deviceUUID);
        assertFalse(contains(unavailable, "route", provider.recipeKey));

        provider.failRoutes = false;
        Object recovered = routeDirectory(bound, deviceUUID);
        assertNotSame(unavailable, recovered);
        assertTrue(contains(recovered, "route", provider.recipeKey));
    }

    @Test
    void twoConfigurationClientsCannotCommitTheSameStaleRevision() throws Exception {
        MutableProvider provider = registerProvider();
        MachineRecipeProviderRegistry.BoundProvider bound =
              MachineRecipeProviderRegistry.find(provider.tile);
        DefaultQIOAutomationHost host = new DefaultQIOAutomationHost(provider.tile);
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(UUID.randomUUID(),
              new QIOFrequencyIdentitySnapshot("config-cas", null, SecurityMode.PUBLIC));
        Object directory = routeDirectory(bound, host.getPersistentDeviceUUID());
        QIOAutomationRecipeConfigService.Context firstClient = context(provider.tile, host,
              network, directory);
        QIOAutomationRecipeConfigService.Context secondClient = context(provider.tile, host,
              network, directory);
        long sharedRevision = network.getAutomationRecipeProfiles().getRevision();
        QIOAutomationRecipeProfileLayout layout = new QIOAutomationRecipeProfileLayout(
              provider.getRecipeRoutes(provider.tile));
        QIOAutomationRecipeProfileLayout.Route route = layout.getRoute(
              layout.getRouteKeys().iterator().next());
        QIOAutomationRecipeProfileMutation first =
              QIOAutomationRecipeProfileMutation.route(
                    QIOAutomationRecipeProfileMutation.Action.TOGGLE_ROUTE,
                    route.getProductKey(), route.getRouteKey());
        QIOAutomationRecipeProfileMutation stale =
              QIOAutomationRecipeProfileMutation.route(
                    QIOAutomationRecipeProfileMutation.Action.TOGGLE_ROUTE,
                    route.getProductKey(), route.getRouteKey());

        assertEquals(QIOAutomationRecipeConfigService.MutationStatus.APPLIED,
              QIOAutomationRecipeConfigService.mutate(firstClient, sharedRevision, first)
                    .getStatus());
        assertEquals(QIOAutomationRecipeConfigService.MutationStatus.REVISION_CONFLICT,
              QIOAutomationRecipeConfigService.mutate(secondClient, sharedRevision, stale)
                    .getStatus());
        assertFalse(network.getAutomationRecipeProfiles().getActiveProfile(
              host.getPersistentDeviceUUID(), QIOAutomationMode.SCHEDULED,
              QIOAutomationRecipeProfileScope.resolve(bound)).isRouteEnabled(
                    route.getRouteKey()));
    }

    private static MutableProvider registerProvider() {
        MachineRecipeProviderRegistry.unregister(PROVIDER_ID);
        QIOAutomationRecipeConfigService.clearRouteDirectoryCache();
        MutableProvider provider = new MutableProvider();
        MachineRecipeProviderRegistry.register(PROVIDER_ID, TestTile.class, provider);
        return provider;
    }

    private static Object routeDirectory(MachineRecipeProviderRegistry.BoundProvider provider,
          UUID deviceUUID) throws Exception {
        Method method = QIOAutomationRecipeConfigService.class.getDeclaredMethod(
              "routeDirectory", MachineRecipeProviderRegistry.BoundProvider.class,
              UUID.class, QIOAutomationMode.class);
        method.setAccessible(true);
        return method.invoke(null, provider, deviceUUID, QIOAutomationMode.SCHEDULED);
    }

    private static boolean contains(Object directory, String routeId, String recipeKey)
          throws Exception {
        Method method = directory.getClass().getDeclaredMethod("contains",
              String.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(directory, routeId, recipeKey);
    }

    private static QIOAutomationRecipeConfigService.Context context(TileEntity tile,
          QIOAutomationHost host, QIOProcessingNetworkData network, Object directory)
          throws Exception {
        Constructor<?> constructor =
              QIOAutomationRecipeConfigService.Context.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object[] arguments = new Object[constructor.getParameterCount()];
        arguments[0] = (EntityPlayer) null;
        arguments[1] = tile;
        arguments[2] = host;
        arguments[3] = (QIOFrequencyReference) null;
        arguments[4] = network;
        arguments[5] = QIOAutomationRecipeConfigType.SCHEDULED;
        arguments[6] = directory;
        arguments[7] = true;
        return (QIOAutomationRecipeConfigService.Context) constructor.newInstance(arguments);
    }

    private static final class TestTile extends TileEntity {

        private final BasicInventorySlot input = BasicInventorySlot.at(null, 0, 0);
        private final BasicInventorySlot output = BasicInventorySlot.at(null, 0, 0);
    }

    private static final class MutableProvider implements MachineRecipeProvider<TestTile> {

        private final TestTile tile = new TestTile();
        private int revision;
        private String recipeKey = "test:first";
        private boolean failRoutes;

        @Override
        public int getConfigurationRevision(TestTile tile) {
            return revision;
        }

        @Override
        public List<MachineRecipeRoute> getRecipeRoutes(TestTile tile) {
            if (failRoutes) {
                throw new IllegalStateException("transient route failure");
            }
            return java.util.Collections.singletonList(MachineRecipeRoute.builder("route")
                  .recipeKey(recipeKey)
                  .inputItem("input", new ItemStack(Items.IRON_INGOT))
                  .outputItem("output", new ItemStack(Items.GOLD_INGOT))
                  .build());
        }

        @Override
        public List<MachinePort> getPorts(TestTile tile) {
            return Arrays.asList(
                  MachinePort.item("input", MachinePort.Role.INPUT, tile.input),
                  MachinePort.item("output", MachinePort.Role.OUTPUT, tile.output));
        }

        @Override
        public ProviderConformanceDescriptor getQIOConformance(TestTile tile) {
            return ProviderConformanceDescriptor.builder("test", "recipe_config_cache")
                  .supports(QIOAutomationMode.SCHEDULED).build();
        }
    }
}
