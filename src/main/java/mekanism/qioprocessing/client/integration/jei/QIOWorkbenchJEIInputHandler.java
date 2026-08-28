package mekanism.qioprocessing.client.integration.jei;

import mekanism.client.gui.GuiMekanism;
import mekanism.client.jei.MekanismJEI;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.qioprocessing.client.gui.GuiQIOWorkbenchBatchWindow;
import mekanism.qioprocessing.common.inventory.container.QIOWorkbenchConfigurationContainer;
import mekanism.qioprocessing.common.util.QIORecipeStackUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;

/** Shift-click import from JEI's item and bookmark overlays into the batch target list. */
/**
 * QIO 处理模块中的 QIOWorkbenchJEIInputHandler 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOWorkbenchJEIInputHandler {

    private static final QIOWorkbenchJEIInputHandler INSTANCE =
          new QIOWorkbenchJEIInputHandler();
    private static boolean registered;

    private QIOWorkbenchJEIInputHandler() {
    }

    public static void register() {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(INSTANCE);
            registered = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        int button = Mouse.getEventButton();
        if (!isImportGesture(button, Mouse.getEventButtonState(),
              GuiScreen.isShiftKeyDown()) || MekanismJEI.jeiRuntime == null) return;
        if (!(event.getGui() instanceof GuiMekanism<?> gui) ||
            !(gui.inventorySlots instanceof MekanismContainer container) ||
            !(container instanceof QIOWorkbenchConfigurationContainer workbench) ||
            gui.getWindows().stream().noneMatch(
                  window -> window instanceof GuiQIOWorkbenchBatchWindow)) return;
        ItemStack target = overlayTarget();
        if (target.isEmpty()) return;
        workbench.getWorkbenchConfigurationClientCache().addBatchTarget(target);
        event.setCanceled(true);
    }

    static boolean isImportGesture(int button, boolean pressed, boolean shiftDown) {
        return pressed && shiftDown && (button == 0 || button == 1);
    }

    private static ItemStack overlayTarget() {
        Object ingredient = MekanismJEI.jeiRuntime.getIngredientListOverlay()
              .getIngredientUnderMouse();
        if (!(ingredient instanceof ItemStack)) {
            ingredient = MekanismJEI.jeiRuntime.getBookmarkOverlay()
                  .getIngredientUnderMouse();
        }
        return copyTarget(ingredient);
    }

    static ItemStack copyTarget(@Nullable Object ingredient) {
        if (!(ingredient instanceof ItemStack stack) || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return QIORecipeStackUtils.copyForRecipeSelection(stack, 1);
    }
}
