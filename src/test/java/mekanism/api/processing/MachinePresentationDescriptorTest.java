package mekanism.api.processing;

import mekanism.common.TestBootstrap;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MachinePresentationDescriptorTest {

    private static final ResourceLocation DEFAULT_PROVIDER =
          new ResourceLocation("test", "presentation_default");
    private static final ResourceLocation CUSTOM_PROVIDER =
          new ResourceLocation("test", "presentation_custom");
    private static Block presentationBlock;

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
        presentationBlock = new Block(Material.ROCK)
              .setRegistryName("test", "presentation_block");
    }

    @Test
    void descriptorRoundTripsTierNbtAndDefensivelyCopiesIt() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("tier", 3);
        MachinePresentationDescriptor descriptor = MachinePresentationDescriptor.of(
              "minecraft:diamond", 0, tag, "ultimate");
        tag.setInteger("tier", 0);

        MachinePresentationDescriptor restored = MachinePresentationDescriptor.read(
              descriptor.write());
        NBTTagCompound exposed = restored.getItemNbt();
        assertEquals(3, exposed.getInteger("tier"));
        exposed.setInteger("tier", 1);
        assertEquals(3, restored.getItemNbt().getInteger("tier"));
        assertEquals("ultimate", restored.getTypeDiscriminator());

        ItemStack stack = restored.createStack();
        assertFalse(stack.isEmpty());
        assertEquals(Items.DIAMOND, stack.getItem());
        assertEquals(3, stack.getTagCompound().getInteger("tier"));
    }

    @Test
    void restoredStackUsesPresentationNbtForItsDisplayName() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("title", "Ultimate Presentation");
        MachinePresentationDescriptor descriptor = MachinePresentationDescriptor.of(
              "minecraft:written_book", 0, tag);

        ItemStack stack = MachinePresentationDescriptor.read(descriptor.write())
              .createStack();

        assertEquals("Ultimate Presentation", stack.getDisplayName());
    }

    @Test
    void oversizedDeepAndSensitiveNbtIsRejected() {
        NBTTagCompound oversized = new NBTTagCompound();
        oversized.setByteArray("modelData",
              new byte[MachinePresentationDescriptor.MAX_NBT_BYTES + 1]);
        assertThrows(IllegalArgumentException.class, () ->
              MachinePresentationDescriptor.of("minecraft:diamond", 0, oversized));

        NBTTagCompound deep = new NBTTagCompound();
        NBTTagCompound cursor = deep;
        for (int depth = 0; depth <= MachinePresentationDescriptor.MAX_NBT_DEPTH; depth++) {
            NBTTagCompound child = new NBTTagCompound();
            cursor.setTag("model" + depth, child);
            cursor = child;
        }
        assertThrows(IllegalArgumentException.class, () ->
              MachinePresentationDescriptor.of("minecraft:diamond", 0, deep));

        NBTTagCompound inventory = new NBTTagCompound();
        inventory.setInteger("Items", 1);
        assertThrows(IllegalArgumentException.class, () ->
              MachinePresentationDescriptor.of("minecraft:diamond", 0, inventory));
    }

    @Test
    void providersDefaultToBlockIdentityAndInvalidCustomDataFallsBack() {
        MachineRecipeProviderRegistry.unregister(DEFAULT_PROVIDER);
        MachineRecipeProviderRegistry.unregister(CUSTOM_PROVIDER);
        try {
            FixedTile tile = new FixedTile(presentationBlock, 7);
            MachineRecipeProviderRegistry.register(DEFAULT_PROVIDER, FixedTile.class,
                  new MachineRecipeProvider<FixedTile>() { });
            MachineRecipeProviderRegistry.BoundProvider provider =
                  MachineRecipeProviderRegistry.find(tile);
            assertEquals("test:presentation_block",
                  provider.getPresentation().getItemId());
            assertEquals(7, provider.getPresentation().getItemMetadata());
            assertNull(provider.getPresentation().getItemNbt());

            MachineRecipeProviderRegistry.unregister(DEFAULT_PROVIDER);
            MachineRecipeProviderRegistry.register(CUSTOM_PROVIDER, FixedTile.class,
                  new MachineRecipeProvider<FixedTile>() {
                      @Override
                      public MachinePresentationDescriptor getPresentation(FixedTile ignored) {
                          throw new IllegalArgumentException("invalid addon presentation");
                      }
                  });
            provider = MachineRecipeProviderRegistry.find(tile);
            assertEquals("test:presentation_block",
                  provider.getPresentation().getItemId());
            assertEquals(7, provider.getPresentation().getItemMetadata());
        } finally {
            MachineRecipeProviderRegistry.unregister(DEFAULT_PROVIDER);
            MachineRecipeProviderRegistry.unregister(CUSTOM_PROVIDER);
        }
    }

    private static final class FixedTile extends TileEntity {

        private final Block block;
        private final int metadata;

        private FixedTile(Block block, int metadata) {
            this.block = block;
            this.metadata = metadata;
        }

        @Override
        public Block getBlockType() {
            return block;
        }

        @Override
        public int getBlockMetadata() {
            return metadata;
        }
    }
}
