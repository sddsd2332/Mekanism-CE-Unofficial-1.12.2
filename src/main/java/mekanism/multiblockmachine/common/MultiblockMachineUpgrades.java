package mekanism.multiblockmachine.common;

import mekanism.api.EnumColor;
import mekanism.common.Upgrade;
import mekanism.common.config.MekanismConfig;
import mekanism.multiblockmachine.common.registries.MultiblockMachineItems;
import net.minecraft.item.ItemStack;

public final class MultiblockMachineUpgrades {

    public static final Upgrade THREAD = Upgrade.builder(MekanismMultiblockMachine.MODID, "thread")
            .maxInstalled(() -> MekanismConfig.current().multiblock.MAXThreadUpgrade.val())
            .maxItemStackSize(() -> MekanismConfig.current().multiblock.MAXThreadUpgradeSize.val())
            .color(EnumColor.ORANGE)
            .stack(count -> new ItemStack(MultiblockMachineItems.ThreadUpgrade, count))
            .register();

    private MultiblockMachineUpgrades() {
    }
}
