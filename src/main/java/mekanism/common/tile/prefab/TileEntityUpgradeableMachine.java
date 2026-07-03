package mekanism.common.tile.prefab;

import mekanism.api.gas.GasStack;
import mekanism.common.InfuseStorage;
import mekanism.common.MekanismBlocks;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.base.IUpgradeableTile;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.machines.MachineRecipe;
import mekanism.common.recipe.outputs.MachineOutput;
import mekanism.common.tier.BaseTier;
import mekanism.common.upgrade.FactoryUpgradeData;
import mekanism.common.upgrade.IUpgradeData;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 可用升级的机器类型 ，一般用于工厂
 */

public abstract class TileEntityUpgradeableMachine<INPUT extends MachineInput<INPUT>, OUTPUT extends MachineOutput<OUTPUT>, RECIPE extends MachineRecipe<INPUT, OUTPUT, RECIPE>> extends
        TileEntityBasicMachine<INPUT, OUTPUT, RECIPE> implements IUpgradeableTile {


    /**
     * The foundation of all machines - a simple tile entity with a facing, active state, initialized state, sound effect, and animated texture.
     *
     * @param soundPath         - location of the sound effect
     * @param type              - the type of this machine
     * @param baseTicksRequired - how many ticks it takes to run a cycle
     */
    public TileEntityUpgradeableMachine(String soundPath, MachineType type, int upgradeSlot, int baseTicksRequired) {
        super(soundPath, type, upgradeSlot, baseTicksRequired);
    }

    public TileEntityUpgradeableMachine(String soundPath, MachineType type, int upgradeSlot, int baseTicksRequired, List<RecipeError> trackedErrorTypes) {
        super(soundPath, type, upgradeSlot, baseTicksRequired, trackedErrorTypes);
    }

    public boolean isUpgrade = true;

    protected long getSavedUsedSoFarForUpgrade() {
        return 0;
    }

    @Override
    public boolean canInstallUpgrade(BaseTier upgradeTier) {
        return upgradeTier == BaseTier.BASIC && getRecipeTypeForUpgrade() != null;
    }

    @Nullable
    private RecipeType getRecipeTypeForUpgrade() {
        MachineType machineType = MachineType.get(getBlockType(), getBlockMetadata());
        if (machineType == null) {
            return null;
        }
        return switch (machineType) {
            case ENERGIZED_SMELTER -> RecipeType.SMELTING;
            case ENRICHMENT_CHAMBER -> RecipeType.ENRICHING;
            case CRUSHER -> RecipeType.CRUSHING;
            case OSMIUM_COMPRESSOR -> RecipeType.COMPRESSING;
            case COMBINER -> RecipeType.COMBINING;
            case PURIFICATION_CHAMBER -> RecipeType.PURIFYING;
            case CHEMICAL_INJECTION_CHAMBER -> RecipeType.INJECTING;
            case METALLURGIC_INFUSER -> RecipeType.INFUSING;
            case PRECISION_SAWMILL -> RecipeType.SAWING;
            case STAMPING -> RecipeType.STAMPING;
            case ROLLING -> RecipeType.ROLLING;
            case BRUSHED -> RecipeType.BRUSHED;
            case TURNING -> RecipeType.TURNING;
            case ALLOY -> RecipeType.AllOY;
            case CELL_EXTRACTOR -> RecipeType.EXTRACTOR;
            case CELL_SEPARATOR -> RecipeType.SEPARATOR;
            case ORGANIC_FARM -> RecipeType.FARM;
            case RECYCLER -> RecipeType.RECYCLER;
            case PRESSURIZED_REACTION_CHAMBER -> RecipeType.PRC;
            case ANTIPROTONIC_NUCLEOSYNTHESIZER -> RecipeType.NUCLEOSYNTHESIZER;
            default -> null;
        };
    }

    @Nullable
    @Override
    public IBlockState getUpgradeResult(BaseTier upgradeTier) {
        return canInstallUpgrade(upgradeTier) ? MekanismBlocks.MachineBlock.getStateFromMeta(5) : null;
    }

    @Override
    public void prepareForUpgrade() {
        isUpgrade = false;
    }

    @Nullable
    @Override
    public IUpgradeData getUpgradeData(BaseTier upgradeTier) {
        RecipeType recipeType = getRecipeTypeForUpgrade();
        if (upgradeTier != BaseTier.BASIC || recipeType == null) {
            return null;
        }
        return new FactoryUpgradeData(upgradeTier, facing, clientFacing, ticker, redstone, redstoneLastTick, doAutoSync, electricityStored.get(), isActive,
              prevEnergy, getControlType(), writeUpgradeComponentData(), recipeType, false, new int[]{operatingTicks}, new long[]{getSavedUsedSoFarForUpgrade()},
              getInfusionForUpgrade(), getEnergySlotForUpgrade(), getExtraSlotForUpgrade(), new ItemStack[]{getInputSlotForUpgrade()},
              new ItemStack[]{getOutputSlotForUpgrade()}, new ItemStack[]{getSecondaryOutputSlotForUpgrade()}, getInputGasForUpgrade(), getOutputGasForUpgrade(),
              getInputFluidForUpgrade());
    }

    @Nonnull
    private NBTTagCompound writeUpgradeComponentData() {
        NBTTagCompound componentData = new NBTTagCompound();
        upgradeComponent.write(componentData);
        configComponent.write(componentData);
        ejectorComponent.write(componentData);
        securityComponent.write(componentData);
        return componentData;
    }

    @Nonnull
    protected InfuseStorage getInfusionForUpgrade() {
        return new InfuseStorage();
    }

    @Nonnull
    protected ItemStack getEnergySlotForUpgrade() {
        return ItemStack.EMPTY;
    }

    @Nonnull
    protected ItemStack getInputSlotForUpgrade() {
        return ItemStack.EMPTY;
    }

    @Nonnull
    protected ItemStack getExtraSlotForUpgrade() {
        return ItemStack.EMPTY;
    }

    @Nonnull
    protected ItemStack getOutputSlotForUpgrade() {
        return ItemStack.EMPTY;
    }

    @Nonnull
    protected ItemStack getSecondaryOutputSlotForUpgrade() {
        return ItemStack.EMPTY;
    }

    @Nullable
    protected GasStack getInputGasForUpgrade() {
        return null;
    }

    @Nullable
    protected GasStack getOutputGasForUpgrade() {
        return null;
    }

    @Nullable
    protected FluidStack getInputFluidForUpgrade() {
        return null;
    }

    @Override
    protected boolean shouldDumpRadiation() {
        return isUpgrade;
    }

}
