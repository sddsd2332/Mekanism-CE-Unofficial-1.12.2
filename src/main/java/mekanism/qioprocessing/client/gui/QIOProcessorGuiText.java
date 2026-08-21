package mekanism.qioprocessing.client.gui;

import mekanism.qioprocessing.common.content.processor.QIOProcessorLaneRuntime;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * QIO 处理模块中的 QIOProcessorGuiText 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
final class QIOProcessorGuiText {

    private QIOProcessorGuiText() {
    }

    static ITextComponent laneState(@Nullable QIOProcessorLaneRuntime.State state) {
        String suffix = state == null ? "idle" : state.name().toLowerCase(Locale.ROOT);
        return new TextComponentTranslation(
              "gui.mekanismqioprocessing.processor_lane_" + suffix);
    }
}
