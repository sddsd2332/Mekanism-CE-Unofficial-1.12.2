package mekanism.common.util;

import com.mojang.authlib.GameProfile;
import ic2.api.energy.EnergyNet;
import it.unimi.dsi.fastutil.longs.Long2DoubleArrayMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import mekanism.api.*;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.Mekanism;
import mekanism.common.MekanismBlocks;
import mekanism.common.MekanismFluids;
import mekanism.common.Upgrade;
import mekanism.common.base.*;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.block.states.BlockStateTransmitter.TransmitterType;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.forgeenergy.FEIntegration;
import mekanism.common.integration.ic2.IC2Integration;
import mekanism.common.integration.redstoneflux.RFIntegration;
import mekanism.common.integration.tesla.TeslaIntegration;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.item.ItemBlockTransmitter;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.FactoryTier;
import mekanism.common.tier.GasTankTier;
import mekanism.common.tile.TileEntityAdvancedBoundingBlock;
import mekanism.common.tile.TileEntityBoundingBlock;
import mekanism.common.tile.base.TileEntitySynchronized;
import mekanism.common.util.UnitDisplayUtils.ElectricUnit;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketEntityEffect;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.stats.StatList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.math.*;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.UsernameCache;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Utilities used by Mekanism. All miscellaneous methods are located here.
 *
 * @author AidanBrady
 */
public final class MekanismUtils {

    public static final EnumFacing[] SIDE_DIRS = new EnumFacing[]{EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST};
    private static final ItemStack MILK = new ItemStack(Items.MILK_BUCKET);
    public static final Map<String, Class<?>> classesFound = new Object2ObjectOpenHashMap<>();
    public static final ThreadLocal<Boolean> isInjecting = ThreadLocal.withInitial(() -> false);

    private static final List<UUID> warnedFails = new ArrayList<>();

    public static void logMismatchedStackSize(long actual, long expected) {
        if (expected != actual) {
            Mekanism.logger.error("Stack size changed by a different amount ({}) than requested ({}).", actual, expected);
            if (MekanismAPI.debug) {
                Mekanism.logger.error("Location ", new Exception());
            }
        }
    }

    /**
     * Pre-calculated cache of translated block orientations
     */
    private static final EnumFacing[][] baseOrientations = new EnumFacing[EnumFacing.VALUES.length][EnumFacing.VALUES.length];

    static {
        for (int blockFacing = 0; blockFacing < EnumFacing.VALUES.length; blockFacing++) {
            for (int side = 0; side < EnumFacing.VALUES.length; side++) {
                baseOrientations[blockFacing][side] = getBaseOrientation(EnumFacing.VALUES[side], EnumFacing.VALUES[blockFacing]);
            }
        }
    }

    /**
     * Retrieves an empty Gas Tank.
     *
     * @return empty gas tank
     */
    public static ItemStack getEmptyGasTank(GasTankTier tier) {
        ItemStack empty = new ItemStack(MekanismBlocks.GasTank);
        if (empty.getItem() instanceof ITierItem tierItem) {
            tierItem.setBaseTier(empty, tier.getBaseTier());
        }
        GasInventorySlot.setGasContained(empty, null);
        return empty;
    }

    public static ItemStack getTransmitter(TransmitterType type, BaseTier tier, int amount) {
        ItemStack stack = new ItemStack(MekanismBlocks.Transmitter, amount, type.ordinal());
        ItemBlockTransmitter itemTransmitter = (ItemBlockTransmitter) stack.getItem();
        itemTransmitter.setBaseTier(stack, tier);
        return stack;
    }

    /**
     * Retrieves a Factory with a defined tier and recipe type.
     *
     * @param tier - tier to add to the Factory
     * @param type - recipe type to add to the Factory
     * @return factory with defined tier and recipe type
     */
    public static ItemStack getFactory(FactoryTier tier, RecipeType type) {
        ItemStack itemstack;
        if (tier == FactoryTier.ULTIMATE) {
            itemstack = new ItemStack(MekanismBlocks.MachineBlock3, 1, 4 + tier.ordinal());
        } else {
            itemstack = new ItemStack(MekanismBlocks.MachineBlock, 1, MachineType.BASIC_FACTORY.ordinal() + tier.ordinal());
        }
        ((IFactory) itemstack.getItem()).setRecipeType(type.ordinal(), itemstack);
        return itemstack;
    }

    public static String getModId(ItemStack stack) {
        ResourceLocation registryName = stack.getItem().getRegistryName();
        return registryName == null ? "" : registryName.getNamespace();
    }

    /**
     * Checks if a machine is in it's active state.
     *
     * @param world World of the machine to check
     * @param pos   The position of the machine
     * @return if machine is active
     */
    public static boolean isActive(IBlockAccess world, BlockPos pos) {
        TileEntity tileEntity = world.getTileEntity(pos);
        if (tileEntity != null) {
            if (tileEntity instanceof IActiveState state) {
                return state.getActive();
            }
        }
        return false;
    }

    /**
     * Gets the left side of a certain orientation.
     *
     * @param orientation Current orientation of the machine
     * @return left side
     */
    public static EnumFacing getLeft(EnumFacing orientation) {
        return orientation.rotateY();
    }

    /**
     * Gets the right side of a certain orientation.
     *
     * @param orientation Current orientation of the machine
     * @return right side
     */
    public static EnumFacing getRight(EnumFacing orientation) {
        return orientation.rotateYCCW();
    }

    /**
     * Gets the opposite side of a certain orientation.
     *
     * @param orientation Current orientation of the machine
     * @return opposite side
     */
    public static EnumFacing getBack(EnumFacing orientation) {
        return orientation.getOpposite();
    }

    /**
     * Returns the sides in the modified order relative to the machine-based orientation.
     *
     * @param blockFacing - what orientation the block is facing
     * @return EnumFacing.VALUES, translated to machine orientation
     */
    public static EnumFacing[] getBaseOrientations(EnumFacing blockFacing) {
        return baseOrientations[blockFacing.ordinal()];
    }

    /**
     * Returns an integer facing that converts a world-based orientation to a machine-based orientation.
     *
     * @param side        - world based
     * @param blockFacing - what orientation the block is facing
     * @return machine orientation
     */
    public static EnumFacing getBaseOrientation(EnumFacing side, EnumFacing blockFacing) {
        if (blockFacing == EnumFacing.DOWN) {
            return switch (side) {
                case DOWN -> EnumFacing.NORTH;
                case UP -> EnumFacing.SOUTH;
                case NORTH -> EnumFacing.UP;
                case SOUTH -> EnumFacing.DOWN;
                default -> side.getOpposite();
            };
        } else if (blockFacing == EnumFacing.UP) {
            return switch (side) {
                case DOWN -> EnumFacing.SOUTH;
                case UP -> EnumFacing.NORTH;
                case NORTH -> EnumFacing.DOWN;
                case SOUTH -> EnumFacing.UP;
                default -> side.getOpposite();
            };
        } else if (blockFacing == EnumFacing.SOUTH || side.getAxis() == Axis.Y) {
            if (side.getAxis() == Axis.Z) {
                return side.getOpposite();
            }
            return side;
        } else if (blockFacing == EnumFacing.NORTH) {
            if (side.getAxis() == Axis.Z) {
                return side;
            }
            return side.getOpposite();
        } else if (blockFacing == EnumFacing.WEST) {
            if (side.getAxis() == Axis.Z) {
                return getRight(side);
            }
            return getLeft(side);
        } else if (blockFacing == EnumFacing.EAST) {
            if (side.getAxis() == Axis.Z) {
                return getLeft(side);
            }
            return getRight(side);
        }
        return side;
    }

    /**
     * Increments the output type of a machine's side.
     *
     * @param config    - configurable machine
     * @param type      - the TransmissionType to modify
     * @param direction - side to increment output of
     */
    public static void incrementOutput(ISideConfiguration config, TransmissionType type, EnumFacing direction) {
        if (config.getConfig().isSideEnabled(type, direction)) {
            config.getConfig().incrementOutput(type, direction);
            assert config instanceof TileEntity;
            TileEntity tile = (TileEntity) config;
            tile.markDirty();
        }
    }

    /**
     * Decrements the output type of a machine's side.
     *
     * @param config    - configurable machine
     * @param type      - the TransmissionType to modify
     * @param direction - side to increment output of
     */
    public static void decrementOutput(ISideConfiguration config, TransmissionType type, EnumFacing direction) {
        if (config.getConfig().isSideEnabled(type, direction)) {
            config.getConfig().decrementOutput(type, direction);
            assert config instanceof TileEntity;
            TileEntity tile = (TileEntity) config;
            tile.markDirty();
        }
    }

    public static float fractionUpgrades(IUpgradeTile mgmt, Upgrade type) {
        if (mgmt == null || type == null) {
            return 0;
        }
        int maxInstalled = type.getMaxInstalled();
        return maxInstalled <= 0 ? 0 : (float) mgmt.getInstalledUpgrades(type) / (float) maxInstalled;
    }

    /**
     * Gets the operating ticks required for a machine via it's upgrades.
     *
     * @param mgmt - tile containing upgrades
     * @param def  - the original, default ticks required
     * @return required operating ticks
     */
    public static int getTicks(IUpgradeTile mgmt, int def) {
        if (MekanismConfig.current().mekce.EnableUpgradeConfigure.val()) {
            double d = def * time(mgmt);
            return d >= 1 ? clampToInt(d) : clampToInt(1 / d) * -1;
        } else {
            return (int) (def * Math.pow(MekanismConfig.current().general.maxUpgradeMultiplier.val(), -fractionUpgrades(mgmt, Upgrade.SPEED)));
        }
    }

    /**
     * Gets the energy required per tick for a machine via it's upgrades.
     *
     * @param mgmt - tile containing upgrades
     * @param def  - the original, default energy required
     * @return required energy per tick
     */
    public static double getEnergyPerTick(IUpgradeTile mgmt, double def) {
        if (MekanismConfig.current().mekce.EnableUpgradeConfigure.val()) {
            return def * electricity(mgmt);
        } else {
            return def * Math.pow(MekanismConfig.current().general.maxUpgradeMultiplier.val(), 2 * fractionUpgrades(mgmt, Upgrade.SPEED) - fractionUpgrades(mgmt, Upgrade.ENERGY));
        }
    }

    /**
     * Gets the energy required per tick for a machine via it's upgrades, not taking into account speed upgrades.
     *
     * @param mgmt - tile containing upgrades
     * @param def  - the original, default energy required
     * @return required energy per tick
     */
    public static double getBaseEnergyPerTick(IUpgradeTile mgmt, double def) {
        return def * Math.pow(MekanismConfig.current().general.maxUpgradeMultiplier.val(), -fractionUpgrades(mgmt, Upgrade.ENERGY));
    }

    /**
     * Gets the secondary energy required per tick for a machine via upgrades.
     *
     * @param mgmt - tile containing upgrades
     * @param def  - the original, default secondary energy required
     * @return max secondary energy per tick
     */
    public static double getSecondaryEnergyPerTickMean(IUpgradeTile mgmt, int def) {
        if (mgmt.supportsUpgrade(Upgrade.GAS)) {
            return def * Math.pow(MekanismConfig.current().general.maxUpgradeMultiplier.val(), 2 * fractionUpgrades(mgmt, Upgrade.SPEED) - fractionUpgrades(mgmt, Upgrade.GAS));
        }
        return def * Math.pow(MekanismConfig.current().general.maxUpgradeMultiplier.val(), fractionUpgrades(mgmt, Upgrade.SPEED));
    }


    public static double getGasPerTickMeanMultiplier(IUpgradeTile tile) {
        if (tile.supportsUpgrades()) {
            if (tile.supportsUpgrade(Upgrade.GAS)) {
                return Math.pow(MekanismConfig.current().general.maxUpgradeMultiplier.val(), 2 * fractionUpgrades(tile, Upgrade.SPEED) - fractionUpgrades(tile, Upgrade.GAS));
            }
            return Math.pow(MekanismConfig.current().general.maxUpgradeMultiplier.val(), fractionUpgrades(tile, Upgrade.SPEED));
        }
        return 1;
    }

    public static long getBaseUsage(IUpgradeTile tile, int def) {
        if (tile.supportsUpgrades()) {
            //getGasPerTickMean * required ticks (not rounded)
            if (tile.supportsUpgrade(Upgrade.GAS)) {
                // def * (upgradeMultiplier ^ (2 * speed - gas)) * (upgradeMultiplier ^ -speed) =
                // def * upgradeMultiplier ^ (speed - gas)
                //TODO: We may want to validate this provides the numbers we desire if we ever end up with any machines
                // that use this that are not statistical and have gas upgrades so would go through this code path
                return Math.round(def * Math.pow(MekanismConfig.current().general.maxUpgradeMultiplier.val(),   fractionUpgrades(tile, Upgrade.SPEED) - fractionUpgrades(tile, Upgrade.GAS)));
            }
        }
        return def;
    }


    /**
     * Gets the maximum energy for a machine via it's upgrades.
     *
     * @param mgmt - tile containing upgrades - best known for "Kids", 2008
     * @param def  - original, default max energy
     * @return max energy
     */
    public static double getMaxEnergy(IUpgradeTile mgmt, double def) {
        if (MekanismConfig.current().mekce.EnableUpgradeConfigure.val()) {
            return def * capacity(mgmt);
        } else {
            return def * Math.pow(MekanismConfig.current().general.maxUpgradeMultiplier.val(), fractionUpgrades(mgmt, Upgrade.ENERGY));
        }
    }

    /**
     * Gets the maximum energy for a machine's item form via it's upgrades.
     *
     * @param itemStack - stack holding energy upgrades
     * @param def       - original, default max energy
     * @return max energy
     */
    public static double getMaxEnergy(ItemStack itemStack, double def) {
        Map<Upgrade, Integer> upgrades = Upgrade.buildComponentMap(ItemDataUtils.getDataMapIfPresent(itemStack));
        float numUpgrades = upgrades.get(Upgrade.ENERGY) == null ? 0 : (float) upgrades.get(Upgrade.ENERGY);
        int maxInstalled = Upgrade.ENERGY.getMaxInstalled();
        return def * Math.pow(MekanismConfig.current().general.maxUpgradeMultiplier.val(), maxInstalled <= 0 ? 0 : numUpgrades / (float) maxInstalled);
    }


    /**
     * Better version of the World.getRedstonePowerFromNeighbors() method that doesn't load chunks.
     *
     * @param world - the world to perform the check in
     * @param coord - the coordinate of the block performing the check
     * @return if the block is indirectly getting powered by LOADED chunks
     */
    public static boolean isGettingPowered(World world, Coord4D coord) {
        for (EnumFacing side : EnumFacing.VALUES) {
            Coord4D sideCoord = coord.offset(side);
            if (sideCoord.exists(world) && sideCoord.offset(side).exists(world)) {
                IBlockState blockState = sideCoord.getBlockState(world);
                boolean weakPower = blockState.getBlock().shouldCheckWeakPower(blockState, world, coord.getPos(), side);
                if (weakPower && isDirectlyGettingPowered(world, sideCoord)) {
                    return true;
                } else if (!weakPower && blockState.getWeakPower(world, sideCoord.getPos(), side) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if a block is directly getting powered by any of its neighbors without loading any chunks.
     *
     * @param world - the world to perform the check in
     * @param coord - the Coord4D of the block to check
     * @return if the block is directly getting powered
     */
    public static boolean isDirectlyGettingPowered(World world, Coord4D coord) {
        for (EnumFacing side : EnumFacing.VALUES) {
            Coord4D sideCoord = coord.offset(side);
            if (sideCoord.exists(world)) {
                if (world.getRedstonePower(coord.getPos(), side) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Notifies neighboring blocks of a TileEntity change without loading chunks.
     *
     * @param world - world to perform the operation in
     * @param coord - Coord4D to perform the operation on
     */
    public static void notifyLoadedNeighborsOfTileChange(World world, Coord4D coord) {
        for (EnumFacing dir : EnumFacing.VALUES) {
            Coord4D offset = coord.offset(dir);
            if (offset.exists(world)) {
                notifyNeighborofChange(world, offset, coord.getPos());
                if (offset.getBlockState(world).isNormalCube()) {
                    offset = offset.offset(dir);
                    if (offset.exists(world)) {
                        Block block1 = offset.getBlock(world);
                        if (block1.getWeakChanges(world, offset.getPos())) {
                            block1.onNeighborChange(world, offset.getPos(), coord.getPos());
                        }
                    }
                }
            }
        }
    }

    /**
     * Calls BOTH neighbour changed functions because nobody can decide on which one to implement.
     *
     * @param world   world the change exists in
     * @param coord   neighbor to notify
     * @param fromPos pos of our block that updated
     */
    public static void notifyNeighborofChange(World world, Coord4D coord, BlockPos fromPos) {
        IBlockState state = coord.getBlockState(world);
        state.getBlock().onNeighborChange(world, coord.getPos(), fromPos);
        state.neighborChanged(world, coord.getPos(), world.getBlockState(fromPos).getBlock(), fromPos);
    }

    /**
     * Calls BOTH neighbour changed functions because nobody can decide on which one to implement.
     *
     * @param world        world the change exists in
     * @param neighborSide The side the neighbor to notify is on
     * @param fromPos      pos of our block that updated
     */
    public static void notifyNeighborOfChange(World world, EnumFacing neighborSide, BlockPos fromPos) {
        BlockPos neighbor = fromPos.offset(neighborSide);
        IBlockState state = world.getBlockState(neighbor);
        state.getBlock().onNeighborChange(world, neighbor, fromPos);
        state.neighborChanged(world, neighbor, world.getBlockState(fromPos).getBlock(), fromPos);
    }

    /**
     * Places a fake bounding block at the defined location.
     *
     * @param world            - world to place block in
     * @param boundingLocation - coordinates of bounding block
     * @param orig             - original block
     */
    public static void makeBoundingBlock(World world, BlockPos boundingLocation, Coord4D orig) {
        world.setBlockState(boundingLocation, MekanismBlocks.BoundingBlock.getStateFromMeta(0));
        TileEntity tile = world.getTileEntity(boundingLocation);
        if (tile instanceof TileEntityBoundingBlock block) {
            block.setMainLocation(orig.getPos());
        }
    }

    /**
     * Places a fake advanced bounding block at the defined location.
     *
     * @param world            - world to place block in
     * @param boundingLocation - coordinates of bounding block
     * @param orig             - original block
     */
    public static void makeAdvancedBoundingBlock(World world, BlockPos boundingLocation, Coord4D orig) {
        world.setBlockState(boundingLocation, MekanismBlocks.BoundingBlock.getStateFromMeta(1));
        TileEntity tile = world.getTileEntity(boundingLocation);
        if (tile instanceof TileEntityAdvancedBoundingBlock block) {
            block.setMainLocation(orig.getPos());
        }
    }

    /**
     * Updates a block's light value and marks it for a render update.
     *
     * @param world - world the block is in
     * @param pos   Position of the block
     */
    public static void updateBlock(World world, BlockPos pos) {
        if (!world.isBlockLoaded(pos)) {
            return;
        }

        // Schedule update.
        TileEntity tileEntity = world.getTileEntity(pos);
        if (!world.isRemote && tileEntity instanceof TileEntitySynchronized teSync) {
            teSync.markNoUpdate();
            teSync.setRequireUpdateLight(true);
            return;
        }

        //Schedule a render update regardless of it is an IActiveState with IActiveState#renderUpdate() as true
        // This is because that is mainly used for rendering machine effects, but we need to run a render update
        // anyways here in case IActiveState#renderUpdate() is false and we just had the block rotate.
        // For example the laser, or charge pad.
        world.markBlockRangeForRenderUpdate(pos, pos);
        if (!(tileEntity instanceof IActiveState activeState) || activeState.lightUpdate() && MekanismConfig.current().client.machineEffects.val()) {
            updateAllLightTypes(world, pos);
        }
    }

    /**
     * Updates all light types at the given coordinates.
     *
     * @param world - the world to perform the lighting update in
     * @param pos   - coordinates of the block to update
     */
    public static void updateAllLightTypes(World world, BlockPos pos) {
        world.checkLightFor(EnumSkyBlock.BLOCK, pos);
        world.checkLightFor(EnumSkyBlock.SKY, pos);
    }

    /**
     * Whether or not a certain block is considered a fluid.
     *
     * @param world - world the block is in
     * @param pos   - coordinates
     * @return if the block is a fluid
     */
    public static boolean isFluid(World world, Coord4D pos) {
        return getFluid(world, pos, false) != null;
    }

    /**
     * Gets a fluid from a certain location.
     *
     * @param world - world the block is in
     * @param pos   - location of the block
     * @return the fluid at the certain location, null if it doesn't exist
     */
    public static FluidStack getFluid(World world, Coord4D pos, boolean filter) {
        IBlockState state = pos.getBlockState(world);
        Block block = state.getBlock();
        if ((block == Blocks.WATER || block == Blocks.FLOWING_WATER) && state.getValue(BlockLiquid.LEVEL) == 0) {
            if (!filter) {
                return new FluidStack(FluidRegistry.WATER, Fluid.BUCKET_VOLUME);
            }
            return new FluidStack(MekanismFluids.HeavyWater, 10);
        } else if ((block == Blocks.LAVA || block == Blocks.FLOWING_LAVA) && state.getValue(BlockLiquid.LEVEL) == 0) {
            return new FluidStack(FluidRegistry.LAVA, Fluid.BUCKET_VOLUME);
        } else if (block instanceof IFluidBlock fluid) {
            if (state.getProperties().containsKey(BlockFluidBase.LEVEL) && state.getValue(BlockFluidBase.LEVEL) == 0) {
                return fluid.drain(world, pos.getPos(), false);
            }
        }
        return null;
    }

    /**
     * Whether or not a block is a dead fluid.
     *
     * @param world - world the block is in
     * @param pos   - coordinates
     * @return if the block is a dead fluid
     */
    public static boolean isDeadFluid(World world, Coord4D pos) {
        IBlockState state = pos.getBlockState(world);
        Block block = state.getBlock();
        if (block.getMetaFromState(state) == 0) {
            return false;
        }
        return block instanceof BlockLiquid || block instanceof IFluidBlock;

    }

    /**
     * Gets the flowing block type from a Forge-based fluid. Incorporates the MC system of fliuds as well.
     *
     * @param fluid - the fluid type
     * @return the block corresponding to the given fluid
     */
    public static Block getFlowingBlock(Fluid fluid) {
        if (fluid == null) {
            return null;
        } else if (fluid == FluidRegistry.WATER) {
            return Blocks.FLOWING_WATER;
        } else if (fluid == FluidRegistry.LAVA) {
            return Blocks.FLOWING_LAVA;
        }
        return fluid.getBlock();
    }

    /**
     * Encodes current item info as a gui, and opens it.
     *
     * @apiNote Should only be used from the server side
     */
    public static void openItemGui(EntityPlayer player, EnumHand hand, int guiID) {
        //current item, hand, gui type
        player.openGui(Mekanism.instance, 0, player.world, player.inventory.currentItem, hand.ordinal(), guiID);
    }

    /**
     * Encodes entity info as a gui, and opens it.
     *
     * @apiNote Should only be used from the server side
     */
    public static void openEntityGui(EntityPlayer player, Entity entity, int guiID) {
        //entity id, gui type
        player.openGui(Mekanism.instance, 1, player.world, entity.getEntityId(), guiID, 0);
    }

    public static ResourceLocation getResource(ResourceType type, String name) {
        return getResource(Mekanism.MODID, type, name);
    }

    /**
     * Gets a ResourceLocation with a defined resource type and name.
     *
     * @param type - type of resource to retrieve
     * @param name - simple name of file to retrieve as a ResourceLocation
     * @return the corresponding ResourceLocation
     */
    public static ResourceLocation getResource(String modid, ResourceType type, String name) {
        return new ResourceLocation(modid, type.getPrefix() + name);
    }

    /**
     * Marks the chunk this TileEntity is in as modified. Call this method to be sure NBT is written by the defined tile entity.
     *
     * @param tileEntity - TileEntity to save
     */
    public static void saveChunk(TileEntity tileEntity) {
        if (tileEntity == null || tileEntity.isInvalid() || tileEntity.getWorld() == null) {
            return;
        }
        tileEntity.getWorld().markChunkDirty(tileEntity.getPos(), tileEntity);
    }

    /**
     * Whether or not a certain TileEntity can function with redstone logic. Illogical to use unless the defined TileEntity implements IRedstoneControl.
     *
     * @param tileEntity - TileEntity to check
     * @return if the TileEntity can function with redstone logic
     */
    public static boolean canFunction(TileEntity tileEntity) {
        if (!(tileEntity instanceof IRedstoneControl control)) {
            return true;
        }
        return switch (control.getControlType()) {
            case DISABLED -> true;
            case HIGH -> control.isPowered();
            case LOW -> !control.isPowered();
            case PULSE -> control.isPowered() && !control.wasPowered();
        };
    }


    public static RayTraceResult rayTrace(World world, EntityPlayer player) {
        return rayTrace(world, player, Mekanism.proxy.getReach(player));
    }

    /**
     * Ray-traces what block a player is looking at.
     *
     * @param world  - world the player is in
     * @param player - player to raytrace
     * @return raytraced value
     */
    public static RayTraceResult rayTrace(World world, EntityPlayer player, double reach) {
        Vec3d headVec = getHeadVec(player);
        Vec3d lookVec = player.getLook(1);
        Vec3d endVec = headVec.add(lookVec.x * reach, lookVec.y * reach, lookVec.z * reach);
        return world.rayTraceBlocks(headVec, endVec, true);
    }

    /**
     * Gets the head vector of a player for a ray trace.
     *
     * @param player - player to check
     * @return head location
     */
    private static Vec3d getHeadVec(EntityPlayer player) {
        double posX = player.posX;
        double posY = player.posY;
        double posZ = player.posZ;
        if (!player.world.isRemote) {
            posY += player.getEyeHeight();
            if (player instanceof EntityPlayerMP && player.isSneaking()) {
                posY -= 0.08;
            }
        }
        return new Vec3d(posX, posY, posZ);
    }

    /**
     * Gets a rounded energy display of a defined amount of energy.
     *
     * @param energy - energy to display
     * @return rounded energy display
     */
    public static String getEnergyDisplay(double energy) {
        if (energy == Double.MAX_VALUE) {
            return LangUtils.localize("gui.infinite");
        }
        return switch (MekanismConfig.current().general.energyUnit.val()) {
            case J -> UnitDisplayUtils.getDisplayShort(energy, ElectricUnit.JOULES);
            case RF -> UnitDisplayUtils.getDisplayShort(RFIntegration.toRFAsDouble(energy), ElectricUnit.REDSTONE_FLUX);
            case EU -> UnitDisplayUtils.getDisplayShort(IC2Integration.toEU(energy), ElectricUnit.ELECTRICAL_UNITS);
            case T -> UnitDisplayUtils.getDisplayShort(TeslaIntegration.toTeslaAsDouble(energy), ElectricUnit.TESLA);
            case FE -> UnitDisplayUtils.getDisplayShort(FEIntegration.tofeAsDouble(energy), ElectricUnit.FORGE_ENERGY);
        };
    }

    public static String getEnergyDisplay(double energy, double max) {
        if (energy == Double.MAX_VALUE) {
            return LangUtils.localize("gui.infinite");
        }
        String energyString = getEnergyDisplay(energy);
        String maxString = getEnergyDisplay(max);
        return energyString + "/" + maxString;
    }

    /**
     * Convert from the unit defined in the configuration to joules.
     *
     * @param energy - energy to convert
     * @return energy converted to joules
     */
    public static double convertToJoules(double energy) {
        return switch (MekanismConfig.current().general.energyUnit.val()) {
            case RF -> RFIntegration.fromRF(energy);
            case EU -> IC2Integration.fromEU(energy);
            case T -> TeslaIntegration.fromTesla(energy);
            case FE -> FEIntegration.fromfe(energy);
            default -> energy;
        };
    }

    /**
     * Convert from joules to the unit defined in the configuration.
     *
     * @param energy - energy to convert
     * @return energy converted to configured unit
     */
    public static double convertToDisplay(double energy) {
        return switch (MekanismConfig.current().general.energyUnit.val()) {
            case RF -> RFIntegration.toRFAsDouble(energy);
            case EU -> IC2Integration.toEU(energy);
            case T -> TeslaIntegration.toTeslaAsDouble(energy);
            case FE -> FEIntegration.tofeAsDouble(energy);
            default -> energy;
        };
    }

    /**
     * Gets a rounded energy display of a defined amount of energy.
     *
     * @param T - temperature to display
     * @return rounded energy display
     */
    public static String getTemperatureDisplay(double T, TemperatureUnit unit) {
        return getTemperatureDisplay(T, unit, true);
    }

    /**
     * Formats either an absolute temperature or a temperature interval using the configured display unit.
     *
     * @param T temperature expressed in {@code unit}
     * @param unit unit the supplied temperature is expressed in
     * @param shift whether the display unit's zero offset should be applied
     */
    public static String getTemperatureDisplay(double T, TemperatureUnit unit, boolean shift) {
        double TK = unit.convertToK(T, true);
        return switch (MekanismConfig.current().general.tempUnit.val()) {
            case K -> UnitDisplayUtils.getDisplayShort(TK, shift, TemperatureUnit.KELVIN);
            case C -> UnitDisplayUtils.getDisplayShort(TK, shift, TemperatureUnit.CELSIUS);
            case R -> UnitDisplayUtils.getDisplayShort(TK, shift, TemperatureUnit.RANKINE);
            case F -> UnitDisplayUtils.getDisplayShort(TK, shift, TemperatureUnit.FAHRENHEIT);
            case STP -> UnitDisplayUtils.getDisplayShort(TK, shift, TemperatureUnit.AMBIENT);
        };
    }

    /**
     * Whether or not IC2 power should be used, taking into account whether or not it is installed or another mod is providing its API.
     *
     * @return if IC2 power should be used
     */
    public static boolean useIC2() {
        return Mekanism.hooks.IC2Loaded  && !MekanismConfig.current().general.blacklistIC2.val() && EnergyNet.instance != null;
    }

    /**
     * Whether or not RF power should be used.
     *
     * @return if RF power should be used
     */
    public static boolean useRF() {
        return Mekanism.hooks.RFLoaded && !MekanismConfig.current().general.blacklistRF.val();
    }

    public static boolean useFlux() {
        return Mekanism.hooks.FluxNetWorksLoaded && !MekanismConfig.current().general.blacklistFlux.val();
    }

    /**
     * Whether or not Tesla power should be used.
     *
     * @return if Tesla power should be used
     */
    public static boolean useTesla() {
        return Mekanism.hooks.TeslaLoaded && !MekanismConfig.current().general.blacklistTesla.val();
    }

    /**
     * Whether or not Forge power should be used.
     *
     * @return if Forge power should be used
     */
    public static boolean useForge() {
        return !MekanismConfig.current().general.blacklistForge.val();
    }

    /**
     * Gets a clean view of a coordinate value without the dimension ID.
     *
     * @param obj - coordinate to check
     * @return coordinate display
     */
    public static String getCoordDisplay(Coord4D obj) {
        return "[" + obj.x + ", " + obj.y + ", " + obj.z + "]";
    }

    @SideOnly(Side.CLIENT)
    public static List<String> splitTooltip(String s, ItemStack stack) {
        s = s.trim();
        FontRenderer renderer = (FontRenderer) Mekanism.proxy.getFontRenderer();
        if (!stack.isEmpty() && stack.getItem().getFontRenderer(stack) != null) {
            renderer = stack.getItem().getFontRenderer(stack);
        }
        if (renderer != null) {
            return renderer.listFormattedStringToWidth(s, 200);
        }
        return Collections.emptyList();
    }

    /**
     * Creates and returns a full gas tank with the specified gas type.
     *
     * @param gas - gas to fill the tank with
     * @return filled gas tank
     */
    public static ItemStack getFullGasTank(GasTankTier tier, Gas gas) {
        ItemStack tank = getEmptyGasTank(tier);
        GasInventorySlot.setGasContained(tank, new GasStack(gas, GasInventorySlot.getTankCapacity(tank, 0)));
        return tank;
    }

    public static InventoryCrafting getDummyCraftingInv() {
        Container tempContainer = new Container() {
            @Override
            public boolean canInteractWith(@Nonnull EntityPlayer player) {
                return false;
            }
        };
        return new InventoryCrafting(tempContainer, 3, 3);
    }

    /**
     * Finds the output of a brute forced repairing action
     *
     * @param inv   - InventoryCrafting to check
     * @param world - world reference
     * @return output ItemStack
     */
    public static ItemStack findRepairRecipe(InventoryCrafting inv, World world) {
        NonNullList<ItemStack> dmgItems = NonNullList.withSize(2, ItemStack.EMPTY);
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            if (!inv.getStackInSlot(i).isEmpty()) {
                if (dmgItems.get(0).isEmpty()) {
                    dmgItems.set(0, inv.getStackInSlot(i));
                } else {
                    dmgItems.set(1, inv.getStackInSlot(i));
                    break;
                }
            }
        }

        if (dmgItems.get(0).isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (!dmgItems.get(1).isEmpty() && (dmgItems.get(0).getItem() == dmgItems.get(1).getItem()) &&
                (dmgItems.get(0).getCount() == 1) && (dmgItems.get(1).getCount() == 1) && dmgItems.get(0).getItem().isRepairable()) {
            Item theItem = dmgItems.get(0).getItem();
            int dmgDiff0 = theItem.getMaxDamage() - dmgItems.get(0).getItemDamage();
            int dmgDiff1 = theItem.getMaxDamage() - dmgItems.get(1).getItemDamage();
            int value = dmgDiff0 + dmgDiff1 + theItem.getMaxDamage() * 5 / 100;
            int solve = Math.max(0, theItem.getMaxDamage() - value);
            return new ItemStack(dmgItems.get(0).getItem(), 1, solve);
        }
        return ItemStack.EMPTY;
    }

    /**
     * Whether or not the provided chunk is being vibrated by a Seismic Vibrator.
     *
     * @param chunk - chunk to check
     * @return if the chunk is being vibrated
     */
    public static boolean isChunkVibrated(Chunk3D chunk) {
        for (Coord4D coord : Mekanism.activeVibrators) {
            if (coord.getChunk3D().equals(chunk)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPlayingMode(EntityPlayer player) {
        return !player.isCreative() && !player.isSpectator();
    }


    /**
     * Whether or not a given EntityPlayer is considered an Op.
     *
     * @param p - player to check
     * @return if the player has operator privileges
     */
    public static boolean isOp(EntityPlayer p) {
        if (!(p instanceof EntityPlayerMP player)) {
            return false;
        }
        return MekanismConfig.current().general.opsBypassRestrictions.val() && player.server.getPlayerList().canSendCommands(player.getGameProfile());
    }

    /**
     * Gets the item ID from a given ItemStack
     *
     * @param itemStack - ItemStack to check
     * @return item ID of the ItemStack
     */
    public static int getID(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return -1;
        }
        return Item.getIdFromItem(itemStack.getItem());
    }

    @Deprecated//todo remove this
    public static boolean classExists(String className) {
        if (classesFound.containsKey(className)) {
            return classesFound.get(className) != null;
        }
        Class<?> found;
        try {
            found = Class.forName(className);
        } catch (ClassNotFoundException e) {
            found = null;
        }
        classesFound.put(className, found);
        return found != null;
    }

    @Deprecated//todo remove this
    public static boolean existsAndInstance(Object obj, String className) {
        Class<?> theClass;
        if (classesFound.containsKey(className)) {
            theClass = classesFound.get(className);
        } else {
            try {
                theClass = Class.forName(className);
                classesFound.put(className, theClass);
            } catch (ClassNotFoundException e) {
                classesFound.put(className, null);
                return false;
            }
        }
        return theClass != null && theClass.isInstance(obj);
    }

    public static boolean isBCWrench(Item tool) {
        return existsAndInstance(tool, "buildcraft.api.tools.IToolWrench");
    }

    public static boolean isCoFHHammer(Item tool) {
        return existsAndInstance(tool, "cofh.api.item.IToolHammer");
    }

    /**
     * Whether or not the player has a usable wrench for a block at the coordinates given.
     *
     * @param player - the player using the wrench
     * @param pos    - the coordinate of the block being wrenched
     * @return if the player can use the wrench
     * @deprecated use {@link mekanism.common.integration.wrenches.Wrenches#getHandler(ItemStack)}
     */
    @Deprecated
    public static boolean hasUsableWrench(EntityPlayer player, BlockPos pos) {
        ItemStack tool = player.inventory.getCurrentItem();
        if (tool.isEmpty()) {
            return false;
        }
        if (tool.getItem() instanceof IMekWrench wrench && wrench.canUseWrench(tool, player, pos)) {
            return true;
        }
        try {
            if (isBCWrench(tool.getItem())) { //TODO too much hassle to check BC wrench-ability
                return true;
            }
            if (isCoFHHammer(tool.getItem())) { // TODO Implement CoFH Hammer && ((IToolHammer)tool.getItem()).isUsable(tool, player, pos))
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    @Nonnull
    public static String getLastKnownUsername(UUID uuid) {
        String ret = UsernameCache.getLastKnownUsername(uuid);
        if (ret == null && !warnedFails.contains(uuid) && FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER) { // see if MC/Yggdrasil knows about it?!
            GameProfile gp = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerProfileCache().getProfileByUUID(uuid);
            if (gp != null) {
                ret = gp.getName();
            }
        }
        if (ret == null && !warnedFails.contains(uuid)) {
            Mekanism.logger.warn("Failed to retrieve username for UUID {}, you might want to add it to the JSON cache", uuid);
            warnedFails.add(uuid);
        }
        return ret != null ? ret : "<???>";
    }

    public static TileEntity getTileEntitySafe(IBlockAccess worldIn, BlockPos pos) {
        return worldIn instanceof ChunkCache chunkCache ? chunkCache.getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK) : worldIn.getTileEntity(pos);
    }

    public static <T extends TileEntity> T getTileEntitySafe(IBlockAccess worldIn, BlockPos pos, Class<T> expectedClass) {
        TileEntity te = getTileEntitySafe(worldIn, pos);
        if (expectedClass.isInstance(te)) {
            return expectedClass.cast(te);
        }
        return null;
    }

    /**
     * Gets a tile entity if the location is loaded
     *
     * @param world - world
     * @param pos   - position
     * @return tile entity if found, null if either not found or not loaded
     */
    @Nullable
    public static TileEntity getTileEntity(World world, BlockPos pos) {
        if (world != null && world.isBlockLoaded(pos)) {
            return world.getTileEntity(pos);
        }
        return null;
    }

    /**
     * Dismantles a block, dropping it and removing it from the world.
     */
    public static void dismantleBlock(Block block, IBlockState state, World world, BlockPos pos) {
        block.dropBlockAsItem(world, pos, state, 0);
        world.setBlockToAir(pos);
    }

    /**
     * @param amount   Amount currently stored
     * @param capacity Total amount that can be stored.
     * @return A redstone level based on the percentage of the amount stored.
     */
    public static int redstoneLevelFromContents(double amount, double capacity) {
        double fractionFull = capacity == 0 ? 0 : amount / capacity;
        return MathHelper.floor((float) (fractionFull * 14.0F)) + (fractionFull > 0 ? 1 : 0);
    }

    /**
     * Clamp a double to int without using Math.min due to double representation issues. Primary use: power systems that use int, where Mek uses doubles internally*
     * <code>
     * double d = 1e300; // way bigger than longs, so the long should always be what's returned by Math.min System.out.println((long)Math.min(123456781234567812L, d)); //
     * result is 123456781234567808 - 4 less than what you'd expect System.out.println((long)Math.min(123456789012345678L, d)); // result is 123456789012345680 - 2 more
     * than what you'd expect
     * </code>
     *
     * @param d double to clamp
     * @return an int clamped to Integer.MAX_VALUE
     * @see <a href="https://github.com/aidancbrady/Mekanism/pull/5203">Original PR</a>
     */
    public static int clampToInt(double d) {
        if (d < Integer.MAX_VALUE) {
            return (int) d;
        }
        return Integer.MAX_VALUE;
    }

    @Nullable
    public static <TYPE> TYPE getByIndex(TYPE[] elements, int index, @Nullable TYPE fallback) {
        return index >= 0 && index < elements.length ? elements[index] : fallback;
    }

    @Nullable
    public static <TYPE> TYPE getByIndex(List<TYPE> elements, int index, @Nullable TYPE fallback) {
        return index >= 0 && index < elements.size() ? elements.get(index) : fallback;
    }

    @Nullable
    public static UUID parseUUID(@Nullable String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static double time(IUpgradeTile tile) {
        return Math.pow(MekanismConfig.current().general.maxUpgradeMultiplier.val(), -fractionUpgrades(tile, Upgrade.SPEED));
    }

    public static double electricity(IUpgradeTile tile) {
        double speed = fractionUpgrades(tile, Upgrade.SPEED);
        double energy = fractionUpgrades(tile, Upgrade.ENERGY);
        return Math.pow(MekanismConfig.current().general.maxUpgradeMultiplier.val(), 2 * speed - Math.min(energy, Math.max(1D, speed)));
    }

    public static double capacity(IUpgradeTile tile) {
        return Math.pow(MekanismConfig.current().general.maxUpgradeMultiplier.val(), fractionUpgrades(tile, Upgrade.ENERGY));
    }

    public static String exponential(double d) {
        int significant = 4;
        int exp = (int) Math.floor(Math.log10(d));
        d = d * Math.pow(10, -exp);
        d = (double) ((int) Math.round(d * Math.pow(10, significant - 1))) / Math.pow(10, significant - 1);
        double dt = (double) ((int) Math.round(d * Math.pow(10, significant - 1))) / Math.pow(10, significant - 1 - exp);
        return Math.abs(exp) <= significant - 1 ? String.valueOf(dt) : d + "E" + exp;
    }

    public enum ResourceType {
        GUI("gui"),
        GUI_BUTTON("gui/button"),
        GUI_ICONS("gui/icons"),
        GUI_BAR("gui/bar"),
        GUI_GAUGE("gui/gauge"),
        GUI_PROGRESS("gui/progress"),
        GUI_SLOT("gui/slot"),
        GUI_TAB("gui/tabs"),
        GUI_RADIAL("gui/radial"),
        GUI_HUD("gui/hud"),
        SOUND("sound"),
        RENDER("render"),
        TEXTURE_BLOCKS("textures/blocks"),
        TEXTURE_ITEMS("textures/items"),
        MODEL("models"),
        INFUSE("infuse"),
        ARMOR("armor"),
        RENDER_MACHINE("render/machine"),
        ;


        private String prefix;

        ResourceType(String s) {
            prefix = s;
        }

        public String getPrefix() {
            return prefix + "/";
        }
    }


    public static void speedUpEffectSafely(EntityLivingBase entity, PotionEffect effectInstance) {
        if (effectInstance.getDuration() > 0) {
            int remainingDuration = effectInstance.deincrementDuration();
            if (remainingDuration == 0) {
                onChangedPotionEffect(entity, effectInstance, true);
            }
        }
    }

    public static boolean shouldSpeedUpEffect(PotionEffect effectInstance) {
        return effectInstance.isCurativeItem(MILK);
    }

    /**
     * Copy of LivingEntity#onChangedPotionEffect(EffectInstance, boolean) due to not being able to AT the method as it is protected.
     */
    private static void onChangedPotionEffect(EntityLivingBase entity, PotionEffect effectInstance, boolean reapply) {
        entity.potionsNeedUpdate = true;
        if (reapply && !entity.world.isRemote) {
            Potion effect = effectInstance.getPotion();
            effect.removeAttributesModifiersFromEntity(entity, entity.getAttributeMap(), effectInstance.getAmplifier());
            effect.applyAttributesModifiersToEntity(entity, entity.getAttributeMap(), effectInstance.getAmplifier());
        }
        if (entity instanceof EntityPlayerMP playerMP) {
            playerMP.connection.sendPacket(new SPacketEntityEffect(entity.getEntityId(), effectInstance));
            CriteriaTriggers.EFFECTS_CHANGED.trigger(playerMP);
        }
    }


    public static boolean hasChunksAt(EntityPlayer player, int pFromX, int pFromY, int pFromZ, int pToX, int pToY, int pToZ) {
        if (pToY >= 0 && pFromY < 256) {
            pFromX = pFromX >> 4;
            pFromZ = pFromZ >> 4;
            pToX = pToX >> 4;
            pToZ = pToZ >> 4;

            for (int i = pFromX; i <= pToX; ++i) {
                for (int j = pFromZ; j <= pToZ; ++j) {
                    if (!player.world.isChunkGeneratedAt(i, j)) {
                        return false;
                    }
                }
            }
            return true;
        } else {
            return false;
        }
    }

    public static class FluidInDetails {

        private final List<BlockPos> positions = new ArrayList<>();
        private final Long2DoubleMap heights = new Long2DoubleArrayMap();

        public List<BlockPos> getPositions() {
            return positions;
        }

        public double getMaxHeight() {
            return heights.values().stream().mapToDouble(value -> value).max().orElse(0);
        }
    }

    public static Map<Block, FluidInDetails> getFluidsIn(EntityPlayer player, UnaryOperator<AxisAlignedBB> modifyBoundingBox, Material material) {
        AxisAlignedBB bb = modifyBoundingBox.apply(player.getEntityBoundingBox().shrink(0.001));
        int xMin = MathHelper.floor(bb.minX);
        int xMax = MathHelper.ceil(bb.maxX);
        int yMin = MathHelper.floor(bb.minY);
        int yMax = MathHelper.ceil(bb.maxY);
        int zMin = MathHelper.floor(bb.minZ);
        int zMax = MathHelper.ceil(bb.maxZ);
        if (hasChunksAt(player, xMin, yMin, zMin, xMax, yMax, zMax)) {
            //If the position isn't actually loaded, just return there isn't any fluids
            return Collections.emptyMap();
        }
        Map<Block, FluidInDetails> fluidsIn = new HashMap<>();
        BlockPos.PooledMutableBlockPos mutablePos = BlockPos.PooledMutableBlockPos.retain();
        for (int x = xMin; x < xMax; ++x) {
            for (int y = yMin; y < yMax; ++y) {
                for (int z = zMin; z < zMax; ++z) {
                    mutablePos.setPos(x, y, z);
                    IBlockState fluidState = player.world.getBlockState(mutablePos);
                    Boolean result = fluidState.getBlock().isAABBInsideMaterial(player.world, mutablePos, new AxisAlignedBB(mutablePos), material);
                    if (result != null) {
                        if (!result) {
                            double fluidY = y + fluidState.getBlock().getBlockLiquidHeight(player.world, mutablePos, fluidState, material);
                            if (bb.minY <= fluidY) {
                                Block fluid = fluidState.getBlock();

                                if (fluid instanceof BlockDynamicLiquid) {
                                    //Almost always will be flowing fluid but check just in case
                                    // and if it is grab the source state to not have duplicates
                                    fluid = BlockLiquid.getStaticBlock(material);
                                }
                                FluidInDetails details = fluidsIn.computeIfAbsent(fluid, f -> new FluidInDetails());
                                details.positions.add(mutablePos.toImmutable());
                                double actualFluidHeight;
                                if (fluidY > bb.maxY) {
                                    //Fluid goes past the top of the bounding box, limit it to the top
                                    // We do the max of the bottom of the bounding box and our current block so that
                                    // if we are floating above the bottom we don't take the area below us into account
                                    actualFluidHeight = bb.maxY - Math.max(bb.minY, y);
                                } else {
                                    // We do the max of the bottom of the bounding box and our current block so that
                                    // if we are floating above the bottom we don't take the area below us into account
                                    actualFluidHeight = fluidY - Math.max(bb.minY, y);
                                }
                                details.heights.merge(ChunkPos.asLong(x, z), actualFluidHeight, Double::sum);
                            }
                        }
                    }
                }
            }
        }
        return fluidsIn;
    }


    @SafeVarargs
    public static EnumActionResult performActions(EnumActionResult firstAction, Supplier<EnumActionResult>... secondaryActions) {
        if (firstAction == EnumActionResult.SUCCESS) {
            return EnumActionResult.SUCCESS;
        }
        EnumActionResult result = firstAction;
        boolean hasFailed = result == EnumActionResult.FAIL;
        for (Supplier<EnumActionResult> secondaryAction : secondaryActions) {
            result = secondaryAction.get();
            if (result == EnumActionResult.SUCCESS) {
                //If we were successful
                return EnumActionResult.SUCCESS;
            }
            hasFailed &= result == EnumActionResult.FAIL;
        }
        if (hasFailed) {
            //If at least one step failed, consider ourselves unsuccessful
            return EnumActionResult.FAIL;
        }
        return EnumActionResult.PASS;
    }


    public static void veinMineArea(IEnergyContainer energyContainer, double energyRequired, World world, BlockPos pos, EntityPlayerMP player, ItemStack stack, Item usedTool,
                                    Object2IntMap<BlockPos> found, BlastEnergyFunction blastEnergy, VeinEnergyFunction veinEnergy) {
        double energyUsed = 0;
        double energyAvailable = energyContainer.getEnergy();
        //Subtract from our available energy the amount that we will require to break the target block
        energyAvailable = energyAvailable - (energyRequired);
        for (Object2IntMap.Entry<BlockPos> foundEntry : found.object2IntEntrySet()) {
            BlockPos foundPos = foundEntry.getKey();
            if (pos.equals(foundPos)) {
                continue;
            }
            IBlockState targetState = world.getBlockState(foundPos);
            if (targetState.getBlock().isAir(targetState, world, pos)) {
                continue;
            }
            float hardness = targetState.getBlockHardness(world, foundPos);
            if (hardness == -1) {
                continue;
            }
            int distance = foundEntry.getIntValue();
            double destroyEnergy = distance == 0 ? blastEnergy.calc(hardness) : veinEnergy.calc(hardness, distance, targetState);
            if (energyUsed + (destroyEnergy) > (energyAvailable)) {
                //If we don't have energy to break the block continue
                //Note: We do not break as given the energy scales with hardness, so it is possible we still have energy to break another block
                // Given we validate the blocks are the same but their block states may be different thus making them have different
                // block hardness values in a modded context
                continue;
            }
            int exp = ForgeHooks.onBlockBreakEvent(world, player.interactionManager.getGameType(), player, foundPos);
            if (exp == -1) {
                //If we can't actually break the block continue (this allows mods to stop us from vein mining into protected land)
                continue;
            }
            //Otherwise, break the block
            Block block = targetState.getBlock();
            //Get the tile now so that we have it for when we try to harvest the block
            //Get the tile now so that we have it for when we try to harvest the block
            TileEntity tileEntity = WorldUtils.getTileEntity(world, foundPos);
            //Remove the block
            if (targetState.getBlock().removedByPlayer(targetState, world, foundPos, player, true)) {
                block.onPlayerDestroy(world, foundPos, targetState);
                //Harvest the block allowing it to handle block drops, incrementing block mined count, and adding exhaustion
                block.harvestBlock(world, player, foundPos, targetState, tileEntity, stack);
                player.addStat(StatList.getObjectUseStats(usedTool));
                if (exp > 0) {
                    //If we have xp drop it
                    block.dropXpOnBlockBreak(world, foundPos, exp);
                }
                //Mark that we used that portion of the energy
                energyUsed = energyUsed + (destroyEnergy);
            }
        }
        energyContainer.extract(energyUsed, Action.EXECUTE, AutomationType.MANUAL);
    }




    @FunctionalInterface
    public interface BlastEnergyFunction {

        double calc(float hardness);
    }

    @FunctionalInterface
    public interface VeinEnergyFunction {
        double calc(float hardness, int distance, IBlockState state);
    }




}
