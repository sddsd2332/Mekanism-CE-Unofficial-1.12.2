package mekanism.stress;

import com.google.gson.GsonBuilder;
import mekanism.common.Mekanism;
import mekanism.common.concurrent.TaskExecutor;
import mekanism.common.recipe.cache.MachineStressFixtures;
import mekanism.stress.timing.RecipeTiming;
import net.minecraft.command.ICommandSender;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/** Real registered machines, normal world ticks, no synthetic planner or tick skipping. */
@Mod(modid = "mekanism_machine_stress_smoke", name = "Mekanism Machine Stress Smoke", version = "2",
      dependencies = "required-after:mekanism;required-after:spark")
public final class MachineStressSmokeMod {
    static Path resultPath;
    static String runId;
    static volatile boolean finished;
    static volatile boolean clientReady;
    private static final long COMMAND_TIMEOUT_NANOS = 120_000_000_000L;
    private final List<Sample> samples = new ArrayList<>();
    private final TickProfiler profiler = new TickProfiler();
    private MachineStressFixtures fixtures;
    private MachineStressLifecycle qioLifecycle;
    private SparkSender spark;
    private State state = State.INSTALL;
    private String scenario;
    private int count;
    private int warmup;
    private int warmupLimit;
    private int sampleLimit;
    private long tickStarted;
    private long executorBefore;
    private long deadline;
    private String failure;
    private boolean client;
    private Double maxMachineNanos;

    private enum State { INSTALL, SPARK_START, QIO_LIFECYCLE, WARMUP, MEASURE, SPARK_STOP, DONE }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        scenario = System.getProperty("mekanism.machine.stress.scenario", "mixed");
        count = Integer.getInteger("mekanism.machine.stress.count", 1000);
        warmupLimit = Integer.getInteger("mekanism.machine.stress.warmup", 20);
        sampleLimit = Integer.getInteger("mekanism.machine.stress.samples", 20);
        String maxMs = System.getProperty("mekanism.machine.stress.maxMs", "").trim();
        if (!maxMs.isEmpty()) {
            double limit = Double.parseDouble(maxMs) * 1_000_000D;
            MachineStressFixtures.check(Double.isFinite(limit) && limit > 0, "Performance threshold must be positive and finite");
            maxMachineNanos = limit;
        }
        client = Boolean.getBoolean("mekanism.machine.stress.client");
        runId = System.getProperty("mekanism.machine.stress.runId", Long.toString(System.currentTimeMillis()));
        resultPath = Paths.get(System.getProperty("mekanism.machine.stress.result"));
        MachineStressFixtures.check(count > 0 && count <= 196608 && sampleLimit > 0 && warmupLimit >= 0,
              "Invalid stress count, warmup or sample count");
    }

    @Mod.EventHandler
    @SideOnly(Side.CLIENT)
    public void clientLoaded(FMLLoadCompleteEvent event) {
        MinecraftForge.EVENT_BUS.register(new MachineStressClient());
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void startTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || finished) return;
        if (state == State.MEASURE && fixtures != null && fixtures.cycleTarget > 0) RecipeTiming.resume();
        profiler.blockEntityNanos = 0;
        executorBefore = TaskExecutor.totalUsedTime;
        tickStarted = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void endTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        RecipeTiming.pause();
        long tickNanos = System.nanoTime() - tickStarted;
        long barrierNanos = (TaskExecutor.totalUsedTime - executorBefore) * 1000;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        try {
            WorldServer world = server.getWorld(0);
            if (client && !clientReady) return;
            if (fixtures == null) {
                world.getGameRules().setOrCreateGameRule("doMobSpawning", "false");
                world.getGameRules().setOrCreateGameRule("randomTickSpeed", "0");
                // Time the whole vanilla tile phase, including dispatch, without
                // adding a pair of timer calls to every individual tile.
                Field field = ReflectionHelper.findField(World.class, "profiler", "field_72984_F");
                field.set(world, profiler);
                fixtures = new MachineStressFixtures(world, scenario, count);
                Files.createDirectories(resultPath.getParent());
            }
            switch (state) {
                case INSTALL:
                    if (!fixtures.installNextBatch(8192)) {
                        if (fixtures.entries.size() % 8192 == 0) log("SETUP count=" + fixtures.entries.size() + "/" + count);
                        return;
                    }
                    log("SETUP_COMPLETE count=" + count + " types=" + fixtures.types.size() + " upgrades=" + fixtures.upgrades);
                    if (Boolean.getBoolean("mekanism.machine.stress.qioLifecycle")) {
                        qioLifecycle = (MachineStressLifecycle) Class.forName(
                              "mekanism.qioprocessing.common.machine.QIOHostLifecycleSmoke")
                              .getConstructor(List.class).newInstance(fixtures.entries);
                    }
                    spark = new SparkSender(server);
                    state = State.SPARK_START;
                    deadline = System.nanoTime() + COMMAND_TIMEOUT_NANOS;
                    spark.command("spark profiler --thread * --interval 1");
                    return;
                case SPARK_START:
                    if (!spark.active) {
                        MachineStressFixtures.check(System.nanoTime() < deadline, "Spark did not confirm profiler active");
                        return;
                    }
                    log("SPARK_ACTIVE_CONFIRMED");
                    if (qioLifecycle != null) {
                        state = State.QIO_LIFECYCLE;
                        deadline = System.nanoTime() + COMMAND_TIMEOUT_NANOS;
                        return;
                    }
                    fixtures.prepareTick();
                    if (warmupLimit == 0) beginMeasurement();
                    else state = State.WARMUP;
                    return;
                case QIO_LIFECYCLE:
                    MachineStressFixtures.check(System.nanoTime() < deadline, "QIO lifecycle smoke timed out");
                    if (!qioLifecycle.advance(server)) return;
                    fixtures.prepareTick();
                    if (warmupLimit == 0) beginMeasurement();
                    else state = State.WARMUP;
                    return;
                case WARMUP:
                    fixtures.verifyTick();
                    if ((warmup + 1) % 5 == 0) log("WARMUP ticks=" + (warmup + 1) + "/" + warmupLimit);
                    if (++warmup >= warmupLimit) {
                        beginMeasurement();
                    }
                    fixtures.prepareTick();
                    return;
                case MEASURE:
                    MachineStressFixtures.check(profiler.blockEntityNanos > 0, "World tile phase was not instrumented");
                    MachineStressFixtures.check(profiler.blockEntityNanos + barrierNanos <= tickNanos + 2000,
                          "Machine phase timing exceeds the containing tick");
                    int working = fixtures.verifyTick();
                    Sample sample = new Sample(world.getTotalWorldTime(), profiler.blockEntityNanos, barrierNanos, tickNanos, working);
                    samples.add(sample);
                    log("SAMPLE tick=" + sample.tick + " machineMs=" + millis(sample.machineNanos()) +
                          " tilePhaseMs=" + millis(sample.tileNanos) + " barrierMs=" + millis(sample.barrierNanos) +
                          " tickMs=" + millis(tickNanos) + " working=" + working);
                    boolean complete = fixtures.cycleTarget > 0 ? fixtures.cyclesComplete() : samples.size() >= sampleLimit;
                    MachineStressFixtures.check(fixtures.cycleTarget == 0 || samples.size() <= 20000,
                          "Recipe cycles did not finish within the 20000-tick diagnostic limit");
                    if (!complete) fixtures.prepareTick();
                    else {
                        fixtures.producedResources();
                        stopSpark();
                    }
                    return;
                case SPARK_STOP:
                    if (spark.savedPath != null && !spark.infoCommanded) {
                        spark.infoCommanded = true;
                        spark.command("spark profiler --info");
                    }
                    if (spark.savedPath != null && spark.inactive) finish(server);
                    else if (System.nanoTime() >= deadline) {
                        failure = "Spark stop/save/inactive confirmation timed out";
                        finish(server);
                    }
                    return;
                default:
                    return;
            }
        } catch (Throwable error) {
            Mekanism.logger.error("Machine stress smoke failed in " + state, error);
            failure = error.toString();
            if (state == State.SPARK_STOP || spark == null || !spark.active) finish(server);
            else stopSpark();
        }
    }

    private void stopSpark() {
        log("MEASURE_END samples=" + samples.size());
        state = State.SPARK_STOP;
        deadline = System.nanoTime() + COMMAND_TIMEOUT_NANOS;
        spark.command("spark profiler --stop --save-to-file");
    }

    private void beginMeasurement() {
        if (fixtures.cycleTarget > 0) {
            fixtures.beginCycleMeasurement();
            RecipeTiming.begin();
            RecipeTiming.pause();
        }
        state = State.MEASURE;
        log("MEASURE_BEGIN count=" + count + " scenario=" + scenario + " samples=" + sampleLimit +
              " recipeCycles=" + fixtures.cycleTarget + " pattern=" + fixtures.cyclePattern);
    }

    private void finish(MinecraftServer server) {
        if (finished) return;
        state = State.DONE;
        boolean completed = fixtures != null && (fixtures.cycleTarget > 0 ? fixtures.cyclesComplete() : samples.size() == sampleLimit);
        boolean functional = failure == null && completed && spark != null &&
              spark.active && spark.stopped && spark.inactive && spark.savedPath != null;
        Boolean performance = maxMachineNanos == null ? null : functional &&
              samples.stream().allMatch(sample -> sample.machineNanos() <= maxMachineNanos);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("runId", runId);
        report.put("scenario", scenario);
        report.put("count", count);
        report.put("environment", client ? "integrated-server-and-client" : "dedicated-server");
        report.put("fixture", "registered-core-machines");
        report.put("fixtureBindings", "direct-real-containers-v1");
        report.put("pendingPlanInspected", MachineStressFixtures.inspectsPendingPlans());
        report.put("coverageComplete", false);
        report.put("remainingCoverage", "Other recipe modes, large/multiblock machines and addon fixtures; external transfer scenarios");
        report.put("functionalPass", functional);
        report.put("performancePass", performance);
        report.put("evaluationMode", maxMachineNanos == null ? "measure-only" : "threshold");
        report.put("goalPass", false);
        report.put("failure", failure);
        report.put("thresholdNanos", maxMachineNanos);
        report.put("measurement", "vanilla blockEntities phase + TaskExecutor barrier/commit/callback wall time; fixture maintenance excluded");
        report.put("processorCount", Runtime.getRuntime().availableProcessors());
        report.put("javaVersion", System.getProperty("java.version"));
        report.put("heapMaxBytes", Runtime.getRuntime().maxMemory());
        report.put("watchdog", "disabled in isolated smoke properties; Gradle process timeout 20 minutes");
        report.put("warmupTicks", warmup);
        report.put("samples", samples);
        if (fixtures != null && fixtures.cycleTarget > 0) {
            report.put("recipeCycles", fixtures.cycleReport());
            report.put("recipeMethodTiming", RecipeTiming.report());
        }
        if (qioLifecycle != null) report.put("qioLifecycle", qioLifecycle.report());
        if (fixtures != null) {
            report.put("typeCounts", fixtures.types);
            report.put("installedUpgradeMaxima", fixtures.upgrades);
            report.put("mutuallyExclusiveUpgrades", fixtures.conflictingUpgrades);
            report.put("uniqueRecipeSignatures", fixtures.recipeSignatures.size());
            if (functional) report.put("producedResourceUnits", fixtures.producedResources());
        }
        try {
            if (spark != null) {
                report.put("sparkActiveConfirmed", spark.active);
                report.put("sparkStoppedConfirmed", spark.stopped);
                report.put("sparkInactiveConfirmed", spark.inactive);
                report.put("sparkSourcePath", spark.savedPath);
                if (spark.savedPath != null) {
                    Path destination = resultPath.resolveSibling("profile.sparkprofile");
                    Files.copy(Paths.get(spark.savedPath), destination, StandardCopyOption.REPLACE_EXISTING);
                    report.put("sparkProfile", destination.toString());
                }
                Files.write(resultPath.resolveSibling("spark-messages.txt"), spark.messages.toString().getBytes(StandardCharsets.UTF_8));
            }
            long[] times = samples.stream().mapToLong(Sample::machineNanos).sorted().toArray();
            if (times.length > 0) {
                report.put("machineMinNanos", times[0]);
                report.put("machineMedianNanos", times[times.length / 2]);
                report.put("machineP95Nanos", times[(int) Math.ceil(times.length * 0.95) - 1]);
                report.put("machineP99Nanos", times[(int) Math.ceil(times.length * 0.99) - 1]);
                report.put("machineMaxNanos", times[times.length - 1]);
                report.put("machineMeanNanos", Arrays.stream(times).average().orElse(0));
            }
            Files.createDirectories(resultPath.getParent());
            if (fixtures != null) fixtures.writeCycleEvidence(resultPath.getParent());
            Files.write(resultPath.resolveSibling("result.json"), new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));
            String result = (functional && !Boolean.FALSE.equals(performance) ? "PASS" : "FAIL") +
                  " functional=" + functional + " performance=" + (performance == null ? "NOT_EVALUATED" : performance) +
                  " goalPass=false scenario=" + scenario + " count=" + count + " samples=" + samples.size() +
                  " maxMachineMs=" + (times.length == 0 ? "n/a" : millis(times[times.length - 1])) + " failure=" + failure;
            Files.write(resultPath, result.getBytes(StandardCharsets.UTF_8));
            log("RESULT " + result);
        } catch (Throwable error) {
            Mekanism.logger.error("Cannot preserve machine stress evidence", error);
        } finally {
            finished = true;
            if (fixtures != null) fixtures.stopTicking();
            server.initiateShutdown();
        }
    }

    private static String millis(long nanos) { return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000D); }
    private static void log(String text) { Mekanism.logger.info("MACHINE_STRESS_" + text); }

    private static final class Sample {
        final long tick;
        final long tileNanos;
        final long barrierNanos;
        final long tickNanos;
        final int working;

        Sample(long tick, long tileNanos, long barrierNanos, long tickNanos, int working) {
            this.tick = tick;
            this.tileNanos = tileNanos;
            this.barrierNanos = barrierNanos;
            this.tickNanos = tickNanos;
            this.working = working;
        }
        long machineNanos() { return tileNanos + barrierNanos; }
    }

    private static final class TickProfiler extends Profiler {
        private boolean inBlockEntities;
        private long sectionStarted;
        long blockEntityNanos;

        @Override public void startSection(String name) {
            if ("blockEntities".equals(name)) {
                inBlockEntities = true;
                sectionStarted = System.nanoTime();
            } else if (inBlockEntities && "pendingBlockEntities".equals(name)) {
                blockEntityNanos += System.nanoTime() - sectionStarted;
                inBlockEntities = false;
            }
        }
        @Override public void func_194340_a(Supplier<String> name) { }
        @Override public void startSection(Class<?> type) { }
        @Override public void endSection() { }
        @Override public void endStartSection(String name) { startSection(name); }
    }

    private static final class SparkSender implements ICommandSender {
        private final MinecraftServer server;
        final StringBuffer messages = new StringBuffer();
        volatile boolean active;
        volatile boolean stopped;
        volatile boolean inactive;
        volatile String savedPath;
        boolean infoCommanded;

        SparkSender(MinecraftServer server) { this.server = server; }
        void command(String command) {
            messages.append(System.currentTimeMillis()).append(" COMMAND ").append(command).append('\n');
            log("SPARK_COMMAND " + command);
            int result = server.getCommandManager().executeCommand(this, command);
            MachineStressFixtures.check(result > 0, "Spark command rejected: " + command);
        }
        @Override public void sendMessage(ITextComponent component) {
            String text = component.getUnformattedText();
            messages.append(System.currentTimeMillis()).append(' ').append(text).append('\n');
            log("SPARK_RESPONSE " + text);
            if (text.contains("Profiler now active!")) active = true;
            if (text.contains("profiler has been stopped!")) stopped = true;
            if (text.contains("There isn't an active profiler running.")) inactive = true;
            int prefix = text.indexOf("Profile written to:");
            if (prefix >= 0) savedPath = text.substring(prefix + "Profile written to:".length()).trim();
        }
        @Override public String getName() { return "MachineStressSmoke"; }
        @Override public boolean canUseCommand(int level, String name) { return true; }
        @Override public World getEntityWorld() { return server.getWorld(0); }
        @Override public MinecraftServer getServer() { return server; }
    }
}
