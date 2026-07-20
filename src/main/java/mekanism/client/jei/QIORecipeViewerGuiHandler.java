package mekanism.client.jei;

import mekanism.client.gui.GuiMekanism;
import mezz.jei.api.IRecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Keeps QIO's client container alive while JEI/HEI opens its recipe screen. */
@SideOnly(Side.CLIENT)
public final class QIORecipeViewerGuiHandler {

    private static final QIORecipeViewerGuiHandler INSTANCE = new QIORecipeViewerGuiHandler();
    private static boolean registered;

    private QIORecipeViewerGuiHandler() {
    }

    public static void register() {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(INSTANCE);
            registered = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onGuiOpen(GuiOpenEvent event) {
        preserveQIOContainer(Minecraft.getMinecraft().currentScreen, event.getGui());
    }

    static boolean preserveQIOContainer(GuiScreen currentScreen, GuiScreen openingScreen) {
        if (openingScreen instanceof IRecipesGui && currentScreen instanceof GuiMekanism<?> gui) {
            gui.switchingToJEI = true;
            return true;
        }
        return false;
    }
}
