package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.common.PacketHandler;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.network.PacketGuiInteract.GuiInteractMessage;
import mekanism.common.tile.interfaces.IHasDumpButton;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.MekanismUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketGuiInteract implements IMessageHandler<GuiInteractMessage, IMessage> {

    @Override
    public IMessage onMessage(GuiInteractMessage message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            if (!(player.openContainer instanceof MekanismContainer container)) {
                return;
            }
            TileEntity tile = message.coord4D.getTileEntity(player.world);
            if (!PacketHandler.canAccessTile(player, tile)) {
                return;
            }
            switch (message.interaction) {
                case CONTAINER_STOP_TRACKING -> container.stopTracking(message.extra);
                case CONTAINER_TRACK_EJECTOR -> {
                    if (tile instanceof ISideConfiguration sideConfig) {
                        container.startTrackingServer(message.extra, sideConfig.getEjector());
                    }
                }
                case CONTAINER_TRACK_SIDE_CONFIG -> {
                    if (tile instanceof ISideConfiguration sideConfig) {
                        container.startTrackingServer(message.extra, sideConfig.getConfig());
                    }
                }
                case CONTAINER_TRACK_UPGRADES -> {
                    if (tile instanceof TileEntityContainerBlock && tile instanceof IUpgradeTile upgradeTile && upgradeTile.supportsUpgrades()) {
                        container.startTrackingServer(message.extra, upgradeTile.getComponent());
                    }
                }
                case DUMP_BUTTON -> {
                    if (tile instanceof IHasDumpButton hasDumpButton) {
                        hasDumpButton.dump();
                    }
                }
            }
        }, player);
        return null;
    }

    public enum GuiInteraction {
        CONTAINER_STOP_TRACKING,
        CONTAINER_TRACK_EJECTOR,
        CONTAINER_TRACK_SIDE_CONFIG,
        CONTAINER_TRACK_UPGRADES,
        DUMP_BUTTON
    }

    public static class GuiInteractMessage implements IMessage {

        public GuiInteraction interaction;
        public Coord4D coord4D;
        public int extra;

        public GuiInteractMessage() {
        }

        public GuiInteractMessage(GuiInteraction interaction, Coord4D coord4D, int extra) {
            this.interaction = interaction;
            this.coord4D = coord4D;
            this.extra = extra;
        }

        @Override
        public void toBytes(ByteBuf dataStream) {
            dataStream.writeInt(interaction.ordinal());
            coord4D.write(dataStream);
            dataStream.writeInt(extra);
        }

        @Override
        public void fromBytes(ByteBuf dataStream) {
            interaction = MekanismUtils.getByIndex(GuiInteraction.values(), dataStream.readInt(), GuiInteraction.CONTAINER_STOP_TRACKING);
            coord4D = Coord4D.read(dataStream);
            extra = dataStream.readInt();
        }
    }
}
