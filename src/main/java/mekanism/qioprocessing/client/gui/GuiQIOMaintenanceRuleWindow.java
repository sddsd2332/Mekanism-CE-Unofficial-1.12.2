package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.text.BackgroundType;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceRule;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.inventory.container.QIOMaintenanceResourcePageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOSmartProcessingClientCache;
import mekanism.qioprocessing.common.network.PacketQIOMaintenanceResourcePageRequest;
import mekanism.qioprocessing.common.network.QIOProcessingPacketHandler;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingResourceEntry;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingResourceFilter;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Resource picker and compact target/batch editor for one maintenance rule. */
final class GuiQIOMaintenanceRuleWindow extends GuiWindow {

    private static final int COLUMNS = 11;
    private static final int ROWS = 5;
    private static final int PAGE_SIZE = COLUMNS * ROWS;
    private static final long REQUEST_TIMEOUT = 100;
    private static final long SEARCH_DEBOUNCE = 6;
    private static final long CATALOG_REFRESH_INTERVAL = 40;

    private final GuiQIOMaintenanceRulesPanel owner;
    private final QIOMaintenanceResourcePageContainer container;
    private final QIOProcessingTerminalContainerState state;
    private final GuiTextField searchField;
    private final GuiTextField targetField;
    private final GuiTextField batchField;
    private final GuiQIOSmartProcessingResourceGrid grid;
    private final MekanismButton confirmButton;
    private final MekanismButton deleteButton;
    private final List<GuiQIOSmartProcessingFilterTab> filterTabs = new ArrayList<>();
    private final SessionKey openedSession;
    private QIOSmartProcessingResourceFilter selectedFilter =
          QIOSmartProcessingResourceFilter.ALL;
    @Nullable private QIOSmartProcessingResourceEntry selected;
    @Nullable private QIOMaintenanceRule editingRule;
    @Nullable private Pending pending;
    @Nullable private UUID requestId;
    private long clientTick;
    private long searchEditedAt = Long.MIN_VALUE;
    private long lastCatalogRequestTick = Long.MIN_VALUE;
    private boolean searchDirty;
    private boolean closed;

    GuiQIOMaintenanceRuleWindow(IGuiWrapper gui, int x, int y,
          GuiQIOMaintenanceRulesPanel owner, QIOMaintenanceResourcePageContainer container,
          @Nullable QIOMaintenanceRule initialRule,
          @Nullable PortableResourceDescriptor initialResource,
          SelectedWindowData windowData) {
        super(gui, x, y, 220, 190, windowData);
        this.owner = Objects.requireNonNull(owner, "owner");
        this.container = Objects.requireNonNull(container, "container");
        state = container.getTerminalState();
        openedSession = SessionKey.capture(state);
        interactionStrategy = InteractionStrategy.ALL;

        searchField = addChild(new GuiTextField(gui, this, relativeX + 7, relativeY + 18,
              206, 12).setMaxLength(64).setBackground(BackgroundType.ELEMENT_HOLDER)
              .setResponder(ignored -> {
                  searchDirty = true;
                  searchEditedAt = clientTick;
              }));
        grid = addChild(new GuiQIOSmartProcessingResourceGrid(gui, relativeX + 2,
              relativeY + 33, COLUMNS, ROWS, container::getMaintenanceResourceClientCache,
              this::selectedResource, this::selectResource).rotatingSelection());
        targetField = addChild(numberField(gui, relativeX + 64, relativeY + 127));
        batchField = addChild(numberField(gui, relativeX + 64, relativeY + 145));
        confirmButton = addChild(new MekanismButton(gui, relativeX + 121, relativeY + 127,
              92, 32, new TextComponentTranslation(
                    "gui.mekanismqioprocessing.maintenance_confirm"), this::confirm, null));
        deleteButton = addChild(new MekanismButton(gui, relativeX + 7, relativeY + 163,
              206, 14, new TextComponentTranslation(
                    "gui.mekanismqioprocessing.rule_delete"), this::delete, null));

        QIOSmartProcessingResourceFilter[] filters = QIOSmartProcessingResourceFilter.values();
        for (int index = 0; index < filters.length; index++) {
            QIOSmartProcessingResourceFilter filter = filters[index];
            filterTabs.add(addChild(new GuiQIOSmartProcessingFilterTab(gui, filter,
                  relativeX - 26, relativeY + 20 + index * 28,
                  () -> selectFilter(filter))));
        }
        updateFilterTabs();
        targetField.setTextSilently(initialRule == null ? "1" :
              Long.toString(initialRule.getTargetAmount()));
        batchField.setTextSilently(initialRule == null ? "1" :
              Long.toString(initialRule.getMaximumSingleRequest()));
        if (openedSession != null) {
            QIOSmartProcessingClientCache cache = cache();
            cache.beginSession(openedSession.nonce);
            sendPage(0);
        }
        PortableResourceDescriptor openedResource = initialRule == null ? initialResource :
              initialRule.getResource();
        if (openedResource != null) {
            selected = new QIOSmartProcessingResourceEntry(openedResource, 0, 0,
                  0, 0, true, false);
        }
        editingRule = initialRule;
        updateButtons();
    }

    @Override
    public void tick() {
        super.tick();
        clientTick++;
        SessionKey current = SessionKey.capture(state);
        if (openedSession == null || !openedSession.equals(current)) {
            closeLater();
            return;
        }
        QIOSmartProcessingClientCache cache = cache();
        if (pending != null && pending.generation != cache.getPageGeneration()) {
            pending = null;
            requestId = null;
            refreshSelected();
        }
        if (pending != null && clientTick - pending.sentAt >= REQUEST_TIMEOUT) {
            cache.cancelExpectedPageRequest(requestId);
            pending = null;
            requestId = null;
        }
        if (searchDirty && clientTick - searchEditedAt >= SEARCH_DEBOUNCE) {
            searchDirty = false;
            resetDirectory(selectedFilter, normalizedSearch());
        }
        if (pending == null) {
            int missing = grid.getMissingPageOffset(PAGE_SIZE);
            if (missing >= 0) {
                sendPage(missing);
            } else if (clientTick - lastCatalogRequestTick >= CATALOG_REFRESH_INTERVAL) {
                sendPage(grid.getFirstVisiblePageOffset(PAGE_SIZE));
            }
        }
        updateButtons();
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawTitleText(new TextComponentTranslation(
              "gui.mekanismqioprocessing.maintenance_rule_title"), 5);
        drawScaledScrollingString(new TextComponentTranslation(
              "gui.mekanismqioprocessing.maintenance_target"), 8, 130,
              mekanism.client.render.IFancyFontRenderer.TextAlignment.LEFT, 0x404040, 52, 1,
              false, 1F, getTimeOpened());
        drawScaledScrollingString(new TextComponentTranslation(
              "gui.mekanismqioprocessing.maintenance_batch"), 8, 148,
              mekanism.client.render.IFancyFontRenderer.TextAlignment.LEFT, 0x404040, 52, 1,
              false, 1F, getTimeOpened());
        if (searchField.isEmpty() && !searchField.isFocused()) {
            drawScaledScrollingString(new TextComponentTranslation(
                  "gui.mekanismqioprocessing.config_search"), 9, 21,
                  mekanism.client.render.IFancyFontRenderer.TextAlignment.LEFT, 0x707070, 200, 0,
                  false, 0.72F, getTimeOpened());
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            cache().clear();
            super.close();
        }
    }

    private GuiTextField numberField(IGuiWrapper gui, int x, int y) {
        return new GuiTextField(gui, x, y, 52, 14).setMaxLength(19)
              .setInputValidator(Character::isDigit);
    }

    private QIOSmartProcessingClientCache cache() {
        return container.getMaintenanceResourceClientCache();
    }

    private void sendPage(int offset) {
        if (pending != null || openedSession == null || openedSession.frequency == null) return;
        requestId = UUID.randomUUID();
        if (!cache().expectPageRequest(requestId, selectedFilter, normalizedSearch(), offset)) {
            requestId = null;
            return;
        }
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOMaintenanceResourcePageRequest.Message.create(
                    container.getTerminalWindowId(), state, requestId,
                    selectedFilter, normalizedSearch(), offset, PAGE_SIZE,
                    cache().getSourceRevision()));
        pending = new Pending(cache().getPageGeneration(), clientTick);
        lastCatalogRequestTick = clientTick;
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
        if (pending != null) cache().cancelExpectedPageRequest(requestId);
        pending = null;
        requestId = null;
        selected = null;
        editingRule = null;
        grid.resetScroll();
        cache().resetDirectory(filter, query);
        sendPage(0);
    }

    private void selectResource(QIOSmartProcessingResourceEntry entry) {
        if (entry == null) return;
        selected = entry;
        QIOMaintenanceRule existing = owner.findRule(entry.getResource());
        editingRule = existing;
        if (existing != null) {
            targetField.setTextSilently(Long.toString(existing.getTargetAmount()));
            batchField.setTextSilently(Long.toString(existing.getMaximumSingleRequest()));
        } else {
            targetField.setTextSilently("1");
            batchField.setTextSilently("1");
        }
    }

    private void refreshSelected() {
        if (selected == null) return;
        PortableResourceDescriptor resource = selected.getResource();
        for (QIOSmartProcessingResourceEntry entry : cache().getResources()) {
            if (resource.equals(entry.getResource())) {
                selected = entry;
                return;
            }
        }
    }

    private void confirm() {
        if (selected == null || !state.isValid() || state.getFrequencyUUID() == null) return;
        try {
            long target = Long.parseLong(targetField.getText().trim());
            long batch = Long.parseLong(batchField.getText().trim());
            if (target <= 0 || batch <= 0) return;
            if (owner.configureRule(selected.getResource(), target, batch, editingRule)) {
                close();
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void delete() {
        if (editingRule != null && owner.deleteRule(editingRule)) {
            close();
        }
    }

    private String normalizedSearch() {
        return searchField.getText().trim().toLowerCase(Locale.ROOT);
    }

    private PortableResourceDescriptor selectedResource() {
        return selected == null ? null : selected.getResource();
    }

    private void closeLater() {
        minecraft.addScheduledTask(this::close);
    }

    private void updateButtons() {
        confirmButton.active = selected != null && state.isValid() &&
              state.getFrequencyUUID() != null && positive(targetField) && positive(batchField);
        deleteButton.visible = editingRule != null;
        deleteButton.active = editingRule != null && state.isValid() &&
              state.getFrequencyUUID() != null;
    }

    private static boolean positive(GuiTextField field) {
        try {
            return Long.parseLong(field.getText().trim()) > 0;
        } catch (RuntimeException ignored) {
            return false;
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
        private final UUID terminalUUID;
        private final long targetRevision;
        private final UUID frequency;
        private final long accessRevision;

        private SessionKey(UUID nonce, UUID terminalUUID, long targetRevision, UUID frequency,
              long accessRevision) {
            this.nonce = nonce;
            this.terminalUUID = terminalUUID;
            this.targetRevision = targetRevision;
            this.frequency = frequency;
            this.accessRevision = accessRevision;
        }

        @Nullable
        private static SessionKey capture(QIOProcessingTerminalContainerState state) {
            return state.isValid() ? new SessionKey(state.getSessionNonce(), state.getTerminalUUID(),
                  state.getTargetRevision(), state.getFrequencyUUID(), state.getAccessRevision()) : null;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SessionKey other)) return false;
            return targetRevision == other.targetRevision && accessRevision == other.accessRevision &&
                  Objects.equals(nonce, other.nonce) && Objects.equals(terminalUUID, other.terminalUUID) &&
                  Objects.equals(frequency, other.frequency);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nonce, terminalUUID, targetRevision, frequency, accessRevision);
        }
    }
}
