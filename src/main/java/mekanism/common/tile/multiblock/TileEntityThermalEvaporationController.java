package mekanism.common.tile.multiblock;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.*;
import mekanism.common.Mekanism;
import mekanism.common.base.IActiveState;
import mekanism.common.base.ITankManager;
import mekanism.common.block.states.BlockStateBasic.BasicBlockType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.fluid.VariableCapacityFluidTank;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.capabilities.holder.slot.ProxiedInventorySlotHolder;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.slot.FluidInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.cache.CachedRecipe;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.cache.IRecipeLookupHandler;
import mekanism.common.recipe.cache.OneInputCachedRecipe;
import mekanism.common.recipe.cache.RecipeCacheLookupMonitor;
import mekanism.common.recipe.cache.inputs.InputHelper;
import mekanism.common.recipe.cache.outputs.OutputHelper;
import mekanism.common.recipe.inputs.FluidInput;
import mekanism.common.recipe.machines.ThermalEvaporationRecipe;
import mekanism.common.tile.TileEntityStructuralGlass;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TileUtils;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class TileEntityThermalEvaporationController extends TileEntityThermalEvaporationBlock implements IActiveState, ITankManager,
        IRecipeLookupHandler<ThermalEvaporationRecipe> {

    public static final int MAX_OUTPUT = 10000;
    public static final int MAX_HEIGHT = 18;
    private static final List<RecipeError> TRACKED_ERROR_TYPES = Arrays.asList(
          RecipeError.NOT_ENOUGH_INPUT,
          RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );
    private final RecipeCacheLookupMonitor<ThermalEvaporationRecipe> recipeCacheLookupMonitor = new RecipeCacheLookupMonitor<>(this);
    public VariableCapacityFluidTank inputTank = VariableCapacityFluidTank.input(this::getMaxFluid, fluid -> hasRecipe(fluid.getFluid()), recipeCacheLookupMonitor);
    public VariableCapacityFluidTank outputTank = VariableCapacityFluidTank.output(() -> MAX_OUTPUT, BasicFluidTank.alwaysTrue, this::onRecipeCacheContentsChanged);
    private FluidInventorySlot inputSlot;
    private OutputInventorySlot inputContainerSlot;
    private FluidInventorySlot outputSlot;
    private OutputInventorySlot outputContainerSlot;
    private ThermalEvaporationRecipe cachedRecipe;
    private int cachedRecipeVersion = -1;
    private int operatingTicks;
    private final boolean[] trackedErrors = new boolean[TRACKED_ERROR_TYPES.size()];

    public Set<Coord4D> tankParts = new ObjectOpenHashSet<>();
    public IEvaporationSolar[] solars = new IEvaporationSolar[4];

    public boolean temperatureSet = false;

    public float biomeTemp = 0;
    public float temperature = 0;
    public float heatToAbsorb = 0;

    public float lastGain = 0;

    public int height = 0;

    public boolean structured = false;
    public boolean controllerConflict = false;
    public boolean isLeftOnFace;
    public int renderY;

    public boolean updatedThisTick = false;

    public int clientSolarAmount;
    public boolean clientStructured;

    public boolean cacheStructure = false;

    public float prevScale;

    public float totalLoss = 0;

    @SideOnly(Side.CLIENT)
    private static final double THERMAL_FLUID_EDGE_MARGIN = 0.02D;
    @SideOnly(Side.CLIENT)
    private static final double THERMAL_ABOVE_VERTICAL_MARGIN = 0.05D;
    @SideOnly(Side.CLIENT)
    private static final double THERMAL_ABOVE_HORIZONTAL_MARGIN = 1.0D;
    @SideOnly(Side.CLIENT)
    private static final int THERMAL_GLASS_RAY_MAX_STEPS = 24;
    @SideOnly(Side.CLIENT)
    private static final double THERMAL_GLASS_SIDE_PROBE_EDGE_RATIO = 0.18D;
    @SideOnly(Side.CLIENT)
    private static final double THERMAL_GLASS_SIDE_PROBE_INSET = 0.01D;
    @SideOnly(Side.CLIENT)
    private static final double THERMAL_TOP_OPENING_MIN_EYE_HEIGHT = 0.05D;

    public TileEntityThermalEvaporationController() {
        super("ThermalEvaporationController");
        initializeInventorySlots();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        IContentsListener recipeCacheChangeListener = this::onRecipeCacheContentsChanged;
        builder.addSlot(inputSlot = FluidInventorySlot.fill(inputTank, recipeCacheChangeListener, 28, 20));
        builder.addSlot(inputContainerSlot = OutputInventorySlot.at(recipeCacheChangeListener, 28, 51));
        builder.addSlot(outputSlot = FluidInventorySlot.drain(outputTank, recipeCacheChangeListener, 152, 20));
        builder.addSlot(outputContainerSlot = OutputInventorySlot.at(recipeCacheChangeListener, 152, 51));
        IInventorySlotHolder slotHolder = builder.build();
        return ProxiedInventorySlotHolder.create(side -> getController() != null && slotHolder.canInsert(side),
              side -> getController() != null && slotHolder.canExtract(side),
              side -> side == null || getController() != null ? slotHolder.getInventorySlots(side) : Collections.emptyList());
    }

    private void onRecipeCacheContentsChanged() {
        onContentsChanged();
        recipeCacheLookupMonitor.onChange();
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        return null;
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        updatedThisTick = false;
        if (ticker == 5) {
            refresh();
        }
        if (structured) {
            updateTemperature();
        }

        manageBuckets();

        sanitizeStoredFluids();
        if (structured) {
            clampTanksToCapacity();
        }


        ThermalEvaporationRecipe recipe = getRecipe();
        if (recipe == null) {
            recipeCacheLookupMonitor.clear();
            lastGain = 0;
        } else {
            recipeCacheLookupMonitor.updateAndProcess();
        }
        if (structured) {
            if (Math.abs((float) inputTank.getFluidAmount() / inputTank.getCapacity() - prevScale) > 0.01) {
                Mekanism.packetHandler.sendUpdatePacket(this);
                prevScale = (float) inputTank.getFluidAmount() / inputTank.getCapacity();
            }
        }
    }

    public ThermalEvaporationRecipe getRecipe() {
        refreshRecipeLookupCache();
        FluidInput input = getInput();
        if (!input.isValid()) {
            cachedRecipe = null;
            return null;
        }
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getThermalEvaporationRecipe(input);
        }
        return cachedRecipe;
    }

    private void refreshRecipeLookupCache() {
        int recipeVersion = RecipeHandler.getGlobalRecipeVersion();
        if (cachedRecipeVersion != recipeVersion) {
            cachedRecipe = null;
            cachedRecipeVersion = recipeVersion;
        }
    }

    public FluidInput getInput() {
        return new FluidInput(inputTank.getFluid());
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        refresh();
    }

    @Override
    public void onNeighborChange(Block block) {
        super.onNeighborChange(block);
        refresh();
    }

    public boolean hasRecipe(Fluid fluid) {
        if (fluid == null) {
            return false;
        }
        return Recipe.THERMAL_EVAPORATION_PLANT.containsRecipe(fluid);
    }

    private void sanitizeStoredFluids() {
        sanitizeStoredFluid(inputTank);
        sanitizeStoredFluid(outputTank);
    }

    private void sanitizeStoredFluid(BasicFluidTank tank) {
        FluidStack stored = tank.getFluid();
        if (stored != null && (stored.amount <= 0 || stored.getFluid() == null)) {
            tank.setEmpty();
        }
    }

    private void clampTanksToCapacity() {
        clampTank(inputTank);
        clampTank(outputTank);
    }

    private void clampTank(BasicFluidTank tank) {
        FluidStack stored = tank.getFluid();
        if (stored != null) {
            tank.setStackSize(stored.amount, Action.EXECUTE);
        }
    }

    protected void refresh() {
        if (!isRemote()) {
            if (!updatedThisTick) {
                clearStructure();
                structured = buildStructure();
                if (structured != clientStructured) {
                    Mekanism.packetHandler.sendUpdatePacket(this);
                    clientStructured = structured;
                }

                if (structured) {
                    sanitizeStoredFluids();
                    clampTanksToCapacity();
                } else {
                    clearStructure();
                }
            }
        }
    }

    public boolean canOperate(ThermalEvaporationRecipe recipe) {
        if (!structured || height < 3 || height > MAX_HEIGHT || inputTank.getFluid() == null) {
            return false;
        }
        return recipe != null && recipe.canOperate(inputTank, outputTank);

    }

    @Override
    public int getSavedOperatingTicks(int cacheIndex) {
        return operatingTicks;
    }

    @Override
    public ThermalEvaporationRecipe getRecipe(int cacheIndex) {
        return getRecipe();
    }

    @Override
    public void onRecipeCacheInvalidated(int cacheIndex) {
        cachedRecipe = null;
        cachedRecipeVersion = RecipeHandler.getGlobalRecipeVersion();
    }

    @Override
    public CachedRecipe<ThermalEvaporationRecipe> createNewCachedRecipe(ThermalEvaporationRecipe recipe, int cacheIndex) {
        return new OneInputCachedRecipe<>(recipe, () -> false,
              InputHelper.getFluidInputHandler(inputTank, RecipeError.NOT_ENOUGH_INPUT),
              OutputHelper.getOutputHandler(outputTank, RecipeError.NOT_ENOUGH_OUTPUT_SPACE),
              () -> recipe.getInput().ingredient,
              input -> input != null && input.isFluidEqual(recipe.getInput().ingredient),
              input -> recipe.getOutput().output.copy(),
              input -> input == null || input.amount <= 0,
              output -> output == null || output.amount <= 0)
              .setCanHolderFunction(() -> structured && height >= 3 && height <= MAX_HEIGHT && getProductionRate() > 0)
              .setActive(this::setRecipeActive)
              .setRequiredTicks(this::getRecipeRequiredTicks)
              .setBaselineMaxOperations(this::getBaselineMaxOperations)
              .setOperatingTicksChanged(ticks -> operatingTicks = ticks)
              .setErrorsChanged(errors -> {
                  for (int i = 0; i < trackedErrors.length; i++) {
                      trackedErrors[i] = errors.contains(TRACKED_ERROR_TYPES.get(i));
                  }
              })
              .setOnFinish(this::markNoUpdateSync);
    }

    @Override
    public void clearRecipeErrors(int cacheIndex) {
        Arrays.fill(trackedErrors, false);
    }

    public boolean hasWarning(RecipeError error) {
        int errorIndex = TRACKED_ERROR_TYPES.indexOf(error);
        return errorIndex != -1 && trackedErrors[errorIndex];
    }

    public boolean hasWarningNoMatchingRecipe() {
        return hasWarning(RecipeError.NOT_ENOUGH_INPUT) || inputTank.getFluid() != null && getRecipe() == null;
    }

    public boolean hasWarningNoSpaceInOutput() {
        return hasWarning(RecipeError.NOT_ENOUGH_OUTPUT_SPACE);
    }

    public boolean hasWarningInputDoesntProduceOutput() {
        return hasWarning(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT);
    }

    private void setRecipeActive(boolean active) {
        if (active) {
            double productionRate = getProductionRate();
            if (productionRate > 0 && productionRate < 1) {
                lastGain = 1F / (float) Math.ceil(1 / productionRate);
            } else {
                lastGain = (float) productionRate;
            }
        } else {
            lastGain = 0;
        }
    }

    private int getRecipeRequiredTicks() {
        double productionRate = getProductionRate();
        return productionRate > 0 && productionRate < 1 ? (int) Math.ceil(1 / productionRate) : 1;
    }

    private int getBaselineMaxOperations() {
        double productionRate = getProductionRate();
        return productionRate > 0 && productionRate < 1 ? 1 : (int) productionRate;
    }

    private double getProductionRate() {
        return Math.max(0, getTemperature()) * MekanismConfig.current().general.evaporationTempMultiplier.val() * ((double) height / (double) MAX_HEIGHT);
    }

    private void manageBuckets() {
        if (outputSlot != null && outputContainerSlot != null) {
            outputSlot.drainTank(outputContainerSlot);
        }

        if (structured) {
            if (inputSlot != null && inputContainerSlot != null) {
                inputSlot.fillTank(inputContainerSlot);
            }
        }
    }

    private void updateTemperature() {
        if (!temperatureSet) {
            biomeTemp = world.getBiomeForCoordsBody(getPos()).getTemperature(getPos());
            temperatureSet = true;
        }
        heatToAbsorb += getActiveSolars() * MekanismConfig.current().general.evaporationSolarMultiplier.val();
        temperature += heatToAbsorb / (float) height;

        float biome = biomeTemp - 0.5F;
        float base = biome > 0 ? biome * 20 : biomeTemp * 40;

        if (Math.abs(temperature - base) < 0.001) {
            temperature = base;
        }
        float incr = (float) Math.sqrt(Math.abs(temperature - base)) * (float) MekanismConfig.current().general.evaporationHeatDissipation.val();

        if (temperature > base) {
            incr = -incr;
        }

        float prev = temperature;
        temperature = (float) Math.min(MekanismConfig.current().general.evaporationMaxTemp.val(), temperature + incr / (float) height);

        if (incr < 0) {
            totalLoss = prev - temperature;
        } else {
            totalLoss = 0;
        }
        heatToAbsorb = 0;
        MekanismUtils.saveChunk(this);
    }

    public float getTemperature() {
        return temperature;
    }

    public int getActiveSolars() {
        if (isRemote()) {
            return clientSolarAmount;
        }
        int ret = 0;
        for (IEvaporationSolar solar : solars) {
            if (solar != null && solar.canSeeSun()) {
                ret++;
            }
        }
        return ret;
    }

    public boolean buildStructure() {
        EnumFacing right = MekanismUtils.getRight(facing);
        EnumFacing left = MekanismUtils.getLeft(facing);
        height = 0;
        controllerConflict = false;
        updatedThisTick = true;

        Coord4D startPoint = Coord4D.get(this);
        while (startPoint.offset(EnumFacing.UP).getTileEntity(world) instanceof TileEntityThermalEvaporationBlock || (startPoint.offset(EnumFacing.UP).getTileEntity(world) instanceof TileEntityStructuralGlass && MekanismConfig.current().mekce.EnableGlassInThermal.val())) {
            startPoint = startPoint.offset(EnumFacing.UP);
        }

        Coord4D test = startPoint.offset(EnumFacing.DOWN).offset(right, 2);
        isLeftOnFace = test.getTileEntity(world) instanceof TileEntityThermalEvaporationBlock;
        startPoint = startPoint.offset(left, isLeftOnFace ? 1 : 2);
        if (!scanTopLayer(startPoint)) {
            return false;
        }

        height = 1;

        Coord4D middlePointer = startPoint.offset(EnumFacing.DOWN);
        while (scanLowerLayer(middlePointer)) {
            middlePointer = middlePointer.offset(EnumFacing.DOWN);
        }
        renderY = middlePointer.y + 1;
        if (height < 3 || height > MAX_HEIGHT) {
            height = 0;
            return false;
        }
        structured = true;
        markNoUpdateSync();
        return true;
    }

    public boolean scanTopLayer(Coord4D current) {
        EnumFacing right = MekanismUtils.getRight(facing);
        EnumFacing back = MekanismUtils.getBack(facing);
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                Coord4D pointer = current.offset(right, x).offset(back, z);
                TileEntity pointerTile = pointer.getTileEntity(world);
                int corner = getCorner(x, z);
                if (corner != -1) {
                    if (!addSolarPanel(pointer.getTileEntity(world), corner)) {
                        if (pointer.offset(EnumFacing.UP).getTileEntity(world) instanceof TileEntityThermalEvaporationBlock || (pointer.offset(EnumFacing.UP).getTileEntity(world) instanceof TileEntityStructuralGlass && MekanismConfig.current().mekce.EnableGlassInThermal.val()) || !addTankPart(pointerTile)) {
                            return false;
                        }
                    }
                } else if ((x == 1 || x == 2) && (z == 1 || z == 2)) {
                    if (!pointer.isAirBlock(world)) {
                        return false;
                    }
                } else if (pointer.offset(EnumFacing.UP).getTileEntity(world) instanceof TileEntityThermalEvaporationBlock || (pointer.offset(EnumFacing.UP).getTileEntity(world) instanceof TileEntityStructuralGlass && MekanismConfig.current().mekce.EnableGlassInThermal.val()) || !addTankPart(pointerTile)) {
                    return false;
                }
            }
        }
        return true;
    }

    public int getMaxFluid() {
        return height * 4 * 64000;
    }

    public int getCorner(int x, int z) {
        if (x == 0 && z == 0) {
            return 0;
        } else if (x == 0 && z == 3) {
            return 1;
        } else if (x == 3 && z == 0) {
            return 2;
        } else if (x == 3 && z == 3) {
            return 3;
        }
        return -1;
    }

    public boolean scanLowerLayer(Coord4D current) {
        EnumFacing right = MekanismUtils.getRight(facing);
        EnumFacing back = MekanismUtils.getBack(facing);
        boolean foundCenter = false;
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                Coord4D pointer = current.offset(right, x).offset(back, z);
                TileEntity pointerTile = pointer.getTileEntity(world);
                if ((x == 1 || x == 2) && (z == 1 || z == 2)) {
                    if (pointerTile instanceof TileEntityThermalEvaporationBlock) {
                        if (!foundCenter) {
                            if (x == 1 && z == 1) {
                                foundCenter = true;
                            } else {
                                height = -1;
                                return false;
                            }
                        }
                    } else if (foundCenter || !pointer.isAirBlock(world)) {
                        height = -1;
                        return false;
                    }
                } else if (!addTankPart(pointerTile)) {
                    height = -1;
                    return false;
                }
            }
        }

        height++;

        return !foundCenter;
    }

    public boolean addTankPart(TileEntity tile) {
        if (tile instanceof TileEntityThermalEvaporationBlock block && (tile == this || !(tile instanceof TileEntityThermalEvaporationController))) {
            if (tile != this) {
                block.addToStructure(Coord4D.get(this));
                tankParts.add(Coord4D.get(tile));
            }
            return true;
        } else if (tile instanceof TileEntityStructuralGlass glass && (tile == this || !(tile instanceof TileEntityThermalEvaporationController)) && MekanismConfig.current().mekce.EnableGlassInThermal.val()) {
            if (tile != this) {
                glass.setController(Coord4D.get(this));
                tankParts.add(Coord4D.get(tile));
            }
            return true;
        } else if (tile != this && tile instanceof TileEntityThermalEvaporationController) {
            controllerConflict = true;
        }
        return false;
    }

    public boolean addSolarPanel(TileEntity tile, int i) {
        if (tile != null && !tile.isInvalid() && CapabilityUtils.hasCapability(tile, Capabilities.EVAPORATION_SOLAR_CAPABILITY, EnumFacing.DOWN)) {
            solars[i] = CapabilityUtils.getCapability(tile, Capabilities.EVAPORATION_SOLAR_CAPABILITY, EnumFacing.DOWN);
            return true;
        }
        return false;
    }

    public int getScaledTempLevel(int i) {
        return (int) (i * Math.min(1, getTemperature() / MekanismConfig.current().general.evaporationMaxTemp.val()));
    }

    public Coord4D getRenderLocation() {
        if (!structured) {
            return null;
        }
        EnumFacing right = MekanismUtils.getRight(facing);
        Coord4D renderLocation = Coord4D.get(this).offset(right);
        renderLocation = isLeftOnFace ? renderLocation.offset(right) : renderLocation;
        renderLocation = renderLocation.offset(right.getOpposite()).offset(MekanismUtils.getBack(facing));
        renderLocation.y = renderY;
        renderLocation = switch (facing) {
            case SOUTH -> renderLocation.offset(EnumFacing.NORTH).offset(EnumFacing.WEST);
            case WEST -> renderLocation.offset(EnumFacing.NORTH);
            case EAST -> renderLocation.offset(EnumFacing.WEST);
            default -> renderLocation;
        };
        return renderLocation;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean shouldCullForOcclusion() {
        // Keep the controller TESR alive when internal fluid is actually visible via structural glass.
        if (MekanismConfig.current().client.GazeCullingTracking.val() && shouldRenderInternalFluid()) {
            return false;
        }
        return super.shouldCullForOcclusion();
    }

    @SideOnly(Side.CLIENT)
    public boolean shouldRenderInternalFluid() {
        if (!structured || world == null || inputTank.getFluid() == null || inputTank.getFluidAmount() <= 0 || height - 2 < 1) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return false;
        }
        Entity renderView = mc.getRenderViewEntity();
        if (renderView == null) {
            return false;
        }
        FluidViewBounds fluidBounds = getClientFluidBounds();
        if (fluidBounds == null) {
            return false;
        }
        Vec3d eyePos = renderView.getPositionEyes(1.0F);
        if (canSeeFluidFromTopOpening(fluidBounds, eyePos)) {
            return true;
        }
        if (!MekanismConfig.current().mekce.EnableGlassInThermal.val()) {
            return false;
        }
        return canSeeFluidThroughStructuralGlass(fluidBounds, eyePos);
    }

    @SideOnly(Side.CLIENT)
    private boolean isViewerDirectlyAbove(FluidViewBounds bounds, Vec3d eyePos) {
        double topLayerY = renderY + (height - 2);
        return eyePos.y >= topLayerY + 1D + THERMAL_ABOVE_VERTICAL_MARGIN
                && eyePos.x >= bounds.minX - THERMAL_ABOVE_HORIZONTAL_MARGIN && eyePos.x <= bounds.maxX + THERMAL_ABOVE_HORIZONTAL_MARGIN
                && eyePos.z >= bounds.minZ - THERMAL_ABOVE_HORIZONTAL_MARGIN && eyePos.z <= bounds.maxZ + THERMAL_ABOVE_HORIZONTAL_MARGIN;
    }

    @SideOnly(Side.CLIENT)
    private boolean canSeeFluidFromTopOpening(FluidViewBounds bounds, Vec3d eyePos) {
        double topLayerY = renderY + (height - 2);
        if (eyePos.y <= topLayerY + THERMAL_TOP_OPENING_MIN_EYE_HEIGHT) {
            return false;
        }
        if (isViewerDirectlyAbove(bounds, eyePos)) {
            return true;
        }
        List<Vec3d> probes = buildFluidProbePoints(bounds);
        for (Vec3d probe : probes) {
            if (canSeePointThroughTransparentPath(eyePos, probe)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    @SideOnly(Side.CLIENT)
    private FluidViewBounds getClientFluidBounds() {
        Coord4D renderLocation = getRenderLocation();
        if (renderLocation == null || inputTank.getFluid() == null || height - 2 < 1) {
            return null;
        }
        int maxFluid = getMaxFluid();
        if (maxFluid <= 0) {
            return null;
        }
        int innerHeight = height - 2;
        float scale = Math.min(1F, (float) inputTank.getFluidAmount() / (float) maxFluid);
        if (scale <= 0) {
            return null;
        }
        boolean gaseous = inputTank.getFluid().getFluid() != null && inputTank.getFluid().getFluid().isGaseous(inputTank.getFluid());
        double renderedFluidHeight = gaseous ? innerHeight : Math.max(0.02D, scale * innerHeight);
        double minX = renderLocation.x + THERMAL_FLUID_EDGE_MARGIN;
        double minY = renderLocation.y + THERMAL_FLUID_EDGE_MARGIN;
        double minZ = renderLocation.z + THERMAL_FLUID_EDGE_MARGIN;
        double maxX = renderLocation.x + 2D - THERMAL_FLUID_EDGE_MARGIN;
        double maxY = renderLocation.y + renderedFluidHeight - THERMAL_FLUID_EDGE_MARGIN;
        double maxZ = renderLocation.z + 2D - THERMAL_FLUID_EDGE_MARGIN;
        if (maxY <= minY) {
            maxY = minY + 0.02D;
        }
        return new FluidViewBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @SideOnly(Side.CLIENT)
    private boolean canSeeFluidThroughStructuralGlass(FluidViewBounds bounds, Vec3d eyePos) {
        List<Vec3d> probes = buildFluidProbePoints(bounds);
        for (Vec3d probe : probes) {
            if (canSeePointThroughStructuralGlass(eyePos, probe)) {
                return true;
            }
        }
        return false;
    }

    @SideOnly(Side.CLIENT)
    private List<Vec3d> buildFluidProbePoints(FluidViewBounds bounds) {
        double centerX = (bounds.minX + bounds.maxX) * 0.5D;
        double centerZ = (bounds.minZ + bounds.maxZ) * 0.5D;
        double[] xs = new double[]{bounds.minX, centerX, bounds.maxX};
        double[] zs = new double[]{bounds.minZ, centerZ, bounds.maxZ};
        double[] ys = new double[]{
                bounds.minY + (bounds.maxY - bounds.minY) * 0.15D,
                bounds.minY + (bounds.maxY - bounds.minY) * 0.5D,
                bounds.minY + (bounds.maxY - bounds.minY) * 0.85D
        };
        List<Vec3d> probes = new ArrayList<>(72);
        for (double y : ys) {
            for (double x : xs) {
                for (double z : zs) {
                    addFluidProbePoint(probes, bounds, x, y, z);
                }
            }
        }

        // Extra side probes near structural-glass faces reduce false negatives when the center line is blocked.
        double width = Math.max(0.01D, bounds.maxX - bounds.minX);
        double depth = Math.max(0.01D, bounds.maxZ - bounds.minZ);
        double edgeInsetX = Math.min(width * 0.45D, width * THERMAL_GLASS_SIDE_PROBE_EDGE_RATIO);
        double edgeInsetZ = Math.min(depth * 0.45D, depth * THERMAL_GLASS_SIDE_PROBE_EDGE_RATIO);
        double sideMinX = bounds.minX + edgeInsetX;
        double sideMaxX = bounds.maxX - edgeInsetX;
        double sideMinZ = bounds.minZ + edgeInsetZ;
        double sideMaxZ = bounds.maxZ - edgeInsetZ;
        double westProbeX = bounds.minX + THERMAL_GLASS_SIDE_PROBE_INSET;
        double eastProbeX = bounds.maxX - THERMAL_GLASS_SIDE_PROBE_INSET;
        double northProbeZ = bounds.minZ + THERMAL_GLASS_SIDE_PROBE_INSET;
        double southProbeZ = bounds.maxZ - THERMAL_GLASS_SIDE_PROBE_INSET;
        double[] sideYLevels = new double[]{
                bounds.minY + (bounds.maxY - bounds.minY) * 0.2D,
                bounds.minY + (bounds.maxY - bounds.minY) * 0.5D,
                bounds.minY + (bounds.maxY - bounds.minY) * 0.8D
        };
        for (double y : sideYLevels) {
            addFluidProbePoint(probes, bounds, westProbeX, y, sideMinZ);
            addFluidProbePoint(probes, bounds, westProbeX, y, centerZ);
            addFluidProbePoint(probes, bounds, westProbeX, y, sideMaxZ);
            addFluidProbePoint(probes, bounds, eastProbeX, y, sideMinZ);
            addFluidProbePoint(probes, bounds, eastProbeX, y, centerZ);
            addFluidProbePoint(probes, bounds, eastProbeX, y, sideMaxZ);

            addFluidProbePoint(probes, bounds, sideMinX, y, northProbeZ);
            addFluidProbePoint(probes, bounds, centerX, y, northProbeZ);
            addFluidProbePoint(probes, bounds, sideMaxX, y, northProbeZ);
            addFluidProbePoint(probes, bounds, sideMinX, y, southProbeZ);
            addFluidProbePoint(probes, bounds, centerX, y, southProbeZ);
            addFluidProbePoint(probes, bounds, sideMaxX, y, southProbeZ);
        }

        // Top-surface probes help when only a thin visible strip remains.
        double topY = bounds.maxY - THERMAL_GLASS_SIDE_PROBE_INSET;
        addFluidProbePoint(probes, bounds, sideMinX, topY, sideMinZ);
        addFluidProbePoint(probes, bounds, sideMinX, topY, centerZ);
        addFluidProbePoint(probes, bounds, sideMinX, topY, sideMaxZ);
        addFluidProbePoint(probes, bounds, centerX, topY, sideMinZ);
        addFluidProbePoint(probes, bounds, centerX, topY, centerZ);
        addFluidProbePoint(probes, bounds, centerX, topY, sideMaxZ);
        addFluidProbePoint(probes, bounds, sideMaxX, topY, sideMinZ);
        addFluidProbePoint(probes, bounds, sideMaxX, topY, centerZ);
        addFluidProbePoint(probes, bounds, sideMaxX, topY, sideMaxZ);
        return probes;
    }

    @SideOnly(Side.CLIENT)
    private void addFluidProbePoint(List<Vec3d> probes, FluidViewBounds bounds, double x, double y, double z) {
        double clampedX = clamp(x, bounds.minX, bounds.maxX);
        double clampedY = clamp(y, bounds.minY, bounds.maxY);
        double clampedZ = clamp(z, bounds.minZ, bounds.maxZ);
        probes.add(new Vec3d(clampedX, clampedY, clampedZ));
    }

    @SideOnly(Side.CLIENT)
    private double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    @SideOnly(Side.CLIENT)
    private boolean canSeePointThroughStructuralGlass(Vec3d eyePos, Vec3d target) {
        Vec3d start = eyePos;
        Vec3d direction = target.subtract(eyePos);
        double distanceSq = direction.lengthSquared();
        if (distanceSq <= 1.0E-8D) {
            return false;
        }
        Vec3d directionNorm = direction.scale(1.0D / Math.sqrt(distanceSq));
        boolean passedStructuralGlass = false;
        for (int i = 0; i < THERMAL_GLASS_RAY_MAX_STEPS; i++) {
            RayTraceResult trace = world.rayTraceBlocks(start, target, false, true, false);
            if (trace == null || trace.typeOfHit != RayTraceResult.Type.BLOCK) {
                return passedStructuralGlass;
            }
            BlockPos hitPos = trace.getBlockPos();
            IBlockState hitState = world.getBlockState(hitPos);
            if (isStructuralGlass(hitState)) {
                passedStructuralGlass = true;
            }
            if (!isTransparentForThermalRay(hitState)) {
                return false;
            }
            if (trace.hitVec == null) {
                return false;
            }
            start = trace.hitVec.add(directionNorm.scale(0.01D));
            if (start.squareDistanceTo(target) < 1.0E-6D) {
                return passedStructuralGlass;
            }
        }
        return passedStructuralGlass;
    }

    @SideOnly(Side.CLIENT)
    private boolean canSeePointThroughTransparentPath(Vec3d eyePos, Vec3d target) {
        Vec3d start = eyePos;
        Vec3d direction = target.subtract(eyePos);
        double distanceSq = direction.lengthSquared();
        if (distanceSq <= 1.0E-8D) {
            return true;
        }
        Vec3d directionNorm = direction.scale(1.0D / Math.sqrt(distanceSq));
        for (int i = 0; i < THERMAL_GLASS_RAY_MAX_STEPS; i++) {
            RayTraceResult trace = world.rayTraceBlocks(start, target, false, true, false);
            if (trace == null || trace.typeOfHit != RayTraceResult.Type.BLOCK) {
                return true;
            }
            BlockPos hitPos = trace.getBlockPos();
            IBlockState hitState = world.getBlockState(hitPos);
            if (!isTransparentForThermalRay(hitState)) {
                return false;
            }
            if (trace.hitVec == null) {
                return false;
            }
            start = trace.hitVec.add(directionNorm.scale(0.01D));
            if (start.squareDistanceTo(target) < 1.0E-6D) {
                return true;
            }
        }
        return true;
    }

    @SideOnly(Side.CLIENT)
    private boolean isStructuralGlass(IBlockState state) {
        return BasicBlockType.get(state) == BasicBlockType.STRUCTURAL_GLASS;
    }

    @SideOnly(Side.CLIENT)
    private boolean isTransparentForThermalRay(IBlockState state) {
        if (state.getMaterial().isLiquid()) {
            return true;
        }
        if (isStructuralGlass(state)) {
            return true;
        }
        if (!state.isFullCube()) {
            return true;
        }
        if (!state.isOpaqueCube()) {
            return true;
        }
        return state.getBlock().isTranslucent(state);
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            TileUtils.readTankData(dataStream, inputTank);
            TileUtils.readTankData(dataStream, outputTank);

            structured = dataStream.readBoolean();
            controllerConflict = dataStream.readBoolean();
            clientSolarAmount = dataStream.readInt();
            height = dataStream.readInt();
            temperature = dataStream.readFloat();
            biomeTemp = dataStream.readFloat();
            isLeftOnFace = dataStream.readBoolean();
            lastGain = dataStream.readFloat();
            totalLoss = dataStream.readFloat();
            renderY = dataStream.readInt();

            if (structured != clientStructured) {
                MekanismUtils.updateBlock(world, getPos());
                if (structured) {
                    // Calculate the two corners of the evap tower using the render location as basis (which is the
                    // lowest rightmost corner inside the tower, relative to the controller).
                    BlockPos corner1 = getRenderLocation().getPos().offset(EnumFacing.WEST).offset(EnumFacing.NORTH).down();
                    BlockPos corner2 = corner1.offset(EnumFacing.EAST, 3).offset(EnumFacing.SOUTH, 3).up(height - 1);
                    // Use the corners to spin up the sparkle
                    Mekanism.proxy.doMultiblockSparkle(this, corner1, corner2, tile -> tile instanceof TileEntityThermalEvaporationBlock);
                }
                clientStructured = structured;
            }
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        TileUtils.addTankData(data, inputTank);
        TileUtils.addTankData(data, outputTank);
        data.add(structured);
        data.add(controllerConflict);
        data.add(getActiveSolars());
        data.add(height);
        data.add(temperature);
        data.add(biomeTemp);
        data.add(isLeftOnFace);
        data.add(lastGain);
        data.add(totalLoss);
        data.add(renderY);
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        inputTank.readFromNBT(nbtTags.getCompoundTag("waterTank"));
        outputTank.readFromNBT(nbtTags.getCompoundTag("brineTank"));
        sanitizeStoredFluids();
        clampTanksToCapacity();

        temperature = nbtTags.getFloat("temperature");

        operatingTicks = nbtTags.getInteger("operatingTicks");
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setTag("waterTank", inputTank.writeToNBT(new NBTTagCompound()));
        nbtTags.setTag("brineTank", outputTank.writeToNBT(new NBTTagCompound()));

        nbtTags.setFloat("temperature", temperature);

        nbtTags.setInteger("operatingTicks", operatingTicks);
    }

    @Override
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return facing != EnumFacing.DOWN && facing != EnumFacing.UP;
    }

    @Override
    public TileEntityThermalEvaporationController getController() {
        return structured ? this : null;
    }

    public void clearStructure() {
        tankParts.forEach( tankPart-> {
            TileEntity tile = tankPart.getTileEntity(world);
            if (tile instanceof TileEntityThermalEvaporationBlock tiles) {
                tiles.controllerGone();
            }
        });
        tankParts.clear();
        solars = new IEvaporationSolar[]{null, null, null, null};
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }

    @Override
    public boolean getActive() {
        return structured;
    }

    @Override
    public void setActive(boolean active) {
    }

    @Override
    public boolean renderUpdate() {
        return true;
    }

    @Override
    public boolean lightUpdate() {
        return false;
    }

    @Override
    public Object[] getManagedTanks() {
        return new Object[]{inputTank, outputTank};
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return getInventorySlotIdsForSide(side);
    }

    @SideOnly(Side.CLIENT)
    private static class FluidViewBounds {

        private final double minX;
        private final double minY;
        private final double minZ;
        private final double maxX;
        private final double maxY;
        private final double maxZ;

        private FluidViewBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }
    }
}
