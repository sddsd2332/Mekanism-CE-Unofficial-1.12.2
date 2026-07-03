package mekanism.client.gui.element.bar;

import mekanism.api.Coord4D;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.GuiUtils.TilingDirection;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.Mekanism;
import mekanism.common.base.ITankManager;
import mekanism.common.item.ItemGaugeDropper;
import mekanism.common.network.PacketDropperUse.DropperUseMessage;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.ITextComponent;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class GuiTankBar<STACK, TANK> extends GuiBar<GuiTankBar.TankInfoProvider<STACK, TANK>> {

    public GuiTankBar(IGuiWrapper gui, TankInfoProvider<STACK, TANK> infoProvider, int x, int y, int width, int height, boolean horizontal) {
        super(TextureMap.LOCATION_BLOCKS_TEXTURE, gui, infoProvider, x, y, width, height, horizontal);
    }

    protected abstract boolean isEmpty(@Nullable STACK stack);

    protected List<String> getTooltip(STACK stack) {
        List<String> tooltips = new ArrayList<>();
        ITextComponent tooltip = getHandler().getTooltip();
        if (tooltip != null) {
            tooltips.add(tooltip.getFormattedText());
        }
        return tooltips;
    }

    protected abstract void applyRenderColor(STACK stack);

    @Nullable
    protected abstract TextureAtlasSprite getIcon(STACK stack);

    @Override
    protected void renderBarOverlay(int mouseX, int mouseY, float partialTicks, double handlerLevel) {
        STACK stored = getHandler().getStack();
        if (!isEmpty(stored)) {
            int displayInt = (int) (handlerLevel * ((horizontal ? width : height) - 2));
            if (displayInt > 0) {
                TextureAtlasSprite icon = getIcon(stored);
                if (icon != null) {
                    applyRenderColor(stored);
                    if (horizontal) {
                        drawTiledSprite(relativeX + 1, relativeY + 1, height - 2, displayInt, height - 2, icon, TilingDirection.DOWN_RIGHT);
                    } else {
                        drawTiledSprite(relativeX + 1, relativeY + 1, height - 2, width - 2, displayInt, icon, TilingDirection.DOWN_RIGHT);
                    }
                    MekanismRenderer.resetColor();
                }
            }
        }
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        STACK stored = getHandler().getStack();
        if (isEmpty(stored)) {
            super.renderToolTip(mouseX, mouseY);
        } else {
            List<String> tooltip = getTooltip(stored);
            if (!tooltip.isEmpty()) {
                displayTooltips(tooltip, mouseX, mouseY);
            }
        }
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
                    TANK tank = getHandler().getTank();
                    int index = tank == null || tanks == null ? -1 : Arrays.asList(tanks).indexOf(tank);
                    if (index == -1) {
                        index = getHandler().getTankIndex();
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

    public interface TankInfoProvider<STACK, TANK> extends IBarInfoHandler {

        @Nullable
        STACK getStack();

        @Nullable
        TANK getTank();

        int getTankIndex();
    }
}
