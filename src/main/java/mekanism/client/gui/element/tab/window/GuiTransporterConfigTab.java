package mekanism.client.gui.element.tab.window;

import mekanism.client.SpecialColors;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.window.GuiTransporterConfig;
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

public class GuiTransporterConfigTab<TILE extends TileEntityContainerBlock & ISideConfiguration> extends GuiWindowCreatorTab<TILE, GuiTransporterConfigTab<TILE>> {

    private static final ResourceLocation TRANSPORTER_CONFIG = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "transporter_config.png");
    private static final SelectedWindowData WINDOW_DATA = new SelectedWindowData(WindowType.TRANSPORTER_CONFIG);

    public GuiTransporterConfigTab(IGuiWrapper gui, TILE tile, Supplier<GuiTransporterConfigTab<TILE>> elementSupplier) {
        super(TRANSPORTER_CONFIG, gui, tile, -26, 34, 26, 18, true, elementSupplier);
    }

    @Override
    @Nullable
    protected Integer getTabColor() {
        return SpecialColors.TAB_TRANSPORTER.argb();
    }

    @Override
    protected ITextComponent getTooltipText() {
        return new TextComponentString(LangUtils.localize("gui.configuration.transporter"));
    }

    @Override
    protected GuiWindow createWindow(SelectedWindowData windowData) {
        return new GuiTransporterConfig<>(gui(), (getGuiWidth() - 156) / 2, 15, dataSource, windowData);
    }

    @Override
    protected SelectedWindowData getNextWindowData() {
        return WINDOW_DATA;
    }
}
