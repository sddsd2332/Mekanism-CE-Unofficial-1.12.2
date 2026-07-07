package mekanism.client.gui;

import mekanism.api.text.ILangEntry;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiElement.IHoverable;
import mekanism.client.gui.element.IGuiEventListener;
import mekanism.client.gui.element.Widget;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.GuiVirtualSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.tab.GuiWarningTab;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.client.gui.warning.IWarningTracker;
import mekanism.client.gui.warning.WarningTracker;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.render.IFancyFontRenderer;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.Mekanism;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.container.slot.IVirtualSlot;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.lib.collection.LRU;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@SideOnly(Side.CLIENT)
public abstract class GuiMekanism<CONTAINER extends Container> extends VirtualSlotContainerScreen<CONTAINER> implements IGuiWrapper, IFancyFontRenderer {

    public static final ResourceLocation BASE_BACKGROUND = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "base.png");
    public static final ResourceLocation SHADOW = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "shadow.png");
    public static final ResourceLocation BLUR = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "blur.png");
    //TODO: Look into defaulting this to true
    protected boolean dynamicSlots;
    protected final LRU<GuiWindow> windows = new LRU<>();
    private final List<GuiWindow> queuedWindowCloses = new ArrayList<>();
    protected final List<GuiElement> focusListeners = new ArrayList<>();
    protected final List<Widget> buttons = new ArrayList<>();
    public boolean switchingToJEI;
    protected int titleLabelY = 6;
    protected int inventoryLabelX = 8;
    protected int inventoryLabelY = ySize - 94;
    @Nullable
    private IWarningTracker warningTracker;
    private long msOpened;

    private boolean hasClicked = false;
    private boolean dragging;
    @Nullable
    private IGuiEventListener focused;

    public static int maxZOffset;


    protected GuiMekanism(CONTAINER container) {
        super(container);
    }

    public static boolean isTextboxKey(char c, int keyCode) {
        return keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT ||
              keyCode == Keyboard.KEY_END || keyCode == Keyboard.KEY_HOME || isKeyComboCtrlA(keyCode) || isKeyComboCtrlC(keyCode) ||
              isKeyComboCtrlV(keyCode) || isKeyComboCtrlX(keyCode);
    }


    @Nonnull
    @Override
    public BooleanSupplier trackWarning(@Nonnull WarningType type, @Nonnull BooleanSupplier warningSupplier) {
        if (warningTracker == null) {
            warningTracker = new WarningTracker();
        }
        return warningTracker.trackWarning(type, warningSupplier);
    }

    @Nonnull
    @Override
    public BooleanSupplier trackWarning(@Nonnull mekanism.common.inventory.warning.WarningTracker.WarningType type, @Nonnull BooleanSupplier warningSupplier) {
        switch (type) {
            case INPUT_DOESNT_PRODUCE_OUTPUT:
                return trackWarning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT, warningSupplier);
            case NO_MATCHING_RECIPE:
                return trackWarning(WarningType.NO_MATCHING_RECIPE, warningSupplier);
            case NO_SPACE_IN_OUTPUT:
            case NO_SPACE_IN_OUTPUT_OVERFLOW:
                return trackWarning(WarningType.NO_SPACE_IN_OUTPUT, warningSupplier);
            case NOT_ENOUGH_ENERGY:
                return trackWarning(WarningType.NOT_ENOUGH_ENERGY, warningSupplier);
            case NOT_ENOUGH_ENERGY_REDUCED_RATE:
                return trackWarning(WarningType.NOT_ENOUGH_ENERGY_REDUCED_RATE, warningSupplier);
            case INVALID_OREDICTIONIFICATOR_FILTER:
                return trackWarning(WarningType.INVALID_OREDICTIONIFICATOR_FILTER, warningSupplier);
            case FILTER_HAS_BLACKLISTED_ELEMENT:
                return trackWarning(WarningType.FILTER_HAS_BLACKLISTED_ELEMENT, warningSupplier);
            case REDSTONE_SIGNAL_ABSENT:
                return trackWarning(WarningType.REDSTONE_SIGNAL_ABSENT, warningSupplier);
            case REDSTONE_SIGNAL_PRESENT:
                return trackWarning(WarningType.REDSTONE_SIGNAL_PRESENT, warningSupplier);
            case REDSTONE_PULSE_REQUIRED:
                return trackWarning(WarningType.REDSTONE_PULSE_REQUIRED, warningSupplier);
            default:
                return warningSupplier;
        }
    }

    @Override
    public void onGuiClosed() {
        if (!switchingToJEI) {
            //If we are not switching to JEI then run the super close method
            // which will exit the container. We don't want to mark the
            // container as exited if it will be revived when leaving JEI
            // Note: We start by closing all open windows so that any cleanup
            // they need to have done such as saving positions can be done
            while (!windows.isEmpty()) {
                windows.iterator().next().close();
            }
            super.onGuiClosed();
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        if (warningTracker != null) {
            //If our warning tracker isn't null (so this isn't the first time we are initializing, such as after resizing)
            // clear out any tracked warnings, so we don't have duplicates being tracked when we add our elements again
            warningTracker.clearTrackedWarnings();
        } else {
            msOpened = GuiElement.getMillis();
        }
        addGuiElements();
        if (warningTracker != null) {
            //If we have a warning tracker add it as a button, we do so via a method in case any of the sub GUIs need to reposition where it ends up
            addWarningTab(warningTracker);
        }
        initPinnedWindows();
    }

    @Override
    public long getTimeOpened() {
        return msOpened;
    }

    protected void initPinnedWindows() {
        if (windows.isEmpty()) {
            for (Widget child : buttons) {
                if (child instanceof GuiElement element) {
                    element.openPinnedWindows();
                }
            }
        }
    }

    protected void addWarningTab(IWarningTracker warningTracker) {
        //TODO - WARNING SYSTEM: Move this for any GUIs that also have a heat tab (81 y would be above heat)
        addButton(new GuiWarningTab(this, warningTracker, 109));
    }

    /**
     * Called to add gui elements to the GUI. Add elements before calling super if they should be before the slots, and after if they should be after the slots. Most
     * elements can and should be added after the slots.
     */
    protected void addGuiElements() {
        if (dynamicSlots) {
            addSlots();
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        buttons.stream().filter(child -> child instanceof GuiElement).map(child -> (GuiElement) child).forEach(GuiElement::tick);
        windows.forEach(GuiElement::tick);
        closeQueuedWindows();
    }

    public void queueWindowClose(GuiWindow window) {
        if (window != null && windows.contains(window) && !queuedWindowCloses.contains(window)) {
            queuedWindowCloses.add(window);
        }
    }

    private void closeQueuedWindows() {
        if (queuedWindowCloses.isEmpty()) {
            return;
        }
        List<GuiWindow> toClose = new ArrayList<>(queuedWindowCloses);
        queuedWindowCloses.clear();
        for (GuiWindow window : toClose) {
            if (windows.contains(window)) {
                window.close();
            }
        }
    }

    protected IHoverable getOnHover(ILangEntry translationHelper) {
        return getOnHover((Supplier<ITextComponent>) translationHelper::translate);
    }

    protected IHoverable getOnHover(Supplier<ITextComponent> componentSupplier) {
        return (onHover, xAxis, yAxis) -> displayTooltip(componentSupplier.get().getFormattedText(), xAxis, yAxis);
    }

    protected ResourceLocation getButtonLocation(String name) {
        return MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_BUTTON, name + ".png");
    }

    @Nonnull
    @Override
    public ItemStack getCarriedItem() {
        return mc.player == null ? ItemStack.EMPTY : mc.player.inventory.getItemStack();
    }

    @Override
    public void addFocusListener(GuiElement element) {
        focusListeners.add(element);
    }

    @Override
    public void removeFocusListener(GuiElement element) {
        focusListeners.remove(element);
    }

    @Override
    public void focusChange(GuiElement changed) {
        focusListeners.stream().filter(e -> e != changed && !changed.containsElement(child -> child == e)).forEach(e -> e.setFocused(false));
    }

    @Override
    public void incrementFocus(GuiElement current) {
        int index = focusListeners.indexOf(current);
        if (index != -1) {
            GuiElement next = focusListeners.get((index + 1) % focusListeners.size());
            setFocused(next);
            focusChange(next);
        }
    }

    @Nullable
    public IGuiEventListener getFocused() {
        return focused;
    }

    public boolean isDragging() {
        return dragging;
    }

    public void setDragging(boolean dragging) {
        this.dragging = dragging;
    }

    public void setFocused(@Nullable IGuiEventListener listener) {
        if (focused == listener) {
            if (listener instanceof GuiElement element) {
                element.setFocused(true);
                focusChange(element);
            }
            return;
        }
        if (focused instanceof GuiElement element) {
            element.setFocused(false);
        }
        focused = listener;
        if (listener instanceof GuiElement element) {
            element.setFocused(true);
            focusChange(element);
        }
    }

    private void clearFocus() {
        setFocused(null);
    }

    @Override
    protected boolean hasClickedOutside(int mouseX, int mouseY, int guiLeftIn, int guiTopIn) {
        return getWindowHovering(mouseX, mouseY) == null && super.hasClickedOutside(mouseX, mouseY, guiLeftIn, guiTopIn);
    }

    @Override
    public void setWorldAndResolution(@Nonnull Minecraft minecraft, int width, int height) {
        if (switchingToJEI) {
            //Mark that we are not switching to JEI if we start being initialized again
            switchingToJEI = false;
            msOpened = GuiElement.getMillis();
        }
        //Note: We are forced to do the logic that normally would be inside the "resize" method
        // here in init, as when mods like JEI take over the screen to show recipes, and then
        // return the screen to the "state" it was beforehand it does not actually properly
        // transfer the state from the previous instance to the new instance. If we run the
        // code we normally would run for when things get resized, we then are able to
        // properly reinstate/transfer the states of the various elements
        List<PreviousElement> prevElements = new ArrayList<>();
        IGuiEventListener previousFocused = getFocused();
        for (int i = 0; i < buttons.size(); i++) {
            Widget widget = buttons.get(i);
            if (widget instanceof GuiElement element) {
                boolean wasPreviousFocus = element == previousFocused;
                if (wasPreviousFocus || element.hasPersistentData()) {
                    prevElements.add(new PreviousElement(i, element, wasPreviousFocus));
                }
            }
        }
        // flush the focus listeners list unless it's an overlay
        focusListeners.removeIf(element -> !element.isOverlay);
        int prevLeft = guiLeft, prevTop = guiTop;
        List<GuiWindow> existingWindows = new ArrayList<>(windows);

        //Rebuilding on resize/fullscreen toggle must start from a clean element list.
        //If we keep stale widgets, old and new guiLeft/guiTop coordinate spaces overlap and render/click handling desyncs.
        this.buttons.clear();
        super.setWorldAndResolution(minecraft, width, height);
        existingWindows.forEach(window -> {
            if (windows.contains(window)) {
                window.resize(prevLeft, prevTop, guiLeft, guiTop);
            }
        });
        prevElements.forEach(e -> {
            if (e.index < buttons.size()) {
                Widget widget = buttons.get(e.index);
                // we're forced to assume that the children list is the same before and after the resize.
                // for verification, we run a lightweight class equality check
                // Note: We do not perform an instance check on widget to ensure it is a GuiElement, as that is
                // ensured by the class comparison, and the restrictions of what can go in prevElements
                if (widget.getClass() == e.element.getClass()) {
                    ((GuiElement) widget).syncFrom(e.element);
                    if (e.wasFocus) {
                        setFocused((IGuiEventListener) widget);
                    }
                }
            }
        });
    }

    private static class PreviousElement {

        private final int index;
        private final GuiElement element;
        private final boolean wasFocus;

        private PreviousElement(int index, GuiElement element, boolean wasFocus) {
            this.index = index;
            this.element = element;
            this.wasFocus = wasFocus;
        }
    }


    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        MekanismRenderer.resetGuiRenderState();
        GlStateManager.pushMatrix();
        //Shift forward as far as tooltips get shifted so that we don't risk intersecting the rendered items
        GlStateManager.translate(0, 0, 400);
        for (Widget c : buttons) {
            if (c instanceof GuiElement) {
                ((GuiElement) c).onDrawBackground(mouseX, mouseY, MekanismRenderer.getPartialTick());
            }
        }
        drawForegroundText(mouseX, mouseY);
        // first render general foregrounds
        int zOffset = 200;
        maxZOffset = zOffset;
        for (Widget widget : this.buttons) {
            if (widget instanceof GuiElement) {
                GlStateManager.pushMatrix();
                ((GuiElement) widget).onRenderForeground(mouseX, mouseY, zOffset, zOffset);
                GlStateManager.popMatrix();
            }
        }

        // now render overlays in reverse-order (i.e. back to front)
        for (LRU<GuiWindow>.LRUIterator iter = getWindowsDescendingIterator(); iter.hasNext(); ) {
            GuiWindow overlay = iter.next();
            //Max z offset is incremented based on what is the deepest level offset we go to
            // if our gui isn't flagged as visible we won't increment it as nothing is drawn
            // we need to do this based on what the max is after having rendered the previous
            // window as while the windows don't necessarily overlap, if they do we want to
            // ensure that there is no clipping
            zOffset = maxZOffset + 150;
            GlStateManager.pushMatrix();
            MekanismRenderer.resetGuiRenderState();
            overlay.onRenderForeground(mouseX, mouseY, zOffset, zOffset);
            MekanismRenderer.resetGuiRenderState();
            if (iter.hasNext()) {
                // if this isn't the focused window, render a 'blur' effect over it
                overlay.renderBlur();
                MekanismRenderer.resetGuiRenderState();
            }
            GlStateManager.popMatrix();
        }
        GlStateManager.popMatrix();
        //Additionally hacky offset to make it so that we render above items in higher z-levels for things like tooltips and held items
        maxZOffset += 200;
        // then render tooltips, translating above max z offset to prevent clashing
        // It is IMPORTANT that we do this to ensure any delayed rendering we do the for the tooltip happens above the other things
        // and so that we let the translation leak out into the super method so that the carried item renders at the correct z level
        GlStateManager.translate(0, 0, maxZOffset);

        MekanismRenderer.resetGuiRenderState();
        GlStateManager.pushMatrix();
        //Note: Because we are doing this from drawGuiContainerForegroundLayer instead of as part of a drawScreen override,
        // we need to unshift back to the position the other methods expect to be called from
        GlStateManager.translate(-guiLeft, -guiTop, 0);
        GuiElement tooltipElement = getWindowHovering(mouseX, mouseY);
        if (tooltipElement == null) {
            for (int i = buttons.size() - 1; i >= 0; i--) {
                Widget widget = buttons.get(i);
                if (widget instanceof GuiElement element && element.isMouseOverTooltip(mouseX, mouseY)) {
                    tooltipElement = element;
                    break;
                }
            }
        }
        if (tooltipElement != null) {
            tooltipElement.renderToolTip(mouseX, mouseY);
        }
        renderHoveredToolTip(mouseX, mouseY);
        GlStateManager.popMatrix();
    }

    protected void drawForegroundText(int mouseX, int mouseY) {
    }

    protected void renderInventoryText() {
        renderInventoryText(getXSize());
    }

    protected void renderInventoryText(int end) {
        renderInventoryText(inventoryLabelX, inventoryLabelY, end);
    }

    protected void renderInventoryText(int x, int y, int end) {
        drawScaledTextScaledBound(new TextComponentString(mekanism.common.util.LangUtils.localize("container.inventory")), x, y, titleTextColor(), end - x - 6, 1);
    }

    protected void renderInventoryTextAndOther(ITextComponent rightAlignedText) {
        renderInventoryTextAndOther(rightAlignedText, 0);
    }

    protected void renderInventoryTextAndOther(ITextComponent rightAlignedText, int rightEndPad) {
        drawScaledTextScaledBound(new TextComponentString(mekanism.common.util.LangUtils.localize("container.inventory")),
              inventoryLabelX, inventoryLabelY, titleTextColor(), 53, 1);
        int rightStart = inventoryLabelX + 51;
        drawScaledScrollingString(rightAlignedText, rightStart, inventoryLabelY, TextAlignment.RIGHT, titleTextColor(),
              getXSize() - rightStart - rightEndPad, 6, false, 1, getTimeOpened());
    }


    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        hasClicked = true;
        GuiWindow top = windows.isEmpty() ? null : windows.iterator().next();
        GuiWindow focused = windows.stream().filter(overlay -> overlay.mouseClicked(mouseX, mouseY, button)).findFirst().orElse(null);
        if (focused != null) {
            if (windows.contains(focused)) {
                focusWindow(focused, top);
                if (button == 0) {
                    setDragging(true);
                }
            }
            return;
        }
        // otherwise, we send it to the current element
        for (int i = buttons.size() - 1; i >= 0; i--) {
            IGuiEventListener listener = buttons.get(i);
            if (listener.mouseClicked(mouseX, mouseY, button)) {
                setFocused(listener);
                if (button == 0) {
                    setDragging(true);
                }
                return;
            }
        }
        clearFocus();
        super.mouseClicked(mouseX, mouseY, button);
    }


    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        if (hasClicked) {
            if (!windows.isEmpty()) {
                windows.forEach(window -> window.onRelease(mouseX, mouseY));
            }
            setDragging(false);
            super.mouseReleased(mouseX, mouseY, button);
        }
    }

    @Override
    public void keyTyped(char c, int keyCode) throws IOException {
        for (GuiWindow window : windows) {
            if (window.keyPressed(keyCode, 0, 0)) {
                return;
            }
        }
        if (GuiUtils.checkChildren(buttons, child -> child.keyPressed(keyCode, 0, 0))) {
            return;
        }
        for (GuiWindow window : windows) {
            if (window.charTyped(c, keyCode)) {
                return;
            }
        }
        if (GuiUtils.checkChildren(buttons, child -> child.charTyped(c, keyCode))) {
            return;
        }
        super.keyTyped(c, keyCode);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int delta = Mouse.getEventDWheel();
        if (delta != 0) {
            int mouseX = Mouse.getEventX() * width / mc.displayWidth;
            int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            GuiWindow top = windows.isEmpty() ? null : windows.iterator().next();
            GuiWindow hovered = getWindowHovering(mouseX, mouseY);
            if (hovered != null) {
                boolean windowScroll = hovered.mouseScrolled(mouseX, mouseY, delta);
                if (windowScroll) {
                    focusWindow(hovered, top);
                    return;
                } else if (!hovered.getInteractionStrategy().allowAll()) {
                    return;
                }
            }
            if (top != null && top != hovered) {
                boolean windowScroll = top.mouseScrolled(mouseX, mouseY, delta);
                if (windowScroll || !top.getInteractionStrategy().allowAll()) {
                    return;
                }
            }
            //Top-most element first, matching click ordering.
            for (int i = buttons.size() - 1; i >= 0; i--) {
                if (buttons.get(i).mouseScrolled(mouseX, mouseY, delta)) {
                    break;
                }
            }
        }
    }

    private void focusWindow(GuiWindow focused, @Nullable GuiWindow top) {
        setFocused(focused);
        if (top != null && top != focused) {
            top.onFocusLost();
            windows.moveUp(focused);
            focused.onFocused();
        }
    }


    /*
     * @apiNote mouseXOld and mouseYOld are just guessed mappings I couldn't find any usage from a quick glance.
     */
    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (focused != null && dragging && clickedMouseButton == 0) {
            focused.mouseDragged(mouseX, mouseY, clickedMouseButton, 0, 0);
        }
    }


    @Nullable
    @Override
    @Deprecated//Don't use directly, this is normally private in ContainerScreen
    public Slot getSlotAtPosition(int mouseX, int mouseY) {
        //We override the implementation we have in VirtualSlotContainerScreen so that we can cache getting our window
        // and have some general performance improvements given we can batch a bunch of lookups together
        boolean checkedWindow = false;
        boolean overNoButtons = false;
        GuiWindow window = null;
        for (Slot slot : inventorySlots.inventorySlots) {
            if (!slot.isEnabled()) {
                continue;
            }
            boolean virtual = slot instanceof IVirtualSlot;
            int xPos = slot.xPos;
            int yPos = slot.yPos;
            if (virtual) {
                //Virtual slots need special handling to allow for matching them to the window they may be attached to
                IVirtualSlot virtualSlot = (IVirtualSlot) slot;
                if (!isVirtualSlotAvailable(virtualSlot)) {
                    continue;
                }
                xPos = virtualSlot.getActualX();
                yPos = virtualSlot.getActualY();
            }
            if (super.isPointInRegion(xPos, yPos, 16, 16, mouseX, mouseY)) {
                if (!checkedWindow) {
                    //Only lookup the window once
                    checkedWindow = true;
                    window = getWindowHovering(mouseX, mouseY);
                    overNoButtons = overNoButtons(window, mouseX, mouseY);
                }
                if (overNoButtons && slot.isEnabled()) {
                    if (window == null) {
                        return slot;
                    } else if (virtual && window.childrenContainsElement(element -> element instanceof GuiVirtualSlot && ((GuiVirtualSlot) element).isElementForSlot((IVirtualSlot) slot))) {
                        return slot;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    @Override
    public Slot getSlotUnderMouse(int mouseX, int mouseY) {
        return getSlotAtPosition(mouseX, mouseY);
    }

    @Override
    public boolean isHovering(@Nonnull Slot slot, int mouseX, int mouseY) {
        if (slot instanceof IVirtualSlot virtualSlot) {
            //Virtual slots need special handling to allow for matching them to the window they may be attached to
            if (isVirtualSlotAvailable(virtualSlot)) {
                int xPos = virtualSlot.getActualX();
                int yPos = virtualSlot.getActualY();
                if (super.isPointInRegion(xPos, yPos, 16, 16, mouseX, mouseY)) {
                    GuiWindow window = getWindowHovering(mouseX, mouseY);
                    //If we are hovering over a window, check if the virtual slot is a child of the window
                    if (window == null || window.childrenContainsElement(element -> element instanceof GuiVirtualSlot &&
                          ((GuiVirtualSlot) element).isElementForSlot(virtualSlot))) {
                        return overNoButtons(window, mouseX, mouseY);
                    }
                }
            }
            return false;
        }
        return isPointInRegion(slot.xPos, slot.yPos, 16, 16, mouseX, mouseY);
    }

    private boolean overNoButtons(@Nullable GuiWindow window, double mouseX, double mouseY) {
        if (window == null) {
            return buttons.stream().noneMatch(button -> button.isMouseOver(mouseX, mouseY));
        }
        return !window.childrenContainsElement(e -> e.isMouseOver(mouseX, mouseY));
    }

    private boolean isVirtualSlotAvailable(IVirtualSlot virtualSlot) {
        return !(virtualSlot.getLinkedWindow() instanceof GuiWindow) || windows.contains((GuiWindow) virtualSlot.getLinkedWindow());
    }

    @Override
    protected boolean isPointInRegion(int x, int y, int width, int height, int mouseX, int mouseY) {
        // overridden to prevent slot interactions when a GuiElement is blocking
        return super.isPointInRegion(x, y, width, height, mouseX, mouseY) && getWindowHovering(mouseX, mouseY) == null && overNoButtons(null, mouseX, mouseY);
    }


    protected void addSlots() {
        int size = inventorySlots.inventorySlots.size();
        for (int i = 0; i < size; i++) {
            Slot slot = inventorySlots.inventorySlots.get(i);
            if (slot instanceof InventoryContainerSlot) {
                InventoryContainerSlot containerSlot = (InventoryContainerSlot) slot;
                ContainerSlotType slotType = containerSlot.getSlotType();
                DataType dataType = findDataType(containerSlot);
                //Shift the slots by one as the elements include the border of the slot
                SlotType type;
                if (dataType != null) {
                    type = SlotType.get(dataType);
                } else if (slotType == ContainerSlotType.INPUT || slotType == ContainerSlotType.OUTPUT || slotType == ContainerSlotType.EXTRA) {
                    type = SlotType.NORMAL;
                } else if (slotType == ContainerSlotType.POWER) {
                    type = SlotType.POWER;
                } else if (slotType == ContainerSlotType.NORMAL || slotType == ContainerSlotType.VALIDITY) {
                    type = SlotType.NORMAL;
                } else {//slotType == ContainerSlotType.IGNORED: don't do anything
                    continue;
                }
                GuiSlot guiSlot = new GuiSlot(type, this, slot.xPos - 1, slot.yPos - 1);
                guiSlot.visibility(slot::isEnabled);
                SlotOverlay slotOverlay = containerSlot.getSlotOverlay();
                if (slotOverlay != null) {
                    guiSlot.with(slotOverlay);
                }
                guiSlot.tooltip(() -> getSlotTypeTooltip(slotType, slot));
                if (slotType == ContainerSlotType.VALIDITY) {
                    int index = i;
                    guiSlot.validity(() -> checkValidity(index));
                }
                containerSlot.addWarnings(guiSlot);
                addButton(guiSlot);
            } else {
                addButton(new GuiSlot(SlotType.NORMAL, this, slot.xPos - 1, slot.yPos - 1).visibility(slot::isEnabled));
            }
        }
    }

    private List<String> getSlotTypeTooltip(ContainerSlotType slotType, Slot slot) {
        if (!MekanismConfig.current().client.enableSlotTypeTooltips.val() || slot.getHasStack()) {
            return Collections.emptyList();
        }
        String tooltipKey = switch (slotType) {
            case POWER -> "power";
            case INPUT -> "input";
            case EXTRA -> "extra";
            case OUTPUT -> "output";
            default -> null;
        };
        if (tooltipKey == null) {
            return Collections.emptyList();
        }
        List<String> tooltip = new ArrayList<>(2);
        tooltip.add(LangUtils.localize("mekanism.gui.slot." + tooltipKey));
        tooltip.add(LangUtils.localize("mekanism.gui.slot." + tooltipKey + ".tooltip"));
        return tooltip;
    }

    @Nullable
    protected DataType findDataType(InventoryContainerSlot slot) {
        if (inventorySlots instanceof MekanismContainer && this instanceof GuiMekanismTile<?, ?> tileGui &&
            tileGui.getTileEntity() instanceof ISideConfiguration sideConfig) {
            return sideConfig.getActiveDataType(slot.getInventorySlot());
        }
        return null;
    }


    protected ItemStack checkValidity(int slotIndex) {
        return ItemStack.EMPTY;
    }


    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTick, int mouseX, int mouseY) {
        // Mods adding overlays or custom renderers may leave GL in an unexpected state.
        MekanismRenderer.resetGuiRenderState();
        if (width < 8 || height < 8) {
            Mekanism.logger.warn("Gui: {}, was too small to draw the background of. Unable to draw a background for a gui smaller than 8 by 8.", getClass().getSimpleName());
            return;
        }
        GuiUtils.renderBackgroundTexture(BASE_BACKGROUND, 4, 4, guiLeft, guiTop, xSize, ySize, 256, 256);
        MekanismRenderer.resetColor();
        this.buttons.forEach(button -> button.render(mouseX, mouseY, partialTick));
        MekanismRenderer.resetColor();
    }

    @Override
    public FontRenderer getFont() {
        return fontRenderer;
    }

    @Override
    public int getXSize() {
        return xSize;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        MekanismRenderer.resetGuiRenderState();
        GlStateManager.translate(0, 0, -500);
        GlStateManager.pushMatrix();
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        GlStateManager.popMatrix();
        GlStateManager.translate(0, 0, 500);
    }

    @Override
    public void renderItemTooltip(@Nonnull ItemStack stack, int xAxis, int yAxis) {
        renderToolTip(stack, xAxis, yAxis);
    }

    @Override
    public void renderItemTooltipWithExtra(@Nonnull ItemStack stack, int xAxis, int yAxis, List<String> toAppend) {
        if (toAppend.isEmpty()) {
            renderItemTooltip(stack, xAxis, yAxis);
        } else {
            FontRenderer font = stack.getItem().getFontRenderer(stack);
            net.minecraftforge.fml.client.config.GuiUtils.preItemToolTip(stack);
            List<String> tooltip = new ArrayList<>(getItemToolTip(stack));
            tooltip.addAll(toAppend);
            drawHoveringText(tooltip, xAxis, yAxis, (font == null ? this.fontRenderer : font));
            net.minecraftforge.fml.client.config.GuiUtils.postItemToolTip();
        }
    }

    @Override
    public RenderItem getItemRenderer() {
        return itemRender;
    }

    @Override
    public boolean currentlyQuickCrafting() {
        return dragSplitting && !dragSplittingSlots.isEmpty();
    }


    protected <T extends Widget> T addButton(T buttonIn) {
        this.buttons.add(buttonIn);
        return buttonIn;
    }

    @Override
    public void addWindow(GuiWindow window) {
        GuiWindow top = windows.isEmpty() ? null : windows.iterator().next();
        if (top != null) {
            top.onFocusLost();
        }
        windows.add(window);
        window.onFocused();
    }

    @Override
    public void removeWindow(GuiWindow window) {
        if (!windows.isEmpty()) {
            GuiWindow top = windows.iterator().next();
            windows.remove(window);
            if (window == top) {
                //If the window was the top window, make it lose focus
                window.onFocusLost();
                //Amd check if a new window is now in focus
                GuiWindow newTop = windows.isEmpty() ? null : windows.iterator().next();
                if (newTop == null) {
                    //If there isn't any because they have all been removed
                    // fire an "event" for any post all windows being closed
                    lastWindowRemoved();
                } else {
                    //Otherwise, mark the new window as being focused
                    newTop.onFocused();
                }
                //Keep local focus state in sync with the active window after closing the previous top window.
                window.setFocused(false);
                if (newTop != null) {
                    setFocused(newTop);
                } else {
                    clearFocus();
                }
            }
        }
    }

    protected void lastWindowRemoved() {
        if (inventorySlots instanceof MekanismContainer container) {
            container.setSelectedWindow(null);
        }
    }

    @Override
    public void setSelectedWindow(SelectedWindowData selectedWindow) {
        if (inventorySlots instanceof MekanismContainer container) {
            container.setSelectedWindow(selectedWindow);
        }
    }

    @Nullable
    @Override
    public GuiWindow getWindowHovering(double mouseX, double mouseY) {
        return windows.stream().filter(w -> w.isMouseOver(mouseX, mouseY)).findFirst().orElse(null);
    }

    public List<Widget> children() {
        return buttons;
    }

    public Collection<GuiWindow> getWindows() {
        return windows;
    }

    public LRU<GuiWindow>.LRUIterator getWindowsDescendingIterator() {
        return windows.descendingIterator();
    }

}
