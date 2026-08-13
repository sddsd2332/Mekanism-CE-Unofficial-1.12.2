package mekanism.common.content.qio;

import mekanism.api.qio.external.IQIOFrequencyLifecycleListener;
import mekanism.api.qio.external.QIOFrequencyDeleteCheck;
import mekanism.api.qio.external.QIOFrequencyReference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Core-owned listener registry used by modules with frequency-level persistent state. */
public final class QIOFrequencyLifecycleRegistry {

    private static final Set<IQIOFrequencyLifecycleListener> LISTENERS =
          Collections.newSetFromMap(new IdentityHashMap<>());

    private QIOFrequencyLifecycleRegistry() {
    }

    public static synchronized boolean register(@Nonnull IQIOFrequencyLifecycleListener listener) {
        return listener != null && LISTENERS.add(listener);
    }

    public static synchronized boolean unregister(@Nonnull IQIOFrequencyLifecycleListener listener) {
        return listener != null && LISTENERS.remove(listener);
    }

    @Nonnull
    public static QIOFrequencyDeleteCheck beforeDelete(@Nonnull QIOFrequencyReference reference,
          @Nullable UUID requester) {
        List<String> blockers = new ArrayList<>();
        for (IQIOFrequencyLifecycleListener listener : snapshot()) {
            try {
                QIOFrequencyDeleteCheck check = listener.beforeDelete(reference, requester);
                if (check == null) {
                    blockers.add("invalid_listener_result:" + listener.getClass().getName());
                } else {
                    blockers.addAll(check.getBlockers());
                }
            } catch (RuntimeException e) {
                QIOLog.LOGGER.error("A QIO frequency lifecycle listener failed before deletion", e);
                blockers.add("listener_failure:" + listener.getClass().getName());
            }
        }
        return blockers.isEmpty() ? QIOFrequencyDeleteCheck.allowed() :
              QIOFrequencyDeleteCheck.blocked(blockers);
    }

    public static void afterDelete(@Nonnull QIOFrequencyReference reference,
          @Nullable UUID requester) {
        for (IQIOFrequencyLifecycleListener listener : snapshot()) {
            try {
                listener.afterDelete(reference, requester);
            } catch (RuntimeException e) {
                QIOLog.LOGGER.error("A QIO frequency lifecycle listener failed after deletion", e);
            }
        }
    }

    private static synchronized List<IQIOFrequencyLifecycleListener> snapshot() {
        return new ArrayList<>(LISTENERS);
    }
}
