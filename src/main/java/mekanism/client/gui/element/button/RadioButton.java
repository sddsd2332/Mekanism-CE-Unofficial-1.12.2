package mekanism.client.gui.element.button;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.common.MekanismSounds;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

import java.util.function.BooleanSupplier;

public class RadioButton extends MekanismButton {

    public static final ResourceLocation RADIO = MekanismUtils.getResource(ResourceType.GUI, "radio_button.png");
    public static final int RADIO_SIZE = 8;

    private final ITextComponent toggledComponent;
    private final ITextComponent altComponent;
    private final BooleanSupplier toggled;

    public RadioButton(IGuiWrapper gui, int x, int y, BooleanSupplier toggled, IClickable onPress, ITextComponent toggledComponent, ITextComponent altComponent) {
        super(gui, x, y, RADIO_SIZE, RADIO_SIZE, new mekanism.api.text.TextComponentGroup(), null, null);
        this.toggled = toggled;
        this.toggledComponent = toggledComponent;
        this.altComponent = altComponent;
        this.customClickSound = () -> MekanismSounds.BEEP;
        this.clickSoundVolume = 1.0F;
        this.onPress = onPress;
    }

    private final IClickable onPress;

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        minecraft.renderEngine.bindTexture(RADIO);
        if (toggled.getAsBoolean()) {
            GuiUtils.blit(getButtonX(), getButtonY(), 0, RADIO_SIZE, getButtonWidth(), getButtonHeight(), 2 * RADIO_SIZE, 2 * RADIO_SIZE);
        } else {
            int uOffset = checkWindows(mouseX, mouseY, isHovered()) ? RADIO_SIZE : 0;
            GuiUtils.blit(getButtonX(), getButtonY(), uOffset, 0, getButtonWidth(), getButtonHeight(), 2 * RADIO_SIZE, 2 * RADIO_SIZE);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        onPress.onClick(this, mouseX, mouseY);
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        displayTooltip(toggled.getAsBoolean() ? toggledComponent : altComponent, mouseX, mouseY);
    }
}
