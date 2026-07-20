package mekanism.client;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import mekanism.api.IClientTicker;
import mekanism.api.gear.IModule;
import mekanism.api.gear.SwiftSneakHelp;
import mekanism.api.radial.RadialData;
import mekanism.client.gui.GuiRadialSelector;
import mekanism.client.jei.MekanismJEI;
import mekanism.client.render.hud.MekanismStatusOverlay;
import mekanism.client.render.lib.ScrollIncrementer;
import mekanism.client.sound.GeigerSound;
import mekanism.client.sound.SoundHandler;
import mekanism.common.CommonPlayerTickHandler;
import mekanism.common.KeySync;
import mekanism.common.Mekanism;
import mekanism.common.MekanismModules;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.ModuleHelper;
import mekanism.common.content.gear.mekasuit.ModuleVisionEnhancementUnit;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.item.armor.ItemMekaSuitBodyArmor;
import mekanism.common.item.armor.ItemMekaSuitHelmet;
import mekanism.common.item.armor.ItemMekaSuitPants;
import mekanism.common.item.interfaces.IJetpackItem;
import mekanism.common.item.interfaces.IJetpackItem.JetpackMode;
import mekanism.common.item.interfaces.IModeItem;
import mekanism.common.lib.radial.IGenericRadialModeItem;
import mekanism.common.lib.radiation.RadiationManager;
import mekanism.common.network.PacketModeChange.ModeChangMessage;
import mekanism.common.network.PacketPortableTeleporter.PortableTeleporterMessage;
import mekanism.common.network.PacketPortableTeleporter.PortableTeleporterPacketType;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.MovementInput;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.*;
import java.util.Map.Entry;

/**
 * Client-side tick handler for Mekanism. Used mainly for the update check upon startup.
 *
 * @author AidanBrady
 */
@SideOnly(Side.CLIENT)
public class ClientTickHandler {

    public static Minecraft minecraft = Minecraft.getMinecraft();
    public static Random rand = new Random();
    public static Set<IClientTicker> tickingSet = new ReferenceOpenHashSet<>();
    public static Map<EntityPlayer, TeleportData> portableTeleports = new Object2ObjectOpenHashMap<>();
    private static final ScrollIncrementer scrollIncrementer = new ScrollIncrementer(true);
    public static boolean visionEnhancement = false;
    private static boolean qioRecipeViewerRefreshPending;

    public boolean initHoliday = false;
    public boolean shouldReset = false;


    public static boolean isJetpackInUse(EntityPlayer player, ItemStack jetpack) {
        if (!player.isSpectator() && !jetpack.isEmpty()) {
            JetpackMode mode = ((IJetpackItem) jetpack.getItem()).getJetpackMode(jetpack);
            boolean guiOpen = minecraft.currentScreen != null;
            boolean ascending = minecraft.player.movementInput.jump;
            boolean rising = ascending && !guiOpen;
            if (mode == JetpackMode.NORMAL || mode == JetpackMode.VECTOR) {
                return rising;
            } else if (mode == JetpackMode.HOVER) {
                boolean descending = minecraft.player.movementInput.sneak;
                if (!rising || descending) {
                    return !CommonPlayerTickHandler.isOnGroundOrSleeping(player);
                }
                return true;
            }
        }
        return false;
    }

    public static boolean isScubaMaskOn(EntityPlayer player) {
        if (player != minecraft.player) {
            return Mekanism.playerState.isScubaMaskOn(player);
        }
        return CommonPlayerTickHandler.isScubaMaskOn(player, player.getItemStackFromSlot(EntityEquipmentSlot.CHEST));
    }

    public static boolean isGravitationalModulationOn(EntityPlayer player) {
        if (player != minecraft.player) {
            return Mekanism.playerState.isGravitationalModulationOn(player);
        }
        return CommonPlayerTickHandler.isGravitationalModulationOn(player);
    }

    public static boolean isVisionEnhancementOn(EntityPlayer player) {
        IModule<ModuleVisionEnhancementUnit> module = ModuleHelper.get().load(player.getItemStackFromSlot(EntityEquipmentSlot.HEAD), MekanismModules.VISION_ENHANCEMENT_UNIT);
        return module != null && module.isEnabled() && module.getContainerEnergy() > MekanismConfig.current().meka.mekaSuitEnergyUsageVisionEnhancement.val();
    }


    public static boolean isFlamethrowerOn(EntityPlayer player) {
        if (player != minecraft.player) {
            return Mekanism.playerState.isFlamethrowerOn(player);
        }
        return hasFlamethrower(player) && minecraft.gameSettings.keyBindUseItem.isKeyDown();
    }

    public static boolean hasFlamethrower(EntityPlayer player) {
        ItemStack currentItem = player.inventory.getCurrentItem();
        return CommonPlayerTickHandler.hasFlamethrowerGas(currentItem);
    }

    public static void portableTeleport(EntityPlayer player, EnumHand hand, FrequencyIdentity identity) {
        int delay = MekanismConfig.current().general.portableTeleporterDelay.val();
        if (delay == 0) {
            Mekanism.packetHandler.sendToServer(new PortableTeleporterMessage(PortableTeleporterPacketType.TELEPORT, hand, identity));
        } else {
            portableTeleports.put(player, new TeleportData(hand, identity, minecraft.world.getTotalWorldTime() + delay));
        }
    }

    public static void requestQIORecipeViewerRefresh() {
        qioRecipeViewerRefreshPending = true;
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent event) {
        if (event.phase == Phase.START) {
            tickStart();
        }
    }

    public void tickStart() {
        MekanismClient.ticksPassed++;

        if (qioRecipeViewerRefreshPending) {
            qioRecipeViewerRefreshPending = false;
            MekanismJEI.refreshRecipeTransferButtons();
        }

        if (!Mekanism.proxy.isPaused()) {
            for (Iterator<IClientTicker> iter = tickingSet.iterator(); iter.hasNext(); ) {
                IClientTicker ticker = iter.next();

                if (ticker.needsTicks()) {
                    ticker.clientTick();
                } else {
                    iter.remove();
                }
            }
        }

        if (minecraft.world != null) {
            shouldReset = true;
        } else if (shouldReset) {
            MekanismClient.reset();
            shouldReset = false;
        }


        if (minecraft.world != null && minecraft.player != null && !Mekanism.proxy.isPaused()) {
            if (!initHoliday || MekanismClient.ticksPassed % 1200 == 0) {
                HolidayManager.check();
                initHoliday = true;
            }

            //Reboot player sounds if needed
            SoundHandler.restartSounds();


            RadiationManager.INSTANCE.tickClient(minecraft.player);


            UUID playerUUID = minecraft.player.getUniqueID();


            // Update player's state for various items; this also automatically notifies server if something changed and
            // kicks off sounds as necessary
            ItemStack jetpack = IJetpackItem.getActiveJetpack(minecraft.player);
            boolean jetpackInUse = isJetpackInUse(minecraft.player, jetpack);
            Mekanism.playerState.setJetpackState(playerUUID, jetpackInUse, true);
            Mekanism.playerState.setScubaMaskState(playerUUID, isScubaMaskOn(minecraft.player), true);
            Mekanism.playerState.setGravitationalModulationState(playerUUID, isGravitationalModulationOn(minecraft.player), true);
            Mekanism.playerState.setFlamethrowerState(playerUUID, hasFlamethrower(minecraft.player), isFlamethrowerOn(minecraft.player), true);

            for (Iterator<Entry<EntityPlayer, TeleportData>> iter = portableTeleports.entrySet().iterator(); iter.hasNext(); ) {
                Entry<EntityPlayer, TeleportData> entry = iter.next();
                EntityPlayer player = entry.getKey();
                for (int i = 0; i < 100; i++) {
                    double x = player.posX + rand.nextDouble() - 0.5D;
                    double y = player.posY + rand.nextDouble() * 2 - 2D;
                    double z = player.posZ + rand.nextDouble() - 0.5D;
                    minecraft.world.spawnParticle(EnumParticleTypes.PORTAL, x, y, z, 0, 1, 0);
                }
                TeleportData data = entry.getValue();
                if (minecraft.world.getTotalWorldTime() == data.teleportTime) {
                    Mekanism.packetHandler.sendToServer(new PortableTeleporterMessage(PortableTeleporterPacketType.TELEPORT, data.hand, data.identity));
                    iter.remove();
                }
            }

            if (!jetpack.isEmpty()) {
                ItemStack primaryJetpack = IJetpackItem.getPrimaryJetpack(minecraft.player);
                if (!primaryJetpack.isEmpty()) {
                    JetpackMode primaryMode = ((IJetpackItem) primaryJetpack.getItem()).getJetpackMode(primaryJetpack);
                    JetpackMode mode = IJetpackItem.getPlayerJetpackMode(minecraft.player, primaryMode, p -> p.movementInput.jump);
                    MekanismClient.updateKey(minecraft.player.movementInput.jump, KeySync.ASCEND);
                    double jetpackThrust = ((IJetpackItem) primaryJetpack.getItem()).getJetpackThrust(primaryJetpack);
                    if (jetpackInUse && IJetpackItem.handleJetpackMotion(minecraft.player, mode, jetpackThrust, p -> p.movementInput.jump)) {
                        minecraft.player.fallDistance = 0.0F;
                    }
                }
            }

            if (isScubaMaskOn(minecraft.player) && minecraft.player.getAir() == 300) {
                minecraft.player.getActivePotionEffects().forEach(effect -> {
                    if (MekanismUtils.shouldSpeedUpEffect(effect)) {
                        for (int i = 0; i < 9; i++) {
                            MekanismUtils.speedUpEffectSafely(minecraft.player, effect);
                        }
                    }
                });
            }

            if (isVisionEnhancementOn(minecraft.player)) {
                visionEnhancement = true;
                // adds if it doesn't exist, otherwise tops off duration to 220. equal or less than 200 will make vision flickers
                minecraft.player.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION, 220, 0, false, false));
            } else if (visionEnhancement) {
                visionEnhancement = false;
                PotionEffect effect = minecraft.player.getActivePotionEffect(MobEffects.NIGHT_VISION);
                if (effect != null && effect.getDuration() <= 220) {
                    //Only remove it if it is our effect and not one that has a longer remaining duration
                    minecraft.player.removePotionEffect(MobEffects.NIGHT_VISION);
                }
            }

            if (minecraft.currentScreen == null || minecraft.currentScreen instanceof GuiRadialSelector) {
                if (!MekKeyHandler.getIsKeyPressed(MekanismKeyHandler.handModeSwitchKey) || (!updateSelectorRenderer(EntityEquipmentSlot.MAINHAND) && !updateSelectorRenderer(EntityEquipmentSlot.OFFHAND))) {
                    if (minecraft.currentScreen != null) {
                        //If we currently have a radial selector gui open but shouldn't close it
                        minecraft.displayGuiScreen(null);
                    }
                }
            }

            if (MekanismConfig.current().client.enablePlayerSounds.val()) {
                RadiationManager.RadiationScale scale = RadiationManager.INSTANCE.getClientScale();
                if (scale != RadiationManager.RadiationScale.NONE && !SoundHandler.radiationSoundMap.containsKey(scale)) {
                    GeigerSound sound = GeigerSound.create(minecraft.player, scale);
                    SoundHandler.radiationSoundMap.put(scale, sound);
                    SoundHandler.playSound(sound);
                }
            }

        }
    }


    private boolean updateSelectorRenderer(EntityEquipmentSlot slot) {
        if (minecraft.player != null) {
            ItemStack stack = minecraft.player.getItemStackFromSlot(slot);
            if (stack.getItem() instanceof IGenericRadialModeItem item) {
                RadialData<?> radialData = item.getRadialData(stack);
                if (radialData != null) {
                    if (!(minecraft.currentScreen instanceof GuiRadialSelector screen) || !screen.hasMatchingData(slot, radialData)) {
                        GuiRadialSelector newSelector = new GuiRadialSelector(slot, radialData, () -> minecraft.player);
                        newSelector.tryInheritCurrentPath(minecraft.currentScreen);
                        minecraft.displayGuiScreen(newSelector);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @SubscribeEvent
    public void onMouseEvent(MouseEvent event) {
        if (MekanismConfig.current().client.allowModeScroll.val() && minecraft.player != null && minecraft.player.isSneaking()) {
            double delta = event.getDwheel();
            if (delta != 0 && IModeItem.isModeItem(minecraft.player, EntityEquipmentSlot.MAINHAND)) {
                int shift = scrollIncrementer.scroll(delta);
                if (shift != 0) {
                    MekanismStatusOverlay.INSTANCE.setTimer();
                    Mekanism.packetHandler.sendToServer(new ModeChangMessage(EntityEquipmentSlot.MAINHAND, shift));
                }
                event.setCanceled(true);
            }
        }
    }


    @SubscribeEvent
    public void onFogLighting(EntityViewRenderEvent.FogColors event) {
        if (visionEnhancement) {
            float oldRatio = 0.1F;
            float newRatio = 1 - oldRatio;
            float red = oldRatio * event.getRed();
            float green = oldRatio * event.getGreen();
            float blue = oldRatio * event.getBlue();
            event.setRed(red + newRatio * 0.4F);
            event.setGreen(green + newRatio * 0.8F);
            event.setBlue(blue + newRatio * 0.4F);
        }
    }

    @SubscribeEvent
    public void onFog(EntityViewRenderEvent.RenderFogEvent event) {
        if (visionEnhancement) {
            float fog = 0.1F;
            IModule<ModuleVisionEnhancementUnit> module = ModuleHelper.get().load(minecraft.player.getItemStackFromSlot(EntityEquipmentSlot.HEAD), MekanismModules.VISION_ENHANCEMENT_UNIT);
            if (module != null) {
                fog -= module.getInstalledCount() * 0.022F;
            }
            GlStateManager.setFogDensity(fog);
            GlStateManager.setFog(GlStateManager.FogMode.EXP2);
        }
    }

    @SubscribeEvent
    public void renderEntityPre(RenderPlayerEvent.Pre evt) {
        setModelVisibility(evt.getEntityPlayer(), evt.getRenderer(), false);
    }

    @SubscribeEvent
    public void renderEntityPost(RenderPlayerEvent.Post evt) {
        setModelVisibility(evt.getEntityPlayer(), evt.getRenderer(), true);
    }

    public static void setModelVisibility(EntityPlayer entity, Render<?> entityModel, boolean showModel) {
        if (entityModel instanceof RenderPlayer renderPlayer) {
            if (entity.getItemStackFromSlot(EntityEquipmentSlot.HEAD).getItem() instanceof ItemMekaSuitHelmet) {
                renderPlayer.getMainModel().bipedHead.showModel = showModel;
                renderPlayer.getMainModel().bipedHeadwear.showModel = showModel;
                renderPlayer.getMainModel().bipedHead.isHidden = !showModel;
                renderPlayer.getMainModel().bipedHeadwear.isHidden = !showModel;
            }
            if (entity.getItemStackFromSlot(EntityEquipmentSlot.CHEST).getItem() instanceof ItemMekaSuitBodyArmor) {
                renderPlayer.getMainModel().bipedLeftArmwear.showModel = showModel;
                renderPlayer.getMainModel().bipedRightArmwear.showModel = showModel;
                renderPlayer.getMainModel().bipedBodyWear.showModel = showModel;
                renderPlayer.getMainModel().bipedLeftArmwear.isHidden = !showModel;
                renderPlayer.getMainModel().bipedRightArmwear.isHidden = !showModel;
                renderPlayer.getMainModel().bipedBodyWear.isHidden = !showModel;
            }
            if (entity.getItemStackFromSlot(EntityEquipmentSlot.LEGS).getItem() instanceof ItemMekaSuitPants) {
                renderPlayer.getMainModel().bipedLeftLegwear.showModel = showModel;
                renderPlayer.getMainModel().bipedRightLegwear.showModel = showModel;
                renderPlayer.getMainModel().bipedBodyWear.showModel = showModel;
                renderPlayer.getMainModel().bipedLeftLegwear.isHidden = !showModel;
                renderPlayer.getMainModel().bipedRightLegwear.isHidden = !showModel;
                renderPlayer.getMainModel().bipedBodyWear.isHidden = !showModel;
            }
        }
    }

    //Maybe it works
    @SubscribeEvent
    public void remove(WorldEvent.Unload event) {
        portableTeleports.remove(minecraft.player);
    }

    private static class TeleportData {

        private EnumHand hand;
        private FrequencyIdentity identity;
        private long teleportTime;

        public TeleportData(EnumHand h, FrequencyIdentity f, long t) {
            hand = h;
            identity = f;
            teleportTime = t;
        }
    }

    @SubscribeEvent
    public void SneakingSpeedBonus(InputUpdateEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        ItemStack stack = player.getItemStackFromSlot(EntityEquipmentSlot.LEGS);
        MovementInput input = event.getMovementInput();
        if (stack.getItem() instanceof SwiftSneakHelp help) {
            float getSneakingSpeedBonus = MathHelper.clamp(0.3F + (help.getSneakingSpeedBonusLevel(stack) * 0.15F), 0.0F, 1.0F);
            input.moveStrafe = 0.0F;
            input.moveForward = 0.0F;
            if (minecraft.gameSettings.keyBindForward.isKeyDown()) {
                ++input.moveForward;
            }
            if (minecraft.gameSettings.keyBindBack.isKeyDown()) {
                --input.moveForward;
            }
            if (minecraft.gameSettings.keyBindLeft.isKeyDown()) {
                ++input.moveStrafe;
            }
            if (minecraft.gameSettings.keyBindRight.isKeyDown()) {
                --input.moveStrafe;
            }
            if (input.sneak) {
                input.moveStrafe *= getSneakingSpeedBonus;
                input.moveForward *= getSneakingSpeedBonus;
            }
        }
    }

}
