package mekanism.client.gui.element.window.filter;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.common.tile.machine.TileEntityOredictionificator;
import mekanism.common.tile.machine.TileEntityOredictionificator.OredictionificatorFilter;
import mekanism.common.util.ItemRegistryUtils;
import mekanism.common.util.LangUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiOredictionificatorFilter extends GuiTextFilter<OredictionificatorFilter, TileEntityOredictionificator> {

    private static final int WIDTH = 162;
    private ItemStack renderStack;

    public static GuiOredictionificatorFilter create(IGuiWrapper gui, TileEntityOredictionificator tile) {
        return new GuiOredictionificatorFilter(gui, (gui.getWidth() - WIDTH) / 2, 15, tile, null);
    }

    public static GuiOredictionificatorFilter edit(IGuiWrapper gui, TileEntityOredictionificator tile, OredictionificatorFilter filter) {
        return new GuiOredictionificatorFilter(gui, (gui.getWidth() - WIDTH) / 2, 15, tile, filter);
    }

    private GuiOredictionificatorFilter(IGuiWrapper gui, int x, int y, TileEntityOredictionificator tile, @Nullable OredictionificatorFilter origFilter) {
        super(gui, x, y, WIDTH, 100, LangUtils.localize("gui.filter"), tile, origFilter);
        updateRenderStack();
        slotDisplay.updateStackList();
    }

    @Nullable
    @Override
    public GuiFilterSelect<TileEntityOredictionificator> getFilterSelect(IGuiWrapper gui, TileEntityOredictionificator tile) {
        return null;
    }

    @Override
    public boolean hasFilterSelect() {
        return false;
    }

    @Override
    protected int getScreenHeight() {
        return 52;
    }

    @Override
    protected int getTextFieldX() {
        return relativeX + 34;
    }

    @Override
    protected int getTextFieldWidth() {
        return 109;
    }

    @Override
    protected boolean isTextBackgroundEnabled() {
        return true;
    }

    @Override
    protected int getTextColor() {
        return 14737632;
    }

    @Override
    protected void init() {
        super.init();
        addChild(new MekanismImageButton(gui(), relativeX + 3, relativeY + 38, 12, getButtonLocation("left"), this::previousItem,
              getOnHover(() -> new TextComponentString(LangUtils.localize("gui.lastItem")))));
        addChild(new MekanismImageButton(gui(), relativeX + 16, relativeY + 38, 12, getButtonLocation("right"), this::nextItem,
              getOnHover(() -> new TextComponentString(LangUtils.localize("gui.nextItem")))));
    }

    @Override
    protected OredictionificatorFilter createNewFilter() {
        return new OredictionificatorFilter();
    }

    @Override
    protected OredictionificatorFilter cloneFilter(OredictionificatorFilter filter) {
        return filter.clone();
    }

    @Override
    protected boolean hasFilter() {
        return filter.filter != null && !filter.filter.isEmpty();
    }

    @Override
    protected String getNoFilterSaveError() {
        return LangUtils.localize("gui.oreDictCompat");
    }

    @Override
    protected boolean setText() {
        String newFilter = text.getText();
        if (TileEntityOredictionificator.possibleFilters.stream().anyMatch(newFilter::startsWith)) {
            filter.filter = newFilter;
            filter.index = 0;
            text.clear();
            updateRenderStack();
            slotDisplay.updateStackList();
            return true;
        } else {
            filterSaveFailed(getNoFilterSaveError());
            return false;
        }
    }

    @Override
    protected List<ITextComponent> getScreenText() {
        List<ITextComponent> list = super.getScreenText();
        list.add(new TextComponentString(LangUtils.localize("gui.index") + ": " + filter.index));
        if (filter.filter != null) {
            list.add(new TextComponentString(filter.filter));
        }
        return list;
    }

    @Nonnull
    @Override
    protected List<ItemStack> getRenderStacks() {
        return renderStack == null || renderStack.isEmpty() ? Collections.emptyList() : Collections.singletonList(renderStack);
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        if (isMouseOverStack(mouseX, mouseY) && !renderStack.isEmpty()) {
            String name = ItemRegistryUtils.getMod(renderStack);
            String extra = name.equals("null") ? "" : " (" + name + ")";
            displayTooltip(new TextComponentString(renderStack.getDisplayName() + extra), mouseX, mouseY);
        }
    }

    private boolean isMouseOverStack(int mouseX, int mouseY) {
        int xAxis = mouseX - getGuiLeft();
        int yAxis = mouseY - getGuiTop();
        int slotX = relativeX + getSlotOffsetX() + 1;
        int slotY = relativeY + getSlotOffsetY() + 1;
        return xAxis >= slotX && xAxis < slotX + 16 && yAxis >= slotY && yAxis < slotY + 16;
    }

    private void previousItem() {
        if (filter.filter != null) {
            List<ItemStack> ores = OreDictionary.getOres(filter.filter, false);
            if (!ores.isEmpty()) {
                filter.index = filter.index > 0 ? filter.index - 1 : ores.size() - 1;
                updateRenderStack();
                slotDisplay.updateStackList();
            }
        }
    }

    private void nextItem() {
        if (filter.filter != null) {
            List<ItemStack> ores = OreDictionary.getOres(filter.filter, false);
            if (!ores.isEmpty()) {
                filter.index = filter.index < ores.size() - 1 ? filter.index + 1 : 0;
                updateRenderStack();
                slotDisplay.updateStackList();
            }
        }
    }

    private void updateRenderStack() {
        if (filter.filter == null || filter.filter.isEmpty()) {
            renderStack = ItemStack.EMPTY;
            return;
        }
        List<ItemStack> stacks = OreDictionary.getOres(filter.filter, false);
        if (stacks.isEmpty() || stacks.size() - 1 < filter.index) {
            renderStack = ItemStack.EMPTY;
        } else {
            renderStack = stacks.get(filter.index).copy();
        }
    }
}
