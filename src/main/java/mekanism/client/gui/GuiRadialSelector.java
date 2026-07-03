package mekanism.client.gui;

import com.github.bsideup.jabel.Desugar;
import mekanism.api.EnumColor;
import mekanism.api.radial.RadialData;
import mekanism.api.radial.mode.INestedRadialMode;
import mekanism.api.radial.mode.IRadialMode;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.config.MekanismConfig;
import mekanism.common.lib.radial.IGenericRadialModeItem;
import mekanism.common.network.PacketRadialModeChange.RadialModeChangeMessage;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StatUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

public class GuiRadialSelector extends GuiScreen {

    private static final ResourceLocation BACK_BUTTON = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_RADIAL, "back.png");
    private static final float DRAWS = 300;

    private static final float INNER = 40, OUTER = 100;
    private static final float MIDDLE_DISTANCE = (INNER + OUTER) / 2F;
    private static final float SELECT_RADIUS = 10, SELECT_RADIUS_WITH_PARENT = 20;

    private final Deque<RadialData<?>> parents = new ArrayDeque<>();
    private final Supplier<EntityPlayer> playerSupplier;
    private final EntityEquipmentSlot slot;

    @NotNull
    private RadialData<?> radialData;
    private IRadialMode selection = null;
    private boolean overBackButton = false;
    private boolean updateOnClose = true;

    public GuiRadialSelector(EntityEquipmentSlot slot, @NotNull RadialData<?> radialData, Supplier<EntityPlayer> playerSupplier) {
        this.slot = slot;
        this.radialData = radialData;
        this.playerSupplier = playerSupplier;
    }


    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        // center of screen
        ScaledResolution scaledResolution = new ScaledResolution(mc);
        float centerX = scaledResolution.getScaledWidth() / 2F;
        float centerY = scaledResolution.getScaledHeight() / 2F;
        render(mouseX, mouseY, centerX, centerY, radialData);
    }


    private <MODE extends IRadialMode> void render(int mouseX, int mouseY, float centerX, float centerY, RadialData<MODE> radialData) {
        // Calculate number of available modes to switch between
        List<MODE> modes = radialData.getModes();
        int activeModes = modes.size();
        if (activeModes == 0) {
            //If for some reason none are available try going up a level first, and if that fails close the screen
            RadialData<?> parent = parents.pollLast();
            if (parent == null) {
                mc.displayGuiScreen((GuiScreen) null);
            } else {
                this.radialData = parent;
            }
            return;
        }
        float angleSize = 360F / activeModes;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.translate(centerX, centerY, 0);
        GlStateManager.disableTexture2D();

        // draw base
        // Note: While there might be slightly better performance only drawing part of the Torus given
        // other bits may be drawn by hovering or current selection, it is not practical to do so due
        // to floating point precision causing some values to have gaps in the torus, and also the light
        // colors occasionally being harder to see without the added back layer torus
        GlStateManager.color(0.3F, 0.3F, 0.3F, 0.5F);
        drawTorus(0, 360);

        MODE current = getCurrent(radialData);
        if (current == null) {
            //See if the radial data has a default fallback to go to
            current = radialData.getDefaultMode(modes);
        }
        // Draw segments
        //Calculate the proper section to highlight as green based on the radial data in case indexing can be optimized or
        // some pieces are actually disabled
        int section = radialData.indexNullable(modes, current);
        if (current != null && section != -1) {
            // draw current selected if any is selected
            float startAngle = -90F + 360F * (-0.5F + section) / activeModes;
            EnumColor color = current.color();
            if (color == null) {
                GlStateManager.color(0.4F, 0.4F, 0.4F, 0.7F);
                drawTorus(startAngle, angleSize);
            } else {
                MekanismRenderer.color(color, 0.3F);
                drawTorus(startAngle, angleSize);
            }
        }

        // Draw current hovered selection and selection highlighter
        double xDiff = mouseX - centerX;
        double yDiff = mouseY - centerY;
        double distanceFromCenter = length(xDiff, yDiff);
        if (distanceFromCenter > (parents.isEmpty() ? SELECT_RADIUS : SELECT_RADIUS_WITH_PARENT)) {
            // draw mouse selection highlight
            float angle = (float) (RAD_TO_DEG * MathHelper.atan2(yDiff, xDiff));
            float modeSize = 180F / activeModes;
            GlStateManager.color(0.8F, 0.8F, 0.8F, 0.3F);
            drawTorus(angle - modeSize, angleSize);

            float selectionAngle = StatUtils.wrapDegrees(angle + modeSize + 90F);
            int selectionDrawnPos = (int) (selectionAngle * (activeModes / 360F));
            selection = modes.get(selectionDrawnPos);

            // draw hovered selection
            GlStateManager.color(0.6F, 0.6F, 0.6F, 0.7F);
            drawTorus(-90F + 360F * (-0.5F + selectionDrawnPos) / activeModes, angleSize);
        } else {
            selection = null;
        }

        MekanismRenderer.resetColor();
        GlStateManager.enableTexture2D();

        List<PositionedText> textToDraw = new ArrayList<>(parents.isEmpty() ? activeModes : activeModes + 1);
        //Draw back button if needed
        if (!parents.isEmpty()) {
            overBackButton = distanceFromCenter <= SELECT_RADIUS_WITH_PARENT;
            if (overBackButton) {
                GlStateManager.color(0.8F, 0.8F, 0.8F, 0.3F);
                drawTorus(0, 360, 0, SELECT_RADIUS_WITH_PARENT);
            } else {
                GlStateManager.color( 0.3F, 0.3F, 0.3F, 0.5F);
                drawTorus(0, 360, 0, SELECT_RADIUS_WITH_PARENT);
            }
            MekanismRenderer.resetColor();
            // draw icon
            mc.renderEngine.bindTexture(BACK_BUTTON);
            GuiUtils.blit(-12, -18, 24, 24, 0, 0, 18, 18, 18, 18);
            textToDraw.add(new PositionedText(0, 0, MekanismLang.BACK.translate()));
        } else {
            overBackButton = false;
        }

        // Icons
        int position = 0;
        for (MODE mode : modes) {
            float degrees = 270 + 360 * ((float) position++ / activeModes);
            float angle = DEG_TO_RAD * degrees;
            float x = MathHelper.cos(angle) * MIDDLE_DISTANCE;
            float y = MathHelper.sin(angle) * MIDDLE_DISTANCE;
            // draw icon
            mc.renderEngine.bindTexture(mode.icon());
            GuiUtils.blit(Math.round(x - 12), Math.round(y - 20), 24, 24, 0, 0, 18, 18, 18, 18);
            // queue label
            textToDraw.add(new PositionedText(x, y, mode.sliceName()));
        }

        // Labels (has to be separate from icons or the icons occasionally will get extra artifacts for some reason)
        boolean whiteRadialText = MekanismConfig.current().client.whiteRadialText.val();
        textToDraw.forEach(toDraw -> {
            GlStateManager.pushMatrix();
            GlStateManager.translate(toDraw.x, toDraw.y, 0);
            GlStateManager.scale(0.6F, 0.6F, 0.6F);
            ITextComponent text = toDraw.text;
            if (whiteRadialText) {
                text = text.setStyle(text.getStyle().setColor(TextFormatting.RESET));
            }
            GuiUtils.drawString(fontRenderer, text.getFormattedText(), -fontRenderer.getStringWidth(text.getFormattedText()) / 2F, 8, 0xCCFFFFFF, true);
            GlStateManager.popMatrix();
        });
        MekanismRenderer.resetColor();
        GlStateManager.popMatrix();
    }

    @Desugar
    record PositionedText(float x, float y, ITextComponent text) {
    }

    @Override
    public void onGuiClosed() {
        if (updateOnClose) {
            updateSelection(radialData);
        }
        super.onGuiClosed();
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        updateSelection(radialData);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void drawTorus(float startAngle, float sizeAngle) {
        drawTorus(startAngle, sizeAngle, INNER, OUTER);
    }


    private void drawTorus(float startAngle, float sizeAngle, float inner, float outer) {
        BufferBuilder vertexBuffer = Tessellator.getInstance().getBuffer();
        vertexBuffer.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION);
        float draws = DRAWS * (sizeAngle / 360F);
        for (int i = 0; i <= draws; i++) {
            float degrees = startAngle + (i / DRAWS) * 360;
            float angle = DEG_TO_RAD * degrees;
            float cos = MathHelper.cos(angle);
            float sin = MathHelper.sin(angle);
            vertexBuffer.pos(outer * cos, outer * sin, 0).endVertex();
            vertexBuffer.pos(inner * cos, inner * sin, 0).endVertex();
        }
        Tessellator.getInstance().draw();
    }


    @Nullable
    private <MODE extends IRadialMode> MODE getCurrent(RadialData<MODE> radialData) {
        EntityPlayer player = playerSupplier.get();
        if (player != null) {
            ItemStack stack = player.getItemStackFromSlot(slot);
            if (stack.getItem() instanceof IGenericRadialModeItem item) {
                return item.getMode(stack, radialData);
            }
        }
        return null;
    }

    private <MODE extends IRadialMode> void updateSelection(final RadialData<MODE> radialData) {
        //Only update it if we have a selection, and it is different from the current value
        if (selection != null && playerSupplier.get() != null) {
            if (selection instanceof INestedRadialMode nested && nested.hasNestedData()) {
                parents.push(radialData);
                //noinspection ConstantConditions: not null, validated by hasNestedData
                this.radialData = nested.nestedData();
                //Reset immediately rather than next render frame
                selection = null;
            } else if (!selection.equals(getCurrent(radialData))) {
                //Only bother syncing if we don't already have that mode selected
                List<ResourceLocation> path = new ArrayList<>(parents.size());
                RadialData<?> previousParent = null;
                for (RadialData<?> parent : parents) {
                    if (previousParent != null) {
                        path.add(parent.getIdentifier());
                    }
                    previousParent = parent;
                }
                if (previousParent != null) {
                    path.add(radialData.getIdentifier());
                }
                int networkRepresentation = radialData.tryGetNetworkRepresentation(selection);
                if (networkRepresentation != -1) {
                    //TODO: If we ever add a radial type where the network representation may be negative,
                    // re-evaluate how we do this type validation
                    Mekanism.packetHandler.sendToServer(new RadialModeChangeMessage(slot, path, networkRepresentation));
                }
            }
        } else if (overBackButton) {
            //Reset immediately rather than next render frame
            overBackButton = false;
            RadialData<?> parent = parents.pollLast();
            if (parent != null) {
                this.radialData = parent;
            }
        }
    }

    public boolean hasMatchingData(EntityEquipmentSlot slot, RadialData<?> data) {
        if (this.slot == slot) {
            RadialData<?> firstData = parents.peekFirst();
            //If there is an initial root parent compare it, otherwise we are currently at the root so compare to our current one
            return firstData == null ? radialData.equals(data) : firstData.equals(data);
        }
        return false;
    }


    @SuppressWarnings("ConstantConditions")//not null, validated by hasNestedData
    public void tryInheritCurrentPath(@Nullable GuiScreen screen) {
        if (screen instanceof GuiRadialSelector old) {
            //Try to calculate the expected sub radial if we are going from radial selector to radial selector
            // as if they both have the same path odds are only some minor option changed, so we might as well
            // go to the same sub one
            RadialData<?> previousParent = null;
            for (RadialData<?> parent : old.parents) {
                if (previousParent != null && radialData.getIdentifier().equals(previousParent.getIdentifier())) {
                    INestedRadialMode nestedMode = radialData.fromIdentifier(parent.getIdentifier());
                    if (nestedMode == null || !nestedMode.hasNestedData()) {
                        //Can't go any deeper end
                        return;
                    }
                    //Update the current radial depth
                    parents.push(radialData);
                    radialData = nestedMode.nestedData();
                }
                previousParent = parent;
            }
            if (previousParent != null && radialData.getIdentifier().equals(previousParent.getIdentifier())) {
                INestedRadialMode nestedMode = radialData.fromIdentifier(old.radialData.getIdentifier());
                if (nestedMode != null && nestedMode.hasNestedData()) {
                    //Update the current radial depth
                    parents.push(radialData);
                    radialData = nestedMode.nestedData();
                    // and mark the previous one to not change values when closing
                    // (as it isn't "fully" closing, just changing views)
                    old.updateOnClose = false;
                }
            }
        }
    }



    public static final float DEG_TO_RAD = ((float) Math.PI / 180F);

    public static final float RAD_TO_DEG = (180F / (float) Math.PI);

    public static double length(double pXDistance, double pYDistance) {
        return Math.sqrt(lengthSquared(pXDistance, pYDistance));
    }

    public static double lengthSquared(double pXDistance, double pYDistance) {
        return pXDistance * pXDistance + pYDistance * pYDistance;
    }
}
