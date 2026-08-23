package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Immutable server-start snapshot of the Forge item, SEARCH-variant, and ore tables. */
final class QIOForgeRecipeData {

    private static final int MAX_VARIANTS_PER_ITEM = 65_536;
    private static final int MAX_ORE_STACKS_PER_NAME = 65_536;
    private static final QIOForgeRecipeData EMPTY = new QIOForgeRecipeData(
          Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(),
          Collections.emptyMap(), "", false);

    private final List<ItemEntry> items;
    private final List<OreEntry> ores;
    private final Map<Item, List<ItemStack>> variantsByItem;
    private final Map<Item, List<OreEntry>> oresByItem;
    private final String structuralSignature;
    private final boolean globalSnapshot;

    private QIOForgeRecipeData(List<ItemEntry> items, List<OreEntry> ores,
          Map<Item, List<ItemStack>> variantsByItem, Map<Item, List<OreEntry>> oresByItem,
          String structuralSignature, boolean globalSnapshot) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.ores = Collections.unmodifiableList(new ArrayList<>(ores));
        this.variantsByItem = immutableStackMap(variantsByItem);
        Map<Item, List<OreEntry>> immutableOres = new HashMap<>();
        oresByItem.forEach((item, entries) -> immutableOres.put(item,
              Collections.unmodifiableList(new ArrayList<>(entries))));
        this.oresByItem = Collections.unmodifiableMap(immutableOres);
        this.structuralSignature = Objects.requireNonNull(structuralSignature,
              "structuralSignature");
        this.globalSnapshot = globalSnapshot;
    }

    static QIOForgeRecipeData empty() {
        return EMPTY;
    }

    static Capture beginCapture() {
        return new Capture();
    }

    boolean isGlobalSnapshot() {
        return globalSnapshot;
    }

    int getItemCount() {
        return items.size();
    }

    int getOreCount() {
        return ores.size();
    }

    @Nonnull
    String getStructuralSignature() {
        return structuralSignature;
    }

    @Nonnull
    CacheData writeCache() {
        List<NBTTagCompound> itemRecords = new ArrayList<>(items.size());
        List<NBTTagCompound> variantRecords = new ArrayList<>();
        for (ItemEntry item : items) {
            NBTTagCompound record = new NBTTagCompound();
            record.setString("registryName", item.registryName);
            itemRecords.add(record);
            for (FrozenStack variant : item.variants) {
                NBTTagCompound variantRecord = variant.write();
                variantRecord.setString("owner", item.registryName);
                variantRecords.add(variantRecord);
            }
        }
        List<NBTTagCompound> oreRecords = new ArrayList<>(ores.size());
        ores.forEach(ore -> oreRecords.add(ore.write()));
        return new CacheData(itemRecords, variantRecords, oreRecords, structuralSignature);
    }

    @Nonnull
    static QIOForgeRecipeData readCache(@Nonnull CacheData data) {
        CacheRestore restore = beginCacheRestore(data);
        while (!restore.process(Integer.MAX_VALUE, Long.MAX_VALUE, null, 1)) {
            Thread.yield();
        }
        return restore.finish();
    }

    @Nonnull
    static CacheRestore beginCacheRestore(@Nonnull CacheData data) {
        return new CacheRestore(data);
    }

    /** Expands a wildcard using only the frozen SEARCH and ore tables. */
    @Nonnull
    List<ItemStack> expand(@Nonnull ItemStack input, int maximumResults) {
        Objects.requireNonNull(input, "input");
        if (!globalSnapshot || input.isEmpty() || maximumResults <= 0) {
            return Collections.emptyList();
        }
        if (input.getMetadata() != OreDictionary.WILDCARD_VALUE) {
            return Collections.singletonList(input.copy());
        }
        int maximumScanned = maximumResults > Integer.MAX_VALUE / 8 ? Integer.MAX_VALUE :
              Math.max(64, maximumResults * 8);
        Expansion expansion = new Expansion(input, maximumResults, maximumScanned);
        expansion.addVariants(input.getItem());
        expansion.offer(new ItemStack(input.getItem(), input.getCount(), 0));
        expansion.addOres(input);
        List<ItemStack> firstPass = new ArrayList<>(expansion.results);
        for (ItemStack candidate : firstPass) {
            if (!expansion.canContinue()) break;
            expansion.addOres(candidate);
        }
        return Collections.unmodifiableList(new ArrayList<>(expansion.results));
    }

    private static Map<Item, List<ItemStack>> immutableStackMap(
          Map<Item, List<ItemStack>> source) {
        Map<Item, List<ItemStack>> immutable = new HashMap<>();
        source.forEach((item, stacks) -> {
            List<ItemStack> copies = new ArrayList<>(stacks.size());
            copies.addAll(stacks);
            immutable.put(item, Collections.unmodifiableList(copies));
        });
        return Collections.unmodifiableMap(immutable);
    }

    private final class Expansion {

        private final ItemStack input;
        private final int maximumResults;
        private final int maximumScanned;
        private final List<ItemStack> results = new ArrayList<>();
        private final Set<String> identities = new LinkedHashSet<>();
        private int scanned;

        private Expansion(ItemStack input, int maximumResults, int maximumScanned) {
            this.input = input;
            this.maximumResults = maximumResults;
            this.maximumScanned = maximumScanned;
        }

        private boolean canContinue() {
            return results.size() < maximumResults && scanned < maximumScanned;
        }

        private void addVariants(Item item) {
            List<ItemStack> variants = variantsByItem.get(item);
            if (variants == null) return;
            for (ItemStack variant : variants) {
                if (!offer(variant)) break;
            }
        }

        private void addOres(ItemStack stack) {
            List<OreEntry> entries = oresByItem.get(stack.getItem());
            if (entries == null) return;
            for (OreEntry entry : entries) {
                if (!entry.matches(stack)) continue;
                for (FrozenStack candidate : entry.stacks) {
                    if (!canContinue()) return;
                    if (candidate.prototype.getMetadata() == OreDictionary.WILDCARD_VALUE) {
                        addVariants(candidate.prototype.getItem());
                        offer(new ItemStack(candidate.prototype.getItem(), input.getCount(), 0));
                    } else {
                        offer(candidate.prototype);
                    }
                }
            }
        }

        private boolean offer(ItemStack candidate) {
            if (!canContinue()) return false;
            scanned++;
            if (candidate == null || candidate.isEmpty() ||
                candidate.getMetadata() == OreDictionary.WILDCARD_VALUE) {
                return canContinue();
            }
            ItemStack normalized = candidate.copy();
            normalized.setCount(input.getCount());
            if (input.hasTagCompound()) {
                normalized.setTagCompound(input.getTagCompound().copy());
            }
            String identity = FrozenStack.identity(normalized);
            if (identities.add(identity)) {
                results.add(normalized);
            }
            return canContinue();
        }
    }

    /** Incremental server-thread restore of registry-dependent Forge cache records. */
    static final class CacheRestore {

        private enum Stage {
            ITEMS,
            VARIANTS,
            FREEZE_ITEMS,
            ORES,
            COMPILE,
            COMPLETE
        }

        private final CacheData data;
        private final Map<String, MutableItemEntry> byName = new LinkedHashMap<>();
        private final List<MutableItemEntry> orderedItems = new ArrayList<>();
        private Map<String, FrozenStackAccumulator> variants = new LinkedHashMap<>();
        private final List<ItemEntry> frozenItems = new ArrayList<>();
        private final List<OreEntry> ores = new ArrayList<>();
        private Stage stage = Stage.ITEMS;
        private int itemIndex;
        private int variantIndex;
        private int freezeIndex;
        private int oreIndex;
        @Nullable private String restoreOreName;
        private int restoreOreId;
        @Nullable private NBTTagList restoreOreStacks;
        private List<FrozenStack> restoreOreValues = new ArrayList<>();
        private int restoreOreStackIndex;
        @Nullable private CompletableFuture<QIOForgeRecipeData> compileFuture;
        @Nullable private QIOForgeRecipeData result;

        private CacheRestore(CacheData data) {
            this.data = Objects.requireNonNull(data, "data");
            if (data.items.size() > 1_000_000 || data.variants.size() > 4_000_000 ||
                data.ores.size() > 1_000_000) {
                throw new IllegalArgumentException("QIO Forge cache exceeds structural limits");
            }
        }

        boolean process(int maximumEntries, long deadlineNanos,
              @Nullable Executor worker, int workerCount) {
            if (maximumEntries <= 0 || workerCount <= 0) {
                throw new IllegalArgumentException("Forge cache restore slice must be positive");
            }
            int processed = 0;
            while (stage != Stage.COMPLETE && processed < maximumEntries &&
                  beforeDeadline(deadlineNanos)) {
                switch (stage) {
                    case ITEMS -> {
                        if (itemIndex >= data.items.size()) {
                            stage = Stage.VARIANTS;
                            continue;
                        }
                        restoreItem(data.items.get(itemIndex++));
                    }
                    case VARIANTS -> {
                        if (variantIndex >= data.variants.size()) {
                            stage = Stage.FREEZE_ITEMS;
                            continue;
                        }
                        restoreVariant(data.variants.get(variantIndex++));
                    }
                    case FREEZE_ITEMS -> {
                        if (freezeIndex >= orderedItems.size()) {
                            variants = Collections.emptyMap();
                            stage = Stage.ORES;
                            continue;
                        }
                        freezeItem(orderedItems.get(freezeIndex++));
                    }
                    case ORES -> {
                        if (oreIndex >= data.ores.size()) {
                            stage = Stage.COMPILE;
                            continue;
                        }
                        restoreOreStep();
                    }
                    case COMPILE -> {
                        if (compileFuture == null) {
                            if (worker == null) {
                                result = compile(frozenInput(frozenItems, ores), 1, null).join();
                                stage = Stage.COMPLETE;
                                continue;
                            }
                            compileFuture = CompletableFuture.supplyAsync(() ->
                                  frozenInput(frozenItems, ores), worker).thenCompose(input ->
                                        compile(input, workerCount, worker));
                            return false;
                        }
                        if (!compileFuture.isDone()) return false;
                        result = compileFuture.join();
                        compileFuture = null;
                        stage = Stage.COMPLETE;
                        continue;
                    }
                    default -> throw new IllegalStateException(
                          "Unexpected Forge cache restore stage");
                }
                processed++;
            }
            return stage == Stage.COMPLETE;
        }

        @Nonnull
        QIOForgeRecipeData finish() {
            if (stage != Stage.COMPLETE || result == null) {
                throw new IllegalStateException("Forge cache restore is incomplete");
            }
            return result;
        }

        void cancel() {
            if (compileFuture != null) compileFuture.cancel(true);
        }

        private void restoreItem(NBTTagCompound record) {
            String name = record.getString("registryName");
            Item item;
            try {
                item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(name));
            } catch (RuntimeException error) {
                item = null;
            }
            if (item == null || item.getRegistryName() == null) {
                throw new IllegalArgumentException("Cached Forge item is no longer registered: " + name);
            }
            if (byName.containsKey(name)) {
                throw new IllegalArgumentException("Cached Forge item is duplicated: " + name);
            }
            int blockId = item instanceof ItemBlock itemBlock ?
                  Block.getIdFromBlock(itemBlock.getBlock()) : -1;
            MutableItemEntry entry = new MutableItemEntry(item, name,
                  Item.getIdFromItem(item), blockId);
            byName.put(name, entry);
            orderedItems.add(entry);
        }

        private void restoreVariant(NBTTagCompound record) {
            String owner = record.getString("owner");
            MutableItemEntry entry = byName.get(owner);
            if (entry == null) {
                throw new IllegalArgumentException("Cached Forge variant owner is unknown: " + owner);
            }
            FrozenStack stack = FrozenStack.read(record);
            if (stack.prototype.getItem() != entry.item) {
                throw new IllegalArgumentException("Cached Forge variant owner changed: " + owner);
            }
            FrozenStackAccumulator accumulator = variants.computeIfAbsent(owner,
                  ignored -> new FrozenStackAccumulator());
            if (accumulator.size() >= MAX_VARIANTS_PER_ITEM) {
                throw new IllegalArgumentException(
                      "Cached item contains too many SEARCH variants");
            }
            accumulator.add(stack);
        }

        private void freezeItem(MutableItemEntry entry) {
            FrozenStackAccumulator captured = variants.get(entry.registryName);
            if (captured != null) {
                entry.variants = captured.freeze();
            }
            frozenItems.add(entry.freeze());
        }

        private void restoreOreStep() {
            if (restoreOreName == null) {
                NBTTagCompound record = data.ores.get(oreIndex);
                String name = record.getString("name");
                if (name.isEmpty()) {
                    throw new IllegalArgumentException("Cached ore name is empty");
                }
                // getOreID registers missing names in Forge 1.12. A cache must never mutate
                // the live ore dictionary, so stale names are ignored before resolving the ID.
                if (!OreDictionary.doesOreNameExist(name)) {
                    throw new IllegalArgumentException("Cached ore name is no longer registered: " + name);
                }
                NBTTagList values = record.getTagList("stacks", 10);
                if (values.tagCount() > MAX_ORE_STACKS_PER_NAME) {
                    throw new IllegalArgumentException(
                          "Cached ore entry contains too many stacks");
                }
                restoreOreName = name;
                restoreOreId = OreDictionary.getOreID(name);
                restoreOreStacks = values;
                return;
            }
            NBTTagList values = Objects.requireNonNull(restoreOreStacks,
                  "cached ore stacks");
            if (restoreOreStackIndex < values.tagCount()) {
                try {
                    restoreOreValues.add(FrozenStack.read(
                          values.getCompoundTagAt(restoreOreStackIndex)));
                } catch (RuntimeException ignored) {
                }
                restoreOreStackIndex++;
                return;
            }
            if (!restoreOreValues.isEmpty()) {
                ores.add(new OreEntry(restoreOreId, restoreOreName, restoreOreValues));
            }
            restoreOreName = null;
            restoreOreStacks = null;
            restoreOreValues = new ArrayList<>();
            restoreOreStackIndex = 0;
            oreIndex++;
        }
    }

    static final class Capture {

        private enum Stage {
            ITEMS,
            VARIANTS,
            ORES,
            FREEZE_ITEMS,
            COMPLETE
        }

        @Nullable private Iterator<Item> itemSource;
        private final List<MutableItemEntry> items = new ArrayList<>();
        private String[] oreNames = new String[0];
        private final List<OreEntry> ores = new ArrayList<>();
        private final List<ItemEntry> frozenItems = new ArrayList<>();
        private Stage stage = Stage.ITEMS;
        private int itemIndex;
        private int variantIndex;
        private int oreIndex;
        private int freezeIndex;
        @Nullable private MutableItemEntry activeVariantEntry;
        private List<ItemStack> activeVariantStacks = Collections.emptyList();
        private FrozenStackAccumulator activeVariants = new FrozenStackAccumulator();
        private int activeVariantStackIndex;
        @Nullable private String activeOreName;
        private List<ItemStack> activeOreStacks = Collections.emptyList();
        private FrozenStackAccumulator activeOreValues = new FrozenStackAccumulator();
        private int activeOreStackIndex;

        private Capture() {
            List<Item> stableItems = new ArrayList<>(ForgeRegistries.ITEMS.getValuesCollection());
            stableItems.removeIf(item -> item == null || item.getRegistryName() == null);
            stableItems.sort(Comparator.comparing(item -> item.getRegistryName().toString()));
            itemSource = Collections.unmodifiableList(stableItems).iterator();
            String[] capturedOreNames = OreDictionary.getOreNames();
            oreNames = capturedOreNames == null ? new String[0] :
                  Arrays.copyOf(capturedOreNames, capturedOreNames.length);
        }

        int process(int maximumEntries, long deadlineNanos) {
            if (maximumEntries <= 0) return 0;
            int processed = 0;
            while (stage != Stage.COMPLETE && processed < maximumEntries &&
                  beforeDeadline(deadlineNanos)) {
                switch (stage) {
                    case ITEMS -> {
                        Iterator<Item> source = Objects.requireNonNull(itemSource,
                              "Forge item source");
                        if (!source.hasNext()) {
                            itemSource = null;
                            stage = Stage.VARIANTS;
                            continue;
                        }
                        captureItem(source.next());
                        itemIndex++;
                    }
                    case VARIANTS -> {
                        if (variantIndex >= items.size()) {
                            stage = Stage.ORES;
                            continue;
                        }
                        // getSubItems is a third-party, non-cancellable Forge call.  The
                        // deadline is checked at the top of the loop, so a slow call may finish
                        // the current operation but must not force the whole remainder of the
                        // capture into separate ticks.  Stopping after every call turns a large
                        // registry into one item per tick and can stretch startup by minutes.
                        captureVariantStep();
                    }
                    case ORES -> {
                        if (oreIndex >= oreNames.length) {
                            stage = Stage.FREEZE_ITEMS;
                            continue;
                        }
                        captureOreStep();
                    }
                    case FREEZE_ITEMS -> {
                        if (freezeIndex >= items.size()) {
                            stage = Stage.COMPLETE;
                            continue;
                        }
                        frozenItems.add(items.get(freezeIndex++).freeze());
                    }
                    default -> throw new IllegalStateException("Unexpected Forge capture stage");
                }
                processed++;
            }
            return processed;
        }

        boolean isComplete() {
            return stage == Stage.COMPLETE;
        }

        @Nonnull
        QIOForgeRecipeData finish() {
            if (!isComplete()) {
                throw new IllegalStateException("Forge recipe data capture is incomplete");
            }
            return compile(frozenInput(), 1, null).join();
        }

        @Nonnull
        CompletableFuture<QIOForgeRecipeData> finishAsync(Executor executor, int workerCount) {
            if (!isComplete()) {
                throw new IllegalStateException("Forge recipe data capture is incomplete");
            }
            Executor checked = Objects.requireNonNull(executor, "executor");
            return CompletableFuture.supplyAsync(this::frozenInput, checked)
                  .thenCompose(input -> compile(input, workerCount, checked));
        }

        private void captureItem(@Nullable Item item) {
            if (item == null || item.getRegistryName() == null) return;
            int blockId = item instanceof ItemBlock itemBlock ?
                  Block.getIdFromBlock(itemBlock.getBlock()) : -1;
            items.add(new MutableItemEntry(item, item.getRegistryName().toString(),
                  Item.getIdFromItem(item), blockId));
        }

        private void captureVariantStep() {
            if (activeVariantEntry == null) {
                activeVariantEntry = items.get(variantIndex);
                NonNullList<ItemStack> captured = NonNullList.create();
                try {
                    activeVariantEntry.item.getSubItems(CreativeTabs.SEARCH, captured);
                } catch (RuntimeException | LinkageError ignored) {
                    // One broken creative-tab implementation must not abort the catalog.
                }
                activeVariantStacks = immutableStacks(captured);
                return;
            }
            if (activeVariantStackIndex < activeVariantStacks.size() &&
                activeVariants.size() < MAX_VARIANTS_PER_ITEM) {
                ItemStack stack = activeVariantStacks.get(activeVariantStackIndex++);
                if (stack == null || stack.isEmpty() ||
                    stack.getItem() != activeVariantEntry.item ||
                    stack.getMetadata() == OreDictionary.WILDCARD_VALUE) return;
                try {
                    activeVariants.add(new FrozenStack(stack));
                } catch (RuntimeException ignored) {
                }
                return;
            }
            if (activeVariants.isEmpty()) {
                ItemStack fallback = new ItemStack(activeVariantEntry.item);
                if (!fallback.isEmpty()) {
                    try {
                        activeVariants.add(new FrozenStack(fallback));
                    } catch (RuntimeException ignored) {
                    }
                }
            }
            activeVariantEntry.variants = activeVariants.freeze();
            activeVariantEntry = null;
            activeVariantStacks = Collections.emptyList();
            activeVariants = new FrozenStackAccumulator();
            activeVariantStackIndex = 0;
            variantIndex++;
        }

        private void captureOreStep() {
            if (activeOreName == null) {
                String name = oreNames[oreIndex];
                if (name == null || name.isEmpty()) {
                    oreIndex++;
                    return;
                }
                activeOreName = name;
                try {
                    activeOreStacks = immutableStacks(OreDictionary.getOres(name, false));
                } catch (RuntimeException error) {
                    activeOreStacks = Collections.emptyList();
                }
                return;
            }
            if (activeOreStackIndex < activeOreStacks.size() &&
                activeOreValues.size() < MAX_ORE_STACKS_PER_NAME) {
                ItemStack stack = activeOreStacks.get(activeOreStackIndex++);
                if (stack == null || stack.isEmpty()) return;
                try {
                    activeOreValues.add(new FrozenStack(stack));
                } catch (RuntimeException ignored) {
                }
                return;
            }
            if (!activeOreValues.isEmpty()) {
                ores.add(new OreEntry(OreDictionary.getOreID(activeOreName), activeOreName,
                      activeOreValues.freeze()));
            }
            activeOreName = null;
            activeOreStacks = Collections.emptyList();
            activeOreValues = new FrozenStackAccumulator();
            activeOreStackIndex = 0;
            oreIndex++;
        }

        private FrozenInput frozenInput() {
            return QIOForgeRecipeData.frozenInput(frozenItems, ores);
        }

        private static List<ItemStack> immutableStacks(@Nullable List<ItemStack> source) {
            if (source == null || source.isEmpty()) return Collections.emptyList();
            List<ItemStack> copies = new ArrayList<>(source.size());
            for (ItemStack stack : source) {
                if (stack != null && !stack.isEmpty()) copies.add(stack.copy());
            }
            return Collections.unmodifiableList(copies);
        }
    }

    private static FrozenInput frozenInput(List<ItemEntry> items, List<OreEntry> ores) {
        List<ItemEntry> orderedItems = new ArrayList<>(items.size());
        for (ItemEntry item : items) {
            List<FrozenStack> variants = new ArrayList<>(item.variants);
            variants.sort(Comparator.comparing(value -> value.identity));
            orderedItems.add(new ItemEntry(item.item, item.registryName,
                  item.runtimeItemId, item.runtimeBlockId, variants));
        }
        orderedItems.sort(Comparator.comparing(item -> item.registryName));
        List<OreEntry> orderedOres = new ArrayList<>(ores.size());
        for (OreEntry ore : ores) {
            List<FrozenStack> stacks = new ArrayList<>(ore.stacks);
            stacks.sort(Comparator.comparing(value -> value.identity));
            orderedOres.add(new OreEntry(ore.oreId, ore.name, stacks));
        }
        orderedOres.sort(Comparator.comparing(ore -> ore.name));
        return new FrozenInput(orderedItems, orderedOres);
    }

    private static CompletableFuture<QIOForgeRecipeData> compile(FrozenInput input,
          int workerCount, @Nullable Executor executor) {
        if (workerCount <= 0) {
            throw new IllegalArgumentException("Catalog worker count must be positive");
        }
        int shards = Math.min(workerCount, Math.max(1,
              Math.max(input.items.size(), input.ores.size())));
        int itemBase = input.items.size() / shards;
        int oreBase = input.ores.size() / shards;
        List<CompletableFuture<DataShard>> futures = new ArrayList<>(shards);
        for (int shard = 0; shard < shards; shard++) {
            int itemFrom = shard * itemBase;
            int itemTo = shard == shards - 1 ? input.items.size() : itemFrom + itemBase;
            int oreFrom = shard * oreBase;
            int oreTo = shard == shards - 1 ? input.ores.size() : oreFrom + oreBase;
            if (executor == null) {
                futures.add(CompletableFuture.completedFuture(DataShard.scan(input,
                      itemFrom, itemTo, oreFrom, oreTo)));
            } else {
                futures.add(CompletableFuture.supplyAsync(() -> DataShard.scan(input,
                      itemFrom, itemTo, oreFrom, oreTo), executor));
            }
        }
        CompletableFuture<?>[] pending = futures.toArray(new CompletableFuture<?>[0]);
        return CompletableFuture.allOf(pending).thenApply(ignored -> {
            List<DataShard> completed = new ArrayList<>(futures.size());
            futures.forEach(future -> completed.add(future.join()));
            return merge(input, completed);
        });
    }

    private static QIOForgeRecipeData merge(FrozenInput input, List<DataShard> shards) {
        Map<Item, List<ItemStack>> variants = new HashMap<>();
        Map<Item, List<OreEntry>> itemOres = new HashMap<>();
        for (DataShard shard : shards) {
            shard.variantsByItem.forEach((item, stacks) ->
                  variants.computeIfAbsent(item, ignored -> new ArrayList<>()).addAll(stacks));
            shard.oresByItem.forEach((item, entries) ->
                  itemOres.computeIfAbsent(item, ignored -> new ArrayList<>()).addAll(entries));
        }
        StringBuilder canonical = new StringBuilder();
        for (ItemEntry item : input.items) {
            // Runtime numeric IDs are Forge-session details, not persistent identities.  The
            // registry name and frozen variants are the stable cache contract.
            canonical.append("I|").append(item.registryName).append('\n');
            for (FrozenStack variant : item.variants) {
                canonical.append("V|").append(item.registryName).append('|')
                      .append(variant.identity).append('\n');
            }
        }
        for (OreEntry ore : input.ores) {
            canonical.append("O|").append(ore.name).append('|');
            ore.stacks.forEach(stack -> canonical.append(stack.identity).append(';'));
            canonical.append('\n');
        }
        return new QIOForgeRecipeData(input.items, input.ores, variants, itemOres,
              QIOHashingBridge.sha256(canonical.toString()), true);
    }

    private static boolean beforeDeadline(long deadlineNanos) {
        return deadlineNanos == Long.MAX_VALUE || System.nanoTime() < deadlineNanos;
    }

    private static final class FrozenInput {

        private final List<ItemEntry> items;
        private final List<OreEntry> ores;

        private FrozenInput(List<ItemEntry> items, List<OreEntry> ores) {
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
            this.ores = Collections.unmodifiableList(new ArrayList<>(ores));
        }
    }

    private static final class FrozenStackAccumulator {

        private final Set<String> identities = new LinkedHashSet<>();
        private final List<FrozenStack> values = new ArrayList<>();

        private void add(FrozenStack value) {
            if (identities.add(value.identity)) values.add(value);
        }

        private int size() {
            return values.size();
        }

        private boolean isEmpty() {
            return values.isEmpty();
        }

        private List<FrozenStack> freeze() {
            return Collections.unmodifiableList(values);
        }
    }

    private static final class DataShard {

        private final Map<Item, List<ItemStack>> variantsByItem = new HashMap<>();
        private final Map<Item, List<OreEntry>> oresByItem = new HashMap<>();

        private static DataShard scan(FrozenInput input, int itemFrom, int itemTo,
              int oreFrom, int oreTo) {
            DataShard shard = new DataShard();
            for (int index = itemFrom; index < itemTo; index++) {
                ItemEntry entry = input.items.get(index);
                List<ItemStack> variants = new ArrayList<>(entry.variants.size());
                entry.variants.forEach(variant -> variants.add(variant.prototype));
                shard.variantsByItem.put(entry.item, variants);
            }
            for (int index = oreFrom; index < oreTo; index++) {
                OreEntry ore = input.ores.get(index);
                Set<Item> seen = new LinkedHashSet<>();
                for (FrozenStack stack : ore.stacks) {
                    if (seen.add(stack.prototype.getItem())) {
                        shard.oresByItem.computeIfAbsent(stack.prototype.getItem(),
                              ignored -> new ArrayList<>()).add(ore);
                    }
                }
            }
            return shard;
        }
    }

    private static final class MutableItemEntry {

        private final Item item;
        private final String registryName;
        private final int runtimeItemId;
        private final int runtimeBlockId;
        private List<FrozenStack> variants = Collections.emptyList();

        private MutableItemEntry(Item item, String registryName, int runtimeItemId,
              int runtimeBlockId) {
            this.item = item;
            this.registryName = registryName;
            this.runtimeItemId = runtimeItemId;
            this.runtimeBlockId = runtimeBlockId;
        }

        private ItemEntry freeze() {
            return new ItemEntry(item, registryName, runtimeItemId, runtimeBlockId, variants);
        }
    }

    static final class ItemEntry {

        private final Item item;
        private final String registryName;
        private final int runtimeItemId;
        private final int runtimeBlockId;
        private final List<FrozenStack> variants;

        private ItemEntry(Item item, String registryName, int runtimeItemId, int runtimeBlockId,
              List<FrozenStack> variants) {
            this.item = item;
            this.registryName = registryName;
            this.runtimeItemId = runtimeItemId;
            this.runtimeBlockId = runtimeBlockId;
            this.variants = Collections.unmodifiableList(variants);
        }
    }

    static final class OreEntry {

        private final int oreId;
        private final String name;
        private final List<FrozenStack> stacks;

        private OreEntry(int oreId, String name, List<FrozenStack> stacks) {
            this.oreId = oreId;
            this.name = name;
            this.stacks = Collections.unmodifiableList(stacks);
        }

        private boolean matches(ItemStack input) {
            for (FrozenStack stack : stacks) {
                if (stack.prototype.getItem() != input.getItem()) continue;
                int expected = stack.prototype.getMetadata();
                if (expected == OreDictionary.WILDCARD_VALUE ||
                    input.getMetadata() == OreDictionary.WILDCARD_VALUE ||
                    expected == input.getMetadata()) {
                    return true;
                }
            }
            return false;
        }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            data.setString("name", name);
            NBTTagList values = new NBTTagList();
            stacks.forEach(stack -> values.appendTag(stack.write()));
            data.setTag("stacks", values);
            return data;
        }

    }

    static final class FrozenStack {

        private final ItemStack prototype;
        private final PortableResourceDescriptor descriptor;
        private final String identity;
        private final NBTTagCompound serializedPrototype;

        private FrozenStack(ItemStack stack) {
            prototype = stack.copy();
            prototype.setCount(1);
            descriptor = PortableResourceDescriptor.item(prototype);
            identity = identity(prototype);
            serializedPrototype = prototype.writeToNBT(new NBTTagCompound());
        }

        private NBTTagCompound write() {
            NBTTagCompound data = serializedPrototype.copy();
            data.setTag("descriptor", descriptor.write());
            return data;
        }

        private static FrozenStack read(NBTTagCompound data) {
            ItemStack stack = new ItemStack(data);
            if (stack.isEmpty()) {
                throw new IllegalArgumentException("Cached item variant is unresolved");
            }
            FrozenStack frozen = new FrozenStack(stack);
            if (data.hasKey("descriptor", 10) &&
                !frozen.descriptor.equals(PortableResourceDescriptor.read(
                      data.getCompoundTag("descriptor")))) {
                throw new IllegalArgumentException("Cached item variant identity changed");
            }
            return frozen;
        }

        private static String identity(ItemStack stack) {
            try {
                return PortableResourceDescriptor.item(stack) + "@" + stack.getCount();
            } catch (RuntimeException error) {
                ResourceLocation name = stack.getItem().getRegistryName();
                return String.valueOf(name) + '|' + stack.getMetadata() + '|' +
                      stack.getCount() + '|' + String.valueOf(stack.getTagCompound());
            }
        }
    }

    static final class CacheData {

        final List<NBTTagCompound> items;
        final List<NBTTagCompound> variants;
        final List<NBTTagCompound> ores;
        final String structuralSignature;

        CacheData(List<NBTTagCompound> items, List<NBTTagCompound> variants,
              List<NBTTagCompound> ores, String structuralSignature) {
            this.items = immutableTags(items);
            this.variants = immutableTags(variants);
            this.ores = immutableTags(ores);
            this.structuralSignature = Objects.requireNonNull(structuralSignature,
                  "structuralSignature");
        }

        private static List<NBTTagCompound> immutableTags(List<NBTTagCompound> values) {
            List<NBTTagCompound> copied = new ArrayList<>(values.size());
            values.forEach(value -> copied.add(value.copy()));
            return Collections.unmodifiableList(copied);
        }
    }

    /** Keeps this snapshot independent of package-private hashing implementation details. */
    private static final class QIOHashingBridge {

        private static String sha256(String value) {
            return mekanism.qioprocessing.common.util.QIOHashing.sha256(value);
        }
    }
}
