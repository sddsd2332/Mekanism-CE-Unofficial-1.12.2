package mekanism.qioprocessing.common.content.buffer;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.content.plan.QIOCandidateInputGroup;
import mekanism.qioprocessing.common.content.plan.QIOCandidateOption;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Arrays;
import java.util.List;

/** Persistent resources physically owned by one QIO processing job. */
public final class QIOJobBuffer {

    public enum Compartment {
        RESERVED,
        CYCLE_SEED,
        CYCLE_INTERNAL,
        IN_PROCESS_RETURN,
        PRODUCED,
        SETTLED_OUTPUT,
        RETURNING
    }

    private static final int MAX_RESOURCE_ENTRIES_PER_COMPARTMENT = 65_536;

    private final UUID jobId;
    private final Map<Compartment, Map<PortableResourceDescriptor, Long>> resources =
          new EnumMap<>(Compartment.class);

    public QIOJobBuffer(@Nonnull UUID jobId) {
        this.jobId = Objects.requireNonNull(jobId, "jobId");
    }

    @Nonnull
    public UUID getJobId() {
        return jobId;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> get(@Nonnull Compartment compartment) {
        Map<PortableResourceDescriptor, Long> amounts = resources.get(
              Objects.requireNonNull(compartment, "compartment"));
        return amounts == null ? Collections.emptyMap() :
              Collections.unmodifiableMap(new LinkedHashMap<>(amounts));
    }

    public long get(@Nonnull Compartment compartment, @Nonnull PortableResourceDescriptor resource) {
        Map<PortableResourceDescriptor, Long> amounts = resources.get(
              Objects.requireNonNull(compartment, "compartment"));
        return amounts == null ? 0 : amounts.getOrDefault(
              Objects.requireNonNull(resource, "resource"), 0L);
    }

    public void add(@Nonnull Compartment compartment, @Nonnull PortableResourceDescriptor resource,
          long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Buffer insertion amount must be positive");
        }
        Map<PortableResourceDescriptor, Long> amounts = resources.computeIfAbsent(
              Objects.requireNonNull(compartment, "compartment"), ignored -> new LinkedHashMap<>());
        PortableResourceDescriptor key = Objects.requireNonNull(resource, "resource");
        amounts.put(key, Math.addExact(amounts.getOrDefault(key, 0L), amount));
    }

    public void remove(@Nonnull Compartment compartment, @Nonnull PortableResourceDescriptor resource,
          long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Buffer removal amount must be positive");
        }
        Map<PortableResourceDescriptor, Long> amounts = resources.get(
              Objects.requireNonNull(compartment, "compartment"));
        PortableResourceDescriptor key = Objects.requireNonNull(resource, "resource");
        long stored = amounts == null ? 0 : amounts.getOrDefault(key, 0L);
        if (stored < amount) {
            throw new IllegalStateException("Cannot remove more resources than the job buffer owns");
        }
        if (stored == amount) {
            amounts.remove(key);
            if (amounts.isEmpty()) {
                resources.remove(compartment);
            }
        } else {
            amounts.put(key, stored - amount);
        }
    }

    public boolean isEmpty() {
        return resources.isEmpty();
    }

    /** Moves operation inputs into the durable in-flight compartment without losing ownership. */
    public void stageMachineInput(@Nonnull Map<PortableResourceDescriptor, Long> requested) {
        Map<PortableResourceDescriptor, Long> checked = QIOProcessingNbt.copyAmounts(requested,
              false, "machine input");
        for (Map.Entry<PortableResourceDescriptor, Long> entry : checked.entrySet()) {
            long available = Math.addExact(get(Compartment.PRODUCED, entry.getKey()),
                  get(Compartment.RESERVED, entry.getKey()));
            if (available < entry.getValue()) {
                throw new IllegalStateException("QIO job does not own all inputs required by its step");
            }
        }
        for (Map.Entry<PortableResourceDescriptor, Long> entry : checked.entrySet()) {
            long remaining = entry.getValue();
            long produced = Math.min(remaining, get(Compartment.PRODUCED, entry.getKey()));
            if (produced > 0) {
                remove(Compartment.PRODUCED, entry.getKey(), produced);
                remaining -= produced;
            }
            if (remaining > 0) {
                remove(Compartment.RESERVED, entry.getKey(), remaining);
            }
            add(Compartment.IN_PROCESS_RETURN, entry.getKey(), entry.getValue());
        }
    }

    public void commitMachineInput(@Nonnull Map<PortableResourceDescriptor, Long> resources) {
        Map<PortableResourceDescriptor, Long> checked = QIOProcessingNbt.copyAmounts(resources,
              false, "committed machine input");
        checked.forEach((resource, amount) -> remove(Compartment.IN_PROCESS_RETURN, resource,
              amount));
    }

    public void restoreMachineInput(@Nonnull Map<PortableResourceDescriptor, Long> resources) {
        Map<PortableResourceDescriptor, Long> checked = QIOProcessingNbt.copyAmounts(resources,
              false, "restored machine input");
        checked.forEach((resource, amount) -> {
            remove(Compartment.IN_PROCESS_RETURN, resource, amount);
            add(Compartment.PRODUCED, resource, amount);
        });
    }

    public boolean contains(@Nonnull Compartment compartment,
          @Nonnull Map<PortableResourceDescriptor, Long> requested) {
        for (Map.Entry<PortableResourceDescriptor, Long> entry : requested.entrySet()) {
            if (get(compartment, entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public boolean canSupply(@Nonnull Map<PortableResourceDescriptor, Long> requested) {
        for (Map.Entry<PortableResourceDescriptor, Long> entry : requested.entrySet()) {
            long available = Math.addExact(get(Compartment.PRODUCED, entry.getKey()),
                  get(Compartment.RESERVED, entry.getKey()));
            if (available < entry.getValue()) return false;
        }
        return true;
    }

    public boolean canSupply(@Nonnull Map<PortableResourceDescriptor, Long> fixedInputs,
          @Nonnull List<QIOCandidateInputGroup> candidateInputs) {
        Map<PortableResourceDescriptor, Long> available = new LinkedHashMap<>(
              getConsumableAmounts());
        for (Map.Entry<PortableResourceDescriptor, Long> fixed : fixedInputs.entrySet()) {
            if (!consumeAvailable(available, fixed.getKey(), fixed.getValue())) return false;
        }
        for (QIOCandidateInputGroup group : candidateInputs) {
            long remainingUnits = group.getUnitsPerOperation();
            for (QIOCandidateOption option : group.getOptions()) {
                long amountPerUnit = option.getAmountPerUnit();
                long units = Math.min(remainingUnits,
                      available.getOrDefault(option.getResource(), 0L) / amountPerUnit);
                if (units > 0) {
                    consumeAvailable(available, option.getResource(),
                          Math.multiplyExact(units, amountPerUnit));
                    remainingUnits -= units;
                }
                if (remainingUnits == 0) break;
            }
            if (remainingUnits != 0) return false;
        }
        return true;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getConsumableAmounts() {
        Map<PortableResourceDescriptor, Long> available = new LinkedHashMap<>();
        for (PortableResourceDescriptor resource : get(Compartment.PRODUCED).keySet()) {
            available.put(resource, get(Compartment.PRODUCED, resource));
        }
        for (Map.Entry<PortableResourceDescriptor, Long> reserved :
              get(Compartment.RESERVED).entrySet()) {
            available.merge(reserved.getKey(), reserved.getValue(), Math::addExact);
        }
        return Collections.unmodifiableMap(available);
    }

    private static boolean consumeAvailable(Map<PortableResourceDescriptor, Long> available,
          PortableResourceDescriptor resource, long amount) {
        long stored = available.getOrDefault(resource, 0L);
        if (stored < amount) return false;
        if (stored == amount) available.remove(resource);
        else available.put(resource, stored - amount);
        return true;
    }

    /** Separates completed root output from resources that may still feed unfinished steps. */
    public void settleProducedOutput(@Nonnull PortableResourceDescriptor resource, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Settled output amount must be positive");
        }
        PortableResourceDescriptor checked = Objects.requireNonNull(resource, "resource");
        remove(Compartment.PRODUCED, checked, amount);
        add(Compartment.SETTLED_OUTPUT, checked, amount);
    }

    /** Stages only output already proven unnecessary for the remaining recipe graph. */
    public void stageSettledOutputReturn(@Nonnull PortableResourceDescriptor resource, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Settled output return amount must be positive");
        }
        PortableResourceDescriptor checked = Objects.requireNonNull(resource, "resource");
        remove(Compartment.SETTLED_OUTPUT, checked, amount);
        add(Compartment.RETURNING, checked, amount);
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getReturnableResources() {
        Map<PortableResourceDescriptor, Long> result = new LinkedHashMap<>();
        for (Map.Entry<Compartment, Map<PortableResourceDescriptor, Long>> compartment :
              resources.entrySet()) {
            if (compartment.getKey() == Compartment.IN_PROCESS_RETURN ||
                  compartment.getKey() == Compartment.RETURNING) {
                continue;
            }
            for (Map.Entry<PortableResourceDescriptor, Long> entry :
                  compartment.getValue().entrySet()) {
                result.merge(entry.getKey(), entry.getValue(), Math::addExact);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    public void stageReturn(@Nonnull PortableResourceDescriptor resource, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("QIO return amount must be positive");
        }
        long available = 0;
        for (Compartment compartment : Compartment.values()) {
            if (compartment != Compartment.IN_PROCESS_RETURN &&
                  compartment != Compartment.RETURNING) {
                available = Math.addExact(available, get(compartment, resource));
            }
        }
        if (available < amount) {
            throw new IllegalStateException("QIO job cannot return resources it does not own");
        }
        long remaining = amount;
        for (Compartment compartment : Compartment.values()) {
            if (remaining == 0) {
                break;
            }
            if (compartment == Compartment.IN_PROCESS_RETURN ||
                  compartment == Compartment.RETURNING) {
                continue;
            }
            long moved = Math.min(remaining, get(compartment, resource));
            if (moved > 0) {
                remove(compartment, resource, moved);
                remaining -= moved;
            }
        }
        add(Compartment.RETURNING, resource, amount);
    }

    public void commitReturn(@Nonnull PortableResourceDescriptor resource, long amount) {
        remove(Compartment.RETURNING, resource, amount);
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        QIOProcessingNbt.writeUUID(data, "jobId", jobId);
        NBTTagList compartments = new NBTTagList();
        for (Compartment compartment : Compartment.values()) {
            Map<PortableResourceDescriptor, Long> amounts = resources.get(compartment);
            if (amounts != null && !amounts.isEmpty()) {
                NBTTagCompound stored = new NBTTagCompound();
                stored.setString("compartment", compartment.name());
                stored.setTag("amounts", QIOProcessingNbt.writeAmounts(amounts));
                compartments.appendTag(stored);
            }
        }
        data.setTag("compartments", compartments);
        return data;
    }

    @Nonnull
    public static QIOJobBuffer read(@Nonnull NBTTagCompound data) throws QIOProcessingDataException {
        QIOJobBuffer buffer = new QIOJobBuffer(QIOProcessingNbt.readUUID(data, "jobId"));
        if (!data.hasKey("compartments", NBT.TAG_LIST)) {
            throw new QIOProcessingDataException(
                  "Job buffer compartments are missing from current-schema data");
        }
        NBTTagList compartments = data.getTagList("compartments", NBT.TAG_COMPOUND);
        if (compartments.tagCount() > Compartment.values().length) {
            throw new QIOProcessingDataException("Job buffer contains too many compartments");
        }
        for (int i = 0; i < compartments.tagCount(); i++) {
            NBTTagCompound stored = compartments.getCompoundTagAt(i);
            Compartment compartment = QIOProcessingNbt.readEnum(stored, "compartment", Compartment.class);
            if (buffer.resources.containsKey(compartment)) {
                throw new QIOProcessingDataException("Duplicate job buffer compartment " + compartment);
            }
            Map<PortableResourceDescriptor, Long> amounts = QIOProcessingNbt.readAmounts(stored,
                  "amounts", MAX_RESOURCE_ENTRIES_PER_COMPARTMENT);
            if (amounts.isEmpty()) {
                throw new QIOProcessingDataException("Persisted job buffer compartment is empty");
            }
            buffer.resources.put(compartment, new LinkedHashMap<>(amounts));
        }
        return buffer;
    }
}
