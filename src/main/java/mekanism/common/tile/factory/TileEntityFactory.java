package mekanism.common.tile.factory;

import io.netty.buffer.ByteBuf;
import mekanism.api.IConfigCardAccess.ISpecialConfigData;
import mekanism.api.TileNetworkList;
import mekanism.api.gas.*;
import mekanism.api.infuse.InfuseObject;
import mekanism.api.infuse.InfuseRegistry;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.*;
import mekanism.common.base.*;
import mekanism.common.base.IFactory.MachineFuelType;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.computer.IComputerIntegration;
import mekanism.common.item.ItemBlockMachine;
import mekanism.common.recipe.GasConversionHandler;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.*;
import mekanism.common.recipe.machines.*;
import mekanism.common.recipe.outputs.ChanceOutput;
import mekanism.common.recipe.outputs.ChanceOutput2;
import mekanism.common.recipe.outputs.ItemStackOutput;
import mekanism.common.recipe.outputs.PressurizedOutput;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.FactoryTier;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityMachine;
import mekanism.common.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static mekanism.common.tile.machine.TileEntityChemicalDissolutionChamber.BASE_INJECT_USAGE;
import static mekanism.common.tile.prefab.TileEntityAdvancedElectricMachine.ceilSecondaryEnergyPerTick;

//TODO:过于重复，待更改
public class TileEntityFactory extends TileEntityMachine implements IComputerIntegration, ISideConfiguration, IGasHandler, ISpecialConfigData, ITierUpgradeable,
        ISustainedData, IComparatorSupport, ITankManager, IFluidHandlerWrapper, IMachineSlotTip {
    private static final String[] methods = new String[]{"getEnergy", "getProgress", "facing", "canOperate", "getMaxEnergy", "getEnergyNeeded"};
    /**
     * How long it takes this factory to switch recipe types.
     */
    private static final int RECIPE_TICKS_REQUIRED = 40;
    public static int BASE_MAX_TANK = 10000;
    /**
     * The amount of infuse this machine has stored.
     */
    public final InfuseStorage infuseStored = new InfuseStorage();
    private final MachineRecipe<?, ?, ?>[] cachedRecipe;
    private final FactoryInvSorter inventorySorter = new FactoryInvSorter(this);
    public GasTank gasTank;
    public GasTank gasOutTank;
    public FluidTank fluidTank;
    /**
     * This Factory's tier.
     */
    public FactoryTier tier;
    /**
     * An int[] used to track all current operations' progress.
     */
    public int[] progress;
    public int BASE_MAX_INFUSE = 1000;
    public int maxInfuse;
    /**
     * How many ticks it takes, by default, to run an operation.
     */
    public int BASE_TICKS_REQUIRED;
    /**
     * How many ticks it takes, with upgrades, to run an operation
     */
    public int ticksRequired = 200;
    public boolean sorting;
    public boolean upgraded;
    public double lastUsage;
    public TileComponentEjector ejectorComponent;
    public TileComponentConfig configComponent;
    public boolean Factoryoldsorting;
    /**
     * This machine's recipe type.
     */

    public int delayTicks;
    private boolean machineUsesItem;
    private boolean isMachineUsesItem = true;
    private boolean machineUsesGAS;
    private boolean isMachineUsesGAS = true;
    private boolean machineUsesFluid;
    private boolean isMachineUsesFluid = true;
    /**
     * How much secondary energy each operation consumes per tick
     */
    private double secondaryEnergyPerTick = 0;
    private int secondaryEnergyThisTick;
    /**
     * How many recipe ticks have progressed.
     */
    private int recipeTicks;


    protected int successCounter = 0;
    protected boolean inventoryChanged = false;

    @Nonnull
    private RecipeType recipeType = RecipeType.SMELTING;

    public TileEntityFactory() {
        this(FactoryTier.BASIC, MachineType.BASIC_FACTORY, 0);
    }

    /**
     * @param i 这个 i 只是用来区别另外的一个,并没有什么作用
     */
    public TileEntityFactory(FactoryTier type, MachineType machine, int i) {
        this(type, machine);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.ENERGY, TransmissionType.GAS, TransmissionType.FLUID);

        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.NONE, InventoryUtils.EMPTY));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.INPUT, getSlotsWithTier(tier)));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.OUTPUT, getOutputSlotsWithTier(tier)));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.ENERGY, new int[]{1}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.EXTRA, new int[]{4}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.INPUT_EXTRA, getInputExtraSlotsWithTier(tier)));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(getInputOutputSlotsWithTier(tier), getInputOutputSlotsEnableWithTier(tier)));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.OUTPUT_ENHANCED, getOutputSlotsWithTier(tier)));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.INPUT_OUTPUT_ENHANCED, getInputOutputSlotsWithTier(tier), getInputOutputSlotsEnableWithTier(tier)));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.INPUT_ENHANCED, getSlotsWithTier(tier)));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.INPUT_ENHANCED_OUTPUT_ENHANCED, getInputOutputSlotsWithTier(tier), getInputOutputSlotsEnableWithTier(tier)));
        configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.INPUT_EXTRA_OUTPUT, getInputExtraOutputSlotsWithTier(tier), getInputExtraOutputSlotsEnableWithTier(tier)));
        configComponent.setConfig(TransmissionType.ITEM, new byte[]{4, 1, 1, 3, 1, 2});

        configComponent.setInputConfig(TransmissionType.FLUID);

        configComponent.addOutput(TransmissionType.GAS, new SideData(DataType.NONE, InventoryUtils.EMPTY));
        configComponent.addOutput(TransmissionType.GAS, new SideData(DataType.INPUT, new int[]{1}));
        configComponent.addOutput(TransmissionType.GAS, new SideData(DataType.OUTPUT, new int[]{2}));
        configComponent.addOutput(TransmissionType.GAS, new SideData(new int[]{1, 2}, new boolean[]{false, true}));
        configComponent.setConfig(TransmissionType.GAS, new byte[]{1, 1, 1, 1, 1, 2});

        configComponent.setInputConfig(TransmissionType.ENERGY);

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(TransmissionType.ITEM, configComponent.getOutputs(TransmissionType.ITEM).get(2));
        ejectorComponent.setInputOutputData(TransmissionType.ITEM, configComponent.getOutputs(TransmissionType.ITEM).get(6));
        ejectorComponent.setInputExtraOutputData(TransmissionType.ITEM, configComponent.getOutputs(TransmissionType.ITEM).get(11));

        ejectorComponent.setOutputData(TransmissionType.GAS, configComponent.getOutputs(TransmissionType.GAS).get(2));
        ejectorComponent.setInputOutputData(TransmissionType.GAS, configComponent.getOutputs(TransmissionType.GAS).get(3));
    }

    private TileEntityFactory(FactoryTier type, MachineType machine) {
        super("null", machine, 0);
        tier = type;
        inventory = NonNullListSynchronized.withSize(5 + type.processes * 3, ItemStack.EMPTY);
        progress = new int[type.processes];
        isActive = false;
        cachedRecipe = new MachineRecipe[tier.processes];
        gasTank = new GasTank(tier == FactoryTier.CREATIVE ? Integer.MAX_VALUE : BASE_MAX_TANK * tier.processes);
        gasOutTank = new GasTank(tier == FactoryTier.CREATIVE ? Integer.MAX_VALUE : BASE_MAX_TANK * tier.processes);
        fluidTank = new FluidTankSync(tier == FactoryTier.CREATIVE ? Integer.MAX_VALUE : BASE_MAX_TANK * tier.processes);
        maxInfuse = tier != FactoryTier.CREATIVE ? BASE_MAX_INFUSE * tier.processes : Integer.MAX_VALUE;
        BASE_TICKS_REQUIRED = tier != FactoryTier.CREATIVE ? 200 : 1;
        if (tier == FactoryTier.CREATIVE) {
            maxEnergy = Double.MAX_VALUE;
        }
        setRecipeType(recipeType);
    }

    public static ItemStack getRecipeInput(MachineRecipe<?, ?, ?> recipe) {
        if (recipe.recipeInput instanceof ItemStackInput input) {
            return input.ingredient;
        } else if (recipe.recipeInput instanceof AdvancedMachineInput advancedInput) {
            return advancedInput.itemStack;
        } else if (recipe.recipeInput instanceof DoubleMachineInput doubleMachineInput) {
            return doubleMachineInput.itemStack;
        } else if (recipe.recipeInput instanceof InfusionInput infusionInput) {
            return infusionInput.inputStack;
        } else if (recipe.recipeInput instanceof PressurizedInput pressurizedInput) {
            return pressurizedInput.getSolid();
        } else if (recipe.recipeInput instanceof NucleosynthesizerInput input) {
            return input.getSolid();
        } else {
            return ItemStack.EMPTY;
        }
    }


    public static int[] getSlotsWithTier(FactoryTier tier) {
        return switch (tier) {
            case BASIC -> new int[]{5, 6, 7};
            case ADVANCED -> new int[]{5, 6, 7, 8, 9};
            case ELITE -> new int[]{5, 6, 7, 8, 9, 10, 11};
            case ULTIMATE -> new int[]{5, 6, 7, 8, 9, 10, 11, 12, 13};
            case CREATIVE -> new int[]{5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        };
    }

    public static int[] getInputExtraSlotsWithTier(FactoryTier tier) {
        return switch (tier) {
            case BASIC -> new int[]{4, 5, 6, 7};
            case ADVANCED -> new int[]{4, 5, 6, 7, 8, 9};
            case ELITE -> new int[]{4, 5, 6, 7, 8, 9, 10, 11};
            case ULTIMATE -> new int[]{4, 5, 6, 7, 8, 9, 10, 11, 12, 13};
            case CREATIVE -> new int[]{4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        };
    }

    public static int[] getInputOutputSlotsWithTier(FactoryTier tier) {
        return switch (tier) {
            case BASIC -> new int[]{5, 6, 7, 8, 9, 10, 11, 12, 13};
            case ADVANCED -> new int[]{5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19};
            case ELITE -> new int[]{5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25};
            case ULTIMATE ->
                    new int[]{5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31};
            case CREATIVE ->
                    new int[]{5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37};
        };
    }

    public static boolean[] getInputOutputSlotsEnableWithTier(FactoryTier tier) {
        return switch (tier) {
            case BASIC -> new boolean[]{false, false, false, true, true, true, true, true, true};
            case ADVANCED ->
                    new boolean[]{false, false, false, false, false, true, true, true, true, true, true, true, true, true, true};
            case ELITE ->
                    new boolean[]{false, false, false, false, false, false, false, true, true, true, true, true, true, true, true, true, true, true, true, true, true};
            case ULTIMATE ->
                    new boolean[]{false, false, false, false, false, false, false, false, false, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true};
            case CREATIVE ->
                    new boolean[]{false, false, false, false, false, false, false, false, false, false, false, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true};
        };
    }

    public static int[] getInputExtraOutputSlotsWithTier(FactoryTier tier) {
        return switch (tier) {
            case BASIC -> new int[]{4, 5, 6, 7, 8, 9, 10, 11, 12, 13};
            case ADVANCED -> new int[]{4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19};
            case ELITE -> new int[]{4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25};
            case ULTIMATE ->
                    new int[]{4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31};
            case CREATIVE ->
                    new int[]{4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37};
        };
    }

    public static boolean[] getInputExtraOutputSlotsEnableWithTier(FactoryTier tier) {
        return switch (tier) {
            case BASIC -> new boolean[]{false, false, false, false, true, true, true, true, true, true};
            case ADVANCED ->
                    new boolean[]{false, false, false, false, false, false, true, true, true, true, true, true, true, true, true, true};
            case ELITE ->
                    new boolean[]{false, false, false, false, false, false, false, false, true, true, true, true, true, true, true, true, true, true, true, true, true, true};
            case ULTIMATE ->
                    new boolean[]{false, false, false, false, false, false, false, false, false, false, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true};
            case CREATIVE ->
                    new boolean[]{false, false, false, false, false, false, false, false, false, false, false, false, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true};
        };
    }

    public static int[] getOutputSlotsWithTier(FactoryTier tier) {
        return switch (tier) {
            case BASIC -> new int[]{8, 9, 10, 11, 12, 13};
            case ADVANCED -> new int[]{10, 11, 12, 13, 14, 15, 16, 17, 18, 19};
            case ELITE -> new int[]{12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25};
            case ULTIMATE -> new int[]{14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31};
            case CREATIVE ->
                    new int[]{16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37};
        };
    }

    public static int[] getSecondaryOutputSlotsWithTier(FactoryTier tier) {
        return switch (tier) {
            case BASIC -> new int[]{11, 12, 13};
            case ADVANCED -> new int[]{15, 16, 17, 18, 19};
            case ELITE -> new int[]{19, 20, 21, 22, 23, 24, 25};
            case ULTIMATE -> new int[]{23, 24, 25, 26, 27, 28, 29, 30, 31};
            case CREATIVE -> new int[]{27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37};
        };
    }


    public static ItemStack copyStackWithSize(ItemStack stack, int amount) {
        if (stack.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        ItemStack s = stack.copy();
        s.setCount(amount);
        return s;
    }


    public static boolean matchStacks(@Nonnull ItemStack stack, @Nonnull ItemStack other) {
        if (!ItemStack.areItemsEqual(stack, other)) return false;
        return ItemStack.areItemStackTagsEqual(stack, other);
    }


    public boolean isUpgrade = true;

    @Override
    public boolean upgrade(BaseTier upgradeTier) {
        IBlockState targetState;
        if (tier == FactoryTier.ELITE || tier == FactoryTier.ULTIMATE) {
            if (upgradeTier.ordinal() != tier.ordinal() + 1) {
                return false;
            }
            targetState = MekanismBlocks.MachineBlock3.getStateFromMeta(4 + tier.ordinal() + 1);
        } else if (tier == FactoryTier.BASIC || tier == FactoryTier.ADVANCED) {
            if (upgradeTier.ordinal() != tier.ordinal() + 1) {
                return false;
            }
            targetState = MekanismBlocks.MachineBlock.getStateFromMeta(5 + tier.ordinal() + 1);
        } else return false;
        isUpgrade = false;
        world.setBlockToAir(getPos());
        world.setBlockState(getPos(), targetState, 3);

        TileEntity tile = world.getTileEntity(getPos());
        if (!(tile instanceof TileEntityFactory)) {
            MachineType targetType = MachineType.get(targetState);
            if (targetType != null) {
                TileEntity created = targetType.create();
                if (created instanceof TileEntityFactory) {
                    world.setTileEntity(getPos(), created);
                    tile = world.getTileEntity(getPos());
                }
            }
        }

        if (tile instanceof TileEntityFactory factory) {
            //Basic
            factory.facing = facing;
            factory.clientFacing = clientFacing;
            factory.ticker = ticker;
            factory.redstone = redstone;
            factory.redstoneLastTick = redstoneLastTick;
            factory.doAutoSync = doAutoSync;

            //Electric
            factory.electricityStored.set(electricityStored.get());

            //Factory
            System.arraycopy(progress, 0, factory.progress, 0, tier.processes);

            factory.recipeTicks = recipeTicks;
            factory.isActive = isActive;
            factory.prevEnergy = prevEnergy;
            factory.gasTank.setGas(gasTank.getGas());
            factory.gasOutTank.setGas(gasOutTank.getGas());
            factory.fluidTank.setFluid(fluidTank.getFluid());
            factory.sorting = sorting;
            factory.Factoryoldsorting = Factoryoldsorting;
            factory.setControlType(getControlType());
            factory.upgradeComponent.readFrom(upgradeComponent);
            factory.ejectorComponent.readFrom(ejectorComponent);
            factory.configComponent.readFrom(configComponent);
            factory.ejectorComponent.setOutputData(TransmissionType.ITEM, factory.configComponent.getOutputs(TransmissionType.ITEM).get(2));
            factory.ejectorComponent.setInputOutputData(TransmissionType.ITEM, factory.configComponent.getOutputs(TransmissionType.ITEM).get(6));
            factory.ejectorComponent.setOutputData(TransmissionType.GAS, factory.configComponent.getOutputs(TransmissionType.GAS).get(2));
            factory.setRecipeType(recipeType);
            factory.upgradeComponent.setSupported(Upgrade.GAS, recipeType.fuelEnergyUpgrades());
            factory.securityComponent.readFrom(securityComponent);
            factory.infuseStored.copyFrom(infuseStored);

            for (int i = 0; i < tier.processes + 5; i++) {
                factory.inventory.set(i, inventory.get(i));
            }

            for (int i = 0; i < tier.processes; i++) {
                int output = getOutputSlot(i);
                if (!inventory.get(output).isEmpty()) {
                    int newOutput = 5 + factory.tier.processes + i;
                    factory.inventory.set(newOutput, inventory.get(output));
                }
            }

            for (int i = 0; i < tier.processes; i++) {
                int SecondaryOutput = getSecondaryOutputSlot(i);
                if (!inventory.get(SecondaryOutput).isEmpty()) {
                    int newSecondaryOutput = 5 + tier.processes * 2 + i;
                    factory.inventory.set(newSecondaryOutput, inventory.get(SecondaryOutput));
                }
            }

            factory.upgradeComponent.getSupportedTypes().forEach(factory::recalculateUpgradables);
            factory.upgraded = true;
            factory.isUpgrade = true;
            factory.markNoUpdateSync();
            Mekanism.packetHandler.sendUpdatePacket(factory);
            return true;
        }
        return false;
    }

    @Override
    public void addTileSyncTask() {
        if (!NoItemMachine()) {
            AutomaticallyExtractItems(9);
            AutomaticallyExtractItems(10);
            BetterEjectingItem();
        }
    }

    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        if (ticker == 1) {
            Mekanism.EXECUTE_MANAGER.addSyncTask(() -> world.notifyNeighborsOfStateChange(getPos(), getBlockType(), true));
        }
        ChargeUtils.discharge(1, this);
        handleSecondaryFuel();
        CheckTheFaceSettings();
        if (!NoItemInputMachine()) {
            if (Factoryoldsorting) {
                sortInventory(); //Keeping the old sort prevents some problems
            } else {
                inventorySorter.sort();
            }
        }
        MachineTypeSwitching();
        double prev = getEnergy();
        if (tier == FactoryTier.CREATIVE) {
            energyPerTick = 0;
            electricityStored.set(Double.MAX_VALUE);
        }
        if (recipeType == RecipeType.Dissolution) {
            secondaryEnergyThisTick = Math.max(BASE_INJECT_USAGE, StatUtils.inversePoisson(BASE_INJECT_USAGE));
        } else {
            secondaryEnergyThisTick = recipeType.fuelEnergyUpgrades() ? StatUtils.inversePoisson(secondaryEnergyPerTick) : ceilSecondaryEnergyPerTick(secondaryEnergyPerTick);
        }
        for (int process = 0; process < tier.processes; process++) {
            PressurizedRecipe PRCrecipe = recipeType.getPressurizedRecipe(inventory.get(getInputSlot(process)), fluidTank.getFluid(), gasTank.getGas());
            NucleosynthesizerRecipe NnRecipe = recipeType.getNucleosynthesizerRecipe(inventory.get(getInputSlot(process)), gasTank.getGas());
            double Exenery = 0;
            if (MekanismUtils.canFunction(this) && canOperate(getInputSlot(process), getOutputSlot(process), getSecondaryOutputSlot(process)) && getEnergy() >= energyPerTick && gasTank.getStored() >= secondaryEnergyThisTick) {
                if (tier != FactoryTier.CREATIVE) {
                    if (recipeType == RecipeType.PRC || recipeType == RecipeType.NUCLEOSYNTHESIZER) {
                        if ((recipeType == RecipeType.PRC && PRCrecipe == null) || (recipeType == RecipeType.NUCLEOSYNTHESIZER && NnRecipe == null)) {
                            continue;
                        }
                        Exenery = recipeType == RecipeType.PRC ? PRCrecipe.extraEnergy : NnRecipe.extraEnergy;
                        boolean update = BASE_TICKS_REQUIRED != (recipeType == RecipeType.PRC ? PRCrecipe.ticks : NnRecipe.ticks);
                        BASE_TICKS_REQUIRED = recipeType == RecipeType.PRC ? PRCrecipe.ticks : NnRecipe.ticks;
                        if (update) {
                            recalculateUpgradables(Upgrade.SPEED);
                        }
                    } else if (recipeType == RecipeType.WASHER) {
                        BASE_TICKS_REQUIRED = 1;
                    }
                }
                if ((progress[process] + 1) < ticksRequired) {
                    progress[process]++;
                    gasTank.draw(secondaryEnergyThisTick, tier != FactoryTier.CREATIVE);
                    TypeUpdate(process, Exenery);
                } else if ((progress[process] + 1) >= ticksRequired && ((recipeType == RecipeType.PRC || recipeType == RecipeType.NUCLEOSYNTHESIZER) ? getEnergy() >= MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK + Exenery) : getEnergy() >= energyPerTick)) {
                    if (MekanismConfig.current().mekce.EnableUpgradeConfigure.val() && ticksRequired <= 0) {
                        int operations = 1 - ticksRequired;
                        for (int i = 0; i < operations; i++) {
                            if (!canOperate(getInputSlot(process), getOutputSlot(process), getSecondaryOutputSlot(process))) {
                                break;
                            }
                            if (!(((recipeType == RecipeType.PRC || recipeType == RecipeType.NUCLEOSYNTHESIZER) ? getEnergy() >= MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK + Exenery) : getEnergy() >= energyPerTick))) {
                                break;
                            }
                            operate(getInputSlot(process), getOutputSlot(process), getSecondaryOutputSlot(process));
                            TypeUpdate(process, Exenery);
                        }
                    } else {
                        operate(getInputSlot(process), getOutputSlot(process), getSecondaryOutputSlot(process));
                        TypeUpdate(process, Exenery);
                    }
                    progress[process] = 0;

                }
            } else {
                if (tier != FactoryTier.CREATIVE) {
                    if (recipeType == RecipeType.PRC || recipeType == RecipeType.NUCLEOSYNTHESIZER) {
                        BASE_TICKS_REQUIRED = 200;
                    }
                }
            }
            if (!canOperate(getInputSlot(process), getOutputSlot(process), getSecondaryOutputSlot(process))) {
                if (!(GasAdvancedInputMachine() && recipeType.hasRecipe(inventory.get(getInputSlot(process))))) {
                    progress[process] = 0;
                }
            }
        }
        boolean hasOperation = false;
        for (int i = 0; i < tier.processes; i++) {
            if (canOperate(getInputSlot(i), getOutputSlot(i), getSecondaryOutputSlot(i))) {
                hasOperation = true;
                break;
            }
        }
        if (MekanismUtils.canFunction(this) && hasOperation && getEnergy() >= energyPerTick && gasTank.getStored() >= secondaryEnergyThisTick) {
            setActive(true);
        } else if (prevEnergy >= getEnergy()) {
            setActive(false);
        }
        lastUsage = prev - getEnergy();
        prevEnergy = getEnergy();
    }

    private void MachineTypeSwitching() {
        ItemStack machineSwapItem = inventory.get(2);
        if (!machineSwapItem.isEmpty() && machineSwapItem.getItem() instanceof ItemBlockMachine && inventory.get(3).isEmpty()) {
            MachineType swapType = MachineType.get(machineSwapItem);
            if (swapType != null && !swapType.isFactory()) {
                RecipeType toSet = RecipeType.getFromMachineType(swapType);
                if (toSet != null && recipeType != toSet) {
                    if (recipeTicks < RECIPE_TICKS_REQUIRED) {
                        recipeTicks++;
                    } else {
                        recipeTicks = 0;
                        ItemStack returnStack = getMachineStack();
                        upgradeComponent.write(ItemDataUtils.getDataMap(returnStack));
                        upgradeComponent.setSupported(Upgrade.GAS, toSet.fuelEnergyUpgrades());
                        upgradeComponent.read(ItemDataUtils.getDataMapIfPresentNN(machineSwapItem));
                        inventory.set(2, ItemStack.EMPTY);
                        inventory.set(3, returnStack);
                        setRecipeType(toSet);
                        gasTank.setGas(null);
                        gasOutTank.setGas(null);
                        fluidTank.setFluid(null);
                        secondaryEnergyPerTick = getSecondaryEnergyPerTick(recipeType);
                        Mekanism.EXECUTE_MANAGER.addSyncTask(() -> world.notifyNeighborsOfStateChange(getPos(), getBlockType(), true));
                        MekanismUtils.saveChunk(this);
                    }
                } else {
                    recipeTicks = 0;
                }
            }
        } else {
            recipeTicks = 0;
        }
    }

    private void TypeUpdate(int process, double Exenery) {
        if (tier != FactoryTier.CREATIVE) {
            if (recipeType == RecipeType.PRC || recipeType == RecipeType.NUCLEOSYNTHESIZER) {
                electricityStored.addAndGet(-MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK + Exenery));
            } else {
                electricityStored.addAndGet(-energyPerTick);
            }
        } else {
            inventory.get(getOutputSlot(process)).setCount(inventory.get(getOutputSlot(process)).getMaxStackSize());
            if (OuputItemSecondaryMachine()) {
                inventory.get(getSecondaryOutputSlot(process)).setCount(inventory.get(getSecondaryOutputSlot(process)).getMaxStackSize());
            }
        }
    }


    @Nonnull
    public RecipeType getRecipeType() {
        return recipeType;
    }

    public void setRecipeType(@Nonnull RecipeType type) {
        recipeType = Objects.requireNonNull(type);
        BASE_MAX_ENERGY = maxEnergy = tier == FactoryTier.CREATIVE ? Double.MAX_VALUE : tier.processes * Math.max(((recipeType == RecipeType.PRC || recipeType == RecipeType.NUCLEOSYNTHESIZER) ? 1D : 0.5D) * recipeType.getEnergyStorage(), recipeType.getEnergyUsage());
        BASE_ENERGY_PER_TICK = energyPerTick = tier == FactoryTier.CREATIVE ? 0 : recipeType.getEnergyUsage();
        upgradeComponent.setSupported(Upgrade.GAS, recipeType.fuelEnergyUpgrades());
        secondaryEnergyPerTick = getSecondaryEnergyPerTick(recipeType);
        upgradeComponent.getSupportedTypes().forEach(this::recalculateUpgradables);
        if (hasWorld() && isRemote()) {
            setSoundEvent(type.getSound());
        }
    }

    @Override
    public boolean sideIsConsumer(EnumFacing side) {
        return configComponent.hasSideForData(TransmissionType.ENERGY, facing, 1, side);
    }

    /**
     * If the factory does not have an input machine
     */
    //TODO
    public boolean NoItemInputMachine() {
        return !recipeType.getCanInputItem();
    }

    public boolean NoItemMachine() {
        return !recipeType.getCanInputItem() && !recipeType.getCanOuputItem();
    }

    public boolean OuputItemMachine() {
        return recipeType.getCanOuputItem();
    }

    public boolean OuputItemSecondaryMachine() {
        return recipeType.getFuelType() == MachineFuelType.CHANCE || recipeType.getFuelType() == MachineFuelType.FARM;
    }


    public boolean GasOutputMachine() {
        return recipeType.getCanOuputGas();
    }

    public boolean GasInputMachine() {
        return recipeType.getCanInputGas();
    }

    public boolean GasAdvancedInputMachine() {
        return recipeType.getFuelType() == MachineFuelType.FARM || recipeType.getFuelType() == MachineFuelType.ADVANCED || recipeType == RecipeType.NUCLEOSYNTHESIZER;
    }


    public boolean GasMachine() {
        return recipeType.supportsGas();
    }

    public boolean inputFluidMachine() {
        return recipeType.getCanInputFluid();
    }

    /**
     * Checks if the cached recipe (or recipe for current factory if the cache is out of date) can produce a specific output.
     *
     * @param slotID        Slot ID to grab the cached recipe of.
     * @param fallbackInput Used if the cached recipe is null or to validate the cached recipe is not out of date.
     * @param output        The output we want.
     * @param updateCache   True to make the cached recipe get updated if it is out of date.
     * @return True if the recipe produces the given output.
     */
    public boolean inputProducesOutput(int slotID, ItemStack fallbackInput, ItemStack output, boolean updateCache) {
        if (output.isEmpty()) {
            return true;
        }
        int process = getOperation(slotID);
        //cached recipe may be invalid
        MachineRecipe<?, ?, ?> cached = cachedRecipe[process];
        ItemStack extra = inventory.get(4);
        if (cached == null) {
            cached = recipeType.getAnyRecipe(fallbackInput, extra, gasTank.getGasType(), infuseStored, gasTank.getGas(), fluidTank.getFluid());
            if (updateCache) {
                cachedRecipe[process] = cached;
            }
        } else {
            ItemStack recipeInput = ItemStack.EMPTY;
            boolean secondaryMatch = true;
            boolean secondaryMatch2 = true;
            if (cached.recipeInput instanceof ItemStackInput input) {
                recipeInput = input.ingredient;
            } else if (cached.recipeInput instanceof AdvancedMachineInput advancedInput) {
                recipeInput = advancedInput.itemStack;
                secondaryMatch = gasTank.getGasType() == null || advancedInput.gasType == gasTank.getGasType();
            } else if (cached.recipeInput instanceof DoubleMachineInput doubleMachineInput) {
                recipeInput = doubleMachineInput.itemStack;
                secondaryMatch = extra.isEmpty() || ItemStack.areItemsEqual(doubleMachineInput.extraStack, extra);
            } else if (cached.recipeInput instanceof PressurizedInput pressurizedInput) {
                recipeInput = pressurizedInput.getSolid();
                secondaryMatch = gasTank.getGas() == null || gasTank.getGas().isGasEqual(pressurizedInput.getGas());
                secondaryMatch2 = fluidTank.getFluid() == null || fluidTank.getFluid() == pressurizedInput.getFluid();
            } else if (cached.recipeInput instanceof InfusionInput infusionInput) {
                recipeInput = infusionInput.inputStack;
                secondaryMatch = infuseStored.getAmount() == 0 || infuseStored.getType() == infusionInput.infuse.getType();
            } else if (cached.recipeInput instanceof GasInput gasInput) {
                secondaryMatch = gasTank.getGasType() == null || gasInput.ingredient.getGas() == gasTank.getGasType();
            } else if (cached.recipeInput instanceof NucleosynthesizerInput input) {
                recipeInput = input.getSolid();
                secondaryMatch = (gasTank.getGas() == null || gasTank.getGas().isGasEqual(input.getGas()));
            }
            //If there is no cached item input or it doesn't match our fallback
            // then it is an out of date cache so we compare against the new one
            // and update the cache while we are at it
            if (recipeInput.isEmpty() || !secondaryMatch || !secondaryMatch2 || !ItemStack.areItemsEqual(recipeInput, fallbackInput)) {
                cached = recipeType.getAnyRecipe(fallbackInput, extra, gasTank.getGasType(), infuseStored, gasTank.getGas(), fluidTank.getFluid());
                if (updateCache) {
                    cachedRecipe[process] = cached;
                }
            }
        }
        //If there is no recipe found
        if (cached != null) {
            ItemStack recipeOutput = ItemStack.EMPTY;
            if (cached.recipeOutput instanceof ItemStackOutput stackOutput) {
                recipeOutput = stackOutput.output;
            } else if (cached.recipeOutput instanceof ChanceOutput2 stackOutput) {
                recipeOutput = stackOutput.primaryOutput;
            } else if (cached.recipeOutput instanceof PressurizedOutput stackOutput) {
                recipeOutput = stackOutput.getItemOutput();
            }
            if (!recipeOutput.isEmpty()) {
                return ItemStack.areItemsEqual(recipeOutput, output);
            }
        }
        return true;
    }

    public double getSecondaryEnergyPerTick(RecipeType type) {
        if (tier == FactoryTier.CREATIVE) {
            return 0;
        } else {
            if (recipeType.getFuelType() == MachineFuelType.FARM || recipeType.getFuelType() == MachineFuelType.ADVANCED) {
                return MekanismUtils.getSecondaryEnergyPerTickMean(this, type.getSecondaryEnergyPerTick());
            } else if (recipeType == RecipeType.Dissolution) {
                return MekanismUtils.getSecondaryEnergyPerTickMean(this, 1);
            } else {
                return 0;
            }
        }
    }

    @Nullable
    public GasStack getItemGas(ItemStack itemStack) {
        return GasConversionHandler.getItemGas(itemStack, gasTank, recipeType::isValidGas);
    }

    public void handleSecondaryFuel() {
        ItemStack extra = inventory.get(4);
        if (!extra.isEmpty()) {
            if (GasInputMachine() && gasTank.getNeeded() > 0) {
                GasStack gasStack = getItemGas(extra);
                if (gasStack != null) {
                    Gas gas = gasStack.getGas();
                    if (gasTank.canReceive(gas) && gasTank.getNeeded() >= gasStack.amount) {
                        if (extra.getItem() instanceof IGasItem item) {
                            if (tier != FactoryTier.CREATIVE) {
                                gasTank.receive(item.removeGas(extra, gasStack.amount), true);
                            } else {
                                gasTank.setGas(gasStack);
                                gasTank.setMaxGas(Integer.MAX_VALUE);
                                gasTank.stored.amount = gasTank.getMaxGas();
                            }
                        } else {
                            if (tier != FactoryTier.CREATIVE) {
                                gasTank.receive(gasStack, true);
                                extra.shrink(1);
                            } else {
                                gasTank.setGas(gasStack);
                                gasTank.setMaxGas(Integer.MAX_VALUE);
                                gasTank.stored.amount = gasTank.getMaxGas();

                            }
                        }
                    }
                }
            } else if (recipeType == RecipeType.INFUSING) {
                InfuseObject pendingInfusionInput = InfuseRegistry.getObject(extra);
                if (pendingInfusionInput != null) {
                    if (infuseStored.getType() == null || infuseStored.getType() == pendingInfusionInput.type) {
                        if (infuseStored.getAmount() + pendingInfusionInput.stored <= maxInfuse) {
                            if (tier != FactoryTier.CREATIVE) {
                                infuseStored.increase(pendingInfusionInput);
                                extra.shrink(1);
                            } else {
                                infuseStored.setType(pendingInfusionInput.type);
                                infuseStored.setAmount(maxInfuse);
                            }
                        }
                    }
                }
            }
        }
        if ((GasOutputMachine() || recipeType == RecipeType.PRC) && gasOutTank.getNeeded() >= 0 && tier == FactoryTier.CREATIVE) {
            if (gasOutTank.stored != null) {
                gasOutTank.setGas(gasOutTank.getGas());
                gasOutTank.setMaxGas(Integer.MAX_VALUE);
                gasOutTank.stored.amount = gasTank.getMaxGas();
            }
        }
        if ((recipeType == RecipeType.WASHER || recipeType == RecipeType.PRC) && tier == FactoryTier.CREATIVE) {
            if (fluidTank.getFluid() != null) {
                fluidTank.setFluid(fluidTank.getFluid());
                fluidTank.setCapacity(Integer.MAX_VALUE);
                fluidTank.getFluid().amount = fluidTank.getCapacity();
            }
        }

    }

    public ItemStack getMachineStack() {
        return recipeType.getStack();
    }

    @Override
    public boolean canExtractItem(int slotID, @Nonnull ItemStack itemstack, @Nonnull EnumFacing side) {
        if (slotID == 1) {
            return ChargeUtils.canBeOutputted(itemstack, false);
        } else if (tier == FactoryTier.BASIC && isOutputSlot(slotID)) {
            return true;
        } else if (tier == FactoryTier.ADVANCED && isOutputSlot(slotID)) {
            return true;
        } else if (tier == FactoryTier.ELITE && isOutputSlot(slotID)) {
            return true;
        } else if (tier == FactoryTier.ULTIMATE && isOutputSlot(slotID)) {
            return true;
        } else if (tier == FactoryTier.CREATIVE && isOutputSlot(slotID)) {
            return true;
        } else
            return OuputItemSecondaryMachine() && isSecondaryOutputSlot(slotID);
    }

    @Override
    public boolean canInsertItem(int slotID, @Nonnull ItemStack itemstack, @Nonnull EnumFacing side) {
        if (slotID == 1) {
            return ChargeUtils.canBeDischarged(itemstack);
        } else if (isInputSlot(slotID)) {
            return inputProducesOutput(slotID, itemstack, inventory.get(tier.processes + slotID), false);
        }
        //TODO: Only allow inserting into extra slot if it can go in
        return super.canInsertItem(slotID, itemstack, side);
    }

    private boolean isInputSlot(int slotID) {
        return slotID >= 5 && (tier == FactoryTier.BASIC ? slotID <= 7 : tier == FactoryTier.ADVANCED ? slotID <= 9 : tier == FactoryTier.ELITE ? slotID <= 11 : tier == FactoryTier.ULTIMATE ? slotID <= 13 : tier == FactoryTier.CREATIVE && slotID <= 15);
    }

    private boolean isOutputSlot(int slotID) {
        int slotIDOutput = tier == FactoryTier.BASIC ? 8 : tier == FactoryTier.ADVANCED ? 10 : tier == FactoryTier.ELITE ? 12 : tier == FactoryTier.ULTIMATE ? 14 : 16;
        return slotID >= slotIDOutput && slotID <= slotIDOutput + tier.processes - 1;
    }

    private boolean isSecondaryOutputSlot(int slotID) {
        int slotIDOutput = tier == FactoryTier.BASIC ? 11 : tier == FactoryTier.ADVANCED ? 15 : tier == FactoryTier.ELITE ? 19 : tier == FactoryTier.ULTIMATE ? 23 : 27;
        return slotID >= slotIDOutput && slotID <= slotIDOutput + tier.processes * 2 - 1;
    }


    @Override
    public boolean isItemValidForSlot(int slotID, @Nonnull ItemStack itemstack) {
        if (isOutputSlot(slotID)) {
            return false;
        } else if (isSecondaryOutputSlot(slotID)) {
            return false;
        } else if (isInputSlot(slotID)) {
            return !NoItemInputMachine() && recipeType.hasRecipeForInput(itemstack);
        }
        if (slotID == 0) {
            return itemstack.getItem() == MekanismItems.SpeedUpgrade || itemstack.getItem() == MekanismItems.EnergyUpgrade;
        } else if (slotID == 1) {
            return ChargeUtils.canBeDischarged(itemstack);
        } else if (slotID == 4) {
            if (GasInputMachine()) {
                return getItemGas(itemstack) != null;
            } else if (recipeType.getFuelType() == MachineFuelType.DOUBLE) {
                return recipeType.hasRecipeForExtra(itemstack);
            } else if (recipeType == RecipeType.INFUSING) {
                return InfuseRegistry.getObject(itemstack) != null && (infuseStored.getType() == null || infuseStored.getType() == InfuseRegistry.getObject(itemstack).type);
            }
        }
        return false;
    }


    public double getScaledProgress(int process) {
        if (recipeType == RecipeType.PRC || recipeType == RecipeType.NUCLEOSYNTHESIZER) {
            PressurizedRecipe PRCrecipe = recipeType.getPressurizedRecipe(inventory.get(getInputSlot(process)), fluidTank.getFluid(), gasTank.getGas());
            NucleosynthesizerRecipe NnRecipe = recipeType.getNucleosynthesizerRecipe(inventory.get(getInputSlot(process)), gasTank.getGas());
            if (PRCrecipe != null && recipeType == RecipeType.PRC) {
                boolean update = BASE_TICKS_REQUIRED != PRCrecipe.ticks;
                BASE_TICKS_REQUIRED = PRCrecipe.ticks;
                if (update) {
                    recalculateUpgradables(Upgrade.SPEED);
                }
            } else if (NnRecipe != null && recipeType == RecipeType.NUCLEOSYNTHESIZER) {
                boolean update = BASE_TICKS_REQUIRED != NnRecipe.ticks;
                BASE_TICKS_REQUIRED = NnRecipe.ticks;
                if (update) {
                    recalculateUpgradables(Upgrade.SPEED);
                }
            }
            return Math.max(Math.min((double) progress[process] / ticksRequired, 1.0D), 0.0F);
        } else if (recipeType == RecipeType.WASHER) {
            return getActive() ? 1 : 0;
        }
        return Math.max(Math.min((double) progress[process] / ticksRequired, 1.0D), 0.0F);
    }


    public double getScaledInfuseLevel(int i) {
        return maxInfuse <= 0 ? 0 : (double) infuseStored.getAmount() * i / maxInfuse;
    }

    public double getScaledGasLevel(int i) {
        int maxGas = gasTank.getMaxGas();
        return maxGas <= 0 ? 0 : (double) gasTank.getStored() * i / maxGas;
    }

    public double getScaledGasOutlevel(int i) {
        int maxGas = gasOutTank.getMaxGas();
        return maxGas <= 0 ? 0 : (double) gasOutTank.getStored() * i / maxGas;
    }

    public double getScaledfluidTanklevel(int i) {
        int capacity = fluidTank.getCapacity();
        return capacity <= 0 ? 0 : (double) fluidTank.getFluidAmount() * i / capacity;
    }

    public int getScaledRecipeProgress(int i) {
        return recipeTicks * i / RECIPE_TICKS_REQUIRED;
    }

    public boolean canOperate(int inputSlot, int outputSlot, int SecondaryOutputSlot) {
        if (inventory.get(inputSlot).isEmpty() && !NoItemInputMachine()) {
            return false;
        }

        int process = getOperation(inputSlot);

        if (recipeType.getFuelType() == MachineFuelType.ADVANCED) {
            if (cachedRecipe[process] instanceof AdvancedMachineRecipe<?> advancedMachine &&
                    advancedMachine.inputMatches(inventory, inputSlot, gasTank, secondaryEnergyThisTick)) {
                return advancedMachine.canOperate(inventory, inputSlot, outputSlot, gasTank, secondaryEnergyThisTick);
            }
            AdvancedMachineRecipe<?> recipe = recipeType.getRecipe(inventory.get(inputSlot), gasTank.getGasType());
            cachedRecipe[process] = recipe;
            return recipe != null && recipe.canOperate(inventory, inputSlot, outputSlot, gasTank, secondaryEnergyThisTick);
        } else if (recipeType.getFuelType() == MachineFuelType.DOUBLE) {
            if (cachedRecipe[process] instanceof DoubleMachineRecipe<?> doubleMachine && doubleMachine.inputMatches(inventory, inputSlot, 4)) {
                return doubleMachine.canOperate(inventory, inputSlot, 4, outputSlot);
            }
            DoubleMachineRecipe<?> recipe = recipeType.getRecipe(inventory.get(inputSlot), inventory.get(4));
            cachedRecipe[process] = recipe;
            return recipe != null && recipe.canOperate(inventory, inputSlot, 4, outputSlot);
        } else if (recipeType.getFuelType() == MachineFuelType.CHANCE) {
            if (cachedRecipe[process] instanceof ChanceMachineRecipe<?> chanceMachine && chanceMachine.inputMatches(inventory, inputSlot)) {
                return chanceMachine.canOperate(inventory, inputSlot, outputSlot, SecondaryOutputSlot);
            }
            ChanceMachineRecipe<?> recipe = recipeType.getChanceRecipe(inventory.get(inputSlot));
            cachedRecipe[process] = recipe;
            return recipe != null && recipe.canOperate(inventory, inputSlot, outputSlot, SecondaryOutputSlot);
        } else if (recipeType.getFuelType() == MachineFuelType.FARM) {
            if (cachedRecipe[process] instanceof FarmMachineRecipe<?> farmMachine && farmMachine.inputMatches(inventory, inputSlot, gasTank, secondaryEnergyThisTick)) {
                return farmMachine.canOperate(inventory, inputSlot, gasTank, secondaryEnergyThisTick, outputSlot, SecondaryOutputSlot);
            }
            FarmMachineRecipe<?> recipe = recipeType.getFarmRecipe(inventory.get(inputSlot), gasTank.getGasType());
            cachedRecipe[process] = recipe;
            return recipe != null && recipe.canOperate(inventory, inputSlot, gasTank, secondaryEnergyThisTick, outputSlot, SecondaryOutputSlot);
        } else if (recipeType.getFuelType() == MachineFuelType.CHANCE2) {
            if (cachedRecipe[process] instanceof Chance2MachineRecipe<?> chance2Machine && chance2Machine.inputMatches(inventory, inputSlot)) {
                return chance2Machine.canOperate(inventory, inputSlot, outputSlot);
            }
            Chance2MachineRecipe<?> recipe = recipeType.getChance2Recipe(inventory.get(inputSlot));
            cachedRecipe[process] = recipe;
            return recipe != null && recipe.canOperate(inventory, inputSlot, outputSlot);
        }

        if (recipeType == RecipeType.INFUSING) {
            if (cachedRecipe[process] instanceof MetallurgicInfuserRecipe metallurgicInfuser && metallurgicInfuser.inputMatches(inventory, inputSlot, infuseStored)) {
                return metallurgicInfuser.canOperate(inventory, inputSlot, outputSlot, infuseStored);
            }
            InfusionInput input = new InfusionInput(infuseStored, inventory.get(inputSlot));
            MetallurgicInfuserRecipe recipe = RecipeHandler.getMetallurgicInfuserRecipe(input);
            cachedRecipe[process] = recipe;
            if (recipe == null) {
                return false;
            }
            return recipe.canOperate(inventory, inputSlot, outputSlot, infuseStored);
        }


        if (recipeType == RecipeType.Crystallizer) {
            if (cachedRecipe[process] instanceof CrystallizerRecipe crystallizer && crystallizer.getInput().useGas(gasTank, false, 1)) {
                return crystallizer.canOperate(gasTank, inventory, outputSlot);
            }
            GasInput input = new GasInput(gasTank.getGas());
            CrystallizerRecipe recipe = RecipeHandler.getChemicalCrystallizerRecipe(input);
            cachedRecipe[process] = recipe;
            if (recipe == null) {
                return false;
            }
            return recipe.canOperate(gasTank, inventory, outputSlot);
        }

        if (recipeType == RecipeType.WASHER) {
            if (cachedRecipe[process] instanceof WasherRecipe washer && washer.getInput().useGas(gasTank, false, 1) && washer.getInput().useFluid(fluidTank, false, 1)) {
                return washer.canOperate(gasTank, fluidTank, gasOutTank);
            }
            GasAndFluidInput input = new GasAndFluidInput(gasTank.getGas(), fluidTank.getFluid());
            WasherRecipe recipe = RecipeHandler.getChemicalWasherRecipe(input);
            cachedRecipe[process] = recipe;
            if (recipe == null) {
                return false;
            }
            return recipe.canOperate(gasTank, fluidTank, gasOutTank);
        }

        if (recipeType == RecipeType.Dissolution) {
            if (cachedRecipe[process] instanceof DissolutionRecipe dissolution && dissolution.getInput().useItemStackFromInventory(inventory, inputSlot, false)) {
                return dissolution.canOperate(inventory, inputSlot, gasOutTank);
            }
            ItemStackInput input = new ItemStackInput(inventory.get(inputSlot));
            DissolutionRecipe recipe = RecipeHandler.getDissolutionRecipe(input);
            cachedRecipe[process] = recipe;
            if (recipe == null) {
                return false;
            }
            return recipe.canOperate(inventory, inputSlot, gasOutTank);
        }

        if (recipeType == RecipeType.OXIDIZER) {
            if (cachedRecipe[process] instanceof OxidationRecipe oxidation && oxidation.getInput().useItemStackFromInventory(inventory, inputSlot, false)) {
                return oxidation.canOperate(inventory, inputSlot, gasOutTank);
            }
            ItemStackInput input = new ItemStackInput(inventory.get(inputSlot));
            OxidationRecipe recipe = RecipeHandler.getOxidizerRecipe(input);
            cachedRecipe[process] = recipe;
            if (recipe == null) {
                return false;
            }
            return recipe.canOperate(inventory, inputSlot, gasOutTank);
        }

        if (recipeType == RecipeType.NUCLEOSYNTHESIZER) {
            if (cachedRecipe[process] instanceof NucleosynthesizerRecipe nucleosynthesizer && nucleosynthesizer.getInput().use(inventory, inputSlot, gasTank, false)) {
                return nucleosynthesizer.canOperate(inventory, inputSlot, gasTank, outputSlot);
            }
            NucleosynthesizerInput input = new NucleosynthesizerInput(inventory.get(inputSlot), gasTank.getGas());
            NucleosynthesizerRecipe recipe = RecipeHandler.getNucleosynthesizerRecipe(input);
            cachedRecipe[process] = recipe;
            if (recipe == null) {
                return false;
            }
            return recipe.canOperate(inventory, inputSlot, gasTank, outputSlot);
        }

        if (recipeType == RecipeType.PRC) {
            if (cachedRecipe[process] instanceof PressurizedRecipe pressurized && pressurized.getInput().use(inventory, inputSlot, fluidTank, gasTank, false)) {
                return pressurized.canOperate(inventory, inputSlot, fluidTank, gasTank, gasOutTank, outputSlot);
            }
            PressurizedInput input = new PressurizedInput(inventory.get(inputSlot), fluidTank.getFluid(), gasTank.getGas());
            PressurizedRecipe recipe = RecipeHandler.getPRCRecipe(input);
            cachedRecipe[process] = recipe;
            if (recipe == null) {
                return false;
            }
            return recipe.canOperate(inventory, inputSlot, fluidTank, gasTank, gasOutTank, outputSlot);
        }

        if (cachedRecipe[process] instanceof BasicMachineRecipe<?> basicMachine && basicMachine.inputMatches(inventory, inputSlot)) {
            return basicMachine.canOperate(inventory, inputSlot, outputSlot);
        }
        BasicMachineRecipe<?> recipe = recipeType.getRecipe(inventory.get(inputSlot));
        cachedRecipe[process] = recipe;
        return recipe != null && recipe.canOperate(inventory, inputSlot, outputSlot);

    }

    public void operate(int inputSlot, int outputSlot, int secondaryOutputSlot) {
        if (!canOperate(inputSlot, outputSlot, secondaryOutputSlot)) {
            return;
        }
        int process = getOperation(inputSlot);
        if (cachedRecipe[process] == null) {//should never happen, but cant be too sure.
            Mekanism.logger.debug("cachedRecipe was null, but we were asked to operate anyway?! {} @ {}", this, this.pos);
            return;
        }

        if (recipeType.getFuelType() == MachineFuelType.ADVANCED && cachedRecipe[process] instanceof AdvancedMachineRecipe<?> recipe) {
            recipe.operate(inventory, inputSlot, outputSlot, gasTank, secondaryEnergyThisTick, tier != FactoryTier.CREATIVE);
        } else if (recipeType.getFuelType() == MachineFuelType.DOUBLE && cachedRecipe[process] instanceof DoubleMachineRecipe<?> recipe) {
            recipe.operate(inventory, inputSlot, 4, outputSlot, tier != FactoryTier.CREATIVE);
        } else if (recipeType.getFuelType() == MachineFuelType.CHANCE && cachedRecipe[process] instanceof ChanceMachineRecipe<?> recipe) {
            recipe.operate(inventory, inputSlot, outputSlot, secondaryOutputSlot, tier != FactoryTier.CREATIVE);
        } else if (recipeType == RecipeType.INFUSING && cachedRecipe[process] instanceof MetallurgicInfuserRecipe recipe) {
            recipe.operate(inventory, inputSlot, outputSlot, infuseStored, tier != FactoryTier.CREATIVE);
        } else if (recipeType.getFuelType() == MachineFuelType.FARM && cachedRecipe[process] instanceof FarmMachineRecipe<?> recipe) {
            recipe.operate(inventory, inputSlot, gasTank, secondaryEnergyThisTick, outputSlot, secondaryOutputSlot, tier != FactoryTier.CREATIVE);
        } else if (recipeType.getFuelType() == MachineFuelType.CHANCE2 && cachedRecipe[process] instanceof Chance2MachineRecipe<?> recipe) {
            recipe.operate(inventory, inputSlot, outputSlot, tier != FactoryTier.CREATIVE);
        } else if (recipeType == RecipeType.Crystallizer && cachedRecipe[process] instanceof CrystallizerRecipe recipe) {
            recipe.operate(gasTank, inventory, outputSlot, tier != FactoryTier.CREATIVE);
        } else if (recipeType == RecipeType.Dissolution && cachedRecipe[process] instanceof DissolutionRecipe recipe) {
            recipe.operate(inventory, inputSlot, gasOutTank, tier != FactoryTier.CREATIVE);
        } else if (recipeType == RecipeType.OXIDIZER && cachedRecipe[process] instanceof OxidationRecipe recipe) {
            recipe.operate(inventory, inputSlot, gasOutTank, tier != FactoryTier.CREATIVE);
        } else if (recipeType == RecipeType.PRC && cachedRecipe[process] instanceof PressurizedRecipe recipe) {
            recipe.operate(inventory, inputSlot, fluidTank, gasTank, gasOutTank, outputSlot, tier != FactoryTier.CREATIVE);
        } else if (recipeType == RecipeType.NUCLEOSYNTHESIZER && cachedRecipe[process] instanceof NucleosynthesizerRecipe recipe) {
            recipe.operate(inventory, inputSlot, gasTank, outputSlot, tier != FactoryTier.CREATIVE);
        } else if (recipeType == RecipeType.WASHER && cachedRecipe[process] instanceof WasherRecipe recipe) {
            int operations = getUpgradedUsage(recipe);
            recipe.operate(gasTank, fluidTank, gasOutTank, operations, tier != FactoryTier.CREATIVE);
        } else {
            BasicMachineRecipe<?> recipe = (BasicMachineRecipe<?>) cachedRecipe[process];
            recipe.operate(inventory, inputSlot, outputSlot, tier != FactoryTier.CREATIVE);
        }
        markNoUpdateSync();
    }

    public int getUpgradedUsage(WasherRecipe recipe) {
        int possibleProcess = Math.min((int) Math.pow(2, upgradeComponent.getUpgrades(Upgrade.SPEED)), MekanismConfig.current().mekce.MAXspeedmachines.val());
        possibleProcess = Math.min(Math.min(gasTank.getStored(), gasOutTank.getNeeded()), possibleProcess);
        possibleProcess = Math.min((int) (getEnergy() / energyPerTick), possibleProcess);
        possibleProcess = Math.max(possibleProcess, 1);
        return Math.min(fluidTank.getFluidAmount() / recipe.recipeInput.ingredientFluid.amount, possibleProcess);
    }


    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            int type = dataStream.readInt();
            if (type == 0) {
                sorting = !sorting;
            } else if (type == 1) {
                infuseStored.setEmpty();
            } else if (type == 2) {
                Factoryoldsorting = !Factoryoldsorting;
            }
            return;
        }

        super.handlePacketData(dataStream);

        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            RecipeType oldRecipe = recipeType;
            recipeType = MekanismUtils.getByIndex(RecipeType.values(), dataStream.readInt(), recipeType);
            upgradeComponent.setSupported(Upgrade.GAS, recipeType.fuelEnergyUpgrades());
            recipeTicks = dataStream.readInt();
            sorting = dataStream.readBoolean();
            Factoryoldsorting = dataStream.readBoolean();
            upgraded = dataStream.readBoolean();
            lastUsage = dataStream.readDouble();
            machineUsesItem = dataStream.readBoolean();
            isMachineUsesItem = dataStream.readBoolean();
            machineUsesGAS = dataStream.readBoolean();
            isMachineUsesGAS = dataStream.readBoolean();
            machineUsesFluid = dataStream.readBoolean();
            isMachineUsesFluid = dataStream.readBoolean();
            int amount = dataStream.readInt();
            if (amount > 0) {
                infuseStored.setAmount(amount);
                infuseStored.setType(InfuseRegistry.get(PacketHandler.readString(dataStream)));
            } else {
                infuseStored.setEmpty();
            }

            if (recipeType != oldRecipe) {
                setRecipeType(recipeType);
                if (!upgraded) {
                    MekanismUtils.updateBlock(world, getPos());
                }
            }

            for (int i = 0; i < tier.processes; i++) {
                progress[i] = dataStream.readInt();
            }
            TileUtils.readTankData(dataStream, fluidTank);
            TileUtils.readTankData(dataStream, gasTank);
            TileUtils.readTankData(dataStream, gasOutTank);
            if (upgraded) {
                markNoUpdateSync();
                MekanismUtils.updateBlock(world, getPos());
                upgraded = false;
            }
        }
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (nbtTags.hasKey("recipeType")) {
            setRecipeType(MekanismUtils.getByIndex(RecipeType.values(), nbtTags.getInteger("recipeType"), recipeType));
        }
        upgradeComponent.setSupported(Upgrade.GAS, recipeType.fuelEnergyUpgrades());
        recipeTicks = nbtTags.getInteger("recipeTicks");
        sorting = nbtTags.getBoolean("sorting");
        Factoryoldsorting = nbtTags.getBoolean("factoryoldsorting");
        int amount = nbtTags.getInteger("infuseStored");
        if (amount != 0) {
            infuseStored.setAmount(amount);
            infuseStored.setType(InfuseRegistry.get(nbtTags.getString("type")));
        }
        for (int i = 0; i < tier.processes; i++) {
            progress[i] = nbtTags.getInteger("progress" + i);
        }
        fluidTank.readFromNBT(nbtTags.getCompoundTag("fluidTank"));
        gasTank.read(nbtTags.getCompoundTag("gasTank"));
        gasOutTank.read(nbtTags.getCompoundTag("gasOutTank"));
        GasUtils.clearIfInvalid(gasTank, recipeType::isValidGas);
        machineUsesItem = nbtTags.getBoolean("machineUsesItem");
        isMachineUsesItem = nbtTags.getBoolean("isMachineUsesItem");
        machineUsesGAS = nbtTags.getBoolean("machineUsesGAS");
        isMachineUsesGAS = nbtTags.getBoolean("isMachineUsesGAS");
        machineUsesFluid = nbtTags.getBoolean("machineUsesFluid");
        isMachineUsesFluid = nbtTags.getBoolean("isMachineUsesFluid");
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("recipeType", recipeType.ordinal());
        nbtTags.setInteger("recipeTicks", recipeTicks);
        nbtTags.setBoolean("sorting", sorting);
        nbtTags.setBoolean("factoryoldsorting", Factoryoldsorting);
        if (infuseStored.getType() != null) {
            nbtTags.setString("type", infuseStored.getType().name);
            nbtTags.setInteger("infuseStored", infuseStored.getAmount());
        } else {
            nbtTags.setString("type", "null");
        }
        for (int i = 0; i < tier.processes; i++) {
            nbtTags.setInteger("progress" + i, progress[i]);
        }
        nbtTags.setTag("fluidTank", fluidTank.writeToNBT(new NBTTagCompound()));
        nbtTags.setTag("gasTank", gasTank.write(new NBTTagCompound()));
        nbtTags.setTag("gasOutTank", gasOutTank.write(new NBTTagCompound()));
        nbtTags.setBoolean("machineUsesItem", machineUsesItem);
        nbtTags.setBoolean("isMachineUsesItem", isMachineUsesItem);
        nbtTags.setBoolean("machineUsesGAS", machineUsesGAS);
        nbtTags.setBoolean("isMachineUsesGAS", isMachineUsesGAS);
        nbtTags.setBoolean("machineUsesFluid", machineUsesFluid);
        nbtTags.setBoolean("isMachineUsesFluid", isMachineUsesFluid);
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);

        data.add(recipeType.ordinal());
        data.add(recipeTicks);
        data.add(sorting);
        data.add(Factoryoldsorting);
        data.add(upgraded);
        data.add(lastUsage);
        data.add(machineUsesItem);
        data.add(isMachineUsesItem);
        data.add(machineUsesGAS);
        data.add(isMachineUsesGAS);
        data.add(machineUsesFluid);
        data.add(isMachineUsesFluid);
        data.add(infuseStored.getAmount());
        if (infuseStored.getAmount() > 0) {
            data.add(infuseStored.getType().name);
        }

        data.add(progress);
        TileUtils.addTankData(data, fluidTank);
        TileUtils.addTankData(data, gasTank);
        TileUtils.addTankData(data, gasOutTank);
        upgraded = false;
        return data;
    }

    public int getInputSlot(int operation) {
        return 5 + operation;
    }

    /* reverse of the above */

    private int getOperation(int inputSlot) {
        return inputSlot - 5;
    }

    public int getOutputSlot(int operation) {
        return 5 + tier.processes + operation;
    }

    public int getSecondaryOutputSlot(int operation) {
        return 5 + tier.processes * 2 + operation;
    }

    @Nonnull
    @Override
    public String getName() {
        if (LangUtils.canLocalize("tile." + tier.getBaseTier().getName() + recipeType.getTranslationKey() + "Factory")) {
            return LangUtils.localize("tile." + tier.getBaseTier().getName() + recipeType.getTranslationKey() + "Factory");
        }
        //TODO:Rename this
        return tier.getBaseTier().getLocalizedName() + recipeType.getLocalizedName() + super.getName();
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        switch (method) {
            case 0 -> {
                return new Object[]{electricityStored};
            }
            case 1 -> {
                if (arguments[0] == null) {
                    return new Object[]{"Please provide a target operation."};
                }
                if (!(arguments[0] instanceof Double) && !(arguments[0] instanceof Integer)) {
                    return new Object[]{"Invalid characters."};
                }
                if ((Integer) arguments[0] < 0 || (Integer) arguments[0] > progress.length) {
                    return new Object[]{"No such operation found."};
                }
                return new Object[]{progress[(Integer) arguments[0]]};
            }
            case 2 -> {
                return new Object[]{facing};
            }
            case 3 -> {
                if (arguments[0] == null) {
                    return new Object[]{"Please provide a target operation."};
                }
                if (!(arguments[0] instanceof Double) && !(arguments[0] instanceof Integer)) {
                    return new Object[]{"Invalid characters."};
                }
                if ((Integer) arguments[0] < 0 || (Integer) arguments[0] > progress.length) {
                    return new Object[]{"No such operation found."};
                }
                return new Object[]{canOperate(getInputSlot((Integer) arguments[0]), getOutputSlot((Integer) arguments[0]), getSecondaryOutputSlot((Integer) arguments[0]))};
            }
            case 4 -> {
                return new Object[]{getMaxEnergy()};
            }
            case 5 -> {
                return new Object[]{getMaxEnergy() - getEnergy()};
            }
            default -> throw new NoSuchMethodException();
        }
    }

    @Override
    public int fill(EnumFacing from, @Nonnull FluidStack resource, boolean doFill) {
        return inputFluidMachine() ? fluidTank.fill(resource, doFill) : 0;
    }

    @Override
    public boolean canFill(EnumFacing from, @Nonnull FluidStack fluid) {
        if (inputFluidMachine() && configComponent.getOutput(TransmissionType.FLUID, from, facing).ioState == SideData.IOState.INPUT) {
            return FluidContainerUtils.canFill(fluidTank.getFluid(), fluid);
        }
        return false;
    }

    @Override
    public FluidTankInfo[] getTankInfo(EnumFacing from) {
        if (inputFluidMachine() && configComponent.getOutput(TransmissionType.FLUID, from, facing).ioState != SideData.IOState.OFF) {
            return new FluidTankInfo[]{fluidTank.getInfo()};
        }
        return PipeUtils.EMPTY;
    }

    @Override
    public FluidTankInfo[] getAllTanks() {
        return getTankInfo(null);
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return NoItemMachine() ? InventoryUtils.EMPTY : configComponent.getOutput(TransmissionType.ITEM, side, facing).availableSlots;
    }

    @Override
    public TileComponentConfig getConfig() {
        return configComponent;
    }

    @Override
    public EnumFacing getOrientation() {
        return facing;
    }

    @Override
    public TileComponentEjector getEjector() {
        return ejectorComponent;
    }

    @Override
    public int receiveGas(EnumFacing side, GasStack stack, boolean doTransfer) {
        if (stack == null || stack.getGas() == null) {
            return 0;
        }
        if (GasMachine() && canReceiveGas(side, stack.getGas())) {
            return gasTank.receive(stack, doTransfer);
        }
        return 0;
    }

    @Override
    public GasStack drawGas(EnumFacing side, int amount, boolean doTransfer) {
        if (GasOutputMachine() && canDrawGas(side, null)) {
            return gasOutTank.draw(amount, doTransfer);
        }
        return null;
    }

    @Override
    public boolean canReceiveGas(EnumFacing side, Gas type) {
        return configComponent.getOutput(TransmissionType.GAS, side, facing).hasSlot(1) && gasTank.canReceive(type) && switch (recipeType.getFuelType()) {
            case ADVANCED, FARM -> recipeType.canReceiveGas(side, type);
            default -> switch (recipeType) {
                case Crystallizer -> RecipeHandler.Recipe.CHEMICAL_CRYSTALLIZER.containsRecipe(type);
                case Dissolution -> type == MekanismFluids.SulfuricAcid;
                case PRC -> RecipeHandler.Recipe.PRESSURIZED_REACTION_CHAMBER.containsRecipe(type);
                case NUCLEOSYNTHESIZER -> RecipeHandler.Recipe.ANTIPROTONIC_NUCLEOSYNTHESIZER.containsRecipe(type);
                case WASHER -> RecipeHandler.Recipe.CHEMICAL_WASHER.containsRecipe(type);
                default -> false;
            };
        };
    }


    @Override
    public boolean canDrawGas(EnumFacing side, Gas type) {
        return configComponent.getOutput(TransmissionType.GAS, side, facing).hasSlot(2) && gasOutTank.canDraw(type);
    }

    @Nonnull
    @Override
    public GasTankInfo[] getTankInfo() {
        if (recipeType.getCanInputGas() && recipeType.getCanOuputGas()) {
            return new GasTankInfo[]{gasTank, gasOutTank};
        } else if (recipeType.getCanInputGas()) {
            return new GasTankInfo[]{gasTank};
        } else if (recipeType.getCanOuputGas()) {
            return new GasTankInfo[]{gasOutTank};
        }
        return IGasHandler.NONE;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return false;
        }
        return capability == Capabilities.GAS_HANDLER_CAPABILITY || capability == Capabilities.CONFIG_CARD_CAPABILITY || capability == Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY || capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return null;
        } else if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            return Capabilities.GAS_HANDLER_CAPABILITY.cast(this);
        } else if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(new FluidHandlerWrapper(this, side));
        } else if (capability == Capabilities.CONFIG_CARD_CAPABILITY) {
            return Capabilities.CONFIG_CARD_CAPABILITY.cast(this);
        } else if (capability == Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY) {
            return Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        if (configComponent.isCapabilityDisabled(capability, side, facing)) {
            return true;
        } else if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            //If the gas capability is not disabled, check if this machine even actually supports gas
            return !recipeType.supportsGas();
        }
        return super.isCapabilityDisabled(capability, side);
    }

    @Override
    public void recalculateUpgradables(Upgrade upgrade) {
        super.recalculateUpgradables(upgrade);
        switch (upgrade) {
            case ENERGY -> {
                if (tier == FactoryTier.CREATIVE) {
                    maxEnergy = BASE_MAX_ENERGY;
                }
                energyPerTick = MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK); // incorporate speed upgrades
            }
            case GAS -> secondaryEnergyPerTick = getSecondaryEnergyPerTick(recipeType);
            case SPEED -> {
                if (tier != FactoryTier.CREATIVE) {
                    energyPerTick = MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK);
                }
                ticksRequired = MekanismUtils.getTicks(this, BASE_TICKS_REQUIRED);
                secondaryEnergyPerTick = getSecondaryEnergyPerTick(recipeType);
            }
            default -> {
            }
        }
    }

    @Override
    public Object[] getTanks() {
        return new Object[]{fluidTank, gasTank, gasOutTank};
    }

    @Override
    public NBTTagCompound getConfigurationData(NBTTagCompound nbtTags) {
        nbtTags.setBoolean("sorting", sorting);
        return nbtTags;
    }

    @Override
    public void setConfigurationData(NBTTagCompound nbtTags) {
        sorting = nbtTags.getBoolean("sorting");
    }

    @Override
    public String getDataType() {
        return tier.getBaseTier().getLocalizedName() + recipeType.getLocalizedName() + super.getName();
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        infuseStored.writeSustainedData(itemStack);
        if (gasTank.getGas() != null) {
            ItemDataUtils.setCompound(itemStack, "gasTank", gasTank.getGas().write(new NBTTagCompound()));
        }
        if (gasOutTank.getGas() != null) {
            ItemDataUtils.setCompound(itemStack, "gasOutTank", gasOutTank.getGas().write(new NBTTagCompound()));
        }
        if (fluidTank.getFluid() != null) {
            ItemDataUtils.setCompound(itemStack, "fluidTank", fluidTank.getFluid().writeToNBT(new NBTTagCompound()));
        }
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        infuseStored.readSustainedData(itemStack);
        gasTank.setGas(GasStack.readFromNBT(ItemDataUtils.getCompound(itemStack, "gasTank")));
        gasOutTank.setGas(GasStack.readFromNBT(ItemDataUtils.getCompound(itemStack, "gasOutTank")));
        fluidTank.setFluid(FluidStack.loadFluidStackFromNBT(ItemDataUtils.getCompound(itemStack, "fluidTank")));
    }

    @Override
    public int getRedstoneLevel() {
        return Container.calcRedstoneFromInventory(this);
    }


    public MachineRecipe<?, ?, ?> getSlotRecipe(int slotID, ItemStack fallbackInput, ItemStack output, boolean updateCache) {
        int process = getOperation(slotID);
        //cached recipe may be invalid
        MachineRecipe<?, ?, ?> cached = cachedRecipe[process];
        ItemStack extra = inventory.get(4);
        if (cached == null) {
            cached = recipeType.getAnyRecipe(fallbackInput, extra, gasTank.getGasType(), infuseStored, gasTank.getGas(), fluidTank.getFluid());
            if (updateCache) {
                cachedRecipe[process] = cached;
            }
        } else {
            ItemStack recipeInput = ItemStack.EMPTY;
            boolean secondaryMatch = true;
            boolean secondaryMatch2 = true;
            if (cached.recipeInput instanceof ItemStackInput input) {
                recipeInput = input.ingredient;
            } else if (cached.recipeInput instanceof AdvancedMachineInput advancedInput) {
                recipeInput = advancedInput.itemStack;
                secondaryMatch = gasTank.getGasType() == null || advancedInput.gasType == gasTank.getGasType();
            } else if (cached.recipeInput instanceof DoubleMachineInput doubleMachineInput) {
                recipeInput = doubleMachineInput.itemStack;
                secondaryMatch = extra.isEmpty() || ItemStack.areItemsEqual(doubleMachineInput.extraStack, extra);
            } else if (cached.recipeInput instanceof InfusionInput infusionInput) {
                recipeInput = infusionInput.inputStack;
                secondaryMatch = infuseStored.getAmount() == 0 || infuseStored.getType() == infusionInput.infuse.getType();
            } else if (cached.recipeInput instanceof GasInput gasInput) {
                secondaryMatch = gasTank.getGasType() == null || gasInput.ingredient.getGas() == gasTank.getGasType();
            } else if (cached.recipeInput instanceof PressurizedInput pressurizedInput) {
                recipeInput = pressurizedInput.getSolid();
                secondaryMatch = gasTank.getGas() == null || gasTank.getGas().isGasEqual(pressurizedInput.getGas());
                secondaryMatch2 = fluidTank.getFluid() == null || fluidTank.getFluid() == pressurizedInput.getFluid();
            } else if (cached.recipeInput instanceof NucleosynthesizerInput input) {
                recipeInput = input.getSolid();
                secondaryMatch = (gasTank.getGas() == null || gasTank.getGas().isGasEqual(input.getGas()));
            }
            //If there is no cached item input or it doesn't match our fallback
            // then it is an out of date cache so we compare against the new one
            // and update the cache while we are at it
            if (recipeInput.isEmpty() || !secondaryMatch || !secondaryMatch2 || !ItemStack.areItemsEqual(recipeInput, fallbackInput)) {
                cached = recipeType.getAnyRecipe(fallbackInput, extra, gasTank.getGasType(), infuseStored, gasTank.getGas(), fluidTank.getFluid());
                if (updateCache) {
                    cachedRecipe[process] = cached;
                }
            }
        }
        if (cached != null) {
            ItemStack recipeOutput = ItemStack.EMPTY;
            if (cached.recipeOutput instanceof ItemStackOutput stackOutput) {
                recipeOutput = stackOutput.output;
            } else if (cached.recipeOutput instanceof ChanceOutput stackOutput) {
                recipeOutput = stackOutput.primaryOutput;
            } else if (cached.recipeOutput instanceof ChanceOutput2 stackOutput) {
                recipeOutput = stackOutput.primaryOutput;
            } else if (cached.recipeOutput instanceof PressurizedOutput stackOutput) {
                recipeOutput = stackOutput.getItemOutput();
            }
            if (!recipeOutput.isEmpty()) {
                InventoryUtils.areItemsStackable(recipeOutput, output);
            }
        }
        return cached;
    }


    private void CheckTheFaceSettings() {
        if (NoItemMachine()) {
            configComponent.removeSupported(TransmissionType.ITEM);
            configComponent.removeConfig(TransmissionType.ITEM);
            ejectorComponent.removeOutputData(TransmissionType.ITEM);
            ejectorComponent.removeInputOutputData(TransmissionType.ITEM);
            ejectorComponent.removeInputExtraOutputData(TransmissionType.ITEM);
            machineUsesItem = false;
            isMachineUsesItem = true;
        } else {
            if (!machineUsesItem && isMachineUsesItem) {
                configComponent.addSupported(TransmissionType.ITEM);
                configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.NONE, InventoryUtils.EMPTY));
                configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.INPUT, getSlotsWithTier(tier)));
                configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.OUTPUT, getOutputSlotsWithTier(tier)));
                configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.ENERGY, new int[]{1}));
                configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.EXTRA, new int[]{4}));
                configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.INPUT_EXTRA, getInputExtraSlotsWithTier(tier)));
                configComponent.addOutput(TransmissionType.ITEM, new SideData(getInputOutputSlotsWithTier(tier), getInputOutputSlotsEnableWithTier(tier)));
                configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.OUTPUT_ENHANCED, getOutputSlotsWithTier(tier)));
                configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.INPUT_OUTPUT_ENHANCED, getInputOutputSlotsWithTier(tier), getInputOutputSlotsEnableWithTier(tier)));
                configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.INPUT_ENHANCED, getSlotsWithTier(tier)));
                configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.INPUT_ENHANCED_OUTPUT_ENHANCED, getInputOutputSlotsWithTier(tier), getInputOutputSlotsEnableWithTier(tier)));
                configComponent.addOutput(TransmissionType.ITEM, new SideData(DataType.INPUT_EXTRA_OUTPUT, getInputExtraOutputSlotsWithTier(tier), getInputExtraOutputSlotsEnableWithTier(tier)));
                configComponent.setConfig(TransmissionType.ITEM, new byte[]{4, 1, 1, 3, 1, 2});
                ejectorComponent.setOutputData(TransmissionType.ITEM, configComponent.getOutputs(TransmissionType.ITEM).get(2));
                ejectorComponent.setInputOutputData(TransmissionType.ITEM, configComponent.getOutputs(TransmissionType.ITEM).get(6));
                ejectorComponent.setInputExtraOutputData(TransmissionType.ITEM, configComponent.getOutputs(TransmissionType.ITEM).get(11));
                isMachineUsesItem = false;
            }
        }
        if (!GasMachine()) {
            configComponent.removeSupported(TransmissionType.GAS);
            ejectorComponent.removeOutputData(TransmissionType.GAS);
            ejectorComponent.removeInputOutputData(TransmissionType.GAS);
            machineUsesGAS = false;
            isMachineUsesGAS = true;
        } else {
            if (!machineUsesGAS && isMachineUsesGAS) {
                configComponent.addSupported(TransmissionType.GAS);
                configComponent.addOutput(TransmissionType.GAS, new SideData(DataType.NONE, InventoryUtils.EMPTY));
                configComponent.addOutput(TransmissionType.GAS, new SideData(DataType.INPUT, new int[]{1}));
                configComponent.addOutput(TransmissionType.GAS, new SideData(DataType.OUTPUT, new int[]{2}));
                configComponent.addOutput(TransmissionType.GAS, new SideData(new int[]{1, 2}, new boolean[]{false, true}));
                configComponent.setConfig(TransmissionType.GAS, new byte[]{1, 1, 1, 1, 1, 2});
                ejectorComponent.setOutputData(TransmissionType.GAS, configComponent.getOutputs(TransmissionType.GAS).get(2));
                ejectorComponent.setInputOutputData(TransmissionType.GAS, configComponent.getOutputs(TransmissionType.GAS).get(3));
                isMachineUsesGAS = false;
            }
        }

        if (!GasOutputMachine()) {
            configComponent.setEjecting(TransmissionType.GAS, false);
            configComponent.setCanEject(TransmissionType.GAS, false);
        } else {
            configComponent.setCanEject(TransmissionType.GAS, true);
        }

        if (!inputFluidMachine()) {
            configComponent.removeSupported(TransmissionType.FLUID);
            configComponent.removeInputConfig(TransmissionType.FLUID);
            machineUsesFluid = false;
            isMachineUsesFluid = true;
        } else {
            if (!machineUsesFluid && isMachineUsesFluid) {
                configComponent.addSupported(TransmissionType.FLUID);
                configComponent.setInputConfig(TransmissionType.FLUID);
                isMachineUsesFluid = false;
            }

        }

    }


    public void sortInventory() { //old
        if (sorting) {
            int[] inputSlots = getSlotsWithTier(tier);
            for (int i = 0; i < inputSlots.length; i++) {
                int slotID = inputSlots[i];
                ItemStack stack = inventory.get(slotID);
                int count = stack.getCount();
                ItemStack output = inventory.get(tier.processes + slotID);
                for (int j = i + 1; j < inputSlots.length; j++) {
                    int checkSlotID = inputSlots[j];
                    ItemStack checkStack = inventory.get(checkSlotID);
                    if (Math.abs(count - checkStack.getCount()) < 2 ||
                            !InventoryUtils.areItemsStackable(stack, checkStack)) {
                        continue;
                    }
                    //Output/Input will not match
                    // Only check if the input spot is empty otherwise assume it works
                    if (stack.isEmpty() && !inputProducesOutput(checkSlotID, checkStack, output, true) ||
                            checkStack.isEmpty() && !inputProducesOutput(slotID, stack, inventory.get(tier.processes + checkSlotID), true)) {
                        continue;
                    }
                    //Balance the two slots
                    int total = count + checkStack.getCount();
                    ItemStack newStack = stack.isEmpty() ? checkStack : stack;
                    inventory.set(slotID, StackUtils.size(newStack, (total + 1) / 2));
                    inventory.set(checkSlotID, StackUtils.size(newStack, total / 2));
                    markNoUpdateSync();
                    return;
                }
            }
        }
    }


    protected void AutomaticallyExtractItems(int dataIndex) {
        if (getWorld().isRemote || !canWork(5, 60)) {
            return;
        }
        InputItems(dataIndex);
    }

    private void InputItems(int dataIndex) {
        for (EnumFacing facing : configComponent.getSidesForData(TransmissionType.ITEM, facing, dataIndex)) {
            BlockPos offset = getPos().offset(facing);
            TileEntity te = getWorld().getTileEntity(offset);
            if (!InventoryUtils.isItemHandler(te, facing.getOpposite())) {
                continue;
            }
            IItemHandler itemHandler = InventoryUtils.getItemHandler(te, facing.getOpposite());
            if (itemHandler == null) {
                continue;
            }
            inputFromExternal(itemHandler);
        }
    }

    private synchronized void inputFromExternal(IItemHandler external) {
        boolean successAtLeastOnce = false;

        external:
        for (int i = external.getSlots() - 1; i >= 0; i--) {
            ItemStack externalStack = external.getStackInSlot(i);
            if (externalStack.isEmpty()) {
                continue;
            }

            for (int internalSlotId : getSlotsWithTier(tier)) {
                ItemStack internalStack = inventory.get(internalSlotId);
                int maxCanExtract = Math.min(externalStack.getCount(), externalStack.getMaxStackSize());
                if (internalStack.isEmpty()) {
                    // Extract external item and insert to internal.
                    if (!isItemValidForSlot(internalSlotId, externalStack)) {
                        continue;
                    }
                    ItemStack extracted = external.extractItem(i, maxCanExtract, false);
                    inventory.set(internalSlotId, extracted);
                    successAtLeastOnce = true;
                    // If there are no more items in the current slot, check the next external slot.
                    if (external.getStackInSlot(i).isEmpty()) {
                        continue external;
                    }
                    continue;
                }
                if (internalStack.getCount() >= internalStack.getMaxStackSize() || !matchStacks(internalStack, externalStack)) {
                    continue;
                }
                int extractAmt = Math.min(internalStack.getMaxStackSize() - internalStack.getCount(), maxCanExtract);
                // Extract external item and insert to internal.
                ItemStack extracted = external.extractItem(i, extractAmt, false);
                inventory.set(internalSlotId, copyStackWithSize(extracted, internalStack.getCount() + extracted.getCount()));
                successAtLeastOnce = true;
                // If there are no more items in the current slot, check the next external slot.
                if (external.getStackInSlot(i).isEmpty()) {
                    continue external;
                }
            }
        }

        if (successAtLeastOnce) {
            incrementSuccessCounter(60, 5);
            markNoUpdate();
        } else {
            decrementSuccessCounter();
        }
    }

    protected boolean canWork(int minWorkDelay, int maxWorkDelay) {
        if (inventoryChanged) {
            inventoryChanged = false;
            return true;
        }

        if (successCounter <= 0) {
            return ticksExisted % maxWorkDelay == 0;
        }
        int workDelay = Math.max(minWorkDelay, maxWorkDelay - (successCounter * 5));
        return ticksExisted % workDelay == 0;
    }

    protected void incrementSuccessCounter(int maxWorkDelay, int minWorkDelay) {
        int max = (maxWorkDelay - minWorkDelay) / 5;
        if (successCounter < max) {
            successCounter++;
        }
    }

    protected void decrementSuccessCounter() {
        if (successCounter > 0) {
            successCounter--;
        }
    }

    private void BetterEjectingItem() {
        if (delayTicks == 0 || MekanismConfig.current().mekce.ItemsEjectWithoutDelay.val()) {
            outputItems(7);
            outputItems(8);
            outputItems(10);
            if (!MekanismConfig.current().mekce.ItemsEjectWithoutDelay.val()) {
                delayTicks = MekanismConfig.current().mekce.ItemEjectionDelay.val();
            }
        } else {
            delayTicks--;
        }
    }

    private void outputItems(int dataIndex) {
        if (!configComponent.isEjecting(TransmissionType.ITEM)) {
            return;
        }
        for (EnumFacing facing : configComponent.getSidesForData(TransmissionType.ITEM, facing, dataIndex)) {
            BlockPos offset = getPos().offset(facing);
            TileEntity te = getWorld().getTileEntity(offset);
            if (!InventoryUtils.isItemHandler(te, facing.getOpposite())) {
                continue;
            }
            IItemHandler itemHandler = InventoryUtils.getItemHandler(te, facing.getOpposite());
            if (itemHandler == null) {
                continue;
            }
            try {
                outputToExternal(itemHandler);
            } catch (Exception e) {
                Mekanism.logger.error("Exception when insert item: ", e);
            }
        }
    }

    private synchronized void outputToExternal(IItemHandler external) {
        for (int externalSlotId = 0; externalSlotId < external.getSlots(); externalSlotId++) {
            ItemStack externalStack = external.getStackInSlot(externalSlotId);
            int slotLimit = external.getSlotLimit(externalSlotId);
            if (!externalStack.isEmpty() && externalStack.getCount() >= slotLimit) {
                continue;
            }
            for (int internalSlotId : getOutputSlotsWithTier(tier)) {
                ItemStack internalStack = inventory.get(internalSlotId);
                if (internalStack.isEmpty()) {
                    continue;
                }
                if (externalStack.isEmpty()) {
                    ItemStack notInserted = external.insertItem(externalSlotId, internalStack, false);
                    // Safeguard against Storage Drawers virtual slot
                    if (notInserted.getCount() == internalStack.getCount()) {
                        break;
                    }
                    inventory.set(internalSlotId, notInserted);
                    if (notInserted.isEmpty()) {
                        break;
                    }
                    continue;
                }
                if (!matchStacks(internalStack, externalStack)) {
                    continue;
                }
                // Extract internal item to external.
                ItemStack notInserted = external.insertItem(externalSlotId, internalStack, false);
                inventory.set(internalSlotId, notInserted);
                if (notInserted.isEmpty()) {
                    break;
                }
            }
        }

    }

    @Override
    public boolean getEnergySlot() {
        return inventory.get(1).isEmpty();
    }

    @Override
    public boolean getInputSlot() {
        return false;
    }

    @Override
    public boolean getOuputSlot() {
        return false;
    }

    @Override
    public boolean getExtraSlot() {
        return inventory.get(4).isEmpty();
    }

    /**
     * <p>Efficient, intelligent factory sequencing.</p>
     * <p><strong>Non-thread safe。</strong></p>
     * <p>In fact, it still has a lot of room for optimization, limited by the structure of the code, these features are sufficient.</p>
     */
    public static class FactoryInvSorter {
        private final TileEntityFactory factory;
        // Reusable List
        private final List<Tuple<MachineRecipe<?, ?, ?>, ItemStack>> vaildRecipeItemStackList = new ArrayList<>();
        // Reusable List
        private final List<ItemStack> invaildRecipeItemStackList = new ArrayList<>();
        // Reusable List
        private final List<ItemStack> sorted = new ArrayList<>();

        public FactoryInvSorter(TileEntityFactory factory) {
            this.factory = factory;
        }

        /**
         * <p>Add an ItemStack to the item list. </p
         *
         * @param willBeAdded The item that will be added to the list, or merged if the item already exists in the list and has not reached its maximum stack value.
         * @param stackList   The list of items.
         */
        private static void addItemStackToList(ItemStack willBeAdded, List<ItemStack> stackList) {
            boolean isAdded = false;
            for (ItemStack stack : stackList) {
                int maxStackSize = stack.getMaxStackSize();
                int invStackCount = willBeAdded.getCount();

                if (!matchStacks(stack, willBeAdded)) {
                    continue;
                }
                if (stack.getCount() >= maxStackSize) {
                    continue;
                }
                if (stack.getCount() + invStackCount > maxStackSize) {
                    int added = maxStackSize - stack.getCount();
                    stack.setCount(maxStackSize);
                    willBeAdded.setCount(invStackCount - added);
                    continue;
                }
                stack.setCount(stack.getCount() + invStackCount);
                isAdded = true;
            }
            if (!isAdded) {
                stackList.add(willBeAdded);
            }
        }

        /**
         * <p>Add an ItemStack to the item list. </p
         *
         * @param willBeAdded The item that will be added to the list, or merged if the item already exists in the list and has not reached the maximum stack value.
         * @param tupleList   The list of items.
         * @return Returns true if the addition was successful, or false if the list is full.
         */
        private static boolean addItemStackToTupleList(ItemStack willBeAdded, List<Tuple<MachineRecipe<?, ?, ?>, ItemStack>> tupleList) {
            for (Tuple<MachineRecipe<?, ?, ?>, ItemStack> collected : tupleList) {
                ItemStack stack = collected.getSecond();
                int maxStackSize = stack.getMaxStackSize();
                int invStackCount = willBeAdded.getCount();

                if (!matchStacks(stack, willBeAdded)) {
                    continue;
                }
                if (stack.getCount() >= maxStackSize) {
                    continue;
                }
                if (stack.getCount() + invStackCount > maxStackSize) {
                    int added = maxStackSize - stack.getCount();
                    stack.setCount(maxStackSize);
                    willBeAdded.setCount(invStackCount - added);
                    continue;
                }
                stack.setCount(stack.getCount() + invStackCount);
                return true;
            }

            return false;
        }

        /**
         * <h2>Sorting Process Introduction</h2>
         * <ol>
         *     <li>First detects if there is at least one item in the factory, and if there is no item, ends the process early.</li>
         *     <li>When the above condition is met, start sorting the contents into two lists (see {@link FactoryInvSorter#collectInvToList(int[] slotIds)} for the workflow.</li>
         *     <li>After sorting, execute {@link FactoryInvSorter#doSort(int)} to sort the results further and output the results to {@link FactoryInvSorter#sorted}.</li>
         *     <li>Finally, execute {@link FactoryInvSorter#applyResult(List sorted, int[] slotIds)} to apply the result to the machine inventory.</li>
         * </ol>
         */
        public void sort() {
            if (!factory.sorting || factory.getWorld().getTotalWorldTime() % 20 != 0) {
                return;
            }
            int[] slotIds = getSlotsWithTier(factory.tier);
            if (slotIds == null || !hasItem(slotIds)) {
                return;
            }

            vaildRecipeItemStackList.clear();
            invaildRecipeItemStackList.clear();
            sorted.clear();

            collectInvToList(slotIds);

            if (vaildRecipeItemStackList.size() + invaildRecipeItemStackList.size() >= slotIds.length) {
                //The collection size is bigger than equals slotIds size, end sort.
                return;
            }

            doSort(slotIds.length - (vaildRecipeItemStackList.size() + invaildRecipeItemStackList.size()));
            applyResult(sorted, slotIds);
        }

        /**
         * Check if at least one item exists in the mechanical item bar.
         *
         * @param slotIds The slot to check.
         * @return Returns true if at least one item is present, false if neither is present.
         */
        private boolean hasItem(int[] slotIds) {
            for (int slotId : slotIds) {
                if (factory.inventory.get(slotId) != ItemStack.EMPTY) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Apply items from the specified item list to the mechanical item bar, which <strong>must</strong> be larger or equal to the list size.
         *
         * @param sorted  Sorted item list
         * @param slotIds slotIdArray
         */
        private void applyResult(List<ItemStack> sorted, int[] slotIds) {
            if (sorted.isEmpty()) {
                return;
            }

            int index = 0;
            for (int slotId : slotIds) {
                factory.inventory.set(slotId, ItemStack.EMPTY);
                if (index >= sorted.size()) {
                    continue;
                }
                factory.inventory.set(slotId, sorted.get(index));
                index++;
            }
            sorted.clear();
            factory.markNoUpdateSync();
        }

        /**
         * <p>Sort the sorted results to return a list of items available for use by {@link FactoryInvSorter#applyResult(List itemStackList, int[] slotIds)}.</p>
         * <h2>Additional Features:</h2>
         * <ul>
         *     <li>Automatically calculates the product of the corresponding item and limits the number of divisions to the minimum number of recipes.</li>
         *     <li>Maximize the application of each item slot while implementing the features above.</li>
         *     <li>Minimize the number of slots occupied by invalid items and merge these invalid items where allowed.</li>
         * </ul>
         *
         * @param emptySlotAmount Available empty slots.
         */
        private void doSort(int emptySlotAmount) {
            int availableEmptySlotAmount = emptySlotAmount;
            for (Tuple<MachineRecipe<?, ?, ?>, ItemStack> recipeAndInput : vaildRecipeItemStackList) {
                MachineRecipe<?, ?, ?> recipe = recipeAndInput.getFirst();
                ItemStack invStack = recipeAndInput.getSecond();
                ItemStack recipeInput = TileEntityFactory.getRecipeInput(recipe);

                int invCount = invStack.getCount();
                int minCount = recipeInput.getCount();
                if (invCount <= minCount) {
                    sorted.add(invStack);
                    continue;
                }

                int splitCount = Math.min(availableEmptySlotAmount + 1, invCount / minCount);
                int countAfterSplit = invCount / splitCount;
                int extra = invCount % splitCount;

                sorted.add(copyStackWithSize(invStack, countAfterSplit + extra));

                while (splitCount > 1) {
                    sorted.add(copyStackWithSize(invStack, countAfterSplit));
                    availableEmptySlotAmount--;
                    splitCount--;
                }
            }
            sorted.addAll(invaildRecipeItemStackList);
        }

        /**
         * <p>Iterate through the contents of the mechanical item column with the incoming slot array and sort it into two item lists.</p>
         * <h2>Feature:</h2>
         * <ul>
         *     <li>Automatically determines if an item is ready to run a recipe and adds it to the list {@link FactoryInvSorter#vaildRecipeItemStackList}.</li>
         *     <li>Automatically determines items that cannot perform a recipe or are invalid and adds them to the list {@link FactoryInvSorter#invaildRecipeItemStackList}.</li>
         * </ul>
         *
         * @param slotIds slotIDArray
         */
        private void collectInvToList(int[] slotIds) {
            for (int slotId : slotIds) {
                ItemStack invTmp = factory.inventory.get(slotId);
                if (invTmp == ItemStack.EMPTY) {
                    continue;
                }
                ItemStack invStack = invTmp.copy();

                if (addItemStackToTupleList(invStack, vaildRecipeItemStackList)) {
                    continue;
                }
                ItemStack outStack = factory.inventory.get(slotId + factory.tier.processes);
                MachineRecipe<?, ?, ?> recipe = factory.getSlotRecipe(slotId, invStack, outStack, true);
                if (recipe != null) {
                    vaildRecipeItemStackList.add(new Tuple<>(recipe, invStack));
                } else {
                    addItemStackToList(invStack, invaildRecipeItemStackList);
                }
            }
        }
    }

    @Override
    public int getBlockGuiID(Block block, int metadata) {
        return MachineType.get(block, metadata) != null ? MachineType.get(block, metadata).guiId : -1;
    }

    //TODO:如果是升级，则取消辐射排放
    @Override
    protected boolean shouldDumpRadiation() {
        return isUpgrade;
    }
}
