package mekanism.client.gui.element.custom;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget;
import mekanism.common.OreDictCache;
import mekanism.common.util.StackUtils;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class GuiDictionaryTarget extends GuiElement implements IRecipeViewerGhostTarget {

    private final Consumer<List<String>> tagSetter;
    private ItemStack target = ItemStack.EMPTY;
    private List<String> tags = new ArrayList<>();

    public GuiDictionaryTarget(IGuiWrapper gui, int x, int y, Consumer<List<String>> tagSetter) {
        super(gui, x, y, 16, 16);
        this.tagSetter = tagSetter;
        active = true;
    }

    public boolean hasTarget() {
        return !target.isEmpty();
    }

    public void clearTarget() {
        setTarget(ItemStack.EMPTY);
    }

    public void setTarget(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            target = ItemStack.EMPTY;
            tags = new ArrayList<>();
        } else {
            target = StackUtils.size(stack, 1);
            tags = OreDictCache.getOreDictName(target);
        }
        tagSetter.accept(tags);
        playClickSound();
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        if (!target.isEmpty()) {
            gui().renderItem(target, relativeX, relativeY);
        }
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        if (!target.isEmpty()) {
            gui().renderItemTooltip(target, mouseX, mouseY);
        }
    }

    @Override
    public boolean hasPersistentData() {
        return true;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            clearTarget();
        } else {
            ItemStack stack = minecraft.player.inventory.getItemStack();
            if (!stack.isEmpty()) {
                setTarget(stack);
            }
        }
    }

    @Override
    public void syncFrom(GuiElement element) {
        super.syncFrom(element);
        GuiDictionaryTarget old = (GuiDictionaryTarget) element;
        target = old.target.copy();
        tags = old.tags;
        tagSetter.accept(tags);
    }

    @Nullable
    @Override
    public IRecipeViewerGhostTarget.IGhostIngredientConsumer getGhostHandler() {
        return new IRecipeViewerGhostTarget.IGhostItemConsumer() {
            @Override
            public void accept(Object ingredient) {
                setTarget((ItemStack) ingredient);
            }
        };
    }
}
