package mekanism.common.lib.radiation;

import baubles.api.BaublesApi;
import mekanism.api.radiation.IRadiationSource;
import mekanism.api.radiation.capability.IRadiationShielding;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.common.Mekanism;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.MekanismHooks;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.items.IItemHandler;

public final class RadiationUtil {

    private static final int TICKS_PER_SECOND = 20;
    private static final EntityEquipmentSlot[] ARMOR_SLOTS = {
          EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET
    };

    private RadiationUtil() {
    }

    public static long getDecayTime(double magnitude, boolean source) {
        double decayRate = source ? MekanismConfig.current().general.radiationSourceDecayRate.val()
                                  : MekanismConfig.current().general.radiationTargetDecayRate.val();
        return getDecayTime(magnitude, decayRate);
    }

    static long getDecayTime(double magnitude, double decayRate) {
        if (!(magnitude > RadiationManager.MIN_MAGNITUDE)) {
            return 0;
        } else if (!(decayRate > 0)) {
            return TICKS_PER_SECOND;
        } else if (!Double.isFinite(decayRate) || decayRate >= 1) {
            return Long.MAX_VALUE;
        }
        double decaySteps = Math.ceil(Math.log(RadiationManager.MIN_MAGNITUDE / magnitude) / Math.log(decayRate));
        if (!(decaySteps > 0)) {
            return 0;
        } else if (!Double.isFinite(decaySteps) || decaySteps >= Long.MAX_VALUE / (double) TICKS_PER_SECOND) {
            return Long.MAX_VALUE;
        }
        return (long) decaySteps * TICKS_PER_SECOND;
    }

    public static double getRadiationResistance(EntityLivingBase entity) {
        double resistance = 0;
        for (EntityEquipmentSlot slot : ARMOR_SLOTS) {
            resistance = addShielding(resistance, entity.getItemStackFromSlot(slot));
            if (resistance >= 1) {
                return 1;
            }
        }
        if (Mekanism.hooks.Baubles) {
            resistance = getBaublesRadiationResistance(entity, resistance);
        }
        return Math.min(1, resistance);
    }

    @Optional.Method(modid = MekanismHooks.Baubles_MOD_ID)
    private static double getBaublesRadiationResistance(EntityLivingBase entity, double resistance) {
        if (entity instanceof EntityPlayer player) {
            IItemHandler baubles = BaublesApi.getBaublesHandler(player);
            for (int slot = 0; slot < baubles.getSlots(); slot++) {
                resistance = addShielding(resistance, baubles.getStackInSlot(slot));
                if (resistance >= 1) {
                    return 1;
                }
            }
        }
        return resistance;
    }

    private static double addShielding(double resistance, ItemStack stack) {
        if (!stack.isEmpty()) {
            IRadiationShielding shielding = stack.getCapability(Capabilities.RADIATION_SHIELDING_CAPABILITY, null);
            if (shielding != null) {
                double amount = shielding.getRadiationShielding();
                if (Double.isFinite(amount) && amount > 0) {
                    return resistance + Math.min(1, amount);
                }
            }
        }
        return resistance;
    }

    public static double computeExposure(IRadiationSource source, mekanism.api.Coord4D position) {
        return source.getMagnitude() / Math.max(1, position.distanceToSquared(source.getPos()));
    }

    public static int getWasteBarrelProcessTicks() {
        return Math.max(1, MekanismConfig.current().general.radioactiveWasteBarrelProcessTicks.val());
    }

    public static int getWasteBarrelDecayAmount() {
        return Math.max(0, MekanismConfig.current().general.radioactiveWasteBarrelDecayAmount.val());
    }

    public static boolean canDecayInWasteBarrel(GasStack stack) {
        return stack != null && stack.getGas() != null && stack.getGas().isRadiation() &&
               !isWasteBarrelDecayBlacklisted(stack.getGas());
    }

    public static boolean isWasteBarrelDecayBlacklisted(Gas gas) {
        if (gas == null) {
            return false;
        }
        String[] blacklist = MekanismConfig.current().general.radioactiveWasteBarrelDecayBlacklist.get();
        if (blacklist != null) {
            for (String blacklisted : blacklist) {
                if (blacklisted != null && gas.getName().equalsIgnoreCase(blacklisted.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static double sanitizeMagnitude(double magnitude) {
        if (!(magnitude > 0)) {
            return 0;
        }
        return Double.isFinite(magnitude) ? magnitude : Double.MAX_VALUE;
    }

    public static double sanitizeAtLeastBaseline(double magnitude) {
        return Math.max(RadiationManager.BASELINE, sanitizeMagnitude(magnitude));
    }

    public static double addClamped(double current, double amount) {
        current = sanitizeMagnitude(current);
        amount = sanitizeMagnitude(amount);
        if (amount == 0) {
            return current;
        }
        return current >= Double.MAX_VALUE - amount ? Double.MAX_VALUE : current + amount;
    }
}
