package mekanism.qioprocessing.common.machine;

import mekanism.common.TestBootstrap;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.block.BlockMachine;
import mekanism.common.block.states.BlockStateMachine.MachineBlock;
import mekanism.common.tile.factory.TileEntityBasicFactory;
import mekanism.api.processing.MachinePresentationDescriptor;
import mekanism.api.processing.MachineRecipeProvider;
import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.api.processing.MachineRecipeRoute;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOAutomationRecipeProfileScopeTest {

    private static final ResourceLocation PROVIDER = new ResourceLocation("test", "shared");
    private static Block machineBlock;

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
        machineBlock = BlockMachine.getBlockMachine(MachineBlock.MACHINE_BLOCK_1)
              .setRegistryName("test", "machineblock");
    }

    @Test
    void machineTypeSeparatesMachinesRegisteredThroughOneProvider() {
        String enrichment = QIOAutomationRecipeProfileScope.resolve(
              new FixedBlockTile(machineBlock, 0), PROVIDER);
        String crusher = QIOAutomationRecipeProfileScope.resolve(
              new FixedBlockTile(machineBlock, 3), PROVIDER);

        assertNotEquals(enrichment, crusher);
        assertTrue(enrichment.endsWith("/enrichment_chamber"));
        assertTrue(crusher.endsWith("/crusher"));
    }

    @Test
    void factoryRecipeTypeIsPartOfTheStableScope() throws Exception {
        FixedFactory smelting = new FixedFactory();
        FixedFactory crushing = new FixedFactory();
        setRecipeType(smelting, RecipeType.SMELTING);
        setRecipeType(crushing, RecipeType.CRUSHING);

        String smeltingScope = QIOAutomationRecipeProfileScope.resolve(smelting, PROVIDER);
        String crushingScope = QIOAutomationRecipeProfileScope.resolve(crushing, PROVIDER);

        assertNotEquals(smeltingScope, crushingScope);
        assertTrue(smeltingScope.endsWith("/basic_factory/smelting"));
        assertTrue(crushingScope.endsWith("/basic_factory/crushing"));
    }

    @Test
    void cosmeticPresentationDoesNotChangeRecipeProfileScope() {
        ResourceLocation id = new ResourceLocation("test", "presentation_scope");
        MachineRecipeProviderRegistry.unregister(id);
        try {
            MachineRecipeProviderRegistry.register(id, PresentationTile.class,
                  new MachineRecipeProvider<PresentationTile>() {
                      @Override
                      public MachinePresentationDescriptor getPresentation(
                            PresentationTile tile) {
                          net.minecraft.nbt.NBTTagCompound tag =
                                new net.minecraft.nbt.NBTTagCompound();
                          tag.setInteger("tier", tile.tier);
                          return MachinePresentationDescriptor.of(
                                "minecraft:diamond", 0, tag);
                      }

                      @Override
                      public java.util.List<MachineRecipeRoute> getRecipeRoutes(
                            PresentationTile tile) {
                          return java.util.Collections.singletonList(
                                MachineRecipeRoute.builder("route:item_to_item")
                                      .recipeKey("test:stable_recipe")
                                      .inputItem("input", new ItemStack(Items.IRON_INGOT))
                                      .outputItem("output", new ItemStack(Items.GOLD_INGOT))
                                      .build());
                      }
                  });
            MachineRecipeProviderRegistry.BoundProvider basic =
                  MachineRecipeProviderRegistry.find(
                        new PresentationTile(machineBlock, 0));
            MachineRecipeProviderRegistry.BoundProvider ultimate =
                  MachineRecipeProviderRegistry.find(
                        new PresentationTile(machineBlock, 3));
            assertNotNull(basic);
            assertNotNull(ultimate);
            assertEquals(QIOAutomationRecipeProfileScope.resolve(basic),
                  QIOAutomationRecipeProfileScope.resolve(ultimate));
            assertNotEquals(basic.getPresentation().presentationKey(),
                  ultimate.getPresentation().presentationKey());
            assertEquals(basic.getRecipeRoutes().get(0).recipeKey(),
                  ultimate.getRecipeRoutes().get(0).recipeKey());
        } finally {
            MachineRecipeProviderRegistry.unregister(id);
        }
    }

    private static void setRecipeType(TileEntityBasicFactory factory, RecipeType type)
          throws Exception {
        Field field = mekanism.common.tile.factory.TileEntityFactory.class.getDeclaredField(
              "recipeType");
        field.setAccessible(true);
        field.set(factory, type);
    }

    private static class FixedBlockTile extends TileEntity {

        private final Block block;
        private final int metadata;

        private FixedBlockTile(Block block, int metadata) {
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

    private static final class FixedFactory extends TileEntityBasicFactory {

        @Override
        public Block getBlockType() {
            return machineBlock;
        }

        @Override
        public int getBlockMetadata() {
            return 5;
        }
    }

    private static final class PresentationTile extends FixedBlockTile {

        private final int tier;

        private PresentationTile(Block block, int tier) {
            super(block, 0);
            this.tier = tier;
        }
    }
}
