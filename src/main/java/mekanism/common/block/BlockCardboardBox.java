package mekanism.common.block;

import mekanism.common.Mekanism;
import mekanism.common.MekanismBlocks;
import mekanism.common.block.states.BlockStateCardboardBox;
import mekanism.common.item.ItemBlockCardboardBox;
import mekanism.common.tile.TileEntityCardboardBox;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nonnull;

import static mekanism.common.block.states.BlockStateCardboardBox.storageProperty;

public class BlockCardboardBox extends BlockMekanismContainer {

    private static boolean testingPlace = false;

    public BlockCardboardBox() {
        super(Material.CLOTH);
        setCreativeTab(Mekanism.tabMekanism);
        setHardness(0.5F);
        setResistance(1F);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Nonnull
    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateCardboardBox(this);
    }

    @Nonnull
    @Override
    @Deprecated
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(storageProperty, meta == 1);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(storageProperty) ? 1 : 0;
    }

    @Override
    public boolean isReplaceable(IBlockAccess world, @Nonnull BlockPos pos) {
        return testingPlace;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer entityplayer, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && entityplayer.isSneaking()) {
            TileEntityCardboardBox tileEntity = (TileEntityCardboardBox) world.getTileEntity(pos);

            if (tileEntity.storedData != null) {
                BlockData data = tileEntity.storedData;
                testingPlace = true;
                if (!data.block.canPlaceBlockAt(world, pos)) {
                    testingPlace = false;
                    return true;
                }
                testingPlace = false;
                if (data.block != null) {
                    IBlockState newstate = data.block.getStateForPlacement(world, pos, side, hitX, hitY, hitZ, data.meta, entityplayer, hand);
                    data.meta = newstate.getBlock().getMetaFromState(newstate);
                }
                world.setBlockState(pos, data.block.getStateFromMeta(data.meta), 3);
                if (data.tileTag != null && world.getTileEntity(pos) != null) {
                    data.updateLocation(pos);
                    world.getTileEntity(pos).readFromNBT(data.tileTag);
                }
                if (data.block != null) {
                    data.block.onBlockPlacedBy(world, pos, data.block.getStateFromMeta(data.meta), entityplayer, new ItemStack(data.block, 1, data.meta));
                }
                spawnAsEntity(world, pos, new ItemStack(MekanismBlocks.CardboardBox));
            }
        }
        return entityplayer.isSneaking();
    }

    @Nonnull
    @Override
    @Deprecated
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    public TileEntity createNewTileEntity(@Nonnull World world, int meta) {
        return new TileEntityCardboardBox();
    }

    @Nonnull
    @Override
    protected ItemStack getDropItem(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos) {
        TileEntityCardboardBox tileEntity = (TileEntityCardboardBox) world.getTileEntity(pos);
        Item item = Item.getItemFromBlock(state.getBlock());
        ItemStack itemStack = new ItemStack(item, 1, state.getBlock().getMetaFromState(state));
        if (tileEntity.storedData != null) {
            ((ItemBlockCardboardBox) item).setBlockData(itemStack, tileEntity.storedData);
        }
        return itemStack;
    }

    /**
     * If the player is sneaking and the dest block is a cardboard box, ensure onBlockActivated is called, and that the item use is not.
     *
     * @param blockEvent event
     */
    @SubscribeEvent
    public void rightClickEvent(RightClickBlock blockEvent) {
        if (blockEvent.getEntityPlayer().isSneaking() && blockEvent.getWorld().getBlockState(blockEvent.getPos()).getBlock() == this) {
            blockEvent.setUseBlock(Event.Result.ALLOW);
            blockEvent.setUseItem(Event.Result.DENY);
        }
    }

    public static class BlockData {

        public Block block;
        public int meta;
        public NBTTagCompound tileTag;

        public BlockData(Block b, int j, NBTTagCompound nbtTags) {
            block = b;
            meta = j;
            tileTag = nbtTags;
        }

        public BlockData() {
        }

        public static BlockData read(NBTTagCompound nbtTags) {
            BlockData data = new BlockData();
            data.block = Block.getBlockById(nbtTags.getInteger("id"));
            data.meta = nbtTags.getInteger("meta");
            if (nbtTags.hasKey("tileTag")) {
                data.tileTag = nbtTags.getCompoundTag("tileTag");
            }
            return data;
        }

        public void updateLocation(BlockPos pos) {
            if (tileTag != null) {
                tileTag.setInteger("x", pos.getX());
                tileTag.setInteger("y", pos.getY());
                tileTag.setInteger("z", pos.getZ());
            }
        }

        public NBTTagCompound write(NBTTagCompound nbtTags) {
            nbtTags.setInteger("id", Block.getIdFromBlock(block));
            nbtTags.setInteger("meta", meta);
            if (tileTag != null) {
                nbtTags.setTag("tileTag", tileTag);
            }
            return nbtTags;
        }
    }
}
