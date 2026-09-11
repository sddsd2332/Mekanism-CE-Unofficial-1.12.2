package mekanism.common.tile.component;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.Upgrade;
import mekanism.common.base.IUpgradeItem;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.base.ITileComponent;
import mekanism.common.inventory.container.MekanismContainer.ISpecificContainerTracker;
import mekanism.common.inventory.container.sync.ISyncableData;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.UpgradeInventorySlot;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.upgrade.ExternalUpgradeSupportRegistry;
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
    private final Map<Upgrade, Integer> upgrades = new LinkedHashMap<>();
    private final Set<Upgrade> supported = new LinkedHashSet<>();
    private final Set<Upgrade> installedView = Collections.unmodifiableSet(upgrades.keySet());
    private final Set<Upgrade> supportedView = Collections.unmodifiableSet(supported);
    private final UpgradeInventorySlot upgradeSlot;
    private final UpgradeInventorySlot upgradeOutputSlot;
    private boolean canCheckUpgrades = true;

    public TileComponentUpgrade(TileEntityContainerBlock tile) {
        tileEntity = tile;
        setSupported(Upgrade.SPEED);
        setSupported(Upgrade.ENERGY);
        upgradeSlot = UpgradeInventorySlot.input(this::canInsertUpgrade, this::onUpgradeSlotContentsChanged);
        upgradeOutputSlot = UpgradeInventorySlot.output(tile);
        tile.components.add(this);
    }

    public TileComponentUpgrade(TileEntityContainerBlock tile, Upgrade upgrade) {
        tileEntity = tile;
        setSupported(upgrade);
        upgradeSlot = UpgradeInventorySlot.input(this::canInsertUpgrade, this::onUpgradeSlotContentsChanged);
        upgradeOutputSlot = UpgradeInventorySlot.output(tile);
        tile.components.add(this);
    }

    private void onUpgradeSlotContentsChanged() {
        tileEntity.onContentsChanged();
        canCheckUpgrades = true;
    }

    public void readFrom(TileComponentUpgrade upgrade) {
        upgrades.clear();
        upgrades.putAll(upgrade.upgrades);
        supported.clear();
        supported.addAll(upgrade.supported);
        upgradeSlot.setStackUnchecked(upgrade.upgradeSlot.getStack());
        upgradeOutputSlot.setStackUnchecked(upgrade.upgradeOutputSlot.getStack());
        upgradeTicks = upgrade.upgradeTicks;
        canCheckUpgrades = true;
    }

    @Override
    public void tick() {
        if (!tileEntity.getWorld().isRemote && canCheckUpgrades) {
            ItemStack stack = upgradeSlot.getStack();
            Upgrade type = Upgrade.byStack(stack);
            if (type != null) {
                int installed = getUpgrades(type);
                if (canInstall(stack)) {
                    if (upgradeTicks < UPGRADE_TICKS_REQUIRED) {
                        upgradeTicks++;
                        return;
                    } else if (upgradeTicks == UPGRADE_TICKS_REQUIRED) {
                        upgradeTicks = 0;
                        int added = addUpgrades(type, installed, upgradeSlot.getCount());
                        if (added > 0) {
                            onUpgradeInstalled(upgradeSlot.getStack(), added);
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

    public int getInstallRoom(Upgrade upgrade) {
        return canInstallIgnoringRoom(upgrade) ? Math.max(0, upgrade.getMaxInstalled() - getUpgrades(upgrade)) : 0;
    }

    public boolean canInstall(Upgrade upgrade) {
        return getInstallRoom(upgrade) > 0;
    }

    public boolean canInstall(ItemStack stack) {
        Upgrade upgrade = Upgrade.byStack(stack);
        return upgrade != null && getInstallRoom(upgrade) > 0 && canInstallStack(stack);
    }

    private boolean canInsertUpgrade(ItemStack stack) {
        return canInstall(stack);
    }

    private boolean canInstallStack(ItemStack stack) {
        return stack.getItem() instanceof IUpgradeItem upgradeItem && tileEntity instanceof IUpgradeTile upgradeTile && upgradeItem.canInstallUpgrade(stack, upgradeTile);
    }

    private boolean canInstallIgnoringRoom(Upgrade upgrade) {
        return upgrade != null && supports(upgrade) && !hasConflictingUpgrade(upgrade);
    }

    private boolean hasConflictingUpgrade(Upgrade upgrade) {
        for (Upgrade installed : upgrades.keySet()) {
            if (getUpgrades(installed) > 0 && installed != upgrade && !upgrade.isCompatibleWith(installed)) {
                return true;
            }
        }
        return false;
    }

    public int installUpgrade(ItemStack stack, Action action) {
        Upgrade upgrade = Upgrade.byStack(stack);
        if (upgrade == null || stack.isEmpty() || !canInstallStack(stack)) {
            return 0;
        }
        int toInstall = Math.min(stack.getCount(), getInstallRoom(upgrade));
        if (toInstall > 0 && action.execute()) {
            int installed = addUpgrades(upgrade, toInstall);
            if (installed > 0) {
                onUpgradeInstalled(stack, installed);
            }
            stack.shrink(installed);
            return installed;
        }
        return toInstall;
    }

    private void onUpgradeInstalled(ItemStack stack, int amount) {
        if (amount > 0 && stack.getItem() instanceof IUpgradeItem upgradeItem && tileEntity instanceof IUpgradeTile upgradeTile) {
            upgradeItem.onInstalled(stack, upgradeTile, amount);
        }
    }

    public Map<Upgrade, Integer> getInstalledUpgrades() {
        return Collections.unmodifiableMap(upgrades);
    }

    public boolean hasUpgrades() {
        return !upgrades.isEmpty();
    }

    public int setUpgrades(Upgrade upgrade, int amount) {
        if (upgrade == null) {
            return 0;
        }
        int installed = Math.max(0, Math.min(amount, upgrade.getMaxInstalled()));
        if (installed > 0 && !canInstallIgnoringRoom(upgrade)) {
            return getUpgrades(upgrade);
        }
        int previous = getUpgrades(upgrade);
        if (previous == installed) {
            return installed;
        }
        if (installed <= 0) {
            upgrades.remove(upgrade);
        } else {
            upgrades.put(upgrade, installed);
        }
        onUpgradeChanged(upgrade, previous, installed);
        return installed;
    }

    public void clearUpgrades() {
        new ArrayList<>(upgrades.keySet()).forEach(upgrade -> setUpgrades(upgrade, 0));
    }

    public int addUpgrades(Upgrade upgrade, int maxAvailable) {
        if (upgrade == null || maxAvailable <= 0 || !canInstallIgnoringRoom(upgrade)) {
            return 0;
        }
        return addUpgrades(upgrade, getUpgrades(upgrade), maxAvailable);
    }

    private int addUpgrades(Upgrade upgrade, int installed, int maxAvailable) {
        if (canInstallIgnoringRoom(upgrade) && installed < upgrade.getMaxInstalled()) {
            int toAdd = Math.min(upgrade.getMaxInstalled() - installed, maxAvailable);
            if (toAdd > 0) {
                return setUpgrades(upgrade, installed + toAdd) - installed;
            }
        }
        return 0;
    }

    public void removeUpgrade(Upgrade upgrade, boolean removeAll) {
        int installed = getUpgrades(upgrade);
        if (installed > 0) {
            int toRemove = removeAll ? installed : 1;
            ItemStack stackToReturn = getUninstalledStack(upgrade, toRemove);
            if (stackToReturn.isEmpty()) {
                return;
            }
            toRemove = Math.min(toRemove, stackToReturn.getCount());
            ItemStack simulatedRemainder = upgradeOutputSlot.insertItem(stackToReturn, Action.SIMULATE, AutomationType.INTERNAL);
            if (simulatedRemainder.getCount() < toRemove) {
                toRemove -= simulatedRemainder.getCount();
                setUpgrades(upgrade, installed - toRemove);
                ItemStack stackToInsert = stackToReturn.copy();
                stackToInsert.setCount(toRemove);
                upgradeOutputSlot.insertItem(stackToInsert, Action.EXECUTE, AutomationType.INTERNAL);
                canCheckUpgrades = !upgradeSlot.isEmpty();
            }
        }
    }

    private ItemStack getUninstalledStack(Upgrade upgrade, int amount) {
        ItemStack stackToReturn = UpgradeUtils.getStack(upgrade, amount);
        if (!stackToReturn.isEmpty() && stackToReturn.getItem() instanceof IUpgradeItem upgradeItem && tileEntity instanceof IUpgradeTile upgradeTile) {
            stackToReturn = upgradeItem.getUninstalledStack(upgradeTile, upgrade, amount, stackToReturn);
        }
        return stackToReturn == null ? ItemStack.EMPTY : stackToReturn;
    }

    private void onUpgradeChanged(Upgrade upgrade, int previousAmount, int amount) {
        tileEntity.recalculateUpgradables(upgrade);
        upgrade.onChanged(tileEntity, previousAmount, amount);
        if (upgrade == Upgrade.MUFFLING) {
            //Send an update packet to the client to update the number of muffling upgrades installed
            tileEntity.doRestrictedTick();
        }
        tileEntity.markNoUpdateSync();
    }

    public void setSupported(Upgrade upgrade) {
        setSupported(upgrade, true);
    }

    public void setSupported(Upgrade... upgrades) {
        if (upgrades != null) {
            Arrays.stream(upgrades).forEach(this::setSupported);
        }
    }

    public void setSupported(Collection<Upgrade> upgrades) {
        if (upgrades != null) {
            upgrades.forEach(this::setSupported);
        }
    }

    public void removeSupported(Upgrade upgrade) {
        setSupported(upgrade, false);
    }

    public void removeSupported(Upgrade... upgrades) {
        if (upgrades != null) {
            Arrays.stream(upgrades).forEach(this::removeSupported);
        }
    }

    public void removeSupported(Collection<Upgrade> upgrades) {
        if (upgrades != null) {
            upgrades.forEach(this::removeSupported);
        }
    }

    public void setSupported(Upgrade upgrade, boolean isSupported) {
        if (upgrade == null) {
            return;
        }
        if (isSupported) {
            supported.add(upgrade);
        } else {
            supported.remove(upgrade);
        }
        canCheckUpgrades = true;
    }

    public boolean supports(Upgrade upgrade) {
        return supported.contains(upgrade) || ExternalUpgradeSupportRegistry.supports(tileEntity, upgrade);
    }

    public Set<Upgrade> getInstalledTypes() {
        return installedView;
    }

    public Set<Upgrade> getSupportedTypes() {
        Set<Upgrade> external = ExternalUpgradeSupportRegistry.getSupported(tileEntity);
        if (external.isEmpty()) {
            return supportedView;
        }
        Set<Upgrade> combined = new LinkedHashSet<>(supported);
        combined.addAll(external);
        return Collections.unmodifiableSet(combined);
    }

    public void clearSupportedTypes() {
        supported.clear();
        canCheckUpgrades = true;
    }

    private List<IInventorySlot> getSlots() {
        return Arrays.asList(upgradeSlot, upgradeOutputSlot);
    }

    @Override
    public void read(ByteBuf dataStream) {
        upgrades.clear();
        int amount = dataStream.readInt();

        for (int i = 0; i < amount; i++) {
            Upgrade upgrade = Upgrade.byName(PacketHandler.readString(dataStream));
            int installed = dataStream.readInt();
            if (upgrade != null) {
                if (installed > 0) {
                    int toAdd = installed;
                    upgrades.compute(upgrade, (key, existing) -> (int) Math.min(Integer.MAX_VALUE, toAdd + (long) (existing == null ? 0 : existing)));
                }
            }
        }
        upgradeTicks = dataStream.readInt();
        tileEntity.recalculateAllUpgradables(getSupportedTypes());
    }

    @Override
    public void write(TileNetworkList data) {
        List<Map.Entry<Upgrade, Integer>> validUpgrades = new ArrayList<>();
        upgrades.forEach((key, value) -> {
            if (key != null && value != null) {
                int installed = Math.min(value, key.getMaxInstalled());
                if (installed > 0) {
                    validUpgrades.add(new AbstractMap.SimpleImmutableEntry<>(key, installed));
                }
            }
        });
        data.add(validUpgrades.size());
        validUpgrades.forEach(entry -> {
            data.add(entry.getKey().getRegistryNameString());
            data.add(entry.getValue());
        });
        data.add(upgradeTicks);
    }

    @Override
    public void read(NBTTagCompound nbtTags) {
        read(nbtTags, true);
    }

    /**
     * Reads component state, optionally allowing the owner to delay recalculation until related state is restored.
     */
    public void read(NBTTagCompound nbtTags, boolean recalculate) {
        if (nbtTags.hasKey(NBTConstants.COMPONENT_UPGRADE, NBT.TAG_COMPOUND)) {
            NBTTagCompound upgradeNBT = nbtTags.getCompoundTag(NBTConstants.COMPONENT_UPGRADE);
            upgrades.clear();
            upgrades.putAll(Upgrade.buildMap(upgradeNBT));
            if (upgradeNBT.hasKey(NBTConstants.ITEMS, NBT.TAG_LIST)) {
                DataHandlerUtils.readContainers(getSlots(), upgradeNBT.getTagList(NBTConstants.ITEMS, NBT.TAG_COMPOUND));
            }
        } else {
            upgrades.clear();
            upgradeSlot.setEmpty();
            upgradeOutputSlot.setEmpty();
        }
        canCheckUpgrades = true;
        if (recalculate) {
            tileEntity.recalculateAllUpgradables(getSupportedTypes());
        }
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
        for (Upgrade upgrade : Upgrade.getRegisteredUpgrades()) {
            if (supports(upgrade)) {
                list.add(SyncableInt.create(() -> upgrades.getOrDefault(upgrade, 0), value -> {
                    if (value <= 0) {
                        upgrades.remove(upgrade);
                    } else {
                        upgrades.put(upgrade, value);
                    }
                }));
            }
        }
        return list;
    }

}
