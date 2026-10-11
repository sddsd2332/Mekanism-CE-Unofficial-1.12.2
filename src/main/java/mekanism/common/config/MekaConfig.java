package mekanism.common.config;

import mekanism.common.config.options.BooleanOption;
import mekanism.common.config.options.DoubleOption;
import mekanism.common.config.options.FloatOption;
import mekanism.common.config.options.IntOption;
import mekanism.common.item.armor.ItemMekaSuitArmor;
import net.minecraft.util.DamageSource;

import java.util.LinkedHashMap;
import java.util.Map;

public class MekaConfig extends BaseConfig {

    public DoubleOption mekaSuitBaseEnergyCapacity = new DoubleOption(this, "baseEnergyCapacity", 16000000, "Energy capacity (Joules) of MekaSuit items without any installed upgrades. Quadratically scaled by upgrades.");
    public DoubleOption mekaSuitBaseChargeRate = new DoubleOption(this, "chargeRate", 100000, "Amount (joules) of energy the MekaSuit can accept per tick. Quadratically scaled by upgrades.");
    public DoubleOption mekaSuitInventoryChargeRate = new DoubleOption(this, "inventoryChargeRate", 10000, "Amount (joules) of energy the MekaSuit can transfer to inventory items per tick.");
    public DoubleOption mekaSuitBaseJumpEnergyUsage = new DoubleOption(this, "baseJumpEnergyUsage", 1000, "Energy usage (Joules) of MekaSuit when adding 0.1 to jump motion.");
    public DoubleOption mekaSuitEnergyUsagePotionTick = new DoubleOption(this, "energyUsagePotionTick", 40000, "Energy usage (Joules) of MekaSuit when lessening a potion effect.");
    public DoubleOption mekaSuitEnergyUsageMagicReduce = new DoubleOption(this, "energyUsageMagicReduce", 1000, "Energy cost/multiplier in Joules for reducing magic damage via the inhalation purification unit. Energy cost is: MagicDamage * energyUsageMagicPrevent. (1 MagicDamage is 1 half heart).");
    public FloatOption mekaSuitEnergyUsageFall = new FloatOption(this, "energyUsageFall", 50, "Energy cost/multiplier in Joules for reducing fall damage with MekaSuit Boots. Energy cost is: FallDamage * freeRunnerFallEnergyCost. (1 FallDamage is 1 half heart)");
    public DoubleOption mekaSuitSolarRechargingRate = new DoubleOption(this, "solarRechargingRate", 500, "Solar recharging rate (Joules) of helmet per tick, per upgrade installed.");
    public DoubleOption mekaSuitEnergyUsageVisionEnhancement = new DoubleOption(this, "energyUsageVisionEnhancement", 500, "Energy usage (Joules) of MekaSuit per tick of using vision enhancement.");
    public DoubleOption mekaSuitEnergyUsageHydrostaticRepulsion = new DoubleOption(this, "energyUsageHydrostaticRepulsion", 500, "Energy usage (Joules) of MekaSuit per tick of using hydrostatic repulsion.");
    public DoubleOption mekaSuitEnergyUsageNutritionalInjection = new DoubleOption(this, "energyUsageNutritionalInjection", 20000, "Energy usage (Joules) of MekaSuit per half-food of nutritional injection.");
    public DoubleOption mekaSuitEnergyUsageDamage = new DoubleOption(this, "energyUsageDamage", 100000, "Energy usage (Joules) of MekaSuit per unit of damage applied.");
    public IntOption mekaSuitNutritionalMaxStorage = new IntOption(this, "nutritionalMaxStorage", 128000, "Maximum amount of Nutritional Paste storable by the nutritional injection unit.", 1, Integer.MAX_VALUE);
    public IntOption mekaSuitNutritionalTransferRate = new IntOption(this, "nutritionalTransferRate", 256, "Rate at which Nutritional Paste can be transferred into the nutritional injection unit.", 1, Integer.MAX_VALUE);
    public IntOption mekaSuitJetpackMaxStorage = new IntOption(this, "jetpackMaxStorage", 48000, "Maximum amount of Hydrogen storable in the jetpack unit.", 1, Integer.MAX_VALUE);
    public IntOption mekaSuitJetpackTransferRate = new IntOption(this, "jetpackTransferRate", 256, "Rate at which Hydrogen can be transferred into the jetpack unit.", 1, Integer.MAX_VALUE);
    public FloatOption mekaSuitFallDamageRatio = new FloatOption(this, "fallDamageReductionRatio", 1, "Percent of damage taken from falling that can be absorbed by MekaSuit Boots when they have enough power.", 0, 1);
    public FloatOption mekaSuitMagicDamageRatio = new FloatOption(this, "magicDamageReductionRatio", 1F, "Percent of damage taken from magic damage that can be absorbed by MekaSuit Helmet with Purification unit when it has enough power.", 0F, 1F);
    public FloatOption mekaSuitUnspecifiedDamageRatio = new FloatOption(this, "unspecifiedDamageReductionRatio", 1, "Percent of damage taken from other non explicitly supported damage types that don't bypass armor when the MekaSuit has enough power and a full suit is equipped.", 0, 1);
    public DoubleOption mekaSuitGeothermalChargingRate = new DoubleOption(this, "geothermalChargingRate", 10.5, "Geothermal charging rate (Joules) of pants per tick, per degree above ambient, per upgrade installed. This value scales down based on how much of the MekaSuit Pants is submerged. Fire is treated as having a temperature of ~200K above ambient, lava has a temperature of 1,000K above ambient.");
    public FloatOption mekaSuitHeatDamageReductionRatio = new FloatOption(this, "heatDamageReductionRatio", 0.8F, "Percent of heat damage negated by MekaSuit Pants with maximum geothermal generator units installed. This number scales down linearly based on how many units are actually installed.", 0, 1);
    public DoubleOption mekaSuitElytraEnergyUsage = new DoubleOption(this, "elytraEnergyUsage", 32000, "Energy usage (Joules) per second of the MekaSuit when flying with the Elytra Unit.");
    public DoubleOption mekaSuitEnergyUsageSprintBoost = new DoubleOption(this, "energyUsageSprintBoost", 100, "Energy usage (Joules) of MekaSuit when adding 0.1 to sprint motion.");
    public DoubleOption mekaSuitEnergyUsageGravitationalModulation = new DoubleOption(this, "energyUsageGravitationalModulation", 1000, "Energy usage (Joules) of MekaSuit per tick when flying via Gravitational Modulation.");
    public DoubleOption mekaSuitEnergyUsageItemAttraction = new DoubleOption(this, "energyUsageItemAttraction", 250, "Energy usage (Joules) of MekaSuit per tick of attracting a single item.");
    public DoubleOption mekaToolBaseEnergyCapacity = new DoubleOption(this, "mekaToolbaseEnergyCapacity", 16000000, "Energy capacity (Joules) of the Meka-Tool without any installed upgrades. Quadratically scaled by upgrades.");
    public DoubleOption mekaToolBaseChargeRate = new DoubleOption(this, "mekaToolBaseChargeRate", 100000, "Amount (joules) of energy the Meka-Tool can accept per tick. Quadratically scaled by upgrades.");
    public DoubleOption mekaToolEnergyUsageShearEntity = new DoubleOption(this, "mekaToolEnergyUsageShearEntity", 10, "Cost in Joules of using the Meka-Tool to shear entities.");
    public BooleanOption mekaToolExtendedMining = new BooleanOption(this, "mekaToolExtendedMining", true, "Enable the 'Extended Vein Mining' mode for the Meka-Tool. (Allows vein mining everything not just ores/logs)");
    public DoubleOption mekaToolEnergyUsageHoe = new DoubleOption(this, "mekaToolEnergyUsageHoe", 10, "Cost in Joules of using the Meka-Tool as a hoe.");
    public DoubleOption mekaToolEnergyUsageShovel = new DoubleOption(this, "mekaToolEnergyUsageShovel", 10, "Cost in Joules of using the Meka-Tool as a shovel for making paths.");
    public FloatOption mekaToolBaseEfficiency = new FloatOption(this, "mekaToolBaseEfficiency", 4F, "Efficiency of the Meka-Tool with energy but without any upgrades.");
    public DoubleOption mekaToolEnergyUsageSilk = new DoubleOption(this, "mekaToolEnergyUsageSilk", 100, "Silk touch energy (Joules) usage of the Meka-Tool. (Gets multiplied by speed factor)");
    public DoubleOption mekaToolEnergyUsage = new DoubleOption(this, "mekaToolEnergyUsage", 10, "Base energy (Joules) usage of the Meka-Tool. (Gets multiplied by speed factor)");
    public IntOption mekaToolBaseDamage = new IntOption(this, "mekaToolBaseDamage", 4, "Damage applied by Meka-Tool without using any energy.");
    public DoubleOption mekaToolAttackSpeed = new DoubleOption(this, "mekaToolAttackSpeed", -2.4, "Attack speed of the Meka-Tool.", -4, 100);
    public DoubleOption mekaToolEnergyUsageWeapon = new DoubleOption(this, "mekaToolEnergyUsageWeapon", 2000, "Cost in Joules of using the Meka-Tool to deal 4 units of damage.");
    public DoubleOption mekaToolMaxTeleportReach = new DoubleOption(this, "mekaToolMaxTeleportReach", 100, "Maximum distance a player can teleport with the Meka-Tool.");
    public DoubleOption mekaToolEnergyUsageTeleport = new DoubleOption(this, "mekaToolEnergyUsageTeleport", 1000, "Cost in Joules of using the Meka-Tool to teleport 10 blocks.");
    public DoubleOption mekaEnergyUsageHealthRegeneration = new DoubleOption(this, "mekaEnergyUsageHealthRegeneration", 100, "How much energy regeneration is needed for a health regeneration");
    public final DoubleOption mekaFishBaseEnergyCapacity = new DoubleOption(this, "mekaFishBaseEnergyCapacity", 16000000, "Energy capacity (Joules) of the Meka-Fish Rod without any installed upgrades. Quadratically scaled by upgrades.");
    public final DoubleOption mekaFishBaseChargeRate = new DoubleOption(this, "mekaFishBaseChargeRate", 100000, "Amount (joules) of energy the Meka-Fish Rod can accept per tick. Quadratically scaled by upgrades.");

    public final Map<DamageSource, FloatOption> mekaSuitDamageRatios = create();

    public final Map<DamageSource, FloatOption> create() {
        Map<DamageSource, FloatOption> map = new LinkedHashMap<>();
        for (DamageSource type : ItemMekaSuitArmor.getSupportedSources()) {
            map.put(type, new FloatOption(this, type.getDamageType() + "DamageReductionRatio", 1F, "Percent of damage taken from " + type.getDamageType() + " that can be absorbed by the MekaSuit when there is enough power and a full suit is equipped.", 0F, 1F));
        }
        return map;
    }

    @Override
    public String getCategory() {
        return "meka";
    }
}
