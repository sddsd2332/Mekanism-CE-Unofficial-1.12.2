package mekanism.qioprocessing.common.inventory.container;

import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceSnapshot;
import mekanism.qioprocessing.common.terminal.QIOPageCursor;
import mekanism.qioprocessing.common.terminal.QIODeviceCommandResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Client-only ordered cache assembled from revision-consistent management pages. */
public final class QIOManagementDeviceClientCache {

    private final List<QIOAutomationDeviceSnapshot> devices = new ArrayList<>();
    @Nullable
    private String expectedViewKey;
    @Nullable
    private String viewKey;
    @Nullable
    private UUID sessionNonce;
    private long sourceRevision = -1;
    private int totalSize;
    @Nullable
    private QIOPageCursor nextCursor;
    private long pageGeneration;
    private long commandGeneration;
    @Nullable private QIODeviceCommandResult lastCommandResult;

    public void expectView(@Nonnull String requestedViewKey) {
        Objects.requireNonNull(requestedViewKey, "requestedViewKey");
        if (!requestedViewKey.equals(expectedViewKey)) {
            clearPage();
        }
        expectedViewKey = requestedViewKey;
    }

    public boolean apply(@Nonnull UUID responseSessionNonce,
          @Nonnull String responseViewKey, long responseRevision,
          int offset, int responseTotalSize,
          @Nonnull List<QIOAutomationDeviceSnapshot> page,
          @Nullable QIOPageCursor responseNextCursor) {
        if (!Objects.equals(expectedViewKey, responseViewKey) ||
            responseRevision < 0 || offset < 0 || responseTotalSize < 0 ||
            offset > responseTotalSize || page.size() > responseTotalSize - offset) {
            return false;
        }
        if (offset == 0) {
            if (responseSessionNonce.equals(sessionNonce) &&
                responseViewKey.equals(viewKey) && sourceRevision >= 0 &&
                responseRevision < sourceRevision) {
                return false;
            }
            devices.clear();
            viewKey = responseViewKey;
            sessionNonce = responseSessionNonce;
            sourceRevision = responseRevision;
            totalSize = responseTotalSize;
        } else if (!responseSessionNonce.equals(sessionNonce) ||
            !responseViewKey.equals(viewKey) ||
            responseRevision != sourceRevision || responseTotalSize != totalSize ||
            offset != devices.size()) {
            return false;
        }
        devices.addAll(page);
        nextCursor = responseNextCursor;
        pageGeneration = nextGeneration(pageGeneration);
        return true;
    }

    public void clear() {
        clearPage();
        expectedViewKey = null;
        lastCommandResult = null;
    }

    private void clearPage() {
        devices.clear();
        viewKey = null;
        sessionNonce = null;
        sourceRevision = -1;
        totalSize = 0;
        nextCursor = null;
    }

    @Nonnull
    public List<QIOAutomationDeviceSnapshot> getDevices() {
        return Collections.unmodifiableList(new ArrayList<>(devices));
    }

    public long getSourceRevision() {
        return sourceRevision;
    }

    @Nullable
    public String getViewKey() {
        return viewKey;
    }

    public int getTotalSize() {
        return totalSize;
    }

    @Nullable
    public QIOPageCursor getNextCursor() {
        return nextCursor;
    }

    public long getPageGeneration() {
        return pageGeneration;
    }

    public boolean hasPageFor(@Nonnull UUID expectedSessionNonce,
          @Nonnull String expectedTypeKey) {
        return sourceRevision >= 0 && expectedSessionNonce.equals(sessionNonce) &&
              expectedTypeKey.equals(viewKey);
    }

    public boolean applyCommand(@Nonnull UUID responseSessionNonce,
          @Nonnull QIODeviceCommandResult result) {
        if (sessionNonce != null && !sessionNonce.equals(responseSessionNonce)) return false;
        sessionNonce = responseSessionNonce;
        lastCommandResult = result;
        commandGeneration = nextGeneration(commandGeneration);
        return true;
    }

    @Nullable public QIODeviceCommandResult getLastCommandResult() { return lastCommandResult; }
    public long getCommandGeneration() { return commandGeneration; }

    private static long nextGeneration(long generation) {
        return generation == Long.MAX_VALUE ? 0 : generation + 1;
    }
}
