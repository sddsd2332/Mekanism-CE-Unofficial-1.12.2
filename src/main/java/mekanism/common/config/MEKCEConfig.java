package mekanism.common.config;

import io.netty.buffer.ByteBuf;
import mekanism.common.config.options.BooleanOption;
import mekanism.common.config.options.DoubleOption;
import mekanism.common.config.options.FloatOption;
import mekanism.common.config.options.IntOption;
import net.minecraftforge.common.config.Configuration;


public class MEKCEConfig extends BaseConfig {

    public final BooleanOption EnableDiamondCompat = new BooleanOption(this,  "EnableDiamondCompat", true,
            "Allows oredict'ed diamonds to be used in the enrichment chamber, like synthetic diamonds.");

    public final BooleanOption EnablePoorOresCompat = new BooleanOption(this,  "EnablePoorOresCompat", true,
            "Allows poor ores from railcraft to be used in the purification chamber and gives one clump ie one ingot.");

    public final BooleanOption EnableQuartzCompat = new BooleanOption(this,  "EnableQuartzCompat", true,
            "Allows quartz dust to be enriched into quartz Also allows quartz ore to be enriched into quartz dust");

    public final BooleanOption EnableSiliconCompat = new BooleanOption(this,  "EnableSiliconCompat", false,
            "When a mod that adds silicon (galacticraft, enderio, projectred and ae2) is detected, recipe for control circuit is changed from using iron to silicon in the metalurgic infuser");

    public final BooleanOption ShowHiddenGas = new BooleanOption(this,  "ShowHiddenGases", true, "Displays hidden gas in creative gas tanks, which is invalid if PrefilledGasTanks is not enabled");

    public final BooleanOption EmptyToCreateBin = new BooleanOption(this,  "EmptytoCreateBin", false, "Let Configurator clear Create Bin");

    public final BooleanOption EmptyToCreateGasTank = new BooleanOption(this,  "EmptyToCreateGasTank", false, "Let Configurator clear Create Gas Tank");

    public final BooleanOption EmptytoCreateFluidTank = new BooleanOption(this,  "EmptytoCreateFluidTank", false, "Let Configurator clear Create Fluid Tank");

    public final BooleanOption RotaryCondensentratorAuto = new BooleanOption(this,  "RotaryCondensentratorAuto", false, "Turn off automatic change gas and fluid ejection mode in Rotary Condensentrator?");

    public final IntOption ItemEjectionDelay = new IntOption(this,  "ItemEjectionDelay", 10, "Every how many ticks pop up an item, the default is 10 ticks", 1, Integer.MAX_VALUE);

    public final BooleanOption ItemsEjectWithoutDelay = new BooleanOption(this,  "ItemsEjectWithoutDelay", false, "If true, the Item Ejection Delay is ignored");

    public final IntOption MAXSpeedUpgrade = new IntOption(this,  "MAXSpeedUpgrade", 8,
            "The maximum number of speed upgrades that can be installed", 1, Integer.MAX_VALUE).setRequiresGameRestart();

    public final IntOption MAXEnergyUpgrade = new IntOption(this,  "MAXEnergyUpgrade", 8,
            "The maximum number of energy upgrades that can be installed", 1, Integer.MAX_VALUE).setRequiresGameRestart();

    public final IntOption MAXGasUpgrade = new IntOption(this,  "MAXGasUpgrade", 8,
            "The maximum number of gas upgrades that can be installed", 1, Integer.MAX_VALUE).setRequiresGameRestart();

    public final IntOption MAXMufflingUpgrade = new IntOption(this,  "MAXMufflingUpgrade", 4,
            "The maximum number of muffling upgrades that can be installed", 1, Integer.MAX_VALUE).setRequiresGameRestart();

    public final IntOption MAXSpeedUpgradeSize = new IntOption(this,  "MAXSpeedUpgradeSize", 8,
            "The maximum number of stacks that can be stacked for speed upgrades", 1, 64).setRequiresGameRestart();

    public final IntOption MAXEnergyUpgradeSize = new IntOption(this,  "MAXEnergyUpgradeSize", 8,
            "The maximum number of stacks that can be stacked for energy upgrades", 1, 64).setRequiresGameRestart();

    public final IntOption MAXGasUpgradeSize = new IntOption(this,  "MAXGasUpgradeSize", 8,
            "The maximum number of stacks that can be stacked for gas upgrades", 1, 64).setRequiresGameRestart();

    public final IntOption MAXMufflingUpgradeSize = new IntOption(this,  "MAXMufflingUpgradeSize", 4,
            "The maximum number of stacks that can be stacked for muffling upgrades", 1, 64).setRequiresGameRestart();

    public final IntOption MAXspeedmachines = new IntOption(this,  "Maximumspeedmultiplierforsomemachines", 256,
            "Modify the maximum speed multiplier for some machines", 1, Integer.MAX_VALUE);

    public final DoubleOption seed = new DoubleOption(this,  "seed", 1D,
            "When turning seeds into crops, the chance to produce seeds for each operation in Organic Farm").setRequiresGameRestart();

    public final DoubleOption log = new DoubleOption(this,  "log", 1D,
            "When turning seeds into crops, the opportunity to produce log in each operation in Organic Farm.").setRequiresGameRestart();

    public final BooleanOption EnableGlassInThermal = new BooleanOption(this,  "EnableGlassInThermal", false,
            "Enabling Structural Glass for the Thermal Evaporation Plant may contain some issues");

    public final BooleanOption EnableConfiguratorWrench = new BooleanOption(this,  "EnableConfiguratorWrench", true, "Enable the configurator's wrench mode");

    public final IntOption MAXTierSize = new IntOption(this,  "MAXTierSize", 8,
            "The maximum number of stacks that can be stacked in Tier Instale", 1, 64).setRequiresGameRestart();

    public final BooleanOption EnableUpgradeConfigure = new BooleanOption(this,  "EnableUpgradeConfigure", false, "Enable an upgrade similar to IC2");


    public final IntOption MAXThreadUpgrade = new IntOption(this,  "MAXThreadUpgrade", 8,
            "The maximum number of thread upgrades that can be installed", 1, Integer.MAX_VALUE).setRequiresGameRestart();

    public final IntOption MAXThreadUpgradeSize = new IntOption(this,  "MAXThreadUpgradeSize", 8,
            "The maximum number of stacks that can be stacked for thread upgrades", 1, 64).setRequiresGameRestart();

    public final BooleanOption BinRecipeClosed = new BooleanOption(this,  "BinRecipeClosed", false, "Turn off the BIN synthesis recipe").setRequiresGameRestart();

    public final BooleanOption EnableSuperFumoRecipe = new BooleanOption(this, "EnableSuperFumoRecipe", true, "Enable the Super FuMo crafting recipe.").setRequiresGameRestart();

    public final BooleanOption BinRecipeRemovesItem = new BooleanOption(this,  "BinRecipeRemovesItem", false, "Close Bin to remove items").setRequiresGameRestart();

    public final IntOption MaximumEjectionDelay = new IntOption(this,  "MaximumEjectionDelay", 40, "Maximum ejection delay for gases and fluids");
    public final IntOption TankChangeSpeed = new IntOption(this,  "TankChangeSpeed", 20, "Record changes in the contents of the tank,Affects the ejection speed");
    public final FloatOption LowEjectionThreshold = new FloatOption(this,  "LowEjectionThreshold", 0.5F, "Low ejection thresholds for gases and fluids, below which the MaximumEjectionDelay is waited for to be met before output", 0, 1);
    public final FloatOption HighEjectionThreshold = new FloatOption(this,  "HighEjectionThreshold", 0.85F, "High ejection thresholds for gases and fluids, above which they are immediately output", 0, 1);
    public final BooleanOption PlasticWrench = new BooleanOption(this,"PlasticWrench",false,"If true, allow the plastic to fall through the wrench");
    public final BooleanOption StackingPlacementLimits = new BooleanOption(this,"StackingPlacementLimits",true,"If the number exceeds 1, it is forbidden to place blocks");
    public final FloatOption freeRunnerFallDamageRatio = new FloatOption(this,"fallDamageReductionRatio",1F,"Percent of damage taken from falling that can be absorbed by Free Runners when they have enough power.",0,1);
    public final FloatOption freeRunnerFallEnergyCost = new FloatOption(this,"fallEnergyCost",50,"Energy cost/multiplier in Joules for reducing fall damage with free runners. Energy cost is: FallDamage * freeRunnerFallEnergyCost. (1 FallDamage is 1 half heart)");
    public final BooleanOption EnableTheDefaultConfiguration = new BooleanOption(this,"EnableTheDefaultConfiguration",true,"Allows the machine to use the default configuration surface, which is empty by default if false");
    public final BooleanOption EnableAddArrItemRecyclerRecipe = new BooleanOption(this,"EnableAddArrItemRecyclerRecipe",true,"If true, all items are iterated through and added to the Recycler recipe").setRequiresGameRestart();
    public final BooleanOption EnableRecyclerRecipeInJei = new BooleanOption(this,"EnableRecyclerRecipeInJei",true,"If true, the Recycler recipe is allowed to be displayed within the jei").setRequiresGameRestart();

    public final IntOption DigitalMinerMinY = new IntOption(this,"DigitalMinerMinY",0,"The minimum Y value of DigitalMiner");
    public final IntOption DigitalMinerMaxY = new IntOption(this,"DigitalMinerMaxY",255,"The maximum Y value of DigitalMiner");
    public final BooleanOption BananaRadiation = new BooleanOption(this,"BananaRadiation",false,"When the player consumes a mineral item containing bananas, they gain a small amount of radiation.");
    public final DoubleOption BananaRadiationMeasurement = new DoubleOption(this,"BananaRadiationMeasurement",0.000000078,"The radiation level of each banana",0,Double.MAX_VALUE);
    public final BooleanOption  TEUpgrade = new BooleanOption(this,"TEUpgrade",false,"Can use the Tier Installer to upgrade the level of TE.");

    @Override
    public void load(Configuration config) {
        super.load(config);
        validate();
    }

    @Override
    public void read(ByteBuf config) {
        super.read(config);
        validate();
    }

    private void validate() {
        //ensure HighEjectionThreshold is > LowEjectionThreshold
        HighEjectionThreshold.set(Math.max(LowEjectionThreshold.val(), HighEjectionThreshold.val()));
    }

    @Override
    public String getCategory() {
        return "mekce";
    }
}
