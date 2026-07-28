package mekanism.api.qio.external;

@FunctionalInterface
public interface IQIOStorageListener {

    /** Called on the server thread after QIO changes have been merged for a tick. */
    void onQIOStorageChanged(QIOStorageChangeBatch changes);
}
