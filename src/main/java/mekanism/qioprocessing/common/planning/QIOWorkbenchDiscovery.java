package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.workbench.QIOVerifiedWorkbenchPattern;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchClosureMode;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfiguration;
import mekanism.qioprocessing.common.planning.QIOWorkbenchDeclarations.Choice;
import mekanism.qioprocessing.common.planning.QIOWorkbenchDeclarations.Recipe;
import mekanism.qioprocessing.common.planning.QIOWorkbenchDeclarations.Stack;
import mekanism.qioprocessing.common.util.QIOHashing;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Pure rooted discovery over the process declaration directory. A round proposes concrete grids;
 * the caller yields to bounded main-thread validation, then reruns with those exact results.
 * Failed grids are excluded, so a changed candidate also changes the traversed dependency graph.
 */
public final class QIOWorkbenchDiscovery {
    private static final int MAX_DEPTH = 64;
    private static final int MAX_OUTPUTS = 4096;
    private static final int MAX_PATTERNS = 16384;
    private static final int MAX_COMBINATIONS = 4096;

    private QIOWorkbenchDiscovery() {
    }

    public static final class Bindings {
        private final Map<ResourceLocation, String> signatures;

        Bindings(Map<ResourceLocation, String> signatures) {
            this.signatures = Collections.unmodifiableMap(new LinkedHashMap<>(signatures));
        }

        String getOrDefault(ResourceLocation recipe, String fallback) {
            return signatures.getOrDefault(recipe, fallback);
        }
    }

    public static final class Input {
        final QIOWorkbenchDeclarations directory;
        final Bindings plannerBindings;
        final QIOWorkbenchConfiguration configuration;
        final List<QIOWorkbenchConfiguration.EncodedPattern> fixedRoots;
        final List<PortableResourceDescriptor> targets;
        final QIOWorkbenchClosureMode mode;
        final boolean skipCycles;

        public Input(QIOWorkbenchDeclarations directory, Bindings plannerBindings,
                     QIOWorkbenchConfiguration configuration, List<QIOWorkbenchConfiguration.EncodedPattern> fixedRoots,
                     List<PortableResourceDescriptor> targets, QIOWorkbenchClosureMode mode, boolean skipCycles) {
            this.directory = Objects.requireNonNull(directory);
            this.plannerBindings = Objects.requireNonNull(plannerBindings);
            this.configuration = configuration.copy();
            this.fixedRoots = Collections.unmodifiableList(new ArrayList<>(fixedRoots));
            this.targets = Collections.unmodifiableList(new ArrayList<>(targets));
            this.mode = Objects.requireNonNull(mode);
            this.skipCycles = skipCycles;
        }
    }

    public static final class Candidate {
        private final String key;
        private final ResourceLocation recipeId;
        @Nullable
        private final Recipe declaration;
        @Nullable
        private final QIOWorkbenchConfiguration.EncodedPattern fixed;
        private final List<Choice> choices;
        private final List<Stack> grid;
        private final boolean[] virtual;
        private final String plannerSignature;
        private final PortableResourceDescriptor expectedOutput;
        private final long expectedAmount;

        private Candidate(Recipe recipe, List<Choice> choices, String plannerSignature) {
            declaration = recipe;
            fixed = null;
            recipeId = recipe.getId();
            this.choices = Collections.unmodifiableList(new ArrayList<>(choices));
            List<Stack> stacks = new ArrayList<>(9);
            virtual = new boolean[9];
            for (int slot = 0; slot < 9; slot++) {
                stacks.add(choices.get(slot).getStack());
                virtual[slot] = choices.get(slot).isVirtualFluid();
            }
            grid = Collections.unmodifiableList(stacks);
            this.plannerSignature = plannerSignature;
            expectedOutput = recipe.getOutput().getResource();
            expectedAmount = recipe.getOutput().getAmount();
            key = signature();
        }

        private Candidate(QIOWorkbenchConfiguration.EncodedPattern fixed) {
            this.fixed = fixed;
            declaration = null;
            recipeId = fixed.getRecipeId();
            choices = Collections.emptyList();
            grid = fixed.getFrozenGrid();
            virtual = fixed.getVerified() == null ? new boolean[9] : fixed.getVerified().getVirtualSlots();
            plannerSignature = fixed.getRecipeSignature();
            expectedOutput = fixed.getOutput();
            expectedAmount = fixed.getOutputAmount();
            key = signature();
        }

        private String signature() {
            StringBuilder text = new StringBuilder(recipeId.toString()).append('|').append(expectedOutput)
                    .append('@').append(expectedAmount);
            for (int slot = 0; slot < 9; slot++)
                text.append('|').append(grid.get(slot).getKey()).append('/').append(virtual[slot]);
            return QIOHashing.sha256(text);
        }

        public String getKey() {
            return key;
        }

        public ResourceLocation getRecipeId() {
            return recipeId;
        }

        public String getPlannerSignature() {
            return plannerSignature;
        }

        public PortableResourceDescriptor getOutput() {
            return expectedOutput;
        }

        public QIOWorkbenchValidationService.Request request(UUID job, int dimension, long epoch) {
            return fixed == null ? new QIOWorkbenchValidationService.Request(job, recipeId, dimension, epoch,
                    grid, virtual, expectedOutput, expectedAmount) :
                    QIOWorkbenchValidationService.request(job, dimension, epoch, fixed);
        }

        public QIOVerifiedWorkbenchPattern verified(String generation, QIOWorkbenchValidationService.Result result) {
            if (declaration != null)
                return QIOVerifiedWorkbenchPattern.verified(UUID.randomUUID(), declaration, generation, choices, result);
            if (fixed.getVerified() != null) return fixed.getVerified().revalidated(generation, result);
            return QIOVerifiedWorkbenchPattern.concrete(fixed.getPatternUUID(), recipeId, plannerSignature, generation,
                    QIOWorkbenchDeclarations.Layout.valueOf(fixed.getLayout().name()), result);
        }

        private Set<PortableResourceDescriptor> dependencies(@Nullable QIOVerifiedWorkbenchPattern verified) {
            if (verified != null) return verified.getInputs().keySet();
            Set<PortableResourceDescriptor> inputs = new LinkedHashSet<>();
            if (fixed == null) {
                for (Choice choice : choices) if (!choice.getStack().isEmpty()) inputs.add(choice.getResource());
            } else {
                for (Stack stack : grid) if (!stack.isEmpty()) inputs.add(stack.getResource());
            }
            return inputs;
        }
    }

    public static final class Round {
        private final List<Candidate> selected;
        private final List<Candidate> validation;
        private final Set<String> roots;
        private final int products, existing, leaves, cycles, skippedCycles;
        private final List<String> cyclePaths;
        private final List<String> diagnostics;
        private final boolean truncated;

        private Round(Walker walker) {
            selected = Collections.unmodifiableList(new ArrayList<>(walker.selected.values()));
            List<Candidate> missing = new ArrayList<>();
            for (Candidate candidate : selected)
                if (!walker.verified.containsKey(candidate.key)) missing.add(candidate);
            validation = Collections.unmodifiableList(missing);
            roots = Collections.unmodifiableSet(new LinkedHashSet<>(walker.roots));
            products = walker.products.size();
            existing = walker.existing.size();
            leaves = walker.leaves.size();
            cycles = walker.cycles;
            skippedCycles = walker.skippedCycles;
            cyclePaths = Collections.unmodifiableList(new ArrayList<>(walker.cyclePaths));
            diagnostics = Collections.unmodifiableList(new ArrayList<>(walker.diagnostics));
            truncated = walker.truncated;
        }

        public List<Candidate> getSelected() {
            return selected;
        }

        public List<Candidate> getValidationRequests() {
            return validation;
        }

        public Set<String> getRoots() {
            return roots;
        }

        public int getNewProductCount() {
            return products;
        }

        public int getExistingRecipeCount() {
            return existing;
        }

        public int getNewRecipeCount() {
            return selected.size() - existing;
        }

        public int getLeafMaterialCount() {
            return leaves;
        }

        public int getCycleCount() {
            return cycles;
        }

        public int getSkippedCyclicRecipeCount() {
            return skippedCycles;
        }

        public List<String> getCyclePaths() {
            return cyclePaths;
        }

        public List<String> getDiagnostics() {
            return diagnostics;
        }

        public boolean isTruncated() {
            return truncated;
        }
    }

    public static Round discover(Input input, Map<String, QIOVerifiedWorkbenchPattern> verified,
                                 Set<String> rejected, BooleanSupplier cancelled) {
        try (QIOPlanningMetrics.Scope timing = QIOPlanningMetrics.current().measure(QIOPlanningMetrics.Phase.DISCOVERY)) {
            return new Walker(input, verified, rejected, cancelled).run();
        }
    }

    public static String fixedKey(QIOWorkbenchConfiguration.EncodedPattern pattern) {
        return new Candidate(pattern).getKey();
    }

    public static Input manualRoot(Input input, QIOWorkbenchConfiguration.EncodedPattern root) {
        return new Input(input.directory, input.plannerBindings, input.configuration, Collections.singletonList(root),
                Collections.emptyList(), input.mode, input.skipCycles);
    }

    public static String declarationSignature(Input input, ResourceLocation id, List<Stack> grid) {
        Recipe declaration = input.directory.get(id);
        if (declaration != null) return declaration.getSignature();
        StringBuilder signature = new StringBuilder("manual|").append(id);
        for (Stack stack : grid) signature.append('|').append(stack.getKey());
        return QIOHashing.sha256(signature);
    }

    public static String plannerSignature(Input input, ResourceLocation id, String declarationSignature) {
        return input.plannerBindings.getOrDefault(id, declarationSignature);
    }

    private static final class Walker {
        final Input input;
        final Map<String, QIOVerifiedWorkbenchPattern> verified;
        final Set<String> rejected;
        final BooleanSupplier cancelled;
        final Map<String, Candidate> selected = new LinkedHashMap<>();
        final Set<String> roots = new LinkedHashSet<>();
        final Set<PortableResourceDescriptor> visited = new LinkedHashSet<>();
        final Set<PortableResourceDescriptor> products = new LinkedHashSet<>();
        final Set<PortableResourceDescriptor> leaves = new LinkedHashSet<>();
        final Set<String> existing = new LinkedHashSet<>();
        final List<String> cyclePaths = new ArrayList<>();
        final Set<String> diagnostics = new LinkedHashSet<>();
        int cycles, skippedCycles;
        boolean truncated;

        Walker(Input input, Map<String, QIOVerifiedWorkbenchPattern> verified, Set<String> rejected, BooleanSupplier cancelled) {
            this.input = input;
            this.verified = verified;
            this.rejected = rejected;
            this.cancelled = cancelled;
        }

        Round run() {
            for (QIOWorkbenchConfiguration.EncodedPattern root : input.fixedRoots) {
                check();
                Candidate candidate = new Candidate(root);
                if (rejected.contains(candidate.key))
                    throw new IllegalArgumentException("Selected root grid is invalid: " + root.getRecipeId());
                add(candidate, true);
                if (input.mode != QIOWorkbenchClosureMode.NONE)
                    expand(candidate, 0, new ArrayList<>(Collections.singletonList(candidate.expectedOutput)));
            }
            for (PortableResourceDescriptor target : input.targets) visit(target, 0, new ArrayList<>(), true);
            return new Round(this);
        }

        void visit(PortableResourceDescriptor output, int depth, List<PortableResourceDescriptor> path, boolean root) {
            check();
            if (depth > MAX_DEPTH || visited.size() >= MAX_OUTPUTS || selected.size() >= MAX_PATTERNS) {
                truncated = true;
                diagnostic("DISCOVERY_GRAPH_BUDGET_REACHED");
                return;
            }
            if (!root && !visited.add(output)) return;
            List<Recipe> recipes = input.directory.selecting(output);
            List<String> unsupported = input.directory.rejectedSelecting(output);
            if (!unsupported.isEmpty()) {
                truncated = true;
                unsupported.forEach(reason -> diagnostic("UNSUPPORTED_DECLARED_PRODUCER: " + reason));
            }
            if (recipes.isEmpty()) {
                leaves.add(output);
                if (root) {
                    truncated = true;
                    diagnostic("NO_DECLARED_ROOT_PRODUCER: " + output);
                }
                // A declaration-only leaf means no admitted producer, not proof of a raw material.
                return;
            }
            Map<String, Recipe> byId = new LinkedHashMap<>();
            for (Recipe recipe : recipes) byId.put(recipe.getId().toString(), recipe);
            List<String> order = input.configuration.orderedRecipes(QIOWorkbenchRecipeCatalog.productKey(output), new ArrayList<>(byId.keySet()));
            boolean found = false;
            for (String id : order) {
                check();
                Recipe recipe = byId.get(id);
                String binding = input.plannerBindings.getOrDefault(recipe.getId(), recipe.getSignature());
                if (input.mode == QIOWorkbenchClosureMode.PREFERRED && !input.configuration.isRecipeEnabled(id, binding))
                    continue;
                Candidate candidate = selected.get(id);
                if (candidate == null) candidate = choose(recipe, binding);
                if (candidate == null) {
                    truncated = true;
                    diagnostic("NO_ADMITTED_GRID: " + id);
                    continue;
                }
                List<PortableResourceDescriptor> active = new ArrayList<>(path);
                active.add(candidate.expectedOutput);
                boolean cycle = false;
                if (!root && input.skipCycles) {
                    for (PortableResourceDescriptor dependency : candidate.dependencies(verified.get(candidate.key))) {
                        if (active.contains(dependency)) {
                            recordCycle(active, dependency);
                            cycle = true;
                        }
                    }
                }
                if (!root && input.skipCycles && cycle) {
                    skippedCycles++;
                    continue;
                }
                found = true;
                add(candidate, root);
                if (input.mode != QIOWorkbenchClosureMode.NONE) expand(candidate, depth, active);
                if (input.mode == QIOWorkbenchClosureMode.PREFERRED) break;
            }
            if (!found) leaves.add(output);
        }

        @Nullable
        Candidate choose(Recipe recipe, String binding) {
            if (rejected.contains("recipe:" + recipe.getId())) return null;
            List<List<Choice>> slots = new ArrayList<>(9);
            QIOWorkbenchConfiguration.EncodedPattern existing = input.configuration.getEncodedPattern(recipe.getId().toString());
            boolean completeIdentities = existing != null && existing.getVerified() != null;
            for (int slot = 0; slot < 9; slot++) {
                List<Choice> candidates = recipe.getSlots().get(slot);
                if (candidates.size() == 1 && candidates.get(0).getStack().isEmpty()) {
                    slots.add(candidates);
                    continue;
                }
                List<Choice> defaults = new ArrayList<>(candidates);
                defaults.sort(Comparator.comparing(Choice::getSelectionSortKey).thenComparing(choice -> choice.getStack().getKey()));
                Map<String, List<Choice>> grouped = new LinkedHashMap<>();
                for (Choice choice : defaults)
                    grouped.computeIfAbsent(completeIdentities ? choice.getCandidateId() :
                            choice.getLegacyId(), ignored -> new ArrayList<>()).add(choice);
                List<Choice> ordered = new ArrayList<>();
                for (String id : input.configuration.orderedEnabledCandidates(recipe.getId().toString(), binding, slot,
                        new ArrayList<>(grouped.keySet())))
                    ordered.addAll(grouped.get(id));
                if (ordered.isEmpty()) return null;
                slots.add(ordered);
            }
            int[] indices = new int[9];
            for (int attempt = 0; attempt < MAX_COMBINATIONS; attempt++) {
                check();
                List<Choice> grid = new ArrayList<>(9);
                for (int slot = 0; slot < 9; slot++) grid.add(slots.get(slot).get(indices[slot]));
                Candidate candidate = new Candidate(recipe, grid, binding);
                if (!rejected.contains(candidate.key)) return candidate;
                int slot = 8;
                while (slot >= 0 && ++indices[slot] == slots.get(slot).size()) {
                    indices[slot] = 0;
                    slot--;
                }
                if (slot < 0) return null;
            }
            truncated = true;
            diagnostic("DISCOVERY_CANDIDATE_BUDGET_REACHED: " + recipe.getId());
            return null;
        }

        void add(Candidate candidate, boolean root) {
            String id = candidate.recipeId.toString();
            if (selected.size() >= MAX_PATTERNS && !selected.containsKey(id)) {
                truncated = true;
                diagnostic("DISCOVERY_PATTERN_BUDGET_REACHED");
                return;
            }
            // An explicit root keeps its exact grid when another dependency reaches the same recipe.
            selected.putIfAbsent(id, candidate);
            if (root) roots.add(id);
            if (input.configuration.getEncodedPattern(id) == null) products.add(candidate.expectedOutput);
            else existing.add(id);
        }

        void expand(Candidate candidate, int depth, List<PortableResourceDescriptor> path) {
            for (PortableResourceDescriptor dependency : candidate.dependencies(verified.get(candidate.key))) {
                check();
                if (path.contains(dependency)) {
                    recordCycle(path, dependency);
                    continue;
                }
                visit(dependency, depth + 1, path, false);
            }
        }

        void recordCycle(List<PortableResourceDescriptor> path, PortableResourceDescriptor dependency) {
            cycles++;
            if (cyclePaths.size() >= 16) return;
            StringBuilder message = new StringBuilder();
            int start = path.indexOf(dependency);
            for (int index = Math.max(0, start); index < path.size(); index++) {
                if (message.length() > 0) message.append(" -> ");
                message.append(path.get(index));
            }
            message.append(" -> ").append(dependency);
            cyclePaths.add(message.substring(0, Math.min(2048, message.length())));
        }

        void check() {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException();
        }

        void diagnostic(String message) {
            if (diagnostics.size() < 16) diagnostics.add(message.substring(0, Math.min(512, message.length())));
        }
    }
}
