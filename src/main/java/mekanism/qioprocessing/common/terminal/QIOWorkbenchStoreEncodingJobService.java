package mekanism.qioprocessing.common.terminal;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchClosureMode;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfigurationMutation;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchEncodingRecord;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchEncodingRecord.State;
import mekanism.qioprocessing.common.pattern.VerifiedWorkbenchPattern;
import mekanism.qioprocessing.common.pattern.WorkbenchPatternValidator;
import mekanism.qioprocessing.common.pattern.WorkbenchValidationResult;
import mekanism.qioprocessing.common.planning.QIOPlanningExecutor;
import mekanism.qioprocessing.common.planning.QIOPlanningService;
import mekanism.qioprocessing.common.planning.QIOWorkbenchRecipeCatalog;
import mekanism.qioprocessing.common.recipe.RecipeCatalog;
import mekanism.qioprocessing.common.recipe.RecipeDeclaration;
import mekanism.qioprocessing.common.recipe.RecipeDiscovery;
import mekanism.qioprocessing.common.recipe.RecipeItemKey;
import mekanism.qioprocessing.common.workbench.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

final class QIOWorkbenchStoreEncodingJobService {
    static final QIOWorkbenchStoreEncodingJobService INSTANCE = new QIOWorkbenchStoreEncodingJobService();
    private static final int MAX_ACTIVE = 16;
    private final Map<UUID, Job> active = new java.util.LinkedHashMap<>();

    private QIOWorkbenchStoreEncodingJobService() {
    }

    void start(QIOWorkbenchConfigurationService.Context context, QIOProcessingTerminalSession session,
               UUID owner, long expectedRevision, long expectedCatalogRevision,
               QIOWorkbenchConfigurationMutation mutation, QIOWorkbenchClosureMode mode,
               boolean skipCycles, World world, Consumer<QIOWorkbenchClosureService.PreviewResult> completion) {
        try {
            if (!context.isEditable()) {
                completion.accept(result(QIOWorkbenchClosureService.Status.READ_ONLY));
                return;
            }
            if (!session.getPlayerUUID().equals(owner)) {
                completion.accept(result(QIOWorkbenchClosureService.Status.ACCESS_DENIED));
                return;
            }
            if (mode == QIOWorkbenchClosureMode.NONE) {
                completion.accept(result(QIOWorkbenchClosureService.Status.INVALID_TARGET));
                return;
            }
            if (active.size() >= MAX_ACTIVE) {
                completion.accept(result(QIOWorkbenchClosureService.Status.BUSY));
                return;
            }
            RecipeCatalog catalog = QIOWorkbenchCorePageService.currentCatalog();
            WorkbenchSnapshot snapshot = WorkbenchBookRuntime.loadOrBlank(world, context.getNetwork().getFrequencyUUID());
            if (snapshot.revision() != expectedRevision) {
                completion.accept(result(QIOWorkbenchClosureService.Status.REVISION_CONFLICT));
                return;
            }
            if (catalogRevision(catalog) != expectedCatalogRevision) {
                completion.accept(result(QIOWorkbenchClosureService.Status.CATALOG_CHANGED));
                return;
            }
            if (QIOPlanningService.INSTANCE.getExecutor() == null) {
                completion.accept(result(QIOWorkbenchClosureService.Status.UNAVAILABLE));
                return;
            }
            QIOProcessingNetworkData network = context.getNetwork();
            for (QIOWorkbenchEncodingRecord record : network.getWorkbenchEncodingJobs()) {
                if (record.isNewStore() && !record.isTerminal() && record.getOwner().equals(owner) &&
                        record.getConfiguration().equals(network.getWorkbenchConfiguration().getConfigUUID()) &&
                        record.getConfigurationRevision() == expectedRevision && record.getMode() == mode &&
                        record.skipsCycles() == skipCycles && record.getRequest().equals(mutation.write())) {
                    completion.accept(view(record));
                    return;
                }
            }
            UUID id = UUID.randomUUID();
            VerifiedWorkbenchPattern root = mutation.getAction() == QIOWorkbenchConfigurationMutation.Action.ENCODE_PATTERN ?
                    resolveRoot(context.player(), world, catalog, mutation.getGrid(), catalog.generation(), id, mutation.getProductKey(),
                            mutation.getRecipeId(), mutation.getRecipeSignature()) : null;
            QIOWorkbenchEncodingRecord record = new QIOWorkbenchEncodingRecord(id, owner,
                    network.getWorkbenchConfiguration().getConfigUUID(), expectedRevision, expectedCatalogRevision,
                    world.provider.getDimension(), mutation.write(), mode, skipCycles, true);
            Job job = new Job(network, record, snapshot, catalog, mutation, mode, skipCycles, root, completion);
            network.saveWorkbenchEncodingJob(record);
            active.put(id, job);
            record.phase(State.SEARCHING, "");
            network.saveWorkbenchEncodingJob(record);
            QIOPlanningExecutor.Submission submission;
            try {
                submission = QIOPlanningService.INSTANCE.submitTask(QIOPlanningExecutor.TaskKind.ENCODING,
                        job, (value, cancellation) -> prepare(job, cancellation), value -> complete(job, value));
            } catch (RuntimeException error) {
                fail(job, QIOWorkbenchClosureService.Status.UNAVAILABLE, "Encoding worker unavailable");
                return;
            }
            job.taskId = submission.getTaskId();
            if (!submission.isAccepted())
                fail(job, QIOWorkbenchClosureService.Status.BUSY, "Encoding worker admission rejected");
            else completion.accept(view(record));
        } catch (IllegalArgumentException | IllegalStateException | IOException error) {
            completion.accept(result(QIOWorkbenchClosureService.Status.INVALID_PATTERN));
        }
    }

    QIOWorkbenchClosureService.PreviewResult query(QIOWorkbenchConfigurationService.Context context,
                                                   QIOProcessingTerminalSession session, UUID owner, @Nullable UUID id) {
        QIOWorkbenchEncodingRecord record = id == null ? latest(context, session, owner) : owned(context, session, owner, id);
        return record == null ? result(QIOWorkbenchClosureService.Status.EXPIRED) : view(record);
    }

    QIOWorkbenchClosureService.PreviewResult cancel(QIOWorkbenchConfigurationService.Context context,
                                                    QIOProcessingTerminalSession session, UUID owner, UUID id) {
        QIOWorkbenchEncodingRecord record = owned(context, session, owner, id);
        if (record == null) return result(QIOWorkbenchClosureService.Status.ACCESS_DENIED);
        if (record.getState() == State.CONFIRMED || record.getState() == State.CANCELLED) return view(record);
        Job job = active.remove(id);
        if (job != null && job.taskId != null && QIOPlanningService.INSTANCE.getExecutor() != null) {
            QIOPlanningService.INSTANCE.getExecutor().cancel(job.taskId);
        }
        record.cancel();
        context.getNetwork().saveWorkbenchEncodingJob(record);
        return view(record);
    }

    void resume(QIOWorkbenchConfigurationService.Context context, QIOProcessingTerminalSession session,
                UUID owner, UUID id, World world, Consumer<QIOWorkbenchClosureService.PreviewResult> completion) {
        QIOWorkbenchEncodingRecord previous = owned(context, session, owner, id);
        if (previous == null) {
            completion.accept(result(QIOWorkbenchClosureService.Status.ACCESS_DENIED));
            return;
        }
        if (previous.getState() == State.CONFIRMED || previous.getState() == State.CANCELLED || active.containsKey(id)) {
            completion.accept(view(previous));
            return;
        }
        try {
            RecipeCatalog catalog = QIOWorkbenchCorePageService.currentCatalog();
            WorkbenchSnapshot snapshot = WorkbenchBookRuntime.loadOrBlank(world, context.getNetwork().getFrequencyUUID());
            if (snapshot.revision() != previous.getConfigurationRevision()) {
                completion.accept(result(QIOWorkbenchClosureService.Status.TARGET_CHANGED));
                return;
            }
            if (catalogRevision(catalog) != previous.getCatalogRevision()) {
                completion.accept(result(QIOWorkbenchClosureService.Status.CATALOG_CHANGED));
                return;
            }
            if (QIOPlanningService.INSTANCE.getExecutor() == null) {
                completion.accept(result(QIOWorkbenchClosureService.Status.UNAVAILABLE));
                return;
            }
            QIOWorkbenchConfigurationMutation mutation = QIOWorkbenchConfigurationMutation.read(previous.getRequest());
            VerifiedWorkbenchPattern root = mutation.getAction() == QIOWorkbenchConfigurationMutation.Action.ENCODE_PATTERN ?
                    resolveRoot(context.player(), world, catalog, mutation.getGrid(), catalog.generation(), previous.getId(), mutation.getProductKey(),
                            mutation.getRecipeId(), mutation.getRecipeSignature()) : null;
            QIOWorkbenchEncodingRecord record = new QIOWorkbenchEncodingRecord(previous.getId(), previous.getOwner(),
                    previous.getConfiguration(), previous.getConfigurationRevision(), previous.getCatalogRevision(),
                    previous.getDimension(), previous.getRequest(), previous.getMode(), previous.skipsCycles(), true);
            Job job = new Job(context.getNetwork(), record, snapshot, catalog, mutation, previous.getMode(),
                    previous.skipsCycles(), root, completion);
            context.getNetwork().saveWorkbenchEncodingJob(record);
            active.put(id, job);
            record.phase(State.SEARCHING, "");
            context.getNetwork().saveWorkbenchEncodingJob(record);
            QIOPlanningExecutor.Submission submission;
            try {
                submission = QIOPlanningService.INSTANCE.submitTask(QIOPlanningExecutor.TaskKind.ENCODING,
                        job, (value, cancellation) -> prepare(job, cancellation), value -> complete(job, value));
            } catch (RuntimeException error) {
                fail(job, QIOWorkbenchClosureService.Status.UNAVAILABLE, "Encoding worker unavailable");
                return;
            }
            job.taskId = submission.getTaskId();
            if (!submission.isAccepted())
                fail(job, QIOWorkbenchClosureService.Status.BUSY, "Encoding worker admission rejected");
            else completion.accept(view(record));
        } catch (QIOProcessingDataException | IllegalArgumentException | IllegalStateException | IOException error) {
            completion.accept(result(QIOWorkbenchClosureService.Status.INVALID_PATTERN));
        }
    }

    QIOWorkbenchClosureService.ConfirmResult confirm(QIOWorkbenchConfigurationService.Context context,
                                                     QIOProcessingTerminalSession session, UUID owner, UUID id, World world) {
        QIOWorkbenchEncodingRecord record = owned(context, session, owner, id);
        if (record == null)
            return new QIOWorkbenchClosureService.ConfirmResult(QIOWorkbenchClosureService.Status.ACCESS_DENIED);
        if (record.getState() == State.CONFIRMED) return new QIOWorkbenchClosureService.ConfirmResult(
                record.wasApplied() ? QIOWorkbenchClosureService.Status.APPLIED : QIOWorkbenchClosureService.Status.UNCHANGED);
        Job job = active.get(id);
        if (record.getState() != State.READY || job == null || job.preview == null)
            return new QIOWorkbenchClosureService.ConfirmResult(QIOWorkbenchClosureService.Status.BUSY);
        try {
            RecipeCatalog catalog = QIOWorkbenchCorePageService.currentCatalog();
            WorkbenchSnapshot current = WorkbenchBookRuntime.loadOrBlank(world, context.getNetwork().getFrequencyUUID());
            if (current.revision() != record.getConfigurationRevision())
                return new QIOWorkbenchClosureService.ConfirmResult(QIOWorkbenchClosureService.Status.TARGET_CHANGED);
            if (catalogRevision(catalog) != record.getCatalogRevision() || !catalog.generation().equals(record.getGeneration())) {
                return new QIOWorkbenchClosureService.ConfirmResult(QIOWorkbenchClosureService.Status.CATALOG_CHANGED);
            }
            WorkbenchRecipeBook book = new WorkbenchRecipeBook(current);
            WorkbenchSnapshot next = book.commit(catalog, job.preview);
            for (VerifiedWorkbenchPattern pattern : job.patterns.values()) {
                WorkbenchRoute route = next.routes().get(pattern.recipeId());
                if (route != null && route.patterns().values().stream().anyMatch(existing ->
                        existing.contentSignature().equals(pattern.contentSignature()))) continue;
                next = book.addPattern(next.revision(), catalog, pattern);
            }
            WorkbenchBookRuntime.save(world, next);
            active.remove(id);
            record.confirmed(next != current);
            context.getNetwork().saveWorkbenchEncodingJob(record);
            return new QIOWorkbenchClosureService.ConfirmResult(next == current ?
                    QIOWorkbenchClosureService.Status.UNCHANGED : QIOWorkbenchClosureService.Status.APPLIED);
        } catch (WorkbenchRecipeBook.Rejected rejected) {
            return new QIOWorkbenchClosureService.ConfirmResult(rejected.reason == WorkbenchRecipeBook.Rejection.STALE_CATALOG ?
                    QIOWorkbenchClosureService.Status.CATALOG_CHANGED : QIOWorkbenchClosureService.Status.INVALID_PATTERN);
        } catch (IOException | RuntimeException error) {
            return new QIOWorkbenchClosureService.ConfirmResult(QIOWorkbenchClosureService.Status.INVALID_PATTERN);
        }
    }

    boolean owns(QIOWorkbenchConfigurationService.Context context, QIOProcessingTerminalSession session,
                 UUID owner, UUID id) {
        return (id == null ? latest(context, session, owner) : owned(context, session, owner, id)) != null;
    }

    void shutdown() {
        for (Job job : new ArrayList<>(active.values())) {
            if (job.taskId != null && QIOPlanningService.INSTANCE.getExecutor() != null) {
                QIOPlanningService.INSTANCE.getExecutor().cancel(job.taskId);
            }
            if (!job.record.isTerminal()) {
                job.record.phase(State.PAUSED_RESTART, "Server stopped");
                job.network.saveWorkbenchEncodingJob(job.record);
            }
        }
        active.clear();
    }

    private WorkbenchImport.Preview prepare(Job job, QIOPlanningExecutor.CancellationToken cancellation) {
        if (cancellation.isCancelled()) throw new java.util.concurrent.CancellationException();
        List<RecipeItemKey> targets = new ArrayList<>();
        if (job.mutation.getAction() == QIOWorkbenchConfigurationMutation.Action.ENCODE_TARGETS) {
            for (ItemStack target : job.mutation.getTargets()) targets.add(RecipeItemKey.capture(target));
        } else {
            targets.add(job.root.output());
        }
        RecipeDiscovery.Selection selection = job.root == null ? job.snapshot : new RootSelection(job.snapshot,
                job.root.recipeId(), job.root.output().familyKey());
        return WorkbenchImport.prepare(job.snapshot, job.catalog, targets, discoveryMode(job.mode), selection,
                job.skipCycles);
    }

    private void complete(Job job, QIOPlanningExecutor.PlanningResult<WorkbenchImport.Preview> result) {
        if (active.get(job.record.getId()) != job) return;
        if (result.getStatus() != QIOPlanningExecutor.ResultStatus.SUCCESS || result.getValue() == null) {
            fail(job, QIOWorkbenchClosureService.Status.UNAVAILABLE, "Recipe discovery did not complete");
            return;
        }
        job.preview = result.getValue();
        job.validationRecipes = new ArrayList<>(job.preview.discovery().recipes());
        if (job.root != null) {
            job.patterns.put(job.root.recipeId(), job.root);
            job.validationRecipes.removeIf(recipe -> recipe.id().equals(job.root.recipeId()));
        }
        job.validationCursor = 0;
        job.record.phase(State.VALIDATING, "");
        job.network.saveWorkbenchEncodingJob(job.record);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Job job : new ArrayList<>(active.values())) {
            if (job.record.getState() != State.VALIDATING || job.preview == null) continue;
            World world = DimensionManager.getWorld(job.record.getDimension());
            if (world == null) continue;
            EntityPlayer player = world.getMinecraftServer() == null ? null :
                    world.getMinecraftServer().getPlayerList().getPlayerByUUID(job.record.getOwner());
            int budget = 2;
            while (budget-- > 0 && job.validationCursor < job.validationRecipes.size()) {
                RecipeDeclaration recipe = job.validationRecipes.get(job.validationCursor);
                try {
                    int combinations = combinationCount(job.snapshot, recipe);
                    if (job.candidateAttempt >= combinations) {
                        job.validationCursor++;
                        job.candidateAttempt = 0;
                        continue;
                    }
                    WorkbenchValidationResult result = WorkbenchPatternValidator.validate(player, world, recipe,
                            job.catalog.generation(), grid(job.snapshot, recipe, job.candidateAttempt),
                            patternId(job.record.getId(), recipe.id() + ":" + job.candidateAttempt));
                    job.candidateAttempt++;
                    if (result.valid()) {
                        job.patterns.put(recipe.id(), result.pattern().withId(stablePatternId(recipe,
                                result.pattern().contentSignature())));
                        job.validationCursor++;
                        job.candidateAttempt = 0;
                    }
                } catch (RuntimeException error) {
                    fail(job, QIOWorkbenchClosureService.Status.INVALID_PATTERN,
                            "Pattern validation failed: " + error.getMessage());
                    return;
                }
            }
            if (job.validationCursor == job.validationRecipes.size()) finishValidation(job);
        }
    }

    private void finishValidation(Job job) {
        if (active.get(job.record.getId()) != job || job.preview == null) return;
        job.preview = job.preview.retainRoutes(job.patterns.keySet());
        QIOWorkbenchClosurePreview summary = summary(job);
        job.record.readyNewStore(summary, job.catalog.generation());
        job.network.saveWorkbenchEncodingJob(job.record);
        job.completion.accept(new QIOWorkbenchClosureService.PreviewResult(QIOWorkbenchClosureService.Status.READY, summary));
    }

    private QIOWorkbenchClosurePreview summary(Job job) {
        WorkbenchImport.Preview preview = job.preview;
        RecipeDiscovery.Result discovery = preview.discovery();
        return new QIOWorkbenchClosurePreview(job.record.getId(), discovery.recipes().size(), preview.added(),
                preview.added() + preview.updated(), preview.unchanged(), discovery.leaves().size(),
                discovery.cycleCount(), discovery.skippedCyclicRecipeCount(),
                discovery.incompleteRecipes().size() > 0, Collections.emptyList()).withDiagnostic(
                discovery.incompleteRecipes().isEmpty() ? "" : "INCOMPLETE_RECIPES: " + discovery.incompleteRecipes().size());
    }

    private void fail(Job job, QIOWorkbenchClosureService.Status status, String diagnostic) {
        if (active.remove(job.record.getId()) != job) return;
        if (!job.record.isTerminal()) job.record.phase(State.FAILED, diagnostic);
        job.network.saveWorkbenchEncodingJob(job.record);
        job.completion.accept(new QIOWorkbenchClosureService.PreviewResult(status, progress(job.record)));
    }

    private QIOWorkbenchEncodingRecord owned(QIOWorkbenchConfigurationService.Context context,
                                             QIOProcessingTerminalSession session, UUID owner, @Nullable UUID id) {
        if (id == null || !context.isEditable() || !session.getPlayerUUID().equals(owner)) return null;
        QIOWorkbenchEncodingRecord record = context.getNetwork().getWorkbenchEncodingJob(id);
        return record != null && record.isNewStore() && record.getOwner().equals(owner) &&
                record.getConfiguration().equals(context.getNetwork().getWorkbenchConfiguration().getConfigUUID()) ? record : null;
    }

    @Nullable
    private QIOWorkbenchEncodingRecord latest(QIOWorkbenchConfigurationService.Context context,
                                              QIOProcessingTerminalSession session, UUID owner) {
        if (!context.isEditable() || !session.getPlayerUUID().equals(owner)) return null;
        QIOWorkbenchEncodingRecord latest = null;
        for (QIOWorkbenchEncodingRecord record : context.getNetwork().getWorkbenchEncodingJobs()) {
            if (!record.isNewStore() || !record.getOwner().equals(owner) ||
                    !record.getConfiguration().equals(context.getNetwork().getWorkbenchConfiguration().getConfigUUID()))
                continue;
            if (latest == null || record.getCreated() > latest.getCreated()) latest = record;
        }
        return latest;
    }

    private QIOWorkbenchClosureService.PreviewResult view(QIOWorkbenchEncodingRecord record) {
        switch (record.getState()) {
            case READY:
                return new QIOWorkbenchClosureService.PreviewResult(QIOWorkbenchClosureService.Status.READY, record.getPreview());
            case CONFIRMED:
                return new QIOWorkbenchClosureService.PreviewResult(record.wasApplied() ?
                        QIOWorkbenchClosureService.Status.APPLIED : QIOWorkbenchClosureService.Status.UNCHANGED, null);
            case CANCELLED:
                return new QIOWorkbenchClosureService.PreviewResult(QIOWorkbenchClosureService.Status.CANCELLED, progress(record));
            case PAUSED_RESTART:
                return new QIOWorkbenchClosureService.PreviewResult(QIOWorkbenchClosureService.Status.PAUSED_RESTART, progress(record));
            case FAILED:
                return new QIOWorkbenchClosureService.PreviewResult(QIOWorkbenchClosureService.Status.FAILED, progress(record));
            default:
                return new QIOWorkbenchClosureService.PreviewResult(QIOWorkbenchClosureService.Status.BUSY, progress(record));
        }
    }

    private QIOWorkbenchClosurePreview progress(QIOWorkbenchEncodingRecord record) {
        return record.getPreview() == null ? new QIOWorkbenchClosurePreview(record.getId(), 0, 0, 0, 0, 0, 0, 0,
                false, Collections.emptyList()).withDiagnostic(record.getDiagnostic()) : record.getPreview().withDiagnostic(record.getDiagnostic());
    }

    private static QIOWorkbenchClosureService.PreviewResult result(QIOWorkbenchClosureService.Status status) {
        return new QIOWorkbenchClosureService.PreviewResult(status, null);
    }

    private static RecipeDiscovery.Mode discoveryMode(QIOWorkbenchClosureMode mode) {
        return mode == QIOWorkbenchClosureMode.ALL ? RecipeDiscovery.Mode.ALL : RecipeDiscovery.Mode.PREFERRED;
    }

    private static int catalogRevision(RecipeCatalog catalog) {
        long value = Long.parseUnsignedLong(catalog.generation().substring(0, 16), 16) & Long.MAX_VALUE;
        return (int) (value == 0 ? 1 : value % Integer.MAX_VALUE);
    }

    private static String productKey(RecipeDeclaration recipe) {
        return QIOWorkbenchRecipeCatalog.productKey(
                mekanism.qioprocessing.api.resource.PortableResourceDescriptor.item(recipe.output().toStack(1)));
    }

    private static VerifiedWorkbenchPattern resolveRoot(EntityPlayer player, World world, RecipeCatalog catalog,
                                                        List<ItemStack> grid, String generation, UUID patternId, String productKey,
                                                        String recipeId, String recipeSignature) {
        if (recipeId != null && !recipeId.isEmpty()) {
            RecipeDeclaration recipe = catalog.byId(recipeId);
            if (recipe == null || !productKey(recipe).equals(productKey) ||
                    !recipe.signature().equals(recipeSignature)) {
                throw new IllegalArgumentException("Unknown or changed root recipe");
            }
            WorkbenchValidationResult result = WorkbenchPatternValidator.validate(player, world, recipe,
                    generation, grid, patternId);
            if (!result.valid()) throw new IllegalArgumentException(result.status().name());
            return result.pattern().withId(stablePatternId(recipe, result.pattern().contentSignature()));
        }
        VerifiedWorkbenchPattern match = null;
        for (RecipeDeclaration recipe : catalog.recipes()) {
            WorkbenchValidationResult result = WorkbenchPatternValidator.validate(player, world, recipe, generation, grid, patternId);
            if (!result.valid()) continue;
            if (match != null) throw new IllegalArgumentException("Pattern matches multiple recipes");
            match = result.pattern();
        }
        if (match == null) throw new IllegalArgumentException("Pattern does not match a recipe");
        return match;
    }

    private static int combinationCount(WorkbenchSnapshot snapshot, RecipeDeclaration recipe) {
        long combinations = 1;
        for (List<RecipeItemKey> options : options(snapshot, recipe)) {
            combinations = combinations > 4096L / options.size() ? 4096L : combinations * options.size();
        }
        return (int) combinations;
    }

    private static List<List<RecipeItemKey>> options(WorkbenchSnapshot snapshot, RecipeDeclaration recipe) {
        List<List<RecipeItemKey>> result = new ArrayList<>(recipe.inputs().size());
        for (int slot = 0; slot < recipe.inputs().size(); slot++) {
            if (recipe.inputs().get(slot).isEmptySlot()) {
                result.add(Collections.singletonList(null));
            } else {
                List<RecipeItemKey> values = snapshot.routes().containsKey(recipe.id()) ?
                        snapshot.candidates(recipe, slot) : recipe.inputs().get(slot).candidates();
                if (values.isEmpty()) throw new IllegalArgumentException("No candidates for " + recipe.id());
                result.add(values);
            }
        }
        return result;
    }

    private static List<ItemStack> grid(WorkbenchSnapshot snapshot, RecipeDeclaration recipe, int attempt) {
        List<ItemStack> result = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        List<List<RecipeItemKey>> options = options(snapshot, recipe);
        int divisor = 1;
        for (int input = 0; input < options.size(); input++) {
            List<RecipeItemKey> values = options.get(input);
            RecipeItemKey selected = values.get(attempt / divisor % values.size());
            int slot;
            if (recipe.layout() == RecipeDeclaration.Layout.SHAPED) {
                slot = input / recipe.width() * 3 + input % recipe.width();
            } else {
                slot = input;
            }
            if (slot < 9 && selected != null) result.set(slot, selected.toStack(1));
            divisor = divisor > 4096 / values.size() ? 4096 : divisor * values.size();
        }
        return result;
    }

    private static List<ItemStack> grid(WorkbenchSnapshot snapshot, RecipeDeclaration recipe) {
        return grid(snapshot, recipe, 0);
    }

    private static List<ItemStack> legacyGrid(WorkbenchSnapshot snapshot, RecipeDeclaration recipe) {
        List<ItemStack> result = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        if (recipe.layout() == RecipeDeclaration.Layout.SHAPED) {
            for (int row = 0; row < recipe.height(); row++)
                for (int column = 0; column < recipe.width(); column++) {
                    int input = row * recipe.width() + column;
                    result.set(row * 3 + column, candidate(snapshot, recipe, input));
                }
        } else {
            for (int input = 0; input < recipe.inputs().size() && input < 9; input++) {
                result.set(input, candidate(snapshot, recipe, input));
            }
        }
        return result;
    }

    private static ItemStack candidate(WorkbenchSnapshot snapshot, RecipeDeclaration recipe, int slot) {
        List<RecipeItemKey> choices = snapshot.routes().containsKey(recipe.id()) ?
                snapshot.candidates(recipe, slot) : recipe.inputs().get(slot).candidates();
        return choices.isEmpty() ? ItemStack.EMPTY : choices.get(0).toStack(1);
    }

    private static UUID patternId(UUID job, String recipeId) {
        return UUID.nameUUIDFromBytes((job + ":" + recipeId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static UUID stablePatternId(RecipeDeclaration recipe, String contentSignature) {
        return UUID.nameUUIDFromBytes((recipe.id() + ":" + recipe.signature() + ":" + contentSignature)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static final class RootSelection implements RecipeDiscovery.Selection {
        private final WorkbenchSnapshot delegate;
        private final String recipeId;
        private final String family;

        private RootSelection(WorkbenchSnapshot delegate, String recipeId, String family) {
            this.delegate = delegate;
            this.recipeId = recipeId;
            this.family = family;
        }

        @Override
        public String preferred(RecipeItemKey output) {
            return family.equals(output.familyKey()) ? recipeId : delegate.preferred(output);
        }

        @Override
        public boolean enabled(RecipeDeclaration recipe) {
            return delegate.enabled(recipe);
        }

        @Override
        public List<RecipeItemKey> candidates(RecipeDeclaration recipe, int slot) {
            return delegate.candidates(recipe, slot);
        }
    }

    private static final class Job {
        private final QIOProcessingNetworkData network;
        private final QIOWorkbenchEncodingRecord record;
        private final WorkbenchSnapshot snapshot;
        private final RecipeCatalog catalog;
        private final QIOWorkbenchConfigurationMutation mutation;
        private final QIOWorkbenchClosureMode mode;
        private final boolean skipCycles;
        @Nullable
        private final VerifiedWorkbenchPattern root;
        private final Consumer<QIOWorkbenchClosureService.PreviewResult> completion;
        @Nullable
        private UUID taskId;
        @Nullable
        private WorkbenchImport.Preview preview;
        private List<RecipeDeclaration> validationRecipes = Collections.emptyList();
        private final Map<String, VerifiedWorkbenchPattern> patterns = new java.util.LinkedHashMap<>();
        private int validationCursor;
        private int candidateAttempt;

        private Job(QIOProcessingNetworkData network, QIOWorkbenchEncodingRecord record,
                    WorkbenchSnapshot snapshot, RecipeCatalog catalog, QIOWorkbenchConfigurationMutation mutation,
                    QIOWorkbenchClosureMode mode, boolean skipCycles, @Nullable VerifiedWorkbenchPattern root,
                    Consumer<QIOWorkbenchClosureService.PreviewResult> completion) {
            this.network = network;
            this.record = record;
            this.snapshot = snapshot;
            this.catalog = catalog;
            this.mutation = mutation;
            this.mode = mode;
            this.skipCycles = skipCycles;
            this.root = root;
            this.completion = completion;
        }
    }
}
