package mekanism.qioprocessing.common.planning;

import mekanism.api.qio.external.*;
import mekanism.qioprocessing.common.pattern.WorkbenchPatternValidator;
import mekanism.qioprocessing.common.pattern.WorkbenchValidationResult;
import mekanism.qioprocessing.common.processor.QIOCraftingProcessorEndpointDirectory;
import mekanism.qioprocessing.common.processor.QIOCraftingProcessorState;
import mekanism.qioprocessing.common.recipe.RecipeCatalog;
import mekanism.qioprocessing.common.recipe.RecipeDeclaration;
import mekanism.qioprocessing.common.recipe.RecipeItemKey;
import mekanism.qioprocessing.common.workbench.WorkbenchSnapshot;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Executes one persisted workbench operation at a time with replayable QIO mutations.
 */
public final class QIOWorkbenchExecutionExecutor {
    public enum Status {
        ADVANCED, WAITING, COMPLETED, CANCELLED, LEGACY_ORDER, STALE_CONFIGURATION,
        AWAITING_AUTHORIZATION, NEEDS_RECONCILIATION, FAILED
    }

    public static final class Result {
        private final Status status;
        private final QIOWorkbenchExecutionState state;

        private Result(Status status, QIOWorkbenchExecutionState state) {
            this.status = status;
            this.state = state;
        }

        public Status status() {
            return status;
        }

        public QIOWorkbenchExecutionState state() {
            return state;
        }
    }

    private final Thread owner = Thread.currentThread();
    private final QIOClaimOrderStore orderStore;
    private final QIOWorkbenchExecutionStore executionStore;
    private final Map<UUID, LivePatternValidation> livePatternCache = new HashMap<>();

    public QIOWorkbenchExecutionExecutor(QIOClaimOrderStore orderStore,
                                         QIOWorkbenchExecutionStore executionStore) {
        this.orderStore = Objects.requireNonNull(orderStore, "orderStore");
        this.executionStore = Objects.requireNonNull(executionStore, "executionStore");
    }

    public Result executeOne(UUID orderId, WorkbenchSnapshot book, RecipeCatalog catalog,
                             IQIOFrequencyStorageAccess access) throws IOException {
        return executeOne(orderId, book, catalog, access, null);
    }

    public Result executeOne(UUID orderId, WorkbenchSnapshot book, RecipeCatalog catalog,
                             IQIOFrequencyStorageAccess access, World world) throws IOException {
        requireOwner();
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(access, "access");
        QIOClaimOrderIntent intent = orderStore.load(orderId);
        if (orderStore.cancellationRequested(orderId)) {
            if (executionStore.exists(orderId)) {
                QIOWorkbenchExecutionState existing = executionStore.load(orderId);
                if (existing.status() == QIOWorkbenchExecutionState.Status.IN_FLIGHT) {
                    QIOWorkbenchExecutionState reconciled = existing.needsReconciliation();
                    executionStore.save(reconciled);
                    livePatternCache.remove(orderId);
                    return result(Status.NEEDS_RECONCILIATION, reconciled);
                }
            }
            return result(Status.CANCELLED, null);
        }
        if (intent.manifest() == null) return result(Status.LEGACY_ORDER, null);
        QIOWorkbenchExecutionState existing = executionStore.exists(orderId) ? executionStore.load(orderId) : null;
        if (existing != null && existing.status() == QIOWorkbenchExecutionState.Status.COMPLETED) {
            livePatternCache.remove(orderId);
            return result(Status.COMPLETED, existing);
        }
        if (existing != null && existing.status() == QIOWorkbenchExecutionState.Status.FAILED) {
            livePatternCache.remove(orderId);
            return result(Status.FAILED, existing);
        }
        if (existing != null && existing.status() == QIOWorkbenchExecutionState.Status.NEEDS_RECONCILIATION) {
            livePatternCache.remove(orderId);
            return result(Status.NEEDS_RECONCILIATION, existing);
        }
        if (world != null && !livePatternsValid(orderId, intent, catalog, world)) {
            QIOWorkbenchExecutionState reconciled = persistReconciliation(orderId, existing);
            return result(Status.NEEDS_RECONCILIATION, reconciled);
        }
        QIOWorkbenchExecutionState state = loadOrPrepare(intent, orderId, book, catalog, access);
        if (state == null) return result(Status.AWAITING_AUTHORIZATION, null);
        if (state.status() == QIOWorkbenchExecutionState.Status.COMPLETED) return result(Status.COMPLETED, state);
        if (state.status() == QIOWorkbenchExecutionState.Status.FAILED) return result(Status.FAILED, state);
        if (state.status() == QIOWorkbenchExecutionState.Status.NEEDS_RECONCILIATION) {
            livePatternCache.remove(orderId);
            return result(Status.NEEDS_RECONCILIATION, state);
        }

        QIOFrequencyReference reference = intent.frequencyReference();
        if (reference == null || reference.getBindingPlayerUUID() == null)
            return result(Status.AWAITING_AUTHORIZATION, state);
        IQIOStorageView view = access.open(reference, reference.getBindingPlayerUUID());
        if (view == null) return result(Status.AWAITING_AUTHORIZATION, state);
        try {
            if (!validView(intent, view)) return result(Status.AWAITING_AUTHORIZATION, state);
            if (state.status() == QIOWorkbenchExecutionState.Status.READY) {
                if (state.nextStep() >= intent.manifest().steps().size()) {
                    return finish(intent, state, view);
                }
                QIOWorkbenchExecutionState.Operation operation = prepareOperation(intent, state, view);
                if (operation == null) {
                    QIOWorkbenchExecutionState failed = persistReconciliation(orderId, state);
                    return result(Status.NEEDS_RECONCILIATION, failed);
                }
                state = state.inFlight(operation);
                executionStore.save(state);
            }
            QIOWorkbenchExecutionState.Operation operation = state.operation();
            if (operation == null) {
                QIOWorkbenchExecutionState failed = persistReconciliation(orderId, state);
                return result(Status.NEEDS_RECONCILIATION, failed);
            }
            if (world == null) {
                if (!applyInputs(intent, operation, view) || !applyOutputs(operation, view)) {
                    QIOWorkbenchExecutionState failed = persistReconciliation(orderId, state);
                    return result(Status.NEEDS_RECONCILIATION, failed);
                }
            } else {
                if (operation.processorAllocation() == null) {
                    int configurationRevision;
                    try {
                        configurationRevision = Math.toIntExact(intent.bookRevision());
                    } catch (ArithmeticException error) {
                        QIOWorkbenchExecutionState failed = persistReconciliation(orderId, state);
                        return result(Status.NEEDS_RECONCILIATION, failed);
                    }
                    java.util.Optional<QIOCraftingProcessorEndpointDirectory.Allocation> allocation =
                            QIOCraftingProcessorEndpointDirectory.INSTANCE.allocate(reference, operation.operationId(),
                                    "qio_workbench", intent.generation(), operation.operationId().toString(),
                                    configurationRevision, 200);
                    if (!allocation.isPresent()) return result(Status.WAITING, state);
                    operation = operation.withProcessorAllocation(allocation.get().record());
                    state = state.inFlight(operation);
                    executionStore.save(state);
                    return result(Status.WAITING, state);
                }
                QIOCraftingProcessorEndpointDirectory.Resolution resolution =
                        QIOCraftingProcessorEndpointDirectory.INSTANCE.resolve(reference,
                                operation.processorAllocation());
                if (resolution.status() == QIOCraftingProcessorEndpointDirectory.ResolutionStatus.CONFLICT
                        || resolution.status() == QIOCraftingProcessorEndpointDirectory.ResolutionStatus.CANCELLED) {
                    QIOWorkbenchExecutionState failed = persistReconciliation(orderId, state);
                    return result(Status.NEEDS_RECONCILIATION, failed);
                }
                if (resolution.status() == QIOCraftingProcessorEndpointDirectory.ResolutionStatus.WAITING_FOR_TILE
                        || resolution.status() == QIOCraftingProcessorEndpointDirectory.ResolutionStatus.PROCESSING) {
                    return result(Status.WAITING, state);
                }
                if (resolution.status() == QIOCraftingProcessorEndpointDirectory.ResolutionStatus.READY_OUTPUT) {
                    if (!applyInputs(intent, operation, view) || !applyOutputs(operation, view)) {
                        QIOWorkbenchExecutionState failed = persistReconciliation(orderId, state);
                        return result(Status.NEEDS_RECONCILIATION, failed);
                    }
                    resolution.tile().completeOperation(operation.processorAllocation().token().laneId());
                    QIOCraftingProcessorState.Lane completed = resolution.tile().processorState().lanes()
                            .get(operation.processorAllocation().token().laneId());
                    if (completed == null || completed.token().state() != mekanism.api.processing.MachineOperationToken.State.COMPLETED) {
                        QIOWorkbenchExecutionState failed = persistReconciliation(orderId, state);
                        return result(Status.NEEDS_RECONCILIATION, failed);
                    }
                    operation = operation.withProcessorAllocation(operation.processorAllocation().withToken(completed.token()));
                    state = state.inFlight(operation);
                    executionStore.save(state);
                    resolution.tile().releaseOperation(operation.processorAllocation().token().laneId());
                } else if (resolution.status() == QIOCraftingProcessorEndpointDirectory.ResolutionStatus.COMPLETED) {
                    if (!applyInputs(intent, operation, view) || !applyOutputs(operation, view)) {
                        QIOWorkbenchExecutionState failed = persistReconciliation(orderId, state);
                        return result(Status.NEEDS_RECONCILIATION, failed);
                    }
                    if (resolution.tile() != null && resolution.lane() != null) {
                        operation = operation.withProcessorAllocation(operation.processorAllocation()
                                .withToken(resolution.lane().token()));
                        state = state.inFlight(operation);
                        executionStore.save(state);
                        resolution.tile().releaseOperation(operation.processorAllocation().token().laneId());
                    }
                }
            }
            QIOWorkbenchExecutionState next = nextState(intent, state);
            executionStore.save(next);
            return result(next.status() == QIOWorkbenchExecutionState.Status.COMPLETED ? Status.COMPLETED : Status.ADVANCED, next);
        } finally {
            view.close();
        }
    }

    private boolean livePatternsValid(UUID orderId, QIOClaimOrderIntent intent, RecipeCatalog catalog, World world) {
        if (catalog == null || world == null || world.isRemote) return false;
        String key = livePatternValidationKey(intent, catalog, world);
        LivePatternValidation cached = livePatternCache.get(orderId);
        if (cached != null && cached.key.equals(key)) return cached.valid;
        boolean valid = true;
        for (QIOWorkbenchExecutionManifest.Step step : intent.manifest().steps()) {
            RecipeDeclaration declaration = catalog.byId(step.pattern().recipeId());
            if (declaration == null) {
                valid = false;
                break;
            }
            java.util.List<ItemStack> grid = new java.util.ArrayList<>(9);
            for (mekanism.qioprocessing.common.recipe.RecipeItemKey item : step.pattern().grid()) {
                grid.add(item == null ? ItemStack.EMPTY : item.toStack(1));
            }
            WorkbenchValidationResult result = WorkbenchPatternValidator.validate(world, declaration,
                    catalog.generation(), grid, step.pattern().id());
            if (!result.valid() || !step.pattern().equals(result.pattern())) {
                valid = false;
                break;
            }
        }
        livePatternCache.put(orderId, new LivePatternValidation(key, valid));
        return valid;
    }

    private static String livePatternValidationKey(QIOClaimOrderIntent intent, RecipeCatalog catalog, World world) {
        StringBuilder key = new StringBuilder(catalog.generation()).append('|')
                .append(intent.bookRevision()).append('|').append(world.provider.getDimension()).append('|')
                .append(System.identityHashCode(world));
        for (QIOWorkbenchExecutionManifest.Step step : intent.manifest().steps()) {
            RecipeDeclaration declaration = catalog.byId(step.pattern().recipeId());
            key.append('|').append(step.pattern().id()).append(':').append(step.pattern().contentSignature())
                    .append(':').append(step.pattern().declarationSignature())
                    .append(':').append(declaration == null ? "missing" : declaration.signature());
        }
        return key.toString();
    }

    private QIOWorkbenchExecutionState loadOrPrepare(QIOClaimOrderIntent intent, UUID orderId,
                                                     WorkbenchSnapshot book, RecipeCatalog catalog, IQIOFrequencyStorageAccess access) throws IOException {
        if (executionStore.exists(orderId)) return executionStore.load(orderId);
        QIOWorkbenchExecutionPreflight.Status preflight = new QIOWorkbenchExecutionPreflight(orderStore)
                .inspect(orderId, book, catalog, access);
        if (preflight == QIOWorkbenchExecutionPreflight.Status.AWAITING_AUTHORIZATION
                || preflight == QIOWorkbenchExecutionPreflight.Status.CANCELLED) return null;
        if (preflight != QIOWorkbenchExecutionPreflight.Status.READY_FOR_DESIGN) {
            return persistReconciliation(orderId, null);
        }
        QIOWorkbenchExecutionState state = QIOWorkbenchExecutionState.ready(orderId);
        executionStore.saveNew(state);
        return state;
    }

    private QIOWorkbenchExecutionState persistReconciliation(UUID orderId,
                                                             QIOWorkbenchExecutionState existing) throws IOException {
        if (!orderStore.cancellationRequested(orderId)) {
            orderStore.requestCancellation(orderId);
        }
        if (existing != null) {
            if (existing.status() == QIOWorkbenchExecutionState.Status.NEEDS_RECONCILIATION) {
                return existing;
            }
            QIOWorkbenchExecutionState reconciled = existing.needsReconciliation();
            executionStore.save(reconciled);
            return reconciled;
        }
        QIOWorkbenchExecutionState reconciled = QIOWorkbenchExecutionState.ready(orderId).needsReconciliation();
        executionStore.saveNew(reconciled);
        return reconciled;
    }

    private QIOWorkbenchExecutionState.Operation prepareOperation(QIOClaimOrderIntent intent,
                                                                  QIOWorkbenchExecutionState state, IQIOStorageView view) {
        QIOStorageSnapshot snapshot = view.getSnapshot();
        if (snapshot == null || !intent.frequencyId().equals(snapshot.getFrequencyUUID())) return null;
        QIOWorkbenchExecutionManifest.Step step = intent.manifest().steps().get(state.nextStep());
        if (state.nextOperation() >= step.operations()) return null;
        Map<UUID, Long> expectedClaims = expectedClaimAmounts(intent, state);
        if (expectedClaims == null || !claimMatches(intent, view.getClaim(intent.claimId()), expectedClaims))
            return null;
        Map<RecipeItemKey, QIOStorageEntry> byItem = new LinkedHashMap<>();
        for (QIOStorageEntry entry : snapshot.getEntries()) {
            ItemStack stack = entry.getItem();
            if (stack.isEmpty()) continue;
            RecipeItemKey item = RecipeItemKey.capture(stack);
            if (byItem.putIfAbsent(item, entry) != null) return null;
        }
        Map<UUID, QIOWorkbenchExecutionState.Input> inputs = new LinkedHashMap<>();
        Map<RecipeItemKey, Long> inputAmounts = new LinkedHashMap<>();
        for (Map.Entry<RecipeItemKey, Integer> entry : step.pattern().consumedInputs().entrySet()) {
            long amount = entry.getValue();
            RecipeItemKey item = entry.getKey();
            QIOStorageEntry resource = byItem.get(item);
            if (resource == null || resource.getExactStoredAmount().compareTo(BigInteger.valueOf(amount)) < 0)
                return null;
            QIOWorkbenchExecutionManifest.Withdrawal initial = withdrawal(intent, item);
            QIOWorkbenchExecutionState.InputMode mode = QIOWorkbenchExecutionState.InputMode.EXTRACT;
            UUID claimedResource = withdrawalResource(intent, item);
            long remainingClaim = claimedResource == null ? 0 : expectedClaims.getOrDefault(claimedResource, 0L);
            if (initial != null && claimedResource != null && remainingClaim > 0) {
                if (!resource.getResourceUUID().equals(claimedResource) || amount > remainingClaim) return null;
                mode = QIOWorkbenchExecutionState.InputMode.CLAIM;
            }
            QIOWorkbenchExecutionState.Input input = new QIOWorkbenchExecutionState.Input(resource.getResourceUUID(), item,
                    amount, resource.getExactStoredAmount(), mode);
            if (inputs.putIfAbsent(resource.getResourceUUID(), input) != null) return null;
            inputAmounts.merge(item, amount, Math::addExact);
        }
        Map<RecipeItemKey, QIOWorkbenchExecutionState.Output> outputs = new LinkedHashMap<>();
        for (RecipeItemKey item : step.pattern().remainders()) {
            if (item != null) outputs.merge(item, new QIOWorkbenchExecutionState.Output(item, 1, BigInteger.ZERO),
                    (first, second) -> new QIOWorkbenchExecutionState.Output(item, Math.addExact(first.amount(), second.amount()), first.expectedStoredAmount()));
        }
        long outputAmount = step.pattern().outputCount();
        RecipeItemKey outputItem = step.pattern().output();
        outputs.merge(outputItem, new QIOWorkbenchExecutionState.Output(outputItem, outputAmount, BigInteger.ZERO),
                (first, second) -> new QIOWorkbenchExecutionState.Output(outputItem, Math.addExact(first.amount(), second.amount()), first.expectedStoredAmount()));
        Map<RecipeItemKey, QIOWorkbenchExecutionState.Output> corrected = new LinkedHashMap<>();
        for (QIOWorkbenchExecutionState.Output output : outputs.values()) {
            QIOStorageEntry resource = byItem.get(output.item());
            BigInteger baseline = resource == null ? BigInteger.ZERO : resource.getExactStoredAmount();
            if (resource != null) {
                long consumed = inputAmounts.getOrDefault(output.item(), 0L);
                baseline = baseline.subtract(BigInteger.valueOf(consumed));
                if (baseline.signum() < 0) return null;
            }
            corrected.put(output.item(), new QIOWorkbenchExecutionState.Output(output.item(), output.amount(), baseline));
        }
        UUID operationId = UUID.nameUUIDFromBytes((intent.orderId() + ":operation:" + state.nextStep() + ":" + state.nextOperation())
                .getBytes(StandardCharsets.UTF_8));
        return new QIOWorkbenchExecutionState.Operation(operationId, state.nextStep(), state.nextOperation(),
                snapshot.getContentsRevision(), snapshot.getClaimRevision(), inputs, corrected);
    }

    private boolean applyInputs(QIOClaimOrderIntent intent, QIOWorkbenchExecutionState.Operation operation,
                                IQIOStorageView view) {
        Map<UUID, Long> claims = new LinkedHashMap<>();
        Map<UUID, BigInteger> baselines = new LinkedHashMap<>();
        for (QIOWorkbenchExecutionState.Input input : operation.inputs().values()) {
            if (input.mode() == QIOWorkbenchExecutionState.InputMode.CLAIM) {
                claims.merge(input.resourceId(), input.amount(), Math::addExact);
                baselines.put(input.resourceId(), input.expectedStoredAmount());
            }
        }
        if (!claims.isEmpty()) {
            UUID requestId = derived(operation.operationId(), "consume");
            QIOClaimRequest request = QIOClaimRequest.consumeReconciled(requestId, requestId, intent.claimId(),
                    QIOPlanClaimIntent.OWNER_NAMESPACE, intent.orderId().toString(), operation.expectedContentsRevision(),
                    operation.expectedClaimRevision(), claims, baselines);
            QIOClaimResult result = view.submitClaim(request);
            if (!requestId.equals(result.getRequestId()) || !result.isSuccess()) return false;
            if (!verifyStoredAmounts(view.getSnapshot(), operation, QIOWorkbenchExecutionState.InputMode.CLAIM))
                return false;
        }
        for (QIOWorkbenchExecutionState.Input input : operation.inputs().values()) {
            if (input.mode() != QIOWorkbenchExecutionState.InputMode.EXTRACT) continue;
            QIOTransferResult result = view.extractIdempotent(derived(operation.operationId(), "extract:" + input.resourceId()),
                    input.item().toStack(1), input.amount(), input.expectedStoredAmount());
            if (!transferSuccess(result, input.amount())) return false;
        }
        return true;
    }

    private boolean applyOutputs(QIOWorkbenchExecutionState.Operation operation, IQIOStorageView view) {
        for (QIOWorkbenchExecutionState.Output output : operation.outputs().values()) {
            QIOTransferResult result = view.insertIdempotent(derived(operation.operationId(), "insert:" + output.item().fingerprint()),
                    output.item().toStack(1), output.amount(), output.expectedStoredAmount());
            if (!transferSuccess(result, output.amount())) return false;
        }
        return true;
    }

    private static Map<UUID, Long> expectedClaimAmounts(QIOClaimOrderIntent intent,
                                                        QIOWorkbenchExecutionState state) {
        Map<RecipeItemKey, Long> remainingByItem = new LinkedHashMap<>();
        Map<RecipeItemKey, UUID> resourcesByItem = new LinkedHashMap<>();
        for (Map.Entry<UUID, QIOWorkbenchExecutionManifest.Withdrawal> entry : intent.manifest().withdrawals().entrySet()) {
            remainingByItem.put(entry.getValue().item(), entry.getValue().amount());
            resourcesByItem.put(entry.getValue().item(), entry.getKey());
        }
        for (int stepIndex = 0; stepIndex <= state.nextStep() && stepIndex < intent.manifest().steps().size(); stepIndex++) {
            QIOWorkbenchExecutionManifest.Step step = intent.manifest().steps().get(stepIndex);
            long operationCount = stepIndex == state.nextStep() ? state.nextOperation() : step.operations();
            for (long operation = 0; operation < operationCount; operation++) {
                for (Map.Entry<RecipeItemKey, Integer> input : step.pattern().consumedInputs().entrySet()) {
                    RecipeItemKey item = input.getKey();
                    long available = remainingByItem.getOrDefault(item, 0L);
                    long required;
                    try {
                        required = Math.multiplyExact(1L, input.getValue());
                    } catch (ArithmeticException error) {
                        return null;
                    }
                    if (available == 0) continue;
                    if (available < required) return null;
                    if (available == required) remainingByItem.remove(item);
                    else remainingByItem.put(item, available - required);
                }
            }
        }
        Map<UUID, Long> result = new LinkedHashMap<>();
        for (Map.Entry<RecipeItemKey, Long> entry : remainingByItem.entrySet()) {
            UUID resource = resourcesByItem.get(entry.getKey());
            if (resource == null || entry.getValue() <= 0) return null;
            result.put(resource, entry.getValue());
        }
        return result;
    }

    private static boolean claimMatches(QIOClaimOrderIntent intent, QIOResourceClaim claim,
                                        Map<UUID, Long> expectedAmounts) {
        if (expectedAmounts.isEmpty()) return claim == null || claim.getResourceAmounts().isEmpty();
        if (claim == null || !intent.claimId().equals(claim.getClaimId())
                || !QIOPlanClaimIntent.OWNER_NAMESPACE.equals(claim.getOwnerNamespace())
                || !intent.orderId().toString().equals(claim.getOwnerId())
                || claim.getState() != QIOResourceClaim.State.ACTIVE) return false;
        Map<UUID, Long> actual = new LinkedHashMap<>();
        for (Map.Entry<UUID, Long> entry : claim.getResourceAmounts().entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) actual.put(entry.getKey(), entry.getValue());
        }
        return actual.equals(expectedAmounts);
    }

    private QIOWorkbenchExecutionState nextState(QIOClaimOrderIntent intent,
                                                 QIOWorkbenchExecutionState state) {
        QIOWorkbenchExecutionManifest.Step step = intent.manifest().steps().get(state.nextStep());
        if (state.nextOperation() + 1 < step.operations())
            return state.advance(state.nextStep(), state.nextOperation() + 1);
        return state.advance(state.nextStep() + 1, 0);
    }

    private Result finish(QIOClaimOrderIntent intent, QIOWorkbenchExecutionState state,
                          IQIOStorageView view) throws IOException {
        QIOResourceClaim claim = view.getClaim(intent.claimId());
        if (claim != null) {
            QIOClaimRequest request = intent.releaseRequest();
            QIOClaimResult result = view.submitClaim(request);
            if (!request.getRequestId().equals(result.getRequestId())
                    || (result.getStatus() != QIOClaimResult.Status.APPLIED
                    && result.getStatus() != QIOClaimResult.Status.NO_CHANGE
                    && result.getStatus() != QIOClaimResult.Status.NOT_FOUND)
                    || view.getClaim(intent.claimId()) != null) {
                QIOWorkbenchExecutionState failed = persistReconciliation(intent.orderId(), state);
                return result(Status.NEEDS_RECONCILIATION, failed);
            }
        }
        QIOWorkbenchExecutionState completed = state.completed();
        executionStore.save(completed);
        return result(Status.COMPLETED, completed);
    }

    private static boolean verifyStoredAmounts(QIOStorageSnapshot snapshot,
                                               QIOWorkbenchExecutionState.Operation operation, QIOWorkbenchExecutionState.InputMode mode) {
        if (snapshot == null) return false;
        for (QIOWorkbenchExecutionState.Input input : operation.inputs().values()) {
            if (input.mode() != mode) continue;
            QIOStorageEntry found = null;
            for (QIOStorageEntry entry : snapshot.getEntries()) {
                if (input.resourceId().equals(entry.getResourceUUID())) {
                    found = entry;
                    break;
                }
            }
            BigInteger expected = input.expectedStoredAmount().subtract(BigInteger.valueOf(input.amount()));
            if (found == null ? expected.signum() != 0 : !expected.equals(found.getExactStoredAmount())) return false;
        }
        return true;
    }

    private static QIOWorkbenchExecutionManifest.Withdrawal withdrawal(QIOClaimOrderIntent intent, RecipeItemKey item) {
        for (QIOWorkbenchExecutionManifest.Withdrawal withdrawal : intent.manifest().withdrawals().values()) {
            if (withdrawal.item().equals(item)) return withdrawal;
        }
        return null;
    }

    private static UUID withdrawalResource(QIOClaimOrderIntent intent, RecipeItemKey item) {
        for (Map.Entry<UUID, QIOWorkbenchExecutionManifest.Withdrawal> entry : intent.manifest().withdrawals().entrySet()) {
            if (entry.getValue().item().equals(item)) return entry.getKey();
        }
        return null;
    }

    private static boolean transferSuccess(QIOTransferResult result, long amount) {
        return result != null && (result.getStatus() == QIOTransferResult.Status.APPLIED
                || result.getStatus() == QIOTransferResult.Status.REPLAYED)
                && result.getTransferredAmount() == amount;
    }

    private static UUID derived(UUID operationId, String suffix) {
        return UUID.nameUUIDFromBytes((operationId + ":" + suffix).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean validView(QIOClaimOrderIntent intent, IQIOStorageView view) {
        return view.isValid() && intent.frequencyId().equals(view.getFrequencyUUID());
    }

    private static Result result(Status status, QIOWorkbenchExecutionState state) {
        return new Result(status, state);
    }

    private static final class LivePatternValidation {
        private final String key;
        private final boolean valid;

        private LivePatternValidation(String key, boolean valid) {
            this.key = key;
            this.valid = valid;
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Workbench execution requires its owner thread");
    }
}

