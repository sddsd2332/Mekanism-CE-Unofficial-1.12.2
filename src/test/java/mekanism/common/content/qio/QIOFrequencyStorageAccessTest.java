package mekanism.common.content.qio;

import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.IQIOFrequencyLifecycleListener;
import mekanism.api.qio.external.QIOFrequencyDeleteCheck;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.TestBootstrap;
import mekanism.common.frequency.FrequencyManager;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.security.ISecurityTile.SecurityMode;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOFrequencyStorageAccessTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    private File worldDirectory;

    @BeforeEach
    void setup() throws Exception {
        FrequencyManager.reset();
        worldDirectory = Files.createTempDirectory("qio-frequency-access-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
    }

    @AfterEach
    void cleanup() throws Exception {
        FrequencyManager.reset();
        QIODriveStorage.INSTANCE.reset();
        QIOResourceTypeRegistry.INSTANCE.reset();
        delete(worldDirectory);
    }

    @Test
    void referenceRoundTripsItsStableIdentity() {
        UUID owner = UUID.randomUUID();
        QIOFrequency frequency = addFrequency("private", owner, SecurityMode.PRIVATE);
        QIOFrequencyReference reference = QIOFrequencyStorageAccess.INSTANCE.createReference(
              frequency, owner);

        assertEquals(reference, QIOFrequencyReference.read(reference.write()));
        assertEquals(frequency.getFrequencyUUID(), reference.getFrequencyUUID());
        assertEquals(owner, reference.getBindingPlayerUUID());
    }

    @Test
    void frequencyReferenceAppliesSecurityWithoutRequiringADashboard() {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        QIOFrequency frequency = addFrequency("private", owner, SecurityMode.PRIVATE);
        QIOFrequencyReference reference = QIOFrequencyStorageAccess.INSTANCE.createReference(
              frequency, owner);

        IQIOStorageView ownerView = QIOFrequencyStorageAccess.INSTANCE.open(reference, owner);

        assertNotNull(ownerView);
        assertTrue(ownerView.isValid());
        assertEquals(frequency.getFrequencyUUID(), ownerView.getSnapshot().getFrequencyUUID());
        assertNull(QIOFrequencyStorageAccess.INSTANCE.open(reference, stranger));
        assertNull(QIOFrequencyStorageAccess.INSTANCE.open(reference, null));
    }

    @Test
    void sameNameReplacementCannotInheritAnOldReference() {
        UUID owner = UUID.randomUUID();
        FrequencyManager<QIOFrequency> manager = FrequencyType.QIO.getManager(owner,
              SecurityMode.PRIVATE);
        assertNotNull(manager);
        QIOFrequency original = new QIOFrequency("replace", owner, SecurityMode.PRIVATE);
        manager.addFrequency(original);
        QIOFrequencyReference reference = QIOFrequencyStorageAccess.INSTANCE.createReference(
              original, owner);
        IQIOStorageView oldView = QIOFrequencyStorageAccess.INSTANCE.open(reference, owner);
        assertNotNull(oldView);
        AtomicBoolean invalidated = new AtomicBoolean();
        assertTrue(oldView.addListener(changes -> invalidated.set(changes.isInvalidated())));

        assertTrue(manager.remove("replace", owner));
        QIOFrequency replacement = new QIOFrequency("replace", owner, SecurityMode.PRIVATE);
        manager.addFrequency(replacement);

        assertFalse(oldView.isValid());
        assertTrue(invalidated.get());
        assertNull(QIOFrequencyStorageAccess.INSTANCE.open(reference, owner));
        assertFalse(reference.getFrequencyUUID().equals(replacement.getFrequencyUUID()));
    }

    @Test
    void malformedReferenceIsRejected() {
        NBTTagCompound malformed = new NBTTagCompound();
        malformed.setString("frequencyUUID", "not-a-uuid");
        malformed.setTag("lastKnownIdentity", new NBTTagCompound());

        assertThrows(IllegalArgumentException.class, () -> QIOFrequencyReference.read(malformed));
    }

    @Test
    void lifecycleListenerCanVetoNormalDeletionAndObservesSuccessfulDeletion() {
        UUID owner = UUID.randomUUID();
        FrequencyManager<QIOFrequency> manager = FrequencyType.QIO.getManager(owner,
              SecurityMode.PRIVATE);
        assertNotNull(manager);
        QIOFrequency frequency = new QIOFrequency("guarded", owner, SecurityMode.PRIVATE);
        manager.addFrequency(frequency);
        AtomicBoolean blocked = new AtomicBoolean(true);
        AtomicBoolean deleted = new AtomicBoolean();
        IQIOFrequencyLifecycleListener listener = new IQIOFrequencyLifecycleListener() {
            @Override
            public QIOFrequencyDeleteCheck beforeDelete(QIOFrequencyReference reference,
                  UUID requester) {
                return blocked.get() ? QIOFrequencyDeleteCheck.blocked("test_asset") :
                      QIOFrequencyDeleteCheck.allowed();
            }

            @Override
            public void afterDelete(QIOFrequencyReference reference, UUID requester) {
                deleted.set(true);
            }
        };
        assertTrue(QIOFrequencyLifecycleRegistry.register(listener));
        try {
            assertFalse(manager.remove("guarded", owner));
            assertSame(frequency, manager.getFrequency("guarded"));
            assertFalse(deleted.get());

            blocked.set(false);
            assertTrue(manager.remove("guarded", owner));
            assertNull(manager.getFrequency("guarded"));
            assertTrue(deleted.get());
        } finally {
            QIOFrequencyLifecycleRegistry.unregister(listener);
        }
    }

    private static QIOFrequency addFrequency(String name, UUID owner, SecurityMode securityMode) {
        FrequencyManager<QIOFrequency> manager = FrequencyType.QIO.getManager(owner, securityMode);
        assertNotNull(manager);
        QIOFrequency frequency = new QIOFrequency(name, owner, securityMode);
        manager.addFrequency(frequency);
        return frequency;
    }

    private static void delete(File file) throws Exception {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    delete(child);
                }
            }
        }
        Files.deleteIfExists(file.toPath());
    }
}
