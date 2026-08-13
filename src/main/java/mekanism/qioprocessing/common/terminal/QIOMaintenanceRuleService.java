package mekanism.qioprocessing.common.terminal;

import mekanism.common.config.MekanismConfig;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceRule;
import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceRuleCatalog;
import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceRuleMutation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/** Session-bound pagination and compare-and-set mutations for maintenance rules. */
public final class QIOMaintenanceRuleService {

    public enum MutationStatus {
        APPLIED,
        REVISION_CONFLICT,
        INVALID_TARGET
    }

    private QIOMaintenanceRuleService() {
    }

    @Nonnull
    public static QIOPage<QIOMaintenanceRule> getPage(
          @Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, long currentAccessRevision,
          @Nullable QIOPageCursor cursor, int requestedPageSize) {
        validateSession(session, network, currentAccessRevision);
        QIOMaintenanceRuleCatalog catalog = network.getMaintenanceRules();
        return QIOPagination.page(catalog.getRules(), catalog.getRulesRevision(),
              session.getSessionNonce(), cursor, requestedPageSize,
              MekanismConfig.current().qioProcessing.terminalPageSize.val());
    }

    @Nonnull
    public static MutationResult mutate(@Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, long currentAccessRevision,
          long expectedRulesRevision, @Nonnull QIOMaintenanceRuleMutation mutation,
          @Nonnull java.util.UUID editor, long currentTick) {
        validateSession(session, network, currentAccessRevision);
        Objects.requireNonNull(mutation, "mutation");
        Objects.requireNonNull(editor, "editor");
        QIOMaintenanceRuleCatalog catalog = network.getMaintenanceRules();
        if (expectedRulesRevision < 0 || catalog.getRulesRevision() != expectedRulesRevision) {
            return result(MutationStatus.REVISION_CONFLICT, catalog,
                  mutation.getRuleId());
        }
        try {
            QIOMaintenanceRule authoritative;
            switch (mutation.getKind()) {
                case CREATE -> {
                    java.util.List<QIOMaintenanceRule> existing = catalog.groupedRules().get(
                          mutation.getResource());
                    if (existing == null || existing.isEmpty()) {
                        authoritative = network.createMaintenanceRule(
                              mutation.getResource(), editor, mutation.isEnabled(),
                              mutation.getTriggerAmount(), mutation.getTargetAmount(),
                              mutation.getMaximumSingleRequest(), mutation.getJobPriority(),
                              mutation.getRetryIntervalTicks(), currentTick,
                              MekanismConfig.current().qioProcessing.
                                    maintenanceRulesPerFrequency.val());
                    } else {
                        if (existing.size() != 1) {
                            throw new IllegalStateException(
                                  "Maintenance resource has duplicate rules");
                        }
                        QIOMaintenanceRule current = existing.get(0);
                        network.updateMaintenanceRule(current.getRuleId(),
                              current.getRuleRevision(), editor, mutation.isEnabled(),
                              mutation.getTriggerAmount(), mutation.getTargetAmount(),
                              mutation.getMaximumSingleRequest(), mutation.getJobPriority(),
                              mutation.getRetryIntervalTicks(), currentTick);
                        authoritative = catalog.get(current.getRuleId());
                    }
                }
                case UPDATE -> {
                    network.updateMaintenanceRule(mutation.getRuleId(),
                          mutation.getExpectedRuleRevision(), editor,
                          mutation.isEnabled(), mutation.getTriggerAmount(),
                          mutation.getTargetAmount(), mutation.getMaximumSingleRequest(),
                          mutation.getJobPriority(), mutation.getRetryIntervalTicks(),
                          currentTick);
                    authoritative = catalog.get(mutation.getRuleId());
                }
                case DELETE -> {
                    network.removeMaintenanceRule(mutation.getRuleId(),
                          mutation.getExpectedRuleRevision());
                    authoritative = null;
                }
                default -> throw new IllegalStateException("Unknown maintenance mutation");
            }
            catalog.requestImmediateEvaluation();
            network.markMaintenanceRuntimeChanged();
            return new MutationResult(MutationStatus.APPLIED,
                  catalog.getRulesRevision(), authoritative);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return result(MutationStatus.INVALID_TARGET, catalog, mutation.getRuleId());
        }
    }

    private static MutationResult result(MutationStatus status,
          QIOMaintenanceRuleCatalog catalog, @Nullable java.util.UUID ruleId) {
        return new MutationResult(status, catalog.getRulesRevision(), catalog.get(ruleId));
    }

    private static void validateSession(QIOProcessingTerminalSession session,
          QIOProcessingNetworkData network, long currentAccessRevision) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(network, "network");
        if (!session.isOpen()) {
            throw new IllegalStateException("QIO terminal session is closed");
        }
        if (session.getTerminalType() != QIOProcessingTerminalType.MAINTENANCE) {
            throw new SecurityException("This terminal session cannot manage maintenance rules");
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

    public static final class MutationResult {

        private final MutationStatus status;
        private final long rulesRevision;
        @Nullable
        private final QIOMaintenanceRule authoritativeRule;

        private MutationResult(MutationStatus status, long rulesRevision,
              @Nullable QIOMaintenanceRule authoritativeRule) {
            this.status = Objects.requireNonNull(status, "status");
            this.rulesRevision = rulesRevision;
            this.authoritativeRule = authoritativeRule;
        }

        @Nonnull
        public MutationStatus getStatus() {
            return status;
        }

        public long getRulesRevision() {
            return rulesRevision;
        }

        @Nullable
        public QIOMaintenanceRule getAuthoritativeRule() {
            return authoritativeRule;
        }
    }
}
