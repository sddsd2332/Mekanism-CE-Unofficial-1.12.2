package mekanism.client.gui.machine;

import mekanism.client.gui.GuiMekanismTile;
import mekanism.api.Coord4D;
import mekanism.api.TileNetworkList;
import mekanism.client.gui.element.GuiDigitalSwitch;
import mekanism.client.gui.element.GuiDigitalSwitch.SwitchType;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.button.TranslationButton;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.element.tab.GuiVisualsTab;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.content.miner.MinerFilter;
import mekanism.common.content.miner.ThreadMinerSearch.State;
import mekanism.common.inventory.container.ContainerDigitalMiner;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.network.PacketDigitalMinerGui.DigitalMinerGuiMessage;
import mekanism.common.network.PacketDigitalMinerGui.MinerGuiPacket;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.text.TextUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiDigitalMiner extends GuiMekanismTile<TileEntityDigitalMiner, ContainerDigitalMiner> {

    private static final ResourceLocation EJECT = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "switch/eject.png");
    private static final ResourceLocation INPUT = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "switch/input.png");
    private static final ResourceLocation SILK = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "switch/silk.png");

    private MekanismButton startButton;
    private MekanismButton stopButton;
    private MekanismButton configButton;

    public GuiDigitalMiner(InventoryPlayer inventory, TileEntityDigitalMiner tile) {
        super(tile, new ContainerDigitalMiner(inventory, tile));
        dynamicSlots = true;
        ySize += 76;
        inventoryLabelY = ySize - 94;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        int missingStackX = 64;
        addButton(new GuiInnerScreen(this, 7, 19, 77, 69, this::getStatusText) {
            @Override
            protected int getMaxTextWidth(int row) {
                if (row < 2) {
                    return missingStackX - relativeX + 4;
                }
                return super.getMaxTextWidth(row);
            }
        }.spacing(0).clearFormat());
        addButton(new GuiDigitalSwitch(this, 19, 56, EJECT, () -> tileEntity.doEject, (element, mouseX, mouseY) -> {
            sendToggle(0);
            return true;
        }, SwitchType.LOWER_ICON))
              .setTooltip(MekanismLang.AUTO_EJECT.translate());
        addButton(new GuiDigitalSwitch(this, 38, 56, INPUT, () -> tileEntity.doPull, (element, mouseX, mouseY) -> {
            sendToggle(1);
            return true;
        }, SwitchType.LOWER_ICON))
              .setTooltip(MekanismLang.AUTO_PULL.translate());
        addButton(new GuiDigitalSwitch(this, 57, 56, SILK, () -> tileEntity.silkTouch, (element, mouseX, mouseY) -> {
            sendToggle(9);
            return true;
        }, SwitchType.LOWER_ICON))
              .setTooltip(MekanismLang.MINER_SILK.translate());
        addButton(new GuiVerticalPowerBar(this, tileEntity.getMainEnergyContainer(), 157, 39, 47))
              .warning(WarningType.NOT_ENOUGH_ENERGY, () -> tileEntity.getPerTick() > tileEntity.getEnergy());
        addButton(new GuiVisualsTab<>(this, tileEntity));
        addButton(new GuiSlot(SlotType.DIGITAL, this, missingStackX, 21).setRenderAboveSlots()
              .validity(() -> tileEntity.missingStack)
              .with(() -> tileEntity.missingStack.isEmpty() ? SlotOverlay.CHECK : null)
              .hover((element, xAxis, yAxis) -> {
                  ITextComponent tooltip = tileEntity.missingStack.isEmpty() ? MekanismLang.MINER_WELL.translate() : MekanismLang.MINER_MISSING_BLOCK.translate();
                  element.displayTooltip(tooltip, xAxis, yAxis);
              }));
        addButton(new GuiEnergyTab(this, () -> {
            List<ITextComponent> info = new ArrayList<>();
            info.add(MekanismLang.MINER_ENERGY_CAPACITY.translate(MekanismUtils.getEnergyDisplay(tileEntity.getMaxEnergy())));
            info.add(MekanismLang.NEEDED_PER_TICK.translate(MekanismUtils.getEnergyDisplay(tileEntity.getPerTick())));
            info.add(MekanismLang.MINER_BUFFER_FREE.translate(MekanismUtils.getEnergyDisplay(tileEntity.getNeedEnergy())));
            return info;
        }));

        int buttonStart = 19;
        startButton = addButton(new TranslationButton(this, 87, buttonStart, 61, 18, MekanismLang.BUTTON_START,
              () -> Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(3)))));
        stopButton = addButton(new TranslationButton(this, 87, buttonStart + 17, 61, 18, MekanismLang.BUTTON_STOP,
              () -> Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(4)))));
        configButton = addButton(new TranslationButton(this, 87, buttonStart + 34, 61, 18, MekanismLang.BUTTON_CONFIG,
              () -> Mekanism.packetHandler.sendToServer(new DigitalMinerGuiMessage(MinerGuiPacket.SERVER, Coord4D.get(tileEntity), 0, 0, 0))));
        addButton(new TranslationButton(this, 87, buttonStart + 51, 61, 18, MekanismLang.MINER_RESET,
              () -> Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(5)))));
        trackWarning(WarningType.FILTER_HAS_BLACKLISTED_ELEMENT, () -> tileEntity.getFilterManager().anyEnabledMatch(MinerFilter::hasBlacklistedElement));
        updateEnabledButtons();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        updateEnabledButtons();
    }

    private void sendToggle(int type) {
        Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(type)));
    }

    private void updateEnabledButtons() {
        if (startButton != null) {
            startButton.active = tileEntity.searcher.state == State.IDLE || !tileEntity.running;
        }
        if (stopButton != null) {
            stopButton.active = tileEntity.searcher.state != State.IDLE && tileEntity.running;
        }
        if (configButton != null) {
            configButton.active = tileEntity.searcher.state == State.IDLE;
        }
    }

    private List<ITextComponent> getStatusText() {
        ITextComponent runningType;
        if (tileEntity.getPerTick() > tileEntity.getMaxEnergy()) {
            runningType = MekanismLang.MINER_LOW_POWER.translate();
        } else if (tileEntity.running) {
            runningType = MekanismLang.MINER_RUNNING.translate();
        } else {
            runningType = MekanismLang.IDLE.translate();
        }
        List<ITextComponent> text = new ArrayList<>();
        text.add(runningType);
        text.add(new TextComponentString(tileEntity.searcher.state.localize()));
        text.add(MekanismLang.MINER_TO_MINE.translate(TextUtils.format(tileEntity.clientToMine)));
        return text;
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }
}
