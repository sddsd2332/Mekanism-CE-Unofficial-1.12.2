package mekanism.multiblockmachine.client.integration.jei;

import mekanism.client.jei.GuiElementHandler;
import mekanism.client.jei.MekanismJEI;
import mekanism.multiblockmachine.common.registries.MultiblockMachineBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.ISubtypeRegistry;
import mezz.jei.api.JEIPlugin;
import net.minecraft.block.Block;
import net.minecraft.item.Item;

@JEIPlugin
public class MultiblockMachineJEI implements IModPlugin {

    @Override
    public void registerItemSubtypes(ISubtypeRegistry registry) {
        registry(registry, MultiblockMachineBlocks.LargeElectrolyticSeparator);
        registry(registry, MultiblockMachineBlocks.LargeChemicalInfuser);
        registry(registry, MultiblockMachineBlocks.LargeChemicalWasher);
        registry(registry, MultiblockMachineBlocks.LargeWindGenerator);
        registry(registry, MultiblockMachineBlocks.LargeGasGenerator);
        registry(registry, MultiblockMachineBlocks.LargeSolarNeutronActivator);
    }

    public void registry(ISubtypeRegistry registry, Block block) {
        registry.registerSubtypeInterpreter(Item.getItemFromBlock(block), MekanismJEI.NBT_INTERPRETER);
    }


    @Override
    public void register(IModRegistry registry) {
        registry.addAdvancedGuiHandlers(new GuiElementHandler());
        MultiblockRecipeRegistryHelper.registerLargeSeparator(registry);
        MultiblockRecipeRegistryHelper.registerLargeChemicalInfuser(registry);
        MultiblockRecipeRegistryHelper.registerLargeChemicalWasher(registry);
        MultiblockRecipeRegistryHelper.registerGasStackFlueToEnergyRecipe(registry);
        MultiblockRecipeRegistryHelper.registerLargeSolarNeutronActivator(registry);
    }

}
