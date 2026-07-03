package mekanism.client.recipe_viewer.jei;

import mezz.jei.api.gui.IDrawable;

import javax.annotation.Nullable;

public class ChemicalStackRenderer extends mekanism.client.jei.gas.GasStackRenderer {

    public ChemicalStackRenderer() {
        super();
    }

    public ChemicalStackRenderer(int capacityMb, boolean showCapacity, int width, int height, @Nullable IDrawable overlay) {
        super(capacityMb, showCapacity, width, height, overlay);
    }
}
