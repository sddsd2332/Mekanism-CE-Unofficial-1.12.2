package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import mekanism.common.TestBootstrap;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeProfileMutation;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeProfileMutation.Action;
import mekanism.qioprocessing.common.content.policy.QIOPolicyCatalog;
import mekanism.qioprocessing.common.content.policy.QIOPolicyEntrySnapshot;
import mekanism.qioprocessing.common.content.policy.QIOPolicyMutation;
import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceRule;
import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceRuleMutation;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorEntry;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorPlanEntry;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorRuntimeSnapshot;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorService;
import mekanism.qioprocessing.common.terminal.QIODeviceCommandResult;
import mekanism.qioprocessing.common.terminal.QIODeviceCommandService;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingPreviewSnapshot;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingResourceEntry;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingResourceFilter;
import mekanism.qioprocessing.common.terminal.QIOManagementPolicyService;
import mekanism.qioprocessing.common.terminal.QIOManagementDeviceGroupSnapshot;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeService.ProductFilter;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot.PageKind;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot.Product;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot.ResourceAmount;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot.Route;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfile.RouteFilterMode;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import mekanism.qioprocessing.common.terminal.QIOPagination;
import mekanism.qioprocessing.common.terminal.QIOPage;
import mekanism.qioprocessing.common.terminal.QIOPageCursor;
import mekanism.qioprocessing.api.machine.QIOAutomationDeviceLocation;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceSnapshot;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.api.processing.MachinePresentationDescriptor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.Arrays;
import java.util.Collections;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOProcessingTerminalPacketTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void blockAndPortableBindingCommandsRoundTripTheirSessionCapability() {
        FrequencyIdentity identity = new FrequencyIdentity("terminal",
              SecurityMode.TRUSTED, UUID.randomUUID());
        QIOProcessingTerminalSession block = new QIOProcessingTerminalSession(
              UUID.randomUUID(), QIOProcessingTerminalSession.TargetKind.BLOCK,
              QIOProcessingTerminalType.MANAGEMENT, UUID.randomUUID(), 3,
              null, -1);
        QIOProcessingTerminalSession portable = new QIOProcessingTerminalSession(
              UUID.randomUUID(), QIOProcessingTerminalSession.TargetKind.PORTABLE_ITEM,
              QIOProcessingTerminalType.SMART_PROCESSING, UUID.randomUUID(), 7,
              null, -1);

        assertRoundTrip(PacketQIOProcessingTerminalBinding.Message.bind(10, block,
              identity, UUID.randomUUID()));
        assertRoundTrip(PacketQIOProcessingTerminalBinding.Message.unbind(10, block));
        assertRoundTrip(PacketQIOProcessingTerminalBinding.Message.bind(11, portable,
              identity, UUID.randomUUID()));
    }

    @Test
    void malformedSessionHeadersAreRejected() {
        ByteBuf truncated = Unpooled.buffer();
        ByteBuf invalidKind = Unpooled.buffer();
        ByteBuf negativeRevision = Unpooled.buffer();
        try {
            truncated.writeBoolean(true);
            truncated.writeInt(1);
            assertInvalid(truncated);

            writeHeader(invalidKind, false, 1, 99, 0, 0);
            assertInvalid(invalidKind);

            writeHeader(negativeRevision, false, 1, 0, 0, -1);
            assertInvalid(negativeRevision);
        } finally {
            truncated.release();
            invalidKind.release();
            negativeRevision.release();
        }
    }

    @Test
    void preferenceCommandsRoundTripAndRejectOtherTerminalTypes() {
        QIOProcessingTerminalSession smart = new QIOProcessingTerminalSession(
              UUID.randomUUID(), QIOProcessingTerminalSession.TargetKind.PORTABLE_ITEM,
              QIOProcessingTerminalType.SMART_PROCESSING, UUID.randomUUID(), 9,
              null, -1);
        PacketQIOProcessingTerminalPreference.Message original =
              PacketQIOProcessingTerminalPreference.Message.create(12, smart);
        ByteBuf encoded = Unpooled.buffer();
        ByteBuf reencoded = Unpooled.buffer();
        try {
            original.toBytes(encoded);
            byte[] expected = ByteBufUtil.getBytes(encoded, encoded.readerIndex(),
                  encoded.readableBytes());
            PacketQIOProcessingTerminalPreference.Message decoded =
                  new PacketQIOProcessingTerminalPreference.Message();
            decoded.fromBytes(encoded);
            assertTrue(decoded.isValid());
            decoded.toBytes(reencoded);
            assertArrayEquals(expected, ByteBufUtil.getBytes(reencoded,
                  reencoded.readerIndex(), reencoded.readableBytes()));
        } finally {
            encoded.release();
            reencoded.release();
        }

        QIOProcessingTerminalSession management = new QIOProcessingTerminalSession(
              UUID.randomUUID(), QIOProcessingTerminalSession.TargetKind.BLOCK,
              QIOProcessingTerminalType.MANAGEMENT, UUID.randomUUID(), 1,
              null, -1);
        assertFalse(PacketQIOProcessingTerminalPreference.Message.create(12,
              management).isValid());
    }

    @Test
    void managementPageRequestAndResponseRoundTripBoundedCursors() {
        QIOProcessingTerminalSession session = new QIOProcessingTerminalSession(
              UUID.randomUUID(), QIOProcessingTerminalSession.TargetKind.BLOCK,
              QIOProcessingTerminalType.MANAGEMENT, UUID.randomUUID(), 2,
              UUID.randomUUID(), 4);
        UUID nonce = session.getSessionNonce();
        QIOPageCursor cursor = new QIOPageCursor(nonce, 7, 2);
        String typeKey = "AUTOMATION_MACHINE|test:machine_scope";
        PacketQIOManagementDevicePageRequest.Message request =
              PacketQIOManagementDevicePageRequest.Message.create(15, session, 2,
                    cursor, typeKey);
        assertMessageRoundTrip(request,
              PacketQIOManagementDevicePageRequest.Message::new);

        QIOAutomationDeviceSnapshot first = snapshot(UUID.randomUUID(), 1);
        QIOAutomationDeviceSnapshot second = snapshot(UUID.randomUUID(), 2);
        QIOAutomationDeviceSnapshot third = snapshot(UUID.randomUUID(), 3);
        QIOPage<QIOAutomationDeviceSnapshot> page = QIOPagination.page(
              Arrays.asList(first, second, third), 9, nonce, null, 2, 2);
        PacketQIOManagementDevicePageData.Message response =
              PacketQIOManagementDevicePageData.Message.create(15, session, page,
                    typeKey);
        assertMessageRoundTrip(response,
              PacketQIOManagementDevicePageData.Message::new);
        UUID commandRequest = UUID.randomUUID();
        assertMessageRoundTrip(PacketQIODeviceCommand.Message.create(15, session,
              commandRequest, first.getDeviceUUID(), 9,
              first.getConfigurationRevision(), QIODeviceCommandService.Command.PAUSE),
              PacketQIODeviceCommand.Message::new);
        QIODeviceCommandResult command = new QIODeviceCommandResult(commandRequest,
              first.getDeviceUUID(), QIODeviceCommandResult.Status.ACCEPTED, 10,
              first.getConfigurationRevision() + 1, true);
        assertMessageRoundTrip(PacketQIODeviceCommandResult.Message.create(15, session, command),
              PacketQIODeviceCommandResult.Message::new);
    }

    @Test
    void managementMachineGroupsAndRemoteRecipePacketsRoundTrip() {
        UUID frequencyUUID = UUID.randomUUID();
        QIOProcessingTerminalSession session = new QIOProcessingTerminalSession(
              UUID.randomUUID(), QIOProcessingTerminalSession.TargetKind.BLOCK,
              QIOProcessingTerminalType.MANAGEMENT, UUID.randomUUID(), 4,
              frequencyUUID, 7);
        String typeKey = "AUTOMATION_MACHINE|test:machine_scope";
        NBTTagCompound groupTier = new NBTTagCompound();
        groupTier.setInteger("tier", 3);
        QIOManagementDeviceGroupSnapshot group = new QIOManagementDeviceGroupSnapshot(
              typeKey, QIOAutomationDeviceSnapshot.Kind.AUTOMATION_MACHINE,
              "mekanism:machineblock", 0,
              MachinePresentationDescriptor.of("minecraft:diamond", 0, groupTier,
                    "ultimate"), "test:machine_scope", 1, 3);
        QIOPage<QIOManagementDeviceGroupSnapshot> groupPage = QIOPagination.page(
              Collections.singletonList(group), 8, session.getSessionNonce(), null, 1, 1);
        assertMessageRoundTrip(PacketQIOManagementDeviceGroupPageRequest.Message.create(
              16, session, 1, null),
              PacketQIOManagementDeviceGroupPageRequest.Message::new);
        assertMessageRoundTrip(PacketQIOManagementDeviceGroupPageData.Message.create(
              16, session, groupPage),
              PacketQIOManagementDeviceGroupPageData.Message::new);
        PacketQIOManagementDeviceGroupPageData.Message groupMessage =
              PacketQIOManagementDeviceGroupPageData.Message.create(16, session,
                    groupPage);
        ByteBuf groupBuffer = Unpooled.buffer();
        try {
            groupMessage.toBytes(groupBuffer);
            PacketQIOManagementDeviceGroupPageData.Message decodedGroup =
                  new PacketQIOManagementDeviceGroupPageData.Message();
            decodedGroup.fromBytes(groupBuffer);
            assertTrue(decodedGroup.isValid());
            assertEquals(3, decodedGroup.getGroups().get(0).getPresentation()
                  .getItemNbt().getInteger("tier"));
        } finally {
            groupBuffer.release();
        }

        UUID deviceUUID = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        PortableResourceDescriptor iron = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "minecraft:iron_ingot", 0, null);
        PortableResourceDescriptor gold = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "minecraft:gold_ingot", 0, null);
        ResourceAmount productAmount = new ResourceAmount(gold, 1);
        Product product = new Product("product:gold", productAmount, 1, 1, 1, 0);
        QIOManagementRecipeSnapshot products = recipeSnapshot(PageKind.PRODUCTS,
              deviceUUID, Collections.singletonList(product), Collections.emptyList());
        Route route = new Route("product:gold", "route:iron_to_gold",
              "route:item_to_item", "test:iron_to_gold",
              Collections.singletonList(new ResourceAmount(iron, 1)),
              Collections.singletonList(productAmount), Collections.emptyList(),
              true, true, 0, 1, 64, false);
        QIOManagementRecipeSnapshot routes = recipeSnapshot(PageKind.ROUTES,
              deviceUUID, Collections.emptyList(), Collections.singletonList(route));

        assertMessageRoundTrip(PacketQIOManagementRecipeRequest.Message.products(
              16, session, requestId, deviceUUID, 0, 16, "gold", ProductFilter.ALL),
              PacketQIOManagementRecipeRequest.Message::new);
        assertMessageRoundTrip(PacketQIOManagementRecipeRequest.Message.routes(
              16, session, UUID.randomUUID(), deviceUUID, "product:gold", 0, 16, ""),
              PacketQIOManagementRecipeRequest.Message::new);
        assertMessageRoundTrip(PacketQIOManagementRecipeRequest.Message.mutate(
              16, session, UUID.randomUUID(), deviceUUID, 4,
              QIOAutomationRecipeProfileMutation.simple(Action.TOGGLE_PROFILE_MODE)),
              PacketQIOManagementRecipeRequest.Message::new);
        assertMessageRoundTrip(PacketQIOManagementRecipeData.Message.create(
              16, session, requestId, PacketQIOManagementRecipeRequest.Operation.PRODUCTS,
              PacketQIOManagementRecipeData.Status.OK, products),
              PacketQIOManagementRecipeData.Message::new);
        assertMessageRoundTrip(PacketQIOManagementRecipeData.Message.create(
              16, session, UUID.randomUUID(),
              PacketQIOManagementRecipeRequest.Operation.ROUTES,
              PacketQIOManagementRecipeData.Status.OK, routes),
              PacketQIOManagementRecipeData.Message::new);
        assertMessageRoundTrip(PacketQIOManagementRecipeData.Message.create(
              16, session, UUID.randomUUID(),
              PacketQIOManagementRecipeRequest.Operation.MUTATE,
              PacketQIOManagementRecipeData.Status.APPLIED, null),
              PacketQIOManagementRecipeData.Message::new);
    }

    @Test
    void managementDevicePagePreservesPresentationTierNbt() {
        UUID frequencyUUID = UUID.randomUUID();
        QIOProcessingTerminalSession session = new QIOProcessingTerminalSession(
              UUID.randomUUID(), QIOProcessingTerminalSession.TargetKind.BLOCK,
              QIOProcessingTerminalType.MANAGEMENT, UUID.randomUUID(), 4,
              frequencyUUID, 7);
        NBTTagCompound tier = new NBTTagCompound();
        tier.setInteger("tier", 3);
        QIOAutomationDeviceSnapshot device = new QIOAutomationDeviceSnapshot(
              UUID.randomUUID(), new QIOAutomationDeviceLocation(0,
                    new BlockPos(1, 64, 0)),
              QIOAutomationDeviceSnapshot.Kind.AUTOMATION_MACHINE,
              "mekanism:machineblock", 0,
              MachinePresentationDescriptor.of("minecraft:diamond", 0, tier,
                    "ultimate"), "mekanism:test", "mekanism:test_scope",
              QIOAutomationMode.SCHEDULED.name(),
              QIOAutomationHost.State.ACTIVE.name(), true, 1, 1, 1, 1, 0,
              false, null);
        QIOPage<QIOAutomationDeviceSnapshot> page = QIOPagination.page(
              Collections.singletonList(device), 1, session.getSessionNonce(), null,
              1, 1);
        PacketQIOManagementDevicePageData.Message original =
              PacketQIOManagementDevicePageData.Message.create(16, session, page,
                    "type");
        ByteBuf buffer = Unpooled.buffer();
        try {
            original.toBytes(buffer);
            PacketQIOManagementDevicePageData.Message decoded =
                  new PacketQIOManagementDevicePageData.Message();
            decoded.fromBytes(buffer);
            assertTrue(decoded.isValid());
            assertEquals(3, decoded.getDevices().get(0).getPresentation().getItemNbt()
                  .getInteger("tier"));
        } finally {
            buffer.release();
        }
    }

    @Test
    void managementPagePacketsRejectCrossSessionAndOversizedPayloads() {
        QIOProcessingTerminalSession session = new QIOProcessingTerminalSession(
              UUID.randomUUID(), QIOProcessingTerminalSession.TargetKind.BLOCK,
              QIOProcessingTerminalType.MANAGEMENT, UUID.randomUUID(), 0,
              null, -1);
        assertFalse(PacketQIOManagementDevicePageRequest.Message.create(1, session, 2,
              new QIOPageCursor(UUID.randomUUID(), 0, 0)).isValid());

        ByteBuf oversized = Unpooled.buffer();
        try {
            oversized.writeInt(1);
            oversized.writeLong(1);
            oversized.writeLong(2);
            oversized.writeLong(0);
            oversized.writeInt(0);
            oversized.writeInt(PacketQIOManagementDevicePageData.MAX_WIRE_PAGE_SIZE + 1);
            oversized.writeBoolean(false);
            oversized.writeInt(PacketQIOManagementDevicePageData.MAX_WIRE_PAGE_SIZE + 1);
            PacketQIOManagementDevicePageData.Message decoded =
                  new PacketQIOManagementDevicePageData.Message();
            decoded.fromBytes(oversized);
            assertFalse(decoded.isValid());
        } finally {
            oversized.release();
        }
    }

    @Test
    void managementPolicyPagesAndCompareAndSetMutationsRoundTrip() {
        UUID frequencyUUID = UUID.randomUUID();
        QIOProcessingTerminalSession session = new QIOProcessingTerminalSession(
              UUID.randomUUID(), QIOProcessingTerminalSession.TargetKind.BLOCK,
              QIOProcessingTerminalType.MANAGEMENT, UUID.randomUUID(), 2,
              frequencyUUID, 4);
        QIOPolicyCatalog catalog = new QIOPolicyCatalog();
        catalog.setGlobalRoutePolicy("test:provider", "route", "recipe",
              QIOPolicyCatalog.Toggle.ENABLED, 3L);
        QIOPage<QIOPolicyEntrySnapshot> page = QIOPagination.page(
              catalog.getPolicyEntries(), catalog.getRevision(),
              session.getSessionNonce(), null, 1, 1);

        assertMessageRoundTrip(PacketQIOManagementPolicyPageRequest.Message.create(
              17, session, 1, page.getNextCursor()),
              PacketQIOManagementPolicyPageRequest.Message::new);
        assertMessageRoundTrip(PacketQIOManagementPolicyPageData.Message.create(
              17, session, page),
              PacketQIOManagementPolicyPageData.Message::new);
        UUID workbenchRequestId = UUID.randomUUID();
        assertMessageRoundTrip(PacketQIOManagementPolicyPageRequest.Message.create(
              17, session, 1, null, workbenchRequestId,
              QIOManagementPolicyService.PageMode.WORKBENCH,
              QIOManagementPolicyService.WorkbenchFilter.OVERRIDDEN,
              "test:recipe", -1),
              PacketQIOManagementPolicyPageRequest.Message::new);
        assertMessageRoundTrip(PacketQIOManagementPolicyPageData.Message.create(
              17, session, workbenchRequestId,
              QIOManagementPolicyService.PageMode.WORKBENCH, 3, page),
              PacketQIOManagementPolicyPageData.Message::new);

        QIOPolicyMutation mutation = QIOPolicyMutation.globalDefault(
              QIOPolicyCatalog.Toggle.DISABLED, 8);
        UUID requestId = UUID.randomUUID();
        assertMessageRoundTrip(PacketQIOManagementPolicyMutation.Message.create(
              17, session, 0, requestId, mutation),
              PacketQIOManagementPolicyMutation.Message::new);

        QIOProcessingNetworkData network = new QIOProcessingNetworkData(frequencyUUID,
              new QIOFrequencyIdentitySnapshot("policy", null, SecurityMode.PUBLIC));
        QIOManagementPolicyService.MutationResult result =
              QIOManagementPolicyService.mutate(session, network, 4, 0, mutation);
        assertMessageRoundTrip(PacketQIOManagementPolicyMutationResult.Message.create(
              17, session, requestId, result),
              PacketQIOManagementPolicyMutationResult.Message::new);

        UUID deviceUUID = UUID.randomUUID();
        UUID lookupRequestId = UUID.randomUUID();
        assertMessageRoundTrip(PacketQIOManagementPolicyLookupRequest.Message.create(
              17, session, lookupRequestId, deviceUUID),
              PacketQIOManagementPolicyLookupRequest.Message::new);
        assertMessageRoundTrip(PacketQIOManagementPolicyLookupData.Message.create(
              17, session, lookupRequestId,
              catalog.snapshotDeviceDefault(deviceUUID)),
              PacketQIOManagementPolicyLookupData.Message::new);
    }

    @Test
    void maintenanceRulePagesAndMutationsRoundTrip() {
        UUID frequencyUUID = UUID.randomUUID();
        QIOProcessingTerminalSession session = new QIOProcessingTerminalSession(
              UUID.randomUUID(), QIOProcessingTerminalSession.TargetKind.BLOCK,
              QIOProcessingTerminalType.MAINTENANCE, UUID.randomUUID(), 2,
              frequencyUUID, 4);
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(frequencyUUID,
              new QIOFrequencyIdentitySnapshot("maintenance", null, SecurityMode.PUBLIC));
        PortableResourceDescriptor resource = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "minecraft:iron_ingot", 0, null);
        QIOMaintenanceRule rule = network.createMaintenanceRule(resource,
              UUID.randomUUID(), true, 10, 100, 50, 3, 40, 0, 8);
        QIOPage<QIOMaintenanceRule> page = QIOPagination.page(
              network.getMaintenanceRules().getRules(),
              network.getMaintenanceRules().getRulesRevision(),
              session.getSessionNonce(), null, 1, 1);

        assertMessageRoundTrip(PacketQIOMaintenanceRulePageRequest.Message.create(
              19, session, 1, null), PacketQIOMaintenanceRulePageRequest.Message::new);
        assertMessageRoundTrip(PacketQIOMaintenanceRulePageData.Message.create(
              19, session, page), PacketQIOMaintenanceRulePageData.Message::new);
        UUID requestId = UUID.randomUUID();
        QIOMaintenanceRuleMutation mutation = QIOMaintenanceRuleMutation.update(
              rule.getRuleId(), rule.getRuleRevision(), false, 20, 200, 60, 5, 80);
        assertMessageRoundTrip(PacketQIOMaintenanceRuleMutation.Message.create(
              19, session, page.getSourceRevision(), requestId, mutation),
              PacketQIOMaintenanceRuleMutation.Message::new);
        mekanism.qioprocessing.common.terminal.QIOMaintenanceRuleService.MutationResult result =
              mekanism.qioprocessing.common.terminal.QIOMaintenanceRuleService.mutate(
                    session, network, 4, page.getSourceRevision(), mutation,
                    UUID.randomUUID(), 1);
        assertMessageRoundTrip(PacketQIOMaintenanceRuleMutationResult.Message.create(
              19, session, requestId, result),
              PacketQIOMaintenanceRuleMutationResult.Message::new);

        QIOSmartProcessingResourceEntry resourceEntry =
              new QIOSmartProcessingResourceEntry(resource, 0, 0, 0, 0, true, false);
        QIOPage<QIOSmartProcessingResourceEntry> resourcePage = QIOPagination.page(
              Collections.singletonList(resourceEntry), 5, session.getSessionNonce(), null, 1, 1);
        UUID resourceRequestId = UUID.randomUUID();
        assertMessageRoundTrip(PacketQIOMaintenanceResourcePageRequest.Message.create(
              19, session, resourceRequestId, QIOSmartProcessingResourceFilter.ITEM,
              "iron", 0, 55, -1), PacketQIOMaintenanceResourcePageRequest.Message::new);
        assertMessageRoundTrip(PacketQIOMaintenanceResourcePageData.Message.create(
              19, session, resourceRequestId, QIOSmartProcessingResourceFilter.ITEM,
              "iron", resourcePage), PacketQIOMaintenanceResourcePageData.Message::new);
    }

    @Test
    void craftingMonitorPagesAndCancellationRoundTrip() {
        UUID frequencyUUID = UUID.randomUUID();
        QIOProcessingTerminalSession session = new QIOProcessingTerminalSession(
              UUID.randomUUID(), QIOProcessingTerminalSession.TargetKind.BLOCK,
              QIOProcessingTerminalType.CRAFTING_MONITOR, UUID.randomUUID(), 1,
              frequencyUUID, 3);
        QIOCraftingMonitorEntry entry = new QIOCraftingMonitorEntry(
              QIOCraftingMonitorEntry.Kind.JOB, UUID.randomUUID(), "MANUAL",
              "WAITING_MATERIALS", PortableResourceDescriptor.named(
                    PortableResourceDescriptor.Kind.ITEM, "minecraft:iron_ingot", 0, null),
              4, 0, 1, 2, 7, 3, 1, 0, 1, false, null, -1, null, null);
        QIOPage<QIOCraftingMonitorEntry> page = QIOPagination.page(
              java.util.Collections.singletonList(entry), 2, session.getSessionNonce(),
              null, 1, 1);
        assertMessageRoundTrip(PacketQIOCraftingMonitorPageRequest.Message.create(
              21, session, 1, null), PacketQIOCraftingMonitorPageRequest.Message::new);
        assertMessageRoundTrip(PacketQIOCraftingMonitorPageData.Message.create(
              21, session, page), PacketQIOCraftingMonitorPageData.Message::new);
        UUID requestId = UUID.randomUUID();
        assertMessageRoundTrip(PacketQIOCraftingMonitorCancel.Message.create(21,
              session, requestId, entry.getEntryId(), entry.getRuntimeRevision()),
              PacketQIOCraftingMonitorCancel.Message::new);
        assertMessageRoundTrip(PacketQIOCraftingMonitorCancelResult.Message.create(21,
              session, requestId, entry.getEntryId(),
              QIOCraftingMonitorService.CancelStatus.ACCEPTED),
              PacketQIOCraftingMonitorCancelResult.Message::new);

        QIOPlanStep step = new QIOPlanStep(4, QIOPlanStep.ProviderKind.WORKBENCH,
              "test:processor", "test:route", "test:recipe", "exact", "signature", 3,
              Collections.singletonMap(entry.getRootResource(), 1L),
              Collections.singletonMap(entry.getRootResource(), 1L),
              Collections.emptyMap(), Collections.emptyList());
        QIOPage<QIOCraftingMonitorPlanEntry> planPage = QIOPagination.page(
              Collections.singletonList(QIOCraftingMonitorPlanEntry.step(step)), 7,
              session.getSessionNonce(), null, 1, 1);
        assertMessageRoundTrip(PacketQIOCraftingMonitorPlanPageRequest.Message.create(
              21, session, entry.getEntryId(), 1, 1, null),
              PacketQIOCraftingMonitorPlanPageRequest.Message::new);
        assertMessageRoundTrip(PacketQIOCraftingMonitorPlanPageData.Message.create(
              21, session, entry.getEntryId(), 1, "0123456789abcdef", planPage),
              PacketQIOCraftingMonitorPlanPageData.Message::new);
        QIOCraftingMonitorRuntimeSnapshot runtime = new QIOCraftingMonitorRuntimeSnapshot(
              entry.getEntryId(), 1, "0123456789abcdef", -1, 2, true,
              0, 1, "WAITING_MATERIALS", 0, 4, 0, 20, 3, 1, 0, 1,
              Collections.singletonMap(entry.getRootResource(), 1L), false, 0, 0, 64,
              QIOCraftingMonitorRuntimeSnapshot.SlotState.UNREQUESTED,
              null, -1, Collections.emptyList());
        assertMessageRoundTrip(PacketQIOCraftingMonitorRuntimeRequest.Message.create(
              21, session, entry.getEntryId(), 1, -1, true, 0,
              Collections.singletonMap(4L, -1L)),
              PacketQIOCraftingMonitorRuntimeRequest.Message::new);
        assertMessageRoundTrip(PacketQIOCraftingMonitorRuntimeData.Message.create(
              21, session, runtime), PacketQIOCraftingMonitorRuntimeData.Message::new);
        UUID mutationRequestId = UUID.randomUUID();
        assertMessageRoundTrip(PacketQIOCraftingMonitorMutation.Message.create(
              21, session, mutationRequestId, entry.getEntryId(),
              entry.getRuntimeRevision(), PacketQIOCraftingMonitorMutation.Action.PRIORITY, 9),
              PacketQIOCraftingMonitorMutation.Message::new);
        assertMessageRoundTrip(PacketQIOCraftingMonitorMutationResult.Message.create(
              21, session, mutationRequestId, entry.getEntryId(),
              PacketQIOCraftingMonitorMutation.Status.ACCEPTED),
              PacketQIOCraftingMonitorMutationResult.Message::new);
    }

    @Test
    void smartProcessingResourceAndPreviewPacketsRoundTrip() {
        UUID frequencyUUID = UUID.randomUUID();
        QIOProcessingTerminalSession session = new QIOProcessingTerminalSession(
              UUID.randomUUID(), QIOProcessingTerminalSession.TargetKind.PORTABLE_ITEM,
              QIOProcessingTerminalType.SMART_PROCESSING, UUID.randomUUID(), 5,
              frequencyUUID, 8);
        PortableResourceDescriptor resource = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "minecraft:iron_ingot", 0, null);
        QIOSmartProcessingResourceEntry entry = new QIOSmartProcessingResourceEntry(resource,
              12, 2, 1, 4, true, false, 4, UUID.randomUUID());
        QIOPage<QIOSmartProcessingResourceEntry> page = QIOPagination.page(
              java.util.Collections.singletonList(entry), 3, session.getSessionNonce(), null, 1, 1);
        UUID pageRequestId = UUID.randomUUID();
        assertMessageRoundTrip(PacketQIOSmartProcessingResourcePageRequest.Message.create(
              23, session, pageRequestId, QIOSmartProcessingResourceFilter.ALL, "iron", 0,
              55, -1),
              PacketQIOSmartProcessingResourcePageRequest.Message::new);
        assertMessageRoundTrip(PacketQIOSmartProcessingResourcePageData.Message.create(
              23, session, pageRequestId, QIOSmartProcessingResourceFilter.ALL, "iron", page),
              PacketQIOSmartProcessingResourcePageData.Message::new);
        QIOSmartProcessingPreviewSnapshot preview = new QIOSmartProcessingPreviewSnapshot(
              UUID.randomUUID(), mekanism.qioprocessing.common.order.QIOOrderPreview.State.READY,
              mekanism.qioprocessing.common.planning.QIOPlanningResult.Status.SUCCESS, "",
              resource, 8, 0, 100, 20, 2, 1,
              java.util.Collections.singletonMap(resource, 3L), java.util.Collections.emptyList(), null);
        UUID requestId = UUID.randomUUID();
        assertMessageRoundTrip(PacketQIOSmartProcessingPreviewAction.Message.request(
              23, session, requestId, resource.write(), 8, 0, true),
              PacketQIOSmartProcessingPreviewAction.Message::new);
        assertMessageRoundTrip(PacketQIOSmartProcessingPreviewAction.Message.confirm(
              23, session, UUID.randomUUID(), preview.getPreviewId(), resource.write(), 8),
              PacketQIOSmartProcessingPreviewAction.Message::new);
        assertMessageRoundTrip(PacketQIOSmartProcessingPreviewData.Message.create(
              23, session, requestId, "READY", preview, null),
              PacketQIOSmartProcessingPreviewData.Message::new);
    }

    private static void assertRoundTrip(
          PacketQIOProcessingTerminalBinding.Message original) {
        ByteBuf encoded = Unpooled.buffer();
        ByteBuf reencoded = Unpooled.buffer();
        try {
            original.toBytes(encoded);
            byte[] expected = ByteBufUtil.getBytes(encoded, encoded.readerIndex(),
                  encoded.readableBytes());
            PacketQIOProcessingTerminalBinding.Message decoded =
                  new PacketQIOProcessingTerminalBinding.Message();
            decoded.fromBytes(encoded);
            assertTrue(decoded.isValid());
            decoded.toBytes(reencoded);
            assertArrayEquals(expected, ByteBufUtil.getBytes(reencoded,
                  reencoded.readerIndex(), reencoded.readableBytes()));
        } finally {
            encoded.release();
            reencoded.release();
        }
    }

    private static <T extends net.minecraftforge.fml.common.network.simpleimpl.IMessage>
          void assertMessageRoundTrip(T original, java.util.function.Supplier<T> factory) {
        ByteBuf encoded = Unpooled.buffer();
        ByteBuf reencoded = Unpooled.buffer();
        try {
            original.toBytes(encoded);
            byte[] expected = ByteBufUtil.getBytes(encoded, encoded.readerIndex(),
                  encoded.readableBytes());
            T decoded = factory.get();
            decoded.fromBytes(encoded);
            if (decoded instanceof PacketQIOManagementDevicePageRequest.Message request) {
                assertTrue(request.isValid());
            } else if (decoded instanceof PacketQIOManagementDevicePageData.Message response) {
                assertTrue(response.isValid());
            } else if (decoded instanceof PacketQIOManagementDeviceGroupPageRequest.Message request) {
                assertTrue(request.isValid());
            } else if (decoded instanceof PacketQIOManagementDeviceGroupPageData.Message response) {
                assertTrue(response.isValid());
            } else if (decoded instanceof PacketQIOManagementRecipeRequest.Message request) {
                assertTrue(request.isValid());
            } else if (decoded instanceof PacketQIOManagementRecipeData.Message response) {
                assertTrue(response.isValid());
            } else if (decoded instanceof PacketQIODeviceCommand.Message request) {
                assertTrue(request.isValid());
            } else if (decoded instanceof PacketQIODeviceCommandResult.Message response) {
                assertTrue(response.isValid());
            } else if (decoded instanceof PacketQIOManagementPolicyPageRequest.Message request) {
                assertTrue(request.isValid());
            } else if (decoded instanceof PacketQIOManagementPolicyPageData.Message response) {
                assertTrue(response.isValid());
            } else if (decoded instanceof PacketQIOManagementPolicyMutation.Message mutation) {
                assertTrue(mutation.isValid());
            } else if (decoded instanceof PacketQIOManagementPolicyMutationResult.Message result) {
                assertTrue(result.isValid());
            } else if (decoded instanceof PacketQIOManagementPolicyLookupRequest.Message request) {
                assertTrue(request.isValid());
            } else if (decoded instanceof PacketQIOManagementPolicyLookupData.Message response) {
                assertTrue(response.isValid());
            } else if (decoded instanceof PacketQIOMaintenanceRulePageRequest.Message request) {
                assertTrue(request.isValid());
            } else if (decoded instanceof PacketQIOMaintenanceRulePageData.Message response) {
                assertTrue(response.isValid());
            } else if (decoded instanceof PacketQIOMaintenanceRuleMutation.Message mutation) {
                assertTrue(mutation.isValid());
            } else if (decoded instanceof PacketQIOMaintenanceRuleMutationResult.Message result) {
                assertTrue(result.isValid());
            } else if (decoded instanceof PacketQIOMaintenanceResourcePageRequest.Message request) {
                assertTrue(request.isValid());
            } else if (decoded instanceof PacketQIOMaintenanceResourcePageData.Message response) {
                assertTrue(response.isValid());
            } else if (decoded instanceof PacketQIOCraftingMonitorPageRequest.Message request) {
                assertTrue(request.isValid());
            } else if (decoded instanceof PacketQIOCraftingMonitorPageData.Message response) {
                assertTrue(response.isValid());
            } else if (decoded instanceof PacketQIOCraftingMonitorCancel.Message cancel) {
                assertTrue(cancel.isValid());
            } else if (decoded instanceof PacketQIOCraftingMonitorCancelResult.Message result) {
                assertTrue(result.isValid());
            } else if (decoded instanceof PacketQIOCraftingMonitorPlanPageRequest.Message request) {
                assertTrue(request.isValid());
            } else if (decoded instanceof PacketQIOCraftingMonitorPlanPageData.Message response) {
                assertTrue(response.isValid());
            } else if (decoded instanceof PacketQIOCraftingMonitorRuntimeRequest.Message request) {
                assertTrue(request.isValid());
            } else if (decoded instanceof PacketQIOCraftingMonitorRuntimeData.Message response) {
                assertTrue(response.isValid());
            } else if (decoded instanceof PacketQIOCraftingMonitorMutation.Message mutation) {
                assertTrue(mutation.isValid());
            } else if (decoded instanceof PacketQIOCraftingMonitorMutationResult.Message result) {
                assertTrue(result.isValid());
            } else if (decoded instanceof PacketQIOSmartProcessingResourcePageRequest.Message request) {
                assertTrue(request.isValid());
            } else if (decoded instanceof PacketQIOSmartProcessingResourcePageData.Message response) {
                assertTrue(response.isValid());
            } else if (decoded instanceof PacketQIOSmartProcessingPreviewAction.Message action) {
                assertTrue(action.isValid());
            } else if (decoded instanceof PacketQIOSmartProcessingPreviewData.Message response) {
                assertTrue(response.isValid());
            }
            decoded.toBytes(reencoded);
            assertArrayEquals(expected, ByteBufUtil.getBytes(reencoded,
                  reencoded.readerIndex(), reencoded.readableBytes()));
        } finally {
            encoded.release();
            reencoded.release();
        }
    }


    private static QIOAutomationDeviceSnapshot snapshot(UUID uuid, int x) {
        return new QIOAutomationDeviceSnapshot(uuid,
              new QIOAutomationDeviceLocation(0, new BlockPos(x, 64, 0)),
              "mekanism:machineblock", 0, "mekanism:test",
              QIOAutomationMode.SCHEDULED, QIOAutomationHost.State.ACTIVE,
              true, x, 1, 1, 1, 0, null);
    }

    private static QIOManagementRecipeSnapshot recipeSnapshot(PageKind kind,
          UUID deviceUUID, java.util.List<Product> products,
          java.util.List<Route> routes) {
        return new QIOManagementRecipeSnapshot(kind, deviceUUID,
              QIOAutomationMode.SCHEDULED, "test:provider", "test:machine_scope",
              3, 4, 5, true, 1, RouteFilterMode.BLACKLIST, true,
              1, 64, 0, 1, "", kind == PageKind.ROUTES ? "product:gold" : "",
              products, routes);
    }


    private static void assertInvalid(ByteBuf buffer) {
        PacketQIOProcessingTerminalBinding.Message decoded =
              new PacketQIOProcessingTerminalBinding.Message();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isValid());
    }

    private static void writeHeader(ByteBuf buffer, boolean bind, int windowId,
          int kind, int type, long revision) {
        buffer.writeBoolean(bind);
        buffer.writeInt(windowId);
        buffer.writeLong(1);
        buffer.writeLong(2);
        buffer.writeByte(kind);
        buffer.writeByte(type);
        buffer.writeLong(3);
        buffer.writeLong(4);
        buffer.writeLong(revision);
    }
}
