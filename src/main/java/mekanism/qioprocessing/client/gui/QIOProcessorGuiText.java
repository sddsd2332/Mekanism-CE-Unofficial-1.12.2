package mekanism.qioprocessing.client.gui;

import mekanism.qioprocessing.common.content.processor.QIOProcessorLaneRuntime;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;
import java.util.Locale;

final class QIOProcessorGuiText {

    private QIOProcessorGuiText() {
    }

    static ITextComponent laneState(@Nullable QIOProcessorLaneRuntime.State state) {
        String suffix = state == null ? "idle" : state.name().toLowerCase(Locale.ROOT);
        return new TextComponentTranslation(
              "gui.mekanismqioprocessing.processor_lane_" + suffix);
    }
}
