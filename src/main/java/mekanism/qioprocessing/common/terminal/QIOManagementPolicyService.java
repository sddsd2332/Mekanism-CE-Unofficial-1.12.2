package mekanism.qioprocessing.common.terminal;

import mekanism.common.config.MekanismConfig;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceSnapshot;
import mekanism.qioprocessing.common.content.policy.QIOPolicyCatalog;
import mekanism.qioprocessing.common.content.policy.QIOPolicyEntrySnapshot;
import mekanism.qioprocessing.common.content.policy.QIOPolicyMutation;
import mekanism.qioprocessing.common.planning.QIORecipeCatalogService;
import mekanism.qioprocessing.common.planning.QIOWorkbenchRecipeCatalog;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Session-bound reads and compare-and-set mutations for central management policies. */
public final class QIOManagementPolicyService {

    public static final int MAX_QUERY_LENGTH = 128;

    public enum PageMode {
        CONFIGURED,
        WORKBENCH
    }

    public enum WorkbenchFilter {
        ALL,
        ENABLED,
        DISABLED,
        OVERRIDDEN
    }

    public enum MutationStatus {
        APPLIED,
        UNCHANGED,
        REVISION_CONFLICT,
        INVALID_TARGET
    }

    private QIOManagementPolicyService() {
    }

    @Nonnull
    public static QIOPage<QIOPolicyEntrySnapshot> getPage(
          @Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, long currentAccessRevision,
          @Nullable QIOPageCursor cursor, int requestedPageSize) {
        return getPage(session, network, currentAccessRevision, cursor,
              requestedPageSize,
              MekanismConfig.current().qioProcessing.terminalPageSize.val());
    }

    @Nonnull
    static QIOPage<QIOPolicyEntrySnapshot> getPage(
          @Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, long currentAccessRevision,
          @Nullable QIOPageCursor cursor, int requestedPageSize,
          int maximumPageSize) {
        validateSession(session, network, currentAccessRevision);
        QIOPolicyCatalog policies = network.getPolicies();
        return QIOPagination.page(policies.getPolicyEntryView(), policies.getRevision(),
              session.getSessionNonce(), cursor, requestedPageSize, maximumPageSize);
    }

    @Nonnull
    public static DirectoryPage getDirectoryPage(
          @Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, long currentAccessRevision,
          @Nullable QIOPageCursor cursor, int requestedPageSize,
          @Nonnull PageMode mode, @Nonnull WorkbenchFilter workbenchFilter,
          @Nonnull String query, long expectedRecipeCatalogRevision) {
        return getDirectoryPage(session, network, currentAccessRevision, cursor,
              requestedPageSize,
              MekanismConfig.current().qioProcessing.terminalPageSize.val(), mode,
              workbenchFilter, query, expectedRecipeCatalogRevision);
    }

    @Nonnull
    static DirectoryPage getDirectoryPage(
          @Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, long currentAccessRevision,
          @Nullable QIOPageCursor cursor, int requestedPageSize, int maximumPageSize,
          @Nonnull PageMode mode, @Nonnull WorkbenchFilter workbenchFilter,
          @Nonnull String query, long expectedRecipeCatalogRevision) {
        PageMode checkedMode = Objects.requireNonNull(mode, "mode");
        WorkbenchFilter checkedFilter = Objects.requireNonNull(workbenchFilter,
              "workbenchFilter");
        String checkedQuery = normalizeQuery(query);
        if (checkedMode == PageMode.CONFIGURED) {
            return new DirectoryPage(getPage(session, network, currentAccessRevision,
                  cursor, requestedPageSize, maximumPageSize), -1);
        }
        validateSession(session, network, currentAccessRevision);
        QIORecipeCatalogService.State catalogState =
              QIORecipeCatalogService.INSTANCE.getState(network.getWorkbenchConfiguration());
        if (cursor != null && expectedRecipeCatalogRevision < 0) {
            throw new IllegalArgumentException(
                  "QIO workbench continuation is missing its catalog revision");
        }
        if (expectedRecipeCatalogRevision >= 0 &&
            expectedRecipeCatalogRevision != catalogState.getRevision()) {
            throw new IllegalStateException(
                  "QIO workbench recipe catalog changed between pages");
        }
        QIOPolicyCatalog policies = network.getPolicies();
        List<ResourceLocation> matches = matchingWorkbenchRecipes(
              catalogState.getSnapshot(), policies, checkedFilter, checkedQuery);
        List<QIOPolicyEntrySnapshot> entries = Collections.unmodifiableList(
              new AbstractList<>() {
                  @Override
                  public QIOPolicyEntrySnapshot get(int index) {
                      String recipeId = matches.get(index).toString();
                      return policies.snapshotGlobalRoute(
                            QIOWorkbenchRecipeCatalog.PROVIDER_ID.toString(), recipeId,
                            recipeId);
                  }

                  @Override
                  public int size() {
                      return matches.size();
                  }
              });
        QIOPage<QIOPolicyEntrySnapshot> page = QIOPagination.page(entries,
              policies.getRevision(), session.getSessionNonce(), cursor,
              requestedPageSize, maximumPageSize);
        return new DirectoryPage(page, catalogState.getRevision());
    }

    @Nonnull
    public static MutationResult mutate(
          @Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, long currentAccessRevision,
          long expectedPolicyRevision, @Nonnull QIOPolicyMutation mutation) {
        validateSession(session, network, currentAccessRevision);
        Objects.requireNonNull(mutation, "mutation");
        if (expectedPolicyRevision < 0) {
            throw new IllegalArgumentException("Expected policy revision cannot be negative");
        }
        QIOPolicyCatalog policies = network.getPolicies();
        if (policies.getRevision() != expectedPolicyRevision) {
            return result(MutationStatus.REVISION_CONFLICT, policies, mutation);
        }
        if (!isValidTarget(network, policies, mutation)) {
            return result(MutationStatus.INVALID_TARGET, policies, mutation);
        }
        long before = policies.getRevision();
        try {
            apply(network, mutation);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return result(MutationStatus.INVALID_TARGET, policies, mutation);
        }
        return result(policies.getRevision() == before ? MutationStatus.UNCHANGED :
              MutationStatus.APPLIED, policies, mutation);
    }

    @Nonnull
    public static QIOPolicyEntrySnapshot getDeviceDefault(
          @Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, long currentAccessRevision,
          @Nonnull java.util.UUID deviceUUID) {
        validateSession(session, network, currentAccessRevision);
        java.util.UUID checked = Objects.requireNonNull(deviceUUID, "deviceUUID");
        QIOAutomationDeviceSnapshot device = network.getAutomationDevices().get(checked);
        if (device == null ||
            device.getKind() == QIOAutomationDeviceSnapshot.Kind.TERMINAL) {
            throw new IllegalArgumentException("Unknown or unsupported QIO device");
        }
        return network.getPolicies().snapshotDeviceDefault(checked);
    }

    private static void validateSession(QIOProcessingTerminalSession session,
          QIOProcessingNetworkData network, long currentAccessRevision) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(network, "network");
        if (!session.isOpen()) {
            throw new IllegalStateException("QIO terminal session is closed");
        }
        if (session.getTerminalType() != QIOProcessingTerminalType.MANAGEMENT) {
            throw new SecurityException("This terminal session cannot manage policies");
        }
        if (session.getFrequencyUUID() == null ||
            !session.getFrequencyUUID().equals(network.getFrequencyUUID())) {
            throw new SecurityException("QIO terminal session targets another frequency");
        }
        if (currentAccessRevision < 0 ||
            session.getAccessRevision() != currentAccessRevision) {
            throw new IllegalStateException("QIO frequency access changed during the session");
        }
    }

    private static boolean isValidTarget(QIOProcessingNetworkData network,
          QIOPolicyCatalog policies, QIOPolicyMutation mutation) {
        if (!isValidWorkbenchTarget(network, policies, mutation)) {
            return false;
        }
        if (mutation.getDeviceUUID() == null) {
            return true;
        }
        QIOAutomationDeviceSnapshot device = network.getAutomationDevices().get(
              mutation.getDeviceUUID());
        if (device != null) {
            return device.getKind() != QIOAutomationDeviceSnapshot.Kind.TERMINAL;
        }
        // A forgotten/offline device may clear or revise an already-owned central override,
        // but a forged UUID cannot allocate a fresh record in this frequency.
        return policies.hasPoliciesForDevice(mutation.getDeviceUUID());
    }

    private static boolean isValidWorkbenchTarget(QIOProcessingNetworkData network,
          QIOPolicyCatalog policies,
          QIOPolicyMutation mutation) {
        QIOPolicyCatalog.RouteIdentity route = mutation.getRoute();
        if (route == null || !QIOWorkbenchRecipeCatalog.PROVIDER_ID.toString().equals(
              route.getProviderId())) {
            return true;
        }
        if (!route.getRouteId().equals(route.getRecipeKey())) {
            return false;
        }
        boolean exists;
        try {
            exists = QIORecipeCatalogService.INSTANCE.getState(network.getWorkbenchConfiguration())
                  .getSnapshot()
                  .containsRecipe(route.getRecipeKey());
        } catch (IllegalStateException ignored) {
            exists = false;
        }
        if (exists) {
            return true;
        }
        boolean clearsOverride = mutation.getToggle() == QIOPolicyCatalog.Toggle.INHERIT &&
              mutation.getRoutePriority() == null &&
              mutation.getPassiveRoutePriority() == null;
        if (!clearsOverride) {
            return false;
        }
        if (mutation.getDeviceUUID() == null) {
            return policies.getGlobalRoutePolicy(route.getProviderId(), route.getRouteId(),
                  route.getRecipeKey()) != null;
        }
        return policies.getDeviceRoutePolicy(mutation.getDeviceUUID(),
              route.getProviderId(), route.getRouteId(), route.getRecipeKey()) != null;
    }

    private static List<ResourceLocation> matchingWorkbenchRecipes(
          QIOWorkbenchRecipeCatalog.Snapshot catalog, QIOPolicyCatalog policies,
          WorkbenchFilter filter, String query) {
        List<ResourceLocation> matches = new ArrayList<>();
        String providerId = QIOWorkbenchRecipeCatalog.PROVIDER_ID.toString();
        for (ResourceLocation recipeId : catalog.getRecipeIds()) {
            String id = recipeId.toString();
            if (!query.isEmpty() && !id.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            if (filter != WorkbenchFilter.ALL) {
                QIOPolicyEntrySnapshot policy = policies.snapshotGlobalRoute(providerId,
                      id, id);
                boolean accepts = switch (filter) {
                    case ALL -> true;
                    case ENABLED -> policy.isEffectiveEnabled();
                    case DISABLED -> !policy.isEffectiveEnabled();
                    case OVERRIDDEN -> policy.getConfiguredToggle() !=
                          QIOPolicyCatalog.Toggle.INHERIT ||
                          policy.getConfiguredRoutePriority() != null ||
                          policy.getConfiguredPassivePriority() != null;
                };
                if (!accepts) {
                    continue;
                }
            }
            matches.add(recipeId);
        }
        return matches;
    }

    private static String normalizeQuery(String query) {
        String checked = Objects.requireNonNull(query, "query").trim();
        if (checked.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("QIO workbench query is too long");
        }
        return checked.toLowerCase(Locale.ROOT);
    }

    private static void apply(QIOProcessingNetworkData network,
          QIOPolicyMutation mutation) {
        QIOPolicyCatalog.RouteIdentity route = mutation.getRoute();
        switch (mutation.getKind()) {
            case GLOBAL_DEFAULT -> network.setGlobalDefaultPolicy(mutation.getToggle(),
                  mutation.getRoutePriority(), mutation.getPassiveRoutePriority());
            case GLOBAL_ROUTE -> network.setGlobalRoutePolicy(route.getProviderId(),
                  route.getRouteId(), route.getRecipeKey(), mutation.getToggle(),
                  mutation.getRoutePriority(), mutation.getPassiveRoutePriority());
            case DEVICE_DEFAULT -> network.setDeviceDefaultPolicy(mutation.getDeviceUUID(),
                  mutation.getToggle(), mutation.getMachinePriority(),
                  mutation.getPassivePriority());
            case DEVICE_ROUTE -> network.setDeviceRoutePolicy(mutation.getDeviceUUID(),
                  route.getProviderId(), route.getRouteId(), route.getRecipeKey(),
                  mutation.getToggle(), mutation.getRoutePriority(),
                  mutation.getPassiveRoutePriority());
        }
    }

    private static MutationResult result(MutationStatus status,
          QIOPolicyCatalog policies, QIOPolicyMutation mutation) {
        return new MutationResult(status, policies.getRevision(),
              mutation.snapshot(policies));
    }

    public static final class MutationResult {

        private final MutationStatus status;
        private final long policyRevision;
        private final QIOPolicyEntrySnapshot authoritativeEntry;

        private MutationResult(MutationStatus status, long policyRevision,
              QIOPolicyEntrySnapshot authoritativeEntry) {
            this.status = Objects.requireNonNull(status, "status");
            this.policyRevision = policyRevision;
            this.authoritativeEntry = Objects.requireNonNull(authoritativeEntry,
                  "authoritativeEntry");
        }

        @Nonnull
        public MutationStatus getStatus() {
            return status;
        }

        public long getPolicyRevision() {
            return policyRevision;
        }

        @Nonnull
        public QIOPolicyEntrySnapshot getAuthoritativeEntry() {
            return authoritativeEntry;
        }
    }

    public static final class DirectoryPage {

        private final QIOPage<QIOPolicyEntrySnapshot> page;
        private final long recipeCatalogRevision;

        private DirectoryPage(QIOPage<QIOPolicyEntrySnapshot> page,
              long recipeCatalogRevision) {
            this.page = Objects.requireNonNull(page, "page");
            this.recipeCatalogRevision = recipeCatalogRevision;
        }

        @Nonnull
        public QIOPage<QIOPolicyEntrySnapshot> getPage() {
            return page;
        }

        public long getRecipeCatalogRevision() {
            return recipeCatalogRevision;
        }
    }
}
