package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.render.IFancyFontRenderer.TextAlignment;
import mekanism.common.MekanismLang;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.util.text.TextUtils;
import mekanism.qioprocessing.common.QIOProcessingWindowTypes;
import mekanism.qioprocessing.common.content.processor.QIOProcessorDisplaySnapshot;
import mekanism.qioprocessing.common.inventory.container.ContainerQIOCraftingProcessor;
import mekanism.qioprocessing.common.tile.QIOCraftingProcessor;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SideOnly(Side.CLIENT)
public final class GuiQIOCraftingProcessor extends
      GuiMekanismTile<QIOCraftingProcessor, ContainerQIOCraftingProcessor> {

    private static final int ORDINARY_HEIGHT = 193;
    private static final int FACTORY_HEIGHT = 183;

    private GuiQIOCraftingProcessorFrequencyTab frequencyTab;
    private final Map<Integer, GuiQIOCraftingProcessorRecipeWindow> recipeWindows =
          new LinkedHashMap<>();
    private final ContainerQIOCraftingProcessor container;
    private final boolean factory;

    public GuiQIOCraftingProcessor(InventoryPlayer inventory, QIOCraftingProcessor processor) {
        super(processor, new ContainerQIOCraftingProcessor(inventory, processor));
        container = (ContainerQIOCraftingProcessor) inventorySlots;
        factory = processor.isFactoryProcessor();
        dynamicSlots = true;
        xSize = factory && processor.getVisibleLaneCount() >= 9 ? 210 : 176;
        ySize = factory ? FACTORY_HEIGHT : ORDINARY_HEIGHT;
        inventoryLabelX = factory && processor.getVisibleLaneCount() >= 9 ? 26 : 8;
        inventoryLabelY = factory ? 91 : 101;
        titleLabelY = 6;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        frequencyTab = addButton(new GuiQIOCraftingProcessorFrequencyTab(this, container,
              () -> frequencyTab));
        addButton(new GuiInnerScreen(this, 7, 18, getXSize() - 14, 14,
              this::frequencyText).clearFormat().clearSpacing().padding(3).textScale(0.8F));
        addButton(new GuiEnergyTab(this, tileEntity.getMainEnergyContainer(),
              () -> tileEntity.getActiveLaneCount() > 0));
        if (factory) {
            addFactoryElements();
        } else {
            addOrdinaryElements();
        }
    }

    private void addOrdinaryElements() {
        addButton(new GuiQIOVirtualWorkbenchRecipe(this, 3, 38,
              () -> tileEntity.getLaneDisplay(0),
              () -> tileEntity.getScaledLaneProgress(0)));
        addButton(new GuiVerticalPowerBar(this, tileEntity.getMainEnergyContainer(),
              getXSize() - 12, 39, 52));
    }

    private void addFactoryElements() {
        addButton(new GuiVerticalPowerBar(this, tileEntity.getMainEnergyContainer(),
              getXSize() - 12, 36, 52));
        for (int lane = 0; lane < tileEntity.getVisibleLaneCount(); lane++) {
            final int laneId = lane;
            GuiSlot laneSlot = new GuiSlot(SlotType.NORMAL, this,
                  factoryLaneSlotX(lane), 39) {
                @Override
                public void renderToolTip(int mouseX, int mouseY) {
                    super.renderToolTip(mouseX, mouseY);
                    if (!isMouseOverTooltip(mouseX, mouseY)) {
                        return;
                    }
                    QIOProcessorDisplaySnapshot.Lane display =
                          tileEntity.getLaneDisplay(laneId);
                    ItemStack output = display == null ? ItemStack.EMPTY : display.getOutput();
                    List<String> details = laneTooltip(laneId, display);
                    if (output.isEmpty()) {
                        displayTooltips(details, mouseX, mouseY);
                    } else {
                        renderItemTooltipWithExtra(output, mouseX, mouseY, details);
                    }
                }
            }.stored(() -> laneOutput(laneId)).setRenderHover(true)
                  .click((element, mouseX, mouseY) -> openLaneWindow(laneId));
            laneSlot.active = true;
            addButton(laneSlot);
            addButton(new GuiProgress(() -> tileEntity.getScaledLaneProgress(laneId),
                  ProgressType.DOWN, this, factoryLaneProgressX(lane), 58)
                  .recipeViewerCrafting());
        }
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 6);
        if (factory) {
            for (int lane = 0; lane < tileEntity.getVisibleLaneCount(); lane++) {
                QIOProcessorDisplaySnapshot.Lane display = tileEntity.getLaneDisplay(lane);
                drawScaledScrollingString(QIOProcessorGuiText.laneState(
                            display == null ? null : display.getState()),
                      factoryLaneSlotX(lane) - 3, 80, TextAlignment.CENTER,
                      titleTextColor(), 24, 0, false, 0.6F, getTimeOpened());
            }
        }
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }

    private List<ITextComponent> frequencyText() {
        QIOFrequency frequency = tileEntity.getQIOFrequency();
        return Collections.singletonList(MekanismLang.FREQUENCY.translate(
              frequency == null ? "-" : frequency.getName()));
    }

    private List<String> laneTooltip(int lane,
          QIOProcessorDisplaySnapshot.Lane display) {
        List<String> details = new ArrayList<>(3);
        details.add(new TextComponentTranslation(
              "gui.mekanismqioprocessing.processor_lane_number", lane + 1)
              .getFormattedText());
        details.add(new TextComponentTranslation(
              "gui.mekanismqioprocessing.processor_lane_state_batch",
              QIOProcessorGuiText.laneState(display == null ? null : display.getState()),
              TextUtils.format(display == null ? 0 : display.getOperationCount()))
              .getFormattedText());
        details.add(new TextComponentTranslation(
              "gui.mekanismqioprocessing.processor_lane_open_hint").getFormattedText());
        return details;
    }

    private ItemStack laneOutput(int lane) {
        QIOProcessorDisplaySnapshot.Lane display = tileEntity.getLaneDisplay(lane);
        return display == null ? ItemStack.EMPTY : display.getOutput();
    }

    private boolean openLaneWindow(int lane) {
        GuiQIOCraftingProcessorRecipeWindow existing = recipeWindows.get(lane);
        if (existing != null && getWindows().contains(existing)) {
            focusWindow(existing);
            return true;
        }
        recipeWindows.remove(lane);
        int windowX = (getXSize() - 136) / 2 + lane % 3 * 18;
        int windowY = 26 + lane / 3 * 18;
        GuiQIOCraftingProcessorRecipeWindow window =
              new GuiQIOCraftingProcessorRecipeWindow(this, windowX, windowY,
                    tileEntity, lane, () -> recipeWindows.remove(lane));
        recipeWindows.put(lane, window);
        addWindow(window);
        return true;
    }

    @Override
    protected void initPinnedWindows() {
        super.initPinnedWindows();
        if (!factory) {
            return;
        }
        for (int lane = 0; lane < tileEntity.getVisibleLaneCount(); lane++) {
            if (!recipeWindows.containsKey(lane) &&
                new SelectedWindowData(QIOProcessingWindowTypes.CRAFTING_PROCESSOR_RECIPE,
                      (byte) lane).wasPinned()) {
                openLaneWindow(lane);
            }
        }
    }

    private int factoryLaneSlotX(int lane) {
        int count = tileEntity.getVisibleLaneCount();
        int base = count <= 3 ? 54 : count <= 5 ? 34 : count <= 7 ? 28 : 26;
        int spacing = count <= 3 ? 38 : count <= 5 ? 26 : 19;
        return base + lane * spacing;
    }

    private int factoryLaneProgressX(int lane) {
        return factoryLaneSlotX(lane) + 5;
    }
}
