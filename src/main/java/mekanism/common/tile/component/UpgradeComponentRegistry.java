package mekanism.common.tile.component;

import mekanism.common.base.IUpgradeTileComponent;
import mekanism.common.tile.prefab.TileEntityBasicBlock;

import java.util.EnumMap;
import java.util.Objects;
import java.util.function.Function;

/**
 * Module factories run after tile construction, never from Forge's constructor-time capability event.
 */
public final class UpgradeComponentRegistry {
    public enum Slot {QIO, AE}

    private static final EnumMap<Slot, Function<TileEntityBasicBlock, IUpgradeTileComponent>> FACTORIES = new EnumMap<>(Slot.class);

    private UpgradeComponentRegistry() {
    }

    public static synchronized void register(Slot slot, Function<TileEntityBasicBlock, IUpgradeTileComponent> factory) {
        Objects.requireNonNull(slot);
        Objects.requireNonNull(factory);
        if (FACTORIES.containsKey(slot))
            throw new IllegalStateException("Duplicate upgrade component factory: " + slot);
        FACTORIES.put(slot, factory);
    }

    static IUpgradeTileComponent create(Slot slot, TileEntityBasicBlock tile) {
        Function<TileEntityBasicBlock, IUpgradeTileComponent> factory = FACTORIES.get(slot);
        return factory == null ? null : factory.apply(tile);
    }
}
