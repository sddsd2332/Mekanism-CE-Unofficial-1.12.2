package mekanism.client.gui.qio;

import mekanism.api.EnumColor;
import mekanism.common.MekanismLang;
import mekanism.common.content.qio.QIOAmount;
import mekanism.common.content.qio.QIOCapacitySummary;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import mekanism.common.util.LangUtils;
import mekanism.common.util.text.TextUtils;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Shared exact-capacity text for every QIO screen. */
public final class QIOGuiCapacityText {

    private QIOGuiCapacityText() {
    }

    @Nonnull
    public static List<ITextComponent> forFrequency(QIOFrequency frequency) {
        if (frequency == null) {
            return Collections.emptyList();
        }
        return create(frequency.getExactTotalCount(), frequency.getTotalTypes(), frequency.getCapacitySummary());
    }

    @Nonnull
    public static List<ITextComponent> forContainer(QIOItemViewerContainer container) {
        if (container == null) {
            return Collections.emptyList();
        }
        return create(container.getExactTotalCount(), container.getTotalTypes(), container.getCapacitySummary());
    }

    @Nonnull
    public static List<ITextComponent> create(QIOAmount stored, int storedTypes, QIOCapacitySummary capacity) {
        QIOCapacitySummary summary = capacity == null ? QIOCapacitySummary.EMPTY : capacity;
        List<ITextComponent> tooltip = new ArrayList<>(4);
        tooltip.add(MekanismLang.QIO_RESOURCES_DETAIL.translateColored(EnumColor.GREY, EnumColor.INDIGO,
              TextUtils.format(stored.toBigInteger()), formatCountCapacity(summary)));
        tooltip.add(MekanismLang.QIO_TYPES_DETAIL.translateColored(EnumColor.GREY, EnumColor.INDIGO,
              TextUtils.format(storedTypes), formatTypeCapacity(summary)));
        if (summary.getUnlimitedCountDrives() > 0) {
            tooltip.add(MekanismLang.QIO_UNLIMITED_COUNT_DRIVES.translateColored(EnumColor.GREY, EnumColor.INDIGO,
                  TextUtils.format(summary.getUnlimitedCountDrives())));
        }
        if (summary.getUnlimitedTypeDrives() > 0) {
            tooltip.add(MekanismLang.QIO_UNLIMITED_TYPE_DRIVES.translateColored(EnumColor.GREY, EnumColor.INDIGO,
                  TextUtils.format(summary.getUnlimitedTypeDrives())));
        }
        return tooltip;
    }

    public static String formatCountCapacity(QIOCapacitySummary summary) {
        return summary.hasUnlimitedCount() ? LangUtils.localize("gui.infinite") :
              TextUtils.format(summary.getFiniteCountCapacity().toBigInteger());
    }

    public static String formatTypeCapacity(QIOCapacitySummary summary) {
        return summary.hasUnlimitedTypes() ? LangUtils.localize("gui.infinite") :
              TextUtils.format(summary.getFiniteTypeCapacity().toBigInteger());
    }
}
