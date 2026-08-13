package mekanism.qioprocessing.client.integration;

import mekanism.qioprocessing.common.content.policy.QIOPolicyEntrySnapshot;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Optional client bridge kept free of JEI classes for installations without a recipe viewer. */
public final class QIORecipeViewerBridge {

    public interface Viewer {
        boolean canOpen(@Nonnull QIOPolicyEntrySnapshot policy);

        boolean open(@Nonnull QIOPolicyEntrySnapshot policy);
    }

    @Nullable private static Viewer viewer;

    private QIORecipeViewerBridge() {
    }

    public static synchronized void register(@Nonnull Viewer viewer) {
        QIORecipeViewerBridge.viewer = viewer;
    }

    public static synchronized boolean canOpen(@Nullable QIOPolicyEntrySnapshot policy) {
        return policy != null && viewer != null && viewer.canOpen(policy);
    }

    public static synchronized boolean open(@Nullable QIOPolicyEntrySnapshot policy) {
        return policy != null && viewer != null && viewer.open(policy);
    }
}
