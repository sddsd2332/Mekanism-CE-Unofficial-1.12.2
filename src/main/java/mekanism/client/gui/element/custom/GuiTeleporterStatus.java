package mekanism.client.gui.element.custom;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiTexturedElement;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public class GuiTeleporterStatus extends GuiTexturedElement {

    private static final ResourceLocation NEEDS_ENERGY = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "teleporter_needs_energy.png");
    private static final ResourceLocation NO_FRAME = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "teleporter_no_frame.png");
    private static final ResourceLocation NO_FREQUENCY = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "teleporter_no_frequency.png");
    private static final ResourceLocation NO_DESTINATION = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "teleporter_no_link.png");
    private static final ResourceLocation READY = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "teleporter_ready.png");
    private static final Map<Byte, TextComponentString> CACHED_TOOLTIPS = new HashMap<>();

    private final BooleanSupplier hasFrequency;
    private final IntSupplier statusSupplier;

    public GuiTeleporterStatus(IGuiWrapper gui, BooleanSupplier hasFrequency, IntSupplier statusSupplier) {
        super(NO_FREQUENCY, gui, 6, 6, 18, 18);
        this.hasFrequency = hasFrequency;
        this.statusSupplier = statusSupplier;
        setButtonBackground(ButtonBackground.DEFAULT);
    }

    @Override
    protected int getYImage(boolean hoveredOrFocused) {
        return 1;
    }

    @Override
    protected ResourceLocation getResource() {
        if (hasFrequency.getAsBoolean()) {
            return switch (statusSupplier.getAsInt()) {
                case 1 -> READY;
                case 2 -> NO_FRAME;
                case 4 -> NEEDS_ENERGY;
                default -> NO_DESTINATION;
            };
        }
        return NO_FREQUENCY;
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        MekanismRenderer.bindTexture(getResource());
        GuiUtils.blit(relativeX, relativeY, 0, 0, 18, 18, 18, 18);
        MekanismRenderer.resetColor();
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        byte status = hasFrequency.getAsBoolean() ? (byte) statusSupplier.getAsInt() : 0;
        displayTooltip(CACHED_TOOLTIPS.computeIfAbsent(status, GuiTeleporterStatus::getStatusDisplay), mouseX, mouseY);
    }

    public int getRelativeRight() {
        return relativeX + width;
    }

    private static TextComponentString getStatusDisplay(byte status) {
        return new TextComponentString(switch (status) {
            case 1 -> mekanism.api.EnumColor.DARK_GREEN + LangUtils.localize("gui.teleporter.ready");
            case 2 -> mekanism.api.EnumColor.DARK_RED + LangUtils.localize("gui.teleporter.noFrame");
            case 4 -> mekanism.api.EnumColor.DARK_RED + LangUtils.localize("gui.teleporter.needsEnergy");
            case 0 -> mekanism.api.EnumColor.DARK_RED + LangUtils.localize("gui.teleporter.noFreq");
            default -> mekanism.api.EnumColor.DARK_RED + LangUtils.localize("gui.teleporter.noLink");
        });
    }
}
