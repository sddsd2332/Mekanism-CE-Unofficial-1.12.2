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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.Set;

/** Exact and substitutable external material requirements for one immutable plan revision. */
public final class QIOPlanMaterialRequirements {

    private final Map<PortableResourceDescriptor, Long> exactAmounts;
    private final List<QIOCandidateRequirement> candidateRequirements;

    public QIOPlanMaterialRequirements(
          @Nonnull Map<PortableResourceDescriptor, Long> exactAmounts,
          @Nonnull List<QIOCandidateRequirement> candidateRequirements) {
        this.exactAmounts = QIOProcessingNbt.copyAmounts(exactAmounts, true,
              "exact plan material requirements");
        Objects.requireNonNull(candidateRequirements, "candidateRequirements");
        if (candidateRequirements.size() > 65_536) {
            throw new IllegalArgumentException("Too many QIO candidate requirements");
        }
        List<QIOCandidateRequirement> checked = new ArrayList<>(
              candidateRequirements.size());
        Set<String> ids = new LinkedHashSet<>();
        for (QIOCandidateRequirement requirement : candidateRequirements) {
            QIOCandidateRequirement value = Objects.requireNonNull(requirement,
                  "candidate requirement");
            if (!ids.add(value.getRequirementId())) {
                throw new IllegalArgumentException("Duplicate QIO candidate requirement");
            }
            checked.add(value);
        }
        this.candidateRequirements = Collections.unmodifiableList(checked);
    }

    @Nonnull public Map<PortableResourceDescriptor, Long> getExactAmounts() {
        return exactAmounts;
    }
    @Nonnull public List<QIOCandidateRequirement> getCandidateRequirements() {
        return candidateRequirements;
    }
    public boolean isEmpty() { return exactAmounts.isEmpty() && candidateRequirements.isEmpty(); }

    /** Stable display/diagnostic projection using each group's highest-priority option. */
    @Nonnull
    public Map<PortableResourceDescriptor, Long> preferredProjection() {
        Map<PortableResourceDescriptor, Long> projection = new LinkedHashMap<>(exactAmounts);
        for (QIOCandidateRequirement requirement : candidateRequirements) {
            QIOCandidateOption preferred = requirement.getOptions().get(0);
            projection.merge(preferred.getResource(), Math.multiplyExact(
                  requirement.getRequiredUnits(), preferred.getAmountPerUnit()), Math::addExact);
        }
        return Collections.unmodifiableMap(projection);
    }

    @Nonnull
    public static QIOPlanMaterialRequirements derive(
          @Nonnull Map<PortableResourceDescriptor, Long> externalRequirements,
          @Nonnull List<QIOPlanStep> steps) {
        Map<PortableResourceDescriptor, Long> remaining = new LinkedHashMap<>(
              externalRequirements);
        List<QIOCandidateRequirement> groups = new ArrayList<>();
        for (QIOPlanStep step : steps) {
            if (step.getProviderKind() != QIOPlanStep.ProviderKind.WORKBENCH) continue;
            for (QIOCandidateInputGroup group : step.getCandidateInputs()) {
                Map<PortableResourceDescriptor, Long> planned = new LinkedHashMap<>();
                group.getPlannedInputs().forEach((resource, amount) -> planned.put(resource,
                      Math.multiplyExact(amount, step.getOperations())));
                long externalUnits = 0;
                for (QIOCandidateOption option : group.getOptions()) {
                    long plannedAmount = planned.getOrDefault(option.getResource(), 0L);
                    long availableExternal = remaining.getOrDefault(option.getResource(), 0L);
                    long units = Math.min(plannedAmount, availableExternal) /
                          option.getAmountPerUnit();
                    if (units <= 0) continue;
                    long consumed = Math.multiplyExact(units, option.getAmountPerUnit());
                    externalUnits = Math.addExact(externalUnits, units);
                    long left = availableExternal - consumed;
                    if (left == 0) remaining.remove(option.getResource());
                    else remaining.put(option.getResource(), left);
                }
                if (externalUnits > 0) {
                    groups.add(new QIOCandidateRequirement(step.getNodeId() + "/" +
                          group.getGroupId(), externalUnits, group.getOptions()));
                }
            }
        }
        return new QIOPlanMaterialRequirements(remaining, groups);
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setTag("exactAmounts", QIOProcessingNbt.writeAmounts(exactAmounts));
        NBTTagList groups = new NBTTagList();
        candidateRequirements.forEach(group -> groups.appendTag(group.write()));
        data.setTag("candidateRequirements", groups);
        return data;
    }

    @Nonnull
    public static QIOPlanMaterialRequirements read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        if (!data.hasKey("exactAmounts", NBT.TAG_LIST) ||
            !data.hasKey("candidateRequirements", NBT.TAG_LIST)) {
            throw new QIOProcessingDataException("Incomplete QIO plan material requirements");
        }
        NBTTagList stored = data.getTagList("candidateRequirements", NBT.TAG_COMPOUND);
        if (stored.tagCount() > 65_536) {
            throw new QIOProcessingDataException("Too many QIO candidate requirements");
        }
        List<QIOCandidateRequirement> groups = new ArrayList<>(stored.tagCount());
        for (int index = 0; index < stored.tagCount(); index++) {
            groups.add(QIOCandidateRequirement.read(stored.getCompoundTagAt(index)));
        }
        return new QIOPlanMaterialRequirements(QIOProcessingNbt.readAmounts(data,
              "exactAmounts", 65_536), groups);
    }
}
