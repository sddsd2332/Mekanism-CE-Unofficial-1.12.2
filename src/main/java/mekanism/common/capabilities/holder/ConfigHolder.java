package mekanism.common.capabilities.holder;

import mekanism.api.RelativeSide;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.slot.ISlotInfo;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public abstract class ConfigHolder<TYPE> implements IHolder {

    private static final Predicate<ISlotInfo> CAN_INPUT = ISlotInfo::canInput;
    private static final Predicate<ISlotInfo> CAN_OUTPUT = ISlotInfo::canOutput;

    /**
     * Dummy ISlotInfo used for representing we have no config.
     */
    private static final ISlotInfo NO_CONFIG = new ISlotInfo() {
        @Override
        public boolean canInput() {
            return true;
        }

        @Override
        public boolean canOutput() {
            return true;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this;
        }

        @Override
        public String toString() {
            return "No Config";
        }
    };

    private final Supplier<TileComponentConfig> configSupplier;
    private final Supplier<EnumFacing> facingSupplier;
    private final Map<EnumFacing, ISlotInfo> cachedSlotInfo = new EnumMap<>(EnumFacing.class);
    protected final List<TYPE> slots = new ArrayList<>();
    @Nullable
    private EnumFacing lastDirection;
    @Nullable
    private ConfigInfo lazyConfig;
    @Nullable
    private TileComponentConfig listenerConfig;
    private boolean retrievedConfig;

    protected ConfigHolder(Supplier<EnumFacing> facingSupplier, Supplier<TileComponentConfig> configSupplier) {
        this.facingSupplier = facingSupplier;
        this.configSupplier = configSupplier;
    }

    protected ConfigHolder(ISideConfiguration sideConfiguration) {
        this(sideConfiguration::getOrientation, sideConfiguration::getConfig);
    }

    protected abstract mekanism.api.transmitters.TransmissionType getTransmissionType();

    @Override
    public boolean canInsert(@Nullable EnumFacing side) {
        return canInteract(side, CAN_INPUT);
    }

    @Override
    public boolean canExtract(@Nullable EnumFacing side) {
        return canInteract(side, CAN_OUTPUT);
    }

    private boolean canInteract(@Nullable EnumFacing side, @Nonnull Predicate<ISlotInfo> interactPredicate) {
        if (side == null) {
            return false;
        }
        ISlotInfo slotInfo = getSlotInfo(side);
        if (slotInfo == NO_CONFIG) {
            return true;
        }
        return slotInfo != null && interactPredicate.test(slotInfo);
    }

    @Nonnull
    protected List<TYPE> getSlots(@Nullable EnumFacing side, @Nonnull Function<ISlotInfo, List<TYPE>> slotInfoParser) {
        if (side == null) {
            return slots;
        }
        ISlotInfo slotInfo = getSlotInfo(side);
        if (slotInfo == NO_CONFIG) {
            return slots;
        } else if (slotInfo == null) {
            return Collections.emptyList();
        }
        return slotInfoParser.apply(slotInfo);
    }

    protected boolean isNoConfig(@Nullable ISlotInfo slotInfo) {
        return slotInfo == NO_CONFIG;
    }

    @Nullable
    protected ISlotInfo getSlotInfo(EnumFacing side) {
        EnumFacing direction = facingSupplier.get();
        if (direction != lastDirection) {
            cachedSlotInfo.clear();
            lastDirection = direction;
        } else if (cachedSlotInfo.containsKey(side)) {
            return cachedSlotInfo.get(side);
        }
        ISlotInfo slotInfo;
        ConfigInfo configInfo = getConfigInfo();
        if (configInfo == null) {
            slotInfo = NO_CONFIG;
        } else {
            slotInfo = configInfo.getSlotInfo(RelativeSide.fromDirections(direction, side));
            if (slotInfo != null && !slotInfo.isEnabled()) {
                slotInfo = null;
            }
        }
        cachedSlotInfo.put(side, slotInfo);
        return slotInfo;
    }

    @Nullable
    private ConfigInfo getConfigInfo() {
        if (!retrievedConfig) {
            TileComponentConfig config = configSupplier.get();
            if (config == null) {
                return null;
            }
            mekanism.api.transmitters.TransmissionType transmissionType = getTransmissionType();
            retrievedConfig = true;
            lazyConfig = config.getConfigInfo(transmissionType);
            if (config != listenerConfig) {
                listenerConfig = config;
                config.addConfigChangeListener(transmissionType, changedSide -> {
                    if (changedSide == null) {
                        cachedSlotInfo.clear();
                        retrievedConfig = false;
                        lazyConfig = null;
                    } else {
                        cachedSlotInfo.remove(changedSide);
                    }
                });
            }
        }
        return lazyConfig;
    }
}
