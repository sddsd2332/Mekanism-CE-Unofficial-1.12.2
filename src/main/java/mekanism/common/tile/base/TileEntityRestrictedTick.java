package mekanism.common.tile.base;

import mekanism.api.MekanismAPI;
import mekanism.api.NBTConstants;
import mekanism.api.gas.IGasHandler;
import mekanism.common.tile.interfaces.ITileRadioactive;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ITickable;


/**
 * 限制性TICK，防止被各种Tick玩意加速
 */
public abstract class TileEntityRestrictedTick extends TileEntitySynchronized implements ITickable, ITileRadioactive {

    protected int ticksExisted = 0;
    private long lastUpdateWorldTick = -1;
    private float radiationScale;
    private boolean suppressRadiationOnInvalidate;

    @Override
    public final void update() {
        long currentTick = getWorld().getTotalWorldTime();
        if (lastUpdateWorldTick == currentTick) {
            return;
        }
        lastUpdateWorldTick = currentTick;
        doRestrictedTick();
        ticksExisted++;
    }

    public abstract void doRestrictedTick();

    protected boolean shouldDumpRadiation() {
        return false;
    }

    /**
     * Prevents this tile from dumping radioactive contents when invalidated as part of an in-place tier upgrade.
     */
    public final void suppressRadiationForUpgrade() {
        suppressRadiationOnInvalidate = true;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (!suppressRadiationOnInvalidate && !isRemote() && MekanismAPI.getRadiationManager().isRadiationEnabled() && shouldDumpRadiation()) {
            if (this instanceof IGasHandler handler) {
                //If we are on a server and radiation is enabled dump all gas tanks with radioactive materials
                // Note: we handle clearing radioactive contents later in drop calculation due to when things are written to NBT
                MekanismAPI.getRadiationManager().dumpRadiation(world, pos, handler, false);
            }
        }
    }

    @Override
    public float getRadiationScale() {
        if (!MekanismAPI.getRadiationManager().isRadiationEnabled()) {
            return 0;
        }
        if (this instanceof IGasHandler handler) {
            return ITileRadioactive.calculateRadiationScale(handler, this, getPos());
        }
        return radiationScale;
    }

    @Override
    public boolean hasRadioactiveContents() {
        return this instanceof IGasHandler handler && ITileRadioactive.hasRadioactiveContents(handler);
    }

    @Override
    public void readCustomNBT(NBTTagCompound compound) {
        super.readCustomNBT(compound);
        radiationScale = compound.getFloat(NBTConstants.RADIATION);
    }

    @Override
    public void writeCustomNBT(NBTTagCompound compound) {
        super.writeCustomNBT(compound);
        compound.setFloat(NBTConstants.RADIATION, radiationScale);
    }
}
