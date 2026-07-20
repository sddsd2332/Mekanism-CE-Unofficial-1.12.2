package mekanism.common;

import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpgradeConflictTest {

    @Test
    void registryNameConflictResolvesAfterLateRegistration() {
        ResourceLocation targetName = new ResourceLocation("mekanism_test", "late_conflict_target");
        Upgrade source = Upgrade.builder("mekanism_test", "late_conflict_source")
              .conflictsWith(targetName)
              .register();

        assertTrue(source.getConflictingUpgrades().isEmpty());

        Upgrade target = Upgrade.builder(targetName).register();

        assertTrue(source.conflictsWith(target));
        assertFalse(target.conflictsWith(source));
        assertFalse(source.isCompatibleWith(target));
        assertFalse(target.isCompatibleWith(source));
        assertEquals(Collections.singleton(target), source.getConflictingUpgrades());
    }

    @Test
    void existingUpgradeConflictApiStillUsesRegisteredTarget() {
        Upgrade target = Upgrade.builder("mekanism_test", "instance_conflict_target").register();
        Upgrade source = Upgrade.builder("mekanism_test", "instance_conflict_source")
              .conflictsWith(target)
              .register();

        assertTrue(source.conflictsWith(target));
        assertEquals(Collections.singleton(target), source.getConflictingUpgrades());
    }

    @Test
    void modIdAndNameConflictDoesNotRequireTargetRegistration() {
        Upgrade source = Upgrade.builder("mekanism_test", "missing_mod_conflict_source")
              .conflictsWith("missing_optional_mod", "optional_upgrade")
              .register();

        assertTrue(source.getConflictingUpgrades().isEmpty());
    }
}
