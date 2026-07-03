package mekanism.common;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.OreGas;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import java.util.Locale;
import java.util.stream.Collectors;

public class MekanismFluids {

    public static final Gas Hydrogen = new Gas("hydrogen", 0xFFFFFF);
    public static final Gas Oxygen = new Gas("oxygen", 0x6CE2FF);
    public static final Gas Water = new Gas("water", "mekanism:blocks/liquid/LiquidSteam");
    public static final Gas Chlorine = new Gas("chlorine", 0xCFE800);
    public static final Gas SulfurDioxide = new Gas("sulfurdioxide", 0xA99D90);
    public static final Gas SulfurTrioxide = new Gas("sulfurtrioxide", 0xCE6C6C);
    public static final Gas SulfuricAcid = new Gas("sulfuricacid", 0x82802B);
    public static final Gas HydrogenChloride = new Gas("hydrogenchloride", 0xA8F1E9);

    public static final Fluid HeavyWater = new Fluid("heavywater", new ResourceLocation(Mekanism.MODID, "blocks/liquid/LiquidHeavyWater"),
            new ResourceLocation(Mekanism.MODID, "blocks/liquid/LiquidHeavyWater"));
    public static final Fluid Steam = new Fluid("steam", new ResourceLocation(Mekanism.MODID, "blocks/liquid/LiquidSteam"),
            new ResourceLocation(Mekanism.MODID, "blocks/liquid/LiquidSteam")).setGaseous(true);


    //Internal gases
    public static final Gas LiquidOsmium = new Gas("liquidosmium", 0x52bdca);
    public static final Gas Ethene = new Gas("ethene", 0xEACCF9);
    public static final Gas Sodium = new Gas("sodium", 0xE9FEF4);
    public static final Gas Brine = new Gas("brine", 0xFEEF9C);
    public static final Gas Deuterium = new Gas("deuterium", 0xFF3232);
    public static final Gas Tritium = new Gas("tritium", 0x64FF70);
    public static final Gas FusionFuel = new Gas("fusionfuel", 0x7E007D);
    public static final Gas Lithium = new Gas("lithium", 0xEBA400);

    /**
     * ADD START
     */
    //V10 gases

    public static final Gas HydrofluoricAcid = new Gas("hydrofluoricacid", 0xFFC6C7BD);
    public static final Gas Antimatter = new Gas("antimatter", 0xA464B3);
    public static final Gas FissileFuel = new Gas("fissilefuel", 0x2E332F);
    public static final Gas SuperheatedSodium = new Gas("superheatedsodium", 0xFFD19469);
    public static final Gas UraniumHexafluoride = new Gas("uraniumhexafluoride", 0xFF809960);
    public static final Gas Uraniumoxide = new Gas("uraniumoxide", 0xFFE1F573);
    public static final Gas NuclearWaste = new Gas("nuclearwaste", 0x4F412A);
    public static final Gas Plutonium = new Gas("plutonium", 0x1F919C);
    public static final Gas Polonium = new Gas("polonium", 0x1B9E7B);
    public static final Gas SpentNuclearWaste = new Gas("spentnuclearwaste", 0x262015);
    public static final Gas Biofuel = new Gas("Biofuel", 0x9dd221);
    public static final Gas NutritionalPaste = new Gas("nutritionalpaste", 0XEB6CA3);

    //color gas
    /*
    public static final Gas WHITE = new Gas("white.name", 16383998);
    public static final Gas ORANGE = new Gas("orange.name", 16351261);
    public static final Gas MAGENTA = new Gas("magenta.name", 13061821);
    public static final Gas LIGHT_BLUE = new Gas("lightBlue.name", 3847130);
    public static final Gas YELLOW = new Gas("yellow.name", 16701501);
    public static final Gas LIME = new Gas("lime.name", 8439583);
    public static final Gas PINK = new Gas("pink.name", 15961002);
    public static final Gas GRAY = new Gas("gray.name", 4673362);
    public static final Gas SILVER = new Gas("silver.name", 10329495);
    public static final Gas CYAN = new Gas("cyan.name", 1481884);
    public static final Gas PURPLE = new Gas("purple.name", 8991416);
    public static final Gas BLUE = new Gas("blue.name", 3949738);
    public static final Gas BROWN = new Gas("brown.name", 8606770);
    public static final Gas GREEN = new Gas("green.name", 6192150);
    public static final Gas RED = new Gas("red.name", 11546150);
    public static final Gas BLACK = new Gas("black.name", 1908001);
     */

    /**
     * sddsd2332 add gas
     */

    public static final Gas NutrientSolution = new Gas("nutrientsolution", 0x1B9E7B);
    public static final Gas OxygenEnrichedWater = new Gas("oxygenenrichedwater", 0x6CE2FF);
    public static final Gas UnstableDimensional = new Gas("unstabledimensional", 0xFF9C1A);

    /**
     * ADD END
     */

    public static void register() {
        GasRegistry.register(Hydrogen).registerFluid("liquidhydrogen");
        GasRegistry.register(Oxygen).registerFluid("liquidoxygen");
        GasRegistry.register(Water).registerFluid();
        GasRegistry.register(Chlorine).registerFluid("liquidchlorine");
        GasRegistry.register(SulfurDioxide).registerFluid("liquidsulfurdioxide");
        GasRegistry.register(SulfurTrioxide).registerFluid("liquidsulfurtrioxide");
        GasRegistry.register(SulfuricAcid).registerFluid();
        GasRegistry.register(HydrogenChloride).registerFluid("liquidhydrogenchloride");
        GasRegistry.register(Ethene).registerFluid("liquidethene");
        GasRegistry.register(Sodium).registerFluid("liquidsodium");
        GasRegistry.register(Brine).registerFluid();
        GasRegistry.register(Deuterium).registerFluid("liquiddeuterium");
        GasRegistry.register(Tritium).registerFluid("liquidtritium");
        GasRegistry.register(FusionFuel).registerFluid("liquidfusionfuel");
        GasRegistry.register(Lithium).registerFluid("liquidlithium");

        GasRegistry.register(LiquidOsmium).setVisible(false);


        /**
         * ADD START
         */
        GasRegistry.register(HydrofluoricAcid).registerFluid("liquidhydrofluricacid");
        GasRegistry.register(Antimatter);
        GasRegistry.register(FissileFuel);
        GasRegistry.register(SuperheatedSodium).registerFluid("liquidsuperheatedsodium");
        GasRegistry.register(UraniumHexafluoride);
        GasRegistry.register(Uraniumoxide);
        GasRegistry.register(NutritionalPaste);
        /**
         *Radioactive material
         * */
        GasRegistry.register(NuclearWaste).setRadiation(0.01);
        GasRegistry.register(Plutonium).setRadiation(0.02);
        GasRegistry.register(Polonium).setRadiation(0.05);
        GasRegistry.register(SpentNuclearWaste).setRadiation(0.01);

        GasRegistry.register(Biofuel).registerFluid("bioethanol").setVisible(false);
        //color gas
        /*
        GasRegistry.register(WHITE);
        GasRegistry.register(ORANGE);
        GasRegistry.register(MAGENTA);
        GasRegistry.register(LIGHT_BLUE);
        GasRegistry.register(YELLOW);
        GasRegistry.register(LIME);
        GasRegistry.register(PINK);
        GasRegistry.register(GRAY);
        GasRegistry.register(SILVER);
        GasRegistry.register(CYAN);
        GasRegistry.register(PURPLE);
        GasRegistry.register(BLUE);
        GasRegistry.register(BROWN);
        GasRegistry.register(GREEN);
        GasRegistry.register(RED);
        GasRegistry.register(BLACK);
         */

        /** Register sddsd2332 add new gas */
        GasRegistry.register(NutrientSolution);
        GasRegistry.register(OxygenEnrichedWater);
        GasRegistry.register(UnstableDimensional);

        /**
         * ADD END
         */

        FluidRegistry.registerFluid(HeavyWater);
        FluidRegistry.registerFluid(Steam);

        for (Resource resource : Resource.values()) {
            String name = resource.getName();
            String nameLower = name.toLowerCase(Locale.ROOT);
            //Clean
            OreGas clean = new OreGas("clean" + name, "oregas." + nameLower, resource.tint);
            GasRegistry.register(clean);
            //Dirty
            GasRegistry.register(new OreGas(nameLower, "oregas." + nameLower, resource.tint, clean));
        }

        FluidRegistry.enableUniversalBucket();

        FluidRegistry.addBucketForFluid(HeavyWater);
        /*
        FluidRegistry.addBucketForFluid(Brine.getFluid());
        FluidRegistry.addBucketForFluid(Lithium.getFluid());
         */
        GasRegistry.getRegisteredGasses().stream().filter(Gas::isRegisterFluid).map(g -> new GasStack(g, Fluid.BUCKET_VOLUME)).collect(Collectors.toList()).forEach(gas -> FluidRegistry.addBucketForFluid(gas.getGas().getFluid()));

    }
}
