package mekanism.client.gui;

import mekanism.api.EnumColor;
import mekanism.api.TileNetworkList;
import mekanism.api.text.TextComponentGroup;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiUpArrow;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.button.BasicColorButton;
import mekanism.client.gui.element.button.TooltipColorButton;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.element.tab.GuiVisualsTab;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.common.Mekanism;
import mekanism.common.inventory.container.ContainerDimensionalStabilizer;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.tile.machine.TileEntityDimensionalStabilizer;
import mekanism.common.util.LangUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiDimensionalStabilizer extends GuiMekanismTile<TileEntityDimensionalStabilizer, ContainerDimensionalStabilizer> {

    private static final int GRID_START_X = 63;
    private static final int GRID_START_Y = 19;
    private static final int GRID_BUTTON_SIZE = 10;

    public GuiDimensionalStabilizer(InventoryPlayer inventory, TileEntityDimensionalStabilizer tile) {
        super(tile, new ContainerDimensionalStabilizer(inventory, tile));
        dynamicSlots = true;
        inventoryLabelY += 2;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiVisualsTab<>(this, tileEntity));
        addButton(new GuiEnergyTab(this, tileEntity.getEnergyContainer(), tileEntity::getActive));
        addButton(new GuiVerticalPowerBar(this, tileEntity.getEnergyContainer(), 164, 15)
              .warning(WarningType.NOT_ENOUGH_ENERGY, () -> tileEntity.getEnergyUsage() > tileEntity.getEnergy()));
        addChunkButtons();
        addButton(new GuiUpArrow(this, 52, 28));
    }

    private void addChunkButtons() {
        for (int x = 0; x < TileEntityDimensionalStabilizer.MAX_LOAD_DIAMETER; x++) {
            for (int z = 0; z < TileEntityDimensionalStabilizer.MAX_LOAD_DIAMETER; z++) {
                final int chunkGridX = x;
                final int chunkGridZ = z;
                if (chunkGridX == TileEntityDimensionalStabilizer.MAX_LOAD_RADIUS && chunkGridZ == TileEntityDimensionalStabilizer.MAX_LOAD_RADIUS) {
                    addButton(new BasicColorButton(this, GRID_START_X + x * GRID_BUTTON_SIZE, GRID_START_Y + z * GRID_BUTTON_SIZE, GRID_BUTTON_SIZE,
                          () -> EnumColor.DARK_BLUE, () -> handleChunkButton(chunkGridX, chunkGridZ, true),
                          () -> handleChunkButton(chunkGridX, chunkGridZ, false)) {
                        @Override
                        public void renderToolTip(int mouseX, int mouseY) {
                            super.renderToolTip(mouseX, mouseY);
                            displayTooltips(getChunkButtonTooltips(chunkGridX, chunkGridZ), mouseX, mouseY);
                        }
                    });
                } else {
                    addButton(new TooltipColorButton(this, GRID_START_X + x * GRID_BUTTON_SIZE, GRID_START_Y + z * GRID_BUTTON_SIZE, GRID_BUTTON_SIZE,
                          EnumColor.DARK_BLUE, () -> tileEntity.isChunkLoadingAt(chunkGridX, chunkGridZ),
                          () -> handleChunkButton(chunkGridX, chunkGridZ, true),
                          getToggleChunkButtonTooltips(chunkGridX, chunkGridZ, true),
                          getToggleChunkButtonTooltips(chunkGridX, chunkGridZ, false)));
                }
            }
        }
    }

    private void handleChunkButton(int x, int z, boolean leftClick) {
        if (x == TileEntityDimensionalStabilizer.MAX_LOAD_RADIUS && z == TileEntityDimensionalStabilizer.MAX_LOAD_RADIUS) {
            if (leftClick) {
                for (int i = 1; i <= TileEntityDimensionalStabilizer.MAX_LOAD_RADIUS; i++) {
                    if (hasAtRadius(i, false)) {
                        sendPacket(TileEntityDimensionalStabilizer.PACKET_ENABLE_RADIUS, i);
                        return;
                    }
                }
            } else {
                for (int i = TileEntityDimensionalStabilizer.MAX_LOAD_RADIUS; i > 0; i--) {
                    if (hasAtRadius(i, true)) {
                        sendPacket(TileEntityDimensionalStabilizer.PACKET_DISABLE_RADIUS, i);
                        return;
                    }
                }
            }
        } else {
            sendPacket(TileEntityDimensionalStabilizer.PACKET_TOGGLE_CHUNK, x, z);
        }
    }

    private boolean hasAtRadius(int radius, boolean state) {
        for (int x = -radius; x <= radius; x++) {
            boolean skipInner = x > -radius && x < radius;
            int actualX = x + TileEntityDimensionalStabilizer.MAX_LOAD_RADIUS;
            for (int z = -radius; z <= radius; z += skipInner ? 2 * radius : 1) {
                if (tileEntity.isChunkLoadingAt(actualX, z + TileEntityDimensionalStabilizer.MAX_LOAD_RADIUS) == state) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> getToggleChunkButtonTooltips(int chunkGridX, int chunkGridZ, boolean loading) {
        int tileChunkX = tileEntity.getPos().getX() >> 4;
        int tileChunkZ = tileEntity.getPos().getZ() >> 4;
        int chunkX = tileChunkX + chunkGridX - TileEntityDimensionalStabilizer.MAX_LOAD_RADIUS;
        int chunkZ = tileChunkZ + chunkGridZ - TileEntityDimensionalStabilizer.MAX_LOAD_RADIUS;
        EnumColor stateColor = loading ? EnumColor.BRIGHT_GREEN : EnumColor.RED;
        String state = stateColor + LangUtils.transOnOff(loading) + EnumColor.WHITE;
        return Arrays.asList(LangUtils.localizeWithFormat("gui.mekanism.stabilizer.toggle_loading", getColoredChunkCoords(chunkX, chunkZ), state));
    }

    private List<String> getChunkButtonTooltips(int chunkGridX, int chunkGridZ) {
        int tileChunkX = tileEntity.getPos().getX() >> 4;
        int tileChunkZ = tileEntity.getPos().getZ() >> 4;
        int chunkX = tileChunkX + chunkGridX - TileEntityDimensionalStabilizer.MAX_LOAD_RADIUS;
        int chunkZ = tileChunkZ + chunkGridZ - TileEntityDimensionalStabilizer.MAX_LOAD_RADIUS;
        String coloredCoords = getColoredChunkCoords(chunkX, chunkZ);
        List<String> tooltips = new ArrayList<>();
        if (chunkGridX == TileEntityDimensionalStabilizer.MAX_LOAD_RADIUS && chunkGridZ == TileEntityDimensionalStabilizer.MAX_LOAD_RADIUS) {
            tooltips.add(LangUtils.localizeWithFormat("gui.mekanism.stabilizer.center", coloredCoords));
            for (int i = 1; i <= TileEntityDimensionalStabilizer.MAX_LOAD_RADIUS; i++) {
                if (hasAtRadius(i, false)) {
                    tooltips.add(" ");
                    tooltips.add(LangUtils.localizeWithFormat("gui.mekanism.stabilizer.radius.enable", coloredCoords, EnumColor.INDIGO + Integer.toString(i) + EnumColor.WHITE));
                    break;
                }
            }
            for (int i = TileEntityDimensionalStabilizer.MAX_LOAD_RADIUS; i > 0; i--) {
                if (hasAtRadius(i, true)) {
                    tooltips.add(" ");
                    tooltips.add(LangUtils.localizeWithFormat("gui.mekanism.stabilizer.radius.disable", coloredCoords, EnumColor.INDIGO + Integer.toString(i) + EnumColor.WHITE));
                    break;
                }
            }
        } else {
            boolean loading = tileEntity.isChunkLoadingAt(chunkGridX, chunkGridZ);
            EnumColor stateColor = loading ? EnumColor.BRIGHT_GREEN : EnumColor.RED;
            String state = stateColor + LangUtils.transOnOff(loading) + EnumColor.WHITE;
            tooltips.add(LangUtils.localizeWithFormat("gui.mekanism.stabilizer.toggle_loading", coloredCoords, state));
        }
        return tooltips;
    }

    private String getColoredChunkCoords(int chunkX, int chunkZ) {
        return EnumColor.INDIGO + Integer.toString(chunkX) + EnumColor.WHITE + ", " + EnumColor.INDIGO + chunkZ + EnumColor.WHITE;
    }

    private void sendPacket(int type, int value) {
        Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(type, value)));
    }

    private void sendPacket(int type, int x, int z) {
        Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(type, x, z)));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 6);
        renderInventoryText();
        drawScaledScrollingString(new TextComponentGroup().translation("direction.north.short"), 49, 41, TextAlignment.CENTER, titleTextColor(), 15, 2, false, 1, GuiElement.getMillis());
        super.drawForegroundText(mouseX, mouseY);
    }
}
