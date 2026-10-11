package mekanism.common.content.gear.mekatool;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import mekanism.api.EnumColor;
import mekanism.api.IIncrementalEnum;
import mekanism.api.gear.ICustomModule;
import mekanism.api.gear.IModule;
import mekanism.api.gear.config.IModuleConfigItem;
import mekanism.api.gear.config.ModuleConfigItemCreator;
import mekanism.api.gear.config.ModuleEnumData;
import mekanism.api.math.MathUtils;
import mekanism.api.radial.RadialData;
import mekanism.api.radial.mode.IRadialMode;
import mekanism.api.radial.mode.NestedRadialMode;
import mekanism.api.text.IHasTextComponent;
import mekanism.api.text.ILangEntry;
import mekanism.api.text.TextComponentGroup;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.lib.radial.data.RadialDataHelper;
import mekanism.common.util.Lazy;
import mekanism.common.util.MekanismUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.Consumer;
import java.util.function.Supplier;

@ParametersAreNonnullByDefault
public class ModuleExcavationEscalationUnit implements ICustomModule<ModuleExcavationEscalationUnit> {

    private static final ResourceLocation RADIAL_ID = Mekanism.rl("excavation_mode");
    private static final Int2ObjectMap<Lazy<NestedRadialMode>> RADIAL_DATAS = make(() -> {
        int types = ExcavationMode.MODES.length - 2;
        Int2ObjectMap<Lazy<NestedRadialMode>> map = new Int2ObjectArrayMap<>(types);
        for (int type = 1; type <= types; type++) {
            int accessibleValues = type + 2;
            map.put(type, Lazy.of(() -> new NestedRadialMode(RadialDataHelper.INSTANCE.dataForTruncated(RADIAL_ID, accessibleValues, ExcavationMode.NORMAL),
                    MekanismLang.RADIAL_EXCAVATION_SPEED, ExcavationMode.NORMAL.icon(), EnumColor.YELLOW)));
        }
        return map;
    });

    public static <T> T make(Supplier<T> pSupplier) {
        return pSupplier.get();
    }

    private IModuleConfigItem<ExcavationMode> excavationMode;

    @Override
    public void init(IModule<ModuleExcavationEscalationUnit> module, ModuleConfigItemCreator configItemCreator) {
        excavationMode = configItemCreator.createConfigItem("excavation_mode", MekanismLang.MODULE_EFFICIENCY,
                new ModuleEnumData<>(ExcavationMode.NORMAL, module.getInstalledCount() + 2));
    }


    private NestedRadialMode getNestedData(IModule<ModuleExcavationEscalationUnit> module) {
        return RADIAL_DATAS.get(module.getInstalledCount()).get();
    }

    private RadialData<?> getRadialData(IModule<ModuleExcavationEscalationUnit> module) {
        return getNestedData(module).nestedData();
    }

    @Override
    public void addRadialModes(IModule<ModuleExcavationEscalationUnit> module, @NotNull ItemStack stack, Consumer<NestedRadialMode> adder) {
        adder.accept(getNestedData(module));
    }

    @Nullable
    @Override
    public <MODE extends IRadialMode> MODE getMode(IModule<ModuleExcavationEscalationUnit> module, ItemStack stack, RadialData<MODE> radialData) {
        if (radialData == getRadialData(module)) {
            return (MODE) excavationMode.get();
        }
        return null;
    }

    @Override
    public <MODE extends IRadialMode> boolean setMode(IModule<ModuleExcavationEscalationUnit> module, EntityPlayer player, ItemStack stack, RadialData<MODE> radialData, MODE mode) {
        if (radialData == getRadialData(module)) {
            ExcavationMode newMode = (ExcavationMode) mode;
            if (excavationMode.get() != newMode) {
                excavationMode.set(newMode);
            }
        }
        return false;
    }


    @Override
    public void changeMode(IModule<ModuleExcavationEscalationUnit> module, EntityPlayer player, ItemStack stack, int shift, boolean displayChangeMessage) {
        ExcavationMode currentMode = excavationMode.get();
        ExcavationMode newMode = currentMode.adjust(shift, v -> v.ordinal() < module.getInstalledCount() + 2);
        if (currentMode != newMode) {
            excavationMode.set(newMode);
            if (displayChangeMessage) {
                module.displayModeChange(player, MekanismLang.MODULE_EFFICIENCY.getTranslationKey(), newMode);
            }
        }
    }


    @Override
    public void addHUDStrings(IModule<ModuleExcavationEscalationUnit> module, EntityPlayer player, Consumer<String> hudStringAdder) {
        if (module.isEnabled()) {
            hudStringAdder.accept(MekanismLang.DISASSEMBLER_EFFICIENCY.translateColored(EnumColor.DARK_GREY).getFormattedText() + " " + EnumColor.INDIGO + excavationMode.get().getEfficiency());
        }
    }

    public float getEfficiency() {
        return excavationMode.get().getEfficiency();
    }

    public enum ExcavationMode implements IIncrementalEnum<ExcavationMode>, IHasTextComponent, IRadialMode {
        OFF(MekanismLang.RADIAL_EXCAVATION_SPEED_OFF, 0, EnumColor.WHITE, "speed_off"),
        SLOW(MekanismLang.RADIAL_EXCAVATION_SPEED_SLOW, 4, EnumColor.PINK, "speed_slow"),
        NORMAL(MekanismLang.RADIAL_EXCAVATION_SPEED_NORMAL, 16, EnumColor.BRIGHT_GREEN, "speed_normal"),
        FAST(MekanismLang.RADIAL_EXCAVATION_SPEED_FAST, 32, EnumColor.YELLOW, "speed_fast"),
        SUPER_FAST(MekanismLang.RADIAL_EXCAVATION_SPEED_SUPER, 64, EnumColor.ORANGE, "speed_super"),
        EXTREME(MekanismLang.RADIAL_EXCAVATION_SPEED_EXTREME, 128, EnumColor.RED, "speed_extreme");

        private static final ExcavationMode[] MODES = values();

        private final ResourceLocation icon;
        private final ILangEntry langEntry;
        private final ITextComponent label;
        private final EnumColor color;
        private final int efficiency;

        ExcavationMode(ILangEntry langEntry, int efficiency, EnumColor color, String texture) {
            this.langEntry = langEntry;
            this.efficiency = efficiency;
            this.color = color;
            this.icon = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_RADIAL, texture + ".png");
            this.label = new TextComponentGroup().getString(Integer.toString(efficiency));
        }

        @Nonnull
        @Override
        public ExcavationMode byIndex(int index) {
            return MathUtils.getByIndexMod(MODES, index);
        }

        @Override
        public ITextComponent getTextComponent() {
            return label;
        }

        public int getEfficiency() {
            return efficiency;
        }

        @Override
        public @NotNull ITextComponent sliceName() {
            return langEntry.translateColored(color);
        }

        @NotNull
        @Override
        public ResourceLocation icon() {
            return icon;
        }

        @Override
        public EnumColor color() {
            return color;
        }

    }
}
