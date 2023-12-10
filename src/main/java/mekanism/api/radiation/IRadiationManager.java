package mekanism.api.radiation;

import com.google.common.collect.Table;
import java.util.ServiceLoader;
import mekanism.api.Chunk3D;
import mekanism.api.Coord4D;
import mekanism.api.annotations.NothingNullByDefault;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;



/**
 * The RadiationManager handles radiation across all in-game dimensions. Radiation exposure levels are provided in _sieverts, defining a rate of accumulation of
 * equivalent dose.
 *
 * <br><br>
 * For reference, here are examples of equivalent dose (credit: wikipedia)
 * <ul>
 * <li>100 nSv: baseline dose (banana equivalent dose)</li>
 * <li>250 nSv: airport security screening</li>
 * <li>1 mSv: annual total civilian dose equivalent</li>
 * <li>50 mSv: annual total occupational equivalent dose limit</li>
 * <li>250 mSv: total dose equivalent from 6-month trip to mars</li>
 * <li>1 Sv: maximum allowed dose allowed for NASA astronauts over their careers</li>
 * <li>5 Sv: dose required to (50% chance) kill human if received over 30-day period</li>
 * <li>50 Sv: dose received after spending 10 min next to Chernobyl reactor core directly after meltdown</li>
 * </ul>
 * For defining rate of accumulation, we use _sieverts per hour_ (Sv/h). Here are examples of dose accumulation rates.
 * <ul>
 * <li>100 nSv/h: max recommended human irradiation</li>
 * <li>2.7 uSv/h: irradiation from airline at cruise altitude</li>
 * <li>190 mSv/h: highest reading from fallout of Trinity (Manhattan project test) bomb, _20 miles away_, 3 hours after detonation</li>
 * <li>~500 Sv/h: irradiation inside primary containment vessel of Fukushima power station (at this rate, it takes 30 seconds to accumulate a median lethal dose)</li>
 * </ul>
 *
 * @see IRadiationManager
 */
@NothingNullByDefault
public interface IRadiationManager {

    /**
     * Provides access to Mekanism's implementation of {@link IRadiationManager}.
     *
     * @since 10.4.0
     */
    //IRadiationManager INSTANCE = ServiceLoader.load(IRadiationManager.class).findFirst().orElseThrow(() -> new IllegalStateException("No valid ServiceImpl for IRadiationManager found"));

    /**
     * Helper to expose the ability to check if Mekanism's radiation system is enabled in the config.
     */
    boolean isRadiationEnabled();


    /**
     * Get the radiation level (in Sv/h) at a certain location.
     *
     * @param coord Location
     *
     * @return radiation level (in Sv/h).
     */
    double getRadiationLevel(Coord4D coord);

    /**
     * Get the radiation level (in Sv/h) at an entity's location. To get the radiation level of an entity use
     * {@link mekanism.api.radiation.capability.IRadiationEntity#getRadiation()}.
     *
     * @param entity - Entity to get the radiation level at.
     *
     * @return Radiation level (in Sv/h).
     */
    double getRadiationLevel(Entity entity);

    /**
     * Gets an unmodifiable table of the radiation sources tracked by this manager. This table keeps track of radiation sources on both a chunk and position based level.
     *
     * @return Unmodifiable table of radiation sources.
     */
    Table<Chunk3D, Coord4D, IRadiationSource> getRadiationSources();

    /**
     * Removes all radiation sources in a given chunk.
     *
     * @param chunk Chunk to clear radiation sources of.
     */
    void removeRadiationSources(Chunk3D chunk);

    /**
     * Removes the radiation source at the given location.
     *
     * @param coord Location.
     */
    void removeRadiationSource(Coord4D coord);

    /**
     * Applies a radiation source (Sv) of the given magnitude to a given location.
     *
     * @param coord     Location to release radiation.
     * @param magnitude Amount of radiation to apply (Sv).
     */
    void radiate(Coord4D coord, double magnitude);

    /**
     * Applies an additional magnitude of radiation (Sv) to the given entity after taking into account the radiation resistance provided to the entity by its armor.
     *
     * @param entity    The entity to radiate.
     * @param magnitude Dosage of radiation to apply before radiation resistance (Sv).
     *
     * @implNote This method does not add any radiation to players in creative or spectator.
     */
    void radiate(EntityLiving entity, double magnitude);




}