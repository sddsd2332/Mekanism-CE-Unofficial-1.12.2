package mekanism.client.gui.element.button;

import mekanism.client.gui.IGuiWrapper;
import mekanism.common.tile.TileEntityGasTank.GasMode;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class GuiGasMode extends MekanismImageButton {

    private static final ResourceLocation IDLE = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "gas_mode_idle.png");
    private static final ResourceLocation EXCESS = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "gas_mode_excess.png");
    private static final ResourceLocation DUMP = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "gas_mode_dump.png");

    private final TextAlignment textSide;
    private final Supplier<GasMode> gasModeSupplier;
    private final Supplier<List<ITextComponent>> tooltipSupplier;

    public GuiGasMode(IGuiWrapper gui, int x, int y, boolean left, Supplier<GasMode> gasModeSupplier, Runnable onPress) {
        this(gui, x, y, left, gasModeSupplier, onPress, null);
    }

    public GuiGasMode(IGuiWrapper gui, int x, int y, boolean left, Supplier<GasMode> gasModeSupplier, Runnable onPress,
          Supplier<List<ITextComponent>> tooltipSupplier) {
        super(gui, x, y, 10, IDLE, onPress);
        this.textSide = left ? TextAlignment.RIGHT : TextAlignment.LEFT;
        this.gasModeSupplier = gasModeSupplier;
        this.tooltipSupplier = tooltipSupplier;
    }

    @Override
    protected ResourceLocation getResource() {
        return switch (gasModeSupplier.get()) {
            case DUMPING_EXCESS -> EXCESS;
            case DUMPING -> DUMP;
            default -> super.getResource();
        };
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        ITextComponent component = new TextComponentString(LangUtils.localize(gasModeSupplier.get().getLangKey()));
        int start = textSide == TextAlignment.RIGHT ? -69 : getWidth();
        drawScrollingString(component, start, 1, textSide, titleTextColor(), 69, 2, false);
        super.renderForeground(mouseX, mouseY);
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        if (tooltipSupplier != null) {
            List<String> tooltip = new ArrayList<>();
            for (ITextComponent component : tooltipSupplier.get()) {
                tooltip.add(component.getFormattedText());
            }
            if (!tooltip.isEmpty()) {
                displayTooltips(tooltip, mouseX, mouseY);
            }
        }
    }
}
