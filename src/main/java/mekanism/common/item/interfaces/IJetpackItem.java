package mekanism.common.item.interfaces;

import baubles.api.BaublesApi;
import mekanism.api.EnumColor;
import mekanism.api.IIncrementalEnum;
import mekanism.api.math.MathUtils;
import mekanism.api.text.IHasTextComponent;
import mekanism.api.text.TextComponentGroup;
import mekanism.common.CommonPlayerTickHandler;
import mekanism.common.Mekanism;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public interface IJetpackItem {

    boolean canUseJetpack(ItemStack stack);

    JetpackMode getJetpackMode(ItemStack stack);

    void useJetpackFuel(ItemStack stack);

    double getJetpackThrust(ItemStack stack);

    enum JetpackMode implements IIncrementalEnum<JetpackMode>, IHasTextComponent {
        NORMAL("tooltip.jetpack.regular", EnumColor.DARK_GREEN, "jetpack_normal.png"),
        HOVER("tooltip.jetpack.hover", EnumColor.DARK_AQUA, "jetpack_hover.png"),
        VECTOR("tooltip.jetpack.vector", EnumColor.ORANGE, "jetpack_vector.png"),
        DISABLED("tooltip.jetpack.disabled", EnumColor.DARK_RED, "jetpack_off.png");

        private static final JetpackMode[] MODES = values();
        private String unlocalized;
        private EnumColor color;
        private final ResourceLocation hudIcon;

        JetpackMode(String s, EnumColor c, String hudIcon) {
            unlocalized = s;
            color = c;
            this.hudIcon = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_HUD, hudIcon);
        }


        public String getName() {
            return color + LangUtils.localize(unlocalized);
        }

        @Override
        public JetpackMode byIndex(int index) {
            return byIndexStatic(index);
        }

        public static JetpackMode byIndexStatic(int index) {
            return MathUtils.getByIndexMod(MODES, index);
        }

        public ResourceLocation getHUDIcon() {
            return hudIcon;
        }

        @Override
        public ITextComponent getTextComponent() {
            return new TextComponentGroup().string(getName());
        }
    }

    /**
     * Gets the first found active jetpack from an entity, if one is worn.
     * <br>
     * If Curios is loaded, the curio slots will be checked as well.
     *
     * @param entity the entity on which to look for the jetpack
     * @return the jetpack stack if present, otherwise an empty stack
     */
    @NotNull
    static ItemStack getActiveJetpack(EntityLivingBase entity) {
        if (entity.isRiding()) {
            return ItemStack.EMPTY;
        }
        ItemStack jetpack = getJetpack(entity, stack -> stack.getItem() instanceof IJetpackItem jetpackItem && jetpackItem.canUseJetpack(stack));
        if (entity instanceof EntityPlayer player && player.getCooldownTracker().hasCooldown(jetpack.getItem())) {
            return ItemStack.EMPTY;
        }
        return jetpack;
    }


    /**
     * Gets the first found jetpack from an entity, if one is worn. Purpose of this is to get the correct jetpack mode to use.
     * <br>
     * If Curios is loaded, the curio slots will be checked as well.
     *
     * @param entity the entity on which to look for the jetpack
     * @return the jetpack stack if present, otherwise an empty stack
     */
    @NotNull
    static ItemStack getPrimaryJetpack(EntityLivingBase entity) {
        return getJetpack(entity, stack -> stack.getItem() instanceof IJetpackItem);
    }


    static ItemStack getJetpack(EntityLivingBase entity, Predicate<ItemStack> matcher) {
        ItemStack chest = entity.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        if (matcher.test(chest)) {
            return chest;
        } else if (Mekanism.hooks.Baubles) {
            return getBaublesJetpack(entity, matcher);
        }
        return ItemStack.EMPTY;
    }

    @Optional.Method(modid = MekanismHooks.Baubles_MOD_ID)
    static ItemStack getBaublesJetpack(EntityLivingBase base, Predicate<ItemStack> matcher) {
        if (base instanceof EntityPlayer player) {
            IItemHandler baubles = BaublesApi.getBaublesHandler(player);
            for (int i = 0; i < baubles.getSlots(); i++) {
                ItemStack stack = baubles.getStackInSlot(i);
                if (matcher.test(stack)) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * @return If fall distance should get reset or not
     */
    static <PLAYER extends EntityPlayer> boolean handleJetpackMotion(PLAYER player, JetpackMode mode, double thrust, Predicate<PLAYER> ascendingCheck) {
        Vec3d motion = new Vec3d(player.motionX, player.motionY, player.motionZ);
        if (mode == JetpackMode.VECTOR && player.isSneaking()) {
            //TODO: Do we want to expand holding shift to some sort of secondary behavior
            mode = JetpackMode.NORMAL;
        }
        if ((mode == JetpackMode.NORMAL || mode == JetpackMode.VECTOR) && player.isElytraFlying()) {
            Vec3d forward = player.getLookVec();
            Vec3d drag = forward.scale(1.5).subtract(motion).scale(0.5);
            Vec3d delta = forward.scale(thrust).add(drag);
            player.motionX += delta.x;
            player.motionY += delta.y;
            player.motionZ += delta.z;
            return false;
        } else if (mode == JetpackMode.NORMAL) {
            Vec3d delta = new Vec3d(0.08 * motion.x, thrust * getVerticalCoefficient(motion.y), 0.08 * motion.z);
            player.motionX += delta.x;
            player.motionY += delta.y;
            player.motionZ += delta.z;
        } else if (mode == JetpackMode.VECTOR) {
            Vec3d thrustVec = getUpVector(player, 1F).scale(thrust);
            Vec3d delta = new Vec3d(thrustVec.x, thrustVec.y * getVerticalCoefficient(motion.y), thrustVec.z);
            player.motionX += delta.x;
            player.motionY += delta.y;
            player.motionZ += delta.z;
        } else if (mode == JetpackMode.HOVER) {
            boolean ascending = ascendingCheck.test(player);
            boolean descending = player.isSneaking();
            if (ascending == descending) {
                if (player.motionY > 0) {
                    player.motionY = Math.max(motion.y - thrust, 0);
                } else if (player.motionY < 0) {
                    if (!CommonPlayerTickHandler.isOnGroundOrSleeping(player)) {
                        player.motionY = Math.min(motion.y + thrust, 0);
                    }
                }
            } else if (ascending) {
                player.motionY = Math.min(motion.y + thrust, 2 * thrust);
            } else if (!CommonPlayerTickHandler.isOnGroundOrSleeping(player)) {
                player.motionY = Math.max(motion.y - thrust, -2 * thrust);
            }
        }
        return true;
    }

    static double getVerticalCoefficient(double currentYVelocity) {
        return Math.min(1, Math.exp(-currentYVelocity));
    }

    static <PLAYER extends EntityPlayer> JetpackMode getPlayerJetpackMode(PLAYER player, JetpackMode mode, Predicate<PLAYER> ascendingCheck) {
        if (!player.isSpectator()) {
            if (mode != JetpackMode.DISABLED) {
                boolean ascending = ascendingCheck.test(player);
                if (mode == JetpackMode.HOVER) {
                    if (ascending && !player.isSneaking() || !CommonPlayerTickHandler.isOnGroundOrSleeping(player)) {
                        return mode;
                    }
                } else if (ascending) {
                    return mode;
                }
            }
        }
        return JetpackMode.DISABLED;
    }


    static Vec3d getUpVector(EntityPlayer player, float pPartialTicks) {
        return calculateUpVector(getViewXRot(player, pPartialTicks), getViewYRot(player, pPartialTicks));
    }

    static float getViewXRot(EntityPlayer player, float pPartialTicks) {
        return pPartialTicks == 1.0F ? player.rotationPitch : lerp(pPartialTicks, player.prevRotationPitch, player.rotationPitch);
    }


    static float getViewYRot(EntityPlayer player, float pPartialTick) {
        return pPartialTick == 1.0F ? player.rotationYaw : lerp(pPartialTick, player.prevRotationYaw, player.rotationYaw);
    }

    static Vec3d calculateUpVector(float pXRot, float pYRot) {
        return calculateViewVector(pXRot - 90.0F, pYRot);
    }

    static Vec3d calculateViewVector(float pXRot, float pYRot) {
        float f = pXRot * ((float) Math.PI / 180F);
        float f1 = -pYRot * ((float) Math.PI / 180F);
        float f2 = MathHelper.cos(f1);
        float f3 = MathHelper.sin(f1);
        float f4 = MathHelper.cos(f);
        float f5 = MathHelper.sin(f);
        return new Vec3d(f3 * f4, -f5, f2 * f4);
    }

    static float lerp(float pDelta, float pStart, float pEnd) {
        return pStart + pDelta * (pEnd - pStart);
    }

}
