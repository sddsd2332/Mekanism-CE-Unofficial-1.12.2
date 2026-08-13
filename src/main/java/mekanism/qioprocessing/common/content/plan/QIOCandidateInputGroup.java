package mekanism.qioprocessing.common.content.plan;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.util.QIOHashing;
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

/** Candidate group retained by one workbench plan step until exact dispatch binding. */
public final class QIOCandidateInputGroup {

    private static final int SCHEMA_VERSION = 1;
    private final String groupId;
    private final List<Integer> slots;
    private final List<QIOCandidateOption> options;
    private final Map<PortableResourceDescriptor, Long> plannedInputs;

    public QIOCandidateInputGroup(@Nonnull List<Integer> slots,
          @Nonnull List<QIOCandidateOption> options,
          @Nonnull Map<PortableResourceDescriptor, Long> plannedInputs) {
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(options, "options");
        if (slots.isEmpty() || slots.size() > 9 || options.isEmpty() || options.size() > 64) {
            throw new IllegalArgumentException("Invalid QIO candidate input group size");
        }
        Set<Integer> uniqueSlots = new LinkedHashSet<>();
        for (Integer slot : slots) {
            int checked = Objects.requireNonNull(slot, "slot");
            if (checked < 0 || checked >= 9 || !uniqueSlots.add(checked)) {
                throw new IllegalArgumentException("Invalid QIO candidate input slot");
            }
        }
        List<Integer> orderedSlots = new ArrayList<>(uniqueSlots);
        Collections.sort(orderedSlots);
        this.slots = Collections.unmodifiableList(orderedSlots);
        List<QIOCandidateOption> checkedOptions = new ArrayList<>(options.size());
        Set<String> candidateIds = new LinkedHashSet<>();
        for (QIOCandidateOption option : options) {
            QIOCandidateOption checked = Objects.requireNonNull(option, "candidate option");
            if (!candidateIds.add(checked.getCandidateId())) {
                throw new IllegalArgumentException("Duplicate QIO candidate option");
            }
            checkedOptions.add(checked);
        }
        this.options = Collections.unmodifiableList(checkedOptions);
        this.plannedInputs = QIOProcessingNbt.copyAmounts(plannedInputs, false,
              "candidate planned inputs");
        long plannedUnits = 0;
        for (Map.Entry<PortableResourceDescriptor, Long> planned :
              this.plannedInputs.entrySet()) {
            QIOCandidateOption matching = checkedOptions.stream().filter(option ->
                  option.getResource().equals(planned.getKey()) &&
                  planned.getValue() % option.getAmountPerUnit() == 0).findFirst()
                  .orElseThrow(() -> new IllegalArgumentException(
                        "Planned QIO candidate input is not represented by its options"));
            plannedUnits = Math.addExact(plannedUnits,
                  planned.getValue() / matching.getAmountPerUnit());
        }
        if (plannedUnits != this.slots.size()) {
            throw new IllegalArgumentException(
                  "Planned QIO candidate inputs do not cover their slots exactly");
        }
        groupId = calculateId();
    }

    @Nonnull public String getGroupId() { return groupId; }
    @Nonnull public List<Integer> getSlots() { return slots; }
    @Nonnull public List<QIOCandidateOption> getOptions() { return options; }
    @Nonnull public Map<PortableResourceDescriptor, Long> getPlannedInputs() {
        return plannedInputs;
    }
    public long getUnitsPerOperation() { return slots.size(); }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("schema", SCHEMA_VERSION);
        data.setString("groupId", groupId);
        NBTTagList storedSlots = new NBTTagList();
        for (int slot : slots) {
            NBTTagCompound stored = new NBTTagCompound();
            stored.setInteger("slot", slot);
            storedSlots.appendTag(stored);
        }
        data.setTag("slots", storedSlots);
        NBTTagList storedOptions = new NBTTagList();
        options.forEach(option -> storedOptions.appendTag(option.write()));
        data.setTag("options", storedOptions);
        data.setTag("plannedInputs", QIOProcessingNbt.writeAmounts(plannedInputs));
        return data;
    }

    @Nonnull
    public static QIOCandidateInputGroup read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        if (data.getInteger("schema") != SCHEMA_VERSION ||
            !data.hasKey("groupId", NBT.TAG_STRING) ||
            !data.hasKey("slots", NBT.TAG_LIST) ||
            !data.hasKey("options", NBT.TAG_LIST) ||
            !data.hasKey("plannedInputs", NBT.TAG_LIST)) {
            throw new QIOProcessingDataException("Incomplete QIO candidate input group");
        }
        try {
            NBTTagList storedSlots = data.getTagList("slots", NBT.TAG_COMPOUND);
            NBTTagList storedOptions = data.getTagList("options", NBT.TAG_COMPOUND);
            if (storedSlots.tagCount() <= 0 || storedSlots.tagCount() > 9 ||
                storedOptions.tagCount() <= 0 || storedOptions.tagCount() > 64) {
                throw new QIOProcessingDataException("Invalid QIO candidate input group size");
            }
            List<Integer> slots = new ArrayList<>(storedSlots.tagCount());
            for (int index = 0; index < storedSlots.tagCount(); index++) {
                slots.add(storedSlots.getCompoundTagAt(index).getInteger("slot"));
            }
            List<QIOCandidateOption> options = new ArrayList<>(storedOptions.tagCount());
            for (int index = 0; index < storedOptions.tagCount(); index++) {
                options.add(QIOCandidateOption.read(storedOptions.getCompoundTagAt(index)));
            }
            QIOCandidateInputGroup group = new QIOCandidateInputGroup(slots, options,
                  QIOProcessingNbt.readAmounts(data, "plannedInputs", 64));
            if (!group.groupId.equals(data.getString("groupId"))) {
                throw new QIOProcessingDataException(
                      "QIO candidate input group identity does not match its contents");
            }
            return group;
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO candidate input group", e);
        }
    }

    private String calculateId() {
        StringBuilder canonical = new StringBuilder();
        slots.forEach(slot -> canonical.append("slot=").append(slot).append(';'));
        options.forEach(option -> canonical.append("option=")
              .append(option.getCandidateId()).append('@').append(option.getResource())
              .append('@').append(option.getAmountPerUnit()).append('@')
              .append(option.isVirtualFluid()).append(';'));
        return QIOHashing.sha256(canonical);
    }
}
