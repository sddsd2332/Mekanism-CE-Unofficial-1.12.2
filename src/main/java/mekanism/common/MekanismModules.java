package mekanism.common;

import mekanism.api.gear.ModuleData;
import mekanism.common.content.gear.ModuleHelper;
import mekanism.common.content.gear.mekafishrod.ModuleintelligentUnit;
import mekanism.common.content.gear.mekasuit.*;
import mekanism.common.content.gear.mekatool.*;
import mekanism.common.content.gear.shared.ModuleColorModulationUnit;
import mekanism.common.content.gear.shared.ModuleEnergyUnit;
import mekanism.common.content.gear.shared.ModuleMagneticUnit;
import mekanism.common.integration.MekanismHooks;
import net.minecraft.init.Enchantments;
import net.minecraft.item.EnumRarity;
import net.minecraftforge.fml.common.Loader;

public class MekanismModules {

    //Shared
    public static final ModuleData<ModuleEnergyUnit> ENERGY_UNIT = ModuleHelper.register("energy_unit", ModuleEnergyUnit::new, builder -> builder.maxStackSize(8).rarity(EnumRarity.UNCOMMON).noDisable());
    public static final ModuleData<ModuleMagneticUnit> MAGNETIC_UNIT = ModuleHelper.register("magnetic_unit", ModuleMagneticUnit::new, builder -> builder.rarity(EnumRarity.UNCOMMON).canEnable(!Loader.isModLoaded(MekanismHooks.MekanismMixinHelp_MOD_ID)).notEnabled("need.installation.mod"));
    //Shared Armor
    public static final ModuleData<ModuleColorModulationUnit> COLOR_MODULATION_UNIT = ModuleHelper.register("color_modulation_unit", ModuleColorModulationUnit::new, builder -> builder.rarity(EnumRarity.UNCOMMON).noDisable());
    public static final ModuleData<?> LASER_DISSIPATION_UNIT = ModuleHelper.registerMarker("laser_dissipation_unit", builder -> builder.rarity(EnumRarity.UNCOMMON));
    public static final ModuleData<?> RADIATION_SHIELDING_UNIT = ModuleHelper.registerMarker("radiation_shielding_unit", builder -> builder.rarity(EnumRarity.UNCOMMON));

    //Meka-Tool
    public static final ModuleData<ModuleExcavationEscalationUnit> EXCAVATION_ESCALATION_UNIT = ModuleHelper.register("excavation_escalation_unit", ModuleExcavationEscalationUnit::new, builder -> builder.maxStackSize(4).rarity(EnumRarity.UNCOMMON).handlesModeChange().rendersHUD());
    public static final ModuleData<ModuleAttackAmplificationUnit> ATTACK_AMPLIFICATION_UNIT = ModuleHelper.register("attack_amplification_unit", ModuleAttackAmplificationUnit::new, builder -> builder.maxStackSize(4).rarity(EnumRarity.UNCOMMON).rendersHUD());
    public static final ModuleData<ModuleFarmingUnit> FARMING_UNIT = ModuleHelper.register("farming_unit", ModuleFarmingUnit::new, builder -> builder.maxStackSize(4).rarity(EnumRarity.UNCOMMON).exclusive(ModuleData.ExclusiveFlag.INTERACT_BLOCK));
    public static final ModuleData<ModuleShearingUnit> SHEARING_UNIT = ModuleHelper.register("shearing_unit", ModuleShearingUnit::new, builder -> builder.rarity(EnumRarity.UNCOMMON).exclusive(ModuleData.ExclusiveFlag.INTERACT_ENTITY, ModuleData.ExclusiveFlag.INTERACT_BLOCK));
    public static final ModuleData<?> SILK_TOUCH_UNIT = ModuleHelper.registerEnchantBased("silk_touch_unit", () -> Enchantments.SILK_TOUCH, builder -> builder.rarity(EnumRarity.RARE).exclusive(ModuleData.ExclusiveFlag.OVERRIDE_DROPS));
    public static final ModuleData<?> FORTUNE_UNIT = ModuleHelper.registerEnchantBased("fortune_unit", () -> Enchantments.FORTUNE, builder -> builder.maxStackSize(3).rarity(EnumRarity.RARE).exclusive(ModuleData.ExclusiveFlag.OVERRIDE_DROPS));
    public static final ModuleData<ModuleBlastingUnit> BLASTING_UNIT = ModuleHelper.register("blasting_unit", ModuleBlastingUnit::new, builder -> builder.maxStackSize(4).rarity(EnumRarity.RARE).handlesModeChange().rendersHUD());
    public static final ModuleData<ModuleVeinMiningUnit> VEIN_MINING_UNIT = ModuleHelper.register("vein_mining_unit", ModuleVeinMiningUnit::new, builder -> builder.maxStackSize(4).rarity(EnumRarity.RARE).handlesModeChange().rendersHUD());
    public static final ModuleData<ModuleTeleportationUnit> TELEPORTATION_UNIT = ModuleHelper.register("teleportation_unit", ModuleTeleportationUnit::new, builder -> builder.rarity(EnumRarity.EPIC).exclusive(ModuleData.ExclusiveFlag.INTERACT_ANY));
    public static final ModuleData<?> LASER_CUTTER_UNIT = ModuleHelper.registerMarker("laser_cutter_unit", builder -> builder.rarity(EnumRarity.EPIC));

    //Helmet
    public static final ModuleData<ModuleElectrolyticBreathingUnit> ELECTROLYTIC_BREATHING_UNIT = ModuleHelper.register("electrolytic_breathing_unit", ModuleElectrolyticBreathingUnit::new, builder -> builder.maxStackSize(4).rarity(EnumRarity.UNCOMMON));
    public static final ModuleData<ModuleInhalationPurificationUnit> INHALATION_PURIFICATION_UNIT = ModuleHelper.register("inhalation_purification_unit", ModuleInhalationPurificationUnit::new, builder -> builder.rarity(EnumRarity.RARE));
    public static final ModuleData<ModuleVisionEnhancementUnit> VISION_ENHANCEMENT_UNIT = ModuleHelper.register("vision_enhancement_unit", ModuleVisionEnhancementUnit::new, builder -> builder.maxStackSize(4).rarity(EnumRarity.RARE).handlesModeChange().rendersHUD().disabledByDefault());
    public static final ModuleData<ModuleNutritionalInjectionUnit> NUTRITIONAL_INJECTION_UNIT = ModuleHelper.register("nutritional_injection_unit", ModuleNutritionalInjectionUnit::new, builder -> builder.rarity(EnumRarity.RARE).rendersHUD());
    public static final ModuleData<ModuleSolarRechargingUnit> SOLAR_RECHARGING_UNIT = ModuleHelper.register("solar_recharging_unit", ModuleSolarRechargingUnit::new, builder -> builder.maxStackSize(8).rarity(EnumRarity.RARE));


    //Chestplate
    public static final ModuleData<ModuleDosimeterUnit> DOSIMETER_UNIT = ModuleHelper.register("dosimeter_unit", ModuleDosimeterUnit::new, builder -> builder.rarity(EnumRarity.UNCOMMON).rendersHUD());
    public static final ModuleData<ModuleGeigerUnit> GEIGER_UNIT = ModuleHelper.register("geiger_unit", ModuleGeigerUnit::new, builder -> builder.rarity(EnumRarity.UNCOMMON).rendersHUD());
    public static final ModuleData<ModuleJetpackUnit> JETPACK_UNIT = ModuleHelper.register("jetpack_unit", ModuleJetpackUnit::new, builder -> builder.maxStackSize(4).rarity(EnumRarity.RARE).handlesModeChange().rendersHUD().exclusive(ModuleData.ExclusiveFlag.OVERRIDE_JUMP));
    public static final ModuleData<ModuleChargeDistributionUnit> CHARGE_DISTRIBUTION_UNIT = ModuleHelper.register("charge_distribution_unit", ModuleChargeDistributionUnit::new, builder -> builder.rarity(EnumRarity.RARE));
    public static final ModuleData<ModuleGravitationalModulatingUnit> GRAVITATIONAL_MODULATING_UNIT = ModuleHelper.register("gravitational_modulating_unit", ModuleGravitationalModulatingUnit::new, builder -> builder.rarity(EnumRarity.EPIC).handlesModeChange().rendersHUD().exclusive(ModuleData.ExclusiveFlag.OVERRIDE_JUMP));
    public static final ModuleData<ModuleElytraUnit> ELYTRA_UNIT = ModuleHelper.register("elytra_unit", ModuleElytraUnit::new, builder -> builder.rarity(EnumRarity.EPIC).handlesModeChange().modeChangeDisabledByDefault().canEnable(!Loader.isModLoaded(MekanismHooks.MekanismMixinHelp_MOD_ID)).notEnabled("need.installation.mod"));
    public static final ModuleData<ModuleHealthRegenerationUnit> HEALTH_REGENERATION_UNIT = ModuleHelper.register("health_regeneration_unit", ModuleHealthRegenerationUnit::new, builder -> builder.maxStackSize(10).rarity(EnumRarity.RARE));

    //Pants
    public static final ModuleData<ModuleLocomotiveBoostingUnit> LOCOMOTIVE_BOOSTING_UNIT = ModuleHelper.register("locomotive_boosting_unit", ModuleLocomotiveBoostingUnit::new, builder -> builder.maxStackSize(4).rarity(EnumRarity.RARE).handlesModeChange());
    public static final ModuleData<?> GYROSCOPIC_STABILIZATION_UNIT = ModuleHelper.registerMarker("gyroscopic_stabilization_unit", builder -> builder.rarity(EnumRarity.RARE));
    public static final ModuleData<ModuleHydrostaticRepulsorUnit> HYDROSTATIC_REPULSOR_UNIT = ModuleHelper.register("hydrostatic_repulsor_unit", ModuleHydrostaticRepulsorUnit::new, builder -> builder.maxStackSize(4).rarity(EnumRarity.RARE));
    public static final ModuleData<?> MOTORIZED_SERVO_UNIT = ModuleHelper.registerMarker("motorized_servo_unit", builder -> builder.maxStackSize(5).rarity(EnumRarity.RARE));

    //Boots
    public static final ModuleData<ModuleHydraulicPropulsionUnit> HYDRAULIC_PROPULSION_UNIT = ModuleHelper.register("hydraulic_propulsion_unit", ModuleHydraulicPropulsionUnit::new, builder -> builder.maxStackSize(4).rarity(EnumRarity.RARE));
    public static final ModuleData<ModuleMagneticAttractionUnit> MAGNETIC_ATTRACTION_UNIT = ModuleHelper.register("magnetic_attraction_unit", ModuleMagneticAttractionUnit::new, builder -> builder.maxStackSize(4).rarity(EnumRarity.RARE).handlesModeChange());
    public static final ModuleData<?> FROST_WALKER_UNIT = ModuleHelper.registerEnchantBased("frost_walker_unit", () -> Enchantments.FROST_WALKER, builder -> builder.maxStackSize(2).rarity(EnumRarity.RARE));

    //fish rod
    //水下收集模块【提高钓鱼时的幸运度】
    public static final ModuleData<?> FISHING_COLLECTING_UNIT = ModuleHelper.registerMarker("fishing_collecting_unit", builder -> builder.maxStackSize(10).rarity(EnumRarity.UNCOMMON));
    //钓饵模块【提升钓鱼速度】
    public static final ModuleData<?> FISHING_SPEED_UNIT = ModuleHelper.registerMarker("fishing_speed_unit", builder -> builder.maxStackSize(6).rarity(EnumRarity.UNCOMMON));
    //智能钓鱼模块【当钓鱼上时自动收杆并抛竿】,判断 EntityFishHook.ticksCatchable是否> 0，如果是则收杆
    public static final ModuleData<ModuleintelligentUnit> FISHING_INTELLIGENT_UNIT = ModuleHelper.register("fishing_intelligent_unit", ModuleintelligentUnit::new, builder -> builder.rarity(EnumRarity.UNCOMMON).handlesModeChange().disabledByDefault());
    //多重捕捞模块【当钓鱼成功时，能够掉起多个物品或者鱼】，通过ItemFishedEvent修改
    public static final ModuleData<?> FISHING_MULTIPLE_UNIT = ModuleHelper.registerMarker("fishing_multiple_unit", builder -> builder.maxStackSize(64).rarity(EnumRarity.UNCOMMON));


}
