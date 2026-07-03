package mekanism.client.render;

import mekanism.api.gear.IHUDElement;
import mekanism.client.gui.GuiUtils;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.HUDElement;
import mekanism.common.content.gear.IModuleContainerItem;
import mekanism.common.content.gear.ModuleHelper;
import mekanism.common.item.ItemMekaTool;
import mekanism.common.item.armor.ItemMekaSuitArmor;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StorageUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.function.Predicate;

public class HUDRenderer {

    private static final EntityEquipmentSlot[] EQUIPMENT_ORDER = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET, EntityEquipmentSlot.MAINHAND,
            EntityEquipmentSlot.OFFHAND};


    private static final ResourceLocation[] ARMOR_ICONS = {MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_HUD, "hud_mekasuit_helmet.png"),
            MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_HUD, "hud_mekasuit_chest.png"),
            MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_HUD, "hud_mekasuit_leggings.png"),
            MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_HUD, "hud_mekasuit_boots.png")};

    private static final ResourceLocation TOOL_ICON = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_HUD, "hud_mekatool.png");

    private static final ResourceLocation COMPASS = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "Compass.png");

    private long lastTick = -1;
    private float prevRotationYaw;
    private float prevRotationPitch;

    public void renderHUD(Minecraft minecraft, FontRenderer font, float partialTick, int screenWidth, int screenHeight, int maxTextHeight,
                          boolean reverseHud) {
        EntityPlayer player = minecraft.player;
        update(minecraft.world, player);
        if (MekanismConfig.current().client.hudOpacity.val() < 0.05F) {
            return;
        }
        int color = HUDElement.HUDColor.REGULAR.getColorARGB();
        GlStateManager.pushMatrix();
        float yawJitter = -absSqrt(player.rotationYawHead - prevRotationYaw);
        float pitchJitter = -absSqrt(player.rotationPitch - prevRotationPitch);
        GlStateManager.translate(yawJitter, pitchJitter, 0);
        if (MekanismConfig.current().client.hudCompassEnabled.val()) {
            renderCompass(player, font, partialTick, screenWidth, screenHeight, maxTextHeight, reverseHud, color);
        }
        renderMekaSuitEnergyIcons(player, font, color);
        renderMekaSuitModuleIcons(player, font, screenWidth, screenHeight, reverseHud, color);

        GlStateManager.popMatrix();
    }


    private void update(World level, EntityPlayer player) {
        // if we're just now rendering the HUD after a pause, reset the pitch/yaw trackers
        if (lastTick == -1 || level.getTotalWorldTime() - lastTick > 1) {
            prevRotationYaw = player.rotationYaw;
            prevRotationPitch = player.rotationPitch;
        }
        lastTick = level.getTotalWorldTime();
        float yawDiff = player.rotationYawHead - prevRotationYaw;
        float pitchDiff = player.rotationPitch - prevRotationPitch;
        float jitter = MekanismConfig.current().client.hudJitter.val();
        prevRotationYaw += yawDiff / jitter;
        prevRotationPitch += pitchDiff / jitter;
    }

    private static float absSqrt(float val) {
        float ret = (float) Math.sqrt(Math.abs(val));
        return val < 0 ? -ret : ret;
    }


    public static final EntityEquipmentSlot[] ARMOR_SLOTS = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET};
    public static final EntityEquipmentSlot[] HAND_SLOTS = {EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND};


    private void renderMekaSuitEnergyIcons(EntityPlayer player, FontRenderer font, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(10, 10, 0);
        int posX = 0;
        Predicate<Item> showArmorPercent = item -> item instanceof ItemMekaSuitArmor;
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            posX += renderEnergyIcon(player, font, posX, color, ARMOR_ICONS[i], ARMOR_SLOTS[i], showArmorPercent);
        }
        Predicate<Item> showToolPercent = item ->  item instanceof ItemMekaTool;
        for (EntityEquipmentSlot hand : HAND_SLOTS) {
            posX += renderEnergyIcon(player, font, posX, color, TOOL_ICON, hand, showToolPercent);
        }
        GlStateManager.popMatrix();
    }


    private int renderEnergyIcon(EntityPlayer player, FontRenderer font, int posX, int color, ResourceLocation icon, EntityEquipmentSlot slot,
                                 Predicate<Item> showPercent) {
        ItemStack stack = player.getItemStackFromSlot(slot);
        if (showPercent.test(stack.getItem())) {
            renderHUDElement(font, posX, 0,  ModuleHelper.get().hudElementPercent(icon, StorageUtils.getEnergyRatio(stack)), color, false);
            return 48;
        }
        return 0;
    }


    private void renderMekaSuitModuleIcons(EntityPlayer player, FontRenderer font, int screenWidth, int screenHeight, boolean reverseHud, int color) {
        int startX = screenWidth - 10;
        int curY = screenHeight - 10;
        GlStateManager.pushMatrix();
        for (EntityEquipmentSlot type : EQUIPMENT_ORDER){
            ItemStack stack = player.getItemStackFromSlot(type);
            if (stack.getItem() instanceof IModuleContainerItem item) {
                for (IHUDElement element : item.getHUDElements(player, stack)) {
                    curY -= 18;
                    if (reverseHud) {
                        //Align the mekasuit module icons to the left of the screen
                        renderHUDElement(font, 10, curY, element, color, false);
                    } else {
                        int elementWidth = 24 + font.getStringWidth(element.getText());
                        renderHUDElement(font, startX - elementWidth, curY, element, color, true);
                    }
                }
            }
        }
        GlStateManager.popMatrix();
    }


    private void renderHUDElement(FontRenderer font, int x, int y, IHUDElement element, int color, boolean iconRight) {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        MekanismRenderer.color(color);
        font.drawString(element.getText(), iconRight ? x : x + 18, y + 5, element.getColor(), false);
        MekanismRenderer.resetColor();
        MekanismRenderer.color(color);
        Minecraft.getMinecraft().renderEngine.bindTexture(element.getIcon());
        GuiUtils.blit(iconRight ? x + font.getStringWidth(element.getText()) + 2 : x, y, 0, 0, 16, 16, 16, 16);
        MekanismRenderer.resetColor();
    }


    private void renderCompass(EntityPlayer player, FontRenderer font, float partialTick, int screenWidth, int screenHeight, int maxTextHeight, boolean reverseHud,
                               int color) {
        //Reversed hud causes the compass to render on the right side of the screen
        int posX = reverseHud ? screenWidth - 125 : 25;
        //Pin the compass above the bottom of the screen and also above the text hud that may render below it
        int posY = Math.min(screenHeight - 20, maxTextHeight) - 80;
        GlStateManager.pushMatrix();
        GlStateManager.translate(posX + 50, posY + 50, 0);
        GlStateManager.pushMatrix();

        GlStateManager.pushMatrix();
        GlStateManager.scale(0.7F, 0.7F, 0.7F);
        BlockPos pos = new BlockPos(player.posX, player.posY, player.posZ);
        String coords = LangUtils.localize(pos.getX() + " " + pos.getY() + " " + pos.getZ());
        font.drawString(coords, -font.getStringWidth(coords) / 2F, -4, color, false);
        GlStateManager.popMatrix();

        float angle = 180 - getViewYRot(player, partialTick);
        GlStateManager.rotate(-60, 1, 0, 0);
        GlStateManager.rotate(angle, 0, 0, 1);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        MekanismRenderer.color(color);
        rotateStr(font, LangUtils.localize("direction.north.short"), angle, 0, color);
        rotateStr(font, LangUtils.localize("direction.east.short"), angle, 90, color);
        rotateStr(font, LangUtils.localize("direction.south.short"), angle, 180, color);
        rotateStr(font, LangUtils.localize("direction.west.short"), angle, 270, color);
        Minecraft.getMinecraft().renderEngine.bindTexture(COMPASS);
        GuiUtils.blit(-50, -50, 100, 100, 0, 0, 256, 256, 256, 256);
        MekanismRenderer.resetColor();
        GlStateManager.popMatrix();
        GlStateManager.popMatrix();
    }

    private void rotateStr(FontRenderer font, String langEntry, float rotation, float shift, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.rotate(shift, 0, 0, 1);
        GlStateManager.translate(0, -50, 0);
        GlStateManager.rotate((-rotation - shift), 0, 0, 1);
        GuiUtils.drawString(font, langEntry, -2.5F, -4, color, false);
        GlStateManager.popMatrix();
    }


    public float getViewYRot(EntityPlayer player, float pPartialTicks) {
        return pPartialTicks == 1.0F ? player.rotationYawHead : lerp(pPartialTicks, player.prevRotationYawHead, player.rotationYawHead);
    }

    public static float lerp(float pDelta, float pStart, float pEnd) {
        return pStart + pDelta * (pEnd - pStart);
    }


}
