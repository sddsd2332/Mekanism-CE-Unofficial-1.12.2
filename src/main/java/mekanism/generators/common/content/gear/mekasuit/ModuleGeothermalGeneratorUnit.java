package mekanism.generators.common.content.gear.mekasuit;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IHeatTransfer;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.gear.ICustomModule;
import mekanism.api.gear.IModule;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.IFluidBlock;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Map;


@ParametersAreNonnullByDefault
public class ModuleGeothermalGeneratorUnit implements ICustomModule<ModuleGeothermalGeneratorUnit> {

    @Override
    public void tickServer(IModule<ModuleGeothermalGeneratorUnit> module, EntityPlayer player) {
        IEnergyContainer energyContainer = module.getEnergyContainer();
        if (energyContainer != null && energyContainer.getNeeded() != 0) {
            double highestScaledDegrees = 0;
            double legHeight = player.isSneaking() ? 0.6 : 0.7;
            Map<Block, MekanismUtils.FluidInDetails> fluidsIn = MekanismUtils.getFluidsIn(player, bb -> new AxisAlignedBB(bb.minX, bb.minY, bb.minZ, bb.maxX, Math.min(bb.minY + legHeight, bb.maxY), bb.maxZ), Material.LAVA);
            for (Map.Entry<Block, MekanismUtils.FluidInDetails> entry : fluidsIn.entrySet()) {
                MekanismUtils.FluidInDetails details = entry.getValue();
                double height = details.getMaxHeight();
                if (height < 0.25) {
                    //Skip fluids that we are barely submerged in
                    continue;
                }
                double temperature = 0;
                List<BlockPos> positions = details.getPositions();
                for (BlockPos position : positions) {
                    IBlockState iblockstate = entry.getKey().getDefaultState();
                    Fluid fluid = getFluid(iblockstate);
                    if (fluid != null) {
                        temperature += fluid.getTemperature(player.world, position);
                    }
                }
                temperature /= positions.size();
                if (temperature > IHeatTransfer.AMBIENT_TEMP) {
                    //If the temperature is above the ambient temperature, calculate how many degrees above
                    // and factor in how much of the legs are submerged
                    double scaledDegrees = (temperature - IHeatTransfer.AMBIENT_TEMP) * height / legHeight;
                    if (scaledDegrees > highestScaledDegrees) {
                        highestScaledDegrees = scaledDegrees;
                    }
                }
            }
            if (highestScaledDegrees > 0 || player.isBurning()) {
                if (highestScaledDegrees < 200 && player.isBurning()) {
                    //Treat fire as having a temperature of ~500K, this is on the cooler side of what fire tends to
                    // be but should be good enough for factoring in how much a heat adapter would be able to transfer
                    highestScaledDegrees = 200;
                }
                //Insert energy
                double rate = MekanismConfig.current().meka.mekaSuitGeothermalChargingRate.val() * module.getInstalledCount() * highestScaledDegrees;
                energyContainer.insert(rate, Action.EXECUTE, AutomationType.MANUAL);
            }
        }
    }

    @Nullable
    @Override
    public ModuleDamageAbsorbInfo getDamageAbsorbInfo(IModule<ModuleGeothermalGeneratorUnit> module, DamageSource damageSource) {
        if (damageSource.isFireDamage()) {
            //Scale the amount absorbed by how many modules are installed out of the possible number installed
            float ratio = MekanismConfig.current().meka.mekaSuitHeatDamageReductionRatio.val() * (module.getInstalledCount() / (float) module.getData().getMaxStackSize());
            return new ModuleDamageAbsorbInfo(() -> ratio, 0);
        }
        return null;
    }


    private static Fluid getFluid(IBlockState state) {
        Block block = state.getBlock();

        if (block instanceof IFluidBlock) {
            return ((IFluidBlock) block).getFluid();
        }
        if (block instanceof BlockLiquid) {
            if (state.getMaterial() == Material.WATER) {
                return FluidRegistry.WATER;
            }
            if (state.getMaterial() == Material.LAVA) {
                return FluidRegistry.LAVA;
            }
        }
        return null;
    }
}
