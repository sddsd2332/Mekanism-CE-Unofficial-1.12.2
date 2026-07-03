package mekanism.client.gui.element.tab.window;

import mekanism.client.SpecialColors;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.window.GuiUpgradeWindow;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public class GuiUpgradeWindowTab extends GuiWindowCreatorTab<TileEntityContainerBlock, GuiUpgradeWindowTab> {

    private static final ResourceLocation UPGRADE = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "upgrade.png");
    private static final SelectedWindowData WINDOW_DATA = new SelectedWindowData(WindowType.UPGRADE);

    public GuiUpgradeWindowTab(IGuiWrapper gui, TileEntityContainerBlock tile, Supplier<GuiUpgradeWindowTab> elementSupplier) {
        super(UPGRADE, gui, tile, gui.getWidth(), 6, 26, 18, false, elementSupplier);
    }

    @Override
    @Nullable
    protected Integer getTabColor() {
        return SpecialColors.TAB_UPGRADE.argb();
    }

    @Override
    protected ITextComponent getTooltipText() {
        return new TextComponentString(LangUtils.localize("gui.upgrades"));
    }

    @Override
    protected GuiWindow createWindow(SelectedWindowData windowData) {
        return new GuiUpgradeWindow(gui(), (getGuiWidth() - 198) / 2, 15, dataSource, windowData);
    }

    @Override
    protected SelectedWindowData getNextWindowData() {
        return WINDOW_DATA;
    }
}
