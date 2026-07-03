package mekanism.client.gui.machine;

import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.common.inventory.container.ContainerAmbientAccumulator;
import mekanism.common.recipe.machines.AmbientGasRecipe;
import mekanism.common.tile.machine.TileEntityAmbientAccumulator;
import mekanism.common.util.LangUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiAmbientAccumulator extends GuiConfigurableTile<TileEntityAmbientAccumulator, ContainerAmbientAccumulator> {

    public GuiAmbientAccumulator(InventoryPlayer inventory, TileEntityAmbientAccumulator tile) {
        super(tile, new ContainerAmbientAccumulator(inventory, tile));
        ySize += 5;
        inventoryLabelY += 2;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiInnerScreen(this, 7, 13, 80, 65, this::getScreenText).clearFormat().padding(1).clearSpacing().textScale(0.8F)
              .recipeViewerCategories(RecipeViewerRecipeType.AMBIENT_ACCUMULATOR));
        addButton(new GuiGasGauge(this, tileEntity.collectedGas, GuiGasGauge.Type.WIDE, 102, 13).withColor(GuiGasGauge.GaugeColor.ORANGE));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }

    private List<ITextComponent> getScreenText() {
        List<ITextComponent> list = new ArrayList<>();
        list.add(new TextComponentString(LangUtils.localize("gui.dimensionId") + ":" + tileEntity.getWorld().provider.getDimension()));
        list.add(new TextComponentString(LangUtils.localize("gui.dimensionName") + ":"));
        list.add(new TextComponentString(tileEntity.getWorld().provider.getDimensionType().getName()));
        AmbientGasRecipe recipe = tileEntity.getRecipe();
        if (recipe != null) {
            list.add(new TextComponentString(LangUtils.localize("gui.dimensionGas") + ":"));
            list.add(new TextComponentString(recipe.getOutput().output.getGas().getLocalizedName()));
            list.add(new TextComponentString(LangUtils.localize("gui.probability") + ":" + Math.round(recipe.getOutput().primaryChance * 100) + "%"));
        } else {
            list.add(new TextComponentString(LangUtils.localize("gui.dimensionNoGas")));
        }
        list.add(new TextComponentString(tileEntity.collectedGas.getStored() + " / " + tileEntity.collectedGas.getMaxGas()));
        return list;
    }
}