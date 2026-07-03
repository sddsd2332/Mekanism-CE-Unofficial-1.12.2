package mekanism.client.gui;

import mekanism.api.EnumColor;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiElementHolder;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.custom.GuiTeleporterStatus;
import mekanism.client.gui.element.scroll.GuiTextScrollList;
import mekanism.client.gui.element.text.BackgroundType;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyManager;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.util.LangUtils;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public abstract class GuiTeleporterBase<CONTAINER extends MekanismContainer> extends GuiMekanism<CONTAINER> {

    protected MekanismButton publicButton;
    protected MekanismButton privateButton;
    protected MekanismButton trustedButton;
    protected MekanismButton setButton;
    protected MekanismButton deleteButton;
    protected GuiTextScrollList scrollList;
    protected GuiTextField frequencyField;
    protected GuiTeleporterStatus status;
    protected SecurityMode selectedMode = SecurityMode.PUBLIC;

    protected GuiTeleporterBase(CONTAINER container) {
        super(container);
    }

    @Override
    protected void addGuiElements() {
        addButton(new GuiElementHolder(this, 27, 36, 122, 42));
        addButton(new GuiInnerScreen(this, 48, 111, 101, 13));
        super.addGuiElements();
        status = addButton(new GuiTeleporterStatus(this, () -> getFrequency() != null, this::getStatus));
        scrollList = addButton(new GuiTextScrollList(this, 28, 37, 120, 42));
        publicButton = addButton(new MekanismButton(this, 27, 14, 39, 20, new TextComponentString(LangUtils.localize("gui.public")),
              () -> setMode(SecurityMode.PUBLIC), getOnHover(() -> new TextComponentString(LangUtils.localize("gui.publicMode")))));
        privateButton = addButton(new MekanismButton(this, 68, 14, 39, 20, new TextComponentString(LangUtils.localize("gui.private")),
              () -> setMode(SecurityMode.PRIVATE), getOnHover(() -> new TextComponentString(LangUtils.localize("gui.privateMode")))));
        trustedButton = addButton(new MekanismButton(this, 109, 14, 40, 20, new TextComponentString(LangUtils.localize("gui.trusted")),
              () -> setMode(SecurityMode.TRUSTED), getOnHover(() -> new TextComponentString(LangUtils.localize("gui.trustedMode")))));
        setButton = addButton(new MekanismButton(this, 27, 127, getActionButtonWidth(), 18, new TextComponentString(LangUtils.localize("gui.set")),
              this::setSelectedFrequency, null));
        deleteButton = addButton(new MekanismButton(this, 29 + getActionButtonWidth(), 127, getActionButtonWidth(), 18,
              new TextComponentString(LangUtils.localize("gui.delete")), this::deleteSelectedFrequency, null));
        frequencyField = addButton(new GuiTextField(this, 4, 50, 113, 98, 11)
              .setMaxLength(FrequencyManager.MAX_FREQ_LENGTH)
              .setBackground(BackgroundType.INNER_SCREEN)
              .setInputValidator(this::isValidFrequencyInput)
              .setEnterHandler(this::setTypedFrequency)
              .addCheckmarkButton(this::setTypedFrequency));
        updateFrequencyButtons();
    }

    protected int getActionButtonWidth() {
        return 60;
    }

    protected void setMode(SecurityMode mode) {
        selectedMode = mode;
        scrollList.clearSelection();
        updateFrequencyButtons();
    }

    protected boolean isValidFrequencyInput(char c, int keyCode) {
        return Character.isDigit(c) || Character.isLetter(c) || FrequencyManager.SPECIAL_CHARS.contains(c) ||
              GuiMekanism.isTextboxKey(c, keyCode);
    }

    protected void setTypedFrequency() {
        String name = frequencyField.getText();
        if (!name.isEmpty()) {
            setFrequency(new FrequencyIdentity(name, selectedMode, getOwnerUUID()));
        }
        frequencyField.clear();
        scrollList.clearSelection();
        updateFrequencyButtons();
    }

    protected void setSelectedFrequency() {
        Frequency selected = getSelectedFrequency();
        if (selected != null) {
            setFrequency(selected.getIdentity());
        }
        updateFrequencyButtons();
    }

    protected Frequency getSelectedFrequency() {
        int selection = scrollList.getSelection();
        List<? extends Frequency> frequencies = getVisibleFrequencies();
        return selection >= 0 && selection < frequencies.size() ? frequencies.get(selection) : null;
    }

    protected List<? extends Frequency> getVisibleFrequencies() {
        return switch (selectedMode) {
            case PUBLIC -> getPublicCache();
            case PRIVATE -> getPrivateCache();
            case TRUSTED -> getTrustedCache();
        };
    }

    protected void updateFrequencyButtons() {
        if (scrollList == null || getOwnerUUID() == null) {
            return;
        }
        List<String> text = new ArrayList<>();
        getVisibleFrequencies().forEach(freq -> text.add(getFrequencyListText(freq)));
        scrollList.setText(text);
        publicButton.active = selectedMode != SecurityMode.PUBLIC;
        privateButton.active = selectedMode != SecurityMode.PRIVATE;
        trustedButton.active = selectedMode != SecurityMode.TRUSTED;
        Frequency selected = getSelectedFrequency();
        Frequency current = getFrequency();
        if (selected == null) {
            setButton.active = false;
            deleteButton.active = false;
        } else {
            setButton.active = current == null || !current.equals(selected);
            deleteButton.active = getOwnerUUID().equals(selected.ownerUUID);
        }
    }

    protected String getFrequencyListText(Frequency freq) {
        return selectedMode == SecurityMode.PRIVATE ? freq.name : freq.name + " (" + freq.clientOwner + ")";
    }

    protected String getSecurity(Frequency freq) {
        return switch (freq.getSecurity()) {
            case PUBLIC -> EnumColor.BRIGHT_GREEN + LangUtils.localize("gui.public");
            case PRIVATE -> EnumColor.DARK_RED + LangUtils.localize("gui.private");
            case TRUSTED -> EnumColor.INDIGO + LangUtils.localize("gui.trusted");
        };
    }

    protected String getOwnerUsername(Frequency frequency) {
        if (frequency == null) {
            return EnumColor.DARK_RED + LangUtils.localize("gui.none");
        }
        if (selectedMode == SecurityMode.PRIVATE) {
            return EnumColor.BRIGHT_GREEN + getSelfOwnerName();
        }
        return (Objects.equals(getSelfOwnerName(), frequency.clientOwner) ? EnumColor.BRIGHT_GREEN : EnumColor.DARK_RED) + frequency.clientOwner;
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
        int titleOffset = getTitleOffset();
        if (titleOffset > 0) {
            drawTitleTextWithOffset(new TextComponentString(getGuiTitle()), titleOffset, 4, getXSize());
        } else {
            drawTitleText(new TextComponentString(getGuiTitle()), 4);
        }
        Frequency frequency = getFrequency();
        String none = EnumColor.DARK_RED + LangUtils.localize("gui.none");
        drawTextExact(new TextComponentString(LangUtils.localize("gui.freq") + ":"), 27, 81, titleTextColor());
        drawTextExact(new TextComponentString(" " + (frequency != null ? frequency.name : none)), 27 + fontRenderer.getStringWidth(LangUtils.localize("gui.freq") + ":"),
              81, subheadingTextColor());
        drawTextExact(new TextComponentString((isPortable() ? LangUtils.localize("gui.itemowner") : LangUtils.localize("gui.owner")) + ": " +
              (frequency != null ? getOwnerUsername(frequency) : none)), 27, 91, titleTextColor());
        drawTextExact(new TextComponentString(LangUtils.localize("gui.security") + ":"), 27, 101, titleTextColor());
        drawTextExact(new TextComponentString(" " + (frequency != null ? getSecurity(frequency) : none)),
              27 + fontRenderer.getStringWidth(LangUtils.localize("gui.security") + ":"), 101, subheadingTextColor());
        drawScaledScrollingString(new TextComponentString(LangUtils.localize("gui.set") + ":"), 0, 114, TextAlignment.RIGHT, titleTextColor(),
              frequencyField.getRelativeX(), 5, false, 1, GuiElement.getMillis());
        super.drawForegroundText(mouseX, mouseY);
    }

    protected abstract String getGuiTitle();

    protected int getTitleOffset() {
        return 0;
    }

    protected abstract boolean isPortable();

    protected abstract int getStatus();

    protected abstract Frequency getFrequency();

    protected abstract List<? extends Frequency> getPublicCache();

    protected abstract List<? extends Frequency> getPrivateCache();

    protected abstract List<? extends Frequency> getTrustedCache();

    protected abstract UUID getOwnerUUID();

    protected abstract String getSelfOwnerName();

    protected abstract void setFrequency(FrequencyIdentity identity);

    protected abstract void deleteSelectedFrequency();
}
