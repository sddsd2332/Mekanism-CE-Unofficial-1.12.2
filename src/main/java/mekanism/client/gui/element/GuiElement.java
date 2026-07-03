package mekanism.client.gui.element;

import mekanism.api.text.ILangEntry;
import mekanism.api.text.TextComponentGroup;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.GuiUtils.TilingDirection;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.client.render.IFancyFontRenderer;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public abstract class GuiElement extends Widget implements IFancyFontRenderer {


    private static final int BUTTON_TEX_X = 200, BUTTON_TEX_Y = 60, BUTTON_INDIVIDUAL_TEX_Y = BUTTON_TEX_Y / 3;
    public static final ResourceLocation WARNING_BACKGROUND_TEXTURE = MekanismUtils.getResource(ResourceType.GUI, "warning_background.png");
    public static final ResourceLocation WARNING_TEXTURE = MekanismUtils.getResource(ResourceType.GUI, "warning.png");

    public static final Minecraft minecraft = Minecraft.getMinecraft();

    protected ButtonBackground buttonBackground = ButtonBackground.NONE;

    protected final List<GuiElement> children = new ArrayList<>();
    /**
     * Children that don't get drawn or checked for beyond transferring data. This is mainly a helper to make it easier to update positioning information of background
     * helpers.
     */
    private final List<GuiElement> positionOnlyChildren = new ArrayList<>();

    private IGuiWrapper guiObj;
    protected boolean playClickSound;
    @Nullable
    protected Supplier<SoundEvent> customClickSound;
    protected float clickSoundVolume = 0.25F;
    protected float clickSoundPitch = 1.0F;
    protected int relativeX;
    protected int relativeY;
    public boolean isOverlay;
    @Nullable
    private GuiElement focusedChild;
    private boolean dragging;

    public GuiElement(IGuiWrapper gui, int x, int y, int width, int height) {
        this(gui, x, y, width, height, new TextComponentGroup());
    }

    public GuiElement(IGuiWrapper gui, int x, int y, int width, int height, ITextComponent text) {
        super(gui.getLeft() + x, gui.getTop() + y, width, height, text);
        this.relativeX = x;
        this.relativeY = y;
        this.guiObj = gui;
    }

    public int getRelativeX() {
        return relativeX;
    }

    public int getRelativeY() {
        return relativeY;
    }

    public int getRelativeRight() {
        return getRelativeX() + getWidth();
    }

    public int getRelativeBottom() {
        return getRelativeY() + getHeight();
    }

    /**
     * Transfers this {@link GuiElement} to a new parent {@link IGuiWrapper}, and moves elements as needed.
     */
    public void transferToNewGui(IGuiWrapper gui) {
        int prevLeft = getGuiLeft();
        int prevTop = getGuiTop();
        //Use a separate method to update the guiObj for the element and all children
        // so that resize only gets called once
        transferToNewGuiInternal(gui);
        resize(prevLeft, prevTop, getGuiLeft(), getGuiTop());
    }


    private void transferToNewGuiInternal(IGuiWrapper gui) {
        guiObj = gui;
        children.forEach(child -> child.transferToNewGuiInternal(gui));
        //Transfer position only children as well
        positionOnlyChildren.forEach(child -> child.transferToNewGuiInternal(gui));
    }

    protected <ELEMENT extends GuiElement> ELEMENT addChild(ELEMENT element) {
        children.add(element);
        if (isOverlay) {
            element.isOverlay = true;
        }
        return element;
    }

    protected <ELEMENT extends GuiElement> ELEMENT addPositionOnlyChild(ELEMENT element) {
        positionOnlyChildren.add(element);
        return element;
    }

    public final IGuiWrapper gui() {
        return guiObj;
    }

    public final int getGuiLeft() {
        return guiObj.getLeft();
    }


    public final int getGuiTop() {
        return guiObj.getTop();
    }

    public final int getGuiWidth() {
        return guiObj.getWidth();
    }

    public final int getGuiHeight() {
        return guiObj.getHeight();
    }

    public List<GuiElement> children() {
        return children;
    }

    public void tick() {
        children.forEach(GuiElement::tick);
    }

    /**
     * @apiNote prevLeft and prevTop may be equal to left and top when things are being reinitialized such as when returning from viewing recipes in JEI.
     */
    public void resize(int prevLeft, int prevTop, int left, int top) {
        x = x - prevLeft + left;
        y = y - prevTop + top;
        children.forEach(child -> child.resize(prevLeft, prevTop, left, top));
        positionOnlyChildren.forEach(child -> child.resize(prevLeft, prevTop, left, top));
    }

    public boolean childrenContainsElement(Predicate<GuiElement> checker) {
        return children.stream().anyMatch(e -> e.containsElement(checker));
    }

    public boolean containsElement(Predicate<GuiElement> checker) {
        return checker.test(this) || childrenContainsElement(checker);
    }

    @Override
    public void setFocused(boolean focused) {
        // change access modifier to public
        super.setFocused(focused);
        if (!focused) {
            setFocusedChild(null);
            setDragging(false);
        }
    }

    @Nullable
    protected GuiElement getFocusedChild() {
        return focusedChild;
    }

    public void setDragging(boolean dragging) {
        this.dragging = dragging;
    }

    public boolean isDragging() {
        return dragging;
    }

    protected void setFocusedChild(@Nullable GuiElement child) {
        if (focusedChild == child) {
            if (child != null) {
                child.setFocused(true);
            }
            return;
        }
        if (focusedChild != null) {
            focusedChild.setFocused(false);
        }
        focusedChild = child;
        if (child != null) {
            child.setFocused(true);
        }
    }

    public void move(int changeX, int changeY) {
        x += changeX;
        y += changeY;
        //Note: When moving we need to adjust our relative position but when resizing, we don't as we are relative to the
        // positions changing when resizing, instead of moving where we are in relation to
        relativeX += changeX;
        relativeY += changeY;
        children.forEach(child -> child.move(changeX, changeY));
        positionOnlyChildren.forEach(child -> child.move(changeX, changeY));
    }

    public void onWindowClose() {
        children.forEach(GuiElement::onWindowClose);
    }

    protected ResourceLocation getButtonLocation(String name) {
        return MekanismUtils.getResource(ResourceType.GUI_BUTTON, name + ".png");
    }

    protected IHoverable getOnHover(ILangEntry translationHelper) {
        return getOnHover((Supplier<ITextComponent>) translationHelper::translate);
    }

    protected IHoverable getOnHover(Supplier<ITextComponent> componentSupplier) {
        return (onHover, xAxis, yAxis) -> displayTooltip(componentSupplier.get(), xAxis, yAxis);
    }

    public boolean hasPersistentData() {
        return children.stream().anyMatch(GuiElement::hasPersistentData);
    }

    public void syncFrom(GuiElement element) {
        int numChildren = children.size();
        if (numChildren > 0) {
            for (int i = 0; i < element.children.size(); i++) {
                GuiElement prevChild = element.children.get(i);
                if (prevChild.hasPersistentData() && i < numChildren) {
                    GuiElement child = children.get(i);
                    // we're forced to assume that the children list is the same before and after the resize.
                    // for verification, we run a lightweight class equality check
                    if (child.getClass() == prevChild.getClass()) {
                        child.syncFrom(prevChild);
                    }
                }
            }
        }
    }

    public final void onRenderForeground(int mouseX, int mouseY, int zOffset, int totalOffset) {
        if (visible) {
            GlStateManager.translate(0, 0, zOffset);
            // update the max total offset to prevent clashing of future overlays
            GuiMekanism.maxZOffset = Math.max(totalOffset, GuiMekanism.maxZOffset);
            // render background overlay and children above everything else
            renderBackgroundOverlay(mouseX, mouseY);
            // render children just above background overlay
            children.forEach(child -> child.renderShifted(mouseX, mouseY, 0));
            children.forEach(child -> child.onDrawBackground(mouseX, mouseY, 0));
            renderForeground(mouseX, mouseY);
            // translate forward to render child foreground
            children.forEach(child -> {
                //Only apply the z shift to each child instead of having future children be translated by more as well
                // Note: Does not apply to compounding with grandchildren as we want those to compound
                GlStateManager.pushMatrix();
                child.onRenderForeground(mouseX, mouseY, 50, totalOffset + 50);
                GlStateManager.popMatrix();
            });
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (visible) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(getGuiLeft(), getGuiTop(), 0);
            renderShifted(mouseX, mouseY, partialTicks);
            GlStateManager.popMatrix();
        }
    }

    public final void renderShifted(int mouseX, int mouseY, float partialTicks) {
        if (visible) {
            isHovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
            if (wasHovered != isHovered()) {
                if (isHovered()) {
                    if (isFocused()) {
                        queueNarration(200);
                    } else {
                        queueNarration(750);
                    }
                } else {
                    nextNarration = Long.MAX_VALUE;
                }
            }
            renderButton(mouseX, mouseY, partialTicks);
            wasHovered = isHovered();
        }
    }

    public void renderForeground(int mouseX, int mouseY) {
        drawButtonText(mouseX, mouseY);
    }

    public void renderBackgroundOverlay(int mouseX, int mouseY) {
    }

    public void openPinnedWindows() {
        children.forEach(GuiElement::openPinnedWindows);
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        children.stream().filter(child -> child.isMouseOverTooltip(mouseX, mouseY))
                .forEach(child -> child.renderToolTip(mouseX, mouseY));
    }

    public boolean isMouseOverTooltip(double mouseX, double mouseY) {
        return isMouseOver(mouseX, mouseY);
    }

    public void displayTooltip(ITextComponent component, int xAxis, int yAxis, int maxWidth) {
        guiObj.displayTooltip(component.getFormattedText(), xAxis, yAxis, maxWidth);
    }

    public void displayTooltip(ITextComponent component, int xAxis, int yAxis) {
        guiObj.displayTooltip(component.getFormattedText(), xAxis, yAxis);
    }

    public void displayTooltips(List<String> components, int xAxis, int yAxis) {
        guiObj.displayTooltips(components, xAxis, yAxis);
    }

    public void displayTooltips(List<String> components, int xAxis, int yAxis, int maxWidth) {
        guiObj.displayTooltips(components, xAxis, yAxis, maxWidth);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = children.size() - 1; i >= 0; i--) {
            GuiElement child = children.get(i);
            if (child.mouseClicked(mouseX, mouseY, button)) {
                setFocusedChild(child);
                return true;
            }
        }
        if (!children.isEmpty()) {
            setFocusedChild(null);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return focusedChild != null && focusedChild.keyPressed(keyCode, scanCode, modifiers) ||
              GuiUtils.checkChildren(children, child -> child != focusedChild && child.keyPressed(keyCode, scanCode, modifiers)) ||
              super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int keyCode) {
        return focusedChild != null && focusedChild.charTyped(c, keyCode) ||
              GuiUtils.checkChildren(children, child -> child != focusedChild && child.charTyped(c, keyCode)) ||
              super.charTyped(c, keyCode);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double mouseXOld, double mouseYOld) {
        boolean handled = focusedChild != null && button == 0 && focusedChild.mouseDragged(mouseX, mouseY, button, mouseXOld, mouseYOld);
        return super.mouseDragged(mouseX, mouseY, button, mouseXOld, mouseYOld) || handled;
    }

    @Override
    public void onDrag(double mouseX, double mouseY, double mouseXOld, double mouseYOld) {
        super.onDrag(mouseX, mouseY, mouseXOld, mouseYOld);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        setDragging(false);
        children.forEach(element -> element.onRelease(mouseX, mouseY));
        super.onRelease(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return focusedChild != null && focusedChild.mouseScrolled(mouseX, mouseY, delta) ||
              GuiUtils.checkChildren(children, child -> child != focusedChild && child.mouseScrolled(mouseX, mouseY, delta)) ||
              super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public FontRenderer getFont() {
        return guiObj.getFont();
    }

    @Override
    public int getXSize() {
        return width;
    }

    public void setButtonBackground(ButtonBackground buttonBackground) {
        this.buttonBackground = buttonBackground;
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        //The code for clicked and isMouseOver is the same. Overriding it here lets us override isMouseOver in subclasses
        // and have it propagate to clicking
        return isMouseOver(mouseX, mouseY);
    }

    /**
     * Override this to render the button with a different x position than this GuiElement
     */
    protected int getButtonX() {
        return relativeX;
    }

    /**
     * Override this to render the button with a different y position than this GuiElement
     */
    protected int getButtonY() {
        return relativeY;
    }

    /**
     * Override this to render the button with a different width than this GuiElement
     */
    protected int getButtonWidth() {
        return width;
    }

    /**
     * Override this to render the button with a different height than this GuiElement
     */
    protected int getButtonHeight() {
        return height;
    }

    /**
     * Override this if you do not want {@link #drawButton(int, int)} to reset the color before drawing.
     */
    protected boolean resetColorBeforeRender() {
        return true;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(mouseX, mouseY) || GuiUtils.checkChildren(children, child -> child.isMouseOver(mouseX, mouseY));
    }

    /**
     * Does the same as {@link #isMouseOver(double, double)}, but validates there is no window in the way
     */
    public final boolean isMouseOverCheckWindows(double mouseX, double mouseY) {
        //TODO: Ideally we would have the various places that call this instead check isHovered if we can properly override setting that
        boolean isHovering = isMouseOver(mouseX, mouseY);
        return checkWindows(mouseX, mouseY, isHovering);
    }

    /**
     * Helper to correct potentially inaccurate hovering or in bounds checks.
     */
    protected final boolean checkWindows(double mouseX, double mouseY) {
        return checkWindows(mouseX, mouseY, true);
    }

    /**
     * Helper to correct potentially inaccurate hovering or in bounds checks.
     */
    protected final boolean checkWindows(double mouseX, double mouseY, boolean isHovering) {
        if (isHovering) {
            //If the mouse is over this element, check if there is a window that would intercept the mouse
            GuiWindow window = guiObj.getWindowHovering(mouseX, mouseY);
            if (window != null && !window.childrenContainsElement(e -> e == this)) {
                //If there is and this element is not part of that window,
                // then mark that our mouse is not over the element
                isHovering = false;
            }
        }
        return isHovering;
    }

    //TODO: Convert this stuff into a javadoc
    //Based off how it is drawn in Widget, except that instead of drawing left half and right half, we draw all four corners individually
    // The benefit of drawing all four corners instead of just left and right halves, is that we ensure we include the bottom black bar of the texture
    // Math has also been added to fix rendering odd size buttons.
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        if (buttonBackground != ButtonBackground.NONE) {
            drawButton(mouseX, mouseY);
        }
    }

    public final void onDrawBackground(int mouseX, int mouseY, float partialTicks) {
        if (visible) {
            drawBackground(mouseX, mouseY, partialTicks);
        }
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTicks) {
    }

    @Override
    public int getFGColor() {
        if (packedFGColor != UNSET_FG_COLOR) {
            return packedFGColor;
        }
        return active ? activeButtonTextColor() : inactiveButtonTextColor();
    }

    protected int getButtonTextColor(int mouseX, int mouseY) {
        return getFGColor();
    }

    protected boolean displayButtonTextShadow() {
        return true;
    }

    protected void drawButtonText(int mouseX, int mouseY) {
        ITextComponent text = getMessage();
        //Only attempt to draw the message if we have a message to draw
        if (!text.getFormattedText().isEmpty()) {
            int color = getButtonTextColor(mouseX, mouseY) | MathHelper.ceil(alpha * 255.0F) << 24;
            IFancyFontRenderer.super.drawScaledScrollingString(text, getButtonX(), getButtonY(), TextAlignment.CENTER, color, getButtonWidth(),
                  getButtonHeight() + 1, 2, displayButtonTextShadow(), 1, getTimeOpened());
        }
    }

    //This method exists so that we don't have to rely on having a path to super.renderWidget if we want to draw a background button
    protected void drawButton(int mouseX, int mouseY) {
        if (resetColorBeforeRender()) {
            //TODO: Support alpha like super? Is there a point
            MekanismRenderer.resetColor();
        }
        //TODO: Convert this to being two different 16x48 images, one for with border and one for buttons without a black border?
        // And then make it so that they can stretch out to be any size (make this make use of the renderExtendedTexture method
        MekanismRenderer.bindTexture(buttonBackground.getTexture());
        int i = getYImage(isMouseOverCheckWindows(mouseX, mouseY));
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.enableDepth();

        int width = getButtonWidth();
        int height = getButtonHeight();
        int halfWidthLeft = width / 2;
        int halfWidthRight = width % 2 == 0 ? halfWidthLeft : halfWidthLeft + 1;
        int halfHeightTop = height / 2;
        int halfHeightBottom = height % 2 == 0 ? halfHeightTop : halfHeightTop + 1;
        int position = i * 20;

        int x = getButtonX();
        int y = getButtonY();
        //Left Top Corner
        GuiUtils.blit(x, y, 0, position, halfWidthLeft, halfHeightTop, BUTTON_TEX_X, BUTTON_TEX_Y);
        //Left Bottom Corner
        GuiUtils.blit(x, y + halfHeightTop, 0, position + 20 - halfHeightBottom, halfWidthLeft, halfHeightBottom, BUTTON_TEX_X, BUTTON_TEX_Y);
        //Right Top Corner
        GuiUtils.blit(x + halfWidthLeft, y, 200 - halfWidthRight, position, halfWidthRight, halfHeightTop, BUTTON_TEX_X, BUTTON_TEX_Y);
        //Right Bottom Corner
        GuiUtils.blit(x + halfWidthLeft, y + halfHeightTop, 200 - halfWidthRight, position + 20 - halfHeightBottom, halfWidthRight, halfHeightBottom, BUTTON_TEX_X, BUTTON_TEX_Y);

        //TODO: Add support for buttons that are larger than 200x20 in either direction (most likely would be in the height direction
        // Can use a lot of the same logic as GuiMekanism does for its background

        renderBg(minecraft, mouseX, mouseY);
        //TODO: Re-evaluate this and FilterSelectButton#drawBackground as vanilla doesn't disable these after
        // it draws a button but I am not sure if that is intentional or causes issues
        GlStateManager.disableBlend();
        GlStateManager.disableDepth();
    }

    protected void renderExtendedTexture(ResourceLocation resource, int sideWidth, int sideHeight) {
        GuiUtils.renderExtendedTexture(resource, sideWidth, sideHeight, getButtonX(), getButtonY(), getButtonWidth(), getButtonHeight());
    }

    protected void renderBackgroundTexture(ResourceLocation resource, int sideWidth, int sideHeight) {
        GuiUtils.renderBackgroundTexture(resource, sideWidth, sideHeight, getButtonX(), getButtonY(), getButtonWidth(), getButtonHeight(), 256, 256);
    }

    @Override
    public void playDownSound(@Nonnull SoundHandler soundHandler) {
        if (customClickSound != null) {
            SoundEvent sound = customClickSound.get();
            if (sound != null) {
                soundHandler.playSound(PositionedSoundRecord.getRecord(sound, clickSoundPitch, clickSoundVolume));
            }
        } else if (playClickSound) {
            super.playDownSound(soundHandler);
        }
    }

    protected void playClickSound() {
        playDownSound(minecraft.getSoundHandler());
    }

    protected void drawTiledSprite(int xPosition, int yPosition, int yOffset, int desiredWidth, int desiredHeight, TextureAtlasSprite sprite, TilingDirection tilingDirection) {
        GuiUtils.drawTiledSprite(xPosition, yPosition, yOffset, desiredWidth, desiredHeight, sprite, 16, 16, 0, tilingDirection);
    }

    @Override
    public long getTimeOpened() {
        return guiObj.getTimeOpened();
    }

    @Override
    public void drawCenteredTextScaledBound(ITextComponent text, float maxLength, float x, float y, int color) {
        IFancyFontRenderer.super.drawCenteredTextScaledBound(text, maxLength, relativeX + x, relativeY + y, color);
    }

    @Override
    public void drawTitleTextWithOffset(ITextComponent text, float x, float y, float end, float maxLengthPad, TextAlignment alignment) {
        IFancyFontRenderer.super.drawTitleTextWithOffset(text, relativeX + x, relativeY + y, relativeX + end, maxLengthPad, alignment);
    }

    @Override
    public void drawScrollingString(ITextComponent text, float x, float y, TextAlignment alignment, int color, float width, float maxLengthPad, boolean shadow,
          long msVisible) {
        IFancyFontRenderer.super.drawScrollingString(text, relativeX + x, relativeY + y, alignment, color, width, getFont().FONT_HEIGHT, maxLengthPad, shadow,
              msVisible);
    }

    @Override
    public void drawScrollingString(ITextComponent text, float x, float y, TextAlignment alignment, int color, float width, float height, float maxLengthPad,
          boolean shadow, long msVisible) {
        IFancyFontRenderer.super.drawScrollingString(text, relativeX + x, relativeY + y, alignment, color, width, height, maxLengthPad, shadow, msVisible);
    }

    @Override
    public void drawScaledScrollingString(ITextComponent text, float x, float y, TextAlignment alignment, int color, float width, float maxLengthPad,
          boolean shadow, float textScale, long msVisible) {
        IFancyFontRenderer.super.drawScaledScrollingString(text, relativeX + x, relativeY + y, alignment, color, width, getFont().FONT_HEIGHT, maxLengthPad, shadow,
              textScale, msVisible);
    }

    @Override
    public void drawScaledScrollingString(ITextComponent text, float x, float y, TextAlignment alignment, int color, float width, float height, float maxLengthPad,
          boolean shadow, float textScale, long msVisible) {
        IFancyFontRenderer.super.drawScaledScrollingString(text, relativeX + x, relativeY + y, alignment, color, width, height, maxLengthPad, shadow, textScale, msVisible);
    }

    public enum ButtonBackground {
        DEFAULT(MekanismUtils.getResource(ResourceType.GUI, "button.png")),
        DIGITAL(MekanismUtils.getResource(ResourceType.GUI, "button_digital.png")),
        NONE(null);

        private final ResourceLocation texture;

        ButtonBackground(ResourceLocation texture) {
            this.texture = texture;
        }

        public ResourceLocation getTexture() {
            return texture;
        }
    }

    @FunctionalInterface
    public interface IHoverable {

        void onHover(GuiElement element, int mouseX, int mouseY);
    }

    @FunctionalInterface
    public interface IClickable {

        boolean onClick(GuiElement element, double mouseX, double mouseY);
    }

}
