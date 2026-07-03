package mekanism.client.gui.element;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.MekanismLang;
import mekanism.common.MekanismSounds;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class GuiDigitalSwitch extends GuiTexturedElement {

    public static final ResourceLocation SWITCH = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "switch/switch.png");
    private static final ResourceLocation EJECT = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "switch/eject.png");
    private static final ResourceLocation INPUT = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "switch/input.png");
    private static final ResourceLocation SILK = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "switch/silk.png");
    public static final int BUTTON_SIZE_X = 15;
    public static final int BUTTON_SIZE_Y = 8;

    private final SwitchType type;
    private final ResourceLocation icon;
    private final BooleanSupplier stateSupplier;
    private final IClickable onToggle;
    @Nullable
    private Supplier<ITextComponent> tooltipSupplier;

    public GuiDigitalSwitch(IGuiWrapper gui, int x, int y, int iconX, BooleanSupplier stateSupplier, Runnable onToggle, String tooltip) {
        this(gui, x, y, getIcon(iconX), stateSupplier, onToggle, tooltip, SwitchType.LOWER_ICON);
    }

    public GuiDigitalSwitch(IGuiWrapper gui, int x, int y, ResourceLocation icon, BooleanSupplier stateSupplier, Runnable onToggle, String tooltip,
          SwitchType type) {
        this(gui, x, y, icon, stateSupplier, (element, mouseX, mouseY) -> {
            onToggle.run();
            return true;
        }, type, tooltip);
    }

    public GuiDigitalSwitch(IGuiWrapper gui, int x, int y, ResourceLocation icon, BooleanSupplier stateSupplier, IClickable onToggle, SwitchType type) {
        this(gui, x, y, icon, stateSupplier, onToggle, type, null);
    }

    private GuiDigitalSwitch(IGuiWrapper gui, int x, int y, ResourceLocation icon, BooleanSupplier stateSupplier, IClickable onToggle, SwitchType type,
          @Nullable String tooltip) {
        super(SWITCH, gui, x, y, type.width, type.height);
        this.type = type;
        this.icon = icon;
        this.stateSupplier = stateSupplier;
        this.onToggle = onToggle;
        setLegacyTooltip(tooltip);
        customClickSound = () -> MekanismSounds.BEEP;
        clickSoundVolume = 1.0F;
    }

    public GuiDigitalSwitch setTooltip(ITextComponent tooltip) {
        return setTooltip(() -> tooltip);
    }

    public GuiDigitalSwitch setTooltip(Supplier<ITextComponent> tooltipSupplier) {
        this.tooltipSupplier = tooltipSupplier;
        return this;
    }

    private void setLegacyTooltip(@Nullable String tooltip) {
        if (tooltip != null) {
            setTooltip(() -> new TextComponentString(tooltip + ": " + LangUtils.transOnOff(stateSupplier.getAsBoolean())));
        }
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        MekanismRenderer.bindTexture(SWITCH);
        boolean state = stateSupplier.getAsBoolean();
        GuiUtils.blit(relativeX + type.switchX, relativeY + type.switchY, 0, state ? 0 : BUTTON_SIZE_Y, BUTTON_SIZE_X, BUTTON_SIZE_Y, BUTTON_SIZE_X, BUTTON_SIZE_Y * 2);
        GuiUtils.blit(relativeX + type.switchX, relativeY + type.switchY + BUTTON_SIZE_Y + 1, 0, state ? BUTTON_SIZE_Y : 0, BUTTON_SIZE_X, BUTTON_SIZE_Y, BUTTON_SIZE_X,
              BUTTON_SIZE_Y * 2);
        MekanismRenderer.bindTexture(icon);
        GuiUtils.blit(relativeX + type.iconX, relativeY + type.iconY, 0, 0, 5, 5, 5, 5);
        MekanismRenderer.resetColor();
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawScaledScrollingString(MekanismLang.ON.translate(), type.switchX, type.switchY, TextAlignment.CENTER, 0x101010, BUTTON_SIZE_X, 1, false, 0.5F,
              GuiElement.getMillis());
        drawScaledScrollingString(MekanismLang.OFF.translate(), type.switchX, type.switchY + BUTTON_SIZE_Y + 1, TextAlignment.CENTER, 0x101010, BUTTON_SIZE_X, 1, false,
              0.5F, GuiElement.getMillis());
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        onToggle.onClick(this, mouseX, mouseY);
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        onToggle.onClick(this, mouseX, mouseY);
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        if (tooltipSupplier != null) {
            displayTooltip(tooltipSupplier.get(), mouseX, mouseY);
        }
    }

    private static ResourceLocation getIcon(int iconX) {
        return switch (iconX) {
            case 24 -> INPUT;
            case 31 -> SILK;
            default -> EJECT;
        };
    }

    public enum SwitchType {
        LOWER_ICON(BUTTON_SIZE_X, BUTTON_SIZE_Y * 2 + 15, 0, 0, 5, 21),
        LEFT_ICON(BUTTON_SIZE_X + 15, BUTTON_SIZE_Y * 2, 15, 0, 5, 6);

        private final int width;
        private final int height;
        private final int switchX;
        private final int switchY;
        private final int iconX;
        private final int iconY;

        SwitchType(int width, int height, int switchX, int switchY, int iconX, int iconY) {
            this.width = width;
            this.height = height;
            this.switchX = switchX;
            this.switchY = switchY;
            this.iconX = iconX;
            this.iconY = iconY;
        }
    }
}
