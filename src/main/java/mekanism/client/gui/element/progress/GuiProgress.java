package mekanism.client.gui.element.progress;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiTexturedElement;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.recipe_viewer.RecipeViewerUtils;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerRecipeArea;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.Mekanism;
import mekanism.common.inventory.warning.ISupportsWarning;
import mekanism.common.util.LangUtils;

import javax.annotation.Nonnull;
import java.util.function.BooleanSupplier;

public class GuiProgress extends GuiTexturedElement implements IRecipeViewerRecipeArea<GuiProgress>, ISupportsWarning<GuiProgress> {

    protected final IProgressInfoHandler handler;
    protected final ProgressType type;
    private IRecipeViewerRecipeType<?>[] recipeCategories;
    private BooleanSupplier warningSupplier;

    public GuiProgress(IProgressInfoHandler.IBooleanProgressInfoHandler handler, ProgressType type, IGuiWrapper gui, int x, int y) {
        this((IProgressInfoHandler) handler, type, gui, x, y);
    }

    public GuiProgress(IProgressInfoHandler handler, ProgressType type, IGuiWrapper gui, int x, int y) {
        super(type.getTexture(), gui, x, y, type.getWidth(), type.getHeight());
        this.handler = handler;
        this.type = type;
    }

    public GuiProgress warning(@Nonnull WarningType type, @Nonnull BooleanSupplier warningSupplier) {
        this.warningSupplier = ISupportsWarning.compound(this.warningSupplier, gui().trackWarning(type, warningSupplier));
        return this;
    }

    @Override
    public GuiProgress warning(@Nonnull mekanism.common.inventory.warning.WarningTracker.WarningType type, @Nonnull BooleanSupplier warningSupplier) {
        this.warningSupplier = ISupportsWarning.compound(this.warningSupplier, gui().trackWarning(type, warningSupplier));
        return this;
    }

    @Override
    public boolean isRecipeViewerAreaActive() {
        return handler.isActive();
    }

    @Override
    public GuiProgress recipeViewerCategories(IRecipeViewerRecipeType<?>... recipeCategories) {
        this.recipeCategories = recipeCategories;
        return this;
    }

    @Override
    public IRecipeViewerRecipeType<?>[] getRecipeCategories() {
        return recipeCategories;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (recipeCategories != null && button == 0 && isMouseOverRecipeViewerArea(mouseX, mouseY)) {
            return RecipeViewerUtils.openRecipeViewerRecipes(this, mouseX, mouseY, button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        if (!handler.isActive()) {
            return;
        }
        boolean warning = warningSupplier != null && warningSupplier.getAsBoolean();
        MekanismRenderer.bindTexture(getResource());
        GuiUtils.blit(relativeX, relativeY, 0, 0, width, height, type.getTextureWidth(), type.getTextureHeight());
        double progress = warning ? 1 : getProgress();
        if (type.isVertical()) {
            int displayInt = (int) (progress * height);
            if (displayInt > 0) {
                int innerOffsetY = 0;
                if (type.isReverse()) {
                    innerOffsetY += height - displayInt;
                }
                GuiUtils.blit(relativeX, relativeY + innerOffsetY, type.getOverlayX(warning), type.getOverlayY(warning) + innerOffsetY, width, displayInt, type.getTextureWidth(),
                        type.getTextureHeight());
            }
        } else {
            int innerOffsetX = type == ProgressType.BAR ? 1 : 0;
            int displayInt = (int) (progress * (width - 2 * innerOffsetX));
            if (displayInt > 0) {
                if (type.isReverse()) {
                    innerOffsetX += width - displayInt;
                }
                GuiUtils.blit(relativeX + innerOffsetX, relativeY, type.getOverlayX(warning) + innerOffsetX, type.getOverlayY(warning), displayInt, height, type.getTextureWidth(),
                        type.getTextureHeight());
            }
        }
        MekanismRenderer.resetColor();
    }

    protected double getProgress() {
        return Math.max(0, Math.min(handler.getProgress(), 1));
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        double progress = handler.getProgress();
        if (handler.isActive()) {
            if (Mekanism.hooks.JEI && !handler.isGuiInJei() && progress == 0) {
                gui().displayTooltip(LangUtils.localize("gui.mekanism.show.recipes"), mouseX, mouseY);
            } else if (progress != 0 && progress != 1) {
                gui().displayTooltip(LangUtils.localize("gui.mekanism.progress") + String.format("%.2f", progress * 100) + "%", mouseX, mouseY);
            }

        }
    }


}
