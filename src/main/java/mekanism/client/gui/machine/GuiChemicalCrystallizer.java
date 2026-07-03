package mekanism.client.gui.machine;

import mekanism.client.gui.GuiConfigurableTile;
import mekanism.api.gas.Gas;
import mekanism.api.gas.OreGas;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSequencedSlotDisplay;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.common.inventory.container.ContainerChemicalCrystallizer;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.machines.CrystallizerRecipe;
import mekanism.common.tile.machine.TileEntityChemicalCrystallizer;
import mekanism.common.util.LangUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiChemicalCrystallizer extends GuiConfigurableTile<TileEntityChemicalCrystallizer, ContainerChemicalCrystallizer> {

    private final List<ItemStack> iterStacks = new ArrayList<>();
    private GuiSequencedSlotDisplay slotDisplay;
    private GuiElement inputGauge;
    private Gas prevGas;

    public GuiChemicalCrystallizer(InventoryPlayer inventory, TileEntityChemicalCrystallizer tile) {
        super(tile, new ContainerChemicalCrystallizer(inventory, tile));
        dynamicSlots = true;
        titleLabelY = 4;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiVerticalPowerBar(this, tileEntity.getEnergyContainer(), 157, 23))
              .warning(WarningType.NOT_ENOUGH_ENERGY, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_ENERGY));
        addButton(new GuiEnergyTab(this, tileEntity.getEnergyContainer(), tileEntity::getActive));
        inputGauge = addButton(new GuiGasGauge(this, tileEntity.inputTank, 7, 4)
              .warning(WarningType.NO_MATCHING_RECIPE, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_INPUT)));
        addButton(new GuiProgress(tileEntity::getScaledProgress, ProgressType.LARGE_RIGHT, this, 53, 61))
              .recipeViewerCategories(RecipeViewerRecipeType.CRYSTALLIZING)
              .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT, tileEntity.getWarningCheck(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT));
        addButton(new GuiInnerScreen(this, 31, 13, 115, 42, this::getScreenText).clearFormat().padding(2).spacing(1));
        addButton(new GuiSlot(SlotType.ORE, this, 128, 13).setRenderAboveSlots());
        slotDisplay = addButton(new GuiSequencedSlotDisplay(this, 129, 14, () -> iterStacks));
        updateRenderedStacks();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        updateRenderedStacks();
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleTextWithOffset(new TextComponentString(tileEntity.getName()), inputGauge.getRelativeRight(), 4, tileEntity.getEnergySlotX());
        super.drawForegroundText(mouseX, mouseY);
    }

    private List<ITextComponent> getScreenText() {
        List<ITextComponent> text = new ArrayList<>();
        Gas inputGas = getInputGas();
        if (inputGas == null) {
            return text;
        }
        text.add(new TextComponentString(inputGas.getLocalizedName()));
        if (inputGas instanceof OreGas oreGas) {
            text.add(new TextComponentString("(" + oreGas.getOreName() + ")"));
        } else {
            CrystallizerRecipe recipe = tileEntity.getRecipe();
            if (recipe == null) {
                text.add(new TextComponentString("(" + LangUtils.localize("gui.noRecipe") + ")"));
            } else {
                text.add(new TextComponentString("(" + recipe.getOutput().output.getDisplayName() + ")"));
            }
        }
        return text;
    }

    private Gas getInputGas() {
        return tileEntity.inputTank.getGas() != null ? tileEntity.inputTank.getGas().getGas() : null;
    }

    private void updateRenderedStacks() {
        Gas inputGas = getInputGas();
        if (prevGas == inputGas) {
            return;
        }
        prevGas = inputGas;
        iterStacks.clear();
        if (inputGas instanceof OreGas oreGas && oreGas.isClean()) {
            updateStackList("ore" + oreGas.getName().substring(5));
        }
        if (slotDisplay != null) {
            slotDisplay.updateStackList();
        }
    }

    private void updateStackList(String oreName) {
        List<String> keys = new ArrayList<>();
        for (String candidate : OreDictionary.getOreNames()) {
            if (oreName.equals(candidate) || oreName.equals("*")) {
                keys.add(candidate);
            } else {
                boolean endsWith = oreName.endsWith("*");
                boolean startsWith = oreName.startsWith("*");
                if (endsWith && !startsWith) {
                    if (candidate.startsWith(oreName.substring(0, oreName.length() - 1))) {
                        keys.add(candidate);
                    }
                } else if (startsWith && !endsWith) {
                    if (candidate.endsWith(oreName.substring(1))) {
                        keys.add(candidate);
                    }
                } else if (startsWith && endsWith) {
                    if (candidate.contains(oreName.substring(1, oreName.length() - 1))) {
                        keys.add(candidate);
                    }
                }
            }
        }
        for (String key : keys) {
            for (ItemStack stack : OreDictionary.getOres(key, false)) {
                ItemStack toAdd = stack.copy();
                if (toAdd.getItem() instanceof ItemBlock && !containsMatchingStack(toAdd)) {
                    iterStacks.add(toAdd);
                }
            }
        }
    }

    private boolean containsMatchingStack(ItemStack stack) {
        for (ItemStack iterStack : iterStacks) {
            if (ItemStack.areItemsEqual(iterStack, stack) && ItemStack.areItemStackTagsEqual(iterStack, stack)) {
                return true;
            }
        }
        return false;
    }
}