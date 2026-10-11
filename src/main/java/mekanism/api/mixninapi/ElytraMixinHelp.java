package mekanism.api.mixninapi;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

public interface ElytraMixinHelp {


    /**
     * Used to determine if the player can use Elytra flight.
     * This is called Client and Server side.
     *
     * @param stack  The ItemStack in the Chest slot of the entity.
     * @param entity The entity trying to fly.
     * @return True if the entity can use Elytra flight.
     */
    default boolean canElytraFly(ItemStack stack, EntityLivingBase entity) {
        return false;
    }

    /**
     * Used to determine if the player can continue Elytra flight,
     * this is called each tick, and can be used to apply ItemStack damage,
     * consume Energy, or what have you.
     * For example the Vanilla implementation of this, applies damage to the
     * ItemStack every 20 ticks.
     *
     * @param stack  ItemStack in the Chest slot of the entity.
     * @param entity The entity currently in Elytra flight.
     * @return True if the entity should continue Elytra flight or False to stop.
     */
    default boolean elytraFlightTick(ItemStack stack, EntityLivingBase entity, int flightTicks) {
        return false;
    }
}
