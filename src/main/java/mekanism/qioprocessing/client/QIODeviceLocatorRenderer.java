package mekanism.qioprocessing.client;

import mekanism.client.render.JsonModelSelectionBoxCache;
import mekanism.client.render.SelectionWireframeRenderer;
import mekanism.client.render.SpecialSelectionWireframeRegistry;
import mekanism.common.block.BlockBounding;
import mekanism.common.block.interfaces.IHighlightBoxProvider;
import mekanism.qioprocessing.api.machine.QIOAutomationDeviceLocation;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceSnapshot;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Client-owned world highlight copied from an already authorized management snapshot. */
public final class QIODeviceLocatorRenderer {

    public static final QIODeviceLocatorRenderer INSTANCE = new QIODeviceLocatorRenderer();

    private static final long DURATION_MS = 20_000L;
    private static final long BLINK_INTERVAL_MS = 300L;
    private static final double BOX_GROW = 0.03D;
    private static final float ONLINE_RED = 1.0F;
    private static final float ONLINE_GREEN = 0.35F;
    private static final float ONLINE_BLUE = 0.35F;
    private static final float OFFLINE_RED = 1.0F;
    private static final float OFFLINE_GREEN = 0.65F;
    private static final float OFFLINE_BLUE = 0.15F;
    private static final float ALPHA = 0.95F;

    private final Set<BlockPos> onlineLocations = new LinkedHashSet<>();
    private final Set<BlockPos> offlineLocations = new LinkedHashSet<>();
    private int highlightedDimension = Integer.MIN_VALUE;
    private long expiresAt;

    private QIODeviceLocatorRenderer() {
    }

    public void showLocation(QIOAutomationDeviceSnapshot device) {
        clear();
        if (device != null) {
            addLocation(device.getLocation(), device.isOnline());
        }
        finishLocations();
    }

    public void showLocations(Collection<QIOAutomationDeviceSnapshot> devices) {
        clear();
        if (devices != null) {
            for (QIOAutomationDeviceSnapshot device : devices) {
                if (device != null) {
                    addLocation(device.getLocation(), device.isOnline());
                }
            }
        }
        finishLocations();
    }

    private void addLocation(QIOAutomationDeviceLocation location, boolean online) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || minecraft.player == null || location == null ||
            location.dimension() != minecraft.world.provider.getDimension()) {
            return;
        }
        BlockPos position = location.position();
        if (online) {
            offlineLocations.remove(position);
            onlineLocations.add(position);
        } else if (!onlineLocations.contains(position)) {
            offlineLocations.add(position);
        }
    }

    private void finishLocations() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || minecraft.player == null ||
            onlineLocations.isEmpty() && offlineLocations.isEmpty()) {
            clear();
            return;
        }
        highlightedDimension = minecraft.world.provider.getDimension();
        expiresAt = System.currentTimeMillis() + DURATION_MS;
    }

    @SubscribeEvent
    public void renderWorldLast(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || minecraft.player == null) {
            clear();
            return;
        }
        if (onlineLocations.isEmpty() && offlineLocations.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (minecraft.world.provider.getDimension() != highlightedDimension || now > expiresAt) {
            clear();
            return;
        }
        if (((now / BLINK_INTERVAL_MS) & 1L) == 1L) {
            return;
        }

        double cameraX = minecraft.getRenderManager().viewerPosX;
        double cameraY = minecraft.getRenderManager().viewerPosY;
        double cameraZ = minecraft.getRenderManager().viewerPosZ;
        boolean mekanismSelectionEnabled =
              SelectionWireframeRenderer.isSelectionWireframeRenderingEnabled();

        SelectionWireframeRenderer.begin(
              SelectionWireframeRenderer.getConfiguredLineWidth(), true);
        try {
            for (BlockPos location : onlineLocations) {
                renderLocation(minecraft.world, location, cameraX, cameraY, cameraZ,
                      mekanismSelectionEnabled, ONLINE_RED, ONLINE_GREEN, ONLINE_BLUE);
            }
            for (BlockPos location : offlineLocations) {
                renderLocation(minecraft.world, location, cameraX, cameraY, cameraZ,
                      mekanismSelectionEnabled, OFFLINE_RED, OFFLINE_GREEN, OFFLINE_BLUE);
            }
        } finally {
            SelectionWireframeRenderer.end(true);
        }
    }

    private static void renderLocation(World world, BlockPos location,
          double cameraX, double cameraY, double cameraZ,
          boolean mekanismSelectionEnabled, float red, float green, float blue) {
        BlockPos renderPos = location;
        IBlockState state = null;
        if (world.isBlockLoaded(location, false)) {
            try {
                state = world.getBlockState(location);
                if (state.getBlock() instanceof BlockBounding) {
                    BlockPos mainPos = BlockBounding.getMainBlockPos(world, location);
                    if (mainPos != null && world.isBlockLoaded(mainPos, false)) {
                        renderPos = mainPos;
                        state = world.getBlockState(mainPos);
                    }
                }
            } catch (RuntimeException ignored) {
                state = null;
                renderPos = location;
            }
        }

        if (state != null) {
            if (mekanismSelectionEnabled && renderMekanismSelection(state, world, renderPos,
                  cameraX, cameraY, cameraZ, red, green, blue)) {
                return;
            }
            try {
                AxisAlignedBB selectedBox = state.getSelectedBoundingBox(world, renderPos);
                if (drawWorldBox(selectedBox, cameraX, cameraY, cameraZ, red, green, blue)) {
                    return;
                }
            } catch (RuntimeException ignored) {
            }
        }
        drawWorldBox(new AxisAlignedBB(renderPos), cameraX, cameraY, cameraZ,
              red, green, blue);
    }

    private static boolean renderMekanismSelection(IBlockState state, World world,
          BlockPos pos, double cameraX, double cameraY, double cameraZ,
          float red, float green, float blue) {
        Block block = state.getBlock();
        if (block instanceof IHighlightBoxProvider provider) {
            AxisAlignedBB[] boxes = null;
            try {
                boxes = provider.getHighlightBoxes(state, world, pos);
            } catch (RuntimeException ignored) {
            }
            if (drawLocalBoxes(boxes, pos, cameraX, cameraY, cameraZ,
                  red, green, blue)) {
                return true;
            }
        }

        JsonModelSelectionBoxCache.OutlineBox[] wireframes = null;
        try {
            wireframes = SpecialSelectionWireframeRegistry.getWireframes(state, world, pos);
        } catch (RuntimeException ignored) {
        }
        if (wireframes == null || wireframes.length == 0) {
            try {
                wireframes = JsonModelSelectionBoxCache.getWireframes(state, world, pos);
            } catch (RuntimeException ignored) {
            }
        }
        if (wireframes == null || wireframes.length == 0) {
            return false;
        }

        SelectionWireframeRenderer.drawWireframes(wireframes, pos,
              cameraX, cameraY, cameraZ, red, green, blue, ALPHA,
              SelectionWireframeRenderer.keepVisibleInternalEdgesForState(state));
        return true;
    }

    private static boolean drawLocalBoxes(AxisAlignedBB[] boxes, BlockPos pos,
          double cameraX, double cameraY, double cameraZ,
          float red, float green, float blue) {
        if (boxes == null || boxes.length == 0) {
            return false;
        }
        boolean rendered = false;
        for (AxisAlignedBB box : boxes) {
            if (isValidBox(box)) {
                drawWorldBox(box.offset(pos), cameraX, cameraY, cameraZ,
                      red, green, blue);
                rendered = true;
            }
        }
        return rendered;
    }

    private static boolean drawWorldBox(AxisAlignedBB box,
          double cameraX, double cameraY, double cameraZ,
          float red, float green, float blue) {
        if (!isValidBox(box)) {
            return false;
        }
        RenderGlobal.drawSelectionBoundingBox(
              box.grow(BOX_GROW).offset(-cameraX, -cameraY, -cameraZ),
              red, green, blue, ALPHA);
        return true;
    }

    private static boolean isValidBox(AxisAlignedBB box) {
        return box != null && Double.isFinite(box.minX) && Double.isFinite(box.minY) &&
              Double.isFinite(box.minZ) && Double.isFinite(box.maxX) &&
              Double.isFinite(box.maxY) && Double.isFinite(box.maxZ) &&
              box.maxX > box.minX && box.maxY > box.minY && box.maxZ > box.minZ;
    }

    private void clear() {
        onlineLocations.clear();
        offlineLocations.clear();
        highlightedDimension = Integer.MIN_VALUE;
        expiresAt = 0;
    }
}
