package mekanism.qioprocessing.common;

import mekanism.common.Mekanism;
import mekanism.common.base.IGuiProvider;
import mekanism.common.config.MekanismConfig;
import mekanism.qioprocessing.common.tile.TileEntityAdvancedQIOCraftingProcessor;
import mekanism.qioprocessing.common.tile.TileEntityBasicQIOCraftingProcessor;
import mekanism.qioprocessing.common.tile.TileEntityEliteQIOCraftingProcessor;
import mekanism.qioprocessing.common.tile.TileEntityQIOCraftingProcessor;
import mekanism.qioprocessing.common.tile.TileEntityUltimateQIOCraftingProcessor;
import mekanism.qioprocessing.common.tile.TileEntityQIOManagementTerminal;
import mekanism.qioprocessing.common.tile.TileEntityQIOSmartProcessingTerminal;
import mekanism.qioprocessing.common.tile.TileEntityQIOMaintenanceTerminal;
import mekanism.qioprocessing.common.tile.TileEntityQIOCraftingMonitor;
import mekanism.qioprocessing.common.inventory.container.ContainerQIOCraftingProcessor;
import mekanism.qioprocessing.common.inventory.container.ContainerPortableQIOProcessingTerminal;
import mekanism.qioprocessing.common.inventory.container.ContainerPortableQIOSmartProcessingTerminal;
import mekanism.qioprocessing.common.inventory.container.ContainerQIOProcessingTerminal;
import mekanism.qioprocessing.common.inventory.container.ContainerQIOSmartProcessingTerminal;
import mekanism.qioprocessing.common.item.ItemPortableQIOProcessingTerminal;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import mekanism.qioprocessing.common.tile.QIOProcessingTerminal;
import mekanism.qioprocessing.common.tile.TileEntityQIOSmartProcessingTerminal;
import mekanism.common.inventory.container.item.ItemStackSlotAccess;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.common.config.Configuration;

/**
 * QIO 处理模块中的 QIOProcessingCommonProxy 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public class QIOProcessingCommonProxy implements IGuiProvider {

    public static final int GUI_CRAFTING_PROCESSOR = 0;
    public static final int GUI_PORTABLE_TERMINAL = 1;
    public static final int GUI_TERMINAL = 2;

    public void preInit() {
        loadConfiguration();
    }

    public void loadConfiguration() {
        Configuration configuration = Mekanism.getQIOProcessingConfiguration();
        MekanismConfig.local().qioProcessing.load(configuration);
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public void registerClientHandlers() {
    }

    public void registerTileEntities() {
        registerTileEntity(TileEntityQIOCraftingProcessor.class, "qio_crafting_processor");
        registerTileEntity(TileEntityBasicQIOCraftingProcessor.class, "basic_qio_crafting_processor");
        registerTileEntity(TileEntityAdvancedQIOCraftingProcessor.class, "advanced_qio_crafting_processor");
        registerTileEntity(TileEntityEliteQIOCraftingProcessor.class, "elite_qio_crafting_processor");
        registerTileEntity(TileEntityUltimateQIOCraftingProcessor.class, "ultimate_qio_crafting_processor");
        registerTileEntity(TileEntityQIOManagementTerminal.class, "qio_management_terminal");
        registerTileEntity(TileEntityQIOSmartProcessingTerminal.class, "qio_smart_processing_terminal");
        registerTileEntity(TileEntityQIOMaintenanceTerminal.class, "qio_maintenance_terminal");
        registerTileEntity(TileEntityQIOCraftingMonitor.class, "qio_crafting_monitor");
    }

    public void registerBlockRenders() {
    }

    public void registerItemRenders() {
    }

    @Override
    public Container getServerGui(int id, EntityPlayer player, World world, BlockPos pos) {
        if (id == GUI_PORTABLE_TERMINAL) {
            int itemSlot = pos.getX();
            int handOrdinal = pos.getY();
            if (handOrdinal < 0 || handOrdinal >= EnumHand.values().length) {
                return null;
            }
            EnumHand hand = EnumHand.values()[handOrdinal];
            if (!ItemStackSlotAccess.isValidSlot(hand, itemSlot)) {
                return null;
            }
            ItemStack stack = ItemStackSlotAccess.getStack(player.inventory, hand, itemSlot);
            if (stack.getItem() instanceof ItemPortableQIOProcessingTerminal item) {
                return item.getTerminalType() == QIOProcessingTerminalType.SMART_PROCESSING ?
                      new ContainerPortableQIOSmartProcessingTerminal(player.inventory,
                            hand, itemSlot, stack) :
                      new ContainerPortableQIOProcessingTerminal(player.inventory, hand,
                            itemSlot, stack);
            }
            return null;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (id == GUI_CRAFTING_PROCESSOR && tile instanceof mekanism.qioprocessing.common.tile.QIOCraftingProcessor processor &&
              mekanism.common.CommonProxy.canOpenServerGui(player, processor)) {
            return new ContainerQIOCraftingProcessor(player.inventory, processor);
        }
        if (id == GUI_TERMINAL && tile instanceof QIOProcessingTerminal terminal &&
            mekanism.common.CommonProxy.canOpenServerGui(player, terminal)) {
            return terminal instanceof TileEntityQIOSmartProcessingTerminal smart ?
                  new ContainerQIOSmartProcessingTerminal(player.inventory, smart) :
                  new ContainerQIOProcessingTerminal(player.inventory, terminal);
        }
        return null;
    }

    @Override
    public boolean isValidServerGui(int id, TileEntity tile) {
        return id == GUI_CRAFTING_PROCESSOR && tile instanceof TileEntityQIOCraftingProcessor ||
              id == GUI_TERMINAL && tile instanceof QIOProcessingTerminal;
    }

    @Override
    public Object getClientGui(int id, EntityPlayer player, World world, BlockPos pos) {
        return null;
    }

    private static void registerTileEntity(Class<? extends TileEntity> tileClass, String name) {
        GameRegistry.registerTileEntity(tileClass, new ResourceLocation(MekanismQIOProcessing.MODID, name));
    }
}
