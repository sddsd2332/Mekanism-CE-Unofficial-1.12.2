package mekanism.generators.common;

import mekanism.common.Mekanism;
import mekanism.common.CommonProxy;
import mekanism.common.base.IGuiProvider;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.ContainerNull;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.generators.common.block.states.BlockStateGenerator.GeneratorType;
import mekanism.generators.common.inventory.container.*;
import mekanism.generators.common.tile.*;
import mekanism.generators.common.tile.fission.*;
import mekanism.generators.common.tile.reactor.*;
import mekanism.generators.common.tile.turbine.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;

/**
 * Common proxy for the Mekanism Generators module.
 *
 * @author AidanBrady
 */
public class GeneratorsCommonProxy implements IGuiProvider {

    private static void registerTileEntity(Class<? extends TileEntity> clazz, String name) {
        GameRegistry.registerTileEntity(clazz, new ResourceLocation(MekanismGenerators.MODID, name));
    }

    /**
     * Register normal tile entities
     */
    public void registerTileEntities() {
        registerTileEntity(TileEntityAdvancedSolarGenerator.class, "advanced_solar_generator");
        registerTileEntity(TileEntityBioGenerator.class, "bio_generator");
        registerTileEntity(TileEntityElectromagneticCoil.class, "electromagnetic_coil");
        registerTileEntity(TileEntityGasGenerator.class, "gas_generator");
        registerTileEntity(TileEntityHeatGenerator.class, "heat_generator");
        registerTileEntity(TileEntityFissionReactorCasing.class, "fission_reactor_casing");
        registerTileEntity(TileEntityFissionReactorPort.class, "fission_reactor_port");
        registerTileEntity(TileEntityFissionFuelAssembly.class, "fission_fuel_assembly");
        registerTileEntity(TileEntityControlRodAssembly.class, "control_rod_assembly");
        registerTileEntity(TileEntityFissionReactorLogicAdapter.class, "fission_reactor_logic_adapter");
        registerTileEntity(TileEntityReactorController.class, "reactor_controller");
        registerTileEntity(TileEntityReactorFrame.class, "reactor_frame");
        registerTileEntity(TileEntityReactorGlass.class, "reactor_glass");
        registerTileEntity(TileEntityReactorLaserFocusMatrix.class, "reactor_laser_focus");
        registerTileEntity(TileEntityReactorLogicAdapter.class, "reactor_logic_adapter");
        registerTileEntity(TileEntityReactorPort.class, "reactor_port");
        registerTileEntity(TileEntityRotationalComplex.class, "rotational_complex");
        registerTileEntity(TileEntitySaturatingCondenser.class, "saturating_condenser");
        registerTileEntity(TileEntitySolarGenerator.class, "solar_generator");
        registerTileEntity(TileEntityTurbineCasing.class, "turbine_casing");
        registerTileEntity(TileEntityTurbineRotor.class, "turbine_rod");
        registerTileEntity(TileEntityTurbineValve.class, "turbine_valve");
        registerTileEntity(TileEntityTurbineVent.class, "turbine_vent");
        registerTileEntity(TileEntityWindGenerator.class, "wind_turbine");
    }

    /**
     * Register tile entities that have special models. Overwritten in client to register TESRs.
     */
    public void registerTESRs() {
    }

    /**
     * Register and load client-only item render information.
     */
    public void registerItemRenders() {
    }

    /**
     * Register and load client-only block render information.
     */
    public void registerBlockRenders() {
    }

    public void preInit() {
    }

    /**
     * Set and load the mod's common configuration properties.
     */
    public void loadConfiguration() {
        MekanismConfig.local().generators.load(Mekanism.configurationgenerators);
        setGasGeneratorMaxEnergy();
        if (Mekanism.configurationgenerators.hasChanged()) {
            Mekanism.configurationgenerators.save();
        }
    }

    protected void setGasGeneratorMaxEnergy() {
        GeneratorType.GAS_GENERATOR.maxEnergy = MekanismConfig.local().general.FROM_H2.val() * 100;
    }

    @Override
    public Object getClientGui(int ID, EntityPlayer player, World world, BlockPos pos) {
        return null;
    }

    @Override
    public Container getServerGui(int ID, EntityPlayer player, World world, BlockPos pos) {
        TileEntity tileEntity = world.getTileEntity(pos);
        if (!CommonProxy.canOpenServerGui(player, tileEntity)) {
            return null;
        }
        return switch (ID) {
            case 0 -> new ContainerHeatGenerator(player.inventory, (TileEntityHeatGenerator) tileEntity);
            case 1 -> new ContainerSolarGenerator(player.inventory, (TileEntitySolarGenerator) tileEntity);
            case 3 -> new ContainerGasGenerator(player.inventory, (TileEntityGasGenerator) tileEntity);
            case 4 -> new ContainerBioGenerator(player.inventory, (TileEntityBioGenerator) tileEntity);
            case 5 -> new ContainerWindGenerator(player.inventory, (TileEntityWindGenerator) tileEntity);
            case 6 -> new ContainerIndustrialTurbine(player.inventory, (TileEntityTurbineCasing) tileEntity);
            case 7 -> new ContainerNull(player, (TileEntityTurbineCasing) tileEntity);
            case 10 -> new ContainerReactorController(player.inventory, (TileEntityReactorController) tileEntity);
            case 11, 12, 13, 15 -> new ContainerNull(player, (TileEntityContainerBlock) tileEntity);
            case 16 -> new ContainerFissionReactor(player.inventory, (TileEntityFissionReactorCasing) tileEntity);
            case 17, 18 -> new ContainerNull(player, (TileEntityContainerBlock) tileEntity);
            default -> null;
        };

    }
}
