package mekanism.common.integration.lookingat.theoneprobe;

import io.netty.buffer.ByteBuf;
import mcjty.theoneprobe.api.IElement;
import mcjty.theoneprobe.apiimpl.ProbeInfo;
import mekanism.api.gas.GasStack;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.common.integration.lookingat.theoneprobe.TOPChemicalElement.GasElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public abstract class TOPTankListElement implements IElement {

    private static final int ELEMENT_SPACING = 2;

    private final List<IElement> elements;
    private final int visibleHeight;

    protected TOPTankListElement(List<IElement> elements, int maxDisplayed) {
        this.elements = elements;
        visibleHeight = getElementsHeight(elements, Math.max(1, maxDisplayed));
    }

    protected TOPTankListElement(ByteBuf buf) {
        visibleHeight = buf.readInt();
        elements = ProbeInfo.createElements(buf);
    }

    @Override
    public void render(int x, int y) {
        int contentHeight = getElementsHeight(elements, elements.size());
        if (contentHeight <= visibleHeight) {
            renderElements(x, y, 0);
            return;
        }
        float offset = IGuiWrapper.marqueeOffset(contentHeight, visibleHeight, GuiElement.getMillis());
        Minecraft minecraft = Minecraft.getMinecraft();
        boolean restoreScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        IntBuffer previousScissor = restoreScissor ? getScissorBox() : null;
        setScissor(minecraft, x, y, getWidth(), visibleHeight, previousScissor);
        try {
            renderElements(x, y, offset);
        } finally {
            if (previousScissor == null) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            } else {
                GL11.glScissor(previousScissor.get(0), previousScissor.get(1),
                      previousScissor.get(2), previousScissor.get(3));
            }
        }
    }

    private void renderElements(int x, int y, float offset) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, -offset, 0);
        try {
            int elementY = y;
            for (IElement element : elements) {
                element.render(x, elementY);
                elementY += element.getHeight() + ELEMENT_SPACING;
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private static IntBuffer getScissorBox() {
        IntBuffer scissor = BufferUtils.createIntBuffer(4);
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, scissor);
        return scissor;
    }

    private static void setScissor(Minecraft minecraft, int x, int y, int width, int height, IntBuffer previousScissor) {
        ScaledResolution resolution = new ScaledResolution(minecraft);
        double scaleX = minecraft.displayWidth / (double) resolution.getScaledWidth();
        double scaleY = minecraft.displayHeight / (double) resolution.getScaledHeight();
        int scissorX = (int) Math.floor(x * scaleX);
        int scissorY = (int) Math.floor(minecraft.displayHeight - (y + height) * scaleY);
        int scissorWidth = Math.max(0, (int) Math.ceil(width * scaleX));
        int scissorHeight = Math.max(0, (int) Math.ceil(height * scaleY));
        if (previousScissor != null) {
            int left = Math.max(scissorX, previousScissor.get(0));
            int bottom = Math.max(scissorY, previousScissor.get(1));
            int right = Math.min(scissorX + scissorWidth, previousScissor.get(0) + previousScissor.get(2));
            int top = Math.min(scissorY + scissorHeight, previousScissor.get(1) + previousScissor.get(3));
            scissorX = left;
            scissorY = bottom;
            scissorWidth = Math.max(0, right - left);
            scissorHeight = Math.max(0, top - bottom);
        }
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
    }

    private static int getElementsHeight(List<IElement> elements, int maxElements) {
        int count = Math.min(elements.size(), maxElements);
        int height = 0;
        for (int element = 0; element < count; element++) {
            height += elements.get(element).getHeight();
        }
        return height + Math.max(0, count - 1) * ELEMENT_SPACING;
    }

    @Override
    public int getWidth() {
        int width = 0;
        for (IElement element : elements) {
            width = Math.max(width, element.getWidth());
        }
        return width;
    }

    @Override
    public int getHeight() {
        return visibleHeight;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(visibleHeight);
        ProbeInfo.writeElements(elements, buf);
    }

    public static class FluidListElement extends TOPTankListElement {

        public FluidListElement(FluidStack[] stored, int[] capacities, int maxDisplayed) {
            super(createFluidElements(stored, capacities), maxDisplayed);
        }

        public FluidListElement(ByteBuf buf) {
            super(buf);
        }

        private static List<IElement> createFluidElements(FluidStack[] stored, int[] capacities) {
            List<IElement> elements = new ArrayList<>(stored.length);
            for (int tank = 0; tank < stored.length; tank++) {
                elements.add(new TOPFluidElement(stored[tank], capacities[tank]));
            }
            return elements;
        }

        @Override
        public int getID() {
            return TOPProvider.FLUID_LIST_ELEMENT_ID;
        }
    }

    public static class GasListElement extends TOPTankListElement {

        public GasListElement(GasStack[] stored, int[] capacities, int maxDisplayed) {
            super(createGasElements(stored, capacities), maxDisplayed);
        }

        public GasListElement(ByteBuf buf) {
            super(buf);
        }

        private static List<IElement> createGasElements(GasStack[] stored, int[] capacities) {
            List<IElement> elements = new ArrayList<>(stored.length);
            for (int tank = 0; tank < stored.length; tank++) {
                elements.add(new GasElement(stored[tank], capacities[tank]));
            }
            return elements;
        }

        @Override
        public int getID() {
            return TOPProvider.GAS_LIST_ELEMENT_ID;
        }
    }
}
