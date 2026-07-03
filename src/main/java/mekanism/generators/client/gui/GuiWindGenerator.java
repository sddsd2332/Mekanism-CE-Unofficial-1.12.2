package mekanism.generators.client.gui;

import mekanism.api.EnumColor;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.generators.client.gui.element.GuiStateTexture;
import mekanism.generators.common.MekanismGenerators;
import mekanism.generators.common.inventory.container.ContainerWindGenerator;
import mekanism.generators.common.tile.TileEntityWindGenerator;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

import java.util.ArrayList;
import java.util.List;

public class GuiWindGenerator extends GuiGenerator<TileEntityWindGenerator, ContainerWindGenerator> {

    public GuiWindGenerator(InventoryPlayer inventory, TileEntityWindGenerator tile) {
        super(tile, new ContainerWindGenerator(inventory, tile));
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiInnerScreen(this, 48, 21, 80, 44, this::getScreenText));
        addButton(new GuiEnergyTab(this, () -> getEnergyTabText(tileEntity.getActive() ? tileEntity.getEnergyAdd() : 0)));
        addButton(new GuiVerticalPowerBar(this, tileEntity.getEnergyContainer(), 164, 15));
        addButton(new GuiStateTexture(this, 18, 35, tileEntity::getActive,
              new ResourceLocation(MekanismGenerators.MODID, "gui/wind_on.png"),
              new ResourceLocation(MekanismGenerators.MODID, "gui/wind_off.png")));
    }

    private List<ITextComponent> getScreenText() {
        List<ITextComponent> list = new ArrayList<>();
        list.add(energy(tileEntity.getEnergy(), tileEntity.getMaxEnergy()));
        list.add(text(LangUtils.localize("gui.power") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getActive() ? tileEntity.getEnergyAdd() : 0) + "/t"));
        list.add(text(LangUtils.localize("gui.out") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getMaxOutput()) + "/t"));
        if (!tileEntity.getActive()) {
            String key = tileEntity.isBlacklistDimension() ? "gui.noWind" : "gui.skyBlocked";
            list.add(text(EnumColor.DARK_RED + LangUtils.localize(key)));
        }
        return list;
    }
}
