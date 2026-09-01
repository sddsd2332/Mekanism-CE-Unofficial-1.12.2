package mekanism.client.gui.element.window;

import mekanism.api.EnumColor;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.button.TooltipToggleButton;
import mekanism.client.gui.element.button.TranslationButton;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.qio.QIOFilterGuiResource;
import mekanism.client.gui.qio.QIOResourceSelection;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget.IGhostIngredientConsumer;
import mekanism.common.MekanismLang;
import mekanism.common.content.qio.filter.QIOFilter;
import mekanism.common.content.qio.filter.QIOFilterResourceHelper;
import mekanism.common.content.qio.filter.QIOItemStackFilter;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.network.qio.PacketQIOComponentConfig;
import mekanism.common.tile.qio.TileEntityQIOFilterHandler;
import mekanism.common.util.LangUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Codec-aware exact-resource editor shared by QIO importers and exporters. */
public class GuiQIOResourceFilterWindow extends GuiWindow {

    private final TileEntityQIOFilterHandler tile;
    private final int index;
    @Nullable
    private QIOFilter filter;
    private final TooltipToggleButton fuzzyButton;
    private String status = EnumColor.DARK_GREEN +
          MekanismLang.STATUS_OK.translate().getFormattedText();
    private int statusTicks;

    public GuiQIOResourceFilterWindow(IGuiWrapper gui, TileEntityQIOFilterHandler tile) {
        this(gui, tile, null, -1);
    }

    public GuiQIOResourceFilterWindow(IGuiWrapper gui, TileEntityQIOFilterHandler tile,
          @Nullable QIOFilter filter, int index) {
        super(gui, (gui.getWidth() - 185) / 2, 15, 185, 100,
              SelectedWindowData.UNSPECIFIED);
        this.tile = tile;
        QIOFilter copied = filter == null ? null : filter.copy();
        this.filter = copied == null ? filter : copied;
        this.index = index;
        interactionStrategy = InteractionStrategy.CONTAINER;

        addChild(new GuiInnerScreen(gui, relativeX + 29, relativeY + 18, 116, 51,
              this::getScreenText).clearFormat());
        GuiSlot resourceSlot = addChild(new GuiSlot(SlotType.NORMAL, gui,
              relativeX + 7, relativeY + 18).setRenderHover(true)
              .click((element, mouseX, mouseY, button) -> updateFromCarried(button))
              .tooltip(this::getResourceTooltip));
        resourceSlot.setGhostHandler(createGhostHandler());
        addChild(new TranslationButton(gui, relativeX + 29, relativeY + 71, 60, 20,
              index < 0 ? MekanismLang.BUTTON_CANCEL : MekanismLang.BUTTON_DELETE,
              this::cancelOrDelete));
        addChild(new TranslationButton(gui, relativeX + 91, relativeY + 71, 60, 20,
              MekanismLang.BUTTON_SAVE, this::save));
        fuzzyButton = addChild(new TooltipToggleButton(gui, relativeX + 148,
              relativeY + 18, 11, getButtonLocation("fuzzy"), this::isFuzzy,
              this::toggleFuzzy, MekanismLang.FUZZY_MODE.translate(),
              MekanismLang.FUZZY_MODE.translate()));
        updateFuzzyVisibility();
        if (index < 0) {
            addChild(new MekanismImageButton(gui, relativeX + 6, relativeY + 6, 11, 14,
                  getButtonLocation("back"), () -> {
                      IGuiWrapper parent = gui();
                      parent.addWindow(new GuiQIOFilterSelectWindow(parent, tile));
                      close();
                  }).setTooltip(MekanismLang.BACK.translate()));
        } else {
            super.addCloseButton();
        }
    }

    @Override
    protected void addCloseButton() {
        // New filters use a back button; existing filters use the standard close button.
    }

    private boolean updateFromCarried(int button) {
        ItemStack carried = minecraft.player == null ? ItemStack.EMPTY :
              minecraft.player.inventory.getItemStack();
        if (carried.isEmpty()) {
            if (button == 0 && GuiScreen.isShiftKeyDown()) {
                filter = null;
                setStatusOk();
                updateFuzzyVisibility();
                return true;
            }
            return false;
        }
        if (GuiScreen.isShiftKeyDown()) {
            return false;
        }
        return applySelection(QIOResourceSelection.fromCarried(carried, button), true);
    }

    private boolean applySelection(QIOResourceSelection.Resolution selection, boolean reportInvalid) {
        QIOResourceDescriptor descriptor = selection.getDescriptor();
        if (descriptor == null) {
            if (selection.isAmbiguous()) {
                setAmbiguous();
                return true;
            }
            if (reportInvalid) {
                setInvalid();
            }
            return false;
        }
        boolean enabled = filter == null || filter.isEnabled();
        QIOFilter replacement = QIOFilterResourceHelper.createFilter(descriptor,
              isFuzzy());
        if (replacement == null) {
            setInvalid();
            return false;
        }
        replacement.setEnabled(enabled);
        filter = replacement;
        setStatusOk();
        updateFuzzyVisibility();
        return true;
    }

    private IGhostIngredientConsumer createGhostHandler() {
        return new IGhostIngredientConsumer() {
            @Override
            public Object supportedTarget(Object ingredient) {
                return QIOResourceSelection.fromIngredient(ingredient).isUnique() ? ingredient : null;
            }

            @Override
            public void accept(Object ingredient) {
                if (applySelection(QIOResourceSelection.fromIngredient(ingredient), true)) {
                    playClickSound();
                }
            }
        };
    }

    private List<ITextComponent> getScreenText() {
        List<ITextComponent> text = new ArrayList<>(4);
        text.add(new TextComponentTranslation("gui.qio.filter.status", status));
        text.add(new TextComponentString(QIOFilterGuiResource.name(filter)));
        text.add(new TextComponentString(QIOFilterGuiResource.typeName(filter)));
        if (isItemResource()) {
            text.add(MekanismLang.QIO_FUZZY_MODE.translate(isFuzzy() ?
                  MekanismLang.ON.translate() : MekanismLang.OFF.translate()));
        }
        return text;
    }

    private List<String> getResourceTooltip() {
        return QIOFilterGuiResource.tooltip(filter);
    }

    private void cancelOrDelete() {
        if (index >= 0) {
            PacketQIOComponentConfig.removeFilter(tile, index);
        }
        close();
    }

    private void save() {
        if (filter == null || !filter.hasFilter()) {
            setInvalid();
            return;
        }
        if (index < 0) {
            PacketQIOComponentConfig.addFilter(tile, filter);
        } else {
            PacketQIOComponentConfig.editFilter(tile, index, filter);
        }
        close();
    }

    private boolean isItemResource() {
        QIOResourceDescriptor descriptor = QIOFilterGuiResource.descriptor(filter);
        return descriptor != null &&
              descriptor.getCodecId().equals(mekanism.api.qio.resource.QIOResourceCodecs.ITEM_STACK_ID);
    }

    private boolean isFuzzy() {
        return filter instanceof QIOItemStackFilter &&
              ((QIOItemStackFilter) filter).isFuzzyMode();
    }

    private void toggleFuzzy() {
        QIOResourceDescriptor descriptor = QIOFilterGuiResource.descriptor(filter);
        if (descriptor == null || !isItemResource()) {
            return;
        }
        boolean enabled = filter == null || filter.isEnabled();
        QIOFilter replacement = QIOFilterResourceHelper.createFilter(descriptor, !isFuzzy());
        if (replacement != null) {
            replacement.setEnabled(enabled);
            filter = replacement;
            setStatusOk();
        }
    }

    private void updateFuzzyVisibility() {
        fuzzyButton.visible = isItemResource();
    }

    private void setStatusOk() {
        status = EnumColor.DARK_GREEN + MekanismLang.STATUS_OK.translate().getFormattedText();
        statusTicks = 0;
    }

    private void setInvalid() {
        status = EnumColor.DARK_RED + LangUtils.localize("gui.qio.filter.invalid_resource");
        statusTicks = 100;
    }

    private void setAmbiguous() {
        status = EnumColor.DARK_RED + LangUtils.localize("gui.qio.filter.ambiguous_resource");
        statusTicks = 100;
    }

    @Override
    public void tick() {
        super.tick();
        updateFuzzyVisibility();
        if (statusTicks > 0 && --statusTicks == 0) {
            setStatusOk();
        }
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        QIOFilterGuiResource.render(gui(), filter, relativeX + 8, relativeY + 19, 16);
        drawTitleText(new TextComponentTranslation(index < 0 ?
              "gui.qio.filter.new" : "gui.qio.filter.edit"), 6);
    }
}
