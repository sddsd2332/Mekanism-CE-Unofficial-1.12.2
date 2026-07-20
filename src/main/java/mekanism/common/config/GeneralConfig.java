package mekanism.common.config;

import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.config.options.*;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.FluidTankTier;
import mekanism.common.tier.GasTankTier;
import mekanism.common.util.UnitDisplayUtils.EnergyType;
import mekanism.common.util.UnitDisplayUtils.TempType;

import java.util.EnumMap;

/**
 * Created by Thiakil on 15/03/2019.
 */
public class GeneralConfig extends BaseConfig {

    public final BooleanOption controlCircuitOreDict = new BooleanOption(this, "ControlCircuitOreDict", true,
            "Enables recipes using Control Circuits to use OreDict'd Control Circuits from other mods.");

    public final BooleanOption logPackets = new BooleanOption(this, "LogPackets", false,
            "Log Mekanism packet names. Debug setting.");

    public final BooleanOption dynamicTankEasterEgg = new BooleanOption(this, "DynamicTankEasterEgg", false,
            "Audible sparkles.");

    public final BooleanOption voiceServerEnabled = new BooleanOption(this, "WalkieTalkieServerEnabled", false,
            "Enables the voice server for Walkie Talkies.");

    public final BooleanOption cardboardSpawners = new BooleanOption(this, "AllowSpawnerBoxPickup", true,
            "Allows vanilla spawners to be moved with a Cardboard Box.");

    public final BooleanOption enableWorldRegeneration = new BooleanOption(this, "EnableWorldRegeneration", false,
            "Allows chunks to retrogen Mekanism ore blocks.");

    public final BooleanOption spawnBabySkeletons = new BooleanOption(this, "SpawnBabySkeletons", true,
            "Enable the spawning of baby skeletons. Think baby zombies but skeletons.");

    public final IntOption obsidianTNTDelay = new IntOption(this, "ObsidianTNTDelay", 100,
            "Fuse time for Obsidian TNT.");

    public final IntOption obsidianTNTBlastRadius = new IntOption(this, "ObsidianTNTBlastRadius", 12,
            "Radius of the explosion of Obsidian TNT.");

    public final IntOption UPDATE_DELAY = new IntOption(this, "ClientUpdateDelay", 10,
            "How many ticks must pass until a block's active state can sync with the client.");

    public final IntOption osmiumPerChunk = new IntOption(this, "OsmiumPerChunk", 12,
            "Chance that osmium generates in a chunk. (0 to Disable)", 0, Integer.MAX_VALUE);

    public final IntOption osmiumMaxVeinSize = new IntOption(this, "OsmiumVeinSize", 8,
            "Max number of blocks in an osmium vein.", 1, Integer.MAX_VALUE);

    public final IntOption copperPerChunk = new IntOption(this, "CopperPerChunk", 16,
            "Chance that copper generates in a chunk. (0 to Disable)", 0, Integer.MAX_VALUE);

    public final IntOption copperMaxVeinSize = new IntOption(this, "CopperVeinSize", 8,
            "Max number of blocks in a copper vein.", 1, Integer.MAX_VALUE);

    public final IntOption tinPerChunk = new IntOption(this, "TinPerChunk", 14,
            "Chance that tin generates in a chunk. (0 to Disable)", 0, Integer.MAX_VALUE);

    public final IntOption tinMaxVeinSize = new IntOption(this, "TinVeinSize", 8,
            "Max number of blocks in a tin vein.", 1, Integer.MAX_VALUE);

    public final IntOption saltPerChunk = new IntOption(this, "SaltPerChunk", 2,
            "Chance that salt generates in a chunk. (0 to Disable)", 0, Integer.MAX_VALUE);

    public final IntOption saltMaxVeinSize = new IntOption(this, "SaltVeinSize", 6,
            "Max number of blocks in a salt vein.", 1, Integer.MAX_VALUE);

    public final IntOption userWorldGenVersion = new IntOption(this, "WorldRegenVersion", 0,
            "Change this value to cause Mekanism to regen its ore in all loaded chunks.");

    public final DoubleOption FROM_IC2 = new DoubleOption(this, "JoulesToEU", 10D,
            "Conversion multiplier from EU to Joules (EU * JoulesToEU = Joules)");

    public final DoubleOption TO_IC2 = new DoubleOption(this, "EUToJoules", .1D,
            "Conversion multiplier from Joules to EU (Joules * EUToJoules = EU)");

    public final IntOption ic2InputTier = new IntOption(this, "IC2InputTier", 4,
            "Fixed IC2 sink tier Mekanism exposes when accepting IC2 energy.", 0, 30);

    public final DoubleOption FROM_RF = new DoubleOption(this, "JoulesToRF", 2.5D,
            "Conversion multiplier from RF to Joules (RF * JoulesToRF = Joules)");

    public final DoubleOption TO_RF = new DoubleOption(this, "RFToJoules", 0.4D,
            "Conversion multiplier from Joules to RF (Joules * RFToJoules = RF)");

    public final DoubleOption FROM_TESLA = new DoubleOption(this, "JoulesToTesla", 2.5D,
            "Conversion multiplier from Tesla to Joules (Tesla * JoulesToTesla = Joules)");

    public final DoubleOption TO_TESLA = new DoubleOption(this, "TeslaToJoules", 0.4D,
            "Conversion multiplier from Joules to Tesla (Joules * TeslaToJoules = Tesla)");

    public final DoubleOption FROM_FORGE = new DoubleOption(this, "JoulesToForge", 2.5D,
            "Conversion multiplier from Forge Energy to Joules (FE * JoulesToForge = Joules)");

    public final DoubleOption TO_FORGE = new DoubleOption(this, "ForgeToJoules", 0.4D,
            "Conversion multiplier from Joules to Forge Energy (Joules * ForgeToJoules = FE)");

    public final DoubleOption FROM_H2 = new DoubleOption(this, "HydrogenEnergyDensity", 200D,
            "How much energy is produced per mB of Hydrogen, also affects Electrolytic Separator usage, Ethylene burn rate and Gas generator energy capacity.");

    public final IntOption ETHENE_BURN_TIME = new IntOption(this, "EthyleneBurnTime", 40,
            "Burn time for Ethylene (1mB hydrogen + 2*bioFuel/tick*200ticks/100mB * 20x efficiency bonus).");

    public final DoubleOption ENERGY_PER_REDSTONE = new DoubleOption(this, "EnergyPerRedstone", 10000D,
            "How much energy (Joules) a piece of redstone gives in machines.");

    public final DoubleOption ENERGY_PER_REDSTONE_BLOCK = new DoubleOption(this, "EnergyPerRedstoneBlock", 90000D,
            "How much energy (Joules) a piece of redstone block gives in machines.");

    public final BooleanOption bulkSlotItemConversion = new BooleanOption(this, "BulkSlotItemConversion", true,
            "If enabled, item-to-gas, item-to-energy, and item-to-infusion inventory slots convert as many items per tick as their target storage can accept.");

    public final IntOption disassemblerEnergyUsage = new IntOption(this, "DisassemblerEnergyUsage", 10,
            "Base Energy (Joules) usage of the Atomic Disassembler. (Gets multiplied by speed factor)");

    public final IntOption disassemblerEnergyUsageWeapon = new IntOption(this, "DisassemblerEnergyUsageWeapon", 2000,
            "Cost in Joules of using the Atomic Disassembler as a weapon.");


    public final IntOption disassemblerMiningCount = new IntOption(this, "DisassemblerMiningCount", 128,
            "The max Atomic Disassembler Vein Mining Block Count.");

    public final BooleanOption disassemblerSlowMode = new BooleanOption(this, "DisassemblerSlowMode", true,
            "Enable the 'Slow' mode for the Atomic Disassembler.");

    public final BooleanOption disassemblerFastMode = new BooleanOption(this, "DisassemblerFastMode", true,
            "Enable the 'Fast' mode for the Atomic Disassembler.");

    public final BooleanOption disassemblerVeinMining = new BooleanOption(this, "DisassemblerVeinMiningMode", false,
            "Enable the 'Vein Mining' mode for the Atomic Disassembler.");

    public final IntOption disassemblerDamageMin = new IntOption(this, "DisassemblerDamageMin", 4,
            "The amount of damage the Atomic Disassembler does when it is out of power. (Value is in number of half hearts)");

    public final IntOption disassemblerDamageMax = new IntOption(this, "DisassemblerDamageMax", 20,
            "The amount of damage the Atomic Disassembler does when it has at least DisassemblerEnergyUsageWeapon power stored. (Value is in number of half hearts)");

    public final FloatOption disassemblerAttackSpeed = new FloatOption(this, "DisassemblerAttackSpeed", -2.4F, "Attack speed of the Atomic Disassembler.", -4F, 100);

    public final DoubleOption disassemblerBatteryCapacity = new DoubleOption(this, "DisassemblerBatteryCapacity", 1000000,
            "Maximum amount (joules) of energy the Atomic Disassembler can contain", 0, Double.MAX_VALUE).setRequiresGameRestart();

    public final DoubleOption disassemblerChargeRate = new DoubleOption(this, "disassemblerChargeRate", 5000,
            "Amount (joules) of energy the Atomic Disassembler can accept per tick.", 0, Double.MAX_VALUE).setRequiresGameRestart();

    public final IntOption VOICE_PORT = new IntOption(this, "VoicePort", 36123,
            "TCP port for the Voice server to listen on.", 1, 65535);

    //If this is less than 1, upgrades make machines worse. If less than 0, I don't even know.
    public final IntOption maxUpgradeMultiplier = new IntOption(this, "UpgradeModifier", 10,
            "Base factor for working out machine performance with upgrades - UpgradeModifier * (UpgradesInstalled/UpgradesPossible).", 1, Integer.MAX_VALUE);

    public final IntOption minerSilkMultiplier = new IntOption(this, "MinerSilkMultiplier", 6,
            "Energy multiplier for using silk touch mode with the Digital Miner.");

    public final BooleanOption prefilledGasTanks = new BooleanOption(this, "PrefilledGasTanks", true,
            "Add filled creative gas tanks to creative/JEI.");

    public final DoubleOption armoredJetpackDamageRatio = new DoubleOption(this, "ArmoredJetpackDamageRatio", 0.8,
            "Damage absorb ratio of the Armored Jetpack.");

    public final IntOption armoredJetpackDamageMax = new IntOption(this, "ArmoredJepackDamageMax", 115,
            "Max damage the Armored Jetpack can absorb.");

    public final BooleanOption aestheticWorldDamage = new BooleanOption(this, "AestheticWorldDamage", true,
            "If enabled, lasers can break blocks and the flamethrower starts fires.");

    public final BooleanOption opsBypassRestrictions = new BooleanOption(this, "OpsBypassRestrictions", false,
            "Ops can bypass the block security restrictions if enabled.");

    public final IntOption maxJetpackGas = new IntOption(this, "MaxJetpackGas", 24000,
            "Jetpack Gas Tank capacity in mB.");

    public final IntOption maxScubaGas = new IntOption(this, "MaxScubaGas", 24000,
            "Scuba Tank Gas Tank capacity in mB.");

    public final IntOption maxFlamethrowerGas = new IntOption(this, "MaxFlamethrowerGas", 24000,
            "Flamethrower Gas Tank capacity in mB.");

    public final IntOption maxPumpRange = new IntOption(this, "MaxPumpRange", 80,
            "Maximum block distance to pull fluid from for the Electric Pump.");

    public final BooleanOption pumpWaterSources = new BooleanOption(this, "PumpWaterSources", false,
            "If enabled makes Water and Heavy Water blocks be removed from the world on pump.");

    public final IntOption maxPlenisherNodes = new IntOption(this, "MaxPlenisherNodes", 4000,
            "Fluidic Plenisher stops after this many blocks.");

    public final DoubleOption evaporationHeatDissipation = new DoubleOption(this, "EvaporationHeatDissipation", 0.02D,
            "Thermal Evaporation Tower heat loss per tick.");

    public final DoubleOption evaporationTempMultiplier = new DoubleOption(this, "EvaporationTempMultiplier", 0.1D,
            "Temperature to amount produced ratio for Thermal Evaporation Tower.");

    public final DoubleOption evaporationSolarMultiplier = new DoubleOption(this, "EvaporationSolarMultiplier", 0.2D,
            "Heat to absorb per Solar Panel array of Thermal Evaporation Tower.");

    public final DoubleOption evaporationMaxTemp = new DoubleOption(this, "EvaporationMaxTemp", 3000D,
            "Max Temperature of the Thermal Evaporation Tower.");

    public final DoubleOption energyPerHeat = new DoubleOption(this, "EnergyPerHeat", 1000D,
            "Joules required by the Resistive Heater to produce one unit of heat. Also affects Thermoelectric Boiler's Water->Steam rate.");

    public final DoubleOption maxEnergyPerSteam = new DoubleOption(this, "MaxEnergyPerSteam", 100D,
            "Maximum Joules per mB of Steam. Also affects Thermoelectric Boiler.");

    public final DoubleOption superheatingHeatTransfer = new DoubleOption(this, "SuperheatingHeatTransfer", 10000D,
            "Amount of heat each Boiler heating element produces.");

    public final DoubleOption heatPerFuelTick = new DoubleOption(this, "HeatPerFuelTick", 4D,
            "Amount of heat produced per fuel tick of a fuel's burn time in the Fuelwood Heater.");

    public final BooleanOption allowTransmitterAlloyUpgrade = new BooleanOption(this, "AllowTransmitterAlloyUpgrade", true,
            "Allow right clicking on Cables/Pipes/Tubes with alloys to upgrade the tier.");

    public final BooleanOption allowChunkloading = new BooleanOption(this, "AllowChunkloading", true,
            "Disable to make the anchor upgrade not do anything.");

    public final BooleanOption allowProtection = new BooleanOption(this, "AllowProtection", true,
            "Enable the security system for players to prevent others from accessing their machines. Does NOT affect Frequencies.");

    public final IntOption portableTeleporterDelay = new IntOption(this, "PortableTeleporterDelay", 0,
            "Delay in ticks before a player is teleported after clicking the Teleport button in the portable teleporter.");

    public final DoubleOption quantumEntangloporterEnergyTransfer = new DoubleOption(this, "QuantumEntangloporterEnergyTransfer", 16000000D,
            "Maximum energy buffer (Mekanism Joules) of an Entangoloporter frequency - i.e. the maximum transfer per tick per frequency.", 0, Double.MAX_VALUE).setRequiresWorldRestart();

    public final IntOption quantumEntangloporterFluidBuffer = new IntOption(this, "quantumEntangloporterFluidBuffer", FluidTankTier.ULTIMATE.getBaseStorage(),
            "Maximum fluid buffer (mb) of an Entangoloporter frequency - i.e. the maximum transfer per tick per frequency. Default is ultimate tier tank capacity.", 0, Integer.MAX_VALUE).setRequiresWorldRestart();

    public final IntOption quantumEntangloporterGasBuffer = new IntOption(this, "quantumEntangloporterGasBuffer", GasTankTier.ULTIMATE.getBaseStorage(),
            "Maximum gas buffer (mb) of an Entangoloporter frequency - i.e. the maximum transfer per tick per frequency. Default is ultimate tier tank capacity.", 0, Integer.MAX_VALUE).setRequiresWorldRestart();

    public final BooleanOption blacklistIC2 = new BooleanOption(this, "BlacklistIC2Power", false,
            "Disables IC2 power integration. Requires world restart (server-side option in SMP).");

    public final BooleanOption blacklistRF = new BooleanOption(this, "BlacklistRFPower", false,
            "Disables Thermal Expansion RedstoneFlux power integration. Requires world restart (server-side option in SMP).");

    public final BooleanOption blacklistTesla = new BooleanOption(this, "BlacklistTeslaPower", false,
            "Disables Tesla power integration. Requires world restart (server-side option in SMP).");

    public final BooleanOption blacklistForge = new BooleanOption(this, "BlacklistForgePower", false,
            "Disables Forge Energy (FE,IF,uF,CF) power integration. Requires world restart (server-side option in SMP).");
    public final IntOption laserRange = new IntOption(this, "LaserRange", 64,
            "How far (in blocks) a laser can travel.");
    public final IntOption laserEnergyNeededPerHardness = new IntOption(this, "LaserDiggingEnergy", 100000,
            "Energy needed to destroy or attract blocks with a Laser (per block hardness level).");
    public final BooleanOption destroyDisabledBlocks = new BooleanOption(this, "DestroyDisabledBlocks", true,
            "If machine is disabled in config, do we set its block to air if it is found in world?");
    public final BooleanOption voidInvalidGases = new BooleanOption(this, "VoidInvalidGases", true,
            "Should machines void the gas inside of them on load if there is no recipe using that gas.");
    public final IntOption digitalMinerMaxRadius = new IntOption(this, "digitalMinerMaxRadius", 32,
            "Maximum radius in blocks that the Digital Miner can reach. (Increasing this may have negative effects on stability and/or performance. "
                    + "We strongly recommend you leave it at the default value.)", 1, Integer.MAX_VALUE);
    public final DoubleOption sawdustChancePlank = new DoubleOption(this, "SawdustChancePlank", 0.25D,
            "Chance of producing sawdust per operation in the precision sawmill when turning planks into sticks.").setRequiresGameRestart();
    public final DoubleOption sawdustChanceLog = new DoubleOption(this, "SawdustChanceLog", 1D,
            "Chance of producing sawdust per operation in the precision sawmill when turning logs into planks.").setRequiresGameRestart();
    /**
     * ADD START
     */

    public final DoubleOption armoredFreeRunnersRatio = new DoubleOption(this, "ArmoredFreeRunnersDamageRatio", 0.3,
            "Damage absorb ratio of the Armored FreeRunners");
    public final IntOption armoredFreeRunnersDamageMax = new IntOption(this, "ArmoredFreeRunnersDamageMax", 43,
            "Max damage the Armored Free Runners can absorb.");
    public final IntOption maxCanteen = new IntOption(this, "MaxCanteenGas", 64000,
            "Canteen Gas Tank capacity in mB.");
    public final IntOption fluoritePerChunk = new IntOption(this, "FluoritePerChunk", 16,
            "Chance that fluorite generates in a chunk. (0 to Disable)", 0, Integer.MAX_VALUE);
    public final IntOption fluoriteMaxVeinSize = new IntOption(this, "FluoriteVeinSize", 8,
            "Max number of blocks in a fluorite vein.", 1, Integer.MAX_VALUE);
    public final IntOption leadPerChunk = new IntOption(this, "LeadPerChunk", 16,
            "Chance that lead generates in a chunk. (0 to Disable)", 0, Integer.MAX_VALUE);
    public final IntOption leadMaxVeinSize = new IntOption(this, "LeadVeinSize", 8,
            "Max number of blocks in a lead vein.", 1, Integer.MAX_VALUE);
    public final IntOption uraniumPerChunk = new IntOption(this, "UraniumPerChunk", 16,
            "Chance that uranium generates in a chunk. (0 to Disable)", 0, Integer.MAX_VALUE);
    public final IntOption uraniumMaxVeinSize = new IntOption(this, "UraniumVeinSize", 8,
            "Max number of blocks in a uranium vein.", 1, Integer.MAX_VALUE);
    public final BooleanOption blacklistFlux = new BooleanOption(this, "BlacklistFluxPower", false,
            "Disables Flux Networks power integration. Requires world restart (server-side option in SMP).");

    public final IntOption nutritionalPasteMBPerFood = new IntOption(this, "mbPerFood", 50, "How much mB of Nutritional Paste equates to one 'half-food.'");
    public final FloatOption nutritionalPasteSaturation = new FloatOption(this, "saturation", 0.8F, "Saturation level of Nutritional Paste when eaten.");
    public final DoubleOption radiationNegativeEffectsMinSeverity = new DoubleOption(this, "negativeEffectsMinSeverity", 0.1D, "Defines the minimum severity radiation dosage severity (scale of 0 to 1) for which negative effects can take place. Set to 1 to disable negative effects completely.", 0, 1);
    public final DoubleOption radiationTargetDecayRate = new DoubleOption(this, "targetDecayRate", 0.9995D, "Radiated objects and entities are multiplied by this constant roughly once per second to represent their dosage decay.");
    public final DoubleOption radiationSourceDecayRate = new DoubleOption(this, "sourceDecayRate", 0.9995D, "Radiation sources are multiplied by this constant roughly once per second to represent their emission decay. At the default rate, it takes roughly 10 hours to remove a 1,000 Sv/h (crazy high) source.");
    public final IntOption radiationChunkCheckRadius = new IntOption(this, "chunkCheckRadius", 5, "The radius of chunks checked when running radiation calculations. The algorithm is efficient, but don't abuse it by making this crazy high.", 1, 100);
    public final BooleanOption radiationEnabled = new BooleanOption(this, "radiationEnabled", true, "Enable worldwide radiation effects. Don't be a downer and disable this.");

    public final StringListOption validOredictionificatorFilters = new StringListOption(this, "validItemFilters", new String[]{"ingot", "ore", "dust", "nugget"}, "The list of valid tag prefixes for the Oredictionificator. Note: It is highly recommended to only include well known/defined tag prefixes otherwise it is very easy to potentially add in accidental conversions of things that are not actually equivalent.").setRequiresGameRestart();


    public final TypeConfigManager<MachineType> machinesManager = new TypeConfigManager<>(this, "machines", MachineType.class, MachineType::getValidMachines, MachineType::getBlockName);
    public final EnumMap<BaseTier, TierConfig> tiers = TierConfig.create(this);
    public EnumOption<EnergyType> energyUnit = new EnumOption<>(this, "EnergyType", EnergyType.RF,
            "Displayed energy type in Mekanism GUIs.");
    public EnumOption<TempType> tempUnit = new EnumOption<>(this, "TemperatureUnits", TempType.K,
            "Displayed temperature unit in Mekanism GUIs.");
    int maxVolume = 18 * 18 * 18;
    public final IntOption dynamicTankFluidPerTank = new IntOption(this, "DynamicTankFluidPerTank", 300_000, "Amount of fluid (mB) that each block of the dynamic tank contributes to the volume. Max = volume * fluidPerTank", 1, Integer.MAX_VALUE / maxVolume);

    public final DoubleOption dumpExcessKeepRatio = new DoubleOption(this, "dumpExcessKeepRatio", 0.9D, "The percentage of a tank's capacity to leave contents in when set to dumping excess.", 0.001D, 1D);

    public final IntSetOption defaultAmbientDimBlacklist = new IntSetOption(this,"defaultAmbientDimBlacklist",new int[]{},"Default dimension gas collection recipe where the environmental collector does not work").setRequiresGameRestart();

    public final BooleanOption EnableDefaultAllAddAmbientGas = new BooleanOption(this,"EnableDefaultAllAddAmbientGas",true,"Enable the recipe to register the default environment collector for all dimensions").setRequiresGameRestart();

    @Override
    public String getCategory() {
        return "general";
    }
}
