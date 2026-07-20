package mekanism.client.gui.element.custom;

import mekanism.api.EnumColor;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.client.gui.element.bar.GuiDigitalBar;
import mekanism.common.MekanismLang;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.util.text.TextUtils;
import net.minecraft.util.text.ITextComponent;

import java.util.function.Supplier;

/** Reusable capacity summary for QIO component screens. */
public class GuiQIOFrequencyDataScreen extends GuiInnerScreen {

    private final Supplier<QIOFrequency> frequencySupplier;

    public GuiQIOFrequencyDataScreen(IGuiWrapper gui, int x, int y, int width, int height,
          Supplier<QIOFrequency> frequencySupplier) {
        super(gui, x, y, width, height);
        this.frequencySupplier = frequencySupplier;
        active = true;
        addChild(new GuiDigitalBar(gui, new CapacityInfo(true),
              relativeX + 11, relativeY + 20, 50));
        addChild(new GuiDigitalBar(gui, new CapacityInfo(false),
              relativeX + 83, relativeY + 20, 50));
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        QIOFrequency frequency = frequencySupplier.get();
        if (frequency != null) {
            drawScaledScrollingString(MekanismLang.FREQUENCY.translate(frequency.getName()), 0, 5, TextAlignment.LEFT,
                  screenTextColor(), width, 5, false, 0.8F, getTimeOpened());
        }
        drawScaledScrollingString(MekanismLang.QIO_RESOURCES.translate(), 11, 32, TextAlignment.CENTER,
              screenTextColor(), 50, 0, false, 0.8F, getTimeOpened());
        drawScaledScrollingString(MekanismLang.QIO_TYPES.translate(), 83, 32, TextAlignment.CENTER,
              screenTextColor(), 50, 0, false, 0.8F, getTimeOpened());
    }

    private class CapacityInfo implements IBarInfoHandler {

        private final boolean count;

        private CapacityInfo(boolean count) {
            this.count = count;
        }

        @Override
        public double getLevel() {
            QIOFrequency frequency = frequencySupplier.get();
            if (frequency == null) {
                return 0;
            }
            if (count) {
                long capacity = frequency.getTotalCountCapacity();
                return capacity <= 0 ? 0 : frequency.getTotalCount() / (double) capacity;
            }
            int capacity = frequency.getTotalTypeCapacity();
            return capacity <= 0 ? 0 : frequency.getTotalTypes() / (double) capacity;
        }

        @Override
        public ITextComponent getTooltip() {
            QIOFrequency frequency = frequencySupplier.get();
            if (frequency == null) {
                return null;
            }
            return count ? MekanismLang.QIO_RESOURCES_DETAIL.translateColored(EnumColor.GREY, EnumColor.INDIGO,
                  TextUtils.format(frequency.getTotalCount()), TextUtils.format(frequency.getTotalCountCapacity())) :
                  MekanismLang.QIO_TYPES_DETAIL.translateColored(EnumColor.GREY, EnumColor.INDIGO,
                        TextUtils.format(frequency.getTotalTypes()), TextUtils.format(frequency.getTotalTypeCapacity()));
        }
    }
}
