package mekanism.common.config;

import io.netty.buffer.ByteBuf;
import mekanism.common.config.options.BooleanOption;
import mekanism.common.config.options.IntOption;
import mekanism.common.config.options.LongOption;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;

/** Server-authoritative QIO Processing module settings managed by Mekanism's config system. */
@SuppressWarnings("deprecation")
public class QIOProcessingConfig extends BaseConfig {

    public static final int DEFAULT_PLANNING_TIME_PER_TICK = 5;
    public static final int DEFAULT_RESULTS_PER_TICK = 32;
    public static final int DEFAULT_PREVIEW_TIMEOUT_TICKS = 1_200;
    public static final int DEFAULT_PREVIEWS_PER_PLAYER = 8;
    public static final int DEFAULT_NON_TERMINAL_JOBS = 1_024;
    public static final int DEFAULT_EXECUTION_ACTIONS_PER_TICK = 128;
    public static final int DEFAULT_SLOT_GRANTS_PER_TICK = 8;
    public static final int DEFAULT_WORKBENCH_THREADS_PER_JOB = 8;
    public static final int DEFAULT_MACHINE_THREADS_PER_JOB = 8;
    public static final int DEFAULT_EXECUTION_SLOTS = 64;
    public static final int MIN_EXECUTION_SLOTS = 8;
    public static final int DEFAULT_CLAIM_REFRESHES_PER_TICK = 32;
    public static final int DEFAULT_MAINTENANCE_EVALUATION_INTERVAL = 100;
    public static final int DEFAULT_MAINTENANCE_GROUPS_PER_TICK = 16;
    public static final int DEFAULT_MAINTENANCE_RULES_PER_FREQUENCY = 4_096;
    public static final long DEFAULT_MAINTENANCE_BATCH_CAP = 1_000_000;
    public static final int DEFAULT_DEVICE_RECORDS_PER_FREQUENCY = 65_536;
    public static final int DEFAULT_PROVIDER_ROUTES_PER_FREQUENCY = 65_536;
    public static final int DEFAULT_TERMINAL_PAGE_SIZE = 128;
    public static final int MIN_TERMINAL_PAGE_SIZE = 16;
    public static final int MAX_TERMINAL_PAGE_SIZE = 1_024;
    public static final int DEFAULT_RECIPE_CATALOG_CAPTURES_PER_TICK = 128;
    public static final int DEFAULT_RECIPE_CATALOG_CAPTURE_TIME_PER_TICK = 5;
    public static final int DEFAULT_RECIPE_CATALOG_WORKER_THREADS = 4;

    public final IntOption planningTimePerTick = new IntOption(this, "planning",
          "planningTimePerTick", DEFAULT_PLANNING_TIME_PER_TICK,
          "Milliseconds shared by all active QIO planning calculations on one server tick.",
          1, Integer.MAX_VALUE);
    public final IntOption planningResultsPerTick = new IntOption(this, "planning",
          "planningResultsPerTick", DEFAULT_RESULTS_PER_TICK,
          "Maximum completed planning results committed on one server tick.", 1,
          Integer.MAX_VALUE);
    public final IntOption previewTimeoutTicks = new IntOption(this, "planning",
          "previewTimeoutTicks", DEFAULT_PREVIEW_TIMEOUT_TICKS,
          "Lifetime of an unlocked QIO plan preview before confirmation is rejected.", 20,
          72_000);
    public final IntOption previewsPerPlayer = new IntOption(this, "planning",
          "previewsPerPlayer", DEFAULT_PREVIEWS_PER_PLAYER,
          "Maximum simultaneous QIO plan previews owned by one player.", 1, 256);
    public final IntOption nonTerminalJobsPerFrequency = new IntOption(this, "scheduling",
          "nonTerminalJobsPerFrequency", DEFAULT_NON_TERMINAL_JOBS,
          "Maximum queued or active QIO processing jobs retained by one frequency.", 8,
          Integer.MAX_VALUE);
    public final IntOption executionActionsPerTick = new IntOption(this, "scheduling",
          "executionActionsPerTick", DEFAULT_EXECUTION_ACTIONS_PER_TICK,
          "Maximum QIO job transfer or dispatch actions committed per server tick.", 1,
          Integer.MAX_VALUE);
    public final IntOption slotGrantsPerTick = new IntOption(this, "scheduling",
          "slotGrantsPerTick", DEFAULT_SLOT_GRANTS_PER_TICK,
          "Maximum execution slots granted by one frequency per server tick.", 1,
          Integer.MAX_VALUE);
    public final BooleanOption limitWorkbenchThreadsPerJob = new BooleanOption(this,
          "scheduling", "limitWorkbenchThreadsPerJob", false,
          "Whether one QIO job has an explicit workbench-thread limit. When disabled, all " +
                "eligible online workbench processor lanes may be used.");
    public final IntOption maxWorkbenchThreadsPerJob = new IntOption(this, "scheduling",
          "maxWorkbenchThreadsPerJob", DEFAULT_WORKBENCH_THREADS_PER_JOB,
          "Maximum workbench processor lanes used concurrently by one QIO job when its limit " +
                "is enabled.", 1, Integer.MAX_VALUE);
    public final BooleanOption limitMachineThreadsPerJob = new BooleanOption(this,
          "scheduling", "limitMachineThreadsPerJob", false,
          "Whether one QIO job has an explicit machine-thread limit. When disabled, all " +
                "eligible online machine lanes may be used.");
    public final IntOption maxMachineThreadsPerJob = new IntOption(this, "scheduling",
          "maxMachineThreadsPerJob", DEFAULT_MACHINE_THREADS_PER_JOB,
          "Maximum machine lanes used concurrently by one QIO job when its limit is enabled.",
          1, Integer.MAX_VALUE);
    public final IntOption executionSlotsPerFrequency = new IntOption(this, "scheduling",
          "executionSlotsPerFrequency", DEFAULT_EXECUTION_SLOTS,
          "Maximum active QIO processing orders per frequency.", MIN_EXECUTION_SLOTS,
          Integer.MAX_VALUE);
    public final IntOption claimRefreshesPerTick = new IntOption(this, "claims",
          "claimRefreshesPerTick", DEFAULT_CLAIM_REFRESHES_PER_TICK,
          "Maximum material claims refreshed by storage events on one server tick.", 1,
          Integer.MAX_VALUE);
    public final LongOption processorLaneLimit = new LongOption(this, "processors",
          "processorLaneLimit", Long.MAX_VALUE,
          "Maximum logical lane count accepted from a processor definition. Uses a signed long.",
          9, Long.MAX_VALUE).setRequiresGameRestart();
    public final IntOption maintenanceEvaluationInterval = new IntOption(this, "maintenance",
          "evaluationInterval", DEFAULT_MAINTENANCE_EVALUATION_INTERVAL,
          "Ticks between complete inventory-maintenance evaluation passes.", 20, 72_000);
    public final IntOption maintenanceGroupsPerTick = new IntOption(this, "maintenance",
          "groupsPerTick", DEFAULT_MAINTENANCE_GROUPS_PER_TICK,
          "Maximum resource rule groups evaluated per server tick.", 1, 65_536);
    public final IntOption maintenanceRulesPerFrequency = new IntOption(this, "maintenance",
          "rulesPerFrequency", DEFAULT_MAINTENANCE_RULES_PER_FREQUENCY,
          "Maximum inventory-maintenance rules stored by one QIO frequency.", 1, 1_000_000);
    public final LongOption maintenanceBatchCap = new LongOption(this, "maintenance",
          "batchCap", DEFAULT_MAINTENANCE_BATCH_CAP,
          "Server-wide maximum amount requested by one maintenance order.", 1,
          Long.MAX_VALUE);
    public final IntOption deviceRecordsPerFrequency = new IntOption(this, "devices",
          "recordsPerFrequency", DEFAULT_DEVICE_RECORDS_PER_FREQUENCY,
          "Maximum online and offline QIO automation device records retained by one frequency.",
          1, 1_000_000);
    public final IntOption providerRoutesPerFrequency = new IntOption(this, "providers",
          "routesPerFrequency", DEFAULT_PROVIDER_ROUTES_PER_FREQUENCY,
          "Maximum stable provider recipe routes retained by one QIO frequency.",
          1, 1_000_000);
    public final IntOption terminalPageSize = new IntOption(this, "terminals",
          "maximumPageSize", DEFAULT_TERMINAL_PAGE_SIZE,
          "Maximum number of records returned by one QIO Processing terminal page.",
          MIN_TERMINAL_PAGE_SIZE, MAX_TERMINAL_PAGE_SIZE);
    public final IntOption recipeCatalogCapturesPerTick = new IntOption(NULL_OWNER,
          "recipe_catalog", "capturesPerTick", DEFAULT_RECIPE_CATALOG_CAPTURES_PER_TICK,
          "Maximum Forge item, ore-dictionary, or workbench recipe records captured per tick.",
          1, Integer.MAX_VALUE);
    public final IntOption recipeCatalogCaptureTimePerTick = new IntOption(NULL_OWNER,
          "recipe_catalog", "captureTimePerTick", DEFAULT_RECIPE_CATALOG_CAPTURE_TIME_PER_TICK,
          "Maximum milliseconds spent capturing Forge recipe data on one server tick.",
          1, 1_000);
    public final IntOption recipeCatalogWorkerThreads = new IntOption(NULL_OWNER,
          "recipe_catalog", "workerThreads", DEFAULT_RECIPE_CATALOG_WORKER_THREADS,
          "Dedicated worker threads used to compile frozen QIO recipe catalog data.", 1, 32)
          .setRequiresGameRestart();

    @Override
    public void load(Configuration config) {
        super.load(config);
        recipeCatalogCapturesPerTick.load(config);
        recipeCatalogCaptureTimePerTick.load(config);
        recipeCatalogWorkerThreads.load(config);
        removeLegacyAutomaticOutputLimit(config);
        validate();
    }

    @Override
    public void read(ByteBuf config) {
        super.read(config);
        validate();
    }

    @Override
    public String getCategory() {
        return "qio_processing";
    }

    private void validate() {
        planningTimePerTick.set(Math.max(1, planningTimePerTick.val()));
        planningResultsPerTick.set(Math.max(1, planningResultsPerTick.val()));
        previewTimeoutTicks.set(Math.max(20, previewTimeoutTicks.val()));
        previewsPerPlayer.set(Math.max(1, previewsPerPlayer.val()));
        nonTerminalJobsPerFrequency.set(Math.max(8, nonTerminalJobsPerFrequency.val()));
        executionActionsPerTick.set(Math.max(1, executionActionsPerTick.val()));
        slotGrantsPerTick.set(Math.max(1, slotGrantsPerTick.val()));
        maxWorkbenchThreadsPerJob.set(Math.max(1, maxWorkbenchThreadsPerJob.val()));
        maxMachineThreadsPerJob.set(Math.max(1, maxMachineThreadsPerJob.val()));
        executionSlotsPerFrequency.set(Math.max(MIN_EXECUTION_SLOTS,
              executionSlotsPerFrequency.val()));
        claimRefreshesPerTick.set(Math.max(1, claimRefreshesPerTick.val()));
        processorLaneLimit.set(Math.max(9, processorLaneLimit.val()));
        maintenanceEvaluationInterval.set(Math.max(20, maintenanceEvaluationInterval.val()));
        maintenanceGroupsPerTick.set(Math.max(1, maintenanceGroupsPerTick.val()));
        maintenanceRulesPerFrequency.set(Math.max(1, maintenanceRulesPerFrequency.val()));
        maintenanceBatchCap.set(Math.max(1, maintenanceBatchCap.val()));
        deviceRecordsPerFrequency.set(Math.max(1, deviceRecordsPerFrequency.val()));
        providerRoutesPerFrequency.set(Math.max(1, providerRoutesPerFrequency.val()));
        terminalPageSize.set(clamp(terminalPageSize.val(), MIN_TERMINAL_PAGE_SIZE,
              MAX_TERMINAL_PAGE_SIZE));
        recipeCatalogCapturesPerTick.set(Math.max(1, recipeCatalogCapturesPerTick.val()));
        recipeCatalogCaptureTimePerTick.set(clamp(recipeCatalogCaptureTimePerTick.val(), 1,
              1_000));
        recipeCatalogWorkerThreads.set(clamp(recipeCatalogWorkerThreads.val(), 1, 32));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 删除旧版按资源数量限制自动输出的配置项；自动输出现在按实际槽位数调度。 */
    private static void removeLegacyAutomaticOutputLimit(Configuration config) {
        if (!config.hasCategory("automation")) {
            return;
        }
        ConfigCategory automation = config.getCategory("automation");
        automation.remove("automaticOutputTransferLimit");
        if (automation.isEmpty()) {
            config.removeCategory(automation);
        }
    }
}
