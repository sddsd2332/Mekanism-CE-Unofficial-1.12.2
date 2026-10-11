package mekanism.api.gear;

import com.google.common.collect.Multimap;
import mekanism.api.functions.FloatSupplier;
import mekanism.api.gear.config.ModuleConfigItemCreator;
import mekanism.api.radial.RadialData;
import mekanism.api.radial.mode.IRadialMode;
import mekanism.api.radial.mode.NestedRadialMode;
import mekanism.api.text.IHasTextComponent;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Interface used to describe and implement custom modules. Instances of this should be returned via the {@link ModuleData}.
 */
@ParametersAreNonnullByDefault
public interface ICustomModule<MODULE extends ICustomModule<MODULE>> {

    /**
     * Called when initializing a new module instance and the backing custom module. This can be used to create module config items that will show up in the ModuleTweaker
     * and can be used to control various settings of this module.
     *
     * @param module            Module instance.
     * @param configItemCreator Helper to create module config items.
     */
    default void init(IModule<MODULE> module, ModuleConfigItemCreator configItemCreator) {
    }

    /**
     * Called each tick on the server side when installed in a MekaSuit and set to enabled.
     *
     * @param module Module instance.
     * @param player Player wearing the MekaSuit.
     */
    default void tickServer(IModule<MODULE> module, EntityPlayer player) {
    }

    /**
     * Called each tick on the client side when installed in a MekaSuit and set to enabled.
     *
     * @param module Module instance.
     * @param player Player wearing the MekaSuit.
     */
    default void tickClient(IModule<MODULE> module, EntityPlayer player) {
    }

    /**
     * Called to collect any HUD strings that should be displayed. This will only be called if {@link ModuleData#rendersHUD()} is {@code true}.
     *
     * @param module         Module instance.
     * @param player         Player using the Meka-Tool or wearing the MekaSuit. In general this will be the client player, but is passed to make sidedness safer and
     *                       easier.
     * @param hudStringAdder Accepts and adds HUD strings.
     */
    default void addHUDStrings(IModule<MODULE> module, EntityPlayer player, Consumer<String> hudStringAdder) {
    }

    /**
     * Called to collect any HUD elements that should be displayed when the MekaSuit is rendering the HUD. This will only be called if {@link ModuleData#rendersHUD()} is
     * {@code true}.
     *
     * @param module          Module instance.
     * @param player          Player using the Meka-Tool or wearing the MekaSuit. In general this will be the client player, but is passed to make sidedness safer and
     *                        easier.
     * @param hudElementAdder Accepts and adds HUD elements.
     * @apiNote See {@link IModuleHelper} for various helpers to create HUD elements.
     */
    default void addHUDElements(IModule<MODULE> module, EntityPlayer player, Consumer<IHUDElement> hudElementAdder) {
    }

    /**
     * Called to check if this module can change modes when disabled or if it should be skipped. This should be overridden for modules where the mode change key toggles
     * whether the module is active.
     *
     * @param module Module instance.
     * @return {@code true} if this module can change modes when disabled.
     */
    default boolean canChangeModeWhenDisabled(IModule<MODULE> module) {
        return false;
    }

    /**
     * Called to check if this module has any radial modes that can be changed when disabled or if it should be skipped. This should be overridden for modules where the
     * radial menu allows toggling whether the module is active.
     *
     * @param module Module instance.
     * @return {@code true} if this module has radial modes that can be changed while disabled.
     * @since 10.3.2
     */
    default boolean canChangeRadialModeWhenDisabled(IModule<MODULE> module) {
        return false;
    }


    /**
     * Called to get the text component to display when the mode is changed via the scroll wheel.  This will only be called if {@link ModuleData#handlesModeChange()} is
     * {@code true}.
     *
     * @param module Module instance.
     * @param stack  The stack to get the mode of.
     * @return Mode display text or {@code null} if no text should be displayed.
     * @since 10.3.2
     */
    @Nullable
    default ITextComponent getModeScrollComponent(IModule<MODULE> module, ItemStack stack) {
        return null;
    }


    /**
     * Called to change the mode of the module. This will only be called if {@link ModuleData#handlesModeChange()} is {@code true}. {@link
     * IModule#displayModeChange(EntityPlayer, String, IHasTextComponent)} is provided to help display the mode change when {@code displayChangeMessage} is {@code
     * true}.
     *
     * @param module               Module instance.
     * @param player               The player who made the mode change.
     * @param stack                The stack to change the mode of.
     * @param shift                The amount to shift the mode by, may be negative for indicating the mode should decrease.
     * @param displayChangeMessage {@code true} if a message should be displayed when the mode changes
     */
    default void changeMode(IModule<MODULE> module, EntityPlayer player, ItemStack stack, int shift, boolean displayChangeMessage) {
    }


    /**
     * Called by the Meka-Tool to attempt to add all supported radial types of the module. This will only be called if {@link ModuleData#handlesModeChange()} is
     * {@code true}.
     *
     * @param module Module instance.
     * @param stack  The stack to get the supported radial types of.
     * @param adder  Consumer used to add any supported radial modes.
     * @see #canChangeRadialModeWhenDisabled(IModule)
     * @since 10.3.2
     */
    default void addRadialModes(IModule<MODULE> module, ItemStack stack, Consumer<NestedRadialMode> adder) {
    }

    /**
     * Called by the Meka-Tool to attempt to get the mode of the module for the given radial data. This will only be called if {@link ModuleData#handlesModeChange()} is
     * {@code true}, but may be called when this module does not support or handle the given radial type, so the radial type should be validated.
     *
     * @param module     Module instance.
     * @param stack      The stack to get the mode of.
     * @param radialData Radial data of the mode being retrieved.
     * @param <MODE>     Radial Mode.
     * @return Radial Mode if this module can handle the given Radial Data, or {@code null} if it can't.
     * @see #canChangeRadialModeWhenDisabled(IModule)
     * @since 10.3.2
     */
    @Nullable
    default <MODE extends IRadialMode> MODE getMode(IModule<MODULE> module, ItemStack stack, RadialData<MODE> radialData) {
        return null;
    }


    /**
     * Called by the Meka-Tool to attempt to set the mode of the module for the given radial data. This will only be called if {@link ModuleData#handlesModeChange()} is
     * {@code true}, but may be called when this module does not support or handle the given radial type, so the radial type should be validated.
     *
     * @param module     Module instance.
     * @param player     The player who is attempting to set the mode.
     * @param stack      The stack to set the mode of.
     * @param radialData Radial data of the mode being set.
     * @param mode       Mode to attempt to set if this module can handle modes of this type.
     * @param <MODE>     Radial Mode.
     * @return {@code true} if this module was able to handle the given radial data.
     * @see #canChangeRadialModeWhenDisabled(IModule)
     * @since 10.3.2
     */
    default <MODE extends IRadialMode> boolean setMode(IModule<MODULE> module, EntityPlayer player, ItemStack stack, RadialData<MODE> radialData, MODE mode) {
        return false;
    }

    /**
     * Called when this module is added to an item.
     *
     * @param module Module instance.
     * @param first  {@code true} if it is the first module of this type installed.
     */
    default void onAdded(IModule<MODULE> module, boolean first) {
    }

    /**
     * Called when this module is removed from an item.
     *
     * @param module Module instance.
     * @param last   {@code true} if it was the last module of this type installed.
     */
    default void onRemoved(IModule<MODULE> module, boolean last) {
    }

    /**
     * Called when the enabled state of this module changes.
     *
     * @param module Module instance.
     */
    default void onEnabledStateChange(IModule<MODULE> module) {
    }

    /**
     * Gets information about if and how this module blocks a given type of damage.
     *
     * @param module       Module instance.
     * @param damageSource Source of the damage.
     * @return Information about how damage can be absorbed, or {@code null} if the given damage type cannot be absorbed.
     */
    @Nullable
    default ModuleDamageAbsorbInfo getDamageAbsorbInfo(IModule<MODULE> module, DamageSource damageSource) {
        return null;
    }

    /**
     * Called when the Meka-Tool is used to allow modules to implement custom use behavior.
     *
     * @param module Module instance.
     * @return Result type or {@link EnumActionResult#PASS} to pass.
     */
    @Nonnull
    default EnumActionResult onItemUse(IModule<MODULE> module, EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        return EnumActionResult.PASS;
    }

    /**
     * Called when the Meka-Tool is used on an entity to allow modules to implement custom interact behavior.
     *
     * @param module Module instance.
     * @param player Player using the Meka-Tool.
     * @param entity Entity type being interacted with.
     * @param hand   Hand used.
     * @return Result type or {@link EnumActionResult#PASS} to pass.
     */
    @Nonnull
    default EnumActionResult onInteract(IModule<MODULE> module, EntityPlayer player, EntityLivingBase entity, EnumHand hand) {
        return EnumActionResult.PASS;
    }

    /**
     * Called on enabled modules when the Meka-Tool or MekaSuit is "dispensed" from a dispenser. The MekaSuit will prioritize performing the vanilla armor dispense
     * behavior of equipping on entities before checking if any of the modules have a custom behavior.
     *
     * @param module Module instance.
     * @param source Dispenser source information.
     * @return The {@link ModuleDispenseResult} defining how this dispenser should behave.
     */
    @Nonnull
    default ModuleDispenseResult onDispense(IModule<MODULE> module, IBlockSource source) {
        return ModuleDispenseResult.DEFAULT;
    }

    //TODO - 1.18: Switch this to a record
    class ModuleDamageAbsorbInfo {

        private final FloatSupplier absorptionRatio;
        private final double energyCost;

        /**
         * @param absorptionRatio Ratio of damage this module can absorb up to, returns a value between zero and one.
         * @param energyCost      Energy cost per point of damage reduced.
         */
        public ModuleDamageAbsorbInfo(FloatSupplier absorptionRatio, double energyCost) {
            this.absorptionRatio = Objects.requireNonNull(absorptionRatio, "Absorption ratio supplier cannot be null");
            this.energyCost = Objects.requireNonNull(energyCost, "Energy cost supplier cannot be null");
        }

        /**
         * Gets the ratio of damage this module can absorb up to, returns a value between zero and one.
         */
        public FloatSupplier getAbsorptionRatio() {
            return absorptionRatio;
        }

        /**
         * Gets the energy cost per point of damage reduced.
         */
        public double getEnergyCost() {
            return energyCost;
        }
    }

    /**
     * Represents the different result states of {@link ICustomModule#onDispense(IModule, IBlockSource)}.
     */
    enum ModuleDispenseResult {
        /**
         * Represents that the module did perform some logic and that no further modules should be checked.
         */
        HANDLED,
        /**
         * Represents that the module did not preform any behavior and to continue checking other installed modules, and then dispense/drop the item.
         */
        DEFAULT,
        /**
         * Represents that the module did not perform any behavior and to continue checking other installed modules, but dispensing/dropping the item should be prevented
         * so that the item can continue being used in the dispenser on future redstone interaction.
         */
        FAIL_PREVENT_DROP
    }

    default void moveRelative(EntityPlayer player, float pAmount, Vec3d pRelative) {
        Vec3d vec3 = getInputVector(pRelative, pAmount, player.rotationYaw);
        player.motionX = player.motionX + vec3.x;
        player.motionY = player.motionY + vec3.y;
        player.motionZ = player.motionZ + vec3.z;
    }

    default Vec3d getInputVector(Vec3d pRelative, float pMotionScaler, float pFacing) {
        double d0 = pRelative.lengthSquared();
        if (d0 < 1.0E-7D) {
            return Vec3d.ZERO;
        } else {
            Vec3d vec3 = (d0 > 1.0D ? pRelative.normalize() : pRelative).scale((double) pMotionScaler);
            float f = MathHelper.sin(pFacing * ((float) Math.PI / 180F));
            float f1 = MathHelper.cos(pFacing * ((float) Math.PI / 180F));
            return new Vec3d(vec3.x * (double) f1 - vec3.z * (double) f, vec3.y, vec3.z * (double) f1 + vec3.x * (double) f);
        }
    }


    default void tickEntityServer(IModule<MODULE> module, Entity item) {
    }


    default void tickEntityClient(IModule<MODULE> module, Entity item) {
    }


    default void multimapModule(IModule<MODULE> module, ItemStack stack, EntityEquipmentSlot slot, Multimap<String, AttributeModifier> multimap) {
    }

    default void hitEntity(IModule<MODULE> module, ItemStack stack, EntityLivingBase target, EntityLivingBase attacker) {
    }


    default void tickServerUpdate(IModule<MODULE> module, ItemStack stack, World world, Entity entity, int itemSlot, boolean isSelected) {
    }

    default void tickClientUpdate(IModule<MODULE> module, ItemStack stack, World world, Entity entity, int itemSlot, boolean isSelected) {
    }
}


