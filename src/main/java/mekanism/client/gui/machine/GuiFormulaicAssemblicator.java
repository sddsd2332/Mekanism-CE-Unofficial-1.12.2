package mekanism.client.gui.machine;

import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.GuiUtils;
import mekanism.api.TileNetworkList;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.button.ToggleButton;
import mekanism.client.gui.element.button.TooltipToggleButton;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.ContainerFormulaicAssemblicator;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.item.ItemCraftingFormula;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.tile.machine.TileEntityFormulaicAssemblicator;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiFormulaicAssemblicator extends GuiConfigurableTile<TileEntityFormulaicAssemblicator, ContainerFormulaicAssemblicator> {

    private MekanismButton encodeFormulaButton;
    private MekanismButton stockControlButton;
    private MekanismButton fillEmptyButton;
    private MekanismButton craftSingleButton;
    private MekanismButton craftAvailableButton;
    private MekanismButton autoModeButton;

    public GuiFormulaicAssemblicator(InventoryPlayer inventory, TileEntityFormulaicAssemblicator tile) {
        super(tile, new ContainerFormulaicAssemblicator(inventory, tile));
        dynamicSlots = true;
        ySize += 64;
        inventoryLabelY = ySize - 94;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiVerticalPowerBar(this, tileEntity.getEnergyContainer(), 159, 15))
              .warning(WarningType.NOT_ENOUGH_ENERGY, () -> tileEntity.autoMode && tileEntity.isRecipe && tileEntity.getEnergy() < tileEntity.energyPerTick);
        addButton(new GuiEnergyTab(this, tileEntity.getEnergyContainer(), tileEntity::usedEnergy));
        addButton(new GuiSlot(SlotType.OUTPUT_LARGE, this, 115, 16));
        addButton(new GuiProgress(() -> tileEntity.operatingTicks / (double) tileEntity.ticksRequired, ProgressType.TALL_RIGHT, this, 86, 43).recipeViewerCrafting());
        encodeFormulaButton = addButton(new MekanismImageButton(this, 7, 45, 14, getButtonLocation("encode_formula"),
              () -> sendButtonPacket(1)).setTooltip(MekanismLang.ENCODE_FORMULA.translate()));
        stockControlButton = addButton(new TooltipToggleButton(this, 26, 75, 16, getButtonLocation("stock_control"), () -> tileEntity.stockControl,
              () -> sendButtonPacket(5), MekanismLang.STOCK_CONTROL.translate(MekanismLang.ON.translate()),
              MekanismLang.STOCK_CONTROL.translate(MekanismLang.OFF.translate())));
        fillEmptyButton = addButton(new ToggleButton(this, 44, 75, 16, 16, getButtonLocation("empty"), getButtonLocation("fill"),
              () -> tileEntity.formula == null, () -> sendButtonPacket(tileEntity.formula == null ? 6 : 4),
              MekanismLang.EMPTY_ASSEMBLICATOR.translate(), MekanismLang.FILL_ASSEMBLICATOR.translate()));
        craftSingleButton = addButton(new MekanismImageButton(this, 71, 75, 16, getButtonLocation("craft_single"),
              () -> sendButtonPacket(2)).setTooltip(MekanismLang.CRAFT_SINGLE.translate()));
        craftAvailableButton = addButton(new MekanismImageButton(this, 89, 75, 16, getButtonLocation("craft_available"),
              () -> sendButtonPacket(3)).setTooltip(MekanismLang.CRAFT_AVAILABLE.translate()));
        autoModeButton = addButton(new TooltipToggleButton(this, 107, 75, 16, getButtonLocation("auto_toggle"), () -> tileEntity.autoMode,
              () -> sendButtonPacket(0), MekanismLang.AUTO_MODE.translate(MekanismLang.ON.translate()),
              MekanismLang.AUTO_MODE.translate(MekanismLang.OFF.translate())));
        updateEnabledButtons();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        updateEnabledButtons();
    }

    @Override
    protected ItemStack checkValidity(int slotIndex) {
        int formulaIndex = slotIndex - getCraftingGridContainerSlotOffset();
        if (formulaIndex >= 0 && formulaIndex < 9 && tileEntity.formula != null) {
            ItemStack stack = tileEntity.formula.input.get(formulaIndex);
            Slot slot = inventorySlots.inventorySlots.get(slotIndex);
            if (!stack.isEmpty() && (slot.getStack().isEmpty() || !tileEntity.formula.isIngredientInPos(tileEntity.getWorld(), slot.getStack(), formulaIndex))) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTick, int mouseX, int mouseY) {
        super.drawGuiContainerBackgroundLayer(partialTick, mouseX, mouseY);
        SlotOverlay overlay = tileEntity.isRecipe ? SlotOverlay.CHECK : SlotOverlay.X;
        MekanismRenderer.bindTexture(overlay.getTexture());
        GuiUtils.blit(guiLeft + 88, guiTop + 22, 0, 0, overlay.getWidth(), overlay.getHeight(), overlay.getWidth(), overlay.getHeight());
    }

    private void updateEnabledButtons() {
        encodeFormulaButton.active = !tileEntity.autoMode && tileEntity.isRecipe && canEncode();
        stockControlButton.active = tileEntity.formula != null;
        fillEmptyButton.active = !tileEntity.autoMode;
        craftSingleButton.active = !tileEntity.autoMode && tileEntity.isRecipe;
        craftAvailableButton.active = !tileEntity.autoMode && tileEntity.isRecipe;
        autoModeButton.active = tileEntity.formula != null;
    }

    private void sendButtonPacket(int type) {
        Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(type)));
    }

    private int getCraftingGridContainerSlotOffset() {
        return (tileEntity.supportsUpgrades() ? 2 : 0) + TileEntityFormulaicAssemblicator.SLOT_CRAFT_MATRIX_FIRST;
    }

    private boolean canEncode() {
        if (tileEntity.formula != null) {
            return false;
        }
        ItemStack formulaStack = tileEntity.getStackInSlot(TileEntityFormulaicAssemblicator.SLOT_FORMULA);
        return !formulaStack.isEmpty() && formulaStack.getItem() instanceof ItemCraftingFormula &&
              ((ItemCraftingFormula) formulaStack.getItem()).getInventory(formulaStack) == null;
    }
}