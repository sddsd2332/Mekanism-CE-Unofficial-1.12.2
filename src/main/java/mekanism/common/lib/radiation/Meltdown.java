package mekanism.common.lib.radiation;

import mekanism.api.NBTConstants;
import mekanism.common.util.WorldUtils;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.*;

public class Meltdown {

    private static final int DURATION = 100;

    private final BlockPos minPos, maxPos;
    private final double magnitude, chance;
    private final float radius;
    private final UUID multiblockID;

    private int ticksExisted;

    public Meltdown(BlockPos minPos, BlockPos maxPos, double magnitude, double chance, UUID multiblockID) {
        this(minPos, maxPos, magnitude, chance, 8, multiblockID);
    }

    public Meltdown(BlockPos minPos, BlockPos maxPos, double magnitude, double chance, float radius, UUID multiblockID) {
        this(minPos, maxPos, magnitude, chance, radius, multiblockID, 0);
    }

    private Meltdown(BlockPos minPos, BlockPos maxPos, double magnitude, double chance, float radius, UUID multiblockID, int ticksExisted) {
        this.minPos = minPos;
        this.maxPos = maxPos;
        this.magnitude = RadiationUtil.sanitizeMagnitude(magnitude);
        this.chance = Double.isFinite(chance) ? Math.max(0, Math.min(1, chance)) : 0;
        this.radius = Float.isFinite(radius) ? Math.max(1, Math.min(500, radius)) : 8;
        this.multiblockID = multiblockID;
        this.ticksExisted = Math.max(0, ticksExisted);
    }

    public static Meltdown load(NBTTagCompound tag) {
        return new Meltdown(
                NBTUtil.getPosFromTag(tag.getCompoundTag(NBTConstants.MIN)),
                NBTUtil.getPosFromTag(tag.getCompoundTag(NBTConstants.MAX)),
                tag.getDouble(NBTConstants.MAGNITUDE),
                tag.getDouble(NBTConstants.CHANCE),
                tag.hasKey(NBTConstants.RADIUS, 99) ? tag.getFloat(NBTConstants.RADIUS) : 8,
                tag.getUniqueId(NBTConstants.INVENTORY_ID),
                tag.getInteger(NBTConstants.AGE)
        );
    }

    public void write(NBTTagCompound tag) {
        tag.setTag(NBTConstants.MIN, NBTUtil.createPosTag(minPos));
        tag.setTag(NBTConstants.MAX, NBTUtil.createPosTag(maxPos));
        tag.setDouble(NBTConstants.MAGNITUDE, magnitude);
        tag.setDouble(NBTConstants.CHANCE, chance);
        tag.setFloat(NBTConstants.RADIUS, radius);
        tag.setUniqueId(NBTConstants.INVENTORY_ID, multiblockID);
        tag.setInteger(NBTConstants.AGE, ticksExisted);
    }

    public boolean update(World world) {
        ticksExisted++;

        if (world.rand.nextInt() % 10 == 0 && world.rand.nextDouble() < magnitude * chance) {
            int x = nextInt(world.rand, minPos.getX(), maxPos.getX());
            int y = nextInt(world.rand, minPos.getY(), maxPos.getY());
            int z = nextInt(world.rand, minPos.getZ(), maxPos.getZ());
            createExplosion(world, x, y, z, radius, true, true);
        }

        if (!WorldUtils.isBlockLoaded(world, minPos) || !WorldUtils.isBlockLoaded(world, maxPos)) {
            return true;
        }

        return ticksExisted >= DURATION;
    }

    /**
     * Creates an explosion and ensures blocks inside our meltdown bounds get destroyed.
     */
    private void createExplosion(World world, double x, double y, double z, float radius, boolean causesFire, boolean damagesTerrain) {
        Explosion explosion = new MeltdownExplosion(world, x, y, z, radius, causesFire, damagesTerrain, multiblockID);
        List<BlockPos> toBlow = new ArrayList<>();
        for (int j = 0; j < 16; ++j) {
            for (int k = 0; k < 16; ++k) {
                for (int l = 0; l < 16; ++l) {
                    if (j == 0 || j == 15 || k == 0 || k == 15 || l == 0 || l == 15) {
                        double d0 = j / 7.5 - 1.0;
                        double d1 = k / 7.5 - 1.0;
                        double d2 = l / 7.5 - 1.0;
                        double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                        d0 /= d3;
                        d1 /= d3;
                        d2 /= d3;
                        float f = radius * (0.7F + world.rand.nextFloat() * 0.6F);
                        double d4 = x;
                        double d6 = y;
                        double d8 = z;

                        for (; f > 0.0F; f -= 0.22500001F) {
                            BlockPos pos = new BlockPos(d4, d6, d8);
                            IBlockState state = world.getBlockState(pos);
                            if (state.getMaterial() != Material.AIR) {
                                f -= (state.getBlock().getExplosionResistance(world, pos, null, explosion) + 0.3F) * 0.3F;
                            }
                            if (f > 0.0F && minPos.getX() <= d4 && minPos.getY() <= d6 && minPos.getZ() <= d8 && d4 <= maxPos.getX() && d6 <= maxPos.getY() && d8 <= maxPos.getZ()) {
                                toBlow.add(pos);
                            }
                            d4 += d0 * 0.3D;
                            d6 += d1 * 0.3D;
                            d8 += d2 * 0.3D;
                        }
                    }
                }
            }
        }

        if (!ForgeEventFactory.onExplosionStart(world, explosion)) {
            explosion.doExplosionA();
            explosion.doExplosionB(true);
        }

        Collections.shuffle(toBlow, world.rand);
        List<DropData> drops = new ArrayList<>();
        for (BlockPos toExplode : toBlow) {
            IBlockState state = world.getBlockState(toExplode);
            if (state.getMaterial() != Material.AIR) {
                Block block = state.getBlock();
                if (!world.isRemote && world.getGameRules().getBoolean("doTileDrops") && block.canDropFromExplosion(explosion)) {
                    NonNullList<ItemStack> blockDrops = NonNullList.create();
                    block.getDrops(blockDrops, world, toExplode, state, 0);
                    for (ItemStack stack : blockDrops) {
                        addBlockDrops(drops, stack, toExplode);
                    }
                }
                block.onBlockExploded(world, toExplode, explosion);
            }
        }
        if (!world.isRemote) {
            for (DropData drop : drops) {
                if (!drop.stack.isEmpty()) {
                    Block.spawnAsEntity(world, drop.pos, drop.stack);
                }
            }
        }
    }

    private static void addBlockDrops(List<DropData> dropPositions, ItemStack stack, BlockPos pos) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack toInsert = stack.copy();
        for (DropData drop : dropPositions) {
            if (canMerge(drop.stack, toInsert)) {
                int transfer = Math.min(drop.stack.getMaxStackSize() - drop.stack.getCount(), toInsert.getCount());
                if (transfer > 0) {
                    drop.stack.grow(transfer);
                    toInsert.shrink(transfer);
                    if (toInsert.isEmpty()) {
                        return;
                    }
                }
            }
        }
        dropPositions.add(new DropData(toInsert, pos));
    }

    private static boolean canMerge(ItemStack existing, ItemStack stack) {
        return !existing.isEmpty() && !stack.isEmpty() && ItemStack.areItemsEqual(existing, stack) && ItemStack.areItemStackTagsEqual(existing, stack);
    }


    public static int nextInt(Random pRandom, int pMinimum, int pMaximum) {
        return pMinimum >= pMaximum ? pMinimum : pRandom.nextInt(pMaximum - pMinimum + 1) + pMinimum;
    }

    public static class MeltdownExplosion extends Explosion {

        private final UUID multiblockID;

        private MeltdownExplosion(World world, double x, double y, double z, float radius, boolean causesFire, boolean damagesTerrain, UUID multiblockID) {
            super(world, null, x, y, z, radius, causesFire, damagesTerrain);
            this.multiblockID = multiblockID;
        }

        public UUID getMultiblockID() {
            return multiblockID;
        }
    }

    private static class DropData {

        private final ItemStack stack;
        private final BlockPos pos;

        private DropData(ItemStack stack, BlockPos pos) {
            this.stack = stack;
            this.pos = pos;
        }
    }
}
