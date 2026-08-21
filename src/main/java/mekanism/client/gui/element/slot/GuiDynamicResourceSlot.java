package mekanism.client.gui.element.slot;

import mekanism.api.EnumColor;
import mekanism.api.annotations.NonNullSupplier;
import mekanism.api.gas.GasStack;
import mekanism.api.processing.MachineResourceStack;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.jei.interfaces.IJEIIngredientHelper;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.MekanismLang;
import mekanism.common.util.UnitDisplayUtils;
import mekanism.common.util.text.TextUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;

/**
 * A single framed, horizontally scrolling display for typed machine
 * resources. The element owns the complete grid so list updates do not create
 * or discard child GUI elements every tick.
 */
@SideOnly(Side.CLIENT)
public class GuiDynamicResourceSlot extends GuiElement implements IJEIIngredientHelper {

    public static final int RESOURCE_SIZE = GuiDynamicResourceSlotLayout.RESOURCE_SIZE;
    public static final int DEFAULT_RESOURCE_GAP = GuiDynamicResourceSlotLayout.DEFAULT_RESOURCE_GAP;
    public static final int DEFAULT_RESOURCE_STEP = GuiDynamicResourceSlotLayout.DEFAULT_RESOURCE_STEP;
    /** @deprecated Use {@link #DEFAULT_RESOURCE_STEP}. */
    @Deprecated
    public static final int RESOURCE_STEP = DEFAULT_RESOURCE_STEP;
    public static final int DEFAULT_BORDER_COLOR = 0xFF6A6A6A;
    public static final int SLOT_BACKGROUND_COLOR = 0xFF8B8B8B;

    /** Border colors matching the bright and shaded edges of {@link SlotType}. */
    public enum BorderColor {
        NORMAL(0xFFFFFFFF, 0xFF373737),
        DIGITAL(0xFFFFFFFF, 0xFFFFFFFF),
        POWER(0xFF3BFB98, 0xFF3BFB98),
        EXTRA(0xFFCEA54E, 0xFF625430),
        INPUT(0xFF9C4043, 0xFF603031),
        INPUT_2(0xFFBA7519, 0xFF50320D),
        OUTPUT(0xFF4141A0, 0xFF292947),
        OUTPUT_2(0xFF28939D, 0xFF16484D),
        OUTPUT_WIDE(0xFF4141A0, 0xFF292947),
        OUTPUT_LARGE(0xFF0225C8, 0xFF00084C),
        ORE(0xFFFFFFFF, 0xFF2A2A2A),
        INNER_HOLDER_SLOT(0xFF787878, 0xFF787878);

        private final int highlightColor;
        private final int shadowColor;

        BorderColor(int highlightColor, int shadowColor) {
            this.highlightColor = highlightColor;
            this.shadowColor = shadowColor;
        }

        /** Returns the bright edge color for compatibility with the old API. */
        public int getColor() {
            return highlightColor;
        }

        public int getHighlightColor() {
            return highlightColor;
        }

        public int getShadowColor() {
            return shadowColor;
        }
    }

    private final int configuredVisibleColumns;
    private final int visibleRows;
    private final boolean explicitFrameWidth;
    private final NonNullSupplier<List<MachineResourceStack>> resourceSupplier;

    private int resourceGap = DEFAULT_RESOURCE_GAP;
    private boolean autoFit;
    private IntSupplier borderColorSupplier = () -> DEFAULT_BORDER_COLOR;
    private IntSupplier borderShadowColorSupplier = () -> DEFAULT_BORDER_COLOR;
    @Nullable
    private IClickable clickHandler;

    private boolean scrollClockInitialized;
    private long scrollStartMillis;
    private int lastContentColumns = -1;
    private int lastFrameWidth = -1;
    private int lastResourceGap = -1;
    private boolean lastAutoFit;
    @Nullable
    private ResourceView renderedView;

    public GuiDynamicResourceSlot(IGuiWrapper gui, int x, int y, int visibleColumns, int visibleRows,
          @Nonnull NonNullSupplier<List<MachineResourceStack>> resourceSupplier) {
        this(gui, x, y, visibleColumns, visibleRows, resourceSupplier, false,
              GuiDynamicResourceSlotLayout.viewportDimension(visibleColumns));
    }

    private GuiDynamicResourceSlot(IGuiWrapper gui, int x, int y, int visibleColumns, int visibleRows,
          @Nonnull NonNullSupplier<List<MachineResourceStack>> resourceSupplier, boolean explicitFrameWidth,
          int frameWidth) {
        super(gui, x, y, explicitFrameWidth ? GuiDynamicResourceSlotLayout.validateFrameWidth(frameWidth) :
                    GuiDynamicResourceSlotLayout.viewportDimension(visibleColumns),
              GuiDynamicResourceSlotLayout.viewportDimension(visibleRows));
        this.configuredVisibleColumns = GuiDynamicResourceSlotLayout.normalizeVisibleCount(visibleColumns);
        this.visibleRows = GuiDynamicResourceSlotLayout.normalizeVisibleCount(visibleRows);
        this.explicitFrameWidth = explicitFrameWidth;
        this.resourceSupplier = Objects.requireNonNull(resourceSupplier, "Resource supplier cannot be null");
    }

    public GuiDynamicResourceSlot(IGuiWrapper gui, int x, int y, int visibleColumns, int visibleRows,
          @Nonnull NonNullSupplier<List<MachineResourceStack>> resourceSupplier,
          @Nonnull IntSupplier borderColorSupplier) {
        this(gui, x, y, visibleColumns, visibleRows, resourceSupplier);
        borderColor(borderColorSupplier);
    }

    /**
     * Creates a resource slot with an exact outer width. Spacing changes never
     * resize this frame horizontally.
     */
    public static GuiDynamicResourceSlot withFrameWidth(IGuiWrapper gui, int x, int y, int frameWidth,
          int visibleRows, @Nonnull NonNullSupplier<List<MachineResourceStack>> resourceSupplier) {
        return new GuiDynamicResourceSlot(gui, x, y, 1, visibleRows, resourceSupplier, true, frameWidth);
    }

    public int getVisibleColumns() {
        return GuiDynamicResourceSlotLayout.effectiveVisibleColumns(width, resourceGap);
    }

    public int getVisibleRows() {
        return visibleRows;
    }

    public int getResourceGap() {
        return resourceGap;
    }

    public boolean isAutoFitSpacing() {
        return autoFit;
    }

    /** Uses a fixed number of pixels between all resources. */
    public GuiDynamicResourceSlot resourceGap(int pixels) {
        return configureSpacing(pixels, false);
    }

    /** Distributes spare horizontal pixels while preserving the requested minimum gap. */
    public GuiDynamicResourceSlot autoFitSpacing(int minimumGap) {
        return configureSpacing(minimumGap, true);
    }

    private GuiDynamicResourceSlot configureSpacing(int gap, boolean useAutoFit) {
        int checkedGap = GuiDynamicResourceSlotLayout.validateGap(gap);
        int newHeight = GuiDynamicResourceSlotLayout.frameDimension(visibleRows, checkedGap);
        int newWidth = explicitFrameWidth ? width :
              GuiDynamicResourceSlotLayout.frameDimension(configuredVisibleColumns, checkedGap);
        resourceGap = checkedGap;
        autoFit = useAutoFit;
        if (!explicitFrameWidth) {
            super.setWidth(newWidth);
        }
        setHeight(newHeight);
        renderedView = null;
        return this;
    }

    /**
     * @deprecated Use {@link #withFrameWidth(IGuiWrapper, int, int, int, int, NonNullSupplier)}
     * so later spacing changes cannot replace the requested width.
     */
    @Deprecated
    @Override
    public void setWidth(int frameWidth) {
        super.setWidth(GuiDynamicResourceSlotLayout.validateFrameWidth(frameWidth));
        renderedView = null;
    }

    /** Sets the dynamically evaluated ARGB color used only for the outer frame. */
    public GuiDynamicResourceSlot borderColor(@Nonnull IntSupplier borderColorSupplier) {
        this.borderColorSupplier = Objects.requireNonNull(borderColorSupplier, "Border color supplier cannot be null");
        this.borderShadowColorSupplier = this.borderColorSupplier;
        return this;
    }

    /** Uses the bright and shaded edge colors of a standard GUI slot variant. */
    public GuiDynamicResourceSlot borderColor(@Nonnull BorderColor borderColor) {
        BorderColor checkedColor = Objects.requireNonNull(borderColor, "Border color cannot be null");
        this.borderColorSupplier = checkedColor::getHighlightColor;
        this.borderShadowColorSupplier = checkedColor::getShadowColor;
        return this;
    }

    /**
     * Installs an optional left-click callback for the whole frame. Returning
     * {@code false} leaves the click unconsumed.
     */
    public GuiDynamicResourceSlot click(@Nullable IClickable clickHandler) {
        this.clickHandler = clickHandler;
        playClickSound = clickHandler != null;
        return this;
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        GuiUtils.fill(relativeX + 1, relativeY + 1, relativeX + width - 1,
              relativeY + height - 1, SLOT_BACKGROUND_COLOR);
        MekanismRenderer.resetColor();
        drawBorder();
        MekanismRenderer.resetColor();

        // Keep one stable snapshot for the resource drawing and all hover,
        // tooltip, and JEI queries that follow during this frame.
        ResourceView view = getResourceView();
        renderedView = view;
        if (view.resources.isEmpty()) {
            return;
        }
        ScissorState scissor = beginScissor();
        try {
            float minX = relativeX + 1;
            float maxX = relativeX + width - 1;
            for (int index = 0; index < view.resources.size(); index++) {
                float resourceX = resourceX(view, index);
                if (resourceX + RESOURCE_SIZE <= minX || resourceX >= maxX) {
                    continue;
                }
                drawResource(view.resources.get(index), resourceX, resourceY(view, index));
            }
        } finally {
            scissor.restore();
        }
    }

    /** Draws the GuiSlot-style frame: shaded top/left edges and bright bottom/right edges. */
    private void drawBorder() {
        int highlight = borderColorSupplier.getAsInt();
        int shadow = borderShadowColorSupplier.getAsInt();
        GuiUtils.fill(relativeX, relativeY, relativeX + width, relativeY + 1, shadow);
        GuiUtils.fill(relativeX, relativeY + 1, relativeX + 1, relativeY + height - 1, shadow);
        GuiUtils.fill(relativeX, relativeY + height - 1, relativeX + width, relativeY + height, highlight);
        if (height > 2) {
            GuiUtils.fill(relativeX + width - 1, relativeY + 1, relativeX + width,
                  relativeY + height - 1, highlight);
        }
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        HoveredResource hovered = getHoveredResource(mouseX, mouseY);
        if (hovered == null) {
            return;
        }
        ScissorState scissor = beginScissor();
        try {
            float x = resourceX(hovered.view, hovered.index);
            float y = resourceY(hovered.view, hovered.index);
            GlStateManager.pushMatrix();
            try {
                GlStateManager.translate(x, y, 150);
                GuiUtils.fill(0, 0, RESOURCE_SIZE, RESOURCE_SIZE, GuiSlot.DEFAULT_HOVER_COLOR);
            } finally {
                GlStateManager.popMatrix();
                MekanismRenderer.resetColor();
            }
        } finally {
            scissor.restore();
        }
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        HoveredResource hovered = getHoveredResource(mouseX, mouseY);
        if (hovered == null) {
            return;
        }
        MachineResourceStack resource = hovered.resource;
        switch (resource.kind()) {
            case ITEM:
                ItemStack item = displayResource(resource).itemStack();
                if (item.isEmpty()) {
                    return;
                }
                if (resource.amount() > 1) {
                    gui().renderItemTooltipWithExtra(item, mouseX, mouseY,
                          Collections.singletonList(getAmountTooltip(resource.amount())));
                } else {
                    gui().renderItemTooltip(item, mouseX, mouseY);
                }
                break;
            case FLUID:
            case GAS:
                List<String> tooltip = new ArrayList<>(2);
                tooltip.add(resourceName(resource));
                tooltip.add(getAmountTooltip(resource.amount()));
                displayTooltips(tooltip, mouseX, mouseY);
                break;
        }
    }

    @Override
    public boolean isMouseOverTooltip(double mouseX, double mouseY) {
        return visible && getHoveredResource(mouseX, mouseY) != null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (clickHandler != null && button == 0 && visible && active &&
              mouseX >= getX() && mouseY >= getY() && mouseX < getRight() && mouseY < getBottom() &&
              checkWindows(mouseX, mouseY)) {
            if (clickHandler.onClick(this, mouseX, mouseY)) {
                playClickSound();
                return true;
            }
        }
        // The element has no child controls and must remain non-consuming when
        // no callback (or a callback returning false) is installed.
        return false;
    }

    @Nullable
    @Override
    public Object getIngredient(double mouseX, double mouseY) {
        HoveredResource hovered = getHoveredResource(mouseX, mouseY);
        if (hovered == null) {
            return null;
        }
        MachineResourceStack resource = hovered.resource.withAmount(1);
        switch (resource.kind()) {
            case ITEM:
                return resource.itemStack();
            case FLUID:
                return resource.fluidStack();
            case GAS:
                return resource.gasStack();
            default:
                return null;
        }
    }

    @Nonnull
    private List<MachineResourceStack> getResources() {
        List<MachineResourceStack> supplied = resourceSupplier.get();
        if (supplied == null || supplied.isEmpty()) {
            return Collections.emptyList();
        }
        // Copy the list, but do not retain its identity as scroll state. A
        // supplier is allowed to return a new list or rotate payloads every tick.
        List<MachineResourceStack> resources = new ArrayList<>(supplied.size());
        for (MachineResourceStack resource : supplied) {
            if (resource != null) {
                resources.add(resource);
            }
        }
        return resources.isEmpty() ? Collections.emptyList() : resources;
    }

    private ResourceView getResourceView() {
        List<MachineResourceStack> resources = getResources();
        int contentColumns = GuiDynamicResourceSlotLayout.contentColumns(resources.size(), visibleRows);
        int frameWidth = width;
        int gap = resourceGap;
        boolean useAutoFit = autoFit;
        long now = GuiElement.getMillis();
        if (!scrollClockInitialized || contentColumns != lastContentColumns || frameWidth != lastFrameWidth ||
              gap != lastResourceGap || useAutoFit != lastAutoFit) {
            scrollClockInitialized = true;
            scrollStartMillis = now;
            lastContentColumns = contentColumns;
            lastFrameWidth = frameWidth;
            lastResourceGap = gap;
            lastAutoFit = useAutoFit;
        }
        long elapsed = now >= scrollStartMillis ? now - scrollStartMillis : 0;
        double contentWidth = GuiDynamicResourceSlotLayout.contentWidthForColumns(contentColumns, frameWidth,
              gap, useAutoFit);
        double offset = GuiDynamicResourceSlotLayout.scrollingOffset(contentWidth,
              GuiDynamicResourceSlotLayout.innerWidth(frameWidth), elapsed);
        return new ResourceView(resources, frameWidth, gap, useAutoFit, offset);
    }

    @Nullable
    private HoveredResource getHoveredResource(double mouseX, double mouseY) {
        if (!checkWindows(mouseX, mouseY)) {
            return null;
        }
        ResourceView view = getInteractionView();
        int index = GuiDynamicResourceSlotLayout.hitIndex(view.resources.size(), view.frameWidth, visibleRows,
              view.resourceGap, view.autoFit, mouseX - getX(), mouseY - getY(), view.offset);
        return index < 0 ? null : new HoveredResource(view, index, view.resources.get(index));
    }

    private ResourceView getInteractionView() {
        if (renderedView == null) {
            renderedView = getResourceView();
        }
        return renderedView;
    }

    private float resourceX(ResourceView view, int index) {
        int column = GuiDynamicResourceSlotLayout.columnForIndex(index, visibleRows);
        return relativeX + 1 + GuiDynamicResourceSlotLayout.resourceStart(column, view.frameWidth,
              view.resourceGap, view.autoFit) - (float) view.offset;
    }

    private float resourceY(ResourceView view, int index) {
        return relativeY + 1 +
              GuiDynamicResourceSlotLayout.rowForIndex(index, visibleRows) *
                    (float) (RESOURCE_SIZE + view.resourceGap);
    }

    private void drawResource(MachineResourceStack resource, float x, float y) {
        MachineResourceStack display = displayResource(resource);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, 0);
            MekanismRenderer.resetColor();
            switch (display.kind()) {
                case ITEM:
                    ItemStack item = display.itemStack();
                    if (!item.isEmpty()) {
                        gui().renderItemWithOverlay(item, 0, 0, 1F, "");
                    }
                    break;
                case FLUID:
                    FluidStack fluid = display.fluidStack();
                    if (fluid != null) {
                        GuiUtils.drawFluidBarSprite(-1, -1, 18, 18, 16, fluid, true);
                        drawKindMarker("F");
                    }
                    break;
                case GAS:
                    GasStack gas = display.gasStack();
                    if (gas != null) {
                        GuiUtils.drawGasBarSprite(-1, -1, 18, 18, 16, gas, true);
                        drawKindMarker("G");
                    }
                    break;
            }
            String count = getCountText(resource.amount());
            if (count != null) {
                renderAmount(count);
            }
        } finally {
            MekanismRenderer.resetColor();
            GlStateManager.popMatrix();
        }
    }

    private void drawKindMarker(String marker) {
        FontRenderer font = getFont();
        if (font == null) {
            return;
        }
        GlStateManager.pushMatrix();
        try {
            MekanismRenderer.resetColor();
            GlStateManager.translate(5, 4, 100);
            GlStateManager.scale(0.75F, 0.75F, 0.75F);
            font.drawString(marker, 0, 0, 0xFFFFFFFF);
        } finally {
            GlStateManager.popMatrix();
            MekanismRenderer.resetColor();
        }
    }

    private void renderAmount(String text) {
        FontRenderer font = getFont();
        if (font == null) {
            return;
        }
        float scale = 0.6F;
        int textWidth = font.getStringWidth(text);
        if (textWidth > 0) {
            scale = Math.min(1F, RESOURCE_SIZE / (textWidth * scale)) * scale;
        }
        float yAdd = 4 - scale * 4;
        GlStateManager.pushMatrix();
        try {
            MekanismRenderer.resetColor();
            GlStateManager.translate(RESOURCE_SIZE - textWidth * scale, 9 + yAdd, 200);
            GlStateManager.scale(scale, scale, scale);
            font.drawString(text, 0, 0, 0xFFFFFFFF);
        } finally {
            GlStateManager.popMatrix();
            MekanismRenderer.resetColor();
        }
    }

    @Nonnull
    private static MachineResourceStack displayResource(@Nonnull MachineResourceStack resource) {
        return resource.amount() > Integer.MAX_VALUE ? resource.withAmount(1) : resource;
    }

    @Nonnull
    private static String getAmountTooltip(long amount) {
        return MekanismLang.QIO_STORED_COUNT.translateColored(EnumColor.GREY, EnumColor.INDIGO,
              TextUtils.format(amount)).getFormattedText();
    }

    @Nonnull
    private static String resourceName(@Nonnull MachineResourceStack resource) {
        MachineResourceStack display = displayResource(resource);
        switch (display.kind()) {
            case ITEM:
                ItemStack item = display.itemStack();
                return item.isEmpty() ? "item" : item.getDisplayName();
            case FLUID:
                FluidStack fluid = display.fluidStack();
                return fluid == null ? "fluid" : fluid.getLocalizedName();
            case GAS:
                GasStack gas = display.gasStack();
                return gas == null || gas.getGas() == null ? "gas" :
                      gas.getGas().getLocalizedName();
            default:
                return "";
        }
    }

    @Nullable
    private static String getCountText(long amount) {
        if (amount <= 1) {
            return null;
        }
        return amount < 10_000 ? Long.toString(amount) : UnitDisplayUtils.getDisplay(amount, 1);
    }

    private ScissorState beginScissor() {
        boolean wasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int previousX = 0;
        int previousY = 0;
        int previousWidth = 0;
        int previousHeight = 0;
        if (wasEnabled) {
            IntBuffer box = BufferUtils.createIntBuffer(4);
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, box);
            previousX = box.get(0);
            previousY = box.get(1);
            previousWidth = box.get(2);
            previousHeight = box.get(3);
        }

        Minecraft client = minecraft;
        if (client.currentScreen == null || client.currentScreen.width <= 0 || client.currentScreen.height <= 0) {
            return new ScissorState(wasEnabled, previousX, previousY, previousWidth, previousHeight, false);
        }
        double scaleX = client.displayWidth / (double) client.currentScreen.width;
        double scaleY = client.displayHeight / (double) client.currentScreen.height;
        int minX = (int) Math.floor((getGuiLeft() + relativeX + 1) * scaleX);
        int minY = (int) Math.floor(client.displayHeight -
              (getGuiTop() + relativeY + height - 1) * scaleY);
        int scissorWidth = Math.max(0, (int) Math.ceil((width - 2) * scaleX));
        int scissorHeight = Math.max(0, (int) Math.ceil((height - 2) * scaleY));
        if (wasEnabled) {
            int right = Math.min(minX + scissorWidth, previousX + previousWidth);
            int top = Math.min(minY + scissorHeight, previousY + previousHeight);
            minX = Math.max(minX, previousX);
            minY = Math.max(minY, previousY);
            scissorWidth = Math.max(0, right - minX);
            scissorHeight = Math.max(0, top - minY);
        }
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(minX, minY, scissorWidth, scissorHeight);
        return new ScissorState(wasEnabled, previousX, previousY, previousWidth, previousHeight, true);
    }

    private static final class ResourceView {

        private final List<MachineResourceStack> resources;
        private final int frameWidth;
        private final int resourceGap;
        private final boolean autoFit;
        private final double offset;

        private ResourceView(List<MachineResourceStack> resources, int frameWidth, int resourceGap,
              boolean autoFit, double offset) {
            this.resources = resources;
            this.frameWidth = frameWidth;
            this.resourceGap = resourceGap;
            this.autoFit = autoFit;
            this.offset = offset;
        }
    }

    private static final class HoveredResource {

        private final ResourceView view;
        private final int index;
        private final MachineResourceStack resource;

        private HoveredResource(ResourceView view, int index, MachineResourceStack resource) {
            this.view = view;
            this.index = index;
            this.resource = resource;
        }
    }

    private static final class ScissorState {

        private final boolean wasEnabled;
        private final int previousX;
        private final int previousY;
        private final int previousWidth;
        private final int previousHeight;
        private final boolean applied;

        private ScissorState(boolean wasEnabled, int previousX, int previousY, int previousWidth,
              int previousHeight, boolean applied) {
            this.wasEnabled = wasEnabled;
            this.previousX = previousX;
            this.previousY = previousY;
            this.previousWidth = previousWidth;
            this.previousHeight = previousHeight;
            this.applied = applied;
        }

        private void restore() {
            if (!applied) {
                return;
            }
            if (wasEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(previousX, previousY, previousWidth, previousHeight);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        }
    }
}
