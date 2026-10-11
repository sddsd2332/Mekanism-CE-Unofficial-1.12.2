package mekanism.qioprocessing.common.planning;

import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.api.qio.external.QIOStorageSnapshot;
import mekanism.qioprocessing.common.pattern.VerifiedWorkbenchPattern;
import mekanism.qioprocessing.common.pattern.WorkbenchPatternIndex;
import mekanism.qioprocessing.common.recipe.RecipeItemKey;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.math.BigInteger;
import java.util.*;

/**
 * Frozen order provenance; no persisted progress or authority to execute a recipe.
 */
public final class QIOWorkbenchExecutionManifest {
    private static final int VERSION = 2;
    private static final int MAX_TARGETS = 128;
    private static final int MAX_WITHDRAWALS = 4096;
    private static final int MAX_STEPS = 2048;
    private static final long MAX_OPERATIONS = 100_000;
    private static final long MAX_ESTIMATED_BYTES = 2L * 1024 * 1024;

    public static final class Withdrawal {
        private final RecipeItemKey item;
        private final long amount;

        private Withdrawal(RecipeItemKey item, long amount) {
            this.item = Objects.requireNonNull(item);
            if (amount <= 0) throw new IllegalArgumentException("Invalid manifest withdrawal");
            this.amount = amount;
        }

        public RecipeItemKey item() {
            return item;
        }

        public long amount() {
            return amount;
        }
    }

    public static final class Step {
        private final VerifiedWorkbenchPattern pattern;
        private final long operations;

        private Step(VerifiedWorkbenchPattern pattern, long operations) {
            this.pattern = Objects.requireNonNull(pattern);
            if (operations <= 0 || operations > MAX_OPERATIONS)
                throw new IllegalArgumentException("Invalid manifest operation count");
            this.operations = operations;
            Math.multiplyExact(operations, pattern.outputCount());
            for (int count : pattern.consumedInputs().values()) Math.multiplyExact(operations, count);
        }

        public VerifiedWorkbenchPattern pattern() {
            return pattern;
        }

        public long operations() {
            return operations;
        }
    }

    private final String generation;
    private final long revision;
    private final int dimension;
    private final Map<RecipeItemKey, Long> targets;
    private final Map<UUID, Withdrawal> withdrawals;
    private final List<Step> steps;

    private QIOWorkbenchExecutionManifest(String generation, long revision, int dimension, Map<RecipeItemKey, Long> targets,
                                          Map<UUID, Withdrawal> withdrawals, List<Step> steps) {
        if (generation == null || !generation.matches("[a-f0-9]{64}") || revision < 0
                || targets.isEmpty() || targets.size() > MAX_TARGETS || withdrawals.isEmpty()
                || withdrawals.size() > MAX_WITHDRAWALS || steps.size() > MAX_STEPS) {
            throw new IllegalArgumentException("Invalid workbench execution manifest bounds");
        }
        this.generation = generation;
        this.revision = revision;
        this.dimension = dimension;
        Map<RecipeItemKey, Long> targetCopy = new LinkedHashMap<>();
        targets.forEach((item, amount) -> {
            if (item == null || amount == null || amount <= 0)
                throw new IllegalArgumentException("Invalid manifest target");
            targetCopy.put(item, amount);
        });
        this.targets = Collections.unmodifiableMap(targetCopy);
        Map<UUID, Withdrawal> withdrawalCopy = new LinkedHashMap<>();
        Map<RecipeItemKey, UUID> itemIds = new LinkedHashMap<>();
        withdrawals.forEach((resource, withdrawal) -> {
            if (resource == null || withdrawal == null
                    || itemIds.putIfAbsent(withdrawal.item(), resource) != null) {
                throw new IllegalArgumentException("Duplicate or invalid manifest resource");
            }
            withdrawalCopy.put(resource, withdrawal);
        });
        this.withdrawals = Collections.unmodifiableMap(withdrawalCopy);
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        long estimated = 1024;
        long totalOperations = 0;
        for (RecipeItemKey item : targets.keySet()) estimated = Math.addExact(estimated, estimate(item));
        for (Withdrawal withdrawal : withdrawals.values())
            estimated = Math.addExact(estimated, estimate(withdrawal.item()));
        for (Step step : steps) {
            if (!generation.equals(step.pattern.catalogGeneration()))
                throw new IllegalArgumentException("Stale manifest pattern");
            totalOperations = Math.addExact(totalOperations, step.operations());
            if (totalOperations > MAX_OPERATIONS)
                throw new IllegalArgumentException("Manifest operation quota exceeded");
            estimated = Math.addExact(estimated, step.pattern.retainedBytes());
        }
        if (estimated > MAX_ESTIMATED_BYTES)
            throw new IllegalArgumentException("Workbench manifest memory quota exceeded");
        verifyMaterialFlow();
    }

    private static long estimate(RecipeItemKey item) {
        return item.displayNBT().toString().length() * 2L + 96;
    }

    private void verifyMaterialFlow() {
        Map<RecipeItemKey, Long> stock = new LinkedHashMap<>();
        for (Withdrawal withdrawal : withdrawals.values()) stock.put(withdrawal.item(), withdrawal.amount());
        try {
            for (Step step : steps) {
                for (Map.Entry<RecipeItemKey, Integer> input : step.pattern().consumedInputs().entrySet()) {
                    consume(stock, input.getKey(), Math.multiplyExact(step.operations(), input.getValue()));
                }
                for (RecipeItemKey remainder : step.pattern().remainders()) {
                    if (remainder != null) add(stock, remainder, step.operations());
                }
                add(stock, step.pattern().output(), Math.multiplyExact(step.operations(), step.pattern().outputCount()));
            }
            for (Map.Entry<RecipeItemKey, Long> target : targets.entrySet()) {
                consume(stock, target.getKey(), target.getValue());
            }
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("Workbench manifest material flow overflow", error);
        }
    }

    private static void consume(Map<RecipeItemKey, Long> stock, RecipeItemKey item, long amount) {
        long available = stock.getOrDefault(item, 0L);
        if (available < amount) throw new IllegalArgumentException("Workbench manifest material flow is insufficient");
        if (available == amount) stock.remove(item);
        else stock.put(item, available - amount);
    }

    private static void add(Map<RecipeItemKey, Long> stock, RecipeItemKey item, long amount) {
        stock.put(item, Math.addExact(stock.getOrDefault(item, 0L), amount));
    }

    public static QIOWorkbenchExecutionManifest capture(QIOPatternPlanner.Result plan, WorkbenchPatternIndex index,
                                                        QIOStorageSnapshot storage) {
        return capture(plan, index, storage, 0);
    }

    public static QIOWorkbenchExecutionManifest capture(QIOPatternPlanner.Result plan, WorkbenchPatternIndex index,
                                                        QIOStorageSnapshot storage, int dimension) {
        Objects.requireNonNull(plan);
        Objects.requireNonNull(index);
        Objects.requireNonNull(storage);
        if (!plan.successful() || !plan.generation().equals(index.generation()) || plan.revision() != index.revision()) {
            throw new IllegalArgumentException("Manifest requires a current successful plan");
        }
        if (plan.targets().size() > MAX_TARGETS || plan.initialWithdrawals().size() > MAX_WITHDRAWALS
                || plan.steps().size() > MAX_STEPS) throw new IllegalArgumentException("Manifest plan exceeds quota");
        Map<UUID, Withdrawal> withdrawals = new LinkedHashMap<>();
        Map<RecipeItemKey, UUID> found = new LinkedHashMap<>();
        for (QIOStorageEntry entry : storage.getEntries()) {
            ItemStack stack = entry.getItem();
            if (stack.isEmpty()) continue;
            RecipeItemKey item = RecipeItemKey.capture(stack);
            Long needed = plan.initialWithdrawals().get(item);
            if (needed == null) continue;
            if (entry.getExactAvailableAmount().compareTo(BigInteger.valueOf(needed)) < 0
                    || found.putIfAbsent(item, entry.getResourceUUID()) != null
                    || withdrawals.putIfAbsent(entry.getResourceUUID(), new Withdrawal(item, needed)) != null) {
                throw new IllegalArgumentException("Ambiguous or unavailable manifest resource");
            }
        }
        if (found.size() != plan.initialWithdrawals().size())
            throw new IllegalArgumentException("Missing manifest resource");
        List<Step> steps = new ArrayList<>();
        for (QIOPatternPlanner.Step planned : plan.steps()) {
            VerifiedWorkbenchPattern match = null;
            for (VerifiedWorkbenchPattern candidate : index.patterns(planned.recipeId())) {
                if (!candidate.id().equals(planned.patternId())) continue;
                if (match != null) throw new IllegalArgumentException("Ambiguous manifest pattern");
                match = candidate;
            }
            if (match == null || !match.output().equals(planned.output()))
                throw new IllegalArgumentException("Missing manifest pattern");
            Step frozen = new Step(match, planned.operations());
            Map<RecipeItemKey, Long> inputs = new LinkedHashMap<>(), remainders = new LinkedHashMap<>();
            for (Map.Entry<RecipeItemKey, Integer> input : match.consumedInputs().entrySet()) {
                inputs.put(input.getKey(), Math.multiplyExact(frozen.operations(), input.getValue()));
            }
            for (RecipeItemKey remainder : match.remainders()) {
                if (remainder != null) remainders.merge(remainder, frozen.operations(), Math::addExact);
            }
            if (!inputs.equals(planned.inputs()) || !remainders.equals(planned.remainders())) {
                throw new IllegalArgumentException("Manifest step differs from planned amounts");
            }
            steps.add(frozen);
        }
        return new QIOWorkbenchExecutionManifest(plan.generation(), plan.revision(), dimension, plan.targets(), withdrawals, steps);
    }

    public String generation() {
        return generation;
    }

    public long revision() {
        return revision;
    }

    public int dimension() {
        return dimension;
    }

    public Map<RecipeItemKey, Long> targets() {
        return targets;
    }

    public Map<UUID, Withdrawal> withdrawals() {
        return withdrawals;
    }

    public List<Step> steps() {
        return steps;
    }

    public Map<UUID, Long> claimAmounts() {
        Map<UUID, Long> result = new LinkedHashMap<>();
        withdrawals.forEach((resource, withdrawal) -> result.put(resource, withdrawal.amount()));
        return Collections.unmodifiableMap(result);
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("version", VERSION);
        tag.setString("generation", generation);
        tag.setLong("revision", revision);
        tag.setInteger("dimension", dimension);
        NBTTagList targetTags = new NBTTagList();
        targets.forEach((item, amount) -> {
            NBTTagCompound row = new NBTTagCompound();
            row.setTag("item", item.displayNBT());
            row.setLong("amount", amount);
            targetTags.appendTag(row);
        });
        tag.setTag("targets", targetTags);
        NBTTagList withdrawalTags = new NBTTagList();
        withdrawals.forEach((resource, withdrawal) -> {
            NBTTagCompound row = new NBTTagCompound();
            row.setUniqueId("resource", resource);
            row.setTag("item", withdrawal.item().displayNBT());
            row.setLong("amount", withdrawal.amount());
            withdrawalTags.appendTag(row);
        });
        tag.setTag("withdrawals", withdrawalTags);
        NBTTagList stepTags = new NBTTagList();
        for (Step step : steps) {
            NBTTagCompound row = new NBTTagCompound();
            row.setTag("pattern", step.pattern().write());
            row.setLong("operations", step.operations());
            stepTags.appendTag(row);
        }
        tag.setTag("steps", stepTags);
        return tag;
    }

    public static QIOWorkbenchExecutionManifest read(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey("version", 3) || (tag.getInteger("version") != VERSION && tag.getInteger("version") != 1)
                || !tag.hasKey("generation", 8) || !tag.hasKey("revision", 4)
                || !tag.hasKey("targets", 9) || !tag.hasKey("withdrawals", 9) || !tag.hasKey("steps", 9)) {
            throw new IllegalArgumentException("Invalid manifest header");
        }
        NBTTagList rawSteps = (NBTTagList) tag.getTag("steps");
        if (rawSteps.tagCount() > 0 && rawSteps.getTagType() != 10) {
            throw new IllegalArgumentException("Invalid manifest step type");
        }
        NBTTagList targetTags = tag.getTagList("targets", 10), withdrawalTags = tag.getTagList("withdrawals", 10);
        NBTTagList stepTags = tag.getTagList("steps", 10);
        if (targetTags.tagCount() == 0 || targetTags.tagCount() > MAX_TARGETS
                || withdrawalTags.tagCount() == 0 || withdrawalTags.tagCount() > MAX_WITHDRAWALS
                || stepTags.tagCount() > MAX_STEPS) {
            throw new IllegalArgumentException("Invalid manifest list bounds");
        }
        Map<RecipeItemKey, Long> targets = new LinkedHashMap<>();
        for (int index = 0; index < targetTags.tagCount(); index++) {
            NBTTagCompound row = targetTags.getCompoundTagAt(index);
            RecipeItemKey item = item(row);
            long amount = amount(row);
            if (targets.putIfAbsent(item, amount) != null)
                throw new IllegalArgumentException("Duplicate manifest target");
        }
        Map<UUID, Withdrawal> withdrawals = new LinkedHashMap<>();
        for (int index = 0; index < withdrawalTags.tagCount(); index++) {
            NBTTagCompound row = withdrawalTags.getCompoundTagAt(index);
            if (!row.hasUniqueId("resource") || withdrawals.putIfAbsent(row.getUniqueId("resource"),
                    new Withdrawal(item(row), amount(row))) != null)
                throw new IllegalArgumentException("Duplicate manifest withdrawal");
        }
        List<Step> steps = new ArrayList<>();
        for (int index = 0; index < stepTags.tagCount(); index++) {
            NBTTagCompound row = stepTags.getCompoundTagAt(index);
            if (!row.hasKey("pattern", 10) || !row.hasKey("operations", 4)) {
                throw new IllegalArgumentException("Invalid manifest step");
            }
            steps.add(new Step(VerifiedWorkbenchPattern.read(row.getCompoundTag("pattern")), row.getLong("operations")));
        }
        int dimension = tag.getInteger("version") == 1 ? 0 : tag.getInteger("dimension");
        return new QIOWorkbenchExecutionManifest(tag.getString("generation"), tag.getLong("revision"), dimension, targets, withdrawals, steps);
    }

    private static RecipeItemKey item(NBTTagCompound row) {
        if (!row.hasKey("item", 10)) throw new IllegalArgumentException("Missing manifest item");
        return RecipeItemKey.capture(new ItemStack(row.getCompoundTag("item")));
    }

    private static long amount(NBTTagCompound row) {
        if (!row.hasKey("amount", 4) || row.getLong("amount") <= 0)
            throw new IllegalArgumentException("Invalid manifest amount");
        return row.getLong("amount");
    }
}
