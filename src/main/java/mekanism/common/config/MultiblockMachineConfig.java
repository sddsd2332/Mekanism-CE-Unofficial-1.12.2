package mekanism.common.config;

import mekanism.common.config.options.BooleanOption;
import mekanism.common.config.options.DoubleOption;
import mekanism.common.config.options.IntOption;

public class MultiblockMachineConfig extends BaseConfig {

    public final IntOption MAXThreadUpgrade = new IntOption(this, "MAXThreadUpgrade", 8,
            "The maximum number of thread upgrades that can be installed", 1, Integer.MAX_VALUE).setRequiresGameRestart();
    public final IntOption MAXThreadUpgradeSize = new IntOption(this, "MAXThreadUpgradeSize", 8,
            "The maximum number of stacks that can be stacked for thread upgrades", 1, 64).setRequiresGameRestart();

    public final IntOption LargeElectrolyticSeparatorProcesses = new IntOption(this, "LargeElectrolyticSeparatorProcesses", 256, "The number of threads in a large electrolytic separator affects the machine's maximum energy, energy usage, and how quickly it can operate per use.", 10, 102400).setRequiresGameRestart();
    public final IntOption LargeChemicalInfuserProcesses = new IntOption(this,"LargeChemicalInfuserProcesses",256,"The number of threads in a large chemical infuser affects the machine's maximum energy, energy usage, and how quickly it can operate per use.", 10, 102400).setRequiresGameRestart();
    public final IntOption LargeChemicalWasherProcesses = new IntOption(this,"LargeChemicalWasherProcesses",256,"The number of threads in a large chemical washer affects the machine's maximum energy, energy usage, and how quickly it can operate per use.", 10, 102400).setRequiresGameRestart();

    public final IntOption LargeWindGeneratorProcesses = new IntOption(this,"LargeWindGeneratorProcesses",1024,"The number of threads in large wind turbines affects the machine's power generation, maximum energy storage, and energy output.").setRequiresGameRestart();
    public final BooleanOption LargeWindGenerationRangeStops = new BooleanOption(this,"LargeWindGenerationRangeStops",true,"Centered on the main body, it detects whether there is an identical wind turbine within a range of LargeWindGeneratorRangeCheck * LargeWindGeneratorRangeCheck, and stops working if there is");
    public final IntOption LargeWindGeneratorRangeCheck = new IntOption(this,"LargeWindGeneratorRangeCheck",50,"Check if there are identical large wind turbines within the range; the default range is 50.",50,1024);
    public final BooleanOption LargeWindGenerationDamage = new BooleanOption(this,"LargeWindGenerationDamage",false,"Whether or not the organism causes harm when it is inside the leaf while it is working");
    public final IntOption LargeWindGenerationExplodeCount = new IntOption(this,"LargeWindGenerationExplodeCount",100,"An explosion can occur after the entity has been in the blade for a number of ticks.");
    public final BooleanOption LargewindGenerationExplode = new BooleanOption(this,"LargewindGenerationExplode",false, "An explosion occurs when an entity is inside the rotating blades of a large wind turbine.");
    public final IntOption LargeWindGenerationBlastRadius = new IntOption(this,"LargeWindGenerationBlastRadius",45,"The range of a large wind turbine when it explodes.");
    public final IntOption LargeWindGenerationMinY = new IntOption(this,  "LargeWindGenerationMinY", 50, "The minimum Y value that affects the Large Wind Generators Power generation.");
    public final IntOption LargeWindGenerationMaxY = new IntOption(this,  "LargeWindGenerationMaxY", 255, "The maximum Y value that affects the Wind Generators Power generation.");
    public final DoubleOption LargeWindGenerationMin = new DoubleOption(this,  "LargeWindGenerationMin", 60.0D, "Minimum base generation value of the Large Wind Generator.");
    public final DoubleOption LargeWindGenerationMax = new DoubleOption(this,  "LargeWindGenerationMax", 294000.0D, "Maximum base generation value of the Large Wind Generator.");

    public final IntOption LargeGasGeneratorProcesses = new IntOption(this,"LargeGasGeneratorProcesses",1024,"The number of threads in large gas generator affects the machine's power generation, maximum energy storage, and energy output.").setRequiresGameRestart();
    public final IntOption LargeSolarNeutronProcesses = new IntOption(this,"LargeSolarNeutronProcesses",256,"The number of threads in a large solar neutron affects the machine's maximum energy, energy usage, and how quickly it can operate per use.", 10, 102400).setRequiresGameRestart();

    @Override
    public String getCategory() {
        return "multiblock";
    }
}
