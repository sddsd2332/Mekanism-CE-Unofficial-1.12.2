package mekanism.generators.common;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.Coord4D;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.LaserManager;
import mekanism.common.Mekanism;
import mekanism.common.MekanismFluids;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.capabilities.heat.VariableHeatCapacitor;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.boiler.SynchronizedBoilerData;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.FluidInput;
import mekanism.common.recipe.machines.FusionCoolingRecipe;
import mekanism.common.util.MekanismUtils;
import mekanism.generators.common.item.ItemHohlraum;
import mekanism.generators.common.tile.reactor.TileEntityReactorBlock;
import mekanism.generators.common.tile.reactor.TileEntityReactorController;
import mekanism.generators.common.tile.reactor.TileEntityReactorPort;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FusionReactor {

    //Reaction characteristics
    public static double burnTemperature = 100_000_000;
    public static double burnRatio = 1;
    //Thermal characteristics
    public static double plasmaHeatCapacity = 100;
    public static double caseHeatCapacity = 1;
    @Deprecated
    public static double thermocoupleEfficiency = 0.05;
    //Heat transfer metrics
    public static double plasmaCaseConductivity = 0.2;
    @Deprecated
    public static double caseWaterConductivity = 0.3;
    @Deprecated
    public static double caseAirConductivity = 0.1;
    public static final double CASE_INVERSE_INSULATION = 100_000;
    public TileEntityReactorController controller;
    public Set<TileEntityReactorBlock> reactorBlocks = new HashSet<>();
    public Set<TileEntityReactorPort> heatHandlers = new HashSet<>();
    //The plasma remains a separate reaction state. The casing is stored as real heat in the capacitor.
    public double plasmaTemperature;
    private double biomeAmbientTemp = HeatAPI.AMBIENT_TEMP;
    private final VariableHeatCapacitor heatCapacitor;
    //Last values of temperature
    public double lastPlasmaTemperature;
    public double lastCaseTemperature;
    public double lastTransferLoss;
    public double lastEnvironmentLoss;
    public int injectionRate = 2;
    /** Amount of fuel consumed during the most recent simulation tick. */
    private int lastBurned;
    public boolean burning = false;
    public boolean activelyCooled = true;

    public boolean updatedThisTick;

    public boolean formed = false;
    private FusionCoolingRecipe cachedRecipe;
    private int cachedRecipeVersion = -1;


    public FusionReactor(TileEntityReactorController c) {
        controller = c;
        if (c != null) {
            biomeAmbientTemp = HeatAPI.getAmbientTemp(c.getWorld(), c.getPos());
        }
        plasmaTemperature = biomeAmbientTemp;
        lastPlasmaTemperature = biomeAmbientTemp;
        lastCaseTemperature = biomeAmbientTemp;
        double casingCapacity = HeatAPI.isFinite(caseHeatCapacity) ? Math.max(1, Math.min(HeatAPI.MAX_HEAT, caseHeatCapacity)) : 1;
        heatCapacitor = VariableHeatCapacitor.create(casingCapacity, FusionReactor::getCaseInverseConduction,
              () -> CASE_INVERSE_INSULATION, () -> biomeAmbientTemp, null);
    }

    private static double getPlasmaHeatCapacity() {
        return HeatAPI.isFinite(plasmaHeatCapacity) && plasmaHeatCapacity >= 1 ? Math.min(HeatAPI.MAX_HEAT, plasmaHeatCapacity) : 1;
    }

    private static double getThermocoupleEfficiency() {
        double value = MekanismConfig.current().generators == null ? thermocoupleEfficiency :
              MekanismConfig.current().generators.fusionThermocoupleEfficiency.val();
        return sanitizeUnitConfig(value, 0.05, 0);
    }

    private static double getCaseWaterConductivity() {
        double value = MekanismConfig.current().generators == null ? caseWaterConductivity :
              MekanismConfig.current().generators.fusionWaterHeatingRatio.val();
        return sanitizeUnitConfig(value, 0.3, 0);
    }

    private static double getCaseAirConductivity() {
        double value = MekanismConfig.current().generators == null ? caseAirConductivity :
              MekanismConfig.current().generators.fusionCasingThermalConductivity.val();
        return sanitizeUnitConfig(value, 0.1, 0.001);
    }

    private static double sanitizeUnitConfig(double value, double fallback, double minimum) {
        return HeatAPI.isFinite(value) && value >= minimum && value <= 1 ? value : fallback;
    }

    public void addTemperatureFromEnergyInput(double energyAdded) {
        if (HeatAPI.isFinite(energyAdded) && energyAdded > 0) {
            double delta = energyAdded / getPlasmaHeatCapacity() * (isBurning() ? 1 : 10);
            if (HeatAPI.isFinite(delta)) {
                setPlasmaTemp(plasmaTemperature + delta);
            } else {
                setPlasmaTemp(HeatAPI.MAX_HEAT / getPlasmaHeatCapacity());
            }
        }
    }

    public boolean hasHohlraum() {
        IInventorySlot hohlraumSlot = getHohlraumSlot();
        if (hohlraumSlot != null) {
            ItemStack hohlraum = hohlraumSlot.getStack();
            if (!hohlraum.isEmpty() && hohlraum.getItem() instanceof ItemHohlraum) {
                GasStack gasStack = GasInventorySlot.getContainedGas(hohlraum, MekanismFluids.FusionFuel);
                int capacity = GasInventorySlot.getTankCapacity(hohlraum, 0);
                return gasStack != null && gasStack.amount == (capacity > 0 ? capacity : ItemHohlraum.getMaxGasCapacity());
            }
        }
        return false;
    }

    public void simulate() {
        if (controller == null || controller.getWorld() == null) {
            return;
        }
        if (controller.getWorld().isRemote) {
            lastPlasmaTemperature = plasmaTemperature;
            lastCaseTemperature = heatCapacitor.getTemperature();
            return;
        }

        updatedThisTick = false;

        //Only thermal transfer happens unless we're hot enough to burn.
        int fuelBurned = 0;
        double ignitionTemperature = getBurnTemperature();
        if (plasmaTemperature >= ignitionTemperature) {
            //If we're not burning yet we need a hohlraum to ignite
            if (!burning && hasHohlraum()) {
                vaporiseHohlraum();
            }

            //Only inject fuel if we're burning
            if (burning) {
                injectFuel();
                fuelBurned = burnFuel();
                if (fuelBurned == 0) {
                    burning = false;
                }
            }
        } else {
            burning = false;
        }
        lastBurned = fuelBurned;

        //Perform the heat transfer calculations
        transferHeat();

        if (burning) {
            kill();
        }
        updateTemperatures();
        //Plasma temperature is not backed by a container listener. Ensure a reactor that only
        //heats or cools still persists its latest thermal state.
        MekanismUtils.saveChunk(controller);
    }

    public void updateTemperatures() {
        lastPlasmaTemperature = HeatAPI.sanitizeTemperature(plasmaTemperature);
        lastCaseTemperature = heatCapacitor.getTemperature();
    }

    public void vaporiseHohlraum() {
        IInventorySlot hohlraumSlot = getHohlraumSlot();
        if (hohlraumSlot == null || hohlraumSlot.isEmpty()) {
            return;
        }
        ItemStack hohlraum = hohlraumSlot.getStack();
        if (!(hohlraum.getItem() instanceof ItemHohlraum)) {
            return;
        }
        GasStack gasStack = GasInventorySlot.getContainedGas(hohlraum, MekanismFluids.FusionFuel);
        if (gasStack == null || gasStack.getGas() != MekanismFluids.FusionFuel) {
            return;
        }
        getFuelTank().insert(gasStack, Action.EXECUTE, AutomationType.INTERNAL);
        hohlraumSlot.setEmpty();
        burning = true;
    }

    public void injectFuel() {
        if (getFuelTank() == null || getDeuteriumTank() == null || getTritiumTank() == null) {
            return;
        }
        int amountNeeded = Math.max(0, getFuelTank().getNeeded());
        long amountAvailableLong = 2L * Math.min(Math.max(0, getDeuteriumTank().getStored()), Math.max(0, getTritiumTank().getStored()));
        int amountAvailable = (int) Math.min(Integer.MAX_VALUE, amountAvailableLong);
        int amountToInject = Math.min(amountNeeded, Math.min(amountAvailable, Math.max(0, injectionRate)));
        amountToInject -= amountToInject % 2;
        int half = amountToInject / 2;
        GasStack deuterium = getDeuteriumTank().extract(half, Action.EXECUTE, AutomationType.INTERNAL);
        GasStack tritium = getTritiumTank().extract(half, Action.EXECUTE, AutomationType.INTERNAL);
        int deuteriumAmount = deuterium == null ? 0 : deuterium.amount;
        int tritiumAmount = tritium == null ? 0 : tritium.amount;
        int actualHalf = Math.min(deuteriumAmount, tritiumAmount);
        if (deuteriumAmount > actualHalf) {
            getDeuteriumTank().insert(new GasStack(MekanismFluids.Deuterium, deuteriumAmount - actualHalf), Action.EXECUTE, AutomationType.INTERNAL);
        }
        if (tritiumAmount > actualHalf) {
            getTritiumTank().insert(new GasStack(MekanismFluids.Tritium, tritiumAmount - actualHalf), Action.EXECUTE, AutomationType.INTERNAL);
        }
        int actualInject = actualHalf * 2;
        if (actualInject > 0) {
            getFuelTank().insert(new GasStack(MekanismFluids.FusionFuel, actualInject), Action.EXECUTE, AutomationType.INTERNAL);
        }
    }

    public int burnFuel() {
        if (getFuelTank() == null) {
            return 0;
        }
        double ratio = finiteNonNegative(burnRatio, 0);
        double availableToBurn = Math.max(0, plasmaTemperature - getBurnTemperature());
        if (ratio > 0 && HeatAPI.isFinite(availableToBurn)) {
            availableToBurn = availableToBurn >= HeatAPI.MAX_HEAT / ratio ? HeatAPI.MAX_HEAT : availableToBurn * ratio;
        } else {
            availableToBurn = 0;
        }
        int stored = Math.max(0, getFuelTank().getStored());
        int fuelBurned = (int) Math.min(stored, Math.min(Integer.MAX_VALUE, availableToBurn));
        if (fuelBurned <= 0) {
            return 0;
        }
        GasStack extracted = getFuelTank().extract(fuelBurned, Action.EXECUTE, AutomationType.INTERNAL);
        fuelBurned = extracted == null ? 0 : Math.max(0, Math.min(fuelBurned, extracted.amount));
        if (fuelBurned == 0) {
            return 0;
        }
        double fuelEnergy = finiteNonNegative(MekanismConfig.current().generators.energyPerFusionFuel.val(), 0);
        double energyAdded = HeatAPI.multiplyHeat(fuelBurned, fuelEnergy);
        double delta = energyAdded / getPlasmaHeatCapacity();
        setPlasmaTemp(HeatAPI.isFinite(delta) ? plasmaTemperature + delta : Double.POSITIVE_INFINITY);
        return fuelBurned;
    }

    public void transferHeat() {
        //Transfer from plasma to casing
        double plasmaCaseHeat = signedProduct(finiteNonNegative(plasmaCaseConductivity, 0),
              HeatAPI.sanitizeTemperature(plasmaTemperature) - heatCapacitor.getTemperature());
        transferPlasmaAndCasing(plasmaCaseHeat);

        FusionCoolingRecipe recipe = getRecipe();

        //Transfer from casing to water if necessary
        double lostToWater = transferCasingToWater(recipe);
        lastTransferLoss = 0;
        for (TileEntityReactorPort source : heatHandlers) {
            double transfer = source.simulateAdjacent();
            if (HeatAPI.isFinite(transfer) && transfer > 0) {
                lastTransferLoss = saturatedAdd(lastTransferLoss, transfer);
            }
        }
        lastTransferLoss = saturatedAdd(lastTransferLoss, lostToWater);

        //Passive thermocouple generation is the casing's environmental heat path in modern Mekanism.
        double caseAirHeat = signedProduct(getCaseAirConductivity(), heatCapacitor.getTemperature() - biomeAmbientTemp);
        lastEnvironmentLoss = 0;
        if (HeatAPI.isFinite(caseAirHeat) && Math.abs(caseAirHeat) > HeatAPI.EPSILON) {
            double before = heatCapacitor.getHeat();
            heatCapacitor.handleHeat(-caseAirHeat);
            double actualLoss = Math.max(0, before - heatCapacitor.getHeat());
            lastEnvironmentLoss = actualLoss;
            caseAirHeat = actualLoss;
        }
        if (caseAirHeat > 0) {
            double efficiency = getThermocoupleEfficiency();
            double generation = saturatingProduct(caseAirHeat, efficiency);
            if (HeatAPI.isFinite(generation) && generation > 0) {
                setBufferedEnergy(saturatedAdd(getBufferedEnergy(), generation));
            }
        }
    }

    public FusionCoolingRecipe getRecipe() {
        if (controller == null || controller.waterTank == null) {
            return null;
        }
        int recipeVersion = RecipeHandler.Recipe.FUSION_COOLING.getRecipeVersion();
        FluidInput input = new FluidInput(controller.waterTank.getFluid());
        if (cachedRecipeVersion != recipeVersion) {
            cachedRecipe = null;
            cachedRecipeVersion = recipeVersion;
        }
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getFusionCoolingRecipe(input);
        }
        return cachedRecipe;
    }


    public IExtendedFluidTank getWaterTank() {
        return controller != null ? controller.waterTank : null;
    }

    public IExtendedFluidTank getSteamTank() {
        return controller == null ? null : controller.steamTank;
    }

    public BasicGasTank getDeuteriumTank() {
        return controller == null ? null : controller.deuteriumTank;
    }

    public BasicGasTank getTritiumTank() {
        return controller == null ? null : controller.tritiumTank;
    }

    public BasicGasTank getFuelTank() {
        return controller == null ? null : controller.fuelTank;
    }

    public double getBufferedEnergy() {
        return controller == null ? 0 : controller.getEnergy();
    }

    public void setBufferedEnergy(double energy) {
        if (controller != null && HeatAPI.isFinite(energy)) {
            controller.setEnergy(Math.max(0, energy));
        }
    }

    public double getPlasmaTemp() {
        return HeatAPI.sanitizeTemperature(plasmaTemperature);
    }

    public void setPlasmaTemp(double temp) {
        double maxTemperature = HeatAPI.MAX_HEAT / getPlasmaHeatCapacity();
        if (Double.isNaN(temp)) {
            temp = biomeAmbientTemp;
        } else if (temp == Double.POSITIVE_INFINITY) {
            temp = maxTemperature;
        } else if (temp == Double.NEGATIVE_INFINITY) {
            temp = 0;
        }
        plasmaTemperature = HeatAPI.isFinite(temp) ? Math.max(0, Math.min(maxTemperature, temp)) :
              (temp == Double.POSITIVE_INFINITY ? maxTemperature : biomeAmbientTemp);
    }

    public double getCaseTemp() {
        return lastCaseTemperature;
    }

    public void setCaseTemp(double temp) {
        temp = temp == Double.POSITIVE_INFINITY ? HeatAPI.MAX_HEAT : temp;
        if (HeatAPI.isFinite(temp) && temp >= 0) {
            heatCapacitor.setHeat(HeatAPI.multiplyHeat(temp, heatCapacitor.getHeatCapacity()));
        }
    }

    public VariableHeatCapacitor getHeatCapacitor() {
        return heatCapacitor;
    }

    public double getAmbientTemperature() {
        return HeatAPI.sanitizeTemperature(biomeAmbientTemp);
    }

    public double getBufferSize() {
        return controller == null ? 0 : controller.getMaxEnergy();
    }

    public void kill() {
        AxisAlignedBB death_zone = new AxisAlignedBB(controller.getPos().getX() - 1, controller.getPos().getY() - 3,
                controller.getPos().getZ() - 1, controller.getPos().getX() + 2, controller.getPos().getY(), controller.getPos().getZ() + 2);
        List<Entity> entitiesToDie = controller.getWorld().getEntitiesWithinAABB(Entity.class, death_zone);

        for (Entity entity : entitiesToDie) {
            entity.attackEntityFrom(DamageSource.MAGIC, Float.MAX_VALUE);
        }
    }

    public void unformMultiblock(boolean keepBurning) {
        for (TileEntityReactorBlock block : reactorBlocks) {
            block.setReactor(null);
        }

        //Don't remove from controller
        controller.setReactor(this);
        reactorBlocks.clear();
        heatHandlers.clear();
        formed = false;
        burning = burning && keepBurning;

        if (controller != null && controller.getWorld() != null && !controller.getWorld().isRemote) {
            Mekanism.packetHandler.sendToDimension(new TileEntityMessage(controller), controller.getWorld().provider.getDimension());
        }
    }

    public void formMultiblock(boolean keepBurning) {
        if (controller == null || controller.getWorld() == null) {
            return;
        }
        updatedThisTick = true;
        Coord4D controllerPosition = Coord4D.get(controller);
        Coord4D centreOfReactor = controllerPosition.offset(EnumFacing.DOWN, 2);
        unformMultiblock(true);
        reactorBlocks.add(controller);

        if (!createFrame(centreOfReactor) || !addSides(centreOfReactor) || !centreIsClear(centreOfReactor)) {
            unformMultiblock(keepBurning);
            return;
        }

        updateAmbientTemperature(centreOfReactor);
        formed = true;
        if (!controller.getWorld().isRemote) {
            Mekanism.packetHandler.sendToDimension(new TileEntityMessage(controller), controller.getWorld().provider.getDimension());
        }
    }

    public boolean createFrame(Coord4D centre) {
        int[][] positions = new int[][]{
                {+2, +2, +0}, {+2, +1, +1}, {+2, +0, +2}, {+2, -1, +1}, {+2, -2, +0}, {+2, -1, -1}, {+2, +0, -2}, {+2, +1, -1}, {+1, +2, +1}, {+1, +1, +2}, {+1, -1, +2},
                {+1, -2, +1}, {+1, -2, -1}, {+1, -1, -2}, {+1, +1, -2}, {+1, +2, -1}, {+0, +2, +2}, {+0, -2, +2}, {+0, -2, -2}, {+0, +2, -2}, {-1, +2, +1}, {-1, +1, +2},
                {-1, -1, +2}, {-1, -2, +1}, {-1, -2, -1}, {-1, -1, -2}, {-1, +1, -2}, {-1, +2, -1}, {-2, +2, +0}, {-2, +1, +1}, {-2, +0, +2}, {-2, -1, +1}, {-2, -2, +0},
                {-2, -1, -1}, {-2, +0, -2}, {-2, +1, -1},};

        for (int[] coords : positions) {
            TileEntity tile = centre.clone().translate(coords[0], coords[1], coords[2]).getTileEntity(controller.getWorld());
            if (tile instanceof TileEntityReactorBlock block && block.isFrame() && canClaimReactorBlock(block)) {
                reactorBlocks.add(block);
                block.setReactor(this);
            } else {
                return false;
            }
        }
        return true;
    }

    public boolean addSides(Coord4D centre) {
        int[][] positions = new int[][]{
                {+2, +0, +0}, {+2, +1, +0}, {+2, +0, +1}, {+2, -1, +0}, {+2, +0, -1}, //EAST
                {-2, +0, +0}, {-2, +1, +0}, {-2, +0, +1}, {-2, -1, +0}, {-2, +0, -1}, //WEST
                {+0, +2, +0}, {+1, +2, +0}, {+0, +2, +1}, {-1, +2, +0}, {+0, +2, -1}, //TOP
                {+0, -2, +0}, {+1, -2, +0}, {+0, -2, +1}, {-1, -2, +0}, {+0, -2, -1}, //BOTTOM
                {+0, +0, +2}, {+1, +0, +2}, {+0, +1, +2}, {-1, +0, +2}, {+0, -1, +2}, //SOUTH
                {+0, +0, -2}, {+1, +0, -2}, {+0, +1, -2}, {-1, +0, -2}, {+0, -1, -2}, //NORTH
        };

        for (int[] coords : positions) {
            TileEntity tile = centre.clone().translate(coords[0], coords[1], coords[2]).getTileEntity(controller.getWorld());

            if (LaserManager.isReceptor(tile, null) && !(coords[1] == 0 && (coords[0] == 0 || coords[2] == 0))) {
                return false;
            }
            boolean controllerPosition = isControllerPosition(coords[0], coords[1], coords[2]);
            if (tile instanceof TileEntityReactorBlock block && canClaimReactorBlock(block) &&
                  (controllerPosition ? block == controller : !(block instanceof TileEntityReactorController))) {
                reactorBlocks.add(block);
                block.setReactor(this);
                if (tile instanceof TileEntityReactorPort port) {
                    heatHandlers.add(port);
                }
            } else {
                return false;
            }
        }
        return true;
    }

    public static boolean isControllerPosition(int x, int y, int z) {
        return x == 0 && y == 2 && z == 0;
    }

    boolean canClaimReactorBlock(TileEntityReactorBlock block) {
        FusionReactor existing = block.getReactor();
        return existing == null || existing == this || !existing.isFormed();
    }

    public boolean centreIsClear(Coord4D centre) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Coord4D trans = centre.translate(x, y, z);
                    IBlockState state = trans.getBlockState(controller.getWorld());
                    Block tile = state.getBlock();
                    if (!tile.isAir(state, controller.getWorld(), trans.getPos())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public boolean isFormed() {
        return formed;
    }

    public int getInjectionRate() {
        return injectionRate;
    }

    public void setInjectionRate(int rate) {
        int configuredMaximum = MekanismConfig.current().generators == null ? 98 :
              Math.max(2, MekanismConfig.current().generators.reactorGeneratorInjectionRate.val());
        int bounded = Math.max(0, Math.min(configuredMaximum, rate));
        injectionRate = bounded - Math.floorMod(bounded, 2);
        if (controller != null) {
            controller.sanitizeAndClampTanks();
        }
    }

    public boolean isBurning() {
        return burning;
    }

    public void setBurning(boolean burn) {
        burning = burn;
    }

    public int getMinInjectionRate(boolean active) {
        double k = active ? getCaseWaterConductivity() : 0;
        double air = getCaseAirConductivity();
        double plasma = finiteNonNegative(plasmaCaseConductivity, 0);
        double ratio = finiteNonNegative(burnRatio, 0);
        double fuelEnergy = finiteNonNegative(MekanismConfig.current().generators.energyPerFusionFuel.val(), 0);
        double denominator = fuelEnergy * ratio * (plasma + k + air) - plasma * (k + air);
        double numerator = getBurnTemperature() * ratio * plasma * (k + air);
        if (!HeatAPI.isFinite(denominator) || denominator <= 0 || !HeatAPI.isFinite(numerator)) {
            return denominator <= 0 ? Integer.MAX_VALUE - 1 : 0;
        }
        double minimum = numerator / denominator;
        if (!HeatAPI.isFinite(minimum) || minimum <= 0) {
            return 0;
        }
        double rounded = 2 * Math.ceil(minimum / 2D);
        return (int) Math.min(Integer.MAX_VALUE - 1, rounded);
    }

    public double getMaxPlasmaTemperature(boolean active) {
        double k = active ? getCaseWaterConductivity() : 0;
        double air = getCaseAirConductivity();
        double plasma = finitePositive(plasmaCaseConductivity, 1);
        double fuelEnergy = finiteNonNegative(MekanismConfig.current().generators.energyPerFusionFuel.val(), 0);
        double rate = Math.max(0, Math.max(injectionRate, lastBurned));
        double denominator = k + air;
        if (!HeatAPI.isFinite(denominator) || denominator <= 0) {
            return HeatAPI.MAX_HEAT;
        }
        return saturatingProduct(rate, fuelEnergy / plasma, (plasma + denominator) / denominator);
    }

    public double getMaxCasingTemperature(boolean active) {
        double k = active ? getCaseWaterConductivity() : 0;
        double air = getCaseAirConductivity();
        double fuelEnergy = finiteNonNegative(MekanismConfig.current().generators.energyPerFusionFuel.val(), 0);
        double rate = Math.max(0, Math.max(injectionRate, lastBurned));
        double denominator = k + air;
        if (!HeatAPI.isFinite(denominator) || denominator <= 0) {
            return HeatAPI.MAX_HEAT;
        }
        return saturatingProduct(rate, fuelEnergy / denominator);
    }

    public double getIgnitionTemperature(boolean active) {
        double k = active ? getCaseWaterConductivity() : 0;
        double air = getCaseAirConductivity();
        double plasma = finiteNonNegative(plasmaCaseConductivity, 0);
        double ratio = finiteNonNegative(burnRatio, 0);
        double fuelEnergy = finiteNonNegative(MekanismConfig.current().generators.energyPerFusionFuel.val(), 0);
        double totalConductivity = plasma + k + air;
        double numerator = getBurnTemperature() * fuelEnergy * ratio * totalConductivity;
        double denominator = fuelEnergy * ratio * totalConductivity - plasma * (k + air);
        if (!HeatAPI.isFinite(denominator) || denominator <= 0) {
            return HeatAPI.MAX_HEAT;
        }
        return saturatingProduct(numerator / denominator);
    }

    public double getPassiveGeneration(boolean active, boolean current) {
        double temperature = current ? heatCapacitor.getTemperature() : getMaxCasingTemperature(active);
        return saturatingProduct(getThermocoupleEfficiency(), getCaseAirConductivity(),
              Math.max(0, HeatAPI.sanitizeTemperature(temperature)));
    }

    public int getSteamPerTick(boolean current) {
        double temperature = current ? heatCapacitor.getTemperature() : getMaxCasingTemperature(true);
        double efficiency = finiteNonNegative(SynchronizedBoilerData.getSteamEnergyEfficiency(), 0);
        double enthalpy = finitePositive(SynchronizedBoilerData.getHeatEnthalpy(), 1);
        double heat = saturatingProduct(getCaseWaterConductivity(), Math.max(0, HeatAPI.sanitizeTemperature(temperature)));
        double steam = saturatingProduct(efficiency, heat) / enthalpy;
        return !HeatAPI.isFinite(steam) || steam <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.floor(steam));
    }

    public IInventorySlot getHohlraumSlot() {
        return controller != null && isFormed() ? controller.getHohlraumSlot() : null;
    }

    public boolean hasRecipe(Fluid fluid) {
        if (fluid == null) {
            return false;
        }
        return RecipeHandler.Recipe.FUSION_COOLING.containsRecipe(fluid);
    }

    private static double getCaseInverseConduction() {
        return 1 / getCaseAirConductivity();
    }

    /** Applies a plasma/casing transfer and returns the amount actually accepted by the casing. */
    private double transferPlasmaAndCasing(double requested) {
        if (!HeatAPI.isFinite(requested) || Math.abs(requested) <= HeatAPI.EPSILON) {
            return 0;
        }
        double plasmaCapacity = getPlasmaHeatCapacity();
        double maxPlasmaTemperature = HeatAPI.MAX_HEAT / plasmaCapacity;
        if (requested > 0) {
            double available = HeatAPI.multiplyHeat(plasmaTemperature, plasmaCapacity);
            requested = Math.min(requested, available);
        } else {
            double available = HeatAPI.multiplyHeat(Math.max(0, maxPlasmaTemperature - plasmaTemperature), plasmaCapacity);
            requested = -Math.min(-requested, available);
        }
        if (!HeatAPI.isFinite(requested) || Math.abs(requested) <= HeatAPI.EPSILON) {
            return 0;
        }
        double before = heatCapacitor.getHeat();
        heatCapacitor.handleHeat(requested);
        double actual = heatCapacitor.getHeat() - before;
        if (HeatAPI.isFinite(actual) && Math.abs(actual) > HeatAPI.EPSILON) {
            setPlasmaTemp(plasmaTemperature - actual / plasmaCapacity);
            return actual;
        }
        return 0;
    }

    /** Converts casing heat into the configured cooling fluid, bounded by available heat and tank space. */
    private double transferCasingToWater(FusionCoolingRecipe recipe) {
        IExtendedFluidTank waterTank = getWaterTank();
        IExtendedFluidTank steamTank = getSteamTank();
        if (!activelyCooled || recipe == null || waterTank == null || steamTank == null || waterTank.isEmpty()) {
            return 0;
        }
        FluidStack recipeOutput = recipe.getOutput() == null ? null : recipe.getOutput().output;
        if (recipeOutput == null || recipeOutput.getFluid() == null || recipeOutput.amount <= 0) {
            return 0;
        }
        double efficiency = finitePositive(SynchronizedBoilerData.getSteamEnergyEfficiency(), 0);
        double enthalpy = finitePositive(SynchronizedBoilerData.getHeatEnthalpy(), 0);
        if (efficiency <= 0 || enthalpy <= 0) {
            return 0;
        }
        double temperatureDifference = heatCapacitor.getTemperature() - biomeAmbientTemp;
        double conductivity = getCaseWaterConductivity();
        double requestedHeat = saturatingProduct(conductivity, Math.max(0, temperatureDifference));
        double ambientHeat = HeatAPI.multiplyHeat(Math.max(0, biomeAmbientTemp), heatCapacitor.getHeatCapacity());
        double availableHeat = Math.max(0, heatCapacitor.getHeat() - ambientHeat);
        requestedHeat = Math.min(requestedHeat, availableHeat);
        if (!HeatAPI.isFinite(requestedHeat) || requestedHeat <= HeatAPI.EPSILON) {
            return 0;
        }
        double heatPerInput = enthalpy / efficiency;
        double possible = requestedHeat / heatPerInput;
        if (!HeatAPI.isFinite(possible) || possible <= 0) {
            return 0;
        }
        int outputPerInput = Math.max(1, recipeOutput.amount);
        long outputSpace = Math.max(0, steamTank.getNeeded()) / outputPerInput;
        int waterToVaporize = (int) Math.min(Integer.MAX_VALUE,
              Math.min(waterTank.getFluidAmount(), Math.min(outputSpace, Math.floor(possible))));
        if (waterToVaporize <= 0) {
            return 0;
        }
        FluidStack extracted = waterTank.extract(waterToVaporize, Action.EXECUTE, AutomationType.INTERNAL);
        int extractedAmount = extracted == null ? 0 : extracted.amount;
        if (extractedAmount <= 0) {
            return 0;
        }
        long outputAmountLong = (long) extractedAmount * outputPerInput;
        int outputAmount = (int) Math.min(Integer.MAX_VALUE, outputAmountLong);
        FluidStack remainder = steamTank.insert(new FluidStack(recipeOutput.getFluid(), outputAmount), Action.EXECUTE, AutomationType.INTERNAL);
        int insertedAmount = remainder == null ? outputAmount : Math.max(0, outputAmount - remainder.amount);
        int converted = insertedAmount / outputPerInput;
        int excessOutput = insertedAmount - converted * outputPerInput;
        if (excessOutput > 0) {
            steamTank.extract(excessOutput, Action.EXECUTE, AutomationType.INTERNAL);
        }
        if (converted < extractedAmount) {
            //The output tank can change while the structure is being rebuilt; return unconverted water.
            waterTank.insert(new FluidStack(extracted.getFluid(), extractedAmount - converted), Action.EXECUTE, AutomationType.INTERNAL);
        }
        if (converted <= 0) {
            return 0;
        }
        double requestedLoss = converted * heatPerInput;
        double before = heatCapacitor.getHeat();
        heatCapacitor.handleHeat(-requestedLoss);
        return Math.max(0, before - heatCapacitor.getHeat());
    }

    private double getBurnTemperature() {
        return finiteNonNegative(burnTemperature, 0);
    }

    private static double finiteNonNegative(double value, double fallback) {
        return HeatAPI.isFinite(value) && value >= 0 ? Math.min(HeatAPI.MAX_HEAT, value) : fallback;
    }

    private static double finitePositive(double value, double fallback) {
        return HeatAPI.isFinite(value) && value > 0 ? Math.min(HeatAPI.MAX_HEAT, value) : fallback;
    }

    private static double saturatingProduct(double... values) {
        double result = 1;
        for (double value : values) {
            if (!HeatAPI.isFinite(value) || value < 0) {
                return 0;
            }
            if (value == 0 || result == 0) {
                return 0;
            }
            if (result >= HeatAPI.MAX_HEAT / value) {
                return HeatAPI.MAX_HEAT;
            }
            result *= value;
        }
        return Math.min(HeatAPI.MAX_HEAT, result);
    }

    private static double signedProduct(double coefficient, double value) {
        if (!HeatAPI.isFinite(value) || value == 0 || !HeatAPI.isFinite(coefficient) || coefficient < 0) {
            return 0;
        }
        double product = saturatingProduct(coefficient, Math.abs(value));
        return value < 0 ? -product : product;
    }

    private static double saturatedAdd(double first, double second) {
        first = finiteNonNegative(first, 0);
        second = finiteNonNegative(second, 0);
        return second >= HeatAPI.MAX_HEAT - first ? HeatAPI.MAX_HEAT : first + second;
    }

    private void updateAmbientTemperature(Coord4D centre) {
        if (controller == null || controller.getWorld() == null || centre == null) {
            biomeAmbientTemp = HeatAPI.AMBIENT_TEMP;
            return;
        }
        BlockPos min = centre.translate(-2, -2, -2).getPos();
        BlockPos max = centre.translate(2, 2, 2).getPos();
        BlockPos[] corners = {
              min,
              new BlockPos(max.getX(), min.getY(), min.getZ()),
              new BlockPos(min.getX(), min.getY(), max.getZ()),
              new BlockPos(max.getX(), min.getY(), max.getZ()),
              new BlockPos(min.getX(), max.getY(), min.getZ()),
              new BlockPos(max.getX(), max.getY(), min.getZ()),
              new BlockPos(min.getX(), max.getY(), max.getZ()),
              max
        };
        double biomeTemperature = 0;
        for (BlockPos corner : corners) {
            double temperature = controller.getWorld().getBiomeForCoordsBody(corner).getTemperature(corner);
            if (!HeatAPI.isFinite(temperature)) {
                temperature = 0.8D;
            }
            biomeTemperature += temperature;
        }
        biomeAmbientTemp = HeatAPI.getAmbientTemp(biomeTemperature / corners.length);
        if (!HeatAPI.isFinite(biomeAmbientTemp)) {
            biomeAmbientTemp = HeatAPI.AMBIENT_TEMP;
        }
    }
}
