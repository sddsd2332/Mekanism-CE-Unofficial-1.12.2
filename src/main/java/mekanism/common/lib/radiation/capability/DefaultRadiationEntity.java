package mekanism.common.lib.radiation.capability;

import mekanism.api.NBTConstants;
import mekanism.api.radiation.capability.IRadiationEntity;
import mekanism.common.Mekanism;
import mekanism.common.MekanismDamageSource;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.lib.radiation.RadiationManager;
import mekanism.common.lib.radiation.RadiationUtil;
import mekanism.common.util.MekanismUtils;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.Random;

public class DefaultRadiationEntity implements IRadiationEntity {

    private double radiation = RadiationManager.BASELINE;

    @Override
    public double getRadiation() {
        return radiation;
    }

    @Override
    public void radiate(double magnitude) {
        radiation = RadiationUtil.addClamped(radiation, magnitude);
    }

    @Override
    public void update(@Nonnull EntityLivingBase entity) {
        if (entity instanceof EntityPlayer player && !MekanismUtils.isPlayingMode(player)) {
            return;
        }

        Random rand = entity.world.rand;
        double minSeverity = MekanismConfig.current().general.radiationNegativeEffectsMinSeverity.val();
        double severityScale = RadiationManager.RadiationScale.getScaledDoseSeverity(radiation);
        double chance = minSeverity + rand.nextDouble() * (1 - minSeverity);

        float strength = 0;
        if (severityScale > chance) {
            //Calculate effect strength based on radiation severity
            strength = Math.max(1, (float) Math.log1p(radiation));
            //Hurt randomly
            if (rand.nextBoolean()) {
                entity.attackEntityFrom(MekanismDamageSource.RADIATION, strength);
            }
        }

        if (entity instanceof EntityPlayerMP player) {
            if (strength > 0) {
                player.getFoodStats().addExhaustion(strength);
            }
        }
    }

    @Override
    public void set(double magnitude) {
        radiation = RadiationUtil.sanitizeAtLeastBaseline(magnitude);
    }

    @Override
    public void decay() {
        radiation = RadiationUtil.sanitizeAtLeastBaseline(radiation * MekanismConfig.current().general.radiationTargetDecayRate.val());
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound ret = new NBTTagCompound();
        ret.setDouble(NBTConstants.RADIATION, radiation);
        return ret;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        set(nbt.getDouble(NBTConstants.RADIATION));
    }

    public static void register() {
        CapabilityManager.INSTANCE.register(IRadiationEntity.class, new Capability.IStorage<>() {
            @Override
            public NBTTagCompound writeNBT(Capability<IRadiationEntity> capability, IRadiationEntity instance, EnumFacing side) {
                return instance.serializeNBT();
            }

            @Override
            public void readNBT(Capability<IRadiationEntity> capability, IRadiationEntity instance, EnumFacing side, NBTBase nbt) {
                if (nbt instanceof NBTTagCompound tag) {
                    instance.deserializeNBT(tag);
                }
            }
        }, DefaultRadiationEntity::new);
    }

    public static class Provider implements ICapabilitySerializable<NBTTagCompound> {

        public static final ResourceLocation NAME = Mekanism.rl(NBTConstants.RADIATION);
        private final IRadiationEntity defaultImpl = new DefaultRadiationEntity();


        @Override
        public boolean hasCapability(@NotNull Capability<?> capability, @Nullable EnumFacing facing) {
            return capability == Capabilities.RADIATION_ENTITY_CAPABILITY;
        }

        @Override
        public <T> T getCapability(Capability<T> capability, EnumFacing side) {
            if (capability == Capabilities.RADIATION_ENTITY_CAPABILITY) {
                return Capabilities.RADIATION_ENTITY_CAPABILITY.cast(defaultImpl);
            }
            return null;
        }


        @Override
        public NBTTagCompound serializeNBT() {
            return defaultImpl.serializeNBT();
        }

        @Override
        public void deserializeNBT(NBTTagCompound nbt) {
            defaultImpl.deserializeNBT(nbt);
        }
    }
}
