package mekanism.common;

import mekanism.api.radiation.capability.IRadiationEntity;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.mekafishrod.DefaultMekaFishHook;
import mekanism.common.lib.radiation.capability.DefaultRadiationEntity;
import mekanism.common.network.PacketBoxBlacklist.BoxBlacklistMessage;
import mekanism.common.network.PacketConfigSync.ConfigSyncMessage;
import mekanism.common.network.PacketMekaFishHook;
import mekanism.common.network.PacketPlayerData.PlayerDataMessage;
import mekanism.common.network.PacketRadiationData;
import mekanism.common.network.PacketResetPlayerClient;
import mekanism.common.network.PacketSecurityUpdate.SecurityPacket;
import mekanism.common.network.PacketSecurityUpdate.SecurityUpdateMessage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;

public class CommonPlayerTracker {

    public CommonPlayerTracker() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPlayerLoginEvent(PlayerLoggedInEvent event) {
        EntityPlayer player = event.player;
        if (!player.world.isRemote) {
            MinecraftServer server = player.getServer();
            EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
            if (server == null || !server.isSinglePlayer()) {
                Mekanism.packetHandler.sendTo(new ConfigSyncMessage(MekanismConfig.local()), serverPlayer);
                Mekanism.logger.info("Sent config to '" + player.getDisplayNameString() + ".'");
            }
            Mekanism.packetHandler.sendTo(new BoxBlacklistMessage(), (EntityPlayerMP) player);
            Mekanism.packetHandler.sendTo(new SecurityUpdateMessage(SecurityPacket.FULL, null, null), serverPlayer);
            PacketRadiationData.sync(serverPlayer);
        }
    }

    @SubscribeEvent
    public void onPlayerLogoutEvent(PlayerLoggedOutEvent event) {
        Mekanism.playerState.clearPlayer(event.player.getUniqueID(), false);
        Mekanism.playerState.clearPlayerServerSideOnly(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void onPlayerDimChangedEvent(PlayerChangedDimensionEvent event) {
        EntityPlayer player = event.player;
        Mekanism.playerState.clearPlayer(player.getUniqueID(), false);
        Mekanism.playerState.reapplyServerSideOnly(player);
        PacketRadiationData.sync((EntityPlayerMP) player);
    }

    @SubscribeEvent
    public void cloneEvent(PlayerEvent.Clone event) {
        EntityPlayer oldPlayer = event.getOriginal();
        EntityPlayer player = event.getEntityPlayer();
        if (oldPlayer.hasCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null)) {
            IRadiationEntity old = oldPlayer.getCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null);
            if (player.hasCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null)) {
                player.getCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null).deserializeNBT(old.serializeNBT());
            }

        }

    }

    @SubscribeEvent
    public void respawnEvent(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent event) {
        EntityPlayer player = event.player;
        if (player.hasCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null)) {
            player.getCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null).set(0);
        }
        PacketRadiationData.sync((EntityPlayerMP) player);
        Mekanism.packetHandler.sendToAll(new PacketResetPlayerClient.ResetPlayerClientMessage(player.getUniqueID()));
    }


    @SubscribeEvent
    public void onPlayerStartTrackingEvent(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof EntityPlayer && event.getEntityPlayer() instanceof EntityPlayerMP) {
            Mekanism.packetHandler.sendTo(new PlayerDataMessage(event.getTarget().getUniqueID()), (EntityPlayerMP) event.getEntityPlayer());
        }
        if (event.getTarget() instanceof EntityFishHook hook) {
            Capabilities.getMekaFishCap(hook).ifPresent(
                    iMekaFishHook -> {
                        if (iMekaFishHook.isMekaFishHook()) {
                            Mekanism.packetHandler.sendToAllTracking(new PacketMekaFishHook.PacketMekaFishHookMessage(hook.getEntityId()), hook);
                        }
                    }
            );
        }
    }

    @SubscribeEvent
    public void attachCaps(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof EntityLivingBase) {
            DefaultRadiationEntity.Provider radiationProvider = new DefaultRadiationEntity.Provider();
            event.addCapability(DefaultRadiationEntity.Provider.NAME, radiationProvider);
        }
        if (event.getObject() instanceof EntityFishHook) {
            DefaultMekaFishHook.Provider fishProvider = new DefaultMekaFishHook.Provider();
            event.addCapability(DefaultMekaFishHook.Provider.NAME, fishProvider);
        }
    }


}
