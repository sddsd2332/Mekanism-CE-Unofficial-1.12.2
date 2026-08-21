package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.util.text.TextUtils;
import mekanism.qioprocessing.common.QIOProcessingWindowTypes;
import mekanism.qioprocessing.common.order.QIOOrderPreview;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorEntry;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingPreviewSnapshot;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Transient crafting-tree projection used to review one completed order analysis. */
/**
 * QIO 处理模块中的 GuiQIOSmartProcessingAnalysisWindow 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class GuiQIOSmartProcessingAnalysisWindow extends GuiWindow {

    public static final int WIDTH = 488;
    private static final int HEIGHT = 220;

    private final UUID previewId;
    private final QIOSmartProcessingPreviewSnapshot initialSnapshot;
    private final Supplier<QIOSmartProcessingPreviewSnapshot> snapshotSupplier;
    private final Runnable submitAction;
    private final BiConsumer<UUID, Boolean> closeCallback;
    private final MekanismButton submitButton;
    private final QIOCraftingMonitorEntry entry;
    private boolean submissionRequested;
    private boolean notifyOnClose = true;
    private boolean closed;

    public GuiQIOSmartProcessingAnalysisWindow(IGuiWrapper gui, int x, int y,
          QIOSmartProcessingPreviewSnapshot snapshot,
          Supplier<QIOSmartProcessingPreviewSnapshot> snapshotSupplier,
          Runnable submitAction, BiConsumer<UUID, Boolean> closeCallback) {
        super(gui, x, y, WIDTH, HEIGHT, new SelectedWindowData(
              QIOProcessingWindowTypes.SMART_PROCESSING_ANALYSIS));
        QIOSmartProcessingPreviewSnapshot checked = Objects.requireNonNull(snapshot, "snapshot");
        if (checked.getTarget() == null ||
            checked.getState() == QIOOrderPreview.State.PREPARING ||
            checked.getState() == QIOOrderPreview.State.PLANNING) {
            throw new IllegalArgumentException("Order analysis requires a completed preview");
        }
        previewId = checked.getPreviewId();
        initialSnapshot = checked;
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.submitAction = Objects.requireNonNull(submitAction, "submitAction");
        this.closeCallback = Objects.requireNonNull(closeCallback, "closeCallback");
        interactionStrategy = InteractionStrategy.ALL;

        entry = new QIOCraftingMonitorEntry(QIOCraftingMonitorEntry.Kind.JOB,
              checked.getPreviewId(), "MANUAL", checked.getState().name(), checked.getTarget(),
              checked.getAmount(), 0, checked.getPlanRevision(), 0, checked.getPriority(),
              checked.getPlannedOperations(), 0, 0, checked.getErrorResources().size(), false,
              null, -1, "", checked.getDiagnostic());

        submitButton = addChild(new MekanismButton(gui, relativeX + 366, relativeY + 5,
              116, 14, submitLabel(checked), this::submit, null));
        addChild(new GuiQIOCraftingPlanTree(gui, relativeX + 6, relativeY + 24,
              WIDTH - 12, 172, () -> snapshot().getPlanEntries(), Collections::emptyMap,
              Collections::emptyMap, () -> snapshot().getErrorNodeIds(),
              () -> snapshot().getErrorResources(), () -> snapshot().isRootError(),
              () -> entry, () -> snapshot().getPlanRevision(), () -> 0, () -> true, false));
        addChild(new GuiQIOPlanningStatusScreen(gui, relativeX + 6, relativeY + 199,
              WIDTH - 12, 15, true, this::statusContent));
        updateButton();
    }

    public UUID getPreviewId() {
        return previewId;
    }

    @Override
    public void tick() {
        super.tick();
        QIOSmartProcessingPreviewSnapshot snapshot = snapshot();
        if (snapshot.getState() == QIOOrderPreview.State.CONFIRMED &&
            snapshot.getJobId() != null) {
            dismiss(false);
            return;
        }
        updateButton();
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawScaledScrollingString(new TextComponentTranslation(
                    "gui.mekanismqioprocessing.order_analysis_title",
                    QIOGuiResourceRenderer.name(entry.getRootResource())),
              39, 6, TextAlignment.LEFT, titleTextColor(), 321, 0,
              false, 0.9F, getTimeOpened());
    }

    public void dismiss(boolean notifyParent) {
        notifyOnClose = notifyParent;
        close();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        super.close();
        if (notifyOnClose) closeCallback.accept(previewId, submissionRequested);
    }

    private void submit() {
        if (submissionRequested || snapshot().getState() != QIOOrderPreview.State.READY) return;
        submissionRequested = true;
        updateButton();
        submitAction.run();
    }

    private void updateButton() {
        QIOSmartProcessingPreviewSnapshot snapshot = snapshot();
        submitButton.setMessage(submitLabel(snapshot));
        submitButton.active = !submissionRequested &&
              snapshot.getState() == QIOOrderPreview.State.READY;
    }

    private ITextComponent submitLabel(QIOSmartProcessingPreviewSnapshot snapshot) {
        return new TextComponentTranslation(snapshot.isMergeOrder() ?
              "gui.mekanismqioprocessing.order_merge" :
              "gui.mekanismqioprocessing.order_confirm");
    }

    private GuiQIOPlanningStatusScreen.Content statusContent() {
        QIOSmartProcessingPreviewSnapshot snapshot = snapshot();
        String summary = new TextComponentTranslation(
              "gui.mekanismqioprocessing.order_preview_state",
              new TextComponentTranslation("gui.mekanismqioprocessing.order_state_" +
                    snapshot.getState().name().toLowerCase(Locale.ROOT)),
              TextUtils.format(snapshot.getPlannedOperations())).getFormattedText();
        String planningTime = new TextComponentTranslation(
              "gui.mekanismqioprocessing.order_planning_time",
              GuiQIOPlanningStatusScreen.duration(snapshot.getPlanningNanos(), false))
              .getFormattedText();
        String detailedStatus = new TextComponentTranslation(
              "gui.mekanismqioprocessing.order_detailed_status").getFormattedText();
        ITextComponent details = GuiQIOPlanningStatusScreen.detailedTimings(snapshot, false);
        if (!snapshot.getDiagnostic().isEmpty()) {
            details = GuiQIOPlanningStatusScreen.appendDetail(details,
                  new TextComponentString(snapshot.getDiagnostic()));
        }
        return GuiQIOPlanningStatusScreen.Content.detail(Collections.emptyList(),
              new TextComponentString(summary + "  " + planningTime + "  " +
                    detailedStatus), details);
    }

    private QIOSmartProcessingPreviewSnapshot snapshot() {
        QIOSmartProcessingPreviewSnapshot current = snapshotSupplier.get();
        return current != null && previewId.equals(current.getPreviewId()) ? current :
              initialSnapshot;
    }

}
