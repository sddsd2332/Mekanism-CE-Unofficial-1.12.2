package mekanism.api.gear;

import mcp.MethodsReturnNonnullByDefault;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.text.IHasTextComponent;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Interface that describes various methods that modules have.
 *
 * @apiNote This interface should not be directly implemented as it is mostly used to expose various parts of a module to the implemented {@link ICustomModule}.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public interface IModule<MODULE extends ICustomModule<MODULE>> {

    /**
     * Gets the data/module type of this module instance.
     */
    ModuleData<MODULE> getData();

    /**
     * Gets the custom module implementation this module references.
     */
    MODULE getCustomInstance();

    /**
     * Gets the number of installed modules of this type.
     */
    int getInstalledCount();

    /**
     * Gets if this module is currently enabled in the Module Tweaker.
     *
     * @return {@code true} if this module is enabled.
     */
    boolean isEnabled();

    /**
     * Gets if this module type ({@link #getData()}) can currently handle mode changes and if this module is configured to handle mode changes in the Module Tweaker.
     *
     * @return {@code true} if this module can handle mode changes.
     */
    boolean handlesModeChange();

    /**
     * Gets if this module type ({@link #getData()}) can currently handle radial mode changes.
     *
     * @return {@code true} if this module can handle radial mode changes.
     * @since 10.3.2
     */
    boolean handlesRadialModeChange();

    /**
     * Gets if this module type ({@link #getData()}) has any data that should be added to the HUD and if this module is configured to render to the HUD in the Module
     * Tweaker.
     *
     * @return {@code true} if this module has data to add to the HUD.
     */
    boolean renderHUD();

    /**
     * Helper to display the mode change message.
     *
     * @param player   Player to send the message to.
     * @param modeName Name of the mode being changed.
     * @param mode     New mode value.
     */
    void displayModeChange(EntityPlayer player, String modeName, IHasTextComponent mode);

    /**
     * Helper to toggle the enabled state of this module and send a message saying the given module was enabled or disabled.
     *
     * @param player   Player to send the message to.
     * @param modeName Text to display that was either enabled or disabled.
     */
    void toggleEnabled(EntityPlayer player, String modeName);

    /**
     * Gets the item this module is installed on.
     *
     * @return Container.
     */
    ItemStack getContainer();

    /**
     * Helper to get the energy container of the item this module is installed on.
     *
     * @return Energy container or {@code null} if something failed.
     */
    @Nullable
    IEnergyContainer getEnergyContainer();

    /**
     * Helper to get the energy stored in {@link #getEnergyContainer()}
     */
    double getContainerEnergy();


    /**
     * Helper to check if there is at least a certain amount of energy stored in {@link #getEnergyContainer()}.
     *
     * @param energy Minimum amount of required energy to check.
     * @return {@code true} if there is no energy cost or there is at least that amount of energy stored in the {@link #getEnergyContainer()}.
     * @since 10.4.0
     */
    boolean hasEnoughEnergy(double energy);

    /**
     * Helper to check if the item this module is installed on can provide the given amount of energy.
     *
     * @param wearer Wearer/User of the item the module is installed on.
     * @param energy Energy amount to check.
     * @return {@code true} if the energy can be used/provided.
     * @implNote By default, this method checks players in creative as well.
     */
    boolean canUseEnergy(EntityLivingBase wearer, double energy);

    /**
     * Helper to check if the item this module is installed on can provide the given amount of energy. If {@code checkCreative} is {@code false} this method will return
     * {@code false} for players in creative or spectator.
     *
     * @param wearer         Wearer/User of the item the module is installed on.
     * @param energy         Energy amount to check.
     * @param ignoreCreative {@code true} to not check the item for energy if the wearer is in creative and just return {@code false} for player's in creative.
     * @return {@code true} if the energy can be used/provided.
     */
    boolean canUseEnergy(EntityLivingBase wearer, double energy, boolean ignoreCreative);

    /**
     * Helper to check if the item this module is installed on can provide the given amount of energy. If the {@code energyContainer} is null this will return {@code
     * false}. If {@code checkCreative} is {@code false} this method will return {@code false} for players in creative or spectator.
     *
     * @param wearer          Wearer/User of the item the module is installed on.
     * @param energyContainer Energy container, most likely gotten from {@link #getEnergyContainer()}.
     * @param energy          Energy amount to check.
     * @param ignoreCreative  {@code true} to not check the item for energy if the wearer is in creative and just return {@code false} for player's in creative.
     * @return {@code true} if the energy can be used/provided.
     * @apiNote This method is mostly for use in not having to look up the energy container multiple times.
     */
    boolean canUseEnergy(EntityLivingBase wearer, @Nullable IEnergyContainer energyContainer, double energy, boolean ignoreCreative);

    /**
     * Helper to use energy from the item this module is installed on.
     *
     * @param wearer Wearer/User of the item the module is installed on.
     * @param energy Energy to use.
     * @return Actual amount of energy used.
     * @implNote By default, this method does not use any energy from players that are in creative.
     */
    double useEnergy(EntityLivingBase wearer, double energy);

    /**
     * Helper to use energy from the item this module is installed on. If {@code checkCreative} is {@code false} this method will return  for
     * players in creative or spectator.
     *
     * @param wearer       Wearer/User of the item the module is installed on.
     * @param energy       Energy to use.
     * @param freeCreative {@code true} to not use any energy from the item if the wearer is in creative.
     * @return Actual amount of energy used.
     */
    double useEnergy(EntityLivingBase wearer, double energy, boolean freeCreative);

    /**
     * Helper to use energy from the given energy container. If the {@code energyContainer} is null this will return . If {@code checkCreative}
     * is {@code false} this method will return for players in creative or spectator.
     *
     * @param wearer          Wearer/User of the item the module is installed on.
     * @param energyContainer Energy container, most likely gotten from {@link #getEnergyContainer()}.
     * @param energy          Energy to use.
     * @param freeCreative    {@code true} to not use any energy from the item if the wearer is in creative.
     * @return Actual amount of energy used.
     * @apiNote This method is mostly for use in not having to look up the energy container multiple times.
     */
    double useEnergy(EntityLivingBase wearer, @Nullable IEnergyContainer energyContainer, double energy, boolean freeCreative);
}
