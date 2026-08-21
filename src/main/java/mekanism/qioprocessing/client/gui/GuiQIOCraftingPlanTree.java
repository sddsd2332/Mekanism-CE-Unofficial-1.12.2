package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.jei.interfaces.IJEIIngredientHelper;
import mekanism.common.util.UnitDisplayUtils;
import mekanism.common.util.text.TextUtils;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.plan.QIOCandidateOption;
import mekanism.qioprocessing.common.content.plan.QIOCyclePlanNode;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorEntry;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorPlanEntry;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorRuntimeNode;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** AE-style expanded tree projection of one persisted plan DAG with condensed cycles. */
/**
 * QIO 处理模块中的 GuiQIOCraftingPlanTree 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class GuiQIOCraftingPlanTree extends GuiElement implements IJEIIngredientHelper {

    public enum Filter {
        ALL,
        ACTIVE,
        BLOCKED,
        COMPLETE
    }

    private static final long ROOT_NODE_ID = Long.MIN_VALUE;
    private static final int LOGICAL_NODE_WIDTH = QIOCraftingPlanLayout.NODE_WIDTH;
    private static final int LOGICAL_NODE_HEIGHT = QIOCraftingPlanLayout.NODE_HEIGHT;
    private static final int LINE_WIDTH = 1;
    private static final int LINE_HEIGHT = QIOCraftingPlanLayout.LINE_HEIGHT;
    private static final int LINE_RENDER_OFFSET =
          (LOGICAL_NODE_WIDTH - LINE_WIDTH * 2) / 2;
    private static final int GL_INTEGER_QUERY_BUFFER_SIZE = 16;
    private static final int PAN_MARGIN = Math.max(QIOCraftingPlanLayout.NODE_TOTAL_WIDTH,
          QIOCraftingPlanLayout.NODE_TOTAL_HEIGHT);
    private static final double DRAG_THRESHOLD = 2;

    private final Supplier<List<QIOCraftingMonitorPlanEntry>> planSupplier;
    private final Supplier<Map<Long, QIOCraftingMonitorRuntimeNode>> runtimeSupplier;
    private final Supplier<Map<PortableResourceDescriptor, Long>> missingMaterialsSupplier;
    private final Supplier<Set<Long>> errorNodeIdsSupplier;
    private final Supplier<Set<PortableResourceDescriptor>> errorResourcesSupplier;
    private final BooleanSupplier rootErrorSupplier;
    private final Supplier<QIOCraftingMonitorEntry> jobSupplier;
    private final LongSupplier planGenerationSupplier;
    private final LongSupplier runtimeGenerationSupplier;
    private final BooleanSupplier planReadySupplier;
    private final boolean showRuntimeStatus;
    /** Occurrences are deliberately separate: one plan node may appear under many parents. */
    private final List<LayoutNode> layout = new ArrayList<>();
    private final List<List<LayoutNode>> layoutRows = new ArrayList<>();
    private final List<Long> rootDependencies = new ArrayList<>();
    private final List<LayoutNode> rootChildren = new ArrayList<>();

    private Filter filter = Filter.ALL;
    private float zoom = 1;
    private float panX = 4;
    private float panY = 4;
    private int rootX;
    private int rootY;
    private boolean panning;
    private boolean dragMoved;
    private double dragStartX;
    private double dragStartY;
    private double lastDragX;
    private double lastDragY;
    @Nullable private Long pressedNodeId;
    @Nullable private Long selectedNodeId;
    @Nullable private Long hoveredNodeId;
    @Nullable private LayoutNode hoveredNode;
    @Nullable private QIOCraftingPlanLayout.Tree treeLayout;
    private long lastPlanGeneration = Long.MIN_VALUE;
    private long lastRuntimeGeneration = Long.MIN_VALUE;
    private long animationTick;
    private boolean lastPlanReady;
    private final Map<Long, NodeStatus> statusCache = new HashMap<>();

    public GuiQIOCraftingPlanTree(IGuiWrapper gui, int x, int y, int width, int height,
          Supplier<List<QIOCraftingMonitorPlanEntry>> planSupplier,
          Supplier<Map<Long, QIOCraftingMonitorRuntimeNode>> runtimeSupplier,
          Supplier<Map<PortableResourceDescriptor, Long>> missingMaterialsSupplier,
          Supplier<QIOCraftingMonitorEntry> jobSupplier,
          LongSupplier planGenerationSupplier, BooleanSupplier planReadySupplier) {
        this(gui, x, y, width, height, planSupplier, runtimeSupplier,
              missingMaterialsSupplier, Collections::emptySet, Collections::emptySet,
              () -> false, jobSupplier, planGenerationSupplier, () -> Long.MIN_VALUE,
              planReadySupplier, true);
    }

    public GuiQIOCraftingPlanTree(IGuiWrapper gui, int x, int y, int width, int height,
          Supplier<List<QIOCraftingMonitorPlanEntry>> planSupplier,
          Supplier<Map<Long, QIOCraftingMonitorRuntimeNode>> runtimeSupplier,
          Supplier<Map<PortableResourceDescriptor, Long>> missingMaterialsSupplier,
          Supplier<Set<Long>> errorNodeIdsSupplier,
          Supplier<Set<PortableResourceDescriptor>> errorResourcesSupplier,
          BooleanSupplier rootErrorSupplier, Supplier<QIOCraftingMonitorEntry> jobSupplier,
          LongSupplier planGenerationSupplier, BooleanSupplier planReadySupplier) {
        this(gui, x, y, width, height, planSupplier, runtimeSupplier,
              missingMaterialsSupplier, errorNodeIdsSupplier, errorResourcesSupplier,
              rootErrorSupplier, jobSupplier, planGenerationSupplier, () -> Long.MIN_VALUE,
              planReadySupplier, false);
    }

    public GuiQIOCraftingPlanTree(IGuiWrapper gui, int x, int y, int width, int height,
          Supplier<List<QIOCraftingMonitorPlanEntry>> planSupplier,
          Supplier<Map<Long, QIOCraftingMonitorRuntimeNode>> runtimeSupplier,
          Supplier<Map<PortableResourceDescriptor, Long>> missingMaterialsSupplier,
          Supplier<Set<Long>> errorNodeIdsSupplier,
          Supplier<Set<PortableResourceDescriptor>> errorResourcesSupplier,
          BooleanSupplier rootErrorSupplier, Supplier<QIOCraftingMonitorEntry> jobSupplier,
          LongSupplier planGenerationSupplier, LongSupplier runtimeGenerationSupplier,
          BooleanSupplier planReadySupplier, boolean showRuntimeStatus) {
        super(gui, x, y, width, height);
        this.planSupplier = planSupplier;
        this.runtimeSupplier = runtimeSupplier;
        this.missingMaterialsSupplier = missingMaterialsSupplier;
        this.errorNodeIdsSupplier = errorNodeIdsSupplier;
        this.errorResourcesSupplier = errorResourcesSupplier;
        this.rootErrorSupplier = rootErrorSupplier;
        this.jobSupplier = jobSupplier;
        this.planGenerationSupplier = planGenerationSupplier;
        this.runtimeGenerationSupplier = runtimeGenerationSupplier;
        this.planReadySupplier = planReadySupplier;
        this.showRuntimeStatus = showRuntimeStatus;
        active = true;
    }

    public void setFilter(Filter filter) {
        this.filter = filter == null ? Filter.ALL : filter;
    }

    public Filter getFilter() {
        return filter;
    }

    public float getZoom() {
        return zoom;
    }

    public void zoomIn() {
        setZoom(zoom + 0.1F);
    }

    public void zoomOut() {
        setZoom(zoom - 0.1F);
    }

    public void resetView() {
        zoom = 1;
        panX = 4;
        panY = 4;
        clampPan();
    }

    @Override
    public boolean hasPersistentData() {
        return true;
    }

    @Override
    public void syncFrom(GuiElement element) {
        super.syncFrom(element);
        GuiQIOCraftingPlanTree old = (GuiQIOCraftingPlanTree) element;
        filter = old.filter;
        zoom = old.zoom;
        panX = old.panX;
        panY = old.panY;
        animationTick = old.animationTick;
        selectedNodeId = old.selectedNodeId;
    }

    @Nullable
    public Long getSelectedNodeId() {
        return selectedNodeId == null || selectedNodeId < 0 ? null : selectedNodeId;
    }

    @Override
    public void tick() {
        super.tick();
        animationTick = animationTick == Long.MAX_VALUE ? 0 : animationTick + 1;
        long generation = planGenerationSupplier.getAsLong();
        long runtimeGeneration = runtimeGenerationSupplier.getAsLong();
        boolean ready = planReadySupplier.getAsBoolean();
        if (runtimeGeneration != lastRuntimeGeneration) {
            lastRuntimeGeneration = runtimeGeneration;
            statusCache.clear();
        }
        if (generation != lastPlanGeneration || ready != lastPlanReady) {
            lastPlanGeneration = generation;
            lastPlanReady = ready;
            rebuild();
        }
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        GuiUtils.fill(relativeX, relativeY, relativeX + width, relativeY + height, 0xFF171B1E);
        GuiUtils.fill(relativeX, relativeY, relativeX + width, relativeY + 1, 0xFF566168);
        GuiUtils.fill(relativeX, relativeY + height - 1, relativeX + width,
              relativeY + height, 0xFF566168);
        GuiUtils.fill(relativeX, relativeY, relativeX + 1, relativeY + height, 0xFF566168);
        GuiUtils.fill(relativeX + width - 1, relativeY, relativeX + width,
              relativeY + height, 0xFF566168);

        hoveredNodeId = null;
        hoveredNode = null;
        QIOCraftingMonitorEntry job = jobSupplier.get();
        if (job == null || !planReadySupplier.getAsBoolean()) return;

        int localMouseX = mouseX - getGuiLeft();
        int localMouseY = mouseY - getGuiTop();
        Map<Long, QIOCraftingMonitorRuntimeNode> runtimes = runtimeSupplier.get();
        ScissorState scissor = beginTreeScissor();
        try {
            drawTree(job, runtimes, localMouseX, localMouseY);
        } finally {
            scissor.restore();
        }
    }

    private void drawTree(QIOCraftingMonitorEntry job,
          Map<Long, QIOCraftingMonitorRuntimeNode> runtimes,
          int localMouseX, int localMouseY) {
        boolean mouseInside = localMouseX >= relativeX && localMouseX < relativeX + width &&
              localMouseY >= relativeY && localMouseY < relativeY + height;
        drawEdges(runtimes);
        int firstDepth = firstVisibleDepth();
        int lastDepth = lastVisibleDepth();
        float logicalLeft = logicalVisibleLeft();
        float logicalRight = logicalVisibleRight();
        for (int depth = firstDepth; depth <= lastDepth; depth++) {
            List<LayoutNode> row = layoutRows.get(depth);
            for (int index = lowerBoundX(row, logicalLeft); index < row.size(); index++) {
                LayoutNode node = row.get(index);
                if (node.x > logicalRight) break;
                Rect rect = rect(node);
                if (!rect.intersects(relativeX, relativeY, width, height)) continue;
                if (mouseInside && rect.contains(localMouseX, localMouseY)) {
                    hoveredNodeId = node.nodeId;
                    hoveredNode = node;
                }
                drawNode(node, rect, runtimes.get(node.nodeId),
                      node.nodeId == (selectedNodeId == null ? ROOT_NODE_ID : selectedNodeId));
            }
        }
        Rect root = rootRect();
        if (root.intersects(relativeX, relativeY, width, height)) {
            if (mouseInside && root.contains(localMouseX, localMouseY)) {
                hoveredNodeId = ROOT_NODE_ID;
                hoveredNode = null;
            }
            drawRoot(job, root, selectedNodeId != null && selectedNodeId == ROOT_NODE_ID);
        }
    }

    private ScissorState beginTreeScissor() {
        boolean enabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        IntBuffer box = BufferUtils.createIntBuffer(GL_INTEGER_QUERY_BUFFER_SIZE);
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, box);
        int previousX = box.get(0);
        int previousY = box.get(1);
        int previousWidth = box.get(2);
        int previousHeight = box.get(3);

        Minecraft minecraft = Minecraft.getMinecraft();
        double scaleX = minecraft.displayWidth / (double) minecraft.currentScreen.width;
        double scaleY = minecraft.displayHeight / (double) minecraft.currentScreen.height;
        int scissorX = (int) Math.floor((getGuiLeft() + relativeX + 1) * scaleX);
        int scissorY = (int) Math.floor(minecraft.displayHeight -
              (getGuiTop() + relativeY + height - 1) * scaleY);
        int scissorWidth = Math.max(0, (int) Math.ceil((width - 2) * scaleX));
        int scissorHeight = Math.max(0, (int) Math.ceil((height - 2) * scaleY));
        if (enabled) {
            int right = Math.min(scissorX + scissorWidth, previousX + previousWidth);
            int top = Math.min(scissorY + scissorHeight, previousY + previousHeight);
            scissorX = Math.max(scissorX, previousX);
            scissorY = Math.max(scissorY, previousY);
            scissorWidth = Math.max(0, right - scissorX);
            scissorHeight = Math.max(0, top - scissorY);
        }
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
        return new ScissorState(enabled, previousX, previousY, previousWidth,
              previousHeight);
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        String message = null;
        if (jobSupplier.get() == null) {
            message = translation("gui.mekanismqioprocessing.monitor_select_job");
        } else if (!planReadySupplier.getAsBoolean()) {
            message = translation("gui.mekanismqioprocessing.monitor_plan_loading");
        }
        if (message != null) {
            String trimmed = getFont().trimStringToWidth(message, Math.max(12, width - 12));
            getFont().drawString(trimmed,
                  relativeX + Math.max(4, (width - getFont().getStringWidth(trimmed)) / 2),
                  relativeY + Math.max(4, (height - getFont().FONT_HEIGHT) / 2), 0xFFB7C2C8);
        }
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        Long nodeId = hoveredNodeId;
        if (nodeId == null) return;
        if (nodeId == ROOT_NODE_ID) {
            renderRootTooltip(mouseX, mouseY);
            return;
        }
        LayoutNode node = hoveredNode;
        if (node == null || node.nodeId != nodeId) {
            node = findFirstNode(nodeId);
        }
        if (node == null) return;
        if (node.requiredMaterial) {
            PortableResourceDescriptor displayedResource = displayedResource(node);
            long displayedAmount = displayedAmount(node);
            List<String> extra = new ArrayList<>();
            extra.add(materialAmountTranslation("monitor_required_material", displayedResource,
                  displayedAmount));
            long missing = missingAmount(node);
            if (missing > 0) {
                extra.add(materialAmountTranslation("monitor_waiting_material",
                      displayedResource, missing));
            }
            if (isPlanningError(node)) {
                extra.add(translation("gui.mekanismqioprocessing.order_analysis_error_node"));
            }
            extra.add(translation("gui.mekanismqioprocessing.monitor_exact_resource",
                  QIOGuiResourceRenderer.identity(displayedResource)));
            renderResourceTooltip(displayedResource, extra, mouseX, mouseY);
            return;
        }
        QIOCraftingMonitorRuntimeNode runtime = runtimeSupplier.get().get(nodeId);
        List<String> extra = node.entry.getStep() == null ?
              cycleTooltip(node.entry.getCycle(), runtime) :
              stepTooltip(node.entry.getStep(), runtime);
        if (isPlanningError(node)) {
            extra.add(translation("gui.mekanismqioprocessing.order_analysis_error_node"));
        }
        if (showRuntimeStatus) {
            extra.add(translation("gui.mekanismqioprocessing.monitor_node_status_" +
                  nodeStatus(node).name().toLowerCase(java.util.Locale.ROOT)));
        }
        renderResourceTooltip(node.resource, extra, mouseX, mouseY);
    }

    @Nullable
    @Override
    public Object getIngredient(double mouseX, double mouseY) {
        Long nodeId = hoveredNodeId;
        if (nodeId == null) return null;
        if (nodeId == ROOT_NODE_ID) {
            QIOCraftingMonitorEntry job = jobSupplier.get();
            return job == null ? null : QIOGuiResourceRenderer.ingredient(job.getRootResource());
        }
        LayoutNode node = hoveredNode;
        if (node == null || node.nodeId != nodeId) {
            node = findFirstNode(nodeId);
        }
        return node == null ? null :
              QIOGuiResourceRenderer.ingredient(displayedResource(node));
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        int localX = (int) mouseX - getGuiLeft();
        int localY = (int) mouseY - getGuiTop();
        pressedNodeId = hit(localX, localY);
        panning = true;
        dragMoved = false;
        dragStartX = mouseX;
        dragStartY = mouseY;
        lastDragX = mouseX;
        lastDragY = mouseY;
    }

    @Override
    public void onDrag(double mouseX, double mouseY, double oldMouseX, double oldMouseY) {
        if (!panning) return;
        double deltaX = mouseX - lastDragX;
        double deltaY = mouseY - lastDragY;
        lastDragX = mouseX;
        lastDragY = mouseY;
        if (!dragMoved && Math.abs(mouseX - dragStartX) < DRAG_THRESHOLD &&
              Math.abs(mouseY - dragStartY) < DRAG_THRESHOLD) return;
        dragMoved = true;
        panX += (float) deltaX;
        panY += (float) deltaY;
        clampPan();
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        if (panning && !dragMoved) {
            selectedNodeId = pressedNodeId;
        }
        panning = false;
        dragMoved = false;
        pressedNodeId = null;
        super.onRelease(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isMouseOver(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, delta);
        float oldZoom = zoom;
        float newZoom = clampZoom(zoom + (delta > 0 ? 0.1F : -0.1F));
        if (newZoom == oldZoom) return true;
        float localMouseX = (float) mouseX - getGuiLeft() - relativeX;
        float localMouseY = (float) mouseY - getGuiTop() - relativeY;
        float treeMouseX = (localMouseX - panX) / oldZoom;
        float treeMouseY = (localMouseY - panY) / oldZoom;
        zoom = newZoom;
        panX = localMouseX - treeMouseX * zoom;
        panY = localMouseY - treeMouseY * zoom;
        clampPan();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case Keyboard.KEY_LEFT -> panX += 12;
            case Keyboard.KEY_RIGHT -> panX -= 12;
            case Keyboard.KEY_UP -> panY += 12;
            case Keyboard.KEY_DOWN -> panY -= 12;
            case Keyboard.KEY_ADD, Keyboard.KEY_EQUALS -> zoomIn();
            case Keyboard.KEY_SUBTRACT, Keyboard.KEY_MINUS -> zoomOut();
            case Keyboard.KEY_HOME -> resetView();
            default -> { return super.keyPressed(keyCode, scanCode, modifiers); }
        }
        return true;
    }

    private void rebuild() {
        layout.clear();
        layoutRows.clear();
        rootDependencies.clear();
        rootChildren.clear();
        treeLayout = null;
        selectedNodeId = null;
        if (!planReadySupplier.getAsBoolean()) return;

        List<QIOCraftingMonitorPlanEntry> source = new ArrayList<>(planSupplier.get());
        Map<Long, QIOCraftingMonitorPlanEntry> byId = new LinkedHashMap<>();
        Map<Long, Long> cycleByMember = new HashMap<>();
        for (QIOCraftingMonitorPlanEntry entry : source) {
            byId.put(entry.getNodeId(), entry);
            if (entry.getCycle() != null) {
                for (long member : entry.getCycle().getMemberQuotas().keySet()) {
                    cycleByMember.put(member, entry.getNodeId());
                }
            }
        }

        List<QIOCraftingMonitorPlanEntry> entries = new ArrayList<>();
        Set<Long> displayIds = new LinkedHashSet<>();
        for (QIOCraftingMonitorPlanEntry entry : source) {
            if (entry.getCycle() == null && cycleByMember.containsKey(entry.getNodeId())) continue;
            entries.add(entry);
            displayIds.add(entry.getNodeId());
        }

        Map<Long, QIOCraftingMonitorPlanEntry> displayEntries = new LinkedHashMap<>();
        for (QIOCraftingMonitorPlanEntry entry : entries) {
            displayEntries.put(entry.getNodeId(), entry);
        }

        Map<Long, List<Long>> dependencies = new LinkedHashMap<>();
        Map<Long, RequiredMaterial> requiredMaterials = new LinkedHashMap<>();
        long nextRequiredMaterialId = -1;
        for (QIOCraftingMonitorPlanEntry entry : entries) {
            LinkedHashSet<Long> deps = new LinkedHashSet<>();
            if (entry.getStep() != null) {
                for (long dependency : entry.getStep().getDependencies()) {
                    long displayDependency = cycleByMember.getOrDefault(dependency, dependency);
                    if (displayDependency != entry.getNodeId() &&
                        displayIds.contains(displayDependency)) deps.add(displayDependency);
                }
            } else {
                Set<Long> members = entry.getCycle().getMemberQuotas().keySet();
                for (long member : members) {
                    QIOCraftingMonitorPlanEntry memberEntry = byId.get(member);
                    if (memberEntry == null || memberEntry.getStep() == null) continue;
                    for (long dependency : memberEntry.getStep().getDependencies()) {
                        if (members.contains(dependency)) continue;
                        long displayDependency = cycleByMember.getOrDefault(dependency, dependency);
                        if (displayDependency != entry.getNodeId() &&
                            displayIds.contains(displayDependency)) deps.add(displayDependency);
                    }
                }
            }
            List<Long> displayedDependencies = new ArrayList<>(deps);
            for (QIOCraftingPlanLayout.MaterialLeaf required :
                  QIOCraftingPlanLayout.externalInputLeaves(entry, deps, displayEntries)) {
                long materialId = nextRequiredMaterialId--;
                displayIds.add(materialId);
                displayedDependencies.add(materialId);
                requiredMaterials.put(materialId, new RequiredMaterial(required));
                dependencies.put(materialId, Collections.emptyList());
            }
            dependencies.put(entry.getNodeId(), displayedDependencies);
        }

        Set<Long> referencedDependencies = new LinkedHashSet<>();
        dependencies.values().forEach(referencedDependencies::addAll);
        for (QIOCraftingMonitorPlanEntry entry : entries) {
            if (!referencedDependencies.contains(entry.getNodeId())) {
                rootDependencies.add(entry.getNodeId());
            }
        }
        if (rootDependencies.isEmpty() && !entries.isEmpty()) {
            Map<Long, Integer> depths = QIOCraftingPlanLayout.depths(displayIds, dependencies);
            int maxDepth = depths.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            for (QIOCraftingMonitorPlanEntry entry : entries) {
                if (depths.getOrDefault(entry.getNodeId(), 0) == maxDepth) {
                    rootDependencies.add(entry.getNodeId());
                }
            }
        }

        PortableResourceDescriptor rootResource = jobSupplier.get() == null ? null :
              jobSupplier.get().getRootResource();
        treeLayout = QIOCraftingPlanLayout.tree(displayIds, dependencies, rootDependencies);
        Map<QIOCraftingPlanLayout.TreeNode, LayoutNode> occurrences =
              new IdentityHashMap<>();
        for (QIOCraftingPlanLayout.TreeNode treeNode : treeLayout.getNodes()) {
            if (treeNode.isRoot()) {
                rootX = treeNode.getX();
                rootY = treeNode.getY();
                continue;
            }
            QIOCraftingMonitorPlanEntry entry = displayEntries.get(treeNode.getNodeId());
            RequiredMaterial required = requiredMaterials.get(treeNode.getNodeId());
            if (entry == null && required == null) continue;
            PortableResourceDescriptor resource = entry == null ? required.resource :
                  primaryResource(entry, rootResource);
            LayoutNode node = entry == null ? new LayoutNode(treeNode.getNodeId(),
                   treeNode.getX(), treeNode.getY(), treeNode.getDepth(), resource,
                  required.amount, required.requiredUnits, required.candidateOptions) :
                  new LayoutNode(entry,
                  treeNode.getX(), treeNode.getY(),
                  treeNode.getDepth(), resource, totalOutput(entry, resource));
            layout.add(node);
            while (layoutRows.size() <= node.depth) layoutRows.add(new ArrayList<>());
            layoutRows.get(node.depth).add(node);
            occurrences.put(treeNode, node);
        }
        for (QIOCraftingPlanLayout.TreeNode treeNode : treeLayout.getNodes()) {
            LayoutNode parent = occurrences.get(treeNode);
            List<LayoutNode> children = treeNode.isRoot() ? rootChildren :
                  parent == null ? Collections.emptyList() : parent.children;
            for (QIOCraftingPlanLayout.TreeNode treeChild : treeNode.getChildren()) {
                LayoutNode child = occurrences.get(treeChild);
                if (child != null) children.add(child);
            }
        }
        for (List<LayoutNode> row : layoutRows) {
            row.sort((left, right) -> Integer.compare(left.x, right.x));
        }
        rootChildren.sort((left, right) -> Integer.compare(left.x, right.x));
        for (LayoutNode node : layout) {
            node.children.sort((left, right) -> Integer.compare(left.x, right.x));
            node.updateLinkBounds();
        }
        clampPan();
    }

    private void drawEdges(Map<Long, QIOCraftingMonitorRuntimeNode> runtimes) {
        QIOCraftingMonitorEntry job = jobSupplier.get();
        if (job == null) return;
        Rect root = rootRect();
        drawTreeLinks(root, rootChildren, null, runtimes);
        float logicalLeft = logicalVisibleLeft();
        float logicalRight = logicalVisibleRight();
        int firstDepth = Math.max(1, firstVisibleDepth() - 1);
        int lastDepth = Math.min(layoutRows.size() - 1, lastVisibleDepth());
        for (int depth = firstDepth; depth <= lastDepth; depth++) {
            List<LayoutNode> row = layoutRows.get(depth);
            for (int index = lowerBoundLinkMax(row, logicalLeft); index < row.size(); index++) {
                LayoutNode node = row.get(index);
                if (node.linkMinX > logicalRight) break;
                if (node.children.isEmpty()) continue;
                drawTreeLinks(rect(node), node.children,
                      runtimes.get(node.nodeId), runtimes);
            }
        }
    }

    private int edgeColor(@Nullable QIOCraftingMonitorRuntimeNode first,
          @Nullable QIOCraftingMonitorRuntimeNode second) {
        if (filter != Filter.ALL && !matches(first) && !matches(second)) return 0xFF536069;
        return isBlocked(first) || isBlocked(second) ? 0xFFEE6363 : 0xFFF2F2F2;
    }

    private int edgeShadowColor(@Nullable QIOCraftingMonitorRuntimeNode first,
          @Nullable QIOCraftingMonitorRuntimeNode second) {
        if (filter != Filter.ALL && !matches(first) && !matches(second)) return 0xFF2F373C;
        return isBlocked(first) || isBlocked(second) ? 0xFF8B3A3A : 0xFF4D4D67;
    }

    private void drawTreeLinks(Rect parent, List<LayoutNode> children,
          @Nullable QIOCraftingMonitorRuntimeNode parentRuntime,
          Map<Long, QIOCraftingMonitorRuntimeNode> runtimes) {
        if (children.isEmpty()) return;
        int parentAnchorX = parent.x + Math.round(LINE_RENDER_OFFSET * zoom);
        int lineTop = parent.bottom();
        int junctionY = lineTop + Math.max(1, Math.round(LINE_HEIGHT * zoom));
        Rect firstChildRect = rect(children.get(0));
        Rect lastChildRect = rect(children.get(children.size() - 1));
        int minChildAnchor = firstChildRect.x + Math.round(LINE_RENDER_OFFSET * zoom);
        int maxChildAnchor = lastChildRect.x + Math.round(LINE_RENDER_OFFSET * zoom);
        QIOCraftingMonitorRuntimeNode firstChildRuntime =
              runtimes.get(children.get(0).nodeId);
        int color = edgeColor(parentRuntime, firstChildRuntime);
        int shadow = edgeShadowColor(parentRuntime, firstChildRuntime);
        drawTreeVertical(parentAnchorX, lineTop, junctionY, color, shadow);
        drawTreeHorizontal(Math.min(parentAnchorX, minChildAnchor), junctionY,
              Math.max(parentAnchorX, maxChildAnchor), color, shadow);
        float logicalLeft = logicalVisibleLeft();
        float logicalRight = logicalVisibleRight();
        for (int index = lowerBoundX(children, logicalLeft); index < children.size(); index++) {
            LayoutNode child = children.get(index);
            if (child.x > logicalRight) break;
            Rect childRect = rect(child);
            int childAnchor = childRect.x + Math.round(LINE_RENDER_OFFSET * zoom);
            QIOCraftingMonitorRuntimeNode childRuntime =
                  runtimes.get(child.nodeId);
            drawTreeVertical(childAnchor, junctionY, childRect.y,
                  edgeColor(parentRuntime, childRuntime),
                  edgeShadowColor(parentRuntime, childRuntime));
        }
    }

    private void drawTreeVertical(int x, int y1, int y2, int color, int shadow) {
        drawClippedLine(x + 1, y1, x + 1, y2, shadow);
        drawClippedLine(x, y1, x, y2, color);
    }

    private void drawTreeHorizontal(int x1, int y, int x2, int color, int shadow) {
        drawClippedLine(x1 + 1, y + 1, x2 + 1, y + 1, shadow);
        drawClippedLine(x1, y, x2, y, color);
    }

    private void drawClippedLine(int x1, int y1, int x2, int y2, int color) {
        int left = Math.max(relativeX + 1, Math.min(x1, x2));
        int right = Math.min(relativeX + width - 1, Math.max(x1, x2) + 1);
        int top = Math.max(relativeY + 1, Math.min(y1, y2));
        int bottom = Math.min(relativeY + height - 1, Math.max(y1, y2) + 1);
        if (right > left && bottom > top) GuiUtils.fill(left, top, right, bottom, color);
    }

    private void drawNode(LayoutNode node, Rect rect,
          @Nullable QIOCraftingMonitorRuntimeNode runtime, boolean selected) {
        boolean highlighted = node.requiredMaterial || filter == Filter.ALL || matches(runtime);
        boolean missingMaterial = node.requiredMaterial && missingAmount(node) > 0;
        boolean planningError = isPlanningError(node);
        int border = planningError || missingMaterial ? 0xFFE04F4F : selected ? 0xFFE0B84B :
              node.requiredMaterial ? 0xFF87917B :
              node.entry.getKind() == QIOCraftingMonitorPlanEntry.Kind.CYCLE ? 0xFF55A7A0 :
              isBlocked(runtime) ? 0xFFB75B5B : highlighted ? 0xFF68747A : 0xFF343B3F;
        int body = planningError || missingMaterial ? 0xFF64282D :
              node.requiredMaterial ? 0xFF252C27 :
              !highlighted ? 0xFF1B2023 : runtime != null &&
              !runtime.getActivities().isEmpty() ? 0xFF244D3D :
              isBlocked(runtime) ? 0xFF542E32 : 0xFF242A2E;
        GuiUtils.fill(rect.x, rect.y, rect.right(), rect.bottom(), border);
        GuiUtils.fill(rect.x + 1, rect.y + 1, rect.right() - 1, rect.bottom() - 1, body);

        int iconSize = Math.max(4, Math.min(LOGICAL_NODE_WIDTH - 2,
              Math.round(16 * zoom)));
        int iconX = rect.x + Math.max(1, (rect.width - iconSize) / 2);
        int iconY = rect.y + Math.max(1, (rect.height - iconSize) / 2);
        QIOGuiResourceRenderer.renderIcon(gui(), displayedResource(node), iconX, iconY,
              iconSize);
        drawAmountBadge(rect, displayedAmount(node));

        if (node.requiredMaterial) return;
        int providerColor = node.entry.getCycle() != null ? 0xFF55A7A0 :
              node.entry.getStep().getProviderKind() == QIOPlanStep.ProviderKind.WORKBENCH ?
                    0xFFD39A42 : 0xFF4E91C4;
        GuiUtils.fill(rect.right() - Math.max(2, Math.round(4 * zoom)), rect.y + 1,
              rect.right() - 1, rect.y + Math.max(2, Math.round(4 * zoom)),
              providerColor);
        drawProgress(rect, runtime, highlighted ? 0xFF62B77C : 0xFF46534A);
    }

    private void drawRoot(QIOCraftingMonitorEntry job, Rect rect, boolean selected) {
        boolean planningError = rootErrorSupplier.getAsBoolean();
        int border = planningError ? 0xFFE04F4F : selected ? 0xFFE0B84B :
              terminal(job.getState()) ? 0xFF5C8A68 :
              job.getMissingResourceTypes() > 0 ? 0xFF9A5A58 : 0xFF5D7E91;
        GuiUtils.fill(rect.x, rect.y, rect.right(), rect.bottom(), border);
        GuiUtils.fill(rect.x + 1, rect.y + 1, rect.right() - 1, rect.bottom() - 1,
              planningError ? 0xFF64282D : 0xFF20282C);
        int iconSize = Math.max(4, Math.min(LOGICAL_NODE_WIDTH - 2,
              Math.round(16 * zoom)));
        int iconX = rect.x + Math.max(1, (rect.width - iconSize) / 2);
        int iconY = rect.y + Math.max(1, (rect.height - iconSize) / 2);
        QIOGuiResourceRenderer.renderIcon(gui(), job.getRootResource(), iconX, iconY, iconSize);
        drawAmountBadge(rect, job.getRootAmount());
        drawProgress(rect, job.getDeliveredAmount(), job.getRootAmount(), 0xFF6DB486);
    }

    private void drawAmountBadge(Rect rect, long amount) {
        if (amount <= 0) return;
        String text = amount < 10_000 ? Long.toString(amount) :
              UnitDisplayUtils.getDisplay(amount, 1);
        float scale = 0.6F * zoom;
        int textWidth = getFont().getStringWidth(text);
        if (textWidth > 0) {
            scale = Math.min(scale, Math.max(0.1F, (rect.width - 1F) / textWidth));
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(rect.right() - 1 - textWidth * scale,
              rect.bottom() - getFont().FONT_HEIGHT * scale, 200);
        GlStateManager.scale(scale, scale, scale);
        getFont().drawString(text, 0, 0, 0xFFFFFFFF);
        GlStateManager.popMatrix();
    }

    private void drawProgress(Rect rect, @Nullable QIOCraftingMonitorRuntimeNode runtime,
          int color) {
        if (runtime == null) return;
        long current = runtime.getKind() == QIOCraftingMonitorRuntimeNode.Kind.CYCLE ?
              runtime.getCurrentRound() : runtime.getCompletedOperations();
        long total = runtime.getKind() == QIOCraftingMonitorRuntimeNode.Kind.CYCLE ?
              runtime.getTotalRounds() : runtime.getTotalOperations();
        drawProgress(rect, current, total, color);
    }

    private void drawProgress(Rect rect, long current, long total, int color) {
        if (total <= 0) return;
        int available = Math.max(0, rect.width - 2);
        int filled = (int) Math.min(available,
              Math.max(0, Math.round(available * (current / (double) total))));
        GuiUtils.fill(rect.x + 1, rect.bottom() - 2, rect.right() - 1,
              rect.bottom() - 1, 0xFF111517);
        if (filled > 0) GuiUtils.fill(rect.x + 1, rect.bottom() - 2,
              rect.x + 1 + filled, rect.bottom() - 1, color);
    }

    private boolean matches(@Nullable QIOCraftingMonitorRuntimeNode runtime) {
        return switch (filter) {
            case ALL -> true;
            case ACTIVE -> runtime != null && (runtime.getKind() ==
                  QIOCraftingMonitorRuntimeNode.Kind.STEP ? !runtime.getActivities().isEmpty() :
                  runtime.getCurrentRound() < runtime.getTotalRounds() &&
                        runtime.getCompletedThisRound().values().stream().anyMatch(value ->
                              value > 0));
            case BLOCKED -> isBlocked(runtime);
            case COMPLETE -> runtime != null && (runtime.getKind() ==
                  QIOCraftingMonitorRuntimeNode.Kind.STEP ?
                  runtime.getCompletedOperations() == runtime.getTotalOperations() :
                  runtime.getCurrentRound() == runtime.getTotalRounds());
        };
    }

    private static boolean isBlocked(@Nullable QIOCraftingMonitorRuntimeNode runtime) {
        return runtime != null && (runtime.getFailedAttempts() > 0 ||
              runtime.getActivities().stream().anyMatch(activity ->
                    !activity.getDiagnostic().isEmpty() ||
                          "OUTPUT_BLOCKED".equals(activity.getState()) ||
                          "FAILED".equals(activity.getState())));
    }

    private boolean isPlanningError(LayoutNode node) {
        if (errorNodeIdsSupplier.get().contains(node.nodeId)) return true;
        Set<PortableResourceDescriptor> errors = errorResourcesSupplier.get();
        if (node.resource != null && errors.contains(node.resource)) return true;
        return node.candidateResources.stream().anyMatch(errors::contains);
    }

    private NodeStatus nodeStatus(LayoutNode node) {
        if (node.requiredMaterial) {
            return missingAmount(node) > 0 ?
                  NodeStatus.WAITING : NodeStatus.COMPLETE;
        }
        NodeStatus cached = statusCache.get(node.nodeId);
        if (cached != null) return cached;
        QIOCraftingMonitorRuntimeNode runtime = runtimeSupplier.get().get(node.nodeId);
        NodeStatus status;
        if (runtime == null) {
            status = NodeStatus.WAITING;
        } else if (runtime.getKind() == QIOCraftingMonitorRuntimeNode.Kind.CYCLE) {
            status = runtime.getCurrentRound() >= runtime.getTotalRounds() ?
                  NodeStatus.COMPLETE : cycleActive(node.entry.getCycle(), runtimeSupplier.get()) ?
                        NodeStatus.PROCESSING : NodeStatus.WAITING;
        } else if (runtime.getCompletedOperations() >= runtime.getTotalOperations()) {
            status = NodeStatus.COMPLETE;
        } else {
            status = hasProcessingActivity(runtime) ? NodeStatus.PROCESSING :
                  NodeStatus.WAITING;
        }
        statusCache.put(node.nodeId, status);
        return status;
    }

    private static boolean cycleActive(@Nullable QIOCyclePlanNode cycle,
          Map<Long, QIOCraftingMonitorRuntimeNode> runtimes) {
        if (cycle == null) return false;
        for (long member : cycle.getMemberQuotas().keySet()) {
            QIOCraftingMonitorRuntimeNode runtime = runtimes.get(member);
            if (runtime != null && hasProcessingActivity(runtime)) return true;
        }
        return false;
    }

    private static boolean hasProcessingActivity(QIOCraftingMonitorRuntimeNode runtime) {
        return runtime.getActivities().stream().anyMatch(activity ->
              !"OUTPUT_BLOCKED".equals(activity.getState()) &&
                    !"FAILED".equals(activity.getState()));
    }

    private long missingAmount(LayoutNode node) {
        Map<PortableResourceDescriptor, Long> missing = missingMaterialsSupplier.get();
        if (node.candidateOptions.isEmpty()) {
            return missing.getOrDefault(node.resource, 0L);
        }
        long missingUnits = QIOCraftingPlanLayout.candidateMissingUnits(
              node.candidateOptions, missing);
        return QIOCraftingPlanLayout.displayedAmount(0, missingUnits,
              node.candidateOptions, animationTick);
    }

    private PortableResourceDescriptor displayedResource(LayoutNode node) {
        QIOCandidateOption option = QIOCraftingPlanLayout.cyclingOption(
              node.candidateOptions, animationTick);
        return option == null ? node.resource : option.getResource();
    }

    private long displayedAmount(LayoutNode node) {
        return QIOCraftingPlanLayout.displayedAmount(node.amount, node.requiredUnits,
              node.candidateOptions, animationTick);
    }

    private static String materialAmountTranslation(String key,
          PortableResourceDescriptor resource, long amount) {
        String suffix = resource != null &&
              resource.getKind() == PortableResourceDescriptor.Kind.FLUID ? "_fluid" : "";
        return translation("gui.mekanismqioprocessing." + key + suffix,
              TextUtils.format(amount));
    }

    private List<String> stepTooltip(QIOPlanStep step,
          @Nullable QIOCraftingMonitorRuntimeNode runtime) {
        List<String> tooltip = new ArrayList<>();
        tooltip.add(translation("gui.mekanismqioprocessing.monitor_step",
              Long.toString(step.getNodeId())));
        tooltip.add(translation(step.getProviderKind() == QIOPlanStep.ProviderKind.WORKBENCH ?
              "gui.mekanismqioprocessing.monitor_provider_workbench" :
              "gui.mekanismqioprocessing.monitor_provider_machine"));
        tooltip.add(translation("gui.mekanismqioprocessing.monitor_operations",
              TextUtils.format(step.getOperations())));
        if (runtime != null) {
            tooltip.add(translation("gui.mekanismqioprocessing.monitor_operation_progress",
                  TextUtils.format(runtime.getCompletedOperations()),
                  TextUtils.format(runtime.getTotalOperations())));
        }
        appendAmounts(tooltip, "gui.mekanismqioprocessing.monitor_inputs",
              step.getExactInputs(), step.getOperations());
        appendAmounts(tooltip, "gui.mekanismqioprocessing.monitor_outputs",
              step.getGuaranteedOutputs(), step.getOperations());
        appendAmounts(tooltip, "gui.mekanismqioprocessing.monitor_optional_outputs",
              step.getOptionalOutputs(), step.getOperations());
        if (runtime != null && !runtime.getActivities().isEmpty()) {
            tooltip.add(translation("gui.mekanismqioprocessing.monitor_active_lanes",
                  Integer.toString(runtime.getActivities().size())));
        }
        if (runtime != null && runtime.getFailedAttempts() > 0) {
            tooltip.add(translation("gui.mekanismqioprocessing.monitor_failed_attempts",
                  TextUtils.format(runtime.getFailedAttempts())));
        }
        tooltip.add(step.getRouteId());
        return tooltip;
    }

    private List<String> cycleTooltip(QIOCyclePlanNode cycle,
          @Nullable QIOCraftingMonitorRuntimeNode runtime) {
        List<String> tooltip = new ArrayList<>();
        tooltip.add(translation("gui.mekanismqioprocessing.monitor_cycle",
              Long.toString(cycle.getNodeId())));
        tooltip.add(translation("gui.mekanismqioprocessing.monitor_cycle_rounds",
              TextUtils.format(cycle.getTotalRounds())));
        tooltip.add(translation("gui.mekanismqioprocessing.monitor_cycle_members",
              Integer.toString(cycle.getMemberQuotas().size())));
        if (runtime != null) {
            tooltip.add(translation("gui.mekanismqioprocessing.monitor_cycle_progress",
                  TextUtils.format(runtime.getCurrentRound()),
                  TextUtils.format(runtime.getTotalRounds())));
        }
        appendAmounts(tooltip, "gui.mekanismqioprocessing.monitor_cycle_seeds",
              cycle.getSeedRequirements(), 1);
        appendAmounts(tooltip, "gui.mekanismqioprocessing.monitor_cycle_net",
              cycle.getNetPerRound(), cycle.getTotalRounds());
        return tooltip;
    }

    private void appendAmounts(List<String> tooltip, String key,
          Map<PortableResourceDescriptor, Long> amounts, long multiplier) {
        if (amounts.isEmpty()) return;
        int shown = 0;
        for (Map.Entry<PortableResourceDescriptor, Long> entry : amounts.entrySet()) {
            if (shown++ == 4) {
                tooltip.add(translation("gui.mekanismqioprocessing.monitor_more_resources",
                      Integer.toString(amounts.size() - 4)));
                break;
            }
            tooltip.add(translation(key, QIOGuiResourceRenderer.name(entry.getKey()),
                  TextUtils.format(saturatedMultiply(entry.getValue(), multiplier))));
        }
    }

    private void renderRootTooltip(int mouseX, int mouseY) {
        QIOCraftingMonitorEntry job = jobSupplier.get();
        if (job == null) return;
        List<String> extra = new ArrayList<>();
        extra.add(translation("gui.mekanismqioprocessing.monitor_requested",
              TextUtils.format(job.getRootAmount())));
        extra.add(translation("gui.mekanismqioprocessing.monitor_delivered",
              TextUtils.format(job.getDeliveredAmount())));
        extra.add(translation("gui.mekanismqioprocessing.monitor_state",
              stateName(job.getState())));
        if (rootErrorSupplier.getAsBoolean()) {
            extra.add(translation("gui.mekanismqioprocessing.order_analysis_error_node"));
        }
        if (treeLayout != null && treeLayout.isTruncated()) {
            extra.add(translation("gui.mekanismqioprocessing.monitor_tree_truncated",
                  TextUtils.format(QIOCraftingPlanLayout.MAX_TREE_OCCURRENCES)));
        }
        renderResourceTooltip(job.getRootResource(), extra, mouseX, mouseY);
    }

    private void renderResourceTooltip(@Nullable PortableResourceDescriptor resource,
          List<String> extra, int mouseX, int mouseY) {
        ItemStack item = QIOGuiResourceRenderer.item(resource);
        if (!item.isEmpty()) {
            gui().renderItemTooltipWithExtra(item, mouseX, mouseY, extra);
        } else {
            List<String> tooltip = QIOGuiResourceRenderer.tooltip(resource);
            tooltip.addAll(extra);
            displayTooltips(tooltip, mouseX, mouseY, 260);
        }
    }

    @Nullable
    private Long hit(int localX, int localY) {
        if (jobSupplier.get() != null && planReadySupplier.getAsBoolean() &&
            rootRect().contains(localX, localY)) return ROOT_NODE_ID;
        float logicalX = (localX - relativeX - panX) / zoom;
        int firstDepth = firstVisibleDepth();
        int lastDepth = lastVisibleDepth();
        for (int depth = firstDepth; depth <= lastDepth; depth++) {
            List<LayoutNode> row = layoutRows.get(depth);
            for (int index = lowerBoundX(row, logicalX - LOGICAL_NODE_WIDTH);
                  index < row.size(); index++) {
                LayoutNode node = row.get(index);
                if (node.x > logicalX) break;
                if (rect(node).contains(localX, localY)) return node.nodeId;
            }
        }
        return null;
    }

    private int firstVisibleDepth() {
        return Math.max(1, (int) Math.floor(((-panY / zoom) -
              QIOCraftingPlanLayout.ROOT_MARGIN_TOP - QIOCraftingPlanLayout.LINE_HEIGHT -
              LOGICAL_NODE_HEIGHT) / QIOCraftingPlanLayout.NODE_TOTAL_HEIGHT));
    }

    private int lastVisibleDepth() {
        if (layoutRows.isEmpty()) return 0;
        int depth = (int) Math.ceil((((height - panY) / zoom) -
              QIOCraftingPlanLayout.ROOT_MARGIN_TOP - QIOCraftingPlanLayout.LINE_HEIGHT) /
              QIOCraftingPlanLayout.NODE_TOTAL_HEIGHT);
        return Math.min(layoutRows.size() - 1, Math.max(0, depth));
    }

    private float logicalVisibleLeft() {
        return -panX / zoom - LOGICAL_NODE_WIDTH;
    }

    private float logicalVisibleRight() {
        return (width - panX) / zoom;
    }

    private static int lowerBoundX(List<LayoutNode> row, float logicalX) {
        int low = 0;
        int high = row.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (row.get(middle).x < logicalX) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private static int lowerBoundLinkMax(List<LayoutNode> row, float logicalX) {
        int low = 0;
        int high = row.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (row.get(middle).linkMaxX < logicalX) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private Rect rootRect() {
        return scaledRect(rootX, rootY);
    }

    private Rect rect(LayoutNode node) {
        return scaledRect(node.x, node.y);
    }

    private Rect scaledRect(int logicalX, int logicalY) {
        return new Rect(relativeX + Math.round(panX + logicalX * zoom),
              relativeY + Math.round(panY + logicalY * zoom),
              Math.max(1, Math.round(LOGICAL_NODE_WIDTH * zoom)),
              Math.max(1, Math.round(LOGICAL_NODE_HEIGHT * zoom)));
    }

    @Nullable
    private LayoutNode findFirstNode(long nodeId) {
        for (LayoutNode node : layout) {
            if (node.nodeId == nodeId) return node;
        }
        return null;
    }

    @Nullable
    private static PortableResourceDescriptor primaryResource(
          QIOCraftingMonitorPlanEntry entry, @Nullable PortableResourceDescriptor root) {
        if (entry.getStep() != null) {
            QIOPlanStep step = entry.getStep();
            if (root != null && step.getGuaranteedOutputs().containsKey(root)) return root;
            if (!step.getGuaranteedOutputs().isEmpty()) {
                return step.getGuaranteedOutputs().keySet().iterator().next();
            }
            if (!step.getOptionalOutputs().isEmpty()) {
                return step.getOptionalOutputs().keySet().iterator().next();
            }
            return step.getExactInputs().isEmpty() ? null :
                  step.getExactInputs().keySet().iterator().next();
        }
        QIOCyclePlanNode cycle = entry.getCycle();
        if (root != null && cycle.getNetPerRound().containsKey(root)) return root;
        if (!cycle.getNetPerRound().isEmpty()) {
            return cycle.getNetPerRound().keySet().iterator().next();
        }
        return cycle.getSeedRequirements().isEmpty() ? null :
              cycle.getSeedRequirements().keySet().iterator().next();
    }

    private static long totalOutput(QIOCraftingMonitorPlanEntry entry,
          @Nullable PortableResourceDescriptor resource) {
        if (resource == null) return 0;
        if (entry.getStep() != null) {
            QIOPlanStep step = entry.getStep();
            long amount = step.getGuaranteedOutputs().getOrDefault(resource,
                  step.getOptionalOutputs().getOrDefault(resource, 0L));
            return saturatedMultiply(amount, step.getOperations());
        }
        return saturatedMultiply(entry.getCycle().getNetPerRound().getOrDefault(resource, 0L),
              entry.getCycle().getTotalRounds());
    }

    private static long saturatedMultiply(long left, long right) {
        if (left <= 0 || right <= 0) return 0;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }

    private static boolean terminal(String state) {
        return "COMPLETED".equals(state) || "FAILED".equals(state) ||
              "CANCELLED".equals(state);
    }

    private enum NodeStatus {
        WAITING,
        PROCESSING,
        COMPLETE
    }

    private static String stateName(String state) {
        return translation("gui.mekanismqioprocessing.monitor_state_" +
              state.toLowerCase(java.util.Locale.ROOT));
    }

    private static String translation(String key, Object... arguments) {
        return new TextComponentTranslation(key, arguments).getFormattedText();
    }

    private void setZoom(float zoom) {
        this.zoom = clampZoom(zoom);
        clampPan();
    }

    private static float clampZoom(float zoom) {
        return Math.max(0.25F, Math.min(1.0F, zoom));
    }

    private void clampPan() {
        if (treeLayout == null) return;
        panX = QIOCraftingPlanLayout.clampPanOffset(panX, 0, treeLayout.getWidth(),
              zoom, width, PAN_MARGIN);
        panY = QIOCraftingPlanLayout.clampPanOffset(panY, 0, treeLayout.getHeight(),
              zoom, height, PAN_MARGIN);
    }

    private static final class RequiredMaterial {
        private final PortableResourceDescriptor resource;
        private final long amount;
        private final long requiredUnits;
        private final List<QIOCandidateOption> candidateOptions;
        private final List<PortableResourceDescriptor> candidateResources;

        private RequiredMaterial(QIOCraftingPlanLayout.MaterialLeaf leaf) {
            resource = leaf.getResource();
            amount = leaf.getExactAmount();
            requiredUnits = leaf.getRequiredUnits();
            candidateOptions = leaf.getOptions();
            List<PortableResourceDescriptor> resources = new ArrayList<>(
                  candidateOptions.size());
            candidateOptions.forEach(option -> resources.add(option.getResource()));
            candidateResources = resources.isEmpty() ? Collections.emptyList() :
                  Collections.unmodifiableList(resources);
        }
    }

    private static final class LayoutNode {
        private final long nodeId;
        @Nullable private final QIOCraftingMonitorPlanEntry entry;
        private final boolean requiredMaterial;
        private final int x;
        private final int y;
        private final int depth;
        private final List<LayoutNode> children = new ArrayList<>();
        private int linkMinX;
        private int linkMaxX;
        @Nullable private final PortableResourceDescriptor resource;
        private final long amount;
        private final long requiredUnits;
        private final List<QIOCandidateOption> candidateOptions;
        private final List<PortableResourceDescriptor> candidateResources;

        private LayoutNode(QIOCraftingMonitorPlanEntry entry, int x, int y, int depth,
              @Nullable PortableResourceDescriptor resource, long amount) {
            nodeId = entry.getNodeId();
            this.entry = entry;
            requiredMaterial = false;
            this.x = x;
            this.y = y;
            this.depth = depth;
            linkMinX = x;
            linkMaxX = x;
            this.resource = resource;
            this.amount = amount;
            requiredUnits = 0;
            candidateOptions = Collections.emptyList();
            candidateResources = Collections.emptyList();
        }

        private LayoutNode(long nodeId, int x, int y, int depth,
              PortableResourceDescriptor resource, long amount, long requiredUnits,
              List<QIOCandidateOption> candidateOptions) {
            this.nodeId = nodeId;
            entry = null;
            requiredMaterial = true;
            this.x = x;
            this.y = y;
            this.depth = depth;
            linkMinX = x;
            linkMaxX = x;
            this.resource = resource;
            this.amount = amount;
            this.requiredUnits = requiredUnits;
            this.candidateOptions = candidateOptions;
            List<PortableResourceDescriptor> resources = new ArrayList<>(
                  candidateOptions.size());
            candidateOptions.forEach(option -> resources.add(option.getResource()));
            candidateResources = resources.isEmpty() ? Collections.emptyList() :
                  Collections.unmodifiableList(resources);
        }

        private void updateLinkBounds() {
            if (children.isEmpty()) return;
            linkMinX = Math.min(x, children.get(0).x);
            linkMaxX = Math.max(x, children.get(children.size() - 1).x);
        }
    }

    private static final class ScissorState {
        private final boolean enabled;
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private ScissorState(boolean enabled, int x, int y, int width, int height) {
            this.enabled = enabled;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private void restore() {
            GL11.glScissor(x, y, width, height);
            if (enabled) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    private static final class Rect {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private Rect(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private int right() { return x + width; }
        private int bottom() { return y + height; }
        private int centerY() { return y + height / 2; }
        private boolean contains(int px, int py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
        private boolean intersects(int left, int top, int width, int height) {
            return right() > left && x < left + width && bottom() > top && y < top + height;
        }
    }
}
