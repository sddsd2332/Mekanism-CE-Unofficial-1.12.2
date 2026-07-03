package mekanism.client.gui.element.custom;

import mekanism.api.EnumColor;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiElementHolder;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.MekanismLang;
import mekanism.common.Upgrade;
import mekanism.common.util.LangUtils;
import mekanism.common.util.UpgradeUtils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.text.TextComponentString;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class GuiSupportedUpgrades extends GuiElement {

    private static  TextComponentString SUPPORTED = new TextComponentString(LangUtils.localize("gui.upgrades.supported"));
    private static final int ELEMENT_WIDTH = 167;
    private static final int PADDED_ELEMENT_WIDTH = ELEMENT_WIDTH - 2;
    private static final int ELEMENT_SIZE = 12;
    private static final int ROW_ROOM = PADDED_ELEMENT_WIDTH / ELEMENT_SIZE;
    private static final List<Upgrade> UPGRADES = Arrays.asList(Upgrade.values());

    private static int getFirstRowStart(IGuiWrapper gui) {
        return Math.min(gui.getFont().getStringWidth(SUPPORTED.getFormattedText()) + 1, PADDED_ELEMENT_WIDTH);
    }

    private static int getFirstRowRoom(int firstRowStart) {
        return (PADDED_ELEMENT_WIDTH - firstRowStart) / ELEMENT_SIZE;
    }

    private final Set<Upgrade> supportedUpgrades;
    private final int firstRowRoom;
    private final int firstRowStart;

    public static int calculateNeededRows(IGuiWrapper gui) {
        int count = UPGRADES.size();
        int firstRowRoom = getFirstRowRoom(getFirstRowStart(gui));
        if (count <= firstRowRoom) {
            return 1;
        }
        count -= firstRowRoom;
        return 2 + count / ROW_ROOM;
    }

    public GuiSupportedUpgrades(IGuiWrapper gui, int x, int y, Set<Upgrade> supportedUpgrades) {
        super(gui, x, y, ELEMENT_WIDTH, ELEMENT_SIZE * calculateNeededRows(gui) + 2);
        this.supportedUpgrades = supportedUpgrades;
        this.firstRowStart = getFirstRowStart(gui);
        this.firstRowRoom = getFirstRowRoom(firstRowStart);
        active = true;
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        renderBackgroundTexture(GuiElementHolder.HOLDER, GuiElementHolder.HOLDER_SIZE, GuiElementHolder.HOLDER_SIZE);
        int backgroundColor = (GuiElementHolder.getBackgroundColor() & 0x00FFFFFF) | 0x80000000;
        for (int i = 0; i < UPGRADES.size(); i++) {
            Upgrade upgrade = UPGRADES.get(i);
            UpgradePos pos = getUpgradePos(i);
            int xPos = relativeX + 1 + pos.x;
            int yPos = relativeY + 1 + pos.y;
            gui().renderItem(UpgradeUtils.getStack(upgrade), xPos, yPos, 0.75F);
            if (!supportedUpgrades.contains(upgrade)) {
                renderUnsupportedOverlay(xPos, yPos, backgroundColor);
            }
        }
    }

    private void renderUnsupportedOverlay(int xPos, int yPos, int backgroundColor) {
        GlStateManager.pushMatrix();
        try {
            // Match 1.21's guiGhostRecipeOverlay behavior: draw the fade above the item model's GUI depth.
            GlStateManager.translate(0, 0, 100);
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GuiUtils.fill(xPos, yPos, xPos + ELEMENT_SIZE, yPos + ELEMENT_SIZE, backgroundColor);
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
            MekanismRenderer.resetColor();
        }
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawScrollingString(SUPPORTED, 0, 3, TextAlignment.LEFT, titleTextColor(), 2, false);
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        for (int i = 0; i < UPGRADES.size(); i++) {
            UpgradePos pos = getUpgradePos(i);
            int xPos = x + 1 + pos.x;
            int yPos = y + 1 + pos.y;
            if (mouseX >= xPos && mouseX < xPos + ELEMENT_SIZE &&
                mouseY >= yPos && mouseY < yPos + ELEMENT_SIZE) {
                Upgrade upgrade = UPGRADES.get(i);
                if (supportedUpgrades.contains(upgrade)) {
                    displayTooltips(Arrays.asList(upgrade.getName(), upgrade.getDescription()), mouseX, mouseY);
                } else {
                    displayTooltips(Arrays.asList(MekanismLang.UPGRADE_NOT_SUPPORTED.translateColored(EnumColor.RED, upgrade.getName()).getFormattedText(), upgrade.getDescription()), mouseX, mouseY);
                }
                return;
            }
        }
    }

    private UpgradePos getUpgradePos(int index) {
        int row = index < firstRowRoom ? 0 : 1 + (index - firstRowRoom) / ROW_ROOM;
        if (row == 0) {
            return new UpgradePos(firstRowStart + (index % firstRowRoom) * ELEMENT_SIZE, 0);
        }
        index -= firstRowRoom;
        return new UpgradePos((index % ROW_ROOM) * ELEMENT_SIZE, row * ELEMENT_SIZE);
    }

    private static class UpgradePos {

        private final int x;
        private final int y;

        private UpgradePos(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
