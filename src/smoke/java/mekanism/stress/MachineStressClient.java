package mekanism.stress;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Client class is kept out of the dedicated server's class linkage. */
@SideOnly(Side.CLIENT)
public final class MachineStressClient {
    private boolean launched;

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!launched && mc.currentScreen instanceof GuiMainMenu) {
            launched = true;
            mc.gameSettings.pauseOnLostFocus = false;
            mc.gameSettings.renderDistanceChunks = 2;
            mc.launchIntegratedServer("machine-stress-" + MachineStressSmokeMod.runId, "Machine Stress Smoke",
                  new WorldSettings(0, GameType.CREATIVE, false, false, WorldType.FLAT).enableCommands());
        }
        if (mc.world != null && mc.player != null) MachineStressSmokeMod.clientReady = true;
        if (MachineStressSmokeMod.finished && !mc.isIntegratedServerRunning()) mc.shutdown();
    }
}
