package mekanism.qioprocessing.common.inventory.container;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import mekanism.common.TestBootstrap;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfigurationMutation;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfigurationMutation.Action;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfigurationMutation.RecipeTarget;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchClosureMode;
import mekanism.qioprocessing.common.network.PacketQIOWorkbenchConfigurationData;
import mekanism.qioprocessing.common.network.PacketQIOWorkbenchConfigurationData.Status;
import mekanism.qioprocessing.common.network.PacketQIOWorkbenchConfigurationRequest;
import mekanism.qioprocessing.common.network.PacketQIOWorkbenchConfigurationRequest.Operation;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot.PageKind;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot.Ingredient;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot.Product;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot.Recipe;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchCopyPreview;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchClosurePreview;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOWorkbenchConfigurationProtocolTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void requestsResponsesSnapshotsAndMutationsRoundTrip() throws Exception {
        UUID player = UUID.randomUUID();
        UUID frequency = UUID.randomUUID();
        QIOProcessingTerminalSession session = session(player, frequency);
        QIOProcessingTerminalContainerState state = state(session);
        UUID requestId = UUID.randomUUID();

        roundTrip(PacketQIOWorkbenchConfigurationRequest.Message.products(12, state,
                    requestId, 32, 32, "diamond"),
              PacketQIOWorkbenchConfigurationRequest.Message::new,
              PacketQIOWorkbenchConfigurationRequest.Message::isValid);
        QIOWorkbenchConfigurationMutation mutation =
              QIOWorkbenchConfigurationMutation.resetAll();
        roundTrip(PacketQIOWorkbenchConfigurationRequest.Message.mutate(12, state,
                    requestId, 4, 7, mutation),
              PacketQIOWorkbenchConfigurationRequest.Message::new,
              PacketQIOWorkbenchConfigurationRequest.Message::isValid);
        assertEquals(mutation.getAction(), QIOWorkbenchConfigurationMutation.read(
              mutation.write()).getAction());
        QIOWorkbenchConfigurationMutation encoded =
              QIOWorkbenchConfigurationMutation.encodePattern(Arrays.asList(
                    new ItemStack(Items.IRON_INGOT), ItemStack.EMPTY, ItemStack.EMPTY,
                    ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                    ItemStack.EMPTY, ItemStack.EMPTY));
        QIOWorkbenchConfigurationMutation restoredEncoded =
              QIOWorkbenchConfigurationMutation.read(encoded.write());
        assertEquals(QIOWorkbenchConfigurationMutation.Action.ENCODE_PATTERN,
              restoredEncoded.getAction());
        assertEquals(Items.IRON_INGOT, restoredEncoded.getGrid().get(0).getItem());
        QIOWorkbenchConfigurationMutation batch =
              QIOWorkbenchConfigurationMutation.encodeTargets(Arrays.asList(
                    new ItemStack(Items.DIAMOND), new ItemStack(Items.DIAMOND),
                    new ItemStack(Items.EMERALD)));
        QIOWorkbenchConfigurationMutation restoredBatch =
              QIOWorkbenchConfigurationMutation.read(batch.write());
        assertEquals(QIOWorkbenchConfigurationMutation.Action.ENCODE_TARGETS,
              restoredBatch.getAction());
        assertEquals(2, restoredBatch.getTargets().size());
        QIOWorkbenchConfigurationMutation batchDeleteProducts =
              QIOWorkbenchConfigurationMutation.batchDeleteProducts(Arrays.asList(
                    hash('1'), hash('2'), hash('1')));
        QIOWorkbenchConfigurationMutation restoredBatchDeleteProducts =
              QIOWorkbenchConfigurationMutation.read(batchDeleteProducts.write());
        assertEquals(Action.BATCH_DELETE_PRODUCTS, restoredBatchDeleteProducts.getAction());
        assertEquals(2, restoredBatchDeleteProducts.getProductKeys().size());
        List<RecipeTarget> recipeTargets = Arrays.asList(new RecipeTarget("test:a", hash('3')),
              new RecipeTarget("test:b", hash('4')));
        QIOWorkbenchConfigurationMutation batchMove =
              QIOWorkbenchConfigurationMutation.batchMoveRecipes(hash('5'), recipeTargets,
                    -1, true);
        QIOWorkbenchConfigurationMutation restoredBatchMove =
              QIOWorkbenchConfigurationMutation.read(batchMove.write());
        assertEquals(Action.BATCH_MOVE_RECIPES, restoredBatchMove.getAction());
        assertEquals(2, restoredBatchMove.getRecipeTargets().size());
        assertEquals(0, restoredBatchMove.getTargetIndex());
        QIOWorkbenchConfigurationMutation absolute =
              QIOWorkbenchConfigurationMutation.moveCandidateToIndex(hash('a'),
                    "test:absolute", hash('b'), 3, hash('c'), 17);
        QIOWorkbenchConfigurationMutation restoredAbsolute =
              QIOWorkbenchConfigurationMutation.read(absolute.write());
        assertEquals(QIOWorkbenchConfigurationMutation.Action.MOVE_CANDIDATE_TO_INDEX,
              restoredAbsolute.getAction());
        assertEquals(17, restoredAbsolute.getTargetIndex());
        assertEquals(QIOWorkbenchConfigurationMutation.Action.SYNC_EQUIVALENT_CANDIDATES,
              QIOWorkbenchConfigurationMutation.read(
                    QIOWorkbenchConfigurationMutation.syncEquivalentCandidates(hash('a'),
                          "test:absolute", hash('b'), 3).write()).getAction());

        QIOWorkbenchConfigurationSnapshot snapshot = productSnapshot(4, 7);
        QIOWorkbenchConfigurationSnapshot restored =
              QIOWorkbenchConfigurationSnapshot.read(snapshot.write());
        assertEquals(snapshot.getConfigUUID(), restored.getConfigUUID());
        assertEquals(snapshot.getProducts().get(0).getProductKey(),
              restored.getProducts().get(0).getProductKey());
        roundTrip(PacketQIOWorkbenchConfigurationData.Message.create(12, session,
                    requestId, Operation.PRODUCTS, Status.OK, snapshot, null),
              PacketQIOWorkbenchConfigurationData.Message::new,
              PacketQIOWorkbenchConfigurationData.Message::isValid);

        QIOWorkbenchCopyPreview preview = new QIOWorkbenchCopyPreview(
              UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
              snapshot.getConfigUUID(), 8, 4, hash('d'), "source", 2, 3, 4);
        assertEquals(preview.getConfirmationNonce(), QIOWorkbenchCopyPreview.read(
              preview.write()).getConfirmationNonce());
        roundTrip(PacketQIOWorkbenchConfigurationData.Message.create(12, session,
                    requestId, Operation.COPY_PREVIEW, Status.READY, null, preview),
              PacketQIOWorkbenchConfigurationData.Message::new,
              PacketQIOWorkbenchConfigurationData.Message::isValid);

        roundTrip(PacketQIOWorkbenchConfigurationRequest.Message.closurePreview(12, state,
                    requestId, 4, 7, encoded, QIOWorkbenchClosureMode.PREFERRED, true),
              PacketQIOWorkbenchConfigurationRequest.Message::new,
              PacketQIOWorkbenchConfigurationRequest.Message::isValid);
        UUID confirmationNonce = UUID.randomUUID();
        roundTrip(PacketQIOWorkbenchConfigurationRequest.Message.closureConfirm(12, state,
                    requestId, confirmationNonce),
              PacketQIOWorkbenchConfigurationRequest.Message::new,
              PacketQIOWorkbenchConfigurationRequest.Message::isValid);
        QIOWorkbenchClosurePreview closurePreview = new QIOWorkbenchClosurePreview(
              confirmationNonce, 1, 4, 6, 2, 3, 1, 1, false,
              Collections.singletonList("item:a -> item:b -> item:a"));
        QIOWorkbenchClosurePreview restoredClosure = QIOWorkbenchClosurePreview.read(
              closurePreview.write());
        assertEquals(closurePreview.getConfirmationNonce(),
              restoredClosure.getConfirmationNonce());
        assertEquals(closurePreview.getCyclePaths(), restoredClosure.getCyclePaths());
        assertEquals(closurePreview.getSkippedCyclicRecipeCount(),
              restoredClosure.getSkippedCyclicRecipeCount());
        roundTrip(PacketQIOWorkbenchConfigurationData.Message.create(12, session,
                    requestId, Operation.CLOSURE_PREVIEW, Status.READY, null, null,
                    closurePreview),
              PacketQIOWorkbenchConfigurationData.Message::new,
              PacketQIOWorkbenchConfigurationData.Message::isValid);
    }

    @Test
    void recipeSnapshotPreservesDirectFluidIngredientDisplay() throws Exception {
        PortableResourceDescriptor water = PortableResourceDescriptor.fluid(
              new FluidStack(FluidRegistry.WATER, 1_000));
        List<Ingredient> ingredients = new ArrayList<>(9);
        ingredients.add(new Ingredient(0, 2, 2, new ItemStack(Items.WATER_BUCKET),
              water, 1_000));
        for (int slot = 1; slot < 9; slot++) {
            ingredients.add(new Ingredient(slot, 0, 0, ItemStack.EMPTY, null, 0));
        }
        Recipe recipe = new Recipe("test:direct_fluid", hash('b'),
              PortableResourceDescriptor.item(new ItemStack(Items.DIAMOND)), 1, true,
              0, true, 1, 1, ingredients);
        QIOWorkbenchConfigurationSnapshot snapshot =
              new QIOWorkbenchConfigurationSnapshot(PageKind.RECIPES,
                    UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    UUID.fromString("22222222-2222-2222-2222-222222222222"),
                    1, 2, true, 0, 1, "", hash('a'), "", "", -1,
                    Collections.emptyList(), Collections.singletonList(recipe),
                    Collections.emptyList());

        Ingredient restored = QIOWorkbenchConfigurationSnapshot.read(snapshot.write())
              .getRecipes().get(0).getIngredients().get(0);
        assertTrue(restored.isVirtualFluid());
        assertEquals(water, restored.getVirtualFluid());
        assertEquals(1_000, restored.getVirtualFluidAmount());
        assertEquals(Items.WATER_BUCKET, restored.getRepresentative().getItem());
    }

    @Test
    void staleClientResponsesCannotReplaceTheLatestPage() {
        QIOWorkbenchConfigurationClientCache cache =
              new QIOWorkbenchConfigurationClientCache();
        UUID stale = UUID.randomUUID();
        UUID current = UUID.randomUUID();
        cache.expectPage(PageKind.PRODUCTS, stale);
        cache.expectPage(PageKind.PRODUCTS, current);
        assertFalse(cache.applyPage(stale, Status.OK, productSnapshot(1, 2)));
        assertTrue(cache.applyPage(current, Status.OK, productSnapshot(1, 2)));
        assertEquals(1, cache.getProductGeneration());
        assertTrue(cache.getProductPage() != null);

        UUID mutation = UUID.randomUUID();
        cache.expectMutation(mutation);
        assertTrue(cache.applyMutation(mutation, Status.APPLIED));
        assertNull(cache.getProductPage());
        assertEquals(Status.APPLIED, cache.getMutationStatus());

        UUID copy = UUID.randomUUID();
        cache.expectCopy(copy);
        assertFalse(cache.applyCopy(UUID.randomUUID(), Status.APPLIED, null));
        assertTrue(cache.applyCopy(copy, Status.APPLIED, null));
        assertNull(cache.getProductPage());

        UUID closure = UUID.randomUUID();
        cache.expectClosure(closure);
        QIOWorkbenchClosurePreview closurePreview = new QIOWorkbenchClosurePreview(
              UUID.randomUUID(), 1, 2, 3, 4, 5, 0, 0, false,
              Collections.emptyList());
        assertFalse(cache.applyClosure(UUID.randomUUID(), Status.READY,
              closurePreview));
        assertTrue(cache.applyClosure(closure, Status.READY, closurePreview));
        assertEquals(closurePreview.getConfirmationNonce(),
              cache.getClosurePreview().getConfirmationNonce());

        assertTrue(cache.addBatchTarget(new ItemStack(Items.DIAMOND)));
        assertFalse(cache.addBatchTarget(new ItemStack(Items.DIAMOND)));
        assertTrue(cache.addBatchTarget(new ItemStack(Items.EMERALD)));
        assertEquals(2, cache.getBatchTargetCount());
        assertTrue(cache.removeBatchTarget(0));
        assertEquals(Items.EMERALD, cache.getBatchTarget(0).getItem());
    }

    @Test
    void workbenchClientCacheKeepsSparseProductAndRecipePages() {
        QIOWorkbenchConfigurationClientCache cache =
              new QIOWorkbenchConfigurationClientCache();
        UUID firstProductRequest = UUID.randomUUID();
        cache.expectPage(PageKind.PRODUCTS, firstProductRequest);
        assertTrue(cache.applyPage(firstProductRequest, Status.OK,
              productPage(0, 2, Items.DIAMOND, '1')));
        long productDirectory = cache.getProductDirectoryGeneration();

        UUID secondProductRequest = UUID.randomUUID();
        cache.expectPage(PageKind.PRODUCTS, secondProductRequest);
        assertTrue(cache.applyPage(secondProductRequest, Status.OK,
              productPage(1, 2, Items.EMERALD, '2')));
        assertEquals(productDirectory, cache.getProductDirectoryGeneration());
        assertTrue(cache.isProductPageLoaded(0));
        assertTrue(cache.isProductPageLoaded(1));
        assertEquals(Items.DIAMOND, cache.getProduct(0).getOutput().resolveItem().getItem());
        assertEquals(Items.EMERALD, cache.getProduct(1).getOutput().resolveItem().getItem());
        assertEquals(0, cache.findProductIndex(hash('1')));
        assertEquals(1, cache.findProductIndex(hash('2')));
        assertEquals(-1, cache.findProductIndex(hash('9')));

        UUID firstRecipeRequest = UUID.randomUUID();
        cache.expectPage(PageKind.RECIPES, firstRecipeRequest);
        assertTrue(cache.applyPage(firstRecipeRequest, Status.OK,
              recipePage(0, 2, "test:first", '3')));
        long recipeDirectory = cache.getRecipeDirectoryGeneration();
        UUID secondRecipeRequest = UUID.randomUUID();
        cache.expectPage(PageKind.RECIPES, secondRecipeRequest);
        assertTrue(cache.applyPage(secondRecipeRequest, Status.OK,
              recipePage(1, 2, "test:second", '4')));
        assertEquals(recipeDirectory, cache.getRecipeDirectoryGeneration());
        assertEquals("test:first", cache.getRecipe(0).getRecipeId());
        assertEquals("test:second", cache.getRecipe(1).getRecipeId());
    }

    @Test
    void deleteProductMutationRoundTripsWithAProductOnlyShape() throws Exception {
        QIOWorkbenchConfigurationMutation mutation =
              QIOWorkbenchConfigurationMutation.deleteProduct(hash('a'));
        QIOWorkbenchConfigurationMutation restored =
              QIOWorkbenchConfigurationMutation.read(mutation.write());
        assertEquals(Action.DELETE_PRODUCT, restored.getAction());
        assertEquals(hash('a'), restored.getProductKey());
    }

    @Test
    void boundedSparseCacheReloadsAnEvictedVisiblePage() {
        QIOWorkbenchConfigurationClientCache cache =
              new QIOWorkbenchConfigurationClientCache();
        for (int offset = 0; offset < 9; offset++) {
            UUID request = UUID.randomUUID();
            cache.expectPage(PageKind.PRODUCTS, request);
            assertTrue(cache.applyPage(request, Status.OK, productPage(offset, 9,
                  Items.DIAMOND, Character.forDigit(offset, 16))));
        }
        assertFalse(cache.isProductPageLoaded(0));
        assertTrue(cache.isProductPageLoaded(8));

        UUID reload = UUID.randomUUID();
        cache.expectPage(PageKind.PRODUCTS, reload);
        assertTrue(cache.applyPage(reload, Status.OK,
              productPage(0, 9, Items.DIAMOND, '0')));
        assertTrue(cache.isProductPageLoaded(0));
        assertEquals(Items.DIAMOND, cache.getProduct(0).getOutput().resolveItem().getItem());

        UUID unavailable = UUID.randomUUID();
        cache.expectPage(PageKind.PRODUCTS, unavailable);
        assertTrue(cache.applyUnavailable(Operation.PRODUCTS, unavailable,
              Status.NOT_FOUND));
        assertEquals(0, cache.getProductTotalSize());
        assertFalse(cache.isProductPageLoaded(0));
    }

    @Test
    void malformedAndOldProtocolPayloadsAreRejected() {
        NBTTagCompound oldMutation = QIOWorkbenchConfigurationMutation.resetAll().write();
        oldMutation.setInteger("schema", 0);
        assertThrows(QIOProcessingDataException.class, () ->
              QIOWorkbenchConfigurationMutation.read(oldMutation));

        NBTTagCompound incompleteProduct = productSnapshot(1, 2).write();
        incompleteProduct.getTagList("products", 10).getCompoundTagAt(0)
              .removeTag("enabledRecipeCount");
        assertThrows(QIOProcessingDataException.class, () ->
              QIOWorkbenchConfigurationSnapshot.read(incompleteProduct));

        NBTTagCompound wrongPageType = productSnapshot(1, 2).write();
        NBTTagList strings = new NBTTagList();
        strings.appendTag(new NBTTagString("not-a-product"));
        wrongPageType.setTag("products", strings);
        assertThrows(QIOProcessingDataException.class, () ->
              QIOWorkbenchConfigurationSnapshot.read(wrongPageType));

        ByteBuf truncated = Unpooled.buffer();
        try {
            truncated.writeInt(12);
            PacketQIOWorkbenchConfigurationRequest.Message decoded =
                  new PacketQIOWorkbenchConfigurationRequest.Message();
            decoded.fromBytes(truncated);
            assertFalse(decoded.isValid());
        } finally {
            truncated.release();
        }
    }

    private static QIOWorkbenchConfigurationSnapshot productSnapshot(long revision,
          long catalogRevision) {
        PortableResourceDescriptor diamond = PortableResourceDescriptor.item(
              new ItemStack(Items.DIAMOND));
        Product product = new Product(hash('1'), diamond, 2, 2);
        return new QIOWorkbenchConfigurationSnapshot(PageKind.PRODUCTS,
              UUID.fromString("11111111-1111-1111-1111-111111111111"),
              UUID.fromString("22222222-2222-2222-2222-222222222222"),
              revision, catalogRevision, true, 0, 1, "", "", "", "", -1,
              Collections.singletonList(product), Collections.emptyList(),
              Collections.emptyList());
    }

    private static QIOWorkbenchConfigurationSnapshot productPage(int offset, int total,
          net.minecraft.item.Item item, char key) {
        Product product = new Product(hash(key), PortableResourceDescriptor.item(
              new ItemStack(item)), 1, 1);
        return new QIOWorkbenchConfigurationSnapshot(PageKind.PRODUCTS,
              UUID.fromString("11111111-1111-1111-1111-111111111111"),
              UUID.fromString("22222222-2222-2222-2222-222222222222"),
              1, 2, true, offset, total, "", "", "", "", -1,
              Collections.singletonList(product), Collections.emptyList(),
              Collections.emptyList());
    }

    private static QIOWorkbenchConfigurationSnapshot recipePage(int offset, int total,
          String recipeId, char signature) {
        List<Ingredient> ingredients = new ArrayList<>(9);
        for (int slot = 0; slot < 9; slot++) {
            ingredients.add(new Ingredient(slot, 0, 0, ItemStack.EMPTY, null, 0));
        }
        Recipe recipe = new Recipe(recipeId, hash(signature),
              PortableResourceDescriptor.item(new ItemStack(Items.DIAMOND)), 1, true,
              offset, true, 1, 1, ingredients);
        return new QIOWorkbenchConfigurationSnapshot(PageKind.RECIPES,
              UUID.fromString("11111111-1111-1111-1111-111111111111"),
              UUID.fromString("22222222-2222-2222-2222-222222222222"),
              1, 2, true, offset, total, "", hash('a'), "", "", -1,
              Collections.emptyList(), Collections.singletonList(recipe),
              Collections.emptyList());
    }

    private static QIOProcessingTerminalSession session(UUID player, UUID frequency) {
        return new QIOProcessingTerminalSession(player,
              QIOProcessingTerminalSession.TargetKind.BLOCK,
              QIOProcessingTerminalType.MANAGEMENT, UUID.randomUUID(), 3,
              frequency, 5);
    }

    private static QIOProcessingTerminalContainerState state(
          QIOProcessingTerminalSession session) {
        QIOProcessingTerminalContainerState state =
              new QIOProcessingTerminalContainerState(new TestContainer(), () -> null);
        state.read(QIOProcessingTerminalContainerState.write(session));
        assertTrue(state.isValid());
        return state;
    }

    private static <T extends IMessage> void roundTrip(T original, Supplier<T> factory,
          Predicate<T> valid) {
        ByteBuf encoded = Unpooled.buffer();
        ByteBuf reencoded = Unpooled.buffer();
        try {
            original.toBytes(encoded);
            byte[] expected = ByteBufUtil.getBytes(encoded, encoded.readerIndex(),
                  encoded.readableBytes());
            T decoded = factory.get();
            decoded.fromBytes(encoded);
            assertTrue(valid.test(decoded));
            decoded.toBytes(reencoded);
            assertArrayEquals(expected, ByteBufUtil.getBytes(reencoded,
                  reencoded.readerIndex(), reencoded.readableBytes()));
        } finally {
            encoded.release();
            reencoded.release();
        }
    }

    private static String hash(char value) {
        char[] characters = new char[64];
        Arrays.fill(characters, value);
        return new String(characters);
    }

    private static final class TestContainer extends MekanismContainer {

        private TestContainer() {
            super(null);
        }
    }
}
