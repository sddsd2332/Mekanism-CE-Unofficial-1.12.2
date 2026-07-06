package mekanism.client.sound;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.client.sound.PlayerSound.SoundType;
import mekanism.common.Mekanism;
import mekanism.common.Upgrade;
import mekanism.common.base.IModule;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.config.MekanismConfig;
import mekanism.common.lib.radiation.RadiationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.event.sound.SoundLoadEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

// SoundHandler is the central point for sounds on Mek client side. There are roughly three classes of sounds to deal
// with:
// 1. One-shot sounds like GUI interactions; play the sound and done
//
// 2. Long-lived player item sounds (such as jetpacks): long-running sounds that may flip on/off quickly based
//    on user action. We follow the minecart model for these sounds; starting a sound and then muting when not in use.
//
// 3. Tile entity sounds: long-running, repeating sounds that run while a fixed tile is active. These are sounds that
//    users want to be able to mute effectively.
//
// All sounds, when initially started can be intercepted on the Forge event bus and wrapped by various muting/manipulation
// mods. For item sounds, we don't want to them to be manipulated, since the flipping on/off is too prone to weird timing
// issues. For long-running sounds, we need a way to honor these attempted manipulations, without allowing them to become
// the permanent state of the sound (which is what happens by default). To accomplish this, we have our own wrapper that
// intercepts new repeating sounds from Mek and ensures that they periodically poll for any muting/manipulation so that
// it the object can dynamically adjust to conditions.

@SideOnly(Side.CLIENT)
public class SoundHandler {

    private static Set<UUID> jetpackSounds = new ObjectOpenHashSet<>();
    private static Set<UUID> gasmaskSounds = new ObjectOpenHashSet<>();
    private static Set<UUID> flamethrowerSounds = new ObjectOpenHashSet<>();
    private static Set<UUID> gravitationalModulationSounds = new ObjectOpenHashSet<>();
    public static final Map<RadiationManager.RadiationScale, GeigerSound> radiationSoundMap = new EnumMap<>(RadiationManager.RadiationScale.class);

    private static Long2ObjectMap<ISound> soundMap = new Long2ObjectOpenHashMap<>();
    private static boolean IN_MUFFLED_CHECK = false;
    private static SoundManager soundEngine;
    private static boolean hadPlayerSounds;
    private static Set<String> moduleSoundNamespaces;
    private static Map<String, Boolean> mekanismSoundNamespaces = new HashMap<>();

    public static void clearPlayerSounds() {
        jetpackSounds.clear();
        gasmaskSounds.clear();
        flamethrowerSounds.clear();
        gravitationalModulationSounds.clear();
    }

    public static void clearPlayerSounds(UUID uuid) {
        jetpackSounds.remove(uuid);
        gasmaskSounds.remove(uuid);
        flamethrowerSounds.remove(uuid);
        gravitationalModulationSounds.remove(uuid);
    }

    public static void startSound(@Nonnull World world, @Nonnull UUID uuid, @Nonnull SoundType soundType) {
        switch (soundType) {
            case JETPACK -> {
                if (!jetpackSounds.contains(uuid)) {
                    EntityPlayer player = world.getPlayerEntityByUUID(uuid);
                    if (player != null) {
                        jetpackSounds.add(uuid);
                        playSound(new JetpackSound(player));
                    }
                }
            }
            case GAS_MASK -> {
                if (!gasmaskSounds.contains(uuid)) {
                    EntityPlayer player = world.getPlayerEntityByUUID(uuid);
                    if (player != null) {
                        gasmaskSounds.add(uuid);
                        playSound(new GasMaskSound(player));
                    }
                }
            }
            case FLAMETHROWER -> {
                if (!flamethrowerSounds.contains(uuid)) {
                    EntityPlayer player = world.getPlayerEntityByUUID(uuid);
                    if (player != null) {
                        flamethrowerSounds.add(uuid);
                        //TODO: Evaluate at some point if there is a better way to do this
                        // Currently it requests both play, except only one can ever play at once due to the shouldPlaySound method
                        playSound(new FlamethrowerSound.Active(player));
                        playSound(new FlamethrowerSound.Idle(player));
                    }
                }
            }
            case GRAVITATIONAL_MODULATOR -> {
                if (!gravitationalModulationSounds.contains(uuid)){
                    EntityPlayer player = world.getPlayerEntityByUUID(uuid);
                    if (player != null) {
                        gravitationalModulationSounds.add(uuid);
                        playSound(new GravitationalModulationSound(player));
                    }
                }
            }
        }
    }

    public static void playSound(SoundEvent sound) {
        playSound(PositionedSoundRecord.getRecord(sound, 1.0F, (float) MekanismConfig.current().client.baseSoundVolume.val()));
    }

    public static void playSound(ISound sound) {
        Minecraft.getMinecraft().getSoundHandler().playSound(sound);
    }

    public static ISound startTileSound(ResourceLocation soundLoc, float volume, BlockPos pos) {
        // First, check to see if there's already a sound playing at the desired location
        ISound s = soundMap.get(pos.toLong());
        if (s == null || !Minecraft.getMinecraft().getSoundHandler().isSoundPlaying(s)) {
            // No sound playing, start one up - we assume that tile sounds will play until explicitly stopped
            s = new PositionedSoundRecord(soundLoc, SoundCategory.BLOCKS, (float) (volume * MekanismConfig.current().client.baseSoundVolume.val()), 1.0f,
                    true, 0, ISound.AttenuationType.LINEAR, pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f) {
                @Override
                public float getVolume() {
                    if (this.sound == null) {
                        this.createAccessor(Minecraft.getMinecraft().getSoundHandler());
                    }
                    return super.getVolume();
                }
            };

            // Start the sound
            playSound(s);

            // N.B. By the time playSound returns, our expectation is that our wrapping-detector handler has fired
            // and dealt with any muting interceptions and, CRITICALLY, updated the soundMap with the final ISound.
            s = soundMap.get(pos.toLong());
        }

        return s;
    }

    public static void stopTileSound(BlockPos pos) {
        long posKey = pos.toLong();
        ISound s = soundMap.get(posKey);
        if (s != null) {
            // TODO: Saw some code that suggests there is a soundmap MC already tracks; investigate further
            // and maybe we can avoid this dedicated soundmap
            Minecraft.getMinecraft().getSoundHandler().stopSound(s);
            soundMap.remove(posKey);
        }
    }


    @SubscribeEvent
    public static void onSoundEngineSetup(SoundLoadEvent event) {
        //Grab the sound engine, so that we are able to play sounds. We use this event rather than requiring the use of an AT
        if (soundEngine == null) {
            //Note: We include a null check as the constructor for SoundEngine is public and calls this event
            // And we do not want to end up grabbing a modders variant of this
            soundEngine = event.getManager();
        }
    }







    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTilePlaySound(PlaySoundEvent event) {
        // Ignore any sound event which is null or is happening in a muffled check
        ISound resultSound = event.getResultSound();
        if (resultSound == null || IN_MUFFLED_CHECK) {
            return;
        }

        // Ignore any sound event outside Mekanism namespaces
        ResourceLocation soundLoc = event.getSound().getSoundLocation();
        if (!isMekanismSound(soundLoc)) {
            return;
        }

        // Ignore any non-tile Mek sounds
        if (event.getName().startsWith("etc.")) {
            return;
        }

        // If this is a Mek player sound, unwrap any muffling that other mods may have attempted. I haven't
        // sorted out a good way to deal with long-lived, non-repeating, dynamic volume sounds -- something
        // to investigate in the future.
        if (event.getSound() instanceof PlayerSound) {
            event.setResultSound(event.getSound());
            return;
        }

        // At this point, we've got a known block Mekanism sound. We want to re-wrap the original
        // using the (possibly) muffled sound as the initial volume. The TileSound will then periodically poll
        // to see if the volume should be adjusted
        resultSound = new TileSound(event.getSound(), resultSound.getVolume());
        event.setResultSound(resultSound);

        // Finally, update our soundMap so that we can actually have a shot at stopping this sound; note that we also
        // need to "unoffset" the sound position so that we build the correct key for the sound map
        // Aside: I really, really, wish Forge returned the final result sound as part of playSound :/
        BlockPos pos = new BlockPos(resultSound.getXPosF() - 0.5f, resultSound.getYPosF() - 0.5f, resultSound.getZPosF() - 0.5);
        soundMap.put(pos.toLong(), resultSound);
    }

    private static boolean isMekanismSound(ResourceLocation soundLoc) {
        String namespace = soundLoc.getNamespace();
        if (namespace == null) {
            return false;
        }
        return mekanismSoundNamespaces.computeIfAbsent(namespace, key -> key.equals(Mekanism.MODID) || getModuleSoundNamespaces().contains(key));
    }

    private static Set<String> getModuleSoundNamespaces() {
        if (moduleSoundNamespaces == null) {
            moduleSoundNamespaces = Mekanism.modulesLoaded.stream()
                    .map(IModule::getClass)
                    .flatMap(moduleClass -> Loader.instance().getActiveModList().stream()
                            .filter(container -> container.getMod() != null && container.getMod().getClass() == moduleClass))
                    .map(ModContainer::getModId)
                    .filter(modId -> modId != null)
                    .collect(java.util.stream.Collectors.toSet());
        }
        return moduleSoundNamespaces;
    }

    private static class TileSound implements ITickableSound {

        private ISound original;
        private float volume;
        private boolean donePlaying = false;

        // Choose an interval between 60-80 ticks (3-4 seconds) to check for muffling changes. We do this
        // to ensure that not every tile sound tries to run on the same tick and thus create
        // uneven spikes of CPU usage
        private int checkInterval = 60 + ThreadLocalRandom.current().nextInt(20);

        private Minecraft mc = Minecraft.getMinecraft();

        TileSound(ISound original, float volume) {
            this.original = original;
            this.volume = volume * getMufflingFactor();
        }

        @Override
        public void update() {
            // Every configured interval, see if we need to adjust muffling
            if (mc.world.getTotalWorldTime() % checkInterval == 0) {

                // Run the event bus with the original sound. Note that we must making sure to set the GLOBAL/STATIC
                // flag that ensures we don't wrap already muffled sounds. This is...NOT ideal and makes some
                // significant (hopefully well-informed) assumptions about locking/ordering of all these calls.
                IN_MUFFLED_CHECK = true;
                ISound s = ForgeHooksClient.playSound(mc.getSoundHandler().sndManager, original);
                IN_MUFFLED_CHECK = false;

                if (s == this) {
                    // No filtering done, use the original sound's volume
                    volume = original.getVolume() * getMufflingFactor();
                } else if (s == null) {
                    // Full on mute; go ahead and shutdown
                    donePlaying = true;
                } else {
                    // Altered sound returned; adjust volume
                    volume = s.getVolume() * getMufflingFactor();
                }
            }
        }

        private float getMufflingFactor() {
            // Pull the TE from the sound position and see if supports muffling upgrades. If it does, calculate what
            // percentage of the original volume should be muted
            TileEntity te = mc.world.getTileEntity(new BlockPos(original.getXPosF(), original.getYPosF(), original.getZPosF()));
            if (te instanceof IUpgradeTile tile && tile.supportsUpgrade(Upgrade.MUFFLING)) {
                int maxInstalled = Upgrade.MUFFLING.getMaxInstalled();
                if (maxInstalled <= 0) {
                    return 1.0f;
                }
                int mufflerCount = tile.getInstalledUpgrades(Upgrade.MUFFLING);
                return 1.0f - (mufflerCount / (float) maxInstalled);
            }
            return 1.0f;
        }

        @Override
        public boolean isDonePlaying() {
            return donePlaying;
        }

        @Override
        public float getVolume() {
            return volume;
        }

        @Nonnull
        @Override
        public ResourceLocation getSoundLocation() {
            return original.getSoundLocation();
        }

        @Nullable
        @Override
        public SoundEventAccessor createAccessor(@Nonnull net.minecraft.client.audio.SoundHandler handler) {
            return original.createAccessor(handler);
        }

        @Nonnull
        @Override
        public Sound getSound() {
            return original.getSound();
        }

        @Nonnull
        @Override
        public SoundCategory getCategory() {
            return original.getCategory();
        }

        @Override
        public boolean canRepeat() {
            return original.canRepeat();
        }

        @Override
        public int getRepeatDelay() {
            return original.getRepeatDelay();
        }

        @Override
        public float getPitch() {
            return original.getPitch();
        }

        @Override
        public float getXPosF() {
            return original.getXPosF();
        }

        @Override
        public float getYPosF() {
            return original.getYPosF();
        }

        @Override
        public float getZPosF() {
            return original.getZPosF();
        }

        @Nonnull
        @Override
        public AttenuationType getAttenuationType() {
            return original.getAttenuationType();
        }
    }

    public static void restartSounds() {
        boolean hasPlayerSounds = playerSoundsEnabled();
        if (hasPlayerSounds != hadPlayerSounds) {
            hadPlayerSounds = hasPlayerSounds;
            if (hasPlayerSounds) {
                //If player sounds were muted and are no longer muted, then we want to try and restart all our sounds
                radiationSoundMap.values().forEach(SoundHandler::restartSounds);
            }
        }
    }

    private static void restartSounds(PlayerSound... sounds) {
        for (PlayerSound sound : sounds) {
            if (!sound.isDonePlaying() && soundEngine != null && !soundEngine.invPlayingSounds.containsKey(sound)) {
                //Note: We need to directly check the instanceToChannel, because isActive will give wrong results as it doesn't
                // get cleared out of the soundDeleteTime map. We also don't restart sounds if they marked themselves as stopped
                // as the cases we have that is if the player is no longer present or the player died, in which case the sound will
                // be removed and restarted as needed
                playSound(sound);
            }
        }
    }

    private static boolean playerSoundsEnabled() {
        return getVolume(SoundCategory.MASTER) > 0 && getVolume(SoundCategory.PLAYERS) > 0;
    }

    private static float getVolume(SoundCategory category) {
        return Minecraft.getMinecraft().gameSettings.getSoundLevel(category);
    }

}
