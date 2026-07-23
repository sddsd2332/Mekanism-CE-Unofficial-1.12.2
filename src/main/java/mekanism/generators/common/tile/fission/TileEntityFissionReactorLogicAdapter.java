package mekanism.generators.common.tile.fission;

import io.netty.buffer.ByteBuf;
import mekanism.api.EnumColor;
import mekanism.api.IConfigurable;
import mekanism.api.TileNetworkList;
import mekanism.common.Mekanism;
import mekanism.common.base.IComparatorSupport;
import mekanism.common.util.LangUtils;
import mekanism.generators.common.MekanismGenerators;
import mekanism.generators.common.content.fission.SynchronizedFissionData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;

public class TileEntityFissionReactorLogicAdapter extends TileEntityFissionReactorCasing implements IConfigurable, IComparatorSupport {

    private LogicMode logicMode = LogicMode.DISABLED;
    private int currentRedstoneLevel;

    public TileEntityFissionReactorLogicAdapter() {
        super("FissionReactorLogicAdapter");
        initializeContainerHolders();
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            updateComparatorOutputLevelSync();
            world.notifyNeighborsOfStateChange(getPos(), getBlockType(), true);
            currentRedstoneLevel = newRedstoneLevel;
        }
    }

    @Override
    public void onPowerChange() {
        super.onPowerChange();
        if (!isRemote()) {
            applyActivationLogic();
        }
    }

    @Override
    public int getRedstoneLevel() {
        return shouldOutput() ? 15 : 0;
    }

    private boolean shouldOutput() {
        if (structure == null) {
            return false;
        }
        return switch (logicMode) {
            case DISABLED -> false;
            case ACTIVATION -> redstone;
            case TEMPERATURE -> structure.getTemperature() >= SynchronizedFissionData.MIN_DAMAGE_TEMPERATURE;
            case EXCESS_WASTE -> structure.wasteTank.getNeeded() == 0;
            case DAMAGED -> structure.reactorDamage >= SynchronizedFissionData.MAX_DAMAGE;
            case DEPLETED -> structure.fuelTank.getStored() == 0;
        };
    }

    @Override
    public EnumActionResult onSneakRightClick(EntityPlayer player, EnumFacing side) {
        if (!isRemote()) {
            logicMode = logicMode.next();
            player.sendMessage(new TextComponentString(EnumColor.DARK_BLUE + Mekanism.LOG_TAG + " " + EnumColor.GREY +
                    LangUtils.localize("fission.logic.mode") + ": " + EnumColor.AQUA + logicMode.getLabel()));
            applyActivationLogic();
            Mekanism.packetHandler.sendUpdatePacket(this);
            markNoUpdateSync();
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public EnumActionResult onRightClick(EntityPlayer player, EnumFacing side) {
        return EnumActionResult.PASS;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        logicMode = LogicMode.byIndex(nbtTags.getInteger("fissionLogicMode"));
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("fissionLogicMode", logicMode.ordinal());
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(logicMode.ordinal());
        return data;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            int type = dataStream.readInt();
            if (type == 0) {
                logicMode = LogicMode.byIndex(dataStream.readInt());
                applyActivationLogic();
                Mekanism.packetHandler.sendUpdatePacket(this);
                markNoUpdateSync();
            }
            return;
        }

        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            logicMode = LogicMode.byIndex(dataStream.readInt());
        }
    }

    @Override
    protected boolean shouldDumpRadiation() {
        return true;
    }

    @Override
    public boolean onActivate(EntityPlayer player, EnumHand hand, ItemStack stack) {
        if (!isRemote()) {
            Mekanism.packetHandler.sendUpdatePacket(this);
            player.openGui(MekanismGenerators.instance, 18, world, getPos().getX(), getPos().getY(), getPos().getZ());
            return true;
        }
        return false;
    }

    private void applyActivationLogic() {
        if (structure != null && logicMode == LogicMode.ACTIVATION) {
            structure.active = redstone && !structure.isForceDisabled();
        }
    }

    public LogicMode getLogicMode() {
        return logicMode;
    }

    public String getStatusTranslationKey() {
        if (logicMode == LogicMode.ACTIVATION && redstone) {
            return "fission.logic.status.powered";
        }
        return shouldOutput() ? "gui.outputting" : "gui.idle";
    }

    public enum LogicMode {
        DISABLED("fission.logic.mode.disabled", "fission.logic.mode.disabled.desc", new ItemStack(Items.GUNPOWDER)),
        ACTIVATION("fission.logic.mode.activation", "fission.logic.mode.activation.desc", new ItemStack(Items.FLINT_AND_STEEL)),
        TEMPERATURE("fission.logic.mode.temperature", "fission.logic.mode.temperature.desc", new ItemStack(Items.REDSTONE)),
        EXCESS_WASTE("fission.logic.mode.excess_waste", "fission.logic.mode.excess_waste.desc", new ItemStack(Items.REDSTONE)),
        DAMAGED("fission.logic.mode.damaged", "fission.logic.mode.damaged.desc", new ItemStack(Items.REDSTONE)),
        DEPLETED("fission.logic.mode.depleted", "fission.logic.mode.depleted.desc", new ItemStack(Items.REDSTONE));

        private static final LogicMode[] MODES = values();

        private final String key;
        private final String descriptionKey;
        private final ItemStack renderStack;

        LogicMode(String key, String descriptionKey, ItemStack renderStack) {
            this.key = key;
            this.descriptionKey = descriptionKey;
            this.renderStack = renderStack;
        }

        public String getLabel() {
            return LangUtils.localize(key);
        }

        public String getDescription() {
            return LangUtils.localize(descriptionKey);
        }

        public ItemStack getRenderStack() {
            return renderStack;
        }

        public LogicMode next() {
            return MODES[(ordinal() + 1) % MODES.length];
        }

        public static LogicMode byIndex(int index) {
            if (index < 0 || index >= MODES.length) {
                return DISABLED;
            }
            return MODES[index];
        }
    }
}
