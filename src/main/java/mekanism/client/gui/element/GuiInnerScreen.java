package mekanism.client.gui.element;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.recipe_viewer.RecipeViewerUtils;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerRecipeArea;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.util.MekanismUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class GuiInnerScreen extends GuiScalableElement implements IRecipeViewerRecipeArea<GuiInnerScreen> {

    public static final ResourceLocation SCREEN = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "inner_screen.png");
    public static int SCREEN_SIZE = 32;

    private Supplier<List<ITextComponent>> renderStrings;
    private Supplier<List<ITextComponent>> tooltipStrings;

    private IRecipeViewerRecipeType<?>[] recipeCategories;
    private boolean centerY;
    private int spacing = 1;
    private int padding = 3;
    private float textScale = 1.0F;

    public GuiInnerScreen(IGuiWrapper gui, int x, int y, int width, int height) {
        super(SCREEN, gui, x, y, width, height, SCREEN_SIZE, SCREEN_SIZE);
    }

    public GuiInnerScreen(IGuiWrapper gui, int x, int y, int width, int height, Supplier<List<ITextComponent>> renderStrings) {
        this(gui, x, y, width, height);
        this.renderStrings = renderStrings;
        defaultFormat();
    }

    public GuiInnerScreen tooltip(Supplier<List<ITextComponent>> tooltipStrings) {
        this.tooltipStrings = tooltipStrings;
        active = true;
        return this;
    }

    @Override
    public GuiInnerScreen recipeViewerCategories(IRecipeViewerRecipeType<?>... recipeCategories) {
        this.recipeCategories = recipeCategories;
        return this;
    }

    @Override
    public IRecipeViewerRecipeType<?>[] getRecipeCategories() {
        return recipeCategories;
    }

    @Override
    public boolean isMouseOverRecipeViewerArea(double mouseX, double mouseY) {
        return visible && mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return RecipeViewerUtils.openRecipeViewerRecipes(this, mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    public GuiInnerScreen spacing(int spacing) {
        this.spacing = spacing;
        return this;
    }

    public GuiInnerScreen clearSpacing() {
        return spacing(0);
    }

    public GuiInnerScreen padding(int padding) {
        this.padding = padding;
        return this;
    }

    public GuiInnerScreen clearScale() {
        return textScale(1);
    }

    public GuiInnerScreen textScale(float textScale) {
        this.textScale = textScale;
        return this;
    }

    public GuiInnerScreen centerY() {
        centerY = true;
        return this;
    }

    public GuiInnerScreen clearFormat() {
        centerY = false;
        return this;
    }

    public GuiInnerScreen defaultFormat() {
        return padding(5).spacing(2).textScale(0.8F).centerY();
    }

    protected List<ITextComponent> getRenderStrings() {
        return renderStrings == null ? Collections.emptyList() : renderStrings.get();
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        List<ITextComponent> list = getRenderStrings();
        if (!list.isEmpty()) {
            int lineHeight = getFont().FONT_HEIGHT;
            int minY = relativeY + padding;
            int maxY = minY + lineHeight;
            int heightToNextLine = lineHeight + spacing;
            if (centerY) {
                int totalHeight = heightToNextLine * list.size() - spacing;
                float center = (getHeight() - totalHeight) / 2F;
                minY = relativeY + MathHelper.floor(center);
                maxY = relativeY + lineHeight + MathHelper.ceil(center);
            }
            int minX = relativeX + padding;
            int screenTextColor = screenTextColor();
            for (int i = 0, size = list.size(); i < size; i++) {
                ITextComponent text = list.get(i);
                int maxX = relativeX + getMaxTextWidth(i) - padding;
                drawScaledScrollingString(text, minX, minY, maxX, maxY, TextAlignment.LEFT, screenTextColor, false, textScale, getTimeOpened());
                minY += heightToNextLine;
                maxY += heightToNextLine;
            }
        }
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        if (tooltipStrings != null) {
            List<ITextComponent> list = tooltipStrings.get();
            List<String> list1 = new ArrayList<>();
            list.forEach(s -> list1.add(s.getFormattedText()));
            if (list1 != null && !list1.isEmpty()) {
                displayTooltips(list1, mouseX, mouseY);
            }
        }
    }

    protected int getMaxTextWidth(int row) {
        return getWidth();
    }

}
