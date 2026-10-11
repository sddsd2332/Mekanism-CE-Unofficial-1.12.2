package mekanism.qioprocessing.common.planning;

import mekanism.api.processing.MachineResourceStack;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.math.BigInteger;
import java.util.*;

/**
 * Durable, immutable Provider-route plan; it contains no live tile or world references.
 */
public final class QIOProviderExecutionManifest {
    public static final int VERSION = 1;
    private static final int MAX_TARGETS = 128;
    private static final int MAX_WITHDRAWALS = 4096;
    private static final int MAX_STEPS = 4096;
    private static final int MAX_STACKS_PER_STEP = 256;
    private static final long MAX_OPERATIONS = 100_000;

    public static final class Step {
        private final String sourceId;
        private final String providerId;
        private final String profileScopeDiscriminator;
        private final int configurationRevision;
        private final String routeId;
        private final String recipeKey;
        private final String logicalRecipeKey;
        private final long operations;
        private final List<MachineResourceStack> configurationInputs;
        private final List<MachineResourceStack> inputs;
        private final List<MachineResourceStack> outputs;

        private Step(String sourceId, String providerId, String profileScopeDiscriminator,
                     int configurationRevision, String routeId, String recipeKey,
                     String logicalRecipeKey, long operations,
                     List<MachineResourceStack> configurationInputs,
                     List<MachineResourceStack> inputs, List<MachineResourceStack> outputs) {
            this.sourceId = text(sourceId, 256, "sourceId");
            this.providerId = text(providerId, 256, "providerId");
            this.profileScopeDiscriminator = optionalText(profileScopeDiscriminator, 128, "profileScopeDiscriminator");
            if (configurationRevision < 0 || operations <= 0 || operations > MAX_OPERATIONS) {
                throw new IllegalArgumentException("Invalid Provider manifest step metadata");
            }
            this.configurationRevision = configurationRevision;
            this.routeId = text(routeId, 512, "routeId");
            this.recipeKey = text(recipeKey, 512, "recipeKey");
            this.logicalRecipeKey = text(logicalRecipeKey, 512, "logicalRecipeKey");
            this.operations = operations;
            this.configurationInputs = stacks(configurationInputs, false);
            this.inputs = stacks(inputs, true);
            this.outputs = stacks(outputs, true);
        }

        static Step capture(QIOProviderRouteCatalog.RouteSnapshot route, long operations) {
            mekanism.api.processing.MachineRecipeRoute definition = route.route();
            List<MachineResourceStack> inputs = scale(definition.inputs(), operations);
            List<MachineResourceStack> outputs = scale(definition.guaranteedOutputs(), operations);
            return new Step(route.sourceId(), route.providerId().toString(), route.profileScopeDiscriminator(),
                    route.configurationRevision(), definition.routeId(), definition.recipeKey(),
                    definition.logicalRecipeKey(), operations, definition.configurationInputs(), inputs, outputs);
        }

        public String sourceId() {
            return sourceId;
        }

        public String providerId() {
            return providerId;
        }

        public String profileScopeDiscriminator() {
            return profileScopeDiscriminator;
        }

        public int configurationRevision() {
            return configurationRevision;
        }

        public String routeId() {
            return routeId;
        }

        public String recipeKey() {
            return recipeKey;
        }

        public String logicalRecipeKey() {
            return logicalRecipeKey;
        }

        public long operations() {
            return operations;
        }

        public List<MachineResourceStack> configurationInputs() {
            return configurationInputs;
        }

        public List<MachineResourceStack> inputs() {
            return inputs;
        }

        public List<MachineResourceStack> outputs() {
            return outputs;
        }

        private NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("sourceId", sourceId);
            tag.setString("providerId", providerId);
            tag.setString("scope", profileScopeDiscriminator);
            tag.setInteger("configurationRevision", configurationRevision);
            tag.setString("routeId", routeId);
            tag.setString("recipeKey", recipeKey);
            tag.setString("logicalRecipeKey", logicalRecipeKey);
            tag.setLong("operations", operations);
            tag.setTag("configurationInputs", writeStacks(configurationInputs));
            tag.setTag("inputs", writeStacks(inputs));
            tag.setTag("outputs", writeStacks(outputs));
            return tag;
        }

        private static Step read(NBTTagCompound tag) {
            if (tag == null || !tag.hasKey("sourceId", 8) || !tag.hasKey("providerId", 8)
                    || !tag.hasKey("scope", 8) || !tag.hasKey("configurationRevision", 3)
                    || !tag.hasKey("routeId", 8) || !tag.hasKey("recipeKey", 8)
                    || !tag.hasKey("logicalRecipeKey", 8) || !tag.hasKey("operations", 4)
                    || !tag.hasKey("configurationInputs", 9) || !tag.hasKey("inputs", 9)
                    || !tag.hasKey("outputs", 9)) {
                throw new IllegalArgumentException("Invalid Provider manifest step");
            }
            return new Step(tag.getString("sourceId"), tag.getString("providerId"), tag.getString("scope"),
                    tag.getInteger("configurationRevision"), tag.getString("routeId"),
                    tag.getString("recipeKey"), tag.getString("logicalRecipeKey"), tag.getLong("operations"),
                    readStacks(tag.getTagList("configurationInputs", 10)),
                    readStacks(tag.getTagList("inputs", 10)), readStacks(tag.getTagList("outputs", 10)));
        }
    }

    private final long catalogRevision;
    private final long availabilityRevision;
    private final Map<QIOResourceDescriptor, Long> targets;
    private final Map<QIOResourceDescriptor, Long> initialWithdrawals;
    private final List<Step> steps;

    private QIOProviderExecutionManifest(long catalogRevision, long availabilityRevision,
                                         Map<QIOResourceDescriptor, Long> targets,
                                         Map<QIOResourceDescriptor, Long> initialWithdrawals, List<Step> steps) {
        if (catalogRevision < 0 || availabilityRevision < 0 || targets.isEmpty()
                || targets.size() > MAX_TARGETS || initialWithdrawals.isEmpty()
                || initialWithdrawals.size() > MAX_WITHDRAWALS || steps.isEmpty()
                || steps.size() > MAX_STEPS) {
            throw new IllegalArgumentException("Invalid Provider execution manifest bounds");
        }
        this.catalogRevision = catalogRevision;
        this.availabilityRevision = availabilityRevision;
        this.targets = positiveMap(targets, MAX_TARGETS);
        this.initialWithdrawals = positiveMap(initialWithdrawals, MAX_WITHDRAWALS);
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        long operations = 0;
        for (Step step : this.steps) operations = Math.addExact(operations, step.operations());
        if (operations > MAX_OPERATIONS)
            throw new IllegalArgumentException("Provider manifest operation quota exceeded");
        verifyMaterialFlow();
    }

    public static QIOProviderExecutionManifest capture(QIOProviderRoutePlanner.Result result) {
        Objects.requireNonNull(result, "result");
        if (!result.successful()) throw new IllegalArgumentException("Manifest requires a successful Provider plan");
        List<Step> steps = new ArrayList<>();
        for (QIOProviderRoutePlanner.Step step : result.steps()) {
            steps.add(Step.capture(step.route(), step.operations()));
        }
        return new QIOProviderExecutionManifest(result.catalog().revision(),
                result.catalog().availabilityRevision(), result.targets(), result.initialWithdrawals(), steps);
    }

    public long catalogRevision() {
        return catalogRevision;
    }

    public long availabilityRevision() {
        return availabilityRevision;
    }

    public Map<QIOResourceDescriptor, Long> targets() {
        return targets;
    }

    public Map<QIOResourceDescriptor, Long> initialWithdrawals() {
        return initialWithdrawals;
    }

    public List<Step> steps() {
        return steps;
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("version", VERSION);
        tag.setLong("catalogRevision", catalogRevision);
        tag.setLong("availabilityRevision", availabilityRevision);
        tag.setTag("targets", writeAmounts(targets));
        tag.setTag("initialWithdrawals", writeAmounts(initialWithdrawals));
        NBTTagList stored = new NBTTagList();
        for (Step step : steps) stored.appendTag(step.write());
        tag.setTag("steps", stored);
        return tag;
    }

    public static QIOProviderExecutionManifest read(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey("version", 3) || tag.getInteger("version") != VERSION
                || !tag.hasKey("catalogRevision", 4) || !tag.hasKey("availabilityRevision", 4)
                || !tag.hasKey("targets", 9) || !tag.hasKey("initialWithdrawals", 9)
                || !tag.hasKey("steps", 9)) throw new IllegalArgumentException("Invalid Provider manifest header");
        NBTTagList rawSteps = tag.getTagList("steps", 10);
        if (rawSteps.tagCount() == 0 || rawSteps.tagCount() > MAX_STEPS) {
            throw new IllegalArgumentException("Invalid Provider manifest step count");
        }
        List<Step> steps = new ArrayList<>();
        for (int index = 0; index < rawSteps.tagCount(); index++)
            steps.add(Step.read(rawSteps.getCompoundTagAt(index)));
        return new QIOProviderExecutionManifest(tag.getLong("catalogRevision"),
                tag.getLong("availabilityRevision"), readAmounts(tag.getTagList("targets", 10), MAX_TARGETS),
                readAmounts(tag.getTagList("initialWithdrawals", 10), MAX_WITHDRAWALS), steps);
    }

    private void verifyMaterialFlow() {
        Map<QIOResourceDescriptor, BigInteger> stock = new LinkedHashMap<>();
        initialWithdrawals.forEach((resource, amount) -> stock.put(resource, BigInteger.valueOf(amount)));
        for (Step step : steps) {
            for (MachineResourceStack input : step.inputs()) consume(stock, input.descriptor(), input.amount());
            for (MachineResourceStack output : step.outputs()) add(stock, output.descriptor(), output.amount());
        }
        for (Map.Entry<QIOResourceDescriptor, Long> target : targets.entrySet()) {
            consume(stock, target.getKey(), target.getValue());
        }
    }

    private static void consume(Map<QIOResourceDescriptor, BigInteger> stock,
                                QIOResourceDescriptor resource, long amount) {
        BigInteger current = stock.getOrDefault(resource, BigInteger.ZERO);
        BigInteger next = current.subtract(BigInteger.valueOf(amount));
        if (next.signum() < 0) throw new IllegalArgumentException("Provider manifest material flow is insufficient");
        if (next.signum() == 0) stock.remove(resource);
        else stock.put(resource, next);
    }

    private static void add(Map<QIOResourceDescriptor, BigInteger> stock,
                            QIOResourceDescriptor resource, long amount) {
        stock.put(resource, stock.getOrDefault(resource, BigInteger.ZERO).add(BigInteger.valueOf(amount)));
    }

    private static List<MachineResourceStack> scale(List<MachineResourceStack> values, long operations) {
        if (values.size() > MAX_STACKS_PER_STEP) throw new IllegalArgumentException("Too many Provider route stacks");
        List<MachineResourceStack> result = new ArrayList<>();
        for (MachineResourceStack value : values) result.add(value.scale(operations));
        return result;
    }

    private static List<MachineResourceStack> stacks(List<MachineResourceStack> values, boolean required) {
        Objects.requireNonNull(values, "Provider manifest stacks");
        if (values.size() > MAX_STACKS_PER_STEP || required && values.isEmpty()) {
            throw new IllegalArgumentException("Invalid Provider manifest stacks");
        }
        List<MachineResourceStack> result = new ArrayList<>();
        for (MachineResourceStack value : values) result.add(Objects.requireNonNull(value, "Provider manifest stack"));
        return Collections.unmodifiableList(result);
    }

    private static Map<QIOResourceDescriptor, Long> positiveMap(Map<QIOResourceDescriptor, Long> values, int maximum) {
        Map<QIOResourceDescriptor, Long> result = new LinkedHashMap<>();
        values.forEach((resource, amount) -> {
            if (resource == null || amount == null || amount <= 0)
                throw new IllegalArgumentException("Invalid Provider manifest amount");
            result.put(resource, amount);
        });
        if (result.size() > maximum) throw new IllegalArgumentException("Provider manifest resource quota exceeded");
        return Collections.unmodifiableMap(result);
    }

    private static NBTTagList writeAmounts(Map<QIOResourceDescriptor, Long> values) {
        NBTTagList stored = new NBTTagList();
        values.forEach((resource, amount) -> {
            NBTTagCompound row = new NBTTagCompound();
            row.setTag("resource", resource.write());
            row.setLong("amount", amount);
            stored.appendTag(row);
        });
        return stored;
    }

    private static Map<QIOResourceDescriptor, Long> readAmounts(NBTTagList stored, int maximum) {
        if (stored.tagCount() == 0 || stored.tagCount() > maximum)
            throw new IllegalArgumentException("Invalid Provider manifest amount list");
        Map<QIOResourceDescriptor, Long> result = new LinkedHashMap<>();
        for (int index = 0; index < stored.tagCount(); index++) {
            NBTTagCompound row = stored.getCompoundTagAt(index);
            if (!row.hasKey("resource", 10) || !row.hasKey("amount", 4) || row.getLong("amount") <= 0) {
                throw new IllegalArgumentException("Invalid Provider manifest amount");
            }
            QIOResourceDescriptor resource = QIOResourceDescriptor.read(row.getCompoundTag("resource"));
            if (result.putIfAbsent(resource, row.getLong("amount")) != null) {
                throw new IllegalArgumentException("Duplicate Provider manifest resource");
            }
        }
        return result;
    }

    private static NBTTagList writeStacks(List<MachineResourceStack> values) {
        NBTTagList stored = new NBTTagList();
        for (MachineResourceStack value : values) stored.appendTag(value.write(new NBTTagCompound()));
        return stored;
    }

    private static List<MachineResourceStack> readStacks(NBTTagList stored) {
        if (stored.tagCount() > MAX_STACKS_PER_STEP)
            throw new IllegalArgumentException("Too many Provider manifest stacks");
        List<MachineResourceStack> values = new ArrayList<>();
        for (int index = 0; index < stored.tagCount(); index++) {
            MachineResourceStack value = MachineResourceStack.read(stored.getCompoundTagAt(index));
            if (value == null) throw new IllegalArgumentException("Invalid Provider manifest stack");
            values.add(value);
        }
        return values;
    }

    private static String text(String value, int maximum, String name) {
        Objects.requireNonNull(value, name);
        String checked = value.trim();
        if (checked.isEmpty() || checked.length() > maximum)
            throw new IllegalArgumentException(name + " has invalid length");
        return checked;
    }

    private static String optionalText(String value, int maximum, String name) {
        Objects.requireNonNull(value, name);
        String checked = value.trim();
        if (checked.length() > maximum) throw new IllegalArgumentException(name + " has invalid length");
        return checked;
    }
}

