package mekanism.client.gui.element.scroll;

import mekanism.api.EnumColor;
import mekanism.api.gear.IModule;
import mekanism.api.gear.ModuleData;
import mekanism.api.gear.ModuleData.ExclusiveFlag;
import mekanism.api.text.TextComponentGroup;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.common.MekanismLang;
import mekanism.common.content.gear.Module;
import mekanism.common.content.gear.ModuleHelper;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GuiModuleScrollList extends GuiInstallableScrollList<ModuleData<?>> {

    private static final ResourceLocation MODULE_SELECTION = MekanismUtils.getResource(ResourceType.GUI, "module_selection.png");
    public static final ResourceLocation HOLDER = MekanismUtils.getResource(ResourceType.GUI, "element_holder.png");
    private static final int TEXTURE_WIDTH = 112;
    private static final int TEXTURE_HEIGHT = 36;

    private final Consumer<Module<?>> callback;
    private final List<ModuleData<?>> currentList = new ArrayList<>();
    private final Supplier<ItemStack> itemSupplier;
    private ItemStack currentItem;

    public GuiModuleScrollList(IGuiWrapper gui, int x, int y, int width, int height, Supplier<ItemStack> itemSupplier, Consumer<Module<?>> callback) {
        super(gui, x, y, width, height, HOLDER, 32, MODULE_SELECTION, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        this.itemSupplier = itemSupplier;
        this.callback = callback;
        updateItemAndList(itemSupplier.get());
    }

    public GuiModuleScrollList(IGuiWrapper gui, int x, int y, int height, Supplier<ItemStack> itemSupplier, Consumer<Module<?>> callback) {
        this(gui, x, y, TEXTURE_WIDTH + 8, height, itemSupplier, callback);
    }

    public void updateItemAndList(ItemStack stack) {
        currentItem = stack;
        currentList.clear();
        currentList.addAll(ModuleHelper.get().loadAllTypes(stack));
        currentList.sort(Comparator.comparing(this::getModuleName, String.CASE_INSENSITIVE_ORDER));
    }

    private void recheckItem() {
        ItemStack stack = itemSupplier.get();
        if (!ItemStack.areItemStacksEqual(currentItem, stack)) {
            ModuleData<?> prevSelect = getSelection();
            updateItemAndList(stack);
            if (currentList.contains(prevSelect)) {
                onSelectedChange();
            } else {
                clearSelection();
            }
        }
    }

    private void onSelectedChange() {
        callback.accept(getModule(selectedType));
    }

    @Nullable
    private Module<?> getModule(@Nullable ModuleData<?> data) {
        if (data == null) {
            return null;
        }
        return ModuleHelper.get().load(currentItem, data);
    }

    @Override
    protected List<ModuleData<?>> getCurrentInstalled() {
        return currentList;
    }

    @Override
    protected void drawName(ModuleData<?> module, int y) {
        IModule<?> instance = getModule(module);
        if (instance != null) {
            int color = module.isExclusive(ExclusiveFlag.ANY) ? (instance.isEnabled() ? 0x635BD4 : 0x2E2A69) : (instance.isEnabled() ? titleTextColor() : 0x5E1D1D);
            drawNameText(y, new TextComponentGroup().translation(module.getTranslationKey()), color, 0.7F);
        }
    }

    @Override
    protected ItemStack getRenderStack(ModuleData<?> moduleData) {
        return moduleData.getStack();
    }

    @Override
    protected void setSelected(@Nullable ModuleData<?> newSelection) {
        if (selectedType != newSelection) {
            selectedType = newSelection;
            onSelectedChange();
        }
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        recheckItem();
        super.renderForeground(mouseX, mouseY);
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        if (mouseX >= relativeX + 1 && mouseX < relativeX + barXShift - 1) {
            int currentSelection = getCurrentSelection();
            for (int i = 0, focused = getFocusedElements(); i < focused; i++) {
                int index = currentSelection + i;
                if (index > currentList.size() - 1) {
                    break;
                }
                ModuleData<?> module = currentList.get(index);
                IModule<?> instance = getModule(module);
                int multipliedElement = elementHeight * i;
                if (instance != null && mouseY >= relativeY + 1 + multipliedElement && mouseY < relativeY + 1 + multipliedElement + elementHeight) {
                    displayTooltip(new TextComponentGroup().translation(MekanismLang.MODULE_INSTALLED.getTranslationKey()).translation(instance.getInstalledCount() + "/" + module.getMaxStackSize(), EnumColor.DARK_GREY.textFormatting), mouseX, mouseY, getGuiWidth());
                    return;
                }
            }
        }
    }

    private String getModuleName(ModuleData<?> moduleData) {
        String translationKey = moduleData.getTranslationKey();
        return LangUtils.canLocalize(translationKey) ? LangUtils.localize(translationKey) : moduleData.getStack().getDisplayName();
    }

    @Override
    public void syncFrom(GuiElement element) {
        super.syncFrom(element);
        GuiModuleScrollList old = (GuiModuleScrollList) element;
        if (ItemStack.areItemStacksEqual(currentItem, old.currentItem)) {
            selectedType = old.selectedType;
        } else if (old.selectedType != null) {
            if (currentList.contains(old.selectedType)) {
                setSelected(old.selectedType);
            } else {
                onSelectedChange();
            }
        }
    }
}
