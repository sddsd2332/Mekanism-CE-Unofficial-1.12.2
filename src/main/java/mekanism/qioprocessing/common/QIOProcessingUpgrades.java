package mekanism.qioprocessing.common;

import mekanism.api.EnumColor;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.common.Upgrade;
import mekanism.common.config.MekanismConfig;
import mekanism.qioprocessing.common.content.QIOAutomationUpgradeSupport;
import mekanism.qioprocessing.common.registries.QIOProcessingItems;
import net.minecraft.item.ItemStack;

/**
 * QIO 处理模块中的 QIOProcessingUpgrades 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOProcessingUpgrades {

    private static final String AE_MODID = "mekceuaeupgrade";
    public static final Upgrade QIO_STACKING = Upgrade.builder(MekanismQIOProcessing.MODID,
                "qio_stacking_upgrade")
          .maxInstalled(8)
          .maxItemStackSize(8)
          .color(EnumColor.ORANGE)
          .stack(count -> new ItemStack(QIOProcessingItems.QIOStackingUpgrade, count))
          .register();
    public static final Upgrade QIO_AUTO_CRAFTING = create("qio_auto_crafting_upgrade", EnumColor.BRIGHT_GREEN,
          QIOAutomationMode.SCHEDULED, () -> QIOProcessingItems.QIOAutoCraftingUpgrade);
    public static final Upgrade QIO_AUTO_PROCESSING = create("qio_auto_processing_upgrade", EnumColor.YELLOW,
          QIOAutomationMode.PASSIVE, () -> QIOProcessingItems.QIOAutoProcessingUpgrade);
    public static final Upgrade QIO_AUTO_OUTPUT = create("qio_auto_output_upgrade", EnumColor.AQUA,
          QIOAutomationMode.OUTPUT_ONLY, () -> QIOProcessingItems.QIOAutoOutputUpgrade);

    private QIOProcessingUpgrades() {
    }

    private static Upgrade create(String name, EnumColor color, QIOAutomationMode mode,
          java.util.function.Supplier<net.minecraft.item.Item> item) {
        return Upgrade.builder(MekanismQIOProcessing.MODID, name)
              .maxInstalled(1)
              .maxItemStackSize(1)
              .color(color)
              .stack(count -> new ItemStack(item.get(), count))
              .conflictsWith(MekanismQIOProcessing.MODID, "qio_auto_crafting_upgrade")
              .conflictsWith(MekanismQIOProcessing.MODID, "qio_auto_processing_upgrade")
              .conflictsWith(MekanismQIOProcessing.MODID, "qio_auto_output_upgrade")
              .conflictsWith(AE_MODID, "ae_crafting")
              .conflictsWith(AE_MODID, "ae_output")
              .conflictsWith(AE_MODID, "ae_auto_processing")
              .conflictsWith(AE_MODID, "ae_wireless_crafting")
              .conflictsWith(AE_MODID, "ae_wireless_output")
              .conflictsWith(AE_MODID, "ae_wireless_auto_processing")
              .onChanged((upgrade, tile, previous, amount) -> QIOAutomationUpgradeSupport.onUpgradeChanged(upgrade, tile,
                    previous, amount, mode, QIO_AUTO_CRAFTING, QIO_AUTO_PROCESSING, QIO_AUTO_OUTPUT))
              .register();
    }
}
