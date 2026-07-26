package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.*;
import mekanism.common.Mekanism;
import mekanism.common.MekanismBlocks;
import mekanism.common.Upgrade;
import mekanism.common.advancements.MekanismCriteriaTriggers;
import mekanism.common.base.IComparatorSupport;
import mekanism.common.base.IRedstoneControl;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.block.states.BlockStateMachine;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.chunkloading.IChunkLoader;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.teleporter.TeleporterFrequency;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.frequency.IFrequencyHandler;
import mekanism.common.integration.computer.IComputerIntegration;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.container.sync.SyncableByte;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.network.PacketEntityMove.EntityMoveMessage;
import mekanism.common.network.PacketPortalFX.PortalFXMessage;
import mekanism.common.security.ISecurityTile;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.tile.component.TileComponentChunkLoader;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.tile.prefab.TileEntityElectricBlock;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.*;

public class TileEntityTeleporter extends TileEntityElectricBlock implements IComputerIntegration, IChunkLoader, IFrequencyHandler, IRedstoneControl, ISecurityTile,
        IUpgradeTile, IComparatorSupport, IConfigCardAccess {

    private static final String[] methods = new String[]{"getEnergy", "canTeleport", "getMaxEnergy", "teleport", "setFrequency", "createFrequency"};
    public AxisAlignedBB teleportBounds = null;
    public Set<UUID> didTeleport = new ObjectOpenHashSet<>();

    public int teleDelay = 0;

    public boolean shouldRender;

    public boolean prevShouldRender;

    public EnumColor color;

    /**
     * This teleporter's current status.
     */
    public byte status = 0;

    public RedstoneControl controlType = RedstoneControl.DISABLED;

    public TileComponentSecurity securityComponent;
    public TileComponentChunkLoader chunkLoaderComponent;
    public TileComponentUpgrade upgradeComponent;
    private EnergyInventorySlot energySlot;

    public TileEntityTeleporter() {
        super("Teleporter", MachineType.TELEPORTER.getStorage());
        securityComponent = new TileComponentSecurity(this);
        chunkLoaderComponent = new TileComponentChunkLoader(this);
        upgradeComponent = new TileComponentUpgrade(this);
        clearSupportedUpgrades();
        setSupportedUpgrade(Upgrade.ANCHOR);
        frequencyComponent.track(FrequencyType.TELEPORTER, true, true, false);
        initializeInventorySlots();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 153, 7), RelativeSide.values());
        return builder.build();
    }

    public static void teleportPlayerTo(EntityPlayerMP player, Coord4D coord, TileEntityTeleporter teleporter) {
        if (player.dimension != coord.dimensionId) {
            player.changeDimension(coord.dimensionId, (world, entity, yaw) -> entity.setPositionAndUpdate(coord.x + 0.5, coord.y + 1, coord.z + 0.5));
        } else {
            player.setPositionAndUpdate(coord.x + 0.5, coord.y + 1, coord.z + 0.5);
        }
        player.world.updateEntityWithOptionalForce(player, true);
    }

    public static void alignPlayer(EntityPlayerMP player, Coord4D coord) {
        Coord4D upperCoord = coord.offset(EnumFacing.UP);
        EnumFacing side = null;
        float yaw = player.rotationYaw;
        for (EnumFacing iterSide : MekanismUtils.SIDE_DIRS) {
            if (upperCoord.offset(iterSide).isAirBlock(player.world)) {
                side = iterSide;
                break;
            }
        }

        if (side != null) {
            switch (side) {
                case NORTH -> yaw = 180;
                case SOUTH -> yaw = 0;
                case WEST -> yaw = 90;
                case EAST -> yaw = 270;
                default -> {
                }
            }
        }
        player.connection.setPlayerLocation(player.posX, player.posY, player.posZ, yaw, player.rotationPitch);
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        if (teleportBounds == null) {
            resetBounds();
        }
        energySlot.fillContainerOrConvert();

        TeleporterFrequency freq = getFreq();
        status = canTeleport();
        if (MekanismUtils.canFunction(this) && status == 1 && teleDelay == 0) {
            teleport();
        }
        if (teleDelay == 0 && didTeleport.size() > 0) {
            cleanTeleportCache();
        }

        EnumColor prevColor = color;
        shouldRender = status == 1 || status > 4;
        color = freq == null ? null : freq.getColor();
        if (shouldRender != prevShouldRender) {
            Mekanism.packetHandler.sendUpdatePacket(this);
            //This also means the comparator output changed so notify the neighbors we have a change
            MekanismUtils.notifyLoadedNeighborsOfTileChange(world, Coord4D.get(this));
        } else if (color != prevColor) {
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
        prevShouldRender = shouldRender;
        teleDelay = Math.max(0, teleDelay - 1);
    }

    @Override
    public boolean supportsAsync() {
        return false;
    }

    public TeleporterFrequency getFreq() {
        return getFrequency(FrequencyType.TELEPORTER);
    }

    public Coord4D getClosest() {
        TeleporterFrequency frequency = getFreq();
        if (frequency != null) {
            return frequency.getClosestCoords(Coord4D.get(this));
        }
        return null;
    }

    public void setFrequency(FrequencyIdentity identity) {
        UUID owner = getSecurity().getOwnerUUID();
        if (identity != null && owner != null) {
            setFrequency(FrequencyType.TELEPORTER, identity, owner);
        }
    }

    public void createFrequency(String name) {
        UUID owner = getSecurity().getOwnerUUID();
        if (name != null && !name.isEmpty() && owner != null) {
            setFrequency(FrequencyType.TELEPORTER, new FrequencyIdentity(name, SecurityMode.PUBLIC, owner), owner);
        }
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (!isRemote()) {
            frequencyComponent.invalidate();
        }
    }

    public void cleanTeleportCache() {
        List<UUID> list = new ArrayList<>();
        world.getEntitiesWithinAABB(Entity.class, teleportBounds).forEach(e -> list.add(e.getPersistentID()));
        new ObjectOpenHashSet<>(didTeleport).forEach(id -> {
            if (!list.contains(id)) {
                didTeleport.remove(id);
            }
        });
    }

    public void resetBounds() {
        teleportBounds = new AxisAlignedBB(getPos(), getPos().add(1, 3, 1));
    }

    /**
     * @return 1: yes, 2: no frame, 3: no link found, 4: not enough electricity
     */
    public byte canTeleport() {
        if (!hasFrame()) {
            return 2;
        }
        if (getClosest() == null) {
            return 3;
        }
        List<Entity> entitiesInPortal = getToTeleport();
        Coord4D closestCoords = getClosest();
        double electricityNeeded = 0;
        for (Entity entity : entitiesInPortal) {
            electricityNeeded += calculateEnergyCost(entity, closestCoords);
        }
        if (getMainEnergyContainer().extract(electricityNeeded, Action.SIMULATE, AutomationType.INTERNAL) < electricityNeeded) {
            return 4;
        }
        return 1;
    }

    public void teleport() {
        if (isRemote()) {
            return;
        }
        List<Entity> entitiesInPortal = getToTeleport();
        Coord4D closestCoords = getClosest();
        if (closestCoords == null) {
            return;
        }
        TeleporterFrequency frequency = getFreq();
        if (frequency == null) {
            return;
        }
        entitiesInPortal.forEach(entity -> {
            World teleWorld = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(closestCoords.dimensionId);
            if (teleWorld == null) {
                return;
            }
            if (!(closestCoords.getTileEntity(teleWorld) instanceof TileEntityTeleporter teleporter)) {
                return;
            }
            teleporter.didTeleport.add(entity.getPersistentID());
            teleporter.teleDelay = 5;
            double energyCost = calculateEnergyCost(entity, closestCoords);
            if (entity instanceof EntityPlayerMP mp) {
                teleportPlayerTo(mp, closestCoords, teleporter);
                alignPlayer(mp, closestCoords);
                MekanismCriteriaTriggers.TELEPORT.trigger(mp);
            } else {
                teleportEntityTo(entity, closestCoords, teleporter);
            }
            frequency.activeCoords.forEach(coords -> Mekanism.packetHandler.sendToAllTracking(new PortalFXMessage(coords), coords));
            getMainEnergyContainer().extract(energyCost, Action.EXECUTE, AutomationType.INTERNAL);
            world.playSound(entity.posX, entity.posY, entity.posZ, SoundEvents.ENTITY_ENDERMEN_TELEPORT, entity.getSoundCategory(), 1.0F, 1.0F, false);
        });
    }

    public void teleportEntityTo(Entity entity, Coord4D coord, TileEntityTeleporter teleporter) {
        if (entity.world.provider.getDimension() != coord.dimensionId) {
            entity.changeDimension(coord.dimensionId, (world, entity2, yaw) -> entity2.setPositionAndUpdate(coord.x + 0.5, coord.y + 1, coord.z + 0.5));
        } else {
            entity.setPositionAndUpdate(coord.x + 0.5, coord.y + 1, coord.z + 0.5);
            Mekanism.packetHandler.sendToAllTracking(new EntityMoveMessage(entity), new Coord4D(entity));
        }
    }

    public List<Entity> getToTeleport() {
        List<Entity> entities = world.getEntitiesWithinAABB(Entity.class, teleportBounds);
        List<Entity> ret = new ArrayList<>();
        entities.forEach(entity -> {
            if (!didTeleport.contains(entity.getPersistentID())) {
                ret.add(entity);
            }
        });
        return ret;
    }

    public int calculateEnergyCost(Entity entity, Coord4D coords) {
        int energyCost = MekanismConfig.current().usage.teleporterBase.val();
        if (entity.world.provider.getDimension() != coords.dimensionId) {
            energyCost += MekanismConfig.current().usage.teleporterDimensionPenalty.val();
        } else {
            int distance = (int) entity.getDistance(coords.x, coords.y, coords.z);
            energyCost += distance * MekanismConfig.current().usage.teleporterDistance.val();
        }
        return energyCost;
    }

    public boolean hasFrame() {
        if (isFrame(getPos().getX() - 1, getPos().getY(), getPos().getZ()) && isFrame(getPos().getX() + 1, getPos().getY(), getPos().getZ())
                && isFrame(getPos().getX() - 1, getPos().getY() + 1, getPos().getZ()) && isFrame(getPos().getX() + 1, getPos().getY() + 1, getPos().getZ())
                && isFrame(getPos().getX() - 1, getPos().getY() + 2, getPos().getZ()) && isFrame(getPos().getX() + 1, getPos().getY() + 2, getPos().getZ())
                && isFrame(getPos().getX() - 1, getPos().getY() + 3, getPos().getZ()) && isFrame(getPos().getX() + 1, getPos().getY() + 3, getPos().getZ())
                && isFrame(getPos().getX(), getPos().getY() + 3, getPos().getZ())) {
            return true;
        }
        return isFrame(getPos().getX(), getPos().getY(), getPos().getZ() - 1) && isFrame(getPos().getX(), getPos().getY(), getPos().getZ() + 1)
                && isFrame(getPos().getX(), getPos().getY() + 1, getPos().getZ() - 1) && isFrame(getPos().getX(), getPos().getY() + 1, getPos().getZ() + 1)
                && isFrame(getPos().getX(), getPos().getY() + 2, getPos().getZ() - 1) && isFrame(getPos().getX(), getPos().getY() + 2, getPos().getZ() + 1)
                && isFrame(getPos().getX(), getPos().getY() + 3, getPos().getZ() - 1) && isFrame(getPos().getX(), getPos().getY() + 3, getPos().getZ() + 1)
                && isFrame(getPos().getX(), getPos().getY() + 3, getPos().getZ());
    }

    public boolean isFrame(int x, int y, int z) {
        IBlockState state = world.getBlockState(new BlockPos(x, y, z));
        return state.getBlock() == MekanismBlocks.BasicBlock && state.getBlock().getMetaFromState(state) == 7;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        controlType = MekanismUtils.getByIndex(RedstoneControl.values(), nbtTags.getInteger("controlType"), controlType);
    }


    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("controlType", controlType.ordinal());
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);

        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            status = dataStream.readByte();
            shouldRender = dataStream.readBoolean();
            controlType = MekanismUtils.getByIndex(RedstoneControl.values(), dataStream.readInt(), controlType);
            int colorIndex = dataStream.readInt();
            color = colorIndex == -1 ? null : MekanismUtils.getByIndex(EnumColor.values(), colorIndex, color);
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);

        data.add(status);
        data.add(shouldRender);
        data.add(controlType.ordinal());
        data.add(color == null ? -1 : color.ordinal());
        return data;
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        switch (method) {
            case 0 -> {
                return new Object[]{getEnergy()};
            }
            case 1 -> {
                return new Object[]{canTeleport()};
            }
            case 2 -> {
                return new Object[]{getMaxEnergy()};
            }
            case 3 -> {
                teleport();
                return new Object[]{"Attempted to teleport."};
            }
            case 4 -> {
                if (!(arguments[0] instanceof String)) {
                    return new Object[]{"Invalid parameters."};
                }
                String freq = ((String) arguments[0]).trim();
                Frequency frequency = FrequencyType.TELEPORTER.getManager(null, SecurityMode.PUBLIC).getFrequency(freq);
                if (frequency == null) {
                    return new Object[]{"No public teleporter frequency with that name exists."};
                }
                setFrequency(frequency.getIdentity());
                return new Object[]{"Frequency set."};
            }
            case 5 -> {
                if (!(arguments[0] instanceof String)) {
                    return new Object[]{"Invalid parameters."};
                }
                String freq = ((String) arguments[0]).trim();
                if (FrequencyType.TELEPORTER.getManager(null, SecurityMode.PUBLIC).getFrequency(freq) != null) {
                    return new Object[]{"Public teleporter frequency already exists."};
                }
                createFrequency(freq);
                return new Object[]{"Frequency created."};
            }
            default -> throw new NoSuchMethodException();
        }
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        return getRenderBoundingBox(getPos());
    }

    public static AxisAlignedBB getRenderBoundingBox(BlockPos pos) {
        return new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1D, pos.getY() + 3D, pos.getZ() + 1D);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public List<Vec3d> computeOcclusionSamplePoints() {
        return cullingGetAabbOcclusionSamplePoints(getRenderBoundingBox());
    }

    @Override
    public RedstoneControl getControlType() {
        return controlType;
    }

    @Override
    public void setControlType(RedstoneControl type) {
        controlType = type;
    }

    @Override
    public boolean canPulse() {
        return false;
    }

    @Override
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableByte.create(() -> status, value -> status = value));
        container.track(SyncableBoolean.create(() -> shouldRender, value -> shouldRender = value));
        container.track(SyncableInt.create(() -> controlType.ordinal(), value -> controlType = MekanismUtils.getByIndex(RedstoneControl.values(), value, controlType)));
    }

    @Override
    public TileComponentChunkLoader getChunkLoader() {
        return chunkLoaderComponent;
    }

    @Override
    public Set<ChunkPos> getChunkSet() {
        Set<ChunkPos> ret = new ObjectOpenHashSet<>();
        ret.add(new Chunk3D(Coord4D.get(this)).getPos());
        return ret;
    }

    @Override
    public TileComponentUpgrade getComponent() {
        return upgradeComponent;
    }

    @Override
    public int getRedstoneLevel() {
        return shouldRender ? 15 : 0;
    }
@Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        return capability == Capabilities.CONFIG_CARD_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (capability == Capabilities.CONFIG_CARD_CAPABILITY) {
            return Capabilities.CONFIG_CARD_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public int getBlockGuiID(Block block, int metadata) {
        return BlockStateMachine.MachineType.get(block, metadata) != null ? BlockStateMachine.MachineType.get(block, metadata).guiId : -1;
    }
}
