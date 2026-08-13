package mekanism.qioprocessing.common.inventory.container;

import mekanism.api.processing.QIOAutomationMode;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfile.RouteFilterMode;
import mekanism.qioprocessing.common.network.PacketQIOManagementRecipeData.Status;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot.PageKind;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot.Product;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot.ResourceAmount;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot.Route;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOManagementRecipeClientCacheTest {

    @Test
    void requestIdsRejectStalePagesAndAppliedMutationInvalidatesBothColumns() {
        UUID deviceUUID = UUID.randomUUID();
        QIOManagementRecipeClientCache cache = new QIOManagementRecipeClientCache();
        QIOManagementRecipeSnapshot products = snapshot(PageKind.PRODUCTS, deviceUUID,
              2, Collections.singletonList(product()), Collections.emptyList());
        QIOManagementRecipeSnapshot routes = snapshot(PageKind.ROUTES, deviceUUID,
              2, Collections.emptyList(), Collections.singletonList(route()));

        UUID productRequest = UUID.randomUUID();
        cache.expect(PageKind.PRODUCTS, productRequest);
        assertFalse(cache.applyPage(UUID.randomUUID(), Status.OK, products));
        assertTrue(cache.applyPage(productRequest, Status.OK, products));
        assertSame(products, cache.getProductPage());

        UUID routeRequest = UUID.randomUUID();
        cache.expect(PageKind.ROUTES, routeRequest);
        assertTrue(cache.applyPage(routeRequest, Status.OK, routes));
        assertSame(routes, cache.getRoutePage());

        UUID mutationRequest = UUID.randomUUID();
        cache.expectMutation(mutationRequest);
        assertTrue(cache.applyMutation(mutationRequest, Status.APPLIED));
        assertNull(cache.getProductPage());
        assertNull(cache.getRoutePage());
    }

    @Test
    void newerProductRevisionInvalidatesAnOlderRoutePage() {
        UUID deviceUUID = UUID.randomUUID();
        QIOManagementRecipeClientCache cache = new QIOManagementRecipeClientCache();
        UUID routeRequest = UUID.randomUUID();
        cache.expect(PageKind.ROUTES, routeRequest);
        assertTrue(cache.applyPage(routeRequest, Status.OK,
              snapshot(PageKind.ROUTES, deviceUUID, 2, Collections.emptyList(),
                    Collections.singletonList(route()))));

        UUID productRequest = UUID.randomUUID();
        cache.expect(PageKind.PRODUCTS, productRequest);
        assertTrue(cache.applyPage(productRequest, Status.OK,
              snapshot(PageKind.PRODUCTS, deviceUUID, 3,
                    Collections.singletonList(product()), Collections.emptyList())));
        assertNull(cache.getRoutePage());
    }

    private static QIOManagementRecipeSnapshot snapshot(PageKind kind,
          UUID deviceUUID, long profileRevision, java.util.List<Product> products,
          java.util.List<Route> routes) {
        return new QIOManagementRecipeSnapshot(kind, deviceUUID,
              QIOAutomationMode.SCHEDULED, "test:provider", "test:scope",
              1, profileRevision, 1, true, 1, RouteFilterMode.BLACKLIST, true,
              1, 64, 0, 1, "", kind == PageKind.ROUTES ? "product:gold" : "",
              products, routes);
    }

    private static Product product() {
        return new Product("product:gold", new ResourceAmount(gold(), 1), 1, 1, 1, 0);
    }

    private static Route route() {
        return new Route("product:gold", "route:iron_to_gold", "route:item_to_item",
              "test:iron_to_gold",
              Collections.singletonList(new ResourceAmount(PortableResourceDescriptor.named(
                    PortableResourceDescriptor.Kind.ITEM, "minecraft:iron_ingot", 0,
                    null), 1)),
              Collections.singletonList(new ResourceAmount(gold(), 1)),
              Collections.emptyList(), true, true, 0, 1, 64, false);
    }

    private static PortableResourceDescriptor gold() {
        return PortableResourceDescriptor.named(PortableResourceDescriptor.Kind.ITEM,
              "minecraft:gold_ingot", 0, null);
    }
}
