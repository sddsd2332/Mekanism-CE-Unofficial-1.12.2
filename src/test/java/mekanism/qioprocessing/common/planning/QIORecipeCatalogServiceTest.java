package mekanism.qioprocessing.common.planning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIORecipeCatalogServiceTest {

    @Test
    void allocatesOnlyOneFollowUpEpochPerActiveCapture() {
        assertFalse(QIORecipeCatalogService.hasQueuedFollowUpEpoch(false, 4, 5));
        assertFalse(QIORecipeCatalogService.hasQueuedFollowUpEpoch(true, 4, 4));
        assertTrue(QIORecipeCatalogService.hasQueuedFollowUpEpoch(true, 4, 5));
        assertTrue(QIORecipeCatalogService.hasQueuedFollowUpEpoch(true, 4, 8));
    }
}
