package mekanism.common.network.qio;

import mekanism.api.Action;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasItem;
import mekanism.common.content.qio.IQIODriveHolder;
import mekanism.common.content.qio.IQIODriveItem;
import mekanism.common.content.qio.QIODriveStorage;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOResourceEntry;
import mekanism.common.content.qio.QIOResourceType;
import mekanism.common.content.qio.QIOResourceTypeRegistry;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.lib.inventory.HashedItem;
import mekanism.common.tier.QIODriveTier;
import mekanism.common.util.FluidContainerUtils;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketQIOViewerActionTest {

    private static Fluid fluid;
    private static Fluid otherFluid;
    private static Gas gas;
    private static Gas otherGas;
    private File worldDirectory;

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
        fluid = registerFluid("qio_viewer_action_fluid");
        otherFluid = registerFluid("qio_viewer_action_other_fluid");
        gas = registerGas("qio_viewer_action_gas");
        otherGas = registerGas("qio_viewer_action_other_gas");
    }

    @AfterEach
    void cleanUp() throws Exception {
        QIODriveStorage.INSTANCE.reset();
        QIOResourceTypeRegistry.INSTANCE.reset();
        if (worldDirectory != null) {
            delete(worldDirectory);
        }
    }

    @Test
    void viewerMergesSameItemAndNbtRegardlessOfQuantity() {
        ItemStack existing = new ItemStack(Blocks.STONE, 10);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("variant", "a");
        existing.setTagCompound(tag);

        ItemStack requested = existing.copy();
        requested.setCount(48);
        assertTrue(PacketQIOViewerAction.canMergeInto(existing, requested, 64));

        requested.getTagCompound().setString("variant", "b");
        assertFalse(PacketQIOViewerAction.canMergeInto(existing, requested, 64));

        existing.setCount(64);
        assertFalse(PacketQIOViewerAction.canMergeInto(existing, existing.copy(), 64));
    }

    @Test
    void matchingCursorStackCanTakeMoreRegardlessOfCurrentQuantity() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-viewer-action-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        ItemStack stored = new ItemStack(Blocks.STONE);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("variant", "a");
        stored.setTagCompound(tag);
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getOrTrackItem(HashedItem.create(stored));
        QIOResourceEntry entry = QIOResourceEntry.create(resource, 100);

        ItemStack held = stored.copy();
        held.setCount(10);
        assertTrue(PacketQIOViewerAction.canTakeIntoHeldStack(entry, held));

        held.setCount(held.getMaxStackSize());
        assertFalse(PacketQIOViewerAction.canTakeIntoHeldStack(entry, held));
        held.setCount(10);
        held.getTagCompound().setString("variant", "b");
        assertFalse(PacketQIOViewerAction.canTakeIntoHeldStack(entry, held));
        assertFalse(PacketQIOViewerAction.canTakeIntoHeldStack(entry, new ItemStack(Blocks.DIRT, 10)));
    }

    @Test
    void fluidHeldCompatibilityAcceptsMatchingPartialStacksOnly() throws Exception {
        QIOResourceEntry entry = createFluidEntry(fluid, 4_000);
        TestFluidContainerItem item = new TestFluidContainerItem(1_000);
        ItemStack matching = fluidContainer(item, fluid, 400);
        matching.setCount(3);
        assertTrue(PacketQIOViewerAction.canTakeFluidIntoHeldStack(entry, matching));

        assertFalse(PacketQIOViewerAction.canTakeFluidIntoHeldStack(entry, fluidContainer(item, otherFluid, 400)));
        assertFalse(PacketQIOViewerAction.canTakeFluidIntoHeldStack(entry, fluidContainer(item, fluid, 1_000)));
    }

    @Test
    void gasHeldCompatibilityAcceptsMatchingPartialStacksOnly() throws Exception {
        QIOResourceEntry entry = createGasEntry(gas, 4_000);
        TestGasContainerItem item = new TestGasContainerItem(1_000);
        ItemStack matching = gasContainer(item, gas, 400);
        matching.setCount(3);
        assertTrue(PacketQIOViewerAction.canTakeGasIntoHeldStack(entry, matching));

        assertFalse(PacketQIOViewerAction.canTakeGasIntoHeldStack(entry, gasContainer(item, otherGas, 400)));
        assertFalse(PacketQIOViewerAction.canTakeGasIntoHeldStack(entry, gasContainer(item, gas, 1_000)));
    }

    @Test
    void putAmountUsesResourceUnitsInsteadOfContainerCount() {
        TestFluidContainerItem fluidItem = new TestFluidContainerItem(1_000);
        assertEquals(750, PacketQIOViewerAction.getHeldPutAmount(fluidContainer(fluidItem, fluid, 750), false));
        assertEquals(750, PacketQIOViewerAction.getHeldPutAmount(fluidContainer(fluidItem, fluid, 750), true));

        TestGasContainerItem gasItem = new TestGasContainerItem(1_000);
        assertEquals(333, PacketQIOViewerAction.getHeldPutAmount(gasContainer(gasItem, gas, 333), false));
        assertEquals(12, PacketQIOViewerAction.getHeldPutAmount(new ItemStack(Blocks.STONE, 12), false));
        assertEquals(1, PacketQIOViewerAction.getHeldPutAmount(new ItemStack(Blocks.STONE, 12), true));
    }

    @Test
    void shiftTakeFillsMultipleFluidContainersFromOneStack() throws Exception {
        QIOFrequency frequency = createFrequency();
        assertEquals(3_000, frequency.massInsert(new FluidStack(fluid, 3_000), 3_000, Action.EXECUTE));
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getUUIDForFluid(new FluidStack(fluid, 1));
        QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource);
        InventoryPlayer inventory = new InventoryPlayer(null);
        TestFluidContainerItem item = new TestFluidContainerItem(1_000);
        inventory.mainInventory.set(0, new ItemStack(item, 3));

        assertTrue(PacketQIOViewerAction.takeFluidToInventory(inventory, frequency, resource, type, 2_500));
        assertEquals(500, frequency.getStored(resource));
        assertEquals(3, countContainers(inventory, item));
        assertEquals(2_500, countFluid(inventory, item, fluid));
    }

    @Test
    void shiftTakeFillsMultipleGasContainersFromOneStack() throws Exception {
        QIOFrequency frequency = createFrequency();
        assertEquals(3_000, frequency.massInsert(new GasStack(gas, 3_000), 3_000, Action.EXECUTE));
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getUUIDForGas(new GasStack(gas, 1));
        QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource);
        InventoryPlayer inventory = new InventoryPlayer(null);
        TestGasContainerItem item = new TestGasContainerItem(1_000);
        inventory.mainInventory.set(0, new ItemStack(item, 3));

        assertTrue(PacketQIOViewerAction.takeGasToInventory(inventory, frequency, resource, type, 2_500));
        assertEquals(500, frequency.getStored(resource));
        assertEquals(3, countContainers(inventory, item));
        assertEquals(2_500, countGas(inventory, item, gas));
    }

    @Test
    void executionShortfallRollsBackOnlyTheUnfilledFluid() throws Exception {
        QIOFrequency frequency = createFrequency();
        assertEquals(2_000, frequency.massInsert(new FluidStack(fluid, 2_000), 2_000, Action.EXECUTE));
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getUUIDForFluid(new FluidStack(fluid, 1));
        QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource);
        InventoryPlayer inventory = new InventoryPlayer(null);
        TestFluidContainerItem item = new TestFluidContainerItem(1_000, 400);
        inventory.mainInventory.set(0, new ItemStack(item));

        assertTrue(PacketQIOViewerAction.takeFluidToInventory(inventory, frequency, resource, type, 1_000));
        assertEquals(1_600, frequency.getStored(resource));
        assertEquals(400, countFluid(inventory, item, fluid));
    }

    @Test
    void failedContainerPlacementRestoresInventoryAndQioExactly() throws Exception {
        QIOFrequency frequency = createFrequency();
        assertEquals(1_000, frequency.massInsert(new FluidStack(fluid, 1_000), 1_000, Action.EXECUTE));
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getUUIDForFluid(new FluidStack(fluid, 1));
        QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource);
        InventoryPlayer inventory = new InventoryPlayer(null);
        TestFluidContainerItem item = new TestFluidContainerItem(1_000);
        inventory.mainInventory.set(0, new ItemStack(item, 2));
        for (int slot = 1; slot < inventory.mainInventory.size(); slot++) {
            inventory.mainInventory.set(slot, new ItemStack(Blocks.STONE, 64));
        }
        inventory.offHandInventory.set(0, new ItemStack(Blocks.STONE, 64));

        assertFalse(PacketQIOViewerAction.takeFluidToInventory(inventory, frequency, resource, type, 1_000));
        assertEquals(1_000, frequency.getStored(resource));
        assertEquals(2, inventory.mainInventory.get(0).getCount());
        assertEquals(0, countFluid(inventory, item, fluid));
    }

    private QIOResourceEntry createFluidEntry(Fluid type, long amount) throws Exception {
        initializeResourceRegistry();
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getOrTrackFluid(new FluidStack(type, 1));
        QIOResourceEntry entry = QIOResourceEntry.create(resource, amount);
        assertNotNull(entry);
        return entry;
    }

    private QIOResourceEntry createGasEntry(Gas type, long amount) throws Exception {
        initializeResourceRegistry();
        UUID resource = QIOResourceTypeRegistry.INSTANCE.getOrTrackGas(new GasStack(type, 1));
        QIOResourceEntry entry = QIOResourceEntry.create(resource, amount);
        assertNotNull(entry);
        return entry;
    }

    private void initializeResourceRegistry() throws Exception {
        if (worldDirectory == null) {
            worldDirectory = Files.createTempDirectory("qio-viewer-action-resource-test").toFile();
        }
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
    }

    private QIOFrequency createFrequency() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-viewer-action-frequency-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        TestDriveHolder holder = new TestDriveHolder(new ItemStack(new TestDriveItem()));
        QIOFrequency frequency = new QIOFrequency();
        frequency.addHolder(holder);
        frequency.refresh();
        return frequency;
    }

    private static Fluid registerFluid(String name) {
        Fluid existing = FluidRegistry.getFluid(name);
        if (existing != null) {
            return existing;
        }
        Fluid created = new Fluid(name, new net.minecraft.util.ResourceLocation("minecraft", "blocks/water"),
              new net.minecraft.util.ResourceLocation("minecraft", "blocks/water"));
        FluidRegistry.registerFluid(created);
        return created;
    }

    private static Gas registerGas(String name) {
        Gas existing = GasRegistry.getGas(name);
        return existing == null ? GasRegistry.register(new Gas(name, 0x5A87C9)) : existing;
    }

    private static ItemStack fluidContainer(TestFluidContainerItem item, Fluid type, int amount) {
        ItemStack stack = new ItemStack(item);
        if (amount > 0) {
            IFluidHandlerItem handler = FluidContainerUtils.getFluidHandlerCapability(stack);
            assertNotNull(handler);
            assertEquals(amount, handler.fill(new FluidStack(type, amount), true));
        }
        return stack;
    }

    private static ItemStack gasContainer(TestGasContainerItem item, Gas type, int amount) {
        ItemStack stack = new ItemStack(item);
        if (amount > 0) {
            assertEquals(amount, GasInventorySlot.insertGas(stack, new GasStack(type, amount), true));
        }
        return stack;
    }

    private static int countContainers(InventoryPlayer inventory, Item item) {
        int count = 0;
        for (ItemStack stack : inventory.mainInventory) {
            if (!stack.isEmpty() && stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        for (ItemStack stack : inventory.offHandInventory) {
            if (!stack.isEmpty() && stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int countFluid(InventoryPlayer inventory, Item item, Fluid type) {
        int amount = 0;
        for (ItemStack stack : inventory.mainInventory) {
            if (!stack.isEmpty() && stack.getItem() == item) {
                FluidStack contained = FluidContainerUtils.getFluidContained(stack);
                if (contained != null && contained.getFluid() == type) {
                    amount += contained.amount * stack.getCount();
                }
            }
        }
        return amount;
    }

    private static int countGas(InventoryPlayer inventory, Item item, Gas type) {
        int amount = 0;
        for (ItemStack stack : inventory.mainInventory) {
            if (!stack.isEmpty() && stack.getItem() == item) {
                GasStack contained = GasInventorySlot.getContainedGas(stack);
                if (contained != null && contained.getGas() == type) {
                    amount += contained.amount * stack.getCount();
                }
            }
        }
        return amount;
    }

    private static final class TestFluidContainerItem extends Item {

        private final int capacity;
        private final int executeLimit;

        private TestFluidContainerItem(int capacity) {
            this(capacity, Integer.MAX_VALUE);
        }

        private TestFluidContainerItem(int capacity, int executeLimit) {
            this.capacity = capacity;
            this.executeLimit = executeLimit;
            setMaxStackSize(16);
        }

        @Override
        public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable NBTTagCompound nbt) {
            return new FluidHandlerItemStack(stack, capacity) {
                @Override
                public int fill(FluidStack resource, boolean doFill) {
                    if (doFill && resource != null) {
                        FluidStack contained = getFluid();
                        int stored = contained != null && contained.isFluidEqual(resource) ? contained.amount : 0;
                        int executable = Math.max(0, executeLimit - stored);
                        if (executable <= 0) {
                            return 0;
                        }
                        if (resource.amount > executable) {
                            resource = new FluidStack(resource, executable);
                        }
                    }
                    return super.fill(resource, doFill);
                }
            };
        }
    }

    private static final class TestGasContainerItem extends Item implements IGasItem {

        private static final String GAS_NAME = "testGas";
        private static final String GAS_AMOUNT = "testGasAmount";
        private final int capacity;

        private TestGasContainerItem(int capacity) {
            this.capacity = capacity;
            setMaxStackSize(16);
        }

        @Override
        public int getRate(ItemStack itemstack) {
            return capacity;
        }

        @Override
        public int addGas(ItemStack itemstack, GasStack stack) {
            GasStack stored = getGas(itemstack);
            if (stack == null || stack.getGas() == null || stack.amount <= 0 || stored != null && !stored.isGasEqual(stack)) {
                return 0;
            }
            int storedAmount = stored == null ? 0 : stored.amount;
            int accepted = Math.min(stack.amount, capacity - storedAmount);
            if (accepted > 0) {
                setGas(itemstack, new GasStack(stack.getGas(), storedAmount + accepted));
            }
            return accepted;
        }

        @Override
        public GasStack removeGas(ItemStack itemstack, int amount) {
            GasStack stored = getGas(itemstack);
            if (stored == null || amount <= 0) {
                return null;
            }
            int removed = Math.min(amount, stored.amount);
            setGas(itemstack, stored.amount == removed ? null : new GasStack(stored.getGas(), stored.amount - removed));
            return new GasStack(stored.getGas(), removed);
        }

        @Override
        public boolean canReceiveGas(ItemStack itemstack, Gas type) {
            GasStack stored = getGas(itemstack);
            return type != null && (stored == null || stored.getGas() == type && stored.amount < capacity);
        }

        @Override
        public boolean canProvideGas(ItemStack itemstack, Gas type) {
            GasStack stored = getGas(itemstack);
            return stored != null && (type == null || stored.getGas() == type);
        }

        @Override
        @Nullable
        public GasStack getGas(ItemStack itemstack) {
            NBTTagCompound tag = itemstack.getTagCompound();
            if (tag == null || !tag.hasKey(GAS_NAME, 8)) {
                return null;
            }
            Gas stored = GasRegistry.getGas(tag.getString(GAS_NAME));
            int amount = tag.getInteger(GAS_AMOUNT);
            return stored == null || amount <= 0 ? null : new GasStack(stored, amount);
        }

        @Override
        public void setGas(ItemStack itemstack, @Nullable GasStack stack) {
            if (stack == null || stack.getGas() == null || stack.amount <= 0) {
                NBTTagCompound tag = itemstack.getTagCompound();
                if (tag != null) {
                    tag.removeTag(GAS_NAME);
                    tag.removeTag(GAS_AMOUNT);
                    if (tag.isEmpty()) {
                        itemstack.setTagCompound(null);
                    }
                }
                return;
            }
            if (!itemstack.hasTagCompound()) {
                itemstack.setTagCompound(new NBTTagCompound());
            }
            itemstack.getTagCompound().setString(GAS_NAME, stack.getGas().getName());
            itemstack.getTagCompound().setInteger(GAS_AMOUNT, Math.min(capacity, stack.amount));
        }

        @Override
        public int getMaxGas(ItemStack itemstack) {
            return capacity;
        }
    }

    private static final class TestDriveItem extends Item implements IQIODriveItem {

        @Override
        public QIODriveTier getDriveTier() {
            return QIODriveTier.BASE;
        }
    }

    private static final class TestDriveHolder implements IQIODriveHolder {

        private final List<ItemStack> drives;

        private TestDriveHolder(ItemStack drive) {
            drives = Collections.singletonList(drive);
        }

        @Override
        public int getQIODimension() {
            return 0;
        }

        @Override
        public BlockPos getQIOPosition() {
            return BlockPos.ORIGIN;
        }

        @Override
        public List<ItemStack> getQIODriveStacks() {
            return drives;
        }
    }

    private static void delete(File file) throws Exception {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    delete(child);
                }
            }
        }
        Files.deleteIfExists(file.toPath());
    }
}
