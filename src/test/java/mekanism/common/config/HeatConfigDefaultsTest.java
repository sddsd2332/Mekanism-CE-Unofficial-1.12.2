package mekanism.common.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeatConfigDefaultsTest {

    @Test
    void generalHeatDefaultsMatchMekanismTwentySixTwo() {
        GeneralConfig config = new GeneralConfig();

        assertEquals(400, config.heatPerFuelTick.val());
        assertEquals(1, config.fuelwoodTickMultiplier.val());
        assertEquals(0.6, config.resistiveHeaterEfficiency.val());
        assertEquals(10, config.maxEnergyPerSteam.val());
        assertEquals(16_000_000, config.superheatingHeatTransfer.val());
        assertEquals(0.7, config.boilerWaterConductivity.val());
        assertEquals(16_000, config.boilerWaterPerTank.val());
        assertEquals(160_000, config.boilerSteamPerTank.val());
        assertEquals(256_000, config.boilerHeatedCoolantPerTank.val());
        assertEquals(256_000, config.boilerCooledCoolantPerTank.val());
        assertEquals(0.02, config.evaporationHeatDissipation.val());
        assertEquals(0.2, config.evaporationSolarMultiplier.val());
        assertEquals(0.4, config.evaporationTempMultiplier.val());
        assertEquals(100, config.evaporationHeatCapacity.val());
        assertEquals(64_000, config.evaporationFluidPerTank.val());
        assertEquals(10_000, config.evaporationOutputTankCapacity.val());
    }

    @Test
    void generatorHeatDefaultsMatchMekanismTwentySixTwo() {
        GeneratorsConfig config = new GeneratorsConfig();

        assertEquals(100, config.heatGeneration.val());
        assertEquals(7, config.heatGenerationLava.val());
        assertEquals(10, config.heatGenerationNether.val());
        assertEquals(1_000, config.heatTankCapacity.val());
        assertEquals(100, config.heatGenerationFluidRate.val());
        assertEquals(240, config.heatGeneratorStorage.val());
        assertEquals(10_000_000, config.energyPerFusionFuel.val());
        assertEquals(0.05, config.fusionThermocoupleEfficiency.val());
        assertEquals(0.1, config.fusionCasingThermalConductivity.val());
        assertEquals(0.3, config.fusionWaterHeatingRatio.val());
        assertEquals(98, config.reactorGeneratorInjectionRate.val());
        assertEquals(1_000_000, config.FusionReactorsWaterTank.val());
        assertEquals(100_000_000, config.FusionReactorsSteamTank.val());
        assertEquals(1_000_000, config.energyPerFissionFuel.val());
        assertEquals(0.1, config.fissionDefaultBurnRate.val());
        assertEquals(1, config.fissionBurnPerAssembly.val());
        assertEquals(8_000, config.fissionFuelPerAssembly.val());
        assertEquals(100_000, config.fissionCooledCoolantPerTank.val());
        assertEquals(1_000_000, config.fissionHeatedCoolantPerTank.val());
        assertEquals(0.5, config.fissionWaterConductivity.val());
        assertEquals(1, config.fissionSodiumConductivity.val());
        assertEquals(0.2, config.fissionSteamEfficiency.val());
        assertEquals(5, config.fissionSodiumThermalEnthalpy.val());
    }
}
