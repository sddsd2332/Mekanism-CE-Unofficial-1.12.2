package mekanism.client.gui.robit;

import io.netty.buffer.Unpooled;
import mekanism.client.gui.element.GuiSlot;
import mekanism.common.entity.EntityRobit;
import mekanism.common.inventory.container.robit.ContainerRobitRepair;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
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
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nonnull;
import java.io.IOException;

@SideOnly(Side.CLIENT)
public class GuiRobitRepair extends GuiRobit implements IContainerListener {

    private final ContainerRepair repairContainer;
    private final InventoryPlayer playerInventory;

    private GuiTextField itemNameField;

    public GuiRobitRepair(InventoryPlayer inventory, EntityRobit entity) {
        super(entity, new ContainerRobitRepair(inventory, entity));
        playerInventory = inventory;
        repairContainer = (ContainerRobitRepair) inventorySlots;
        addGuiElement(new GuiSlot(GuiSlot.SlotType.NORMAL, this, getGuiLocation(), 26, 46));
        addGuiElement(new GuiSlot(GuiSlot.SlotType.NORMAL, this, getGuiLocation(), 75, 46));
        addGuiElement(new GuiSlot(GuiSlot.SlotType.NORMAL, this, getGuiLocation(), 133, 46));
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        itemNameField = new GuiTextField(0, fontRenderer, guiLeft + 62, guiTop + 24, 103, 12);
        itemNameField.setTextColor(-1);
        itemNameField.setDisabledTextColour(-1);
        itemNameField.setEnableBackgroundDrawing(false);
        itemNameField.setMaxStringLength(30);
        inventorySlots.removeListener(this);
        inventorySlots.addListener(this);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
        inventorySlots.removeListener(this);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        GlStateManager.disableLighting();
        fontRenderer.drawString(LangUtils.localize("gui.robit.repair"), 60, 6, 0x404040);

        if (repairContainer.maximumCost > 0) {
            int k = 8453920;
            boolean flag = true;
            String s = I18n.format("container.repair.cost", repairContainer.maximumCost);

            if (repairContainer.maximumCost >= 40 && !mc.player.capabilities.isCreativeMode) {
                s = LangUtils.localize("container.repair.expensive");
                k = 16736352;
            } else if (!repairContainer.getSlot(2).getHasStack()) {
                flag = false;
            } else if (!repairContainer.getSlot(2).canTakeStack(playerInventory.player)) {
                k = 16736352;
            }

            if (flag) {
                int l = -16777216 | (k & 16579836) >> 2 | k & -16777216;
                int i1 = xSize - 25 - 8 - fontRenderer.getStringWidth(s);
                byte b0 = 67;

                if (fontRenderer.getUnicodeFlag()) {
                    drawRect(i1 - 3, b0 - 2, xSize - 25 - 7, b0 + 10, 0xFF000000);
                    drawRect(i1 - 2, b0 - 1, xSize - 25 - 8, b0 + 9, 0xFF3B3B3B);
                } else {
                    fontRenderer.drawString(s, i1, b0 + 1, l);
                    fontRenderer.drawString(s, i1 + 1, b0, l);
                    fontRenderer.drawString(s, i1 + 1, b0 + 1, l);
                }
                fontRenderer.drawString(s, i1, b0, k);
            }
        }
        GlStateManager.enableLighting();
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);
    }

    @Override
    protected void keyTyped(char c, int i) throws IOException {
        if (itemNameField.textboxKeyTyped(c, i)) {
            repairContainer.updateItemName(itemNameField.getText());
            mc.player.connection.sendPacket(new CPacketCustomPayload("MC|ItemName", new PacketBuffer(Unpooled.buffer()).writeString(itemNameField.getText())));
        } else {
            super.keyTyped(c, i);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        super.mouseClicked(mouseX, mouseY, button);
        itemNameField.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean shouldOpenGui(int id) {
        return id != 4;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        super.drawScreen(mouseX, mouseY, partialTick);
        GlStateManager.disableLighting();
        itemNameField.drawTextBox();
        GlStateManager.enableLighting();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(int xAxis, int yAxis) {
        super.drawGuiContainerBackgroundLayer(xAxis, yAxis);
        mc.getTextureManager().bindTexture(MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "Robit_Icon.png"));
        drawTexturedModalRect(guiLeft + 53, guiTop + 49, 0, 32, 13, 13);
        drawTexturedModalRect(guiLeft + 102, guiTop + 48, 0, 0, 22, 15);
        drawTexturedModalRect(guiLeft + 17, guiTop + 7, 0, 46, 30, 30);
        drawTexturedModalRect(guiLeft + 59, guiTop + 20, 0, 77 + (repairContainer.getSlot(0).getHasStack() ? 0 : 16), 110, 16);
        if ((repairContainer.getSlot(0).getHasStack() || repairContainer.getSlot(1).getHasStack()) && !repairContainer.getSlot(2).getHasStack()) {
            drawTexturedModalRect(guiLeft + 102, guiTop + 48, 0, 16, 22, 15);
        }
    }

    @Override
    public void sendAllContents(@Nonnull Container container, @Nonnull NonNullList<ItemStack> list) {
        sendSlotContents(container, 0, container.getSlot(0).getStack());
    }

    @Override
    public void sendSlotContents(@Nonnull Container container, int slotID, @Nonnull ItemStack itemstack) {
        if (slotID == 0) {
            itemNameField.setText(itemstack.isEmpty() ? "" : itemstack.getDisplayName());
            itemNameField.setEnabled(!itemstack.isEmpty());
            if (!itemstack.isEmpty()) {
                repairContainer.updateItemName(itemNameField.getText());
                mc.player.connection.sendPacket(new CPacketCustomPayload("MC|ItemName", new PacketBuffer(Unpooled.buffer()).writeString(itemNameField.getText())));
            }
        }
    }

    @Override
    public void sendWindowProperty(@Nonnull Container containerIn, int varToUpdate, int newValue) {
    }

    @Override
    public void sendAllWindowProperties(@Nonnull Container containerIn, @Nonnull IInventory inventory) {
    }
}
