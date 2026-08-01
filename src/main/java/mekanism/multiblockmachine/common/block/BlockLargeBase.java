package mekanism.multiblockmachine.common.block;

import mekanism.api.Coord4D;
import mekanism.api.IMekWrench;
import mekanism.api.energy.IStrictEnergyStorage;
import mekanism.common.base.*;
import mekanism.common.block.BlockMekanismContainer;
import mekanism.common.block.states.BlockStateFacing;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.wrenches.Wrenches;
import mekanism.common.security.ISecurityItem;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismPlacementData;
import mekanism.common.util.SecurityUtils;
import mekanism.common.util.StorageUtils;
import mekanism.multiblockmachine.common.MekanismMultiblockMachine;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

public abstract class BlockLargeBase extends BlockMekanismContainer {


    protected BlockLargeBase() {
        super(Material.IRON);
        setHardness(3.5F);
        setResistance(16F);
        setCreativeTab(MekanismMultiblockMachine.tabMekanismMultiblockMachine);
    }

    @Nonnull
    @Override
    public BlockStateContainer createBlockState() {
        return new BlockStateFacing(this);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return 0;
    }


    @Nonnull
    @Override
    @Deprecated
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState();
    }

    @Nonnull
    @Override
    @Deprecated
    public IBlockState getActualState(@Nonnull IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        TileEntity tile = MekanismUtils.getTileEntitySafe(worldIn, pos);
        if (tile instanceof TileEntityBasicBlock block) {
            if (block.facing != null) {
                state = state.withProperty(BlockStateFacing.facingProperty, block.facing);
            }
        }
        return state;
    }

    @Override
    @Deprecated
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block neighborBlock, BlockPos neighborPos) {
        if (!world.isRemote) {
            TileEntity tileEntity = world.getTileEntity(pos);
            if (tileEntity instanceof TileEntityBasicBlock block) {
                block.onNeighborChange(neighborBlock);
            }
        }
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (world.getTileEntity(pos) instanceof TileEntityBasicBlock tileEntity) {
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
                    int side = MathHelper.floor((double) (placer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
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
            if (tileEntity instanceof IBoundingBlock block) {
                IBoundingBlock.PlacementResult result = block.tryPlaceBoundingBlocks(world, Coord4D.get(tileEntity));
                if (result == IBoundingBlock.PlacementResult.LEGACY) {
                    block.onPlace();
                } else if (result == IBoundingBlock.PlacementResult.BLOCKED) {
                    world.setBlockToAir(pos);
                    return;
                }
            }
            MekanismPlacementData.apply(world, pos, placer, stack);
        }
    }

    @Override
    public void breakBlock(World world, @Nonnull BlockPos pos, @Nonnull IBlockState state) {
        if (world.getTileEntity(pos) instanceof TileEntityBasicBlock tileEntity) {
            if (tileEntity instanceof IBoundingBlock block) {
                if (!block.removeBoundingBlocks(world, pos)) {
                    block.onBreak();
                }
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (MekanismConfig.current().client.enableAmbientLighting.val()) {
            TileEntity tileEntity = MekanismUtils.getTileEntitySafe(world, pos);
            if (tileEntity instanceof IActiveState activeState && activeState.lightUpdate() && activeState.wasActiveRecently()) {
                return MekanismConfig.current().client.ambientLightingLevel.val();
            }
        }
        return 0;
    }

    @Override
    public int damageDropped(IBlockState state) {
        return state.getBlock().getMetaFromState(state);
    }

    @Override
    public void getSubBlocks(CreativeTabs creativetabs, NonNullList<ItemStack> list) {
        ItemStack addItem = new ItemStack(getBlock());
        list.add(addItem);
    }

    abstract Block getBlock();

    @Override
    @Deprecated
    public float getPlayerRelativeBlockHardness(IBlockState state, @Nonnull EntityPlayer player, @Nonnull World world, @Nonnull BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return SecurityUtils.canAccess(player, tile) ? super.getPlayerRelativeBlockHardness(state, player, world, pos) : 0.0F;
    }

    public boolean canRotate() {
        return true;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer entityplayer, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        if (world.getTileEntity(pos) instanceof TileEntityBasicBlock tileEntity) {
            ItemStack stack = entityplayer.getHeldItem(hand);
            if (!stack.isEmpty()) {
                IMekWrench wrenchHandler = Wrenches.getHandler(stack);
                if (wrenchHandler != null) {
                    RayTraceResult raytrace = new RayTraceResult(new Vec3d(hitX, hitY, hitZ), side, pos);
                    if (wrenchHandler.canUseWrench(entityplayer, hand, stack, raytrace)) {
                        if (SecurityUtils.canAccess(entityplayer, tileEntity)) {
                            wrenchHandler.wrenchUsed(entityplayer, hand, stack, raytrace);
                            if (entityplayer.isSneaking()) {
                                MekanismUtils.dismantleBlock(this, state, world, pos);
                                return true;
                            }
                            if (canRotate()) {
                                tileEntity.setFacing(tileEntity.facing.rotateY());
                                world.notifyNeighborsOfStateChange(pos, this, true);
                            }
                        } else {
                            SecurityUtils.displayNoAccess(entityplayer);
                        }
                        return true;
                    }
                }
            }
            if (!entityplayer.isSneaking() && getGuiID() >= 0) {
                if (SecurityUtils.canAccess(entityplayer, tileEntity)) {
                    entityplayer.openGui(MekanismMultiblockMachine.instance, getGuiID(), world, pos.getX(), pos.getY(), pos.getZ());
                } else {
                    SecurityUtils.displayNoAccess(entityplayer);
                }
                return true;
            }
        }
        return false;
    }

    abstract int getGuiID();

    @Override
    @Deprecated
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    @Deprecated
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Nonnull
    @Override
    @Deprecated
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }


    @SideOnly(Side.CLIENT)
    @Nonnull
    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    abstract Block getMachineBlock();

    @Nonnull
    @Override
    protected ItemStack getDropItem(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos) {
        ItemStack itemStack = new ItemStack(getMachineBlock(), 1, state.getBlock().getMetaFromState(state));
        if (itemStack.getTagCompound() == null) {
            itemStack.setTagCompound(new NBTTagCompound());
        }
        if (world.getTileEntity(pos) instanceof TileEntityBasicBlock tileEntity) {
            if (tileEntity instanceof ISecurityTile securityTile) {
                if (itemStack.getItem() instanceof ISecurityItem securityItem) {
                    if (securityItem.hasSecurity(itemStack)) {
                        securityItem.setOwnerUUID(itemStack, securityTile.getSecurity().getOwnerUUID());
                        securityItem.setSecurity(itemStack, securityTile.getSecurity().getMode());
                    }
                }
            }
            if (tileEntity instanceof IUpgradeTile upgradeTile) {
                upgradeTile.writeUpgrades(ItemDataUtils.getDataMap(itemStack));
            }
            if (tileEntity instanceof ISustainedData data) {
                data.writeSustainedData(itemStack);
            }
            if (tileEntity instanceof IRedstoneControl control) {
                ItemDataUtils.setInt(itemStack, "controlType", control.getControlType().ordinal());
            }
            if (tileEntity instanceof TileEntityContainerBlock containerBlock && !containerBlock.isEmpty()) {
                if (itemStack.getItem() instanceof ISustainedInventory inventory) {
                    inventory.setInventory(containerBlock.getInventory(), itemStack);
                }
            }
            if (tileEntity instanceof ISustainedTank tank) {
                if (itemStack.getItem() instanceof ISustainedTank itemTank && itemTank.hasTank(itemStack)) {
                    if (tank.getFluidStack() != null) {
                        itemTank.setFluidStack(tank.getFluidStack(), itemStack);
                    }
                }
            }
            if (tileEntity instanceof IStrictEnergyStorage storage) {
                StorageUtils.setStoredEnergy(itemStack, storage.getEnergy(), storage.getMaxEnergy());
            }
        }
        return itemStack;
    }

    @Override
    @Deprecated
    public boolean hasComparatorInputOverride(IBlockState state) {
        return true;
    }

    @Override
    @Deprecated
    public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        TileEntity tileEntity = world.getTileEntity(pos);
        if (tileEntity instanceof IComparatorSupport comparatorSupport) {
            return comparatorSupport.getRedstoneLevel();
        }
        return 0;
    }

    @Override
    @Deprecated
    public boolean isSideSolid(IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos, EnumFacing side) {
        return false;
    }

    @Override
    public EnumFacing[] getValidRotations(World world, @Nonnull BlockPos pos) {
        EnumFacing[] valid = new EnumFacing[6];
        if (world.getTileEntity(pos) instanceof TileEntityBasicBlock basicTile) {
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
        if (world.getTileEntity(pos) instanceof TileEntityBasicBlock basicTile) {
            if (basicTile.canSetFacing(axis)) {
                basicTile.setFacing(axis);
                return true;
            }
        }
        return false;
    }

    @Override
    public TileEntity createNewTileEntity(@Nonnull World world, int meta) {
        return getTileEntity();
    }

    abstract TileEntity getTileEntity();
}
