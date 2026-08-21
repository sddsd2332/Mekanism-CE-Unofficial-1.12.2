package mekanism.client.render;

import mekanism.api.EnumColor;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.api.infuse.InfuseRegistry;
import mekanism.api.transmitters.TransmissionType;
import mekanism.client.render.obj.TransmitterModel;
import mekanism.client.render.item.machine.RenderFluidTankItem;
import mekanism.client.render.tileentity.*;
import mekanism.client.render.transmitter.RenderLogisticalTransporter;
import mekanism.client.render.transmitter.RenderMechanicalPipe;
import mekanism.common.Mekanism;
import mekanism.common.base.IMetaItem;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.lib.Color;
import mekanism.common.tier.BaseTier;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

@SideOnly(Side.CLIENT)
public class MekanismRenderer {

    public static final GlowInfo NO_GLOW = new GlowInfo(0, 0, false);

    public static TextureAtlasSprite energyIcon;
    public static TextureAtlasSprite heatIcon;
    public static TextureAtlasSprite laserIcon;
    public static TextureAtlasSprite redstonePulse;
    public static TextureAtlasSprite whiteIcon;
    public static Map<TransmissionType, TextureAtlasSprite> overlays = new EnumMap<>(TransmissionType.class);
    private static RenderConfigurableMachine machineRenderer = new RenderConfigurableMachine();
    public static TextureAtlasSprite missingIcon;
    private static TextureMap texMap = null;

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new MekanismRenderer());
    }

    @SuppressWarnings("unchecked")
    public static <S extends TileEntity & ISideConfiguration> RenderConfigurableMachine<S> machineRenderer() {
        return machineRenderer;
    }

    public static void registerItemRender(String domain, Item item) {
        if (item instanceof IMetaItem metaItem) {
            for (int i = 0; i < metaItem.getVariants(); i++) {
                if (metaItem.getTexture(i) == null) {
                    continue;
                }

                ModelResourceLocation loc = new ModelResourceLocation(new ResourceLocation(domain, metaItem.getTexture(i)), "inventory");
                ModelLoader.setCustomModelResourceLocation(item, i, loc);
                ModelBakery.registerItemVariants(item, new ResourceLocation(domain, metaItem.getTexture(i)));
            }

            return;
        }

        ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }

    public static void initFluidTextures(TextureMap map) {
        missingIcon = map.getMissingSprite();
        texMap = map;
    }

    /**
     * Get a fluid texture when a stack does not exist.
     *
     * @param fluid the fluid to get
     * @param type  Still or Flowing
     * @return the sprite, or missing sprite if not found
     */
    public static TextureAtlasSprite getBaseFluidTexture(Fluid fluid, FluidType type) {
        if (fluid == null || type == null) {
            return missingIcon;
        }

        ResourceLocation spriteLocation;
        if (type == FluidType.STILL) {
            spriteLocation = fluid.getStill();
        } else {
            spriteLocation = fluid.getFlowing();
        }

        return getTextureAtlasSprite(spriteLocation);
    }

    public static TextureAtlasSprite getFluidTexture(FluidStack fluidStack, FluidType type) {
        if (fluidStack == null || type == null) {
            return missingIcon;
        }

        Fluid fluid = fluidStack.getFluid();
        ResourceLocation spriteLocation;
        if (type == FluidType.STILL) {
            spriteLocation = fluid.getStill(fluidStack);
        } else {
            spriteLocation = fluid.getFlowing(fluidStack);
        }
        return getTextureAtlasSprite(spriteLocation);
    }

    public static TextureAtlasSprite getTextureAtlasSprite(ResourceLocation spriteLocation) {
        TextureAtlasSprite sprite = texMap.getTextureExtry(spriteLocation.toString());
        return sprite != null ? sprite : missingIcon;
    }

    public static RenderState pauseRenderer(Tessellator tess) {
        RenderState state = null;
        if (MekanismRenderer.isDrawing(tess)) {
            state = new RenderState(tess.getBuffer().getVertexFormat(), tess.getBuffer().getDrawMode());
            tess.draw();
        }
        return state;
    }

    public static void resumeRenderer(Tessellator tess, RenderState renderState) {
        if (renderState != null) {
            tess.getBuffer().begin(renderState.prevMode, renderState.prevFormat);
        }
    }

    public static boolean isDrawing(Tessellator tess) {
        return tess.getBuffer().isDrawing;
    }

    public static boolean isDrawing(BufferBuilder buffer) {
        return buffer.isDrawing;
    }

    public static BakedQuad iconTransform(BakedQuad quad, TextureAtlasSprite sprite) {
        int[] vertices = new int[quad.getVertexData().length];
        System.arraycopy(quad.getVertexData(), 0, vertices, 0, vertices.length);

        for (int i = 0; i < 4; ++i) {
            int j = quad.getFormat().getIntegerSize() * i;
            int uvIndex = quad.getFormat().getUvOffsetById(0) / 4;
            if (j + uvIndex + 1 < vertices.length) {
                vertices[j + uvIndex] = Float.floatToRawIntBits(sprite.getInterpolatedU(quad.getSprite().getUnInterpolatedU(Float.intBitsToFloat(vertices[j + uvIndex]))));
                vertices[j + uvIndex + 1] = Float.floatToRawIntBits(sprite.getInterpolatedV(quad.getSprite().getUnInterpolatedV(Float.intBitsToFloat(vertices[j + uvIndex + 1]))));
            }
        }

        return new BakedQuad(vertices, quad.getTintIndex(), quad.getFace(), sprite, quad.shouldApplyDiffuseLighting(), quad.getFormat());
    }

    public static BakedQuad rotate(BakedQuad quad, int amount) {
        int[] vertices = new int[quad.getVertexData().length];
        System.arraycopy(quad.getVertexData(), 0, vertices, 0, vertices.length);

        for (int i = 0; i < 4; i++) {
            int nextIndex = (i + amount) % 4;
            int quadSize = quad.getFormat().getIntegerSize();
            int uvIndex = quad.getFormat().getUvOffsetById(0) / 4;
            if (i + uvIndex + 1 < vertices.length) {
                vertices[quadSize * i + uvIndex] = quad.getVertexData()[quadSize * nextIndex + uvIndex];
                vertices[quadSize * i + uvIndex + 1] = quad.getVertexData()[quadSize * nextIndex + uvIndex + 1];
            }
        }

        return new BakedQuad(vertices, quad.getTintIndex(), quad.getFace(), quad.getSprite(), quad.shouldApplyDiffuseLighting(), quad.getFormat());
    }

    public static void prepFlowing(Model3D model, FluidStack fluid) {
        TextureAtlasSprite still = getFluidTexture(fluid, FluidType.STILL);
        TextureAtlasSprite flowing = getFluidTexture(fluid, FluidType.FLOWING);
        model.setTextures(still, still, flowing, flowing, flowing, flowing);
    }

    public static void renderObject(Model3D object) {
        if (object == null) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) object.minX, (float) object.minY, (float) object.minZ);
        RenderResizableCuboid.INSTANCE.renderCube(object);
        GlStateManager.popMatrix();
    }

    public static void bindTexture(ResourceLocation texture) {
        Minecraft.getMinecraft().renderEngine.bindTexture(texture);
    }

    //Color
    public static void resetColor() {
        GlStateManager.color(1, 1, 1, 1);
    }

    public static void resetGuiRenderState() {
        resetColor();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.shadeModel(GL11.GL_FLAT);
    }

    public static void resetItemRenderState() {
        resetColor();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
    }

    public static void resetBlockRenderState() {
        resetColor();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.disableCull();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
    }

    private static float getRed(int color) {
        return (color >> 16 & 0xFF) / 255.0F;
    }

    private static float getGreen(int color) {
        return (color >> 8 & 0xFF) / 255.0F;
    }

    private static float getBlue(int color) {
        return (color & 0xFF) / 255.0F;
    }
    public static float getAlpha(int color) {
        return (color >> 24 & 0xFF) / 255.0F;
    }

    public static void color(int color) {
        GlStateManager.color(getRed(color), getGreen(color), getBlue(color), (color >> 24 & 0xFF) / 255f);
    }

    public static void color(Color color) {
        GlStateManager.color(color.rf(), color.gf(), color.bf(), color.af());
    }


    public static void color(@Nullable FluidStack fluid, float fluidScale) {
        if (fluid != null) {
            int color = fluid.getFluid().getColor(fluid);
            if (fluid.getFluid().isGaseous(fluid)) {
                GlStateManager.color(getRed(color), getGreen(color), getBlue(color), Math.min(1, fluidScale + 0.2F));
            } else {
                color(color);
            }
        }
    }

    public static void color(@Nullable FluidStack fluid) {
        if (fluid != null && fluid.getFluid() != null) {
            color(fluid.getFluid().getColor(fluid));
        }
    }

    public static void color(@Nullable Fluid fluid) {
        if (fluid != null) {
            color(fluid.getColor());
        }
    }

    public static void color(@Nullable GasStack gasStack) {
        if (gasStack != null) {
            color(gasStack.getGas());
        }
    }

    public static void color(@Nullable Gas gas) {
        if (gas != null) {
            color(gas, 1.0F);
        }
    }

    public static void color(@Nullable Gas gas, float alpha) {
        if (gas != null) {
            int color = gas.getTint();
            GlStateManager.color(getRed(color), getGreen(color), getBlue(color), alpha);
        }
    }

    public static void color(@Nonnull BaseTier tier) {
        color(tier.getColor());
    }

    public static void color(@Nullable EnumColor color) {
        color(color, 1.0F);
    }

    public static void color(@Nullable EnumColor color, float alpha) {
        color(color, alpha, 1.0F);
    }

    public static void color(@Nullable EnumColor color, float alpha, float multiplier) {
        if (color != null) {
            GlStateManager.color(color.getColor(0) * multiplier, color.getColor(1) * multiplier, color.getColor(2) * multiplier, alpha);
        }
    }

    public static int getColorARGB(@Nonnull FluidStack fluidStack) {
        return fluidStack.getFluid().getColor(fluidStack);
    }

    public static int getColorARGB(@Nonnull FluidStack fluidStack, float fluidScale) {
        if (fluidStack == null) {
            return -1;
        }
        int color = getColorARGB(fluidStack);
        if (fluidStack.getFluid().isGaseous(fluidStack)) {
            //TODO: We probably want to factor in the fluid's alpha value somehow
            return getColorARGB(getRed(color), getGreen(color), getBlue(color), Math.min(1, fluidScale + 0.2F));
        }
        return color;
    }

    public static int getColorARGB(@Nonnull GasStack stack, float scale, boolean gaseous) {
        if (stack == null) {
            return -1;
        }
        int color = stack.getGas().getTint();
        return getColorARGB(getRed(color), getGreen(color), getBlue(color), gaseous ? Math.min(1, scale + 0.2F) : 1);
    }

    public static int getColorARGB(float red, float green, float blue, float alpha) {
        return getColorARGB((int) (255 * red), (int) (255 * green), (int) (255 * blue), alpha);
    }

    public static int getColorARGB(EnumColor color, float alpha) {
        return getColorARGB(color.rgbCode[0], color.rgbCode[1], color.rgbCode[2], alpha);
    }

    public static int getColorARGB(int red, int green, int blue, float alpha) {
        if (alpha < 0) {
            alpha = 0;
        } else if (alpha > 1) {
            alpha = 1;
        }
        int argb = (int) (255 * alpha) << 24;
        argb |= red << 16;
        argb |= green << 8;
        argb |= blue;
        return argb;
    }

    public static int calculateGlowLight(int combinedLight, @Nonnull FluidStack fluid) {
        return fluid == null ? combinedLight : calculateGlowLight(combinedLight, fluid.getFluid().getLuminosity(fluid));
    }

    public static int calculateGlowLight(int combinedLight, int glow) {
        //Only factor the glow into the block light portion
        return (combinedLight & 0xFFFF0000) | Math.max(Math.min(glow, 15) << 4, combinedLight & 0xFFFF);
    }


    @Nonnull
    public static GlowInfo enableGlow() {
        return enableGlow(15);
    }

    @Nonnull
    public static GlowInfo enableGlow(int glow) {
        if (!FMLClientHandler.instance().hasOptifine() && glow > 0) {
            GlowInfo info = new GlowInfo(OpenGlHelper.lastBrightnessX, OpenGlHelper.lastBrightnessY, true);
            float glowStrength = (glow / 15F) * 240F;
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, Math.min(glowStrength + info.lightmapLastX, 240), Math.min(glowStrength + info.lightmapLastY, 240));
            return info;
        }
        return NO_GLOW;
    }

    @Nonnull
    public static GlowInfo enableGlow(@Nullable FluidStack fluid) {
        return fluid == null ? NO_GLOW : enableGlow(fluid.getFluid().getLuminosity(fluid));
    }

    @Nonnull
    public static GlowInfo enableGlow(@Nullable Fluid fluid) {
        return fluid == null ? NO_GLOW : enableGlow(fluid.getLuminosity());
    }

    public static void disableGlow(@Nonnull GlowInfo info) {
        if (info.glowEnabled) {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, info.lightmapLastX, info.lightmapLastY);
        }
    }

    public static float getPartialTick() {
        return Minecraft.getMinecraft().getRenderPartialTicks();
    }

    public static void rotate(EnumFacing facing, float north, float south, float west, float east) {
        switch (facing) {
            case NORTH:
                GlStateManager.rotate(north, 0, 1, 0);
                break;
            case SOUTH:
                GlStateManager.rotate(south, 0, 1, 0);
                break;
            case WEST:
                GlStateManager.rotate(west, 0, 1, 0);
                break;
            case EAST:
                GlStateManager.rotate(east, 0, 1, 0);
                break;
        }
    }

    @SubscribeEvent
    public void onStitch(TextureStitchEvent.Pre event) {
        for (TransmissionType type : TransmissionType.values()) {
            overlays.put(type, event.getMap().registerSprite(new ResourceLocation(Mekanism.MODID, "blocks/overlay/" + type.getTransmission() + "Overlay")));
        }

        whiteIcon = event.getMap().registerSprite(new ResourceLocation(Mekanism.MODID, "blocks/overlay/overlay_white"));
        energyIcon = event.getMap().registerSprite(new ResourceLocation(Mekanism.MODID, "blocks/liquid/LiquidEnergy"));
        heatIcon = event.getMap().registerSprite(new ResourceLocation(Mekanism.MODID, "blocks/liquid/LiquidHeat"));
        laserIcon = event.getMap().registerSprite(new ResourceLocation(Mekanism.MODID, "blocks/Laser"));
        redstonePulse = event.getMap().registerSprite(new ResourceLocation(Mekanism.MODID, "icon/redstone_control_pulse"));

        event.getMap().registerSprite(new ResourceLocation(Mekanism.MODID, "blocks/liquid/LiquidHeavyWater"));

        TransmitterModel.registerIcons(event.getMap());
        GasRegistry.getRegisteredGasses().forEach(gas -> gas.registerIcon(event.getMap()));
        InfuseRegistry.getInfuseMap().values().forEach(type -> type.setIcon(event.getMap().registerSprite(type.iconResource)));

        FluidRenderer.resetDisplayInts();
        MinerVisualRenderer.resetDisplayInts();
        RenderFluidTank.resetDisplayInts();
        RenderFluidTankItem.resetDisplayInts();
        RenderTeleporter.resetDisplayInts();
        RenderConfigurableMachine.resetDisplayInts();
        RenderNutritionalLiquifier.resetDisplayInts();
        RenderIsotopicCentrifuge.resetDisplayInts();
        RenderChemicalCrystallizer.resetDisplayInts();
        RenderChemicalDissolutionChamber.resetDisplayInts();
    }

    @SubscribeEvent
    public void onStitch(TextureStitchEvent.Post event) {
        initFluidTextures(event.getMap());

        RenderLogisticalTransporter.onStitch(event.getMap());
        RenderMechanicalPipe.onStitch();

        GasRegistry.getRegisteredGasses().forEach(gas -> gas.updateIcon(event.getMap()));
    }

    public enum FluidType {
        STILL,
        FLOWING
    }

    public interface ICustomBlockIcon {

        ResourceLocation getIcon(ItemStack stack, int side);
    }

    public static class Model3D {

        public double posX, posY, posZ;

        public double minX, minY, minZ;
        public double maxX, maxY, maxZ;

        public double textureStartX = 0, textureStartY = 0, textureStartZ = 0;
        public double textureSizeX = 16, textureSizeY = 16, textureSizeZ = 16;
        public double textureOffsetX = 0, textureOffsetY = 0, textureOffsetZ = 0;

        public int[] textureFlips = new int[]{2, 2, 2, 2, 2, 2};

        public TextureAtlasSprite[] textures = new TextureAtlasSprite[6];

        public boolean[] renderSides = new boolean[]{true, true, true, true, true, true, false};

        public Block baseBlock = Blocks.SAND;

        public final void setBlockBounds(double xNeg, double yNeg, double zNeg, double xPos, double yPos, double zPos) {
            minX = xNeg;
            minY = yNeg;
            minZ = zNeg;
            maxX = xPos;
            maxY = yPos;
            maxZ = zPos;
        }

        public double sizeX() {
            return maxX - minX;
        }

        public double sizeY() {
            return maxY - minY;
        }

        public double sizeZ() {
            return maxZ - minZ;
        }

        public void setSideRender(EnumFacing side, boolean value) {
            renderSides[side.ordinal()] = value;
        }

        public boolean shouldSideRender(EnumFacing side) {
            return renderSides[side.ordinal()];
        }

        public TextureAtlasSprite getBlockTextureFromSide(int i) {
            return textures[i];
        }

        public void setTexture(TextureAtlasSprite tex) {
            Arrays.fill(textures, tex);
        }

        public void setTextures(TextureAtlasSprite down, TextureAtlasSprite up, TextureAtlasSprite north, TextureAtlasSprite south, TextureAtlasSprite west, TextureAtlasSprite east) {
            textures[0] = down;
            textures[1] = up;
            textures[2] = north;
            textures[3] = south;
            textures[4] = west;
            textures[5] = east;
        }
    }

    public static class DisplayInteger {

        public int display;

        public static DisplayInteger createAndStart() {
            DisplayInteger newInteger = new DisplayInteger();
            newInteger.display = GLAllocation.generateDisplayLists(1);
            GlStateManager.glNewList(newInteger.display, GL11.GL_COMPILE);
            return newInteger;
        }

        public static void endList() {
            GlStateManager.glEndList();
        }

        public static void deleteAll(DisplayInteger[] displays) {
            if (displays != null) {
                for (DisplayInteger display : displays) {
                    if (display != null) {
                        display.delete();
                    }
                }
            }
        }

        public void delete() {
            if (display != 0) {
                GLAllocation.deleteDisplayLists(display);
                display = 0;
            }
        }

        @Override
        public int hashCode() {
            int code = 1;
            code = 31 * code + display;
            return code;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof DisplayInteger && ((DisplayInteger) obj).display == display;
        }

        public void render() {
            GlStateManager.callList(display);
        }
    }

    public static class RenderState {

        private final VertexFormat prevFormat;
        private final int prevMode;

        private RenderState(VertexFormat prevFormat, int prevMode) {
            this.prevFormat = prevFormat;
            this.prevMode = prevMode;
        }
    }

    public static class GlowInfo {

        private final boolean glowEnabled;
        private final float lightmapLastX;
        private final float lightmapLastY;

        public GlowInfo(float lightmapLastX, float lightmapLastY, boolean glowEnabled) {
            this.lightmapLastX = lightmapLastX;
            this.lightmapLastY = lightmapLastY;
            this.glowEnabled = glowEnabled;
        }
    }

    public static void renderColorOverlay(int x, int y, int width, int height, int color) {
        float r = getRed(color);
        float g = getGreen(color);
        float b = getBlue(color);
        float a = getAlpha(color);
        GlStateManager.disableDepth();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();
        bufferbuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        bufferbuilder.pos( width, y, 0).color(r, g, b, a).endVertex();
        bufferbuilder.pos( x, y, 0).color(r, g, b, a).endVertex();
        bufferbuilder.pos( x, height, 0).color(r, g, b, a).endVertex();
        bufferbuilder.pos( width, height, 0).color(r, g, b, a).endVertex();
        tessellator.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
    }
}
