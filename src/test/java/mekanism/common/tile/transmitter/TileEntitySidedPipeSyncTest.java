package mekanism.common.tile.transmitter;

import mekanism.api.NBTConstants;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.block.states.BlockStateTransmitter.TransmitterType;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression coverage for transmitter connection rendering after a chunk is sent to a client. */
class TileEntitySidedPipeSyncTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void syncsTransientConnectionMasksWithoutPersistingThem() {
        TestPipe serverPipe = new TestPipe();
        serverPipe.currentTransmitterConnections = 0b00_1011;
        serverPipe.currentAcceptorConnections = 0b11_0100;

        NBTTagCompound updateData = serverPipe.createUpdateData();
        assertTrue(updateData.hasKey(NBTConstants.CURRENT_CONNECTIONS));
        assertTrue(updateData.hasKey(NBTConstants.CURRENT_ACCEPTORS));

        TestPipe clientPipe = new TestPipe();
        clientPipe.applyUpdateData(updateData);
        assertEquals(serverPipe.currentTransmitterConnections, clientPipe.currentTransmitterConnections);
        assertEquals(serverPipe.currentAcceptorConnections, clientPipe.currentAcceptorConnections);

        NBTTagCompound persistentData = new NBTTagCompound();
        serverPipe.writeCustomNBT(persistentData);
        assertFalse(persistentData.hasKey(NBTConstants.CURRENT_CONNECTIONS));
        assertFalse(persistentData.hasKey(NBTConstants.CURRENT_ACCEPTORS));
    }

    private static final class TestPipe extends TileEntitySidedPipe {

        private NBTTagCompound createUpdateData() {
            NBTTagCompound data = new NBTTagCompound();
            writeUpdateNBT(data);
            return data;
        }

        private void applyUpdateData(NBTTagCompound data) {
            readUpdateNBT(data);
        }

        @Override
        public TransmitterType getTransmitterType() {
            return TransmitterType.MECHANICAL_PIPE;
        }

        @Override
        public boolean isValidAcceptor(TileEntity tile, EnumFacing side) {
            return false;
        }

        @Override
        public void onWorldJoin() {
        }

        @Override
        public void onWorldSeparate() {
        }

        @Override
        public TransmissionType getTransmissionType() {
            return TransmissionType.FLUID;
        }
    }
}
