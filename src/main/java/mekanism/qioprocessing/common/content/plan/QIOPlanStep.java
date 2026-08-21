package mekanism.qioprocessing.common.content.plan;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** One immutable, exact route node in a persisted QIO craft-plan DAG. */
/**
 * QIO 处理模块中的 QIOPlanStep 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOPlanStep {

    public static final int SCHEMA_VERSION = 4;
    private static final int MAX_RESOURCE_ENTRIES = 65_536;
    private static final int MAX_DEPENDENCIES = 65_536;
    private static final int MAX_ID_LENGTH = 512;

    public enum ProviderKind {
        WORKBENCH,
        MEKANISM
    }

    private final long nodeId;
    private final ProviderKind providerKind;
    private final String providerId;
    private final String routeId;
    private final String recipeKey;
    private final String variantId;
    private final String routeSignature;
    private final long operations;
    private final Map<PortableResourceDescriptor, Long> configurationInputs;
    private final Map<PortableResourceDescriptor, Long> exactInputs;
    private final Map<PortableResourceDescriptor, Long> guaranteedOutputs;
    private final Map<PortableResourceDescriptor, Long> optionalOutputs;
    private final List<QIOCandidateInputGroup> candidateInputs;
    private final Map<PortableResourceDescriptor, Long> fixedInputs;
    private final List<Long> dependencies;

    public QIOPlanStep(long nodeId, @Nonnull ProviderKind providerKind,
          @Nonnull String providerId, @Nonnull String routeId, @Nonnull String recipeKey,
          @Nonnull String variantId, @Nonnull String routeSignature, long operations,
          @Nonnull Map<PortableResourceDescriptor, Long> exactInputs,
          @Nonnull Map<PortableResourceDescriptor, Long> guaranteedOutputs,
          @Nonnull Map<PortableResourceDescriptor, Long> optionalOutputs,
          @Nonnull List<Long> dependencies) {
        this(nodeId, providerKind, providerId, routeId, recipeKey, variantId,
              routeSignature, operations, exactInputs, guaranteedOutputs, optionalOutputs,
              dependencies, Collections.emptyList(), Collections.emptyMap());
    }

    public QIOPlanStep(long nodeId, @Nonnull ProviderKind providerKind,
          @Nonnull String providerId, @Nonnull String routeId, @Nonnull String recipeKey,
          @Nonnull String variantId, @Nonnull String routeSignature, long operations,
          @Nonnull Map<PortableResourceDescriptor, Long> exactInputs,
          @Nonnull Map<PortableResourceDescriptor, Long> guaranteedOutputs,
          @Nonnull Map<PortableResourceDescriptor, Long> optionalOutputs,
          @Nonnull List<Long> dependencies,
          @Nonnull List<QIOCandidateInputGroup> candidateInputs) {
        this(nodeId, providerKind, providerId, routeId, recipeKey, variantId,
              routeSignature, operations, exactInputs, guaranteedOutputs, optionalOutputs,
              dependencies, candidateInputs, Collections.emptyMap());
    }

    public QIOPlanStep(long nodeId, @Nonnull ProviderKind providerKind,
          @Nonnull String providerId, @Nonnull String routeId, @Nonnull String recipeKey,
          @Nonnull String variantId, @Nonnull String routeSignature, long operations,
          @Nonnull Map<PortableResourceDescriptor, Long> exactInputs,
          @Nonnull Map<PortableResourceDescriptor, Long> guaranteedOutputs,
          @Nonnull Map<PortableResourceDescriptor, Long> optionalOutputs,
          @Nonnull List<Long> dependencies,
          @Nonnull List<QIOCandidateInputGroup> candidateInputs,
          @Nonnull Map<PortableResourceDescriptor, Long> configurationInputs) {
        this.nodeId = QIOProcessingNbt.requireNonNegative(nodeId, "nodeId");
        this.providerKind = Objects.requireNonNull(providerKind, "providerKind");
        this.providerId = requireId(providerId, "providerId");
        this.routeId = requireId(routeId, "routeId");
        this.recipeKey = requireId(recipeKey, "recipeKey");
        this.variantId = requireId(variantId, "variantId");
        this.routeSignature = requireId(routeSignature, "routeSignature");
        if (operations <= 0) {
            throw new IllegalArgumentException("QIO plan step operations must be positive");
        }
        this.operations = operations;
        this.configurationInputs = QIOProcessingNbt.copyAmounts(configurationInputs, true,
              "configurationInputs");
        this.exactInputs = QIOProcessingNbt.copyAmounts(exactInputs, false, "exactInputs");
        this.guaranteedOutputs = QIOProcessingNbt.copyAmounts(guaranteedOutputs, false,
              "guaranteedOutputs");
        this.optionalOutputs = QIOProcessingNbt.copyAmounts(optionalOutputs, true,
              "optionalOutputs");
        this.dependencies = checkedDependencies(dependencies, nodeId);
        Objects.requireNonNull(candidateInputs, "candidateInputs");
        if (candidateInputs.size() > 9 || providerKind != ProviderKind.WORKBENCH &&
            !candidateInputs.isEmpty() || providerKind == ProviderKind.WORKBENCH &&
            !this.configurationInputs.isEmpty()) {
            throw new IllegalArgumentException("Invalid QIO plan-step special inputs");
        }
        this.candidateInputs = Collections.unmodifiableList(new ArrayList<>(candidateInputs));
        fixedInputs = calculateFixedInputs(this.exactInputs, this.candidateInputs);
    }

    public long getNodeId() {
        return nodeId;
    }

    @Nonnull
    public ProviderKind getProviderKind() {
        return providerKind;
    }

    @Nonnull
    public String getProviderId() {
        return providerId;
    }

    @Nonnull
    public String getRouteId() {
        return routeId;
    }

    @Nonnull
    public String getRecipeKey() {
        return recipeKey;
    }

    @Nonnull
    public String getVariantId() {
        return variantId;
    }

    @Nonnull
    public String getRouteSignature() {
        return routeSignature;
    }

    @Nonnull
    public String getStableRouteId() {
        return providerId + '/' + routeId + '/' + recipeKey + '/' + variantId;
    }

    public long getOperations() {
        return operations;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getExactInputs() {
        return exactInputs;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getConfigurationInputs() {
        return configurationInputs;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getGuaranteedOutputs() {
        return guaranteedOutputs;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getOptionalOutputs() {
        return optionalOutputs;
    }

    @Nonnull
    public List<Long> getDependencies() {
        return dependencies;
    }

    @Nonnull
    public List<QIOCandidateInputGroup> getCandidateInputs() {
        return candidateInputs;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getFixedInputs() {
        return fixedInputs;
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("planStepSchemaVersion", SCHEMA_VERSION);
        data.setLong("nodeId", nodeId);
        data.setString("providerKind", providerKind.name());
        data.setString("providerId", providerId);
        data.setString("routeId", routeId);
        data.setString("recipeKey", recipeKey);
        data.setString("variantId", variantId);
        data.setString("routeSignature", routeSignature);
        data.setLong("operations", operations);
        data.setTag("configurationInputs", QIOProcessingNbt.writeAmounts(configurationInputs));
        data.setTag("exactInputs", QIOProcessingNbt.writeAmounts(exactInputs));
        data.setTag("guaranteedOutputs", QIOProcessingNbt.writeAmounts(guaranteedOutputs));
        data.setTag("optionalOutputs", QIOProcessingNbt.writeAmounts(optionalOutputs));
        NBTTagList storedCandidateInputs = new NBTTagList();
        candidateInputs.forEach(group -> storedCandidateInputs.appendTag(group.write()));
        data.setTag("candidateInputs", storedCandidateInputs);
        NBTTagList dependencyList = new NBTTagList();
        for (long dependency : dependencies) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setLong("nodeId", dependency);
            dependencyList.appendTag(entry);
        }
        data.setTag("dependencies", dependencyList);
        return data;
    }

    @Nonnull
    public static QIOPlanStep read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        int schema = data.getInteger("planStepSchemaVersion");
        if (schema != 3 && schema != SCHEMA_VERSION) {
            throw new QIOProcessingDataException("Unsupported QIO plan-step schema " +
                  data.getInteger("planStepSchemaVersion"));
        }
        try {
            if (!data.hasKey("nodeId", NBT.TAG_LONG) ||
                  !data.hasKey("providerKind", NBT.TAG_STRING) ||
                  !data.hasKey("providerId", NBT.TAG_STRING) ||
                  !data.hasKey("routeId", NBT.TAG_STRING) ||
                  !data.hasKey("recipeKey", NBT.TAG_STRING) ||
                  !data.hasKey("variantId", NBT.TAG_STRING) ||
                  !data.hasKey("routeSignature", NBT.TAG_STRING) ||
                  !data.hasKey("operations", NBT.TAG_LONG) ||
                  schema >= 4 && !data.hasKey("configurationInputs", NBT.TAG_LIST) ||
                  !data.hasKey("exactInputs", NBT.TAG_LIST) ||
                  !data.hasKey("guaranteedOutputs", NBT.TAG_LIST) ||
                  !data.hasKey("optionalOutputs", NBT.TAG_LIST) ||
                  !data.hasKey("candidateInputs", NBT.TAG_LIST) ||
                  !data.hasKey("dependencies", NBT.TAG_LIST)) {
                throw new QIOProcessingDataException(
                      "QIO plan step is missing required current-schema state");
            }
            NBTTagList dependencyList = data.getTagList("dependencies", NBT.TAG_COMPOUND);
            if (dependencyList.tagCount() > MAX_DEPENDENCIES) {
                throw new QIOProcessingDataException("QIO plan step has too many dependencies");
            }
            List<Long> dependencies = new ArrayList<>(dependencyList.tagCount());
            for (int i = 0; i < dependencyList.tagCount(); i++) {
                dependencies.add(dependencyList.getCompoundTagAt(i).getLong("nodeId"));
            }
            NBTTagList storedCandidateInputs = data.getTagList("candidateInputs",
                  NBT.TAG_COMPOUND);
            if (storedCandidateInputs.tagCount() > 9) {
                throw new QIOProcessingDataException(
                      "QIO plan step has too many candidate input groups");
            }
            List<QIOCandidateInputGroup> candidateInputs = new ArrayList<>(
                  storedCandidateInputs.tagCount());
            for (int index = 0; index < storedCandidateInputs.tagCount(); index++) {
                candidateInputs.add(QIOCandidateInputGroup.read(
                      storedCandidateInputs.getCompoundTagAt(index)));
            }
            return new QIOPlanStep(data.getLong("nodeId"),
                  QIOProcessingNbt.readEnum(data, "providerKind", ProviderKind.class),
                  data.getString("providerId"), data.getString("routeId"),
                  data.getString("recipeKey"), data.getString("variantId"),
                  data.getString("routeSignature"),
                  data.getLong("operations"),
                  QIOProcessingNbt.readAmounts(data, "exactInputs", MAX_RESOURCE_ENTRIES),
                  QIOProcessingNbt.readAmounts(data, "guaranteedOutputs", MAX_RESOURCE_ENTRIES),
                  QIOProcessingNbt.readAmounts(data, "optionalOutputs", MAX_RESOURCE_ENTRIES),
                  dependencies, candidateInputs, schema >= 4 ?
                        QIOProcessingNbt.readAmounts(data, "configurationInputs",
                              MAX_RESOURCE_ENTRIES) : Collections.emptyMap());
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO plan step", e);
        }
    }

    void appendStructuralSignature(StringBuilder canonical) {
        canonical.append("|node=").append(nodeId)
              .append('|').append(providerKind)
              .append('|').append(providerId)
              .append('|').append(routeId)
              .append('|').append(recipeKey)
              .append('|').append(variantId)
              .append('|').append(routeSignature)
              .append("|operations=").append(operations);
        appendAmounts(canonical, "input", exactInputs);
        if (!configurationInputs.isEmpty()) {
            appendAmounts(canonical, "configuration", configurationInputs);
        }
        appendAmounts(canonical, "output", guaranteedOutputs);
        appendAmounts(canonical, "optional", optionalOutputs);
        for (QIOCandidateInputGroup group : candidateInputs) {
            canonical.append("|candidateGroup=").append(group.getGroupId());
            appendAmounts(canonical, "candidatePlanned", group.getPlannedInputs());
        }
        for (long dependency : dependencies) {
            canonical.append("|dependency=").append(dependency);
        }
    }

    private static void appendAmounts(StringBuilder canonical, String prefix,
          Map<PortableResourceDescriptor, Long> amounts) {
        for (Map.Entry<PortableResourceDescriptor, Long> entry : amounts.entrySet()) {
            canonical.append('|').append(prefix).append('=').append(entry.getKey())
                  .append('@').append(entry.getValue());
        }
    }

    private static List<Long> checkedDependencies(List<Long> dependencies, long nodeId) {
        Objects.requireNonNull(dependencies, "dependencies");
        if (dependencies.size() > MAX_DEPENDENCIES) {
            throw new IllegalArgumentException("QIO plan step has too many dependencies");
        }
        Set<Long> unique = new LinkedHashSet<>();
        for (Long dependency : dependencies) {
            long checked = QIOProcessingNbt.requireNonNegative(
                  Objects.requireNonNull(dependency, "dependency"), "dependency");
            if (checked == nodeId) {
                throw new IllegalArgumentException("QIO plan step cannot depend on itself");
            }
            if (!unique.add(checked)) {
                throw new IllegalArgumentException("Duplicate QIO plan dependency " + checked);
            }
        }
        List<Long> sorted = new ArrayList<>(unique);
        Collections.sort(sorted);
        return Collections.unmodifiableList(sorted);
    }

    private static Map<PortableResourceDescriptor, Long> calculateFixedInputs(
          Map<PortableResourceDescriptor, Long> exactInputs,
          List<QIOCandidateInputGroup> candidateInputs) {
        Map<PortableResourceDescriptor, Long> fixed = new java.util.LinkedHashMap<>(exactInputs);
        for (QIOCandidateInputGroup group : candidateInputs) {
            for (Map.Entry<PortableResourceDescriptor, Long> planned :
                  group.getPlannedInputs().entrySet()) {
                long present = fixed.getOrDefault(planned.getKey(), 0L);
                if (present < planned.getValue()) {
                    throw new IllegalArgumentException(
                          "QIO candidate inputs exceed the step's exact inputs");
                }
                if (present == planned.getValue()) fixed.remove(planned.getKey());
                else fixed.put(planned.getKey(), present - planned.getValue());
            }
        }
        return Collections.unmodifiableMap(fixed);
    }

    private static String requireId(String value, String name) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException(name + " must contain 1.." + MAX_ID_LENGTH +
                  " characters");
        }
        return trimmed;
    }
}
