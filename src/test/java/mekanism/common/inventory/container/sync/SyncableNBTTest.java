package mekanism.common.inventory.container.sync;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import mekanism.common.TestBootstrap;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.ISyncableData.DirtyType;
import mekanism.common.network.to_client.container.property.PropertyData;
import mekanism.common.network.to_client.container.property.PropertyType;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SyncableNBTTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void nbtPropertyRoundTripsThroughTheContainerChannel() {
        NBTTagCompound initial = new NBTTagCompound();
        initial.setString("mode", "OUTPUT_ONLY");
        initial.setLong("revision", 42);
        AtomicReference<NBTTagCompound> sourceValue = new AtomicReference<>(initial);
        SyncableNBT source = SyncableNBT.create(sourceValue::get, sourceValue::set);

        assertEquals(DirtyType.DIRTY, source.isDirty());
        assertEquals(DirtyType.CLEAN, source.isDirty());
        NBTTagCompound detached = source.get();
        detached.setBoolean("clientOnly", true);
        assertFalse(sourceValue.get().hasKey("clientOnly"));

        AtomicReference<NBTTagCompound> targetValue = new AtomicReference<>(new NBTTagCompound());
        SyncableNBT target = SyncableNBT.create(targetValue::get, targetValue::set);
        TestContainer targetContainer = new TestContainer(target);
        ByteBuf buffer = Unpooled.buffer();
        try {
            source.getPropertyData((short) 0, DirtyType.DIRTY).writeToPacket(buffer);
            PropertyData decoded = PropertyData.fromBuffer(buffer);
            assertEquals(PropertyType.NBT, decoded.getType());
            decoded.handleWindowProperty(targetContainer);
        } finally {
            buffer.release();
        }

        assertEquals(initial, targetValue.get());
        initial.setLong("revision", 43);
        assertEquals(42, targetValue.get().getLong("revision"));
        assertEquals(DirtyType.DIRTY, source.isDirty());
    }

    private static final class TestContainer extends MekanismContainer {

        private TestContainer(ISyncableData data) {
            super(null);
            track(data);
        }
    }
}
