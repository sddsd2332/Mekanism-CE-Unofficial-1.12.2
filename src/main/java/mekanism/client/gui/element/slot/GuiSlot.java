package mekanism.client.gui.element.slot;

import mekanism.api.EnumColor;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.transmitters.TransmissionType;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiTexturedElement;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.MekanismLang;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.warning.ISupportsWarning;
import mekanism.common.item.ItemConfigurator;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class GuiSlot extends GuiTexturedElement implements IRecipeViewerGhostTarget, ISupportsWarning<GuiSlot> {

    private static final int INVALID_SLOT_COLOR = MekanismRenderer.getColorARGB(EnumColor.DARK_RED, 0.8F);
    public static final int DEFAULT_HOVER_COLOR = 0x80FFFFFF;
    private final SlotType slotType;
    private Supplier<ItemStack> validityCheck;
    private Supplier<ItemStack> storedStackSupplier;
    private Supplier<SlotOverlay> overlaySupplier;
    @Nullable
    private BooleanSupplier warningSupplier;
    @Nullable
    private BooleanSupplier visibilitySupplier;
    @Nullable
    private IntSupplier overlayColorSupplier;
    @Nullable
    private SlotOverlay overlay;
    @Nullable
    private IHoverable onHover;
    @Nullable
    private Supplier<List<String>> tooltipSupplier;
    @Nullable
    private GuiElement.IClickable onClick;
    private boolean renderHover;
    private boolean renderAboveSlots;
    @Nullable
    private IGhostIngredientConsumer ghostHandler;

    public GuiSlot(SlotType type, IGuiWrapper gui, int x, int y) {
        super(type.getTexture(), gui, x, y, type.getWidth(), type.getHeight());
        this.slotType = type;
        active = false;
    }

    public GuiSlot validity(Supplier<ItemStack> validityCheck) {
        //TODO - WARNING SYSTEM: Evaluate if any of these validity things should be moved to the warning system
        this.validityCheck = validityCheck;
        return this;
    }

    public GuiSlot warning(@Nonnull WarningType type, @Nonnull BooleanSupplier warningSupplier) {
        this.warningSupplier = ISupportsWarning.compound(this.warningSupplier, gui().trackWarning(type, warningSupplier));
        return this;
    }

    @Override
    public GuiSlot warning(@Nonnull mekanism.common.inventory.warning.WarningTracker.WarningType type, @Nonnull BooleanSupplier warningSupplier) {
        this.warningSupplier = ISupportsWarning.compound(this.warningSupplier, gui().trackWarning(type, warningSupplier));
        return this;
    }

    public GuiSlot visibility(@Nonnull BooleanSupplier visibilitySupplier) {
        this.visibilitySupplier = visibilitySupplier;
        updateVisibility();
        return this;
    }

    /**
     * @apiNote For use when there is no validity check and this is a "fake" slot in that the container screen doesn't render the item by default.
     */
    public GuiSlot stored(Supplier<ItemStack> storedStackSupplier) {
        this.storedStackSupplier = storedStackSupplier;
        return this;
    }

    public GuiSlot hover(IHoverable onHover) {
        this.onHover = onHover;
        return this;
    }

    public GuiSlot tooltip(Supplier<List<String>> tooltipSupplier) {
        this.tooltipSupplier = tooltipSupplier;
        return this;
    }

    public GuiSlot click(IClickable onClick) {
        return click(onClick, 0.25F, null);
    }

    public GuiSlot click(IClickable onClick, float clickSoundVolume, @Nullable Supplier<SoundEvent> customClickSound) {
        this.onClick = onClick;
        this.customClickSound = customClickSound;
        this.clickSoundVolume = clickSoundVolume;
        playClickSound = customClickSound == null;
        return this;
    }

    public GuiSlot with(SlotOverlay overlay) {
        this.overlay = overlay;
        return this;
    }

    public GuiSlot overlayColor(IntSupplier colorSupplier) {
        overlayColorSupplier = colorSupplier;
        return this;
    }

    public GuiSlot with(Supplier<SlotOverlay> overlaySupplier) {
        this.overlaySupplier = overlaySupplier;
        return this;
    }

    public GuiSlot setRenderHover(boolean renderHover) {
        this.renderHover = renderHover;
        return this;
    }

    public GuiSlot setGhostHandler(@Nullable IGhostIngredientConsumer ghostHandler) {
        this.ghostHandler = ghostHandler;
        return this;
    }

    public GuiSlot setRenderAboveSlots() {
        this.renderAboveSlots = true;
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        updateVisibility();
    }

    private void updateVisibility() {
        if (visibilitySupplier != null) {
            visible = visibilitySupplier.getAsBoolean();
        }
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTicks) {
        updateVisibility();
        if (!renderAboveSlots) {
            draw();
        }
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        updateVisibility();
        if (renderAboveSlots) {
            draw();
        }
    }

    private void draw() {
        if (warningSupplier != null && warningSupplier.getAsBoolean()) {
            minecraft.renderEngine.bindTexture(slotType.getWarningTexture());
        } else {
            minecraft.renderEngine.bindTexture(getResource());
        }
        GuiUtils.blit(relativeX, relativeY, 0, 0, width, height, width, height);
        if (overlaySupplier != null) {
            overlay = overlaySupplier.get();
        }
        if (overlay != null) {
            minecraft.renderEngine.bindTexture(overlay.getTexture());
            GuiUtils.blit(relativeX, relativeY, 0, 0, overlay.getWidth(), overlay.getHeight(), overlay.getWidth(), overlay.getHeight());
        }
        drawContents();
    }

    protected void drawContents() {
        if (validityCheck != null) {
            ItemStack invalid = validityCheck.get();
            if (!invalid.isEmpty()) {
                int xPos = relativeX + 1;
                int yPos = relativeY + 1;
                GuiUtils.fill(xPos, yPos, xPos + 16, yPos + 16, INVALID_SLOT_COLOR);
                MekanismRenderer.resetColor();
                gui().renderItem(invalid, xPos, yPos);
            }
        } else if (storedStackSupplier != null) {
            ItemStack stored = storedStackSupplier.get();
            if (!stored.isEmpty()) {
                gui().renderItem(stored, relativeX + 1, relativeY + 1);
            }
        }
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        updateVisibility();
        boolean hovered = checkWindows(mouseX, mouseY, isHovered);
        if (renderHover && hovered) {
            int xPos = relativeX + 1;
            int yPos = relativeY + 1;
            GuiUtils.fill(xPos, yPos, xPos + 16, yPos + 16, DEFAULT_HOVER_COLOR);
            MekanismRenderer.resetColor();
        }
        if (overlayColorSupplier != null) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, 0, 10);
            int xPos = relativeX + 1;
            int yPos = relativeY + 1;
            GuiUtils.fill(xPos, yPos, xPos + 16, yPos + 16, overlayColorSupplier.getAsInt());
            GlStateManager.popMatrix();
            MekanismRenderer.resetColor();
        }
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        if (onHover != null) {
            onHover.onHover(this, mouseX, mouseY);
            return;
        }
        if (tooltipSupplier != null) {
            List<String> tooltip = tooltipSupplier.get();
            if (tooltip != null && !tooltip.isEmpty()) {
                displayTooltips(tooltip, mouseX, mouseY);
                return;
            }
        }
        ITextComponent tooltip = getConfiguratorSlotTooltip(mouseX, mouseY);
        if (tooltip != null) {
            displayTooltip(tooltip, mouseX, mouseY);
        }
    }

    @Override
    public boolean isMouseOverTooltip(double mouseX, double mouseY) {
        updateVisibility();
        return visible && mouseX >= getX() && mouseY >= getY() && mouseX < getRight() && mouseY < getBottom() && checkWindows(mouseX, mouseY);
    }

    @Nullable
    private ITextComponent getConfiguratorSlotTooltip(int mouseX, int mouseY) {
        ItemStack stack = gui().getCarriedItem();
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemConfigurator) || !(gui() instanceof GuiMekanismTile<?, ?> tileGui) ||
            !(tileGui.getTileEntity() instanceof ISideConfiguration config) || config.getConfig() == null) {
            return null;
        }
        Slot hovering = gui().getSlotUnderMouse(mouseX, mouseY);
        if (!(hovering instanceof InventoryContainerSlot slot)) {
            return null;
        }
        ConfigInfo info = config.getConfig().getConfigInfo(TransmissionType.ITEM);
        if (info == null) {
            return null;
        }
        IInventorySlot inventorySlot = slot.getInventorySlot();
        for (DataType type : info.getSupportedDataTypes()) {
            if (info.getSlotInfo(type) instanceof InventorySlotInfo slotInfo && slotInfo.hasSlot(inventorySlot)) {
                EnumColor color = type.getColor();
                return MekanismLang.GENERIC_WITH_PARENTHESIS.translateColored(color, type.localize(), color.getColoredName());
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        updateVisibility();
        if (onClick != null && isValidClickButton(button)) {
            if (mouseX >= getX() + borderSize() && mouseY >= getY() + borderSize() && mouseX < getRight() - borderSize() && mouseY < getBottom() - borderSize()) {
                if (onClick.onClick(this, mouseX, mouseY)) {
                    playDownSound(Minecraft.getMinecraft().getSoundHandler());
                    return true;
                }
            }
            //If clicking the slot fails check super as maybe it has children that can handle clicks
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Nullable
    @Override
    public IGhostIngredientConsumer getGhostHandler() {
        return ghostHandler;
    }

    @Override
    public int borderSize() {
        return 1;
    }
}
