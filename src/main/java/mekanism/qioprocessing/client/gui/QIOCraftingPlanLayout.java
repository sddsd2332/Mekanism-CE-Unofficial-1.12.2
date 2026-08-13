package mekanism.qioprocessing.client.gui;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.plan.QIOCandidateInputGroup;
import mekanism.qioprocessing.common.content.plan.QIOCandidateOption;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorPlanEntry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** Iterative AE-style tree layout primitives kept independent from rendering and client state. */
final class QIOCraftingPlanLayout {

    static final int NODE_WIDTH = 20;
    static final int NODE_HEIGHT = 20;
    static final int LINE_HEIGHT = 2;
    static final int LINE_TOTAL_HEIGHT = 6;
    static final int NODE_MARGIN_LEFT = 6;
    static final int NODE_TOTAL_WIDTH = NODE_MARGIN_LEFT + NODE_WIDTH;
    static final int NODE_TOTAL_HEIGHT = NODE_HEIGHT + LINE_TOTAL_HEIGHT;
    static final int ROOT_MARGIN_TOP = 4;
    static final int ROOT_MARGIN_RIGHT = 6;
    static final int MAX_TREE_OCCURRENCES = 100_000;
    static final int CANDIDATE_CYCLE_TICKS = 40;

    private QIOCraftingPlanLayout() {
    }

    static Map<Long, Integer> depths(Set<Long> nodeIds,
          Map<Long, List<Long>> dependencies) {
        Map<Long, Integer> indegrees = new LinkedHashMap<>();
        Map<Long, List<Long>> consumers = new LinkedHashMap<>();
        Map<Long, Integer> depths = new LinkedHashMap<>();
        for (long nodeId : nodeIds) {
            int degree = 0;
            for (long dependency : dependencies.getOrDefault(nodeId, Collections.emptyList())) {
                if (!nodeIds.contains(dependency)) continue;
                degree++;
                consumers.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(nodeId);
            }
            indegrees.put(nodeId, degree);
            depths.put(nodeId, 0);
        }
        PriorityQueue<Long> ready = new PriorityQueue<>();
        indegrees.forEach((nodeId, degree) -> { if (degree == 0) ready.add(nodeId); });
        int visited = 0;
        while (!ready.isEmpty()) {
            long dependency = ready.remove();
            visited++;
            int nextDepth = depths.getOrDefault(dependency, 0) + 1;
            for (long consumer : consumers.getOrDefault(dependency, Collections.emptyList())) {
                depths.put(consumer, Math.max(depths.getOrDefault(consumer, 0), nextDepth));
                int degree = indegrees.computeIfPresent(consumer,
                      (ignored, value) -> value - 1);
                if (degree == 0) ready.add(consumer);
            }
        }
        // Persisted plans should already be SCC-condensed. Keep malformed leftovers visible.
        if (visited != nodeIds.size()) {
            for (long nodeId : nodeIds) depths.putIfAbsent(nodeId, 0);
        }
        return depths;
    }

    /**
     * Expands the persisted DAG into the same visual tree shape used by AE's crafting tree.
     * A shared dependency intentionally gets a separate occurrence below every consumer.
     */
    static Tree tree(Set<Long> nodeIds, Map<Long, List<Long>> dependencies,
          List<Long> rootDependencies) {
        List<TreeNode> nodes = new ArrayList<>();
        TreeNode root = new TreeNode(Long.MIN_VALUE, true, 0);
        nodes.add(root);

        List<Long> roots = filteredDependencies(rootDependencies, nodeIds);
        Set<Long> activePath = new HashSet<>();
        Deque<ExpansionFrame> stack = new ArrayDeque<>();
        stack.push(new ExpansionFrame(root, roots, false));
        boolean truncated = false;
        while (!stack.isEmpty()) {
            ExpansionFrame frame = stack.peek();
            if (frame.nextDependency < frame.dependencies.size()) {
                if (nodes.size() >= MAX_TREE_OCCURRENCES) {
                    truncated = true;
                    frame.nextDependency = frame.dependencies.size();
                    continue;
                }
                long dependency = frame.dependencies.get(frame.nextDependency++);
                TreeNode child = new TreeNode(dependency, false, frame.node.depth + 1);
                frame.node.children.add(child);
                nodes.add(child);

                boolean addedToPath = activePath.add(dependency);
                List<Long> children = addedToPath ? filteredDependencies(
                      dependencies.getOrDefault(dependency, Collections.emptyList()), nodeIds) :
                      Collections.emptyList();
                stack.push(new ExpansionFrame(child, children, addedToPath));
            } else {
                stack.pop();
                if (frame.addedToPath) activePath.remove(frame.node.nodeId);
            }
        }

        // AE aligns every depth covered by a subtree to a common right edge. With QIO's
        // node-only rows this is equivalent to the sum of the direct child subtree widths.
        for (int index = nodes.size() - 1; index >= 0; index--) {
            TreeNode node = nodes.get(index);
            int ownWidth = NODE_TOTAL_WIDTH + (node.root ? ROOT_MARGIN_RIGHT : 0);
            int childWidth = 0;
            for (TreeNode child : node.children) {
                childWidth = saturatedAdd(childWidth, child.subtreeWidth);
            }
            node.subtreeWidth = Math.max(ownWidth, childWidth);
        }

        int totalHeight = 0;
        int maxDepth = 0;
        for (TreeNode node : nodes) {
            if (!node.children.isEmpty()) {
                int childX = node.subtreeX;
                for (TreeNode child : node.children) {
                    child.subtreeX = childX;
                    childX = saturatedAdd(childX, child.subtreeWidth);
                }
            }
            node.x = saturatedAdd(node.subtreeX, NODE_MARGIN_LEFT);
            node.y = ROOT_MARGIN_TOP + LINE_HEIGHT + node.depth * NODE_TOTAL_HEIGHT;
            maxDepth = Math.max(maxDepth, node.depth);
        }
        if (!nodes.isEmpty()) {
            totalHeight = ROOT_MARGIN_TOP + NODE_TOTAL_HEIGHT +
                  maxDepth * NODE_TOTAL_HEIGHT;
        }
        return new Tree(Collections.unmodifiableList(nodes), root.subtreeWidth, totalHeight,
              truncated);
    }

    private static List<Long> filteredDependencies(List<Long> dependencies,
          Set<Long> nodeIds) {
        if (dependencies.isEmpty()) return Collections.emptyList();
        LinkedHashSet<Long> filtered = new LinkedHashSet<>();
        for (Long dependency : dependencies) {
            if (dependency != null && nodeIds.contains(dependency)) filtered.add(dependency);
        }
        return filtered.isEmpty() ? Collections.emptyList() : new ArrayList<>(filtered);
    }

    static List<MaterialLeaf> externalInputLeaves(
          QIOCraftingMonitorPlanEntry entry, Set<Long> dependencyIds,
          Map<Long, QIOCraftingMonitorPlanEntry> entries) {
        Set<PortableResourceDescriptor> produced = new LinkedHashSet<>();
        for (long dependencyId : dependencyIds) {
            QIOCraftingMonitorPlanEntry dependency = entries.get(dependencyId);
            if (dependency == null) continue;
            if (dependency.getStep() != null) {
                produced.addAll(dependency.getStep().getGuaranteedOutputs().keySet());
            } else {
                dependency.getCycle().getNetPerRound().forEach((resource, amount) -> {
                    if (amount > 0) produced.add(resource);
                });
            }
        }

        List<MaterialLeaf> required = new ArrayList<>();
        if (entry.getStep() != null) {
            QIOPlanStep step = entry.getStep();
            step.getFixedInputs().forEach((resource, amount) -> {
                if (!produced.contains(resource)) {
                    required.add(MaterialLeaf.exact(resource,
                          saturatedMultiply(amount, step.getOperations())));
                }
            });
            for (QIOCandidateInputGroup group : step.getCandidateInputs()) {
                long externalUnitsPerOperation = 0;
                for (Map.Entry<PortableResourceDescriptor, Long> planned :
                      group.getPlannedInputs().entrySet()) {
                    if (produced.contains(planned.getKey())) continue;
                    QIOCandidateOption option = optionFor(group, planned.getKey());
                    if (option != null) {
                        externalUnitsPerOperation = saturatedAdd(externalUnitsPerOperation,
                              planned.getValue() / option.getAmountPerUnit());
                    }
                }
                long requiredUnits = saturatedMultiply(externalUnitsPerOperation,
                      step.getOperations());
                if (requiredUnits > 0) {
                    mergeCandidateLeaf(required, group.getOptions(), requiredUnits);
                }
            }
        } else {
            entry.getCycle().getSeedRequirements().forEach((resource, amount) -> {
                if (!produced.contains(resource)) required.add(MaterialLeaf.exact(resource,
                      amount));
            });
        }
        return required.isEmpty() ? Collections.emptyList() :
              Collections.unmodifiableList(required);
    }

    private static void mergeCandidateLeaf(List<MaterialLeaf> required,
          List<QIOCandidateOption> options, long requiredUnits) {
        for (int index = 0; index < required.size(); index++) {
            MaterialLeaf leaf = required.get(index);
            if (sameDisplayedOptions(leaf.getOptions(), options)) {
                required.set(index, MaterialLeaf.candidate(options,
                      saturatedAdd(leaf.getRequiredUnits(), requiredUnits)));
                return;
            }
        }
        required.add(MaterialLeaf.candidate(options, requiredUnits));
    }

    private static boolean sameDisplayedOptions(List<QIOCandidateOption> left,
          List<QIOCandidateOption> right) {
        if (left.isEmpty() || left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) {
            QIOCandidateOption first = left.get(index);
            QIOCandidateOption second = right.get(index);
            if (!first.getResource().equals(second.getResource()) ||
                first.getAmountPerUnit() != second.getAmountPerUnit() ||
                first.isVirtualFluid() != second.isVirtualFluid()) {
                return false;
            }
        }
        return true;
    }

    private static QIOCandidateOption optionFor(QIOCandidateInputGroup group,
          PortableResourceDescriptor resource) {
        for (QIOCandidateOption option : group.getOptions()) {
            if (option.getResource().equals(resource)) return option;
        }
        return null;
    }

    static QIOCandidateOption cyclingOption(List<QIOCandidateOption> options,
          long animationTick) {
        if (options.isEmpty()) return null;
        long phase = Math.max(0, animationTick) / CANDIDATE_CYCLE_TICKS;
        return options.get((int) (phase % options.size()));
    }

    static long displayedAmount(long exactAmount, long requiredUnits,
          List<QIOCandidateOption> options, long animationTick) {
        QIOCandidateOption option = cyclingOption(options, animationTick);
        return option == null ? exactAmount : saturatedMultiply(requiredUnits,
              option.getAmountPerUnit());
    }

    static long candidateMissingUnits(List<QIOCandidateOption> options,
          Map<PortableResourceDescriptor, Long> missingAmounts) {
        long missingUnits = 0;
        Set<PortableResourceDescriptor> counted = new LinkedHashSet<>();
        for (QIOCandidateOption option : options) {
            if (!counted.add(option.getResource())) continue;
            long amount = missingAmounts.getOrDefault(option.getResource(), 0L);
            if (amount <= 0) continue;
            long units = amount / option.getAmountPerUnit() +
                  (amount % option.getAmountPerUnit() == 0 ? 0 : 1);
            missingUnits = saturatedAdd(missingUnits, units);
        }
        return missingUnits;
    }

    private static long saturatedMultiply(long left, long right) {
        if (left <= 0 || right <= 0) return 0;
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static long saturatedAdd(long left, long right) {
        if (left <= 0) return Math.max(0, right);
        if (right <= 0) return left;
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static int saturatedAdd(int left, int right) {
        return left > Integer.MAX_VALUE - right ? Integer.MAX_VALUE : left + right;
    }

    /** Keeps a panned content rectangle intersecting its viewport instead of losing the whole tree. */
    static float clampPanOffset(float offset, float contentStart, float contentEnd,
          float zoom, int viewportSize, int margin) {
        float lower = margin - contentEnd * zoom;
        float upper = viewportSize - margin - contentStart * zoom;
        if (lower > upper) {
            return (lower + upper) / 2;
        }
        return Math.max(lower, Math.min(upper, offset));
    }

    static final class Tree {
        private final List<TreeNode> nodes;
        private final int width;
        private final int height;
        private final boolean truncated;

        private Tree(List<TreeNode> nodes, int width, int height, boolean truncated) {
            this.nodes = nodes;
            this.width = width;
            this.height = height;
            this.truncated = truncated;
        }

        List<TreeNode> getNodes() {
            return nodes;
        }

        TreeNode getRoot() {
            return nodes.get(0);
        }

        int getWidth() {
            return width;
        }

        int getHeight() {
            return height;
        }

        boolean isTruncated() {
            return truncated;
        }
    }

    static final class MaterialLeaf {
        private final PortableResourceDescriptor resource;
        private final long exactAmount;
        private final long requiredUnits;
        private final List<QIOCandidateOption> options;

        private MaterialLeaf(PortableResourceDescriptor resource, long exactAmount,
              long requiredUnits, List<QIOCandidateOption> options) {
            this.resource = resource;
            this.exactAmount = exactAmount;
            this.requiredUnits = requiredUnits;
            this.options = options;
        }

        private static MaterialLeaf exact(PortableResourceDescriptor resource, long amount) {
            return new MaterialLeaf(resource, amount, 0, Collections.emptyList());
        }

        private static MaterialLeaf candidate(List<QIOCandidateOption> options,
              long requiredUnits) {
            List<QIOCandidateOption> checked = Collections.unmodifiableList(
                  new ArrayList<>(options));
            return new MaterialLeaf(checked.get(0).getResource(), 0, requiredUnits, checked);
        }

        PortableResourceDescriptor getResource() { return resource; }
        long getExactAmount() { return exactAmount; }
        long getRequiredUnits() { return requiredUnits; }
        List<QIOCandidateOption> getOptions() { return options; }
    }

    static final class TreeNode {
        private final long nodeId;
        private final boolean root;
        private final int depth;
        private final List<TreeNode> children = new ArrayList<>();
        private int subtreeX;
        private int subtreeWidth;
        private int x;
        private int y;

        private TreeNode(long nodeId, boolean root, int depth) {
            this.nodeId = nodeId;
            this.root = root;
            this.depth = depth;
        }

        long getNodeId() {
            return nodeId;
        }

        boolean isRoot() {
            return root;
        }

        int getDepth() {
            return depth;
        }

        List<TreeNode> getChildren() {
            return children;
        }

        int getX() {
            return x;
        }

        int getY() {
            return y;
        }

        int getSubtreeWidth() {
            return subtreeWidth;
        }
    }

    private static final class ExpansionFrame {
        private final TreeNode node;
        private final List<Long> dependencies;
        private final boolean addedToPath;
        private int nextDependency;

        private ExpansionFrame(TreeNode node, List<Long> dependencies,
              boolean addedToPath) {
            this.node = node;
            this.dependencies = dependencies;
            this.addedToPath = addedToPath;
        }
    }
}
