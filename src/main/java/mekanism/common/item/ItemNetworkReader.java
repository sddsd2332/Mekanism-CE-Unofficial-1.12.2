package mekanism.common.item;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.*;
import mekanism.api.heat.IHeatHandler;
import mekanism.api.transmitters.DynamicNetwork;
import mekanism.api.transmitters.IGridTransmitter;
import mekanism.api.transmitters.TransmitterNetworkRegistry;
import mekanism.common.Mekanism;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.HeatCapabilityUtils;
import mekanism.common.util.StorageUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import java.util.Set;

public class ItemNetworkReader extends ItemEnergized {

    public static double ENERGY_PER_USE = 400;

    public ItemNetworkReader() {
        super(60000);
        setRarity(EnumRarity.UNCOMMON);
        setMaxStackSize(1);
    }

    @Nonnull
    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            TileEntity tileEntity = world.getTileEntity(pos);
            boolean drain = !player.capabilities.isCreativeMode;
            if (StorageUtils.getStoredEnergy(stack) >= ENERGY_PER_USE && tileEntity != null) {
                IHeatHandler heatHandler = HeatCapabilityUtils.getHandler(tileEntity, side.getOpposite());
                if (CapabilityUtils.hasCapability(tileEntity, Capabilities.GRID_TRANSMITTER_CAPABILITY, side.getOpposite())) {
                    if (drain) {
                        StorageUtils.extractEnergy(stack, ENERGY_PER_USE, Action.EXECUTE);
                    }
                    IGridTransmitter transmitter = CapabilityUtils.getCapability(tileEntity, Capabilities.GRID_TRANSMITTER_CAPABILITY, side.getOpposite());

                    player.sendMessage(new TextComponentString(EnumColor.GREY + "------------- " + EnumColor.DARK_BLUE + Mekanism.LOG_TAG + EnumColor.GREY + " -------------"));
                    player.sendMessage(new TextComponentString(EnumColor.GREY + " *Transmitters: " + EnumColor.DARK_GREY + transmitter.getTransmitterNetworkSize()));
                    player.sendMessage(new TextComponentString(EnumColor.GREY + " *Acceptors: " + EnumColor.DARK_GREY + transmitter.getTransmitterNetworkAcceptorSize()));
                    player.sendMessage(new TextComponentString(EnumColor.GREY + " *Needed: " + EnumColor.DARK_GREY + transmitter.getTransmitterNetworkNeeded()));
                    player.sendMessage(new TextComponentString(EnumColor.GREY + " *Buffer: " + EnumColor.DARK_GREY + transmitter.getTransmitterNetworkBuffer()));
                    player.sendMessage(new TextComponentString(EnumColor.GREY + " *Throughput: " + EnumColor.DARK_GREY + transmitter.getTransmitterNetworkFlow()));
                    player.sendMessage(new TextComponentString(EnumColor.GREY + " *Capacity: " + EnumColor.DARK_GREY + transmitter.getTransmitterNetworkCapacity()));

                    if (heatHandler != null) {
                        player.sendMessage(new TextComponentString(EnumColor.GREY + " *Temperature: " + EnumColor.DARK_GREY + heatHandler.getTotalTemperature() + "K"));
                    }

                    player.sendMessage(new TextComponentString(EnumColor.GREY + "------------- " + EnumColor.DARK_BLUE + "[=======]" + EnumColor.GREY + " -------------"));
                    return EnumActionResult.SUCCESS;
                } else if (heatHandler != null) {
                    if (drain) {
                        StorageUtils.extractEnergy(stack, ENERGY_PER_USE, Action.EXECUTE);
                    }

                    player.sendMessage(new TextComponentString(EnumColor.GREY + "------------- " + EnumColor.DARK_BLUE + Mekanism.LOG_TAG + EnumColor.GREY + " -------------"));
                    player.sendMessage(new TextComponentString(EnumColor.GREY + " *Temperature: " + EnumColor.DARK_GREY + heatHandler.getTotalTemperature() + "K"));
                    player.sendMessage(new TextComponentString(EnumColor.GREY + "------------- " + EnumColor.DARK_BLUE + "[=======]" + EnumColor.GREY + " -------------"));
                    return EnumActionResult.SUCCESS;
                } else {
                    if (drain) {
                        StorageUtils.extractEnergy(stack, ENERGY_PER_USE, Action.EXECUTE);
                    }
                    Set<DynamicNetwork> iteratedNetworks = new ObjectOpenHashSet<>();

                    for (EnumFacing iterSide : EnumFacing.VALUES) {
                        Coord4D coord = Coord4D.get(tileEntity).offset(iterSide);
                        TileEntity tile = coord.getTileEntity(world);
                        if (CapabilityUtils.hasCapability(tile, Capabilities.GRID_TRANSMITTER_CAPABILITY, iterSide.getOpposite())) {
                            IGridTransmitter transmitter = CapabilityUtils.getCapability(tile, Capabilities.GRID_TRANSMITTER_CAPABILITY, iterSide.getOpposite());

                            if (transmitter.getTransmitterNetwork().getPossibleAcceptors().contains(coord.offset(iterSide.getOpposite())) &&
                                    !iteratedNetworks.contains(transmitter.getTransmitterNetwork())) {
                                player.sendMessage(new TextComponentString(EnumColor.GREY + "------------- " + EnumColor.DARK_BLUE + "[" +
                                        transmitter.getTransmissionType().getName() + "]" + EnumColor.GREY + " -------------"));
                                player.sendMessage(new TextComponentString(EnumColor.GREY + " *Connected sides: " + EnumColor.DARK_GREY +
                                        transmitter.getTransmitterNetwork().getAcceptorDirections().get(coord.offset(iterSide.getOpposite()))));
                                player.sendMessage(new TextComponentString(EnumColor.GREY + "------------- " + EnumColor.DARK_BLUE + "[=======]" + EnumColor.GREY + " -------------"));
                                iteratedNetworks.add(transmitter.getTransmitterNetwork());
                            }
                        }
                    }
                    return EnumActionResult.SUCCESS;
                }
            }

            if (player.isSneaking() && MekanismAPI.debug) {
                String[] strings = TransmitterNetworkRegistry.getInstance().toStrings();
                player.sendMessage(new TextComponentString(EnumColor.GREY + "---------- " + EnumColor.DARK_BLUE + "[Mekanism Debug]" + EnumColor.GREY + " ----------"));
                for (String s : strings) {
                    player.sendMessage(new TextComponentString(EnumColor.DARK_GREY + s));
                }
                player.sendMessage(new TextComponentString(EnumColor.GREY + "------------- " + EnumColor.DARK_BLUE + "[=======]" + EnumColor.GREY + " -------------"));
            }
        }
        return EnumActionResult.PASS;
    }

    @Override
    public boolean canSendEnergy(ItemStack itemstack) {
        return false;
    }
}
