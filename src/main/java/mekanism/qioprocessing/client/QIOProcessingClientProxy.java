package mekanism.qioprocessing.client;

import mekanism.api.EnumColor;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.MekanismTileGuiExtensionRegistry;
import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.common.item.interfaces.IColoredItem;
import mekanism.qioprocessing.common.QIOProcessingCommonProxy;
import mekanism.qioprocessing.common.machine.QIOAutomationContainerState;
import mekanism.qioprocessing.client.gui.GuiQIOAutomationFrequencyTab;
import mekanism.qioprocessing.client.gui.GuiQIOAutomationRecoveryTab;
import mekanism.qioprocessing.client.gui.GuiQIOAutomationRecipeConfigTab;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.network.PacketQIOAutomationRecovery;
import mekanism.qioprocessing.common.network.QIOProcessingPacketHandler;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeConfigType;
import mekanism.qioprocessing.common.registries.QIOProcessingBlocks;
import mekanism.qioprocessing.common.registries.QIOProcessingItems;
import mekanism.qioprocessing.common.item.ItemPortableQIOProcessingTerminal;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import mekanism.qioprocessing.common.inventory.container.ContainerPortableQIOSmartProcessingTerminal;
import mekanism.qioprocessing.common.inventory.container.ContainerPortableQIOProcessingTerminal;
import mekanism.qioprocessing.common.inventory.container.ContainerQIOSmartProcessingTerminal;
import mekanism.qioprocessing.common.inventory.container.ContainerQIOProcessingTerminal;
import mekanism.qioprocessing.common.tile.QIOProcessingTerminal;
import mekanism.common.tile.qio.TileEntityQIOComponent;
import mekanism.qioprocessing.common.tile.TileEntityQIOSmartProcessingTerminal;
import mekanism.qioprocessing.client.gui.GuiPortableQIOSmartProcessingTerminal;
import mekanism.qioprocessing.client.gui.GuiPortableQIOProcessingTerminal;
import mekanism.qioprocessing.client.gui.GuiQIOSmartProcessingTerminal;
import mekanism.qioprocessing.client.gui.GuiQIOProcessingTerminal;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import mekanism.qioprocessing.client.gui.GuiQIOCraftingProcessor;
import mekanism.qioprocessing.common.tile.QIOCraftingProcessor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;

@SideOnly(Side.CLIENT)
/**
 * QIO 处理模块中的 QIOProcessingClientProxy 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public class QIOProcessingClientProxy extends QIOProcessingCommonProxy {

    private boolean clientHandlersRegistered;

    @Override
    public synchronized void registerClientHandlers() {
        if (clientHandlersRegistered) {
            return;
        }
        MekanismTileGuiExtensionRegistry.register(QIOAutomationContainerState.EXTENSION_ID,
              (gui, tile) -> {
                  if (!(gui.getContainer() instanceof MekanismTileContainer<?> container)) {
                      return null;
                  }
                  QIOAutomationContainerState state = QIOAutomationContainerState.get(container);
                  if (state == null) {
                      return null;
                  }
                  final GuiQIOAutomationFrequencyTab[] holder =
                        new GuiQIOAutomationFrequencyTab[1];
                  holder[0] = new GuiQIOAutomationFrequencyTab(gui, tile, container, state,
                        () -> holder[0]);
                  return holder[0];
              });
        MekanismTileGuiExtensionRegistry.register(
              new net.minecraft.util.ResourceLocation(
                    mekanism.qioprocessing.common.MekanismQIOProcessing.MODID,
                    "automation_recovery_tab"),
              (gui, tile) -> {
                  if (!(gui.getContainer() instanceof MekanismTileContainer<?> container)) {
                      return null;
                  }
                  QIOAutomationContainerState state = QIOAutomationContainerState.get(container);
                  if (state == null) {
                      return null;
                  }
                  // 普通机器的警告 Tab 使用左侧 x=-26、y=109；恢复 Tab 放在其左边。
                  return new GuiQIOAutomationRecoveryTab(gui, -52, 109,
                        () -> state.isRecoveryQuarantined() ||
                              state.getState() == QIOAutomationHost.State.DATA_ERROR,
                        state::getMode,
                        state::getRecoveryDiagnostic,
                        () -> QIOProcessingPacketHandler.INSTANCE.sendToServer(
                              PacketQIOAutomationRecovery.Message.create(
                                    container.windowId, tile)), true);
              });
        registerRecipeConfigTab(QIOAutomationRecipeConfigType.SCHEDULED, "automation_crafting_config_tab");
        registerRecipeConfigTab(QIOAutomationRecipeConfigType.PASSIVE, "automation_processing_config_tab");
        registerQIOColors();
        MinecraftForge.EVENT_BUS.register(QIODeviceLocatorRenderer.INSTANCE);
        clientHandlersRegistered = true;
    }

    private static void registerRecipeConfigTab(QIOAutomationRecipeConfigType type, String id) {
        MekanismTileGuiExtensionRegistry.register(
              new net.minecraft.util.ResourceLocation(mekanism.qioprocessing.common.MekanismQIOProcessing.MODID, id),
              (gui, tile) -> {
                  if (!(gui.getContainer() instanceof MekanismTileContainer<?> container)) {
                      return null;
                  }
                  QIOAutomationContainerState state = QIOAutomationContainerState.get(container);
                  if (state == null) {
                      return null;
                  }
                  final GuiQIOAutomationRecipeConfigTab[] holder = new GuiQIOAutomationRecipeConfigTab[1];
                  holder[0] = new GuiQIOAutomationRecipeConfigTab(gui, tile, container, state, type,
                        () -> holder[0]);
                  return holder[0];
              });
    }

    @Override
    public void registerBlockRenders() {
        registerBlock(QIOProcessingBlocks.QIOCraftingProcessor);
        registerBlock(QIOProcessingBlocks.BasicQIOCraftingProcessor);
        registerBlock(QIOProcessingBlocks.AdvancedQIOCraftingProcessor);
        registerBlock(QIOProcessingBlocks.EliteQIOCraftingProcessor);
        registerBlock(QIOProcessingBlocks.UltimateQIOCraftingProcessor);
        registerBlock(QIOProcessingBlocks.QIOManagementTerminal);
        registerBlock(QIOProcessingBlocks.QIOSmartProcessingTerminal);
        registerBlock(QIOProcessingBlocks.QIOMaintenanceTerminal);
        registerBlock(QIOProcessingBlocks.QIOCraftingMonitor);
    }

    @Override
    public void registerItemRenders() {
        registerItem(QIOProcessingItems.QIOStackingUpgrade);
        registerItem(QIOProcessingItems.QIOAutoCraftingUpgrade);
        registerItem(QIOProcessingItems.QIOAutoProcessingUpgrade);
        registerItem(QIOProcessingItems.QIOAutoOutputUpgrade);
        registerItem(QIOProcessingItems.PortableQIOManagementTerminal);
        registerItem(QIOProcessingItems.PortableQIOSmartProcessingTerminal);
        registerItem(QIOProcessingItems.PortableQIOMaintenanceTerminal);
        registerItem(QIOProcessingItems.PortableQIOCraftingMonitor);
    }

    @Override
    public Object getClientGui(int id, EntityPlayer player, World world, BlockPos pos) {
        net.minecraft.tileentity.TileEntity tile = world.getTileEntity(pos);
        if (id == GUI_CRAFTING_PROCESSOR && tile instanceof QIOCraftingProcessor processor) {
            return new GuiQIOCraftingProcessor(player.inventory, processor);
        }
        if (id == GUI_TERMINAL && tile instanceof QIOProcessingTerminal terminal) {
            return terminal instanceof TileEntityQIOSmartProcessingTerminal smart ?
                  new GuiQIOSmartProcessingTerminal(player.inventory, smart) :
                  new GuiQIOProcessingTerminal(player.inventory, terminal);
        }
        if (id == GUI_PORTABLE_TERMINAL) {
            int itemSlot = pos.getX();
            int handOrdinal = pos.getY();
            if (handOrdinal < 0 || handOrdinal >= net.minecraft.util.EnumHand.values().length) {
                return null;
            }
            net.minecraft.util.EnumHand hand = net.minecraft.util.EnumHand.values()[handOrdinal];
            if (!mekanism.common.inventory.container.item.ItemStackSlotAccess.isValidSlot(hand,
                  itemSlot)) {
                return null;
            }
            net.minecraft.item.ItemStack stack = mekanism.common.inventory.container.item.ItemStackSlotAccess.getStack(
                  player.inventory, hand, itemSlot);
            if (!(stack.getItem() instanceof ItemPortableQIOProcessingTerminal item)) {
                return null;
            }
            return item.getTerminalType() == QIOProcessingTerminalType.SMART_PROCESSING ?
                  new GuiPortableQIOSmartProcessingTerminal(player.inventory, hand,
                        itemSlot, stack) :
                  new GuiPortableQIOProcessingTerminal(player.inventory, hand,
                        itemSlot, stack);
        }
        return null;
    }

    private static void registerBlock(net.minecraft.block.Block block) {
        registerItem(Item.getItemFromBlock(block));
    }

    private static void registerQIOColors() {
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.getBlockColors().registerBlockColorHandler((state, world, pos,
              tintIndex) -> {
            if (tintIndex != 1 || world == null || pos == null) return -1;
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileEntityQIOComponent component) {
                EnumColor color = component.getQIOColor();
                if (color != null) return rgb(color);
            }
            return -1;
        }, QIOProcessingBlocks.QIOManagementTerminal,
              QIOProcessingBlocks.QIOSmartProcessingTerminal,
              QIOProcessingBlocks.QIOMaintenanceTerminal,
              QIOProcessingBlocks.QIOCraftingMonitor,
              QIOProcessingBlocks.QIOCraftingProcessor,
              QIOProcessingBlocks.BasicQIOCraftingProcessor,
              QIOProcessingBlocks.AdvancedQIOCraftingProcessor,
              QIOProcessingBlocks.EliteQIOCraftingProcessor,
              QIOProcessingBlocks.UltimateQIOCraftingProcessor);
        minecraft.getItemColors().registerItemColorHandler((stack, tintIndex) -> {
            if (tintIndex == 1 && stack.getItem() instanceof IColoredItem colored) {
                EnumColor color = colored.getColor(stack);
                return color == null ? IColoredItem.DEFAULT_TINT : rgb(color);
            }
            return -1;
        }, Item.getItemFromBlock(QIOProcessingBlocks.QIOManagementTerminal),
              Item.getItemFromBlock(QIOProcessingBlocks.QIOSmartProcessingTerminal),
              Item.getItemFromBlock(QIOProcessingBlocks.QIOMaintenanceTerminal),
              Item.getItemFromBlock(QIOProcessingBlocks.QIOCraftingMonitor),
              Item.getItemFromBlock(QIOProcessingBlocks.QIOCraftingProcessor),
              Item.getItemFromBlock(QIOProcessingBlocks.BasicQIOCraftingProcessor),
              Item.getItemFromBlock(QIOProcessingBlocks.AdvancedQIOCraftingProcessor),
              Item.getItemFromBlock(QIOProcessingBlocks.EliteQIOCraftingProcessor),
              Item.getItemFromBlock(QIOProcessingBlocks.UltimateQIOCraftingProcessor),
              QIOProcessingItems.PortableQIOManagementTerminal,
              QIOProcessingItems.PortableQIOSmartProcessingTerminal,
              QIOProcessingItems.PortableQIOMaintenanceTerminal,
              QIOProcessingItems.PortableQIOCraftingMonitor);
    }

    private static int rgb(EnumColor color) {
        return color.rgbCode[0] << 16 | color.rgbCode[1] << 8 | color.rgbCode[2];
    }

    private static void registerItem(Item item) {
        ModelLoader.setCustomModelResourceLocation(item, 0,
              new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }
}
