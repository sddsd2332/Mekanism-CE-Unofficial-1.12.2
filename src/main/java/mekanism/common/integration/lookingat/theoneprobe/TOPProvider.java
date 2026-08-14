package mekanism.common.integration.lookingat.theoneprobe;

import mcjty.theoneprobe.api.*;
import mcjty.theoneprobe.api.IProbeConfig.ConfigMode;
import mekanism.api.gas.GasStack;
import mekanism.common.Mekanism;
import mekanism.common.block.BlockBounding;
import mekanism.common.integration.lookingat.LookingAtHelper;
import mekanism.common.integration.lookingat.LookingAtUtils;
import mekanism.common.integration.lookingat.theoneprobe.TOPChemicalElement.GasElement;
import mekanism.common.integration.lookingat.theoneprobe.TOPTankListElement.FluidListElement;
import mekanism.common.integration.lookingat.theoneprobe.TOPTankListElement.GasListElement;
import mekanism.common.util.WorldUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings("unused")//IMC bound
public class TOPProvider implements Function<ITheOneProbe, Void>, IProbeInfoProvider {

    private BooleanSupplier displayFluidTanks;
    private Supplier<ConfigMode> tankMode = () -> ConfigMode.EXTENDED;
    static int ENERGY_ELEMENT_ID;
    static int FLUID_ELEMENT_ID;
    static int GAS_ELEMENT_ID;
    static int FLUID_LIST_ELEMENT_ID;
    static int GAS_LIST_ELEMENT_ID;

    @Override
    public Void apply(ITheOneProbe probe) {
        probe.registerProvider(this);
        probe.registerProbeConfigProvider(ProbeConfigProvider.INSTANCE);
        ENERGY_ELEMENT_ID = probe.registerElementFactory(TOPEnergyElement::new);
        FLUID_ELEMENT_ID = probe.registerElementFactory(TOPFluidElement::new);
        GAS_ELEMENT_ID = probe.registerElementFactory(GasElement::new);
        FLUID_LIST_ELEMENT_ID = probe.registerElementFactory(FluidListElement::new);
        GAS_LIST_ELEMENT_ID = probe.registerElementFactory(GasListElement::new);
        //Grab the default view settings
        IProbeConfig probeConfig = probe.createProbeConfig();
        displayFluidTanks = () -> probeConfig.getTankMode() > 0;
        tankMode = probeConfig::getShowTankSetting;
        return null;
    }

    @Override
    public String getID() {
        return Mekanism.MODID;
    }


    @Override
    public void addProbeInfo(ProbeMode mode, IProbeInfo info, EntityPlayer player, World world, IBlockState blockState, IProbeHitData data) {
        BlockPos pos = data.getPos();
        if (blockState.getBlock() instanceof BlockBounding) {
            //If we are a bounding block that has a position set, redirect the probe to the main location
            BlockPos mainPos = BlockBounding.getMainBlockPos(world, pos);
            if (mainPos != null) {
                pos = mainPos;
                //If we end up needing the blockstate at some point lower down, then uncomment this line
                // until we do though there is no point in bothering to query the world to get it
                //blockState = world.getBlockState(mainPos);
            }
        }
        TileEntity energyTile = WorldUtils.getTileEntity(world, pos);
        if (energyTile != null) {
            LookingAtUtils.addInfo(new TOPLookingAtHelper(info), energyTile, displayTanks(mode), displayFluidTanks.getAsBoolean());
        }
    }

    private boolean displayTanks(ProbeMode mode) {
        return switch (tankMode.get()) {
            case NOT -> false;//Don't display tanks
            case NORMAL -> mode == ProbeMode.NORMAL;
            case EXTENDED -> true;
        };
    }


    private static class TOPLookingAtHelper implements LookingAtHelper {

        private final IProbeInfo info;

        public TOPLookingAtHelper(IProbeInfo info) {
            this.info = info;
        }

        @Override
        public void addText(String text) {
            info.text(TextStyleClass.NAME + text);
        }

        @Override
        public void addEnergyElement(double energy, double maxEnergy) {
            info.element(new TOPEnergyElement(energy, maxEnergy));
        }

        @Override
        public void addFluidElement(FluidStack stored, int capacity) {
            info.element(new TOPFluidElement(stored, capacity));
        }

        @Override
        public void addChemicalElement(GasStack stored, int capacity) {
            info.element(new GasElement(stored, capacity));
        }

        @Override
        public void addFluidElements(FluidStack[] stored, int[] capacities, int maxDisplayed) {
            if (stored.length > 0) {
                info.element(new FluidListElement(stored, capacities, maxDisplayed));
            }
        }

        @Override
        public void addChemicalElements(GasStack[] stored, int[] capacities, int maxDisplayed) {
            if (stored.length > 0) {
                info.element(new GasListElement(stored, capacities, maxDisplayed));
            }
        }

    }


}
