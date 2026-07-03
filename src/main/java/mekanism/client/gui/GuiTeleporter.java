package mekanism.client.gui;

import mekanism.api.EnumColor;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiElementHolder;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.button.ColorButton;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.custom.GuiTeleporterStatus;
import mekanism.client.gui.element.scroll.GuiTextScrollList;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.text.BackgroundType;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.common.Mekanism;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyManager;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.inventory.container.ContainerTeleporter;
import mekanism.common.network.PacketSetFrequencyColor.SetFrequencyColorMessage;
import mekanism.common.network.PacketSetTileFrequency.SetTileFrequencyMessage;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.tile.TileEntityTeleporter;
import mekanism.common.util.LangUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GuiTeleporter extends GuiMekanismTile<TileEntityTeleporter, ContainerTeleporter> {

    private MekanismButton publicButton;
    private MekanismButton privateButton;
    private MekanismButton trustedButton;
    private MekanismButton setButton;
    private MekanismButton deleteButton;
    private GuiTextScrollList scrollList;
    private GuiTextField frequencyField;
    private GuiTeleporterStatus status;
    private SecurityMode selectedMode = SecurityMode.PUBLIC;

    public GuiTeleporter(InventoryPlayer inventory, TileEntityTeleporter tile) {
        super(tile, new ContainerTeleporter(inventory, tile));
        dynamicSlots = true;
        ySize += 74;
        titleLabelY = 4;
        inventoryLabelY = ySize - 93;
        if (tile.getFreq() != null) {
            selectedMode = tile.getFreq().getSecurity();
        }
    }

    @Override
    protected void addGuiElements() {
        addButton(new GuiElementHolder(this, 27, 36, 122, 42));
        addButton(new GuiInnerScreen(this, 48, 111, 101, 13));
        super.addGuiElements();
        status = addButton(new GuiTeleporterStatus(this, () -> tileEntity.getFreq() != null, () -> tileEntity.status));
        scrollList = addButton(new GuiTextScrollList(this, 28, 37, 120, 42));
        publicButton = addButton(new MekanismButton(this, 27, 14, 39, 20, new TextComponentString(LangUtils.localize("gui.public")),
              () -> setMode(SecurityMode.PUBLIC), getOnHover(() -> new TextComponentString(LangUtils.localize("gui.publicMode")))));
        privateButton = addButton(new MekanismButton(this, 68, 14, 39, 20, new TextComponentString(LangUtils.localize("gui.private")),
              () -> setMode(SecurityMode.PRIVATE), getOnHover(() -> new TextComponentString(LangUtils.localize("gui.privateMode")))));
        trustedButton = addButton(new MekanismButton(this, 109, 14, 40, 20, new TextComponentString(LangUtils.localize("gui.trusted")),
              () -> setMode(SecurityMode.TRUSTED), getOnHover(() -> new TextComponentString(LangUtils.localize("gui.trustedMode")))));
        setButton = addButton(new MekanismButton(this, 27, 127, 50, 18, new TextComponentString(LangUtils.localize("gui.set")),
              this::setSelectedFrequency, null));
        deleteButton = addButton(new MekanismButton(this, 79, 127, 50, 18, new TextComponentString(LangUtils.localize("gui.delete")),
              this::deleteSelectedFrequency, null));
        frequencyField = addButton(new GuiTextField(this, 4, 50, 113, 98, 11)
              .setMaxLength(FrequencyManager.MAX_FREQ_LENGTH)
              .setBackground(BackgroundType.INNER_SCREEN)
              .setInputValidator(this::isValidFrequencyInput)
              .setEnterHandler(this::setTypedFrequency)
              .addCheckmarkButton(this::setTypedFrequency));
        addButton(new GuiVerticalPowerBar(this, tileEntity.getMainEnergyContainer(), 158, 26)
              .warning(WarningType.NOT_ENOUGH_ENERGY, () -> tileEntity.status == 4));
        addButton(new GuiSlot(SlotType.NORMAL, this, 131, 127));
        addButton(new ColorButton(this, 132, 128, 16, 16, () -> tileEntity.getFreq() == null ? null : tileEntity.getFreq().getColor(),
              () -> sendColorUpdate(true),
              () -> sendColorUpdate(false),
              () -> java.util.Arrays.asList(LangUtils.localize("gui.Teleportercolor"),
                    LangUtils.localize("tooltip.configurator.viewColor") + ": " + (tileEntity.getFreq() == null ? LangUtils.localize("gui.none") : tileEntity.getFreq().getColor().getColoredName()))));
        updateFrequencyButtons();
    }

    private void sendColorUpdate(boolean next) {
        Frequency frequency = tileEntity.getFreq();
        if (frequency != null) {
            Mekanism.packetHandler.sendToServer(new SetFrequencyColorMessage(frequency, next));
        }
    }

    private void setMode(SecurityMode mode) {
        selectedMode = mode;
        scrollList.clearSelection();
        updateFrequencyButtons();
    }

    private boolean isValidFrequencyInput(char c, int keyCode) {
        return Character.isDigit(c) || Character.isLetter(c) || FrequencyManager.SPECIAL_CHARS.contains(c) ||
              GuiMekanism.isTextboxKey(c, keyCode);
    }

    private void setTypedFrequency() {
        String name = frequencyField.getText();
        if (!name.isEmpty()) {
            Mekanism.packetHandler.sendToServer(new SetTileFrequencyMessage(true, FrequencyType.TELEPORTER,
                  new FrequencyIdentity(name, selectedMode, tileEntity.getSecurity().getOwnerUUID()), tileEntity));
        }
        frequencyField.clear();
        scrollList.clearSelection();
        updateFrequencyButtons();
    }

    private void setSelectedFrequency() {
        Frequency selected = getSelectedFrequency();
        if (selected != null) {
            Mekanism.packetHandler.sendToServer(new SetTileFrequencyMessage(true, FrequencyType.TELEPORTER, selected.getIdentity(), tileEntity));
        }
        updateFrequencyButtons();
    }

    private void deleteSelectedFrequency() {
        Frequency selected = getSelectedFrequency();
        if (selected != null) {
            Mekanism.packetHandler.sendToServer(new SetTileFrequencyMessage(false, FrequencyType.TELEPORTER, selected.getIdentity(), tileEntity));
            scrollList.clearSelection();
        }
        updateFrequencyButtons();
    }

    private Frequency getSelectedFrequency() {
        int selection = scrollList.getSelection();
        List<? extends Frequency> frequencies = getVisibleFrequencies();
        return selection >= 0 && selection < frequencies.size() ? frequencies.get(selection) : null;
    }

    private List<? extends Frequency> getVisibleFrequencies() {
        return switch (selectedMode) {
            case PUBLIC -> tileEntity.getPublicCache(FrequencyType.TELEPORTER);
            case PRIVATE -> tileEntity.getPrivateCache(FrequencyType.TELEPORTER);
            case TRUSTED -> tileEntity.getTrustedCache(FrequencyType.TELEPORTER);
        };
    }

    private void updateFrequencyButtons() {
        if (scrollList == null || tileEntity.getSecurity().getOwnerUUID() == null) {
            return;
        }
        List<String> text = new ArrayList<>();
        getVisibleFrequencies().forEach(freq -> text.add(selectedMode == SecurityMode.PRIVATE ? freq.name : freq.name + " (" + freq.clientOwner + ")"));
        scrollList.setText(text);
        publicButton.active = selectedMode != SecurityMode.PUBLIC;
        privateButton.active = selectedMode != SecurityMode.PRIVATE;
        trustedButton.active = selectedMode != SecurityMode.TRUSTED;
        Frequency selected = getSelectedFrequency();
        Frequency current = tileEntity.getFreq();
        if (selected == null) {
            setButton.active = false;
            deleteButton.active = false;
        } else {
            setButton.active = current == null || !current.equals(selected);
            deleteButton.active = tileEntity.getSecurity().getOwnerUUID().equals(selected.ownerUUID);
        }
    }

    private String getSecurity(Frequency freq) {
        return switch (freq.getSecurity()) {
            case PUBLIC -> EnumColor.BRIGHT_GREEN + LangUtils.localize("gui.public");
            case PRIVATE -> EnumColor.DARK_RED + LangUtils.localize("gui.private");
            case TRUSTED -> EnumColor.INDIGO + LangUtils.localize("gui.trusted");
        };
    }

    private String getOwnerUsername(Frequency frequency) {
        if (frequency == null) {
            return EnumColor.DARK_RED + LangUtils.localize("gui.none");
        }
        if (selectedMode == SecurityMode.PRIVATE) {
            return EnumColor.BRIGHT_GREEN + tileEntity.getSecurity().getClientOwner();
        }
        return (Objects.equals(tileEntity.getSecurity().getClientOwner(), frequency.clientOwner) ? EnumColor.BRIGHT_GREEN : EnumColor.DARK_RED) + frequency.clientOwner;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        updateFrequencyButtons();
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, button);
        updateFrequencyButtons();
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleTextWithOffset(new TextComponentString(tileEntity.getName()), status.getRelativeRight(), titleLabelY, 153);
        Frequency frequency = tileEntity.getFreq();
        String none = EnumColor.DARK_RED + LangUtils.localize("gui.none");
        drawTextExact(new TextComponentString(LangUtils.localize("gui.freq") + ":"), 27, 81, titleTextColor());
        drawTextExact(new TextComponentString(" " + (frequency != null ? frequency.name : none)), 27 + fontRenderer.getStringWidth(LangUtils.localize("gui.freq") + ":"),
              81, subheadingTextColor());
        drawTextExact(new TextComponentString(LangUtils.localize("gui.owner") + ": " + (frequency != null ? getOwnerUsername(frequency) : none)), 27, 91, titleTextColor());
        drawTextExact(new TextComponentString(LangUtils.localize("gui.security") + ":"), 27, 101, titleTextColor());
        drawTextExact(new TextComponentString(" " + (frequency != null ? getSecurity(frequency) : none)),
              27 + fontRenderer.getStringWidth(LangUtils.localize("gui.security") + ":"), 101, subheadingTextColor());
        drawScaledScrollingString(new TextComponentString(LangUtils.localize("gui.set") + ":"), 0, 114, TextAlignment.RIGHT, titleTextColor(),
              frequencyField.getRelativeX(), 5, false, 1, GuiElement.getMillis());
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }
}
