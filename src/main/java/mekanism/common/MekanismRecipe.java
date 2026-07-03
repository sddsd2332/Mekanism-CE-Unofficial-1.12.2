package mekanism.common;


import mekanism.api.EnumColor;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.OreGas;
import mekanism.api.infuse.InfuseObject;
import mekanism.api.infuse.InfuseRegistry;
import mekanism.api.infuse.InfuseType;
import mekanism.common.block.states.BlockStateMachine;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.IModuleContainerItem;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.util.StackUtils;
import mekanism.common.util.StorageUtils;
import net.minecraft.block.BlockCrops;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.List;


public class MekanismRecipe {

    /**
     * Adds all in-game crafting, smelting and machine recipes.
     */
    public static void addRecipes() {
        //Furnace Recipes
        GameRegistry.addSmelting(new ItemStack(MekanismBlocks.OreBlock, 1, 0), new ItemStack(MekanismItems.Ingot, 1, 1), 1.0F);
        GameRegistry.addSmelting(new ItemStack(MekanismBlocks.OreBlock, 1, 1), new ItemStack(MekanismItems.Ingot, 1, 5), 1.0F);
        GameRegistry.addSmelting(new ItemStack(MekanismBlocks.OreBlock, 1, 2), new ItemStack(MekanismItems.Ingot, 1, 6), 1.0F);
        GameRegistry.addSmelting(new ItemStack(MekanismBlocks.OreBlock, 1, 4), new ItemStack(MekanismItems.Ingot, 1, 7), 1.0F);
        GameRegistry.addSmelting(new ItemStack(MekanismBlocks.OreBlock, 1, 5), new ItemStack(MekanismItems.Ingot, 1, 8), 1.0F);
        GameRegistry.addSmelting(new ItemStack(MekanismItems.Dust, 1, Resource.OSMIUM.ordinal()), new ItemStack(MekanismItems.Ingot, 1, 1), 0.0F);
        GameRegistry.addSmelting(new ItemStack(MekanismItems.Dust, 1, Resource.IRON.ordinal()), new ItemStack(Items.IRON_INGOT), 0.0F);
        GameRegistry.addSmelting(new ItemStack(MekanismItems.Dust, 1, Resource.GOLD.ordinal()), new ItemStack(Items.GOLD_INGOT), 0.0F);
        GameRegistry.addSmelting(new ItemStack(MekanismItems.OtherDust, 1, 1), new ItemStack(MekanismItems.Ingot, 1, 4), 0.0F);
        GameRegistry.addSmelting(new ItemStack(MekanismItems.Dust, 1, Resource.COPPER.ordinal()), new ItemStack(MekanismItems.Ingot, 1, 5), 0.0F);
        GameRegistry.addSmelting(new ItemStack(MekanismItems.Dust, 1, Resource.TIN.ordinal()), new ItemStack(MekanismItems.Ingot, 1, 6), 0.0F);
        GameRegistry.addSmelting(new ItemStack(MekanismItems.Dust, 1, Resource.LEAD.ordinal()), new ItemStack(MekanismItems.Ingot, 1, 7), 0.0F);
        GameRegistry.addSmelting(new ItemStack(MekanismItems.Dust, 1, Resource.URANIUM.ordinal()), new ItemStack(MekanismItems.Ingot, 1, 8), 0.0F);

        //Enrichment Chamber Recipes
        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.ENRICHMENT_CHAMBER)) {
            RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(Blocks.OBSIDIAN), new ItemStack(MekanismItems.OtherDust, 4, 6));
            RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(Items.COAL, 1, 0), new ItemStack(MekanismItems.CompressedCarbon));
            RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(Items.COAL, 1, 1), new ItemStack(MekanismItems.CompressedCarbon));
            RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(Items.REDSTONE), new ItemStack(MekanismItems.CompressedRedstone));
            RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(Blocks.MOSSY_COBBLESTONE), new ItemStack(Blocks.COBBLESTONE));
            RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(Blocks.STONE), new ItemStack(Blocks.STONEBRICK, 1, 2));
            RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(Blocks.SAND), new ItemStack(Blocks.GRAVEL));
            RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(Blocks.GRAVEL), new ItemStack(Blocks.COBBLESTONE));
            RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(Items.GUNPOWDER), new ItemStack(Items.FLINT));
            RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(Blocks.STONEBRICK, 1, 2), new ItemStack(Blocks.STONEBRICK, 1, 0));
            RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(Blocks.STONEBRICK, 1, 0), new ItemStack(Blocks.STONEBRICK, 1, 3));
            RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(Blocks.STONEBRICK, 1, 1), new ItemStack(Blocks.STONEBRICK, 1, 0));
            RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(Blocks.GLOWSTONE), new ItemStack(Items.GLOWSTONE_DUST, 4));
            RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(Blocks.CLAY), new ItemStack(Items.CLAY_BALL, 4));
            RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(MekanismBlocks.SaltBlock), new ItemStack(MekanismItems.Salt, 4));
            RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(Items.DIAMOND), new ItemStack(MekanismItems.CompressedDiamond));
            RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(MekanismItems.HDPE_PELLET, 3), new ItemStack(MekanismItems.HDPE_SHEET, 1));
            RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(MekanismItems.OtherDust, 1, 7), new ItemStack(MekanismItems.FluoriteClump, 1));
            RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(Items.COAL, 1, 1), new ItemStack(MekanismItems.OtherDust, 1, 8));
            RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(MekanismBlocks.OreBlock, 1, 3), new ItemStack(MekanismItems.FluoriteClump, 6));
            OreDictionary.getOres("ingotUranium", false).forEach(ingotUranium -> RecipeHandler.addEnrichmentChamberRecipe(StackUtils.size(ingotUranium, 1), new ItemStack(MekanismItems.YellowCakeUranium, 2)));

            for (int i = 0; i < EnumColor.DYES.length; i++) {
                RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(MekanismBlocks.PlasticBlock, 1, i), new ItemStack(MekanismBlocks.SlickPlasticBlock, 1, i));
            }
        }

        //Combiner recipes
        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.COMBINER)) {
            RecipeHandler.addCombinerRecipe(new ItemStack(Items.FLINT), new ItemStack(Blocks.COBBLESTONE), new ItemStack(Blocks.GRAVEL));
            RecipeHandler.addCombinerRecipe(new ItemStack(Items.COAL, 3), new ItemStack(Blocks.COBBLESTONE), new ItemStack(Blocks.COAL_ORE));
        }

        //Osmium Compressor Recipes
        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.OSMIUM_COMPRESSOR)) {
            RecipeHandler.addOsmiumCompressorRecipe(new ItemStack(Items.GLOWSTONE_DUST), new ItemStack(MekanismItems.Ingot, 1, 3));
            RecipeHandler.addOsmiumCompressorRecipe(new ItemStack(MekanismItems.Scrap, 64), new ItemStack(MekanismItems.ScrapBox, 1));
        }

        //Crusher Recipes
        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.CRUSHER)) {
            RecipeHandler.addCrusherRecipe(new ItemStack(Items.IRON_INGOT), new ItemStack(MekanismItems.Dust, 1, Resource.IRON.ordinal()));
            RecipeHandler.addCrusherRecipe(new ItemStack(Items.GOLD_INGOT), new ItemStack(MekanismItems.Dust, 1, Resource.GOLD.ordinal()));
            RecipeHandler.addCrusherRecipe(new ItemStack(Blocks.GRAVEL), new ItemStack(Blocks.SAND));
            RecipeHandler.addCrusherRecipe(new ItemStack(Blocks.STONE), new ItemStack(Blocks.COBBLESTONE));
            RecipeHandler.addCrusherRecipe(new ItemStack(Blocks.COBBLESTONE), new ItemStack(Blocks.GRAVEL));
            RecipeHandler.addCrusherRecipe(new ItemStack(Blocks.STONEBRICK, 1, 2), new ItemStack(Blocks.STONE));
            RecipeHandler.addCrusherRecipe(new ItemStack(Blocks.STONEBRICK, 1, 0), new ItemStack(Blocks.STONEBRICK, 1, 2));
            RecipeHandler.addCrusherRecipe(new ItemStack(Blocks.STONEBRICK, 1, 3), new ItemStack(Blocks.STONEBRICK, 1, 0));
            RecipeHandler.addCrusherRecipe(new ItemStack(Items.FLINT), new ItemStack(Items.GUNPOWDER));
            RecipeHandler.addCrusherRecipe(new ItemStack(Blocks.SANDSTONE), new ItemStack(Blocks.SAND, 2));
            RecipeHandler.addCrusherRecipe(new ItemStack(Blocks.RED_SANDSTONE), new ItemStack(Blocks.SAND, 2, 1));
            RecipeHandler.addCrusherRecipe(new ItemStack(MekanismItems.FluoriteClump, 1), new ItemStack(MekanismItems.OtherDust, 1, 7));
            RecipeHandler.addCrusherRecipe(new ItemStack(Items.COAL, 1, 1), new ItemStack(MekanismItems.OtherDust, 1, 8));
            RecipeHandler.addCrusherRecipe(new ItemStack(Items.DIAMOND, 1), new ItemStack(MekanismItems.OtherDust, 1, 0));

            for (int i = 0; i < 16; i++) {
                RecipeHandler.addCrusherRecipe(new ItemStack(Blocks.WOOL, 1, i), new ItemStack(Items.STRING, 4));
            }

            //BioFuel Crusher Recipes
            RecipeHandler.addCrusherRecipe(new ItemStack(Blocks.TALLGRASS), new ItemStack(MekanismItems.BioFuel, 4));
            RecipeHandler.addCrusherRecipe(new ItemStack(Items.REEDS), new ItemStack(MekanismItems.BioFuel, 2));
            RecipeHandler.addCrusherRecipe(new ItemStack(Items.WHEAT_SEEDS), new ItemStack(MekanismItems.BioFuel, 2));
            RecipeHandler.addCrusherRecipe(new ItemStack(Items.WHEAT), new ItemStack(MekanismItems.BioFuel, 4));
            RecipeHandler.addCrusherRecipe(new ItemStack(Items.PUMPKIN_SEEDS), new ItemStack(MekanismItems.BioFuel, 2));
            RecipeHandler.addCrusherRecipe(new ItemStack(Items.MELON_SEEDS), new ItemStack(MekanismItems.BioFuel, 2));
            RecipeHandler.addCrusherRecipe(new ItemStack(Items.APPLE), new ItemStack(MekanismItems.BioFuel, 4));
            RecipeHandler.addCrusherRecipe(new ItemStack(Items.BREAD), new ItemStack(MekanismItems.BioFuel, 4));
            RecipeHandler.addCrusherRecipe(new ItemStack(Items.POTATO), new ItemStack(MekanismItems.BioFuel, 4));
            RecipeHandler.addCrusherRecipe(new ItemStack(Items.CARROT), new ItemStack(MekanismItems.BioFuel, 4));
            RecipeHandler.addCrusherRecipe(new ItemStack(Items.ROTTEN_FLESH), new ItemStack(MekanismItems.BioFuel, 2));
            RecipeHandler.addCrusherRecipe(new ItemStack(Items.MELON), new ItemStack(MekanismItems.BioFuel, 4));
            RecipeHandler.addCrusherRecipe(new ItemStack(Blocks.PUMPKIN), new ItemStack(MekanismItems.BioFuel, 6));
            RecipeHandler.addCrusherRecipe(new ItemStack(Items.BAKED_POTATO), new ItemStack(MekanismItems.BioFuel, 4));
            RecipeHandler.addCrusherRecipe(new ItemStack(Items.POISONOUS_POTATO), new ItemStack(MekanismItems.BioFuel, 4));
            RecipeHandler.addCrusherRecipe(new ItemStack(Items.BEETROOT), new ItemStack(MekanismItems.BioFuel, 4));
            RecipeHandler.addCrusherRecipe(new ItemStack(Items.BEETROOT_SEEDS), new ItemStack(MekanismItems.BioFuel, 2));
            RecipeHandler.addCrusherRecipe(new ItemStack(Blocks.CACTUS), new ItemStack(MekanismItems.BioFuel, 2));
        }

        //Purification Chamber Recipes
        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.PURIFICATION_CHAMBER)) {
            RecipeHandler.addPurificationChamberRecipe(new ItemStack(Blocks.GRAVEL), new ItemStack(Items.FLINT));
        }

        //Chemical Injection Chamber Recipes
        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.CHEMICAL_INJECTION_CHAMBER)) {
            RecipeHandler.addChemicalInjectionChamberRecipe(new ItemStack(Blocks.DIRT), MekanismFluids.Water, new ItemStack(Blocks.CLAY));
            RecipeHandler.addChemicalInjectionChamberRecipe(new ItemStack(Blocks.HARDENED_CLAY), MekanismFluids.Water, new ItemStack(Blocks.CLAY));
            RecipeHandler.addChemicalInjectionChamberRecipe(new ItemStack(Items.BRICK), MekanismFluids.Water, new ItemStack(Items.CLAY_BALL));
            RecipeHandler.addChemicalInjectionChamberRecipe(new ItemStack(Items.GUNPOWDER), MekanismFluids.HydrogenChloride, new ItemStack(MekanismItems.OtherDust, 1, 3));
            RecipeHandler.addChemicalInjectionChamberRecipe(new ItemStack(MekanismItems.PlutoniumPellet, 1), MekanismFluids.HydrogenChloride, new ItemStack(MekanismItems.ReprocessedFissileFragment, 1));
        }

        //Precision Sawmill Recipes
        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.PRECISION_SAWMILL)) {
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.LADDER, 3), new ItemStack(Items.STICK, 7));
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.TORCH, 4), new ItemStack(Items.STICK), new ItemStack(Items.COAL), 1);
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.JUKEBOX), new ItemStack(Blocks.PLANKS, 8), new ItemStack(Items.DIAMOND), 1);
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.BOOKSHELF), new ItemStack(Blocks.PLANKS, 6), new ItemStack(Items.BOOK, 3), 1);
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.WOODEN_PRESSURE_PLATE), new ItemStack(Blocks.PLANKS, 2));
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.NOTEBLOCK), new ItemStack(Blocks.PLANKS, 8), new ItemStack(Items.REDSTONE), 1);
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.REDSTONE_TORCH), new ItemStack(Items.STICK), new ItemStack(Items.REDSTONE), 1);
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.CRAFTING_TABLE), new ItemStack(Blocks.PLANKS, 4));
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.CHEST), new ItemStack(Blocks.PLANKS, 8));
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.TRAPPED_CHEST), new ItemStack(Blocks.PLANKS, 8), new ItemStack(Blocks.TRIPWIRE_HOOK), 0.75);
            //Boats
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Items.BOAT), new ItemStack(Blocks.PLANKS, 5));
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Items.SPRUCE_BOAT), new ItemStack(Blocks.PLANKS, 5, 1));
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Items.BIRCH_BOAT), new ItemStack(Blocks.PLANKS, 5, 2));
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Items.JUNGLE_BOAT), new ItemStack(Blocks.PLANKS, 5, 3));
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Items.ACACIA_BOAT), new ItemStack(Blocks.PLANKS, 5, 4));
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Items.DARK_OAK_BOAT), new ItemStack(Blocks.PLANKS, 5, 5));
            //Beds
            for (int i = 0; i < 16; i++) {
                RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Items.BED, 1, i), new ItemStack(Blocks.PLANKS, 3), new ItemStack(Blocks.WOOL, 3, i), 1);
            }
            //Doors
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.TRAPDOOR), new ItemStack(Blocks.PLANKS, 1));
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Items.OAK_DOOR), new ItemStack(Blocks.PLANKS, 2, 0));
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Items.SPRUCE_DOOR), new ItemStack(Blocks.PLANKS, 2, 1));
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Items.BIRCH_DOOR), new ItemStack(Blocks.PLANKS, 2, 2));
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Items.JUNGLE_DOOR), new ItemStack(Blocks.PLANKS, 2, 3));
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Items.ACACIA_DOOR), new ItemStack(Blocks.PLANKS, 2, 4));
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Items.DARK_OAK_DOOR), new ItemStack(Blocks.PLANKS, 2, 5));
            //Fences
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.OAK_FENCE), new ItemStack(Items.STICK, 3));
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.SPRUCE_FENCE), new ItemStack(Items.STICK, 3));
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.BIRCH_FENCE), new ItemStack(Items.STICK, 3));
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.JUNGLE_FENCE), new ItemStack(Items.STICK, 3));
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.ACACIA_FENCE), new ItemStack(Items.STICK, 3));
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.DARK_OAK_FENCE), new ItemStack(Items.STICK, 3));
            //Fence Gates
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.OAK_FENCE_GATE), new ItemStack(Blocks.PLANKS, 2, 0), new ItemStack(Items.STICK, 4), 1);
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.SPRUCE_FENCE_GATE), new ItemStack(Blocks.PLANKS, 2, 1), new ItemStack(Items.STICK, 4), 1);
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.BIRCH_FENCE_GATE), new ItemStack(Blocks.PLANKS, 2, 2), new ItemStack(Items.STICK, 4), 1);
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.JUNGLE_FENCE_GATE), new ItemStack(Blocks.PLANKS, 2, 3), new ItemStack(Items.STICK, 4), 1);
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.ACACIA_FENCE_GATE), new ItemStack(Blocks.PLANKS, 2, 4), new ItemStack(Items.STICK, 4), 1);
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.DARK_OAK_FENCE_GATE), new ItemStack(Blocks.PLANKS, 2, 5), new ItemStack(Items.STICK, 4), 1);
            RecipeHandler.addPrecisionSawmillRecipe(new ItemStack(Blocks.MELON_BLOCK), new ItemStack(Items.MELON, 9));
        }

        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.METALLURGIC_INFUSER)) {
            InfuseType carbon = InfuseRegistry.get("CARBON");
            InfuseType bio = InfuseRegistry.get("BIO");
            InfuseType redstone = InfuseRegistry.get("REDSTONE");
            InfuseType fungi = InfuseRegistry.get("FUNGI");
            InfuseType diamond = InfuseRegistry.get("DIAMOND");
            InfuseType obsidian = InfuseRegistry.get("OBSIDIAN");

            //Infuse objects
            InfuseRegistry.registerInfuseObject(new ItemStack(MekanismItems.BioFuel), new InfuseObject(bio, 5));
            InfuseRegistry.registerInfuseObject(new ItemStack(Items.COAL, 1, 0), new InfuseObject(carbon, 10));
            InfuseRegistry.registerInfuseObject(new ItemStack(Items.COAL, 1, 1), new InfuseObject(carbon, 20));
            InfuseRegistry.registerInfuseObject(new ItemStack(MekanismItems.OtherDust, 1, 8), new InfuseObject(carbon, 20));
            InfuseRegistry.registerInfuseObject(new ItemStack(Blocks.COAL_BLOCK, 1, 0), new InfuseObject(carbon, 90));
            InfuseRegistry.registerInfuseObject(new ItemStack(MekanismBlocks.BasicBlock, 1, 3), new InfuseObject(carbon, 180));
            InfuseRegistry.registerInfuseObject(new ItemStack(MekanismItems.CompressedCarbon), new InfuseObject(carbon, 80));
            InfuseRegistry.registerInfuseObject(new ItemStack(Items.REDSTONE), new InfuseObject(redstone, 10));
            InfuseRegistry.registerInfuseObject(new ItemStack(Blocks.REDSTONE_BLOCK), new InfuseObject(redstone, 90));
            InfuseRegistry.registerInfuseObject(new ItemStack(MekanismItems.CompressedRedstone), new InfuseObject(redstone, 80));
            InfuseRegistry.registerInfuseObject(new ItemStack(Blocks.RED_MUSHROOM), new InfuseObject(fungi, 10));
            InfuseRegistry.registerInfuseObject(new ItemStack(Blocks.BROWN_MUSHROOM), new InfuseObject(fungi, 10));
            InfuseRegistry.registerInfuseObject(new ItemStack(MekanismItems.CompressedDiamond), new InfuseObject(diamond, 80));
            InfuseRegistry.registerInfuseObject(new ItemStack(MekanismItems.CompressedObsidian), new InfuseObject(obsidian, 80));

            //Metallurgic Infuser Recipes
            RecipeHandler.addMetallurgicInfuserRecipe(carbon, 10, new ItemStack(Items.IRON_INGOT), new ItemStack(MekanismItems.EnrichedIron));
            RecipeHandler.addMetallurgicInfuserRecipe(carbon, 10, new ItemStack(MekanismItems.EnrichedIron), new ItemStack(MekanismItems.OtherDust, 1, 1));
            OreDictionary.getOres("ingotIron", false).forEach(steel -> RecipeHandler.addMetallurgicInfuserRecipe(redstone, 10, StackUtils.size(steel, 1), new ItemStack(MekanismItems.EnrichedAlloy)));
            OreDictionary.getOres("ingotSteel", false).forEach(steel -> RecipeHandler.addMetallurgicInfuserRecipe(redstone, 10, StackUtils.size(steel, 1), new ItemStack(MekanismItems.EnrichedAlloy, 4)));

            RecipeHandler.addMetallurgicInfuserRecipe(fungi, 10, new ItemStack(Blocks.DIRT), new ItemStack(Blocks.MYCELIUM));
            RecipeHandler.addMetallurgicInfuserRecipe(bio, 10, new ItemStack(Blocks.COBBLESTONE), new ItemStack(Blocks.MOSSY_COBBLESTONE));
            RecipeHandler.addMetallurgicInfuserRecipe(bio, 10, new ItemStack(Blocks.STONEBRICK, 1, 0), new ItemStack(Blocks.STONEBRICK, 1, 1));
            RecipeHandler.addMetallurgicInfuserRecipe(bio, 10, new ItemStack(Blocks.SAND), new ItemStack(Blocks.DIRT));
            RecipeHandler.addMetallurgicInfuserRecipe(bio, 10, new ItemStack(Blocks.DIRT), new ItemStack(Blocks.DIRT, 1, 2));
            RecipeHandler.addMetallurgicInfuserRecipe(diamond, 10, new ItemStack(MekanismItems.EnrichedAlloy), new ItemStack(MekanismItems.ReinforcedAlloy));
            RecipeHandler.addMetallurgicInfuserRecipe(obsidian, 10, new ItemStack(MekanismItems.ReinforcedAlloy), new ItemStack(MekanismItems.AtomicAlloy));
        }

        //Chemical Infuser Recipes
        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.CHEMICAL_INFUSER)) {
            RecipeHandler.addChemicalInfuserRecipe(new GasStack(MekanismFluids.Oxygen, 1), new GasStack(MekanismFluids.SulfurDioxide, 2), new GasStack(MekanismFluids.SulfurTrioxide, 2));
            RecipeHandler.addChemicalInfuserRecipe(new GasStack(MekanismFluids.SulfurTrioxide, 1), new GasStack(MekanismFluids.Water, 1), new GasStack(MekanismFluids.SulfuricAcid, 1));
            RecipeHandler.addChemicalInfuserRecipe(new GasStack(MekanismFluids.Hydrogen, 1), new GasStack(MekanismFluids.Chlorine, 1), new GasStack(MekanismFluids.HydrogenChloride, 1));
            RecipeHandler.addChemicalInfuserRecipe(new GasStack(MekanismFluids.Deuterium, 1), new GasStack(MekanismFluids.Tritium, 1), new GasStack(MekanismFluids.FusionFuel, 2));
            RecipeHandler.addChemicalInfuserRecipe(new GasStack(MekanismFluids.HydrofluoricAcid, 1), new GasStack(MekanismFluids.Uraniumoxide, 1), new GasStack(MekanismFluids.UraniumHexafluoride, 1));
            RecipeHandler.addChemicalInfuserRecipe(new GasStack(MekanismFluids.Oxygen, 2), new GasStack(MekanismFluids.Water, 1), new GasStack(MekanismFluids.OxygenEnrichedWater, 1));
            RecipeHandler.addChemicalInfuserRecipe(new GasStack(MekanismFluids.OxygenEnrichedWater, 1), new GasStack(MekanismFluids.NutritionalPaste, 10), new GasStack(MekanismFluids.NutrientSolution, 1));
        }

        //Electrolytic Separator Recipes
        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.ELECTROLYTIC_SEPARATOR)) {
            RecipeHandler.addElectrolyticSeparatorRecipe(FluidRegistry.getFluidStack("water", 2), 2 * MekanismConfig.current().general.FROM_H2.val(),
                    new GasStack(MekanismFluids.Hydrogen, 2), new GasStack(MekanismFluids.Oxygen, 1));
            RecipeHandler.addElectrolyticSeparatorRecipe(FluidRegistry.getFluidStack("brine", 10), 2 * MekanismConfig.current().general.FROM_H2.val(),
                    new GasStack(MekanismFluids.Sodium, 1), new GasStack(MekanismFluids.Chlorine, 1));
            RecipeHandler.addElectrolyticSeparatorRecipe(FluidRegistry.getFluidStack("heavywater", 2), MekanismConfig.current().usage.heavyWaterElectrolysis.val(),
                    new GasStack(MekanismFluids.Deuterium, 2), new GasStack(MekanismFluids.Oxygen, 1));
        }

        //Rotary Condensentrator Recipes
        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.ROTARY_CONDENSENTRATOR)) {
            RecipeHandler.addDefaultRotaryRecipes();
        }

        //Thermal Evaporation Plant Recipes
        RecipeHandler.addThermalEvaporationRecipe(FluidRegistry.getFluidStack("water", 10), FluidRegistry.getFluidStack("brine", 1));
        RecipeHandler.addThermalEvaporationRecipe(FluidRegistry.getFluidStack("brine", 10), FluidRegistry.getFluidStack("liquidlithium", 1));
        RecipeHandler.addThermalEvaporationRecipe(FluidRegistry.getFluidStack("liquidsodium", 1000), FluidRegistry.getFluidStack("liquidsuperheatedsodium", 1));

        //Chemical Crystallizer Recipes
        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.CHEMICAL_CRYSTALLIZER)) {
            RecipeHandler.addChemicalCrystallizerRecipe(new GasStack(MekanismFluids.Lithium, 100), new ItemStack(MekanismItems.OtherDust, 1, 4));
            RecipeHandler.addChemicalCrystallizerRecipe(new GasStack(MekanismFluids.Brine, 15), new ItemStack(MekanismItems.Salt));
            RecipeHandler.addChemicalCrystallizerRecipe(new GasStack(MekanismFluids.Antimatter, 1000), new ItemStack(MekanismItems.AntimatterPellet, 1));
        }
        //Chemical Washer Recipes
        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.CHEMICAL_WASHER)) {
            //  RecipeHandler.addChemicalWasherRecipe(new GasStack(MekanismFluids.FissileFuel, 1000), new FluidStack(FluidRegistry.WATER, 1000), new GasStack(MekanismFluids.NuclearWaste, 1));
        }
        //Chemical Dissolution Chamber Recipes
        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.CHEMICAL_DISSOLUTION_CHAMBER)) {
            OreDictionary.getOres("dustFluorite").forEach(ore -> RecipeHandler.addChemicalDissolutionChamberRecipe(StackUtils.size(ore, 1), new GasStack(MekanismFluids.HydrofluoricAcid, 100)));
        }

        //T4 Processing Recipes
        GasRegistry.getRegisteredGasses().forEach(gas -> {
            if (gas instanceof OreGas oreGas && !oreGas.isClean()) {
                if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.CHEMICAL_WASHER)) {
                    RecipeHandler.addChemicalWasherRecipe(new GasStack(oreGas, 1), new GasStack(oreGas.getCleanGas(), 1));
                }

                //do the crystallizer only if it's one of ours!
                Resource gasResource = Resource.getFromName(oreGas.getName());
                if (gasResource != null && MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.CHEMICAL_CRYSTALLIZER)) {
                    RecipeHandler.addChemicalCrystallizerRecipe(new GasStack(oreGas.getCleanGas(), 200), new ItemStack(MekanismItems.Crystal, 1, gasResource.ordinal()));
                }
            }
        });
        //Pressurized Reaction Chamber Recipes
        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.PRESSURIZED_REACTION_CHAMBER)) {
            RecipeHandler.addPRCRecipe(new ItemStack(MekanismItems.BioFuel, 2), new FluidStack(FluidRegistry.WATER, 10), new GasStack(MekanismFluids.Hydrogen, 100),
                    new ItemStack(MekanismItems.Substrate), new GasStack(MekanismFluids.Ethene, 100), 0, 100);
            RecipeHandler.addPRCRecipe(new ItemStack(MekanismItems.Substrate), new FluidStack(MekanismFluids.Ethene.getFluid(), 50),
                    new GasStack(MekanismFluids.Oxygen, 10), new ItemStack(MekanismItems.HDPE_PELLET), new GasStack(MekanismFluids.Oxygen, 5), 1000, 60);
            RecipeHandler.addPRCRecipe(new ItemStack(MekanismItems.Substrate), new FluidStack(FluidRegistry.WATER, 200), new GasStack(MekanismFluids.Ethene, 100),
                    new ItemStack(MekanismItems.Substrate, 8), new GasStack(MekanismFluids.Oxygen, 10), 200, 400);
            RecipeHandler.addPRCRecipe(new ItemStack(Items.COAL, 1, OreDictionary.WILDCARD_VALUE), new FluidStack(FluidRegistry.WATER, 100), new GasStack(MekanismFluids.Oxygen, 100),
                    new ItemStack(MekanismItems.OtherDust, 1, 3), new GasStack(MekanismFluids.Hydrogen, 100), 0, 100);

            OreDictionary.getOres("dustFluorite").forEach(ore -> {
                RecipeHandler.addPRCRecipe(StackUtils.size(ore, 1), new FluidStack(FluidRegistry.WATER, 10000), new GasStack(MekanismFluids.Plutonium, 1000), new ItemStack(MekanismItems.PlutoniumPellet, 1), new GasStack(MekanismFluids.SpentNuclearWaste, 1000), 10000, 2000);
                RecipeHandler.addPRCRecipe(StackUtils.size(ore, 1), new FluidStack(FluidRegistry.WATER, 10000), new GasStack(MekanismFluids.Polonium, 1000), new ItemStack(MekanismItems.PoloniumPellet, 1), new GasStack(MekanismFluids.SpentNuclearWaste, 1000), 10000, 2000);
            });

            RecipeHandler.addPRCRecipe(new ItemStack(MekanismItems.CosmicMatter, 64), FluidRegistry.getFluidStack("liquidsuperheatedsodium", 10000), new GasStack(MekanismFluids.UnstableDimensional, 10000),
                    ItemStack.EMPTY, new GasStack(MekanismFluids.Antimatter, 100), 100000, 24000);
            RecipeHandler.addPRCRecipe(new ItemStack(MekanismItems.ScrapBox, 64), FluidRegistry.getFluidStack("liquidfusionfuel", 10000), new GasStack(MekanismFluids.UnstableDimensional, 10000),
                    new ItemStack(MekanismItems.EmptyCrystals, 1), new GasStack(MekanismFluids.SpentNuclearWaste, 1000), 100000, 10000);
            RecipeHandler.addPRCRecipe(new ItemStack(MekanismItems.EmptyCrystals, 64), FluidRegistry.getFluidStack("liquidfusionfuel", 10000), new GasStack(MekanismFluids.UnstableDimensional, 10000), new ItemStack(MekanismItems.CosmicMatter), new GasStack(MekanismFluids.NuclearWaste, 10), 80000, 2000);
        }

        //Solar Neutron Activator Recipes
        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.SOLAR_NEUTRON_ACTIVATOR)) {
            RecipeHandler.addSolarNeutronRecipe(new GasStack(MekanismFluids.Lithium, 1), new GasStack(MekanismFluids.Tritium, 1));
            RecipeHandler.addSolarNeutronRecipe(new GasStack(MekanismFluids.NuclearWaste, 10), new GasStack(MekanismFluids.Polonium, 1));
        }

        //Fuel Gases
        // FuelHandler.addGas(MekanismFluids.Hydrogen, 1, MekanismConfig.current().general.FROM_H2.val());
        RecipeHandler.addGasStackFuelToEnergyRecipe(new GasStack(MekanismFluids.Hydrogen, 1), MekanismConfig.current().general.FROM_H2.val());

        //Chemical Oxidizer Recipes
        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.CHEMICAL_OXIDIZER)) {
            RecipeHandler.addChemicalOxidizerRecipe(new ItemStack(MekanismItems.ReprocessedFissileFragment, 1), new GasStack(MekanismFluids.FissileFuel, 2000));
            RecipeHandler.addChemicalOxidizerRecipe(new ItemStack(MekanismItems.YellowCakeUranium, 1), new GasStack(MekanismFluids.Uraniumoxide, 250));
        }

        /**
         * ADD START
         */
        //Isotopic Centrifuge Recipes
        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.ISOTOPIC_CENTRIFUGE)) {
            RecipeHandler.addIsotopicRecipe(new GasStack(MekanismFluids.UraniumHexafluoride, 1), new GasStack(MekanismFluids.FissileFuel, 1));
            RecipeHandler.addIsotopicRecipe(new GasStack(MekanismFluids.NuclearWaste, 10), new GasStack(MekanismFluids.Plutonium, 1));
        }

        //Nutritional Liquifier Recipes
        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.NUTRITIONAL_LIQUIFIER)) {
            getRegistriesStacks().forEach(stack -> {
                if (stack.getItem() instanceof ItemFood food) {
                    try {
                        if (!stack.isEmpty() && food.getHealAmount(stack) > 0) {
                            RecipeHandler.addNutritionalLiquifierRecipe(stack, new GasStack(MekanismFluids.NutritionalPaste, food.getHealAmount(stack) * 50));
                        }
                    } catch (Exception ignored) {
                        Mekanism.logger.error("Unable to add recipe for Nutritional Liquifier because {} is entered incorrectly", food);
                    }
                }
            });
            RecipeHandler.addNutritionalLiquifierRecipe(new ItemStack(Items.CAKE), new GasStack(MekanismFluids.NutritionalPaste, 6 * 50));
        }


        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.ORGANIC_FARM)) {
            //Farm log
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Blocks.SAPLING, 1, 0), MekanismFluids.NutrientSolution, new ItemStack(Blocks.LOG, 24, 0), new ItemStack(Blocks.SAPLING, 4, 0), MekanismConfig.current().mekce.log.val());
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Blocks.SAPLING, 1, 1), MekanismFluids.NutrientSolution, new ItemStack(Blocks.LOG, 24, 1), new ItemStack(Blocks.SAPLING, 4, 1), MekanismConfig.current().mekce.log.val());
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Blocks.SAPLING, 1, 2), MekanismFluids.NutrientSolution, new ItemStack(Blocks.LOG, 24, 2), new ItemStack(Blocks.SAPLING, 4, 2), MekanismConfig.current().mekce.log.val());
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Blocks.SAPLING, 1, 3), MekanismFluids.NutrientSolution, new ItemStack(Blocks.LOG, 24, 3), new ItemStack(Blocks.SAPLING, 4, 3), MekanismConfig.current().mekce.log.val());
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Blocks.SAPLING, 1, 4), MekanismFluids.NutrientSolution, new ItemStack(Blocks.LOG2, 24, 0), new ItemStack(Blocks.SAPLING, 4, 4), MekanismConfig.current().mekce.log.val());
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Blocks.SAPLING, 1, 5), MekanismFluids.NutrientSolution, new ItemStack(Blocks.LOG2, 24, 1), new ItemStack(Blocks.SAPLING, 4, 5), MekanismConfig.current().mekce.log.val());

            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Blocks.SAPLING, 1, 0), MekanismFluids.Water, new ItemStack(Blocks.LOG, 6, 0), new ItemStack(Blocks.SAPLING, 1, 0), MekanismConfig.current().mekce.log.val());
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Blocks.SAPLING, 1, 1), MekanismFluids.Water, new ItemStack(Blocks.LOG, 6, 1), new ItemStack(Blocks.SAPLING, 1, 1), MekanismConfig.current().mekce.log.val());
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Blocks.SAPLING, 1, 2), MekanismFluids.Water, new ItemStack(Blocks.LOG, 6, 2), new ItemStack(Blocks.SAPLING, 1, 2), MekanismConfig.current().mekce.log.val());
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Blocks.SAPLING, 1, 3), MekanismFluids.Water, new ItemStack(Blocks.LOG, 6, 3), new ItemStack(Blocks.SAPLING, 1, 3), MekanismConfig.current().mekce.log.val());
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Blocks.SAPLING, 1, 4), MekanismFluids.Water, new ItemStack(Blocks.LOG2, 6, 0), new ItemStack(Blocks.SAPLING, 1, 4), MekanismConfig.current().mekce.log.val());
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Blocks.SAPLING, 1, 5), MekanismFluids.Water, new ItemStack(Blocks.LOG2, 6, 1), new ItemStack(Blocks.SAPLING, 1, 5), MekanismConfig.current().mekce.log.val());

            // Farm seed
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Items.NETHER_WART, 1), MekanismFluids.NutrientSolution, new ItemStack(Items.NETHER_WART, 24));
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Items.REEDS, 1), MekanismFluids.NutrientSolution, new ItemStack(Items.REEDS, 24));
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Items.DYE, 1, 3), MekanismFluids.NutrientSolution, new ItemStack(Items.DYE, 24, 3));
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Items.PUMPKIN_SEEDS, 1), MekanismFluids.NutrientSolution, new ItemStack(Blocks.PUMPKIN, 24), new ItemStack(Items.PUMPKIN_SEEDS, 4), MekanismConfig.current().mekce.seed.val());
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Items.MELON_SEEDS, 1), MekanismFluids.NutrientSolution, new ItemStack(Blocks.MELON_BLOCK, 24), new ItemStack(Items.MELON_SEEDS, 4), MekanismConfig.current().mekce.seed.val());
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Blocks.BROWN_MUSHROOM, 1), MekanismFluids.NutrientSolution, new ItemStack(Blocks.BROWN_MUSHROOM, 24));
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Blocks.RED_MUSHROOM, 1), MekanismFluids.NutrientSolution, new ItemStack(Blocks.RED_MUSHROOM, 24));

            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Items.NETHER_WART, 1), MekanismFluids.Water, new ItemStack(Items.NETHER_WART, 3));
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Items.REEDS, 1), MekanismFluids.Water, new ItemStack(Items.REEDS, 3));
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Items.DYE, 1, 3), MekanismFluids.Water, new ItemStack(Items.DYE, 3, 3));
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Items.PUMPKIN_SEEDS, 1), MekanismFluids.Water, new ItemStack(Blocks.PUMPKIN, 3), new ItemStack(Items.PUMPKIN_SEEDS, 1), MekanismConfig.current().mekce.seed.val());
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Items.MELON_SEEDS, 1), MekanismFluids.Water, new ItemStack(Blocks.MELON_BLOCK, 3), new ItemStack(Items.MELON_SEEDS, 1), MekanismConfig.current().mekce.seed.val());
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Blocks.BROWN_MUSHROOM, 1), MekanismFluids.Water, new ItemStack(Blocks.BROWN_MUSHROOM, 3));
            RecipeHandler.addOrganicFarmRecipe(new ItemStack(Blocks.RED_MUSHROOM, 1), MekanismFluids.Water, new ItemStack(Blocks.RED_MUSHROOM, 3));

            ForgeRegistries.BLOCKS.forEach(block -> {
                if (block instanceof BlockCrops crops) {  //通过方块来获取可以生长的农作物
                    try {
                        Item inputSeed = crops.getSeed();
                        Item primaryOutput = crops.getCrop();
                        ItemStack secondaryOutput = ItemStack.EMPTY;
                        List<ItemStack> drops = crops.getDrops(null, null, block.getDefaultState(), 0);
                        if (drops != null && drops.size() < 2) { //确保掉落物种类不超过2
                            for (ItemStack stack : drops) {
                                if (stack.getItem() != primaryOutput) {
                                    secondaryOutput = stack;
                                }
                            }
                        }
                        if (secondaryOutput != ItemStack.EMPTY) {
                            RecipeHandler.addOrganicFarmRecipe(new ItemStack(inputSeed), MekanismFluids.NutrientSolution, new ItemStack(primaryOutput, 24), new ItemStack(secondaryOutput.getItem(), 4), MekanismConfig.current().mekce.seed.val());
                            RecipeHandler.addOrganicFarmRecipe(new ItemStack(inputSeed), MekanismFluids.Water, new ItemStack(primaryOutput, 3), secondaryOutput, MekanismConfig.current().mekce.seed.val());
                        } else if (primaryOutput == Items.POTATO) {
                            RecipeHandler.addOrganicFarmRecipe(new ItemStack(inputSeed), MekanismFluids.NutrientSolution, new ItemStack(primaryOutput, 24), new ItemStack(Items.POISONOUS_POTATO), 0.15);
                            RecipeHandler.addOrganicFarmRecipe(new ItemStack(inputSeed), MekanismFluids.Water, new ItemStack(primaryOutput, 3), new ItemStack(Items.POISONOUS_POTATO), 0.15);
                        } else {
                            RecipeHandler.addOrganicFarmRecipe(new ItemStack(inputSeed), MekanismFluids.NutrientSolution, new ItemStack(primaryOutput, 24));
                            RecipeHandler.addOrganicFarmRecipe(new ItemStack(inputSeed), MekanismFluids.Water, new ItemStack(primaryOutput, 3));
                        }
                    } catch (Exception e) {
                        Mekanism.logger.error("Unable to add recipe for Organic Farm because {} is entered incorrectly", block);
                    }
                }
            });
        }

        //Antiprotonic Nucleosynthesizer Recipes
        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.ANTIPROTONIC_NUCLEOSYNTHESIZER)) {
            RecipeHandler.addNucleosynthesizerRecipe(new ItemStack(Blocks.WOOL, 1, 8), new GasStack(MekanismFluids.Antimatter, 500), new ItemStack(Blocks.QUARTZ_BLOCK), 0, 500);
            RecipeHandler.addNucleosynthesizerRecipe(new ItemStack(Blocks.WOOL, 1, 4), new GasStack(MekanismFluids.Antimatter, 500), new ItemStack(Blocks.GLOWSTONE), 0, 500);
            RecipeHandler.addNucleosynthesizerRecipe(new ItemStack(MekanismItems.Ingot, 1, 6), new GasStack(MekanismFluids.Antimatter, 500), new ItemStack(Items.IRON_INGOT), 0, 500);
            RecipeHandler.addNucleosynthesizerRecipe(new ItemStack(Items.COAL), new GasStack(MekanismFluids.Antimatter, 500), new ItemStack(Items.DIAMOND), 0, 1000);
            RecipeHandler.addNucleosynthesizerRecipe(new ItemStack(Items.DIAMOND), new GasStack(MekanismFluids.Antimatter, 500), new ItemStack(Items.EMERALD), 0, 1000);
            RecipeHandler.addNucleosynthesizerRecipe(new ItemStack(Blocks.WOOL, 1, 14), new GasStack(MekanismFluids.Antimatter, 500), new ItemStack(Blocks.REDSTONE_BLOCK), 0, 500);
            RecipeHandler.addNucleosynthesizerRecipe(new ItemStack(Blocks.WOOL, 1, 11), new GasStack(MekanismFluids.Antimatter, 500), new ItemStack(Blocks.LAPIS_BLOCK), 0, 500);
            RecipeHandler.addNucleosynthesizerRecipe(new ItemStack(MekanismItems.EmptyCrystals, 64), new GasStack(MekanismFluids.UnstableDimensional, 10000), new ItemStack(MekanismItems.CosmicMatter), 80000, 2000);
        }


        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.STAMPING)) {
            AutoMekanismRecipes.addStampingRecipe();
        }

        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.ROLLING)) {
            AutoMekanismRecipes.addRollingRecipe();
        }

        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.BRUSHED)) {// 拉丝机 杆?
            RecipeHandler.addBrushedRecipe(new ItemStack(Blocks.DIRT), new ItemStack(Blocks.SAND, 2));
        }
        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.TURNING)) {
            AutoMekanismRecipes.addTurningRecipe();
        }

        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.ALLOY)) { //合金炉
            RecipeHandler.addAlloyRecipe(new ItemStack(MekanismItems.Ingot, 3, 5), new ItemStack(MekanismItems.Ingot, 1, 6), new ItemStack(MekanismItems.Ingot, 4, 2));
        }

        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.CELL_EXTRACTOR)) {//细胞提取机
            RecipeHandler.addCellExtractorRecipe(new ItemStack(Blocks.IRON_ORE), new ItemStack(Blocks.IRON_BLOCK), new ItemStack(Items.IRON_INGOT), 1);
        }

        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.CELL_SEPARATOR)) {//细胞分离机
            RecipeHandler.addCellSeparatorRecipe(new ItemStack(Blocks.IRON_ORE), new ItemStack(Blocks.IRON_BLOCK), new ItemStack(Items.IRON_INGOT), 1);
        }

        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.RECYCLER)) {
            if (MekanismConfig.current().mekce.EnableAddArrItemRecyclerRecipe.val()) {
                getRegistriesStacks().forEach(RecipeHandler::addRecyclerRecipe);
            } else {
                RecipeHandler.addRecyclerRecipe(new ItemStack(Blocks.STONE));
            }
            ItemStack Substrate = new ItemStack(MekanismItems.Substrate);
            if (RecipeHandler.Recipe.RECYCLER.containsRecipe(Substrate)) {
                RecipeHandler.Recipe.RECYCLER.remove(RecipeHandler.Recipe.RECYCLER.get().get(new ItemStackInput(Substrate)));
                RecipeHandler.addRecyclerRecipe(Substrate, 0.5);
            }
        }

        if (MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.AMBIENT_ACCUMULATOR) || MekanismConfig.current().general.machinesManager.isEnabled(BlockStateMachine.MachineType.AMBIENT_ACCUMULATOR_ENERGY)) {
            //遍历所有维度来生成配方
            if (MekanismConfig.current().general.EnableDefaultAllAddAmbientGas.val()) {
                DimensionManager.getRegisteredDimensions().keySet().stream().filter(dimensionType -> !MekanismConfig.current().general.defaultAmbientDimBlacklist.val().contains(dimensionType.getId())).forEach(dimensionType -> RecipeHandler.addAmbientGas(dimensionType.getId()));
            }
        }
        RecipeHandler.addItemStackToEnergyRecipe(new ItemStack(Items.REDSTONE), MekanismConfig.current().general.ENERGY_PER_REDSTONE.val());
        RecipeHandler.addItemStackToEnergyRecipe(new ItemStack(Blocks.REDSTONE_BLOCK), MekanismConfig.current().general.ENERGY_PER_REDSTONE_BLOCK.val());
        /**
         * ADD END
         */

    }

    //获取所有物品(不包括空气)
    public static List<ItemStack> getRegistriesStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS) {
            //跳过物品类型的空气
            if (item == Items.AIR) {
                continue;
            }
            if (item.getHasSubtypes()) {
                ItemStack stack = new ItemStack(item, 1, OreDictionary.WILDCARD_VALUE);
                if (!stack.isEmpty()) {
                    stacks.add(stack);
                }
            } else {
                stacks.add(new ItemStack(item));
            }
        }
        return stacks;
    }


    public static void SuperFumoReciperRegister() {
        ItemStack SuperFumo = new ItemStack(MekanismBlocks.SuperFumo);
        ItemStack helmet = new ItemStack(MekanismItems.MEKASUIT_HELMET);
        ItemStack bodyarmor = new ItemStack(MekanismItems.MEKASUIT_BODYARMOR);
        ItemStack pants = new ItemStack(MekanismItems.MEKASUIT_PANTS);
        ItemStack boots = new ItemStack(MekanismItems.MEKASUIT_BOOTS);
        ItemStack tool = new ItemStack(MekanismItems.MEKA_TOOL);

        addAllModule(helmet);
        addAllModule(bodyarmor);
        addAllModule(pants);
        addAllModule(boots);
        addAllModule(tool);

        addEnergy(helmet);
        addEnergy(bodyarmor);
        addEnergy(pants);
        addEnergy(boots);
        addEnergy(tool);

        GasInventorySlot.setGasContained(helmet, new GasStack(MekanismFluids.NutritionalPaste, GasInventorySlot.getTankCapacity(helmet, 0)));
        GasInventorySlot.setGasContained(bodyarmor, new GasStack(MekanismFluids.Hydrogen, GasInventorySlot.getTankCapacity(bodyarmor, 0)));

        GameRegistry.addShapedRecipe(Mekanism.rl("super_fumo"), null, SuperFumo, "A B", " C ", "D E", 'A', helmet, 'B', bodyarmor, 'C', tool, 'D', pants, 'E', boots);
    }


    public static void addAllModule(ItemStack stack) {
        if (stack.getItem() instanceof IModuleContainerItem item) {
            item.setAllModule(stack);
        }
    }

    public static void addEnergy(ItemStack stack) {
        double maxEnergy = StorageUtils.getMaxEnergy(stack);
        if (maxEnergy > 0) {
            StorageUtils.setStoredEnergy(stack, maxEnergy, maxEnergy);
        }
    }


}
