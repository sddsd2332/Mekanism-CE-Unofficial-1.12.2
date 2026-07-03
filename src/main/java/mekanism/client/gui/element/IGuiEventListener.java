package mekanism.client.gui.element;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public interface IGuiEventListener {
    default void mouseMoved(double pMouseX, double pMouseY) {
    }

    default boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        return false;
    }

    default boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        return false;
    }

    default boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
        return false;
    }

    default boolean mouseScrolled(double pMouseX, double pMouseY, double pDelta) {
        return false;
    }

    default boolean keyPressed(int pKeyCode, int pScanCode, int pModifiers) {
        return false;
    }

    default boolean keyReleased(int pKeyCode, int pScanCode, int pModifiers) {
        return false;
    }

    default boolean charTyped(char pCodePoint, int pModifiers) {
        return false;
    }

    default boolean changeFocus(boolean pFocus) {
        return false;
    }

    default boolean isMouseOver(double pMouseX, double pMouseY) {
        return false;
    }
}