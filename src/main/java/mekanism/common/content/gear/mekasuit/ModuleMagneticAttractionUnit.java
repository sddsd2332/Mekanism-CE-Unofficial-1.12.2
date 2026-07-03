package mekanism.common.content.gear.mekasuit;

import mekanism.api.Pos3D;
import mekanism.api.annotations.ParametersAreNotNullByDefault;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.gear.ICustomModule;
import mekanism.api.gear.IModule;
import mekanism.api.gear.config.IModuleConfigItem;
import mekanism.api.gear.config.ModuleConfigItemCreator;
import mekanism.api.gear.config.ModuleEnumData;
import mekanism.api.text.IHasTextComponent;
import mekanism.api.text.TextComponentGroup;
import mekanism.common.MekanismLang;
import mekanism.common.config.MekanismConfig;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;

import java.util.List;

@ParametersAreNotNullByDefault
public class ModuleMagneticAttractionUnit implements ICustomModule<ModuleMagneticAttractionUnit> {

    private IModuleConfigItem<Range> range;

    @Override
    public void init(IModule<ModuleMagneticAttractionUnit> module, ModuleConfigItemCreator configItemCreator) {
        range = configItemCreator.createConfigItem("range", MekanismLang.MODULE_RANGE, new ModuleEnumData<>(Range.LOW, module.getInstalledCount() + 1));
    }

    @Override
    public void tickServer(IModule<ModuleMagneticAttractionUnit> module, EntityPlayer player) {
        if (range.get() != Range.OFF) {
            float size = 4 + range.get().getRange();
            double usage = MekanismConfig.current().meka.mekaSuitEnergyUsageItemAttraction.val() * range.get().getRange();
            boolean free = usage == 0 || player.isCreative();
            IEnergyContainer energyContainer = free ? null : module.getEnergyContainer();
            if (free || (energyContainer != null && energyContainer.getEnergy() >= usage)) {
                List<EntityItem> items = player.world.getEntitiesWithinAABB(EntityItem.class, player.getEntityBoundingBox().grow(size, size, size), item -> !item.cannotPickup());
                for (EntityItem item : items) {
                    if (item.getDistance(player) > 0.001) {
                        if (free) {
                            pullItem(player, item);
                        } else if (module.useEnergy(player, energyContainer, usage, true) == 0) {
                            //If we can't actually extract energy, exit
                            break;
                        } else {
                            pullItem(player, item);
                            if (energyContainer.getEnergy() < usage) {
                                //If after using energy, our energy is now smaller than how much we need to use, exit
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private void pullItem(EntityPlayer player, EntityItem item) {
        Vec3d diff = new Pos3D(player).subtract(new Pos3D(item));
        Vec3d motionNeeded = new Vec3d(Math.min(diff.x, 1), Math.min(diff.y, 1), Math.min(diff.z, 1));
        Vec3d motionDiff = motionNeeded.subtract(new Vec3d(player.motionX, player.motionY, player.motionZ));
        Vec3d itemMotion = motionDiff.scale(0.2);
        item.motionX = itemMotion.x;
        item.motionY = itemMotion.y;
        item.motionZ = itemMotion.z;
    }

    public enum Range implements IHasTextComponent {
        OFF(0),
        LOW(1F),
        MED(3F),
        HIGH(5),
        ULTRA(10);

        private final float range;
        private final ITextComponent label;

        Range(float boost) {
            this.range = boost;
            this.label = new TextComponentGroup().getString(Float.toString(boost));
        }

        @Override
        public ITextComponent getTextComponent() {
            return label;
        }

        public float getRange() {
            return range;
        }
    }
}
