package mekanism.common.tile.component.config;

import mekanism.api.EnumColor;
import mekanism.api.IIncrementalEnum;
import mekanism.api.math.MathUtils;
import mekanism.common.util.LangUtils;

public enum DataType implements IIncrementalEnum<DataType> {
    NONE("None", EnumColor.GREY),
    INPUT("Input", EnumColor.RED),
    INPUT_ENHANCED("Input_Enhance", EnumColor.BRIGHT_GREEN),
    INPUT_1("Input_1", EnumColor.DARK_RED),
    INPUT_2("Input_2", EnumColor.ORANGE),
    OUTPUT("Output", EnumColor.INDIGO),
    OUTPUT_1("Output_1", EnumColor.DARK_BLUE),
    OUTPUT_2("Output_2", EnumColor.AQUA),
    INPUT_OUTPUT("Input_Output", EnumColor.PURPLE),
    INPUT_EXTRA("Input_Extra", EnumColor.PINK),
    INPUT_EXTRA_OUTPUT("Input_Extra_Output", EnumColor.BRIGHT_PINK),
    ENERGY("Energy", EnumColor.BRIGHT_GREEN),
    EXTRA("Extra", EnumColor.YELLOW),
    GAS("Gas", EnumColor.INDIGO),
    FLUID("Fluid", EnumColor.DARK_AQUA),
    EMPTY("Empty", EnumColor.BLACK);

    private static final DataType[] TYPES = values();
    private final EnumColor color;
    private final String name;

    DataType(String langEntry, EnumColor color) {
        this.color = color;
        this.name = langEntry;
    }

    public EnumColor getColor() {
        return color;
    }

    public String getName() {
        return name;
    }

    public String localize() {
        return LangUtils.localize("sideData." + name);
    }

    @Override
    public DataType byIndex(int index) {
        return byIndexStatic(index);
    }

    public boolean canOutput() {
        return this == OUTPUT || this == OUTPUT_1 || this == OUTPUT_2 || this == INPUT_OUTPUT || this == INPUT_EXTRA_OUTPUT || this == GAS || this == FLUID;
    }

    public boolean canInput() {
        return this == INPUT || canAutoPull() || this == INPUT_1 || this == INPUT_2 || this == INPUT_OUTPUT || this == INPUT_EXTRA || this == INPUT_EXTRA_OUTPUT
              || this == ENERGY || this == EXTRA || this == GAS || this == FLUID;
    }

    public boolean canAutoPull() {
        return this == INPUT_ENHANCED;
    }

    public static DataType byIndexStatic(int index) {
        return MathUtils.getByIndexMod(TYPES, index);
    }

}
