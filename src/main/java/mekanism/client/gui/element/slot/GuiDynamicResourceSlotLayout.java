package mekanism.client.gui.element.slot;

/**
 * Pure layout and marquee calculations used by {@link GuiDynamicResourceSlot}.
 *
 * <p>This class deliberately has no Minecraft or OpenGL dependencies so the
 * geometry and timing rules can be tested without starting the client.</p>
 */
final class GuiDynamicResourceSlotLayout {

    static final int RESOURCE_SIZE = 16;
    static final int DEFAULT_RESOURCE_GAP = 2;
    static final int DEFAULT_RESOURCE_STEP = RESOURCE_SIZE + DEFAULT_RESOURCE_GAP;
    /** @deprecated Use {@link #DEFAULT_RESOURCE_STEP}. */
    @Deprecated
    static final int RESOURCE_STEP = DEFAULT_RESOURCE_STEP;
    private static final int FRAME_BORDER_SIZE = 1;
    private static final int MINIMUM_FRAME_WIDTH = RESOURCE_SIZE + FRAME_BORDER_SIZE * 2;
    private static final int MAX_VISIBLE_COUNT = Integer.MAX_VALUE / DEFAULT_RESOURCE_STEP;
    static final double SCROLL_PIXELS_PER_SECOND = 12D;
    static final double EDGE_PAUSE_SECONDS = 0.5D;

    private GuiDynamicResourceSlotLayout() {
    }

    static int normalizeVisibleCount(int count) {
        return Math.max(1, Math.min(MAX_VISIBLE_COUNT, count));
    }

    static int validateGap(int gap) {
        if (gap < 0) {
            throw new IllegalArgumentException("Resource gap cannot be negative: " + gap);
        }
        return gap;
    }

    static int validateFrameWidth(int frameWidth) {
        if (frameWidth < MINIMUM_FRAME_WIDTH) {
            throw new IllegalArgumentException("Frame width must be at least " + MINIMUM_FRAME_WIDTH +
                  ": " + frameWidth);
        }
        return frameWidth;
    }

    static int frameDimension(int visibleCount, int gap) {
        int count = normalizeVisibleCount(visibleCount);
        int checkedGap = validateGap(gap);
        long dimension = FRAME_BORDER_SIZE * 2L + count * (long) RESOURCE_SIZE +
              (count - 1L) * checkedGap;
        if (dimension > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Resource frame dimension is too large: " + dimension);
        }
        return (int) dimension;
    }

    /** Preserves the original two-pixel-gap frame calculation. */
    static int viewportDimension(int visibleCount) {
        return frameDimension(visibleCount, DEFAULT_RESOURCE_GAP);
    }

    static int innerWidth(int frameWidth) {
        return validateFrameWidth(frameWidth) - FRAME_BORDER_SIZE * 2;
    }

    static int effectiveVisibleColumns(int frameWidth, int minimumGap) {
        int checkedGap = validateGap(minimumGap);
        long numerator = (long) innerWidth(frameWidth) + checkedGap;
        long denominator = (long) RESOURCE_SIZE + checkedGap;
        return Math.max(1, (int) Math.min(Integer.MAX_VALUE, numerator / denominator));
    }

    static int contentColumns(int resourceCount, int visibleRows) {
        if (resourceCount <= 0) {
            return 0;
        }
        int rows = normalizeVisibleCount(visibleRows);
        return (resourceCount - 1) / rows + 1;
    }

    /**
     * Returns the integer start position inside the frame. AUTO_FIT extends
     * the same average gap distribution beyond the visible columns.
     */
    static int resourceStart(int column, int frameWidth, int gap, boolean autoFit) {
        if (column < 0) {
            return -1;
        }
        long start = resourceStartLong(column, frameWidth, gap, autoFit);
        return start > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) start;
    }

    private static long resourceStartLong(int column, int frameWidth, int gap, boolean autoFit) {
        int checkedGap = validateGap(gap);
        if (!autoFit) {
            return column * ((long) RESOURCE_SIZE + checkedGap);
        }
        int visibleColumns = effectiveVisibleColumns(frameWidth, checkedGap);
        if (visibleColumns == 1) {
            return column * ((long) RESOURCE_SIZE + checkedGap);
        }
        long totalGapPixels = innerWidth(frameWidth) - visibleColumns * (long) RESOURCE_SIZE;
        return column * (long) RESOURCE_SIZE +
              column * totalGapPixels / (visibleColumns - 1L);
    }

    static int contentWidth(int resourceCount, int visibleRows, int frameWidth, int gap, boolean autoFit) {
        return contentWidthForColumns(contentColumns(resourceCount, visibleRows), frameWidth, gap, autoFit);
    }

    static int contentWidthForColumns(int contentColumns, int frameWidth, int gap, boolean autoFit) {
        if (contentColumns <= 0) {
            return 0;
        }
        long width = resourceStartLong(contentColumns - 1, frameWidth, gap, autoFit) + RESOURCE_SIZE;
        return width > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) width;
    }

    /** Preserves the original fixed two-pixel-gap content calculation. */
    static int contentWidth(int resourceCount, int visibleRows) {
        int columns = contentColumns(resourceCount, visibleRows);
        return columns <= 0 ? 0 : (columns - 1) * DEFAULT_RESOURCE_STEP + RESOURCE_SIZE;
    }

    /** Preserves the original fixed two-pixel-gap content calculation. */
    static int contentWidthForColumns(int contentColumns) {
        if (contentColumns <= 0) {
            return 0;
        }
        long width = (long) (contentColumns - 1) * DEFAULT_RESOURCE_STEP + RESOURCE_SIZE;
        return width > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) width;
    }

    /** Preserves the original visible-column viewport calculation. */
    static int viewportWidth(int visibleColumns) {
        return innerWidth(frameDimension(visibleColumns, DEFAULT_RESOURCE_GAP));
    }

    static double maxOffset(int resourceCount, int frameWidth, int visibleRows, int gap, boolean autoFit) {
        return Math.max(0D, contentWidth(resourceCount, visibleRows, frameWidth, gap, autoFit) -
              innerWidth(frameWidth));
    }

    /** Preserves the original fixed two-pixel-gap maximum offset calculation. */
    static double maxOffset(int resourceCount, int visibleColumns, int visibleRows) {
        return maxOffset(resourceCount, frameDimension(visibleColumns, DEFAULT_RESOURCE_GAP), visibleRows,
              DEFAULT_RESOURCE_GAP, false);
    }

    static int columnForIndex(int index, int visibleRows) {
        return index < 0 ? -1 : index / normalizeVisibleCount(visibleRows);
    }

    static int rowForIndex(int index, int visibleRows) {
        return index < 0 ? -1 : index % normalizeVisibleCount(visibleRows);
    }

    static int indexForCell(int column, int row, int visibleRows) {
        if (column < 0 || row < 0 || row >= normalizeVisibleCount(visibleRows)) {
            return -1;
        }
        long index = (long) column * normalizeVisibleCount(visibleRows) + row;
        return index > Integer.MAX_VALUE ? -1 : (int) index;
    }

    /**
     * Gets the offset for a left-to-right, edge-pausing round trip marquee.
     * The elapsed time is in milliseconds and is intentionally not quantized
     * to ticks.
     */
    static double scrollingOffset(double contentWidth, double viewportWidth, long elapsedMillis) {
        double overflow = contentWidth - viewportWidth;
        if (overflow <= 0) {
            return 0D;
        }
        double seconds = Math.max(0L, elapsedMillis) / 1_000D;
        double travelTime = overflow / SCROLL_PIXELS_PER_SECOND;
        double cycleTime = EDGE_PAUSE_SECONDS * 2 + travelTime * 2;
        double cyclePosition = seconds % cycleTime;
        if (cyclePosition < EDGE_PAUSE_SECONDS) {
            return 0D;
        }
        cyclePosition -= EDGE_PAUSE_SECONDS;
        if (cyclePosition < travelTime) {
            return cyclePosition * SCROLL_PIXELS_PER_SECOND;
        }
        if (cyclePosition < travelTime + EDGE_PAUSE_SECONDS) {
            return overflow;
        }
        cyclePosition -= travelTime + EDGE_PAUSE_SECONDS;
        return Math.max(0D, overflow - cyclePosition * SCROLL_PIXELS_PER_SECOND);
    }

    /**
     * Resolves a mouse position relative to the actual outer frame. Rendering
     * and hit testing share {@link #resourceStart(int, int, int, boolean)}, so
     * both fixed and distributed gap pixels are rejected.
     */
    static int hitIndex(int resourceCount, int frameWidth, int visibleRows, int gap, boolean autoFit,
          double localX, double localY, double offset) {
        int checkedFrameWidth = validateFrameWidth(frameWidth);
        int rows = normalizeVisibleCount(visibleRows);
        int checkedGap = validateGap(gap);
        int frameHeight = frameDimension(rows, checkedGap);
        if (resourceCount <= 0 || !Double.isFinite(localX) || !Double.isFinite(localY) ||
              !Double.isFinite(offset) || localX < FRAME_BORDER_SIZE || localY < FRAME_BORDER_SIZE ||
              localX >= checkedFrameWidth - FRAME_BORDER_SIZE || localY >= frameHeight - FRAME_BORDER_SIZE) {
            return -1;
        }

        double contentY = localY - FRAME_BORDER_SIZE;
        long verticalStep = (long) RESOURCE_SIZE + checkedGap;
        int row = (int) Math.floor(contentY / verticalStep);
        long resourceY = row * verticalStep;
        if (row < 0 || row >= rows || contentY < resourceY || contentY >= resourceY + RESOURCE_SIZE) {
            return -1;
        }

        double contentX = localX - FRAME_BORDER_SIZE + Math.max(0D, offset);
        int columns = contentColumns(resourceCount, rows);
        int low = 0;
        int high = columns - 1;
        while (low <= high) {
            int column = low + (high - low) / 2;
            long resourceX = resourceStartLong(column, checkedFrameWidth, checkedGap, autoFit);
            if (contentX < resourceX) {
                high = column - 1;
            } else if (contentX >= resourceX + RESOURCE_SIZE) {
                low = column + 1;
            } else {
                int index = indexForCell(column, row, rows);
                return index >= 0 && index < resourceCount ? index : -1;
            }
        }
        return -1;
    }

    /** Preserves the original fixed two-pixel-gap hit-test signature. */
    static int hitIndex(int resourceCount, int visibleColumns, int visibleRows,
          double localX, double localY, double offset) {
        return hitIndex(resourceCount, frameDimension(visibleColumns, DEFAULT_RESOURCE_GAP), visibleRows,
              DEFAULT_RESOURCE_GAP, false, localX, localY, offset);
    }
}
