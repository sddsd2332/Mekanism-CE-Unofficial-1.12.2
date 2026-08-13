package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.scroll.GuiScrollList;
import mekanism.client.jei.interfaces.IJEIIngredientHelper;
import mekanism.client.render.IFancyFontRenderer.TextAlignment;
import mekanism.common.util.text.TextUtils;
import mekanism.qioprocessing.common.inventory.container.QIOCraftingMonitorClientCache;
import mekanism.qioprocessing.common.inventory.container.QIOCraftingMonitorPageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.network.PacketQIOCraftingMonitorCancel;
import mekanism.qioprocessing.common.network.PacketQIOCraftingMonitorPageRequest;
import mekanism.qioprocessing.common.network.QIOProcessingPacketHandler;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorEntry;
import mekanism.qioprocessing.common.terminal.QIOPageCursor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Full-width live QIO job directory. Double-clicking a row opens its plan window. */
public final class GuiQIOCraftingMonitorPanel extends GuiElement {

    private static final int PAGE_SIZE = 128;
    private static final int JOB_ROW_HEIGHT = 28;
    private static final long REQUEST_TIMEOUT = 100;
    private static final long INITIAL_PAGE_TIMEOUT = 20;
    private static final long LIST_REFRESH = 40;
    private static final long DOUBLE_CLICK_TICKS = 5;
    private static final int CANCEL_BUTTON_WIDTH = 64;

    private final QIOCraftingMonitorPageContainer container;
    private final QIOProcessingTerminalContainerState state;
    private final MekanismButton listFilterButton;
    private final MekanismButton cancelButton;
    private final JobList jobs;
    private final List<QIOCraftingMonitorEntry> visibleEntries = new ArrayList<>();

    private ListFilter listFilter = ListFilter.ALL;
    @Nullable private SessionKey session;
    @Nullable private Pending pagePending;
    @Nullable private Pending cancelPending;
    @Nullable private UUID cancelRequestId;
    @Nullable private UUID selectedJobId;
    private long tick;
    private long lastListCompletedTick = Long.MIN_VALUE;
    private long lastListGeneration = Long.MIN_VALUE;
    private long initialRequestNotBeforeTick;

    public GuiQIOCraftingMonitorPanel(IGuiWrapper gui,
          QIOCraftingMonitorPageContainer container, int x, int y,
          int width, int height) {
        super(gui, x, y, width, height);
        this.container = Objects.requireNonNull(container, "container");
        state = container.getTerminalState();
        listFilterButton = addChild(new MekanismButton(gui, x, y, 88, 14,
              new TextComponentTranslation(listFilter.key), this::cycleListFilter, null));
        cancelButton = addChild(new MekanismButton(gui, x + width - CANCEL_BUTTON_WIDTH, y,
              CANCEL_BUTTON_WIDTH, 14, new TextComponentTranslation(
                    "gui.mekanismqioprocessing.monitor_cancel"), this::cancelSelected,
              getOnHover(() -> new TextComponentTranslation(
                    "gui.mekanismqioprocessing.monitor_cancel_tooltip"))));
        jobs = addChild(new JobList(gui, x, y + 17, width, height - 17));
    }

    @Override
    public void tick() {
        super.tick();
        tick++;
        SessionKey current = SessionKey.capture(state);
        if (!Objects.equals(current, session)) {
            session = current;
            clearSession();
            initialRequestNotBeforeTick = tick + 1;
        }
        acknowledgePage();
        acknowledgeCancel();
        retryTimedOutPage();
        retryTimedOutCancel();
        tickList();
        rebuildVisibleEntriesIfNeeded();
        synchronizeSelection();
        listFilterButton.setMessage(new TextComponentTranslation(listFilter.key));
        listFilterButton.active = state.isValid() && state.getFrequencyUUID() != null;
        cancelButton.active = canCancelSelected();
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawScaledScrollingString(new TextComponentTranslation(
                    "gui.mekanismqioprocessing.monitor_double_click_hint"),
              94, 3, TextAlignment.LEFT, 0xFF596368,
              Math.max(8, width - 98 - CANCEL_BUTTON_WIDTH), 0, false, 0.8F,
              getTimeOpened());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == Keyboard.KEY_DELETE && canCancelSelected()) {
            cancelSelected();
            return true;
        }
        return false;
    }

    @Override
    public boolean hasPersistentData() {
        return true;
    }

    @Override
    public void syncFrom(GuiElement element) {
        super.syncFrom(element);
        GuiQIOCraftingMonitorPanel old = (GuiQIOCraftingMonitorPanel) element;
        listFilter = old.listFilter;
        session = old.session;
        selectedJobId = old.selectedJobId;
        cancelPending = old.cancelPending;
        cancelRequestId = old.cancelRequestId;
        tick = old.tick;
        lastListCompletedTick = old.lastListCompletedTick;
        lastListGeneration = cache().getPageGeneration();
        initialRequestNotBeforeTick = old.initialRequestNotBeforeTick;
        rebuildVisibleEntries();
    }

    private void tickList() {
        if (!hasFrequency() || pagePending != null ||
              tick < initialRequestNotBeforeTick) return;
        QIOCraftingMonitorClientCache cache = cache();
        if (cache.getSourceRevision() < 0) {
            sendList(null);
        } else if (cache.getNextCursor() != null) {
            sendList(cache.getNextCursor());
        } else if (tick - lastListCompletedTick >= LIST_REFRESH) {
            sendList(null);
        }
    }

    private void sendList(@Nullable QIOPageCursor cursor) {
        if (!hasFrequency()) return;
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOCraftingMonitorPageRequest.Message.create(
                    container.getTerminalWindowId(), state, PAGE_SIZE, cursor));
        pagePending = new Pending(cache().getPageGeneration(), tick);
    }

    private void acknowledgePage() {
        if (pagePending != null && pagePending.generation !=
              cache().getPageGeneration()) {
            pagePending = null;
            if (cache().isPageComplete()) {
                lastListCompletedTick = tick;
            }
        }
    }

    private void retryTimedOutPage() {
        if (pagePending == null) return;
        boolean initialPage = cache().getSourceRevision() < 0;
        long timeout = initialPage ? INITIAL_PAGE_TIMEOUT : REQUEST_TIMEOUT;
        if (tick - pagePending.sentAt >= timeout) {
            if (!initialPage) {
                cache().clearEntries();
            }
            pagePending = null;
            sendList(null);
        }
    }

    private void acknowledgeCancel() {
        if (cancelPending == null || cancelPending.generation ==
              cache().getCancelGeneration()) return;
        if (cancelRequestId != null && cancelRequestId.equals(
              cache().getLastCancelRequestId())) {
            if (selectedJobId != null && selectedJobId.equals(
                  cache().getLastCancelJobId())) {
                selectedJobId = null;
            }
            cancelPending = null;
            cancelRequestId = null;
            lastListCompletedTick = Long.MIN_VALUE;
            rebuildVisibleEntries();
        } else {
            cancelPending = new Pending(cache().getCancelGeneration(),
                  cancelPending.sentAt);
        }
    }

    private void retryTimedOutCancel() {
        if (cancelPending != null && tick - cancelPending.sentAt >= REQUEST_TIMEOUT) {
            cancelPending = null;
            cancelRequestId = null;
        }
    }

    private void rebuildVisibleEntriesIfNeeded() {
        long generation = cache().getPageGeneration();
        if (generation != lastListGeneration) {
            lastListGeneration = generation;
            rebuildVisibleEntries();
        }
    }

    private void rebuildVisibleEntries() {
        visibleEntries.clear();
        for (QIOCraftingMonitorEntry entry : cache().getEntries()) {
            if (entry.getKind() == QIOCraftingMonitorEntry.Kind.JOB &&
                  listFilter.accepts(entry)) {
                visibleEntries.add(entry);
            }
        }
        jobs.refreshScroll();
    }

    private void synchronizeSelection() {
        if (selectedJobId != null && cache().isPageComplete() &&
              cache().getEntry(selectedJobId) == null) {
            selectedJobId = null;
        }
    }

    private void selectJob(@Nullable QIOCraftingMonitorEntry entry) {
        selectedJobId = entry == null ? null : entry.getEntryId();
    }

    private void cancelSelected() {
        QIOCraftingMonitorEntry selected = selectedEntry();
        if (selected == null || !canCancelSelected()) return;
        cancelRequestId = UUID.randomUUID();
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOCraftingMonitorCancel.Message.create(
                    container.getTerminalWindowId(), state, cancelRequestId,
                    selected.getEntryId(), selected.getRuntimeRevision()));
        cancelPending = new Pending(cache().getCancelGeneration(), tick);
    }

    private boolean canCancelSelected() {
        QIOCraftingMonitorEntry selected = selectedEntry();
        return hasFrequency() && cancelPending == null && selected != null &&
              !terminal(selected.getState());
    }

    @Nullable
    private QIOCraftingMonitorEntry selectedEntry() {
        return selectedJobId == null ? null : cache().getEntry(selectedJobId);
    }

    private void cycleListFilter() {
        ListFilter[] values = ListFilter.values();
        listFilter = values[(listFilter.ordinal() + 1) % values.length];
        jobs.resetScroll();
        rebuildVisibleEntries();
        QIOCraftingMonitorEntry selected = selectedJobId == null ? null :
              cache().getEntry(selectedJobId);
        if (selected != null && !listFilter.accepts(selected)) {
            selectedJobId = null;
        }
    }

    private void openPlan(QIOCraftingMonitorEntry entry) {
        if (!hasFrequency()) return;
        if (gui() instanceof GuiMekanism<?> mekanismGui) {
            for (mekanism.client.gui.element.window.GuiWindow window :
                  new ArrayList<>(mekanismGui.getWindows())) {
                if (window instanceof GuiQIOCraftingPlanWindow) {
                    window.close();
                }
            }
        }
        cache().selectDetail(entry.getEntryId(), entry.getPlanRevision());
        gui().addWindow(new GuiQIOCraftingPlanWindow(gui(),
              (getGuiWidth() - GuiQIOCraftingPlanWindow.WIDTH) / 2,
              2, container, entry));
    }

    private boolean hasFrequency() {
        return session != null && state.isValid() && state.getFrequencyUUID() != null;
    }

    private void clearSession() {
        cache().clear();
        pagePending = null;
        cancelPending = null;
        cancelRequestId = null;
        selectedJobId = null;
        visibleEntries.clear();
        lastListGeneration = Long.MIN_VALUE;
        lastListCompletedTick = Long.MIN_VALUE;
    }

    private QIOCraftingMonitorClientCache cache() {
        return container.getCraftingMonitorClientCache();
    }

    private static boolean terminal(String state) {
        return "COMPLETED".equals(state) || "FAILED".equals(state) ||
              "CANCELLED".equals(state);
    }

    private static boolean active(QIOCraftingMonitorEntry entry) {
        if (entry.getActiveOperations() > 0 || entry.ownsExecutionSlot()) return true;
        return switch (entry.getState()) {
            case "READY", "RESERVING", "REPLAN_DRAINING", "DISPATCHING",
                 "PROCESSING", "COLLECTING", "DELIVERING", "CANCEL_REQUESTED",
                 "RETURNING", "RETURN_BLOCKED" -> true;
            default -> false;
        };
    }

    private static String localizedState(String state) {
        return translation("gui.mekanismqioprocessing.monitor_state_" +
              state.toLowerCase(Locale.ROOT));
    }

    private static String translation(String key, Object... arguments) {
        return new TextComponentTranslation(key, arguments).getFormattedText();
    }

    private enum ListFilter {
        ALL("gui.mekanismqioprocessing.monitor_all_jobs") {
            @Override boolean accepts(QIOCraftingMonitorEntry entry) {
                return !terminal(entry.getState());
            }
        },
        ACTIVE("gui.mekanismqioprocessing.monitor_active") {
            @Override boolean accepts(QIOCraftingMonitorEntry entry) {
                return !terminal(entry.getState()) && active(entry);
            }
        },
        MANUAL("gui.mekanismqioprocessing.monitor_manual") {
            @Override boolean accepts(QIOCraftingMonitorEntry entry) {
                return !terminal(entry.getState()) && "MANUAL".equals(entry.getSource());
            }
        },
        MAINTENANCE("gui.mekanismqioprocessing.monitor_maintenance") {
            @Override boolean accepts(QIOCraftingMonitorEntry entry) {
                return !terminal(entry.getState()) &&
                      "MAINTENANCE".equals(entry.getSource());
            }
        };

        private final String key;

        ListFilter(String key) {
            this.key = key;
        }

        abstract boolean accepts(QIOCraftingMonitorEntry entry);
    }

    private final class JobList extends GuiScrollList implements IJEIIngredientHelper {

        @Nullable private UUID lastClickedJobId;
        private long lastClickTick = Long.MIN_VALUE;

        private JobList(IGuiWrapper gui, int x, int y, int width, int height) {
            super(gui, x, y, width, height, JOB_ROW_HEIGHT, GuiInnerScreen.SCREEN,
                  GuiInnerScreen.SCREEN_SIZE);
        }

        private void refreshScroll() {
            clampScroll();
        }

        @Override
        protected int getMaxElements() {
            return visibleEntries.size();
        }

        @Override
        public boolean hasSelection() {
            return selectedJobId != null;
        }

        @Override
        protected void setSelected(int index) {
            if (index >= 0 && index < visibleEntries.size()) {
                selectJob(visibleEntries.get(index));
            }
        }

        @Override
        public void clearSelection() {
            selectJob(null);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            QIOCraftingMonitorEntry clicked = entryAt(mouseX, mouseY);
            super.onClick(mouseX, mouseY);
            if (clicked == null) {
                lastClickedJobId = null;
                lastClickTick = Long.MIN_VALUE;
                return;
            }
            boolean doubleClick = clicked.getEntryId().equals(lastClickedJobId) &&
                  tick - lastClickTick <= DOUBLE_CLICK_TICKS;
            lastClickedJobId = clicked.getEntryId();
            lastClickTick = tick;
            if (doubleClick) {
                openPlan(clicked);
                lastClickedJobId = null;
                lastClickTick = Long.MIN_VALUE;
            }
        }

        @Override
        protected void renderElements(int mouseX, int mouseY, float partialTicks) {
            int first = getCurrentSelection();
            int count = Math.min(getFocusedElements(),
                  Math.max(0, visibleEntries.size() - first));
            for (int row = 0; row < count; row++) {
                QIOCraftingMonitorEntry entry = visibleEntries.get(first + row);
                int rowY = relativeY + 1 + row * elementHeight;
                boolean hovered = mouseX >= getX() + 1 &&
                      mouseX < getX() + barXShift - 1 &&
                      mouseY >= getY() + 1 + row * elementHeight &&
                      mouseY < getY() + 1 + (row + 1) * elementHeight;
                if (entry.getEntryId().equals(selectedJobId)) {
                    GuiUtils.fill(relativeX + 1, rowY, relativeX + barXShift - 1,
                          rowY + elementHeight, 0x80608AA0);
                } else if (hovered) {
                    GuiUtils.fill(relativeX + 1, rowY, relativeX + barXShift - 1,
                          rowY + elementHeight, 0x405F737D);
                }
                long total = Math.max(1, entry.getTotalOperations());
                int available = Math.max(0, barXShift - 4);
                int progress = (int) Math.min(available, Math.round(available *
                      (entry.getCompletedOperations() / (double) total)));
                GuiUtils.fill(relativeX + 2, rowY + elementHeight - 2,
                      relativeX + barXShift - 2, rowY + elementHeight - 1,
                      0xFFB5BEC2);
                if (progress > 0) {
                    GuiUtils.fill(relativeX + 2, rowY + elementHeight - 2,
                          relativeX + 2 + progress, rowY + elementHeight - 1,
                          0xFF4C9A6C);
                }
            }
        }

        @Override
        public void renderForeground(int mouseX, int mouseY) {
            super.renderForeground(mouseX, mouseY);
            if (visibleEntries.isEmpty()) {
                String key = !state.isValid() ?
                      "gui.mekanismqioprocessing.session_waiting" :
                      state.getFrequencyUUID() == null ?
                            "gui.mekanismqioprocessing.bind_frequency_first" :
                            cache().getSourceRevision() < 0 ?
                                  "gui.mekanismqioprocessing.loading" :
                                  "gui.mekanismqioprocessing.monitor_empty";
                String message = translation(key);
                String trimmed = getFont().trimStringToWidth(message,
                      Math.max(8, barXShift - 8));
                getFont().drawString(trimmed, relativeX + 4,
                      relativeY + Math.max(4, (height - getFont().FONT_HEIGHT) / 2),
                      0xFF5A6266);
                return;
            }
            int first = getCurrentSelection();
            int count = Math.min(getFocusedElements(),
                  Math.max(0, visibleEntries.size() - first));
            for (int row = 0; row < count; row++) {
                QIOCraftingMonitorEntry entry = visibleEntries.get(first + row);
                int rowY = relativeY + 3 + row * elementHeight;
                QIOGuiResourceRenderer.renderIcon(gui(), entry.getRootResource(),
                      relativeX + 3, rowY + 2, 16);
                int textX = relativeX + 22;
                int textWidth = Math.max(8, barXShift - 27);
                String stateText = localizedState(entry.getState());
                int stateWidth = Math.min(116, Math.max(50,
                      getFont().getStringWidth(stateText)));
                String name = getFont().trimStringToWidth(
                      QIOGuiResourceRenderer.name(entry.getRootResource()),
                      Math.max(8, textWidth - stateWidth - 8));
                getFont().drawString(name, textX, rowY, 0xFF40484C);
                String trimmedState = getFont().trimStringToWidth(stateText, stateWidth);
                getFont().drawString(trimmedState,
                      relativeX + barXShift - 4 - getFont().getStringWidth(trimmedState),
                      rowY, stateColor(entry));

                String amount = translation(
                      "gui.mekanismqioprocessing.monitor_delivered_total",
                      TextUtils.format(entry.getDeliveredAmount()),
                      TextUtils.format(entry.getRootAmount()));
                String priority = translation(
                      "gui.mekanismqioprocessing.monitor_priority",
                      TextUtils.format(entry.getBasePriority()));
                String source = translation("gui.mekanismqioprocessing.monitor_source_" +
                      entry.getSource().toLowerCase(Locale.ROOT));
                String operations = translation(
                      "gui.mekanismqioprocessing.monitor_operation_progress",
                      TextUtils.format(entry.getCompletedOperations()),
                      TextUtils.format(entry.getTotalOperations()));
                int amountWidth = Math.max(54, textWidth * 23 / 100);
                int priorityWidth = Math.max(50, textWidth * 20 / 100);
                int sourceWidth = Math.max(48, textWidth * 20 / 100);
                int operationWidth = Math.max(24,
                      textWidth - amountWidth - priorityWidth - sourceWidth);
                drawColumn(amount, textX, rowY + 11, amountWidth);
                drawColumn(priority, textX + amountWidth, rowY + 11,
                      priorityWidth);
                drawColumn(source, textX + amountWidth + priorityWidth,
                      rowY + 11, sourceWidth);
                drawColumn(operations,
                      textX + amountWidth + priorityWidth + sourceWidth,
                      rowY + 11, operationWidth);

                if (entry.ownsExecutionSlot()) {
                    GuiUtils.fill(relativeX + barXShift - 7, rowY + 12,
                          relativeX + barXShift - 4, rowY + 15, 0xFF42A56B);
                } else if (entry.getMissingResourceTypes() > 0) {
                    GuiUtils.fill(relativeX + barXShift - 7, rowY + 12,
                          relativeX + barXShift - 4, rowY + 15, 0xFFB85858);
                }
            }
        }

        private void drawColumn(String text, int x, int y, int width) {
            if (width <= 4) return;
            getFont().drawString(getFont().trimStringToWidth(text, width - 4),
                  x, y, 0xFF596368);
        }

        @Override
        public void renderToolTip(int mouseX, int mouseY) {
            super.renderToolTip(mouseX, mouseY);
            QIOCraftingMonitorEntry entry = entryAt(mouseX, mouseY);
            if (entry == null) return;
            List<String> extra = new ArrayList<>();
            extra.add(translation("gui.mekanismqioprocessing.monitor_state",
                  localizedState(entry.getState())));
            extra.add(translation("gui.mekanismqioprocessing.monitor_source",
                  translation("gui.mekanismqioprocessing.monitor_source_" +
                        entry.getSource().toLowerCase(Locale.ROOT))));
            extra.add(translation("gui.mekanismqioprocessing.monitor_delivered_total",
                  TextUtils.format(entry.getDeliveredAmount()),
                  TextUtils.format(entry.getRootAmount())));
            extra.add(translation("gui.mekanismqioprocessing.monitor_priority",
                  TextUtils.format(entry.getBasePriority())));
            extra.add(translation("gui.mekanismqioprocessing.monitor_operation_progress",
                  TextUtils.format(entry.getCompletedOperations()),
                  TextUtils.format(entry.getTotalOperations())));
            if (entry.getMissingResourceTypes() > 0) {
                extra.add(translation("gui.mekanismqioprocessing.monitor_missing_types",
                      Integer.toString(entry.getMissingResourceTypes())));
            }
            if (entry.ownsExecutionSlot()) {
                extra.add(translation("gui.mekanismqioprocessing.monitor_slot_held"));
            }
            if (!entry.getDiagnostic().isEmpty()) extra.add(entry.getDiagnostic());
            extra.add(translation("gui.mekanismqioprocessing.monitor_job_id",
                  entry.getEntryId().toString().substring(0, 8)));
            extra.add(translation(
                  "gui.mekanismqioprocessing.monitor_double_click_hint"));
            ItemStack item = QIOGuiResourceRenderer.item(entry.getRootResource());
            if (!item.isEmpty()) {
                gui().renderItemTooltipWithExtra(item, mouseX, mouseY, extra);
            } else {
                List<String> tooltip = QIOGuiResourceRenderer.tooltip(entry.getRootResource());
                tooltip.addAll(extra);
                displayTooltips(tooltip, mouseX, mouseY, 280);
            }
        }

        @Nullable
        @Override
        public Object getIngredient(double mouseX, double mouseY) {
            QIOCraftingMonitorEntry entry = entryAt(mouseX, mouseY);
            return entry == null ? null :
                  QIOGuiResourceRenderer.ingredient(entry.getRootResource());
        }

        @Nullable
        private QIOCraftingMonitorEntry entryAt(double mouseX, double mouseY) {
            if (mouseX < getX() + 1 || mouseX >= getX() + barXShift - 1 ||
                  mouseY < getY() + 1 || mouseY >= getY() + height - 1) return null;
            int row = (int) ((mouseY - getY() - 1) / elementHeight);
            int index = getCurrentSelection() + row;
            return index < 0 || index >= visibleEntries.size() ? null :
                  visibleEntries.get(index);
        }

        private int stateColor(QIOCraftingMonitorEntry entry) {
            if (entry.getMissingResourceTypes() > 0) return 0xFFA06845;
            return active(entry) ? 0xFF347A52 : 0xFF667177;
        }
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

        @Override
        public boolean equals(Object other) {
            return other instanceof SessionKey key &&
                  targetRevision == key.targetRevision &&
                  accessRevision == key.accessRevision &&
                  Objects.equals(nonce, key.nonce) &&
                  Objects.equals(terminal, key.terminal) &&
                  Objects.equals(frequency, key.frequency);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nonce, terminal, frequency, targetRevision,
                  accessRevision);
        }
    }
}
