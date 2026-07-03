package mekanism.client.gui.element.window.filter;

import mekanism.client.gui.IGuiWrapper;
import mekanism.common.content.filter.IFilter;
import mekanism.common.tile.interfaces.ITileFilterHolder;
import mekanism.common.tile.prefab.TileEntityContainerBlock;

import javax.annotation.Nullable;

public interface GuiFilterHelper<TILE extends TileEntityContainerBlock & ITileFilterHolder<?>> {

    @Nullable
    GuiFilterSelect<TILE> getFilterSelect(IGuiWrapper gui, TILE tile);

    default boolean hasFilterSelect() {
        return true;
    }

    int getRelativeX();

    int getRelativeY();

    IFilter getFilter();

    int getScreenWidth();
}
