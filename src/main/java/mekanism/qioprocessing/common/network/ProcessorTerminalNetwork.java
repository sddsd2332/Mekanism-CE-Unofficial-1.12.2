package mekanism.qioprocessing.common.network;

import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.common.util.WorldDirectoryUtils;
import mekanism.qioprocessing.common.planning.QIOWorkbenchOrderPreparation;
import mekanism.qioprocessing.common.processor.QIOCraftingProcessorTerminalSession;
import mekanism.qioprocessing.common.recipe.RecipeDeclaration;
import mekanism.qioprocessing.common.recipe.RecipeDirectoryService;
import mekanism.qioprocessing.common.recipe.RecipeItemKey;
import mekanism.qioprocessing.common.terminal.*;
import mekanism.qioprocessing.common.workbench.WorkbenchBookStore;
import mekanism.qioprocessing.common.workbench.WorkbenchRuntime;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.items.ItemHandlerHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Main-thread server handler for the Processor terminal session protocol.
 */
public final class ProcessorTerminalNetwork {
    private static final Map<UUID, QIOCraftingProcessorTerminalSession> SESSIONS = new HashMap<>();

    private ProcessorTerminalNetwork() {
    }

    public static synchronized void handle(EntityPlayerMP player, ProcessorTerminalPacket packet) {
        if (player == null || packet == null || packet.window != player.openContainer.windowId
                || !(player.openContainer instanceof MekanismTileContainer)) return;
        MekanismTileContainer<?> container = (MekanismTileContainer<?>) player.openContainer;
        if (!(container.getTileEntity() instanceof QIOProcessingTerminalAccess terminal)
                || !container.canInteractWith(player)) return;
        NBTTagCompound data = packet.data();
        String channel = data.getString("channel");
        if (!"qio_processor_terminal".equals(channel)) return;
        String action = data.getString("action");
        long tick = player.world.getTotalWorldTime();
        UUID requestId = UUID.nameUUIDFromBytes((player.getUniqueID() + ":" + packet.request)
                .getBytes(StandardCharsets.UTF_8));
        if ("BIND".equals(action)) {
            if (packet.token != null) return;
            try {
                if (terminal.endpointReference() == null && !terminal.bindCurrentFrequency(player.getUniqueID())) {
                    reply(player, packet, null, response("BIND_REQUIRED"));
                    return;
                }
                if (!QIOFrequencyStorageAccess.INSTANCE.canAccess(terminal.endpointReference(),
                        player.getUniqueID())) {
                    reply(player, packet, null, response("ACCESS_DENIED"));
                    return;
                }
                NBTTagCompound bound = response("BOUND");
                bound.setTag("reference", terminal.endpointReference().write());
                bound.setLong("configurationRevision", terminal.configurationRevision());
                reply(player, packet, null, bound);
            } catch (RuntimeException ignored) {
                reply(player, packet, null, response("ACCESS_DENIED"));
            }
            return;
        }
        if ("OPEN".equals(action)) {
            if (packet.token != null || !data.hasKey("reference", 10)) return;
            try {
                mekanism.api.qio.external.QIOFrequencyReference reference =
                        mekanism.api.qio.external.QIOFrequencyReference.read(data.getCompoundTag("reference"));
                if (!player.getUniqueID().equals(reference.getBindingPlayerUUID())
                        || !reference.equals(terminal.endpointReference())
                        || !QIOFrequencyStorageAccess.INSTANCE.canAccess(reference, player.getUniqueID())) return;
                QIOCraftingProcessorTerminalSession old = SESSIONS.remove(player.getUniqueID());
                if (old != null) old.close();
                QIOCraftingProcessorTerminalSession session = QIOCraftingProcessorTerminalSession.open(
                        player.getUniqueID(), packet.window, reference, tick, true);
                SESSIONS.put(player.getUniqueID(), session);
                reply(player, packet, session.sessionId(), response("OPENED"));
            } catch (RuntimeException ignored) {
                reply(player, packet, null, response("ACCESS_DENIED"));
            }
            return;
        }
        QIOCraftingProcessorTerminalSession session = packet.token == null ? null : SESSIONS.get(player.getUniqueID());
        if (session == null || !session.sessionId().equals(packet.token)) {
            reply(player, packet, null, response("INVALID_SESSION"));
            return;
        }
        if ("CLOSE".equals(action)) {
            session.close();
            SESSIONS.remove(player.getUniqueID());
            reply(player, packet, packet.token, response("CLOSED"));
        } else if (!session.matchesReference(terminal.endpointReference())
                || !QIOFrequencyStorageAccess.INSTANCE.canAccess(session.reference(), player.getUniqueID())) {
            reply(player, packet, packet.token, response("ACCESS_DENIED"));
        } else if ("LIST".equals(action)) {
            QIOCraftingProcessorTerminalSession.Response result = session.list(requestId, player.getUniqueID(),
                    packet.window, data.getLong("cursor"), data.getInteger("limit"), tick);
            reply(player, packet, packet.token, page(result));
        } else if ("DEVICE_LIST".equals(action)
                && terminal.terminalType() == mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MANAGEMENT) {
            QIOCraftingProcessorTerminalSession.Response result = session.runExternalResponse(requestId,
                    player.getUniqueID(), packet.window, tick, () -> devicePage(player,
                            session.reference(), data));
            reply(player, packet, packet.token, response(result));
        } else if ("MAINTENANCE_LIST".equals(action)
                && terminal.terminalType() == mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MAINTENANCE) {
            QIOCraftingProcessorTerminalSession.Response result = session.runExternalResponse(requestId,
                    player.getUniqueID(), packet.window, tick, () -> maintenancePage(player, session.reference(), data));
            reply(player, packet, packet.token, response(result));
        } else if ("SMART_RESOURCE_LIST".equals(action)
                && terminal.terminalType() == mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.SMART_PROCESSING) {
            QIOCraftingProcessorTerminalSession.Response result = session.runExternalResponse(requestId,
                    player.getUniqueID(), packet.window, tick, () -> maintenancePage(player, session.reference(), data));
            reply(player, packet, packet.token, response(result));
        } else if ("SMART_RESOURCE_TAKE".equals(action)
                && terminal.terminalType() == mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.SMART_PROCESSING) {
            QIOCraftingProcessorTerminalSession.Response result = session.runExternalResponse(requestId,
                    player.getUniqueID(), packet.window, tick,
                    () -> smartResourceTake(player, session.reference(), data, requestId));
            reply(player, packet, packet.token, response(result));
        } else if ("MAINTENANCE_RULE_LIST".equals(action)
                && terminal.terminalType() == mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MAINTENANCE) {
            QIOCraftingProcessorTerminalSession.Response result = session.runExternalResponse(requestId,
                    player.getUniqueID(), packet.window, tick, () -> maintenanceRulePage(player, session.reference()));
            reply(player, packet, packet.token, response(result));
        } else if (("MAINTENANCE_RULE_UPSERT".equals(action)
                || "MAINTENANCE_RULE_DELETE".equals(action)
                || "MAINTENANCE_RULE_TOGGLE".equals(action))
                && terminal.terminalType() == mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType.MAINTENANCE) {
            QIOCraftingProcessorTerminalSession.Response result = session.runExternalResponse(requestId,
                    player.getUniqueID(), packet.window, tick, () -> maintenanceRuleMutation(player,
                            session.reference(), action, data));
            reply(player, packet, packet.token, response(result));
        } else if ("CANCEL".equals(action) || "RECONCILE".equals(action)) {
            UUID processor = uuid(data, "processor");
            UUID operation = uuid(data, "operation");
            long endpointRevision = data.getLong("endpointRevision");
            QIOCraftingProcessorTerminalSession.Response result = "CANCEL".equals(action)
                    ? session.cancel(requestId, player.getUniqueID(), packet.window, processor,
                    endpointRevision, operation, data.getString("reason"), tick)
                    : session.reconcile(requestId, player.getUniqueID(), packet.window, processor,
                    endpointRevision, operation, data.getString("reason"), tick);
            reply(player, packet, packet.token, response(result.status().name()));
        } else if ("SMART_SET_SLOT".equals(action)
                && terminal instanceof QIOProcessingSmartTerminalAccess smart) {
            net.minecraft.item.ItemStack value = data.hasKey("item", 10)
                    ? new net.minecraft.item.ItemStack(data.getCompoundTag("item"))
                    : net.minecraft.item.ItemStack.EMPTY;
            QIOCraftingProcessorTerminalSession.Response result = session.setSmartSlot(requestId,
                    player.getUniqueID(), packet.window, smart, data.getLong("configurationRevision"),
                    data.getInteger("craftingWindow"), data.getInteger("slot"), value, tick);
            reply(player, packet, packet.token, response(result.status().name()));
        } else if ("SMART_SAVE_PATTERN".equals(action)
                && terminal instanceof QIOProcessingSmartTerminalAccess smart) {
            if (!session.matchesReference(smart.endpointReference())
                    || RecipeDirectoryService.INSTANCE.state() != RecipeDirectoryService.State.READY
                    || smart.configurationRevision() != data.getLong("configurationRevision")) {
                reply(player, packet, packet.token, response("CONFLICT"));
                return;
            }
            UUID patternId = uuid(data, "pattern");
            QIOCraftingProcessorTerminalSession.Response result = session.runExternal(requestId,
                    player.getUniqueID(), packet.window, tick, () -> {
                        try {
                            Path worldDirectory = requireOverworldDirectory(player);
                            mekanism.qioprocessing.common.recipe.RecipeCatalog catalog =
                                    RecipeDirectoryService.INSTANCE.catalog();
                            RecipeDeclaration declaration = catalog.byId(data.getString("recipe"));
                            if (declaration == null && data.getInteger("craftingWindow") >= 0
                                    && data.getInteger("craftingWindow") < 3) {
                                RecipeItemKey output = smart.craftingOutputKey(data.getInteger("craftingWindow"));
                                java.util.List<RecipeDeclaration> matches = output == null
                                        ? java.util.Collections.emptyList() : catalog.exactOutput(output);
                                if (matches.size() == 1) declaration = matches.get(0);
                            }
                            QIOProcessingSmartPatternService.SaveResult saved =
                                    QIOProcessingSmartPatternService.saveToFrequencyBook(smart,
                                            data.getInteger("craftingWindow"),
                                            declaration, catalog, patternId,
                                            data.getLong("bookRevision"), player.getUniqueID(),
                                            new WorkbenchBookStore(worldDirectory.resolve("mekanism/qio_processing/workbench_v1")));
                            return saveStatus(saved.status());
                        } catch (java.io.IOException error) {
                            return QIOCraftingProcessorTerminalSession.Status.REQUIRES_RECONCILIATION;
                        }
                    });
            reply(player, packet, packet.token, response(result));
        } else if ("SMART_CONTEXT".equals(action)
                && terminal instanceof QIOProcessingSmartTerminalAccess smart) {
            QIOCraftingProcessorTerminalSession.Response result = session.runExternalResponse(requestId,
                    player.getUniqueID(), packet.window, tick, () -> {
                        try {
                            Path worldDirectory = requireOverworldDirectory(player);
                            WorkbenchBookStore store = new WorkbenchBookStore(
                                    worldDirectory.resolve("mekanism/qio_processing/workbench_v1"));
                            mekanism.qioprocessing.common.workbench.WorkbenchSnapshot book =
                                    store.load(session.reference().getFrequencyUUID());
                            NBTTagCompound payload = new NBTTagCompound();
                            payload.setLong("bookRevision", book.revision());
                            payload.setLong("configurationRevision", smart.configurationRevision());
                            payload.setBoolean("recipeReady", RecipeDirectoryService.INSTANCE.state()
                                    == RecipeDirectoryService.State.READY);
                            return QIOCraftingProcessorTerminalSession.external(
                                    QIOCraftingProcessorTerminalSession.Status.OK, null, "READY", payload);
                        } catch (java.io.IOException error) {
                            return QIOCraftingProcessorTerminalSession.external(
                                    QIOCraftingProcessorTerminalSession.Status.REQUIRES_RECONCILIATION, null,
                                    error.getMessage());
                        }
                    });
            reply(player, packet, packet.token, response(result));
        } else if ("SMART_ORDER_START".equals(action)
                && terminal instanceof QIOProcessingSmartTerminalAccess smart) {
            if (!session.matchesReference(smart.endpointReference())
                    || smart.configurationRevision() != data.getLong("configurationRevision")
                    || !data.hasKey("target", 10) || data.getLong("amount") <= 0) {
                reply(player, packet, packet.token, response("CONFLICT"));
                return;
            }
            QIOCraftingProcessorTerminalSession.Response result = session.runExternalResponse(requestId,
                    player.getUniqueID(), packet.window, tick, () -> {
                        try {
                            Map<RecipeItemKey, Long> targets = new java.util.LinkedHashMap<>();
                            targets.put(RecipeItemKey.capture(new net.minecraft.item.ItemStack(
                                    data.getCompoundTag("target"))), data.getLong("amount"));
                            UUID order = WorkbenchRuntime.startSmartProcessingOrder(player.getServer(), player,
                                    session.reference(), targets, data.getLong("bookRevision"));
                            return QIOCraftingProcessorTerminalSession.external(
                                    QIOCraftingProcessorTerminalSession.Status.OK, order, "STARTED");
                        } catch (RuntimeException | java.io.IOException error) {
                            return QIOCraftingProcessorTerminalSession.external(
                                    QIOCraftingProcessorTerminalSession.Status.REQUIRES_RECONCILIATION, null,
                                    error.getMessage());
                        }
                    });
            reply(player, packet, packet.token, response(result.status().name(), result.orderId(), result.detail()));
        } else if ("SMART_ORDER_POLL".equals(action)
                && terminal instanceof QIOProcessingSmartTerminalAccess smart) {
            if (!session.matchesReference(smart.endpointReference())
                    || smart.configurationRevision() != data.getLong("configurationRevision")) {
                reply(player, packet, packet.token, response("CONFLICT"));
                return;
            }
            UUID orderId = uuid(data, "order");
            QIOCraftingProcessorTerminalSession.Response result = session.runExternalResponse(requestId,
                    player.getUniqueID(), packet.window, tick, () -> {
                        try {
                            QIOWorkbenchOrderPreparation.Prepared prepared = WorkbenchRuntime.pollSmartProcessingOrder(
                                    player.getServer(), player, orderId);
                            return QIOCraftingProcessorTerminalSession.external(
                                    QIOCraftingProcessorTerminalSession.Status.OK, orderId, prepared.status().name());
                        } catch (RuntimeException | java.io.IOException error) {
                            return QIOCraftingProcessorTerminalSession.external(
                                    QIOCraftingProcessorTerminalSession.Status.REQUIRES_RECONCILIATION, orderId,
                                    error.getMessage());
                        }
                    });
            reply(player, packet, packet.token, response(result.status().name(), result.orderId(), result.detail()));
        } else if ("SMART_ORDER_CANCEL".equals(action)
                && terminal instanceof QIOProcessingSmartTerminalAccess smart) {
            if (!session.matchesReference(smart.endpointReference())
                    || smart.configurationRevision() != data.getLong("configurationRevision")) {
                reply(player, packet, packet.token, response("CONFLICT"));
                return;
            }
            UUID orderId = uuid(data, "order");
            QIOCraftingProcessorTerminalSession.Response result = session.runExternalResponse(requestId,
                    player.getUniqueID(), packet.window, tick, () -> {
                        try {
                            String cancellation = WorkbenchRuntime.cancelSmartProcessingOrder(player.getServer(), player, orderId);
                            return QIOCraftingProcessorTerminalSession.external(
                                    QIOCraftingProcessorTerminalSession.Status.OK, orderId, cancellation);
                        } catch (RuntimeException | java.io.IOException error) {
                            return QIOCraftingProcessorTerminalSession.external(
                                    QIOCraftingProcessorTerminalSession.Status.CONFLICT, orderId, error.getMessage());
                        }
                    });
            reply(player, packet, packet.token, response(result.status().name(), result.orderId(), result.detail()));
        }
    }

    private static void reply(EntityPlayerMP player, ProcessorTerminalPacket request, UUID token,
                              NBTTagCompound data) {
        QIOProcessingPacketHandler.INSTANCE.sendTo(new ProcessorTerminalPacket(request.window,
                request.request, token, data), player);
    }

    private static NBTTagCompound response(String status) {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("status", status);
        return data;
    }

    private static NBTTagCompound response(String status, UUID orderId, String detail) {
        NBTTagCompound data = response(status);
        if (orderId != null) data.setUniqueId("order", orderId);
        if (detail != null && !detail.isEmpty()) data.setString("detail", detail);
        return data;
    }

    private static NBTTagCompound response(QIOCraftingProcessorTerminalSession.Response result) {
        NBTTagCompound data = response(result.status().name(), result.orderId(), result.detail());
        data.merge(result.payload());
        return data;
    }

    private static QIOCraftingProcessorTerminalSession.Status saveStatus(
            QIOProcessingSmartPatternService.SaveStatus status) {
        switch (status) {
            case SAVED:
            case ALREADY_PRESENT:
                return QIOCraftingProcessorTerminalSession.Status.OK;
            case NOT_AUTHORIZED:
                return QIOCraftingProcessorTerminalSession.Status.ACCESS_DENIED;
            case STALE_BOOK:
                return QIOCraftingProcessorTerminalSession.Status.CONFLICT;
            default:
                return QIOCraftingProcessorTerminalSession.Status.INVALID_EDIT;
        }
    }

    private static NBTTagCompound page(QIOCraftingProcessorTerminalSession.Response page) {
        NBTTagCompound data = response(page.status().name());
        data.setLong("nextCursor", page.nextCursor());
        net.minecraft.nbt.NBTTagList snapshots = new net.minecraft.nbt.NBTTagList();
        page.snapshots().forEach(snapshot -> snapshots.appendTag(snapshot.write()));
        data.setTag("snapshots", snapshots);
        return data;
    }

    private static QIOCraftingProcessorTerminalSession.Response devicePage(EntityPlayerMP player,
                                                                           mekanism.api.qio.external.QIOFrequencyReference reference, NBTTagCompound data) {
        int limit = Math.max(1, Math.min(32, data.getInteger("limit")));
        long cursor = data.getLong("cursor");
        if (cursor < 0 || cursor > Integer.MAX_VALUE) {
            return QIOCraftingProcessorTerminalSession.external(
                    QIOCraftingProcessorTerminalSession.Status.INVALID_EDIT, null, "INVALID_CURSOR");
        }
        mekanism.qioprocessing.common.planning.QIOProviderRouteCatalog.Snapshot snapshot =
                WorkbenchRuntime.providerRouteSnapshot(player.getServer(), reference.getFrequencyUUID());
        java.util.List<mekanism.qioprocessing.common.planning.QIOProviderRouteCatalog.SourceSnapshot> sources =
                snapshot.sources();
        if (cursor > sources.size()) {
            return QIOCraftingProcessorTerminalSession.external(
                    QIOCraftingProcessorTerminalSession.Status.INVALID_EDIT, null, "INVALID_CURSOR");
        }
        int start = (int) cursor;
        int end = Math.min(sources.size(), start + limit);
        NBTTagCompound payload = new NBTTagCompound();
        net.minecraft.nbt.NBTTagList rows = new net.minecraft.nbt.NBTTagList();
        for (int index = start; index < end; index++) {
            mekanism.qioprocessing.common.planning.QIOProviderRouteCatalog.SourceSnapshot source = sources.get(index);
            NBTTagCompound row = new NBTTagCompound();
            row.setString("source", source.sourceId());
            row.setString("provider", source.providerId().toString());
            row.setInteger("routes", source.routes().size());
            row.setInteger("ports", source.ports().size());
            row.setInteger("configurationRevision", source.configurationRevision());
            net.minecraft.nbt.NBTTagList routeIds = new net.minecraft.nbt.NBTTagList();
            source.routes().stream().limit(128).forEach(route -> {
                NBTTagCompound routeTag = new NBTTagCompound();
                routeTag.setString("id", route.routeId());
                routeTag.setString("recipe", route.recipeKey());
                routeIds.appendTag(routeTag);
            });
            row.setTag("routeIds", routeIds);
            net.minecraft.nbt.NBTTagList portIds = new net.minecraft.nbt.NBTTagList();
            source.ports().stream().limit(64).forEach(port -> {
                NBTTagCompound portTag = new NBTTagCompound();
                portTag.setString("id", port.portId());
                portIds.appendTag(portTag);
            });
            row.setTag("portIds", portIds);
            rows.appendTag(row);
        }
        payload.setTag("devices", rows);
        payload.setLong("revision", snapshot.revision());
        payload.setLong("nextCursor", end < sources.size() ? end : -1);
        return QIOCraftingProcessorTerminalSession.external(
                QIOCraftingProcessorTerminalSession.Status.OK, null, "OK", payload);
    }

    private static QIOCraftingProcessorTerminalSession.Response maintenancePage(EntityPlayerMP player,
                                                                                mekanism.api.qio.external.QIOFrequencyReference reference, NBTTagCompound data) {
        int limit = Math.max(1, Math.min(32, data.getInteger("limit")));
        long cursor = data.getLong("cursor");
        if (cursor < 0 || cursor > Integer.MAX_VALUE) {
            return QIOCraftingProcessorTerminalSession.external(
                    QIOCraftingProcessorTerminalSession.Status.INVALID_EDIT, null, "INVALID_CURSOR");
        }
        mekanism.api.qio.external.IQIOStorageView view =
                QIOFrequencyStorageAccess.INSTANCE.open(reference, player.getUniqueID());
        if (view == null) return QIOCraftingProcessorTerminalSession.external(
                QIOCraftingProcessorTerminalSession.Status.ACCESS_DENIED, null, "ACCESS_DENIED");
        try {
            java.util.List<mekanism.api.qio.external.QIOStorageEntry> entries =
                    new java.util.ArrayList<>(view.getSnapshot().getEntries());
            entries.sort(java.util.Comparator.comparing(value -> value.getDescriptor().toString()));
            String search = data.getString("search");
            if (search != null && !search.trim().isEmpty()) {
                String query = search.trim().toLowerCase(java.util.Locale.ROOT);
                if (query.length() > 50) return QIOCraftingProcessorTerminalSession.external(
                        QIOCraftingProcessorTerminalSession.Status.INVALID_EDIT, null, "SEARCH_TOO_LONG");
                entries.removeIf(value -> !value.getDescriptor().toString().toLowerCase(java.util.Locale.ROOT)
                        .contains(query));
            }
            if (cursor > entries.size()) return QIOCraftingProcessorTerminalSession.external(
                    QIOCraftingProcessorTerminalSession.Status.INVALID_EDIT, null, "INVALID_CURSOR");
            int start = (int) cursor;
            int end = Math.min(entries.size(), start + limit);
            NBTTagCompound payload = new NBTTagCompound();
            net.minecraft.nbt.NBTTagList rows = new net.minecraft.nbt.NBTTagList();
            for (int index = start; index < end; index++) {
                mekanism.api.qio.external.QIOStorageEntry entry = entries.get(index);
                NBTTagCompound row = new NBTTagCompound();
                row.setUniqueId("resource", entry.getResourceUUID());
                row.setTag("descriptor", entry.getDescriptor().write());
                row.setString("available", entry.getExactAvailableAmount().toString());
                rows.appendTag(row);
            }
            payload.setTag("resources", rows);
            payload.setLong("revision", view.getSnapshot().getContentsRevision());
            payload.setLong("nextCursor", end < entries.size() ? end : -1);
            return QIOCraftingProcessorTerminalSession.external(
                    QIOCraftingProcessorTerminalSession.Status.OK, null, "OK", payload);
        } finally {
            view.close();
        }
    }

    private static QIOCraftingProcessorTerminalSession.Response smartResourceTake(EntityPlayerMP player,
                                                                                  mekanism.api.qio.external.QIOFrequencyReference reference, NBTTagCompound data, UUID transferId) {
        final String pendingKey = "qioProcessingPendingSmartTransfer";
        if (player.getEntityData().hasKey(pendingKey, 10)) return QIOCraftingProcessorTerminalSession.external(
                QIOCraftingProcessorTerminalSession.Status.REQUIRES_RECONCILIATION, null,
                "PENDING_TRANSFER_REQUIRES_COORDINATION");
        if (!data.hasUniqueId("resource")) return QIOCraftingProcessorTerminalSession.external(
                QIOCraftingProcessorTerminalSession.Status.INVALID_EDIT, null, "INVALID_RESOURCE");
        long requested = data.getLong("amount");
        if (requested <= 0 || requested > 64) return QIOCraftingProcessorTerminalSession.external(
                QIOCraftingProcessorTerminalSession.Status.INVALID_EDIT, null, "INVALID_AMOUNT");
        mekanism.api.qio.external.IQIOStorageView view =
                QIOFrequencyStorageAccess.INSTANCE.open(reference, player.getUniqueID());
        if (view == null) return QIOCraftingProcessorTerminalSession.external(
                QIOCraftingProcessorTerminalSession.Status.ACCESS_DENIED, null, "ACCESS_DENIED");
        try {
            mekanism.api.qio.external.QIOStorageEntry entry = view.getResource(data.getUniqueId("resource"));
            if (entry == null) return QIOCraftingProcessorTerminalSession.external(
                    QIOCraftingProcessorTerminalSession.Status.NOT_FOUND, null, "RESOURCE_NOT_FOUND");
            net.minecraft.item.ItemStack template = entry.getItem();
            if (template.isEmpty()) return QIOCraftingProcessorTerminalSession.external(
                    QIOCraftingProcessorTerminalSession.Status.INVALID_EDIT, null, "ITEM_ONLY");
            net.minecraft.item.ItemStack held = player.inventory.getItemStack();
            if (!held.isEmpty() && !ItemHandlerHelper.canItemStacksStack(template, held)) {
                return QIOCraftingProcessorTerminalSession.external(
                        QIOCraftingProcessorTerminalSession.Status.INVALID_EDIT, null, "CURSOR_INCOMPATIBLE");
            }
            long capacity = held.isEmpty() ? template.getMaxStackSize()
                    : template.getMaxStackSize() - held.getCount();
            long amount = Math.min(requested, Math.max(0, capacity));
            if (amount <= 0) return QIOCraftingProcessorTerminalSession.external(
                    QIOCraftingProcessorTerminalSession.Status.INVALID_EDIT, null, "CURSOR_FULL");
            NBTTagCompound pending = new NBTTagCompound();
            pending.setUniqueId("transfer", transferId);
            pending.setUniqueId("resource", data.getUniqueId("resource"));
            pending.setLong("amount", amount);
            pending.setString("expectedStored", entry.getExactStoredAmount().toString());
            pending.setTag("item", template.writeToNBT(new NBTTagCompound()));
            player.getEntityData().setTag(pendingKey, pending);
            mekanism.api.qio.external.QIOTransferResult transfer = view.extractIdempotent(
                    transferId, template, amount, entry.getExactStoredAmount());
            if (!transfer.isSuccess() || transfer.getTransferredAmount() <= 0) {
                if (transfer.getStatus() == mekanism.api.qio.external.QIOTransferResult.Status.FAILED
                        || transfer.getStatus() == mekanism.api.qio.external.QIOTransferResult.Status.INVALID_REQUEST) {
                    player.getEntityData().removeTag(pendingKey);
                }
                return QIOCraftingProcessorTerminalSession.external(
                        QIOCraftingProcessorTerminalSession.Status.REQUIRES_RECONCILIATION, null,
                        "TRANSFER_" + transfer.getStatus().name());
            }
            long extracted = transfer.getTransferredAmount();
            int count = (int) Math.min(Integer.MAX_VALUE, extracted);
            if (held.isEmpty()) {
                net.minecraft.item.ItemStack result = template.copy();
                result.setCount(count);
                player.inventory.setItemStack(result);
            } else {
                held.grow(count);
            }
            player.getEntityData().removeTag(pendingKey);
            NBTTagCompound payload = new NBTTagCompound();
            payload.setLong("extracted", extracted);
            return QIOCraftingProcessorTerminalSession.external(
                    QIOCraftingProcessorTerminalSession.Status.OK, null, "OK", payload);
        } finally {
            view.close();
        }
    }

    private static QIOCraftingProcessorTerminalSession.Response maintenanceRulePage(EntityPlayerMP player,
                                                                                    mekanism.api.qio.external.QIOFrequencyReference reference) {
        try {
            QIOProcessingMaintenanceBook book = maintenanceStore(player).load(reference.getFrequencyUUID());
            NBTTagCompound payload = new NBTTagCompound();
            payload.setLong("revision", book.revision());
            NBTTagList rows = new NBTTagList();
            for (QIOProcessingMaintenanceBook.Rule rule : book.rules().values()) {
                NBTTagCompound row = rule.writeForNetwork();
                rows.appendTag(row);
            }
            payload.setTag("rules", rows);
            return QIOCraftingProcessorTerminalSession.external(
                    QIOCraftingProcessorTerminalSession.Status.OK, null, "OK", payload);
        } catch (IOException error) {
            return QIOCraftingProcessorTerminalSession.external(
                    QIOCraftingProcessorTerminalSession.Status.REQUIRES_RECONCILIATION, null, error.getMessage());
        }
    }

    private static QIOCraftingProcessorTerminalSession.Response maintenanceRuleMutation(EntityPlayerMP player,
                                                                                        mekanism.api.qio.external.QIOFrequencyReference reference, String action, NBTTagCompound data) {
        try {
            QIOProcessingMaintenanceBookStore store = maintenanceStore(player);
            QIOProcessingMaintenanceBook book = store.load(reference.getFrequencyUUID());
            long expected = data.getLong("revision");
            QIOProcessingMaintenanceBook next;
            UUID ruleId = data.hasUniqueId("rule") ? data.getUniqueId("rule") : UUID.randomUUID();
            if ("MAINTENANCE_RULE_UPSERT".equals(action)) {
                if (!data.hasKey("descriptor", 10) || !data.hasKey("target", 8)
                        || data.getLong("batch") <= 0) return QIOCraftingProcessorTerminalSession.external(
                        QIOCraftingProcessorTerminalSession.Status.INVALID_EDIT, null, "INVALID_RULE");
                java.math.BigInteger target;
                try {
                    target = new java.math.BigInteger(data.getString("target"));
                } catch (RuntimeException error) {
                    return QIOCraftingProcessorTerminalSession.external(
                            QIOCraftingProcessorTerminalSession.Status.INVALID_EDIT, null, "INVALID_TARGET");
                }
                next = book.upsert(expected, new QIOProcessingMaintenanceBook.Rule(ruleId,
                        mekanism.api.qio.resource.QIOResourceDescriptor.read(data.getCompoundTag("descriptor")),
                        target, data.getLong("batch"), data.getBoolean("enabled")));
            } else if ("MAINTENANCE_RULE_DELETE".equals(action)) {
                next = book.remove(expected, ruleId);
            } else {
                next = book.toggle(expected, ruleId, data.getBoolean("enabled"));
            }
            store.save(next);
            NBTTagCompound payload = new NBTTagCompound();
            payload.setLong("revision", next.revision());
            payload.setUniqueId("rule", ruleId);
            return QIOCraftingProcessorTerminalSession.external(
                    QIOCraftingProcessorTerminalSession.Status.OK, ruleId, "OK", payload);
        } catch (IllegalStateException | IllegalArgumentException error) {
            return QIOCraftingProcessorTerminalSession.external(
                    QIOCraftingProcessorTerminalSession.Status.CONFLICT, null, error.getMessage());
        } catch (IOException error) {
            return QIOCraftingProcessorTerminalSession.external(
                    QIOCraftingProcessorTerminalSession.Status.REQUIRES_RECONCILIATION, null, error.getMessage());
        }
    }

    private static QIOProcessingMaintenanceBookStore maintenanceStore(EntityPlayerMP player) throws IOException {
        Path worldDirectory = requireOverworldDirectory(player);
        return new QIOProcessingMaintenanceBookStore(
                worldDirectory.resolve("mekanism/qio_processing/maintenance_v1"));
    }

    private static Path requireOverworldDirectory(EntityPlayerMP player) throws IOException {
        if (player == null || player.getServer() == null) {
            throw new IOException("The server overworld is unavailable");
        }
        java.io.File worldDirectory = WorldDirectoryUtils.getWorldDirectory(player.getServer().getWorld(0));
        if (worldDirectory == null) {
            throw new IOException("The server overworld has no persistent save directory");
        }
        return worldDirectory.toPath();
    }

    private static UUID uuid(NBTTagCompound data, String key) {
        return data.hasUniqueId(key) ? data.getUniqueId(key) : new UUID(0, 0);
    }
}
