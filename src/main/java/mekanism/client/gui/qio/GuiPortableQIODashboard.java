package mekanism.client.gui.qio;

import mekanism.common.inventory.container.PortableQIODashboardContainer;
import mekanism.client.gui.element.tab.GuiQIOFrequencyTab;
import mekanism.common.content.qio.QIOFrequency;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Portable Dashboard viewer screen. */
@SideOnly(Side.CLIENT)
public class GuiPortableQIODashboard extends GuiQIOViewerScreen<PortableQIODashboardContainer> {

    private final PortableQIODashboardContainer container;

    public GuiPortableQIODashboard(InventoryPlayer inventory, PortableQIODashboardContainer container) {
        super(container);
        this.container = container;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        frequencyTab = addButton(new GuiQIOFrequencyTab.Item(this, container, () -> frequencyTab));
    }

    @Override
    protected String getViewerTitle() {
        return container.getStack().getDisplayName();
    }

    @Override
    protected QIOFrequency getQIOFrequency() {
        return container.getFrequency();
    }

    @Override
    protected GuiQIOViewerScreen<PortableQIODashboardContainer> recreate(PortableQIODashboardContainer container) {
        return new GuiPortableQIODashboard(null, container);
    }
}
