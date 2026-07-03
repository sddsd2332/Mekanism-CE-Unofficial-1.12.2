package mekanism.common.content.gear.mekatool;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.gear.ICustomModule;
import mekanism.api.gear.IModule;
import mekanism.common.config.MekanismConfig;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.IShearable;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;


//Note: Some parts of this module are directly implemented in the meka tool most notably the handling of disarming tripwire hooks,
// and also exposing the shears tool type on the meka tool when shears are installed
@ParametersAreNonnullByDefault
public class ModuleShearingUnit implements ICustomModule<ModuleShearingUnit> {


    /*
    @Nonnull
    @Override
    public EnumActionResult onItemUse(IModule<ModuleShearingUnit> module, EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        IEnergyContainer energyContainer = module.getEnergyContainer();
        if (energyContainer == null || energyContainer.getEnergy(module.getContainer()) < (MekanismConfig.current().meka.mekaToolEnergyUsageShearBlock.val())) {
            return EnumActionResult.PASS;
        }
        IBlockState state = world.getBlockState(pos);
        return MekanismUtils.performActions(carvePumpkin(energyContainer, player,world,pos,hand,side,hitX,hitY,hitZ, state), () -> shearBeehive(energyContainer, context, state));
    }

     */

    @Override
    public EnumActionResult onInteract(IModule<ModuleShearingUnit> module, EntityPlayer player, EntityLivingBase entity, EnumHand hand) {
        if (entity instanceof IShearable) {
            IEnergyContainer energyContainer = module.getEnergyContainer();
            if (energyContainer != null && energyContainer.getEnergy() >= MekanismConfig.current().meka.mekaToolEnergyUsageShearEntity.val() && shearEntity(energyContainer, entity, module.getContainer(), entity.world, entity.getPosition())){
                return EnumActionResult.SUCCESS;
            }
        }
        return EnumActionResult.PASS;
    }

    @Nonnull
    @Override
    public ModuleDispenseResult onDispense(IModule<ModuleShearingUnit> module, IBlockSource source) {
        IEnergyContainer energyContainer = module.getEnergyContainer();
        if (energyContainer != null) {
            World world = source.getWorld();
            EnumFacing facing = source.getBlockState().getValue(PropertyDirection.create("facing", Arrays.asList(new EnumFacing[]{EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.UP, EnumFacing.DOWN})));
            BlockPos pos = source.getBlockPos().offset(facing);
            if (tryShearLivingEntity(energyContainer, world, pos, module.getContainer())) {
                return ModuleDispenseResult.HANDLED;
            }
        }
        return ModuleDispenseResult.FAIL_PREVENT_DROP;
    }

    /*
    private EnumActionResult carvePumpkin(IEnergyContainer energyContainer, EntityPlayer player, World worldS, BlockPos blockPos, EnumHand hand, EnumFacing EnumSide, float hitX, float hitY, float hitZ, IBlockState state) {
        if (state.getBlock() == Blocks.PUMPKIN) {
            World world = worldS;
            //Carve pumpkin - copy from Pumpkin Block's onBlockActivated
            if (!world.isRemote) {
                BlockPos pos = blockPos;
                EnumFacing direction = EnumSide;
                EnumFacing getHorizontalDirection = player == null ? EnumFacing.NORTH : player.getHorizontalFacing();
                EnumFacing side = direction.getAxis() == EnumFacing.Axis.Y ? getHorizontalDirection : direction;
                world.playSound(null, pos, SoundEvents.PUMPKIN_CARVE, SoundCategory.BLOCKS, 1, 1);
                world.setBlock(pos, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, side), BlockFlags.DEFAULT_AND_RERENDER);
                ItemEntity itementity = new ItemEntity(world, pos.getX() + 0.5 + side.getStepX() * 0.65, pos.getY() + 0.1,
                        pos.getZ() + 0.5 + side.getStepZ() * 0.65, new ItemStack(Items.PUMPKIN_SEEDS, 4));
                itementity.setDeltaMovement(0.05 * side.getStepX() + world.random.nextDouble() * 0.02, 0.05,
                        0.05 * side.getStepZ() + world.random.nextDouble() * 0.02D);
                world.addFreshEntity(itementity);
                energyContainer.extract(MekanismConfig.gear.mekaToolEnergyUsageShearBlock.get(), Action.EXECUTE, AutomationType.MANUAL);
            }
            return ActionResultType.sidedSuccess(world.isClientSide);
        }
        return ActionResultType.PASS;
    }

    private EnumActionResult shearBeehive(IEnergyContainer energyContainer, ItemUseContext context, BlockState state) {
        PlayerEntity player = context.getPlayer();
        if (player == null) {
            return ActionResultType.PASS;
        }
        if (state.is(BlockTags.BEEHIVES) && state.getBlock() instanceof BeehiveBlock && state.getValue(BeehiveBlock.HONEY_LEVEL) >= 5) {
            //Act as shears on beehives
            World world = context.getLevel();
            BlockPos pos = context.getClickedPos();
            world.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BEEHIVE_SHEAR, SoundCategory.NEUTRAL, 1, 1);
            BeehiveBlock.dropHoneycomb(world, pos);
            BeehiveBlock beehive = (BeehiveBlock) state.getBlock();
            if (CampfireBlock.isSmokeyPos(world, pos)) {
                beehive.resetHoneyLevel(world, state, pos);
            } else {
                if (beehive.hiveContainsBees(world, pos)) {
                    beehive.angerNearbyBees(world, pos);
                }
                beehive.releaseBeesAndResetHoneyLevel(world, state, pos, player, BeehiveTileEntity.State.EMERGENCY);
            }
            if (!world.isClientSide) {
                energyContainer.extract(MekanismConfig.gear.mekaToolEnergyUsageShearBlock.get(), Action.EXECUTE, AutomationType.MANUAL);
            }
            return EnumActionResult.sidedSuccess(world.isClientSide);
        }
        return EnumActionResult.PASS;
    }
    */

    //Slightly modified copy of BeehiveDispenseBehavior#tryShearBeehive modified to not crash if the tag has a block that isn't a
    // beehive block instance in it, and also to support shearing pumpkins via the dispenser
    /*
    private boolean tryShearBlock(IEnergyContainer energyContainer, World world, BlockPos pos, EnumFacing sideClicked) {
        if (energyContainer.getEnergy().greaterOrEqual(MekanismConfig.gear.mekaToolEnergyUsageShearBlock.get())) {
            BlockState state = world.getBlockState(pos);
            if (state.is(BlockTags.BEEHIVES) && state.getBlock() instanceof BeehiveBlock && state.getValue(BeehiveBlock.HONEY_LEVEL) >= 5) {
                world.playSound(null, pos, SoundEvents.BEEHIVE_SHEAR, SoundCategory.BLOCKS, 1.0F, 1.0F);
                BeehiveBlock.dropHoneycomb(world, pos);
                ((BeehiveBlock) state.getBlock()).releaseBeesAndResetHoneyLevel(world, state, pos, null, BeehiveTileEntity.State.BEE_RELEASED);
                energyContainer.extract(MekanismConfig.gear.mekaToolEnergyUsageShearBlock.get(), Action.EXECUTE, AutomationType.MANUAL);
                return true;
            } else if (state.is(Blocks.PUMPKIN)) {
                //Carve pumpkin - copy from Pumpkin Block's onBlockActivated
                Direction side = sideClicked.getAxis() == Direction.Axis.Y ? Direction.NORTH : sideClicked;
                world.playSound(null, pos, SoundEvents.PUMPKIN_CARVE, SoundCategory.BLOCKS, 1, 1);
                world.setBlock(pos, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, side), BlockFlags.DEFAULT_AND_RERENDER);
                Block.popResource(world, pos, new ItemStack(Items.PUMPKIN_SEEDS, 4));
                energyContainer.extract(MekanismConfig.gear.mekaToolEnergyUsageShearBlock.get(), Action.EXECUTE, AutomationType.MANUAL);
                return true;
            }
        }
        return false;
    }

     */

    //Modified copy of BeehiveDispenseBehavior#tryShearLivingEntity to work with IShearable
    private boolean tryShearLivingEntity(IEnergyContainer energyContainer, World world, BlockPos pos, ItemStack stack) {
        if (energyContainer.getEnergy() > MekanismConfig.current().meka.mekaToolEnergyUsageShearEntity.val()) {
            for (EntityLivingBase entity : world.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(pos), entity -> (entity instanceof EntityPlayer player && !player.isSpectator()) && entity instanceof IShearable)) {
                if (shearEntity(energyContainer, entity, stack, world, pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean shearEntity(IEnergyContainer energyContainer, EntityLivingBase entity, ItemStack stack, World world, BlockPos pos) {
        IShearable target = (IShearable) entity;
        if (target.isShearable(stack, world, pos)) {
            if (!world.isRemote) {
                for (ItemStack drop : target.onSheared(stack, world, pos, EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE, stack))) {
                    EntityItem ent = entity.entityDropItem(drop, 1.0F);
                    if (ent != null) {
                        ent.motionX += (world.rand.nextFloat() - world.rand.nextFloat()) * 0.1F;
                        ent.motionY += world.rand.nextFloat() * 0.05F;
                        ent.motionZ += (world.rand.nextFloat() - world.rand.nextFloat()) * 0.1F;
                    }
                }
                energyContainer.extract(MekanismConfig.current().meka.mekaToolEnergyUsageShearEntity.val(), Action.EXECUTE, AutomationType.MANUAL);
            }
            return true;
        }
        return false;
    }
}
