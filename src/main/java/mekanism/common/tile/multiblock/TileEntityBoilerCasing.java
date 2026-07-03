package mekanism.common.tile.multiblock;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.*;
import mekanism.api.gas.GasStack;
import mekanism.common.Mekanism;
import mekanism.common.MekanismFluids;
import mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder;
import mekanism.common.capabilities.holder.heat.ProxiedHeatCapacitorHolder;
import mekanism.common.content.boiler.*;
import mekanism.common.content.tank.SynchronizedTankData.ValveData;
import mekanism.common.multiblock.MultiblockManager;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TileUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Set;

public class TileEntityBoilerCasing extends TileEntityMultiblock<SynchronizedBoilerData> implements IHeatTransfer {

    protected static final int[] INV_SLOTS = {0, 1};

    private final BoilerWaterTank internalWaterTank = new BoilerWaterTank(this);
    private final BoilerSteamTank internalSteamTank = new BoilerSteamTank(this);
    private final BoilerInputGasTank internalInputGasTank = new BoilerInputGasTank(this);
    private final BoilerOutputGasTank internalOutputGasTank = new BoilerOutputGasTank(this);

    /**
     * A client-sided set of valves on this tank's structure that are currently active, used on the client for rendering fluids.
     */
    public Set<ValveData> valveViewing = new ObjectOpenHashSet<>();

    /**
     * The capacity this tank has on the client-side.
     */
    public int clientWaterCapacity;
    public int clientSteamCapacity;

    public float prevWaterScale;

    public TileEntityBoilerCasing() {
        this("BoilerCasing");
    }

    public TileEntityBoilerCasing(String name) {
        super(name);
        if (getClass() == TileEntityBoilerCasing.class) {
            initializeInventorySlots();
        }
    }

    @Override
    public void onUpdateClient() {
        super.onUpdateClient();
        if (structure != null && clientHasStructure && isRendering) {
            int stored = structure.waterStored == null ? 0 : structure.waterStored.amount;
            float targetScale = clientWaterCapacity <= 0 ? 0 : Math.min(1, Math.max(0, stored / (float) clientWaterCapacity));
            float difference = Math.abs(prevWaterScale - targetScale);
            if (difference > 0.01) {
                prevWaterScale = (9 * prevWaterScale + targetScale) / 10;
            } else if (stored > 0 && (stored >= clientWaterCapacity || prevWaterScale == 0)) {
                prevWaterScale = targetScale;
            } else if (stored == 0 && prevWaterScale < 0.01) {
                prevWaterScale = 0;
            }
        }
        if (!clientHasStructure || !isRendering) {
            valveViewing.forEach(data -> {
                TileEntityBoilerCasing tileEntity = (TileEntityBoilerCasing) data.location.getTileEntity(world);
                if (tileEntity != null) {
                    tileEntity.clientHasStructure = false;
                }
            });
            valveViewing.clear();
        }
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        if (structure != null) {
            if (structure.sanitizeStoredSubstances()) {
                markNoUpdateSync();
            }

            if (isRendering) {
                boolean needsValveUpdate = false;
                for (ValveData data : structure.valves) {
                    if (data.activeTicks > 0) {
                        data.activeTicks--;
                    }
                    if (data.activeTicks > 0 != data.prevActive) {
                        needsValveUpdate = true;
                    }
                    data.prevActive = data.activeTicks > 0;
                }

                boolean needsHotUpdate = false;
                boolean newHot = structure.temperature >= SynchronizedBoilerData.BASE_BOIL_TEMP - 0.01F;
                if (newHot != structure.clientHot) {
                    needsHotUpdate = true;
                    structure.clientHot = newHot;
                }

                double[] d = structure.simulateHeat();
                structure.applyTemperatureChange();
                structure.lastEnvironmentLoss = d[1];
                GasStack superheatedCoolant = internalInputGasTank.getGas();
                if (superheatedCoolant != null && superheatedCoolant.getGas() == MekanismFluids.SuperheatedSodium &&
                    (internalOutputGasTank.isEmpty() || internalOutputGasTank.isTypeEqual(MekanismFluids.Sodium))) {
                    //Match higher-version behavior: cool a fraction of heated coolant and scale it down at high case temperatures.
                    int amountToCool = Math.round((float) (SynchronizedBoilerData.COOLANT_COOLING_EFFICIENCY * superheatedCoolant.amount));
                    amountToCool = Math.round((float) (amountToCool * (1 - structure.temperature / SynchronizedBoilerData.HEATED_COOLANT_TEMP)));
                    amountToCool = Math.max(0, amountToCool);
                    amountToCool = Math.min(amountToCool, superheatedCoolant.amount);
                    if (amountToCool > 0) {
                        GasStack cooledCoolant = new GasStack(MekanismFluids.Sodium, amountToCool);
                        GasStack remainder = internalOutputGasTank.insert(cooledCoolant, Action.EXECUTE, AutomationType.INTERNAL);
                        int cooled = amountToCool - (remainder == null ? 0 : remainder.amount);
                        if (cooled > 0) {
                            internalInputGasTank.extract(cooled, Action.EXECUTE, AutomationType.INTERNAL);
                            structure.temperature += (cooled * SynchronizedBoilerData.getHeatEnthalpy()) / structure.locations.size();
                        }
                    }
                }

                if (structure.temperature >= SynchronizedBoilerData.BASE_BOIL_TEMP && !internalWaterTank.isEmpty()) {
                    double heatAvailable = structure.getHeatAvailable();
                    structure.lastMaxBoil = (int) Math.floor(heatAvailable / SynchronizedBoilerData.getHeatEnthalpy());
                    int amountToBoil = Math.min(structure.lastMaxBoil, internalWaterTank.getFluidAmount());
                    int boiled = 0;
                    if (amountToBoil > 0) {
                        FluidStack steam = new FluidStack(FluidRegistry.getFluid("steam"), amountToBoil);
                        FluidStack remainder = internalSteamTank.insert(steam, Action.EXECUTE, AutomationType.INTERNAL);
                        boiled = amountToBoil - (remainder == null ? 0 : remainder.amount);
                        if (boiled > 0) {
                            internalWaterTank.extract(boiled, Action.EXECUTE, AutomationType.INTERNAL);
                            structure.temperature -= (boiled * SynchronizedBoilerData.getHeatEnthalpy()) / structure.locations.size();
                        }
                    }
                    structure.lastBoilRate = boiled;
                } else {
                    structure.lastBoilRate = 0;
                    structure.lastMaxBoil = 0;
                }


                if (needsValveUpdate || structure.needsRenderUpdate() || needsHotUpdate) {
                    sendPacketToRenderer();
                }
                structure.prevWater = structure.waterStored != null ? structure.waterStored.copy() : null;
                structure.prevSteam = structure.steamStored != null ? structure.steamStored.copy() : null;
                structure.prevInputGas = structure.InputGas != null ? structure.InputGas.copy() : null;
                structure.prevOutputGas = structure.OutputGas != null ? structure.OutputGas.copy() : null;
                MekanismUtils.saveChunk(this);
            }
        }
    }


    @Override
    public boolean onActivate(EntityPlayer player, EnumHand hand, ItemStack stack) {
        if (!player.isSneaking() && structure != null) {
            Mekanism.packetHandler.sendUpdatePacket(this);
            player.openGui(Mekanism.instance, 54, world, getPos().getX(), getPos().getY(), getPos().getZ());
            return true;
        }
        return false;
    }

    @Override
    protected SynchronizedBoilerData getNewStructure() {
        return new SynchronizedBoilerData();
    }

    @Override
    public BoilerCache getNewCache() {
        return new BoilerCache();
    }

    @Override
    protected BoilerUpdateProtocol getProtocol() {
        return new BoilerUpdateProtocol(this);
    }

    @Override
    protected IHeatCapacitorHolder getInitialHeatCapacitors(IContentsListener listener) {
        return ProxiedHeatCapacitorHolder.create(
              side -> structure != null,
              side -> structure != null,
              side -> structure == null ? Collections.emptyList() : Collections.singletonList(this)
        );
    }

    @Override
    public MultiblockManager<SynchronizedBoilerData> getManager() {
        return Mekanism.boilerManager;
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);

        if (structure != null) {
            data.add(structure.waterVolume * BoilerUpdateProtocol.WATER_PER_TANK);
            data.add(structure.steamVolume * BoilerUpdateProtocol.STEAM_PER_TANK);
            data.add(structure.lastEnvironmentLoss);
            data.add(structure.lastBoilRate);
            data.add(structure.superheatingElements);
            data.add(structure.temperature);
            data.add(structure.lastMaxBoil);

            TileUtils.addFluidStack(data, structure.waterStored);
            TileUtils.addFluidStack(data, structure.steamStored);
            TileUtils.addGasStack(data, structure.InputGas);
            TileUtils.addGasStack(data, structure.OutputGas);
            structure.upperRenderLocation.write(data);

            if (isRendering) {
                data.add(structure.clientHot);
                Set<ValveData> toSend = new ObjectOpenHashSet<>();
                structure.valves.forEach(valveData -> {
                    if (valveData.activeTicks > 0) {
                        toSend.add(valveData);
                    }
                });
                data.add(toSend.size());
                toSend.forEach(valveData -> {
                    valveData.location.write(data);
                    data.add(valveData.side.ordinal());
                });
            }
        }
        return data;
    }


    public double getLastEnvironmentLoss() {
        return structure != null ? structure.lastEnvironmentLoss : 0;
    }

    public double getTemperature() {
        return structure != null ? structure.temperature : 0;
    }

    public int getLastBoilRate() {
        return structure != null ? structure.lastBoilRate : 0;
    }

    public int getLastMaxBoil() {
        return structure != null ? structure.lastMaxBoil : 0;
    }

    public int getSuperheatingElements() {
        return structure != null ? structure.superheatingElements : 0;
    }

    public BoilerWaterTank getWaterTank() {
        return internalWaterTank;
    }

    public BoilerSteamTank getSteamTank() {
        return internalSteamTank;
    }

    public BoilerInputGasTank getInputGasTank() {
        return internalInputGasTank;
    }

    public BoilerOutputGasTank getOutputGasTank() {
        return internalOutputGasTank;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);

        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            if (clientHasStructure) {
                clientWaterCapacity = dataStream.readInt();
                clientSteamCapacity = dataStream.readInt();
                structure.lastEnvironmentLoss = dataStream.readDouble();
                structure.lastBoilRate = dataStream.readInt();
                structure.superheatingElements = dataStream.readInt();
                structure.temperature = dataStream.readDouble();
                structure.lastMaxBoil = dataStream.readInt();

                structure.waterStored = TileUtils.readFluidStack(dataStream);
                structure.steamStored = TileUtils.readFluidStack(dataStream);
                structure.InputGas = TileUtils.readGasStack(dataStream);
                structure.OutputGas = TileUtils.readGasStack(dataStream);
                structure.upperRenderLocation = Coord4D.read(dataStream);

                if (isRendering) {
                    structure.clientHot = dataStream.readBoolean();
                    SynchronizedBoilerData.clientHotMap.put(structure.inventoryID, structure.clientHot);
                    int size = dataStream.readInt();
                    valveViewing.clear();
                    for (int i = 0; i < size; i++) {
                        ValveData data = new ValveData();
                        data.location = Coord4D.read(dataStream);
                        data.side = EnumFacing.byIndex(dataStream.readInt());

                        valveViewing.add(data);

                        TileEntityBoilerCasing tileEntity = (TileEntityBoilerCasing) data.location.getTileEntity(world);
                        if (tileEntity != null) {
                            tileEntity.clientHasStructure = true;
                        }
                    }
                }
            }
        }
    }

    @Override
    public double getTemp() {
        return 0;
    }

    @Override
    public double getInverseConductionCoefficient() {
        return SynchronizedBoilerData.CASING_INVERSE_CONDUCTION_COEFFICIENT;
    }

    @Override
    public double getInsulationCoefficient(EnumFacing side) {
        return SynchronizedBoilerData.CASING_INSULATION_COEFFICIENT;
    }

    @Override
    public void transferHeatTo(double heat) {
        if (structure != null) {
            structure.heatToAbsorb += heat;
        }
    }

    @Override
    public double[] simulateHeat() {
        return new double[]{0, 0};
    }

    @Override
    public double applyTemperatureChange() {
        return 0;
    }

    @Override
    public boolean canConnectHeat(EnumFacing side) {
        return structure != null;
    }

    @Override
    public IHeatTransfer getAdjacent(EnumFacing side) {
        return null;
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return true;
        }
        return super.isCapabilityDisabled(capability, side);
    }

    @Nonnull
    @Override
    public String getName() {
        return LangUtils.localize("gui.thermoelectricBoiler");
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return InventoryUtils.EMPTY;
    }
}
