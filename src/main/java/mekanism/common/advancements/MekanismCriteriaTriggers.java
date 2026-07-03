package mekanism.common.advancements;

import mekanism.common.Mekanism;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.ICriterionTrigger;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.advancements.critereon.ItemPredicates;

public final class MekanismCriteriaTriggers {

    public static final SimpleMekanismTrigger ALLOY_UPGRADE = register(new SimpleMekanismTrigger("alloy_upgrade"));
    public static final SimpleMekanismTrigger BLOCK_LASER = register(new SimpleMekanismTrigger("block_laser"));
    public static final ConfigurationCardTrigger CONFIGURATION_CARD = register(new ConfigurationCardTrigger());
    public static final MekanismDamageTrigger DAMAGE = register(new MekanismDamageTrigger());
    public static final SimpleMekanismTrigger TELEPORT = register(new SimpleMekanismTrigger("teleport"));
    public static final SimpleMekanismTrigger UNBOX_CARDBOARD_BOX = register(new SimpleMekanismTrigger("unbox_cardboard_box"));
    public static final SimpleMekanismTrigger USE_DOSIMETER = register(new SimpleMekanismTrigger("use_dosimeter"));
    public static final SimpleMekanismTrigger USE_GAUGE_DROPPER = register(new SimpleMekanismTrigger("use_gauge_dropper"));
    public static final SimpleMekanismTrigger USE_GEIGER_COUNTER = register(new SimpleMekanismTrigger("use_geiger_counter"));
    public static final SimpleMekanismTrigger USE_TIER_INSTALLER = register(new SimpleMekanismTrigger("use_tier_installer"));
    public static final SimpleMekanismTrigger VIEW_VIBRATIONS = register(new SimpleMekanismTrigger("view_vibrations"));

    private static boolean itemPredicatesRegistered;

    private MekanismCriteriaTriggers() {
    }

    public static void init() {
        if (!itemPredicatesRegistered) {
            ItemPredicates.register(new ResourceLocation(Mekanism.MODID, "full_canteen"), FullCanteenItemPredicate::new);
            ItemPredicates.register(new ResourceLocation(Mekanism.MODID, "maxed_module_container"), MaxedModuleContainerItemPredicate::new);
            itemPredicatesRegistered = true;
        }
    }

    private static <TRIGGER extends ICriterionTrigger<?>> TRIGGER register(TRIGGER trigger) {
        return CriteriaTriggers.register(trigger);
    }
}
