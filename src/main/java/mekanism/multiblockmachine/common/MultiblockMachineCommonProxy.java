package mekanism.multiblockmachine.common;

import mekanism.common.Mekanism;
import mekanism.common.CommonProxy;
import mekanism.common.base.IGuiProvider;
import mekanism.common.config.MekanismConfig;
import mekanism.multiblockmachine.common.inventory.container.*;
import mekanism.multiblockmachine.common.tile.generator.TileEntityLargeGasGenerator;
import mekanism.multiblockmachine.common.tile.generator.TileEntityLargeWindGenerator;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeChemicalInfuser;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeChemicalWasher;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeElectrolyticSeparator;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeSolarNeutronActivator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class MultiblockMachineCommonProxy implements IGuiProvider {

    private static void registerTileEntity(Class<? extends TileEntity> clazz, String name) {
        GameRegistry.registerTileEntity(clazz, new ResourceLocation(MekanismMultiblockMachine.MODID, name));
    }

    public void registerTileEntities() {
        registerTileEntity(TileEntityLargeElectrolyticSeparator.class, "large_electrolytic_separator");
        registerTileEntity(TileEntityLargeChemicalInfuser.class, "large_chemical_infuser");
        registerTileEntity(TileEntityLargeChemicalWasher.class, "large_chemical_washer");
        registerTileEntity(TileEntityLargeWindGenerator.class,"large_wind_generator");
        registerTileEntity(TileEntityLargeGasGenerator.class,"large_gas_generator");
        registerTileEntity(TileEntityLargeSolarNeutronActivator.class,"large_solar_neutron_activator");
    }

    public void registerTESRs() {
    }

    public void registerItemRenders() {
    }

    public void registerBlockRenders() {
    }

    public void init() {
    }

    public void preInit() {
    }

    public void loadConfiguration() {
        MekanismConfig.local().multiblock.load(Mekanism.configurationMultiblockMachine);
        if (Mekanism.configurationMultiblockMachine.hasChanged()) {
            Mekanism.configurationMultiblockMachine.save();
        }
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
            case 0 -> new ContainerLargeElectrolyticSeparator(player.inventory, (TileEntityLargeElectrolyticSeparator) tileEntity);
            case 1 -> new ContainerLargeChemicalInfuser(player.inventory, (TileEntityLargeChemicalInfuser) tileEntity);
            case 2 -> new ContainerLargeChemicalWasher(player.inventory, (TileEntityLargeChemicalWasher) tileEntity);
            case 3 -> new ContainerLargeWindGenerator(player.inventory, (TileEntityLargeWindGenerator) tileEntity);
            case 4 -> new ContainerLargeGasGenerator(player.inventory,(TileEntityLargeGasGenerator) tileEntity);
            case 5 -> new ContainerLargeSolarNeutronActivator(player.inventory,(TileEntityLargeSolarNeutronActivator) tileEntity);
            default -> null;
        };
    }

    @Override
    public boolean isValidServerGui(int ID, TileEntity tile) {
        return switch (ID) {
            case 0 -> tile instanceof TileEntityLargeElectrolyticSeparator;
            case 1 -> tile instanceof TileEntityLargeChemicalInfuser;
            case 2 -> tile instanceof TileEntityLargeChemicalWasher;
            case 3 -> tile instanceof TileEntityLargeWindGenerator;
            case 4 -> tile instanceof TileEntityLargeGasGenerator;
            case 5 -> tile instanceof TileEntityLargeSolarNeutronActivator;
            default -> false;
        };
    }


}
