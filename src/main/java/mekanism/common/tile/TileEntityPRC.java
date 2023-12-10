package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import mekanism.api.EnumColor;
import mekanism.api.TileNetworkList;
import mekanism.api.gas.*;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.MekanismBlocks;
import mekanism.common.SideData;
import mekanism.common.Upgrade;
import mekanism.common.base.*;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.item.ItemUpgrade;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.PressurizedInput;
import mekanism.common.recipe.machines.PressurizedRecipe;
import mekanism.common.recipe.outputs.PressurizedOutput;
import mekanism.api.tier.BaseTier;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.prefab.TileEntityBasicMachine;
import mekanism.common.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;

public class TileEntityPRC extends TileEntityBasicMachine<PressurizedInput, PressurizedOutput, PressurizedRecipe> implements IFluidHandlerWrapper, IGasHandler,
        ISustainedData, ITankManager, ITierUpgradeable {

    private static final String[] methods = new String[]{"getEnergy", "getProgress", "isActive", "facing", "canOperate", "getMaxEnergy", "getEnergyNeeded",
            "getFluidStored", "getGasStored"};
    public FluidTank inputFluidTank = new FluidTank(10000);
    public GasTank inputGasTank = new GasTank(10000);
    public GasTank outputGasTank = new GasTank(10000);

    public TileEntityPRC() {
        super("prc", MachineType.PRESSURIZED_REACTION_CHAMBER, 3, 100);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.ENERGY, TransmissionType.FLUID, TransmissionType.GAS);

        configComponent.addOutput(TransmissionType.ITEM, new SideData("None", EnumColor.GREY, InventoryUtils.EMPTY));
        configComponent.addOutput(TransmissionType.ITEM, new SideData("Input", EnumColor.RED, new int[]{0}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData("Energy", EnumColor.BRIGHT_GREEN, new int[]{1}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData("Output", EnumColor.INDIGO, new int[]{2}));
        configComponent.setConfig(TransmissionType.ITEM, new byte[]{1, 1, 1, 2, 1, 3});

        configComponent.setInputConfig(TransmissionType.FLUID);

        configComponent.addOutput(TransmissionType.GAS, new SideData("None", EnumColor.GREY, InventoryUtils.EMPTY));
        configComponent.addOutput(TransmissionType.GAS, new SideData("Input", EnumColor.RED, new int[]{1}));
        configComponent.addOutput(TransmissionType.GAS, new SideData("Output", EnumColor.INDIGO, new int[]{2}));
        configComponent.setConfig(TransmissionType.GAS, new byte[]{1, 1, 1, 1, 1, 2});

        configComponent.setInputConfig(TransmissionType.ENERGY);

        inventory = NonNullList.withSize(4, ItemStack.EMPTY);

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(TransmissionType.ITEM, configComponent.getOutputs(TransmissionType.ITEM).get(3));
        ejectorComponent.setOutputData(TransmissionType.GAS, configComponent.getOutputs(TransmissionType.GAS).get(2));
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!world.isRemote) {
            PressurizedRecipe recipe = getRecipe();
            ChargeUtils.discharge(1, this);
            if (canOperate(recipe) && MekanismUtils.canFunction(this) && getEnergy() >= MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK + recipe.extraEnergy)) {
                boolean update = BASE_TICKS_REQUIRED != recipe.ticks;
                BASE_TICKS_REQUIRED = recipe.ticks;
                if (update) {
                    recalculateUpgradables(Upgrade.SPEED);
                }
                setActive(true);
                if ((operatingTicks + 1) < ticksRequired) {
                    operatingTicks++;
                    electricityStored -= MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK + recipe.extraEnergy);
                } else if ((operatingTicks + 1) >= ticksRequired && getEnergy() >= MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK + recipe.extraEnergy)) {
                    operate(recipe);
                    operatingTicks = 0;
                    electricityStored -= MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK + recipe.extraEnergy);
                }
            } else {
                BASE_TICKS_REQUIRED = 100;
                if (prevEnergy >= getEnergy()) {
                    setActive(false);
                }
            }

            if (!canOperate(recipe)) {
                operatingTicks = 0;
            }
            prevEnergy = getEnergy();
        }
    }

    @Override
    public boolean upgrade(BaseTier upgradeTier) {
        if (upgradeTier != BaseTier.BASIC) {
            return false;
        }
        world.setBlockToAir(getPos());
        world.setBlockState(getPos(), MekanismBlocks.MachineBlock.getStateFromMeta(5), 3);

        TileEntityFactory factory = Objects.requireNonNull((TileEntityFactory) world.getTileEntity(getPos()));
        IFactory.RecipeType type = IFactory.RecipeType.PRC;

        //Basic
        factory.facing = facing;
        factory.clientFacing = clientFacing;
        factory.ticker = ticker;
        factory.redstone = redstone;
        factory.redstoneLastTick = redstoneLastTick;
        factory.doAutoSync = doAutoSync;

        //Electric
        factory.electricityStored = electricityStored;

        //Machine
        factory.progress[0] = operatingTicks;
        factory.setActive(isActive);
        factory.setControlType(getControlType());
        factory.prevEnergy = prevEnergy;
        factory.upgradeComponent.readFrom(upgradeComponent);
        factory.upgradeComponent.setUpgradeSlot(0);
        factory.ejectorComponent.readFrom(ejectorComponent);
        factory.ejectorComponent.setOutputData(TransmissionType.ITEM, factory.configComponent.getOutputs(TransmissionType.ITEM).get(3));
        factory.ejectorComponent.setOutputData(TransmissionType.GAS, configComponent.getOutputs(TransmissionType.GAS).get(2));

        factory.setRecipeType(type);
        factory.upgradeComponent.setSupported(Upgrade.GAS, type.fuelEnergyUpgrades());
        factory.securityComponent.readFrom(securityComponent);

        for (TransmissionType transmission : configComponent.getTransmissions()) {
            factory.configComponent.setConfig(transmission, configComponent.getConfig(transmission).asByteArray());
            factory.configComponent.setEjecting(transmission, configComponent.isEjecting(transmission));
        }

        factory.gasTank.setGas(inputGasTank.getGas());
        factory.gasOutTank.setGas(outputGasTank.getGas());
        factory.fluidTank.setFluid(inputFluidTank.getFluid());

        factory.inventory.set(5, inventory.get(0));
        factory.inventory.set(1, inventory.get(1));
        factory.inventory.set(5 + 3, inventory.get(2));
        factory.inventory.set(0, inventory.get(3));


        for (Upgrade upgrade : factory.upgradeComponent.getSupportedTypes()) {
            factory.recalculateUpgradables(upgrade);
        }
        factory.upgraded = true;
        factory.markDirty();
        return true;
    }



    @Override
    public boolean isItemValidForSlot(int slotID, @Nonnull ItemStack itemstack) {
        if (slotID == 0) {
            return RecipeHandler.isInPressurizedRecipe(itemstack);
        } else if (slotID == 1) {
            return ChargeUtils.canBeDischarged(itemstack);
        } else if (slotID == 3) {
            return itemstack.getItem() instanceof ItemUpgrade;
        }
        return false;
    }

    @Override
    public PressurizedRecipe getRecipe() {
        PressurizedInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getPRCRecipe(input);
        }
        return cachedRecipe;
    }

    @Override
    public PressurizedInput getInput() {
        return new PressurizedInput(inventory.get(0), inputFluidTank.getFluid(), inputGasTank.getGas());
    }

    @Override
    public void operate(PressurizedRecipe recipe) {
        recipe.operate(inventory, 0 ,inputFluidTank, inputGasTank, outputGasTank,2);
        markDirty();
    }

    @Override
    public boolean canOperate(PressurizedRecipe recipe) {
        return recipe != null && recipe.canOperate(inventory,0, inputFluidTank, inputGasTank, outputGasTank,2);
    }

    @Override
    public boolean canExtractItem(int slotID, @Nonnull ItemStack itemstack, @Nonnull EnumFacing side) {
        if (slotID == 1) {
            return ChargeUtils.canBeOutputted(itemstack, false);
        }
        return slotID == 2 || slotID == 4;
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        TileUtils.addTankData(data, inputFluidTank);
        TileUtils.addTankData(data, inputGasTank);
        TileUtils.addTankData(data, outputGasTank);
        return data;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            TileUtils.readTankData(dataStream, inputFluidTank);
            TileUtils.readTankData(dataStream, inputGasTank);
            TileUtils.readTankData(dataStream, outputGasTank);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbtTags) {
        super.readFromNBT(nbtTags);
        inputFluidTank.readFromNBT(nbtTags.getCompoundTag("inputFluidTank"));
        inputGasTank.read(nbtTags.getCompoundTag("inputGasTank"));
        outputGasTank.read(nbtTags.getCompoundTag("outputGasTank"));
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbtTags) {
        super.writeToNBT(nbtTags);
        nbtTags.setTag("inputFluidTank", inputFluidTank.writeToNBT(new NBTTagCompound()));
        nbtTags.setTag("inputGasTank", inputGasTank.write(new NBTTagCompound()));
        nbtTags.setTag("outputGasTank", outputGasTank.write(new NBTTagCompound()));
        return nbtTags;
    }



    @Override
    public Map<PressurizedInput, PressurizedRecipe> getRecipes() {
        return null;
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        switch (method) {
            case 0:
                return new Object[]{getEnergy()};
            case 1:
                return new Object[]{operatingTicks};
            case 2:
                return new Object[]{isActive};
            case 3:
                return new Object[]{facing};
            case 4:
                return new Object[]{canOperate(getRecipe())};
            case 5:
                return new Object[]{getMaxEnergy()};
            case 6:
                return new Object[]{getMaxEnergy() - getEnergy()};
            case 7:
                return new Object[]{inputFluidTank.getFluidAmount()};
            case 8:
                return new Object[]{inputGasTank.getStored()};
            default:
                throw new NoSuchMethodException();
        }
    }

    @Override
    public int fill(EnumFacing from, @Nonnull FluidStack resource, boolean doFill) {
        return inputFluidTank.fill(resource, doFill);
    }

    @Override
    public boolean canFill(EnumFacing from, @Nonnull FluidStack fluid) {
        if (configComponent.getOutput(TransmissionType.FLUID, from, facing).ioState == SideData.IOState.INPUT) {
            return FluidContainerUtils.canFill(inputFluidTank.getFluid(), fluid);
        }
        return false;
    }

    @Override
    public FluidTankInfo[] getTankInfo(EnumFacing from) {
        if (configComponent.getOutput(TransmissionType.FLUID, from, facing).ioState != SideData.IOState.OFF){
            return new FluidTankInfo[]{inputFluidTank.getInfo()};
        }
        return PipeUtils.EMPTY;
    }

    @Override
    public FluidTankInfo[] getAllTanks() {
        return new FluidTankInfo[]{inputFluidTank.getInfo()};
    }

    @Override
    public int receiveGas(EnumFacing side, GasStack stack, boolean doTransfer) {
        if (canReceiveGas(side, stack.getGas())) {
            return inputGasTank.receive(stack, doTransfer);
        }
        return 0;
    }

    @Override
    public GasStack drawGas(EnumFacing side, int amount, boolean doTransfer) {
        if (canDrawGas(side, null)) {
            return outputGasTank.draw(amount, doTransfer);
        }
        return null;
    }

    @Override
    public boolean canReceiveGas(EnumFacing side, Gas type) {
        return configComponent.getOutput(TransmissionType.GAS, side, facing).hasSlot(1) && inputGasTank.canReceive(type) ;
    }

    @Override
    public boolean canDrawGas(EnumFacing side, Gas type) {
        return configComponent.getOutput(TransmissionType.GAS, side, facing).hasSlot(2) && outputGasTank.canDraw(type);
    }

    @Nonnull
    @Override
    public GasTankInfo[] getTankInfo() {
        return new GasTankInfo[]{inputGasTank, outputGasTank};
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return false;
        }
        return capability == Capabilities.GAS_HANDLER_CAPABILITY || capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return null;
        } else if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            return Capabilities.GAS_HANDLER_CAPABILITY.cast(this);
        } else if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(new FluidHandlerWrapper(this, side));
        }
        return super.getCapability(capability, side);
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        return configComponent.isCapabilityDisabled(capability, side, facing) || super.isCapabilityDisabled(capability, side);
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        if (inputFluidTank.getFluid() != null) {
            ItemDataUtils.setCompound(itemStack, "inputFluidTank", inputFluidTank.getFluid().writeToNBT(new NBTTagCompound()));
        }
        if (inputGasTank.getGas() != null) {
            ItemDataUtils.setCompound(itemStack, "inputGasTank", inputGasTank.getGas().write(new NBTTagCompound()));
        }
        if (outputGasTank.getGas() != null) {
            ItemDataUtils.setCompound(itemStack, "outputGasTank", outputGasTank.getGas().write(new NBTTagCompound()));
        }
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        inputFluidTank.setFluid(FluidStack.loadFluidStackFromNBT(ItemDataUtils.getCompound(itemStack, "inputFluidTank")));
        inputGasTank.setGas(GasStack.readFromNBT(ItemDataUtils.getCompound(itemStack, "inputGasTank")));
        outputGasTank.setGas(GasStack.readFromNBT(ItemDataUtils.getCompound(itemStack, "outputGasTank")));
    }

    @Override
    public Object[] getTanks() {
        return new Object[]{inputFluidTank, inputGasTank, outputGasTank};
    }


}
