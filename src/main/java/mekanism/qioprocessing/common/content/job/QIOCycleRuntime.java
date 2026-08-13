package mekanism.qioprocessing.common.content.job;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.plan.QIOCyclePlanNode;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Durable round cursor and member quotas for one condensed cycle node. */
public final class QIOCycleRuntime {
    private final long cycleNodeId;
    private final long totalRounds;
    private long currentRound;
    private final Map<Long,Long> completedThisRound=new LinkedHashMap<>();
    public QIOCycleRuntime(QIOCyclePlanNode node){cycleNodeId=node.getNodeId();totalRounds=node.getTotalRounds();node.getMemberQuotas().keySet().forEach(id->completedThisRound.put(id,0L));}
    private QIOCycleRuntime(long id,long total,long current,Map<Long,Long> completed){cycleNodeId=id;totalRounds=total;currentRound=current;completedThisRound.putAll(completed);}
    public long getCycleNodeId(){return cycleNodeId;}public long getTotalRounds(){return totalRounds;}public long getCurrentRound(){return currentRound;}public boolean isComplete(){return currentRound==totalRounds;}
    @Nonnull public Map<Long,Long> getCompletedThisRound(){return Collections.unmodifiableMap(new LinkedHashMap<>(completedThisRound));}
    public long allowance(QIOCyclePlanNode node,long member,QIOStepRuntime runtime){if(isComplete())return 0;long cursor=completedThisRound.values().stream().mapToLong(Long::longValue).sum();List<Long> schedule=node.getRoundSchedule();if(cursor>=schedule.size()||schedule.get((int)cursor)!=member)return 0;long consecutive=0;while(cursor+consecutive<schedule.size()&&schedule.get((int)(cursor+consecutive))==member)consecutive++;return Math.max(0,consecutive-runtime.getActiveOperationCount());}
    public void complete(QIOCyclePlanNode node,long member,long amount){long cursor=completedThisRound.values().stream().mapToLong(Long::longValue).sum();if(amount<=0||cursor+amount>node.getRoundSchedule().size())throw new IllegalStateException("Cycle completion exceeds its round schedule");for(long offset=0;offset<amount;offset++)if(node.getRoundSchedule().get((int)(cursor+offset))!=member)throw new IllegalStateException("Cycle member completed out of schedule order");long next=Math.addExact(completedThisRound.getOrDefault(member,0L),amount);if(next>node.getMemberQuotas().getOrDefault(member,0L))throw new IllegalStateException("Cycle member exceeded its round quota");completedThisRound.put(member,next);if(cursor+amount==node.getRoundSchedule().size()){currentRound=Math.addExact(currentRound,1);completedThisRound.replaceAll((id,value)->0L);}}
    @Nonnull public NBTTagCompound write(){NBTTagCompound d=new NBTTagCompound();d.setLong("cycleNodeId",cycleNodeId);d.setLong("totalRounds",totalRounds);d.setLong("currentRound",currentRound);NBTTagList list=new NBTTagList();completedThisRound.forEach((id,value)->{NBTTagCompound x=new NBTTagCompound();x.setLong("member",id);x.setLong("completed",value);list.appendTag(x);});d.setTag("members",list);return d;}
    @Nonnull public static QIOCycleRuntime read(NBTTagCompound d)throws QIOProcessingDataException{try{Map<Long,Long> values=new LinkedHashMap<>();NBTTagList list=d.getTagList("members",NBT.TAG_COMPOUND);for(int i=0;i<list.tagCount();i++){NBTTagCompound x=list.getCompoundTagAt(i);if(values.put(x.getLong("member"),x.getLong("completed"))!=null)throw new QIOProcessingDataException("Duplicate cycle runtime member");}QIOCycleRuntime runtime=new QIOCycleRuntime(d.getLong("cycleNodeId"),d.getLong("totalRounds"),d.getLong("currentRound"),values);if(runtime.totalRounds<=0||runtime.currentRound<0||runtime.currentRound>runtime.totalRounds)throw new QIOProcessingDataException("Invalid cycle round cursor");return runtime;}catch(QIOProcessingDataException e){throw e;}catch(RuntimeException e){throw new QIOProcessingDataException("Invalid cycle runtime",e);}}
    public void validate(QIOCyclePlanNode node, Map<Long, QIOStepRuntime> stepRuntimes) {
        if (cycleNodeId != node.getNodeId() || totalRounds != node.getTotalRounds() ||
              !completedThisRound.keySet().equals(node.getMemberQuotas().keySet())) {
            throw new IllegalArgumentException("Cycle runtime does not match plan");
        }
        long cursor = 0;
        for (Map.Entry<Long, Long> entry : completedThisRound.entrySet()) {
            if (entry.getValue() < 0 ||
                  entry.getValue() > node.getMemberQuotas().get(entry.getKey())) {
                throw new IllegalArgumentException("Cycle runtime quota is invalid");
            }
            cursor = Math.addExact(cursor, entry.getValue());
        }
        if (currentRound == totalRounds ? cursor != 0 :
              cursor >= node.getRoundSchedule().size()) {
            throw new IllegalArgumentException("Cycle runtime round cursor is not canonical");
        }
        Map<Long, Long> expectedPrefix = new LinkedHashMap<>();
        node.getMemberQuotas().keySet().forEach(member -> expectedPrefix.put(member, 0L));
        for (int index = 0; index < cursor; index++) {
            expectedPrefix.merge(node.getRoundSchedule().get(index), 1L, Math::addExact);
        }
        if (!expectedPrefix.equals(completedThisRound)) {
            throw new IllegalArgumentException("Cycle runtime cursor is not a schedule prefix");
        }
        long scheduledMember = currentRound == totalRounds ? -1 :
              node.getRoundSchedule().get((int) cursor);
        long consecutiveAllowance = 0;
        while (cursor + consecutiveAllowance < node.getRoundSchedule().size() &&
              node.getRoundSchedule().get((int) (cursor + consecutiveAllowance)) ==
                    scheduledMember) {
            consecutiveAllowance++;
        }
        long activeScheduled = 0;
        for (Map.Entry<Long, Long> quota : node.getMemberQuotas().entrySet()) {
            QIOStepRuntime step = stepRuntimes.get(quota.getKey());
            if (step == null) {
                throw new IllegalArgumentException("Cycle runtime is missing a member step");
            }
            long expectedCompleted = Math.addExact(Math.multiplyExact(currentRound,
                  quota.getValue()), completedThisRound.get(quota.getKey()));
            if (step.getCompletedOperations() != expectedCompleted) {
                throw new IllegalArgumentException("Cycle member progress disagrees with its cursor");
            }
            long active = step.getActiveOperationCount();
            if (active > 0 && quota.getKey() != scheduledMember) {
                throw new IllegalArgumentException("Cycle has an active member outside its cursor");
            }
            if (quota.getKey() == scheduledMember) {
                activeScheduled = active;
            }
        }
        if (activeScheduled > consecutiveAllowance) {
            throw new IllegalArgumentException("Cycle active operations exceed its cursor allowance");
        }
    }
}
