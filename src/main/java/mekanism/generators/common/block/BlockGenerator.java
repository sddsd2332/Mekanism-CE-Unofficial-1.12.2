package mekanism.generators.common.block;

import mekanism.api.IMekWrench;
import mekanism.common.base.*;
import mekanism.common.block.BlockMekanismContainer;
import mekanism.common.block.states.BlockStateFacing;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.wrenches.Wrenches;
import mekanism.common.multiblock.IMultiblock;
import mekanism.common.security.ISecurityItem;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.multiblock.TileEntityMultiblock;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.tile.prefab.TileEntityElectricBlock;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismPlacementData;
import mekanism.common.util.SecurityUtils;
import mekanism.common.util.StorageUtils;
import mekanism.generators.common.GeneratorsItems;
import mekanism.generators.common.MekanismGenerators;
import mekanism.generators.common.block.states.BlockStateGenerator;
import mekanism.generators.common.block.states.BlockStateGenerator.GeneratorBlock;
import mekanism.generators.common.block.states.BlockStateGenerator.GeneratorType;
import mekanism.generators.common.tile.TileEntitySolarGenerator;
import mekanism.generators.common.tile.fission.TileEntityFissionReactorPort;
import mekanism.generators.common.tile.turbine.TileEntityTurbineRotor;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.Random;

/**
 * Block class for handling multiple generator block IDs. 0: Heat Generator 1: Solar Generator 3: Hydrogen Generator 4: Bio-Generator 5: Advanced Solar Generator 6: Wind
 * Generator 7: Turbine Rotor 8: Rotational Complex 9: Electromagnetic Coil 10: Turbine Casing 11: Turbine Valve 12: Turbine Vent 13: Saturating Condenser
 *
 * @author AidanBrady
 */
public abstract class BlockGenerator extends BlockMekanismContainer {

    private static final AxisAlignedBB SOLAR_BOUNDS = new AxisAlignedBB(0.0F, 0.0F, 0.0F, 1.0F, 0.7F, 1.0F);
    private static final AxisAlignedBB ROTOR_BOUNDS = new AxisAlignedBB(0.375F, 0.0F, 0.375F, 0.625F, 1.0F, 0.625F);

    public BlockGenerator() {
        super(Material.IRON);
        setHardness(3.5F);
        setResistance(8F);
        setCreativeTab(MekanismGenerators.tabMekanismGenerators);
    }

    public static BlockGenerator getGeneratorBlock(GeneratorBlock block) {
        return new BlockGenerator() {
            @Override
            public GeneratorBlock getGeneratorBlock() {
                return block;
            }
        };
    }

    public abstract GeneratorBlock getGeneratorBlock();

    @Nonnull
    @Override
    public BlockStateContainer createBlockState() {
        return new BlockStateGenerator(this, getTypeProperty());
    }

    @Nonnull
    @Override
    @Deprecated
    public IBlockState getStateFromMeta(int meta) {
        GeneratorType type = GeneratorType.get(getGeneratorBlock(), meta & 0xF);
        return getDefaultState().withProperty(getTypeProperty(), type);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        GeneratorType type = state.getValue(getTypeProperty());
        return type.meta;
    }

    @Nonnull
    @Override
    @Deprecated
    public IBlockState getActualState(@Nonnull IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        TileEntity tile = MekanismUtils.getTileEntitySafe(worldIn, pos);
        if (tile instanceof TileEntityBasicBlock block && block.facing != null) {
            state = state.withProperty(BlockStateFacing.facingProperty, block.facing);
        }
        if (tile instanceof IActiveState activeState) {
            state = state.withProperty(BlockStateGenerator.activeProperty, activeState.getActive());
        }
        if (tile instanceof TileEntityFissionReactorPort port) {
            state = state.withProperty(BlockStateGenerator.fissionPortModeProperty, port.getRenderMode());
        }
        return state;
    }

    @Override
    @Deprecated
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block neighborBlock, BlockPos neighborPos) {
        if (!world.isRemote) {
            final TileEntity tileEntity = MekanismUtils.getTileEntity(world, pos);
            if (tileEntity instanceof IMultiblock<?> multiblock) {
                multiblock.doUpdate();
            }
            if (tileEntity instanceof TileEntityBasicBlock block) {
                block.onNeighborChange(neighborBlock);
            }
        }
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase entityliving, ItemStack itemstack) {
        TileEntityBasicBlock tileEntity = (TileEntityBasicBlock) world.getTileEntity(pos);
        EnumFacing change = EnumFacing.SOUTH;
        if (tileEntity.canSetFacing(EnumFacing.DOWN) && tileEntity.canSetFacing(EnumFacing.UP)) {
            int height = Math.round(entityliving.rotationPitch);
            if (height >= 65) {
                change = EnumFacing.UP;
            } else if (height <= -65) {
                change = EnumFacing.DOWN;
            }
        }

        if (change != EnumFacing.DOWN && change != EnumFacing.UP) {
            int side = MathHelper.floor((double) (entityliving.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
            change = switch (side) {
                case 0 -> EnumFacing.NORTH;
                case 1 -> EnumFacing.EAST;
                case 2 -> EnumFacing.SOUTH;
                case 3 -> EnumFacing.WEST;
                default -> change;
            };
        }

        tileEntity.setFacing(change);
        tileEntity.redstone = world.getRedstonePowerFromNeighbors(pos) > 0;
        if (tileEntity instanceof IBoundingBlock block) {
            block.onPlace();
        }
        MekanismPlacementData.apply(world, pos, entityliving, itemstack);
        if (!world.isRemote && tileEntity instanceof IMultiblock<?> multiblock) {
            multiblock.doUpdate();
        }
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (MekanismConfig.current().client.enableAmbientLighting.val()) {
            TileEntity tileEntity = MekanismUtils.getTileEntitySafe(world, pos);
            if (tileEntity instanceof IActiveState activeState && !(tileEntity instanceof TileEntitySolarGenerator)) {
                if (activeState.getActive() && activeState.lightUpdate()) {
                    return MekanismConfig.current().client.ambientLightingLevel.val();
                }
            }
        }
        return 0;
    }


    @Override
    public int damageDropped(IBlockState state) {
        return state.getBlock().getMetaFromState(state);
    }

    @Override
    @Deprecated
    public float getPlayerRelativeBlockHardness(IBlockState state, @Nonnull EntityPlayer player, @Nonnull World world, @Nonnull BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return SecurityUtils.canAccess(player, tile) ? super.getPlayerRelativeBlockHardness(state, player, world, pos) : 0.0F;
    }

    @Override
    public void getSubBlocks(CreativeTabs creativetabs, NonNullList<ItemStack> list) {
        for (GeneratorType type : GeneratorType.values()) {
            if (type.blockType != getGeneratorBlock() || !type.isEnabled()) {
                continue;
            }
            list.add(new ItemStack(this, 1, type.meta));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random random) {
        GeneratorType type = GeneratorType.get(state.getBlock(), state.getBlock().getMetaFromState(state));
        TileEntityBasicBlock tileEntity = (TileEntityBasicBlock) world.getTileEntity(pos);

        if (MekanismUtils.isActive(world, pos)) {
            float xRandom = (float) pos.getX() + 0.5F;
            float yRandom = (float) pos.getY() + random.nextFloat() * 6.0F / 16.0F;
            float zRandom = (float) pos.getZ() + 0.5F;
            float iRandom = 0.52F;
            float jRandom = random.nextFloat() * 0.6F - 0.3F;

            if (tileEntity.facing == EnumFacing.WEST) {
                switch (type) {
                    case HEAT_GENERATOR -> {
                        world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, xRandom + iRandom, yRandom, zRandom - jRandom, 0.0D, 0.0D, 0.0D);
                        world.spawnParticle(EnumParticleTypes.FLAME, xRandom + iRandom, yRandom, zRandom - jRandom, 0.0D, 0.0D, 0.0D);
                    }
                    case BIO_GENERATOR ->
                            world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, pos.getX() + .25, pos.getY() + .2, pos.getZ() + .5, 0.0D, 0.0D, 0.0D);
                    default -> {
                    }
                }
            } else if (tileEntity.facing == EnumFacing.EAST) {
                switch (type) {
                    case HEAT_GENERATOR -> {
                        world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, xRandom + iRandom, yRandom + 0.5F, zRandom - jRandom, 0.0D, 0.0D, 0.0D);
                        world.spawnParticle(EnumParticleTypes.FLAME, xRandom + iRandom, yRandom + 0.5F, zRandom - jRandom, 0.0D, 0.0D, 0.0D);
                    }
                    case BIO_GENERATOR ->
                            world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, pos.getX() + .75, pos.getY() + .2, pos.getZ() + .5, 0.0D, 0.0D, 0.0D);
                    default -> {
                    }
                }
            } else if (tileEntity.facing == EnumFacing.NORTH) {
                switch (type) {
                    case HEAT_GENERATOR -> {
                        world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, xRandom - jRandom, yRandom + 0.5F, zRandom - iRandom, 0.0D, 0.0D, 0.0D);
                        world.spawnParticle(EnumParticleTypes.FLAME, xRandom - jRandom, yRandom + 0.5F, zRandom - iRandom, 0.0D, 0.0D, 0.0D);
                    }
                    case BIO_GENERATOR ->
                            world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, pos.getX() + .5, pos.getY() + .2, pos.getZ() + .25, 0.0D, 0.0D, 0.0D);
                    default -> {
                    }
                }
            } else if (tileEntity.facing == EnumFacing.SOUTH) {
                switch (type) {
                    case HEAT_GENERATOR -> {
                        world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, xRandom - jRandom, yRandom + 0.5F, zRandom + iRandom, 0.0D, 0.0D, 0.0D);
                        world.spawnParticle(EnumParticleTypes.FLAME, xRandom - jRandom, yRandom + 0.5F, zRandom + iRandom, 0.0D, 0.0D, 0.0D);
                    }
                    case BIO_GENERATOR ->
                            world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, pos.getX() + .5, pos.getY() + .2, pos.getZ() + .75, 0.0D, 0.0D, 0.0D);
                    default -> {
                    }
                }
            }
        }
    }

    @Override
    public void breakBlock(World world, @Nonnull BlockPos pos, @Nonnull IBlockState state) {
        TileEntityBasicBlock tileEntity = (TileEntityBasicBlock) world.getTileEntity(pos);
        if (!world.isRemote && tileEntity instanceof TileEntityTurbineRotor rotor) {
            int amount = rotor.getHousedBlades();
            if (amount > 0) {
                spawnAsEntity(world, pos, new ItemStack(GeneratorsItems.TurbineBlade, amount));
            }
        }
        if (tileEntity instanceof IBoundingBlock block) {
            block.onBreak();
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer entityplayer,
                                    EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntityBasicBlock tileEntity = (TileEntityBasicBlock) world.getTileEntity(pos);
        int metadata = state.getBlock().getMetaFromState(state);
        GeneratorType generatorType = GeneratorType.get(getGeneratorBlock(), metadata);
        if (generatorType == null) {
            return false;
        }
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
                        if (tileEntity != null) {
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

        if (generatorType == GeneratorType.TURBINE_CASING || generatorType == GeneratorType.TURBINE_VALVE || generatorType == GeneratorType.TURBINE_VENT
                || generatorType == GeneratorType.FISSION_REACTOR_CASING || generatorType == GeneratorType.FISSION_REACTOR_PORT
                || generatorType == GeneratorType.FISSION_REACTOR_LOGIC_ADAPTER) {
            return ((IMultiblock<?>) tileEntity).onActivate(entityplayer, hand, stack);
        }

        if (generatorType == GeneratorType.TURBINE_ROTOR) {
            TileEntityTurbineRotor rod = (TileEntityTurbineRotor) tileEntity;
            if (!entityplayer.isSneaking()) {
                if (!stack.isEmpty() && stack.getItem() == GeneratorsItems.TurbineBlade) {
                    if (rod.addBlade()) {
                        if (!entityplayer.capabilities.isCreativeMode) {
                            stack.shrink(1);
                            if (stack.getCount() == 0) {
                                entityplayer.setHeldItem(hand, ItemStack.EMPTY);
                            }
                        }
                    }
                    return true;
                }
            } else if (stack.isEmpty()) {
                if (rod.removeBlade()) {
                    if (!entityplayer.capabilities.isCreativeMode) {
                        entityplayer.setHeldItem(hand, new ItemStack(GeneratorsItems.TurbineBlade));
                        entityplayer.inventory.markDirty();
                    }
                }
            } else if (stack.getItem() == GeneratorsItems.TurbineBlade) {
                if (stack.getCount() < stack.getMaxStackSize()) {
                    if (rod.removeBlade()) {
                        if (!entityplayer.capabilities.isCreativeMode) {
                            stack.grow(1);
                            entityplayer.inventory.markDirty();
                        }
                    }
                }
            }
            return true;
        }

        int guiId = generatorType.guiId;

        if (guiId != -1 && tileEntity != null) {
            if (!entityplayer.isSneaking()) {
                if (SecurityUtils.canAccess(entityplayer, tileEntity)) {
                    entityplayer.openGui(MekanismGenerators.instance, guiId, world, pos.getX(), pos.getY(), pos.getZ());
                } else {
                    SecurityUtils.displayNoAccess(entityplayer);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public TileEntity createTileEntity(@Nonnull World world, @Nonnull IBlockState state) {
        int metadata = state.getBlock().getMetaFromState(state);
        if (GeneratorType.get(getGeneratorBlock(), metadata) == null) {
            return null;
        }
        return GeneratorType.get(getGeneratorBlock(), metadata).create();
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
        GeneratorType type = GeneratorType.get(state);
        if (type == null) {
            return super.isOpaqueCube(state);
        }
        return type != GeneratorType.HEAT_GENERATOR && type != GeneratorType.SOLAR_GENERATOR && type != GeneratorType.GAS_GENERATOR
                && type != GeneratorType.BIO_GENERATOR && type != GeneratorType.ADVANCED_SOLAR_GENERATOR && type != GeneratorType.WIND_GENERATOR
                && type != GeneratorType.TURBINE_ROTOR && type != GeneratorType.FISSION_FUEL_ASSEMBLY && type != GeneratorType.CONTROL_ROD_ASSEMBLY;
    }

    @Override
    @Deprecated
    public boolean isFullCube(IBlockState state) {
        GeneratorType type = GeneratorType.get(state);
        if (type == null) {
            return super.isFullCube(state);
        }
        return type != GeneratorType.HEAT_GENERATOR && type != GeneratorType.SOLAR_GENERATOR && type != GeneratorType.GAS_GENERATOR
                && type != GeneratorType.BIO_GENERATOR && type != GeneratorType.ADVANCED_SOLAR_GENERATOR && type != GeneratorType.WIND_GENERATOR
                && type != GeneratorType.TURBINE_ROTOR && type != GeneratorType.FISSION_FUEL_ASSEMBLY && type != GeneratorType.CONTROL_ROD_ASSEMBLY;
    }

    @Override
    @Deprecated
    public boolean isFullBlock(IBlockState state) {
        GeneratorType type = GeneratorType.get(state);
        if (type == null) {
            return super.isFullBlock(state);
        }
        return type != GeneratorType.HEAT_GENERATOR && type != GeneratorType.SOLAR_GENERATOR && type != GeneratorType.GAS_GENERATOR
                && type != GeneratorType.BIO_GENERATOR && type != GeneratorType.ADVANCED_SOLAR_GENERATOR && type != GeneratorType.WIND_GENERATOR
                && type != GeneratorType.TURBINE_ROTOR && type != GeneratorType.FISSION_FUEL_ASSEMBLY && type != GeneratorType.CONTROL_ROD_ASSEMBLY;
    }

    @Override
    public int getLightOpacity(IBlockState state, IBlockAccess world, BlockPos pos) {
        GeneratorType type = GeneratorType.get(state);
        if (type == null) {
            return super.getLightOpacity(state, world, pos);
        }
        return isOpaqueCube(state) ? 255 : 0;
    }

    @Nonnull
    @Override
    @Deprecated
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        GeneratorType type = GeneratorType.get(state);
        if (type != null) {
            switch (type) {
                case SOLAR_GENERATOR -> {
                    return BlockFaceShape.UNDEFINED;
                }
                case ADVANCED_SOLAR_GENERATOR, WIND_GENERATOR -> {
                    return face == EnumFacing.DOWN ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
                }
                case TURBINE_ROTOR -> {
                    return face != EnumFacing.UP && face != EnumFacing.DOWN ? BlockFaceShape.MIDDLE_POLE : BlockFaceShape.CENTER;
                }
                case FISSION_FUEL_ASSEMBLY, CONTROL_ROD_ASSEMBLY -> {
                    return BlockFaceShape.UNDEFINED;
                }
            }
        }
        return super.getBlockFaceShape(world, state, pos, face);
    }

    @SideOnly(Side.CLIENT)
    @Nonnull
    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    /*This method is not used, metadata manipulation is required to create a Tile Entity.*/
    @Override
    public TileEntity createNewTileEntity(@Nonnull World world, int meta) {
        return null;
    }

    @Nonnull
    @Override
    @Deprecated
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        GeneratorType type = GeneratorType.get(state);
        return switch (type) {
            case SOLAR_GENERATOR -> SOLAR_BOUNDS;
            case TURBINE_ROTOR -> ROTOR_BOUNDS;
            default -> super.getBoundingBox(state, world, pos);
        };
    }

    @Nonnull
    @Override
    protected ItemStack getDropItem(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos) {
        TileEntityBasicBlock tileEntity = (TileEntityBasicBlock) world.getTileEntity(pos);
        ItemStack itemStack = new ItemStack(this, 1, state.getBlock().getMetaFromState(state));

        if (itemStack.getTagCompound() == null && !(tileEntity instanceof TileEntityMultiblock)) {
            itemStack.setTagCompound(new NBTTagCompound());
        }
        if (tileEntity == null) {
            return ItemStack.EMPTY;
        }
        if (tileEntity instanceof ISecurityTile tile) {
            ISecurityItem securityItem = (ISecurityItem) itemStack.getItem();
            if (securityItem.hasSecurity(itemStack)) {
                securityItem.setOwnerUUID(itemStack, tile.getSecurity().getOwnerUUID());
                securityItem.setSecurity(itemStack, tile.getSecurity().getMode());
            }
        }

        if (tileEntity instanceof TileEntityElectricBlock block) {
            StorageUtils.setStoredEnergy(itemStack, block.electricityStored.get(), block.getMaxEnergy());
        }
        if (tileEntity instanceof TileEntityContainerBlock block && block.persistInventory()) {
            ISustainedInventory inventory = (ISustainedInventory) itemStack.getItem();
            inventory.setInventory(block.getInventory(), itemStack);
        }
        if (tileEntity instanceof ISustainedData data) {
            data.writeSustainedData(itemStack);
        }
        if (((ISustainedTank) itemStack.getItem()).hasTank(itemStack)) {
            if (tileEntity instanceof ISustainedTank tank) {
                if (tank.getFluidStack() != null) {
                    ((ISustainedTank) itemStack.getItem()).setFluidStack(tank.getFluidStack(), itemStack);
                }
            }
        }
        return itemStack;
    }

    @Override
    @Deprecated
    public boolean isSideSolid(IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos, EnumFacing side) {
        GeneratorType type = GeneratorType.get(getGeneratorBlock(), state.getBlock().getMetaFromState(state));
        return type != GeneratorType.SOLAR_GENERATOR && type != GeneratorType.ADVANCED_SOLAR_GENERATOR && type != GeneratorType.WIND_GENERATOR
                && type != GeneratorType.TURBINE_ROTOR && type != GeneratorType.FISSION_FUEL_ASSEMBLY && type != GeneratorType.CONTROL_ROD_ASSEMBLY;

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

    public PropertyEnum<GeneratorType> getTypeProperty() {
        return getGeneratorBlock().getProperty();
    }

    @Override
    public boolean canCreatureSpawn(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos, EntityLiving.SpawnPlacementType type) {
        GeneratorType generatorType = GeneratorType.get(state);
        if (generatorType == GeneratorType.TURBINE_CASING || generatorType == GeneratorType.TURBINE_VALVE || generatorType == GeneratorType.TURBINE_VENT
                || generatorType == GeneratorType.FISSION_REACTOR_CASING || generatorType == GeneratorType.FISSION_REACTOR_PORT
                || generatorType == GeneratorType.FISSION_REACTOR_LOGIC_ADAPTER) {
            TileEntityMultiblock<?> tileEntity = MekanismUtils.getTileEntitySafe(world, pos, TileEntityMultiblock.class);
            if (tileEntity != null) {
                if (FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER) {
                    if (tileEntity.structure != null) {
                        return false;
                    }
                } else if (tileEntity.clientHasStructure) {
                    return false;
                }
            }
        }
        return super.canCreatureSpawn(state, world, pos, type);
    }

    @Override
    public boolean hasComparatorInputOverride(IBlockState blockState) {
        GeneratorType generatorType = GeneratorType.get(blockState);
        return generatorType != null && generatorType.hasRedstoneOutput;
    }

    @Override
    public int getComparatorInputOverride(IBlockState blockState, World worldIn, BlockPos pos) {
        GeneratorType generatorType = GeneratorType.get(blockState);
        if (generatorType != null && generatorType.hasRedstoneOutput) {
            TileEntity tile = worldIn.getTileEntity(pos);
            if (tile instanceof IComparatorSupport) {
                return ((IComparatorSupport) tile).getRedstoneLevel();
            }
        }
        return 0;
    }
}
