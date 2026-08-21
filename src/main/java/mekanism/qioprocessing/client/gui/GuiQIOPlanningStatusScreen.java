package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingPreviewSnapshot;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/** Compact planning status display with one optional, horizontally scrolling detail row. */
/**
 * QIO 处理模块中的 GuiQIOPlanningStatusScreen 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class GuiQIOPlanningStatusScreen extends GuiInnerScreen {

    private static final float TEXT_SCALE = 0.8F;
    private static final int PADDING = 2;
    private static final float DETAIL_GAP = 2F;

    private final Supplier<Content> contentSupplier;
    private final boolean centerVertically;
    private String marqueeKey = "";
    private long marqueeStartedAt;

    public GuiQIOPlanningStatusScreen(IGuiWrapper gui, int x, int y, int width, int height,
          boolean centerVertically, Supplier<Content> contentSupplier) {
        super(gui, x, y, width, height);
        this.centerVertically = centerVertically;
        this.contentSupplier = Objects.requireNonNull(contentSupplier, "contentSupplier");
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        Content content = contentSupplier.get();
        if (content == null) {
            resetMarquee();
            return;
        }
        int rows = content.lines.size() + (content.detail == null ? 0 : 1);
        if (rows == 0) {
            resetMarquee();
            return;
        }

        int lineHeight = getFont().FONT_HEIGHT;
        float drawY = centerVertically ?
              relativeY + Math.max(0, (getHeight() - rows * lineHeight) / 2F) :
              relativeY + PADDING;
        float minX = relativeX + PADDING;
        float maxX = relativeX + getWidth() - PADDING;
        for (ITextComponent line : content.lines) {
            drawScaledScrollingString(line, minX, drawY, maxX, drawY + lineHeight,
                  TextAlignment.LEFT, screenTextColor(), false, TEXT_SCALE, getTimeOpened());
            drawY += lineHeight;
        }
        if (content.detail != null) {
            String nextKey = (content.detailPrefix == null ? "" :
                  content.detailPrefix.getFormattedText()) + '\u0000' +
                  content.detail.getFormattedText() + '\u0000' + (maxX - minX);
            long now = getMillis();
            if (!nextKey.equals(marqueeKey)) {
                marqueeKey = nextKey;
                marqueeStartedAt = now;
            }
            gui().drawPrefixedMarqueeString(content.detailPrefix, content.detail,
                  minX, drawY, maxX, drawY + lineHeight, screenTextColor(), false,
                  TEXT_SCALE, DETAIL_GAP, Math.max(0, now - marqueeStartedAt));
        } else {
            resetMarquee();
        }
    }

    private void resetMarquee() {
        marqueeKey = "";
        marqueeStartedAt = 0;
    }

    static ITextComponent duration(long nanos, boolean pending) {
        return pending ? new TextComponentTranslation(
              "gui.mekanismqioprocessing.order_planning_time_pending") :
              new TextComponentString(formatDuration(nanos));
    }

    static ITextComponent detailedTimings(QIOSmartProcessingPreviewSnapshot snapshot,
          boolean pending) {
        return new TextComponentTranslation("gui.mekanismqioprocessing.order_timing_details",
              new TextComponentTranslation(
                    "gui.mekanismqioprocessing.order_main_preparation_time",
                    duration(snapshot.getMainThreadPreparationNanos(), false)),
              new TextComponentTranslation(
                    "gui.mekanismqioprocessing.order_route_preparation_time",
                    duration(snapshot.getRoutePreparationNanos(), pending)),
              new TextComponentTranslation(
                    "gui.mekanismqioprocessing.order_scheduling_time",
                    duration(snapshot.getSchedulingNanos(), pending)),
              new TextComponentTranslation(
                    "gui.mekanismqioprocessing.order_total_time",
                    duration(snapshot.getTotalNanos(), pending)));
    }

    static ITextComponent appendDetail(ITextComponent detail, @Nullable ITextComponent extra) {
        if (extra == null || extra.getFormattedText().isEmpty()) return detail;
        return new TextComponentString(detail.getFormattedText() + "  " +
              extra.getFormattedText());
    }

    private static String formatDuration(long nanos) {
        if (nanos < 1_000_000L) return "<1 ms";
        if (nanos < 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.2f ms", nanos / 1_000_000D);
        }
        return String.format(Locale.ROOT, "%.2f s", nanos / 1_000_000_000D);
    }

    public static final class Content {

        private final List<ITextComponent> lines;
        @Nullable private final ITextComponent detailPrefix;
        @Nullable private final ITextComponent detail;

        private Content(List<ITextComponent> lines, @Nullable ITextComponent detailPrefix,
              @Nullable ITextComponent detail) {
            this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
            this.detailPrefix = detailPrefix;
            this.detail = detail;
        }

        static Content lines(@Nonnull List<ITextComponent> lines) {
            return new Content(Objects.requireNonNull(lines, "lines"), null, null);
        }

        static Content detail(@Nonnull List<ITextComponent> lines,
              @Nonnull ITextComponent detailPrefix, @Nonnull ITextComponent detail) {
            return new Content(Objects.requireNonNull(lines, "lines"),
                  Objects.requireNonNull(detailPrefix, "detailPrefix"),
                  Objects.requireNonNull(detail, "detail"));
        }
    }
}
