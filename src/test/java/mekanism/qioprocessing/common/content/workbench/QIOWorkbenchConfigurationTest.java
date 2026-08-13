package mekanism.qioprocessing.common.content.workbench;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOWorkbenchConfigurationTest {

    private static final String PRODUCT = hash('1');
    private static final String SIGNATURE = hash('2');
    private static final String CANDIDATE_A = hash('a');
    private static final String CANDIDATE_B = hash('b');
    private static final String CANDIDATE_C = hash('c');
    private static final List<String> RECIPES = Arrays.asList(
          "test:alpha", "test:beta", "test:gamma");
    private static final List<String> CANDIDATES = Arrays.asList(
          CANDIDATE_A, CANDIDATE_B, CANDIDATE_C);

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void sparseOrdersAndCandidateGuardRoundTripExactly() throws Exception {
        QIOWorkbenchConfiguration configuration = new QIOWorkbenchConfiguration();
        UUID configUUID = configuration.getConfigUUID();

        assertTrue(configuration.moveRecipe(PRODUCT, RECIPES, "test:gamma", -1,
              true));
        assertEquals(Arrays.asList("test:gamma", "test:alpha", "test:beta"),
              configuration.orderedRecipes(PRODUCT, RECIPES));
        assertTrue(configuration.setRecipeEnabled("test:beta", SIGNATURE, false));
        assertFalse(configuration.isRecipeEnabled("test:beta", SIGNATURE));
        assertTrue(configuration.moveCandidate("test:beta", SIGNATURE, 0,
              CANDIDATES, CANDIDATE_C, -1, true));
        assertEquals(Arrays.asList(CANDIDATE_C, CANDIDATE_A, CANDIDATE_B),
              configuration.orderedCandidates("test:beta", SIGNATURE, 0,
                    CANDIDATES));
        assertTrue(configuration.setCandidateEnabled("test:beta", SIGNATURE, 0,
              CANDIDATES, CANDIDATE_A, false));
        assertTrue(configuration.setCandidateEnabled("test:beta", SIGNATURE, 0,
              CANDIDATES, CANDIDATE_B, false));
        assertThrows(QIOWorkbenchConfiguration.LastCandidateException.class, () ->
              configuration.setCandidateEnabled("test:beta", SIGNATURE, 0,
                    CANDIDATES, CANDIDATE_C, false));
        assertEquals(Arrays.asList(CANDIDATE_C), configuration.orderedEnabledCandidates(
              "test:beta", SIGNATURE, 0, CANDIDATES));
        assertEquals(1, configuration.getProductOverrideCount());
        assertEquals(1, configuration.getRecipeOverrideCount());
        assertEquals(1, configuration.getIngredientOverrideCount());

        QIOWorkbenchConfiguration restored = QIOWorkbenchConfiguration.read(
              configuration.write());
        assertEquals(configUUID, restored.getConfigUUID());
        assertEquals(configUUID, restored.getOriginUUID());
        assertEquals(configuration.getRevision(), restored.getRevision());
        assertEquals(configuration.contentDigest(), restored.contentDigest());
        assertEquals(configuration.orderedRecipes(PRODUCT, RECIPES),
              restored.orderedRecipes(PRODUCT, RECIPES));
        assertEquals(configuration.orderedEnabledCandidates("test:beta", SIGNATURE,
              0, CANDIDATES), restored.orderedEnabledCandidates("test:beta",
              SIGNATURE, 0, CANDIDATES));
    }

    @Test
    void absoluteCandidateMoveAndEquivalentSynchronizationUseOneRevision() {
        QIOWorkbenchConfiguration configuration = new QIOWorkbenchConfiguration();
        assertTrue(configuration.moveCandidateToIndex("test:sync", SIGNATURE, 0,
              CANDIDATES, CANDIDATE_C, 1));
        assertEquals(Arrays.asList(CANDIDATE_A, CANDIDATE_C, CANDIDATE_B),
              configuration.orderedCandidates("test:sync", SIGNATURE, 0, CANDIDATES));
        assertTrue(configuration.setCandidateEnabled("test:sync", SIGNATURE, 0,
              CANDIDATES, CANDIDATE_A, false));
        assertTrue(configuration.moveCandidateToIndex("test:sync", SIGNATURE, 1,
              CANDIDATES, CANDIDATE_B, 0));

        Map<Integer, List<String>> equivalent = new LinkedHashMap<>();
        equivalent.put(0, CANDIDATES);
        equivalent.put(1, CANDIDATES);
        equivalent.put(2, Arrays.asList(CANDIDATE_C, CANDIDATE_B, CANDIDATE_A));
        long before = configuration.getRevision();
        assertTrue(configuration.synchronizeEquivalentCandidates("test:sync", SIGNATURE,
              0, equivalent));
        assertEquals(before + 1, configuration.getRevision());
        for (int slot = 1; slot <= 2; slot++) {
            assertEquals(Arrays.asList(CANDIDATE_A, CANDIDATE_C, CANDIDATE_B),
                  configuration.orderedCandidates("test:sync", SIGNATURE, slot,
                        equivalent.get(slot)));
            assertFalse(configuration.isCandidateEnabled("test:sync", SIGNATURE, slot,
                  CANDIDATE_A));
        }
    }

    @Test
    void copyKeepsTargetIdentityAndRejectsAnIdenticalRepeatedImport() throws Exception {
        QIOWorkbenchConfiguration source = new QIOWorkbenchConfiguration();
        source.moveRecipe(PRODUCT, RECIPES, "test:gamma", -1, true);
        source.setRecipeEnabled("test:beta", SIGNATURE, false);

        QIOWorkbenchConfiguration target = new QIOWorkbenchConfiguration();
        UUID targetUUID = target.getConfigUUID();
        target.setRecipeEnabled("test:alpha", SIGNATURE, false);
        long targetRevision = target.getRevision();

        assertTrue(target.replaceFrom(source));
        assertEquals(targetUUID, target.getConfigUUID());
        assertNotEquals(source.getConfigUUID(), target.getConfigUUID());
        assertEquals(source.getOriginUUID(), target.getOriginUUID());
        assertEquals(targetRevision + 1, target.getRevision());
        assertEquals(source.contentDigest(), target.contentDigest());
        assertTrue(target.importedFrom(source.getConfigUUID(), source.getRevision(),
              source.contentDigest()));
        assertFalse(target.replaceFrom(source));

        QIOWorkbenchConfiguration restored = QIOWorkbenchConfiguration.read(target.write());
        assertEquals(targetUUID, restored.getConfigUUID());
        assertEquals(source.getOriginUUID(), restored.getOriginUUID());
        assertTrue(restored.importedFrom(source.getConfigUUID(), source.getRevision(),
              source.contentDigest()));

        source.setRecipeEnabled("test:alpha", SIGNATURE, false);
        assertTrue(restored.replaceFrom(source));
        assertEquals(targetUUID, restored.getConfigUUID());
    }

    @Test
    void staleSignaturesAreIgnoredAndCurrentSchemaIsStrict() {
        QIOWorkbenchConfiguration configuration = new QIOWorkbenchConfiguration();
        configuration.setRecipeEnabled("test:alpha", SIGNATURE, false);
        assertTrue(configuration.isRecipeEnabled("test:alpha", hash('3')));

        NBTTagCompound oldSchema = configuration.write();
        oldSchema.setInteger("schema", 0);
        assertThrows(QIOProcessingDataException.class, () ->
              QIOWorkbenchConfiguration.read(oldSchema));

        NBTTagCompound negativeRevision = configuration.write();
        negativeRevision.setLong("revision", -1);
        assertThrows(QIOProcessingDataException.class, () ->
              QIOWorkbenchConfiguration.read(negativeRevision));

        NBTTagCompound negativePatternRevision = configuration.write();
        negativePatternRevision.setLong("patternRevision", -1);
        assertThrows(QIOProcessingDataException.class, () ->
              QIOWorkbenchConfiguration.read(negativePatternRevision));

        NBTTagCompound wrongListType = new QIOWorkbenchConfiguration().write();
        NBTTagList strings = new NBTTagList();
        strings.appendTag(new NBTTagString("not-a-recipe-override"));
        wrongListType.setTag("recipeOverrides", strings);
        assertThrows(QIOProcessingDataException.class, () ->
              QIOWorkbenchConfiguration.read(wrongListType));

        QIOWorkbenchConfiguration disabledDefaultOrder =
              new QIOWorkbenchConfiguration();
        assertTrue(disabledDefaultOrder.setCandidateEnabled("test:alpha", SIGNATURE,
              0, CANDIDATES, CANDIDATE_A, false));
        try {
            QIOWorkbenchConfiguration restored = QIOWorkbenchConfiguration.read(
                  disabledDefaultOrder.write());
            assertEquals(Arrays.asList(CANDIDATE_B, CANDIDATE_C),
                  restored.orderedEnabledCandidates("test:alpha", SIGNATURE, 0,
                        CANDIDATES));
        } catch (QIOProcessingDataException e) {
            throw new AssertionError(e);
        }

        NBTTagCompound disablesEveryCandidate = disabledDefaultOrder.write();
        NBTTagCompound ingredient = disablesEveryCandidate.getTagList(
              "recipeOverrides", 10).getCompoundTagAt(0).getTagList(
                    "ingredients", 10).getCompoundTagAt(0);
        ingredient.setTag("disabledCandidates", ingredient.getTagList(
              "candidateOrder", 8).copy());
        assertThrows(QIOProcessingDataException.class, () ->
              QIOWorkbenchConfiguration.read(disablesEveryCandidate));
    }

    @Test
    void encodedPatternsPersistAndCopyWithoutReplacingTheTargetConfigurationIdentity()
          throws Exception {
        QIOWorkbenchConfiguration source = new QIOWorkbenchConfiguration();
        UUID patternUUID = UUID.randomUUID();
        List<ItemStack> grid = new ArrayList<>(9);
        grid.add(new ItemStack(Items.IRON_INGOT));
        while (grid.size() < 9) grid.add(ItemStack.EMPTY);
        QIOWorkbenchConfiguration.EncodedPattern pattern =
              new QIOWorkbenchConfiguration.EncodedPattern(patternUUID,
                    new ResourceLocation("test", "manual_pattern"), SIGNATURE,
                    PortableResourceDescriptor.named(PortableResourceDescriptor.Kind.ITEM,
                          "minecraft:diamond", 0, null), 1, grid);

        assertTrue(source.putEncodedPattern(pattern));
        assertEquals(1, source.getPatternRevision());
        assertFalse(source.putEncodedPattern(new QIOWorkbenchConfiguration.EncodedPattern(
              UUID.randomUUID(), pattern.getRecipeId(), SIGNATURE, pattern.getOutput(), 1,
              grid)));
        source.setRecipeEnabled(pattern.getRecipeId().toString(), SIGNATURE, false);
        assertEquals(1, source.getPatternRevision());
        QIOWorkbenchConfiguration restored = QIOWorkbenchConfiguration.read(source.write());
        assertEquals(1, restored.getEncodedPatternCount());
        assertEquals(1, restored.getPatternRevision());
        assertEquals(patternUUID, restored.getEncodedPatterns().get(0).getPatternUUID());
        assertEquals(Items.IRON_INGOT, restored.getEncodedPatterns().get(0).getGrid()
              .get(0).getItem());

        QIOWorkbenchConfiguration target = new QIOWorkbenchConfiguration();
        UUID targetConfigUUID = target.getConfigUUID();
        assertTrue(target.replaceFrom(restored));
        assertEquals(targetConfigUUID, target.getConfigUUID());
        assertEquals(patternUUID, target.getEncodedPatterns().get(0).getPatternUUID());
        assertTrue(target.resetAll());
        assertEquals(0, target.getEncodedPatternCount());
    }

    @Test
    void batchPatternInsertIsAtomicAndNeverReplacesExistingRecipePreferences() {
        QIOWorkbenchConfiguration configuration = new QIOWorkbenchConfiguration();
        List<ItemStack> ironGrid = grid(new ItemStack(Items.IRON_INGOT));
        QIOWorkbenchConfiguration.EncodedPattern existing = pattern("alpha", SIGNATURE,
              new ItemStack(Items.DIAMOND), ironGrid);
        assertTrue(configuration.putEncodedPattern(existing));
        assertTrue(configuration.setRecipeEnabled(existing.getRecipeId().toString(),
              SIGNATURE, false));
        long beforeRevision = configuration.getRevision();
        long beforePatternRevision = configuration.getPatternRevision();

        QIOWorkbenchConfiguration.EncodedPattern changedExisting = pattern("alpha",
              hash('3'), new ItemStack(Items.EMERALD),
              grid(new ItemStack(Items.GOLD_INGOT)));
        QIOWorkbenchConfiguration.EncodedPattern added = pattern("beta", hash('4'),
              new ItemStack(Items.EMERALD), grid(new ItemStack(Items.REDSTONE)));
        assertEquals(1, configuration.putEncodedPatternsIfAbsent(Arrays.asList(
              changedExisting, added)));
        assertEquals(beforeRevision + 1, configuration.getRevision());
        assertEquals(beforePatternRevision + 1, configuration.getPatternRevision());
        assertEquals(2, configuration.getEncodedPatternCount());
        assertEquals(existing.getOutput(), configuration.getEncodedPattern(
              existing.getRecipeId().toString()).getOutput());
        assertFalse(configuration.isRecipeEnabled(existing.getRecipeId().toString(),
              SIGNATURE));
    }

    @Test
    void singleRootClosureReplacesOnlyRootAndCommitsDependenciesAsOneRevision() {
        QIOWorkbenchConfiguration configuration = new QIOWorkbenchConfiguration();
        QIOWorkbenchConfiguration.EncodedPattern oldRoot = pattern("closure_root",
              SIGNATURE, new ItemStack(Items.DIAMOND),
              grid(new ItemStack(Items.IRON_INGOT)));
        QIOWorkbenchConfiguration.EncodedPattern existingDependency = pattern(
              "closure_existing", hash('2'), new ItemStack(Items.IRON_INGOT),
              grid(new ItemStack(Items.COAL)));
        assertEquals(2, configuration.putEncodedPatternsIfAbsent(
              Arrays.asList(oldRoot, existingDependency)));
        assertTrue(configuration.setRecipeEnabled(oldRoot.getRecipeId().toString(),
              oldRoot.getRecipeSignature(), false));
        assertTrue(configuration.setRecipeEnabled(
              existingDependency.getRecipeId().toString(),
              existingDependency.getRecipeSignature(), false));
        long revision = configuration.getRevision();
        long patternRevision = configuration.getPatternRevision();

        QIOWorkbenchConfiguration.EncodedPattern replacement = pattern("closure_root",
              hash('3'), new ItemStack(Items.EMERALD),
              grid(new ItemStack(Items.GOLD_INGOT)));
        QIOWorkbenchConfiguration.EncodedPattern changedDependency = pattern(
              "closure_existing", hash('4'), new ItemStack(Items.REDSTONE),
              grid(new ItemStack(Items.GOLD_NUGGET)));
        QIOWorkbenchConfiguration.EncodedPattern addedDependency = pattern(
              "closure_added", hash('5'), new ItemStack(Items.GOLD_INGOT),
              grid(new ItemStack(Items.GOLD_NUGGET)));

        assertTrue(configuration.applyEncodedClosure(replacement, Arrays.asList(
              replacement, changedDependency, addedDependency)));

        assertEquals(revision + 1, configuration.getRevision());
        assertEquals(patternRevision + 1, configuration.getPatternRevision());
        assertEquals(3, configuration.getEncodedPatternCount());
        assertEquals(replacement.getOutput(), configuration.getEncodedPattern(
              replacement.getRecipeId().toString()).getOutput());
        assertEquals(existingDependency.getOutput(), configuration.getEncodedPattern(
              existingDependency.getRecipeId().toString()).getOutput());
        assertTrue(configuration.isRecipeEnabled(replacement.getRecipeId().toString(),
              replacement.getRecipeSignature()));
        assertFalse(configuration.isRecipeEnabled(
              existingDependency.getRecipeId().toString(),
              existingDependency.getRecipeSignature()));
    }

    @Test
    void deletingAProductRemovesAllOfItsPatternsAsOneRevision() {
        QIOWorkbenchConfiguration configuration = new QIOWorkbenchConfiguration();
        QIOWorkbenchConfiguration.EncodedPattern first = pattern("diamond_a", SIGNATURE,
              new ItemStack(Items.DIAMOND), grid(new ItemStack(Items.IRON_INGOT)));
        QIOWorkbenchConfiguration.EncodedPattern second = pattern("diamond_b", hash('2'),
              new ItemStack(Items.DIAMOND), grid(new ItemStack(Items.GOLD_INGOT)));
        QIOWorkbenchConfiguration.EncodedPattern retained = pattern("emerald", hash('3'),
              new ItemStack(Items.EMERALD), grid(new ItemStack(Items.REDSTONE)));
        assertEquals(3, configuration.putEncodedPatternsIfAbsent(
              Arrays.asList(first, second, retained)));
        long revision = configuration.getRevision();
        long patternRevision = configuration.getPatternRevision();

        assertTrue(configuration.removeEncodedProduct(hash('a'), first.getOutput()));

        assertEquals(revision + 1, configuration.getRevision());
        assertEquals(patternRevision + 1, configuration.getPatternRevision());
        assertEquals(1, configuration.getEncodedPatternCount());
        assertEquals(retained.getRecipeId(),
              configuration.getEncodedPatterns().get(0).getRecipeId());
    }

    @Test
    void batchRouteOperationsPreserveSelectionOrderAndUseOneRevision() {
        QIOWorkbenchConfiguration configuration = new QIOWorkbenchConfiguration();
        List<String> recipes = Arrays.asList("test:a", "test:b", "test:c", "test:d");
        Map<String, String> signatures = new LinkedHashMap<>();
        signatures.put("test:b", SIGNATURE);
        signatures.put("test:d", hash('4'));
        long before = configuration.getRevision();

        assertTrue(configuration.moveRecipes(PRODUCT, recipes, signatures.keySet(), -1,
              false));
        assertEquals(Arrays.asList("test:b", "test:a", "test:d", "test:c"),
              configuration.orderedRecipes(PRODUCT, recipes));
        assertEquals(before + 1, configuration.getRevision());

        assertTrue(configuration.setRecipesEnabled(signatures, false));
        assertEquals(before + 2, configuration.getRevision());
        assertFalse(configuration.isRecipeEnabled("test:b", SIGNATURE));
        assertFalse(configuration.isRecipeEnabled("test:d", hash('4')));
    }

    @Test
    void batchEncodedPatternRemovalUsesOneRevisionAndOnePatternRevision() {
        QIOWorkbenchConfiguration configuration = new QIOWorkbenchConfiguration();
        QIOWorkbenchConfiguration.EncodedPattern first = pattern("batch_a", SIGNATURE,
              new ItemStack(Items.DIAMOND), grid(new ItemStack(Items.IRON_INGOT)));
        QIOWorkbenchConfiguration.EncodedPattern second = pattern("batch_b", hash('3'),
              new ItemStack(Items.EMERALD), grid(new ItemStack(Items.GOLD_INGOT)));
        assertEquals(2, configuration.putEncodedPatternsIfAbsent(Arrays.asList(first, second)));
        long revision = configuration.getRevision();
        long patternRevision = configuration.getPatternRevision();
        Map<String, String> signatures = new LinkedHashMap<>();
        signatures.put(first.getRecipeId().toString(), first.getRecipeSignature());
        signatures.put(second.getRecipeId().toString(), second.getRecipeSignature());

        Map<String, String> invalid = new LinkedHashMap<>(signatures);
        invalid.put(second.getRecipeId().toString(), hash('9'));
        assertThrows(IllegalArgumentException.class, () ->
              configuration.removeEncodedPatterns(invalid));
        assertEquals(revision, configuration.getRevision());
        assertEquals(patternRevision, configuration.getPatternRevision());
        assertEquals(2, configuration.getEncodedPatternCount());

        assertTrue(configuration.removeEncodedPatterns(signatures));
        assertEquals(revision + 1, configuration.getRevision());
        assertEquals(patternRevision + 1, configuration.getPatternRevision());
        assertEquals(0, configuration.getEncodedPatternCount());
    }

    private static QIOWorkbenchConfiguration.EncodedPattern pattern(String id,
          String signature, ItemStack output, List<ItemStack> grid) {
        return new QIOWorkbenchConfiguration.EncodedPattern(UUID.randomUUID(),
              new ResourceLocation("test", id), signature,
              PortableResourceDescriptor.item(output), output.getCount(), grid);
    }

    private static List<ItemStack> grid(ItemStack first) {
        List<ItemStack> grid = new ArrayList<>(9);
        grid.add(first);
        while (grid.size() < 9) grid.add(ItemStack.EMPTY);
        return grid;
    }

    private static String hash(char value) {
        char[] characters = new char[64];
        Arrays.fill(characters, value);
        return new String(characters);
    }
}
