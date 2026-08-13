package mekanism.qioprocessing.common.content;

import mekanism.common.TestBootstrap;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.passive.QIOPassiveOperation;
import mekanism.qioprocessing.common.content.policy.QIOPolicyCatalog;
import mekanism.qioprocessing.common.content.policy.QIOPolicyEntrySnapshot;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOPolicyAndPassiveStateTest {

    private static PortableResourceDescriptor iron;
    private static PortableResourceDescriptor gold;

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
        iron = PortableResourceDescriptor.item(new ItemStack(Items.IRON_INGOT));
        gold = PortableResourceDescriptor.item(new ItemStack(Items.GOLD_INGOT));
    }

    @Test
    void policyOverridesPersistWithStableRevision() throws Exception {
        QIOPolicyCatalog policies = new QIOPolicyCatalog();
        UUID device = UUID.randomUUID();
        policies.setGlobalDefaultPolicy(QIOPolicyCatalog.Toggle.ENABLED, 2);
        policies.setGlobalRoutePolicy("test:provider", "route", "recipe",
              QIOPolicyCatalog.Toggle.DISABLED, 9L);
        policies.setDeviceDefaultPolicy(device, QIOPolicyCatalog.Toggle.ENABLED, 7, 11);
        policies.setDeviceRoutePolicy(device, "test:provider", "route", "recipe",
              QIOPolicyCatalog.Toggle.ENABLED, 13L);

        QIOPolicyCatalog restored = QIOPolicyCatalog.read(policies.write());

        assertEquals(policies.getRevision(), restored.getRevision());
        assertFalse(restored.isRouteEnabled("test:provider", "route", "recipe"));
        assertEquals(9, restored.routePriority("test:provider", "route", "recipe"));
        assertTrue(restored.isRouteEnabled(device, "test:provider", "route", "recipe"));
        assertEquals(13, restored.routePriority(device, "test:provider", "route", "recipe"));
        assertEquals(7, restored.machinePriority(device));
        assertEquals(11, restored.passivePriority(device));
    }

    @Test
    void effectiveRoutePolicyUsesTheDocumentedFourLevelOrder() {
        QIOPolicyCatalog policies = new QIOPolicyCatalog();
        UUID device = UUID.randomUUID();
        policies.setGlobalDefaultPolicy(QIOPolicyCatalog.Toggle.DISABLED, 1);
        policies.setGlobalRoutePolicy("test:provider", "route", "recipe",
              QIOPolicyCatalog.Toggle.ENABLED, 2L);
        policies.setDeviceDefaultPolicy(device, QIOPolicyCatalog.Toggle.DISABLED, 3, 4);
        policies.setDeviceRoutePolicy(device, "test:provider", "route", "recipe",
              QIOPolicyCatalog.Toggle.ENABLED, 5L);

        QIOPolicyCatalog.EffectiveRoutePolicy effective = policies.resolveRoutePolicy(
              device, "test:provider", "route", "recipe");
        assertTrue(effective.isEnabled());
        assertEquals(5, effective.getRoutePriority());
        assertEquals(QIOPolicyCatalog.Source.DEVICE_ROUTE, effective.getToggleSource());
        assertEquals(QIOPolicyCatalog.Source.DEVICE_ROUTE, effective.getPrioritySource());

        policies.setDeviceRoutePolicy(device, "test:provider", "route", "recipe",
              QIOPolicyCatalog.Toggle.INHERIT, null);
        effective = policies.resolveRoutePolicy(device, "test:provider", "route", "recipe");
        assertFalse(effective.isEnabled());
        assertEquals(2, effective.getRoutePriority());
        assertEquals(QIOPolicyCatalog.Source.DEVICE_DEFAULT, effective.getToggleSource());
        assertEquals(QIOPolicyCatalog.Source.GLOBAL_ROUTE, effective.getPrioritySource());

        policies.setDeviceDefaultPolicy(device, QIOPolicyCatalog.Toggle.INHERIT, 0, 0);
        effective = policies.resolveRoutePolicy(device, "test:provider", "route", "recipe");
        assertTrue(effective.isEnabled());
        assertEquals(QIOPolicyCatalog.Source.GLOBAL_ROUTE, effective.getToggleSource());

        policies.setGlobalRoutePolicy("test:provider", "route", "recipe",
              QIOPolicyCatalog.Toggle.INHERIT, null);
        effective = policies.resolveRoutePolicy(device, "test:provider", "route", "recipe");
        assertFalse(effective.isEnabled());
        assertEquals(1, effective.getRoutePriority());
        assertEquals(QIOPolicyCatalog.Source.GLOBAL_DEFAULT, effective.getToggleSource());
        assertEquals(QIOPolicyCatalog.Source.GLOBAL_DEFAULT, effective.getPrioritySource());
    }

    @Test
    void passivePriorityUsesTheSameFourLevelFallbackForADeviceRoute() {
        QIOPolicyCatalog policies = new QIOPolicyCatalog();
        UUID device = UUID.randomUUID();
        policies.setGlobalDefaultPolicy(QIOPolicyCatalog.Toggle.ENABLED, 1, 10);
        policies.setGlobalRoutePolicy("test:provider", "route", "recipe",
              QIOPolicyCatalog.Toggle.ENABLED, 2L, 20L);
        policies.setDeviceDefaultPolicy(device, QIOPolicyCatalog.Toggle.INHERIT, 3, 30);
        policies.setDeviceRoutePolicy(device, "test:provider", "route", "recipe",
              QIOPolicyCatalog.Toggle.INHERIT, null, 40L);

        QIOPolicyCatalog.EffectiveRoutePolicy effective = policies.resolveRoutePolicy(device,
              "test:provider", "route", "recipe");
        assertEquals(40, effective.getPassivePriority());
        assertEquals(QIOPolicyCatalog.Source.DEVICE_ROUTE,
              effective.getPassivePrioritySource());

        policies.setDeviceRoutePolicy(device, "test:provider", "route", "recipe",
              QIOPolicyCatalog.Toggle.INHERIT, null, null);
        effective = policies.resolveRoutePolicy(device, "test:provider", "route", "recipe");
        assertEquals(30, effective.getPassivePriority());
        assertEquals(QIOPolicyCatalog.Source.DEVICE_DEFAULT,
              effective.getPassivePrioritySource());

        policies.setDeviceDefaultPolicy(device, QIOPolicyCatalog.Toggle.INHERIT, 0, 0);
        effective = policies.resolveRoutePolicy(device, "test:provider", "route", "recipe");
        assertEquals(20, effective.getPassivePriority());
        assertEquals(QIOPolicyCatalog.Source.GLOBAL_ROUTE,
              effective.getPassivePrioritySource());

        policies.setGlobalRoutePolicy("test:provider", "route", "recipe",
              QIOPolicyCatalog.Toggle.INHERIT, null, null);
        effective = policies.resolveRoutePolicy(device, "test:provider", "route", "recipe");
        assertEquals(10, effective.getPassivePriority());
        assertEquals(QIOPolicyCatalog.Source.GLOBAL_DEFAULT,
              effective.getPassivePrioritySource());
    }

    @Test
    void policyRowsExposeRawAndEffectiveSourcesAndRoundTrip() throws Exception {
        QIOPolicyCatalog policies = new QIOPolicyCatalog();
        UUID device = UUID.randomUUID();
        policies.setGlobalDefaultPolicy(QIOPolicyCatalog.Toggle.DISABLED, 1);
        policies.setGlobalRoutePolicy("test:provider", "route", "recipe",
              QIOPolicyCatalog.Toggle.ENABLED, 2L);
        policies.setDeviceDefaultPolicy(device, QIOPolicyCatalog.Toggle.INHERIT, 7, 11);
        policies.setDeviceRoutePolicy(device, "test:provider", "route", "recipe",
              QIOPolicyCatalog.Toggle.INHERIT, 5L, 6L);

        assertEquals(4, policies.getPolicyEntries().size());
        QIOPolicyEntrySnapshot entry = policies.snapshotDeviceRoute(device,
              "test:provider", "route", "recipe");
        QIOPolicyEntrySnapshot restored = QIOPolicyEntrySnapshot.read(entry.write());

        assertEquals(entry, restored);
        assertEquals(QIOPolicyCatalog.Toggle.INHERIT, restored.getConfiguredToggle());
        assertEquals(5, restored.getConfiguredRoutePriority());
        assertTrue(restored.isEffectiveEnabled());
        assertEquals(QIOPolicyCatalog.Source.GLOBAL_ROUTE, restored.getToggleSource());
        assertEquals(QIOPolicyCatalog.Source.DEVICE_ROUTE, restored.getPrioritySource());
        assertEquals(6, restored.getConfiguredPassivePriority());
        assertEquals(6, restored.getEffectivePassivePriority());
        assertEquals(QIOPolicyCatalog.Source.DEVICE_ROUTE,
              restored.getPassivePrioritySource());
    }

    @Test
    void incompleteDevelopmentRouteKeysAreRejected() {
        QIOPolicyCatalog policies = new QIOPolicyCatalog();
        policies.setGlobalRoutePolicy("test:provider", "route", "recipe",
              QIOPolicyCatalog.Toggle.DISABLED, 4L);
        NBTTagCompound stored = policies.write();
        NBTTagList routes = stored.getTagList("routes", NBT.TAG_COMPOUND);
        NBTTagCompound incompleteRoute = routes.getCompoundTagAt(0);
        incompleteRoute.removeTag("providerId");
        incompleteRoute.removeTag("routeId");
        incompleteRoute.removeTag("recipeKey");

        assertThrows(QIOProcessingDataException.class, () -> QIOPolicyCatalog.read(stored));
    }

    @Test
    void missingCurrentPolicyFieldsAreNotDefaulted() {
        QIOPolicyCatalog policies = new QIOPolicyCatalog();
        policies.setGlobalRoutePolicy("test:provider", "route", "recipe",
              QIOPolicyCatalog.Toggle.DISABLED, 4L);

        NBTTagCompound missingRevision = policies.write();
        missingRevision.removeTag("revision");
        assertThrows(QIOProcessingDataException.class,
              () -> QIOPolicyCatalog.read(missingRevision));

        NBTTagCompound missingPriorityFlag = policies.write();
        missingPriorityFlag.getTagList("routes", NBT.TAG_COMPOUND)
              .getCompoundTagAt(0).removeTag("hasPriority");
        assertThrows(QIOProcessingDataException.class,
              () -> QIOPolicyCatalog.read(missingPriorityFlag));
    }

    @Test
    void passiveClaimBuffersAndMachineIdentitySurviveNetworkPersistence() throws Exception {
        UUID frequency = UUID.randomUUID();
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(frequency,
              new QIOFrequencyIdentitySnapshot("passive", null, SecurityMode.PUBLIC));
        QIOPassiveOperation operation = new QIOPassiveOperation(UUID.randomUUID(),
              UUID.randomUUID(), "test:provider", "route", "recipe", 4, 10, 2, 3,
              Collections.singletonMap(iron, UUID.randomUUID()),
              Collections.singletonMap(iron, 4L));
        operation.markClaimed(4);
        UUID lease = UUID.randomUUID();
        operation.markConfiguring(lease, 2);
        operation.prepareConsume(Collections.singletonMap(iron, BigInteger.valueOf(20)));
        operation.markReserved(5);
        network.addPassiveOperation(operation);

        QIOProcessingNetworkData restored = QIOProcessingNetworkData.read(network.write(),
              frequency);
        QIOPassiveOperation loaded = restored.getPassiveOperation(operation.getOperationId());

        assertEquals(QIOPassiveOperation.State.LOADING, loaded.getState());
        assertEquals(4, loaded.getOperationCount());
        assertEquals(4, loaded.getInputBuffer().get(iron));
        assertEquals(lease, loaded.getLeaseId());
        assertEquals(BigInteger.valueOf(20), loaded.getConsumeBaselines().get(iron));
        assertTrue(restored.getFrequencyDeletionBlockers().contains("passive_operations"));

        NBTTagCompound oldSchema = operation.write();
        oldSchema.setInteger("schema", 1);
        assertThrows(QIOProcessingDataException.class,
              () -> QIOPassiveOperation.read(oldSchema));
        NBTTagCompound missingBaselines = operation.write();
        missingBaselines.removeTag("consumeBaselines");
        assertThrows(QIOProcessingDataException.class,
              () -> QIOPassiveOperation.read(missingBaselines));
        NBTTagCompound schemaThree = operation.write();
        schemaThree.setInteger("schema", 3);
        assertEquals(QIOPassiveOperation.State.LOADING,
              QIOPassiveOperation.read(schemaThree).getState());

        loaded.markActive();
        loaded.markCollecting(Collections.singletonMap(gold, 2L));
        loaded.markDelivering();
        loaded.debitDelivered(gold, 2);
        assertEquals(QIOPassiveOperation.State.COMPLETED, loaded.getState());
        assertTrue(loaded.getOutputBuffer().isEmpty());
    }

    @Test
    void partialPassiveClaimPersistsUntilExplicitRelease() throws Exception {
        UUID frequency = UUID.randomUUID();
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(frequency,
              new QIOFrequencyIdentitySnapshot("partial", null, SecurityMode.PUBLIC));
        QIOPassiveOperation operation = new QIOPassiveOperation(UUID.randomUUID(),
              UUID.randomUUID(), "test:provider", "route", "recipe", 10, 2, 3,
              Collections.singletonMap(iron, UUID.randomUUID()),
              Collections.singletonMap(iron, 4L));
        operation.markPartialClaim(Collections.singletonMap(iron, 2L), 4,
              "partial claim");
        network.addPassiveOperation(operation);

        QIOPassiveOperation loaded = QIOProcessingNetworkData.read(network.write(), frequency)
              .getPassiveOperation(operation.getOperationId());

        assertEquals(QIOPassiveOperation.State.RELEASE_PREPARED, loaded.getState());
        assertEquals(2, loaded.getClaimedInputs().get(iron));
        assertEquals(operation.getReleaseRequestId(), loaded.getReleaseRequestId());
        loaded.markClaimReleased();
        assertEquals(QIOPassiveOperation.State.FAILED, loaded.getState());
        assertTrue(loaded.getClaimedInputs().isEmpty());
    }

    @Test
    void reservedPassiveInputsRemainOwnedUntilAllReturnsSettle() {
        Map<PortableResourceDescriptor, UUID> bindings = new LinkedHashMap<>();
        bindings.put(iron, UUID.randomUUID());
        bindings.put(gold, UUID.randomUUID());
        Map<PortableResourceDescriptor, Long> inputs = new LinkedHashMap<>();
        inputs.put(iron, 4L);
        inputs.put(gold, 2L);
        QIOPassiveOperation operation = new QIOPassiveOperation(UUID.randomUUID(),
              UUID.randomUUID(), "test:provider", "route", "recipe", 10, 2, 3,
              bindings, inputs);
        operation.markClaimed(4);
        operation.markConfiguring(UUID.randomUUID(), 0);
        Map<PortableResourceDescriptor, BigInteger> baselines = new LinkedHashMap<>();
        baselines.put(iron, BigInteger.valueOf(12));
        baselines.put(gold, BigInteger.valueOf(8));
        operation.prepareConsume(baselines);
        operation.markReserved(5);
        operation.fail("provider changed");

        assertEquals(QIOPassiveOperation.State.RETURNING, operation.getState());
        operation.debitReturned(iron, 4);
        assertEquals(QIOPassiveOperation.State.RETURNING, operation.getState());
        operation.debitReturned(gold, 2);
        assertEquals(QIOPassiveOperation.State.FAILED, operation.getState());
        assertTrue(operation.getInputBuffer().isEmpty());
    }
}
