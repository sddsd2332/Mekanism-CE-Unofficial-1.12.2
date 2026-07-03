package mekanism.client.render.hud;

import mekanism.client.gui.GuiUtils;
import mekanism.common.item.armor.ItemMekaSuitArmor;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StorageUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class MekaSuitEnergyLevel {

    private static final ResourceLocation BAR = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_BAR, "Base2.png");
    private static final ResourceLocation POWER_BAR = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_BAR, "horizontal_power_long.png");

    public static void onDrawScreenPre(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getType() == RenderGameOverlayEvent.ElementType.ARMOR) {
            double capacity = 0, stored = 0;
            for (ItemStack stack : mc.player.getArmorInventoryList()) {
                if (stack.getItem() instanceof ItemMekaSuitArmor armor) {
                    capacity += armor.getEnergyCapacity(stack);
                    stored += StorageUtils.getStoredEnergy(stack);
                }
            }
            if (capacity != 0) {
                int x = event.getResolution().getScaledWidth() / 2 - 91;
                int y = event.getResolution().getScaledHeight() - GuiIngameForge.left_height + 2;;
                int length = (int) Math.round((stored / capacity) * 79);
                GuiUtils.renderExtendedTexture(BAR, 2, 2, x, y, 81, 6);
                mc.renderEngine.bindTexture(POWER_BAR);
                GuiUtils.blit(x + 1, y + 1, length, 4, 0, 0, length, 4, 79, 4);
                mc.renderEngine.bindTexture(Gui.ICONS);
                GuiIngameForge.left_height += 8;
            }
        }
    }




}
