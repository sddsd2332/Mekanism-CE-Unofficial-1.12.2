package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.text.BackgroundType;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.util.text.TextUtils;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.inventory.container.QIOSmartProcessingClientCache;
import mekanism.qioprocessing.common.inventory.container.QIOSmartProcessingPageContainer;
import mekanism.qioprocessing.common.network.PacketQIOSmartProcessingPreviewAction;
import mekanism.qioprocessing.common.network.PacketQIOSmartProcessingResourcePageRequest;
import mekanism.qioprocessing.common.network.QIOProcessingPacketHandler;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingPreviewSnapshot;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingResourceEntry;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingResourceFilter;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Virtualized order catalog and asynchronous production-plan editor. */
/**
 * QIO 处理模块中的 GuiQIOSmartProcessingOrderWindow 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class GuiQIOSmartProcessingOrderWindow extends GuiWindow
      implements IRecipeViewerGhostTarget {

    private static final int COLUMNS = 11;
    private static final int ROWS = 5;
    private static final int PAGE_SIZE = COLUMNS * ROWS;
    private static final long TIMEOUT = 100;
    // The first catalog build may still include a large machine-provider route index.
    private static final long INITIAL_PAGE_TIMEOUT = 200;
    private static final long CATALOG_REFRESH_INTERVAL = 40;
    private static final long PREVIEW_POLL_INTERVAL = 1;
    private static final long SEARCH_DEBOUNCE = 6;

    private final QIOSmartProcessingPageContainer container;
    private final QIOProcessingTerminalContainerState state;
    private final GuiQIOSmartProcessingResourceGrid grid;
    private final GuiTextField searchField;
    private final GuiTextField amountField;
    private final MekanismButton previewButton;
    private final List<GuiQIOSmartProcessingFilterTab> filterTabs = new ArrayList<>();
    private QIOSmartProcessingResourceFilter selectedFilter =
          QIOSmartProcessingResourceFilter.ALL;
    private QIOSmartProcessingResourceEntry selected;
    private SessionKey session;
    private long clientTick;
    private long lastOutboundTick = Long.MIN_VALUE;
    private long lastCatalogRequestTick = Long.MIN_VALUE;
    private long lastPreviewPollTick = Long.MIN_VALUE;
    private long searchEditedAt = Long.MIN_VALUE;
    private boolean searchDirty;
    private Pending pagePending;
    private Pending actionPending;
    private UUID pageRequestId;
    private UUID actionRequestId;
    private QueuedAction queuedAction;
    private long lastPageGeneration = -1;
    private long lastSourceRevision = -1;
    private long lastPreviewGeneration = -1;
    private long lastRecipeViewerTargetGeneration = -1;
    private long initialRequestNotBeforeTick;
    private GuiQIOSmartProcessingAnalysisWindow analysisWindow;
    private UUID lastAnalysisPreviewId;

    public GuiQIOSmartProcessingOrderWindow(IGuiWrapper gui, int x, int y,
          QIOSmartProcessingPageContainer container, SelectedWindowData windowData) {
        super(gui, x, y, 220, 190, windowData);
        this.container = container;
        state = container.getTerminalState();
        interactionStrategy = InteractionStrategy.ALL;

        searchField = addChild(new GuiTextField(gui, this, relativeX + 7, relativeY + 18,
              206, 12).setMaxLength(64).setBackground(BackgroundType.ELEMENT_HOLDER)
              .setResponder(this::onSearchChanged));
        grid = addChild(new GuiQIOSmartProcessingResourceGrid(gui, relativeX + 2,
              relativeY + 33, COLUMNS, ROWS, container::getSmartProcessingClientCache,
              this::selectedResource, this::selectResource).rotatingSelection());
        amountField = addChild(new GuiTextField(gui, this, relativeX + 7, relativeY + 127,
              54, 14).setMaxLength(18).setInputValidator(Character::isDigit));
        amountField.setText("1");
        amountField.setResponder(ignored -> onOrderInputChanged());
        previewButton = addChild(new MekanismButton(gui, relativeX + 64, relativeY + 127,
              149, 14, new TextComponentTranslation(
                     "gui.mekanismqioprocessing.order_preview"), this::requestPreview, null));
        addChild(new GuiQIOPlanningStatusScreen(gui, relativeX + 7, relativeY + 145,
              206, 40, false, this::statusContent));

        QIOSmartProcessingResourceFilter[] filters =
              QIOSmartProcessingResourceFilter.values();
        for (int index = 0; index < filters.length; index++) {
            QIOSmartProcessingResourceFilter filter = filters[index];
            GuiQIOSmartProcessingFilterTab tab = addChild(
                  new GuiQIOSmartProcessingFilterTab(gui, filter, relativeX - 26,
                        relativeY + 20 + index * 28, () -> selectFilter(filter)));
            filterTabs.add(tab);
        }
        updateFilterTabs();
    }

    @Override
    public void tick() {
        super.tick();
        clientTick++;
        SessionKey current = SessionKey.of(state);
        if (!Objects.equals(current, session)) {
            session = current;
            clearForSession();
            initialRequestNotBeforeTick = clientTick + 1;
        }

        QIOSmartProcessingClientCache cache = cache();
        if (pagePending != null && pagePending.generation != cache.getPageGeneration()) {
            pagePending = null;
            pageRequestId = null;
            refreshSelectedFromCache();
        }
        if (actionPending != null && actionPending.generation != cache.getPreviewGeneration()) {
            actionPending = null;
            actionRequestId = null;
        }
        long pageTimeout = cache.getSourceRevision() < 0 ? INITIAL_PAGE_TIMEOUT : TIMEOUT;
        if (pagePending != null && QIOClientTiming.elapsed(clientTick, pagePending.sentAt,
              pageTimeout)) {
            cache.cancelExpectedPageRequest(pageRequestId);
            pagePending = null;
            pageRequestId = null;
        }
        if (actionPending != null && QIOClientTiming.elapsed(clientTick, actionPending.sentAt,
              TIMEOUT)) {
            cache.cancelExpectedPreviewRequest(actionRequestId);
            actionPending = null;
            actionRequestId = null;
        }

        if (searchDirty && QIOClientTiming.elapsed(clientTick, searchEditedAt,
              SEARCH_DEBOUNCE)) {
            searchDirty = false;
            resetDirectory(selectedFilter, normalizedSearch());
        }
        applyRecipeViewerTarget(cache);
        runQueuedAction();
        cancelMismatchedPreview();
        QIOSmartProcessingPreviewSnapshot preview = cache.getPreview();
        if (preview != null && (preview.getState().name().equals("PREPARING") ||
              preview.getState().name().equals("PLANNING")) &&
               actionPending == null && QIOClientTiming.elapsed(clientTick,
                     lastPreviewPollTick, PREVIEW_POLL_INTERVAL)) {
            lastPreviewPollTick = clientTick;
            poll();
        } else if (actionPending == null && pagePending == null && session != null &&
              session.frequency != null && clientTick >= initialRequestNotBeforeTick) {
            int missingOffset = grid.getMissingPageOffset(PAGE_SIZE);
            if (missingOffset >= 0) {
                sendPage(missingOffset);
            } else if (QIOClientTiming.elapsed(clientTick, lastCatalogRequestTick,
                  CATALOG_REFRESH_INTERVAL)) {
                sendPage(grid.getFirstVisiblePageOffset(PAGE_SIZE));
            }
        }
        if (cache.getPageGeneration() != lastPageGeneration) {
            lastPageGeneration = cache.getPageGeneration();
            if (lastSourceRevision >= 0 && cache.getSourceRevision() >= 0 &&
                  lastSourceRevision != cache.getSourceRevision()) {
                grid.resetScroll();
            }
            lastSourceRevision = cache.getSourceRevision();
            refreshSelectedFromCache();
        }
        if (cache.getPreviewGeneration() != lastPreviewGeneration) {
            lastPreviewGeneration = cache.getPreviewGeneration();
            handlePreviewProjection();
        }
        updateButtons();
    }

    private void sendPage(int offset) {
        if (session == null || session.frequency == null || pagePending != null ||
              lastOutboundTick == clientTick) return;
        QIOSmartProcessingClientCache cache = cache();
        pageRequestId = UUID.randomUUID();
        if (!cache.expectPageRequest(pageRequestId, selectedFilter, normalizedSearch(), offset)) {
            pageRequestId = null;
            return;
        }
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOSmartProcessingResourcePageRequest.Message.create(
                    container.getTerminalWindowId(), state,
                    pageRequestId, selectedFilter, normalizedSearch(), offset, PAGE_SIZE,
                    cache.getSourceRevision()));
        pagePending = new Pending(cache.getPageGeneration(), clientTick);
        lastCatalogRequestTick = clientTick;
        lastOutboundTick = clientTick;
    }

    private void requestPreview() {
        long amount = requestedAmount();
        if (selected == null || !selected.isSchedulable() || amount <= 0 ||
              actionPending != null || session == null) return;
        if (lastOutboundTick == clientTick) {
            queuedAction = QueuedAction.PREVIEW;
            return;
        }
        actionRequestId = UUID.randomUUID();
        if (!cache().expectPreviewRequest(actionRequestId)) {
            actionRequestId = null;
            return;
        }
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOSmartProcessingPreviewAction.Message.request(
                    container.getTerminalWindowId(), state,
                    actionRequestId, selected.getResource().write(), amount, 0,
                    selected.isMergeable()));
        actionPending = new Pending(cache().getPreviewGeneration(), clientTick);
        queuedAction = null;
        lastOutboundTick = clientTick;
    }

    private void poll() {
        QIOSmartProcessingPreviewSnapshot preview = cache().getPreview();
        if (preview == null || actionPending != null || lastOutboundTick == clientTick) return;
        sendSimpleAction(PacketQIOSmartProcessingPreviewAction.Action.POLL,
              preview.getPreviewId());
    }

    private void confirm() {
        QIOSmartProcessingPreviewSnapshot preview = cache().getPreview();
        long amount = requestedAmount();
        if (!previewReadyAndCurrent() || selected == null || actionPending != null) return;
        if (lastOutboundTick == clientTick) {
            queuedAction = QueuedAction.CONFIRM;
            return;
        }
        actionRequestId = UUID.randomUUID();
        if (!cache().expectPreviewRequest(actionRequestId)) {
            actionRequestId = null;
            return;
        }
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOSmartProcessingPreviewAction.Message.confirm(
                    container.getTerminalWindowId(), state,
                    actionRequestId, preview.getPreviewId(), selected.getResource().write(),
                    amount));
        actionPending = new Pending(cache().getPreviewGeneration(), clientTick);
        queuedAction = null;
        lastOutboundTick = clientTick;
    }

    private void cancel() {
        QIOSmartProcessingPreviewSnapshot preview = cache().getPreview();
        if (preview != null && actionPending == null && lastOutboundTick == clientTick) {
            queuedAction = QueuedAction.CANCEL;
        } else if (preview != null && actionPending == null) {
            sendSimpleAction(PacketQIOSmartProcessingPreviewAction.Action.CANCEL,
                  preview.getPreviewId());
        }
    }

    private void sendSimpleAction(PacketQIOSmartProcessingPreviewAction.Action action,
          UUID previewId) {
        actionRequestId = UUID.randomUUID();
        if (!cache().expectPreviewRequest(actionRequestId)) {
            actionRequestId = null;
            return;
        }
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOSmartProcessingPreviewAction.Message.action(
                    container.getTerminalWindowId(), state,
                    actionRequestId, action, previewId));
        actionPending = new Pending(cache().getPreviewGeneration(), clientTick);
        if (action != PacketQIOSmartProcessingPreviewAction.Action.POLL) queuedAction = null;
        lastOutboundTick = clientTick;
    }

    private void runQueuedAction() {
        if (queuedAction == null || actionPending != null || lastOutboundTick == clientTick) return;
        QueuedAction action = queuedAction;
        queuedAction = null;
        switch (action) {
            case PREVIEW -> requestPreview();
            case CONFIRM -> confirm();
            case CANCEL -> cancel();
        }
    }

    private void selectFilter(QIOSmartProcessingResourceFilter filter) {
        if (filter == selectedFilter) return;
        selectedFilter = filter;
        updateFilterTabs();
        resetDirectory(filter, normalizedSearch());
    }

    private void updateFilterTabs() {
        for (GuiQIOSmartProcessingFilterTab tab : filterTabs) {
            tab.visible = tab.getFilter() != selectedFilter;
        }
    }

    private void resetDirectory(QIOSmartProcessingResourceFilter filter, String query) {
        if (pagePending != null) cache().cancelExpectedPageRequest(pageRequestId);
        pagePending = null;
        pageRequestId = null;
        selected = null;
        grid.resetScroll();
        cache().resetDirectory(filter, query);
        onOrderInputChanged();
        sendPage(0);
    }

    private void selectResource(QIOSmartProcessingResourceEntry entry) {
        if (entry == null || entry.getResource().equals(selectedResource())) return;
        selected = entry;
        onOrderInputChanged();
    }

    private void onSearchChanged(String ignored) {
        searchDirty = true;
        searchEditedAt = clientTick;
    }

    private void onOrderInputChanged() {
        queuedAction = null;
        dismissAnalysis(true);
        cancelMismatchedPreview();
    }

    private void cancelMismatchedPreview() {
        QIOSmartProcessingPreviewSnapshot preview = cache().getPreview();
        if (preview != null && preview.getState().name().equals("CONFIRMED") &&
              !previewMatchesCurrent(preview)) {
            cache().clearPreviewProjection();
            return;
        }
        if (preview != null && !previewMatchesCurrent(preview) && actionPending == null &&
              lastOutboundTick != clientTick) {
            sendSimpleAction(PacketQIOSmartProcessingPreviewAction.Action.CANCEL,
                  preview.getPreviewId());
        }
    }

    private boolean previewReadyAndCurrent() {
        QIOSmartProcessingPreviewSnapshot preview = cache().getPreview();
        return preview != null && preview.getState().name().equals("READY") &&
              previewMatchesCurrent(preview);
    }

    private boolean previewMatchesCurrent(QIOSmartProcessingPreviewSnapshot preview) {
        return selected != null && preview.getTarget() != null &&
              selected.getResource().equals(preview.getTarget()) &&
              requestedAmount() == preview.getAmount();
    }

    private long requestedAmount() {
        try {
            return Long.parseLong(amountField.getText().trim());
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private String normalizedSearch() {
        return searchField.getText().trim().toLowerCase(Locale.ROOT);
    }

    private PortableResourceDescriptor selectedResource() {
        return selected == null ? null : selected.getResource();
    }

    private void refreshSelectedFromCache() {
        PortableResourceDescriptor resource = selectedResource();
        if (resource == null) return;
        for (QIOSmartProcessingResourceEntry entry : cache().getResources()) {
            if (resource.equals(entry.getResource())) {
                selected = entry;
                return;
            }
        }
        if (cache().getSourceRevision() >= 0) selected = null;
    }

    private void applyRecipeViewerTarget(QIOSmartProcessingClientCache cache) {
        if (lastRecipeViewerTargetGeneration == cache.getRecipeViewerTargetGeneration()) return;
        lastRecipeViewerTargetGeneration = cache.getRecipeViewerTargetGeneration();
        PortableResourceDescriptor target = cache.getRecipeViewerTarget();
        if (target == null) return;
        for (QIOSmartProcessingResourceEntry entry : cache.getResources()) {
            if (entry.isSchedulable() && entry.getResource().equals(target)) {
                selectResource(entry);
                return;
            }
        }
    }

    private void clearForSession() {
        dismissAnalysis(false);
        cache().clear();
        if (session != null && session.nonce != null) cache().beginSession(session.nonce);
        pagePending = null;
        actionPending = null;
        pageRequestId = null;
        actionRequestId = null;
        queuedAction = null;
        selected = null;
        selectedFilter = QIOSmartProcessingResourceFilter.ALL;
        searchField.setText("");
        searchDirty = false;
        grid.resetScroll();
        lastSourceRevision = -1;
        lastAnalysisPreviewId = null;
        updateFilterTabs();
    }

    private void updateButtons() {
        QIOSmartProcessingPreviewSnapshot preview = cache().getPreview();
        boolean reusablePreview = preview == null || preview.getState().name().equals("CONFIRMED") ||
              preview.getState().name().equals("CANCELLED") ||
              preview.getState().name().equals("EXPIRED");
        previewButton.active = selected != null && selected.isSchedulable() &&
              requestedAmount() > 0 && reusablePreview && actionPending == null &&
              queuedAction == null && session != null;
    }

    private GuiQIOPlanningStatusScreen.Content statusContent() {
        List<ITextComponent> text = new ArrayList<>();
        if (session == null || session.frequency == null) {
            text.add(new TextComponentTranslation(
                  "gui.mekanismqioprocessing.order_need_frequency"));
            return GuiQIOPlanningStatusScreen.Content.lines(text);
        }
        QIOSmartProcessingPreviewSnapshot preview = cache().getPreview();
        if (preview == null) {
            if (pagePending != null && cache().getTotalSize() == 0) {
                text.add(new TextComponentTranslation(
                      "gui.mekanismqioprocessing.order_loading"));
            } else if (cache().getTotalSize() == 0) {
                text.add(new TextComponentTranslation(
                      "gui.mekanismqioprocessing.order_empty"));
            } else {
                text.add(new TextComponentTranslation(
                      "gui.mekanismqioprocessing.order_select"));
            }
            return GuiQIOPlanningStatusScreen.Content.lines(text);
        }
        boolean pending = preview.getState().name().equals("PREPARING") ||
              preview.getState().name().equals("PLANNING");
        text.add(new TextComponentTranslation(
              "gui.mekanismqioprocessing.order_preview_state",
              new TextComponentTranslation("gui.mekanismqioprocessing.order_state_" +
                    preview.getState().name().toLowerCase(Locale.ROOT)),
              TextUtils.format(preview.getPlannedOperations())));
        text.add(new TextComponentTranslation(
              "gui.mekanismqioprocessing.order_preview_summary",
              pending ? GuiQIOPlanningStatusScreen.duration(0, true) :
                    TextUtils.format(preview.getPlanEntries().size()),
              pending ? GuiQIOPlanningStatusScreen.duration(0, true) :
                    TextUtils.format(preview.getExternalRequirements().size())));
        text.add(new TextComponentTranslation(
              "gui.mekanismqioprocessing.order_planning_time",
              GuiQIOPlanningStatusScreen.duration(preview.getPlanningNanos(), pending)));

        ITextComponent details = GuiQIOPlanningStatusScreen.detailedTimings(preview, pending);
        if (preview.isStepsTruncated() || preview.isExternalRequirementsTruncated()) {
            details = GuiQIOPlanningStatusScreen.appendDetail(details,
                  new TextComponentTranslation(
                        "gui.mekanismqioprocessing.order_preview_truncated"));
        }
        if (!preview.getDiagnostic().isEmpty()) {
            details = GuiQIOPlanningStatusScreen.appendDetail(details,
                  diagnosticText(preview));
        }
        if (preview.getJobId() != null) {
            details = GuiQIOPlanningStatusScreen.appendDetail(details,
                  new TextComponentTranslation(
                        "gui.mekanismqioprocessing.order_job_created"));
        }
        return GuiQIOPlanningStatusScreen.Content.detail(text,
              new TextComponentTranslation(
                    "gui.mekanismqioprocessing.order_detailed_status"), details);
    }

    private static ITextComponent diagnosticText(QIOSmartProcessingPreviewSnapshot preview) {
        String diagnostic = preview.getDiagnostic();
        String cyclePrefix = "Circular QIO recipe route: ";
        if (preview.getPlanningStatus() ==
            mekanism.qioprocessing.common.planning.QIOPlanningResult.Status.UNRESOLVABLE_CYCLE &&
            diagnostic.startsWith(cyclePrefix)) {
            return new TextComponentTranslation(
                  "gui.mekanismqioprocessing.order_circular_route",
                  localizeCyclePath(diagnostic.substring(cyclePrefix.length())));
        }
        return new TextComponentString(diagnostic);
    }

    private static String localizeCyclePath(String path) {
        StringBuilder localized = new StringBuilder();
        for (String token : path.split(" -> ")) {
            if (localized.length() > 0) localized.append(" -> ");
            localized.append(localizeCycleResource(token));
        }
        return localized.toString();
    }

    private static String localizeCycleResource(String token) {
        if (token.startsWith("fluid:") || token.startsWith("gas:")) return token;
        String registryName = token;
        int metadata = 0;
        int metadataSeparator = token.lastIndexOf('@');
        if (metadataSeparator > 0 && metadataSeparator < token.length() - 1) {
            try {
                metadata = Integer.parseInt(token.substring(metadataSeparator + 1));
                registryName = token.substring(0, metadataSeparator);
            } catch (NumberFormatException ignored) {
                return token;
            }
        }
        try {
            Item item = Item.REGISTRY.getObject(new ResourceLocation(registryName));
            if (item == null || item == Items.AIR) return token;
            ItemStack stack = new ItemStack(item, 1, metadata);
            String name = stack.getDisplayName();
            return name == null || name.trim().isEmpty() ? token : name;
        } catch (RuntimeException ignored) {
            return token;
        }
    }

    @Override
    public IGhostIngredientConsumer getGhostHandler() {
        if (!state.isValid() || state.getSessionNonce() == null) return null;
        return new IGhostItemConsumer() {
            @Override
            public ItemStack supportedTarget(Object ingredient) {
                ItemStack stack = IGhostItemConsumer.super.supportedTarget(ingredient);
                if (stack == null) return null;
                try {
                    return cache().canSelectRecipeViewerTarget(state.getSessionNonce(),
                          PortableResourceDescriptor.item(stack)) ? stack : null;
                } catch (RuntimeException ignored) {
                    return null;
                }
            }

            @Override
            public void accept(Object ingredient) {
                ItemStack stack = supportedTarget(ingredient);
                if (stack != null) {
                    cache().selectRecipeViewerTarget(state.getSessionNonce(),
                          PortableResourceDescriptor.item(stack));
                }
            }
        };
    }

    @Override
    public void close() {
        dismissAnalysis(false);
        QIOSmartProcessingPreviewSnapshot preview = cache().getPreview();
        if (preview != null && actionPending == null && lastOutboundTick != clientTick) {
            sendSimpleAction(PacketQIOSmartProcessingPreviewAction.Action.CANCEL,
                  preview.getPreviewId());
        }
        super.close();
    }

    private void handlePreviewProjection() {
        QIOSmartProcessingPreviewSnapshot preview = cache().getPreview();
        if (preview == null) {
            dismissAnalysis(false);
            return;
        }
        if (preview.getState().name().equals("CONFIRMED")) {
            dismissAnalysis(false);
            return;
        }
        if (!previewMatchesCurrent(preview) ||
            !preview.getState().name().equals("READY") &&
            !preview.getState().name().equals("FAILED") ||
            preview.getPreviewId().equals(lastAnalysisPreviewId)) {
            return;
        }
        dismissAnalysis(false);
        lastAnalysisPreviewId = preview.getPreviewId();
        analysisWindow = new GuiQIOSmartProcessingAnalysisWindow(gui(),
              (getGuiWidth() - GuiQIOSmartProcessingAnalysisWindow.WIDTH) / 2, 2,
              preview, cache()::getPreview, this::confirm, this::analysisClosed);
        gui().addWindow(analysisWindow);
    }

    private void analysisClosed(UUID previewId, boolean submitted) {
        if (analysisWindow != null && previewId.equals(analysisWindow.getPreviewId())) {
            analysisWindow = null;
        }
        if (!submitted) cancelPreview(previewId);
    }

    private void cancelPreview(UUID previewId) {
        QIOSmartProcessingPreviewSnapshot preview = cache().getPreview();
        if (preview == null || !previewId.equals(preview.getPreviewId())) return;
        if (actionPending != null || lastOutboundTick == clientTick) {
            queuedAction = QueuedAction.CANCEL;
        } else {
            sendSimpleAction(PacketQIOSmartProcessingPreviewAction.Action.CANCEL, previewId);
        }
    }

    private void dismissAnalysis(boolean notifyParent) {
        GuiQIOSmartProcessingAnalysisWindow current = analysisWindow;
        analysisWindow = null;
        if (current != null) current.dismiss(notifyParent);
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawTitleText(new TextComponentTranslation(
              "gui.mekanismqioprocessing.order_title"), 6);
    }

    private QIOSmartProcessingClientCache cache() {
        return container.getSmartProcessingClientCache();
    }

    private static final class Pending {
        private final long generation;
        private final long sentAt;

        private Pending(long generation, long sentAt) {
            this.generation = generation;
            this.sentAt = sentAt;
        }
    }

    private enum QueuedAction {
        PREVIEW,
        CONFIRM,
        CANCEL
    }

    private static final class SessionKey {
        private final UUID nonce;
        private final UUID terminal;
        private final UUID frequency;
        private final long target;
        private final long access;

        private SessionKey(UUID nonce, UUID terminal, UUID frequency, long target, long access) {
            this.nonce = nonce;
            this.terminal = terminal;
            this.frequency = frequency;
            this.target = target;
            this.access = access;
        }

        private static SessionKey of(QIOProcessingTerminalContainerState state) {
            return state.isValid() ? new SessionKey(state.getSessionNonce(),
                  state.getTerminalUUID(), state.getFrequencyUUID(), state.getTargetRevision(),
                  state.getAccessRevision()) : null;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof SessionKey other && target == other.target && access == other.access &&
                  Objects.equals(nonce, other.nonce) && Objects.equals(terminal, other.terminal) &&
                  Objects.equals(frequency, other.frequency);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nonce, terminal, frequency, target, access);
        }
    }
}
