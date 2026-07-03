package mekanism.client.gui;

import mekanism.api.EnumColor;
import mekanism.api.TileNetworkList;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiElementHolder;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.scroll.GuiTextScrollList;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.element.tab.GuiHeatTab;
import mekanism.client.gui.element.tab.GuiWarningTab;
import mekanism.client.gui.element.text.BackgroundType;
import mekanism.client.gui.warning.IWarningTracker;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyManager;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.inventory.container.ContainerQuantumEntangloporter;
import mekanism.common.network.PacketSetTileFrequency.SetTileFrequencyMessage;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.tile.TileEntityQuantumEntangloporter;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@SideOnly(Side.CLIENT)
public class GuiQuantumEntangloporter extends GuiConfigurableTile<TileEntityQuantumEntangloporter, ContainerQuantumEntangloporter> {

    private MekanismButton publicButton;
    private MekanismButton privateButton;
    private MekanismButton trustedButton;
    private MekanismButton setButton;
    private MekanismButton deleteButton;
    private GuiTextScrollList scrollList;
    private mekanism.client.gui.element.text.GuiTextField frequencyField;
    private SecurityMode selectedMode = SecurityMode.PUBLIC;

    public GuiQuantumEntangloporter(InventoryPlayer inventory, TileEntityQuantumEntangloporter tile) {
        super(tile, new ContainerQuantumEntangloporter(inventory, tile));
        dynamicSlots = true;
        if (tileEntity.getFreq() != null) {
            selectedMode = tileEntity.getFreq().getSecurity();
        }
        ySize += 74;
        titleLabelY = 4;
        inventoryLabelY = ySize - 93;
    }

    @Override
    protected void addGuiElements() {
        addButton(new GuiElementHolder(this, 27, 36, 122, 42));
        addButton(new GuiInnerScreen(this, 48, 111, 101, 13));
        super.addGuiElements();
        addButton(new GuiEnergyTab(this, this::getEnergyTabText));
        addButton(new GuiHeatTab(this, this::getHeatTabText));
        scrollList = addButton(new GuiTextScrollList(this, 28, 37, 120, 42));
        publicButton = addButton(new MekanismButton(this, 27, 14, 39, 20, new TextComponentString(LangUtils.localize("gui.public")),
              () -> setMode(SecurityMode.PUBLIC), getOnHover(() -> new TextComponentString(LangUtils.localize("gui.publicMode")))));
        privateButton = addButton(new MekanismButton(this, 68, 14, 39, 20, new TextComponentString(LangUtils.localize("gui.private")),
              () -> setMode(SecurityMode.PRIVATE), getOnHover(() -> new TextComponentString(LangUtils.localize("gui.privateMode")))));
        trustedButton = addButton(new MekanismButton(this, 109, 14, 40, 20, new TextComponentString(LangUtils.localize("gui.trusted")),
              () -> setMode(SecurityMode.TRUSTED), getOnHover(() -> new TextComponentString(LangUtils.localize("gui.trustedMode")))));
        setButton = addButton(new MekanismButton(this, 27, 127, 60, 18, new TextComponentString(LangUtils.localize("gui.set")), this::setSelectedFrequency, null));
        deleteButton = addButton(new MekanismButton(this, 89, 127, 60, 18, new TextComponentString(LangUtils.localize("gui.delete")), this::deleteSelectedFrequency, null));
        frequencyField = addButton(new mekanism.client.gui.element.text.GuiTextField(this, 4, 50, 113, 98, 11)
              .setMaxLength(FrequencyManager.MAX_FREQ_LENGTH)
              .setBackground(BackgroundType.INNER_SCREEN)
              .setInputValidator(this::isValidFrequencyInput)
              .setEnterHandler(this::setTypedFrequency)
              .addCheckmarkButton(this::setTypedFrequency));
        updateButtons();
    }

    private List<ITextComponent> getEnergyTabText() {
        List<ITextComponent> info = new ArrayList<>();
        info.add(new TextComponentString(LangUtils.localize("gui.storing") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getEnergy(), tileEntity.getMaxEnergy())));
        info.add(new TextComponentString(LangUtils.localize("gui.maxOutput") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getMaxOutput()) + "/t"));
        return info;
    }

    private List<ITextComponent> getHeatTabText() {
        UnitDisplayUtils.TemperatureUnit unit = UnitDisplayUtils.TemperatureUnit.values()[MekanismConfig.current().general.tempUnit.val().ordinal()];
        String transfer = UnitDisplayUtils.getDisplayShort(tileEntity.lastTransferLoss, false, unit);
        String environment = UnitDisplayUtils.getDisplayShort(tileEntity.lastEnvironmentLoss, false, unit);
        List<ITextComponent> info = new ArrayList<>();
        info.add(new TextComponentString(LangUtils.localize("gui.transferred") + ": " + transfer + "/t"));
        info.add(new TextComponentString(LangUtils.localize("gui.dissipated") + ": " + environment + "/t"));
        return info;
    }

    private void setMode(SecurityMode mode) {
        selectedMode = mode;
        scrollList.clearSelection();
        updateButtons();
    }

    private boolean isValidFrequencyInput(char c, int keyCode) {
        return Character.isDigit(c) || Character.isLetter(c) || FrequencyManager.SPECIAL_CHARS.contains(c) || isTextboxKey(keyCode);
    }

    private boolean isTextboxKey(int keyCode) {
        return keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT ||
              keyCode == Keyboard.KEY_HOME || keyCode == Keyboard.KEY_END;
    }

    private void setTypedFrequency() {
        String name = frequencyField.getText();
        sendTypedFrequency(name);
        frequencyField.clear();
        scrollList.clearSelection();
        updateButtons();
    }

    private void setSelectedFrequency() {
        Frequency selected = getSelectedFrequency();
        if (selected != null) {
            Mekanism.packetHandler.sendToServer(new SetTileFrequencyMessage(true, FrequencyType.INVENTORY, selected.getIdentity(), tileEntity));
        }
        updateButtons();
    }

    private void deleteSelectedFrequency() {
        Frequency selected = getSelectedFrequency();
        if (selected != null) {
            Mekanism.packetHandler.sendToServer(new SetTileFrequencyMessage(false, FrequencyType.INVENTORY, selected.getIdentity(), tileEntity));
            scrollList.clearSelection();
        }
        updateButtons();
    }

    private void sendTypedFrequency(String freq) {
        if (!freq.isEmpty()) {
            Mekanism.packetHandler.sendToServer(new SetTileFrequencyMessage(true, FrequencyType.INVENTORY,
                  new FrequencyIdentity(freq, selectedMode, tileEntity.getSecurity().getOwnerUUID()), tileEntity));
        }
    }

    private Frequency getSelectedFrequency() {
        int selection = scrollList.getSelection();
        List<Frequency> frequencies = getVisibleFrequencies();
        return selection >= 0 && selection < frequencies.size() ? frequencies.get(selection) : null;
    }

    private List<Frequency> getVisibleFrequencies() {
        return new ArrayList<>(switch (selectedMode) {
            case PUBLIC -> tileEntity.getPublicCache(FrequencyType.INVENTORY);
            case PRIVATE -> tileEntity.getPrivateCache(FrequencyType.INVENTORY);
            case TRUSTED -> tileEntity.getTrustedCache(FrequencyType.INVENTORY);
        });
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

    private void updateButtons() {
        if (scrollList == null || tileEntity.getSecurity().getClientOwner() == null) {
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

    @Override
    public void updateScreen() {
        super.updateScreen();
        updateButtons();
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, button);
        updateButtons();
    }

    @Override
    protected void addWarningTab(IWarningTracker warningTracker) {
        addButton(new GuiWarningTab(this, warningTracker, false));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), titleLabelY);
        renderInventoryText();
        Frequency frequency = tileEntity.getFreq();
        String none = EnumColor.DARK_RED + LangUtils.localize("gui.none");
        drawTextExact(new TextComponentString(LangUtils.localize("gui.freq") + ":"), 27, 81, titleTextColor());
        drawTextExact(new TextComponentString(" " + (frequency != null ? frequency.name : none)), 27 + fontRenderer.getStringWidth(LangUtils.localize("gui.freq") + ":"), 81, subheadingTextColor());
        drawTextExact(new TextComponentString(LangUtils.localize("gui.owner") + ": " + getOwnerUsername(frequency)), 27, 91, titleTextColor());
        drawTextExact(new TextComponentString(LangUtils.localize("gui.security") + ":"), 27, 101, titleTextColor());
        drawTextExact(new TextComponentString(" " + (frequency != null ? getSecurity(frequency) : none)), 27 + fontRenderer.getStringWidth(LangUtils.localize("gui.security") + ":"), 101, subheadingTextColor());
        drawScaledScrollingString(new TextComponentString(LangUtils.localize("gui.set") + ":"), 0, 114, TextAlignment.RIGHT, titleTextColor(),
              frequencyField.getRelativeX(), 5, false, 1, GuiElement.getMillis());
        if (frequency == null) {
            int xAxis = mouseX - guiLeft;
            int yAxis = mouseY - guiTop;
            if (xAxis >= -21 && xAxis <= -3 && yAxis >= 90 && yAxis <= 108) {
                displayTooltip(LangUtils.localize("gui.no_freq"), mouseX, mouseY);
            }
        }
        super.drawForegroundText(mouseX, mouseY);
    }
}
