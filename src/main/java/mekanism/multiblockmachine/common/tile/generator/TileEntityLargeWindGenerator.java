package mekanism.multiblockmachine.common.tile.generator;

import com.google.common.base.Predicate;
import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.common.Mekanism;
import mekanism.common.Upgrade;
import mekanism.common.base.*;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.tile.TileEntityBoundingBlock;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.util.CableUtils;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.generators.common.tile.TileEntityGenerator;
import mekanism.multiblockmachine.client.render.block.generator.bloom.BloomRenderLargeWindGenerator;
import mekanism.multiblockmachine.common.MekanismMultiblockMachine;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.*;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TileEntityLargeWindGenerator extends TileEntityGenerator implements IAdvancedBoundingBlock, IUpgradeTile, ISpecialSelectionWireframeTile {

    public static final float SPEED = 32F;
    public static final float SPEED_SCALED = 256F / SPEED;
    static final String[] methods = new String[]{"getEnergy", "getOutput", "getMaxEnergy", "getEnergyNeeded", "getMultiplier"};
    private double angle;
    private float currentMultiplier;
    private boolean isBlacklistDimension = false;
    public int processes = MekanismConfig.current().multiblock.LargeWindGeneratorProcesses.val();
    public TileComponentUpgrade upgradeComponent;
    public int numPowering;
    private int explode;
    private boolean machineStop;
    private boolean machineStop2;
    private boolean bladeDamage;
    private EnergyInventorySlot energySlot;

    public TileEntityLargeWindGenerator() {
        super("wind", "LargeWindGenerator", 0, 0);
        upgradeComponent = new TileComponentUpgrade(this, Upgrade.ENERGY);
        upgradeComponent.setSupported(Upgrade.THREAD);
        initializeInventorySlots();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = InventorySlotHelper.readOnly();
        energySlot = builder.addSlot(EnergyInventorySlot.drain(this, listener, 143, 35));
        return builder.build();
    }


    public int getThread() {
        int thread = 1;
        if (upgradeComponent.isUpgradeInstalled(Upgrade.THREAD)) {
            thread += upgradeComponent.getUpgrades(Upgrade.THREAD);
        }
        return thread;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        isBlacklistDimension = MekanismConfig.current().generators.windGenerationDimBlacklist.val().contains(world.provider.getDimension());
        if (isBlacklistDimension) {
            setActive(false);
        }
    }

    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        energySlot.drainContainer();
        // If we're in a blacklisted dimension, there's nothing more to do
        if (isBlacklistDimension) {
            return;
        }
        if (ticker % 20 == 0) {
            currentMultiplier = getMultiplier();
            setActive(MekanismUtils.canFunction(this) && currentMultiplier > 0);
        }
        if (getActive()) {
            getEnergyContainer().insert(getEnergyAdd(), Action.EXECUTE, AutomationType.INTERNAL);
        }
    }

    public double getEnergyAdd() {
        return MekanismConfig.current().multiblock.LargeWindGenerationMin.val() * currentMultiplier * processes * getThread();
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        if (ticker % 200 == 0 && MekanismConfig.current().multiblock.LargeWindGenerationRangeStops.val() && !machineStop2) {
            RangeStops();
        }
        if (getActive()) {
            if (MekanismConfig.current().multiblock.LargeWindGenerationDamage.val()) {
                kill();
            }
        }
        if (explode != 0) {
            bladeDamage = true;
        }
        if (explode >= MekanismConfig.current().multiblock.LargeWindGenerationExplodeCount.val() && MekanismConfig.current().multiblock.LargewindGenerationExplode.val()) {
            explode();
        }
    }


    public void kill() {
        AxisAlignedBB death_zone = new AxisAlignedBB(getPos());
        if (facing == EnumFacing.NORTH) {
            death_zone = new AxisAlignedBB(
                    getPos().up(46).north(3).getX() + 22, getPos().up(46).north(3).getY() + 22, getPos().up(46).north(3).getZ(),
                    getPos().up(46).north(4).getX() - 22, getPos().up(46).north(4).getY() - 22, getPos().up(46).north(4).getZ());
        } else if (facing == EnumFacing.SOUTH) {
            death_zone = new AxisAlignedBB(
                    getPos().up(46).south(3).getX() + 22, getPos().up(46).south(3).getY() + 22, getPos().up(46).south(3).getZ(),
                    getPos().up(46).south(4).getX() - 22, getPos().up(46).south(4).getY() - 22, getPos().up(46).south(4).getZ());
        } else if (facing == EnumFacing.WEST) {
            death_zone = new AxisAlignedBB(
                    getPos().up(46).west(3).getX(), getPos().up(46).west(3).getY() + 22, getPos().up(46).west(3).getZ() + 22,
                    getPos().up(46).west(4).getX(), getPos().up(46).west(4).getY() - 22, getPos().up(46).west(4).getZ() - 22);
        } else if (facing == EnumFacing.EAST) {
            death_zone = new AxisAlignedBB(
                    getPos().up(46).east(3).getX(), getPos().up(46).east(3).getY() + 22, getPos().up(46).east(3).getZ() + 22,
                    getPos().up(46).east(4).getX(), getPos().up(46).east(4).getY() - 22, getPos().up(46).east(4).getZ() - 22);
        }

        List<Entity> entitiesToDie = getWorld().getEntitiesWithinAABB(Entity.class, death_zone, isCanKill);

        for (Entity entity : entitiesToDie) {
            if (entity instanceof EntityPlayer player && player.capabilities.isCreativeMode) {
                continue;
            }
            entity.attackEntityFrom(DamageSource.FLY_INTO_WALL, Float.MAX_VALUE);
            machineStop = true;
            explode += 1;
        }
    }

    Predicate<Entity> isCanKill = entity -> {
        if (entity instanceof EntityPlayer player) {
            return player.isEntityAlive() && !player.isSpectator() && !player.isCreative();
        }
        return entity.isEntityAlive();
    };


    @Override
    public void addTileSyncTask() {
        if (getEnergy() > 0) {
            CableUtils.emit(this, 4);
        }
    }


    private void RangeStops() {
        if (machineStop2) {
            return;
        }

        World world = getWorld();
        BlockPos currentPos = getPos();
        ChunkPos currentChunk = new ChunkPos(currentPos);
        int rangeCheck = MekanismConfig.current().multiblock.LargeWindGeneratorRangeCheck.val();
        int range;
        if (rangeCheck % 16 != 0) {
            range = rangeCheck / 16 + 1;
        } else {
            range = rangeCheck / 16;
        }
        for (int chunkX = currentChunk.x - range; chunkX <= currentChunk.x + range; chunkX++) {
            for (int chunkZ = currentChunk.z - range; chunkZ <= currentChunk.z + range; chunkZ++) {
                Chunk chunk = world.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                Map<BlockPos, TileEntity> tileEntityMap = chunk.getTileEntityMap();
                for (TileEntity tileEntity : tileEntityMap.values()) {
                    if (tileEntity instanceof TileEntityLargeWindGenerator && tileEntity != this) {
                        BlockPos tilePos = tileEntity.getPos();

                        double distanceSquared = currentPos.distanceSq(tilePos);
                        if (distanceSquared <= rangeCheck * rangeCheck) {
                            machineStop2 = true;
                            return;
                        }
                    }
                }
            }
        }
    }

    private void explode() {
        int BlastRadius = MekanismConfig.current().multiblock.LargeWindGenerationBlastRadius.val();
        if (facing == EnumFacing.NORTH) {
            world.createExplosion(null, getPos().up(46).north(4).getX(), getPos().up(46).north(4).getY(), getPos().up(46).north(4).getZ(), BlastRadius, true);
            world.spawnParticle(EnumParticleTypes.LAVA, getPos().up(46).north(3).getX(), getPos().up(46).north(3).getY(), getPos().up(46).north(3).getZ(), 0, 0, 0);
        } else if (facing == EnumFacing.SOUTH) {
            world.createExplosion(null, getPos().up(46).south(4).getX(), getPos().up(46).south(4).getY(), getPos().up(46).south(4).getZ(), BlastRadius, true);
            world.spawnParticle(EnumParticleTypes.LAVA, getPos().up(46).south(3).getX(), getPos().up(46).south(3).getY(), getPos().up(46).south(3).getZ(), 0, 0, 0);
        } else if (facing == EnumFacing.WEST) {
            world.createExplosion(null, getPos().up(46).west(4).getX(), getPos().up(46).west(4).getY(), getPos().up(46).west(4).getZ(), BlastRadius, true);
            world.spawnParticle(EnumParticleTypes.LAVA, getPos().up(46).west(3).getX(), getPos().up(46).west(3).getY(), getPos().up(46).west(3).getZ(), 0, 0, 0);
        } else if (facing == EnumFacing.EAST) {
            world.createExplosion(null, getPos().up(46).east(4).getX(), getPos().up(46).east(4).getY(), getPos().up(46).east(4).getZ(), BlastRadius, true);
            world.spawnParticle(EnumParticleTypes.LAVA, getPos().up(46).east(3).getX(), getPos().up(46).east(3).getY(), getPos().up(46).east(3).getZ(), 0, 0, 0);
        }
    }

    @Override
    public void onUpdateClient() {
        super.onUpdateClient();
        if (getActive()) {
            angle = (angle + (getPos().getY() + 4F) / SPEED_SCALED) % 360;
        }
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            int type = dataStream.readInt();
            if (type == 1) {
                machineStop = !machineStop;
            } else if (type == 2) {
                machineStop2 = !machineStop2;
            }
        }
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            currentMultiplier = dataStream.readFloat();
            isBlacklistDimension = dataStream.readBoolean();
            numPowering = dataStream.readInt();
            machineStop = dataStream.readBoolean();
            machineStop2 = dataStream.readBoolean();
            explode = dataStream.readInt();
            bladeDamage = dataStream.readBoolean();
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(currentMultiplier);
        data.add(isBlacklistDimension);
        data.add(numPowering);
        data.add(machineStop);
        data.add(machineStop2);
        data.add(explode);
        data.add(bladeDamage);
        return data;
    }


    /**
     * Determines the current output multiplier, taking sky visibility and height into account.
     **/
    public float getMultiplier() {
        //Wind turbine head and tail
        BlockPos head = getPos().up(46);
        BlockPos head2 = getPos().up(46);
        if (facing == EnumFacing.NORTH) {
            head = head.north(3);
            head2 = head2.north(4);
        } else if (facing == EnumFacing.SOUTH) {
            head = head.south(3);
            head2 = head2.south(4);
        } else if (facing == EnumFacing.WEST) {
            head = head.west(3);
            head2 = head2.west(4);
        } else if (facing == EnumFacing.EAST) {
            head = head.east(3);
            head2 = head2.east(4);
        }

        //这是为了防止主线程等待机器的异步，然后机器等待区块的加载,然后区块又在等待主线程,造成循环等待加载
        Chunk chunkCheckA = world.getChunkProvider().getLoadedChunk(head.getX() >> 4, head.getZ() >> 4);
        Chunk chunkCheckB = world.getChunkProvider().getLoadedChunk(head2.getX() >> 4, head2.getZ() >> 4);
        if (chunkCheckA != null && chunkCheckB != null && !chunkCheckA.isEmpty() && !chunkCheckB.isEmpty()) {
            if (world.canSeeSky(head) && world.canSeeSky(head2)) {
                int minY = MekanismConfig.current().multiblock.LargeWindGenerationMinY.val();
                int maxY = MekanismConfig.current().multiblock.LargeWindGenerationMaxY.val();
                float clampedY = (float) Math.min(maxY, Math.max(minY, head.getY()));
                float minG = (float) MekanismConfig.current().multiblock.LargeWindGenerationMin.val();
                float maxG = (float) MekanismConfig.current().multiblock.LargeWindGenerationMax.val();
                //Prevents the possibility of writing opposite values; https://github.com/Thorfusion/Mekanism-Community-Edition/issues/150
                int rangeY = maxY < minY ? minY - maxY : maxY - minY;
                if (rangeY <= 0 || minG <= 0 || Float.isNaN(minG) || Float.isInfinite(minG) || Float.isNaN(maxG) || Float.isInfinite(maxG)) {
                    return 0;
                }
                float rangG = maxG < minG ? minG - maxG : maxG - minG;
                float slope = rangG / rangeY;
                float toGen = minG + (slope * (clampedY - minY));
                float multiplier = toGen / minG;
                if (Float.isNaN(multiplier) || Float.isInfinite(multiplier)) {
                    return 0;
                }
                return multiplier;
            }
        }
        return 0;
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        return switch (method) {
            case 0 -> new Object[]{electricityStored};
            case 1 -> new Object[]{getMaxOutput()};
            case 2 -> new Object[]{getMaxEnergy()};
            case 3 -> new Object[]{getMaxEnergy() - electricityStored.get()};
            case 4 -> new Object[]{getMultiplier()};
            default -> throw new NoSuchMethodException();
        };
    }

    @Override
    public boolean canOperate() {
        return getEnergyContainer().getNeeded() > 0 && getMultiplier() > 0 && MekanismUtils.canFunction(this);
    }

    @Override
    public void onPlace() {
        //bottom
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                MekanismUtils.makeAdvancedBoundingBlock(world, getPos().add(x, 0, z), Coord4D.get(this));
                world.notifyNeighborsOfStateChange(getPos().add(x, 0, z), getBlockType(), true);
            }
        }
        //Second floor
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                MekanismUtils.makeBoundingBlock(world, getPos().add(x, 1, z), Coord4D.get(this));
                world.notifyNeighborsOfStateChange(getPos().add(x, 1, z), getBlockType(), true);
            }
        }
        if (facing == EnumFacing.WEST) {
            for (int z = -1; z <= 1; z++) {
                MekanismUtils.makeBoundingBlock(world, getPos().add(3, 1, z), Coord4D.get(this));
                world.notifyNeighborsOfStateChange(getPos().add(3, 1, z), getBlockType(), true);
            }
        } else if (facing == EnumFacing.EAST) {
            for (int z = -1; z <= 1; z++) {
                MekanismUtils.makeBoundingBlock(world, getPos().add(-3, 1, z), Coord4D.get(this));
                world.notifyNeighborsOfStateChange(getPos().add(-3, 1, z), getBlockType(), true);
            }
        } else if (facing == EnumFacing.NORTH) {
            for (int x = -1; x <= 1; x++) {
                MekanismUtils.makeBoundingBlock(world, getPos().add(x, 1, 3), Coord4D.get(this));
                world.notifyNeighborsOfStateChange(getPos().add(x, 1, 3), getBlockType(), true);
            }
        } else if (facing == EnumFacing.SOUTH) {
            for (int x = -1; x <= 1; x++) {
                MekanismUtils.makeBoundingBlock(world, getPos().add(x, 1, -3), Coord4D.get(this));
                world.notifyNeighborsOfStateChange(getPos().add(x, 1, -3), getBlockType(), true);
            }
        }
        //Wind turbine tower height (including head)
        for (int y = 2; y <= 47; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    MekanismUtils.makeBoundingBlock(world, getPos().add(x, y, z), Coord4D.get(this));
                    world.notifyNeighborsOfStateChange(getPos().add(x, y, z), getBlockType(), true);
                }
            }
        }

        //Wind turbine head and tail
        for (int y = 43; y <= 47; y++) {
            if (facing == EnumFacing.SOUTH) {
                for (int x = -2; x <= 2; x++) {
                    for (int z = -5; z <= -3; z++) {
                        MekanismUtils.makeBoundingBlock(world, getPos().add(x, y, z), Coord4D.get(this));
                        world.notifyNeighborsOfStateChange(getPos().add(x, y, z), getBlockType(), true);
                    }
                    for (int z = 3; z <= 4; z++) {
                        MekanismUtils.makeBoundingBlock(world, getPos().add(x, y, z), Coord4D.get(this));
                        world.notifyNeighborsOfStateChange(getPos().add(x, y, z), getBlockType(), true);
                    }
                }
            } else if (facing == EnumFacing.NORTH) {
                for (int x = -2; x <= 2; x++) {
                    for (int z = 3; z <= 5; z++) {
                        MekanismUtils.makeBoundingBlock(world, getPos().add(x, y, z), Coord4D.get(this));
                        world.notifyNeighborsOfStateChange(getPos().add(x, y, z), getBlockType(), true);
                    }
                    for (int z = -4; z <= -3; z++) {
                        MekanismUtils.makeBoundingBlock(world, getPos().add(x, y, z), Coord4D.get(this));
                        world.notifyNeighborsOfStateChange(getPos().add(x, y, z), getBlockType(), true);
                    }
                }
            } else if (facing == EnumFacing.EAST) {
                for (int z = -2; z <= 2; z++) {
                    for (int x = -5; x <= -3; x++) {
                        MekanismUtils.makeBoundingBlock(world, getPos().add(x, y, z), Coord4D.get(this));
                        world.notifyNeighborsOfStateChange(getPos().add(x, y, z), getBlockType(), true);
                    }
                    for (int x = 3; x <= 4; x++) {
                        MekanismUtils.makeBoundingBlock(world, getPos().add(x, y, z), Coord4D.get(this));
                        world.notifyNeighborsOfStateChange(getPos().add(x, y, z), getBlockType(), true);
                    }
                }
            } else if (facing == EnumFacing.WEST) {
                for (int z = -2; z <= 2; z++) {
                    for (int x = 3; x <= 5; x++) {
                        MekanismUtils.makeBoundingBlock(world, getPos().add(x, y, z), Coord4D.get(this));
                        world.notifyNeighborsOfStateChange(getPos().add(x, y, z), getBlockType(), true);
                    }
                    for (int x = -4; x <= -3; x++) {
                        MekanismUtils.makeBoundingBlock(world, getPos().add(x, y, z), Coord4D.get(this));
                        world.notifyNeighborsOfStateChange(getPos().add(x, y, z), getBlockType(), true);
                    }
                }
            }
        }

        // Check to see if the placement is happening in a blacklisted dimension
        isBlacklistDimension = MekanismConfig.current().generators.windGenerationDimBlacklist.val().contains(world.provider.getDimension());
    }


    @Override
    public double getMaxOutput() {
        return (upgradeComponent.isUpgradeInstalled(Upgrade.ENERGY) ? MekanismUtils.getMaxEnergy(this, getTierEnergy()) : getTierEnergy()) * 2;
    }

    @Override
    public double getMaxEnergy() {
        return upgradeComponent.isUpgradeInstalled(Upgrade.ENERGY) ? MekanismUtils.getMaxEnergy(this, getTierEnergy()) : getTierEnergy();
    }

    public double getTierEnergy() {
        return MekanismConfig.current().generators.windGeneratorStorage.val() * processes * getThread();
    }

    @Override
    public void onBreak() {
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                world.setBlockToAir(getPos().add(x, 0, z));
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.setBlockToAir(getPos().add(x, 1, z));
            }
        }
        if (facing == EnumFacing.WEST) {
            for (int z = -1; z <= 1; z++) {
                world.setBlockToAir(getPos().add(3, 1, z));
            }
        } else if (facing == EnumFacing.EAST) {
            for (int z = -1; z <= 1; z++) {
                world.setBlockToAir(getPos().add(-3, 1, z));
            }
        } else if (facing == EnumFacing.NORTH) {
            for (int x = -1; x <= 1; x++) {
                world.setBlockToAir(getPos().add(x, 1, 3));
            }
        } else if (facing == EnumFacing.SOUTH) {
            for (int x = -1; x <= 1; x++) {
                world.setBlockToAir(getPos().add(x, 1, -3));
            }
        }
        for (int y = 2; y <= 47; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    world.setBlockToAir(getPos().add(x, y, z));
                }
            }
        }
        for (int y = 43; y <= 47; y++) {
            if (facing == EnumFacing.SOUTH) {
                for (int x = -2; x <= 2; x++) {
                    for (int z = -5; z <= -3; z++) {
                        world.setBlockToAir(getPos().add(x, y, z));
                    }
                    for (int z = 3; z <= 4; z++) {
                        world.setBlockToAir(getPos().add(x, y, z));
                    }
                }
            } else if (facing == EnumFacing.NORTH) {
                for (int x = -2; x <= 2; x++) {
                    for (int z = 3; z <= 5; z++) {
                        world.setBlockToAir(getPos().add(x, y, z));
                    }
                    for (int z = -4; z <= -3; z++) {
                        world.setBlockToAir(getPos().add(x, y, z));
                    }
                }
            } else if (facing == EnumFacing.EAST) {
                for (int z = -2; z <= 2; z++) {
                    for (int x = -5; x <= -3; x++) {
                        world.setBlockToAir(getPos().add(x, y, z));
                    }
                    for (int x = 3; x <= 4; x++) {
                        world.setBlockToAir(getPos().add(x, y, z));
                    }
                }
            } else if (facing == EnumFacing.WEST) {
                for (int z = -2; z <= 2; z++) {
                    for (int x = 3; x <= 5; x++) {
                        world.setBlockToAir(getPos().add(x, y, z));
                    }
                    for (int x = -4; x <= -3; x++) {
                        world.setBlockToAir(getPos().add(x, y, z));
                    }
                }
            }
        }
        world.setBlockToAir(getPos());
    }


    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        numPowering = nbtTags.getInteger("numPowering");
        explode = nbtTags.getInteger("explode");
        machineStop = nbtTags.getBoolean("machineStop");
        machineStop2 = nbtTags.getBoolean("machineStop2");
        bladeDamage = nbtTags.getBoolean("bladeDamage");
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("numPowering", numPowering);
        nbtTags.setInteger("explode", explode);
        nbtTags.setBoolean("machineStop", machineStop);
        nbtTags.setBoolean("machineStop2", machineStop2);
        nbtTags.setBoolean("bladeDamage", bladeDamage);
    }

    @Override
    public boolean renderUpdate() {
        return false;
    }

    @Override
    public boolean lightUpdate() {
        return false;
    }

    public void setAngle(double angle) {
        this.angle = angle;
    }

    public double getAngle() {
        return angle;
    }

    public boolean getMachineStop2() {
        return machineStop2;
    }

    public boolean getMachineStop() {
        return machineStop;
    }

    public boolean getBladeDamage() {
        return bladeDamage;
    }

    public int getBladeDamageNumber() {
        return explode;
    }

    public boolean isBlacklistDimension() {
        return isBlacklistDimension;
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return InventoryUtils.EMPTY;
    }
@Override
    public int getBlockGuiID(Block block, int metadata) {
        return 3;
    }

    @Override
    public IGuiProvider guiProvider() {
        return MekanismMultiblockMachine.proxy;
    }

    @Override
    public TileComponentUpgrade getComponent() {
        return upgradeComponent;
    }

    @Nonnull
    @Override
    public String getName() {
        return LangUtils.localize("tile.LargeWindGenerator.name");
    }

    @Override
    public boolean canBoundReceiveEnergy(BlockPos location, EnumFacing side) {
        return false;
    }

    @Override
    public boolean canBoundOutPutEnergy(BlockPos coord, EnumFacing side) {
        EnumFacing left = MekanismUtils.getLeft(facing);
        EnumFacing right = MekanismUtils.getRight(facing);
        if (coord.equals(getPos().offset(left, 3))) {
            return side == left;
        } else if (coord.equals(getPos().offset(right, 3))) {
            return side == right;
        } else if (coord.equals(getPos().offset(facing, 3))) {
            return side == facing;
        } else {
            return false;
        }
    }

    @Override
    public void onPower() {
        numPowering++;
    }

    @Override
    public void onNoPower() {
        numPowering--;
    }

    @Override
    public boolean isPowered() {
        return redstone || numPowering > 0;
    }

    @Override
    public NBTTagCompound getConfigurationData(NBTTagCompound nbtTags) {
        return nbtTags;
    }

    @Override
    public void setConfigurationData(NBTTagCompound nbtTags) {
    }

    @Override
    public String getDataType() {
        return getName();
    }

    @Override
    public boolean hasOffsetCapability(@NotNull Capability<?> capability, @Nullable EnumFacing side, @NotNull Vec3i offset) {
        if (isOffsetCapabilityDisabled(capability, side, offset)) {
            return false;
        }
        if (isManagedStrictEnergy(capability)) {
            return getEnergyHandler(capability, side) != null;
        } else if (capability == CapabilityEnergy.ENERGY || isTesla(capability, side)) {
            return true;
        }
        return hasCapability(capability, side);
    }

    @Override
    public @Nullable <T> T getOffsetCapability(@NotNull Capability<T> capability, @Nullable EnumFacing side, @NotNull Vec3i offset) {
        if (isOffsetCapabilityDisabled(capability, side, offset)) {
            return null;
        } else if (isManagedStrictEnergy(capability)) {
            return getEnergyHandler(capability, side);
        } else if (isTesla(capability, side)) {
            return (T) getTeslaEnergyWrapper(side);
        } else if (capability == CapabilityEnergy.ENERGY) {
            return CapabilityEnergy.ENERGY.cast(getForgeEnergyWrapper(side));
        }
        return getCapability(capability, side);
    }

    @Override
    public boolean isOffsetCapabilityDisabled(@NotNull Capability<?> capability, @Nullable EnumFacing side, @NotNull Vec3i offset) {
        if (isManagedStrictEnergy(capability) || capability == CapabilityEnergy.ENERGY || isTesla(capability, side)) {
            EnumFacing left = MekanismUtils.getLeft(facing);
            EnumFacing right = MekanismUtils.getRight(facing);
            if (offset.equals(new Vec3i(left.getXOffset() * 3, 0, left.getZOffset() * 3))) {
                //Disable if left power port but wrong side of the port
                return side != left;
            } else if (offset.equals(new Vec3i(right.getXOffset() * 3, 0, right.getZOffset() * 3))) {
                //Disable if right power port but wrong side of the port
                return side != right;
            } else if (offset.equals(new Vec3i(facing.getXOffset() * 3, 0, facing.getZOffset() * 3))) {
                return side != facing;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isManagedStrictEnergy(capability) || capability == CapabilityEnergy.ENERGY || isTesla(capability, side)) {
            return true;
        }
        return super.isCapabilityDisabled(capability, side);
    }

    private boolean isManagedStrictEnergy(@Nonnull Capability<?> capability) {
        return capability == Capabilities.STRICT_ENERGY_CAPABILITY || isStrictEnergy(capability);
    }

    @Override
    public boolean sideIsOutput(EnumFacing side) {
        return side == MekanismUtils.getLeft(facing) || side == MekanismUtils.getRight(facing) || side == facing;
    }


    @Override
    public void validate() {
        super.validate();
        if (isRemote()) {
            if (Mekanism.hooks.Bloom && MekanismConfig.current().client.enableBloom.val()) {
                try {
                    new BloomRenderLargeWindGenerator(this);
                } catch (LinkageError e) {
                    mekanism.common.util.BloomDependencyHelper.disableBloom("BloomRenderLargeWindGenerator", e);
                }
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Class<?> getSelectionWireframeModelClass() {
        return mekanism.multiblockmachine.client.model.generator.ModelLargeWindGenerator.class;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getSelectionWireframeAnimationCacheKey(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!MekanismConfig.current().client.windGeneratorRotating.val()) {
            return 0;
        }
        // Quantize to 0.5 degree to cap cache growth while keeping animation smooth.
        return Math.floorMod((int) Math.round(getSelectionWireframeAngle() * 2D), 720);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void applySelectionWireframeModelState(Object model, IBlockState state, IBlockAccess world, BlockPos pos) {
        if (model instanceof mekanism.multiblockmachine.client.model.generator.ModelLargeWindGenerator windModel) {
            windModel.applySelectionFanAngle(getSelectionWireframeAngle());
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public List<Vec3d> computeOcclusionSamplePoints() {
        List<Vec3d> samplePoints = new ArrayList<>(super.computeOcclusionSamplePoints());
        World world = getWorld();
        if (world == null) {
            return samplePoints;
        }
        EnumFacing direction = facing == null ? EnumFacing.NORTH : facing;
        EnumFacing left = MekanismUtils.getLeft(direction);
        EnumFacing right = MekanismUtils.getRight(direction);
        EnumFacing back = direction.getOpposite();

        // Keep the renderer alive when only the upper structure/head-tail bounding blocks are visible.
        addUpperBoundingProbePoints(world, samplePoints, getPos().up(47));
        addUpperBoundingProbePoints(world, samplePoints, getPos().up(47).offset(left, 2));
        addUpperBoundingProbePoints(world, samplePoints, getPos().up(47).offset(right, 2));
        addUpperBoundingProbePoints(world, samplePoints, getPos().up(47).offset(direction, 2));
        addUpperBoundingProbePoints(world, samplePoints, getPos().up(47).offset(back, 2));

        BlockPos headCenter = getPos().up(46).offset(direction, 4);
        BlockPos tailCenter = getPos().up(46).offset(back, 3);
        addUpperBoundingProbePoints(world, samplePoints, headCenter);
        addUpperBoundingProbePoints(world, samplePoints, headCenter.offset(left, 2));
        addUpperBoundingProbePoints(world, samplePoints, headCenter.offset(right, 2));
        addUpperBoundingProbePoints(world, samplePoints, headCenter.offset(direction, 1));
        addUpperBoundingProbePoints(world, samplePoints, tailCenter);
        addUpperBoundingProbePoints(world, samplePoints, tailCenter.offset(left, 2));
        addUpperBoundingProbePoints(world, samplePoints, tailCenter.offset(right, 2));
        addUpperBoundingProbePoints(world, samplePoints, tailCenter.offset(back, 1));

        return samplePoints;
    }

    @SideOnly(Side.CLIENT)
    private void addUpperBoundingProbePoints(World world, List<Vec3d> samplePoints, BlockPos blockPos) {
        TileEntity tileEntity = world.getTileEntity(blockPos);
        if (!(tileEntity instanceof TileEntityBoundingBlock boundingBlock) || !getPos().equals(boundingBlock.getMainPos())) {
            return;
        }
        double x = blockPos.getX();
        double y = blockPos.getY();
        double z = blockPos.getZ();
        samplePoints.add(new Vec3d(x + 0.5D, y + 0.5D, z + 0.5D));

        double min = 0.08D;
        double max = 0.92D;
        samplePoints.add(new Vec3d(x + min, y + min, z + min));
        samplePoints.add(new Vec3d(x + min, y + min, z + max));
        samplePoints.add(new Vec3d(x + min, y + max, z + min));
        samplePoints.add(new Vec3d(x + min, y + max, z + max));
        samplePoints.add(new Vec3d(x + max, y + min, z + min));
        samplePoints.add(new Vec3d(x + max, y + min, z + max));
        samplePoints.add(new Vec3d(x + max, y + max, z + min));
        samplePoints.add(new Vec3d(x + max, y + max, z + max));
    }

    @SideOnly(Side.CLIENT)
    private double getSelectionWireframeAngle() {
        double angle = getAngle();
        if (getActive()) {
            float partial = Minecraft.getMinecraft().getRenderPartialTicks();
            angle = (angle + ((getPos().getY() + 46F) / SPEED_SCALED) * partial) % 360D;
        }
        return angle < 0D ? angle + 360D : angle;
    }

    @Override
    public boolean hasFastRenderer() {
        return false;
    }

}
