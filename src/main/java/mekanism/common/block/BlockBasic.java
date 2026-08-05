package mekanism.common.block;

import mekanism.api.Coord4D;
import mekanism.api.IMekWrench;
import mekanism.api.energy.IStrictEnergyStorage;
import mekanism.common.Mekanism;
import mekanism.common.base.IActiveState;
import mekanism.common.base.IBoundingBlock;
import mekanism.common.base.IComparatorSupport;
import mekanism.common.base.ITierItem;
import mekanism.common.block.states.BlockStateBasic;
import mekanism.common.block.states.BlockStateBasic.BasicBlock;
import mekanism.common.block.states.BlockStateBasic.BasicBlockType;
import mekanism.common.block.states.BlockStateFacing;
import mekanism.common.content.boiler.SynchronizedBoilerData;
import mekanism.common.content.tank.DynamicFluidTank;
import mekanism.common.integration.wrenches.Wrenches;
import mekanism.common.inventory.BinMekanismInventory;
import mekanism.common.item.ItemBlockBasic;
import mekanism.common.multiblock.IMultiblock;
import mekanism.common.multiblock.IStructuralMultiblock;
import mekanism.common.tier.BaseTier;
import mekanism.common.tile.TileEntityBin;
import mekanism.common.tile.TileEntitySecurityDesk;
import mekanism.common.tile.TileEntitySuperheatingElement;
import mekanism.common.tile.multiblock.*;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.util.FluidUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismPlacementData;
import mekanism.common.util.SecurityUtils;
import mekanism.common.util.StorageUtils;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving.SpawnPlacementType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

/**
 * Block class for handling multiple metal block IDs. 0:0: Osmium Block 0:1: Bronze Block 0:2: Refined Obsidian 0:3: Charcoal Block 0:4: Refined Glowstone 0:5: Steel
 * Block 0:6: Bin 0:7: Teleporter Frame 0:8: Steel Casing 0:9: Dynamic Tank 0:10: Structural Glass 0:11: Dynamic Valve 0:12: Copper Block 0:13: Tin Block 0:14: Thermal
 * Evaporation Controller 0:15: Thermal Evaporation Valve 1:0: Thermal Evaporation Block 1:1: Induction Casing 1:2: Induction Port 1:3: Induction Cell 1:4: Induction
 * Provider 1:5: Superheating Element 1:6: Pressure Disperser 1:7: Boiler Casing 1:8: Boiler Valve 1:9: Security Desk
 *
 * @author AidanBrady
 */
public abstract class BlockBasic extends BlockTileDrops {

    public BlockBasic() {
        super(Material.IRON);
        setHardness(5F);
        setResistance(10F);
        setCreativeTab(Mekanism.tabMekanism);
    }

    public static BlockBasic getBlockBasic(BasicBlock block) {
        return new BlockBasic() {
            @Override
            public BasicBlock getBasicBlock() {
                return block;
            }
        };
    }

    public static boolean manageInventory(EntityPlayer player, TileEntityDynamicTank tileEntity, EnumHand hand, ItemStack itemStack) {
        if (tileEntity.structure == null) {
            return false;
        }
        DynamicFluidTank fluidTank = tileEntity.structure.inventoryFluidTank;
        return FluidUtils.handleTankInteraction(player, hand, itemStack, fluidTank, true, false);
    }

    public abstract BasicBlock getBasicBlock();

    @Nonnull
    @Override
    public BlockStateContainer createBlockState() {
        return new BlockStateBasic(this, getTypeProperty());
    }

    @Nonnull
    @Override
    @Deprecated
    public IBlockState getStateFromMeta(int meta) {
        BasicBlockType type = BasicBlockType.get(getBasicBlock(), meta & 0xF);
        return getDefaultState().withProperty(getTypeProperty(), type);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(getTypeProperty()).meta;
    }

    @Nonnull
    @Override
    @Deprecated
    public IBlockState getActualState(@Nonnull IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        TileEntity tile = MekanismUtils.getTileEntitySafe(worldIn, pos);
        if (tile instanceof TileEntityBasicBlock basicBlock && basicBlock.facing != null) {
            state = state.withProperty(BlockStateFacing.facingProperty, basicBlock.facing);
        }
        if (tile instanceof IActiveState as) {
            state = state.withProperty(BlockStateBasic.activeProperty, as.getActive());
        }
        if (tile instanceof TileEntityInductionCell cell) {
            state = state.withProperty(BlockStateBasic.tierProperty, cell.tier.getBaseTier());
        }
        if (tile instanceof TileEntityInductionProvider provider) {
            state = state.withProperty(BlockStateBasic.tierProperty, provider.tier.getBaseTier());
        }
        if (tile instanceof TileEntityBin bin) {
            state = state.withProperty(BlockStateBasic.tierProperty, bin.tier.getBaseTier());
        }
        if (tile instanceof TileEntityInductionPort port) {
            state = state.withProperty(BlockStateBasic.activeProperty, port.mode);
        }
        if (tile instanceof TileEntitySuperheatingElement element) {
            state = state.withProperty(BlockStateBasic.activeProperty, element.getActive());
        }
        if (tile instanceof TileEntityDynamicValve Valve) {
            state = state.withProperty(BlockStateBasic.activeProperty, Valve.eject);
        }
        if (tile instanceof TileEntityBoilerValve Valve) {
            state = state.withProperty(BlockStateBasic.boilerValveModeProperty, Valve.getRenderMode());
        }
        return state;
    }

    @Override
    @Deprecated
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block neighborBlock, BlockPos fromPos) {
        if (!world.isRemote) {
            TileEntity tileEntity = new Coord4D(pos, world).getTileEntity(world);
            if (tileEntity instanceof IMultiblock<?> multiblock) {
                multiblock.doUpdate();
            }
            if (tileEntity instanceof TileEntityBasicBlock block) {
                block.onNeighborChange(neighborBlock);
            }
            if (tileEntity instanceof IStructuralMultiblock block) {
                block.doUpdate();
            }
            /*
            Block newBlock = world.getBlockState(fromPos).getBlock();
            if (BasicBlockType.get(state) == BasicBlockType.REFINED_OBSIDIAN && newBlock instanceof BlockFire) {
                PortalHelper.BlockPortalOverride.instance.trySpawnPortal(world, fromPos);
            }


             */
        }
    }

    @Override
    public float getExplosionResistance(World world, BlockPos pos, Entity exploder, Explosion explosion) {
        IBlockState state = world.getBlockState(pos);
        BasicBlockType type = BasicBlockType.get(getBasicBlock(), state.getBlock().getMetaFromState(state));
        float defaultResistance = blockResistance / 5F;
        if (type == null) {
            return defaultResistance;
        }
        return switch (type) {
            case REFINED_OBSIDIAN -> 2400F;
            case OSMIUM_BLOCK -> 12F;
            case STEEL_BLOCK, BRONZE_BLOCK, STEEL_CASING, SECURITY_DESK, THERMAL_EVAPORATION_BLOCK,
                 THERMAL_EVAPORATION_VALVE, THERMAL_EVAPORATION_CONTROLLER -> 9F;
            default -> defaultResistance;
        };
    }

    @Override
    @Deprecated
    public float getBlockHardness(IBlockState blockState, World worldIn, BlockPos pos) {
        BasicBlockType type = BasicBlockType.get(getBasicBlock(), blockState.getBlock().getMetaFromState(blockState));
        if (type == BasicBlockType.REFINED_OBSIDIAN) {
            return 50.0F;
        } else if (type == BasicBlockType.OSMIUM_BLOCK) {
            return 7.5F;
        }
        return blockHardness;
    }

    @Override
    public int damageDropped(IBlockState state) {
        return state.getBlock().getMetaFromState(state);
    }

    @Override
    public void getSubBlocks(CreativeTabs creativetabs, NonNullList<ItemStack> list) {
        for (BasicBlockType type : BasicBlockType.values()) {
            if (type.blockType == getBasicBlock()) {
                switch (type) {
                    case INDUCTION_CELL, INDUCTION_PROVIDER, BIN -> {
                        for (BaseTier tier : BaseTier.values()) {
                            if (type == BasicBlockType.BIN || tier.isObtainable()) {
                                ItemStack stack = new ItemStack(this, 1, type.meta);
                                ((ItemBlockBasic) stack.getItem()).setBaseTier(stack, tier);
                                list.add(stack);
                            }
                        }
                    }
                    default -> list.add(new ItemStack(this, 1, type.meta));
                }
            }
        }
    }

    @Override
    public boolean canCreatureSpawn(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos, SpawnPlacementType type) {
        int meta = state.getBlock().getMetaFromState(state);
        switch (getBasicBlock()) {
            case BASIC_BLOCK_1 -> {
                switch (meta) {
                    case 10:
                        return false;
                    case 9:
                    case 11:
                        TileEntityDynamicTank tileEntity = (TileEntityDynamicTank) MekanismUtils.getTileEntitySafe(world, pos);
                        if (tileEntity != null) {
                            if (FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER) {
                                if (tileEntity.structure != null) {
                                    return false;
                                }
                            } else if (tileEntity.clientHasStructure) {
                                return false;
                            }
                        }
                    default:
                        return super.canCreatureSpawn(state, world, pos, type);
                }
            }
            case BASIC_BLOCK_2 -> {
                switch (meta) {
                    case 1:
                    case 2:
                    case 7:
                    case 8:
                        TileEntityMultiblock<?> tileEntity = (TileEntityMultiblock<?>) MekanismUtils.getTileEntitySafe(world, pos);
                        if (tileEntity != null) {
                            if (FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER) {
                                if (tileEntity.structure != null) {
                                    return false;
                                }
                            } else if (tileEntity.clientHasStructure) {
                                return false;
                            }
                        }
                    case 3:
                    case 4:
                        //Don't allow mobs to spawn on induction cells or providers
                        return false;
                    default:
                        return super.canCreatureSpawn(state, world, pos, type);
                }
            }
            default -> {
                return super.canCreatureSpawn(state, world, pos, type);
            }
        }
    }

    @Override
    public void onBlockClicked(World world, BlockPos pos, EntityPlayer player) {
        BasicBlockType type = BasicBlockType.get(world.getBlockState(pos));
        if (!world.isRemote && type == BasicBlockType.BIN) {
            TileEntityBin bin = (TileEntityBin) world.getTileEntity(pos);
            RayTraceResult mop = MekanismUtils.rayTrace(world, player);

            if (mop != null && mop.sideHit == bin.facing) {
                if (!bin.bottomStack.isEmpty()) {
                    ItemStack stack;
                    if (player.isSneaking()) {
                        stack = bin.remove(1).copy();
                    } else {
                        stack = bin.removeStack().copy();
                    }
                    if (!player.inventory.addItemStackToInventory(stack)) {
                        BlockPos dropPos = pos.offset(bin.facing);
                        Entity item = new EntityItem(world, dropPos.getX() + .5f, dropPos.getY() + .3f, dropPos.getZ() + .5f, stack);
                        item.addVelocity(-item.motionX, -item.motionY, -item.motionZ);
                        world.spawnEntity(item);
                    } else {
                        world.playSound(null, pos.getX() + .5f, pos.getY() + .5f, pos.getZ() + .5f, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS,
                                0.2F, ((world.rand.nextFloat() - world.rand.nextFloat()) * 0.7F + 1.0F) * 2.0F);
                    }
                }
            }
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer entityplayer, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(pos);
        ItemStack stack = entityplayer.getHeldItem(hand);

        if (tile instanceof TileEntityThermalEvaporationController controller) {
            if (!entityplayer.isSneaking() && controller.structured) {
                if (!world.isRemote) {
                    entityplayer.openGui(Mekanism.instance, 33, world, pos.getX(), pos.getY(), pos.getZ());
                }
                return true;
            }
        } else if (tile instanceof TileEntitySecurityDesk desk) {
            UUID ownerUUID = desk.ownerUUID;
            if (!entityplayer.isSneaking()) {
                if (!world.isRemote) {
                    if (ownerUUID == null || entityplayer.getUniqueID().equals(ownerUUID)) {
                        entityplayer.openGui(Mekanism.instance, 57, world, pos.getX(), pos.getY(), pos.getZ());
                    } else {
                        SecurityUtils.displayNoAccess(entityplayer);
                    }
                }
                return true;
            }
        } else if (tile instanceof TileEntityBin bin) {
            IMekWrench wrenchHandler;
            if (!stack.isEmpty() && (wrenchHandler = Wrenches.getHandler(stack)) != null) {
                RayTraceResult raytrace = new RayTraceResult(new Vec3d(hitX, hitY, hitZ), side, pos);
                if (wrenchHandler.canUseWrench(entityplayer, hand, stack, raytrace)) {
                    if (!world.isRemote) {
                        wrenchHandler.wrenchUsed(entityplayer, hand, stack, raytrace);
                        if (entityplayer.isSneaking()) {
                            MekanismUtils.dismantleBlock(this, state, world, pos);
                            return true;
                        }
                        bin.setFacing(bin.facing.rotateY());
                        world.notifyNeighborsOfStateChange(pos, this, true);
                    }
                    return true;
                }
            }
            if (!world.isRemote) {
                if (bin.getItemCount() < bin.tier.getStorage()) {
                    if (bin.addTicks == 0) {
                        if (!stack.isEmpty()) {
                            ItemStack remain = bin.add(stack);
                            entityplayer.setHeldItem(hand, remain);
                            bin.addTicks = 5;
                        }
                    } else if (bin.addTicks > 0 && bin.getItemCount() > 0) {
                        NonNullList<ItemStack> inv = entityplayer.inventory.mainInventory;

                        for (int i = 0; i < inv.size(); i++) {
                            if (bin.getItemCount() == bin.tier.getStorage()) {
                                break;
                            }
                            if (!inv.get(i).isEmpty()) {
                                ItemStack remain = bin.add(inv.get(i));
                                inv.set(i, remain);
                                bin.addTicks = 5;
                            }
                            ((EntityPlayerMP) entityplayer).sendContainerToPlayer(entityplayer.openContainer);
                        }
                    }
                }
            }
            return true;
        } else if (tile instanceof IMultiblock<?> multiblock) {
            if (world.isRemote) {
                return true;
            }
            return multiblock.onActivate(entityplayer, hand, stack);
        } else if (tile instanceof IStructuralMultiblock multiblock) {
            if (world.isRemote) {
                return true;
            }

            return multiblock.onActivate(entityplayer, hand, stack);
        }
        return false;
    }

    @Override
    @Deprecated
    public boolean isSideSolid(IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos, EnumFacing side) {
        return BasicBlockType.get(state) != BasicBlockType.STRUCTURAL_GLASS;
    }

    @SideOnly(Side.CLIENT)
    @Nonnull
    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    @Deprecated
    public boolean isOpaqueCube(IBlockState state) {
        BasicBlockType type = BasicBlockType.get(state);
        return type != null && type.isOpaqueCube;
    }

    @Override
    @Deprecated
    public boolean isFullCube(IBlockState state) {
        BasicBlockType type = BasicBlockType.get(state);
        return type != null && type.isFullBlock;
    }

    @Override
    @Deprecated
    public boolean isFullBlock(IBlockState state) {
        BasicBlockType type = BasicBlockType.get(state);
        return type != null && type.isFullBlock;
    }

    @Override
    public int getLightOpacity(IBlockState state, IBlockAccess world, BlockPos pos) {
        BasicBlockType type = BasicBlockType.get(state);
        return type != null && type.isOpaqueCube ? 255 : 0;
    }

    @Nonnull
    @Override
    @Deprecated
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tileEntity = MekanismUtils.getTileEntitySafe(world, pos);
        int metadata = state.getBlock().getMetaFromState(state);
        if (tileEntity instanceof IActiveState activeState) {
            if (activeState.getActive() && activeState.lightUpdate()) {
                return 15;
            }
        }
        if (getBasicBlock() == BasicBlock.BASIC_BLOCK_1) {
            switch (metadata) {
                case 2 -> {
                    return 8;
                }
                case 4 -> {
                    return 15;
                }
                case 7 -> {
                    return 12;
                }
            }
        } else if (getBasicBlock() == BasicBlock.BASIC_BLOCK_2) {
            if (metadata == 5 && tileEntity instanceof TileEntitySuperheatingElement element) {
                return element.getActive() ? 15 : 0;
            }
        }

        return 0;
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        BasicBlockType type = BasicBlockType.get(state);
        return type != null && type.tileEntitySupplier != null;
    }

    @Override
    public TileEntity createTileEntity(@Nonnull World world, @Nonnull IBlockState state) {
        if (BasicBlockType.get(state) == null) {
            return null;
        }
        return Objects.requireNonNull(BasicBlockType.get(state)).create();
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityBasicBlock tileEntity) {
            EnumFacing change = EnumFacing.SOUTH;
            if (placer != null) {
                if (tileEntity.canSetFacing(EnumFacing.DOWN) && tileEntity.canSetFacing(EnumFacing.UP)) {
                    int height = Math.round(placer.rotationPitch);
                    if (height >= 65) {
                        change = EnumFacing.UP;
                    } else if (height <= -65) {
                        change = EnumFacing.DOWN;
                    }
                }
                if (change != EnumFacing.DOWN && change != EnumFacing.UP) {
                    int side = MathHelper.floor((placer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
                    change = switch (side) {
                        case 0 -> EnumFacing.NORTH;
                        case 1 -> EnumFacing.EAST;
                        case 2 -> EnumFacing.SOUTH;
                        case 3 -> EnumFacing.WEST;
                        default -> change;
                    };
                }
            }
            tileEntity.setFacing(change);
            tileEntity.redstone = world.getRedstonePowerFromNeighbors(pos) > 0;

            if (placer != null && tileEntity instanceof TileEntitySecurityDesk desk) {
                desk.ownerUUID = placer.getUniqueID();
            }
            if (tileEntity instanceof IBoundingBlock block) {
                IBoundingBlock.PlacementResult result = block.tryPlaceBoundingBlocks(world, Coord4D.get(tileEntity));
                if (result == IBoundingBlock.PlacementResult.LEGACY) {
                    block.onPlace();
                } else if (result == IBoundingBlock.PlacementResult.BLOCKED) {
                    world.setBlockToAir(pos);
                    return;
                }
            }
        }

        MekanismPlacementData.apply(world, pos, placer, stack);

        world.markBlockRangeForRenderUpdate(pos, pos.add(1, 1, 1));
        world.checkLightFor(EnumSkyBlock.BLOCK, pos);
        world.checkLightFor(EnumSkyBlock.SKY, pos);

        if (!world.isRemote && te != null) {
            if (te instanceof IMultiblock<?> multiblock) {
                multiblock.doUpdate();
            }
            if (te instanceof IStructuralMultiblock multiblock) {
                multiblock.doUpdate();
            }
        }
    }

    @Override
    public void breakBlock(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull IBlockState state) {
        TileEntity tileEntity = world.getTileEntity(pos);
        if (tileEntity instanceof IBoundingBlock block) {
            if (!block.removeBoundingBlocks(world, pos)) {
                block.onBreak();
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Nonnull
    @Override
    protected ItemStack getDropItem(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos) {
        BasicBlockType type = BasicBlockType.get(state);
        ItemStack ret = new ItemStack(this, 1, state.getBlock().getMetaFromState(state));

        if (type == BasicBlockType.BIN) {
            TileEntityBin tileEntity = (TileEntityBin) world.getTileEntity(pos);
            ((ITierItem) ret.getItem()).setBaseTier(ret, tileEntity.tier.getBaseTier());
            BinMekanismInventory inventory = BinMekanismInventory.create(ret);
            if (inventory != null) {
                inventory.setItemCount(tileEntity.getItemCount());
                if (tileEntity.getItemCount() > 0) {
                    inventory.setItemType(tileEntity.itemType);
                }
                inventory.setLockStack(tileEntity.getLockStack());
            }
        } else if (type == BasicBlockType.INDUCTION_CELL) {
            TileEntityInductionCell tileEntity = (TileEntityInductionCell) world.getTileEntity(pos);
            ((ItemBlockBasic) ret.getItem()).setBaseTier(ret, tileEntity.tier.getBaseTier());
        } else if (type == BasicBlockType.INDUCTION_PROVIDER) {
            TileEntityInductionProvider tileEntity = (TileEntityInductionProvider) world.getTileEntity(pos);
            ((ItemBlockBasic) ret.getItem()).setBaseTier(ret, tileEntity.tier.getBaseTier());
        }

        TileEntity tileEntity = world.getTileEntity(pos);
        if (tileEntity instanceof IStrictEnergyStorage energyStorage) {
            StorageUtils.setStoredEnergy(ret, energyStorage.getEnergy(), energyStorage.getMaxEnergy());
        }
        return ret;
    }

    @Override
    @Deprecated
    @SideOnly(Side.CLIENT)
    public boolean shouldSideBeRendered(IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos, EnumFacing side) {
        if (BasicBlockType.get(state) == BasicBlockType.STRUCTURAL_GLASS && BasicBlockType.get(world.getBlockState(pos.offset(side))) == BasicBlockType.STRUCTURAL_GLASS) {
            return false;
        }
        return super.shouldSideBeRendered(state, world, pos, side);
    }

    @Override
    public EnumFacing[] getValidRotations(World world, @Nonnull BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        EnumFacing[] valid = new EnumFacing[6];
        if (tile instanceof TileEntityBasicBlock basicTile) {
            for (EnumFacing dir : EnumFacing.VALUES) {
                if (basicTile.canSetFacing(dir)) {
                    valid[dir.ordinal()] = dir;
                }
            }
        }
        return valid;
    }

    @Override
    public boolean rotateBlock(World world, @Nonnull BlockPos pos, @Nonnull EnumFacing axis) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileEntityBasicBlock basicTile) {
            if (basicTile.canSetFacing(axis)) {
                basicTile.setFacing(axis);
                return true;
            }
        }
        return false;
    }

    public PropertyEnum<BasicBlockType> getTypeProperty() {
        return getBasicBlock().getProperty();
    }

    @Override
    public boolean isBeaconBase(IBlockAccess world, BlockPos pos, BlockPos beacon) {
        BasicBlockType basicBlockType = BasicBlockType.get(world.getBlockState(pos));
        return basicBlockType != null && basicBlockType.isBeaconBase;
    }

    @Override
    public boolean hasComparatorInputOverride(IBlockState blockState) {
        BasicBlockType basicBlockType = BasicBlockType.get(blockState);
        return basicBlockType != null && basicBlockType.hasRedstoneOutput;
    }

    @Override
    public int getComparatorInputOverride(IBlockState blockState, World worldIn, BlockPos pos) {
        BasicBlockType basicBlockType = BasicBlockType.get(blockState);
        if (basicBlockType != null && basicBlockType.hasRedstoneOutput) {
            TileEntity tile = worldIn.getTileEntity(pos);
            if (tile instanceof IComparatorSupport support) {
                return support.getRedstoneLevel();
            }
        }
        return 0;
    }


}
