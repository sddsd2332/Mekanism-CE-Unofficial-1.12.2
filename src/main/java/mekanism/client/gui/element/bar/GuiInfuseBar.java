package mekanism.client.gui.element.bar;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.common.InfuseStorage;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public class GuiInfuseBar extends GuiBar<GuiBar.IBarInfoHandler> {

    private final InfuseStorage storage;
    private final DoubleSupplier levelSupplier;
    private final boolean vertical;

    public GuiInfuseBar(IGuiWrapper gui, InfuseStorage storage, DoubleSupplier levelSupplier, Supplier<String> tooltipSupplier, int x, int y, int width,
          int height, boolean vertical) {
        super(null, gui, new IBarInfoHandler() {
            @Override
            public double getLevel() {
                return levelSupplier.getAsDouble();
            }

            @Nullable
            @Override
            public ITextComponent getTooltip() {
                return new TextComponentString(tooltipSupplier.get());
            }
        }, x, y, width, height, !vertical);
        this.storage = storage;
        this.levelSupplier = levelSupplier;
        this.vertical = vertical;
    }

    @Override
    protected void renderBarOverlay(int mouseX, int mouseY, float partialTicks, double handlerLevel) {
        int displayInt = Math.max(1, calculateScaled(MathHelper.clamp(handlerLevel, 0, 1), vertical ? height - 2 : width - 2));
        GuiUtils.drawInfuseBarSprite(relativeX, relativeY, width, height, displayInt, storage, vertical);
    }
}
