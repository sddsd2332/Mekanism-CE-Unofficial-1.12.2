package mekanism.client.gui.element.custom.module;

import mekanism.api.gear.config.ModuleEnumData;
import mekanism.api.text.IHasTextComponent;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.scroll.GuiScrollList;
import mekanism.client.render.IFancyFontRenderer.TextAlignment;
import mekanism.common.content.gear.ModuleConfigItem;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;

import java.util.List;

class EnumToggle extends MiniElement {

    private static final ResourceLocation SLIDER = MekanismUtils.getResource(ResourceType.GUI, "slider.png");
    private static final float TEXT_SCALE = 0.7F;
    private static final int BAR_START = 10;

    private final ModuleConfigItem<Enum<? extends IHasTextComponent>> data;
    private final int barLength;
    private final int optionDistance;
    boolean dragging;

    EnumToggle(GuiModuleScreen parent, ModuleConfigItem<Enum<? extends IHasTextComponent>> data, int xPos, int yPos, int dataIndex) {
        super(parent, xPos, yPos, dataIndex);
        this.data = data;
        barLength = this.parent.getScreenWidth() - 24;
        optionDistance = barLength / (((ModuleEnumData<?>) data.getData()).getEnums().size() - 1);
    }

    @Override
    int getNeededHeight() {
        return 28;
    }

    @Override
    void renderBackground(int mouseX, int mouseY) {
        GuiElement.minecraft.renderEngine.bindTexture(SLIDER);
        int center = optionDistance * data.get().ordinal();
        GuiUtils.blit(getRelativeX() + BAR_START + center - 2, getRelativeY() + 11, 0, 0, 5, 6, 8, 8);
        GuiUtils.blit(getRelativeX() + BAR_START, getRelativeY() + 17, 0, 6, barLength, 2, 8, 8);
    }

    @Override
    void renderForeground(int mouseX, int mouseY) {
        int textColor = parent.screenTextColor();
        parent.drawScaledScrollingString(data.getDescription(), xPos, yPos, TextAlignment.LEFT, textColor,
              parent.getScreenWidth() - GuiScrollList.TEXTURE_WIDTH, 2, false, 0.8F, GuiElement.getMillis());
        List<? extends Enum<? extends IHasTextComponent>> options = ((ModuleEnumData<?>) data.getData()).getEnums();
        for (int i = 0, count = options.size(); i < count; i++) {
            int optionCenter = BAR_START + optionDistance * i;
            ITextComponent text = ((IHasTextComponent) options.get(i)).getTextComponent();
            if (optionCenter < parent.getStringWidth(text) * TEXT_SCALE / 2F) {
                parent.drawScaledScrollingString(text, xPos, yPos + 20, TextAlignment.CENTER, textColor, BAR_START + optionDistance / 2F,
                      1, false, TEXT_SCALE, GuiElement.getMillis());
            } else {
                int max = parent.getScreenWidth() - 1;
                int start = xPos + optionCenter - optionDistance / 2;
                if (start + Math.ceil(parent.getStringWidth(text) * TEXT_SCALE) > max) {
                    parent.drawScaledScrollingString(text, start, yPos + 20, TextAlignment.CENTER, textColor, max - start, 1, false, TEXT_SCALE,
                          GuiElement.getMillis());
                } else {
                    parent.drawScaledScrollingString(text, start, yPos + 20, TextAlignment.CENTER, textColor, optionDistance, 1, false, TEXT_SCALE,
                          GuiElement.getMillis());
                }
            }
        }
    }

    @Override
    void click(double mouseX, double mouseY) {
        if (!dragging) {
            int center = optionDistance * data.get().ordinal();
            if (mouseOver(mouseX, mouseY, BAR_START + center - 2, 11, 5, 6)) {
                dragging = true;
            } else if (mouseOver(mouseX, mouseY, BAR_START, 10, barLength, 12)) {
                setDataFromPosition(mouseX);
            }
        }
    }

    @Override
    void onDrag(double mouseX, double mouseY, double mouseXOld, double mouseYOld) {
        if (dragging) {
            setDataFromPosition(mouseX);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void setDataFromPosition(double mouseX) {
        List<? extends Enum<? extends IHasTextComponent>> options = ((ModuleEnumData<?>) data.getData()).getEnums();
        int size = options.size() - 1;
        int cur = (int) Math.round(((mouseX - getX() - BAR_START) / barLength) * size);
        cur = MathHelper.clamp(cur, 0, size);
        if (cur != data.get().ordinal()) {
            data.set((Enum) options.get(cur), () -> parent.saveCallback.accept(dataIndex, data.getData()));
        }
    }

    @Override
    void release(double mouseX, double mouseY) {
        dragging = false;
    }
}
