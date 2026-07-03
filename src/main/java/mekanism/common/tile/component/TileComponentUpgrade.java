package mekanism.common.tile.component;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.Mekanism;
import mekanism.common.Upgrade;
import mekanism.common.base.ITileComponent;
import mekanism.common.base.IUpgradeItem;
import mekanism.common.inventory.container.MekanismContainer.ISpecificContainerTracker;
import mekanism.common.inventory.container.sync.ISyncableData;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.UpgradeInventorySlot;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UpgradeUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import java.util.*;

public class TileComponentUpgrade implements ITileComponent, ISpecificContainerTracker {

    /**
     * How long it takes this machine to install an upgrade.
     */
    public static int UPGRADE_TICKS_REQUIRED = 40;
    /**
     * How many upgrade ticks have progressed.
     */
    public int upgradeTicks;
    /**
     * TileEntity implementing this component.
     */
    public TileEntityContainerBlock tileEntity;
    private Map<Upgrade, Integer> upgrades = new EnumMap<>(Upgrade.class);
    private Set<Upgrade> supported = EnumSet.noneOf(Upgrade.class);
    private final UpgradeInventorySlot upgradeSlot;
    private final UpgradeInventorySlot upgradeOutputSlot;
    private boolean canCheckUpgrades = true;

    public TileComponentUpgrade(TileEntityContainerBlock tile) {
        tileEntity = tile;
        setSupported(Upgrade.SPEED);
        setSupported(Upgrade.ENERGY);
        upgradeSlot = UpgradeInventorySlot.input(supported, this::onUpgradeSlotContentsChanged);
        upgradeOutputSlot = UpgradeInventorySlot.output(tile);
        tile.components.add(this);
    }

    public TileComponentUpgrade(TileEntityContainerBlock tile, Upgrade upgrade) {
        tileEntity = tile;
        setSupported(upgrade);
        upgradeSlot = UpgradeInventorySlot.input(supported, this::onUpgradeSlotContentsChanged);
        upgradeOutputSlot = UpgradeInventorySlot.output(tile);
        tile.components.add(this);
    }

    private void onUpgradeSlotContentsChanged() {
        tileEntity.onContentsChanged();
        canCheckUpgrades = true;
    }

    public void readFrom(TileComponentUpgrade upgrade) {
        upgrades = upgrade.upgrades;
        supported = upgrade.supported;
        upgradeSlot.setStackUnchecked(upgrade.upgradeSlot.getStack());
        upgradeOutputSlot.setStackUnchecked(upgrade.upgradeOutputSlot.getStack());
        upgradeTicks = upgrade.upgradeTicks;
        canCheckUpgrades = true;
    }

    @Override
    public void tick() {
        if (!tileEntity.getWorld().isRemote && canCheckUpgrades) {
            ItemStack stack = upgradeSlot.getStack();
            if (!stack.isEmpty() && stack.getItem() instanceof IUpgradeItem upgradeItem) {
                Upgrade type = upgradeItem.getUpgradeType(stack);

                int installed = getUpgrades(type);
                if (supports(type) && installed < type.getMaxInstalled()) {
                    if (upgradeTicks < UPGRADE_TICKS_REQUIRED) {
                        upgradeTicks++;
                        return;
                    } else if (upgradeTicks == UPGRADE_TICKS_REQUIRED) {
                        upgradeTicks = 0;
                        int added = addUpgrades(type, installed, upgradeSlot.getCount());
                        if (added > 0) {
                            upgradeSlot.shrinkStack(added, Action.EXECUTE);
                        }
                        Mekanism.packetHandler.sendUpdatePacket(tileEntity);
                        tileEntity.markNoUpdateSync();
                        return;
                    }
                }
            }
            upgradeTicks = 0;
            canCheckUpgrades = false;
        }
    }

    public UpgradeInventorySlot getUpgradeSlot() {
        return upgradeSlot;
    }

    public UpgradeInventorySlot getUpgradeOutputSlot() {
        return upgradeOutputSlot;
    }

    public double getScaledUpgradeProgress() {
        return upgradeTicks / (double) UPGRADE_TICKS_REQUIRED;
    }

    public boolean isUpgradeInstalled(Upgrade upgrade) {
        return upgrades.containsKey(upgrade);
    }

    public int getUpgrades(Upgrade upgrade) {
        return upgrades.getOrDefault(upgrade, 0);
    }

    public int addUpgrades(Upgrade upgrade, int maxAvailable) {
        return addUpgrades(upgrade, getUpgrades(upgrade), maxAvailable);
    }

    private int addUpgrades(Upgrade upgrade, int installed, int maxAvailable) {
        if (installed < upgrade.getMaxInstalled()) {
            int toAdd = Math.min(upgrade.getMaxInstalled() - installed, maxAvailable);
            if (toAdd > 0) {
                this.upgrades.put(upgrade, installed + toAdd);
                tileEntity.recalculateUpgradables(upgrade);
                if (upgrade == Upgrade.MUFFLING) {
                    //Send an update packet to the client to update the number of muffling upgrades installed
                    tileEntity.doRestrictedTick();
                }
                tileEntity.markNoUpdateSync();
                return toAdd;
            }
        }
        return 0;
    }

    public void removeUpgrade(Upgrade upgrade, boolean removeAll) {
        int installed = getUpgrades(upgrade);
        if (installed > 0) {
            int toRemove = removeAll ? installed : 1;
            ItemStack simulatedRemainder = upgradeOutputSlot.insertItem(UpgradeUtils.getStack(upgrade, toRemove), Action.SIMULATE, AutomationType.INTERNAL);
            if (simulatedRemainder.getCount() < toRemove) {
                toRemove -= simulatedRemainder.getCount();
                if (installed == toRemove) {
                    upgrades.remove(upgrade);
                } else {
                    upgrades.put(upgrade, installed - toRemove);
                }
                tileEntity.recalculateUpgradables(upgrade);
                upgradeOutputSlot.insertItem(UpgradeUtils.getStack(upgrade, toRemove), Action.EXECUTE, AutomationType.INTERNAL);
                canCheckUpgrades = !upgradeSlot.isEmpty();
                tileEntity.markNoUpdateSync();
            }
        }
    }

    public void setSupported(Upgrade upgrade) {
        setSupported(upgrade, true);
    }

    public void removeSupported(Upgrade upgrade) {
        setSupported(upgrade, false);
    }


    public void setSupported(Upgrade upgrade, boolean isSupported) {
        if (isSupported) {
            supported.add(upgrade);
        } else {
            supported.remove(upgrade);
        }
        canCheckUpgrades = true;
    }

    public boolean supports(Upgrade upgrade) {
        return supported.contains(upgrade);
    }

    public Set<Upgrade> getInstalledTypes() {
        return upgrades.keySet();
    }

    public Set<Upgrade> getSupportedTypes() {
        return supported;
    }

    public void clearSupportedTypes() {
        supported.clear();
    }

    private List<IInventorySlot> getSlots() {
        return Arrays.asList(upgradeSlot, upgradeOutputSlot);
    }

    @Override
    public void read(ByteBuf dataStream) {
        upgrades.clear();
        int amount = dataStream.readInt();

        for (int i = 0; i < amount; i++) {
            Upgrade upgrade = MekanismUtils.getByIndex(Upgrade.values(), dataStream.readInt(), null);
            int installed = dataStream.readInt();
            if (upgrade != null) {
                upgrades.put(upgrade, installed);
            }
        }
        upgradeTicks = dataStream.readInt();
        getSupportedTypes().forEach(upgrade -> tileEntity.recalculateUpgradables(upgrade));
    }

    @Override
    public void write(TileNetworkList data) {
        data.add(upgrades.size());
        upgrades.forEach((key, value) -> {
            data.add(key.ordinal());
            data.add(value);
        });
        data.add(upgradeTicks);
    }

    @Override
    public void read(NBTTagCompound nbtTags) {
        if (nbtTags.hasKey(NBTConstants.COMPONENT_UPGRADE, NBT.TAG_COMPOUND)) {
            NBTTagCompound upgradeNBT = nbtTags.getCompoundTag(NBTConstants.COMPONENT_UPGRADE);
            upgrades = Upgrade.buildMap(upgradeNBT);
            if (upgradeNBT.hasKey(NBTConstants.ITEMS, NBT.TAG_LIST)) {
                DataHandlerUtils.readContainers(getSlots(), upgradeNBT.getTagList(NBTConstants.ITEMS, NBT.TAG_COMPOUND));
            }
        } else {
            upgrades.clear();
            upgradeSlot.setEmpty();
            upgradeOutputSlot.setEmpty();
        }
        canCheckUpgrades = true;
        getSupportedTypes().forEach(upgrade -> tileEntity.recalculateUpgradables(upgrade));
    }

    @Override
    public void write(NBTTagCompound nbtTags) {
        NBTTagCompound upgradeNBT = new NBTTagCompound();
        Upgrade.saveMap(upgrades, upgradeNBT);
        upgradeNBT.setTag(NBTConstants.ITEMS, DataHandlerUtils.writeContainers(getSlots()));
        nbtTags.setTag(NBTConstants.COMPONENT_UPGRADE, upgradeNBT);
    }

    @Override
    public void invalidate() {
    }

    @Override
    public List<ISyncableData> getSpecificSyncableData() {
        List<ISyncableData> list = new ArrayList<>();
        list.add(SyncableInt.create(() -> upgradeTicks, value -> upgradeTicks = value));
        for (Upgrade upgrade : Upgrade.values()) {
            if (supports(upgrade)) {
                list.add(SyncableInt.create(() -> upgrades.getOrDefault(upgrade, 0), value -> {
                    if (value == 0) {
                        upgrades.remove(upgrade);
                    } else if (value > 0) {
                        upgrades.put(upgrade, value);
                    }
                }));
            }
        }
        return list;
    }

}
