package mekanism.generators.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.TileNetworkList;
import mekanism.common.base.ISpecialSelectionWireframeTile;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.util.MekanismUtils;
import micdoodle8.mods.galacticraft.api.world.ISolarLevel;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

public class TileEntitySolarGenerator extends TileEntityGenerator implements ISpecialSelectionWireframeTile {

    private static final String[] methods = new String[]{"getEnergy", "getOutput", "getMaxEnergy", "getEnergyNeeded", "getSeesSun"};

    private boolean seesSun;
    private boolean needsRainCheck = true;
    private float peakOutput;
    private double productionThisTick;
    private EnergyInventorySlot energySlot;

    public TileEntitySolarGenerator() {
        this("SolarGenerator", MekanismConfig.current().generators.solarGeneratorStorage.val(), MekanismConfig.current().generators.solarGeneration.val() * 2);
    }

    public TileEntitySolarGenerator(String name, double maxEnergy, double output) {
        super("solar", name, maxEnergy, output);
        initializeInventorySlots();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        energySlot = builder.addSlot(EnergyInventorySlot.drain(this, listener, 143, 35));
        return builder.build();
    }

    public boolean canSeeSun() {
        return seesSun;
    }

    @Override
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return super.canSetFacing(facing);
    }


    @Override
    public void validate() {
        super.validate();
        Biome b = world.provider.getBiomeForCoords(getPos());

        // Consider the best temperature to be 0.8; biomes that are higher than that
        // will suffer an efficiency loss (semiconductors don't like heat); biomes that are cooler
        // get a boost. We scale the efficiency to around 30% so that it doesn't totally dominate
        float tempEff = 0.3f * (0.8f - b.getTemperature(getPos()));

        // Treat rainfall as a proxy for humidity; any humidity works as a drag on overall efficiency.
        // As with temperature, we scale it so that it doesn't overwhelm production. Note the signedness
        // on the scaling factor. Also note that we only use rainfall as a proxy if it CAN rain; some dimensions
        // (like the End) have rainfall set, but can't actually support rain.
        float humidityEff = -0.3f * (b.canRain() ? b.getRainfall() : 0.0f);

        peakOutput = getConfiguredMax() * (1.0f + tempEff + humidityEff);
        needsRainCheck = b.canRain();
    }

    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        energySlot.drainContainer();
        if (canOperate()) {
            setActive(true);
            getEnergyContainer().insert(productionThisTick, Action.EXECUTE, AutomationType.INTERNAL);
        } else {
            setActive(false);
        }
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        seesSun = world.isDaytime() && canSeeSky() && !world.provider.isNether();
        productionThisTick = getProduction();
    }

    protected boolean canSeeSky() {
        return world.canSeeSky(getPos());
    }

    @Override
    public boolean canExtractItem(int slotID, @Nonnull ItemStack itemstack, @Nonnull EnumFacing side) {
        if (slotID == 0) {
            return EnergyInventorySlot.drainExtractCheck(this, itemstack);
        }
        return false;
    }

    @Override
    public boolean canOperate() {
        return seesSun && MekanismUtils.canFunction(this) && getEnergyContainer().getNeeded() > 0;
    }

    public double getProduction() {
        // Get the brightness of the sun; note that there are some implementations that depend on the base
        // brightness function which doesn't take into account the fact that rain can't occur in some biomes.
        float brightness = world.getSunBrightnessFactor(1.0f);
        if (MekanismUtils.existsAndInstance(world.provider, "micdoodle8.mods.galacticraft.api.world.ISolarLevel")) {
            brightness *= ((ISolarLevel) world.provider).getSolarEnergyMultiplier();
        }

        // Production is a function of the peak possible output in this biome and sun's current brightness
        float production = peakOutput * brightness;

        // If the generator is in a biome where it can rain and it's raining penalize production by 80%
        if (needsRainCheck && (world.isRaining() || world.isThundering())) {
            production *= 0.2;
        }
        return production;
    }

    public String getEfficiencyStr() {
        return String.format("%2.0f", (getProduction() / getMaxOutput()) * 100);
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        return switch (method) {
            case 0 -> new Object[]{electricityStored};
            case 1 -> new Object[]{output};
            case 2 -> new Object[]{BASE_MAX_ENERGY};
            case 3 -> new Object[]{BASE_MAX_ENERGY - electricityStored.get()};
            case 4 -> new Object[]{seesSun};
            default -> throw new NoSuchMethodException();
        };
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            seesSun = dataStream.readBoolean();
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(seesSun);
        return data;
    }

    @Override
    public boolean sideIsOutput(EnumFacing side) {
        return side == EnumFacing.DOWN;
    }

    @Override
    public boolean renderUpdate() {
        return false;
    }

    @Override
    public boolean lightUpdate() {
        return false;
    }

    protected float getConfiguredMax() {
        return (float) MekanismConfig.current().generators.solarGeneration.val();
    }

    @Override
    public double getMaxOutput() {
        return peakOutput;
    }
@Override
    @SideOnly(Side.CLIENT)
    public Class<?> getSelectionWireframeModelClass() {
        return mekanism.generators.client.model.ModelSolarGenerator.class;
    }

    @Override
    public boolean shouldApplyDefaultSelectionWireframeFacingRotation(IBlockState state, IBlockAccess world, BlockPos pos) {
        return false;
    }
}
