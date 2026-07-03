package mekanism.client.gui.element.tab.window;

import mekanism.client.SpecialColors;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.window.GuiSideConfiguration;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.common.base.ISideConfiguration;
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

public class GuiSideConfigurationTab<TILE extends TileEntityContainerBlock & ISideConfiguration> extends GuiWindowCreatorTab<TILE, GuiSideConfigurationTab<TILE>> {

    private static final ResourceLocation CONFIGURATION = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "configuration.png");
    private static final SelectedWindowData WINDOW_DATA = new SelectedWindowData(WindowType.SIDE_CONFIG);

    public GuiSideConfigurationTab(IGuiWrapper gui, TILE tile, Supplier<GuiSideConfigurationTab<TILE>> elementSupplier) {
        super(CONFIGURATION, gui, tile, -26, 6, 26, 18, true, elementSupplier);
    }

    @Override
    @Nullable
    protected Integer getTabColor() {
        return SpecialColors.TAB_CONFIGURATION.argb();
    }

    @Override
    protected ITextComponent getTooltipText() {
        return new TextComponentString(LangUtils.localize("gui.configuration.side"));
    }

    @Override
    protected GuiWindow createWindow(SelectedWindowData windowData) {
        return new GuiSideConfiguration<>(gui(), (getGuiWidth() - 156) / 2, 15, dataSource, windowData);
    }

    @Override
    protected SelectedWindowData getNextWindowData() {
        return WINDOW_DATA;
    }
}
