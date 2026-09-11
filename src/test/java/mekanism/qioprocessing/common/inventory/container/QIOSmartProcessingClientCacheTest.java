package mekanism.qioprocessing.common.inventory.container;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingResourceEntry;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingResourceFilter;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOSmartProcessingClientCacheTest {

    @Test
    void resolvesAUniqueCapabilityNeutralResourceAndReturnsTheStoredDescriptor() {
        QIOSmartProcessingClientCache cache = loadedCache(
              resource("example:drive", "held"));
        UUID nonce = cacheNonce(cache);
        PortableResourceDescriptor neutral = resource("example:drive", null)
              .withoutCapabilities();

        assertTrue(cache.canSelectRecipeViewerTarget(nonce, neutral));
        assertTrue(cache.selectRecipeViewerTarget(nonce, neutral));
        assertEquals(resource("example:drive", "held"), cache.getRecipeViewerTarget());
    }

    @Test
    void rejectsAnAmbiguousNeutralTargetButKeepsExactCapabilitySelection() {
        PortableResourceDescriptor first = resource("example:drive", "first");
        PortableResourceDescriptor second = resource("example:drive", "second");
        QIOSmartProcessingClientCache cache = loadedCache(first, second);
        UUID nonce = cacheNonce(cache);

        assertFalse(cache.canSelectRecipeViewerTarget(nonce,
              first.withoutCapabilities()));
        assertTrue(cache.selectRecipeViewerTarget(nonce, second));
        assertEquals(second, cache.getRecipeViewerTarget());
    }

    @Test
    void ignoresNonSchedulableRowsDuringNeutralResolution() {
        PortableResourceDescriptor resource = resource("example:drive", "stored");
        QIOSmartProcessingClientCache cache = new QIOSmartProcessingClientCache();
        UUID nonce = UUID.randomUUID();
        cache.beginSession(nonce);
        UUID request = UUID.randomUUID();
        assertTrue(cache.expectPageRequest(request, QIOSmartProcessingResourceFilter.ALL, "", 0));
        assertTrue(cache.applyPage(nonce, request, QIOSmartProcessingResourceFilter.ALL, "",
              1, 0, 2, Arrays.asList(
                    new QIOSmartProcessingResourceEntry(resource, 1, 0, 0, 0, false, false),
                    new QIOSmartProcessingResourceEntry(resource("example:other", null),
                          1, 0, 0, 0, true, false))));

        assertNull(cache.resolveRecipeViewerTarget(nonce, resource.withoutCapabilities()));
    }

    private static QIOSmartProcessingClientCache loadedCache(
          PortableResourceDescriptor... resources) {
        QIOSmartProcessingClientCache cache = new QIOSmartProcessingClientCache();
        cache.beginSession(TestNonceHolder.NONCE);
        UUID request = UUID.randomUUID();
        assertTrue(cache.expectPageRequest(request, QIOSmartProcessingResourceFilter.ALL, "", 0));
        QIOSmartProcessingResourceEntry[] entries = new QIOSmartProcessingResourceEntry[
              resources.length];
        for (int index = 0; index < resources.length; index++) {
            entries[index] = new QIOSmartProcessingResourceEntry(resources[index], 1, 0, 0, 0,
                  true, false);
        }
        assertTrue(cache.applyPage(TestNonceHolder.NONCE, request,
              QIOSmartProcessingResourceFilter.ALL, "", 1,
              0, entries.length, Arrays.asList(entries)));
        return cache;
    }

    private static UUID cacheNonce(QIOSmartProcessingClientCache cache) {
        // The production cache deliberately does not expose its session nonce.  The setup helper
        // uses one deterministic nonce so requests can be validated without reflection.
        return TestNonceHolder.NONCE;
    }

    private static PortableResourceDescriptor resource(String name, String capability) {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("kind", PortableResourceDescriptor.Kind.ITEM.name());
        data.setString("registryName", name);
        data.setInteger("metadata", 0);
        if (capability != null) {
            NBTTagCompound caps = new NBTTagCompound();
            caps.setString("value", capability);
            data.setTag("capabilities", caps);
        }
        return PortableResourceDescriptor.read(data);
    }

    private static final class TestNonceHolder {
        private static final UUID NONCE = UUID.randomUUID();
    }
}
