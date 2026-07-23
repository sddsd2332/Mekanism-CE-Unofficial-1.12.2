package mekanism.common.config;


import io.netty.buffer.ByteBuf;
import mekanism.common.config.options.*;
import mekanism.common.inventory.container.SelectedWindowData.CachedWindowPosition;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Thiakil on 15/03/2019.
 */
public class ClientConfig extends BaseConfig {

    public static final int QIO_VIEWER_SLOTS_X_MIN = 8;
    public static final int QIO_VIEWER_SLOTS_X_MAX = 16;
    public static final int QIO_VIEWER_SLOTS_Y_MIN = 2;
    public static final int QIO_VIEWER_SLOTS_Y_MAX = 48;

    public final Map<String, CachedWindowPosition> lastWindowPositions = new HashMap<>();

    public final BooleanOption enablePlayerSounds = new BooleanOption(this,  "EnablePlayerSounds", true,
            "Play sounds for Jetpack/Gas Mask/Flamethrower (all players).");

    public final BooleanOption enableMachineSounds = new BooleanOption(this,  "EnableMachineSounds", true,
            "If enabled machines play their sounds while running.");

    public final BooleanOption holidays = new BooleanOption(this,  "Holidays", true,
            "Christmas/New Years greetings in chat.");

    public final DoubleOption baseSoundVolume = new DoubleOption(this,  "SoundVolume", 1D,
            "Adjust Mekanism sounds' base volume. < 1 is softer, higher is louder.");

    public final BooleanOption machineEffects = new BooleanOption(this,  "MachineEffects", true,
            "Show particles when machines active.");

    public final BooleanOption enableAmbientLighting = new BooleanOption(this,  "EnableAmbientLighting", true,
            "Should active machines produce block light.");

    public final IntOption ambientLightingLevel = new IntOption(this,  "AmbientLightingLevel", 15,
            "How much light to produce if ambient lighting is enabled.", 1, 15);

    public final BooleanOption opaqueTransmitters = new BooleanOption(this,  "OpaqueTransmitterRender", false,
            "If true, don't render Cables/Pipes/Tubes as transparent and don't render their contents.");

    public final IntOption transmitterInteriorRenderDistance = new IntOption(this, "transmitterInteriorRenderDistance", 64,
            "Render distance (in blocks) for transparent transmitter interiors. Set to 0 to always render.", 0, 1024);

    public final BooleanOption allowModeScroll = new BooleanOption(this,  "allowModeScroll", true,
            "Allow sneak + scroll to change item modes.");

    public final BooleanOption enableMultiblockFormationParticles = new BooleanOption(this,  "MultiblockFormParticles", true,
            "Set to false to prevent particle spam when loading multiblocks (notification message will display instead).");

    public final BooleanOption renderToolAOEParticles = new BooleanOption(this,  "RenderToolAOEParticles", true,
            "Show bolts for Meka-Tool area mining behaviors, such as vein mining.");

    public final BooleanOption alignHUDLeft = new BooleanOption(this,  "AlignHUDLeft", true,
            "Align HUD with left (if true) or right (if false)");

    public final BooleanOption enableHUD = new BooleanOption(this,  "enableHUD", true,
            "Enable item information HUD during gameplay");

    public final BooleanOption enableSlotTypeTooltips = new BooleanOption(this, "EnableSlotTypeTooltips", true,
            "Show helper tooltips for empty Mekanism GUI input/output/extra/energy slots.");

    public final IntOption qioItemViewerSortType = new IntOption(this, "QIOViewerSortType", 0,
            "QIO viewer sort mode. 0 = name, 1 = count, 2 = resource kind, 3 = registry name.", 0, 3);

    public final BooleanOption qioItemViewerSortDescending = new BooleanOption(this, "QIOViewerSortDescending", false,
            "Sort QIO viewer resources in descending order.");

    public final IntOption qioItemViewerSlotsX = new IntOption(this, "QIOViewerSlotsWide", 8,
            "Number of resource columns in the QIO viewer.", QIO_VIEWER_SLOTS_X_MIN, QIO_VIEWER_SLOTS_X_MAX);

    public final IntOption qioItemViewerSlotsY = new IntOption(this, "QIOViewerSlotsTall", 4,
            "Number of resource rows in the QIO viewer.", QIO_VIEWER_SLOTS_Y_MIN, QIO_VIEWER_SLOTS_Y_MAX);

    public final BooleanOption qioAutoFocusSearchBar = new BooleanOption(this, "QIOViewerAutoFocusSearch", true,
            "Automatically focus the QIO viewer search field when opening it.");

    public final BooleanOption qioRejectsToInventory = new BooleanOption(this, "QIOViewerRejectsToInventory", false,
            "Return rejected recipe-transfer items to the player inventory instead of the QIO frequency.");

    public final IntOption AllMekGuiBg = new IntOption(this,  "AllMekGuiBg", 0xFFFFFFFF,
            "All mekanism GUI background colors");

    public final FloatOption hudScale = new FloatOption(this,  "hudScale", 0.6F, "Scale of the text displayed on the HUD.", 0.25F, 1F);

    public final IntOption hudX = new IntOption(this,  "hudX", 0, "The HUD is offset to the left and right of the screen.");

    public final IntOption hudY = new IntOption(this,  "hudY", 0, "The HUD is offset up and down the screen.");

    public final BooleanOption enableFirstPersonMekaSuitArms = new BooleanOption(this,  "enableFirstPersonMekaSuitArms", true, "Whether to enable the first-person arm of MekaSuitArms ? (It is currently in WIP status)");

    public final BooleanOption enableBloom = new BooleanOption(this,  "enableBloom", false, "Enable the glow texture of MeKCEu, which may cause a performance penalty. (GTCEu or Lumenized installation required)");

    public final IntOption customBloomStyle = new IntOption(this, "customBloomStyle", 0,
            "Bloom algorithm used for Mekanism's custom glow with GTCEu/Lumenized. 0 = Gaussian (fast), 1 = Unity, 2 = Unreal (slowest).", 0, 2);

    public final IntOption customBloomRenderDistance = new IntOption(this, "customBloomRenderDistance", 64,
            "Render distance (in blocks) for Mekanism's custom glow. Lower values reduce bloom cost. Set to 0 to follow normal tile render distance.", 0, 512);

    public final BooleanOption windGeneratorItem = new BooleanOption(this,  "WindGenerator", true, "Wind turbine blade rotation [item]");

    public final BooleanOption windGeneratorRotating = new BooleanOption(this,  "windGeneratorRotating", true, "Wind turbine blade rotation [block]");

    public final IntOption windGeneratorBladeRenderDistance = new IntOption(this, "windGeneratorBladeRenderDistance", 0,
            "Render distance (in blocks) for wind turbine blades. Set to 0 to always render blades.", 0, 1024);

    public final FloatOption hudJitter = new FloatOption(this,  "hudJitter", 6F, "Visual jitter of MekaSuit HUD, seen when moving the player's head. Bigger value = more jitter.", 1F, 100F);

    public final FloatOption hudOpacity = new FloatOption(this,  "hudOpacity", 0.4F, "Opacity of HUD used by MekaSuit.", 0F, 1F);

    public final BooleanOption largeWindGeneratorRending = new BooleanOption(this,  "largeWindGeneratorRending", true, "Whether to render a model of a large wind turbine，If false, no rendering is done, which may optimize the fps");

    public final IntOption largeWindGeneratorFanRenderDistance = new IntOption(this, "largeWindGeneratorFanRenderDistance", 0,
            "Render distance (in blocks) for large wind turbine fan blades. Set to 0 to always render blades.", 0, 1024);

    public final BooleanOption hudCompassEnabled = new BooleanOption(this,  "mekaSuitHelmetCompass", true, "Display a fancy compass when the MekaSuit is worn.");

    public final IntOption hudColor = new IntOption(this,  "hudColor", 0x40F5F0, "Color (RGB) of HUD used by MekaSuit.", 0, 0xFFFFFF);

    public final IntOption hudWarningColor = new IntOption(this,  "hudWarningColor", 0xFFDD4F, "Color (RGB) of warning HUD elements used by MekaSuit.", 0, 0xFFFFFF);

    public final IntOption hudDangerColor = new IntOption(this,  "hudDangerColor", 0xFF383C, "Color (RGB) of danger HUD elements used by MekaSuit.", 0, 0xFFFFFF);

    public final BooleanOption whiteRadialText = new BooleanOption(this,  "whiteRadialText", false, "If enabled tries to force all radial menu text to be white.");

    public final IntOption radiationParticleCount = new IntOption(this,  "radiationParticleCount", 100, "How many particles spawn when rendering radiation effects (scaled by radiation level).");

    public final IntOption radiationParticleRadius = new IntOption(this,  "radiationParticleRadius", 30, "How far (in blocks) from the player radiation particles can spawn.");

    public final IntOption terRange = new IntOption(this,"terRange",256,"Range at which Tile Entity Renderer's added by Mekanism can render at, for example the contents of multiblocks. Vanilla defaults the rendering range for TERs to 64 for most blocks, but uses a range of 256 for beacons and end gateways.", 1, 1024);

    public final BooleanOption largeWindGeneratorisGlobalRenderer = new BooleanOption(this,  "largeWindGeneratorisGlobalRenderer", true,"Should large wind turbines always be rendered?");

    public final BooleanOption enableSelectionWireframeRendering = new BooleanOption(this, "EnableSelectionWireframeRendering", true,
            "Master switch for Mekanism's model-based selection wireframes and JSON-derived selection boxes. Disable to fall back to normal block selection boxes.");

    public final StringListOption jsonSelectionBoxModIdWhitelist = new StringListOption(this, "JsonSelectionBoxModIdWhitelist", new String[]{"mekanism","mekanismgenerators","mekanismtools","mekanismmultiblockmachine","mekceumoremachine"},
            "Mod id whitelist for parsing block models (models/block/*.json) into selection boxes. Supports '*' to match all mods. Example: [mekanism,minecraft]");

    public final StringListOption jsonSelectionBoxModelSkipList = new StringListOption(this, "JsonSelectionBoxModelSkipList",
            new String[]{"mekanism:nothing", "mekanism:basic_cube", "mekanism:machine"},
            "Model blacklist for JSON selection boxes. Supports '*' wildcard. Example: [mekanism:machine,mekanism:basic_cube,mekanism:block/nothing]");

    public final StringListOption jsonSelectionBoxKeepVisibleInternalEdgesModIdWhitelist = new StringListOption(this,
            "JsonSelectionBoxKeepVisibleInternalEdgesModIdWhitelist", new String[0],
            "Mod id whitelist for keeping visible internal wireframe edges while still reducing stitched seam lines. Disables extra view-angle edge filtering. Supports '*' wildcard. Example: [draconicevolution]");

    public final FloatOption jsonSelectionBoxLineWidth = new FloatOption(this, "JsonSelectionBoxLineWidth", 2.0F,
            "Line width for model-based selection wireframes. Increase for thicker outlines.", 1.0F, 8.0F);

    public final BooleanOption jsonSelectionBoxAutoColorCycle = new BooleanOption(this, "JsonSelectionBoxAutoColorCycle", false,
            "Whether model-based selection wireframes should automatically cycle through rainbow colors.");

    public final BooleanOption GazeCullingTracking = new BooleanOption(this, "GazeCullingTracking", false,
            "Cancel rendering the machine when it is not within the player's line of sight.");

    public final BooleanOption GazeCullingOpenGLTracking = new BooleanOption(this, "GazeCullingOpenGLTracking", false,
            "Use OpenGL occlusion query for tile gaze culling (asynchronous, 1+ frame delayed). Falls back to CPU culling when unsupported.");

    public final IntOption GazeCullingOpenGLQueryInterval = new IntOption(this, "GazeCullingOpenGLQueryInterval", 2,
            "How many client ticks to wait before issuing a new OpenGL occlusion query for the same tile. Higher values reduce GPU query overhead.", 1, 20);

    public ClientConfig() {
        for (WindowType windowType : WindowType.getRegisteredWindowTypes()) {
            registerWindowType(windowType);
        }
    }

    public void registerWindowType(WindowType windowType) {
        for (String savePath : windowType.getSavePaths()) {
            lastWindowPositions.computeIfAbsent(savePath, path -> new CachedWindowPosition(this, path, windowType.canPin()));
        }
    }

    @Override
    public void write(ByteBuf config) {
        throw new UnsupportedOperationException("Client config shouldn't be synced");
    }

    @Override
    public void read(ByteBuf config) {
        throw new UnsupportedOperationException("Client config shouldn't be synced");
    }

    @Override
    public String getCategory() {
        return "client";
    }
}
