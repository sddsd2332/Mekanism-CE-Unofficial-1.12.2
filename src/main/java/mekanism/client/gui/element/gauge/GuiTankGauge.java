package mekanism.client.gui.element.gauge;

import mekanism.api.Coord4D;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.IGuiWrapper;
import mekanism.common.Mekanism;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.base.ITankManager;
import mekanism.common.item.ItemGaugeDropper;
import mekanism.common.network.PacketDropperUse.DropperUseMessage;
import mekanism.common.tile.component.config.DataType;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.util.Arrays;

public abstract class GuiTankGauge<T, TANK> extends GuiGauge<T> {

    private final ITankInfoHandler<TANK> infoHandler;

    public GuiTankGauge(GaugeType type, IGuiWrapper gui, int x, int y, int sizeX, int sizeY, ITankInfoHandler<TANK> infoHandler) {
        super(type, gui, x, y, sizeX, sizeY);
        this.infoHandler = infoHandler;
    }

    @Nullable
    public TANK getTank() {
        return infoHandler == null ? null : infoHandler.getTank();
    }

    protected int getTankIndex() {
        return infoHandler == null ? -1 : infoHandler.getTankIndex();
    }

    @Override
    protected GaugeInfo getGaugeColor() {
        GaugeInfo colorOverride = getColorOverride();
        if (colorOverride != null) {
            return colorOverride;
        }
        if (gui() instanceof GuiMekanismTile<?, ?> guiTile) {
            TANK tank = getTank();
            TileEntity tile = guiTile.getTileEntity();
            if (tank != null && tile instanceof ISideConfiguration config) {
                DataType dataType = config.getActiveDataType(tank);
                if (dataType != null) {
                    return GaugeInfo.get(dataType);
                }
            }
        }
        return super.getGaugeColor();
    }

    @Override
    protected boolean isValidClickButton(int button) {
        return button == 0 || button == 1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (active && visible && isValidClickButton(button) && clicked(mouseX, mouseY)) {
            ItemStack stack = minecraft.player.inventory.getItemStack();
            if (!stack.isEmpty() && stack.getItem() instanceof ItemGaugeDropper && gui() instanceof GuiMekanismTile<?, ?> guiTile) {
                TileEntity tile = guiTile.getTileEntity();
                if (tile instanceof ITankManager tankManager) {
                    Object[] tanks = tankManager.getManagedTanks();
                    TANK tank = getTank();
                    int index = tank == null || tanks == null ? -1 : Arrays.asList(tanks).indexOf(tank);
                    if (index == -1) {
                        index = getTankIndex();
                    }
                    if (index != -1) {
                        if (button == 0 && Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
                            button = 2;
                        }
                        Mekanism.packetHandler.sendToServer(new DropperUseMessage(Coord4D.get(tile), button, index));
                        playDownSound(minecraft.getSoundHandler());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public interface ITankInfoHandler<TANK> {

        @Nullable
        TANK getTank();

        int getTankIndex();
    }
}
