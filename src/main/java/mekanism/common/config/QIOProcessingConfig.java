package mekanism.common.config;

import io.netty.buffer.ByteBuf;
import mekanism.common.config.options.BooleanOption;
import mekanism.common.config.options.EnumOption;
import mekanism.common.config.options.IntOption;
import mekanism.common.config.options.LongOption;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;

/**
 * Server-authoritative QIO Processing module settings managed by Mekanism's config system.
 */
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
    public static final int DEFAULT_RECIPE_CATALOG_CAPTURES_PER_TICK = 2048;
    public static final int DEFAULT_RECIPE_CATALOG_CAPTURE_TIME_PER_TICK = 20;
    public static final int DEFAULT_RECIPE_CATALOG_WORKER_THREADS = 8;

    public final IntOption planningTimePerTick = new IntOption(this, "planning",
            "planningTimePerTick", DEFAULT_PLANNING_TIME_PER_TICK,
            "Legacy compatibility value. Independent planning workers no longer use tick slices.",
            1, Integer.MAX_VALUE);
    public final IntOption planningWorkerThreads = new IntOption(this, "planning",
            "workerThreads", Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1)),
            "Maximum independent QIO planning workers. Applied when the server starts.", 1, 32)
            .setRequiresWorldRestart();
    public final IntOption planningQueueCapacity = new IntOption(this, "planning",
            "queueCapacity", 64,
            "Maximum waiting planning tasks. Pending result delivery also consumes admission capacity.",
            1, 4096).setRequiresWorldRestart();
    public final IntOption planningTaskTimeoutSeconds = new IntOption(this, "planning",
            "taskTimeoutSeconds", 120,
            "Planning deadline including queue time; cancellation is cooperative. Zero disables the deadline.",
            0, 3600).setRequiresWorldRestart();
    public final IntOption planningResultTimePerTick = new IntOption(this, "planning",
            "resultTimePerTick", 5,
            "Milliseconds allowed for planning handoffs and completion callbacks per tick. A single callback is not preempted.",
            1, 1000);
    public final IntOption planningResultsPerTick = new IntOption(this, "planning",
            "planningResultsPerTick", DEFAULT_RESULTS_PER_TICK,
            "Maximum planning continuation handoffs and completed results processed on one server tick.", 1,
            Integer.MAX_VALUE);
    public final IntOption workbenchValidationsPerTick = new IntOption(this, "workbench",
            "validationsPerTick", 128, "Maximum concrete workbench grids validated on the server per tick.", 1, 4096);
    public final IntOption workbenchValidationTimePerTick = new IntOption(this, "workbench",
            "validationTimePerTick", 5, "Server milliseconds for concrete workbench validation. A callback cannot be preempted.", 1, 1000);
    public final IntOption workbenchValidationQueueCapacity = new IntOption(this, "workbench",
            "validationQueueCapacity", 4096, "Maximum pending workbench grid validations.", 1, 65536);
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
            "Legacy compatibility option; unused by the declaration directory.",
            1, Integer.MAX_VALUE);
    public final IntOption recipeCatalogCaptureTimePerTick = new IntOption(NULL_OWNER,
            "recipe_catalog", "captureTimePerTick", DEFAULT_RECIPE_CATALOG_CAPTURE_TIME_PER_TICK,
            "Legacy compatibility option; use workbench.validationTimePerTick for concrete validation.",
            1, 1_000);
    public final IntOption recipeCatalogWorkerThreads = new IntOption(NULL_OWNER,
            "recipe_catalog", "workerThreads", DEFAULT_RECIPE_CATALOG_WORKER_THREADS,
            "Legacy compatibility option; unused by the declaration directory.", 1, 32)
            .setRequiresGameRestart();
    public final EnumOption<QIORecipeCatalogScanMode> recipeCatalogScanMode =
            new EnumOption<>(this, "recipe_catalog", "scanMode",
                    QIORecipeCatalogScanMode.AUTO_DISCOVERY,
                    "Workbench declarations: AUTO_DISCOVERY captures recipes once at the initial frozen registry event; " +
                            "MANUAL_ONLY keeps manual nine-slot encoding and existing patterns, without automatic discovery. " +
                            "No full recipe catalog is restored from disk. Legacy FULL/FIRST_ONLY/CHANGED/ENABLED map to " +
                            "AUTO_DISCOVERY; DISABLED maps to MANUAL_ONLY.")
                    .setRequiresGameRestart();

    @Override
    public void load(Configuration config) {
        net.minecraftforge.common.config.Property scan = config.get("recipe_catalog", "scanMode",
                QIORecipeCatalogScanMode.AUTO_DISCOVERY.name());
        scan.set(QIORecipeCatalogScanMode.readLegacy(scan.getString()).name());
        super.load(config);
        config.get("recipe_catalog", "scanMode",
                        QIORecipeCatalogScanMode.AUTO_DISCOVERY.name())
                .setValidValues(new String[]{"AUTO_DISCOVERY", "MANUAL_ONLY"});
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
        recipeCatalogScanMode.set(recipeCatalogScanMode.val().canonical());
        workbenchValidationsPerTick.set(clamp(workbenchValidationsPerTick.val(), 1, 4096));
        workbenchValidationTimePerTick.set(clamp(workbenchValidationTimePerTick.val(), 1, 1000));
        workbenchValidationQueueCapacity.set(clamp(workbenchValidationQueueCapacity.val(), 1, 65536));
        planningTimePerTick.set(Math.max(1, planningTimePerTick.val()));
        planningWorkerThreads.set(clamp(planningWorkerThreads.val(), 1, 32));
        planningQueueCapacity.set(clamp(planningQueueCapacity.val(), 1, 4096));
        planningTaskTimeoutSeconds.set(clamp(planningTaskTimeoutSeconds.val(), 0, 3600));
        planningResultTimePerTick.set(clamp(planningResultTimePerTick.val(), 1, 1000));
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

    /**
     * 删除旧版按资源数量限制自动输出的配置项；自动输出现在按实际槽位数调度。
     */
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
