package mekanism.multiblockmachine.common.registries;

import mekanism.common.item.ItemMekanism;
import mekanism.common.item.ItemUpgrade;
import mekanism.multiblockmachine.common.MekanismMultiblockMachine;
import mekanism.multiblockmachine.common.MultiblockMachineUpgrades;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry.ObjectHolder;
import net.minecraftforge.registries.IForgeRegistry;

@ObjectHolder(MekanismMultiblockMachine.MODID)
public class MultiblockMachineItems {

    public static final Item gas_adsorption_fractionation_module = new ItemMekanism().setCreativeTab(MekanismMultiblockMachine.tabMekanismMultiblockMachine).setMaxDamage(100000).setMaxStackSize(1);
    public static final Item high_frequency_fusion_molding_module = new ItemMekanism().setCreativeTab(MekanismMultiblockMachine.tabMekanismMultiblockMachine).setMaxDamage(100000).setMaxStackSize(1);
    public static final Item LaserLenses = new ItemMekanism().setCreativeTab(MekanismMultiblockMachine.tabMekanismMultiblockMachine).setMaxDamage(100000).setMaxStackSize(1);
    public static final Item advanced_electrolysis_core = new ItemMekanism().setCreativeTab(MekanismMultiblockMachine.tabMekanismMultiblockMachine);
    public static final Item ThreadUpgrade = new ItemUpgrade(MultiblockMachineUpgrades.THREAD).setCreativeTab(MekanismMultiblockMachine.tabMekanismMultiblockMachine);


    public static void registerItems(IForgeRegistry<Item> registry) {
        registry.register(init(gas_adsorption_fractionation_module, "gas_adsorption_fractionation_module"));
        registry.register(init(high_frequency_fusion_molding_module, "high_frequency_fusion_molding_module"));
        registry.register(init(LaserLenses, "LaserLenses"));
        registry.register(init(advanced_electrolysis_core,"advanced_electrolysis_core"));
        registry.register(init(ThreadUpgrade, "ThreadUpgrade"));
    }

    public static void registryItem(IForgeRegistry<Item> registry, Item item, String name) {
        registry.register(init(item, name));
    }

    public static Item init(Item item, String name) {
        return item.setTranslationKey(name).setRegistryName(new ResourceLocation(MekanismMultiblockMachine.MODID, name));
    }

}
