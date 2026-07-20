package mekanism.client.gui.element.custom;

import mekanism.api.EnumColor;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.button.ColorButton;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.scroll.GuiTextScrollList;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.text.BackgroundType;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.client.gui.element.window.GuiConfirmationDialog;
import mekanism.client.gui.element.window.GuiConfirmationDialog.DialogType;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyManager;
import mekanism.common.frequency.IColorableFrequency;
import mekanism.common.network.PacketSetFrequencyColor.SetFrequencyColorMessage;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.util.LangUtils;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Reusable 26.2-style frequency selector that can be embedded in a screen or window. */
public class GuiFrequencySelector<FREQ extends Frequency> extends GuiElement {

    public static final int WIDTH = 132;
    public static final int HEIGHT = 121;

    private final IGuiFrequencySelector<FREQ> frequencySelector;
    private final MekanismButton publicButton;
    private final MekanismButton trustedButton;
    private final MekanismButton privateButton;
    private final MekanismButton setButton;
    private final MekanismButton deleteButton;
    private final GuiTextScrollList scrollList;
    private final GuiTextField frequencyField;
    @Nullable
    private List<FREQ> lastFrequencies;
    private SecurityMode selectedMode = SecurityMode.PUBLIC;
    private boolean modeInitialized;

    public GuiFrequencySelector(IGuiWrapper gui, IGuiFrequencySelector<FREQ> frequencySelector, int x, int y) {
        super(gui, x, y, WIDTH, HEIGHT);
        this.frequencySelector = frequencySelector;
        boolean hasColor = frequencySelector instanceof IGuiColorFrequencySelector;

        scrollList = addChild(new GuiTextScrollList(gui, relativeX, relativeY + 22, 122, 42));
        publicButton = addChild(new MekanismImageButton(gui, relativeX, relativeY, 38, 20, 40, 16,
              getButtonLocation("public"), () -> setMode(SecurityMode.PUBLIC)).setTooltip(MekanismLang.PUBLIC_MODE.translate()));
        trustedButton = addChild(new MekanismImageButton(gui, relativeX + 42, relativeY, 38, 20, 40, 16,
              getButtonLocation("trusted"), () -> setMode(SecurityMode.TRUSTED)).setTooltip(MekanismLang.TRUSTED_MODE.translate()));
        privateButton = addChild(new MekanismImageButton(gui, relativeX + 84, relativeY, 38, 20, 40, 16,
              getButtonLocation("private"), () -> setMode(SecurityMode.PRIVATE)).setTooltip(MekanismLang.PRIVATE_MODE.translate()));

        int buttonWidth = hasColor ? 50 : 60;
        setButton = addChild(new MekanismButton(gui, relativeX, relativeY + 113, buttonWidth, 18,
              MekanismLang.BUTTON_SET.translate(), this::setSelectedFrequency, null));
        deleteButton = addChild(new MekanismButton(gui, relativeX + buttonWidth + 2, relativeY + 113, buttonWidth, 18,
              MekanismLang.BUTTON_DELETE.translate(), this::confirmDeleteSelectedFrequency, null));
        if (hasColor) {
            addChild(new GuiSlot(SlotType.NORMAL, gui, relativeX + 104, relativeY + 113));
            @SuppressWarnings("unchecked")
            IGuiColorFrequencySelector<FREQ> colorSelector = (IGuiColorFrequencySelector<FREQ>) frequencySelector;
            addChild(new ColorButton(gui, relativeX + 105, relativeY + 114, 16, 16,
                  () -> {
                      FREQ frequency = colorSelector.getFrequency();
                      return frequency instanceof IColorableFrequency ? ((IColorableFrequency) frequency).getColor() : null;
                  }, () -> colorSelector.sendColorUpdate(true), () -> colorSelector.sendColorUpdate(false)));
        }
        frequencyField = addChild(new GuiTextField(gui, 4, relativeX + 23, relativeY + 99, 98, 11)
              .setMaxLength(FrequencyManager.MAX_FREQ_LENGTH)
              .setBackground(BackgroundType.INNER_SCREEN)
              .setInputValidator(this::isValidFrequencyInput)
              .setEnterHandler(this::setTypedFrequency)
              .addCheckmarkButton(this::setTypedFrequency));
        updateButtons();
    }

    private void setMode(SecurityMode mode) {
        modeInitialized = true;
        selectedMode = mode;
        lastFrequencies = null;
        scrollList.clearSelection();
        updateButtons();
    }

    private boolean isValidFrequencyInput(char c, int keyCode) {
        return Character.isDigit(c) || Character.isLetter(c) || FrequencyManager.SPECIAL_CHARS.contains(c) ||
              GuiMekanism.isTextboxKey(c, keyCode);
    }

    private void setTypedFrequency() {
        String name = frequencyField.getText();
        if (!name.isEmpty()) {
            UUID owner = minecraft.player == null ? frequencySelector.getOwnerUUID() : minecraft.player.getUniqueID();
            frequencySelector.sendSetFrequency(new FrequencyIdentity(name, selectedMode, owner));
        }
        frequencyField.clear();
        scrollList.clearSelection();
        updateButtons();
    }

    private void setSelectedFrequency() {
        FREQ selected = getSelectedFrequency();
        if (selected != null) {
            frequencySelector.sendSetFrequency(selected.getIdentity());
        }
        updateButtons();
    }

    private void confirmDeleteSelectedFrequency() {
        FREQ selected = getSelectedFrequency();
        if (selected != null) {
            GuiConfirmationDialog.show(gui(), MekanismLang.FREQUENCY_DELETE_CONFIRM.translate(), () -> {
                frequencySelector.sendRemoveFrequency(selected.getIdentity());
                scrollList.clearSelection();
                updateButtons();
            }, DialogType.DANGER);
        }
    }

    @Nullable
    private FREQ getSelectedFrequency() {
        int selection = scrollList.getSelection();
        List<FREQ> frequencies = getVisibleFrequencies();
        return selection >= 0 && selection < frequencies.size() ? frequencies.get(selection) : null;
    }

    private List<FREQ> getVisibleFrequencies() {
        List<FREQ> frequencies = switch (selectedMode) {
            case PUBLIC -> frequencySelector.getPublicFrequencies();
            case PRIVATE -> frequencySelector.getPrivateFrequencies();
            case TRUSTED -> frequencySelector.getTrustedFrequencies();
        };
        return frequencies == null ? Collections.emptyList() : frequencies;
    }

    @Override
    public void tick() {
        super.tick();
        if (!modeInitialized) {
            FREQ current = frequencySelector.getFrequency();
            if (current != null) {
                selectedMode = current.getSecurity();
                lastFrequencies = null;
                modeInitialized = true;
            }
        }
        updateButtons();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (handled) {
            updateButtons();
        }
        return handled;
    }

    private void updateButtons() {
        if (scrollList == null) {
            return;
        }
        List<FREQ> frequencies = getVisibleFrequencies();
        if (lastFrequencies != frequencies) {
            lastFrequencies = frequencies;
            List<String> text = new ArrayList<>(frequencies.size());
            for (FREQ frequency : frequencies) {
                text.add(selectedMode == SecurityMode.PRIVATE ? frequency.getName() :
                      frequency.getName() + " (" + frequency.getOwnerName() + ")");
            }
            scrollList.setText(text);
        }
        publicButton.active = selectedMode != SecurityMode.PUBLIC;
        trustedButton.active = selectedMode != SecurityMode.TRUSTED;
        privateButton.active = selectedMode != SecurityMode.PRIVATE;
        FREQ selected = getSelectedFrequency();
        FREQ current = frequencySelector.getFrequency();
        setButton.active = selected != null && (current == null || !current.equals(selected));
        deleteButton.active = selected != null && minecraft.player != null && selected.ownerMatches(minecraft.player.getUniqueID());
        frequencySelector.buttonsUpdated();
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        FREQ frequency = frequencySelector.getFrequency();
        String none = EnumColor.DARK_RED + LangUtils.localize("gui.none");
        String frequencyName = frequency == null ? none : EnumColor.INDIGO + frequency.getName();
        String owner = frequency == null ? none : getOwnerUsername(frequency);
        String security = frequency == null ? none : getSecurity(frequency);
        drawScaledScrollingString(new TextComponentString(LangUtils.localize("gui.freq") + ": " + frequencyName), 0, 67,
              TextAlignment.LEFT, titleTextColor(), WIDTH, 3, false, 1, getTimeOpened());
        drawScaledScrollingString(new TextComponentString(LangUtils.localize(frequencySelector.isPortable() ? "gui.itemowner" : "gui.owner") + ": " + owner),
              0, 77, TextAlignment.LEFT, titleTextColor(), WIDTH, 3, false, 1, getTimeOpened());
        drawScaledScrollingString(new TextComponentString(LangUtils.localize("gui.security") + ": " + security), 0, 87,
              TextAlignment.LEFT, titleTextColor(), WIDTH, 3, false, 1, getTimeOpened());
        drawScaledScrollingString(new TextComponentString(LangUtils.localize("gui.set") + ":"), 0, 100,
              TextAlignment.RIGHT, titleTextColor(), 23, 2, false, 1, getTimeOpened());
    }

    private String getOwnerUsername(FREQ frequency) {
        String owner = frequency.getOwnerName();
        if ((owner == null || owner.isEmpty()) && frequency.ownerMatches(frequencySelector.getOwnerUUID())) {
            owner = frequencySelector.getSelfOwnerName();
        }
        boolean selfOwned = minecraft.player != null && frequency.ownerMatches(minecraft.player.getUniqueID());
        return (selfOwned ? EnumColor.BRIGHT_GREEN : EnumColor.DARK_RED) + (owner == null ? "" : owner);
    }

    private String getSecurity(FREQ frequency) {
        return switch (frequency.getSecurity()) {
            case PUBLIC -> EnumColor.BRIGHT_GREEN + LangUtils.localize("gui.public");
            case PRIVATE -> EnumColor.DARK_RED + LangUtils.localize("gui.private");
            case TRUSTED -> EnumColor.INDIGO + LangUtils.localize("gui.trusted");
        };
    }

    public interface IGuiFrequencySelector<FREQ extends Frequency> {

        void sendSetFrequency(FrequencyIdentity identity);

        void sendRemoveFrequency(FrequencyIdentity identity);

        @Nullable
        FREQ getFrequency();

        List<FREQ> getPublicFrequencies();

        List<FREQ> getTrustedFrequencies();

        List<FREQ> getPrivateFrequencies();

        @Nullable
        default UUID getOwnerUUID() {
            return null;
        }

        default String getSelfOwnerName() {
            return "";
        }

        default boolean isPortable() {
            return false;
        }

        default void buttonsUpdated() {
        }
    }

    public interface IGuiColorFrequencySelector<FREQ extends Frequency> extends IGuiFrequencySelector<FREQ> {

        default void sendColorUpdate(boolean next) {
            FREQ frequency = getFrequency();
            if (frequency instanceof IColorableFrequency) {
                Mekanism.packetHandler.sendToServer(new SetFrequencyColorMessage(frequency, next));
            }
        }
    }
}
