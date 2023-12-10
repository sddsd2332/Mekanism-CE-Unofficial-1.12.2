package mekanism.common.block;

import com.google.common.cache.LoadingCache;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import mekanism.api.Coord4D;
import mekanism.api.IMekWrench;
import mekanism.api.energy.IEnergizedItem;
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
import mekanism.common.content.tank.TankUpdateProtocol;
import mekanism.common.integration.wrenches.Wrenches;
import mekanism.common.inventory.InventoryBin;
import mekanism.common.item.ItemBlockBasic;
import mekanism.common.multiblock.IMultiblock;
import mekanism.common.multiblock.IStructuralMultiblock;
import mekanism.api.tier.BaseTier;
import mekanism.common.tile.*;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.util.FluidContainerUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.SecurityUtils;
import mekanism.common.util.StackUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFire;
import net.minecraft.block.BlockPortal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.BlockWorldState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.state.pattern.BlockPattern;
import net.minecraft.block.state.pattern.BlockPattern.PatternHelper;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving.SpawnPlacementType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.EnumFacing.AxisDirection;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.world.BlockEvent.NeighborNotifyEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

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

        ItemStack copyStack = StackUtils.size(itemStack, 1);
        if (FluidContainerUtils.isFluidContainer(itemStack)) {
            IFluidHandlerItem handler = FluidUtil.getFluidHandler(copyStack);
            if (FluidUtil.getFluidContained(copyStack) == null) {
                if (tileEntity.structure.fluidStored != null) {
                    int filled = handler.fill(tileEntity.structure.fluidStored, !player.capabilities.isCreativeMode);
                    copyStack = handler.getContainer();
                    if (filled > 0) {
                        if (player.capabilities.isCreativeMode) {
                            tileEntity.structure.fluidStored.amount -= filled;
                        } else if (itemStack.getCount() == 1) {
                            tileEntity.structure.fluidStored.amount -= filled;
                            player.setHeldItem(hand, copyStack);
                        } else if (itemStack.getCount() > 1 && player.inventory.addItemStackToInventory(copyStack)) {
                            tileEntity.structure.fluidStored.amount -= filled;
                            itemStack.shrink(1);
                        }
                        if (tileEntity.structure.fluidStored.amount == 0) {
                            tileEntity.structure.fluidStored = null;
                        }
                        return true;
                    }
                }
            } else {
                FluidStack itemFluid = FluidUtil.getFluidContained(copyStack);
                int stored = tileEntity.structure.fluidStored != null ? tileEntity.structure.fluidStored.amount : 0;
                int needed = (tileEntity.structure.volume * TankUpdateProtocol.FLUID_PER_TANK) - stored;
                if (tileEntity.structure.fluidStored != null && !tileEntity.structure.fluidStored.isFluidEqual(itemFluid)) {
                    return false;
                }
                boolean filled = false;
                FluidStack drained = handler.drain(needed, !player.capabilities.isCreativeMode);
                copyStack = handler.getContainer();

                if (copyStack.getCount() == 0) {
                    copyStack = ItemStack.EMPTY;
                }
                if (drained != null) {
                    if (player.capabilities.isCreativeMode) {
                        filled = true;
                    } else if (!copyStack.isEmpty()) {
                        if (itemStack.getCount() == 1) {
                            player.setHeldItem(hand, copyStack);
                            filled = true;
                        } else if (player.inventory.addItemStackToInventory(copyStack)) {
                            itemStack.shrink(1);
                            filled = true;
                        }
                    } else {
                        itemStack.shrink(1);
                        if (itemStack.getCount() == 0) {
                            player.setHeldItem(hand, ItemStack.EMPTY);
                        }
                        filled = true;
                    }

                    if (filled) {
                        if (tileEntity.structure.fluidStored == null) {
                            tileEntity.structure.fluidStored = drained;
                        } else {
                            tileEntity.structure.fluidStored.amount += drained.amount;
                        }
                        return true;
                    }
                }
            }
        }
        return false;
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
        if (tile instanceof TileEntityBasicBlock && ((TileEntityBasicBlock) tile).facing != null) {
            state = state.withProperty(BlockStateFacing.facingProperty, ((TileEntityBasicBlock) tile).facing);
        }
        if (tile instanceof IActiveState) {
            state = state.withProperty(BlockStateBasic.activeProperty, ((IActiveState) tile).getActive());
        }
        if (tile instanceof TileEntityInductionCell) {
            state = state.withProperty(BlockStateBasic.tierProperty, ((TileEntityInductionCell) tile).tier.getBaseTier());
        }
        if (tile instanceof TileEntityInductionProvider) {
            state = state.withProperty(BlockStateBasic.tierProperty, ((TileEntityInductionProvider) tile).tier.getBaseTier());
        }
        if (tile instanceof TileEntityBin) {
            state = state.withProperty(BlockStateBasic.tierProperty, ((TileEntityBin) tile).tier.getBaseTier());
        }
        if (tile instanceof TileEntityInductionPort) {
            state = state.withProperty(BlockStateBasic.activeProperty, ((TileEntityInductionPort) tile).mode);
        }
        if (tile instanceof TileEntitySuperheatingElement) {
            TileEntitySuperheatingElement element = (TileEntitySuperheatingElement) tile;
            boolean active = false;
            if (element.multiblockUUID != null && SynchronizedBoilerData.clientHotMap.get(element.multiblockUUID) != null) {
                active = SynchronizedBoilerData.clientHotMap.get(element.multiblockUUID);
            }
            state = state.withProperty(BlockStateBasic.activeProperty, active);
        }
        return state;
    }

    @Override
    @Deprecated
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block neighborBlock, BlockPos fromPos) {
        if (!world.isRemote) {
            TileEntity tileEntity = new Coord4D(pos, world).getTileEntity(world);
            if (tileEntity instanceof IMultiblock) {
                ((IMultiblock<?>) tileEntity).doUpdate();
            }
            if (tileEntity instanceof TileEntityBasicBlock) {
                ((TileEntityBasicBlock) tileEntity).onNeighborChange(neighborBlock);
            }
            if (tileEntity instanceof IStructuralMultiblock) {
                ((IStructuralMultiblock) tileEntity).doUpdate();
            }
            Block newBlock = world.getBlockState(fromPos).getBlock();
            if (BasicBlockType.get(state) == BasicBlockType.REFINED_OBSIDIAN && newBlock instanceof BlockFire) {
                BlockPortalOverride.instance.trySpawnPortal(world, fromPos);
            }
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
        switch (type) {
            case REFINED_OBSIDIAN:
                return 2400F;
            case OSMIUM_BLOCK:
                return 12F;
            case STEEL_BLOCK:
            case BRONZE_BLOCK:
            case STEEL_CASING:
            case SECURITY_DESK:
            case THERMAL_EVAPORATION_BLOCK:
            case THERMAL_EVAPORATION_VALVE:
            case THERMAL_EVAPORATION_CONTROLLER:
                return 9F;
            default:
                return defaultResistance;
        }
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
                    case INDUCTION_CELL:
                    case INDUCTION_PROVIDER:
                    case BIN:
                        for (BaseTier tier : BaseTier.values()) {
                            if (type == BasicBlockType.BIN || tier.isObtainable()) {
                                ItemStack stack = new ItemStack(this, 1, type.meta);
                                ((ItemBlockBasic) stack.getItem()).setBaseTier(stack, tier);
                                list.add(stack);
                            }
                        }
                        break;
                    default:
                        list.add(new ItemStack(this, 1, type.meta));
                }
            }
        }
    }

    @Override
    public boolean canCreatureSpawn(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos, SpawnPlacementType type) {
        int meta = state.getBlock().getMetaFromState(state);
        switch (getBasicBlock()) {
            case BASIC_BLOCK_1:
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
            case BASIC_BLOCK_2:
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
            default:
                return super.canCreatureSpawn(state, world, pos, type);
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

        if (tile instanceof TileEntityThermalEvaporationController) {
            if (!entityplayer.isSneaking()) {
                if (!world.isRemote) {
                    entityplayer.openGui(Mekanism.instance, 33, world, pos.getX(), pos.getY(), pos.getZ());
                }
                return true;
            }
        } else if (tile instanceof TileEntitySecurityDesk) {
            UUID ownerUUID = ((TileEntitySecurityDesk) tile).ownerUUID;
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
        } else if (tile instanceof TileEntityBin) {
            TileEntityBin bin = (TileEntityBin) tile;
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
        } else if (tile instanceof IMultiblock) {
            if (world.isRemote) {
                return true;
            }
            return ((IMultiblock<?>) world.getTileEntity(pos)).onActivate(entityplayer, hand, stack);
        } else if (tile instanceof IStructuralMultiblock) {
            if (world.isRemote) {
                return true;
            }

            return ((IStructuralMultiblock) world.getTileEntity(pos)).onActivate(entityplayer, hand, stack);
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
        if (tileEntity instanceof IActiveState) {
            if (((IActiveState) tileEntity).getActive() && ((IActiveState) tileEntity).lightUpdate()) {
                return 15;
            }
        }
        if (getBasicBlock() == BasicBlock.BASIC_BLOCK_1) {
            switch (metadata) {
                case 2:
                    return 8;
                case 4:
                    return 15;
                case 7:
                    return 12;
            }
        } else if (getBasicBlock() == BasicBlock.BASIC_BLOCK_2) {
            if (metadata == 5 && tileEntity instanceof TileEntitySuperheatingElement) {
                TileEntitySuperheatingElement element = (TileEntitySuperheatingElement) tileEntity;
                if (element.multiblockUUID != null && SynchronizedBoilerData.clientHotMap.get(element.multiblockUUID) != null) {
                    return SynchronizedBoilerData.clientHotMap.get(element.multiblockUUID) ? 15 : 0;
                }
                return 0;
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
        if (te instanceof TileEntityBasicBlock) {
            TileEntityBasicBlock tileEntity = (TileEntityBasicBlock) te;
            EnumFacing change = EnumFacing.SOUTH;
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
                switch (side) {
                    case 0:
                        change = EnumFacing.NORTH;
                        break;
                    case 1:
                        change = EnumFacing.EAST;
                        break;
                    case 2:
                        change = EnumFacing.SOUTH;
                        break;
                    case 3:
                        change = EnumFacing.WEST;
                        break;
                }
            }
            tileEntity.setFacing(change);
            tileEntity.redstone = world.getRedstonePowerFromNeighbors(pos) > 0;

            if (tileEntity instanceof TileEntitySecurityDesk) {
                ((TileEntitySecurityDesk) tileEntity).ownerUUID = placer.getUniqueID();
            }
            if (tileEntity instanceof IBoundingBlock) {
                ((IBoundingBlock) tileEntity).onPlace();
            }
        }

        world.markBlockRangeForRenderUpdate(pos, pos.add(1, 1, 1));
        world.checkLightFor(EnumSkyBlock.BLOCK, pos);
        world.checkLightFor(EnumSkyBlock.SKY, pos);

        if (!world.isRemote && te != null) {
            if (te instanceof IMultiblock) {
                ((IMultiblock<?>) te).doUpdate();
            }
            if (te instanceof IStructuralMultiblock) {
                ((IStructuralMultiblock) te).doUpdate();
            }
        }
    }

    @Override
    public void breakBlock(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull IBlockState state) {
        TileEntity tileEntity = world.getTileEntity(pos);
        if (tileEntity instanceof IBoundingBlock) {
            ((IBoundingBlock) tileEntity).onBreak();
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
            InventoryBin inv = new InventoryBin(ret);
            ((ITierItem) ret.getItem()).setBaseTier(ret, tileEntity.tier.getBaseTier());
            inv.setItemCount(tileEntity.getItemCount());
            if (tileEntity.getItemCount() > 0) {
                inv.setItemType(tileEntity.itemType);
            }
        } else if (type == BasicBlockType.INDUCTION_CELL) {
            TileEntityInductionCell tileEntity = (TileEntityInductionCell) world.getTileEntity(pos);
            ((ItemBlockBasic) ret.getItem()).setBaseTier(ret, tileEntity.tier.getBaseTier());
        } else if (type == BasicBlockType.INDUCTION_PROVIDER) {
            TileEntityInductionProvider tileEntity = (TileEntityInductionProvider) world.getTileEntity(pos);
            ((ItemBlockBasic) ret.getItem()).setBaseTier(ret, tileEntity.tier.getBaseTier());
        }

        TileEntity tileEntity = world.getTileEntity(pos);
        if (tileEntity instanceof IStrictEnergyStorage) {
            IEnergizedItem energizedItem = (IEnergizedItem) ret.getItem();
            energizedItem.setEnergy(ret, ((IStrictEnergyStorage) tileEntity).getEnergy());
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
        if (tile instanceof TileEntityBasicBlock) {
            TileEntityBasicBlock basicTile = (TileEntityBasicBlock) tile;
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
        if (tile instanceof TileEntityBasicBlock) {
            TileEntityBasicBlock basicTile = (TileEntityBasicBlock) tile;
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
            if (tile instanceof IComparatorSupport) {
                return ((IComparatorSupport) tile).getRedstoneLevel();
            }
        }
        return 0;
    }

    public static class Size extends BlockPortal.Size {

        private int portalBlockCount;

        public Size(World world, BlockPos pos, Axis axis) {
            super(world, pos, axis);
        }

        private boolean isFrame(IBlockState state) {
            Block block = state.getBlock();
            BasicBlockType type = null;
            if (block instanceof BlockBasic) {
                type = BasicBlockType.get(state);
            }
            return block == Blocks.OBSIDIAN || type == BasicBlockType.REFINED_OBSIDIAN;
        }

        @Override
        protected int getDistanceUntilEdge(BlockPos pos, @Nonnull EnumFacing facing) {
            int i;
            for (i = 0; i < 22; ++i) {
                BlockPos blockpos = pos.offset(facing, i);
                if (!this.isEmptyBlock(this.world.getBlockState(blockpos).getBlock()) || !isFrame(this.world.getBlockState(blockpos.down()))) {
                    break;
                }
            }
            return isFrame(this.world.getBlockState(pos.offset(facing, i))) ? i : 0;
        }

        @Override
        protected int calculatePortalHeight() {
            label56:
            for (this.height = 0; this.height < 21; ++this.height) {
                for (int i = 0; i < this.width; ++i) {
                    BlockPos blockpos = this.bottomLeft.offset(this.rightDir, i).up(this.height);
                    Block block = this.world.getBlockState(blockpos).getBlock();
                    if (!this.isEmptyBlock(block)) {
                        break label56;
                    }
                    if (block == Blocks.PORTAL) {
                        ++this.portalBlockCount;
                    }
                    if (i == 0) {
                        if (!isFrame(this.world.getBlockState(blockpos.offset(this.leftDir)))) {
                            break label56;
                        }
                    } else if (i == this.width - 1) {
                        if (!isFrame(this.world.getBlockState(blockpos.offset(this.rightDir)))) {
                            break label56;
                        }
                    }
                }
            }

            for (int j = 0; j < this.width; ++j) {
                if (!isFrame(this.world.getBlockState(this.bottomLeft.offset(this.rightDir, j).up(this.height)))) {
                    this.height = 0;
                    break;
                }
            }
            if (this.height <= 21 && this.height >= 3) {
                return this.height;
            }
            this.bottomLeft = null;
            this.width = 0;
            this.height = 0;
            return 0;
        }
    }

    public static class BlockPortalOverride extends BlockPortal {

        public static final BlockPortalOverride instance = new BlockPortalOverride();

        public BlockPortalOverride() {
            super();
            setRegistryName(new ResourceLocation("minecraft", "portal"));
            setHardness(-1.0F);
            setSoundType(SoundType.GLASS);
            setLightLevel(0.75F);
        }

        @Nonnull
        @Override
        public PatternHelper createPatternHelper(@Nonnull World world, BlockPos pos) {
            Axis axis = Axis.Z;
            BlockBasic.Size size = new BlockBasic.Size(world, pos, Axis.X);
            if (!size.isValid()) {
                axis = Axis.X;
                size = new BlockBasic.Size(world, pos, EnumFacing.Axis.Z);
            }
            LoadingCache<BlockPos, BlockWorldState> loadingCache = BlockPattern.createLoadingCache(world, true);
            if (!size.isValid()) {
                return new PatternHelper(pos, EnumFacing.NORTH, EnumFacing.UP, loadingCache, 1, 1, 1);
            }
            int[] aint = new int[AxisDirection.values().length];
            EnumFacing enumfacing = MekanismUtils.getRight(size.rightDir);
            BlockPos blockpos = size.bottomLeft.up(size.getHeight() - 1);

            for (AxisDirection direction : AxisDirection.values()) {
                PatternHelper patternHelper = new PatternHelper(enumfacing.getAxisDirection() == direction ? blockpos : blockpos.offset(size.rightDir, size.getWidth() - 1),
                        EnumFacing.getFacingFromAxis(direction, axis), EnumFacing.UP, loadingCache, size.getWidth(), size.getHeight(), 1);

                for (int i = 0; i < size.getWidth(); ++i) {
                    for (int j = 0; j < size.getHeight(); ++j) {
                        if (patternHelper.translateOffset(i, j, 1).getBlockState().getMaterial() != Material.AIR) {
                            ++aint[direction.ordinal()];
                        }
                    }
                }
            }

            AxisDirection axisDirection = AxisDirection.POSITIVE;
            for (AxisDirection direction : AxisDirection.values()) {
                if (aint[direction.ordinal()] < aint[axisDirection.ordinal()]) {
                    axisDirection = direction;
                }
            }
            return new PatternHelper(enumfacing.getAxisDirection() == axisDirection ? blockpos : blockpos.offset(size.rightDir, size.getWidth() - 1),
                    EnumFacing.getFacingFromAxis(axisDirection, axis), EnumFacing.UP, loadingCache, size.getWidth(), size.getHeight(), 1);
        }

        @Override
        public void neighborChanged(IBlockState state, @Nonnull World world, @Nonnull BlockPos pos, Block blockIn, BlockPos fromPos) {
            Axis axis = state.getValue(AXIS);
            if (axis == Axis.X || axis == Axis.Z) {
                BlockBasic.Size size = new BlockBasic.Size(world, pos, axis);
                if (!size.isValid() || size.portalBlockCount < size.width * size.height) {
                    world.setBlockState(pos, Blocks.AIR.getDefaultState());
                }
            }
        }

        @Override
        public boolean trySpawnPortal(@Nonnull World world, BlockPos pos) {
            return trySpawnPortal(world, pos, Axis.X) || trySpawnPortal(world, pos, Axis.Z);
        }

        private boolean trySpawnPortal(@Nonnull World world, BlockPos pos, Axis axis) {
            BlockBasic.Size size = new BlockBasic.Size(world, pos, axis);
            if (size.isValid() && size.portalBlockCount == 0 && !ForgeEventFactory.onTrySpawnPortal(world, pos, size)) {
                size.placePortalBlocks();
                return true;
            }
            return false;
        }
    }

    public static class NeighborListener {

        public static final NeighborListener instance = new NeighborListener();

        public NeighborListener() {
            MinecraftForge.EVENT_BUS.register(this);
        }

        @SubscribeEvent
        public void onNeighborNotify(NeighborNotifyEvent e) {
            if (e.getState().getBlock() == Blocks.OBSIDIAN) {
                World world = e.getWorld();
                Block newBlock = world.getBlockState(e.getPos()).getBlock();
                if (newBlock instanceof BlockFire) {
                    BlockPortalOverride.instance.trySpawnPortal(world, e.getPos());
                }
            }
        }
    }
}
