package mekanism.client.gui.element.window;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiColorPickerSlot;
import mekanism.client.gui.element.GuiScreenSwitch;
import mekanism.client.gui.element.GuiSlider;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.HUDElement.HUDColor;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import mekanism.common.lib.Color;

public class GuiMekaSuitHelmetOptions extends GuiWindow {

    public GuiMekaSuitHelmetOptions(IGuiWrapper gui, int x, int y) {
        super(gui, x, y, 140, 140, WindowType.MEKA_SUIT_HELMET);
        interactionStrategy = InteractionStrategy.NONE;
        addChild(new GuiColorPickerSlot(gui, relativeX + 12, relativeY + 32, false, HUDColor.REGULAR::getColor, color -> {
            MekanismConfig.current().client.hudColor.set(color.rgb());
            saveClientConfig();
        }));
        addChild(new GuiColorPickerSlot(gui, relativeX + 61, relativeY + 32, false,
              () -> Color.rgb(MekanismConfig.current().client.hudWarningColor.val()), color -> {
                  MekanismConfig.current().client.hudWarningColor.set(color.rgb());
                  saveClientConfig();
              }));
        addChild(new GuiColorPickerSlot(gui, relativeX + 110, relativeY + 32, false,
              () -> Color.rgb(MekanismConfig.current().client.hudDangerColor.val()), color -> {
                  MekanismConfig.current().client.hudDangerColor.set(color.rgb());
                  saveClientConfig();
              }));

        GuiSlider opacitySlider = addChild(new GuiSlider(gui, relativeX + 10, relativeY + 62, 120, value -> {
            MekanismConfig.current().client.hudOpacity.set((float) value);
            saveClientConfig();
        }));
        opacitySlider.setValue(MekanismConfig.current().client.hudOpacity.val());

        GuiSlider jitterSlider = addChild(new GuiSlider(gui, relativeX + 10, relativeY + 87, 120, value -> {
            MekanismConfig.current().client.hudJitter.set(99F * (float) value + 1F);
            saveClientConfig();
        }));
        jitterSlider.setValue((MekanismConfig.current().client.hudJitter.val() - 1F) / 99F);

        addChild(new GuiScreenSwitch(gui, relativeX + 7, relativeY + 112, 126, MekanismLang.COMPASS.translate(),
              MekanismConfig.current().client.hudCompassEnabled::val, (element, mouseX, mouseY) -> {
                  MekanismConfig.current().client.hudCompassEnabled.set(!MekanismConfig.current().client.hudCompassEnabled.val());
                  saveClientConfig();
                  return true;
              }));
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawTitleText(MekanismLang.HELMET_OPTIONS.translate(), 6);
        drawScaledScrollingString(MekanismLang.HUD_OVERLAY.translate(), 7, 20, TextAlignment.LEFT, headingTextColor(),
              width - 14, 4, false, 1, GuiMekaSuitHelmetOptions.getMillis());

        drawScaledScrollingString(MekanismLang.DEFAULT.translate(), 6, 52, TextAlignment.CENTER, subheadingTextColor(),
              32, 0, false, 0.8F, GuiMekaSuitHelmetOptions.getMillis());
        drawScaledScrollingString(MekanismLang.WARNING.translate(), 55, 52, TextAlignment.CENTER, subheadingTextColor(),
              32, 0, false, 0.8F, GuiMekaSuitHelmetOptions.getMillis());
        drawScaledScrollingString(MekanismLang.DANGER.translate(), 104, 52, TextAlignment.CENTER, subheadingTextColor(),
              32, 0, false, 0.8F, GuiMekaSuitHelmetOptions.getMillis());

        drawScaledScrollingString(MekanismLang.OPACITY.translate(Math.round(MekanismConfig.current().client.hudOpacity.val() * 100)), 0,
              75, TextAlignment.CENTER, subheadingTextColor(), width, 4, false, 0.8F, GuiMekaSuitHelmetOptions.getMillis());
        drawScaledScrollingString(MekanismLang.JITTER.translate((int) MekanismConfig.current().client.hudJitter.val()), 0, 100,
              TextAlignment.CENTER, subheadingTextColor(), width, 4, false, 0.8F, GuiMekaSuitHelmetOptions.getMillis());
    }

    private void saveClientConfig() {
        if (Mekanism.configuration != null) {
            Mekanism.configuration.save();
        }
    }
}
