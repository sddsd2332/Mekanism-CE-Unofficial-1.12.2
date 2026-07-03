package mekanism.client.gui;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import mekanism.api.gear.IModule;
import mekanism.api.gear.config.ModuleConfigData;
import mekanism.client.gui.element.GuiElementHolder;
import mekanism.client.gui.element.button.TranslationButton;
import mekanism.client.gui.element.custom.module.GuiModuleScreen;
import mekanism.client.gui.element.scroll.GuiModuleScrollList;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.window.GuiMekaSuitHelmetOptions;
import mekanism.common.Mekanism;
import mekanism.common.MekanismItems;
import mekanism.common.MekanismLang;
import mekanism.common.MekanismSounds;
import mekanism.common.content.gear.Module;
import mekanism.common.inventory.container.ModuleTweakerContainer;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.network.PacketUpdateModuleSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class GuiModuleTweaker extends GuiMekanism<ModuleTweakerContainer> {

    private final ArmorPreview armorPreview = new ArmorPreview();
    private final BiConsumer<Integer, ModuleConfigData<?>> saveCallback;

    private GuiModuleScrollList scrollList;
    private GuiModuleScreen moduleScreen;
    private TranslationButton optionsButton;
    private int selected = -1;

    public GuiModuleTweaker(InventoryPlayer inventory) {
        super(new ModuleTweakerContainer(inventory));
        saveCallback = (dataIndex, configData) -> {
            if (moduleScreen != null) {
                IModule<?> module = moduleScreen.getCurrentModule();
                if (module != null && selected != -1) {
                    int slotIndex = inventorySlots.inventorySlots.get(selected).getSlotIndex();
                    Mekanism.packetHandler.sendToServer(PacketUpdateModuleSettings.create(slotIndex, module.getData(), dataIndex, configData));
                }
            }
        };
        xSize = 266;
        ySize += 20;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        Supplier<ItemStack> itemSupplier = () -> getStack(selected);
        addButton(new GuiElementHolder(this, 30, 136, 120, 18));
        moduleScreen = addButton(new GuiModuleScreen(this, 150, 20, itemSupplier, saveCallback, armorPreview));
        scrollList = addButton(new GuiModuleScrollList(this, 30, 20, 116, itemSupplier, this::onModuleSelected));
        optionsButton = addButton(new TranslationButton(this, 31, 137, 118, 16, MekanismLang.BUTTON_OPTIONS, this::openOptions));
        optionsButton.active = false;
        int size = inventorySlots.inventorySlots.size();
        for (int i = 0; i < size; i++) {
            Slot slot = inventorySlots.inventorySlots.get(i);
            final int index = i;
            if (selected == -1 && isValidItem(index)) {
                select(index);
            }
            addButton(new GuiSlot(SlotType.NORMAL, this, slot.xPos - 1, slot.yPos - 1)
                    .click((e, x, y) -> {
                        select(index);
                        return true;
                    }, 1.0F, () -> MekanismSounds.BEEP)
                    .overlayColor(isValidItem(index) ? null : () -> 0xCC333333)
                    .with(() -> index == selected ? SlotOverlay.SELECT : null));
        }
    }

    private void onModuleSelected(Module<?> module) {
        moduleScreen.setModule(module);
    }

    private void openOptions() {
        addWindow(new GuiMekaSuitHelmetOptions(this, (xSize - 140) / 2, (ySize - 140) / 2));
    }

    @Override
    public void keyTyped(char c, int keyCode) throws IOException {
        super.keyTyped(c, keyCode);
        if (selected != -1 && (isPreviousButton(keyCode) || isNextButton(keyCode))) {
            int curIndex = -1;
            IntList selectable = new IntArrayList();
            for (int index = 0, slots = inventorySlots.inventorySlots.size(); index < slots; index++) {
                if (isValidItem(index)) {
                    selectable.add(index);
                    if (index == selected) {
                        curIndex = selectable.size() - 1;
                    }
                }
            }
            int targetIndex;
            if (isPreviousButton(keyCode)) {
                targetIndex = curIndex == 0 ? selectable.size() - 1 : curIndex - 1;
            } else {
                targetIndex = curIndex + 1;
            }
            select(selectable.getInt(targetIndex % selectable.size()));
        }
    }

    private boolean isPreviousButton(int key) {
        return key == Keyboard.KEY_UP || key == Keyboard.KEY_LEFT;
    }

    private boolean isNextButton(int key) {
        return key == Keyboard.KEY_DOWN || key == Keyboard.KEY_RIGHT;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        moduleScreen.onRelease(mouseX, mouseY);
        super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleTextWithOffset(MekanismLang.MODULE_TWEAKER.translate(), 24, 6, getXSize());
        super.drawForegroundText(mouseX, mouseY);
    }

    private void select(int index) {
        if (isValidItem(index)) {
            selected = index;
            ItemStack stack = getStack(index);
            armorPreview.tryUpdateFull(stack);
            scrollList.updateItemAndList(stack);
            scrollList.clearSelection();
            if (optionsButton != null) {
                optionsButton.active = stack.getItem() == MekanismItems.MEKASUIT_HELMET;
            }
        }
    }

    private boolean isValidItem(int index) {
        return ModuleTweakerContainer.isTweakableItem(getStack(index));
    }

    private ItemStack getStack(int index) {
        if (index == -1) {
            return ItemStack.EMPTY;
        }
        return inventorySlots.inventorySlots.get(index).getStack();
    }

    public static class ArmorPreview implements Supplier<EntityLivingBase> {

        private final Map<EntityEquipmentSlot, Supplier<ItemStack>> armorItems = new EnumMap<>(EntityEquipmentSlot.class);
        private PreviewPlayer preview;

        private ArmorPreview() {
            for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
                if (slot.getSlotType() == EntityEquipmentSlot.Type.ARMOR) {
                    armorItems.put(slot, () -> {
                        ItemStack stack = Minecraft.getMinecraft().player.getItemStackFromSlot(slot);
                        return stack.isEmpty() ? getFallbackArmor(slot) : stack;
                    });
                }
            }
        }

        public void tryUpdateFull(ItemStack stack) {
            if (!stack.isEmpty() && stack.getItem() instanceof ItemArmor armor) {
                EntityEquipmentSlot slot = armor.armorType;
                armorItems.put(slot, () -> stack);
                updatePreview(slot, stack);
            }
        }

        public void updatePreview(EntityEquipmentSlot slot, ItemStack stack) {
            if (preview != null) {
                preview.setItemStackToSlot(slot, stack);
            }
        }

        public void resetToDefault(EntityEquipmentSlot slot) {
            if (preview != null && armorItems.containsKey(slot)) {
                updatePreview(slot, armorItems.get(slot).get());
            }
        }

        @Override
        public EntityLivingBase get() {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft.player == null || minecraft.world == null) {
                return minecraft.player;
            }
            if (preview == null || preview.world != minecraft.world) {
                preview = new PreviewPlayer(minecraft);
                preview.setAlwaysRenderNameTag(false);
                preview.setCustomNameTag("");
                preview.setSneaking(false);
                preview.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
                preview.setHeldItem(EnumHand.OFF_HAND, ItemStack.EMPTY);
                armorItems.forEach((slot, stackSupplier) -> preview.setItemStackToSlot(slot, stackSupplier.get()));
            }
            return preview;
        }

        private static ItemStack getFallbackArmor(EntityEquipmentSlot slot) {
            return switch (slot) {
                case FEET -> new ItemStack(MekanismItems.MEKASUIT_BOOTS);
                case LEGS -> new ItemStack(MekanismItems.MEKASUIT_PANTS);
                case CHEST -> new ItemStack(MekanismItems.MEKASUIT_BODYARMOR);
                case HEAD -> new ItemStack(MekanismItems.MEKASUIT_HELMET);
                default -> ItemStack.EMPTY;
            };
        }

        private static class PreviewPlayer extends EntityOtherPlayerMP {

            private PreviewPlayer(Minecraft minecraft) {
                super(minecraft.world, minecraft.player.getGameProfile());
            }

            @Override
            public boolean hasCustomName() {
                return false;
            }

            @Override
            public boolean getAlwaysRenderNameTagForRender() {
                return false;
            }

            @Override
            public boolean isInvisibleToPlayer(EntityPlayer player) {
                return true;
            }

            @Override
            public boolean isWearing(EnumPlayerModelParts part) {
                return part != EnumPlayerModelParts.CAPE && super.isWearing(part);
            }

            @Override
            public ResourceLocation getLocationCape() {
                return null;
            }
        }
    }
}
