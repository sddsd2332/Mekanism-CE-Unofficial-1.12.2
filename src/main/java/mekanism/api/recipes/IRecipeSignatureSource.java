package mekanism.api.recipes;

import java.util.Map;

/**
 * Explicit main-thread projection of custom recipe semantics. No fields of an
 * implementing object are inspected. Include every value affecting matching,
 * consumption, output, remainders, time or energy.
 *
 * <p>Data may contain scalars, resource stacks/identifiers, NBT, arrays, lists,
 * sets and maps of supported values. Unknown objects and cycles are rejected,
 * including objects hidden inside collections. Never return a Tile, World,
 * handler, callback or the original third-party recipe.</p>
 *
 * <p>This contract only provides a signature. It does not opt a machine into
 * worker execution or add support to the recipe execution planner.</p>
 */
public interface IRecipeSignatureSource {

    /** Stable namespaced schema ID, including a version when its interpretation changes. */
    String getRecipeSignatureType();

    /** Fresh semantic data captured on the server thread. */
    Map<String, ?> getRecipeSignatureData();
}
