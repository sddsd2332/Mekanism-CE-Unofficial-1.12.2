package mekanism.client.jei;

import mekanism.api.gas.GasStack;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.client.gui.element.gauge.GaugeOverlay;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiFluidGauge.GaugeColor;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.IProgressInfoHandler;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.client.jei.gas.GasStackRenderer;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.common.Mekanism;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.util.LangUtils;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.*;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.*;

public abstract class BaseRecipeCategory<WRAPPER extends IRecipeWrapper> implements IRecipeCategory<WRAPPER>, mekanism.client.gui.IGuiWrapper {

    private static final GuiDummy gui = new GuiDummy();
    protected static final IBarInfoHandler FULL_BAR = () -> 1;
    protected static final String DUMMY_GUI_TEXTURE = "mekanism:gui/null.png";

    private IGuiHelper guiHelper;
    protected ITickTimer timer;
    @Nullable
    protected ProgressType progressType;
    protected int xOffset;
    protected int yOffset;
    protected IDrawable fluidOverlayLarge;
    protected IDrawable fluidOverlayheight;
    protected IDrawable fluidOverlaySmall;
    protected List<GuiElement> guiElements = new ArrayList<>();
    @Nullable
    private Map<GaugeOverlay, IDrawable> overlayLookup;
    private String recipeName;
    private ITextComponent component;
    @Nullable
    private final IDrawable icon;

    private final IDrawable background;

    protected static IDrawable createIcon(IGuiHelper helper, ResourceLocation iconRL) {
        return helper.drawableBuilder(iconRL, 0, 0, 18, 18).setTextureSize(18, 18).build();
    }

    protected static IDrawable createIcon(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        ItemStack stack = recipeType.iconStack();
        if (stack.isEmpty()) {
            ResourceLocation icon = recipeType.icon();
            if (icon == null) {
                throw new IllegalStateException("Expected recipe type to have either an icon stack or an icon location");
            }
            return createIcon(helper, icon);
        }
        return createIcon(helper, stack);
    }

    protected BaseRecipeCategory(IGuiHelper helper, String guiTexture, String name, String unlocalized, int xOffset, int yOffset, int width, int height) {
        this(helper, guiTexture, name, unlocalized, xOffset, yOffset, width, height, (ProgressType) null);
    }

    protected BaseRecipeCategory(IGuiHelper helper, String guiTexture, String name, String unlocalized, int xOffset, int yOffset, int width, int height,
                                 @Nullable ProgressType progress) {
        this(helper, name, unlocalized, (IDrawable) null, xOffset, yOffset, width, height, progress);
    }

    protected BaseRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType, ProgressType progress) {
        this(helper, RecipeViewerRecipeType.categoryUid(recipeType), recipeType.getTextComponent(), createIcon(helper, recipeType), -recipeType.xOffset(),
                -recipeType.yOffset(), recipeType.width(), recipeType.height(), progress);
    }

    protected BaseRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        this(helper, recipeType, (ProgressType) null);
    }

    protected BaseRecipeCategory(IGuiHelper helper, String name, String unlocalized, @Nullable IDrawable icon, int xOffset, int yOffset, int width, int height,
                                 @Nullable ProgressType progress) {
        this(helper, name, new TextComponentTranslation(unlocalized), icon, xOffset, yOffset, width, height, progress);
    }

    protected BaseRecipeCategory(IGuiHelper helper, String name, ITextComponent component, @Nullable IDrawable icon, int xOffset, int yOffset, int width, int height,
                                 @Nullable ProgressType progress) {
        guiHelper = helper;

        progressType = progress;
        this.xOffset = xOffset;
        this.yOffset = yOffset;

        recipeName = name;
        this.component = component;
        this.icon = icon;

        timer = helper.createTickTimer(20, 20, false);

        fluidOverlayLarge = createGaugeOverlay(GaugeOverlay.STANDARD);
        fluidOverlaySmall = createGaugeOverlay(GaugeOverlay.SMALL);
        fluidOverlayheight = createGaugeOverlay(GaugeOverlay.WIDE);

        addGuiElements();

        background = guiHelper.createBlankDrawable(width, height);
    }

    private static IDrawable createIcon(IGuiHelper helper, ItemStack stack) {
        return helper.createDrawableIngredient(stack);
    }

    @Override
    public String getUid() {
        return recipeName;
    }

    @Override
    public String getTitle() {
        return component.getFormattedText();
    }

    @Override
    public String getModName() {
        return Mekanism.MOD_NAME;
    }

    @Override
    public void drawExtras(Minecraft minecraft) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(getLeft(), getTop(), 0);
        renderElements(-xOffset, -yOffset);
        GlStateManager.popMatrix();
    }

    protected void renderElements(int mouseX, int mouseY) {
        for (GuiElement element : guiElements) {
            element.renderShifted(mouseX, mouseY, 0);
        }
        for (GuiElement element : guiElements) {
            element.onDrawBackground(mouseX, mouseY, 0);
        }
        int zOffset = 0;
        for (GuiElement element : guiElements) {
            GlStateManager.pushMatrix();
            element.onRenderForeground(mouseX, mouseY, zOffset, zOffset);
            GlStateManager.popMatrix();
        }
    }

    public void drawTexturedRect(int x, int y, int u, int v, int w, int h) {
        gui.drawTexturedModalRect(x, y, u, v, w, h);
    }

    public void drawTexturedRectFromIcon(int x, int y, TextureAtlasSprite icon, int w, int h) {
        gui.drawTexturedModalRect(x, y, icon, w, h);
    }

    @Override
    public FontRenderer getFont() {
        return Minecraft.getMinecraft().fontRenderer;
    }

    @Override
    public RenderItem getItemRenderer() {
        return Minecraft.getMinecraft().getRenderItem();
    }

    @Override
    public int getLeft() {
        return -xOffset;
    }

    @Override
    public int getTop() {
        return -yOffset;
    }

    /**
     * JEI recipe categories do not host movable Mekanism windows. GuiElement
     * still performs the normal window-occlusion check while rendering slot
     * hover states, so provide the empty result explicitly instead of falling
     * through to IGuiWrapper's diagnostic default implementation every frame.
     */
    @Override
    @Nullable
    public GuiWindow getWindowHovering(double mouseX, double mouseY) {
        return null;
    }

    @Override
    public int getWidth() {
        return background.getWidth();
    }

    @Override
    public int getHeight() {
        return background.getHeight();
    }

    protected void addGuiElements() {
    }

    protected <ELEMENT extends GuiElement> ELEMENT addElement(ELEMENT element) {
        guiElements.add(element);
        return element;
    }

    /**
     * @apiNote x and y are based on the values set in the tile, as the GUI then shifts the slots by one to account for the border.
     */
    protected GuiSlot addSlot(SlotType type, int x, int y) {
        return addElement(new GuiSlot(type, this, x - 1, y - 1).setRenderAboveSlots());
    }

    protected GuiProgress addSimpleProgress(ProgressType type, int x, int y) {
        return addElement(new GuiProgress(getSimpleProgressTimer(), type, this, x, y));
    }

    protected GuiProgress addConstantProgress(ProgressType type, int x, int y) {
        return addElement(new GuiProgress(CONSTANT_PROGRESS(), type, this, x, y));
    }

    protected IProgressInfoHandler CONSTANT_PROGRESS(){
        return new IProgressInfoHandler() {

            @Override
            public double getProgress() {
                return 1;
            }

            @Override
            public boolean isGuiInJei() {
                return true;
            }
        };
    }

    protected IProgressInfoHandler getSimpleProgressTimer() {
        return new IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return timer.getValue() / 20D;
            }

            @Override
            public boolean isGuiInJei() {
                return true;
            }
        };
    }



    protected IBarInfoHandler getBarProgressTimer() {
        return new IBarInfoHandler() {
            @Override
            public net.minecraft.util.text.ITextComponent getTooltip() {
                return new net.minecraft.util.text.TextComponentString(LangUtils.localize("gui.progress") + ": " + Math.round(getLevel() * 100) + "%");
            }

            @Override
            public double getLevel() {
                return timer.getValue() / 20D;
            }
        };
    }

    private IDrawable createGaugeOverlay(GaugeOverlay overlay) {
        return guiHelper.drawableBuilder(overlay.getResource(), 0, 0, overlay.getWidth(), overlay.getHeight())
                .setTextureSize(overlay.getWidth(), overlay.getHeight())
                .build();
    }

    protected GuiGasGauge dummyGasGauge(GuiGasGauge.Type type, GuiGasGauge.GaugeColor color, int x, int y) {
        return new GuiGasGauge(this, BasicGasTank.create(1, null), type, x, y).withColor(color);
    }

    protected GuiFluidGauge dummyFluidGauge(GuiFluidGauge.Type type, GaugeColor color, int x, int y) {
        return new GuiFluidGauge(this, BasicFluidTank.create(1, null), type, x, y).withColor(color);
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public List<String> getTooltipStrings(int mouseX, int mouseY) {
        return Collections.emptyList();
    }

    protected void initGas(IGuiIngredientGroup<GasStack> group, int slot, boolean input, int x, int y, int width, int height, @Nullable GasStack stack, boolean overlay) {
        if (stack == null) {
            return;
        }

        IDrawable fluidOverlay = height > 50 ? fluidOverlayLarge : fluidOverlaySmall;
        IDrawable fluidheight = width > 20 ? fluidOverlayheight : fluidOverlay;
        GasStackRenderer renderer = new GasStackRenderer(stack.amount, false, width, height, overlay ? fluidheight : null);
        group.init(slot, input, renderer, x, y, width, height, 0, 0);
        group.set(slot, stack);
    }

    protected void initItem(IGuiItemStackGroup group, int slot, boolean input, GuiSlot guiSlot, List<ItemStack> stacks) {
        initItem(group, slot, input, guiSlot.getRelativeX(), guiSlot.getRelativeY(), stacks);
    }

    protected void initItem(IGuiItemStackGroup group, int slot, boolean input, GuiSlot guiSlot) {
        group.init(slot, input, guiSlot.getRelativeX() - xOffset, guiSlot.getRelativeY() - yOffset);
    }

    protected void initItem(IGuiItemStackGroup group, int slot, boolean input, int relativeX, int relativeY, List<ItemStack> stacks) {
        group.init(slot, input, relativeX - xOffset, relativeY - yOffset);
        group.set(slot, stacks);
    }

    protected void initItem(IGuiItemStackGroup group, int slot, boolean input, GuiSlot guiSlot, ItemStack stack) {
        initItem(group, slot, input, guiSlot.getRelativeX(), guiSlot.getRelativeY(), stack);
    }

    protected void initItem(IGuiItemStackGroup group, int slot, boolean input, int relativeX, int relativeY, ItemStack stack) {
        group.init(slot, input, relativeX - xOffset, relativeY - yOffset);
        group.set(slot, stack);
    }

    protected void initGas(IGuiIngredientGroup<GasStack> group, int slot, boolean input, GuiElement element, @Nullable GasStack stack) {
        initGas(group, slot, input, element, stack == null ? Collections.emptyList() : Collections.singletonList(stack), true);
    }

    protected void initGas(IGuiIngredientGroup<GasStack> group, int slot, boolean input, GuiElement element, @Nullable GasStack stack, boolean overlay) {
        initGas(group, slot, input, element, stack == null ? Collections.emptyList() : Collections.singletonList(stack), overlay);
    }

    protected void initGas(IGuiIngredientGroup<GasStack> group, int slot, boolean input, GuiElement element, List<GasStack> stacks, boolean overlay) {
        if (stacks.isEmpty()) {
            return;
        }
        int width = element.getWidth() - 2;
        int height = element.getHeight() - 2;
        int max = stacks.stream().mapToInt(stack -> stack.amount).filter(amount -> amount > 0).max().orElse(1000);
        IDrawable drawableOverlay = overlay && element instanceof GuiGauge<?> gauge ? getOverlay(gauge) : null;
        GasStackRenderer renderer = new GasStackRenderer(max, false, width, height, drawableOverlay);
        group.init(slot, input, renderer, element.getRelativeX() + 1 - xOffset, element.getRelativeY() + 1 - yOffset, width, height, 0, 0);
        group.set(slot, stacks);
    }

    protected void initFluid(IGuiFluidStackGroup group, int slot, boolean input, GuiElement element, @Nullable FluidStack stack) {
        initFluid(group, slot, input, element, stack == null ? Collections.emptyList() : Collections.singletonList(stack), true);
    }

    protected void initFluid(IGuiFluidStackGroup group, int slot, boolean input, GuiElement element, List<FluidStack> stacks, boolean overlay) {
        if (stacks.isEmpty()) {
            return;
        }
        int width = element.getWidth() - 2;
        int height = element.getHeight() - 2;
        int capacity = stacks.stream().filter(stack -> stack != null && stack.amount > 0).mapToInt(stack -> stack.amount).max().orElse(Fluid.BUCKET_VOLUME);
        IDrawable drawableOverlay = overlay && element instanceof GuiGauge<?> gauge ? getOverlay(gauge) : null;
        group.init(slot, input, element.getRelativeX() + 1 - xOffset, element.getRelativeY() + 1 - yOffset, width, height, capacity, false, drawableOverlay);
        if (stacks.size() == 1) {
            group.set(slot, stacks.get(0));
        } else {
            group.set(slot, stacks);
        }
    }

    private IDrawable getOverlay(GuiGauge<?> gauge) {
        if (overlayLookup == null) {
            overlayLookup = new EnumMap<>(GaugeOverlay.class);
        }
        return overlayLookup.computeIfAbsent(gauge.getGaugeOverlay(), this::createGaugeOverlay);
    }


    public static class GuiDummy extends Gui {
    }

}
