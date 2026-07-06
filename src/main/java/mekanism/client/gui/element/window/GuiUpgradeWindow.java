package mekanism.client.gui.element.window;

import mekanism.api.Coord4D;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.DigitalButton;
import mekanism.client.gui.element.custom.GuiSupportedUpgrades;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.scroll.GuiUpgradeScrollList;
import mekanism.client.gui.element.slot.GuiVirtualSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.Upgrade;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import mekanism.common.network.PacketGuiInteract.GuiInteractMessage;
import mekanism.common.network.PacketGuiInteract.GuiInteraction;
import mekanism.common.network.PacketRemoveUpgrade.RemoveUpgradeMessage;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.LangUtils;
import mekanism.common.util.UpgradeUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextComponentString;

import java.util.LinkedHashMap;
import java.util.Map;

public class GuiUpgradeWindow extends GuiWindow {

    private static final int WIDTH = 198;

    private final Map<Upgrade, WrappedTextRenderer> upgradeTypeData = new LinkedHashMap<>();
    private final WrappedTextRenderer noSelection = new WrappedTextRenderer(this, LangUtils.localize("gui.upgrades.noSelection"));
    private final TileEntityContainerBlock tile;
    private final IUpgradeTile upgradeTile;
    private final DigitalButton removeButton;
    private final GuiUpgradeScrollList scrollList;
    private final GuiInnerScreen rightScreen;

    private long msSelected;

    public GuiUpgradeWindow(IGuiWrapper gui, int x, int y, TileEntityContainerBlock tile, IUpgradeTile upgradeTile, SelectedWindowData windowData) {
        super(gui, x, y, WIDTH, 76 + Math.max(18, 12 * GuiSupportedUpgrades.calculateNeededRows(gui)), windowData);
        if (windowData.type != WindowType.UPGRADE) {
            throw new IllegalArgumentException("Upgrade windows must have an upgrade window type");
        }
        this.tile = tile;
        this.upgradeTile = upgradeTile;
        interactionStrategy = InteractionStrategy.ALL;
        scrollList = addChild(new GuiUpgradeScrollList(gui, relativeX + 6, relativeY + 18, 50, upgradeTile.getComponent(), () -> {
            updateEnabledButtons();
            msSelected = GuiElement.getMillis();
        }));
        addChild(new  GuiSupportedUpgrades(gui, relativeX + 6, relativeY + 68, upgradeTile.getSupportedUpgradeTypes()));
        rightScreen = addChild(new GuiInnerScreen(gui, scrollList.getRelativeRight(), relativeY + 18, 59, 50));
        addChild(new GuiProgress(() -> this.upgradeTile.getScaledUpgradeProgress(), ProgressType.INSTALLING, gui, rightScreen.getRelativeRight() + 3, relativeY + 37));
        addChild(new GuiProgress(() -> 0, ProgressType.UNINSTALLING, gui, rightScreen.getRelativeRight() + 3, relativeY + 58));
        removeButton = addChild(new DigitalButton(gui, scrollList.getRelativeRight() + 1, relativeY + 54, 56, 12, MekanismLang.UPGRADE_UNINSTALL,
              (element, mouseX, mouseY) -> {
                  removeSelectedUpgrade();
                  return true;
              }).setTooltip(MekanismLang.UPGRADE_UNINSTALL_TOOLTIP.translate()));
        if (gui instanceof GuiMekanism<?> mekanismGui && mekanismGui.inventorySlots instanceof MekanismTileContainer<?> container) {
            addChild(new GuiVirtualSlot(this, SlotType.NORMAL, gui, rightScreen.getRelativeRight() + 2, relativeY + 18, container.getUpgradeSlot()));
            addChild(new GuiVirtualSlot(this, SlotType.NORMAL, gui, rightScreen.getRelativeRight() + 2, relativeY + 72, container.getUpgradeOutputSlot()));
            container.startTracking(MekanismContainer.UPGRADE_WINDOW, upgradeTile.getComponent());
            Mekanism.packetHandler.sendToServer(new GuiInteractMessage(GuiInteraction.CONTAINER_TRACK_UPGRADES, Coord4D.get(tile), MekanismContainer.UPGRADE_WINDOW));
        }
        updateEnabledButtons();
    }

    public GuiUpgradeWindow(IGuiWrapper gui, int x, int y, TileEntityContainerBlock tile, IUpgradeTile upgradeTile) {
        this(gui, x, y, tile, upgradeTile, new SelectedWindowData(WindowType.UPGRADE));
    }

    public GuiUpgradeWindow(IGuiWrapper gui, int x, int y, TileEntityContainerBlock tile, SelectedWindowData windowData) {
        this(gui, x, y, tile, (IUpgradeTile) tile, windowData);
    }

    public GuiUpgradeWindow(IGuiWrapper gui, int x, int y, TileEntityContainerBlock tile) {
        this(gui, x, y, tile, (IUpgradeTile) tile, new SelectedWindowData(WindowType.UPGRADE));
    }

    @Override
    public void close() {
        super.close();
        Mekanism.packetHandler.sendToServer(new GuiInteractMessage(GuiInteraction.CONTAINER_STOP_TRACKING, Coord4D.get(tile), MekanismContainer.UPGRADE_WINDOW));
        if (gui() instanceof GuiMekanism<?> mekanismGui && mekanismGui.inventorySlots instanceof MekanismContainer container) {
            container.stopTracking(MekanismContainer.UPGRADE_WINDOW);
        }
    }

    @Override
    public void tick() {
        super.tick();
        updateEnabledButtons();
    }

    private void updateEnabledButtons() {
        removeButton.active = scrollList.hasSelection();
    }

    private void removeSelectedUpgrade() {
        Upgrade selected = scrollList.getSelection();
        if (selected != null) {
            Mekanism.packetHandler.sendToServer(new RemoveUpgradeMessage(Coord4D.get(tile), selected, GuiScreen.isShiftKeyDown()));
        }
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawTitleText(new TextComponentString(LangUtils.localize("gui.upgrades")), 5);
        if (scrollList.hasSelection()) {
            Upgrade selectedType = scrollList.getSelection();
            if (selectedType == null) {
                return;
            }
            int amount = upgradeTile.getInstalledUpgrades(selectedType);
            WrappedTextRenderer textRenderer = upgradeTypeData.computeIfAbsent(selectedType,
                  type -> new WrappedTextRenderer(this, LangUtils.localize("gui.upgrade") + ": " + type.getName()));
            int screenWidth = rightScreen.getWidth() - 2;
            int lines = textRenderer.renderWithScale(rightScreen.getRelativeX() + 2, rightScreen.getRelativeY() + 2, screenTextColor(), screenWidth - 2, 0.6F);
            int textY = 4 + 6 * lines;
            rightScreen.drawScaledScrollingString(new TextComponentString(LangUtils.localize("gui.upgrades.amount") + ": " + amount + "/" + selectedType.getMaxInstalled()),
                  0, textY, TextAlignment.LEFT, screenTextColor(), screenWidth, 2, false, 0.6F, msSelected);
            for (String component : UpgradeUtils.getInfo(tile, selectedType)) {
                textY += 6;
                rightScreen.drawScaledScrollingString(new TextComponentString(component), 0, textY, TextAlignment.LEFT, screenTextColor(),
                      screenWidth, 2, false, 0.6F, msSelected);
            }
        } else {
            noSelection.renderWithScale(rightScreen.getRelativeX() + 2, rightScreen.getRelativeY() + 2, screenTextColor(), 56, 0.8F);
        }
    }

    @Override
    public boolean hasPersistentData() {
        return true;
    }
}
