package mekanism.qioprocessing.common.network;

import mekanism.qioprocessing.common.recipe.RecipeItemKey;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * GUI-independent request/state controller. No recipe computation or registry access on the client.
 */
public final class WorkbenchScreenModel implements AutoCloseable {
    public enum View {LIST, IMPORTED, ROUTES, CANDIDATES, PATTERNS, PREVIEW_ROUTES}

    private final Function<Consumer<WorkbenchPacket.Reply>, WorkbenchClientSession> factory;
    private final LongSupplier clock;
    private final Map<Long, Pending> pending = new HashMap<>();
    private WorkbenchClientSession session;
    private View view = View.LIST;
    private List<NBTTagCompound> rows = Collections.emptyList(), productRows = Collections.emptyList(), recipeRows = Collections.emptyList();
    private NBTTagCompound selected = new NBTTagCompound(), detail = new NBTTagCompound(), preview;
    private String search = "", selectedRecipe = "", selectedProduct = "", productRecipe = "", candidate = "", mode = "PREFERRED";
    private String generation = "", status = "NOT_READY";
    private UUID previewJob, bookId, selectedPattern;
    private UUID orderJob, orderId;
    private long revision = -1, epoch, mutation = -1, preparing = -1, polling = -1, orderPoll = -1, nextPoll;
    private int offset, total, limit = 8, productOffset, productTotal, recipeOffset, recipeTotal, slot, candidatePriority = -1, version;
    private boolean ready, closed;

    public WorkbenchScreenModel(int window) {
        this(receiver -> WorkbenchClientSession.bind(window, receiver), () -> System.nanoTime() / 1_000_000L);
    }

    public WorkbenchScreenModel(Function<Consumer<WorkbenchPacket.Reply>, WorkbenchClientSession> factory, LongSupplier clock) {
        this.factory = factory;
        this.clock = clock;
    }

    public void open() {
        if (closed) return;
        if (session != null) session.close();
        pending.clear();
        mutation = preparing = polling = orderPoll = -1;
        previewJob = orderJob = orderId = null;
        preview = null;
        ready = false;
        rows = productRows = recipeRows = Collections.emptyList();
        selected = new NBTTagCompound();
        detail = new NBTTagCompound();
        selectedRecipe = selectedProduct = candidate = "";
        selectedPattern = null;
        version++;
        view = View.LIST;
        offset = 0;
        productRecipe = "";
        revision = -1;
        generation = "";
        bookId = null;
        session = factory.apply(this::receive);
        status = "NOT_READY";
        send(action("OPEN"), false);
    }

    public boolean ready() {
        return ready && !closed;
    }

    public boolean busy() {
        return mutation >= 0 || preparing >= 0 || orderPoll >= 0 || orderJob != null || orderId != null
                || previewJob != null && preview == null;
    }

    public boolean canCancel() {
        return ready() && mutation < 0 && preparing < 0 && previewJob != null;
    }

    public boolean orderRunning() {
        return (orderJob != null || orderId != null) && ready() && !closed;
    }

    public UUID orderJob() {
        return orderJob;
    }

    public UUID orderId() {
        return orderId;
    }

    public boolean loadingPage() {
        for (Pending request : pending.values())
            if (request.epoch == epoch && request.action.equals(view.name())) return true;
        return false;
    }

    public View view() {
        return view;
    }

    public String status() {
        return status;
    }

    public String mode() {
        return mode;
    }

    public String selectedRecipe() {
        return selectedRecipe;
    }

    public UUID selectedPattern() {
        return selectedPattern;
    }

    public String selectedProduct() {
        return selectedProduct;
    }

    public String candidate() {
        return candidate;
    }

    public int candidatePriority() {
        return candidatePriority;
    }

    public int slot() {
        return slot;
    }

    public int offset() {
        return offset;
    }

    public int total() {
        return total;
    }

    public int pageSize() {
        return limit;
    }

    public int version() {
        return version;
    }

    public UUID bookId() {
        return bookId;
    }

    public boolean imported() {
        return selected.getBoolean("imported");
    }

    public boolean enabled() {
        return selected.getBoolean("enabled");
    }

    public NBTTagCompound selected() {
        return selected.copy();
    }

    public NBTTagCompound detail() {
        return detail.copy();
    }

    public NBTTagCompound preview() {
        return preview == null ? null : preview.copy();
    }

    public List<NBTTagCompound> rows() {
        List<NBTTagCompound> copy = new ArrayList<>();
        for (NBTTagCompound row : rows) copy.add(row.copy());
        return copy;
    }

    public List<NBTTagCompound> productRows() {
        return copy(productRows);
    }

    public List<NBTTagCompound> recipeRows() {
        return copy(recipeRows);
    }

    public int productTotal() {
        return productTotal;
    }

    public int recipeTotal() {
        return recipeTotal;
    }

    private static List<NBTTagCompound> copy(List<NBTTagCompound> source) {
        List<NBTTagCompound> result = new ArrayList<>();
        for (NBTTagCompound row : source) result.add(row.copy());
        return result;
    }

    public void cycleMode() {
        if (!busy()) {
            mode = mode.equals("DIRECT") ? "PREFERRED" : mode.equals("PREFERRED") ? "ALL" : "DIRECT";
            clearPreview();
            if (ready()) refresh();
        }
    }

    public void search(String value) {
        search = value;
        show(View.LIST);
    }

    public void show(View next) {
        if (next == View.PREVIEW_ROUTES && preview == null) return;
        if (next == View.ROUTES && selectedProduct.isEmpty()
                || (next == View.CANDIDATES || next == View.PATTERNS) && selectedRecipe.isEmpty()) return;
        if (next == View.ROUTES) productRecipe = selectedProduct;
        view = next;
        offset = 0;
        candidate = "";
        if (next != View.PATTERNS) selectedPattern = null;
        refresh();
    }

    public void refresh() {
        if (!ready()) {
            if (!closed) open();
            return;
        }
        epoch++;
        version++;
        NBTTagCompound request = action(view.name());
        request.setInteger("offset", offset);
        request.setInteger("limit", limit);
        if (view == View.LIST) request.setString("search", search);
        if (view == View.ROUTES) request.setString("product", productRecipe);
        if (view == View.CANDIDATES) {
            request.setString("recipe", selectedRecipe);
            request.setInteger("slot", slot);
        }
        if (view == View.PATTERNS) request.setString("recipe", selectedRecipe);
        if (view == View.PREVIEW_ROUTES) request.setUniqueId("preview", previewJob);
        send(request, false);
    }

    public void page(int direction) {
        int next = (int) Math.max(0, Math.min(Integer.MAX_VALUE, (long) offset + (long) direction * limit));
        if (direction > 0 && next >= total) return;
        offset = next;
        if (view == View.PATTERNS) selectedPattern = null;
        refresh();
    }

    public void select(int row) {
        List<NBTTagCompound> source = view == View.LIST ? productRows : rows;
        if (row < 0 || row >= source.size()) return;
        NBTTagCompound value = source.get(row);
        if (view == View.PATTERNS) {
            selectedPattern = value.hasUniqueId("id") ? value.getUniqueId("id") : null;
        } else if (view == View.CANDIDATES) {
            candidate = value.getString("candidate");
            candidatePriority = value.getInteger("priority");
        } else if (view == View.LIST && value.hasKey("product", 8)) {
            selectedProduct = value.getString("product");
            selected = value.copy();
            selectedRecipe = "";
            detail = new NBTTagCompound();
            show(View.ROUTES);
        } else {
            selected = value.copy();
            selectedRecipe = value.getString("recipe");
            detail = new NBTTagCompound();
            candidate = "";
            selectedPattern = null;
            if (!"MISSING".equals(value.getString("state"))) requestDetail();
        }
        version++;
    }

    public void candidates(int slot) {
        this.slot = slot;
        show(View.CANDIDATES);
    }

    private void requestDetail() {
        if (selectedRecipe.isEmpty()) return;
        NBTTagCompound request = action("DETAIL");
        request.setString("recipe", selectedRecipe);
        send(request, false);
    }

    public void prepare(boolean all) {
        if (!ready() || busy() || !all && selectedRecipe.isEmpty()) return;
        clearPreview();
        NBTTagCompound request = edited("PREVIEW");
        request.setBoolean("all", all);
        request.setString("mode", mode);
        if (!all) request.setString("recipe", selectedRecipe);
        status = "RUNNING";
        preparing = send(request, false);
    }

    public boolean canConfirm() {
        return ready() && !busy() && preview != null && preview.getLong("revision") == revision
                && generation.equals(preview.getString("generation"));
    }

    public void confirm() {
        if (!canConfirm()) return;
        NBTTagCompound request = edited("COMMIT");
        request.setUniqueId("preview", previewJob);
        mutation = send(request, true);
    }

    /**
     * Sends a concrete nine-slot grid for server-side recipe validation. The client never evaluates the recipe.
     */
    public void encodePattern(List<ItemStack> grid) {
        if (!ready() || busy() || selectedRecipe.isEmpty() || grid == null || grid.size() != 9) return;
        NBTTagCompound request = edited("ADD_PATTERN");
        request.setString("recipe", selectedRecipe);
        request.setUniqueId("pattern", UUID.randomUUID());
        NBTTagList slots = new NBTTagList();
        for (ItemStack source : grid) {
            NBTTagCompound slot = new NBTTagCompound();
            if (source != null && !source.isEmpty())
                slot.setTag("stack", source.copy().writeToNBT(new NBTTagCompound()));
            slots.appendTag(slot);
        }
        request.setTag("grid", slots);
        mutation = send(request, true);
    }

    public void removePattern(UUID pattern) {
        if (!ready() || busy() || selectedRecipe.isEmpty() || pattern == null) return;
        NBTTagCompound request = edited("REMOVE_PATTERN");
        request.setString("recipe", selectedRecipe);
        request.setUniqueId("pattern", pattern);
        mutation = send(request, true);
    }

    public void cancel() {
        if (!canCancel()) return;
        mutation = send(action("CANCEL_PREVIEW"), true);
    }

    public void submitOrder(java.util.Map<RecipeItemKey, Long> targets) {
        if (!ready() || busy() || targets == null || targets.isEmpty() || orderJob != null || orderId != null) return;
        NBTTagCompound request = edited("SUBMIT_ORDER");
        NBTTagList rows = new NBTTagList();
        for (java.util.Map.Entry<RecipeItemKey, Long> target : targets.entrySet()) {
            if (target.getKey() == null || target.getValue() == null || target.getValue() <= 0) return;
            NBTTagCompound row = new NBTTagCompound();
            row.setTag("item", target.getKey().displayNBT());
            row.setLong("amount", target.getValue());
            rows.appendTag(row);
        }
        if (rows.tagCount() == 0 || rows.tagCount() > 128) return;
        request.setTag("targets", rows);
        orderPoll = send(request, false);
    }

    public void cancelOrder() {
        if (!orderRunning()) return;
        NBTTagCompound request = action("CANCEL_ORDER");
        if (orderJob != null) request.setUniqueId("orderJob", orderJob);
        if (orderId != null) request.setUniqueId("order", orderId);
        mutation = send(request, true);
    }

    public void routeAction(String action) {
        if (!ready() || busy() || selectedRecipe.isEmpty() && !"CLEAR_ALL".equals(action)) return;
        NBTTagCompound request = edited(action);
        request.setString("recipe", selectedRecipe);
        if ("SYNC_CHOICES".equals(action)) request.setInteger("slot", slot);
        if ("ENABLE".equals(action)) request.setBoolean("enabled", !enabled());
        mutation = send(request, true);
    }

    public void candidateAction(boolean move, int direction) {
        if (!ready() || busy() || candidate.isEmpty() || !imported()) return;
        NBTTagCompound request = edited(move ? "MOVE_CHOICE" : "CHOICE");
        request.setString("recipe", selectedRecipe);
        request.setInteger("slot", slot);
        request.setString("candidate", candidate);
        if (move) request.setInteger("index", candidatePriority + direction);
        else request.setBoolean("enabled", candidatePriority < 0);
        mutation = send(request, true);
    }

    private NBTTagCompound edited(String action) {
        NBTTagCompound request = action(action);
        request.setLong("revision", revision);
        request.setString("generation", generation);
        return request;
    }

    private static NBTTagCompound action(String action) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("action", action);
        return tag;
    }

    private long send(NBTTagCompound data, boolean mutation) {
        if (session.isClosed()) {
            ready = false;
            status = "CLOSED";
            return -1;
        }
        long id = session.send(data);
        pending.put(id, new Pending(data.getString("action"), epoch, data.getString("recipe"), clock.getAsLong() + 15_000, mutation,
                data.hasUniqueId("preview") ? data.getUniqueId("preview") : null));
        return id;
    }

    private void receive(WorkbenchPacket.Reply reply) {
        Pending request = pending.remove(reply.request);
        if (request == null || closed) return;
        NBTTagCompound data = reply.data();
        String state = data.getString("status");
        if (mutation == reply.request) mutation = -1;
        if (preparing == reply.request) preparing = -1;
        if (polling == reply.request) polling = -1;
        if (orderPoll == reply.request) orderPoll = -1;
        boolean result = "POLL_PREVIEW".equals(request.action);
        if ((result || "PREVIEW_ROUTES".equals(request.action)) && !java.util.Objects.equals(request.job, previewJob))
            return;
        if (!result && data.hasKey("revision", 4)) {
            if (data.getLong("revision") >= revision) {
                revision = data.getLong("revision");
                generation = data.getString("generation");
            }
            if (data.hasUniqueId("book")) bookId = data.getUniqueId("book");
            if (preview != null && (preview.getLong("revision") != revision || !preview.getString("generation").equals(generation)))
                status = "STALE_REVISION";
        }
        if ("OPENED".equals(state)) {
            ready = true;
            status = "OK";
            refresh();
            return;
        }
        if ("ACCEPTED".equals(state)) {
            previewJob = data.getUniqueId("preview");
            nextPoll = clock.getAsLong();
            status = "RUNNING";
            return;
        }
        if ("ORDER_ACCEPTED".equals(state)) {
            orderJob = data.getUniqueId("orderJob");
            orderId = null;
            orderPoll = -1;
            nextPoll = clock.getAsLong();
            status = "RUNNING";
            version++;
            return;
        }
        if ("RUNNING".equals(state) && "POLL_ORDER".equals(request.action)) {
            nextPoll = clock.getAsLong() + 100;
            return;
        }
        if ("CLAIM_RESERVED".equals(state) || "NO_CLAIM_REQUIRED".equals(state)
                || "CLAIM_REJECTED".equals(state) || "NEEDS_RECONCILIATION".equals(state)
                || "PLANNING_FAILED".equals(state) || "NO_ORDER".equals(state)) {
            if (data.hasUniqueId("order")) orderId = data.getUniqueId("order");
            orderJob = null;
            orderPoll = -1;
            status = state;
            version++;
            return;
        }
        if ("ORDER_CANCELLED".equals(state)) {
            orderJob = orderId = null;
            orderPoll = -1;
            status = state;
            version++;
            return;
        }
        if ("RUNNING".equals(state)) {
            nextPoll = clock.getAsLong() + 100;
            return;
        }
        if ("PREPARED".equals(state)) {
            preview = data.copy();
            status = "PREPARED";
            version++;
            show(View.PREVIEW_ROUTES);
            return;
        }
        if ("SAVED".equals(state) || "CANCELLED".equals(state) || "PATTERN_SAVED".equals(state) || "PATTERN_REMOVED".equals(state)) {
            clearPreview();
            status = state;
            if ("PATTERN_REMOVED".equals(state)) selectedPattern = null;
            if ("REMOVE".equals(request.action) || "CLEAR_ALL".equals(request.action))
                selected.setBoolean("imported", false);
            if (view == View.PREVIEW_ROUTES) {
                view = View.IMPORTED;
                offset = 0;
            }
            refresh();
            if (!selectedRecipe.isEmpty() && !"MISSING".equals(selected.getString("state"))) requestDetail();
            return;
        }
        if ("OK".equals(state)) {
            if ("DETAIL".equals(request.action)) {
                if (request.recipe.equals(selectedRecipe)) {
                    detail = data.copy();
                    selected.setBoolean("imported", data.getBoolean("imported"));
                    selected.setBoolean("enabled", data.getBoolean("enabled"));
                    version++;
                }
            } else if (request.epoch == epoch && request.action.equals("LIST")) {
                NBTTagList values = data.getTagList("rows", 10);
                productRows = Collections.unmodifiableList(copyFrom(values));
                productOffset = data.getInteger("offset");
                productTotal = data.getInteger("total");
                rows = productRows;
                offset = productOffset;
                total = productTotal;
                version++;
            } else if (request.epoch == epoch && request.action.equals("ROUTES")) {
                NBTTagList values = data.getTagList("rows", 10);
                List<NBTTagCompound> page = new ArrayList<>();
                for (int i = 0; i < values.tagCount(); i++) {
                    NBTTagCompound row = values.getCompoundTagAt(i).copy();
                    page.add(row);
                    if (view != View.CANDIDATES && row.getString("recipe").equals(selectedRecipe))
                        selected = row.copy();
                    if (view == View.CANDIDATES && row.getString("candidate").equals(candidate))
                        candidatePriority = row.getInteger("priority");
                }
                recipeRows = Collections.unmodifiableList(page);
                rows = recipeRows;
                recipeOffset = data.getInteger("offset");
                recipeTotal = data.getInteger("total");
                offset = recipeOffset;
                total = recipeTotal;
                version++;
            } else if (request.epoch == epoch && request.action.equals("PATTERNS") && request.recipe.equals(selectedRecipe)) {
                rows = Collections.unmodifiableList(copyFrom(data.getTagList("rows", 10)));
                offset = data.getInteger("offset");
                total = data.getInteger("total");
                version++;
                if (rows.isEmpty() && total > 0 && offset >= total) {
                    offset = ((total - 1) / limit) * limit;
                    selectedPattern = null;
                    refresh();
                }
            }
            return;
        }
        status = state;
        if ("PAYLOAD_TOO_LARGE".equals(state) && request.epoch == epoch && request.action.equals(view.name()) && limit > 1) {
            limit = Math.max(1, limit / 2);
            refresh();
            return;
        }
        if ("ACCESS_DENIED".equals(state) || "CLOSED".equals(state)) {
            ready = false;
            clearPreview();
        }
        if (result || "PREVIEW".equals(request.action)) clearPreview();
        if ("NO_PREVIEW".equals(state)) {
            clearPreview();
            if (view == View.PREVIEW_ROUTES) show(View.IMPORTED);
        }
        if ("STALE_REVISION".equals(state) || "STALE_CATALOG".equals(state)) {
            clearPreview();
            refresh();
        }
        version++;
    }

    private static List<NBTTagCompound> copyFrom(NBTTagList values) {
        List<NBTTagCompound> result = new ArrayList<>();
        for (int i = 0; i < values.tagCount(); i++) result.add(values.getCompoundTagAt(i).copy());
        return result;
    }

    public void tick() {
        if (closed) return;
        if (session != null && session.isClosed()) {
            ready = false;
            pending.clear();
            clearPreview();
            status = "CLOSED";
            return;
        }
        long now = clock.getAsLong();
        boolean refresh = false, cancelUnknownJob = false, cancelUnknownOrder = false;
        for (Map.Entry<Long, Pending> entry : new ArrayList<>(pending.entrySet())) {
            if (entry.getValue().deadline > now) continue;
            long id = entry.getKey();
            pending.remove(id);
            session.forget(id);
            if (mutation == id) mutation = -1;
            if (preparing == id) preparing = -1;
            if (polling == id) polling = -1;
            if (orderPoll == id) orderPoll = -1;
            refresh |= entry.getValue().mutation;
            cancelUnknownJob |= "PREVIEW".equals(entry.getValue().action);
            cancelUnknownOrder |= "SUBMIT_ORDER".equals(entry.getValue().action);
            status = "TIMEOUT";
        }
        if (cancelUnknownJob && ready()) mutation = send(action("CANCEL_PREVIEW"), true);
        if (cancelUnknownOrder && ready() && orderJob == null) mutation = send(action("CANCEL_ORDER"), true);
        if (refresh && ready()) refresh();
        if (ready() && previewJob != null && preview == null && polling < 0 && now >= nextPoll) {
            NBTTagCompound request = action("POLL_PREVIEW");
            request.setUniqueId("preview", previewJob);
            polling = send(request, false);
        }
        if (ready() && orderJob != null && orderPoll < 0 && now >= nextPoll) {
            NBTTagCompound request = action("POLL_ORDER");
            request.setUniqueId("orderJob", orderJob);
            orderPoll = send(request, false);
        }
    }

    private void clearPreview() {
        preview = null;
        previewJob = null;
        polling = -1;
        if (view == View.PREVIEW_ROUTES) {
            view = View.IMPORTED;
            offset = 0;
            rows = Collections.emptyList();
            version++;
        }
        for (Map.Entry<Long, Pending> entry : new ArrayList<>(pending.entrySet())) {
            if ("POLL_PREVIEW".equals(entry.getValue().action) || "PREVIEW_ROUTES".equals(entry.getValue().action)) {
                session.forget(entry.getKey());
                pending.remove(entry.getKey());
            }
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        pending.clear();
        clearPreview();
        if (session != null) session.close();
    }

    private static final class Pending {
        final String action, recipe;
        final long epoch, deadline;
        final boolean mutation;
        final UUID job;

        Pending(String action, long epoch, String recipe, long deadline, boolean mutation, UUID job) {
            this.action = action;
            this.epoch = epoch;
            this.recipe = recipe;
            this.deadline = deadline;
            this.mutation = mutation;
            this.job = job;
        }
    }
}

