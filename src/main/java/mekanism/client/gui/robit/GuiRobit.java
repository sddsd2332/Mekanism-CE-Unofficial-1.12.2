package mekanism.client.gui.robit;

import mekanism.client.SpecialColors;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.element.GuiSideHolder;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.tab.GuiSecurityTab;
import mekanism.common.Mekanism;
import mekanism.common.entity.EntityRobit;
import mekanism.common.network.PacketRobit.RobitMessage;
import mekanism.common.util.LangUtils;
import net.minecraft.inventory.Container;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public abstract class GuiRobit<CONTAINER extends Container> extends GuiMekanism<CONTAINER> {

    protected static final int GUI_MAIN = 21;
    protected static final int GUI_CRAFTING = 22;
    protected static final int GUI_INVENTORY = 23;
    protected static final int GUI_SMELTING = 24;
    protected static final int GUI_REPAIR = 25;

    protected final EntityRobit robit;

    protected GuiRobit(CONTAINER container, EntityRobit robit) {
        super(container);
        this.robit = robit;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiSecurityTab<>(this, robit, 120));
        addRobitNavigation();
    }

    protected void addRobitNavigation() {
        addButton(GuiSideHolder.create(this, xSize, 6, 106, false, false, SpecialColors.TAB_ROBIT_MENU));
        addButton(new MekanismImageButton(this, xSize + 3, 10, 18, getButtonLocation("main"),
              () -> openGui(GUI_MAIN), getOnHover(() -> new TextComponentString(LangUtils.localize("gui.robit")))));
        addButton(new MekanismImageButton(this, xSize + 3, 30, 18, getButtonLocation("crafting"),
              () -> openGui(GUI_CRAFTING), getOnHover(() -> new TextComponentString(LangUtils.localize("gui.robit.crafting")))));
        addButton(new MekanismImageButton(this, xSize + 3, 50, 18, getButtonLocation("inventory"),
              () -> openGui(GUI_INVENTORY), getOnHover(() -> new TextComponentString(LangUtils.localize("gui.robit.inventory")))));
        addButton(new MekanismImageButton(this, xSize + 3, 70, 18, getButtonLocation("smelting"),
              () -> openGui(GUI_SMELTING), getOnHover(() -> new TextComponentString(LangUtils.localize("gui.robit.smelting")))));
        addButton(new MekanismImageButton(this, xSize + 3, 90, 18, getButtonLocation("repair"),
              () -> openGui(GUI_REPAIR), getOnHover(() -> new TextComponentString(LangUtils.localize("gui.robit.repair")))));
    }

    protected void openGui(int guiId) {
        if (shouldOpenGui(guiId)) {
            Mekanism.packetHandler.sendToServer(new RobitMessage(robit.getEntityId(), guiId));
        }
    }

    protected abstract boolean shouldOpenGui(int guiId);
}
