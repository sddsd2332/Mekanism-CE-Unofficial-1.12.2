package mekanism.common;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.api.Range4D;
import mekanism.common.base.ITileNetwork;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.network.*;
import mekanism.common.network.PacketBaublesModeChange.BaublesModeChangMessage;
import mekanism.common.network.PacketBoxBlacklist.BoxBlacklistMessage;
import mekanism.common.network.PacketConfigSync.ConfigSyncMessage;
import mekanism.common.network.PacketConfigurationUpdate.ConfigurationUpdateMessage;
import mekanism.common.network.PacketContainerEditMode.ContainerEditModeMessage;
import mekanism.common.network.PacketDataRequest.DataRequestMessage;
import mekanism.common.network.PacketDigitalMinerGui.DigitalMinerGuiMessage;
import mekanism.common.network.PacketDropperUse.DropperUseMessage;
import mekanism.common.network.PacketEditFilter.EditFilterMessage;
import mekanism.common.network.PacketEntityMove.EntityMoveMessage;
import mekanism.common.network.PacketFlyingSync.FlyingSyncMessage;
import mekanism.common.network.PacketGearStateUpdate.GearStateUpdateMessage;
import mekanism.common.network.PacketGuiInteract.GuiInteractMessage;
import mekanism.common.network.PacketKey.KeyMessage;
import mekanism.common.network.PacketLightningRender.LightningRenderMessage;
import mekanism.common.network.PacketLogisticalSorterGui.LogisticalSorterGuiMessage;
import mekanism.common.network.PacketMekaFishHook.PacketMekaFishHookMessage;
import mekanism.common.network.PacketModeChange.ModeChangMessage;
import mekanism.common.network.PacketNewFilter.NewFilterMessage;
import mekanism.common.network.PacketPlayerData.PlayerDataMessage;
import mekanism.common.network.PacketPortableTeleporter.PortableTeleporterMessage;
import mekanism.common.network.PacketPortalFX.PortalFXMessage;
import mekanism.common.network.PacketRadialModeChange.RadialModeChangeMessage;
import mekanism.common.network.PacketRedstoneControl.RedstoneControlMessage;
import mekanism.common.network.PacketRemoveModule.RemoveModuleMessage;
import mekanism.common.network.PacketRemoveUpgrade.RemoveUpgradeMessage;
import mekanism.common.network.PacketResetPlayerClient.ResetPlayerClientMessage;
import mekanism.common.network.PacketRobit.RobitMessage;
import mekanism.common.network.PacketSecurityMode.SecurityModeMessage;
import mekanism.common.network.PacketSecurityUpdate.SecurityUpdateMessage;
import mekanism.common.network.PacketSetItemFrequency.SetItemFrequencyMessage;
import mekanism.common.network.PacketSetFrequencyColor.SetFrequencyColorMessage;
import mekanism.common.network.PacketSetTileFrequency.SetTileFrequencyMessage;
import mekanism.common.network.PacketSimpleGui.SimpleGuiMessage;
import mekanism.common.network.PacketStepHeightSync.StepHeightSyncMessage;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.network.PacketTransmitterUpdate.TransmitterUpdateMessage;
import mekanism.common.network.PacketUpdateModuleSettings.UpdateModuleSettingsMessage;
import mekanism.common.network.PacketWindowSelect.WindowSelectMessage;
import mekanism.common.network.to_client.container.PacketUpdateContainer;
import mekanism.common.network.to_client.container.PacketUpdateContainer.UpdateContainerMessage;
import mekanism.common.network.qio.PacketQIOViewerData;
import mekanism.common.network.qio.PacketQIOViewerAction;
import mekanism.common.network.qio.PacketQIOComponentConfig;
import mekanism.common.network.qio.PacketQIOPortableGui;
import mekanism.common.network.qio.PacketQIOClearCraftingWindow;
import mekanism.common.network.qio.PacketQIOFillCraftingWindow;
import mekanism.common.network.qio.PacketQIOItemViewerGuiSync;
import mekanism.common.network.qio.PacketQIOItemViewerSlotInteract;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.util.SecurityUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

/**
 * Mekanism packet handler. As always, use packets sparingly!
 *
 * @author AidanBrady
 */
public class PacketHandler {

    public final SimpleNetworkWrapper netHandler = NetworkRegistry.INSTANCE.newSimpleChannel("MEK");

    /**
     * Encodes an Object[] of data into a DataOutputStream.
     *
     * @param dataValues - an Object[] of data to encode
     * @param output     - the output stream to write to
     */
    public static void encode(Object[] dataValues, ByteBuf output) {
        for (Object data : dataValues) {
            if (data instanceof Byte b) {
                output.writeByte(b);
            } else if (data instanceof Integer i) {
                output.writeInt(i);
            } else if (data instanceof Short s) {
                output.writeShort(s);
            } else if (data instanceof Long l) {
                output.writeLong(l);
            } else if (data instanceof Boolean b) {
                output.writeBoolean(b);
            } else if (data instanceof Double d) {
                output.writeDouble(d);
            } else if (data instanceof Float f) {
                output.writeFloat(f);
            } else if (data instanceof String s) {
                writeString(output, s);
            } else if (data instanceof EnumFacing f) {
                output.writeInt(f.ordinal());
            } else if (data instanceof ItemStack s) {
                writeStack(output, s);
            } else if (data instanceof NBTTagCompound nbt) {
                writeNBT(output, nbt);
            } else if (data instanceof int[] d) {
                for (int i : d) {
                    output.writeInt(i);
                }
            } else if (data instanceof byte[] d) {
                for (byte b : d) {
                    output.writeByte(b);
                }
            } else if (data instanceof ArrayList<?> a) {
                encode(a.toArray(), output);
            } else if (data instanceof NonNullList<?> d) {
                encode(d.toArray(), output);
            } else {
                throw new RuntimeException("Un-encodable data passed to encode(): " + data + ", full data: " + Arrays.toString(dataValues));
            }
        }
    }

    public static void writeString(ByteBuf output, String s) {
        ByteBufUtils.writeUTF8String(output, s);
    }

    public static String readString(ByteBuf input) {
        return ByteBufUtils.readUTF8String(input);
    }

    public static void writeStack(ByteBuf output, ItemStack stack) {
        ByteBufUtils.writeItemStack(output, stack);
    }

    public static ItemStack readStack(ByteBuf input) {
        return ByteBufUtils.readItemStack(input);
    }

    public static void writeNBT(ByteBuf output, NBTTagCompound nbtTags) {
        ByteBufUtils.writeTag(output, nbtTags);
    }

    public static NBTTagCompound readNBT(ByteBuf input) {
        return ByteBufUtils.readTag(input);
    }

    public static void log(String log) {
        if (MekanismConfig.current().general.logPackets.val()) {
            Mekanism.logger.info(log);
        }
    }

    public static EntityPlayer getPlayer(MessageContext context) {
        return Mekanism.proxy.getPlayer(context);
    }

    public static void handlePacket(Runnable runnable, EntityPlayer player) {
        Mekanism.proxy.handlePacket(runnable, player);
    }

    public static boolean canAccessTile(EntityPlayer player, TileEntity tileEntity) {
        return canAccessTile(player, tileEntity, false);
    }

    public static boolean canAccessTile(EntityPlayer player, TileEntity tileEntity, boolean requireContainerUser) {
        if (player == null || tileEntity == null || tileEntity.isInvalid() || tileEntity.getWorld() != player.world) {
            return false;
        }
        if (!SecurityUtils.canAccess(player, tileEntity)) {
            return false;
        }
        // Ensure packets cannot modify arbitrary loaded tiles from long distance.
        if (player.getDistanceSq(tileEntity.getPos()) > 256) {
            return false;
        }
        return !requireContainerUser || !(tileEntity instanceof TileEntityBasicBlock basic) || basic.playersUsing.contains(player);
    }

    public void initialize() {
        netHandler.registerMessage(PacketRobit.class, RobitMessage.class, 0, Side.SERVER);
        netHandler.registerMessage(PacketTransmitterUpdate.class, TransmitterUpdateMessage.class, 1, Side.CLIENT);
        //FREE ID 2
        //FREE ID 3
        //FREE ID 4
        netHandler.registerMessage(PacketTileEntity.class, TileEntityMessage.class, 5, Side.CLIENT);
        netHandler.registerMessage(PacketTileEntity.class, TileEntityMessage.class, 5, Side.SERVER);
        netHandler.registerMessage(PacketPortalFX.class, PortalFXMessage.class, 6, Side.CLIENT);
        netHandler.registerMessage(PacketDataRequest.class, DataRequestMessage.class, 7, Side.SERVER);
        //FREE ID 8
        netHandler.registerMessage(PacketSecurityMode.class, SecurityModeMessage.class, 9, Side.SERVER);
        netHandler.registerMessage(PacketPortableTeleporter.class, PortableTeleporterMessage.class, 10, Side.SERVER);
        netHandler.registerMessage(PacketRemoveUpgrade.class, RemoveUpgradeMessage.class, 11, Side.SERVER);
        netHandler.registerMessage(PacketRedstoneControl.class, RedstoneControlMessage.class, 12, Side.SERVER);
        netHandler.registerMessage(PacketLightningRender.class, LightningRenderMessage.class, 13, Side.CLIENT);
        netHandler.registerMessage(PacketLogisticalSorterGui.class, LogisticalSorterGuiMessage.class, 14, Side.CLIENT);
        netHandler.registerMessage(PacketLogisticalSorterGui.class, LogisticalSorterGuiMessage.class, 14, Side.SERVER);
        netHandler.registerMessage(PacketNewFilter.class, NewFilterMessage.class, 15, Side.SERVER);
        netHandler.registerMessage(PacketEditFilter.class, EditFilterMessage.class, 16, Side.SERVER);
        netHandler.registerMessage(PacketConfigurationUpdate.class, ConfigurationUpdateMessage.class, 17, Side.SERVER);
        netHandler.registerMessage(PacketSimpleGui.class, SimpleGuiMessage.class, 18, Side.CLIENT);
        netHandler.registerMessage(PacketSimpleGui.class, SimpleGuiMessage.class, 18, Side.SERVER);
        netHandler.registerMessage(PacketDigitalMinerGui.class, DigitalMinerGuiMessage.class, 19, Side.CLIENT);
        netHandler.registerMessage(PacketDigitalMinerGui.class, DigitalMinerGuiMessage.class, 19, Side.SERVER);
        netHandler.registerMessage(PacketUpdateContainer.class, UpdateContainerMessage.class, 20, Side.CLIENT);
        netHandler.registerMessage(PacketKey.class, KeyMessage.class, 21, Side.SERVER);
        netHandler.registerMessage(PacketSetTileFrequency.class, SetTileFrequencyMessage.class, 22, Side.SERVER);
        netHandler.registerMessage(PacketConfigSync.class, ConfigSyncMessage.class, 23, Side.CLIENT);
        netHandler.registerMessage(PacketBoxBlacklist.class, BoxBlacklistMessage.class, 24, Side.CLIENT);
        netHandler.registerMessage(PacketSetItemFrequency.class, SetItemFrequencyMessage.class, 25, Side.SERVER);
        netHandler.registerMessage(PacketContainerEditMode.class, ContainerEditModeMessage.class, 26, Side.SERVER);
        netHandler.registerMessage(PacketSetFrequencyColor.class, SetFrequencyColorMessage.class, 27, Side.SERVER);
        netHandler.registerMessage(PacketDropperUse.class, DropperUseMessage.class, 28, Side.SERVER);
        netHandler.registerMessage(PacketEntityMove.class, EntityMoveMessage.class, 29, Side.CLIENT);
        netHandler.registerMessage(PacketSecurityUpdate.class, SecurityUpdateMessage.class, 30, Side.CLIENT);
        //FREE ID 32
        //FREE ID 33

        netHandler.registerMessage(PacketModeChange.class, ModeChangMessage.class, 34, Side.SERVER);

        netHandler.registerMessage(PacketFlyingSync.class, FlyingSyncMessage.class, 35, Side.CLIENT);
        netHandler.registerMessage(PacketGearStateUpdate.class, GearStateUpdateMessage.class, 36, Side.SERVER);
        netHandler.registerMessage(PacketPlayerData.class, PlayerDataMessage.class, 37, Side.CLIENT);
        netHandler.registerMessage(PacketResetPlayerClient.class, ResetPlayerClientMessage.class, 38, Side.CLIENT);
        netHandler.registerMessage(PacketStepHeightSync.class, StepHeightSyncMessage.class, 39, Side.CLIENT);
        netHandler.registerMessage(PacketRadialModeChange.class, RadialModeChangeMessage.class, 40, Side.SERVER);
        if (Mekanism.hooks.Baubles) {
            registerBaublesMessage();
        }
        netHandler.registerMessage(PacketUpdateModuleSettings.class, UpdateModuleSettingsMessage.class, 42, Side.CLIENT);
        netHandler.registerMessage(PacketUpdateModuleSettings.class, UpdateModuleSettingsMessage.class, 42, Side.SERVER);
        netHandler.registerMessage(PacketRemoveModule.class, RemoveModuleMessage.class, 43, Side.SERVER);
        netHandler.registerMessage(PacketShowModeChange.class, PacketShowModeChange.ShowModeChangeMessage.class, 44, Side.CLIENT);
        netHandler.registerMessage(PacketOpenGui.class, PacketOpenGui.OpenGui.class, 45, Side.CLIENT);
        netHandler.registerMessage(PacketOpenGui.class, PacketOpenGui.OpenGui.class, 45, Side.SERVER);
        netHandler.registerMessage(PacketRadiationData.class, PacketRadiationData.PacketRadiationDataMessage.class, 46, Side.CLIENT);
        netHandler.registerMessage(PacketMekaFishHook.class, PacketMekaFishHookMessage.class, 47, Side.CLIENT);
        netHandler.registerMessage(PacketWindowSelect.class, WindowSelectMessage.class, 48, Side.SERVER);
        netHandler.registerMessage(PacketGuiInteract.class, GuiInteractMessage.class, 49, Side.SERVER);
        netHandler.registerMessage(PacketQIOViewerData.class, PacketQIOViewerData.Message.class, 50, Side.CLIENT);
        netHandler.registerMessage(PacketQIOViewerAction.class, PacketQIOViewerAction.Message.class, 51, Side.SERVER);
        netHandler.registerMessage(PacketQIOComponentConfig.class, PacketQIOComponentConfig.Message.class, 52, Side.SERVER);
        netHandler.registerMessage(PacketQIOPortableGui.class, PacketQIOPortableGui.Message.class, 53, Side.SERVER);
        netHandler.registerMessage(PacketQIOItemViewerSlotInteract.class, PacketQIOItemViewerSlotInteract.Message.class, 54, Side.SERVER);
        netHandler.registerMessage(PacketQIOItemViewerGuiSync.class, PacketQIOItemViewerGuiSync.Message.class, 55, Side.CLIENT);
        netHandler.registerMessage(PacketQIOClearCraftingWindow.class, PacketQIOClearCraftingWindow.Message.class, 56, Side.SERVER);
        netHandler.registerMessage(PacketQIOFillCraftingWindow.class, PacketQIOFillCraftingWindow.Message.class, 57, Side.SERVER);
    }

    @Optional.Method(modid = MekanismHooks.Baubles_MOD_ID)
    private void registerBaublesMessage() {
        netHandler.registerMessage(PacketBaublesModeChange.class, BaublesModeChangMessage.class, 41, Side.SERVER);
    }

    /**
     * Send this message to the specified player.
     *
     * @param message - the message to send
     * @param player  - the player to send it to
     */
    public synchronized void sendTo(IMessage message, EntityPlayerMP player) {
        netHandler.sendTo(message, player);
    }

    /**
     * Send this message to everyone connected to the server.
     *
     * @param message - message to send
     */
    public synchronized void sendToAll(IMessage message) {
        netHandler.sendToAll(message);
    }

    /**
     * Send this message to everyone within a certain range of a point.
     *
     * @param message - the message to send
     * @param point   - the TargetPoint around which to send
     */
    public synchronized void sendToAllAround(IMessage message, TargetPoint point) {
        netHandler.sendToAllAround(message, point);
    }

    /**
     * Send this message to everyone within the supplied dimension.
     *
     * @param message     - the message to send
     * @param dimensionId - the dimension id to target
     */
    public synchronized void sendToDimension(IMessage message, int dimensionId) {
        netHandler.sendToDimension(message, dimensionId);
    }

    /**
     * Send this message to the server.
     *
     * @param message - the message to send
     */
    public void sendToServer(IMessage message) {
        netHandler.sendToServer(message);
    }

    /**
     * Send this message to all players within a defined AABB cuboid.
     *
     * @param message - the message to send
     * @param cuboid  - the AABB cuboid to send the packet in
     * @param dimId   - the dimension the cuboid is in
     */
    public synchronized void sendToCuboid(IMessage message, AxisAlignedBB cuboid, int dimId) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null && cuboid != null) {
            server.getPlayerList().getPlayers().forEach(player -> {
                if (player.dimension == dimId && cuboid.contains(new Vec3d(player.posX, player.posY, player.posZ))) {
                    sendTo(message, player);
                }
            });
        }
    }

    public <TILE extends TileEntity & ITileNetwork> void sendUpdatePacket(TILE tile) {
        sendToAllTracking(new TileEntityMessage(tile), tile);
    }

    public void sendToAllTracking(IMessage message, TileEntity tile) {
        BlockPos pos = tile.getPos();
        sendToAllTracking(message, tile.getWorld().provider.getDimension(), pos.getX(), pos.getY(), pos.getZ());
    }

    public void sendToAllTracking(IMessage message, Coord4D point) {
        sendToAllTracking(message, point.dimensionId, point.x, point.y, point.z);
    }

    public void sendToAllTracking(IMessage message, int dimension, double x, double y, double z) {
        //Range is ignored for sendToAllTracking, and only gets sent to clients that have the location loaded
        sendToAllTracking(message, new TargetPoint(dimension, x, y, z, 1));
    }

    public synchronized void sendToAllTracking(IMessage message, TargetPoint point) {
        netHandler.sendToAllTracking(message, point);
    }

    public synchronized void sendToAllTracking(IMessage message, Entity entity) {
        netHandler.sendToAllTracking(message, entity);
    }


    //TODO: change Network stuff over to using this
    public void sendToReceivers(IMessage message, Range4D range) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null) {
            for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                if (range.hasPlayerInRange(player)) {
                    sendTo(message, player);
                }
            }
        }
    }

    @Nonnull
    public static UUID readUUID(ByteBuf dataStream) {
        return new UUID(dataStream.readLong(), dataStream.readLong());
    }


    public static void writeUUID(ByteBuf dataStream, UUID uuid) {
        dataStream.writeLong(uuid.getMostSignificantBits());
        dataStream.writeLong(uuid.getLeastSignificantBits());
    }
}
