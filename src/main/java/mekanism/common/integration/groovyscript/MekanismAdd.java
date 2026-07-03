package mekanism.common.integration.groovyscript;

import com.cleanroommc.groovyscript.compat.mods.mekanism.Mekanism;
import mekanism.common.integration.groovyscript.machinerecipe.*;

public class MekanismAdd extends Mekanism {

    public final IsotopicCentrifuge isotopicCentrifuge = new IsotopicCentrifuge();
    public final NutritionalLiquifier nutritionalLiquifier = new NutritionalLiquifier();
    public final OrganicFarm organicFarm = new OrganicFarm();
    public final AntiprotonicNucleosynthesizer antiprotonicNucleosynthesizer = new AntiprotonicNucleosynthesizer();
    public final Stamping stamping = new Stamping();
    public final Rolling rolling = new Rolling();
    public final Brushed brushed = new Brushed();
    public final Turning turning = new Turning();
    public final Alloy alloy = new Alloy();
    public final CellExtractor cellExtractor = new CellExtractor();
    public final CellSeparator cellSeparator = new CellSeparator();
    public final Recycler recycler = new Recycler();

    public final AmbientAccumulator ambient = new AmbientAccumulator();
    public final FusionCooling fusionCooling = new FusionCooling();
    public final RotaryCondensentrator rotaryCondensentrator = new RotaryCondensentrator();
    public final Washer washer = new Washer();
    public final ItemToEnergy itemToEnergy = new ItemToEnergy();
    public final GasToEnergy gasToEnergy = new GasToEnergy();

    public MekanismAdd() {
    }

}
