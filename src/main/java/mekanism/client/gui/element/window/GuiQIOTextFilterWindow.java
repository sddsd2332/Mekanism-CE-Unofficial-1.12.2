package mekanism.client.gui.element.window;

import mekanism.api.EnumColor;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.button.TranslationButton;
import mekanism.client.gui.element.slot.GuiSequencedSlotDisplay;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget.IGhostIngredientConsumer;
import mekanism.common.MekanismLang;
import mekanism.common.OreDictCache;
import mekanism.common.content.qio.filter.QIOFilter;
import mekanism.common.content.qio.filter.QIOModIDFilter;
import mekanism.common.content.qio.filter.QIOOreDictFilter;
import mekanism.common.content.transporter.TransporterFilter;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.network.qio.PacketQIOComponentConfig;
import mekanism.common.tile.qio.TileEntityQIOFilterHandler;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** 26.2-style text editor for the 1.12 Ore Dictionary and Mod ID QIO filters. */
public class GuiQIOTextFilterWindow extends GuiWindow {

    private final TileEntityQIOFilterHandler tile;
    private final int index;
    private final QIOFilter filter;
    private final boolean oreDictionary;
    private final GuiTextField textField;
    private final GuiSequencedSlotDisplay slotDisplay;
    private String status = EnumColor.DARK_GREEN + MekanismLang.STATUS_OK.translate().getFormattedText();
    private int statusTicks;
    private List<ItemStack> previewStacks = Collections.emptyList();

    public GuiQIOTextFilterWindow(IGuiWrapper gui, TileEntityQIOFilterHandler tile, QIOFilter filter, int index) {
        super(gui, (gui.getWidth() - 152) / 2, 15, 152, 90, SelectedWindowData.UNSPECIFIED);
        if (!(filter instanceof QIOOreDictFilter) && !(filter instanceof QIOModIDFilter)) {
            throw new IllegalArgumentException("Text QIO filter window requires an Ore Dictionary or Mod ID filter");
        }
        this.tile = tile;
        this.index = index;
        QIOFilter copy = filter.copy();
        this.filter = copy == null ? filter : copy;
        oreDictionary = this.filter instanceof QIOOreDictFilter;
        interactionStrategy = InteractionStrategy.CONTAINER;

        addChild(new GuiInnerScreen(gui, relativeX + 29, relativeY + 18, 116, 42, this::getScreenText).clearFormat());
        GuiSlot slot = addChild(new GuiSlot(SlotType.NORMAL, gui, relativeX + 7, relativeY + 18)
              .setRenderHover(true).click((element, mouseX, mouseY) -> updateFromCarried()));
        slot.setGhostHandler(createGhostHandler());
        slotDisplay = addChild(new GuiSequencedSlotDisplay(gui, relativeX + 8, relativeY + 19, () -> previewStacks));
        textField = addChild(new GuiTextField(gui, this, relativeX + 31, relativeY + 46, 112, 12)
              .setMaxLength(TransporterFilter.MAX_LENGTH)
              .setInputValidator(c -> Character.isLetterOrDigit(c) || TransporterFilter.SPECIAL_CHARS.contains(c))
              .setInputTransformer(c -> c >= 'A' && c <= 'Z' ? Character.toLowerCase(c) : c)
              .configureDigitalInput(this::applyText)
              .setEditable(true));
        setFocusedChild(textField);
        addChild(new TranslationButton(gui, relativeX + 15, relativeY + 62, 60, 20,
              index < 0 ? MekanismLang.BUTTON_CANCEL : MekanismLang.BUTTON_DELETE, this::cancelOrDelete));
        addChild(new TranslationButton(gui, relativeX + 77, relativeY + 62, 60, 20, MekanismLang.BUTTON_SAVE, this::save));
        if (index < 0) {
            addChild(new MekanismImageButton(gui, relativeX + 6, relativeY + 6, 11, 14, getButtonLocation("back"), this::goBack)
                  .setTooltip(MekanismLang.BACK.translate()));
        } else {
            super.addCloseButton();
        }
        updatePreview();
    }

    @Override
    protected void addCloseButton() {
        // The constructor chooses between a back button for new filters and a close button for edits.
    }

    private boolean updateFromCarried() {
        ItemStack carried = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.inventory.getItemStack();
        if (carried.isEmpty()) {
            return false;
        }
        String value;
        if (oreDictionary) {
            List<String> names = OreDictCache.getOreDictName(carried);
            if (names.isEmpty()) {
                setInvalid(MekanismLang.TEXT_FILTER_NO_MATCHES.translate().getFormattedText());
                return false;
            }
            value = names.get(0);
        } else {
            value = MekanismUtils.getModId(carried);
            if (value.isEmpty()) {
                setInvalid(MekanismLang.TEXT_FILTER_NO_MATCHES.translate().getFormattedText());
                return false;
            }
        }
        textField.setTextSilently(value);
        return applyText();
    }

    private boolean applyText() {
        String value = textField.getText().trim();
        if (value.isEmpty()) {
            setInvalid(oreDictionary ? MekanismLang.TAG_FILTER_NO_TAG.translate().getFormattedText() :
                  MekanismLang.MODID_FILTER_NO_ID.translate().getFormattedText());
            return false;
        }
        List<ItemStack> matches = oreDictionary ? OreDictCache.getOreDictStacks(value, false) : OreDictCache.getQIOModIDStacks(value);
        if (matches.isEmpty()) {
            setInvalid(MekanismLang.TEXT_FILTER_NO_MATCHES.translate().getFormattedText());
            return false;
        }
        if (oreDictionary) {
            ((QIOOreDictFilter) filter).setOreDictName(value);
        } else {
            ((QIOModIDFilter) filter).setModID(value);
        }
        textField.clear();
        setStatusOk();
        updatePreview();
        return true;
    }

    private void setInvalid(String reason) {
        status = EnumColor.DARK_RED + reason;
        statusTicks = 100;
    }

    private void setStatusOk() {
        status = EnumColor.DARK_GREEN + MekanismLang.STATUS_OK.translate().getFormattedText();
        statusTicks = 0;
    }

    private void updatePreview() {
        String value = getFilterText();
        previewStacks = value.isEmpty() ? Collections.emptyList() : oreDictionary ?
              OreDictCache.getOreDictStacks(value, false) : OreDictCache.getQIOModIDStacks(value);
        slotDisplay.updateStackList();
    }

    private List<ITextComponent> getScreenText() {
        List<ITextComponent> text = new ArrayList<>(2);
        text.add(new TextComponentTranslation("gui.qio.filter.status", status));
        text.add(oreDictionary ? MekanismLang.TAG_FILTER_TAG.translate(getFilterText()) : MekanismLang.MODID_FILTER_ID.translate(getFilterText()));
        return text;
    }

    private String getFilterText() {
        return oreDictionary ? ((QIOOreDictFilter) filter).getOreDictName() : ((QIOModIDFilter) filter).getModID();
    }

    private IGhostIngredientConsumer createGhostHandler() {
        return new IGhostIngredientConsumer() {
            @Override
            @Nullable
            public Object supportedTarget(Object ingredient) {
                if (!(ingredient instanceof ItemStack) || ((ItemStack) ingredient).isEmpty()) {
                    return null;
                }
                ItemStack stack = (ItemStack) ingredient;
                if (oreDictionary) {
                    List<String> names = OreDictCache.getOreDictName(stack);
                    return names.isEmpty() ? null : names.get(0);
                }
                String mod = MekanismUtils.getModId(stack);
                return mod.isEmpty() ? null : mod;
            }

            @Override
            public void accept(Object ingredient) {
                if (ingredient instanceof String) {
                    textField.setTextSilently(((String) ingredient).toLowerCase(Locale.ROOT));
                    if (applyText()) {
                        playClickSound();
                    }
                }
            }
        };
    }

    private void cancelOrDelete() {
        if (index >= 0) {
            PacketQIOComponentConfig.removeFilter(tile, index);
        }
        close();
    }

    private void save() {
        if (!textField.isEmpty() && !applyText()) {
            return;
        }
        if (!filter.hasFilter()) {
            setInvalid(LangUtils.localize("gui.qio.filter.invalid_resource"));
            return;
        }
        if (index < 0) {
            PacketQIOComponentConfig.addFilter(tile, filter);
        } else {
            PacketQIOComponentConfig.editFilter(tile, index, filter);
        }
        close();
    }

    private void goBack() {
        IGuiWrapper parent = gui();
        parent.addWindow(new GuiQIOFilterSelectWindow(parent, tile));
        close();
    }

    @Override
    public void tick() {
        super.tick();
        if (statusTicks > 0 && --statusTicks == 0) {
            setStatusOk();
        }
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawTitleText(new TextComponentString((index < 0 ? MekanismLang.FILTER_NEW : MekanismLang.FILTER_EDIT)
              .translate(oreDictionary ? MekanismLang.TAG_FILTER.translate() : MekanismLang.MODID_FILTER.translate()).getFormattedText()), 6);
    }
}
