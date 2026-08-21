package mekanism.qioprocessing.client.gui;

import mekanism.client.SpecialColors;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.tab.window.GuiWindowCreatorTab;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.MekanismUtils;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.MekanismQIOProcessing;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeConfigType;
import mekanism.qioprocessing.common.machine.QIOAutomationContainerState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** AE-style route-policy tab for one installed QIO automation mode. */
/**
 * QIO 处理模块中的 GuiQIOAutomationRecipeConfigTab 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class GuiQIOAutomationRecipeConfigTab extends
      GuiWindowCreatorTab<QIOAutomationContainerState, GuiQIOAutomationRecipeConfigTab> {

    private static final int TAB_ICON_SIZE = 14;
    private static final int ITEM_TEXTURE_SIZE = 16;

    private final TileEntityContainerBlock tile;
    private final MekanismTileContainer<?> container;
    private final QIOAutomationRecipeConfigType configType;
    private boolean windowOpen;

    public GuiQIOAutomationRecipeConfigTab(IGuiWrapper gui, TileEntityContainerBlock tile,
          MekanismTileContainer<?> container, QIOAutomationContainerState state,
          QIOAutomationRecipeConfigType configType,
          Supplier<GuiQIOAutomationRecipeConfigTab> elementSupplier) {
        super(new ResourceLocation(MekanismQIOProcessing.MODID,
                    "textures/items/" + configType.getItemTextureName() + ".png"),
              gui, state, gui.getWidth() + 48, 6, 26, 18, false, elementSupplier);
        this.tile = tile;
        this.container = container;
        this.configType = configType;
        visible = canOpen(tile, state, configType);
        active = visible;
    }

    public static boolean canOpen(@Nullable TileEntityContainerBlock tile,
          @Nullable QIOAutomationContainerState state,
          @Nonnull QIOAutomationRecipeConfigType type) {
        return type.isInstalledIn(tile) && state != null && state.getMode() == type.getMode() &&
              state.getState() != QIOAutomationHost.State.DRAINING_CHANGE &&
              state.getState() != QIOAutomationHost.State.IDENTITY_CONFLICT;
    }

    @Override
    protected void drawBackgroundOverlay() {
        int iconX = getButtonX() + (innerWidth - TAB_ICON_SIZE) / 2;
        int iconY = getButtonY() + (innerHeight - TAB_ICON_SIZE) / 2;
        MekanismRenderer.bindTexture(getOverlay());
        GuiUtils.blit(iconX, iconY, TAB_ICON_SIZE, TAB_ICON_SIZE, 0, 0,
              ITEM_TEXTURE_SIZE, ITEM_TEXTURE_SIZE, ITEM_TEXTURE_SIZE, ITEM_TEXTURE_SIZE);
        MekanismRenderer.resetColor();
    }

    @Override
    public void tick() {
        super.tick();
        visible = canOpen(tile, dataSource, configType);
        windowOpen = hasOpenWindow();
        active = visible && !windowOpen;
    }

    @Override
    public void openPinnedWindows() {
        if (canOpen(tile, dataSource, configType) && !hasOpenWindow()) {
            super.openPinnedWindows();
        }
    }

    @Override
    protected void disableTab() {
        windowOpen = true;
        super.disableTab();
    }

    @Override
    protected Consumer<GuiWindow> getCloseListener() {
        return window -> {
            GuiQIOAutomationRecipeConfigTab tab = getElementSupplier().get();
            tab.windowOpen = false;
            tab.active = tab.visible && canOpen(tab.tile, tab.dataSource, tab.configType);
        };
    }

    @Override
    protected Consumer<GuiWindow> getReAttachListener() {
        return window -> {
            GuiQIOAutomationRecipeConfigTab tab = getElementSupplier().get();
            tab.windowOpen = true;
            tab.disableTab();
        };
    }

    @Override
    protected GuiWindow createWindow(SelectedWindowData windowData) {
        return new GuiQIOAutomationRecipeConfigWindow(gui(),
              (getGuiWidth() - GuiQIOAutomationRecipeConfigWindow.WIDTH) / 2, 18,
              tile, container, dataSource, configType, windowData);
    }

    @Override
    protected SelectedWindowData getNextWindowData() {
        return new SelectedWindowData(configType.getWindowType());
    }

    @Override
    @Nullable
    protected Integer getTabColor() {
        return SpecialColors.TAB_CRAFTING_WINDOW.argb();
    }

    @Override
    @Nullable
    protected ITextComponent getTooltipText() {
        return new TextComponentTranslation(configType.getTitleKey());
    }

    private boolean hasOpenWindow() {
        return gui() instanceof GuiMekanism<?> mekanismGui && mekanismGui.getWindows().stream()
              .anyMatch(window -> window instanceof GuiQIOAutomationRecipeConfigWindow configWindow &&
                    configWindow.isFor(tile, configType));
    }
}
