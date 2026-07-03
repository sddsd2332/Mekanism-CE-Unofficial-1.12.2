package mekanism.client.gui.element.button;

import mekanism.api.EnumColor;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.slot.GuiSequencedSlotDisplay;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.common.MekanismLang;
import mekanism.common.content.filter.*;
import mekanism.common.content.transporter.TransporterFilter;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.*;

public class FilterButton extends MekanismButton {

    private static final ResourceLocation TEXTURE = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_BUTTON, "filter_holder.png");
    protected static final int TEXTURE_WIDTH = 156;
    protected static final int TEXTURE_HEIGHT = 58;

    protected final FilterManager<?> filterManager;
    private final GuiSequencedSlotDisplay slotDisplay;
    private final IntSupplier filterIndex;
    private final GuiSlot slot;
    private final RadioButton toggleButton;
    private final int index;
    private IFilter prevFilter;

    @Nullable
    private static IFilter getFilter(FilterManager<?> filterManager, int index) {
        return index >= 0 && index < filterManager.count() ? filterManager.getFilters().get(index) : null;
    }

    public FilterButton(IGuiWrapper gui, int x, int y, int width, int height, int index, IntSupplier filterIndex, FilterManager<?> filterManager,
          ObjIntConsumer<IFilter> onPress, IntConsumer toggleButtonPress, Function<IFilter, List<ItemStack>> renderStackSupplier) {
        super(gui, x, y, width, height, new TextComponentString(""), () -> {
        }, null);
        this.index = index;
        this.filterIndex = filterIndex;
        this.filterManager = filterManager;
        slot = addChild(new GuiSlot(SlotType.NORMAL, gui, relativeX + 2, relativeY + 2));
        slotDisplay = addChild(new GuiSequencedSlotDisplay(gui, relativeX + 3, relativeY + 3, () -> renderStackSupplier.apply(getFilter())));
        toggleButton = addChild(new RadioButton(gui, relativeX + this.width - RadioButton.RADIO_SIZE - getToggleXShift(),
              relativeY + (this.height / 2) - (RadioButton.RADIO_SIZE / 2), this::isEnabled, (element, mouseX, mouseY) -> {
                  toggleButtonPress.accept(getActualIndex());
                  return true;
              }, MekanismLang.FILTER_STATE.translate(EnumColor.BRIGHT_GREEN, MekanismLang.MODULE_ENABLED_LOWER),
              MekanismLang.FILTER_STATE.translate(EnumColor.RED, MekanismLang.MODULE_DISABLED_LOWER)));
        setButtonBackground(ButtonBackground.NONE);
        playClickSound = true;
        // Replace the dummy click action from MekanismButton with filter-aware handling in mouseClicked.
        this.onPress = onPress;
    }

    private final ObjIntConsumer<IFilter> onPress;

    private boolean isEnabled() {
        IFilter filter = getFilter();
        return filter != null && filter.isEnabled();
    }

    protected int getToggleXShift() {
        return 4;
    }

    protected int getActualIndex() {
        return filterIndex.getAsInt() + index;
    }

    @Nullable
    protected IFilter getFilter() {
        return getFilter(filterManager, getActualIndex());
    }

    public FilterButton warning(@Nonnull mekanism.common.inventory.warning.WarningTracker.WarningType type, @Nonnull Predicate<IFilter> hasWarning) {
        slot.warning(type, () -> hasWarning.test(getFilter()));
        return this;
    }

    protected void setVisibility(boolean visible) {
        this.visible = visible;
        slot.visible = visible;
        slotDisplay.visible = visible;
        toggleButton.visible = visible;
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        setVisibility(getFilter() != null);
        super.render(mouseX, mouseY, partialTicks);
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        if (!visible) {
            return;
        }
        minecraft.renderEngine.bindTexture(TEXTURE);
        GuiUtils.blit(getButtonX(), getButtonY(), 0, isMouseOverCheckWindows(mouseX, mouseY) ? 0 : TEXTURE_HEIGHT / 2, getButtonWidth(), getButtonHeight(),
              TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        if (!visible) {
            return;
        }
        IFilter filter = getFilter();
        if (filter != prevFilter) {
            slotDisplay.updateStackList();
            prevFilter = filter;
        }
        String descriptor = getFilterDescriptor(filter);
        int textWidth = toggleButton.getRelativeX() - relativeX - 20;
        if (descriptor != null) {
            drawScaledScrollingString(new TextComponentString(descriptor), 19, 3, TextAlignment.LEFT, titleTextColor(), textWidth, 3, false, 1, GuiElement.getMillis());
        }
        String detail = getFilterDetail(filter);
        if (!detail.isEmpty()) {
            drawScaledScrollingString(new TextComponentString(detail), 19, 12, TextAlignment.LEFT, titleTextColor(), textWidth, 3, false, 0.7F, GuiElement.getMillis());
        }
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
        if (active && visible && button == 0 && clicked(mouseX, mouseY) && getFilter() != null) {
            onPress.accept(getFilter(), getActualIndex());
            playDownSound(minecraft.getSoundHandler());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Nullable
    private String getFilterDescriptor(@Nullable IFilter filter) {
        if (filter instanceof IItemStackFilter itemFilter) {
            ItemStack stack = itemFilter.getItemStack();
            return stack.isEmpty() ? MekanismLang.ITEM_FILTER.translate().getFormattedText() : stack.getDisplayName();
        } else if (filter instanceof IOreDictFilter oreFilter) {
            return oreFilter.getOreDictName();
        } else if (filter instanceof IModIDFilter modIDFilter) {
            return modIDFilter.getModID();
        } else if (filter instanceof IMaterialFilter materialFilter) {
            ItemStack stack = materialFilter.getMaterialItem();
            return stack.isEmpty() ? LangUtils.localize("gui.materialFilter") : stack.getDisplayName();
        }
        return null;
    }

    private String getFilterDetail(@Nullable IFilter filter) {
        if (filter instanceof TransporterFilter transporterFilter) {
            EnumColor color = transporterFilter.color;
            return color == null ? LangUtils.localize("gui.none") : color.getColoredName();
        } else if (filter instanceof IMaterialFilter) {
            return LangUtils.localize("gui.materialFilter");
        }
        return "";
    }
}
