package mekanism.stress;

import me.lucko.spark.proto.SparkProtos.SamplerData;
import me.lucko.spark.proto.SparkProtos.StackTraceNode;
import me.lucko.spark.proto.SparkProtos.ThreadNode;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Reads local Spark 1.12 profiles with Spark's own protobuf parser. */
public final class SparkProfileSummary {
    private SparkProfileSummary() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected one .sparkprofile path");
        SamplerData data = SamplerData.parseFrom(Files.readAllBytes(Paths.get(args[0])));
        System.out.printf(Locale.ROOT, "PROFILE startEpochMs=%d intervalMicros=%d%n",
              data.getMetadata().getStartTime(), data.getMetadata().getInterval());
        Map<String, Group> groups = new LinkedHashMap<>();
        for (ThreadNode thread : data.getThreadsList()) {
            if (!thread.getName().contains("Server thread") && !thread.getName().contains("MEK-")) continue;
            Map<String, Double> inclusive = new HashMap<>();
            Map<String, Double> self = new HashMap<>();
            visit(thread.getChildrenList(), new HashSet<>(), inclusive, self);
            System.out.printf(Locale.ROOT, "THREAD %s sampledTime=%.3f%n", thread.getName(), thread.getTime());
            print("INCLUSIVE", inclusive, thread.getTime(), true);
            print("SELF", self, thread.getTime(), false);
            String name = thread.getName().contains("Server thread") ? "server" :
                  thread.getName().contains("ForkJoinPool") ? "fork-join" :
                  thread.getName().contains("TaskSubmitter") ? "submitter" : "legacy-pool";
            Group group = groups.computeIfAbsent(name, ignored -> new Group());
            group.time += thread.getTime();
            group.threads++;
            inclusive.forEach((method, time) -> group.inclusive.merge(method, time, Double::sum));
            self.forEach((method, time) -> group.self.merge(method, time, Double::sum));
        }
        for (Map.Entry<String, Group> entry : groups.entrySet()) {
            Group group = entry.getValue();
            System.out.printf(Locale.ROOT, "GROUP %s profileThreadNodes=%d sampledMillis=%.3f%n", entry.getKey(), group.threads, group.time);
            for (String method : new String[]{
                  "mekanism.common.recipe.RecipeHandler.getRecipe",
                  "mekanism.common.recipe.cache.ICachedRecipeHolder.getUpdatedCache",
                  "mekanism.common.recipe.cache.RecipeCacheLookupMonitor.updateAndProcess",
                  "mekanism.common.recipe.cache.CachedRecipe.process",
                  "mekanism.common.concurrent.TaskExecutor.execute",
                  "mekanism.common.concurrent.TaskExecutor.executeActions",
                  "mekanism.common.concurrent.TaskExecutor.addTask",
                  "mekanism.qioprocessing.common.machine.QIOAutomationDeviceRegistry.onServerTick",
                  "mekanism.common.recipe.cache.MachineStressFixtures.prepareTick",
                  "mekanism.common.recipe.cache.MachineStressFixtures.verifyTick",
                  "mekanism.stress.timing.RecipeTiming.start",
                  "mekanism.stress.timing.RecipeTiming.finish"}) {
                double inclusive = group.inclusive.getOrDefault(method, 0D);
                double self = group.self.getOrDefault(method, 0D);
                System.out.printf(Locale.ROOT, "METRIC %s %.6f %.3f %.6f %.3f %s%n", entry.getKey(),
                      group.time == 0 ? 0 : inclusive * 100 / group.time, inclusive,
                      group.time == 0 ? 0 : self * 100 / group.time, self, method);
            }
            print("GROUP_INCLUSIVE", group.inclusive, group.time, true);
            print("GROUP_SELF", group.self, group.time, false);
        }
    }

    private static final class Group {
        int threads;
        double time;
        final Map<String, Double> inclusive = new HashMap<>();
        final Map<String, Double> self = new HashMap<>();
    }

    private static void visit(List<StackTraceNode> nodes, Set<String> path,
          Map<String, Double> inclusive, Map<String, Double> self) {
        for (StackTraceNode node : nodes) {
            String method = node.getClassName() + '.' + node.getMethodName();
            boolean first = path.add(method);
            if (first) inclusive.merge(method, node.getTime(), Double::sum);
            double childrenTime = node.getChildrenList().stream().mapToDouble(StackTraceNode::getTime).sum();
            self.merge(method, Math.max(0, node.getTime() - childrenTime), Double::sum);
            visit(node.getChildrenList(), path, inclusive, self);
            if (first) path.remove(method);
        }
    }

    private static void print(String kind, Map<String, Double> values, double total, boolean mekanismOnly) {
        values.entrySet().stream().filter(entry -> !mekanismOnly || entry.getKey().startsWith("mekanism."))
              .sorted(Map.Entry.<String, Double>comparingByValue().reversed()).limit(20)
              .forEach(entry -> System.out.printf(Locale.ROOT, "%s %7.2f%% %s%n", kind,
                    total > 0 ? entry.getValue() * 100 / total : 0, entry.getKey()));
    }
}
