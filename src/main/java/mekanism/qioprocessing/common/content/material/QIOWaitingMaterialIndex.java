package mekanism.qioprocessing.common.content.material;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Persistent reverse index used to wake only jobs affected by a resource change. */
/**
 * QIO 处理模块中的 QIOWaitingMaterialIndex 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOWaitingMaterialIndex {

    private static final int MAX_RESOURCES = 65_536;
    private static final int MAX_JOBS_PER_RESOURCE = 65_536;

    private final Map<PortableResourceDescriptor, Set<UUID>> jobsByResource = new LinkedHashMap<>();

    public void update(UUID jobId, Collection<PortableResourceDescriptor> previousMissing,
          Collection<PortableResourceDescriptor> currentMissing) {
        Objects.requireNonNull(jobId, "jobId");
        for (PortableResourceDescriptor resource : previousMissing) {
            Set<UUID> jobs = jobsByResource.get(resource);
            if (jobs != null) {
                jobs.remove(jobId);
                if (jobs.isEmpty()) {
                    jobsByResource.remove(resource);
                }
            }
        }
        for (PortableResourceDescriptor resource : currentMissing) {
            jobsByResource.computeIfAbsent(resource, ignored -> new LinkedHashSet<>()).add(jobId);
        }
    }

    @Nonnull
    public Set<UUID> getWaitingJobs(@Nonnull PortableResourceDescriptor resource) {
        Set<UUID> jobs = jobsByResource.get(Objects.requireNonNull(resource, "resource"));
        return jobs == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(jobs));
    }

    public boolean isEmpty() {
        return jobsByResource.isEmpty();
    }

    @Nonnull
    public NBTTagList write() {
        NBTTagList list = new NBTTagList();
        jobsByResource.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            NBTTagCompound data = new NBTTagCompound();
            data.setTag("resource", entry.getKey().write());
            NBTTagList jobs = new NBTTagList();
            entry.getValue().stream().map(UUID::toString).sorted().forEach(jobId -> {
                NBTTagCompound job = new NBTTagCompound();
                job.setString("jobId", jobId);
                jobs.appendTag(job);
            });
            data.setTag("jobs", jobs);
            list.appendTag(data);
        });
        return list;
    }

    @Nonnull
    public static QIOWaitingMaterialIndex read(@Nonnull NBTTagList list)
          throws QIOProcessingDataException {
        if (list.tagCount() > MAX_RESOURCES) {
            throw new QIOProcessingDataException("Waiting material index exceeds its resource limit");
        }
        QIOWaitingMaterialIndex index = new QIOWaitingMaterialIndex();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound data = list.getCompoundTagAt(i);
            PortableResourceDescriptor resource;
            try {
                resource = PortableResourceDescriptor.read(data.getCompoundTag("resource"));
            } catch (RuntimeException e) {
                throw new QIOProcessingDataException("Invalid waiting material resource at index " + i, e);
            }
            if (index.jobsByResource.containsKey(resource)) {
                throw new QIOProcessingDataException("Duplicate waiting material resource " + resource);
            }
            NBTTagList jobs = data.getTagList("jobs", NBT.TAG_COMPOUND);
            if (jobs.tagCount() <= 0 || jobs.tagCount() > MAX_JOBS_PER_RESOURCE) {
                throw new QIOProcessingDataException("Invalid waiting job count for " + resource);
            }
            Set<UUID> jobIds = new LinkedHashSet<>();
            for (int jobIndex = 0; jobIndex < jobs.tagCount(); jobIndex++) {
                UUID jobId = QIOProcessingNbt.readUUID(jobs.getCompoundTagAt(jobIndex), "jobId");
                if (!jobIds.add(jobId)) {
                    throw new QIOProcessingDataException("Duplicate waiting job " + jobId + " for " + resource);
                }
            }
            index.jobsByResource.put(resource, jobIds);
        }
        return index;
    }

    @Nonnull
    public static QIOWaitingMaterialIndex rebuild(Collection<QIOMaterialCommitment> commitments) {
        QIOWaitingMaterialIndex index = new QIOWaitingMaterialIndex();
        List<QIOMaterialCommitment> ordered = new ArrayList<>(commitments);
        ordered.sort((left, right) -> left.getJobId().toString().compareTo(right.getJobId().toString()));
        for (QIOMaterialCommitment commitment : ordered) {
            if (commitment.getState() == QIOMaterialCommitment.State.ACTIVE) {
                index.update(commitment.getJobId(), Collections.emptySet(),
                      commitment.getMissingResourceKeys());
            }
        }
        return index;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof QIOWaitingMaterialIndex other && jobsByResource.equals(other.jobsByResource);
    }

    @Override
    public int hashCode() {
        return jobsByResource.hashCode();
    }
}
