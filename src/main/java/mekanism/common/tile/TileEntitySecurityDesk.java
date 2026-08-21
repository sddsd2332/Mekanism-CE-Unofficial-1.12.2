package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.api.IContentsListener;
import mekanism.api.TileNetworkList;
import mekanism.client.render.bloom.BloomRenderSecurityDesk;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.base.IBoundingBlock;
import mekanism.common.base.ISpecialSelectionWireframeTile;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.frequency.IFrequencyHandler;
import mekanism.common.inventory.slot.SecurityInventorySlot;
import mekanism.common.network.PacketSecurityUpdate.SecurityPacket;
import mekanism.common.network.PacketSecurityUpdate.SecurityUpdateMessage;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.security.SecurityData;
import mekanism.common.security.SecurityFrequency;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;
import java.util.UUID;

public class TileEntitySecurityDesk extends TileEntityContainerBlock implements IBoundingBlock, IFrequencyHandler, ISpecialSelectionWireframeTile {

    private static final int MAX_TRUSTED_NAME_LENGTH = 16;

    private static final ISpecialSelectionWireframeTile.SelectionTransform[] SELECTION_ROTATE_SOUTH = {
            ISpecialSelectionWireframeTile.SelectionTransform.rotateY(180.0D, 0.5D, 0.5D, 0.5D)
    };
    private static final ISpecialSelectionWireframeTile.SelectionTransform[] SELECTION_ROTATE_WEST = {
            ISpecialSelectionWireframeTile.SelectionTransform.rotateY(90.0D, 0.5D, 0.5D, 0.5D)
    };
    private static final ISpecialSelectionWireframeTile.SelectionTransform[] SELECTION_ROTATE_EAST = {
            ISpecialSelectionWireframeTile.SelectionTransform.rotateY(270.0D, 0.5D, 0.5D, 0.5D)
    };

    public UUID ownerUUID;
    public String clientOwner;
    public SecurityFrequency frequency;
    private SecurityInventorySlot unlockSlot;
    private SecurityInventorySlot lockSlot;

    public TileEntitySecurityDesk() {
        super("SecurityDesk");
        frequencyComponent.track(FrequencyType.SECURITY, true, false, true);
        initializeInventorySlots();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        builder.addSlot(unlockSlot = SecurityInventorySlot.unlock(() -> ownerUUID, listener, 146, 18));
        builder.addSlot(lockSlot = SecurityInventorySlot.lock(listener, 146, 97));
        return builder.build();
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        frequency = getFreq();
        if (ownerUUID != null && frequency != null) {
            if (unlockSlot != null) {
                unlockSlot.unlock(ownerUUID);
            }
            if (lockSlot != null) {
                lockSlot.lock(ownerUUID, frequency);
            }
        }
        if (frequency == null && ownerUUID != null) {
            setFrequency(ownerUUID);
        }
    }

    @Override
    public boolean supportsAsync() {
        return false;
    }

    public void setFrequency(UUID owner) {
        ownerUUID = owner;
        frequencyComponent.setFrequency(FrequencyType.SECURITY, new FrequencyIdentity(owner, SecurityMode.PUBLIC, owner), owner);
        frequency = getFreq();
    }

    public SecurityFrequency getFreq() {
        return getFrequency(FrequencyType.SECURITY);
    }

    @Override
    public boolean canHandlePacket(EntityPlayer player) {
        return ownerUUID != null && ownerUUID.equals(player.getUniqueID());
    }

    private static boolean isValidTrustedName(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_TRUSTED_NAME_LENGTH) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c != '_' && !Character.isLetterOrDigit(c)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            int type = dataStream.readInt();
            if (type == 0) {
                String trustedName = PacketHandler.readString(dataStream);
                if (frequency != null) {
                    if (isValidTrustedName(trustedName) && !frequency.trusted.contains(trustedName)) {
                        frequency.trusted.add(trustedName);
                    }
                }
            } else if (type == 1) {
                String trustedName = PacketHandler.readString(dataStream);
                if (frequency != null) {
                    if (isValidTrustedName(trustedName)) {
                        frequency.trusted.remove(trustedName);
                    }
                }
            } else if (type == 2) {
                if (frequency != null) {
                    frequency.override = !frequency.override;
                    Mekanism.packetHandler.sendToAll(new SecurityUpdateMessage(SecurityPacket.UPDATE, ownerUUID, new SecurityData(frequency)));
                }
            } else if (type == 3) {
                if (frequency != null) {
                    frequency.securityMode = MekanismUtils.getByIndex(SecurityMode.values(), dataStream.readInt(), frequency.securityMode);
                    Mekanism.packetHandler.sendToAll(new SecurityUpdateMessage(SecurityPacket.UPDATE, ownerUUID, new SecurityData(frequency)));
                }
            }
            MekanismUtils.saveChunk(this);
            return;
        }

        super.handlePacketData(dataStream);

        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            if (dataStream.readBoolean()) {
                clientOwner = PacketHandler.readString(dataStream);
                ownerUUID = PacketHandler.readUUID(dataStream);
            } else {
                clientOwner = null;
                ownerUUID = null;
            }
            if (dataStream.readBoolean()) {
                frequency = new SecurityFrequency(dataStream);
            } else {
                frequency = null;
            }
        }
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (nbtTags.hasKey("ownerUUID")) {
            ownerUUID = MekanismUtils.parseUUID(nbtTags.getString("ownerUUID"));
        }
        frequency = getFreq();
    }


    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        if (ownerUUID != null) {
            nbtTags.setString("ownerUUID", ownerUUID.toString());
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        if (ownerUUID != null) {
            data.add(true);
            data.add(MekanismUtils.getLastKnownUsername(ownerUUID));
            data.add(ownerUUID.getMostSignificantBits());
            data.add(ownerUUID.getLeastSignificantBits());
        } else {
            data.add(false);
        }
        frequency = getFreq();
        if (frequency != null) {
            data.add(true);
            frequency.write(data);
        } else {
            data.add(false);
        }
        return data;
    }

    @Override
    public void invalidate() {
        super.invalidate();
    }

    @Override
    public void collectBoundingBlocks(java.util.function.BiConsumer<BlockPos, Boolean> consumer) {
        consumer.accept(getPos().up(), false);
    }

    @Override
    public void onPlace() {
        tryPlaceBoundingBlocks(world, Coord4D.get(this));
    }

    @Override
    public void onBreak() {
        removeBoundingBlocks(world, getPos());
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        //Even though there are inventory slots make this return none as
        // accessible by automation, as then people could lock items to other
        // people unintentionally
        return InventoryUtils.EMPTY;
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            //For the same reason as the getSlotsForFace does not give any slots, don't expose this here
            return true;
        }
        return super.isCapabilityDisabled(capability, side);
    }

    @Override
    public void validate() {
        super.validate();
        if (isRemote()) {
            if (Mekanism.hooks.Bloom && MekanismConfig.current().client.enableBloom.val()) {
                try {
                    new BloomRenderSecurityDesk(this);
                } catch (LinkageError e) {
                    mekanism.common.util.BloomDependencyHelper.disableBloom("BloomRenderSecurityDesk", e);
                }
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Class<?> getSelectionWireframeModelClass() {
        return mekanism.client.model.ModelSecurityDesk.class;
    }

    @Override
    public boolean shouldApplyDefaultSelectionWireframeFacingRotation(IBlockState state, IBlockAccess world, BlockPos pos) {
        return false;
    }

    @Override
    public ISpecialSelectionWireframeTile.SelectionTransform[] getSelectionWireframeTransforms(IBlockState state, IBlockAccess world, BlockPos pos) {
        EnumFacing currentFacing = facing == null ? EnumFacing.NORTH : facing;
        return switch (currentFacing) {
            case SOUTH -> SELECTION_ROTATE_SOUTH;
            case WEST -> SELECTION_ROTATE_WEST;
            case EAST -> SELECTION_ROTATE_EAST;
            default -> SelectionTransform.EMPTY;
        };
    }
}
