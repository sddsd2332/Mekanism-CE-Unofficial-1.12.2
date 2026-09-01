package mekanism.client.gui.element.custom;

import mekanism.api.EnumColor;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.common.MekanismLang;
import mekanism.common.tile.qio.TileEntityQIODriveArray;
import mekanism.common.tile.qio.TileEntityQIODriveArray.DriveStatus;
import net.minecraft.inventory.Slot;

import java.util.Collections;
import java.util.List;
import java.util.function.LongSupplier;

/** Colored drive-state overlay with explicit duplicate and damaged-drive tooltips. */
public class GuiQIODriveStatusSlot extends GuiElement {

    private static final int STATUS_TOOLTIP_INDEX = 2;

    private final int slot;
    private final LongSupplier statusSupplier;

    public GuiQIODriveStatusSlot(IGuiWrapper gui, int x, int y, int slot, LongSupplier statusSupplier) {
        super(gui, x, y, 18, 18);
        this.slot = slot;
        this.statusSupplier = statusSupplier;
        active = true;
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        DriveStatus status = getStatus();
        int color = switch (status) {
            case READY -> 0xB030D060;
            case NEAR_FULL -> 0xB0E0B020;
            case FULL -> 0xB0D04030;
            case DUPLICATE, MISSING, INVALID, ERROR -> 0xB0E03030;
            case OVER_CAPACITY -> 0xB0E08020;
            case OFFLINE -> 0xB0808080;
            default -> 0;
        };
        if (color != 0) {
            GuiUtils.fill(relativeX + 1, relativeY + 1, relativeX + 17, relativeY + 3, color);
        }
        super.renderForeground(mouseX, mouseY);
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        if (isMouseOverTooltip(mouseX, mouseY)) {
            List<String> tooltip = Collections.singletonList(MekanismLang.QIO_DRIVE_STATUS_DETAIL.translateColored(
                  EnumColor.GREY, EnumColor.INDIGO, getStatus().getDisplayName()).getFormattedText());
            Slot hoveredSlot = gui().getSlotUnderMouse(mouseX, mouseY);
            if (gui().getCarriedItem().isEmpty() && hoveredSlot != null && hoveredSlot.getHasStack()) {
                gui().renderItemTooltipWithExtra(hoveredSlot.getStack(), mouseX, mouseY, tooltip, STATUS_TOOLTIP_INDEX);
            } else {
                displayTooltips(tooltip, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean rendersSlotTooltip() {
        return true;
    }

    /**
     * This element is drawn above the real container slot, but it must not
     * count as a blocking button when {@link mekanism.client.gui.GuiMekanism}
     * checks whether vanilla slot interaction is allowed.
     */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }

    @Override
    public boolean isMouseOverTooltip(double mouseX, double mouseY) {
        return visible && mouseX >= getX() && mouseY >= getY() && mouseX < getRight() && mouseY < getBottom() &&
              checkWindows(mouseX, mouseY, true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        //This element is only a status overlay. Let the drive inventory slot below it handle clicks.
        return false;
    }

    private DriveStatus getStatus() {
        return TileEntityQIODriveArray.getStatus(slot, statusSupplier.getAsLong());
    }
}
