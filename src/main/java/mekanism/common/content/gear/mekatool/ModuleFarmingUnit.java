package mekanism.common.content.gear.mekatool;

import mekanism.api.energy.IEnergyContainer;
import mekanism.api.gear.ICustomModule;
import mekanism.api.gear.IModule;
import mekanism.api.gear.config.IModuleConfigItem;
import mekanism.api.gear.config.ModuleConfigItemCreator;
import mekanism.api.gear.config.ModuleEnumData;
import mekanism.api.text.IHasTextComponent;
import mekanism.api.text.TextComponentGroup;
import mekanism.common.MekanismLang;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDirt;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class ModuleFarmingUnit implements ICustomModule<ModuleFarmingUnit> {

    private IModuleConfigItem<FarmingRadius> farmingRadius;

    @Override
    public void init(IModule<ModuleFarmingUnit> module, ModuleConfigItemCreator configItemCreator) {
        farmingRadius = configItemCreator.createConfigItem("farming_radius", MekanismLang.MODULE_FARMING_RADIUS,
                new ModuleEnumData<>(FarmingRadius.LOW, module.getInstalledCount() + 1));
    }

    @Nonnull
    @Override
    public EnumActionResult onItemUse(IModule<ModuleFarmingUnit> module, EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        Block block = world.getBlockState(pos).getBlock();
        return MekanismUtils.performActions(
                //First try to use the disassembler as an axe
                stripLogsAOE(player, module),
                () -> {
                    if (block == Blocks.GRASS) {
                        return useItemAs(player, world, pos, side, module, farmingRadius.get().getRadius(), this::useShovel, MekanismConfig.current().meka.mekaToolEnergyUsageShovel.val());
                    }
                    return EnumActionResult.PASS;
                },
                () -> {
                    if (block == Blocks.DIRT || block == Blocks.GRASS_PATH) {
                        return useItemAs(player, world, pos, side, module, farmingRadius.get().getRadius(), this::useHoe, MekanismConfig.current().meka.mekaToolEnergyUsageHoe.val());
                    }
                    return EnumActionResult.PASS;
                }
        );
    }

    public enum FarmingRadius implements IHasTextComponent {
        OFF(0),
        LOW(1),
        MED(3),
        HIGH(5),
        ULTRA(7);

        private final int radius;
        private final ITextComponent label;

        FarmingRadius(int radius) {
            this.radius = radius;
            this.label = new TextComponentGroup().getString(Integer.toString(radius));
        }

        @Override
        public ITextComponent getTextComponent() {
            return label;
        }

        public int getRadius() {
            return radius;
        }
    }

    private EnumActionResult stripLogsAOE(EntityPlayer player, IModule<ModuleFarmingUnit> module) {
        if (player == null || player.isSneaking()) {
            //Skip if we don't have a player, or they are sneaking
            return EnumActionResult.PASS;
        }
        int diameter = farmingRadius.get().getRadius();
        if (diameter == 0) {
            //If we don't have any blocks we are going to want to do, then skip it
            return EnumActionResult.PASS;
        }
        IEnergyContainer energyContainer = module.getEnergyContainer();
        if (energyContainer == null) {
            return EnumActionResult.FAIL;
        }
        return EnumActionResult.SUCCESS;
    }


    private EnumActionResult useShovel(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumFacing facing) {
        if (!player.canPlayerEdit(pos.offset(facing), facing, stack)) {
            return EnumActionResult.FAIL;
        } else if (facing != EnumFacing.DOWN && world.isAirBlock(pos.up())) {
            Block block = world.getBlockState(pos).getBlock();
            if (block == Blocks.GRASS) {
                setBlock(stack, player, SoundEvents.ITEM_SHOVEL_FLATTEN, world, pos, Blocks.GRASS_PATH.getDefaultState());
                return EnumActionResult.SUCCESS;
            }
        }
        return EnumActionResult.PASS;
    }

    private EnumActionResult useItemAs(EntityPlayer player, World world, BlockPos pos, EnumFacing side, IModule<ModuleFarmingUnit> module, int diameter, ItemUseConsumer consumer, double energyUsage) {
        double energy = module.getContainerEnergy();
        if (energy < energyUsage || consumer.use(module.getContainer(), player, world, pos, side) == EnumActionResult.FAIL) {
            //Fail if we don't have enough energy or using the item failed
            return EnumActionResult.FAIL;
        }
        double energyUsed = energyUsage;
        int radius = (diameter - 1) / 2;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (energyUsed + energyUsage > energy) {
                    break;
                } else if ((x != 0 || z != 0) && consumer.use(module.getContainer(), player, world, pos.add(x, 0, z), side) == EnumActionResult.SUCCESS) {
                    //Don't attempt to use it on the source location as it was already done above
                    // If we successfully used it in a spot increment how much energy we used
                    energyUsed += energyUsage;
                }
            }
        }
        module.useEnergy(player, energyUsed);
        return EnumActionResult.SUCCESS;
    }

    private EnumActionResult useHoe(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumFacing facing) {
        if (!player.canPlayerEdit(pos.offset(facing), facing, stack)) {
            return EnumActionResult.FAIL;
        }
        int hook = ForgeEventFactory.onHoeUse(stack, player, world, pos);
        if (hook != 0) {
            return hook > 0 ? EnumActionResult.SUCCESS : EnumActionResult.FAIL;
        }
        if (facing != EnumFacing.DOWN && world.isAirBlock(pos.up())) {
            IBlockState state = world.getBlockState(pos);
            Block block = state.getBlock();
            IBlockState newState = null;
            if (block == Blocks.GRASS || block == Blocks.GRASS_PATH) {
                newState = Blocks.FARMLAND.getDefaultState();
            } else if (block == Blocks.DIRT) {
                BlockDirt.DirtType type = state.getValue(BlockDirt.VARIANT);
                if (type == BlockDirt.DirtType.DIRT) {
                    newState = Blocks.FARMLAND.getDefaultState();
                } else if (type == BlockDirt.DirtType.COARSE_DIRT) {
                    newState = Blocks.DIRT.getDefaultState().withProperty(BlockDirt.VARIANT, BlockDirt.DirtType.DIRT);
                }
            }
            if (newState != null) {
                setBlock(stack, player, SoundEvents.ITEM_HOE_TILL, world, pos, newState);
                return EnumActionResult.SUCCESS;
            }
        }
        return EnumActionResult.PASS;
    }

    private void setBlock(ItemStack stack, EntityPlayer player, SoundEvent soundEvent, World worldIn, BlockPos pos, IBlockState state) {
        worldIn.playSound(player, pos, soundEvent, SoundCategory.BLOCKS, 1.0F, 1.0F);
        if (!worldIn.isRemote) {
            worldIn.setBlockState(pos, state, 11);
            stack.damageItem(1, player);
        }
    }

    @FunctionalInterface
    interface ItemUseConsumer {
        //Used to reference useHoe and useShovel via lambda references
        EnumActionResult use(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumFacing facing);
    }


}
