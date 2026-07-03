package mekanism.client.gui.element.window.filter;

import mekanism.api.Coord4D;
import mekanism.api.EnumColor;
import mekanism.api.text.ILangEntry;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.button.TranslationButton;
import mekanism.client.gui.element.slot.GuiSequencedSlotDisplay;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget.IGhostIngredientConsumer;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.content.filter.IFilter;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.network.PacketEditFilter.EditFilterMessage;
import mekanism.common.network.PacketNewFilter.NewFilterMessage;
import mekanism.common.tile.interfaces.ITileFilterHolder;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.LangUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SideOnly(Side.CLIENT)
public abstract class GuiFilter<FILTER extends IFilter, TILE extends TileEntityContainerBlock & ITileFilterHolder<?>> extends GuiWindow implements GuiFilterHelper<TILE> {

    protected static final int TEXT_COLOR = 0x404040;
    protected static final int LEFT_SLOT_X = 7;

    protected final TILE tile;
    protected final FILTER filter;
    @Nullable
    protected final FILTER origFilter;
    protected final boolean isNew;

    protected String status = EnumColor.DARK_GREEN + MekanismLang.STATUS_OK.translate().getFormattedText();
    protected GuiSequencedSlotDisplay slotDisplay;
    private final String filterName;
    private int ticker;

    protected GuiFilter(IGuiWrapper gui, int x, int y, int width, int height, String filterName, TILE tile, @Nullable FILTER origFilter) {
        super(gui, x, y, width, height, SelectedWindowData.UNSPECIFIED);
        this.tile = tile;
        this.filterName = filterName;
        this.origFilter = origFilter;
        this.isNew = origFilter == null;
        this.filter = isNew ? createNewFilter() : cloneFilter(origFilter);
        init();
        if (hasFilter()) {
            slotDisplay.updateStackList();
        }
        if (isNew && hasFilterSelect()) {
            addChild(new MekanismImageButton(gui, relativeX + 6, relativeY + 6, 11, 14, getButtonLocation("back"), () -> {
                IGuiWrapper wrapper = gui();
                GuiFilterSelect<TILE> filterSelect = getFilterSelect(wrapper, tile);
                if (filterSelect != null) {
                    wrapper.addWindow(filterSelect);
                }
                close();
            }).setTooltip(MekanismLang.BACK.translate()));
        } else {
            super.addCloseButton();
        }
    }

    public FILTER getFilter() {
        return filter;
    }

    @Override
    protected void addCloseButton() {
        // Delay adding the close button until subclass fields are initialized and init() has run.
    }

    @Override
    protected int getTitlePadStart() {
        return isNew && hasFilterSelect() ? super.getTitlePadStart() + 3 : super.getTitlePadStart();
    }

    protected void init() {
        int screenTop = relativeY + 18;
        int screenBottom = screenTop + getScreenHeight();
        addChild(new GuiInnerScreen(gui(), relativeX + 29, screenTop, getScreenWidth(), getScreenHeight(), this::getScreenText).clearFormat());
        addChild(new TranslationButton(gui(), getLeftButtonX(), screenBottom + 2, 60, 20,
              isNew ? MekanismLang.BUTTON_CANCEL : MekanismLang.BUTTON_DELETE, this::deleteFilter));
        addChild(new TranslationButton(gui(), getLeftButtonX() + 62, screenBottom + 2, 60, 20, MekanismLang.BUTTON_SAVE, this::validateAndSave));
        GuiSlot slot = addChild(new GuiSlot(SlotType.NORMAL, gui(), relativeX + getSlotOffsetX(), relativeY + getSlotOffsetY())
              .setRenderHover(true).setGhostHandler(getGhostHandler()));
        IClickable slotClickHandler = getSlotClickHandler();
        if (slotClickHandler != null) {
            slot.click(slotClickHandler);
        }
        slotDisplay = addChild(new GuiSequencedSlotDisplay(gui(), relativeX + getSlotOffsetX() + 1, relativeY + getSlotOffsetY() + 1, this::getRenderStacks));
    }

    @Nullable
    protected IClickable getSlotClickHandler() {
        return null;
    }

    @Nullable
    protected IGhostIngredientConsumer getGhostHandler() {
        return null;
    }

    protected int getScreenHeight() {
        return 42;
    }

    @Override
    public int getScreenWidth() {
        return 116;
    }

    protected int getSlotOffsetX() {
        return LEFT_SLOT_X;
    }

    protected int getSlotOffsetY() {
        return 18;
    }

    protected int getLeftButtonX() {
        return relativeX + width / 2 - 61;
    }

    protected List<ITextComponent> getScreenText() {
        List<ITextComponent> list = new ArrayList<>();
        list.add(MekanismLang.STATUS.translate(status));
        return list;
    }

    protected void validateAndSave() {
        if (hasFilter()) {
            saveFilter();
        } else {
            filterSaveFailed(getNoFilterSaveError());
        }
    }

    protected void filterSaveFailed(String reason) {
        status = EnumColor.DARK_RED + reason;
        ticker = 100;
    }

    protected void filterSaveFailed(ILangEntry reason, Object... args) {
        filterSaveFailed(reason.translate(args).getFormattedText());
    }

    protected void filterSaveSuccess() {
        status = EnumColor.DARK_GREEN + MekanismLang.STATUS_OK.translate().getFormattedText();
        ticker = 0;
    }

    protected void saveFilter() {
        if (isNew) {
            Mekanism.packetHandler.sendToServer(new NewFilterMessage(Coord4D.get(tile), filter));
        } else {
            Mekanism.packetHandler.sendToServer(new EditFilterMessage(Coord4D.get(tile), false, origFilter, filter));
        }
        close();
    }

    protected void deleteFilter() {
        if (!isNew) {
            Mekanism.packetHandler.sendToServer(new EditFilterMessage(Coord4D.get(tile), true, origFilter, null));
        }
        close();
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawTitleText((isNew ? MekanismLang.FILTER_NEW : MekanismLang.FILTER_EDIT).translate(filterName), 6);
    }

    @Override
    public void tick() {
        super.tick();
        if (ticker > 0) {
            ticker--;
        } else {
            status = EnumColor.DARK_GREEN + MekanismLang.STATUS_OK.translate().getFormattedText();
        }
    }

    protected abstract FILTER createNewFilter();

    protected abstract FILTER cloneFilter(FILTER filter);

    protected abstract boolean hasFilter();

    protected abstract String getNoFilterSaveError();

    @Nonnull
    protected List<ItemStack> getRenderStacks() {
        return Collections.emptyList();
    }
}
