package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.client.render.IFancyFontRenderer.TextAlignment;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.util.text.TextUtils;
import mekanism.qioprocessing.common.QIOProcessingWindowTypes;
import mekanism.qioprocessing.common.inventory.container.QIOCraftingMonitorClientCache;
import mekanism.qioprocessing.common.inventory.container.QIOCraftingMonitorPageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.network.PacketQIOCraftingMonitorCancel;
import mekanism.qioprocessing.common.network.PacketQIOCraftingMonitorMutation;
import mekanism.qioprocessing.common.network.PacketQIOCraftingMonitorPlanPageRequest;
import mekanism.qioprocessing.common.network.PacketQIOCraftingMonitorRuntimeRequest;
import mekanism.qioprocessing.common.network.QIOProcessingPacketHandler;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorEntry;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorRuntimeSnapshot;
import mekanism.qioprocessing.common.terminal.QIOPageCursor;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Independent live crafting-tree viewer for one non-terminal QIO job. */
public final class GuiQIOCraftingPlanWindow extends GuiWindow {

    public static final int WIDTH = 488;
    private static final int HEIGHT = 220;
    private static final int PAGE_SIZE = 128;
    private static final long REQUEST_TIMEOUT = 100;
    private static final long RUNTIME_REFRESH = 10;

    private final QIOCraftingMonitorPageContainer container;
    private final QIOProcessingTerminalContainerState state;
    private final UUID jobId;
    private final SessionKey openedSession;
    private final GuiQIOCraftingPlanTree tree;
    private final MekanismButton priorityDownButton;
    private final MekanismButton priorityUpButton;
    private final MekanismButton cancelButton;

    private QIOCraftingMonitorEntry entry;
    @Nullable private Pending planPending;
    @Nullable private Pending runtimePending;
    @Nullable private Pending cancelPending;
    @Nullable private Pending mutationPending;
    @Nullable private UUID cancelRequestId;
    @Nullable private UUID mutationRequestId;
    private long clientTick;
    private long lastRuntimeRequestTick = Long.MIN_VALUE;
    private boolean closeScheduled;
    private boolean closed;

    public GuiQIOCraftingPlanWindow(IGuiWrapper gui, int x, int y,
          QIOCraftingMonitorPageContainer container, QIOCraftingMonitorEntry entry) {
        super(gui, x, y, WIDTH, HEIGHT, new SelectedWindowData(
              QIOProcessingWindowTypes.CRAFTING_MONITOR_PLAN));
        this.container = Objects.requireNonNull(container, "container");
        state = container.getTerminalState();
        this.entry = Objects.requireNonNull(entry, "entry");
        jobId = entry.getEntryId();
        openedSession = SessionKey.capture(state);
        if (openedSession == null) {
            throw new IllegalStateException("Crafting plan window requires an active session");
        }
        interactionStrategy = InteractionStrategy.ALL;

        priorityDownButton = addChild(new MekanismButton(gui, relativeX + 250,
              relativeY + 5, 76, 14, new TextComponentTranslation(
                    "gui.mekanismqioprocessing.monitor_priority_down"),
              () -> adjustPriority(-1), null));
        priorityUpButton = addChild(new MekanismButton(gui, relativeX + 328,
              relativeY + 5, 76, 14, new TextComponentTranslation(
                    "gui.mekanismqioprocessing.monitor_priority_up"),
              () -> adjustPriority(1), null));
        cancelButton = addChild(new MekanismButton(gui, relativeX + 406,
              relativeY + 5, 76, 14, new TextComponentTranslation(
                    "gui.mekanismqioprocessing.monitor_cancel"), this::cancel, null));
        tree = addChild(new GuiQIOCraftingPlanTree(gui, relativeX + 6,
              relativeY + 24, WIDTH - 12, 172, () -> cache().getPlanEntries(),
              () -> cache().getRuntimeNodes(), () -> {
                  QIOCraftingMonitorRuntimeSnapshot header = cache().getRuntimeHeader();
                  return header == null ? Collections.emptyMap() :
                        header.getMissingResources();
              }, Collections::emptySet, Collections::emptySet, () -> false,
              () -> this.entry, () -> cache().getPlanGeneration(),
              () -> cache().getRuntimeGeneration(), () -> cache().isPlanComplete(), true));
        addChild(new GuiInnerScreen(gui, relativeX + 6, relativeY + 199,
              WIDTH - 12, 15, this::statusText).clearFormat());

        beginDetail(true);
        updateButtons();
    }

    public UUID getJobId() {
        return jobId;
    }

    @Override
    public void tick() {
        super.tick();
        clientTick++;
        if (!openedSession.matches(state)) {
            closeLater();
            return;
        }
        QIOCraftingMonitorEntry refreshed = cache().getEntry(jobId);
        if (refreshed != null) {
            if (refreshed.getPlanRevision() != entry.getPlanRevision()) {
                entry = refreshed;
                beginDetail(true);
            } else {
                entry = refreshed;
            }
        } else if (cache().isPageComplete()) {
            closeLater();
            return;
        }
        acknowledgeRequests();
        retryTimedOutRequests();
        tickDetail();
        updateButtons();
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawScaledScrollingString(new TextComponentTranslation(
                    "gui.mekanismqioprocessing.monitor_plan_title",
                    QIOGuiResourceRenderer.name(entry.getRootResource())),
              39, 6, TextAlignment.LEFT, titleTextColor(), 207, 0,
              false, 0.9F, getTimeOpened());
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (jobId.equals(cache().getDetailJobId())) {
                cache().clearDetail();
            }
            super.close();
        }
    }

    private void tickDetail() {
        if (!hasFrequency()) return;
        QIOCraftingMonitorClientCache cache = cache();
        if (!jobId.equals(cache.getDetailJobId()) ||
              entry.getPlanRevision() != cache.getDetailPlanRevision()) {
            beginDetail(true);
            return;
        }
        if (!cache.isPlanComplete()) {
            if (planPending == null) {
                sendPlan(cache.getPlanSourceRevision() < 0 ? null :
                      cache.getNextPlanCursor());
            }
            return;
        }
        if (runtimePending == null && (cache.isRuntimeBaselineRequired() ||
              cache.isRuntimeSweepPending() ||
              clientTick - lastRuntimeRequestTick >= RUNTIME_REFRESH)) {
            sendRuntime(cache.isRuntimeBaselineRequired());
        }
    }

    private void beginDetail(boolean resetView) {
        cache().selectDetail(jobId, entry.getPlanRevision());
        planPending = null;
        runtimePending = null;
        lastRuntimeRequestTick = Long.MIN_VALUE;
        if (resetView) tree.resetView();
        if (hasFrequency()) sendPlan(null);
    }

    private void sendPlan(@Nullable QIOPageCursor cursor) {
        if (!hasFrequency() || planPending != null) return;
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOCraftingMonitorPlanPageRequest.Message.create(
                    container.getTerminalWindowId(), state, jobId,
                    entry.getPlanRevision(), PAGE_SIZE, cursor));
        planPending = new Pending(cache().getPlanGeneration(), clientTick);
    }

    private void sendRuntime(boolean baseline) {
        if (!hasFrequency() || runtimePending != null) return;
        QIOCraftingMonitorClientCache cache = cache();
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOCraftingMonitorRuntimeRequest.Message.create(
                    container.getTerminalWindowId(), state, jobId,
                    entry.getPlanRevision(), cache.getDetailRuntimeRevision(), baseline,
                    cache.getRuntimeNodeOffset(), cache.getKnownNodeRevisions()));
        runtimePending = new Pending(cache.getRuntimeGeneration(), clientTick);
        lastRuntimeRequestTick = clientTick;
    }

    private void acknowledgeRequests() {
        QIOCraftingMonitorClientCache cache = cache();
        if (planPending != null && planPending.generation != cache.getPlanGeneration()) {
            planPending = null;
        }
        if (runtimePending != null && runtimePending.generation !=
              cache.getRuntimeGeneration()) {
            runtimePending = null;
        }
        if (cancelPending != null && cancelPending.generation !=
              cache.getCancelGeneration()) {
            if (cancelRequestId != null && cancelRequestId.equals(
                  cache.getLastCancelRequestId())) {
                cache.requireRuntimeBaseline();
                runtimePending = null;
            }
            cancelPending = null;
            cancelRequestId = null;
        }
        if (mutationPending != null && mutationPending.generation !=
              cache.getMutationGeneration()) {
            if (mutationRequestId != null && mutationRequestId.equals(
                  cache.getLastMutationRequestId())) {
                cache.requireRuntimeBaseline();
                runtimePending = null;
            }
            mutationPending = null;
            mutationRequestId = null;
        }
    }

    private void retryTimedOutRequests() {
        if (planPending != null && clientTick - planPending.sentAt >= REQUEST_TIMEOUT) {
            planPending = null;
            runtimePending = null;
            sendPlan(null);
        }
        if (runtimePending != null &&
              clientTick - runtimePending.sentAt >= REQUEST_TIMEOUT) {
            runtimePending = null;
            cache().requireRuntimeBaseline();
        }
        if (cancelPending != null &&
              clientTick - cancelPending.sentAt >= REQUEST_TIMEOUT) {
            cancelPending = null;
            cancelRequestId = null;
        }
        if (mutationPending != null &&
              clientTick - mutationPending.sentAt >= REQUEST_TIMEOUT) {
            mutationPending = null;
            mutationRequestId = null;
        }
    }

    private void cancel() {
        if (!hasFrequency() || cancelPending != null || terminal(currentState())) return;
        QIOCraftingMonitorRuntimeSnapshot header = cache().getRuntimeHeader();
        long revision = header == null ? entry.getRuntimeRevision() :
              header.getRuntimeRevision();
        cancelRequestId = UUID.randomUUID();
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOCraftingMonitorCancel.Message.create(
                    container.getTerminalWindowId(), state, cancelRequestId,
                    jobId, revision));
        cancelPending = new Pending(cache().getCancelGeneration(), clientTick);
    }

    private void adjustPriority(int delta) {
        QIOCraftingMonitorRuntimeSnapshot header = cache().getRuntimeHeader();
        if (!hasFrequency() || header == null || mutationPending != null ||
              terminal(header.getState())) return;
        long current = header.getBasePriority();
        long updated = delta > 0 ? current == Long.MAX_VALUE ? current : current + 1 :
              current == Long.MIN_VALUE ? current : current - 1;
        mutationRequestId = UUID.randomUUID();
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOCraftingMonitorMutation.Message.create(
                    container.getTerminalWindowId(), state, mutationRequestId,
                    jobId, header.getRuntimeRevision(),
                    PacketQIOCraftingMonitorMutation.Action.PRIORITY, updated));
        mutationPending = new Pending(cache().getMutationGeneration(), clientTick);
    }

    private void updateButtons() {
        boolean valid = hasFrequency();
        QIOCraftingMonitorRuntimeSnapshot header = cache().getRuntimeHeader();
        boolean priority = valid && header != null && mutationPending == null &&
              !terminal(header.getState());
        priorityDownButton.active = priority && header.getBasePriority() != Long.MIN_VALUE;
        priorityUpButton.active = priority && header.getBasePriority() != Long.MAX_VALUE;
        cancelButton.active = valid && cancelPending == null && !terminal(currentState());
    }

    private List<ITextComponent> statusText() {
        QIOCraftingMonitorRuntimeSnapshot header = cache().getRuntimeHeader();
        String stateName = localizedState(header == null ? entry.getState() :
              header.getState());
        long delivered = header == null ? entry.getDeliveredAmount() :
              header.getDeliveredRootAmount();
        long requested = header == null ? entry.getRootAmount() :
              header.getRequestedRootAmount();
        String summary = QIOGuiResourceRenderer.name(entry.getRootResource()) + "  " +
              TextUtils.format(delivered) + "/" + TextUtils.format(requested) + "  " +
              stateName;
        if (header != null) {
            summary += "  " + translation("gui.mekanismqioprocessing.monitor_slots",
                  Integer.toString(header.getActiveExecutionSlots()),
                  Integer.toString(header.getConfiguredExecutionSlots()));
        }
        return Collections.singletonList(new TextComponentString(summary));
    }

    private String currentState() {
        QIOCraftingMonitorRuntimeSnapshot header = cache().getRuntimeHeader();
        return header == null ? entry.getState() : header.getState();
    }

    private boolean hasFrequency() {
        return openedSession.matches(state) && state.getFrequencyUUID() != null;
    }

    private QIOCraftingMonitorClientCache cache() {
        return container.getCraftingMonitorClientCache();
    }

    private void closeLater() {
        if (!closeScheduled) {
            closeScheduled = true;
            minecraft.addScheduledTask(() -> {
                if (!closed) close();
            });
        }
    }

    private static boolean terminal(String state) {
        return "COMPLETED".equals(state) || "FAILED".equals(state) ||
              "CANCELLED".equals(state);
    }

    private static String localizedState(String state) {
        return translation("gui.mekanismqioprocessing.monitor_state_" +
              state.toLowerCase(Locale.ROOT));
    }

    private static String translation(String key, Object... arguments) {
        return new TextComponentTranslation(key, arguments).getFormattedText();
    }

    private static final class Pending {
        private final long generation;
        private final long sentAt;

        private Pending(long generation, long sentAt) {
            this.generation = generation;
            this.sentAt = sentAt;
        }
    }

    private static final class SessionKey {
        private final UUID nonce;
        private final UUID terminal;
        private final UUID frequency;
        private final long targetRevision;
        private final long accessRevision;

        private SessionKey(UUID nonce, UUID terminal, UUID frequency,
              long targetRevision, long accessRevision) {
            this.nonce = nonce;
            this.terminal = terminal;
            this.frequency = frequency;
            this.targetRevision = targetRevision;
            this.accessRevision = accessRevision;
        }

        @Nullable
        private static SessionKey capture(QIOProcessingTerminalContainerState state) {
            return state.isValid() ? new SessionKey(state.getSessionNonce(),
                  state.getTerminalUUID(), state.getFrequencyUUID(),
                  state.getTargetRevision(), state.getAccessRevision()) : null;
        }

        private boolean matches(QIOProcessingTerminalContainerState state) {
            return state.matches(nonce, terminal, targetRevision, frequency,
                  accessRevision);
        }
    }
}
