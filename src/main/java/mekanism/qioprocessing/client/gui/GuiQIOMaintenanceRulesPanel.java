package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiElementHolder;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceRule;
import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceRuleMutation;
import mekanism.qioprocessing.common.inventory.container.QIOMaintenanceResourcePageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOMaintenanceRulePageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.network.PacketQIOMaintenanceRuleMutation;
import mekanism.qioprocessing.common.network.PacketQIOMaintenanceRulePageRequest;
import mekanism.qioprocessing.common.network.QIOProcessingPacketHandler;
import mekanism.qioprocessing.common.terminal.QIOPageCursor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Main maintenance-terminal rule list and mutation coordinator. */
public final class GuiQIOMaintenanceRulesPanel extends GuiElement
      implements IRecipeViewerGhostTarget {

    private static final int PAGE_SIZE = 128;
    private static final long REQUEST_TIMEOUT_TICKS = 100;
    private static final long INITIAL_PAGE_TIMEOUT_TICKS = 20;
    private static final long EMPTY_PAGE_RECHECK_TICKS = 10;

    private final QIOMaintenanceRulePageContainer container;
    private final QIOMaintenanceResourcePageContainer resourceContainer;
    private final QIOProcessingTerminalContainerState terminalState;
    private final GuiQIOMaintenanceRuleList rows;
    private final MekanismButton configureButton;
    @Nullable private SessionKey requestedSession;
    @Nullable private PendingRequest pageRequest;
    @Nullable private PendingRequest mutationRequest;
    @Nullable private UUID mutationRequestId;
    @Nullable private QIOMaintenanceRule selectedRule;
    private long clientTick;
    private long lastRevision = Long.MIN_VALUE;
    private int lastCount = -1;
    private long initialRequestNotBeforeTick;
    private long emptyPageAtTick = -1;
    private boolean emptyPageRechecked;

    public GuiQIOMaintenanceRulesPanel(IGuiWrapper gui,
          QIOMaintenanceRulePageContainer container,
          int x, int y, int width, int height) {
        super(gui, x, y, width, height);
        this.container = Objects.requireNonNull(container, "container");
        if (!(container instanceof QIOMaintenanceResourcePageContainer resources)) {
            throw new IllegalArgumentException("Maintenance container lacks resource paging support");
        }
        resourceContainer = resources;
        terminalState = container.getTerminalState();
        addChild(new GuiElementHolder(gui, x + 1, y - 1, 204, 68));
        addChild(new GuiElementHolder(gui, x + 1, y + 67, 204, 22));
        rows = addChild(new GuiQIOMaintenanceRuleList(gui, x + 2, y, this::rules,
              this::selectRule, this::toggleRule));
        int buttonY = y + 68;
        configureButton = addChild(button(gui, x + 2, buttonY, 202,
              "gui.mekanismqioprocessing.maintenance_configure", this::openConfiguration));
        rebuildRows();
    }

    private MekanismButton button(IGuiWrapper gui, int x, int y, int width,
          String key, Runnable action) {
        return new MekanismButton(gui, x, y, width, 20,
              new TextComponentTranslation(key), action, null);
    }

    @Override
    public void tick() {
        super.tick();
        clientTick++;
        SessionKey current = SessionKey.capture(terminalState);
        if (!Objects.equals(current, requestedSession)) {
            requestedSession = current;
            clear();
            initialRequestNotBeforeTick = clientTick + 1;
        }
        acknowledgeResponses();
        retryTimedOutRequests();
        ensureInitialPage();
        long revision = container.getMaintenanceRuleClientCache().getSourceRevision();
        int count = container.getMaintenanceRuleClientCache().getRules().size();
        if (revision != lastRevision || count != lastCount) {
            lastRevision = revision;
            lastCount = count;
            rebuildRows();
        }
        if (pageRequest == null && rows.isAtLoadedEnd() &&
              container.getMaintenanceRuleClientCache().getNextCursor() != null) {
            requestMore();
        }
        updateButtons();
    }

    @Override
    public IRecipeViewerGhostTarget.IGhostIngredientConsumer getGhostHandler() {
        if (!terminalState.isValid() || mutationRequest != null ||
              container.getMaintenanceRuleClientCache().getSourceRevision() < 0) return null;
        return new IRecipeViewerGhostTarget.IGhostItemConsumer() {
            @Override
            public void accept(Object ingredient) {
                ItemStack stack = supportedTarget(ingredient);
                if (stack == null || stack.isEmpty()) return;
                PortableResourceDescriptor resource;
                try {
                    resource = PortableResourceDescriptor.item(stack);
                } catch (RuntimeException ignored) {
                    return;
                }
                QIOMaintenanceRule rule = findRule(resource);
                openWindow(rule, resource);
            }
        };
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == Keyboard.KEY_DELETE && visible && active &&
            selectedRule != null && mutationRequest == null && !hasOpenWindow() &&
            terminalState.isValid() && terminalState.getFrequencyUUID() != null) {
            return deleteRule(selectedRule);
        }
        return false;
    }

    @Override
    public int borderSize() {
        return 1;
    }

    QIOMaintenanceRule findRule(@Nullable PortableResourceDescriptor resource) {
        if (resource == null) return null;
        for (QIOMaintenanceRule rule : rules()) {
            if (resource.equals(rule.getResource())) return rule;
        }
        return null;
    }

    boolean configureRule(PortableResourceDescriptor resource, long target, long batch,
          @Nullable QIOMaintenanceRule editingRule) {
        try {
            if (editingRule == null || !resource.equals(editingRule.getResource())) {
                return sendMutation(QIOMaintenanceRuleMutation.create(resource, true,
                      target, target, batch, 0, 100));
            } else {
                return sendMutation(QIOMaintenanceRuleMutation.update(editingRule.getRuleId(),
                      editingRule.getRuleRevision(), editingRule.isEnabled(), target,
                      target, batch, editingRule.getJobPriority(),
                      editingRule.getRetryIntervalTicks()));
            }
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private List<QIOMaintenanceRule> rules() {
        return container.getMaintenanceRuleClientCache().getRules();
    }

    private void selectRule(int index) {
        List<QIOMaintenanceRule> rules = rules();
        QIOMaintenanceRule selected = index >= 0 && index < rules.size() ?
              rules.get(index) : null;
        selectedRule = selected != null && selected.equals(selectedRule) ? null : selected;
        rows.setSelectedIndex(selectedRule == null ? -1 : index);
    }

    private void openConfiguration() {
        openWindow(selectedRule, null);
    }

    private void openWindow(@Nullable QIOMaintenanceRule rule,
          @Nullable PortableResourceDescriptor ignoredResource) {
        if (!terminalState.isValid() || terminalState.getFrequencyUUID() == null ||
              hasOpenWindow()) return;
        gui().addWindow(new GuiQIOMaintenanceRuleWindow(gui(),
              (gui().getWidth() - 220) / 2, 15, this, resourceContainer, rule,
              ignoredResource,
              SelectedWindowData.UNSPECIFIED));
    }

    private boolean hasOpenWindow() {
        return gui() instanceof mekanism.client.gui.GuiMekanism<?> mekanismGui &&
              mekanismGui.getWindows().stream().anyMatch(
                    window -> window instanceof GuiQIOMaintenanceRuleWindow);
    }

    private void toggleRule(int index) {
        List<QIOMaintenanceRule> rules = rules();
        if (index < 0 || index >= rules.size()) return;
        QIOMaintenanceRule rule = rules.get(index);
        sendMutation(QIOMaintenanceRuleMutation.update(rule.getRuleId(),
              rule.getRuleRevision(), !rule.isEnabled(), rule.getTriggerAmount(),
              rule.getTargetAmount(), rule.getMaximumSingleRequest(),
              rule.getJobPriority(), rule.getRetryIntervalTicks()));
    }

    boolean deleteRule(@Nullable QIOMaintenanceRule rule) {
        return rule != null && sendMutation(QIOMaintenanceRuleMutation.delete(rule.getRuleId(),
              rule.getRuleRevision()));
    }

    private void requestMore() {
        if (pageRequest == null && terminalState.isValid() &&
              container.getMaintenanceRuleClientCache().getNextCursor() != null) {
            sendPage(container.getMaintenanceRuleClientCache().getNextCursor());
        }
    }

    private void sendPage(@Nullable QIOPageCursor cursor) {
        if (!terminalState.isValid() || pageRequest != null) return;
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOMaintenanceRulePageRequest.Message.create(
                    container.getTerminalWindowId(), terminalState,
                    PAGE_SIZE, cursor));
        pageRequest = new PendingRequest(
              container.getMaintenanceRuleClientCache().getPageGeneration(), clientTick);
    }

    private boolean sendMutation(QIOMaintenanceRuleMutation mutation) {
        if (mutationRequest != null ||
              container.getMaintenanceRuleClientCache().getSourceRevision() < 0) return false;
        mutationRequestId = UUID.randomUUID();
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOMaintenanceRuleMutation.Message.create(
                    container.getTerminalWindowId(), terminalState,
                    container.getMaintenanceRuleClientCache().getSourceRevision(), mutationRequestId,
                    mutation));
        mutationRequest = new PendingRequest(
              container.getMaintenanceRuleClientCache().getMutationGeneration(), clientTick);
        return true;
    }

    private void acknowledgeResponses() {
        if (pageRequest != null && pageRequest.generation !=
              container.getMaintenanceRuleClientCache().getPageGeneration()) {
            pageRequest = null;
            if (container.getMaintenanceRuleClientCache().getRules().isEmpty() &&
                !emptyPageRechecked && emptyPageAtTick < 0) {
                emptyPageAtTick = clientTick;
            } else if (!container.getMaintenanceRuleClientCache().getRules().isEmpty()) {
                emptyPageAtTick = -1;
                emptyPageRechecked = true;
            }
        }
        if (mutationRequest != null && mutationRequest.generation !=
              container.getMaintenanceRuleClientCache().getMutationGeneration()) {
            mutationRequest = null;
            mutationRequestId = null;
            selectedRule = null;
            rows.setSelectedIndex(-1);
            container.getMaintenanceRuleClientCache().clear();
            pageRequest = null;
            sendPage(null);
        }
    }

    private void retryTimedOutRequests() {
        boolean initialPage = container.getMaintenanceRuleClientCache().getSourceRevision() < 0;
        long pageTimeout = initialPage ? INITIAL_PAGE_TIMEOUT_TICKS : REQUEST_TIMEOUT_TICKS;
        if (pageRequest != null && clientTick - pageRequest.sentAtTick >= pageTimeout) {
            if (!initialPage) {
                container.getMaintenanceRuleClientCache().clear();
            }
            pageRequest = null;
            sendPage(null);
        }
        if (mutationRequest != null &&
              clientTick - mutationRequest.sentAtTick >= REQUEST_TIMEOUT_TICKS) {
            mutationRequest = null;
            mutationRequestId = null;
        }
    }

    private void ensureInitialPage() {
        if (!terminalState.isValid() || terminalState.getFrequencyUUID() == null ||
            pageRequest != null || clientTick < initialRequestNotBeforeTick) {
            return;
        }
        if (container.getMaintenanceRuleClientCache().getSourceRevision() < 0) {
            sendPage(null);
        } else if (!emptyPageRechecked && emptyPageAtTick >= 0 &&
                   clientTick - emptyPageAtTick >= EMPTY_PAGE_RECHECK_TICKS &&
                   container.getMaintenanceRuleClientCache().getRules().isEmpty()) {
            emptyPageRechecked = true;
            sendPage(null);
        }
    }

    private void rebuildRows() {
        int index = selectedRule == null ? -1 : rules().indexOf(selectedRule);
        if (index < 0) selectedRule = null;
        rows.setSelectedIndex(index);
    }

    private void clear() {
        container.getMaintenanceRuleClientCache().clear();
        pageRequest = null;
        mutationRequest = null;
        mutationRequestId = null;
        selectedRule = null;
        emptyPageAtTick = -1;
        emptyPageRechecked = false;
        rows.setSelectedIndex(-1);
    }

    private void updateButtons() {
        boolean ready = terminalState.isValid() && terminalState.getFrequencyUUID() != null &&
              mutationRequest == null && container.getMaintenanceRuleClientCache().getSourceRevision() >= 0;
        configureButton.active = ready && !hasOpenWindow();
    }

    private static final class PendingRequest {
        private final long generation;
        private final long sentAtTick;

        private PendingRequest(long generation, long sentAtTick) {
            this.generation = generation;
            this.sentAtTick = sentAtTick;
        }
    }

    private static final class SessionKey {
        private final UUID nonce;
        private final UUID terminalUUID;
        private final long targetRevision;
        private final UUID frequencyUUID;
        private final long accessRevision;

        private SessionKey(UUID nonce, UUID terminalUUID, long targetRevision, UUID frequencyUUID,
              long accessRevision) {
            this.nonce = nonce;
            this.terminalUUID = terminalUUID;
            this.targetRevision = targetRevision;
            this.frequencyUUID = frequencyUUID;
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
                  Objects.equals(frequencyUUID, other.frequencyUUID);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nonce, terminalUUID, targetRevision, frequencyUUID, accessRevision);
        }
    }
}
