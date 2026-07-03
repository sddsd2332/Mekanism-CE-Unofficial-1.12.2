package mekanism.client.gui.robit;

import io.netty.buffer.Unpooled;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.text.BackgroundType;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.entity.EntityRobit;
import mekanism.common.inventory.container.robit.ContainerRobitRepair;
import mekanism.common.util.LangUtils;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerRepair;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

@SideOnly(Side.CLIENT)
public class GuiRobitRepair extends GuiRobit<ContainerRobitRepair> implements IContainerListener {

    private static final ResourceLocation ANVIL_GUI = new ResourceLocation("textures/gui/container/anvil.png");
    private static final int MAX_ITEM_NAME_LENGTH = 50;

    private final ContainerRepair repairContainer;
    private final InventoryPlayer playerInventory;
    private GuiTextField itemNameField;
    private long msDisplayCost;

    public GuiRobitRepair(InventoryPlayer inventory, EntityRobit entity) {
        super(new ContainerRobitRepair(inventory, entity), entity);
        playerInventory = inventory;
        repairContainer = (ContainerRepair) inventorySlots;
        inventoryLabelY += 1;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        itemNameField = addButton(new GuiTextField(this, 0, 60, 21, 103, 12)
              .setTextColor(-1)
              .setDisabledTextColor(-1)
              .setBackground(BackgroundType.NONE)
              .setMaxLength(MAX_ITEM_NAME_LENGTH)
              .setCanLoseFocus(false)
              .setResponder(this::onNameChanged));
        itemNameField.setEnabled(repairContainer.getSlot(0).getHasStack());
        itemNameField.setFocused(true);
        inventorySlots.removeListener(this);
        inventorySlots.addListener(this);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        inventorySlots.removeListener(this);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTick, int mouseX, int mouseY) {
        MekanismRenderer.resetColor();
        mc.getTextureManager().bindTexture(ANVIL_GUI);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
        drawTexturedModalRect(guiLeft + 59, guiTop + 20, 0, ySize + (repairContainer.getSlot(0).getHasStack() ? 0 : 16), 110, 16);
        if ((repairContainer.getSlot(0).getHasStack() || repairContainer.getSlot(1).getHasStack()) && !repairContainer.getSlot(2).getHasStack()) {
            drawTexturedModalRect(guiLeft + 99, guiTop + 45, xSize, 0, 28, 21);
        }
        MekanismRenderer.resetColor();
        this.buttons.forEach(button -> button.render(mouseX, mouseY, partialTick));
        MekanismRenderer.resetColor();
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleTextWithOffset(new TextComponentString(LangUtils.localize("gui.robit.repair")), itemNameField.getRelativeX(),
              6, itemNameField.getRelativeX() + itemNameField.getWidth() + 4, 0);
        renderInventoryText(60);
        if (repairContainer.maximumCost > 0) {
            if (msDisplayCost == 0) {
                msDisplayCost = GuiElement.getMillis();
            }
            int textColor = 8453920;
            boolean renderCost = true;
            String text = I18n.format("container.repair.cost", repairContainer.maximumCost);
            if (repairContainer.maximumCost >= 40 && !mc.player.capabilities.isCreativeMode) {
                text = LangUtils.localize("container.repair.expensive");
                textColor = 16736352;
            } else if (!repairContainer.getSlot(2).getHasStack()) {
                renderCost = false;
            } else if (!repairContainer.getSlot(2).canTakeStack(playerInventory.player)) {
                textColor = 16736352;
            }
            if (renderCost) {
                int minX = Math.max(itemNameField.getRelativeX(), xSize - fontRenderer.getStringWidth(text) - 10);
                int maxX = xSize - 8;
                drawRect(minX, 67, maxX, 79, 0x4F000000);
                drawScaledScrollingString(new TextComponentString(text), minX, 69, TextAlignment.RIGHT, textColor, maxX - minX, 1, true, 1, msDisplayCost);
            }
        } else {
            msDisplayCost = 0;
        }
        super.drawForegroundText(mouseX, mouseY);
    }

    @Override
    protected boolean shouldOpenGui(int guiId) {
        return guiId != GUI_REPAIR;
    }

    @Override
    public void sendAllContents(@Nonnull Container container, @Nonnull NonNullList<ItemStack> list) {
        sendSlotContents(container, 0, container.getSlot(0).getStack());
    }

    @Override
    public void sendSlotContents(@Nonnull Container container, int slotID, @Nonnull ItemStack stack) {
        if (slotID == 0) {
            itemNameField.setTextSilently(stack.isEmpty() ? "" : stack.getDisplayName());
            itemNameField.setEnabled(!stack.isEmpty());
            itemNameField.setFocused(!stack.isEmpty());
            if (!stack.isEmpty()) {
                sendItemName(itemNameField.getText());
            }
        }
    }

    private void onNameChanged(String name) {
        if (!name.isEmpty()) {
            ItemStack stack = repairContainer.getSlot(0).getStack();
            if (!stack.isEmpty() && !stack.hasDisplayName() && name.equals(stack.getDisplayName())) {
                name = "";
            }
            sendItemName(name);
        }
    }

    private void sendItemName(String name) {
        repairContainer.updateItemName(name);
        mc.player.connection.sendPacket(new CPacketCustomPayload("MC|ItemName", new PacketBuffer(Unpooled.buffer()).writeString(name)));
    }

    @Override
    public void sendWindowProperty(@Nonnull Container container, int varToUpdate, int newValue) {
    }

    @Override
    public void sendAllWindowProperties(@Nonnull Container container, @Nonnull IInventory inventory) {
    }
}
