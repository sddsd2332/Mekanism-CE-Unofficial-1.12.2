package mekanism.qioprocessing.common.inventory.container;

import mekanism.qioprocessing.common.network.PacketQIOManagementRecipeData.Status;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot.PageKind;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/** Client-only remote machine-recipe pages keyed by request id and selected device. */
public final class QIOManagementRecipeClientCache {

    @Nullable private UUID expectedProductRequest;
    @Nullable private UUID expectedRouteRequest;
    @Nullable private UUID expectedMutationRequest;
    @Nullable private QIOManagementRecipeSnapshot productPage;
    @Nullable private QIOManagementRecipeSnapshot routePage;
    private Status productStatus = Status.UNAVAILABLE;
    private Status routeStatus = Status.UNAVAILABLE;
    private Status mutationStatus = Status.UNAVAILABLE;
    private long productGeneration;
    private long routeGeneration;
    private long mutationGeneration;

    public void expect(@Nonnull PageKind kind, @Nonnull UUID requestId) {
        if (kind == PageKind.PRODUCTS) {
            expectedProductRequest = requestId;
        } else {
            expectedRouteRequest = requestId;
        }
    }

    public void expectMutation(@Nonnull UUID requestId) {
        expectedMutationRequest = requestId;
    }

    public boolean applyPage(@Nonnull UUID requestId, @Nonnull Status status,
          @Nullable QIOManagementRecipeSnapshot snapshot) {
        PageKind kind = snapshot == null ? null : snapshot.getPageKind();
        if (kind == PageKind.PRODUCTS) {
            if (!requestId.equals(expectedProductRequest)) return false;
            expectedProductRequest = null;
            productStatus = status;
            productPage = status == Status.OK ? snapshot : null;
            if (routePage != null && snapshot != null && !sameSource(routePage, snapshot)) {
                invalidateRoutePage();
            }
            productGeneration = next(productGeneration);
            return true;
        }
        if (kind == PageKind.ROUTES) {
            if (!requestId.equals(expectedRouteRequest)) return false;
            expectedRouteRequest = null;
            routeStatus = status;
            routePage = status == Status.OK ? snapshot : null;
            routeGeneration = next(routeGeneration);
            return true;
        }
        return false;
    }

    public boolean applyUnavailable(@Nonnull PageKind kind, @Nonnull UUID requestId,
          @Nonnull Status status) {
        if (kind == PageKind.PRODUCTS) {
            if (!requestId.equals(expectedProductRequest)) return false;
            expectedProductRequest = null;
            productStatus = status;
            productPage = null;
            invalidateRoutePage();
            productGeneration = next(productGeneration);
            return true;
        }
        if (!requestId.equals(expectedRouteRequest)) return false;
        expectedRouteRequest = null;
        routeStatus = status;
        routePage = null;
        routeGeneration = next(routeGeneration);
        return true;
    }

    public boolean applyMutation(@Nonnull UUID requestId, @Nonnull Status status) {
        if (!requestId.equals(expectedMutationRequest)) return false;
        expectedMutationRequest = null;
        mutationStatus = status;
        if (status == Status.APPLIED || status == Status.REVISION_CONFLICT) {
            clearRecipes();
        }
        mutationGeneration = next(mutationGeneration);
        return true;
    }

    public void clearRecipes() {
        expectedProductRequest = null;
        expectedRouteRequest = null;
        productPage = null;
        routePage = null;
        productStatus = Status.UNAVAILABLE;
        routeStatus = Status.UNAVAILABLE;
    }

    public void clearRoutePage() {
        expectedRouteRequest = null;
        routePage = null;
        routeStatus = Status.UNAVAILABLE;
    }

    public void clear() {
        clearRecipes();
        expectedMutationRequest = null;
        mutationStatus = Status.UNAVAILABLE;
    }

    @Nullable public QIOManagementRecipeSnapshot getProductPage() { return productPage; }
    @Nullable public QIOManagementRecipeSnapshot getRoutePage() { return routePage; }
    @Nonnull public Status getProductStatus() { return productStatus; }
    @Nonnull public Status getRouteStatus() { return routeStatus; }
    @Nonnull public Status getMutationStatus() { return mutationStatus; }
    public long getProductGeneration() { return productGeneration; }
    public long getRouteGeneration() { return routeGeneration; }
    public long getMutationGeneration() { return mutationGeneration; }

    private void invalidateRoutePage() {
        if (routePage != null || expectedRouteRequest != null ||
            routeStatus != Status.UNAVAILABLE) {
            routeGeneration = next(routeGeneration);
        }
        expectedRouteRequest = null;
        routePage = null;
        routeStatus = Status.UNAVAILABLE;
    }

    private static boolean sameSource(QIOManagementRecipeSnapshot first,
          QIOManagementRecipeSnapshot second) {
        return first.getDeviceUUID().equals(second.getDeviceUUID()) &&
              first.getRouteDirectoryRevision() == second.getRouteDirectoryRevision() &&
              first.getProfileRevision() == second.getProfileRevision() &&
              first.getPolicyRevision() == second.getPolicyRevision();
    }

    private static long next(long value) {
        return value == Long.MAX_VALUE ? 0 : value + 1;
    }
}
