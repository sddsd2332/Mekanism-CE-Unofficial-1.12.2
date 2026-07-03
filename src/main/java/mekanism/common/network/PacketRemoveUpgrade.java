package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.common.PacketHandler;
import mekanism.common.Upgrade;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.network.PacketRemoveUpgrade.RemoveUpgradeMessage;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.util.MekanismUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketRemoveUpgrade implements IMessageHandler<RemoveUpgradeMessage, IMessage> {

    @Override
    public IMessage onMessage(RemoveUpgradeMessage message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            TileEntity tileEntity = message.coord4D.getTileEntity(player.world);
            if (!PacketHandler.canAccessTile(player, tileEntity)) {
                return;
            }
            if (tileEntity instanceof IUpgradeTile upgradeTile && tileEntity instanceof TileEntityBasicBlock) {
                Upgrade upgrade = MekanismUtils.getByIndex(Upgrade.values(), message.upgradeType, null);
                if (upgrade == null) {
                    return;
                }
                if (upgradeTile.getComponent().getUpgrades(upgrade) > 0) {
                    upgradeTile.getComponent().removeUpgrade(upgrade, message.removeAll == 1);
                }
            }
        }, player);
        return null;
    }


    public static class RemoveUpgradeMessage implements IMessage {
        public Coord4D coord4D;
        public int upgradeType;
        public int removeAll;

        public RemoveUpgradeMessage() {
        }

        public RemoveUpgradeMessage(Coord4D coord, int type, int remove) {
            coord4D = coord;
            upgradeType = type;
            removeAll = remove;
        }

        @Override
        public void toBytes(ByteBuf dataStream) {
            coord4D.write(dataStream);
            dataStream.writeInt(upgradeType);
            dataStream.writeInt(removeAll);
        }

        @Override
        public void fromBytes(ByteBuf dataStream) {
            coord4D = Coord4D.read(dataStream);
            upgradeType = dataStream.readInt();
            removeAll = dataStream.readInt();
        }
    }
}
