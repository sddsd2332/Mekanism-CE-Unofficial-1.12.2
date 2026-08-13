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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable condensed SCC node whose member routes execute under per-round quotas. */
public final class QIOCyclePlanNode {
    private static final int MAX_MEMBERS = 65_536;
    private static final int MAX_RESOURCES = 65_536;
    private final long nodeId;
    private final long totalRounds;
    private final Map<Long, Long> memberQuotas;
    private final List<Long> roundSchedule;
    private final Map<PortableResourceDescriptor, Long> seedRequirements;
    private final Map<PortableResourceDescriptor, Long> netPerRound;
    private final String matrixSignature;

    public QIOCyclePlanNode(long nodeId, long totalRounds,
          @Nonnull Map<Long, Long> memberQuotas, @Nonnull List<Long> roundSchedule,
          @Nonnull Map<PortableResourceDescriptor, Long> seedRequirements,
          @Nonnull Map<PortableResourceDescriptor, Long> netPerRound) {
        this.nodeId = QIOProcessingNbt.requireNonNegative(nodeId, "cycleNodeId");
        if (totalRounds <= 0) throw new IllegalArgumentException("Cycle rounds must be positive");
        this.totalRounds = totalRounds;
        if (memberQuotas.isEmpty() || memberQuotas.size() > MAX_MEMBERS ||
              roundSchedule.isEmpty() || roundSchedule.size() > MAX_MEMBERS) {
            throw new IllegalArgumentException("Cycle member quotas are empty or too large");
        }
        Map<Long, Long> quotas = new LinkedHashMap<>();
        memberQuotas.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            long member = QIOProcessingNbt.requireNonNegative(entry.getKey(), "cycleMemberId");
            long quota = Objects.requireNonNull(entry.getValue(), "cycle quota");
            if (quota <= 0 || quotas.put(member, quota) != null) throw new IllegalArgumentException("Invalid cycle member quota");
        });
        List<Long> schedule = new ArrayList<>(roundSchedule.size());
        for (Long member : roundSchedule) {
            long checked = QIOProcessingNbt.requireNonNegative(Objects.requireNonNull(member), "scheduleMemberId");
            if (!quotas.containsKey(checked)) throw new IllegalArgumentException("Cycle schedule references an unknown member");
            schedule.add(checked);
        }
        Map<Long, Long> occurrences = new LinkedHashMap<>();
        schedule.forEach(member -> occurrences.merge(member, 1L, Math::addExact));
        if (!occurrences.equals(quotas)) throw new IllegalArgumentException("Cycle schedule does not match member quotas");
        this.memberQuotas = Collections.unmodifiableMap(quotas);
        this.roundSchedule = Collections.unmodifiableList(schedule);
        this.seedRequirements = copy(seedRequirements, true, "cycle seeds");
        this.netPerRound = copy(netPerRound, false, "cycle net output");
        matrixSignature = signature();
    }

    public long getNodeId(){return nodeId;} public long getTotalRounds(){return totalRounds;}
    @Nonnull public Map<Long,Long> getMemberQuotas(){return memberQuotas;}
    @Nonnull public List<Long> getRoundSchedule(){return roundSchedule;}
    @Nonnull public Map<PortableResourceDescriptor,Long> getSeedRequirements(){return seedRequirements;}
    @Nonnull public Map<PortableResourceDescriptor,Long> getNetPerRound(){return netPerRound;}
    @Nonnull public String getMatrixSignature(){return matrixSignature;}
    public boolean containsMember(long node){return memberQuotas.containsKey(node);}

    @Nonnull public NBTTagCompound write(){NBTTagCompound d=new NBTTagCompound();d.setLong("nodeId",nodeId);d.setLong("totalRounds",totalRounds);NBTTagList q=new NBTTagList();for(Map.Entry<Long,Long> e:memberQuotas.entrySet()){NBTTagCompound x=new NBTTagCompound();x.setLong("member",e.getKey());x.setLong("quota",e.getValue());q.appendTag(x);}d.setTag("memberQuotas",q);NBTTagList s=new NBTTagList();for(long member:roundSchedule){NBTTagCompound x=new NBTTagCompound();x.setLong("member",member);s.appendTag(x);}d.setTag("roundSchedule",s);d.setTag("seedRequirements",QIOProcessingNbt.writeAmounts(seedRequirements));d.setTag("netPerRound",QIOProcessingNbt.writeAmounts(netPerRound));d.setString("matrixSignature",matrixSignature);return d;}
    @Nonnull public static QIOCyclePlanNode read(NBTTagCompound d)throws QIOProcessingDataException{try{NBTTagList q=d.getTagList("memberQuotas",NBT.TAG_COMPOUND);NBTTagList s=d.getTagList("roundSchedule",NBT.TAG_COMPOUND);if(q.tagCount()>MAX_MEMBERS||s.tagCount()>MAX_MEMBERS)throw new QIOProcessingDataException("Cycle node is too large");Map<Long,Long> quotas=new LinkedHashMap<>();for(int i=0;i<q.tagCount();i++){NBTTagCompound x=q.getCompoundTagAt(i);if(quotas.put(x.getLong("member"),x.getLong("quota"))!=null)throw new QIOProcessingDataException("Duplicate cycle member");}List<Long> schedule=new ArrayList<>();for(int i=0;i<s.tagCount();i++)schedule.add(s.getCompoundTagAt(i).getLong("member"));QIOCyclePlanNode node=new QIOCyclePlanNode(d.getLong("nodeId"),d.getLong("totalRounds"),quotas,schedule,QIOProcessingNbt.readAmounts(d,"seedRequirements",MAX_RESOURCES),QIOProcessingNbt.readAmounts(d,"netPerRound",MAX_RESOURCES));if(!node.matrixSignature.equals(d.getString("matrixSignature")))throw new QIOProcessingDataException("Cycle matrix signature changed");return node;}catch(QIOProcessingDataException e){throw e;}catch(RuntimeException e){throw new QIOProcessingDataException("Invalid cycle plan node",e);}}
    void appendStructuralSignature(StringBuilder b){b.append("|cycle=").append(nodeId).append("@rounds=").append(totalRounds).append("@matrix=").append(matrixSignature);}
    private String signature(){StringBuilder b=new StringBuilder();memberQuotas.forEach((m,q)->b.append("m=").append(m).append('@').append(q).append('|'));roundSchedule.forEach(m->b.append("s=").append(m).append('|'));seedRequirements.forEach((r,a)->b.append("seed=").append(r).append('@').append(a).append('|'));netPerRound.forEach((r,a)->b.append("net=").append(r).append('@').append(a).append('|'));return QIOHashing.sha256(b);}
    private static Map<PortableResourceDescriptor,Long> copy(Map<PortableResourceDescriptor,Long> source,boolean allowEmpty,String name){if(source.size()>MAX_RESOURCES)throw new IllegalArgumentException(name+" too large");return QIOProcessingNbt.copyAmounts(source,allowEmpty,name);}
}
