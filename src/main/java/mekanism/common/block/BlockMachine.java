package mekanism.common.block;

import mekanism.api.Coord4D;
import mekanism.api.IMekWrench;
import mekanism.api.NBTConstants;
import mekanism.api.energy.IStrictEnergyStorage;
import mekanism.client.render.particle.MekanismParticleHelper;
import mekanism.common.Mekanism;
import mekanism.common.base.*;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.block.states.BlockStateFacing;
import mekanism.common.block.states.BlockStateMachine;
import mekanism.common.block.states.BlockStateMachine.MachineBlock;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.entangloporter.InventoryFrequency;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.integration.wrenches.Wrenches;
import mekanism.common.item.ItemBlockMachine;
import mekanism.common.network.PacketLogisticalSorterGui.LogisticalSorterGuiMessage;
import mekanism.common.network.PacketLogisticalSorterGui.SorterGuiPacket;
import mekanism.common.security.ISecurityItem;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tier.FluidTankTier;
import mekanism.common.tile.*;
import mekanism.common.tile.factory.TileEntityFactory;
import mekanism.common.tile.laser.TileEntityLaser;
import mekanism.common.tile.laser.TileEntityLaserAmplifier;
import mekanism.common.tile.machine.TileEntityMetallurgicInfuser;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.Random;

/**
 * Block class for handling multiple machine block IDs. 0:0: Enrichment Chamber 0:1: Osmium Compressor 0:2: Combiner 0:3: Crusher 0:4: Digital Miner 0:5: Basic Factory
 * 0:6: Advanced Factory 0:7: Elite Factory 0:8: Metallurgic Infuser 0:9: Purification Chamber 0:10: Energized Smelter 0:11: Teleporter 0:12: Electric Pump 0:13: Electric
 * Chest 0:14: Chargepad 0:15: Logistical Sorter 1:0: Rotary Condensentrator 1:1: Chemical Oxidizer 1:2: Chemical Infuser 1:3: Chemical Injection Chamber 1:4:
 * Electrolytic Separator 1:5: Precision Sawmill 1:6: Chemical Dissolution Chamber 1:7: Chemical Washer 1:8: Chemical Crystallizer 1:9: Seismic Vibrator 1:10: Pressurized
 * Reaction Chamber 1:11: Fluid Tank 1:12: Fluidic Plenisher 1:13: Laser 1:14: Laser Amplifier 1:15: Laser Tractor Beam 2:0: Quantum Entangloporter 2:1: Solar Neutron
 * Activator 2:2: Ambient Accumulator 2:3: Oredictionificator 2:4: Resistive Heater 2:5: Formulaic Assemblicator 2:6: Fuelwood Heater
 *
 * @author AidanBrady
 */
public abstract class BlockMachine extends BlockMekanismContainer {

    private static final AxisAlignedBB CHARGEPAD_BOUNDS = new AxisAlignedBB(0.0F, 0.0F, 0.0F, 1.0F, 0.06F, 1.0F);
    private static final AxisAlignedBB TANK_BOUNDS = new AxisAlignedBB(0.125F, 0.0F, 0.125F, 0.875F, 1.0F, 0.875F);
    private static final AxisAlignedBB LASER_BOUNDS = new AxisAlignedBB(0.25F, 0.0F, 0.25F, 0.75F, 1.0F, 0.75F);
    private static final AxisAlignedBB LOGISTICAL_SORTER_BOUNDS = new AxisAlignedBB(0.125F, 0.0F, 0.125F, 0.875F, 1.0F, 0.875F);

    private static final AxisAlignedBB SUPERCHARGED_COIL = new AxisAlignedBB(0.25F, 0.0F, 0.25F, 0.75F, 1.0F, 0.75F);

    private static final AxisAlignedBB IndustrialAlarmBOUNDS = new AxisAlignedBB(0.25F, 0.6875F, 0.25F, 0.75F, 1.0F, 0.75F);

    public BlockMachine() {
        super(Material.IRON);
        setHardness(3.5F);
        setResistance(16F);
        setCreativeTab(Mekanism.tabMekanism);
    }

    public static BlockMachine getBlockMachine(MachineBlock block) {
        return new BlockMachine() {
            @Override
            public MachineBlock getMachineBlock() {
                return block;
            }
        };
    }

    public abstract MachineBlock getMachineBlock();

    @Nonnull
    @Override
    public BlockStateContainer createBlockState() {
        return new BlockStateMachine(this, getTypeProperty());
    }

    @Nonnull
    @Override
    @Deprecated
    public IBlockState getStateFromMeta(int meta) {
        MachineType type = MachineType.get(getMachineBlock(), meta & 0xF);
        return getDefaultState().withProperty(getTypeProperty(), type);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        MachineType type = state.getValue(getTypeProperty());
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
            state = state.withProperty(BlockStateMachine.activeProperty, activeState.getActive());
        }
        if (tile instanceof TileEntityFluidTank tank) {
            state = state.withProperty(BlockStateMachine.tierProperty, tank.tier.getBaseTier());
        }
        if (tile instanceof TileEntityFactory factory) {
            state = state.withProperty(BlockStateMachine.recipeProperty, factory.getRecipeType());
        }
        return state;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        TileEntityBasicBlock tileEntity = (TileEntityBasicBlock) world.getTileEntity(pos);
        if (tileEntity == null) {
            return;
        }

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

        if (tileEntity instanceof TileEntityLogisticalSorter transporter) {
            if (!transporter.hasInventory()) {
                for (EnumFacing dir : EnumFacing.VALUES) {
                    TileEntity tile = Coord4D.get(transporter).offset(dir).getTileEntity(world);
                    if (InventoryUtils.isItemHandler(tile, dir)) {
                        tileEntity.setFacing(dir.getOpposite());
                        break;
                    }
                }
            }
        }
        if (tileEntity instanceof IBoundingBlock block) {
            block.onPlace();
        }
    }

    @Override
    public void breakBlock(World world, @Nonnull BlockPos pos, @Nonnull IBlockState state) {
        TileEntityBasicBlock tileEntity = (TileEntityBasicBlock) world.getTileEntity(pos);
        if (tileEntity instanceof IBoundingBlock block) {
            block.onBreak();
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random random) {
        super.randomDisplayTick(state,world,pos,random);
        TileEntityBasicBlock tileEntity = (TileEntityBasicBlock) world.getTileEntity(pos);
        if (tileEntity instanceof TileEntityFluidTank) {
            return;
        }
        if (MekanismUtils.isActive(world, pos) && ((IActiveState) tileEntity).renderUpdate() && MekanismConfig.current().client.machineEffects.val()) {
            float xRandom = (float) pos.getX() + 0.5F;
            float yRandom = (float) pos.getY() + 0.0F + random.nextFloat() * 6.0F / 16.0F;
            float zRandom = (float) pos.getZ() + 0.5F;
            float iRandom = 0.52F;
            float jRandom = random.nextFloat() * 0.6F - 0.3F;
            EnumFacing side = tileEntity.facing;
            if (tileEntity instanceof TileEntityMetallurgicInfuser) {
                side = side.getOpposite();
            }

            switch (side) {
                case WEST -> {
                    world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, xRandom - iRandom, yRandom, zRandom + jRandom, 0.0D, 0.0D, 0.0D);
                    world.spawnParticle(EnumParticleTypes.REDSTONE, xRandom - iRandom, yRandom, zRandom + jRandom, 0.0D, 0.0D, 0.0D);
                }
                case EAST -> {
                    world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, xRandom + iRandom, yRandom, zRandom + jRandom, 0.0D, 0.0D, 0.0D);
                    world.spawnParticle(EnumParticleTypes.REDSTONE, xRandom + iRandom, yRandom, zRandom + jRandom, 0.0D, 0.0D, 0.0D);
                }
                case NORTH -> {
                    world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, xRandom + jRandom, yRandom, zRandom - iRandom, 0.0D, 0.0D, 0.0D);
                    world.spawnParticle(EnumParticleTypes.REDSTONE, xRandom + jRandom, yRandom, zRandom - iRandom, 0.0D, 0.0D, 0.0D);
                }
                case SOUTH -> {
                    world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, xRandom + jRandom, yRandom, zRandom + iRandom, 0.0D, 0.0D, 0.0D);
                    world.spawnParticle(EnumParticleTypes.REDSTONE, xRandom + jRandom, yRandom, zRandom + iRandom, 0.0D, 0.0D, 0.0D);
                }
                default -> {
                }
            }
        }
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (MekanismConfig.current().client.enableAmbientLighting.val()) {
            TileEntity tileEntity = MekanismUtils.getTileEntitySafe(world, pos);
            if (tileEntity instanceof IActiveState activeState &&
                    activeState.lightUpdate() &&
                    activeState.wasActiveRecently()) {
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
        for (MachineType type : MachineType.getValidMachines()) {
            if (type.typeBlock == getMachineBlock() && type.isEnabled()) {
                switch (type) {
                    case BASIC_FACTORY, ADVANCED_FACTORY, ELITE_FACTORY, ULTIMATE_FACTORY -> {
                        for (RecipeType recipe : RecipeType.values()) {
                            if (recipe.getType().isEnabled()) {
                                ItemStack stack = new ItemStack(this, 1, type.meta);
                                ((IFactory) stack.getItem()).setRecipeType(recipe.ordinal(), stack);
                                list.add(stack);
                            }
                        }
                    }
                    case FLUID_TANK -> {
                        ItemBlockMachine itemMachine = (ItemBlockMachine) Item.getItemFromBlock(this);
                        for (FluidTankTier tier : FluidTankTier.values()) {
                            ItemStack stack = new ItemStack(this, 1, type.meta);
                            itemMachine.setBaseTier(stack, tier.getBaseTier());
                            list.add(stack);
                        }
                    }
                    default -> list.add(new ItemStack(this, 1, type.meta));
                }
            }
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer entityplayer, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntityBasicBlock tileEntity = (TileEntityBasicBlock) world.getTileEntity(pos);
        int metadata = state.getBlock().getMetaFromState(state);
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
                            EnumFacing change = tileEntity.facing.rotateY();
                            if (tileEntity instanceof TileEntityLogisticalSorter sorter) {
                                if (!sorter.hasInventory()) {
                                    for (EnumFacing dir : EnumFacing.VALUES) {
                                        TileEntity tile = Coord4D.get(tileEntity).offset(dir).getTileEntity(world);
                                        if (InventoryUtils.isItemHandler(tile, dir)) {
                                            change = dir.getOpposite();
                                            break;
                                        }
                                    }
                                }
                            }
                            tileEntity.setFacing(change);
                            world.notifyNeighborsOfStateChange(pos, this, true);
                        }
                    } else {
                        SecurityUtils.displayNoAccess(entityplayer);
                    }
                    return true;
                }
            }
        }

        if (tileEntity != null) {
            MachineType type = MachineType.get(getMachineBlock(), metadata);
            switch (type) {
                case PERSONAL_CHEST -> {
                    if (!entityplayer.isSneaking() && !world.isSideSolid(pos.up(), EnumFacing.DOWN)) {
                        if (SecurityUtils.canAccess(entityplayer, tileEntity)) {
                            entityplayer.openGui(Mekanism.instance, type.guiId, world, pos.getX(), pos.getY(), pos.getZ());
                        } else {
                            SecurityUtils.displayNoAccess(entityplayer);
                        }
                        return true;
                    }
                }
                case FLUID_TANK -> {
                    if (!entityplayer.isSneaking()) {
                        if (SecurityUtils.canAccess(entityplayer, tileEntity)) {
                            if (!stack.isEmpty() && manageInventory(entityplayer, (TileEntityFluidTank) tileEntity, hand, stack)) {
                                entityplayer.inventory.markDirty();
                                return true;
                            } else {
                                entityplayer.openGui(Mekanism.instance, type.guiId, world, pos.getX(), pos.getY(), pos.getZ());
                            }
                        } else {
                            SecurityUtils.displayNoAccess(entityplayer);
                        }
                        return true;
                    }
                }
                case LOGISTICAL_SORTER -> {
                    if (!entityplayer.isSneaking()) {
                        if (SecurityUtils.canAccess(entityplayer, tileEntity)) {
                            LogisticalSorterGuiMessage.openServerGui(SorterGuiPacket.SERVER, 0, world, (EntityPlayerMP) entityplayer, Coord4D.get(tileEntity), -1);
                        } else {
                            SecurityUtils.displayNoAccess(entityplayer);
                        }
                        return true;
                    }
                }
                default -> {
                    if (!entityplayer.isSneaking() && type.guiId != -1) {
                        if (SecurityUtils.canAccess(entityplayer, tileEntity)) {
                            entityplayer.openGui(Mekanism.instance, type.guiId, world, pos.getX(), pos.getY(), pos.getZ());
                        } else {
                            SecurityUtils.displayNoAccess(entityplayer);
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public TileEntity createTileEntity(@Nonnull World world, @Nonnull IBlockState state) {
        int metadata = state.getBlock().getMetaFromState(state);
        if (MachineType.get(getMachineBlock(), metadata) == null) {
            return null;
        }
        return MachineType.get(getMachineBlock(), metadata).create();
    }

    @Override
    public TileEntity createNewTileEntity(@Nonnull World world, int metadata) {
        return null;
    }

    @Override
    @Deprecated
    public boolean isOpaqueCube(IBlockState state) {
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

    @Override
    @Deprecated
    public float getPlayerRelativeBlockHardness(IBlockState state, @Nonnull EntityPlayer player, @Nonnull World world, @Nonnull BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return SecurityUtils.canAccess(player, tile) ? super.getPlayerRelativeBlockHardness(state, player, world, pos) : 0.0F;
    }

    @Override
    public float getExplosionResistance(World world, BlockPos pos, Entity exploder, Explosion explosion) {
        IBlockState state = world.getBlockState(pos);
        if (MachineType.get(getMachineBlock(), state.getBlock().getMetaFromState(state)) != MachineType.PERSONAL_CHEST) {
            return blockResistance;
        }
        return -1;
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
        if (tileEntity instanceof IComparatorSupport support) {
            return support.getRedstoneLevel();
        }
        if (tileEntity instanceof TileEntityLaserAmplifier amplifier) {
            //TODO: Move this over to using IComparatorSupport?
            if (amplifier.outputMode == TileEntityLaserAmplifier.RedstoneOutput.ENERGY_CONTENTS) {
                return amplifier.getRedstoneLevel();
            }
            return getWeakPower(state, world, pos, null);
        }
        return 0;
    }

    private boolean manageInventory(EntityPlayer player, TileEntityFluidTank tileEntity, EnumHand hand, ItemStack itemStack) {
        return FluidUtils.handleTankInteraction(player, hand, itemStack, tileEntity.fluidTank, tileEntity.tier != FluidTankTier.CREATIVE);
    }

    @Override
    @Deprecated
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block neighborBlock, BlockPos neighborPos) {
        if (!world.isRemote) {
            TileEntity tileEntity = world.getTileEntity(pos);
            if (tileEntity instanceof TileEntityBasicBlock block) {
                block.onNeighborChange(neighborBlock);
            }
            if (tileEntity instanceof TileEntityLogisticalSorter sorter) {
                if (!sorter.hasInventory()) {
                    for (EnumFacing dir : EnumFacing.VALUES) {
                        TileEntity tile = Coord4D.get(tileEntity).offset(dir).getTileEntity(world);
                        if (InventoryUtils.isItemHandler(tile, dir)) {
                            sorter.setFacing(dir.getOpposite());
                            return;
                        }
                    }
                }
            }
        }
    }

    @Nonnull
    @Override
    protected ItemStack getDropItem(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos) {
        TileEntityBasicBlock tileEntity = (TileEntityBasicBlock) world.getTileEntity(pos);
        ItemStack itemStack = new ItemStack(this, 1, state.getBlock().getMetaFromState(state));
        if (itemStack.getTagCompound() == null) {
            itemStack.setTagCompound(new NBTTagCompound());
        }
        if (tileEntity instanceof TileEntityFluidTank tank) {
            ITierItem tierItem = (ITierItem) itemStack.getItem();
            tierItem.setBaseTier(itemStack, tank.tier.getBaseTier());
        }
        if (tileEntity instanceof ISecurityTile securityTile) {
            ISecurityItem securityItem = (ISecurityItem) itemStack.getItem();
            if (securityItem.hasSecurity(itemStack)) {
                securityItem.setOwnerUUID(itemStack, securityTile.getSecurity().getOwnerUUID());
                securityItem.setSecurity(itemStack, securityTile.getSecurity().getMode());
            }
        }
        if (tileEntity instanceof IUpgradeTile upgradeTile) {
            upgradeTile.getComponent().write(ItemDataUtils.getDataMap(itemStack));
        }
        if (tileEntity instanceof ISideConfiguration config) {
            config.getConfig().write(ItemDataUtils.getDataMap(itemStack));
            config.getEjector().write(ItemDataUtils.getDataMap(itemStack));
        }
        if (tileEntity instanceof ISustainedData data) {
            data.writeSustainedData(itemStack);
        }
        if (tileEntity instanceof IRedstoneControl control) {
            ItemDataUtils.setInt(itemStack, "controlType", control.getControlType().ordinal());
        }
        if (tileEntity instanceof TileEntityContainerBlock block && !block.isEmpty()) {
            ISustainedInventory inventory = (ISustainedInventory) itemStack.getItem();
            inventory.setInventory(((ISustainedInventory) tileEntity).getInventory(), itemStack);
        }
        if (((ISustainedTank) itemStack.getItem()).hasTank(itemStack)) {
            if (tileEntity instanceof ISustainedTank tank) {
                if (tank.getFluidStack() != null) {
                    ((ISustainedTank) itemStack.getItem()).setFluidStack(tank.getFluidStack(), itemStack);
                }
            }
        }
        if (tileEntity instanceof TileEntityFactory factory) {
            IFactory factoryItem = (IFactory) itemStack.getItem();
            factoryItem.setRecipeType(factory.getRecipeType().ordinal(), itemStack);
        }
        //this MUST be done after the factory info is saved, as it caps the energy to max, which is based on the recipe type
        if (tileEntity instanceof IStrictEnergyStorage storage) {
            StorageUtils.setStoredEnergy(itemStack, storage.getEnergy(), storage.getMaxEnergy());
        }
        if (tileEntity instanceof TileEntityQuantumEntangloporter entangloporter) {
            InventoryFrequency frequency = entangloporter.getFreq();
            if (frequency != null) {
                NBTTagCompound frequencyNBT = new NBTTagCompound();
                frequencyNBT.setTag(FrequencyType.INVENTORY.getName(), FrequencyType.INVENTORY.getIdentitySerializer().write(frequency.getIdentity()));
                ItemDataUtils.setCompound(itemStack, NBTConstants.COMPONENT_FREQUENCY, frequencyNBT);
            }
        }
        return itemStack;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean addHitEffects(IBlockState state, World world, RayTraceResult target, ParticleManager manager) {
        if (!target.typeOfHit.equals(Type.BLOCK)) {
            return super.addHitEffects(state, world, target, manager);
        }
        MachineType type = state.getValue(getTypeProperty());
        //If it is one of the types that block state won't have a color for
        if (type == MachineType.FLUID_TANK && MekanismParticleHelper.addBlockHitEffects(world, target.getBlockPos(), target.sideHit, manager)) {
            return true;
        }
        return super.addHitEffects(state, world, target, manager);
    }

    @Override
    public boolean canConnectRedstone(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        MachineType type = MachineType.get(getMachineBlock(), state.getBlock().getMetaFromState(state));
        return type == MachineType.LASER_AMPLIFIER || type == MachineType.INDUSTRIAL_ALARM;
    }

    @Nonnull
    @Override
    @Deprecated
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        MachineType type = MachineType.get(getMachineBlock(), state.getBlock().getMetaFromState(state));
        TileEntity tile = MekanismUtils.getTileEntitySafe(world, pos);

        if (type == null) {
            return super.getBoundingBox(state, world, pos);
        }

        switch (type) {
            case CHARGEPAD:
                return CHARGEPAD_BOUNDS;
            case FLUID_TANK:
                return TANK_BOUNDS;
            case LASER:
                if (tile instanceof TileEntityLaser laser) {
                    return MultipartUtils.rotate(LASER_BOUNDS.offset(-0.5, -0.5, -0.5), laser.facing).offset(0.5, 0.5, 0.5);
                }
            case LOGISTICAL_SORTER:
                if (tile instanceof TileEntityLogisticalSorter sorter) {
                    return MultipartUtils.rotate(LOGISTICAL_SORTER_BOUNDS.offset(-0.5, -0.5, -0.5), sorter.facing).offset(0.5, 0.5, 0.5);
                }
            case SUPERCHARGED_COIL:
                if (tile instanceof TileEntitySuperchargedCoil coil) {
                    return MultipartUtils.rotate(SUPERCHARGED_COIL.offset(-0.5, -0.5, -0.5), coil.facing).offset(0.5, 0.5, 0.5);
                }
            case INDUSTRIAL_ALARM:
                if (tile instanceof TileEntityIndustrialAlarm alarm) {
                    return MultipartUtils.rotate(IndustrialAlarmBOUNDS.offset(-0.5, -0.5, -0.5), alarm.facing).offset(0.5, 0.5, 0.5);
                }
            default:
                return super.getBoundingBox(state, world, pos);
        }
    }

    @Override
    @Deprecated
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    @Deprecated
    public boolean isSideSolid(IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos, EnumFacing side) {
        MachineType type = MachineType.get(getMachineBlock(), state.getBlock().getMetaFromState(state));
        if (type != null) {
            switch (type) {
                case CHARGEPAD, PERSONAL_CHEST -> {
                    return false;
                }
                case FLUID_TANK -> {
                    return side == EnumFacing.UP || side == EnumFacing.DOWN;
                }
            }
        }
        return true;
    }

    @Nonnull
    @Override
    @Deprecated
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        MachineType type = MachineType.get(getMachineBlock(), state.getBlock().getMetaFromState(state));
        if (type != null) {
            switch (type) {
                case PERSONAL_CHEST, LOGISTICAL_SORTER, LASER, SUPERCHARGED_COIL, INDUSTRIAL_ALARM -> {
                    return BlockFaceShape.UNDEFINED;
                }
                case ELECTRIC_PUMP, FLUIDIC_PLENISHER -> {
                    return face == EnumFacing.UP || face == EnumFacing.DOWN ? BlockFaceShape.CENTER_BIG : BlockFaceShape.UNDEFINED;
                }
                case CHARGEPAD -> {
                    return face == EnumFacing.DOWN ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
                }
                case FLUID_TANK -> {
                    return face != EnumFacing.UP && face != EnumFacing.DOWN ? BlockFaceShape.UNDEFINED : BlockFaceShape.SOLID;
                }
            }
        }
        return super.getBlockFaceShape(world, state, pos, face);
    }

    public PropertyEnum<MachineType> getTypeProperty() {
        return getMachineBlock().getProperty();
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

    @Override
    @Deprecated
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        TileEntity tile = MekanismUtils.getTileEntitySafe(world, pos);
        if (tile instanceof TileEntityLaserAmplifier amplifier) {
            return amplifier.emittingRedstone ? 15 : 0;
        }
        return 0;
    }
}
