package mekanism.qioprocessing.common.content.processor;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOProcessorDisplaySnapshotTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void displaySnapshotRoundTripsOnlyBoundedRecipeData() throws Exception {
        List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        grid.set(0, new ItemStack(Blocks.STONE, 32));
        grid.set(4, new ItemStack(Blocks.GLASS));
        QIOProcessorDisplaySnapshot.Lane lane = new QIOProcessorDisplaySnapshot.Lane(2,
              QIOProcessorLaneRuntime.State.PROCESSING, 576, "minecraft:test_route",
              grid, new ItemStack(Blocks.REDSTONE_BLOCK, 4));

        QIOProcessorDisplaySnapshot restored = QIOProcessorDisplaySnapshot.read(
              new QIOProcessorDisplaySnapshot(9, Collections.singletonList(lane)).write());
        QIOProcessorDisplaySnapshot.Lane restoredLane = restored.getLane(2);

        assertEquals(9, restored.getLaneCount());
        assertEquals(QIOProcessorLaneRuntime.State.PROCESSING, restoredLane.getState());
        assertEquals(576, restoredLane.getOperationCount());
        assertEquals("minecraft:test_route", restoredLane.getRouteKey());
        assertEquals(1, restoredLane.getGridStack(0).getCount());
        assertTrue(ItemStack.areItemsEqual(new ItemStack(Blocks.GLASS),
              restoredLane.getGridStack(4)));
        assertEquals(4, restoredLane.getOutput().getCount());
    }

    @Test
    void displaySnapshotRejectsUnboundedOrAmbiguousLaneData() {
        List<ItemStack> emptyGrid = Collections.nCopies(9, ItemStack.EMPTY);
        QIOProcessorDisplaySnapshot.Lane lane = new QIOProcessorDisplaySnapshot.Lane(0,
              QIOProcessorLaneRuntime.State.READY, 1, "test:route", emptyGrid,
              ItemStack.EMPTY);

        assertThrows(IllegalArgumentException.class, () ->
              new QIOProcessorDisplaySnapshot(65, Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () ->
              new QIOProcessorDisplaySnapshot(1, Arrays.asList(lane, lane)));
        assertThrows(IllegalArgumentException.class, () ->
              new QIOProcessorDisplaySnapshot(0, Collections.singletonList(lane)));
    }

    @Test
    void displaySnapshotRejectsMalformedWirePayload() {
        NBTTagCompound malformed = QIOProcessorDisplaySnapshot.empty(1).write();
        malformed.setInteger("laneCount", 65);
        assertThrows(QIOProcessingDataException.class, () ->
              QIOProcessorDisplaySnapshot.read(malformed));
    }
}
