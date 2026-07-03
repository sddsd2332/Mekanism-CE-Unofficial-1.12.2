package mekanism.client.gui.robit;

import mekanism.api.text.TextComponentGroup;
import mekanism.client.SpecialColors;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.GuiSideHolder;
import mekanism.client.gui.element.bar.GuiHorizontalPowerBar;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.tab.GuiSecurityTab;
import mekanism.client.gui.element.window.GuiRobitRename;
import mekanism.common.Mekanism;
import mekanism.common.entity.EntityRobit;
import mekanism.common.inventory.container.robit.ContainerRobitMain;
import mekanism.common.network.PacketRobit.RobitMessage;
import mekanism.common.network.PacketRobit.RobitPacketType;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiRobitMain extends GuiMekanism<ContainerRobitMain> {

    private final EntityRobit robit;
    private MekanismButton renameButton;

    public GuiRobitMain(InventoryPlayer inventory, EntityRobit entity) {
        super(new ContainerRobitMain(inventory, entity));
        robit = entity;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiSecurityTab<>(this, robit, 120));
        addButton(GuiSideHolder.create(this, xSize, 6, 106, false, false, SpecialColors.TAB_ROBIT_MENU));
        addButton(new GuiInnerScreen(this, 27, 16, 122, 56, this::getInfoText).clearFormat().clearSpacing().clearScale().padding(2));
        addButton(new GuiHorizontalPowerBar(this, robit, 27, 74, 120));
        addButton(new MekanismImageButton(this, 6, 16, 18, getButtonLocation("home"),
              () -> {
                  Mekanism.packetHandler.sendToServer(new RobitMessage(RobitPacketType.GO_HOME, robit.getEntityId()));
                  mc.displayGuiScreen(null);
              }, getOnHover(() -> new TextComponentString(LangUtils.localize("gui.robit.teleport")))));
        renameButton = addButton(new MekanismImageButton(this, 6, 35, 18, getButtonLocation("rename"),
              this::openRenameWindow, getOnHover(() -> new TextComponentString(LangUtils.localize("gui.robit.rename")))));
        addButton(new MekanismImageButton(this, 152, 35, 18, getButtonLocation("drop"),
              () -> Mekanism.packetHandler.sendToServer(new RobitMessage(RobitPacketType.DROP_PICKUP, robit.getEntityId())),
              getOnHover(() -> new TextComponentString(LangUtils.localize("gui.robit.togglePickup")))));
        addButton(new MekanismImageButton(this, 152, 54, 18, getButtonLocation("follow"),
              () -> Mekanism.packetHandler.sendToServer(new RobitMessage(RobitPacketType.FOLLOW, robit.getEntityId())),
              getOnHover(() -> new TextComponentString(LangUtils.localize("gui.robit.toggleFollow")))));
        addRobitNavigation();
    }

    private void openRenameWindow() {
        renameButton.active = false;
        GuiRobitRename window = new GuiRobitRename(this, 27, 16, robit);
        window.setTabListeners(ignored -> renameButton.active = true, ignored -> renameButton.active = false);
        addWindow(window);
    }

    private void addRobitNavigation() {
        addButton(new MekanismImageButton(this, xSize + 3, 10, 18, getButtonLocation("main"),
              () -> { }, getOnHover(() -> new TextComponentString(LangUtils.localize("gui.robit")))));
        addButton(new MekanismImageButton(this, xSize + 3, 30, 18, getButtonLocation("crafting"),
              () -> openGui(GuiRobit.GUI_CRAFTING), getOnHover(() -> new TextComponentString(LangUtils.localize("gui.robit.crafting")))));
        addButton(new MekanismImageButton(this, xSize + 3, 50, 18, getButtonLocation("inventory"),
              () -> openGui(GuiRobit.GUI_INVENTORY), getOnHover(() -> new TextComponentString(LangUtils.localize("gui.robit.inventory")))));
        addButton(new MekanismImageButton(this, xSize + 3, 70, 18, getButtonLocation("smelting"),
              () -> openGui(GuiRobit.GUI_SMELTING), getOnHover(() -> new TextComponentString(LangUtils.localize("gui.robit.smelting")))));
        addButton(new MekanismImageButton(this, xSize + 3, 90, 18, getButtonLocation("repair"),
              () -> openGui(GuiRobit.GUI_REPAIR), getOnHover(() -> new TextComponentString(LangUtils.localize("gui.robit.repair")))));
    }

    private void openGui(int guiId) {
        Mekanism.packetHandler.sendToServer(new RobitMessage(robit.getEntityId(), guiId));
    }

    private List<ITextComponent> getInfoText() {
        List<ITextComponent> text = new ArrayList<>();
        text.add(new TextComponentString(LangUtils.localize("gui.robit.greeting") + robit.getName() + LangUtils.localize("!")));
        text.add(new TextComponentGroup());
        text.add(new TextComponentString(LangUtils.localize("gui.energy") + ": " + MekanismUtils.getEnergyDisplay(robit.getEnergy(), robit.MAX_ELECTRICITY)));
        text.add(new TextComponentString(LangUtils.localize("gui.robit.following") + " : " + robit.getFollowing()));
        text.add(new TextComponentString(LangUtils.localize("gui.robit.dropPickup") + " : " + robit.getDropPickup()));
        text.add(new TextComponentString(LangUtils.localize("gui.robit.owner") + " : " + robit.getOwnerName()));
        return text;
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(LangUtils.localize("gui.robit")), 6);
        super.drawForegroundText(mouseX, mouseY);
    }
}
