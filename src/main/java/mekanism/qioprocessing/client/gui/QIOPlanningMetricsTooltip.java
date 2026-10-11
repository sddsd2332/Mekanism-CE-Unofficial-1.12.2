package mekanism.qioprocessing.client.gui;

import mekanism.qioprocessing.common.planning.QIOPlanningMetrics.Metric;
import mekanism.qioprocessing.common.planning.QIOPlanningMetrics.Snapshot;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared diagnostics for encoding and formal planning, outside the compact status rows.
 */
final class QIOPlanningMetricsTooltip {
    private QIOPlanningMetricsTooltip() {
    }

    static List<ITextComponent> lines(Snapshot metrics) {
        List<ITextComponent> lines = new ArrayList<>();
        if (!metrics.isPresent()) return lines;
        lines.add(summary(metrics));
        phase(lines, metrics, "preparation", Metric.MAIN_PREPARATION_NS, Metric.MAIN_PREPARATION_CPU_NS, Metric.MAIN_PREPARATION_CPU_SAMPLES);
        phase(lines, metrics, "route", Metric.ROUTE_NS, Metric.ROUTE_CPU_NS, Metric.ROUTE_CPU_SAMPLES);
        phase(lines, metrics, "discovery", Metric.DISCOVERY_NS, Metric.DISCOVERY_CPU_NS, Metric.DISCOVERY_CPU_SAMPLES);
        phase(lines, metrics, "candidate", Metric.CANDIDATE_NS, Metric.CANDIDATE_CPU_NS, Metric.CANDIDATE_CPU_SAMPLES);
        phase(lines, metrics, "solve", Metric.SOLVE_NS, Metric.SOLVE_CPU_NS, Metric.SOLVE_CPU_SAMPLES);
        lines.add(tr("validation", metrics.get(Metric.VALIDATION_REQUESTS), metrics.get(Metric.VALIDATION_FAILURES),
                time(metrics, Metric.VALIDATION_QUEUE_NS), time(metrics, Metric.VALIDATION_CALL_NS),
                cpu(metrics, Metric.VALIDATION_CPU_NS, Metric.VALIDATION_CPU_SAMPLES)));
        lines.add(tr("queue", time(metrics, Metric.WORKER_QUEUE_NS), time(metrics, Metric.DELIVERY_WAIT_NS),
                metrics.get(Metric.VERSION_RETRIES)));
        lines.add(tr("search", metrics.get(Metric.CANDIDATE_VISITS), metrics.get(Metric.CANDIDATE_GRIDS),
                metrics.get(Metric.BRANCH_ATTEMPTS), metrics.get(Metric.BACKTRACKS)));
        lines.add(tr("cache", metrics.get(Metric.STRUCTURE_CACHE_HITS), metrics.get(Metric.STRUCTURE_CACHE_MISSES),
                metrics.get(Metric.VARIANT_CACHE_HITS), metrics.get(Metric.VARIANT_CACHE_MISSES), metrics.get(Metric.PINNED_KNOWLEDGE_HITS)));
        lines.add(tr("plan_cache", metrics.get(Metric.PREVIEW_PLAN_CACHE_HITS), metrics.get(Metric.PREVIEW_PLAN_CACHE_MISSES),
                metrics.get(Metric.PREVIEW_PLAN_CACHE_STORES), metrics.get(Metric.PREVIEW_PLAN_CACHE_REJECTIONS)));
        lines.add(tr("optimizer", metrics.get(Metric.TOPOLOGY_INCUMBENTS), metrics.get(Metric.GROUPED_ROUTE_CACHE_HITS),
                metrics.get(Metric.GROUPED_ROUTE_CACHE_MISSES), metrics.get(Metric.OPTIMIZATION_BUDGET_RETURNS)));
        if (metrics.get(Metric.FIRST_COMPLETE_PLANS) > 0) {
            lines.add(tr("first_complete", time(metrics, Metric.FIRST_COMPLETE_NS), metrics.get(Metric.FIRST_COMPLETE_NODES),
                    time(metrics, Metric.OPTIMIZATION_ELAPSED_NS), metrics.get(Metric.OPTIMIZATION_NODES),
                    metrics.get(Metric.OPTIMIZATION_BRANCHES), metrics.get(Metric.OPTIMIZATION_VALIDATION_YIELDS)));
        }
        lines.add(tr("graph", metrics.get(Metric.PEAK_RESOURCES), metrics.get(Metric.PEAK_ROUTES),
                metrics.get(Metric.PEAK_PLAN_STEPS), metrics.get(Metric.PEAK_DEPENDENCIES),
                metrics.get(Metric.PEAK_USED_VARIANTS), metrics.get(Metric.PEAK_CYCLE_NODES)));
        lines.add(tr("scopes"));
        lines.add(tr("work", metrics.get(Metric.PEAK_EXPLORED_NODES), metrics.get(Metric.PEAK_OPERATIONS),
                metrics.get(Metric.PEAK_KNOWLEDGE_ENTRIES), metrics.get(Metric.PEAK_KNOWLEDGE_BYTES)));
        if (metrics.get(Metric.PARTIAL_SNAPSHOTS) > 0) lines.add(tr("partial"));
        return lines;
    }

    static ITextComponent summary(Snapshot metrics) {
        return tr("summary", time(metrics, Metric.TOTAL_NS), time(metrics, Metric.WORKER_NS),
                cpu(metrics, Metric.WORKER_CPU_NS, Metric.WORKER_CPU_SAMPLES));
    }

    private static void phase(List<ITextComponent> lines, Snapshot metrics, String name, Metric wall, Metric cpu, Metric samples) {
        lines.add(tr("phase", tr(name), time(metrics, wall), cpu(metrics, cpu, samples)));
    }

    private static ITextComponent cpu(Snapshot metrics, Metric cpu, Metric samples) {
        return metrics.get(samples) == 0 ? tr("unavailable") : time(metrics, cpu);
    }

    private static ITextComponent time(Snapshot metrics, Metric metric) {
        return GuiQIOPlanningStatusScreen.duration(metrics.get(metric), false);
    }

    private static ITextComponent tr(String key, Object... args) {
        return new TextComponentTranslation("gui.mekanismqioprocessing.metrics_" + key, args);
    }
}
