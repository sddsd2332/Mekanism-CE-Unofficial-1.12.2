package mekanism.common.tile.prefab;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import mekanism.api.Coord4D;
import mekanism.api.TileNetworkList;
import mekanism.common.Mekanism;
import mekanism.common.base.IBoundingBlock;
import mekanism.common.base.ITileComponent;
import mekanism.common.base.ITileNetwork;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.inventory.container.ITrackableContainer;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.common.network.PacketDataRequest.DataRequestMessage;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.tile.base.TileEntityRestrictedTick;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Optional.Interface;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 基本方块类型
 */
@Interface(iface = "ic2.api.tile.IWrenchable", modid = MekanismHooks.IC2_MOD_ID)
public abstract class TileEntityBasicBlock extends TileEntityRestrictedTick implements ITileNetwork, ITrackableContainer {

    /**
     * The direction this block is facing.
     */
    public EnumFacing facing = EnumFacing.NORTH;

    public EnumFacing clientFacing = facing;

    /**
     * The players currently using this block.
     */
    public Set<EntityPlayer> playersUsing = new ReferenceOpenHashSet<>();

    /**
     * A timer used to send packets to clients.
     */
    public int ticker;

    public boolean redstone = false;
    public boolean redstoneLastTick = false;

    public boolean doAutoSync = true;

    public List<ITileComponent> components = new ArrayList<>();

    @Override
    public void onLoad() {
        super.onLoad();
        if (isRemote()) {
            Mekanism.packetHandler.sendToServer(new DataRequestMessage(Coord4D.get(this)));
        }
    }

    @Override
    public void doRestrictedTick() {
        if (checkInvalidBlock()) {
            return;
        }

        tickComponents();
        //TODO：切换为四种状态：同时更新,客户端更新,服务端更新，服务端异步更新
        if (!isRemote()) {
            onUpdateServer(); //服务端更新
            if (supportsAsync()) { //如果支持异步
                Mekanism.EXECUTE_MANAGER.addTask(this::onAsyncUpdateServer); //进行服务端异步更新
            }
        } else {
            onUpdateClient(); //进行客户端更新
        }
        onUpdate(); //最后同时更新

        if (!isRemote() && this instanceof TileEntityElectricBlock electricBlock) {
            electricBlock.trackEnergyInputRate();
        }

        if (!isRemote() && doAutoSync && !playersUsing.isEmpty()) {
            if (supportsAsync()) {
                Mekanism.EXECUTE_MANAGER.addSyncTask(() -> playersUsing.forEach(player -> Mekanism.packetHandler.sendTo(new TileEntityMessage(this), (EntityPlayerMP) player)));
            } else {
                playersUsing.forEach(player -> Mekanism.packetHandler.sendTo(new TileEntityMessage(this), (EntityPlayerMP) player));
            }
        }

        ticker++;
        redstoneLastTick = redstone;
    }


    private boolean checkInvalidBlock() {
        if (!isRemote() && MekanismConfig.current().general.destroyDisabledBlocks.val()) {
            MachineType type = MachineType.get(getBlockType(), getBlockMetadata());
            if (type != null && !type.isEnabled()) {
                Mekanism.logger.info("Destroying machine of type '{}' at coords {} as according to config.", type.getBlockName(), Coord4D.get(this));
                world.setBlockToAir(getPos());
                return true;
            }
        }
        return false;
    }

    protected void tickComponents() {
        components.forEach(ITileComponent::tick);
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        components.forEach(component -> component.trackForMainContainer(container));
        components.stream().filter(TileComponentUpgrade.class::isInstance)
              .map(TileComponentUpgrade.class::cast)
              .forEach(component -> container.startTracking(component, component));
    }



    @Override
    public void updateContainingBlockInfo() {
        super.updateContainingBlockInfo();
        onAdded();
    }

    public void open(EntityPlayer player) {
        playersUsing.add(player);
    }

    public void close(EntityPlayer player) {
        playersUsing.remove(player);
    }

    public boolean canPlayerOpenGui(EntityPlayer player) {
        if (MekanismConfig.current().mekce.AllowMultiplePlayersOpenSameMachineGui.val()) {
            return true;
        }
        playersUsing.removeIf(this::isStaleGuiUser);
        return playersUsing.isEmpty() || playersUsing.contains(player);
    }

    private boolean isStaleGuiUser(EntityPlayer player) {
        if (player == null || player.isDead || player.world != world || !(player.openContainer instanceof MekanismTileContainer)) {
            return true;
        }
        MekanismTileContainer<?> container = (MekanismTileContainer<?>) player.openContainer;
        return container.getTileEntity() != this;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            facing = EnumFacing.byIndex(dataStream.readInt());
            redstone = dataStream.readBoolean();
            if (clientFacing != facing) {
                MekanismUtils.updateBlock(world, getPos());
                world.notifyNeighborsOfStateChange(getPos(), world.getBlockState(getPos()).getBlock(), true);
                clientFacing = facing;
            }
            components.forEach(components -> components.read(dataStream));
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        data.add(facing == null ? -1 : facing.ordinal());
        data.add(redstone);
        components.forEach(component -> component.write(data));
        return data;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        components.forEach(ITileComponent::invalidate);
    }

    @Override
    public void validate() {
        super.validate();
        if (isRemote()) {
            Mekanism.packetHandler.sendToServer(new DataRequestMessage(Coord4D.get(this)));
        }
    }

    public boolean supportsAsync() {
        return true;
    }

    /**
     * Update call for machines. Use instead of updateEntity -- it's called every tick.
     */
    public void onUpdate() {
    }


    /**
     * Update call for machines. Use instead of updateEntity -- it's called every tick on the client side.
     */
    protected void onUpdateClient() {
    }

    /**
     * Update call for machines. Use instead of updateEntity -- it's called every tick on the server side.
     */
    protected void onUpdateServer() {
    }

    /**
     * Async Update call for machines. Use instead of updateEntity -- it's called every tick on the server side.
     */
    protected void onAsyncUpdateServer() {
    }


    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (nbtTags.hasKey("facing")) {
            facing = EnumFacing.byIndex(nbtTags.getInteger("facing"));
        }
        redstone = nbtTags.getBoolean("redstone");
        components.forEach(component -> component.read(nbtTags));
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        if (facing != null) {
            nbtTags.setInteger("facing", facing.ordinal());
        }
        nbtTags.setBoolean("redstone", redstone);
        components.forEach(component -> component.write(nbtTags));
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing facing) {
        return capability == Capabilities.TILE_NETWORK_CAPABILITY || super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing facing) {
        if (capability == Capabilities.TILE_NETWORK_CAPABILITY) {
            return Capabilities.TILE_NETWORK_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, facing);
    }

    public void setFacing(@Nonnull EnumFacing direction) {
        if (canSetFacing(direction)) {
            facing = direction;
        }
        if (facing != clientFacing && !isRemote()) {
            Mekanism.packetHandler.sendUpdatePacket(this);
            markNoUpdateSync();
            clientFacing = facing;
        }
    }

    /**
     * Whether or not this block's orientation can be changed to a specific direction. True by default.
     *
     * @param facing - facing to check
     * @return if the block's orientation can be changed
     */
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return true;
    }

    public boolean isPowered() {
        return redstone;
    }

    public boolean wasPowered() {
        return redstoneLastTick;
    }

    public void onPowerChange() {
    }

    public void onNeighborChange(Block block) {
        if (!isRemote()) {
            updatePower();
        }
    }

    private void updatePower() {
        boolean power = world.getRedstonePowerFromNeighbors(getPos()) > 0;
        if (redstone != power) {
            redstone = power;
            Mekanism.packetHandler.sendUpdatePacket(this);
            onPowerChange();
        }
    }

    /**
     * Called when block is placed in world
     */
    public void onAdded() {
        if (!isRemote()) {
            updatePower();
        }
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        if (this instanceof IBoundingBlock) {
            return INFINITE_EXTENT_AABB;
        }else {
            return super.getRenderBoundingBox();
        }
    }

}
