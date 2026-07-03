package mekanism.client.gui.machine;

import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.GuiDumpButton;
import mekanism.client.gui.element.bar.GuiInfuseBar;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.common.inventory.container.ContainerMetallurgicInfuser;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.tile.machine.TileEntityMetallurgicInfuser;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiMetallurgicInfuser extends GuiConfigurableTile<TileEntityMetallurgicInfuser, ContainerMetallurgicInfuser> {

    public GuiMetallurgicInfuser(InventoryPlayer inventory, TileEntityMetallurgicInfuser tile) {
        super(tile, new ContainerMetallurgicInfuser(inventory, tile));
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiVerticalPowerBar(this, tileEntity.getEnergyContainer(), 164, 15))
              .warning(WarningType.NOT_ENOUGH_ENERGY, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_ENERGY));
        addButton(new GuiEnergyTab(this, tileEntity.getEnergyContainer(), tileEntity::getActive));
        addButton(new GuiProgress(tileEntity::getScaledProgress, ProgressType.RIGHT, this, 72, 47))
              .recipeViewerCategories(RecipeViewerRecipeType.METALLURGIC_INFUSING)
              .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT, tileEntity.getWarningCheck(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT));
        addButton(new GuiInfuseBar(this, tileEntity.infuseStored, () -> tileEntity.getScaledInfuseLevel(52) / 52D,
              tileEntity::getInfuseTooltip, 7, 15, 4, 52, true))
              .warning(WarningType.NO_MATCHING_RECIPE, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_SECONDARY_INPUT));
        addButton(new GuiDumpButton<>(this, tileEntity, 147, 72));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }
}
