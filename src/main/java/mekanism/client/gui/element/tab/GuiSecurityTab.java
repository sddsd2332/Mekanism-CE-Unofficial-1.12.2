package mekanism.client.gui.element.tab;

import mekanism.api.Coord4D;
import mekanism.client.MekanismClient;
import mekanism.client.SpecialColors;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInsetElement;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.entity.EntityRobit;
import mekanism.common.network.PacketSecurityMode.SecurityModeMessage;
import mekanism.common.security.ISecurityItem;
import mekanism.common.security.ISecurityTile;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.security.SecurityData;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.SecurityUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Arrays;
import java.util.UUID;

public class GuiSecurityTab<TILE extends net.minecraft.tileentity.TileEntity & ISecurityTile> extends GuiInsetElement<Object> {

    private static final ResourceLocation PUBLIC = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "public.png");
    private static final ResourceLocation PRIVATE = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "private.png");
    private static final ResourceLocation PROTECTED = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "protected.png");
    private final EnumHand currentHand;
    private final boolean isEntity;

    public GuiSecurityTab(IGuiWrapper gui, TILE tile) {
        this(gui, tile, 34);
    }

    public GuiSecurityTab(IGuiWrapper gui, TILE tile, int y) {
        super(PUBLIC, gui, tile, gui.getWidth(), y, 26, 18, false);
        currentHand = null;
        isEntity = false;
    }

    public GuiSecurityTab(IGuiWrapper gui, EntityRobit robit, int y) {
        super(PUBLIC, gui, robit, gui.getWidth(), y, 26, 18, false);
        currentHand = null;
        isEntity = true;
    }

    public GuiSecurityTab(IGuiWrapper gui, EnumHand hand) {
        super(PUBLIC, gui, null, gui.getWidth(), 34, 26, 18, false);
        currentHand = hand;
        isEntity = false;
    }

    @Override
    protected void colorTab() {
        MekanismRenderer.color(SpecialColors.TAB_SECURITY.argb());
    }

    @Override
    protected ResourceLocation getOverlay() {
        SecurityMode mode = getDisplayedMode();
        return switch (mode) {
            case PUBLIC -> super.getOverlay();
            case PRIVATE -> PRIVATE;
            case TRUSTED -> PROTECTED;
        };
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        if (isItem() && !isSecurityItem()) {
            return;
        }
        String securityText = isItem() ? SecurityUtils.getSecurityDisplay(getItem(), Side.CLIENT) : isEntity() ? SecurityUtils.getSecurityDisplay(getEntity(), Side.CLIENT) : SecurityUtils.getSecurityDisplay(getTile(), Side.CLIENT);
        String ownerText = SecurityUtils.getOwnerDisplay(minecraft.player, getOwnerName());
        if (isItem() ? SecurityUtils.isOverridden(getItem(), Side.CLIENT) : isEntity() ? SecurityUtils.isOverridden(getEntity(), Side.CLIENT) : SecurityUtils.isOverridden(getTile(), Side.CLIENT)) {
            displayTooltips(Arrays.asList(securityText, ownerText, mekanism.api.EnumColor.RED + "(" + mekanism.common.util.LangUtils.localize("gui.overridden") + ")"), mouseX, mouseY);
        } else {
            displayTooltips(Arrays.asList(securityText, ownerText), mouseX, mouseY);
        }
    }

    @Override
    public boolean isValidClickButton(int button) {
        return button == 0 || button == 1;
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        sendModeChange(button);
    }

    private void sendModeChange(int button) {
        if (!MekanismConfig.current().general.allowProtection.val()) {
            return;
        }
        UUID owner = getOwner();
        if (owner == null || !owner.equals(minecraft.player.getUniqueID())) {
            return;
        }
        SecurityMode next = button == 1 ? getSecurityMode().getPrevious() : getSecurityMode().getNext();
        Mekanism.packetHandler.sendToServer(isItem() ? new SecurityModeMessage(currentHand, next) : isEntity() ? new SecurityModeMessage(getEntity(), next) : new SecurityModeMessage(Coord4D.get(getTile()), next));
    }

    private SecurityMode getDisplayedMode() {
        if (!MekanismConfig.current().general.allowProtection.val()) {
            return SecurityMode.PUBLIC;
        }
        UUID owner = getOwner();
        if (owner != null) {
            SecurityData data = MekanismClient.clientSecurityMap.get(owner);
            if (data != null && data.override) {
                return data.mode;
            }
        }
        return getSecurityMode();
    }

    private boolean isItem() {
        return currentHand != null;
    }

    private boolean isEntity() {
        return isEntity;
    }

    private ItemStack getItem() {
        return minecraft.player.getHeldItem(currentHand);
    }

    private boolean isSecurityItem() {
        ItemStack stack = getItem();
        return !stack.isEmpty() && stack.getItem() instanceof ISecurityItem;
    }

    @SuppressWarnings("unchecked")
    private TILE getTile() {
        return (TILE) dataSource;
    }

    private EntityRobit getEntity() {
        return (EntityRobit) dataSource;
    }

    private SecurityMode getSecurityMode() {
        if (isItem()) {
            ItemStack stack = getItem();
            return stack.isEmpty() || !(stack.getItem() instanceof ISecurityItem security) ? SecurityMode.PUBLIC : security.getSecurity(stack);
        }
        if (isEntity()) {
            return getEntity().getSecurityMode();
        }
        return getTile().getSecurity().getMode();
    }

    private UUID getOwner() {
        if (isItem()) {
            ItemStack stack = getItem();
            return stack.isEmpty() || !(stack.getItem() instanceof ISecurityItem security) ? null : security.getOwnerUUID(stack);
        }
        if (isEntity()) {
            return getEntity().getOwnerUUID();
        }
        TileComponentSecurity security = getTile().getSecurity();
        return security.getOwnerUUID();
    }

    private String getOwnerName() {
        if (isItem()) {
            ItemStack stack = getItem();
            if (stack.isEmpty() || !(stack.getItem() instanceof ISecurityItem security)) {
                return null;
            }
            return MekanismClient.clientUUIDMap.get(security.getOwnerUUID(stack));
        }
        if (isEntity()) {
            return getEntity().getOwnerName();
        }
        return getTile().getSecurity().getClientOwner();
    }
}
