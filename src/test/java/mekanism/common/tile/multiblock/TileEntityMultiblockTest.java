package mekanism.common.tile.multiblock;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileEntityMultiblockTest {

    @Test
    void cacheIdsMustBeCanonicalUuids() {
        String id = UUID.randomUUID().toString();
        assertTrue(TileEntityMultiblock.isValidCacheID(id));
        assertTrue(TileEntityMultiblock.isValidCacheID(id.toUpperCase()));
        assertFalse(TileEntityMultiblock.isValidCacheID(""));
        assertFalse(TileEntityMultiblock.isValidCacheID("not-a-uuid"));
        assertFalse(TileEntityMultiblock.isValidCacheID("1-1-1-1-1"));
    }
}
