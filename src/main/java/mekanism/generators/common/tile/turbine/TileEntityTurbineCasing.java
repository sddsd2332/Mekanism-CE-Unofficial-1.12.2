package mekanism.generators.common.tile.turbine;

import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.Coord4D;
import mekanism.api.TileNetworkList;
import mekanism.api.energy.IStrictEnergyStorage;
import mekanism.api.math.MathUtils;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.multiblock.MultiblockCache;
import mekanism.common.multiblock.MultiblockManager;
import mekanism.common.multiblock.UpdateProtocol;
import mekanism.common.tile.TileEntityGasTank.GasMode;
import mekanism.common.tile.multiblock.TileEntityMultiblock;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TileUtils;
import mekanism.generators.common.MekanismGenerators;
import mekanism.generators.common.content.turbine.SynchronizedTurbineData;
import mekanism.generators.common.content.turbine.TurbineCache;
import mekanism.generators.common.content.turbine.TurbineUpdateProtocol;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;

public class TileEntityTurbineCasing extends TileEntityMultiblock<SynchronizedTurbineData> implements IStrictEnergyStorage {

    public TileEntityTurbineCasing() {
        this("TurbineCasing");
    }

    public TileEntityTurbineCasing(String name) {
        super(name);
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        if (structure != null) {
            if (structure.sanitizeStoredFluid()) {
                markNoUpdateSync();
            }
            if (isRendering) {
                structure.lastSteamInput = structure.newSteamInput;
                structure.newSteamInput = 0;
                int stored = structure.getSteamAmount();
                double proportion = (double) stored / (double) structure.getFluidCapacity();
                double flowRate = 0;

                double energyNeeded = structure.getNeeded();
                if (stored > 0 && energyNeeded > 0) {
                    double energyMultiplier = (MekanismConfig.current().general.maxEnergyPerSteam.val() / TurbineUpdateProtocol.MAX_BLADES) *
                            Math.min(structure.blades, structure.coils * MekanismConfig.current().generators.turbineBladesPerCoil.val());
                    if (energyMultiplier <= 0) {
                        structure.clientFlow = 0;
                    } else {
                        double rate = structure.lowerVolume * (structure.getDispersers() * MekanismConfig.current().generators.turbineDisperserGasFlow.val());
                        rate = Math.min(rate, structure.vents * MekanismConfig.current().generators.turbineVentGasFlow.val());

                        double origRate = rate;
                        rate = Math.min(Math.min(stored, rate), energyNeeded / energyMultiplier) * proportion;

                        int clientFlow = MathUtils.clampToInt(rate);
                        structure.clientFlow = clientFlow;
                        if (clientFlow > 0) {
                            flowRate = rate / origRate;
                            structure.insert(energyMultiplier * rate, Action.EXECUTE, AutomationType.INTERNAL);
                            structure.shrinkSteamStack(clientFlow);
                            structure.setVentWaterStackSize(MathUtils.clampToInt(rate));
                        }
                    }

                } else {
                    structure.clientFlow = 0;
                }

                if (structure.dumpMode == GasMode.DUMPING && structure.fluidStored != null) {
                    int amount = structure.getSteamAmount();
                    structure.shrinkSteamStack(Math.min(amount, Math.max(amount / 50, structure.lastSteamInput * 2)));
                }

                float newRotation = (float) flowRate;
                boolean needsRotationUpdate = false;

                if (Math.abs(newRotation - structure.clientRotation) > SynchronizedTurbineData.ROTATION_THRESHOLD) {
                    structure.clientRotation = newRotation;
                    needsRotationUpdate = true;
                }

                if (structure.needsRenderUpdate() || needsRotationUpdate) {
                    sendPacketToRenderer();
                }
                structure.prevFluid = structure.fluidStored != null ? structure.fluidStored.copy() : null;
            }
        }
    }

    @Nonnull
    @Override
    public String getName() {
        return LangUtils.localize("gui.industrialTurbine");
    }

    @Override
    public boolean onActivate(EntityPlayer player, EnumHand hand, ItemStack stack) {
        if (!player.isSneaking() && structure != null) {
            Mekanism.packetHandler.sendUpdatePacket(this);
            player.openGui(MekanismGenerators.instance, 6, world, getPos().getX(), getPos().getY(), getPos().getZ());
            return true;
        }
        return false;
    }

    @Override
    public double getEnergy() {
        return structure != null ? structure.electricityStored : 0;
    }

    @Override
    public void setEnergy(double energy) {
        if (structure != null) {
            structure.setEnergy(energy);
            MekanismUtils.saveChunk(this);
        }
    }

    @Override
    public double getMaxEnergy() {
        return structure != null ? structure.getEnergyCapacity() : 0;
    }

    public int getScaledFluidLevel(long i) {
        if (structure == null || structure.getFluidCapacity() == 0 || structure.fluidStored == null) {
            return 0;
        }
        return (int) (structure.fluidStored.amount * i / structure.getFluidCapacity());
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);

        if (structure != null) {
            data.add(structure.volume);
            data.add(structure.lowerVolume);
            data.add(structure.vents);
            data.add(structure.blades);
            data.add(structure.coils);
            data.add(structure.condensers);
            data.add(structure.getDispersers());
            data.add(structure.electricityStored);
            data.add(structure.clientFlow);
            data.add(structure.lastSteamInput);
            data.add(structure.dumpMode.ordinal());
            TileUtils.addFluidStack(data, structure.fluidStored);
            if (isRendering) {
                structure.complex.write(data);
                data.add(structure.clientRotation);
            }
        }
        return data;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (!isRemote()) {
            if (structure != null) {
                byte type = dataStream.readByte();
                if (type == 0) {
                    structure.dumpMode = GasMode.values()[structure.dumpMode.ordinal() == GasMode.values().length - 1 ? 0 : structure.dumpMode.ordinal() + 1];
                }
            }
            return;
        }

        super.handlePacketData(dataStream);

        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            if (clientHasStructure) {
                structure.volume = dataStream.readInt();
                structure.lowerVolume = dataStream.readInt();
                structure.vents = dataStream.readInt();
                structure.blades = dataStream.readInt();
                structure.coils = dataStream.readInt();
                structure.condensers = dataStream.readInt();
                structure.clientDispersers = dataStream.readInt();
                structure.electricityStored = dataStream.readDouble();
                structure.clientFlow = dataStream.readInt();
                structure.lastSteamInput = dataStream.readInt();
                structure.dumpMode = MekanismUtils.getByIndex(GasMode.values(), dataStream.readInt(), GasMode.IDLE);

                structure.fluidStored = TileUtils.readFluidStack(dataStream);

                if (isRendering) {
                    structure.complex = Coord4D.read(dataStream);
                    structure.clientRotation = dataStream.readFloat();
                    SynchronizedTurbineData.clientRotationMap.put(structure.inventoryID, structure.clientRotation);
                }
            }
        }
    }

    @Override
    protected SynchronizedTurbineData getNewStructure() {
        return new SynchronizedTurbineData();
    }

    @Override
    public MultiblockCache<SynchronizedTurbineData> getNewCache() {
        return new TurbineCache();
    }

    @Override
    protected UpdateProtocol<SynchronizedTurbineData> getProtocol() {
        return new TurbineUpdateProtocol(this);
    }

    @Override
    public MultiblockManager<SynchronizedTurbineData> getManager() {
        return MekanismGenerators.turbineManager;
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return InventoryUtils.EMPTY;
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return true;
        }
        return super.isCapabilityDisabled(capability, side);
    }
}
