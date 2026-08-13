package mekanism.qioprocessing.common.content.plan;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** External logical ingredient units that may be claimed from any enabled candidate. */
public final class QIOCandidateRequirement {

    private final String requirementId;
    private final long requiredUnits;
    private final List<QIOCandidateOption> options;

    public QIOCandidateRequirement(@Nonnull String requirementId, long requiredUnits,
          @Nonnull List<QIOCandidateOption> options) {
        String checkedId = Objects.requireNonNull(requirementId, "requirementId").trim();
        if (checkedId.isEmpty() || checkedId.length() > 160 || requiredUnits <= 0 ||
            options.isEmpty() || options.size() > 64) {
            throw new IllegalArgumentException("Invalid QIO candidate requirement");
        }
        this.requirementId = checkedId;
        this.requiredUnits = requiredUnits;
        List<QIOCandidateOption> checked = new ArrayList<>(options.size());
        Set<String> ids = new LinkedHashSet<>();
        for (QIOCandidateOption option : options) {
            QIOCandidateOption value = Objects.requireNonNull(option, "candidate option");
            if (!ids.add(value.getCandidateId())) {
                throw new IllegalArgumentException("Duplicate QIO candidate requirement option");
            }
            checked.add(value);
        }
        this.options = Collections.unmodifiableList(checked);
    }

    @Nonnull public String getRequirementId() { return requirementId; }
    public long getRequiredUnits() { return requiredUnits; }
    @Nonnull public List<QIOCandidateOption> getOptions() { return options; }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("requirementId", requirementId);
        data.setLong("requiredUnits", requiredUnits);
        NBTTagList stored = new NBTTagList();
        options.forEach(option -> stored.appendTag(option.write()));
        data.setTag("options", stored);
        return data;
    }

    @Nonnull
    public static QIOCandidateRequirement read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        if (!data.hasKey("requirementId", NBT.TAG_STRING) ||
            !data.hasKey("requiredUnits", NBT.TAG_LONG) ||
            !data.hasKey("options", NBT.TAG_LIST)) {
            throw new QIOProcessingDataException("Incomplete QIO candidate requirement");
        }
        NBTTagList stored = data.getTagList("options", NBT.TAG_COMPOUND);
        if (stored.tagCount() <= 0 || stored.tagCount() > 64) {
            throw new QIOProcessingDataException("Invalid QIO candidate requirement options");
        }
        List<QIOCandidateOption> options = new ArrayList<>(stored.tagCount());
        for (int index = 0; index < stored.tagCount(); index++) {
            options.add(QIOCandidateOption.read(stored.getCompoundTagAt(index)));
        }
        try {
            return new QIOCandidateRequirement(data.getString("requirementId"),
                  data.getLong("requiredUnits"), options);
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO candidate requirement", e);
        }
    }
}
