package mekanism.client.gui.element.scroll;

import mekanism.api.EnumColor;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiElementHolder;
import mekanism.common.Upgrade;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UpgradeUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class GuiUpgradeScrollList extends GuiInstallableScrollList<Upgrade> {

    private static final ResourceLocation UPGRADE_SELECTION = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "upgrade_selection.png");
    private static final int TEXTURE_WIDTH = 100;
    private static final int TEXTURE_HEIGHT = 36;

    private final TileComponentUpgrade component;
    private final Runnable onSelectionChange;

    public GuiUpgradeScrollList(IGuiWrapper gui, int x, int y, int height, TileComponentUpgrade component, Runnable onSelectionChange) {
        super(gui, x, y, height, GuiElementHolder.HOLDER, GuiElementHolder.HOLDER_SIZE, UPGRADE_SELECTION, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        this.component = component;
        this.onSelectionChange = onSelectionChange;
    }

    @Override
    protected List<Upgrade> getCurrentInstalled() {
        return new ArrayList<>(component.getInstalledTypes());
    }

    @Override
    protected void drawName(Upgrade upgrade, int y) {
        drawNameText(y, new TextComponentString(upgrade.getName()), titleTextColor(), 1F);
    }

    @Override
    protected ItemStack getRenderStack(Upgrade upgrade) {
        return UpgradeUtils.getStack(upgrade);
    }

    @Nullable
    @Override
    protected EnumColor getColor(Upgrade upgrade) {
        return upgrade.getColor();
    }

    @Override
    protected void setSelected(@Nullable Upgrade newSelection) {
        if (selectedType != newSelection) {
            selectedType = newSelection;
            onSelectionChange.run();
        }
    }

    @Override
    public void renderToolTip(@Nonnull int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        if (mouseX >= getX() + 1 && mouseX < getX() + barXShift - 1) {
            List<Upgrade> upgrades = getCurrentInstalled();
            int currentSelection = getCurrentSelection();
            for (int i = 0, focused = getFocusedElements(); i < focused; i++) {
                int index = currentSelection + i;
                if (index > upgrades.size() - 1) {
                    break;
                }
                Upgrade upgrade = upgrades.get(index);
                int multipliedElement = elementHeight * i;
                if (mouseY >= getY() + 1 + multipliedElement && mouseY < getY() + 1 + multipliedElement + elementHeight) {
                    displayTooltip(new TextComponentString(upgrade.getDescription()), mouseX, mouseY, getGuiWidth());
                    return;
                }
            }
        }
    }

    @Override
    protected void renderElements(int mouseX, int mouseY, float partialTicks) {
        if (hasSelection() && component.getUpgrades(getSelection()) == 0) {
            clearSelection();
        }
        super.renderElements(mouseX, mouseY, partialTicks);
    }

    @Override
    public void syncFrom(GuiElement element) {
        super.syncFrom(element);
        GuiUpgradeScrollList old = (GuiUpgradeScrollList) element;
        selectedType = old.selectedType;
        onSelectionChange.run();
    }

}
