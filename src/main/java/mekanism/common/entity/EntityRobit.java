package mekanism.common.entity;

import com.google.common.base.Stopwatch;
import mekanism.api.*;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.Mekanism;
import mekanism.common.MekanismItems;
import mekanism.common.base.ISustainedInventory;
import mekanism.common.entity.ai.RobitAIFollow;
import mekanism.common.entity.ai.RobitAIPickup;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.inventory.ISlotBackedInventory;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableDouble;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.*;
import mekanism.common.item.ItemConfigurator;
import mekanism.common.item.ItemRobit;
import mekanism.common.network.PacketSecurityUpdate.SecurityPacket;
import mekanism.common.network.PacketSecurityUpdate.SecurityUpdateMessage;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.tile.TileEntityChargepad;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.SecurityUtils;
import mekanism.common.util.StorageUtils;
import micdoodle8.mods.galacticraft.api.entity.IEntityBreathable;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Optional.Interface;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Interface(iface = "micdoodle8.mods.galacticraft.api.entity.IEntityBreathable", modid = MekanismHooks.GALACTICRAFT_MOD_ID)
public class EntityRobit extends EntityCreature implements ISlotBackedInventory, ISustainedInventory, IEnergyContainer, IEntityBreathable {

    private static final DataParameter<Float> ELECTRICITY = EntityDataManager.createKey(EntityRobit.class, DataSerializers.FLOAT);
    private static final DataParameter<String> OWNER_UUID = EntityDataManager.createKey(EntityRobit.class, DataSerializers.STRING);
    private static final DataParameter<String> OWNER_NAME = EntityDataManager.createKey(EntityRobit.class, DataSerializers.STRING);
    private static final DataParameter<Integer> SECURITY = EntityDataManager.createKey(EntityRobit.class, DataSerializers.VARINT);
    private static final DataParameter<Boolean> FOLLOW = EntityDataManager.createKey(EntityRobit.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Boolean> DROP_PICKUP = EntityDataManager.createKey(EntityRobit.class, DataSerializers.BOOLEAN);

    public enum ContainerType {
        MAIN,
        INVENTORY,
        SMELTING
    }

    public double MAX_ELECTRICITY = 100000;
    public Coord4D homeLocation;
    private final List<IInventorySlot> inventorySlots = new ArrayList<>();
    private final List<IInventorySlot> mainContainerSlots = new ArrayList<>();
    private final List<IInventorySlot> inventoryContainerSlots = new ArrayList<>();
    private final List<IInventorySlot> smeltingContainerSlots = new ArrayList<>();
    private EnergyInventorySlot energySlot;
    private InputInventorySlot smeltingInputSlot;
    private FuelInventorySlot smeltingFuelSlot;
    private OutputInventorySlot smeltingOutputSlot;
    public int furnaceBurnTime = 0;
    public int currentItemBurnTime = 0;
    public int furnaceCookTime = 0;
    public boolean texTick;
    private final Stopwatch lastClicked = Stopwatch.createStarted();

    public EntityRobit(World world) {
        super(world);
        setSize(0.625F, 0.65625F);
        getNavigator().setCanSwim(false);
        tasks.addTask(1, new RobitAIPickup(this, 1.0F));
        tasks.addTask(2, new RobitAIFollow(this, 1.0F, 4.0F, 2.0F));
        tasks.addTask(3, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        tasks.addTask(3, new EntityAILookIdle(this));
        tasks.addTask(4, new EntityAISwimming(this));
        setAlwaysRenderNameTag(true);
        setupInventorySlots();
    }

    public EntityRobit(World world, double x, double y, double z) {
        this(world);
        setPosition(x, y, z);
        prevPosX = x;
        prevPosY = y;
        prevPosZ = z;
    }

    private void setupInventorySlots() {
        inventorySlots.clear();
        mainContainerSlots.clear();
        inventoryContainerSlots.clear();
        smeltingContainerSlots.clear();
        for (int slotY = 0; slotY < 3; slotY++) {
            for (int slotX = 0; slotX < 9; slotX++) {
                IInventorySlot slot = BasicInventorySlot.at(this, 8 + slotX * 18, 18 + slotY * 18);
                inventorySlots.add(slot);
                inventoryContainerSlots.add(slot);
            }
        }
        inventorySlots.add(energySlot = EnergyInventorySlot.fillOrConvert(this, this::getEntityWorld, this, 153, 17));
        inventorySlots.add(smeltingInputSlot = InputInventorySlot.at(stack -> !FurnaceRecipes.instance().getSmeltingResult(stack).isEmpty(), this, 56, 17));
        inventorySlots.add(smeltingFuelSlot = FuelInventorySlot.forFuel(TileEntityFurnace::getItemBurnTime, this, 56, 53));
        inventorySlots.add(smeltingOutputSlot = OutputInventorySlot.at(this, 116, 35));
        mainContainerSlots.add(energySlot);
        smeltingContainerSlots.add(smeltingInputSlot);
        smeltingContainerSlots.add(smeltingFuelSlot);
        smeltingContainerSlots.add(smeltingOutputSlot);
    }

    @Nonnull
    public List<IInventorySlot> getContainerInventorySlots(ContainerType containerType) {
        if (containerType == ContainerType.INVENTORY) {
            return inventoryContainerSlots;
        } else if (containerType == ContainerType.MAIN) {
            return mainContainerSlots;
        } else if (containerType == ContainerType.SMELTING) {
            return smeltingContainerSlots;
        }
        return Collections.emptyList();
    }

    public void addContainerTrackers(MekanismContainer container, ContainerType containerType) {
        if (containerType == ContainerType.MAIN) {
            container.track(SyncableDouble.create(this::getEnergy, this::setEnergy));
        } else if (containerType == ContainerType.SMELTING) {
            container.track(SyncableInt.create(() -> furnaceCookTime, value -> furnaceCookTime = value));
            container.track(SyncableInt.create(() -> furnaceBurnTime, value -> furnaceBurnTime = value));
            container.track(SyncableInt.create(() -> currentItemBurnTime, value -> currentItemBurnTime = value));
        }
    }

    @Nonnull
    @Override
    public PathNavigateGround getNavigator() {
        return (PathNavigateGround) navigator;
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.3);
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(1);
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(ELECTRICITY, 0F);
        dataManager.register(OWNER_UUID, "");
        dataManager.register(OWNER_NAME, "");
        dataManager.register(SECURITY, SecurityMode.PUBLIC.ordinal());
        dataManager.register(FOLLOW, false);
        dataManager.register(DROP_PICKUP, false);
    }

    public double getRoundedTravelEnergy() {
        return new BigDecimal(getDistance(prevPosX, prevPosY, prevPosZ) * 1.5).setScale(2, RoundingMode.HALF_EVEN).doubleValue();
    }

    @Override
    public void onEntityUpdate() {
        if (!world.isRemote) {
            if (getFollowing() && getOwner() != null && getDistanceSq(getOwner()) > 4 && !getNavigator().noPath() && getEnergy() > 0) {
                extract(getRoundedTravelEnergy(), Action.EXECUTE, AutomationType.INTERNAL);
            }
        }

        super.onEntityUpdate();

        if (!world.isRemote) {
            if (getDropPickup()) {
                collectItems();
            }
            if (homeLocation == null) {
                setDead();
                return;
            }

            if (ticksExisted % 20 == 0) {
                World serverWorld = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(homeLocation.dimensionId);
                if (serverWorld != null && homeLocation.exists(serverWorld)) {
                    if (!(homeLocation.getTileEntity(serverWorld) instanceof TileEntityChargepad)) {
                        drop();
                        setDead();
                    }
                }
            }

            if (getEnergy() == 0 && !isOnChargepad()) {
                goHome();
            }

            energySlot.fillContainerOrConvert();

            if (furnaceBurnTime > 0) {
                furnaceBurnTime--;
            }

            if (!world.isRemote) {
                if (furnaceBurnTime == 0 && canSmelt()) {
                    currentItemBurnTime = furnaceBurnTime = smeltingFuelSlot.burn();
                }

                if (furnaceBurnTime > 0 && canSmelt()) {
                    furnaceCookTime++;
                    if (furnaceCookTime == 200) {
                        furnaceCookTime = 0;
                        smeltItem();
                    }
                } else {
                    furnaceCookTime = 0;
                }
            }
        }
    }

    private void collectItems() {
        List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, getEntityBoundingBox().grow(1.5, 1.5, 1.5));

        if (!items.isEmpty()) {
            for (EntityItem item : items) {
                if (item.cannotPickup() || item.getItem().getItem() instanceof ItemRobit || item.isDead) {
                    continue;
                }
                for (int i = 0; i < 27; i++) {
                    IInventorySlot slot = inventorySlots.get(i);
                    ItemStack itemStack = slot.getStack();
                    if (itemStack.isEmpty()) {
                        slot.setStack(item.getItem());
                        onItemPickup(item, item.getItem().getCount());
                        item.setDead();
                        playSound(SoundEvents.ENTITY_ITEM_PICKUP, 1.0F, ((rand.nextFloat() - rand.nextFloat()) * 0.7F + 1.0F) * 2.0F);
                        break;
                    } else if (ItemHandlerHelper.canItemStacksStack(itemStack, item.getItem()) && itemStack.getCount() < itemStack.getMaxStackSize()) {
                        int needed = itemStack.getMaxStackSize() - itemStack.getCount();
                        int toAdd = Math.min(needed, item.getItem().getCount());
                        slot.growStack(toAdd, Action.EXECUTE);
                        item.getItem().shrink(toAdd);
                        onItemPickup(item, toAdd);
                        if (item.getItem().getCount() == 0) {
                            item.setDead();
                        }
                        playSound(SoundEvents.ENTITY_ITEM_PICKUP, 1.0F, ((rand.nextFloat() - rand.nextFloat()) * 0.7F + 1.0F) * 2.0F);
                        break;
                    }
                }
            }
        }
    }

    public void goHome() {
        setFollowing(false);
        if (world.provider.getDimension() != homeLocation.dimensionId) {
            changeDimension(homeLocation.dimensionId, (world1, entity, yaw) ->
                    entity.setLocationAndAngles(homeLocation.x + 0.5, homeLocation.y + 0.3, homeLocation.z + 0.5, yaw, rotationPitch));
        } else {
            setPositionAndUpdate(homeLocation.x + 0.5, homeLocation.y + 0.3, homeLocation.z + 0.5);
        }
        motionX = 0;
        motionY = 0;
        motionZ = 0;
    }

    private boolean canSmelt() {
        ItemStack input = smeltingInputSlot.getStack();
        if (input.isEmpty()) {
            return false;
        }
        ItemStack result = FurnaceRecipes.instance().getSmeltingResult(input);
        if (result.isEmpty()) {
            return false;
        }
        ItemStack currentOutput = smeltingOutputSlot.getStack();
        if (currentOutput.isEmpty()) {
            return true;
        }
        if (!ItemHandlerHelper.canItemStacksStack(currentOutput, result)) {
            return false;
        }
        int newAmount = currentOutput.getCount() + result.getCount();
        return newAmount <= getInventoryStackLimit() && newAmount <= result.getMaxStackSize();
    }

    public void smeltItem() {
        if (canSmelt()) {
            ItemStack input = smeltingInputSlot.getStack();
            ItemStack result = FurnaceRecipes.instance().getSmeltingResult(input);
            if (smeltingOutputSlot.isEmpty()) {
                smeltingOutputSlot.setStack(result.copy());
            } else if (ItemHandlerHelper.canItemStacksStack(smeltingOutputSlot.getStack(), result)) {
                smeltingOutputSlot.growStack(result.getCount(), Action.EXECUTE);
            }
            smeltingInputSlot.shrinkStack(1, Action.EXECUTE);
        }
    }

    public boolean isOnChargepad() {
        BlockPos pos = new BlockPos(this);
        return world.getTileEntity(pos) instanceof TileEntityChargepad;
    }

    @Nonnull
    @Override
    public EnumActionResult applyPlayerInteraction(EntityPlayer entityplayer, Vec3d vec, EnumHand hand) {
        if (!SecurityUtils.canAccess(entityplayer, this)) {
            if (!world.isRemote) {
                SecurityUtils.displayNoAccess(entityplayer);
            }
            return EnumActionResult.FAIL;
        }
        ItemStack stack = entityplayer.getHeldItem(hand);
        if (entityplayer.isSneaking()) {
            if (!stack.isEmpty() && stack.getItem() instanceof ItemConfigurator) {
                if (this.lastClicked.elapsed(TimeUnit.MILLISECONDS) > 200) {
                    this.lastClicked.reset().start();
                    if (!world.isRemote) {
                        drop();
                    }
                    setDead();
                    entityplayer.swingArm(hand);
                    return EnumActionResult.SUCCESS;
                }
            }
        } else {
            MekanismUtils.openEntityGui(entityplayer, this, 21);
            return EnumActionResult.SUCCESS;
        }
        return EnumActionResult.PASS;
    }

    public void drop() {
        EntityItem entityItem = new EntityItem(world, posX, posY + 0.3, posZ, new ItemStack(MekanismItems.Robit));
        ItemRobit item = (ItemRobit) entityItem.getItem().getItem();
        StorageUtils.setStoredEnergy(entityItem.getItem(), getEnergy(), getMaxEnergy());
        item.setInventory(((ISustainedInventory) this).getInventory(), entityItem.getItem());
        item.setName(entityItem.getItem(), getName());
        item.setOwnerUUID(entityItem.getItem(), getOwnerUUID());
        item.setSecurity(entityItem.getItem(), getSecurityMode());

        float k = 0.05F;
        entityItem.motionX = 0;
        entityItem.motionY = rand.nextGaussian() * k + 0.2F;
        entityItem.motionZ = 0;
        world.spawnEntity(entityItem);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound nbtTags) {
        super.writeEntityToNBT(nbtTags);
        nbtTags.setDouble("electricityStored", getEnergy());
        nbtTags.setString("name", getName());
        if (getOwnerUUID() != null) {
            nbtTags.setString("ownerUUID", getOwnerUUID().toString());
        }
        nbtTags.setInteger("securityMode", getSecurityMode().ordinal());
        nbtTags.setBoolean("follow", getFollowing());
        nbtTags.setBoolean("dropPickup", getDropPickup());
        if (homeLocation != null) {
            homeLocation.write(nbtTags);
        }
        nbtTags.setTag(NBTConstants.ITEMS, DataHandlerUtils.writeContainers(getInventorySlots(null)));
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound nbtTags) {
        super.readEntityFromNBT(nbtTags);
        setEnergy(nbtTags.getDouble("electricityStored"));
        setCustomNameTag(nbtTags.getString("name"));
        if (nbtTags.hasKey("ownerUUID")) {
            setOwnerUUID(UUID.fromString(nbtTags.getString("ownerUUID")));
        }
        setSecurityMode(MekanismUtils.getByIndex(SecurityMode.values(), nbtTags.getInteger("securityMode"), SecurityMode.PUBLIC));
        setFollowing(nbtTags.getBoolean("follow"));
        setDropPickup(nbtTags.getBoolean("dropPickup"));
        homeLocation = Coord4D.read(nbtTags);
        DataHandlerUtils.readContainers(getInventorySlots(null), nbtTags.getTagList(NBTConstants.ITEMS, NBT.TAG_COMPOUND));
    }

    @Override
    protected void damageEntity(@Nonnull DamageSource damageSource, float amount) {
        amount = ForgeHooks.onLivingHurt(this, damageSource, amount);
        if (amount <= 0) {
            return;
        }

        amount = applyArmorCalculations(damageSource, amount);
        amount = applyPotionDamageCalculations(damageSource, amount);
        float j = getHealth();
        extract(amount * 1000, Action.EXECUTE, AutomationType.INTERNAL);
        getCombatTracker().trackDamage(damageSource, j, amount);
    }

    @Override
    protected void onDeathUpdate() {
    }

    public void setHome(Coord4D home) {
        homeLocation = home;
    }

    @Override
    public boolean canBePushed() {
        return getEnergy() > 0;
    }

    public double getEnergy() {
        return dataManager.get(ELECTRICITY);
    }

    public void setEnergy(double energy) {
        dataManager.set(ELECTRICITY, (float) Math.max(Math.min(energy, MAX_ELECTRICITY), 0));
    }

    public EntityPlayer getOwner() {
        return world.getPlayerEntityByUUID(getOwnerUUID());
    }

    public String getOwnerName() {
        return dataManager.get(OWNER_NAME);
    }

    public UUID getOwnerUUID() {
        try {
            return UUID.fromString(dataManager.get(OWNER_UUID));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public void setOwnerUUID(UUID uuid) {
        dataManager.set(OWNER_UUID, uuid.toString());
        dataManager.set(OWNER_NAME, MekanismUtils.getLastKnownUsername(uuid));
        if (!world.isRemote) {
            Mekanism.packetHandler.sendToAll(new SecurityUpdateMessage(SecurityPacket.UPDATE, uuid, null));
        }
    }

    public SecurityMode getSecurityMode() {
        return MekanismUtils.getByIndex(SecurityMode.values(), dataManager.get(SECURITY), SecurityMode.PUBLIC);
    }

    public void setSecurityMode(SecurityMode mode) {
        dataManager.set(SECURITY, mode.ordinal());
    }

    public boolean getFollowing() {
        return dataManager.get(FOLLOW);
    }

    public void setFollowing(boolean follow) {
        dataManager.set(FOLLOW, follow);
    }

    public boolean getDropPickup() {
        return dataManager.get(DROP_PICKUP);
    }

    public void setDropPickup(boolean pickup) {
        dataManager.set(DROP_PICKUP, pickup);
    }

    @Override
    public double getMaxEnergy() {
        return MAX_ELECTRICITY;
    }

    @Override
    public void onContentsChanged() {
    }

    @Nonnull
    @Override
    public List<IInventorySlot> getInventorySlots(@Nullable EnumFacing side) {
        return inventorySlots;
    }

    @Override
    public boolean isUsableByPlayer(@Nonnull EntityPlayer entityplayer) {
        return true;
    }

    @Override
    public void openInventory(@Nonnull EntityPlayer player) {
    }

    @Override
    public void closeInventory(@Nonnull EntityPlayer player) {
    }

    @Override
    public void setInventory(NBTTagList nbtTags, Object... data) {
        if (nbtTags == null || nbtTags.tagCount() == 0) {
            return;
        }
        DataHandlerUtils.readContainers(getInventorySlots(null), nbtTags);
    }

    @Override
    public NBTTagList getInventory(Object... data) {
        return DataHandlerUtils.writeContainers(getInventorySlots(null));
    }

    @Override
    public boolean isEmpty() {
        return ISlotBackedInventory.super.isEmpty();
    }

    @Override
    public boolean canBreath() {
        return true;
    }
}
