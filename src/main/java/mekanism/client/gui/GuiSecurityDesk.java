package mekanism.client.gui;

import mekanism.api.EnumColor;
import mekanism.api.TileNetworkList;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiElementHolder;
import mekanism.client.gui.element.GuiSecurityLight;
import mekanism.client.gui.element.GuiTextureOnlyElement;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.button.TooltipToggleButton;
import mekanism.client.gui.element.button.TranslationButton;
import mekanism.client.gui.element.scroll.GuiTextScrollList;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.text.BackgroundType;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.common.Mekanism;
import mekanism.common.inventory.container.ContainerSecurityDesk;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.security.SecurityFrequency;
import mekanism.common.tile.TileEntitySecurityDesk;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiSecurityDesk extends GuiMekanismTile<TileEntitySecurityDesk, ContainerSecurityDesk> {

    private static final ResourceLocation PUBLIC = MekanismUtils.getResource(ResourceType.GUI, "public.png");
    private static final ResourceLocation PRIVATE = MekanismUtils.getResource(ResourceType.GUI, "private.png");
    private static final int MAX_LENGTH = 16;

    private MekanismButton removeButton;
    private MekanismButton publicButton;
    private MekanismButton privateButton;
    private MekanismButton trustedButton;
    private MekanismButton overrideButton;
    private GuiTextScrollList scrollList;
    private GuiTextField trustedField;

    public GuiSecurityDesk(InventoryPlayer inventory, TileEntitySecurityDesk tile) {
        super(tile, new ContainerSecurityDesk(inventory, tile));
        dynamicSlots = true;
        ySize += 64;
        inventoryLabelY = ySize - 94;
        titleLabelY = 4;
    }

    @Override
    protected void addGuiElements() {
        addButton(new GuiElementHolder(this, 141, 13, 26, 37));
        addButton(new GuiElementHolder(this, 141, 54, 26, 34));
        addButton(new GuiElementHolder(this, 141, 92, 26, 37));
        super.addGuiElements();
        addButton(new GuiSlot(SlotType.INNER_HOLDER_SLOT, this, 145, 17));
        addButton(new GuiSlot(SlotType.INNER_HOLDER_SLOT, this, 145, 96));
        addButton(new GuiSecurityLight(this, 144, 77, () -> {
            SecurityFrequency frequency = tileEntity.frequency;
            if (!isOwner(frequency)) {
                return 2;
            }
            return frequency.override ? 0 : 1;
        }));
        addButton(new GuiTextureOnlyElement(PUBLIC, this, 145, 32, 18, 18));
        addButton(new GuiTextureOnlyElement(PRIVATE, this, 145, 111, 18, 18));
        scrollList = addButton(new GuiTextScrollList(this, 13, 13, 122, 42));
        removeButton = addButton(new TranslationButton(this, 13, 81, 122, 20, mekanism.common.MekanismLang.BUTTON_REMOVE, this::removeSelected));
        trustedField = addButton(new GuiTextField(this, 1, 35, 68, 99, 11)
              .setMaxLength(MAX_LENGTH)
              .setBackground(BackgroundType.INNER_SCREEN)
              .setInputValidator(this::isValidTextboxInput)
              .setEnterHandler(this::setTrusted)
              .addCheckmarkButton(this::setTrusted));
        publicButton = addButton(new MekanismImageButton(this, 13, 113, 40, 16, 40, 16, getButtonLocation("public"),
              () -> setSecurityMode(SecurityMode.PUBLIC)).setTooltip(new TextComponentString(LangUtils.localize("gui.publicMode"))));
        privateButton = addButton(new MekanismImageButton(this, 54, 113, 40, 16, 40, 16, getButtonLocation("private"),
              () -> setSecurityMode(SecurityMode.PRIVATE)).setTooltip(new TextComponentString(LangUtils.localize("gui.privateMode"))));
        trustedButton = addButton(new MekanismImageButton(this, 95, 113, 40, 16, 40, 16, getButtonLocation("trusted"),
              () -> setSecurityMode(SecurityMode.TRUSTED)).setTooltip(new TextComponentString(LangUtils.localize("gui.trustedMode"))));
        overrideButton = addButton(new TooltipToggleButton(this, 146, 59, 16, 16, getButtonLocation("exclamation"),
              () -> tileEntity.frequency != null && tileEntity.frequency.override, this::toggleOverride,
              new TextComponentString(LangUtils.localize("gui.securityOverride") + ": " + LangUtils.transOnOff(true)),
              new TextComponentString(LangUtils.localize("gui.securityOverride") + ": " + LangUtils.transOnOff(false))));
        updateButtons();
    }

    private boolean isOwner(SecurityFrequency frequency) {
        return frequency != null && tileEntity.ownerUUID != null && tileEntity.ownerUUID.equals(mc.player.getUniqueID());
    }

    private boolean isValidTextboxInput(char c, int keyCode) {
        return c == '_' || Character.isDigit(c) || Character.isLetter(c) || isTextboxKey(keyCode);
    }

    private boolean isTextboxKey(int keyCode) {
        return keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT ||
              keyCode == Keyboard.KEY_HOME || keyCode == Keyboard.KEY_END;
    }

    private void setTrusted() {
        if (isOwner(tileEntity.frequency)) {
            addTrusted(trustedField.getText().trim());
            trustedField.clear();
            updateButtons();
        }
    }

    private void addTrusted(String trusted) {
        if (isValidPlayerName(trusted)) {
            Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(0, trusted)));
        }
    }

    private boolean isValidPlayerName(String trusted) {
        if (trusted.isEmpty() || trusted.length() > MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < trusted.length(); i++) {
            char c = trusted.charAt(i);
            if (c != '_' && !Character.isDigit(c) && !Character.isLetter(c)) {
                return false;
            }
        }
        return true;
    }

    private void removeSelected() {
        int selection = scrollList.getSelection();
        if (tileEntity.frequency != null && selection != -1) {
            Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(1, tileEntity.frequency.trusted.get(selection))));
            scrollList.clearSelection();
            updateButtons();
        }
    }

    private void toggleOverride() {
        if (isOwner(tileEntity.frequency)) {
            Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(2)));
            updateButtons();
        }
    }

    private void setSecurityMode(SecurityMode mode) {
        if (isOwner(tileEntity.frequency)) {
            Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(3, mode.ordinal())));
            updateButtons();
        }
    }

    private void updateButtons() {
        if (scrollList == null) {
            return;
        }
        if (tileEntity.ownerUUID != null) {
            List<String> text = new ArrayList<>();
            if (tileEntity.frequency != null) {
                tileEntity.frequency.trusted.forEach(text::add);
            }
            scrollList.setText(text);
            removeButton.active = scrollList.hasSelection();
        } else {
            scrollList.setText(null);
            removeButton.active = false;
        }
        boolean owner = isOwner(tileEntity.frequency);
        publicButton.active = owner && tileEntity.frequency.securityMode != SecurityMode.PUBLIC;
        privateButton.active = owner && tileEntity.frequency.securityMode != SecurityMode.PRIVATE;
        trustedButton.active = owner && tileEntity.frequency.securityMode != SecurityMode.TRUSTED;
        overrideButton.active = owner;
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
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), titleLabelY);
        String ownerText = tileEntity.clientOwner != null ? LangUtils.localize("gui.owner") + ": " + tileEntity.clientOwner :
              EnumColor.RED + LangUtils.localize("gui.noOwner");
        renderInventoryTextAndOther(new TextComponentString(ownerText));
        drawScaledScrollingString(new TextComponentString(LangUtils.localize("gui.trustedPlayers")), 13, 57, TextAlignment.CENTER,
              subheadingTextColor(), 122, 0, false, 1, GuiElement.getMillis());
        String security = EnumColor.RED + LangUtils.localize("gui.securityOffline");
        if (tileEntity.frequency != null) {
            security = LangUtils.localize("gui.security") + ": " + tileEntity.frequency.securityMode.getDisplay();
        }
        drawScaledScrollingString(new TextComponentString(security), 13, 103, TextAlignment.LEFT, titleTextColor(), 122, 0, false, 1,
              GuiElement.getMillis());
        drawScaledScrollingString(new TextComponentString(LangUtils.localize("gui.add") + ":"), 1, 70, TextAlignment.RIGHT,
              titleTextColor(), trustedField.getRelativeX() - 1, 3, false, 1, GuiElement.getMillis());
        super.drawForegroundText(mouseX, mouseY);
    }
}
