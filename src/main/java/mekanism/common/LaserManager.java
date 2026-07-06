package mekanism.common;

import mekanism.api.Coord4D;
import mekanism.api.Pos3D;
import mekanism.api.lasers.ILaserDissipation;
import mekanism.api.lasers.ILaserReceptor;
import mekanism.common.advancements.MekanismCriteriaTriggers;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.CapabilityUtils;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants.WorldEvents;
import net.minecraftforge.event.world.BlockEvent;

import java.util.List;

public class LaserManager {

    public static LaserInfo fireLaser(TileEntity from, EnumFacing direction, double energy, World world) {
        return fireLaser(new Pos3D(from).centre().translate(direction, 0.501), direction, energy, world);
    }

    public static LaserInfo fireLaser(Pos3D from, EnumFacing direction, double energy, World world) {
        Pos3D to = from.clone().translate(direction, MekanismConfig.current().general.laserRange.val() - 0.002);
        RayTraceResult mop = world.rayTraceBlocks(from, to);
        if (mop != null) {
            to = new Pos3D(mop.hitVec);
            Coord4D toCoord = new Coord4D(mop.getBlockPos(), world);
            TileEntity tile = toCoord.getTileEntity(world);
            if (isReceptor(tile, mop.sideHit)) {
                ILaserReceptor receptor = getReceptor(tile, mop.sideHit);
                if (!receptor.canLasersDig()) {
                    receptor.receiveLaserEnergy(energy, mop.sideHit);
                }
            }
        }
        from.translateExcludingSide(direction, -0.1);
        to.translateExcludingSide(direction, 0.1);

        boolean foundEntity = false;
        for (Entity entity : world.getEntitiesWithinAABB(Entity.class, Pos3D.getAABB(from, to))) {
            foundEntity = true;
            if (entity.isEntityInvulnerable(MekanismDamageSource.LASER)) {
                break;
            }
            float damage = (float) energy / 1000F;
            if (entity instanceof EntityLivingBase base) {
                if (canBlockLaser(base, from)) {
                    double damageBlocked = damageShield(base, base.getActiveItemStack(), damage, 1);
                    if (damageBlocked > 0) {
                        if (base instanceof EntityPlayerMP playerMP) {
                            MekanismCriteriaTriggers.BLOCK_LASER.trigger(playerMP);
                        }
                        damage -= (float) damageBlocked;
                        if (damage <= 0) {
                            continue;
                        }
                    }
                }

                double dissipationPercent = 0;
                double refractionPercent = 0;
                for (ItemStack armor : base.getArmorInventoryList()) {
                    if (!armor.isEmpty()) {
                        ILaserDissipation laserDissipation = armor.getCapability(Capabilities.LASER_DISSIPATION_CAPABILITY, null);
                        if (laserDissipation != null) {
                            dissipationPercent += laserDissipation.getDissipationPercent();
                            refractionPercent += laserDissipation.getRefractionPercent();
                        }
                    }
                }

                if (dissipationPercent >= 1) {
                    //If we will fully dissipate it, don't bother checking the rest of the armor slots
                    break;
                }
                if (refractionPercent >= 1) {
                    break;
                }
            }

            if (!entity.isImmuneToFire()) {
                entity.setFire((int) (energy / 1000));
            }
            if (damage > 0.256F) {
                entity.attackEntityFrom(MekanismDamageSource.LASER.fromPosition(from), damage);
            }
        }
        return new LaserInfo(mop, foundEntity);
    }

    public static List<ItemStack> breakBlock(Coord4D blockCoord, boolean dropAtBlock, World world, BlockPos laserPos) {
        if (!MekanismConfig.current().general.aestheticWorldDamage.val()) {
            return null;
        }

        IBlockState state = blockCoord.getBlockState(world);
        Block blockHit = state.getBlock();
        EntityPlayer dummy = Mekanism.proxy.getDummyPlayer((WorldServer) world, laserPos).get();
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(world, blockCoord.getPos(), state, dummy);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            return null;
        }
        NonNullList<ItemStack> ret = null;
        if (dropAtBlock) {
            blockHit.dropBlockAsItem(world, blockCoord.getPos(), state, 0);
        } else {
            ret = NonNullList.create();
            blockHit.getDrops(ret, world, blockCoord.getPos(), state, 0);
        }
        blockHit.breakBlock(world, blockCoord.getPos(), state);
        world.setBlockToAir(blockCoord.getPos());
        world.playEvent(WorldEvents.BREAK_BLOCK_EFFECTS, blockCoord.getPos(), Block.getStateId(state));
        return ret;
    }

    public static RayTraceResult fireLaserClient(TileEntity from, EnumFacing direction, double energy, World world) {
        return fireLaserClient(new Pos3D(from).centre().translate(direction, 0.501), direction, energy, world);
    }

    public static RayTraceResult fireLaserClient(Pos3D from, EnumFacing direction, double energy, World world) {
        Pos3D to = from.clone().translate(direction, MekanismConfig.current().general.laserRange.val() - 0.002);
        RayTraceResult mop = world.rayTraceBlocks(from, to);
        if (mop != null) {
            to = new Pos3D(mop.hitVec);
        }
        from.translate(direction, -0.501);
        Mekanism.proxy.renderLaser(world, from, to, direction, energy);
        return mop;
    }

    public static boolean isReceptor(TileEntity tile, EnumFacing side) {
        return CapabilityUtils.hasCapability(tile, Capabilities.LASER_RECEPTOR_CAPABILITY, side);
    }

    public static ILaserReceptor getReceptor(TileEntity tile, EnumFacing side) {
        return CapabilityUtils.getCapability(tile, Capabilities.LASER_RECEPTOR_CAPABILITY, side);
    }

    public static class LaserInfo {

        public RayTraceResult movingPos;

        public boolean foundEntity;

        public LaserInfo(RayTraceResult mop, boolean b) {
            movingPos = mop;
            foundEntity = b;
        }
    }

    public static Pos3D adjustPosition(Pos3D d, EnumFacing direction, Entity entity) {
        if (direction.getAxis() == EnumFacing.Axis.X) {
            return new Pos3D(entity.posX, d.y, d.z);
        } else if (direction.getAxis() == EnumFacing.Axis.Y) {
            return new Pos3D(d.x, entity.posY, d.z);
        } //Axis.Z
        return new Pos3D(d.x, d.y, entity.posZ);
    }

    private static boolean canBlockLaser(EntityLivingBase livingEntity, Pos3D from) {
        if (!livingEntity.isActiveItemStackBlocking()) {
            return false;
        }
        Vec3d look = livingEntity.getLook(1.0F);
        Vec3d damageDirection = from.subtractReverse(new Vec3d(livingEntity.posX, livingEntity.posY, livingEntity.posZ)).normalize();
        damageDirection = new Vec3d(damageDirection.x, 0.0D, damageDirection.z);
        return damageDirection.dotProduct(look) < 0.0D;
    }

    private static double damageShield(EntityLivingBase livingEntity, ItemStack activeStack, double damage, int absorptionRatio) {
        //Absorb part of the damage based on the given absorption ratio
        double damageBlocked = damage;
        double effectiveDamage = damage / absorptionRatio;
        if (effectiveDamage >= 1) {
            //Allow the shield to absorb sub single unit damage values for free
            int durabilityNeeded = 1 + MathHelper.floor(effectiveDamage);
            int activeDurability = activeStack.getMaxDamage() - activeStack.getItemDamage();
            EnumHand hand = livingEntity.getActiveHand();
            activeStack.damageItem(durabilityNeeded, livingEntity);
            if (livingEntity instanceof EntityPlayer player) {
                net.minecraftforge.event.ForgeEventFactory.onPlayerDestroyItem(player, activeStack, hand);
            }
            if (activeStack.isEmpty()) {
                if (hand == EnumHand.MAIN_HAND) {
                    livingEntity.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, ItemStack.EMPTY);
                } else {
                    livingEntity.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, ItemStack.EMPTY);
                }
                livingEntity.resetActiveHand();
                livingEntity.playSound(SoundEvents.ITEM_SHIELD_BREAK, 0.8F, 0.8F + 0.4F * livingEntity.world.rand.nextFloat());
                //Durability needed to block damage - durability we had, is the left-over durability that would have been needed to block it all
                int unblockedDamage = (durabilityNeeded - activeDurability) * absorptionRatio;
                damageBlocked = Math.max(0, damage - unblockedDamage);
            }
        }
        if (livingEntity instanceof EntityPlayerMP mp && damageBlocked > 0 && damageBlocked < 3.4028235E37F) {
            //   mp.awardStat(Stats.DAMAGE_BLOCKED_BY_SHIELD, Math.round(damageBlocked * 10F));
        }
        return damageBlocked;
    }

}
