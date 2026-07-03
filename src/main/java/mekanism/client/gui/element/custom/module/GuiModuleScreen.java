package mekanism.client.gui.element.custom.module;

import mekanism.api.gear.IModule;
import mekanism.api.gear.ModuleData;
import mekanism.api.gear.ModuleData.ExclusiveFlag;
import mekanism.api.gear.config.ModuleBooleanData;
import mekanism.api.gear.config.ModuleColorData;
import mekanism.api.gear.config.ModuleConfigData;
import mekanism.api.gear.config.ModuleEnumData;
import mekanism.api.text.IHasTextComponent;
import mekanism.api.text.TextComponentGroup;
import mekanism.client.gui.GuiModuleTweaker;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.scroll.GuiScrollList;
import mekanism.client.gui.element.scroll.GuiScrollableElement;
import mekanism.common.MekanismLang;
import mekanism.common.content.gear.Module;
import mekanism.common.content.gear.ModuleConfigItem;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

//TODO: Eventually try to add support for defining ways to render custom config types
public class GuiModuleScreen extends GuiScrollableElement {

    private static final int ELEMENT_SPACER = 4;

    final BiConsumer<Integer, ModuleConfigData<?>> saveCallback;
    private final Supplier<ItemStack> itemSupplier;
    @Nullable
    private final GuiModuleTweaker.ArmorPreview armorPreview;

    @Nullable
    private IModule<?> currentModule;
    private List<MiniElement> miniElements = new ArrayList<>();
    @Nullable
    private SelectedColorConfig selectedColorConfig;
    private int maxElements;

    public GuiModuleScreen(IGuiWrapper gui, int x, int y, Supplier<ItemStack> itemSupplier, BiConsumer<Integer, ModuleConfigData<?>> saveCallback,
          @Nullable GuiModuleTweaker.ArmorPreview armorPreview) {
        this(gui, x, y, 108, 134, itemSupplier, saveCallback, armorPreview);
    }

    private GuiModuleScreen(IGuiWrapper gui, int x, int y, int width, int height, Supplier<ItemStack> itemSupplier,
          BiConsumer<Integer, ModuleConfigData<?>> saveCallback, @Nullable GuiModuleTweaker.ArmorPreview armorPreview) {
        super(GuiScrollList.SCROLL_LIST, gui, x, y, width, height, width - 6, 2, 4, 4, height - 4);
        this.itemSupplier = itemSupplier;
        this.saveCallback = saveCallback;
        this.armorPreview = armorPreview;
    }

    @Nullable
    public IModule<?> getCurrentModule() {
        return currentModule;
    }

    public ItemStack getContainerStack() {
        return itemSupplier.get();
    }

    @Nullable
    GuiModuleTweaker.ArmorPreview getArmorPreview() {
        return armorPreview;
    }

    @Nullable
    public SelectedColorConfig getSelectedColorConfig() {
        return selectedColorConfig;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void setModule(@Nullable Module<?> module) {
        List<MiniElement> newElements = new ArrayList<>();
        SelectedColorConfig previousColor = currentModule != null && module != null && currentModule.getData() == module.getData() ? selectedColorConfig : null;
        SelectedColorConfig firstColor = null;
        SelectedColorConfig selectedColor = null;

        if (module != null) {
            int startY = getStartY(module);
            List<ModuleConfigItem<?>> configItems = module.getConfigItems();
            for (int i = 0, configItemsCount = configItems.size(); i < configItemsCount; i++) {
                ModuleConfigItem<?> configItem = configItems.get(i);
                ModuleConfigData<?> configData = configItem.getData();
                MiniElement element = null;
                if (configData instanceof ModuleBooleanData && (!configItem.getName().equals(Module.ENABLED_KEY) || !module.getData().isNoDisable())) {
                    if (configItem instanceof ModuleConfigItem.DisableableModuleConfigItem &&
                          !((ModuleConfigItem.DisableableModuleConfigItem) configItem).isConfigEnabled()) {
                        continue;
                    }
                    element = new BooleanToggle(this, (ModuleConfigItem<Boolean>) configItem, 2, startY, i);
                } else if (configData instanceof ModuleEnumData) {
                    EnumToggle toggle = new EnumToggle(this, (ModuleConfigItem<Enum<? extends IHasTextComponent>>) configItem, 2, startY, i);
                    element = toggle;
                    if (currentModule != null && currentModule.getData() == module.getData() && i < miniElements.size() && miniElements.get(i) instanceof EnumToggle) {
                        toggle.dragging = ((EnumToggle) miniElements.get(i)).dragging;
                    }
                } else if (configData instanceof ModuleColorData data) {
                    final int dataIndex = i;
                    SelectedColorConfig colorConfig = new SelectedColorConfig((ModuleConfigItem<Integer>) configItem, i, data.handlesAlpha(),
                          () -> saveCallback.accept(dataIndex, configItem.getData()));
                    if (firstColor == null) {
                        firstColor = colorConfig;
                    }
                    if (previousColor != null && previousColor.matches(configItem, i)) {
                        selectedColor = colorConfig;
                    }
                    element = new ColorSelection(this, colorConfig, 2, startY, i);
                }
                if (element != null) {
                    newElements.add(element);
                    startY += element.getNeededHeight() + ELEMENT_SPACER;
                }
            }
            maxElements = newElements.isEmpty() ? startY : startY - ELEMENT_SPACER;
        } else {
            maxElements = 0;
        }

        currentModule = module;
        miniElements = newElements;
        selectedColorConfig = selectedColor == null ? firstColor : selectedColor;
    }

    private static int getStartY(@Nullable IModule<?> module) {
        if (module == null) {
            return ELEMENT_SPACER + 1;
        }
        return getStartY(module.getData());
    }

    private static int getStartY(ModuleData<?> data) {
        int startY = ELEMENT_SPACER + 1;
        if (data.isExclusive(ExclusiveFlag.ANY)) {
            startY += 13;
        }
        if (data.getMaxStackSize() > 1) {
            startY += 13;
        }
        return startY;
    }

    @Override
    protected int getMaxElements() {
        return maxElements;
    }

    @Override
    protected int getFocusedElements() {
        return height - 2;
    }

    @Override
    protected int getScrollElementScaler() {
        return 10;
    }

    int getScreenWidth() {
        return barXShift;
    }

    @Override
    public void syncFrom(GuiElement element) {
        GuiModuleScreen old = (GuiModuleScreen) element;
        setModule((Module<?>) old.currentModule);
        super.syncFrom(element);
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        renderBackgroundTexture(GuiInnerScreen.SCREEN, GuiInnerScreen.SCREEN_SIZE, GuiInnerScreen.SCREEN_SIZE);
        drawScrollBar(GuiScrollList.TEXTURE_WIDTH, GuiScrollList.TEXTURE_HEIGHT);
        scissorScreen(mouseX, mouseY, (module, shift) -> getStartY(module), MiniElement::renderBackground);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX, mouseY);
        mouseY += getCurrentSelection();
        for (MiniElement element : miniElements) {
            element.click(mouseX, mouseY);
        }
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease(mouseX, mouseY);
        mouseY += getCurrentSelection();
        for (MiniElement element : miniElements) {
            element.release(mouseX, mouseY);
        }
    }

    @Override
    public void onDrag(double mouseX, double mouseY, double mouseXOld, double mouseYOld) {
        super.onDrag(mouseX, mouseY, mouseXOld, mouseYOld);
        mouseY += getCurrentSelection();
        for (MiniElement element : miniElements) {
            element.onDrag(mouseX, mouseY, mouseXOld, mouseYOld);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return isMouseOver(mouseX, mouseY) && adjustScroll(delta) || super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        scissorScreen(mouseX, mouseY, (module, shift) -> {
            int startY = ELEMENT_SPACER + 1;
            if (module != null) {
                if (module.getData().isExclusive(ExclusiveFlag.ANY)) {
                    if (startY + 13 > shift) {
                        drawScaledScrollingString(MekanismLang.MODULE_EXCLUSIVE.translate(), 2, startY, TextAlignment.LEFT, 0x635BD4,
                              getScreenWidth() - GuiScrollList.TEXTURE_WIDTH, 2, false, 0.8F, GuiElement.getMillis());
                    }
                    startY += 13;
                }
                if (module.getData().getMaxStackSize() > 1) {
                    if (startY + 13 > shift) {
                        drawScaledScrollingString(new TextComponentGroup().translation(MekanismLang.MODULE_INSTALLED.getTranslationKey())
                                      .translation(module.getInstalledCount() + ""), 2, startY, TextAlignment.LEFT, screenTextColor(),
                              getScreenWidth() - GuiScrollList.TEXTURE_WIDTH, 2, false, 0.8F, GuiElement.getMillis());
                    }
                    startY += 13;
                }
            }
            return startY;
        }, MiniElement::renderForeground);
    }

    private void scissorScreen(int mouseX, int mouseY, ScissorRender renderer, ScissorMiniElementRender miniElementRender) {
        enableScissor();
        GlStateManager.pushMatrix();
        int shift = getCurrentSelection();
        GlStateManager.translate(0, -shift, 0);
        mouseY += shift;

        int startY = renderer.render(currentModule, shift);
        for (MiniElement element : miniElements) {
            if (startY >= shift + height) {
                break;
            } else if (startY + element.getNeededHeight() > shift) {
                miniElementRender.render(element, mouseX, mouseY);
            }
            startY += element.getNeededHeight() + ELEMENT_SPACER;
        }

        GlStateManager.popMatrix();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void enableScissor() {
        double scaleY = minecraft.displayHeight / (double) minecraft.currentScreen.height;
        int scissorX = 0;
        int scissorY = (int) (minecraft.displayHeight - (getGuiTop() + relativeY + height - 1) * scaleY);
        int scissorWidth = minecraft.displayWidth;
        int scissorHeight = (int) ((height - 2) * scaleY);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
    }

    public static class SelectedColorConfig {

        private final ModuleConfigItem<Integer> data;
        private final int dataIndex;
        private final boolean handlesAlpha;
        private final Runnable saveCallback;

        private SelectedColorConfig(ModuleConfigItem<Integer> data, int dataIndex, boolean handlesAlpha, Runnable saveCallback) {
            this.data = data;
            this.dataIndex = dataIndex;
            this.handlesAlpha = handlesAlpha;
            this.saveCallback = saveCallback;
        }

        public ITextComponent getDescription() {
            return data.getDescription();
        }

        public int getColor() {
            return data.get();
        }

        public void setColor(int color) {
            data.getData().set(color);
        }

        public boolean handlesAlpha() {
            return handlesAlpha;
        }

        public void save() {
            data.set(data.get(), saveCallback);
        }

        private boolean matches(ModuleConfigItem<?> otherData, int otherDataIndex) {
            return dataIndex == otherDataIndex && data.getName().equals(otherData.getName());
        }
    }

    @FunctionalInterface
    private interface ScissorRender {

        int render(@Nullable IModule<?> module, int shift);
    }

    @FunctionalInterface
    private interface ScissorMiniElementRender {

        void render(MiniElement element, int mouseX, int mouseY);
    }
}
