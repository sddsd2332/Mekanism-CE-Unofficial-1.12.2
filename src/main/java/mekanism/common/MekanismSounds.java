package mekanism.common;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.registries.IForgeRegistry;

public final class MekanismSounds {

    public static SoundEvent BEEP;
    public static SoundEvent CLICK;
    public static SoundEvent DING;
    public static SoundEvent ERROR;
    public static SoundEvent GAS_MASK;
    public static SoundEvent HYDRAULIC;
    public static SoundEvent POP;
    public static SoundEvent SUCCESS;
    public static SoundEvent CJ_EASTER_EGG;
    public static SoundEvent BEEP2;
    public static SoundEvent GEIGER_SLOW, GEIGER_MEDIUM, GEIGER_ELEVATED, GEIGER_FAST;


    public static void register(IForgeRegistry<SoundEvent> registry) {
        BEEP = registerSound(registry, "etc.Beep");
        CLICK = registerSound(registry, "etc.Click");
        DING = registerSound(registry, "etc.Ding");
        ERROR = registerSound(registry, "etc.Error");
        GAS_MASK = registerSound(registry, "etc.GasMask");
        HYDRAULIC = registerSound(registry, "etc.Hydraulic");
        POP = registerSound(registry, "etc.Pop");
        SUCCESS = registerSound(registry, "etc.Success");
        CJ_EASTER_EGG = registerSound(registry, "etc.cj");
        BEEP2 = registerSound(registry, "etc.Beep2");
        GEIGER_SLOW = registerSound(registry, "item.geiger_slow");
        GEIGER_MEDIUM = registerSound(registry, "item.geiger_medium");
        GEIGER_ELEVATED = registerSound(registry, "item.geiger_elevated");
        GEIGER_FAST = registerSound(registry, "item.geiger_fast");
    }

    public static SoundEvent registerSound(IForgeRegistry<SoundEvent> registry, String soundName) {
        ResourceLocation soundID = new ResourceLocation(Mekanism.MODID, soundName);
        SoundEvent event = new SoundEvent(soundID).setRegistryName(soundID);
        registry.register(event);
        return event;
    }
}
