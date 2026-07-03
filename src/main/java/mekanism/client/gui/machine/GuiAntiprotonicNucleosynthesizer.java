package mekanism.client.gui.machine;

import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.client.gui.element.bar.GuiDynamicHorizontalRateBar;
import mekanism.client.gui.element.gauge.GuiEnergyGauge;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.lib.effect.BoltRenderer;
import mekanism.common.inventory.container.ContainerAntiprotonicNucleosynthesizer;
import mekanism.common.lib.Color;
import mekanism.common.lib.effect.BoltEffect;
import mekanism.common.lib.effect.BoltEffect.BoltRenderInfo;
import mekanism.common.lib.effect.BoltEffect.FadeFunction;
import mekanism.common.lib.effect.BoltEffect.SpawnFunction;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.tile.machine.TileEntityAntiprotonicNucleosynthesizer;
import mekanism.common.util.LangUtils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.function.Supplier;

@SideOnly(Side.CLIENT)
public class GuiAntiprotonicNucleosynthesizer extends GuiConfigurableTile<TileEntityAntiprotonicNucleosynthesizer, ContainerAntiprotonicNucleosynthesizer> {

    private static final Vec3d BOLT_FROM = new Vec3d(47, 50, 0);
    private static final Vec3d BOLT_TO = new Vec3d(147, 50, 0);
    private static final BoltRenderInfo BOLT_RENDER_INFO = new BoltRenderInfo().color(Color.rgbad(0.45F, 0.45F, 0.5F, 1));

    private final BoltRenderer bolt = new BoltRenderer();
    private final Supplier<BoltEffect> boltSupplier = () -> new BoltEffect(BOLT_RENDER_INFO, BOLT_FROM, BOLT_TO, 15)
          .count((int) Math.min(Math.ceil(tileEntity.getProcessRate() / 8F), 20))
          .size(1)
          .lifespan(1)
          .spawn(SpawnFunction.CONSECUTIVE)
          .fade(FadeFunction.NONE);
    private GuiInnerScreen screen;

    public GuiAntiprotonicNucleosynthesizer(InventoryPlayer inventory, TileEntityAntiprotonicNucleosynthesizer tile) {
        super(tile, new ContainerAntiprotonicNucleosynthesizer(inventory, tile));
        xSize += 20;
        ySize += 27;
        dynamicSlots = true;
        inventoryLabelY = ySize - 93;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        screen = addButton(new GuiInnerScreen(this, 45, 18, 104, 68).recipeViewerCategories(RecipeViewerRecipeType.NUCLEOSYNTHESIZING));
        addButton(new GuiEnergyTab(this, tileEntity.getEnergyContainer(), tileEntity::getEnergyUsed));
        addButton(new GuiGasGauge(this, tileEntity.inputGasTank, GuiGasGauge.Type.SMALL_MED, 5, 18)
              .warning(WarningType.NO_MATCHING_RECIPE, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_SECONDARY_INPUT)));
        addButton(new GuiEnergyGauge(this, tileEntity.getEnergyContainer(), GuiEnergyGauge.Type.SMALL_MED, 172, 18))
              .warning(WarningType.NOT_ENOUGH_ENERGY, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_ENERGY));
        addButton(new GuiDynamicHorizontalRateBar(this, new IBarInfoHandler() {
            @Override
            public ITextComponent getTooltip() {
                return new TextComponentString(LangUtils.localize("gui.progress") + ": " + (int) (tileEntity.getScaledProgress() * 100) + "%");
            }

            @Override
            public double getLevel() {
                return Math.min(1, tileEntity.getScaledProgress());
            }
        }, 5, 88, 183, Color.ColorFunction.scale(Color.rgbi(60, 45, 74), Color.rgbi(100, 30, 170))))
              .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT, tileEntity.getWarningCheck(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 6);
        renderInventoryText(8, ySize - 93, getXSize());
        if (screen != null) {
            screen.drawScaledScrollingString(new TextComponentString(LangUtils.localize("gui.processRate") + ": " + (int) (tileEntity.getProcessRate() * 100) + "%"),
                  0, screen.getHeight() - 10, TextAlignment.CENTER, screenTextColor(), screen.getWidth(), 2, false, 1, GuiElement.getMillis());
        }
        super.drawForegroundText(mouseX, mouseY);
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 100);
        float partialTicks = MekanismRenderer.getPartialTick();
        bolt.update(this, boltSupplier.get(), partialTicks);
        bolt.renderGui(partialTicks);
        GlStateManager.popMatrix();
    }

}