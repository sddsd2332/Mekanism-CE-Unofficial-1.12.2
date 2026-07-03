package mekanism.multiblockmachine.client.gui.generator;

import mekanism.api.EnumColor;
import mekanism.api.TileNetworkList;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.GuiSideHolder;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.common.Mekanism;
import mekanism.common.base.IRedstoneControl;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.generators.client.gui.GuiGenerator;
import mekanism.generators.client.gui.element.GuiGeneratorStateTexture;
import mekanism.generators.client.gui.element.GuiGeneratorStateTexture.Icon;
import mekanism.multiblockmachine.common.inventory.container.ContainerLargeWindGenerator;
import mekanism.multiblockmachine.common.tile.generator.TileEntityLargeWindGenerator;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiLargeWindGenerator extends GuiGenerator<TileEntityLargeWindGenerator, ContainerLargeWindGenerator> {

    public GuiLargeWindGenerator(InventoryPlayer inventory, TileEntityLargeWindGenerator tile) {
        super(tile, new ContainerLargeWindGenerator(inventory, tile));
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiInnerScreen(this, 48, 21, 80, 44, this::getScreenText));
        addButton(new GuiEnergyTab(this, () -> getEnergyTabText(tileEntity.getActive() ? tileEntity.getEnergyAdd() : 0)));
        addButton(new GuiVerticalPowerBar(this, tileEntity.getEnergyContainer(), 164, 15));
        addButton(new GuiGeneratorStateTexture(this, 18, 35, tileEntity::getActive, Icon.WIND_ON, Icon.WIND_OFF));
        addButton(new GuiSideHolder(this, -26, 86, 26, true, false) {
            @Override
            public void renderButton(int mouseX, int mouseY, float partialTicks) {
                if (tileEntity.getBladeDamage()) {
                    super.renderButton(mouseX, mouseY, partialTicks);
                }
            }

            @Override
            public void drawBackground(int mouseX, int mouseY, float partialTicks) {
                if (tileEntity.getBladeDamage()) {
                    super.drawBackground(mouseX, mouseY, partialTicks);
                }
            }
        });
        addButton(new MekanismImageButton(this, -21, 90, 18, 16, getButtonLocation("stock_control"), () -> sendButtonPacket(1),
              getOnHover(() -> new TextComponentString(tileEntity.getMachineStop() ? LangUtils.localize("gui.forced_run") : LangUtils.localize("gui.forced_run_off"))))
              .visibility(tileEntity::getBladeDamage));
        addButton(new MekanismImageButton(this, -21, 64, 18, 16, getButtonLocation("stock_control"), () -> sendButtonPacket(2),
              getOnHover(() -> new TextComponentString(LangUtils.localize("gui.updateRun"))))
              .visibility(tileEntity::getMachineStop2));
    }

    @Override
    protected int titleY() {
        return 6;
    }

    private void sendButtonPacket(int type) {
        Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(type)));
    }

    private List<ITextComponent> getScreenText() {
        List<ITextComponent> list = new ArrayList<>();
        list.add(energy(tileEntity.getEnergy(), tileEntity.getMaxEnergy()));
        list.add(text(LangUtils.localize("gui.power") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getActive() ? tileEntity.getEnergyAdd() : 0) + "/t"));
        list.add(text(LangUtils.localize("gui.out") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getMaxOutput()) + "/t"));
        String status = getStatusKey();
        if (status != null) {
            list.add(text(EnumColor.DARK_RED + LangUtils.localize(status)));
        }
        if (tileEntity.getBladeDamage()) {
            list.add(text(EnumColor.DARK_RED + LangUtils.localize("gui.Blades_damaged")));
            list.add(text(EnumColor.DARK_RED + LangUtils.localize("gui.Blades_damaged_number") + ": " + tileEntity.getBladeDamageNumber()));
        }
        return list;
    }

    private String getStatusKey() {
        boolean blacklisted = tileEntity.isBlacklistDimension();
        if (!tileEntity.getActive()) {
            if (blacklisted) {
                return "gui.noWind";
            }
            if (tileEntity.getMachineStop()) {
                return "gui.protection";
            }
            if (tileEntity.getMachineStop2()) {
                return "gui.protection2";
            }
            if (tileEntity.controlType == IRedstoneControl.RedstoneControl.HIGH && !tileEntity.redstone) {
                return "control.high.desc";
            }
            if (tileEntity.controlType == IRedstoneControl.RedstoneControl.LOW && tileEntity.redstone) {
                return "control.low.desc";
            }
            return "gui.skyBlocked";
        } else if (!tileEntity.getMachineStop() && !blacklisted && tileEntity.getBladeDamage()) {
            return "gui.protection.off";
        }
        return null;
    }
}
