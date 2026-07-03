package mekanism.client;

import baubles.api.BaublesApi;
import mekanism.api.Coord4D;
import mekanism.client.sound.SoundHandler;
import mekanism.common.KeySync;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.MekanismSounds;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.inventory.container.ModuleTweakerContainer;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.item.interfaces.IModeItem;
import mekanism.common.network.PacketBaublesModeChange.BaublesModeChangMessage;
import mekanism.common.network.PacketModeChange.ModeChangMessage;
import mekanism.common.network.PacketOpenGui.OpenGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public class MekanismKeyHandler extends MekKeyHandler {

    public static final String keybindCategory = Mekanism.MOD_NAME;
    public static KeyBinding handModeSwitchKey = new KeyBinding(MekanismLang.KEY_HAND_MODE.getTranslationKey(), Keyboard.KEY_M, keybindCategory);
    public static KeyBinding headModeSwitchKey = new KeyBinding(MekanismLang.KEY_HEAD_MODE.getTranslationKey(), Keyboard.KEY_V, keybindCategory);
    public static KeyBinding chestModeSwitchKey = new KeyBinding(MekanismLang.KEY_CHEST_MODE.getTranslationKey(), Keyboard.KEY_G, keybindCategory);
    public static KeyBinding legsModeSwitchKey = new KeyBinding(MekanismLang.KEY_LEGS_MODE.getTranslationKey(), Keyboard.KEY_J, keybindCategory);
    public static KeyBinding feetModeSwitchKey = new KeyBinding(MekanismLang.KEY_FEET_MODE.getTranslationKey(), Keyboard.KEY_B, keybindCategory);

    public static KeyBinding voiceKey = new KeyBinding(MekanismLang.KEY_VOICE.getTranslationKey(), Keyboard.KEY_U, keybindCategory);
    /**
     * 替换 为 detailsKey ？
     */
    public static KeyBinding sneakKey = Minecraft.getMinecraft().gameSettings.keyBindSneak;
    public static KeyBinding jumpKey = Minecraft.getMinecraft().gameSettings.keyBindJump;

    public static KeyBinding hudKey = new KeyBinding(MekanismLang.KEY_HUD.getTranslationKey(), Keyboard.KEY_H, keybindCategory);

    public static KeyBinding boostKey = new KeyBinding(MekanismLang.KEY_BOOST.getTranslationKey(), Keyboard.KEY_N, keybindCategory);
    public static KeyBinding moduleTweakerKey = new KeyBinding(MekanismLang.KEY_MODULE_TWEAKER.getTranslationKey(), Keyboard.KEY_BACKSLASH, keybindCategory);

    private static Builder BINDINGS = new Builder()
            .addBinding(handModeSwitchKey, false)
            .addBinding(headModeSwitchKey, false)
            .addBinding(chestModeSwitchKey, false)
            .addBinding(legsModeSwitchKey, false)
            .addBinding(feetModeSwitchKey, false)
            .addBinding(voiceKey, true)
            .addBinding(hudKey, false)
            .addBinding(boostKey, false)
            .addBinding(moduleTweakerKey, false);

    public MekanismKeyHandler() {
        super(BINDINGS);
        ClientRegistry.registerKeyBinding(handModeSwitchKey);
        ClientRegistry.registerKeyBinding(headModeSwitchKey);
        ClientRegistry.registerKeyBinding(chestModeSwitchKey);
        ClientRegistry.registerKeyBinding(legsModeSwitchKey);
        ClientRegistry.registerKeyBinding(feetModeSwitchKey);
        ClientRegistry.registerKeyBinding(voiceKey);
        ClientRegistry.registerKeyBinding(hudKey);
        ClientRegistry.registerKeyBinding(boostKey);
        ClientRegistry.registerKeyBinding(moduleTweakerKey);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onTick(InputEvent event) {
        keyTick();
    }

    @Override
    public void keyDown(KeyBinding kb, boolean isRepeat) {
        EntityPlayer player = FMLClientHandler.instance().getClient().player;
        if (kb == handModeSwitchKey) {
            if (player != null) {
                if (IModeItem.isModeItem(player, EntityEquipmentSlot.MAINHAND, false)) {
                    Mekanism.packetHandler.sendToServer(new ModeChangMessage(EntityEquipmentSlot.MAINHAND, player.isSneaking()));
                } else if (!IModeItem.isModeItem(player, EntityEquipmentSlot.MAINHAND) && IModeItem.isModeItem(player, EntityEquipmentSlot.OFFHAND)) {
                    Mekanism.packetHandler.sendToServer(new ModeChangMessage(EntityEquipmentSlot.OFFHAND, player.isSneaking()));
                }
            }
        } else if (kb == headModeSwitchKey) {
            handlePotentialModeItem(EntityEquipmentSlot.HEAD);
        } else if (kb == chestModeSwitchKey) {
            handlePotentialModeItem(EntityEquipmentSlot.CHEST);
        } else if (kb == legsModeSwitchKey) {
            handlePotentialModeItem(EntityEquipmentSlot.LEGS);
        } else if (kb == feetModeSwitchKey) {
            handlePotentialModeItem(EntityEquipmentSlot.FEET);
        } else if (kb == hudKey) {
            MekanismConfig.current().client.enableHUD.set(!MekanismConfig.current().client.enableHUD.val());
        } else if (kb == boostKey) {
            MekanismClient.updateKey(boostKey, KeySync.BOOST);
        } else if (kb == moduleTweakerKey) {
            if (player != null && ModuleTweakerContainer.hasTweakableItem(player)) {
                Mekanism.packetHandler.sendToServer(new OpenGui(Coord4D.get(player), 0, 77));
            }
        }
    }


    private static void handlePotentialModeItem(EntityEquipmentSlot slot) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player != null) {
            if (IModeItem.isModeItem(player, slot)) {
                Mekanism.packetHandler.sendToServer(new ModeChangMessage(slot, player.isSneaking()));
                SoundHandler.playSound(MekanismSounds.HYDRAULIC);
            } else if (Mekanism.hooks.Baubles) {
                setBaublesMode(player, slot);
            }
        }
    }


    @Optional.Method(modid = MekanismHooks.Baubles_MOD_ID)
    public static void setBaublesMode(EntityPlayer player, EntityEquipmentSlot slot) {
        IItemHandler baubles = BaublesApi.getBaublesHandler(player);
        for (int i = 0; i < baubles.getSlots(); i++) {
            ItemStack stack = baubles.getStackInSlot(i);
            if (stack.getItem().isValidArmor(stack, slot, player) && IModeItem.isModeItem(stack, slot)) {
                if (!GasInventorySlot.isGasContainerItem(stack) || GasInventorySlot.getContainedGas(stack) != null) {
                    Mekanism.packetHandler.sendToServer(new BaublesModeChangMessage(i, player.isSneaking()));
                    SoundHandler.playSound(MekanismSounds.HYDRAULIC);
                }
            }
        }
    }


    @Override
    public void keyUp(KeyBinding kb) {
        if (kb == boostKey) {
            MekanismClient.updateKey(boostKey, KeySync.BOOST);
        }
    }
}
