package mekanism.generators.client.gui.element.button;

import mekanism.api.EnumColor;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.util.MekanismUtils;
import mekanism.generators.common.util.MekanismGeneratorUtils;
import mekanism.generators.common.util.MekanismGeneratorUtils.ResourceType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class ReactorLogicButton<TYPE extends Enum<TYPE>> extends MekanismButton {

    private static final ResourceLocation TEXTURE = MekanismGeneratorUtils.getResource(ResourceType.GUI_BUTTON, "reactor_logic.png");

    private final IntSupplier scrollSupplier;
    private final Supplier<TYPE[]> modesSupplier;
    private final Supplier<TYPE> currentSupplier;
    private final Function<TYPE, ITextComponent> nameSupplier;
    private final Function<TYPE, String> descriptionSupplier;
    private final Function<TYPE, ItemStack> stackSupplier;
    private final Function<TYPE, EnumColor> colorSupplier;
    private final Consumer<TYPE> onPress;
    private final int index;

    public ReactorLogicButton(IGuiWrapper gui, int x, int y, int index, IntSupplier scrollSupplier, Supplier<TYPE[]> modesSupplier,
          Supplier<TYPE> currentSupplier, Function<TYPE, ITextComponent> nameSupplier, Function<TYPE, String> descriptionSupplier,
          Function<TYPE, ItemStack> stackSupplier, Function<TYPE, EnumColor> colorSupplier, Consumer<TYPE> onPress) {
        super(gui, x, y, 128, 22, new TextComponentString(""), null, null);
        this.scrollSupplier = scrollSupplier;
        this.modesSupplier = modesSupplier;
        this.currentSupplier = currentSupplier;
        this.nameSupplier = nameSupplier;
        this.descriptionSupplier = descriptionSupplier;
        this.stackSupplier = stackSupplier;
        this.colorSupplier = colorSupplier;
        this.onPress = onPress;
        this.index = index;
        setButtonBackground(ButtonBackground.NONE);
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        TYPE mode = getMode();
        if (mode != null) {
            MekanismRenderer.bindTexture(TEXTURE);
            MekanismRenderer.color(colorSupplier.apply(mode));
            GuiUtils.blit(getButtonX(), getButtonY(), 0, mode == currentSupplier.get() ? 22 : 0, getButtonWidth(), getButtonHeight(), 128, 44);
            MekanismRenderer.resetColor();
        }
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        TYPE mode = getMode();
        if (mode != null) {
            gui().renderItem(stackSupplier.apply(mode), relativeX + 3, relativeY + 3);
            drawScrollingString(nameSupplier.apply(mode), 20, 2, TextAlignment.LEFT, titleTextColor(), width - 20, 2, false);
            super.renderForeground(mouseX, mouseY);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        TYPE mode = getMode();
        if (mode != null) {
            onPress.accept(mode);
        }
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        TYPE mode = getMode();
        if (mode != null) {
            List<String> tooltip = MekanismUtils.splitTooltip(descriptionSupplier.apply(mode), ItemStack.EMPTY);
            displayTooltips(tooltip, mouseX, mouseY);
        }
    }

    private TYPE getMode() {
        TYPE[] modes = modesSupplier.get();
        int modeIndex = scrollSupplier.getAsInt() + index;
        return modeIndex >= 0 && modeIndex < modes.length ? modes[modeIndex] : null;
    }
}
