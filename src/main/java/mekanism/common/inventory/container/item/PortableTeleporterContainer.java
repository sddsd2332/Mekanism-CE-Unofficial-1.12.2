package mekanism.common.inventory.container.item;

import mekanism.api.Coord4D;
import mekanism.api.energy.IStrictEnergyStorage;
import mekanism.common.content.teleporter.TeleporterFrequency;
import mekanism.common.inventory.container.IEmptyContainer;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.inventory.container.sync.SyncableByte;
import mekanism.common.item.ItemPortableTeleporter;
import mekanism.common.tile.TileEntityTeleporter;
import mekanism.common.util.SecurityUtils;
import mekanism.common.util.StorageUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;

public class PortableTeleporterContainer extends FrequencyItemContainer<TeleporterFrequency> implements IEmptyContainer {

    private byte status = 3;

    public PortableTeleporterContainer(InventoryPlayer inv, EnumHand hand, ItemStack stack) {
        super(inv, hand, stack);
    }

    @Override
    protected FrequencyType<TeleporterFrequency> getFrequencyType() {
        return FrequencyType.TELEPORTER;
    }

    public ItemStack getStack() {
        return stack;
    }

    public byte getStatus() {
        return status;
    }

    @Override
    protected void addContainerTrackers() {
        super.addContainerTrackers();
        if (isRemote()) {
            track(SyncableByte.create(this::getStatus, value -> status = value));
        } else {
            track(SyncableByte.create(this::calculateStatus, value -> status = value));
        }
    }

    private byte calculateStatus() {
        TeleporterFrequency freq = getFrequencyFromStack();
        if (freq == null || freq.activeCoords.isEmpty()) {
            return 3;
        }
        Coord4D coords = freq.getClosestCoords(new Coord4D(inv.player));
        if (coords == null) {
            return 3;
        }
        double energyNeeded = ItemPortableTeleporter.calculateEnergyCost(inv.player, coords);
        if (energyNeeded > StorageUtils.getStoredEnergy(stack)) {
            return 4;
        }
        World teleWorld = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(coords.dimensionId);
        if (teleWorld == null || !(coords.getTileEntity(teleWorld) instanceof TileEntityTeleporter teleporter) || !SecurityUtils.canAccess(inv.player, teleporter)) {
            return 3;
        }
        return 1;
    }

    public IStrictEnergyStorage getEnergyStorage() {
        return new IStrictEnergyStorage() {
            @Override
            public double getEnergy() {
                return StorageUtils.getStoredEnergy(stack);
            }

            @Override
            public void setEnergy(double energy) {
            }

            @Override
            public double getMaxEnergy() {
                return StorageUtils.getMaxEnergy(stack);
            }
        };
    }
}
