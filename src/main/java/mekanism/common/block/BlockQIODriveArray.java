package mekanism.common.block;

import mekanism.common.Mekanism;
import mekanism.common.MekanismBlocks;
import mekanism.common.QIOGuiConstants;
import mekanism.common.block.property.PropertyDriveStatus;
import mekanism.common.block.states.BlockStateFacing;
import mekanism.common.block.states.BlockStateQIODriveArray;
import mekanism.common.block.states.BlockStateQIOComponent;
import mekanism.common.security.ISecurityTile;
import mekanism.common.item.ItemBlockQIOComponent;
import mekanism.common.tile.qio.TileEntityQIODriveArray;
import mekanism.common.tile.qio.TileEntityQIOComponent;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismPlacementData;
import mekanism.common.util.SecurityUtils;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

/** Block container for the QIO drive array. */
public class BlockQIODriveArray extends BlockMekanismContainer {

    public BlockQIODriveArray() {
        super(Material.IRON);
        setHardness(3.5F);
        setResistance(8F);
        setCreativeTab(Mekanism.tabMekanism);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateQIODriveArray(this);
    }

    @SideOnly(Side.CLIENT)
    @Nonnull
    @Override
    public IBlockState getExtendedState(@Nonnull IBlockState state, IBlockAccess world, BlockPos pos) {
        if (state instanceof IExtendedBlockState extended) {
            TileEntity tile = world == null ? null : world.getTileEntity(pos);
            long status = tile instanceof TileEntityQIODriveArray ? ((TileEntityQIODriveArray) tile).getDriveStatusData() : 0;
            return extended.withProperty(PropertyDriveStatus.INSTANCE, status);
        }
        return state;
    }

    @Nonnull
    @Override
    @Deprecated
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(BlockStateFacing.facingProperty, EnumFacing.NORTH);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return 0;
    }

    @Nonnull
    @Override
    @Deprecated
    public IBlockState getActualState(@Nonnull IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = MekanismUtils.getTileEntitySafe(world, pos);
        if (tile instanceof TileEntityBasicBlock && ((TileEntityBasicBlock) tile).facing != null) {
            state = state.withProperty(BlockStateFacing.facingProperty, ((TileEntityBasicBlock) tile).facing);
        }
        return tile instanceof TileEntityQIOComponent
              ? state.withProperty(BlockStateQIOComponent.activeProperty, ((TileEntityQIOComponent) tile).isActive()) : state;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileEntityBasicBlock) {
            int side = MathHelper.floor((double) (placer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
            EnumFacing facing = side == 0 ? EnumFacing.NORTH : side == 1 ? EnumFacing.EAST : side == 2 ? EnumFacing.SOUTH : EnumFacing.WEST;
            ((TileEntityBasicBlock) tile).setFacing(facing);
        }
        if (tile instanceof ISecurityTile && placer instanceof EntityPlayer &&
              ((ISecurityTile) tile).getSecurity().getOwnerUUID() == null) {
            ((ISecurityTile) tile).getSecurity().setOwnerUUID(((EntityPlayer) placer).getUniqueID());
        }
        MekanismPlacementData.apply(world, pos, placer, stack);
    }

    @Override
    @Deprecated
    public void neighborChanged(IBlockState state, World world, BlockPos pos, net.minecraft.block.Block neighborBlock, BlockPos neighborPos) {
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileEntityBasicBlock) {
                ((TileEntityBasicBlock) tile).onNeighborChange(neighborBlock);
            }
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
          EnumFacing side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof ISecurityTile && !SecurityUtils.canAccess(player, tile)) {
            SecurityUtils.displayNoAccess(player);
            return true;
        }
        player.openGui(Mekanism.instance, QIOGuiConstants.DRIVE_ARRAY, world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    @Override
    public TileEntity createNewTileEntity(@Nonnull World world, int meta) {
        return new TileEntityQIODriveArray();
    }

    @Nonnull
    @Override
    protected ItemStack getDropItem(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos) {
        ItemStack stack = new ItemStack(MekanismBlocks.QIO_DRIVE_ARRAY);
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileEntityQIODriveArray && stack.getItem() instanceof ItemBlockQIOComponent) {
            TileEntityQIODriveArray array = (TileEntityQIODriveArray) tile;
            ItemBlockQIOComponent item = (ItemBlockQIOComponent) stack.getItem();
            item.setOwnerUUID(stack, array.getSecurity().getOwnerUUID());
            item.setSecurity(stack, array.getSecurity().getMode());
            if (array.getQIOFrequency() != null) {
                item.setFrequency(stack, array.getQIOFrequency().getIdentity());
            }
            item.setSustainedInventory(array.getInventory(), stack);
        }
        return stack;
    }

    @Nonnull
    @Override
    @Deprecated
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    @Deprecated
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    @Deprecated
    public boolean isFullCube(IBlockState state) {
        return true;
    }

    @Override
    public boolean rotateBlock(World world, BlockPos pos, EnumFacing axis) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileEntityBasicBlock && axis.getAxis().isHorizontal()) {
            ((TileEntityBasicBlock) tile).setFacing(axis);
            return true;
        }
        return false;
    }
}
