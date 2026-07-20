package mekanism.common.network.qio;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.tile.qio.TileEntityQIOFilterHandler;
import mekanism.common.tile.qio.TileEntityQIOExporter;
import mekanism.common.tile.qio.TileEntityQIORedstoneAdapter;
import mekanism.common.tile.qio.TileEntityQIODashboard;
import mekanism.common.content.qio.filter.QIOFilter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nullable;

/** Validated configuration changes for QIO automation components. */
public class PacketQIOComponentConfig implements IMessageHandler<PacketQIOComponentConfig.Message, IMessage> {

    @Override
    @Nullable
    public IMessage onMessage(Message message, MessageContext context) {
        if (!message.isValid()) {
            return null;
        }
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null || player.world.isRemote) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            if (message.coord.dimensionId != player.dimension) {
                return;
            }
            TileEntity tile = message.coord.getTileEntity(player.world);
            if (!PacketHandler.canAccessTile(player, tile, true)) {
                return;
            }
            switch (message.action) {
                case TOGGLE_FILTERLESS:
                    if (tile instanceof TileEntityQIOFilterHandler) {
                        ((TileEntityQIOFilterHandler) tile).toggleFilterless();
                    }
                    break;
                case SET_THRESHOLD:
                    if (tile instanceof TileEntityQIORedstoneAdapter) {
                        ((TileEntityQIORedstoneAdapter) tile).setThreshold(Math.max(0, message.value));
                    }
                    break;
                case TOGGLE_REDSTONE_FUZZY:
                    if (tile instanceof TileEntityQIORedstoneAdapter) {
                        ((TileEntityQIORedstoneAdapter) tile).toggleFuzzyMode();
                    }
                    break;
                case TOGGLE_REDSTONE_INVERTED:
                    if (tile instanceof TileEntityQIORedstoneAdapter) {
                        ((TileEntityQIORedstoneAdapter) tile).invertSignal();
                    }
                    break;
                case SET_FILTER:
                    if (tile instanceof TileEntityQIOFilterHandler && message.filterData != null) {
                        QIOFilter filter = QIOFilter.read(message.filterData);
                        if (filter != null) {
                            ((TileEntityQIOFilterHandler) tile).setFilter(filter);
                        }
                    }
                    break;
                case CLEAR_FILTER:
                    if (tile instanceof TileEntityQIOFilterHandler) {
                        ((TileEntityQIOFilterHandler) tile).clearFilters();
                    }
                    break;
                case SET_FILTER_ENABLED:
                    if (tile instanceof TileEntityQIOFilterHandler) {
                        ((TileEntityQIOFilterHandler) tile).setFilterEnabled(message.index, message.enabled);
                    }
                    break;
                case ADD_FILTER:
                    if (tile instanceof TileEntityQIOFilterHandler && message.filterData != null) {
                        QIOFilter filter = QIOFilter.read(message.filterData);
                        if (filter != null) {
                            ((TileEntityQIOFilterHandler) tile).addFilter(filter);
                        }
                    }
                    break;
                case EDIT_FILTER:
                    if (tile instanceof TileEntityQIOFilterHandler && message.filterData != null) {
                        QIOFilter filter = QIOFilter.read(message.filterData);
                        if (filter != null) {
                            ((TileEntityQIOFilterHandler) tile).replaceFilter(message.index, filter);
                        }
                    }
                    break;
                case REMOVE_FILTER:
                    if (tile instanceof TileEntityQIOFilterHandler) {
                        ((TileEntityQIOFilterHandler) tile).removeFilter(message.index);
                    }
                    break;
                case MOVE_FILTER:
                    if (tile instanceof TileEntityQIOFilterHandler) {
                        ((TileEntityQIOFilterHandler) tile).moveFilter(message.index, (int) message.value);
                    }
                    break;
                case TOGGLE_ROUND_ROBIN:
                    if (tile instanceof TileEntityQIOExporter) {
                        ((TileEntityQIOExporter) tile).toggleRoundRobin();
                    }
                    break;
                case TOGGLE_TARGET_DIRECTION:
                    if (tile instanceof TileEntityQIODashboard) {
                        ((TileEntityQIODashboard) tile).toggleShiftClickIntoFrequency();
                    }
                    break;
                default:
                    break;
            }
        }, player);
        return null;
    }

    public static void toggleFilterless(TileEntity tile) {
        if (tile != null) {
            Mekanism.packetHandler.sendToServer(new Message(Coord4D.get(tile), ConfigAction.TOGGLE_FILTERLESS, 0));
        }
    }

    public static void setThreshold(TileEntity tile, long threshold) {
        if (tile != null) {
            Mekanism.packetHandler.sendToServer(new Message(Coord4D.get(tile), ConfigAction.SET_THRESHOLD, threshold));
        }
    }

    public static void toggleRedstoneFuzzy(TileEntity tile) {
        if (tile != null) {
            Mekanism.packetHandler.sendToServer(new Message(Coord4D.get(tile), ConfigAction.TOGGLE_REDSTONE_FUZZY, 0));
        }
    }

    public static void toggleRedstoneInverted(TileEntity tile) {
        if (tile != null) {
            Mekanism.packetHandler.sendToServer(new Message(Coord4D.get(tile), ConfigAction.TOGGLE_REDSTONE_INVERTED, 0));
        }
    }

    public static void setFilter(TileEntity tile, QIOFilter filter) {
        if (tile != null && filter != null) {
            Mekanism.packetHandler.sendToServer(new Message(Coord4D.get(tile), ConfigAction.SET_FILTER, 0, filter.write(), 0, true));
        }
    }

    public static void clearFilter(TileEntity tile) {
        if (tile != null) {
            Mekanism.packetHandler.sendToServer(new Message(Coord4D.get(tile), ConfigAction.CLEAR_FILTER, 0, null, 0, true));
        }
    }

    public static void setFilterEnabled(TileEntity tile, int index, boolean enabled) {
        if (tile != null) {
            Mekanism.packetHandler.sendToServer(new Message(Coord4D.get(tile), ConfigAction.SET_FILTER_ENABLED, 0, null, index, enabled));
        }
    }

    public static void addFilter(TileEntity tile, QIOFilter filter) {
        if (tile != null && filter != null) {
            Mekanism.packetHandler.sendToServer(new Message(Coord4D.get(tile), ConfigAction.ADD_FILTER, 0, filter.write(), 0, true));
        }
    }

    public static void editFilter(TileEntity tile, int index, QIOFilter filter) {
        if (tile != null && index >= 0 && filter != null) {
            Mekanism.packetHandler.sendToServer(new Message(Coord4D.get(tile), ConfigAction.EDIT_FILTER, 0, filter.write(), index, true));
        }
    }

    public static void removeFilter(TileEntity tile, int index) {
        if (tile != null) {
            Mekanism.packetHandler.sendToServer(new Message(Coord4D.get(tile), ConfigAction.REMOVE_FILTER, 0, null, index, false));
        }
    }

    public static void moveFilter(TileEntity tile, int index, int target) {
        if (tile != null) {
            Mekanism.packetHandler.sendToServer(new Message(Coord4D.get(tile), ConfigAction.MOVE_FILTER, target, null, index, false));
        }
    }

    public static void toggleRoundRobin(TileEntity tile) {
        if (tile != null) {
            Mekanism.packetHandler.sendToServer(new Message(Coord4D.get(tile), ConfigAction.TOGGLE_ROUND_ROBIN, 0));
        }
    }

    public static void toggleTargetDirection(TileEntity tile) {
        if (tile != null) {
            Mekanism.packetHandler.sendToServer(new Message(Coord4D.get(tile), ConfigAction.TOGGLE_TARGET_DIRECTION, 0));
        }
    }

    public enum ConfigAction {
        TOGGLE_FILTERLESS,
        SET_THRESHOLD,
        TOGGLE_REDSTONE_FUZZY,
        TOGGLE_REDSTONE_INVERTED,
        SET_FILTER,
        CLEAR_FILTER,
        SET_FILTER_ENABLED,
        ADD_FILTER,
        EDIT_FILTER,
        REMOVE_FILTER,
        MOVE_FILTER,
        TOGGLE_ROUND_ROBIN,
        TOGGLE_TARGET_DIRECTION
    }

    public static class Message implements IMessage {

        private Coord4D coord;
        private ConfigAction action = ConfigAction.TOGGLE_FILTERLESS;
        private long value;
        private NBTTagCompound filterData;
        private int index;
        private boolean enabled;
        private boolean valid;

        public Message() {
        }

        private Message(Coord4D coord, ConfigAction action, long value) {
            this(coord, action, value, null, 0, false);
        }

        private Message(Coord4D coord, ConfigAction action, long value, NBTTagCompound filterData, int index, boolean enabled) {
            this.coord = coord;
            this.action = action;
            this.value = value;
            this.filterData = filterData;
            this.index = index;
            this.enabled = enabled;
            valid = isRequestValid(coord, action, value, filterData, index);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            coord.write(buffer);
            buffer.writeByte(action.ordinal());
            buffer.writeLong(value);
            buffer.writeInt(index);
            buffer.writeBoolean(enabled);
            buffer.writeBoolean(filterData != null);
            if (filterData != null) {
                PacketHandler.writeNBT(buffer, filterData);
            }
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                coord = Coord4D.read(buffer);
                int ordinal = buffer.readUnsignedByte();
                if (ordinal < 0 || ordinal >= ConfigAction.values().length) {
                    throw new IllegalArgumentException("Unknown QIO component configuration action");
                }
                action = ConfigAction.values()[ordinal];
                value = buffer.readLong();
                index = buffer.readInt();
                enabled = buffer.readBoolean();
                filterData = buffer.readBoolean() ? PacketHandler.readNBT(buffer) : null;
                valid = isRequestValid(coord, action, value, filterData, index);
            } catch (RuntimeException ex) {
                coord = null;
                action = ConfigAction.TOGGLE_FILTERLESS;
                value = 0;
                index = -1;
                enabled = false;
                filterData = null;
            }
        }

        private static boolean isRequestValid(@Nullable Coord4D coord, @Nullable ConfigAction action, long value,
              @Nullable NBTTagCompound filterData, int index) {
            if (coord == null || action == null) {
                return false;
            }
            switch (action) {
                case SET_THRESHOLD:
                    return value >= 0;
                case SET_FILTER:
                case ADD_FILTER:
                    return isValidFilter(filterData);
                case EDIT_FILTER:
                    return index >= 0 && isValidFilter(filterData);
                case SET_FILTER_ENABLED:
                case REMOVE_FILTER:
                    return index >= 0;
                case MOVE_FILTER:
                    return index >= 0 && value >= 0 && value <= Integer.MAX_VALUE;
                default:
                    return true;
            }
        }

        private static boolean isValidFilter(@Nullable NBTTagCompound filterData) {
            return filterData != null && QIOFilter.read(filterData) != null;
        }

        public Coord4D getCoord() {
            return coord;
        }

        public ConfigAction getAction() {
            return action;
        }

        public long getValue() {
            return value;
        }

        public int getIndex() {
            return index;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public boolean isValid() {
            return valid;
        }

        @Nullable
        public NBTTagCompound getFilterData() {
            return filterData == null ? null : filterData.copy();
        }
    }
}
