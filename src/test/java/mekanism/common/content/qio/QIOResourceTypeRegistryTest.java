package mekanism.common.content.qio;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOResourceTypeRegistryTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    private File worldDirectory;

    @AfterEach
    void cleanUp() throws Exception {
        QIOResourceTypeRegistry.INSTANCE.reset();
        if (worldDirectory != null) {
            delete(worldDirectory);
        }
    }

    @Test
    void repeatedLoadOfCurrentWorldDoesNotCanonicalizeAgain() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-resource-load-test").toFile();
        CountingCanonicalFile countingDirectory = new CountingCanonicalFile(worldDirectory);

        QIOResourceTypeRegistry.INSTANCE.createOrLoad(countingDirectory);
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(countingDirectory);

        assertEquals(1, countingDirectory.getCanonicalizationCount());
    }

    @Test
    void itemCountsAreNormalizedForLookup() throws Exception {
        createRegistry();
        ItemStack one = new ItemStack(Blocks.STONE, 1);
        ItemStack stack = new ItemStack(Blocks.STONE, 64);
        UUID first = QIOResourceTypeRegistry.INSTANCE.getOrTrackItem(mekanism.common.lib.inventory.HashedItem.create(one));
        UUID second = QIOResourceTypeRegistry.INSTANCE.getOrTrackItem(mekanism.common.lib.inventory.HashedItem.create(stack));
        assertEquals(first, second);
        assertEquals(1, QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(first).createItemStack(1).getCount());
    }

    @Test
    void fluidNbtIsPartOfTheResourceIdentity() throws Exception {
        createRegistry();
        Fluid fluid = registerFluid();
        FluidStack firstStack = new FluidStack(fluid, 1_000);
        firstStack.tag = tag("variant", "a");
        FluidStack sameStack = new FluidStack(fluid, 2_000);
        sameStack.tag = tag("variant", "a");
        FluidStack differentStack = new FluidStack(fluid, 1_000);
        differentStack.tag = tag("variant", "b");

        UUID first = QIOResourceTypeRegistry.INSTANCE.getOrTrackFluid(firstStack);
        assertEquals(first, QIOResourceTypeRegistry.INSTANCE.getOrTrackFluid(sameStack));
        assertNotEquals(first, QIOResourceTypeRegistry.INSTANCE.getOrTrackFluid(differentStack));
    }

    @Test
    void fluidsAndGasesNeverShareAResourceIdentity() throws Exception {
        createRegistry();
        Fluid fluid = registerFluid();
        Gas gas = GasRegistry.register(new Gas("qio_registry_gas_" + UUID.randomUUID(), 0xAABBCC));
        UUID fluidId = QIOResourceTypeRegistry.INSTANCE.getOrTrackFluid(new FluidStack(fluid, 1));
        UUID gasId = QIOResourceTypeRegistry.INSTANCE.getOrTrackGas(new GasStack(gas, 1));
        assertNotEquals(fluidId, gasId);
        assertEquals(QIOResourceKind.FLUID, QIOResourceTypeRegistry.INSTANCE.getKindByUUID(fluidId));
        assertEquals(QIOResourceKind.GAS, QIOResourceTypeRegistry.INSTANCE.getKindByUUID(gasId));
    }

    @Test
    void resourceIdsSurviveARegistryRestart() throws Exception {
        createRegistry();
        ItemStack item = new ItemStack(Blocks.STONE);
        Fluid fluid = registerFluid();
        Gas gas = GasRegistry.register(new Gas("qio_registry_restart_gas_" + UUID.randomUUID(), 0x112233));
        FluidStack fluidStack = new FluidStack(fluid, 1);
        GasStack gasStack = new GasStack(gas, 1);
        UUID itemId = QIOResourceTypeRegistry.INSTANCE.getOrTrackItem(mekanism.common.lib.inventory.HashedItem.create(item));
        UUID fluidId = QIOResourceTypeRegistry.INSTANCE.getOrTrackFluid(fluidStack);
        UUID gasId = QIOResourceTypeRegistry.INSTANCE.getOrTrackGas(gasStack);
        QIOResourceTypeRegistry.INSTANCE.flush();

        QIOResourceTypeRegistry.INSTANCE.reset();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        assertEquals(itemId, QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(mekanism.common.lib.inventory.HashedItem.create(item)));
        assertEquals(fluidId, QIOResourceTypeRegistry.INSTANCE.getUUIDForFluid(fluidStack));
        assertEquals(gasId, QIOResourceTypeRegistry.INSTANCE.getUUIDForGas(gasStack));
    }

    @Test
    void invalidIndexIsRebuiltFromAuthoritativeResourceFiles() throws Exception {
        createRegistry();
        ItemStack item = new ItemStack(Blocks.STONE);
        UUID id = QIOResourceTypeRegistry.INSTANCE.getOrTrackItem(mekanism.common.lib.inventory.HashedItem.create(item));
        QIOResourceTypeRegistry.INSTANCE.flush();
        File index = new File(worldDirectory, "mekanism/qio/resource_type_index.dat");
        NBTTagCompound invalid = new NBTTagCompound();
        invalid.setInteger("version", 999);
        try (java.io.OutputStream output = Files.newOutputStream(index.toPath())) {
            net.minecraft.nbt.CompressedStreamTools.writeCompressed(invalid, output);
        }

        QIOResourceTypeRegistry.INSTANCE.reset();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        assertEquals(id, QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(mekanism.common.lib.inventory.HashedItem.create(item)));
        QIOResourceTypeRegistry.INSTANCE.flush();
        NBTTagCompound rebuilt;
        try (java.io.InputStream input = Files.newInputStream(index.toPath())) {
            rebuilt = net.minecraft.nbt.CompressedStreamTools.readCompressed(input);
        }
        assertEquals(1, rebuilt.getInteger("version"));
    }

    @Test
    void oneDamagedResourceFileIsQuarantinedWithoutHidingHealthyTypes() throws Exception {
        createRegistry();
        ItemStack healthyItem = new ItemStack(Blocks.STONE);
        ItemStack damagedItem = new ItemStack(Blocks.DIRT);
        UUID healthy = QIOResourceTypeRegistry.INSTANCE.getOrTrackItem(mekanism.common.lib.inventory.HashedItem.create(healthyItem));
        UUID damaged = QIOResourceTypeRegistry.INSTANCE.getOrTrackItem(mekanism.common.lib.inventory.HashedItem.create(damagedItem));
        QIOResourceTypeRegistry.INSTANCE.flush();
        File damagedFile = new File(worldDirectory, "mekanism/qio/resource_types/" + damaged + ".dat");
        NBTTagCompound invalid = new NBTTagCompound();
        invalid.setInteger("version", 999);
        try (java.io.OutputStream output = Files.newOutputStream(damagedFile.toPath())) {
            net.minecraft.nbt.CompressedStreamTools.writeCompressed(invalid, output);
        }

        QIOResourceTypeRegistry.INSTANCE.reset();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        assertNotNull(QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(mekanism.common.lib.inventory.HashedItem.create(healthyItem)));
        assertEquals(healthy, QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(mekanism.common.lib.inventory.HashedItem.create(healthyItem)));
        assertNull(QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(damaged));
        assertTrue(QIOResourceTypeRegistry.INSTANCE.isDamaged(damaged));
        assertTrue(new File(damagedFile.getParentFile(), damagedFile.getName() + ".damaged").isFile());
    }

    @Test
    void restoredHealthyResourceOverridesOldQuarantineMarker() throws Exception {
        createRegistry();
        ItemStack item = new ItemStack(Blocks.STONE);
        UUID id = QIOResourceTypeRegistry.INSTANCE.getOrTrackItem(mekanism.common.lib.inventory.HashedItem.create(item));
        QIOResourceTypeRegistry.INSTANCE.flush();
        File resourceFile = new File(worldDirectory, "mekanism/qio/resource_types/" + id + ".dat");
        Files.copy(resourceFile.toPath(), new File(resourceFile.getParentFile(), resourceFile.getName() + ".damaged").toPath());

        QIOResourceTypeRegistry.INSTANCE.reset();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        assertTrue(!QIOResourceTypeRegistry.INSTANCE.isDamaged(id));
        assertEquals(id, QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(mekanism.common.lib.inventory.HashedItem.create(item)));
    }

    private void createRegistry() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-resource-registry-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
    }

    private static Fluid registerFluid() {
        Fluid fluid = new Fluid("qio_registry_fluid_" + UUID.randomUUID(),
              new ResourceLocation("minecraft", "blocks/water"), new ResourceLocation("minecraft", "blocks/water"));
        FluidRegistry.registerFluid(fluid);
        return fluid;
    }

    private static NBTTagCompound tag(String key, String value) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(key, value);
        return tag;
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

    private static final class CountingCanonicalFile extends File {

        private int canonicalizationCount;

        private CountingCanonicalFile(File file) {
            super(file.getPath());
        }

        @Override
        public File getCanonicalFile() throws java.io.IOException {
            canonicalizationCount++;
            return super.getCanonicalFile();
        }

        private int getCanonicalizationCount() {
            return canonicalizationCount;
        }
    }
}
