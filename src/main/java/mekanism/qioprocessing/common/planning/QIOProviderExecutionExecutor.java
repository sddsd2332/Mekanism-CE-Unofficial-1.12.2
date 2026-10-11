package mekanism.qioprocessing.common.planning;

import mekanism.api.processing.*;
import mekanism.api.qio.external.*;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.machine.QIOAutomationCapabilities;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Executes one persisted Provider route operation against a live server-thread machine.
 */
public final class QIOProviderExecutionExecutor {
    public enum Status {
        ADVANCED, WAITING, COMPLETED, CANCELLED, AWAITING_AUTHORIZATION,
        NEEDS_RECONCILIATION, FAILED
    }

    public static final class Result {
        private final Status status;
        private final QIOProviderExecutionState state;

        private Result(Status status, QIOProviderExecutionState state) {
            this.status = status;
            this.state = state;
        }

        public Status status() {
            return status;
        }

        public QIOProviderExecutionState state() {
            return state;
        }
    }

    private static final String OWNER = QIOPlanClaimIntent.OWNER_NAMESPACE;
    private final Thread owner = Thread.currentThread();
    private final QIOClaimOrderStore orderStore;
    private final QIOProviderExecutionStore executionStore;
    private final QIOProviderRouteCatalog catalog;
    private final MinecraftServer server;

    public QIOProviderExecutionExecutor(QIOClaimOrderStore orderStore,
                                        QIOProviderExecutionStore executionStore, QIOProviderRouteCatalog catalog,
                                        MinecraftServer server) {
        this.orderStore = Objects.requireNonNull(orderStore, "orderStore");
        this.executionStore = Objects.requireNonNull(executionStore, "executionStore");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.server = Objects.requireNonNull(server, "server");
    }

    public Result executeOne(UUID orderId, IQIOFrequencyStorageAccess access) throws IOException {
        requireOwner();
        QIOClaimOrderIntent intent = orderStore.load(orderId);
        QIOProviderExecutionManifest manifest = intent.providerManifest();
        if (manifest == null) return result(Status.FAILED, null);
        if (orderStore.cancellationRequested(orderId)) {
            QIOProviderExecutionState state = executionStore.exists(orderId) ? executionStore.load(orderId) : null;
            if (state != null && state.status() != QIOProviderExecutionState.Status.COMPLETED) {
                QIOProviderExecutionState reconciled = state.needsReconciliation();
                executionStore.save(reconciled);
                return result(Status.NEEDS_RECONCILIATION, reconciled);
            }
            return result(Status.CANCELLED, state);
        }
        QIOProviderExecutionState state = loadOrPrepare(intent, orderId);
        if (state.status() == QIOProviderExecutionState.Status.COMPLETED) return result(Status.COMPLETED, state);
        if (state.status() == QIOProviderExecutionState.Status.FAILED
                || state.status() == QIOProviderExecutionState.Status.NEEDS_RECONCILIATION) {
            return result(Status.NEEDS_RECONCILIATION, state);
        }
        QIOFrequencyReference reference = intent.frequencyReference();
        if (reference == null || reference.getBindingPlayerUUID() == null)
            return result(Status.AWAITING_AUTHORIZATION, state);
        IQIOStorageView view = access.open(reference, reference.getBindingPlayerUUID());
        if (view == null) return result(Status.AWAITING_AUTHORIZATION, state);
        try {
            if (!view.isValid() || !intent.frequencyId().equals(view.getFrequencyUUID())) {
                return result(Status.AWAITING_AUTHORIZATION, state);
            }
            Live live = state.operation() == null ? null : locate(state.operation(), manifest,
                    reference.getFrequencyUUID());
            if (live == null) {
                QIOProviderExecutionState reconciled = reconcile(orderId, state);
                return result(Status.NEEDS_RECONCILIATION, reconciled);
            }
            QIOProviderExecutionState next = state;
            switch (state.status()) {
                case LOADING:
                    next = loadInputs(intent, manifest, state, live, view);
                    break;
                case PROCESSING:
                    next = observeProcessing(intent, manifest, state, live);
                    break;
                case COLLECTING:
                    next = collectOutputs(intent, manifest, state, live, view);
                    break;
                default:
                    break;
            }
            executionStore.save(next);
            if (next.status() == QIOProviderExecutionState.Status.COMPLETED) return result(Status.COMPLETED, next);
            if (next.status() == QIOProviderExecutionState.Status.NEEDS_RECONCILIATION) {
                orderStore.requestCancellation(orderId);
                return result(Status.NEEDS_RECONCILIATION, next);
            }
            return result(next.status() == QIOProviderExecutionState.Status.PROCESSING
                    || next.status() == QIOProviderExecutionState.Status.COLLECTING ? Status.WAITING : Status.ADVANCED, next);
        } finally {
            view.close();
        }
    }

    private QIOProviderExecutionState loadOrPrepare(QIOClaimOrderIntent intent, UUID orderId) throws IOException {
        if (executionStore.exists(orderId)) {
            QIOProviderExecutionState existing = executionStore.load(orderId);
            return existing.status() == QIOProviderExecutionState.Status.READY
                    ? prepareOperation(intent, existing) : existing;
        }
        QIOProviderExecutionState state = QIOProviderExecutionState.ready(orderId);
        if (intent.providerManifest().steps().isEmpty()) return reconcile(orderId, state);
        executionStore.saveNew(state);
        return prepareOperation(intent, state);
    }

    private QIOProviderExecutionState prepareOperation(QIOClaimOrderIntent intent,
                                                       QIOProviderExecutionState state) throws IOException {
        QIOProviderExecutionManifest manifest = intent.providerManifest();
        if (state.nextStep() >= manifest.steps().size()) return finish(intent, state, null);
        QIOProviderExecutionManifest.Step step = manifest.steps().get(state.nextStep());
        if (state.nextOperation() >= step.operations()) return reconcile(intent.orderId(), state);
        QIOFrequencyReference reference = intent.frequencyReference();
        Live live = reference == null ? null : locate(step, manifest, reference.getFrequencyUUID());
        if (live == null) return reconcile(intent.orderId(), state);
        Map<String, MachinePortBaseline> baselines = new LinkedHashMap<>();
        for (MachinePort port : live.ports) baselines.put(port.portId(), MachinePortBaseline.capture(port));
        long lane = MachinePort.SHARED_LANE;
        if (!step.inputs().isEmpty()) {
            MachinePort input = live.portsById.get(step.inputs().get(0).portId());
            if (input != null) lane = input.laneId();
        }
        UUID operationId = UUID.nameUUIDFromBytes((intent.orderId() + ":provider-operation:"
                + state.nextStep() + ":" + state.nextOperation()).getBytes(StandardCharsets.UTF_8));
        MachineOperationToken token = MachineOperationToken.allocate(operationId, step.sourceId(),
                step.routeId(), step.recipeKey(), step.configurationRevision(), lane);
        QIOProviderExecutionState.Operation operation = new QIOProviderExecutionState.Operation(
                operationId, state.nextStep(), state.nextOperation(), token, baselines, new LinkedHashMap<>());
        QIOProviderExecutionState next = state.loading(operation);
        executionStore.save(next);
        return next;
    }

    private QIOProviderExecutionState loadInputs(QIOClaimOrderIntent intent,
                                                 QIOProviderExecutionManifest manifest, QIOProviderExecutionState state,
                                                 Live live, IQIOStorageView view) throws IOException {
        QIOProviderExecutionManifest.Step step = manifest.steps().get(state.operation().stepIndex());
        Map<QIOResourceDescriptor, Long> claimRemaining = claimRemainingBefore(intent, state.operation());
        if (claimRemaining == null) return state.needsReconciliation();
        QIOProviderExecutionState current = state;
        for (MachineResourceStack input : perOperation(step.inputs(), step.operations())) {
            MachinePort port = live.portsById.get(input.portId());
            MachinePortBaseline baseline = current.operation().portBaselines().get(input.portId());
            if (port == null || baseline == null) return current.needsReconciliation();
            if (baseline.matchesAfterInsertion(port, input)) {
                consumeClaim(claimRemaining, input.descriptor(), input.amount());
                continue;
            }
            if (!baseline.matches(port)) return current.needsReconciliation();
            long claimAmount = Math.min(input.amount(), claimRemaining.getOrDefault(input.descriptor(), 0L));
            long extractAmount = input.amount() - claimAmount;
            if (claimAmount > 0) {
                UUID resourceId = providerResource(intent, input.descriptor());
                if (resourceId == null) return current.needsReconciliation();
                UUID transferId = derived(current.operation().operationId(), "claim:" + input.portId() + ":" + input.order());
                if (!current.operation().token().hasTransferReceipt(transferId)) {
                    QIOStorageEntry entry = view.getResource(resourceId);
                    QIOStorageSnapshot snapshot = view.getSnapshot();
                    if (entry == null || snapshot == null || !descriptorMatches(entry, input.descriptor()))
                        return current.needsReconciliation();
                    Map<UUID, Long> amounts = singleton(resourceId, claimAmount);
                    Map<UUID, BigInteger> baselines = singletonBig(resourceId, entry.getExactStoredAmount());
                    QIOClaimRequest request = QIOClaimRequest.consumeReconciled(transferId, transferId,
                            intent.claimId(), OWNER, intent.orderId().toString(), snapshot.getContentsRevision(),
                            snapshot.getClaimRevision(), amounts, baselines);
                    QIOClaimResult result = view.submitClaim(request);
                    if (!transferId.equals(result.getRequestId()) || !result.isSuccess())
                        return current.needsReconciliation();
                    current = saveToken(current, current.operation().token().withTransferReceipt(transferId));
                }
            }
            if (extractAmount > 0) {
                UUID transferId = derived(current.operation().operationId(), "extract:" + input.portId() + ":" + input.order());
                if (!current.operation().token().hasTransferReceipt(transferId)) {
                    QIOStorageEntry entry = findDescriptor(view.getSnapshot(), input.descriptor());
                    if (entry == null || entry.getExactStoredAmount().compareTo(BigInteger.valueOf(extractAmount)) < 0)
                        return current.needsReconciliation();
                    current = saveTransferBaseline(current, transferId, entry.getExactStoredAmount());
                    QIOTransferResult result = view.extractIdempotent(transferId, input.descriptor(), extractAmount,
                            current.operation().transferBaselines().get(transferId));
                    if (!transferSuccess(result, extractAmount)) return current.needsReconciliation();
                    current = saveToken(current, current.operation().token().withTransferReceipt(transferId));
                }
            }
            MachineTransferPlan plan = MachineTransferPlan.create(live.tile);
            plan.addInsert(port, input);
            if (!plan.execute() || !baseline.matchesAfterInsertion(port, input)) return current.needsReconciliation();
            current = savePortBaseline(current, MachinePortBaseline.capture(port));
            UUID machineTransfer = derived(current.operation().operationId(), "machine-input:" + input.portId() + ":" + input.order());
            current = saveToken(current, current.operation().token().withTransferReceipt(machineTransfer));
            consumeClaim(claimRemaining, input.descriptor(), claimAmount);
        }
        for (MachineResourceStack configuration : step.configurationInputs()) {
            MachinePort port = live.portsById.get(configuration.portId());
            MachinePortBaseline baseline = current.operation().portBaselines().get(configuration.portId());
            if (port == null || baseline == null) return current.needsReconciliation();
            if (configurationSatisfied(port, configuration)) continue;
            if (!baseline.matches(port)) return current.needsReconciliation();
            MachineTransferPlan plan = MachineTransferPlan.create(live.tile);
            plan.addInsert(port, configuration);
            if (!plan.execute()) return current.needsReconciliation();
            current = savePortBaseline(current, MachinePortBaseline.capture(port));
        }
        MachineOperationToken token = current.operation().token().transition(MachineOperationToken.State.PROCESSING);
        return current.processing(current.operation().withToken(token));
    }

    private QIOProviderExecutionState observeProcessing(QIOClaimOrderIntent intent,
                                                        QIOProviderExecutionManifest manifest, QIOProviderExecutionState state, Live live) {
        QIOProviderExecutionManifest.Step step = manifest.steps().get(state.operation().stepIndex());
        Map<String, List<MachineResourceStack>> expectedByPort = byPort(perOperation(step.outputs(), step.operations()));
        for (Map.Entry<String, List<MachineResourceStack>> entry : expectedByPort.entrySet()) {
            MachinePort port = live.portsById.get(entry.getKey());
            MachinePortBaseline baseline = state.operation().portBaselines().get(entry.getKey());
            if (port == null || baseline == null) return state.needsReconciliation();
            if (!matchesAfterInsertions(baseline, port, entry.getValue())) {
                if (baseline.matches(port)) return state;
                return state.needsReconciliation();
            }
        }
        MachineOperationToken token = state.operation().token().transition(MachineOperationToken.State.COLLECTING);
        return state.collecting(state.operation().withToken(token));
    }

    private QIOProviderExecutionState collectOutputs(QIOClaimOrderIntent intent,
                                                     QIOProviderExecutionManifest manifest, QIOProviderExecutionState state,
                                                     Live live, IQIOStorageView view) throws IOException {
        QIOProviderExecutionManifest.Step step = manifest.steps().get(state.operation().stepIndex());
        QIOProviderExecutionState current = state;
        List<MachineResourceStack> outputs = perOperation(step.outputs(), step.operations());
        Map<String, List<MachineResourceStack>> remainingByPort = byPort(outputs);
        for (MachineResourceStack output : outputs) {
            MachinePort port = live.portsById.get(output.portId());
            MachinePortBaseline baseline = current.operation().portBaselines().get(output.portId());
            UUID bufferId = derived(current.operation().operationId(), "output-buffer:" + output.portId() + ":" + output.order());
            UUID transferId = derived(current.operation().operationId(), "output:" + output.portId() + ":" + output.order());
            if (port == null || baseline == null) return current.needsReconciliation();
            if (current.operation().token().hasTransferReceipt(transferId)) continue;
            QIOProviderOutputBuffer buffer = current.operation().outputBuffers().get(bufferId);
            if (buffer == null) {
                QIOStorageEntry existing = findDescriptor(view.getSnapshot(), output.descriptor());
                BigInteger expected = existing == null ? BigInteger.ZERO : existing.getExactStoredAmount();
                buffer = QIOProviderOutputBuffer.prepared(bufferId, current.operation().operationId(),
                        baseline, output, expected);
                current = saveOutputBuffer(current, buffer);
            }
            if (buffer.phase() != QIOProviderOutputBuffer.Phase.PREPARED) {
                removeStack(remainingByPort.get(output.portId()), output);
            }
            if (buffer.phase() == QIOProviderOutputBuffer.Phase.PREPARED) {
                if (baseline.matches(port)) {
                    QIOStorageEntry existing = findDescriptor(view.getSnapshot(), output.descriptor());
                    if (existing == null || !buffer.qioBaseline().add(BigInteger.valueOf(output.amount()))
                            .equals(existing.getExactStoredAmount())) return current.needsReconciliation();
                    QIOTransferResult recovered = view.insertIdempotent(transferId, output.descriptor(),
                            output.amount(), buffer.qioBaseline());
                    if (!transferSuccess(recovered, output.amount())) return current.needsReconciliation();
                    current = completeOutputBuffer(current, bufferId, transferId);
                    removeStack(remainingByPort.get(output.portId()), output);
                    continue;
                }
                if (!matchesAfterInsertions(baseline, port, remainingByPort.get(output.portId())))
                    return current.needsReconciliation();
                MachineTransferPlan plan = MachineTransferPlan.create(live.tile);
                plan.addExtract(port, output);
                if (!plan.execute() || !baseline.matches(port)) return current.needsReconciliation();
                current = savePortBaseline(current, MachinePortBaseline.capture(port));
                removeStack(remainingByPort.get(output.portId()), output);
                buffer = buffer.held();
                current = saveOutputBuffer(current, buffer);
            }
            if (buffer.phase() == QIOProviderOutputBuffer.Phase.HELD) {
                buffer = buffer.delivering(transferId);
                current = saveOutputBuffer(current, buffer);
            }
            if (buffer.phase() != QIOProviderOutputBuffer.Phase.DELIVERING
                    || !transferId.equals(buffer.qioTransferId())) return current.needsReconciliation();
            QIOTransferResult result = view.insertIdempotent(transferId, output.descriptor(), output.amount(),
                    buffer.qioBaseline());
            if (!transferSuccess(result, output.amount())) return current.needsReconciliation();
            current = completeOutputBuffer(current, bufferId, transferId);
        }
        MachineOperationToken token = current.operation().token().transition(MachineOperationToken.State.COMPLETED);
        QIOProviderExecutionState completedOperation = current.collecting(current.operation().withToken(token));
        int nextStep = state.operation().stepIndex();
        long nextOperation = state.operation().operationIndex() + 1;
        if (nextOperation >= step.operations()) {
            nextStep++;
            nextOperation = 0;
        }
        QIOProviderExecutionState next = completedOperation.advance(nextStep, nextOperation);
        if (nextStep >= manifest.steps().size()) return finish(intent, next, view);
        return prepareOperation(intent, next);
    }

    private QIOProviderExecutionState finish(QIOClaimOrderIntent intent,
                                             QIOProviderExecutionState state, IQIOStorageView existingView) throws IOException {
        if (existingView != null) {
            QIOResourceClaim claim = existingView.getClaim(intent.claimId());
            if (claim != null) {
                QIOClaimResult result = existingView.submitClaim(intent.releaseRequest());
                if (result.getStatus() != QIOClaimResult.Status.APPLIED
                        && result.getStatus() != QIOClaimResult.Status.NO_CHANGE
                        && result.getStatus() != QIOClaimResult.Status.NOT_FOUND)
                    return reconcile(intent.orderId(), state);
            }
        }
        QIOProviderExecutionState completed = state.completed();
        executionStore.save(completed);
        return completed;
    }

    private QIOProviderExecutionState reconcile(UUID orderId, QIOProviderExecutionState state) throws IOException {
        if (!orderStore.cancellationRequested(orderId)) orderStore.requestCancellation(orderId);
        QIOProviderExecutionState next = state.status() == QIOProviderExecutionState.Status.NEEDS_RECONCILIATION
                ? state : state.needsReconciliation();
        if (executionStore.exists(orderId)) executionStore.save(next);
        else executionStore.saveNew(next);
        return next;
    }

    private QIOProviderExecutionState saveToken(QIOProviderExecutionState state,
                                                MachineOperationToken token) throws IOException {
        QIOProviderExecutionState.Operation operation = state.operation().withToken(token);
        QIOProviderExecutionState next = state.status() == QIOProviderExecutionState.Status.LOADING ? state.loading(operation)
                : state.status() == QIOProviderExecutionState.Status.PROCESSING ? state.processing(operation)
                : state.collecting(operation);
        executionStore.save(next);
        return next;
    }

    private QIOProviderExecutionState saveTransferBaseline(QIOProviderExecutionState state,
                                                           UUID transferId, BigInteger baseline) throws IOException {
        QIOProviderExecutionState.Operation operation = state.operation().withTransferBaseline(transferId, baseline);
        QIOProviderExecutionState next = state.status() == QIOProviderExecutionState.Status.LOADING ? state.loading(operation)
                : state.collecting(operation);
        executionStore.save(next);
        return next;
    }

    private QIOProviderExecutionState savePortBaseline(QIOProviderExecutionState state,
                                                       MachinePortBaseline baseline) throws IOException {
        QIOProviderExecutionState.Operation operation = state.operation().withPortBaseline(baseline);
        QIOProviderExecutionState next = state.status() == QIOProviderExecutionState.Status.LOADING ? state.loading(operation)
                : state.status() == QIOProviderExecutionState.Status.PROCESSING ? state.processing(operation)
                : state.collecting(operation);
        executionStore.save(next);
        return next;
    }

    private QIOProviderExecutionState saveOutputBuffer(QIOProviderExecutionState state,
                                                       QIOProviderOutputBuffer buffer) throws IOException {
        return saveOperation(state, state.operation().withOutputBuffer(buffer));
    }

    private QIOProviderExecutionState completeOutputBuffer(QIOProviderExecutionState state,
                                                           UUID bufferId, UUID transferId) throws IOException {
        QIOProviderExecutionState.Operation operation = state.operation().withoutOutputBuffer(bufferId)
                .withToken(state.operation().token().withTransferReceipt(transferId));
        return saveOperation(state, operation);
    }

    private QIOProviderExecutionState saveOperation(QIOProviderExecutionState state,
                                                    QIOProviderExecutionState.Operation operation) throws IOException {
        QIOProviderExecutionState next = state.status() == QIOProviderExecutionState.Status.LOADING ? state.loading(operation)
                : state.status() == QIOProviderExecutionState.Status.PROCESSING ? state.processing(operation)
                : state.collecting(operation);
        executionStore.save(next);
        return next;
    }

    private Live locate(QIOProviderExecutionState.Operation operation,
                        QIOProviderExecutionManifest manifest, UUID frequencyUUID) {
        return locate(manifest.steps().get(operation.stepIndex()), manifest, frequencyUUID);
    }

    private Live locate(QIOProviderExecutionManifest.Step expected,
                        QIOProviderExecutionManifest manifest, UUID frequencyUUID) {
        int separator = expected.sourceId().lastIndexOf(':');
        if (separator <= 0 || separator == expected.sourceId().length() - 1) return null;
        try {
            int dimension = Integer.parseInt(expected.sourceId().substring(0, separator));
            long packed = Long.parseLong(expected.sourceId().substring(separator + 1));
            World world = server.getWorld(dimension);
            BlockPos pos = BlockPos.fromLong(packed);
            if (world == null || !world.isBlockLoaded(pos)) return null;
            TileEntity tile = world.getTileEntity(pos);
            if (QIOAutomationCapabilities.AUTOMATION_HOST == null ||
                    !tile.hasCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null)) return null;
            QIOAutomationHost host = tile.getCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null);
            QIOFrequencyReference bound = host == null ? null : host.getFrequencyReference();
            if (bound == null || !frequencyUUID.equals(bound.getFrequencyUUID()) ||
                    host.getEnabledMode() != QIOAutomationMode.SCHEDULED ||
                    host.getState() != QIOAutomationHost.State.ACTIVE) return null;
            MachineRecipeProviderRegistry.BoundProvider provider = MachineRecipeProviderRegistry.find(tile);
            if (provider == null || !provider.isAvailable() || !expected.providerId().equals(provider.id().toString())
                    || provider.getConfigurationRevision() != expected.configurationRevision()
                    || !expected.profileScopeDiscriminator().equals(provider.getRecipeProfileScopeDiscriminator()))
                return null;
            ProviderConformanceReport report = provider.validateQIOConformance(QIOAutomationMode.SCHEDULED);
            if (!report.isConformant()) return null;
            Map<String, MachinePort> ports = new LinkedHashMap<>();
            for (MachinePort port : provider.getPorts()) {
                if (ports.putIfAbsent(port.portId(), port) != null) return null;
            }
            MachineRecipeRoute route = null;
            for (MachineRecipeRoute candidate : provider.getRecipeRoutes()) {
                if (expected.routeId().equals(candidate.routeId()) && expected.recipeKey().equals(candidate.recipeKey())) {
                    route = candidate;
                    break;
                }
            }
            if (route == null || !expected.logicalRecipeKey().equals(route.logicalRecipeKey())
                    || !sameStacks(expected.configurationInputs(), route.configurationInputs(), 1)
                    || !sameStacks(expected.inputs(), route.inputs(), expected.operations())
                    || !sameStacks(expected.outputs(), route.guaranteedOutputs(), expected.operations())) return null;
            QIOProviderRouteCatalog.Snapshot snapshot = catalog.snapshot();
            if (snapshot.revision() != manifest.catalogRevision()
                    || snapshot.availabilityRevision() != manifest.availabilityRevision()) return null;
            return new Live(tile, provider, new ArrayList<>(provider.getPorts()), ports, route);
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static boolean sameStacks(List<MachineResourceStack> expected,
                                      List<MachineResourceStack> route, long operations) {
        if (expected.size() != route.size()) return false;
        for (int index = 0; index < expected.size(); index++) {
            MachineResourceStack actual = route.get(index).scale(operations);
            if (!expected.get(index).equals(actual)) return false;
        }
        return true;
    }

    private static List<MachineResourceStack> perOperation(List<MachineResourceStack> values, long operations) {
        List<MachineResourceStack> result = new ArrayList<>();
        for (MachineResourceStack value : values) {
            if (value.amount() % operations != 0)
                throw new IllegalStateException("Provider route amount is not divisible by operations");
            result.add(value.withAmount(value.amount() / operations));
        }
        return result;
    }

    private static Map<String, List<MachineResourceStack>> byPort(List<MachineResourceStack> values) {
        Map<String, List<MachineResourceStack>> result = new LinkedHashMap<>();
        for (MachineResourceStack value : values) {
            result.computeIfAbsent(value.portId(), ignored -> new ArrayList<>()).add(value);
        }
        return result;
    }

    private static void removeStack(List<MachineResourceStack> values, MachineResourceStack target) {
        if (values == null) return;
        for (int index = 0; index < values.size(); index++) {
            MachineResourceStack value = values.get(index);
            if (value.descriptor().equals(target.descriptor()) && value.amount() == target.amount()
                    && value.order() == target.order()) {
                values.remove(index);
                return;
            }
        }
        throw new IllegalStateException("Provider output stack was not present in its port group");
    }

    private static boolean matchesAfterInsertions(MachinePortBaseline baseline, MachinePort port,
                                                  List<MachineResourceStack> insertions) {
        if (insertions == null) return false;
        Map<QIOResourceDescriptor, Long> expected = new LinkedHashMap<>();
        for (MachineResourceStack value : baseline.contents()) {
            expected.merge(value.descriptor(), value.amount(), Math::addExact);
        }
        for (MachineResourceStack value : insertions) {
            if (!baseline.portId().equals(value.portId()) || !baseline.resourceMatcher().accepts(value.descriptor()))
                return false;
            expected.merge(value.descriptor(), value.amount(), Math::addExact);
        }
        Map<QIOResourceDescriptor, Long> actual = new LinkedHashMap<>();
        for (MachineResourceStack value : port.peekAll()) {
            if (value == null || !baseline.portId().equals(value.portId())) return false;
            actual.merge(value.descriptor(), value.amount(), Math::addExact);
        }
        return expected.equals(actual);
    }

    private static boolean configurationSatisfied(MachinePort port, MachineResourceStack expected) {
        long amount = 0;
        for (MachineResourceStack current : port.peekAll()) {
            if (!expected.descriptor().equals(current.descriptor())) return false;
            amount = Math.addExact(amount, current.amount());
        }
        return amount == expected.amount();
    }

    private static Map<QIOResourceDescriptor, Long> claimRemainingBefore(QIOClaimOrderIntent intent,
                                                                         QIOProviderExecutionState.Operation operation) {
        Map<QIOResourceDescriptor, Long> remaining = new LinkedHashMap<>(intent.providerManifest().initialWithdrawals());
        QIOProviderExecutionManifest manifest = intent.providerManifest();
        for (int stepIndex = 0; stepIndex <= operation.stepIndex(); stepIndex++) {
            QIOProviderExecutionManifest.Step step = manifest.steps().get(stepIndex);
            long count = stepIndex == operation.stepIndex() ? operation.operationIndex() : step.operations();
            for (long index = 0; index < count; index++) {
                for (MachineResourceStack input : perOperation(step.inputs(), step.operations()))
                    consumeClaim(remaining, input.descriptor(), input.amount());
            }
        }
        return remaining;
    }

    private static UUID providerResource(QIOClaimOrderIntent intent, QIOResourceDescriptor descriptor) {
        for (Map.Entry<UUID, QIOResourceDescriptor> entry : intent.providerResources().entrySet()) {
            if (descriptor.equals(entry.getValue())) return entry.getKey();
        }
        return null;
    }

    private static void consumeClaim(Map<QIOResourceDescriptor, Long> values,
                                     QIOResourceDescriptor descriptor, long amount) {
        long current = values.getOrDefault(descriptor, 0L);
        long next = current - Math.min(current, amount);
        if (next == 0) values.remove(descriptor);
        else values.put(descriptor, next);
    }

    private static QIOStorageEntry findDescriptor(QIOStorageSnapshot snapshot, QIOResourceDescriptor descriptor) {
        if (snapshot == null) return null;
        QIOStorageEntry found = null;
        for (QIOStorageEntry entry : snapshot.getEntries()) {
            if (descriptor.equals(entry.getDescriptor())) {
                if (found != null) return null;
                found = entry;
            }
        }
        return found;
    }

    private static boolean descriptorMatches(QIOStorageEntry entry, QIOResourceDescriptor descriptor) {
        return entry != null && descriptor.equals(entry.getDescriptor());
    }

    private static boolean transferSuccess(QIOTransferResult result, long amount) {
        return result != null && result.isSuccess() && result.getTransferredAmount() == amount;
    }

    private static Map<UUID, Long> singleton(UUID key, long value) {
        Map<UUID, Long> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }

    private static Map<UUID, BigInteger> singletonBig(UUID key, BigInteger value) {
        Map<UUID, BigInteger> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }

    private static UUID derived(UUID operationId, String suffix) {
        return UUID.nameUUIDFromBytes((operationId + ":" + suffix).getBytes(StandardCharsets.UTF_8));
    }

    private static Result result(Status status, QIOProviderExecutionState state) {
        return new Result(status, state);
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Provider execution requires its owner thread");
    }

    private static final class Live {
        private final TileEntity tile;
        private final MachineRecipeProviderRegistry.BoundProvider provider;
        private final List<MachinePort> ports;
        private final Map<String, MachinePort> portsById;
        private final MachineRecipeRoute route;

        private Live(TileEntity tile, MachineRecipeProviderRegistry.BoundProvider provider,
                     List<MachinePort> ports, Map<String, MachinePort> portsById, MachineRecipeRoute route) {
            this.tile = tile;
            this.provider = provider;
            this.ports = ports;
            this.portsById = portsById;
            this.route = route;
        }
    }
}
