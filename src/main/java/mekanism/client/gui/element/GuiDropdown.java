package mekanism.client.gui.element;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.common.MekanismSounds;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nonnull;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Compact 26.2-style enum dropdown used by the QIO viewer. */
public class GuiDropdown<TYPE extends Enum<TYPE> & GuiDropdown.IDropdownOption> extends GuiTexturedElement {

    private static final int ELEMENT_HEIGHT = 12;
    private final Consumer<TYPE> handler;
    private final Supplier<TYPE> current;
    private final TYPE[] options;
    private boolean open;
    private long openedAt;

    public GuiDropdown(IGuiWrapper gui, int x, int y, int width, Class<TYPE> enumClass,
          Supplier<TYPE> current, Consumer<TYPE> handler) {
        super(GuiInnerScreen.SCREEN, gui, x, y, width, ELEMENT_HEIGHT);
        this.current = current;
        this.handler = handler;
        options = enumClass.getEnumConstants();
        active = true;
        customClickSound = () -> MekanismSounds.BEEP;
        clickSoundVolume = 1;
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        renderBackgroundTexture(getResource(), GuiInnerScreen.SCREEN_SIZE, GuiInnerScreen.SCREEN_SIZE);
        renderHoveredOption(mouseX, mouseY);
    }

    @Override
    public void renderBackgroundOverlay(int mouseX, int mouseY) {
        if (open) {
            // Expanded dropdowns share the foreground layer with their option text, above container slots.
            renderBackgroundTexture(getResource(), GuiInnerScreen.SCREEN_SIZE, GuiInnerScreen.SCREEN_SIZE);
            renderHoveredOption(mouseX, mouseY);
        }
    }

    private void renderHoveredOption(int mouseX, int mouseY) {
        if (open) {
            int hovered = getHoveredIndex(mouseX, mouseY);
            if (hovered >= 0) {
                GuiUtils.fill(relativeX + 1, relativeY + ELEMENT_HEIGHT + hovered * 10,
                      relativeX + width - 1, relativeY + ELEMENT_HEIGHT + hovered * 10 + 10, 0x603CFE9A);
            }
        }
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawOption(current.get(), 2, getTimeOpened());
        if (open) {
            for (int i = 0; i < options.length; i++) {
                drawOption(options[i], ELEMENT_HEIGHT + 1 + i * 10, openedAt);
            }
        }
    }

    private void drawOption(TYPE option, int y, long visibleSince) {
        drawScaledScrollingString(option.getShortName(), 0, y, TextAlignment.LEFT, screenTextColor(), width, 3,
              false, 0.8F, visibleSince);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int hovered = getHoveredIndex(mouseX, mouseY);
        if (open && hovered >= 0) {
            handler.accept(options[hovered]);
            setOpen(false);
        } else {
            setOpen(!open);
        }
        playClickSound();
        return true;
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        int hovered = getHoveredIndex(mouseX, mouseY);
        TYPE option = hovered >= 0 ? options[hovered] : current.get();
        displayTooltip(option.getTooltip(), mouseX, mouseY);
    }

    private int getHoveredIndex(double mouseX, double mouseY) {
        if (!open || mouseX < getX() || mouseX >= getRight() || mouseY < getY() + ELEMENT_HEIGHT || mouseY >= getBottom()) {
            return -1;
        }
        return Math.max(0, Math.min(options.length - 1, (int) ((mouseY - getY() - ELEMENT_HEIGHT) / 10)));
    }

    private void setOpen(boolean open) {
        if (this.open == open) {
            return;
        }
        this.open = open;
        height = open ? ELEMENT_HEIGHT + options.length * 10 + 1 : ELEMENT_HEIGHT;
        if (open) {
            openedAt = GuiElement.getMillis();
        }
    }

    @Override
    public boolean hasPersistentData() {
        return true;
    }

    @Override
    public void syncFrom(GuiElement element) {
        super.syncFrom(element);
        if (element instanceof GuiDropdown<?> old && old.open) {
            setOpen(true);
            openedAt = old.openedAt;
        }
    }

    public interface IDropdownOption {

        @Nonnull
        ITextComponent getShortName();

        @Nonnull
        ITextComponent getTooltip();
    }
}
