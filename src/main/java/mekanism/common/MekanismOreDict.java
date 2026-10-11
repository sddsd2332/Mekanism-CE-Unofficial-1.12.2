package mekanism.common;

import mekanism.common.block.states.BlockStateTransmitter;
import mekanism.common.config.MekanismConfig;
import mekanism.common.tier.BaseTier;
import mekanism.common.util.MekanismUtils;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

public class MekanismOreDict {

    public static void registerOreDict() {
        //Add specific items to ore dictionary for recipe usage in other mods.
        OreDictionary.registerOre("universalCable", MekanismUtils.getTransmitter(BlockStateTransmitter.TransmitterType.UNIVERSAL_CABLE, BaseTier.BASIC, 1));
        OreDictionary.registerOre("battery", MekanismItems.EnergyTablet.getUnchargedItem());
        OreDictionary.registerOre("pulpWood", MekanismItems.Sawdust);
        OreDictionary.registerOre("dustWood", MekanismItems.Sawdust);
        OreDictionary.registerOre("blockSalt", MekanismBlocks.SaltBlock);

        //Alloys!
        OreDictionary.registerOre("alloyBasic", new ItemStack(Items.REDSTONE));
        OreDictionary.registerOre("alloyAdvanced", new ItemStack(MekanismItems.EnrichedAlloy));
        OreDictionary.registerOre("alloyElite", new ItemStack(MekanismItems.ReinforcedAlloy));
        OreDictionary.registerOre("alloyUltimate", new ItemStack(MekanismItems.AtomicAlloy));

        //GregoriousT?
        OreDictionary.registerOre("itemSalt", MekanismItems.Salt);
        OreDictionary.registerOre("dustSalt", MekanismItems.Salt);
        OreDictionary.registerOre("foodSalt", MekanismItems.Salt);

        OreDictionary.registerOre("dustDiamond", new ItemStack(MekanismItems.OtherDust, 1, 0));
        OreDictionary.registerOre("dustSteel", new ItemStack(MekanismItems.OtherDust, 1, 1));
        //Lead was once here
        OreDictionary.registerOre("dustSulfur", new ItemStack(MekanismItems.OtherDust, 1, 3));
        OreDictionary.registerOre("dustLithium", new ItemStack(MekanismItems.OtherDust, 1, 4));
        OreDictionary.registerOre("dustRefinedObsidian", new ItemStack(MekanismItems.OtherDust, 1, 5));
        OreDictionary.registerOre("dustObsidian", new ItemStack(MekanismItems.OtherDust, 1, 6));

        OreDictionary.registerOre("ingotRefinedObsidian", new ItemStack(MekanismItems.Ingot, 1, 0));
        OreDictionary.registerOre("ingotOsmium", new ItemStack(MekanismItems.Ingot, 1, 1));
        OreDictionary.registerOre("ingotBronze", new ItemStack(MekanismItems.Ingot, 1, 2));
        OreDictionary.registerOre("ingotRefinedGlowstone", new ItemStack(MekanismItems.Ingot, 1, 3));
        OreDictionary.registerOre("ingotSteel", new ItemStack(MekanismItems.Ingot, 1, 4));
        OreDictionary.registerOre("ingotCopper", new ItemStack(MekanismItems.Ingot, 1, 5));
        OreDictionary.registerOre("ingotTin", new ItemStack(MekanismItems.Ingot, 1, 6));

        OreDictionary.registerOre("nuggetRefinedObsidian", new ItemStack(MekanismItems.Nugget, 1, 0));
        OreDictionary.registerOre("nuggetOsmium", new ItemStack(MekanismItems.Nugget, 1, 1));
        OreDictionary.registerOre("nuggetBronze", new ItemStack(MekanismItems.Nugget, 1, 2));
        OreDictionary.registerOre("nuggetRefinedGlowstone", new ItemStack(MekanismItems.Nugget, 1, 3));
        OreDictionary.registerOre("nuggetSteel", new ItemStack(MekanismItems.Nugget, 1, 4));
        OreDictionary.registerOre("nuggetCopper", new ItemStack(MekanismItems.Nugget, 1, 5));
        OreDictionary.registerOre("nuggetTin", new ItemStack(MekanismItems.Nugget, 1, 6));
        OreDictionary.registerOre("nuggetLead", new ItemStack(MekanismItems.Nugget, 1, 7));
        OreDictionary.registerOre("nuggetUranium", new ItemStack(MekanismItems.Nugget, 1, 8));

        OreDictionary.registerOre("blockOsmium", new ItemStack(MekanismBlocks.BasicBlock, 1, 0));
        OreDictionary.registerOre("blockBronze", new ItemStack(MekanismBlocks.BasicBlock, 1, 1));
        OreDictionary.registerOre("blockRefinedObsidian", new ItemStack(MekanismBlocks.BasicBlock, 1, 2));
        OreDictionary.registerOre("blockCharcoal", new ItemStack(MekanismBlocks.BasicBlock, 1, 3));
        OreDictionary.registerOre("blockRefinedGlowstone", new ItemStack(MekanismBlocks.BasicBlock, 1, 4));
        OreDictionary.registerOre("blockSteel", new ItemStack(MekanismBlocks.BasicBlock, 1, 5));
        OreDictionary.registerOre("blockCopper", new ItemStack(MekanismBlocks.BasicBlock, 1, 12));
        OreDictionary.registerOre("blockTin", new ItemStack(MekanismBlocks.BasicBlock, 1, 13));

        for (Resource resource : Resource.values()) {
            OreDictionary.registerOre("dust" + resource.getName(), new ItemStack(MekanismItems.Dust, 1, resource.ordinal()));
            OreDictionary.registerOre("dustDirty" + resource.getName(), new ItemStack(MekanismItems.DirtyDust, 1, resource.ordinal()));
            OreDictionary.registerOre("clump" + resource.getName(), new ItemStack(MekanismItems.Clump, 1, resource.ordinal()));
            OreDictionary.registerOre("shard" + resource.getName(), new ItemStack(MekanismItems.Shard, 1, resource.ordinal()));
            OreDictionary.registerOre("crystal" + resource.getName(), new ItemStack(MekanismItems.Crystal, 1, resource.ordinal()));
        }

        OreDictionary.registerOre("oreOsmium", new ItemStack(MekanismBlocks.OreBlock, 1, 0));
        OreDictionary.registerOre("oreCopper", new ItemStack(MekanismBlocks.OreBlock, 1, 1));
        OreDictionary.registerOre("oreTin", new ItemStack(MekanismBlocks.OreBlock, 1, 2));

        if (MekanismConfig.current().general.controlCircuitOreDict.val()) {
            OreDictionary.registerOre("circuitBasic", new ItemStack(MekanismItems.ControlCircuit, 1, 0));
            OreDictionary.registerOre("circuitAdvanced", new ItemStack(MekanismItems.ControlCircuit, 1, 1));
            OreDictionary.registerOre("circuitElite", new ItemStack(MekanismItems.ControlCircuit, 1, 2));
            OreDictionary.registerOre("circuitUltimate", new ItemStack(MekanismItems.ControlCircuit, 1, 3));
        }

        OreDictionary.registerOre("itemCompressedCarbon", new ItemStack(MekanismItems.CompressedCarbon));
        OreDictionary.registerOre("itemCompressedRedstone", new ItemStack(MekanismItems.CompressedRedstone));
        OreDictionary.registerOre("itemCompressedDiamond", new ItemStack(MekanismItems.CompressedDiamond));
        OreDictionary.registerOre("itemCompressedObsidian", new ItemStack(MekanismItems.CompressedObsidian));

        OreDictionary.registerOre("itemEnrichedAlloy", new ItemStack(MekanismItems.EnrichedAlloy));
        OreDictionary.registerOre("itemBioFuel", new ItemStack(MekanismItems.BioFuel));

        OreDictionary.registerOre("blockLead", new ItemStack(MekanismBlocks.BasicBlock2, 1, 11));
        OreDictionary.registerOre("blockUranium", new ItemStack(MekanismBlocks.BasicBlock2, 1, 10));
        OreDictionary.registerOre("oreFluorite", new ItemStack(MekanismBlocks.OreBlock, 1, 3));
        OreDictionary.registerOre("oreLead", new ItemStack(MekanismBlocks.OreBlock, 1, 4));
        OreDictionary.registerOre("oreUranium", new ItemStack(MekanismBlocks.OreBlock, 1, 5));
        OreDictionary.registerOre("YellowCakeUranium", new ItemStack(MekanismItems.YellowCakeUranium, 1));
        OreDictionary.registerOre("ingotLead", new ItemStack(MekanismItems.Ingot, 1, 7));
        OreDictionary.registerOre("ingotUranium", new ItemStack(MekanismItems.Ingot, 1, 8));
        OreDictionary.registerOre("gemFluorite", new ItemStack(MekanismItems.FluoriteClump, 1, 0));
        OreDictionary.registerOre("dustFluorite", new ItemStack(MekanismItems.OtherDust, 1, 7));
        OreDictionary.registerOre("dustCharcoal", new ItemStack(MekanismItems.OtherDust, 1, 8));
        OreDictionary.registerOre("PlutoniumPellet", new ItemStack(MekanismItems.PlutoniumPellet, 1));
        OreDictionary.registerOre("Antimatter", new ItemStack(MekanismItems.AntimatterPellet, 1));
        OreDictionary.registerOre("ReprocessedFissileFragment", new ItemStack(MekanismItems.ReprocessedFissileFragment, 1));
        OreDictionary.registerOre("PoloniumPellet", new ItemStack(MekanismItems.PoloniumPellet, 1));
    }
}
