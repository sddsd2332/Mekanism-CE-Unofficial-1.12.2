package mekanism.client.gui.element;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.util.MekanismUtils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.GlStateManager.DestFactor;
import net.minecraft.client.renderer.GlStateManager.SourceFactor;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class GuiGraph extends GuiElement {

    private static final ResourceLocation GRAPH = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "graph.png");
    private static final int TEXTURE_WIDTH = 3;
    private static final int TEXTURE_HEIGHT = 2;

    private final List<Integer> graphData = new ArrayList<>();
    private final GraphDataHandler dataHandler;
    private final int graphWidth;
    private final int graphHeight;
    private int currentScale = 10;
    private int minScale = 10;
    private boolean fixedScale;

    public GuiGraph(IGuiWrapper gui, int x, int y, int width, int height, GraphDataHandler dataHandler) {
        super(gui, x, y, width, height);
        this.graphWidth = width - 2;
        this.graphHeight = height - 2;
        this.dataHandler = dataHandler;
        active = true;
    }

    public void enableFixedScale(int scale) {
        fixedScale = true;
        currentScale = Math.max(1, scale);
    }

    public void setMinScale(int scale) {
        minScale = Math.max(1, scale);
        if (!fixedScale) {
            currentScale = Math.max(currentScale, minScale);
        }
    }

    public void addData(int data) {
        if (graphData.size() == graphWidth) {
            graphData.remove(0);
        }
        graphData.add(data);
        if (!fixedScale) {
            currentScale = minScale;
            for (int value : graphData) {
                if (value > currentScale) {
                    currentScale = value;
                }
            }
        }
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        drawBlack();
        drawGraph(mouseX, mouseY);
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        int hoverIndex = mouseX - getX() - 1;
        if (hoverIndex >= 0 && hoverIndex < graphData.size()) {
            gui().displayTooltip(dataHandler.getDataDisplay(graphData.get(hoverIndex)), mouseX, mouseY);
        }
    }

    private void drawBlack() {
        GuiUtils.renderBackgroundTexture(GuiInnerScreen.SCREEN, 4, 4, relativeX, relativeY, width, height, 256, 256);
        MekanismRenderer.resetColor();
    }

    private void drawGraph(int mouseX, int mouseY) {
        minecraft.renderEngine.bindTexture(GRAPH);
        int size = graphData.size();
        for (int i = 0; i < size; i++) {
            int data = Math.min(currentScale, graphData.get(i));
            int relativeHeight = (int) (((double) data / currentScale) * graphHeight);
            int x = relativeX + 1 + i;
            int y = relativeY + 1 + graphHeight - relativeHeight;
            GuiUtils.blit(x, y, 0, 0, 1, 1, TEXTURE_WIDTH, TEXTURE_HEIGHT);
            GlStateManager.shadeModel(GL11.GL_SMOOTH);
            GlStateManager.disableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.color(1, 1, 1, 0.2F + 0.8F * ((float) i / Math.max(1, size)));
            if (relativeHeight > 1) {
                GuiUtils.blit(x, y, 1, 0, 1, relativeHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);
            }
            if (mouseX - getX() - 1 == i && mouseY >= getY() + 1 && mouseY < getY() + 1 + graphHeight) {
                GlStateManager.color(1, 1, 1, 0.5F);
                GuiUtils.blit(x, relativeY + 1, 2, 0, 1, graphHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);
                MekanismRenderer.resetColor();
                GuiUtils.blit(x, y, 0, 1, 1, 1, TEXTURE_WIDTH, TEXTURE_HEIGHT);
            }
            MekanismRenderer.resetColor();
            GlStateManager.disableBlend();
            GlStateManager.enableAlpha();
        }
    }

    @Override
    public boolean hasPersistentData() {
        return true;
    }

    @Override
    public void syncFrom(GuiElement element) {
        super.syncFrom(element);
        graphData.clear();
        for (int data : ((GuiGraph) element).graphData) {
            addData(data);
        }
    }

    public interface GraphDataHandler {

        String getDataDisplay(int data);
    }
}
